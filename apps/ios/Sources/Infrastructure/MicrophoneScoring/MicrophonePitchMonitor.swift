import AVFoundation
import AudioKit
import SoundpipeAudioKit
import AudioToolbox
import Combine
import Dispatch
import Foundation
import GeloKaraokeCore

public enum MicrophonePitchMonitorStatus:
    Equatable,
    Sendable
{
    case idle
    case requestingPermission
    case running
    case denied
    case failed(String)
}

/// Bridges Apple's existing microphone node into Soundpipe's public Node
/// contract. No second engine or duplicate microphone capture is created.
private final class KantaSoundpipeInput: AudioKit.Node {
    let avAudioNode: AVAudioNode
    var connections: [AudioKit.Node] { [] }
    init(_ inputNode: AVAudioInputNode) { avAudioNode = inputNode }
}

/// Fixed diagnostic transitions only. No raw exception text or microphone
/// samples can escape through this callback.
public enum MicrophoneDiagnosticSignal: Sendable {
    case permissionRequested, running, denied, failed, stopped
    case wiredHeadphones, usbAudio, lineOut, unsupportedOutput
    case monitorAudible, monitorMuted
    case dspSoundpipeSelected, dspAppleSelected, dspDisabled
}

@MainActor
public final class MicrophonePitchMonitor: ObservableObject {
    @Published public private(set) var status:
        MicrophonePitchMonitorStatus = .idle
    @Published public private(set) var observation:
        PitchObservation?
    /// Dry input level, including unvoiced sounds; used by the input check.
    @Published public private(set) var inputRMS: Float = 0

    @Published public private(set) var monitoringRoute: VocalMonitoringRoute = .unsupported
    @Published public private(set) var isMonitorAudible = false

    private let voicePitch = AVAudioUnitTimePitch()
    private let soundpipeCorrect: PitchCorrect
    private var configuredScale: String?
    private var activeBackend: VocalPitchBackend?

    private var pitchFollower = VocalPitchFollower()
    private let voiceEQ = AVAudioUnitEQ(numberOfBands: 2)
    private let compressor = AVAudioUnitEffect(
        audioComponentDescription: AudioComponentDescription(
            componentType: kAudioUnitType_Effect,
            componentSubType: kAudioUnitSubType_DynamicsProcessor,
            componentManufacturer: kAudioUnitManufacturer_Apple,
            componentFlags: 0, componentFlagsMask: 0
        )
    )
    private let deEsserEQ = AVAudioUnitEQ(numberOfBands: 1)
    private let doublerDelay = AVAudioUnitDelay()
    private let peakLimiter = AVAudioUnitEffect(
        audioComponentDescription: AudioComponentDescription(
            componentType: kAudioUnitType_Effect,
            componentSubType: kAudioUnitSubType_PeakLimiter,
            componentManufacturer: kAudioUnitManufacturer_Apple,
            componentFlags: 0, componentFlagsMask: 0
        )
    )
    private var sibilanceReduction: Float = 0
    private let voiceReverb = AVAudioUnitReverb()
    private let voiceDelay = AVAudioUnitDelay()
    private let voiceMixer = AVAudioMixerNode()
    private var effects = VocalFXSettings()
    public var onCaptureActivityChanged: (@MainActor (Bool) -> Void)?
    public var onDiagnosticEvent: (@MainActor (MicrophoneDiagnosticSignal) -> Void)?



    private let engine = AVAudioEngine()
    private let detector: AutocorrelationPitchDetector
    private var hasTap = false
    private var captureGeneration = UUID()

    public init(
        detector: AutocorrelationPitchDetector =
            AutocorrelationPitchDetector()
    ) {
        self.detector = detector
        // SoundpipeAudioKit 5.7.4: reusable native Audio Unit, owned by the
        // same AVAudioEngine. DSP always starts neutral and output hard-muted.
        soundpipeCorrect = PitchCorrect(
            KantaSoundpipeInput(engine.inputNode),
            speed: 0.4, amount: 0
        )

        // Keep all nodes connected from the beginning. FX adjustments never
        // rewire a running engine and pitch scoring taps the unprocessed input.
        engine.attach(soundpipeCorrect.avAudioNode)
        engine.attach(voicePitch)
        engine.attach(voiceEQ)
        engine.attach(compressor)
        engine.attach(deEsserEQ)
        engine.attach(doublerDelay)
        engine.attach(peakLimiter)
        engine.attach(voiceReverb)
        engine.attach(voiceDelay)
        engine.attach(voiceMixer)
        let low = voiceEQ.bands[0]
        low.filterType = .lowShelf
        low.frequency = 180
        low.bypass = false
        let high = voiceEQ.bands[1]
        high.filterType = .highShelf
        high.frequency = 3_500
        high.bypass = false
        let sibilanceBand = deEsserEQ.bands[0]
        sibilanceBand.filterType = .highShelf
        sibilanceBand.frequency = 5_500
        sibilanceBand.bypass = false
        voicePitch.pitch = 0
        soundpipeCorrect.amount = 0
        doublerDelay.delayTime = 0.028
        doublerDelay.feedback = 0
        doublerDelay.wetDryMix = 0
        voiceDelay.delayTime = 0.22
        voiceDelay.feedback = 17
        voiceDelay.lowPassCutoff = 5_000
        voiceMixer.outputVolume = 0 // Never enable microphone feedback by default.

        // The input -> FX chain is connected only after record permission
        // succeeds in start(), so initialization doesn't activate the mic.
        #if os(iOS)
        NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.refreshMonitorSafety()
            }
        }
        #endif
    }

    public func configureEffects(_ value: VocalFXSettings) {
        effects = value.clamped
        let selectedBackend = effects.pitchCorrection?.enabled == true
            ? effects.pitchCorrection?.selectedBackend : nil
        if activeBackend != selectedBackend {
            pitchFollower.reset()
            voicePitch.pitch = 0
            soundpipeCorrect.amount = 0
            activeBackend = selectedBackend
            switch selectedBackend {
            case .some(.soundpipe): onDiagnosticEvent?(.dspSoundpipeSelected)
            case .some(.native): onDiagnosticEvent?(.dspAppleSelected)
            case nil: onDiagnosticEvent?(.dspDisabled)
            }
        }
        updatePitchBackend()
        voiceReverb.loadFactoryPreset(reverbPreset(for: effects.preset))
        voiceReverb.wetDryMix = effects.reverbMix
        voiceDelay.wetDryMix = effects.echoMix
        voiceDelay.delayTime = TimeInterval(effects.delayMilliseconds) / 1_000
        doublerDelay.wetDryMix = effects.doubler
        // Apple's built-in DynamicsProcessor combines a compressor and a
        // downward expander. A separate PeakLimiter protects the output.
        // Parameters are bounded by VocalFXSettings before reaching AU.
        setDynamics(kDynamicsProcessorParam_Threshold,
                    -12 - effects.compression * 22)
        setDynamics(kDynamicsProcessorParam_HeadRoom, 5)
        setDynamics(kDynamicsProcessorParam_ExpansionThreshold,
                    -65 + effects.noiseGate * 27)
        setDynamics(kDynamicsProcessorParam_ExpansionRatio,
                    1 + effects.noiseGate * 7)
        setDynamics(kDynamicsProcessorParam_AttackTime, 0.015)
        setDynamics(kDynamicsProcessorParam_ReleaseTime, 0.18)
        setDynamics(kDynamicsProcessorParam_OverallGain, -3)
        setLimiter(kLimiterParam_PreGain, -2)
        voiceEQ.bands[0].gain = effects.bassGain
        voiceEQ.bands[1].gain = effects.trebleGain
        refreshMonitorSafety()
    }

    /// Configure only on the main actor; never allocate or change a graph
    /// connection on an AVAudioEngine render callback.
    private func updatePitchBackend() {
        guard let setting = effects.pitchCorrection, setting.enabled,
              isMonitorAudible else {
            soundpipeCorrect.amount = 0
            pitchFollower.reset()
            voicePitch.pitch = 0
            return
        }
        switch setting.selectedBackend {
        case .native:
            soundpipeCorrect.amount = 0
        case .soundpipe:
            voicePitch.pitch = 0
            pitchFollower.reset()
            let signature = "\(setting.root)-\(setting.scale.rawValue)"
            if configuredScale != signature {
                // Disable the effect while refreshing the table. Do not
                // rewrite it for normal correction amount/speed slider ticks.
                soundpipeCorrect.amount = 0
                soundpipeCorrect.setScaleFrequencies(
                    Self.allowedFrequencies(for: setting)
                )
                configuredScale = signature
            }
            soundpipeCorrect.speed = setting.speed
            soundpipeCorrect.amount = setting.amount
        }
    }

    /// Exact same interval/key set as VocalPitchQuantizer, expressed as Hz.
    /// These frequencies feed the actual MIT PitchCorrect Audio Unit.
    private static func allowedFrequencies(for setting: VocalPitchCorrection) -> [Float] {
        let settings = setting.clamped
        let classes = Set(settings.scale.intervals.map { ($0 + settings.root) % 12 })
        return (24...107).filter { classes.contains($0 % 12) }.map {
            440 * powf(2, (Float($0) - 69) / 12)
        }
    }

    private func setDynamics(_ key: AudioUnitParameterID, _ value: Float) {
        let unit = compressor.audioUnit
        AudioUnitSetParameter(unit, key, kAudioUnitScope_Global, 0, value, 0)
    }

    private func setLimiter(_ key: AudioUnitParameterID, _ value: Float) {
        let unit = peakLimiter.audioUnit
        AudioUnitSetParameter(unit, key, kAudioUnitScope_Global, 0, value, 0)
    }

    /// Dynamic high-frequency suppression triggered by the dry input's
    /// short-window high-frequency energy. This is intentionally bounded:
    /// reduce sharp sibilants rather than change target pitch/scoring.
    private func updateSibilance(roughness: Float) {
        let threshold: Float = 0.20
        let activity = min(max((roughness - threshold) * 3, 0), 1)
        let wanted = activity * effects.deEsser * 12
        sibilanceReduction = sibilanceReduction * 0.68 + wanted * 0.32
        deEsserEQ.bands[0].gain = -sibilanceReduction
    }

    private func reverbPreset(for preset: VocalFXPreset) -> AVAudioUnitReverbPreset {
        switch preset {
        case .dry, .studio: .mediumRoom
        case .room: .smallRoom
        case .hall: .largeHall
        case .cathedral: .cathedral
        case .plate: .plate
        case .echo: .mediumRoom
        }
    }

    private func refreshMonitorSafety() {
        let oldRoute = monitoringRoute
        let wasAudible = isMonitorAudible
        #if os(iOS)
        let outputs = AVAudioSession.sharedInstance().currentRoute.outputs
        let supported: Set<AVAudioSession.Port> = [
            .headphones, .usbAudio, .lineOut
        ]
        if let output = outputs.first, outputs.count == 1,
           supported.contains(output.portType) {
            switch output.portType {
            case .headphones: monitoringRoute = .wiredHeadphones
            case .usbAudio: monitoringRoute = .usbAudio
            case .lineOut: monitoringRoute = .lineOut
            default: monitoringRoute = .unsupported
            }
        } else {
            monitoringRoute = .unsupported
        }
        #else
        monitoringRoute = .unsupported
        #endif
        if oldRoute != monitoringRoute {
            switch monitoringRoute {
            case .wiredHeadphones: onDiagnosticEvent?(.wiredHeadphones)
            case .usbAudio: onDiagnosticEvent?(.usbAudio)
            case .lineOut: onDiagnosticEvent?(.lineOut)
            case .unsupported: onDiagnosticEvent?(.unsupportedOutput)
            }
        }
        isMonitorAudible = effects.monitorEnabled
            && status == .running && monitoringRoute.permitsLiveMonitor
        if wasAudible != isMonitorAudible {
            onDiagnosticEvent?(isMonitorAudible ? .monitorAudible : .monitorMuted)
        }
        voiceMixer.outputVolume = isMonitorAudible
            ? effects.microphoneLevel : 0
        if !isMonitorAudible {
            soundpipeCorrect.amount = 0
            pitchFollower.reset()
            voicePitch.pitch = 0
        } else {
            updatePitchBackend()
        }
    }

    private func applyPitchCorrection(_ detected: DetectedPitch?) {
        guard isMonitorAudible,
              let settings = effects.pitchCorrection,
              settings.enabled else {
            pitchFollower.reset()
            voicePitch.pitch = 0
            return
        }
        // The MIT Soundpipe Audio Unit detects and corrects pitch itself;
        // Apple's fallback follows the established dry observation path.
        guard settings.selectedBackend == .native else {
            voicePitch.pitch = 0
            return
        }
        voicePitch.pitch = pitchFollower.update(
            frequencyHz: detected?.frequencyHz,
            confidence: detected?.confidence ?? 0,
            settings: settings
        )
    }

    public func start() async {
        guard status != .running, status != .requestingPermission else { return }
        let generation = UUID()
        captureGeneration = generation

        onCaptureActivityChanged?(true)
        status = .requestingPermission
        onDiagnosticEvent?(.permissionRequested)

        let granted = await requestRecordPermission()
        guard generation == captureGeneration else { return }
        guard !Task.isCancelled else {
            stop()
            return
        }
        guard granted else {
            status = .denied
            onCaptureActivityChanged?(false)
            onDiagnosticEvent?(.denied)
            return
        }

        do {
            #if os(iOS)
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(
                .playAndRecord,
                mode: .measurement,
                options: [
                    .defaultToSpeaker,
                    .allowBluetooth
                ]
            )
            try audioSession.setActive(true)
            #endif

            // The raw input tap remains before these processors; reverb
            // never contaminates room-mic pitch scoring observations.
            if engine.outputConnectionPoints(
                for: engine.inputNode, outputBus: 0
            ).isEmpty {
                engine.connect(
                    engine.inputNode, to: soundpipeCorrect.avAudioNode, format: nil
                )
                engine.connect(
                    soundpipeCorrect.avAudioNode, to: voicePitch, format: nil
                )
                engine.connect(voicePitch, to: voiceEQ, format: nil)
                engine.connect(voiceEQ, to: compressor, format: nil)
                engine.connect(compressor, to: deEsserEQ, format: nil)
                engine.connect(deEsserEQ, to: doublerDelay, format: nil)
                engine.connect(doublerDelay, to: voiceReverb, format: nil)
                engine.connect(voiceReverb, to: voiceDelay, format: nil)
                engine.connect(voiceDelay, to: peakLimiter, format: nil)
                engine.connect(peakLimiter, to: voiceMixer, format: nil)
                engine.connect(voiceMixer, to: engine.mainMixerNode, format: nil)
            }
            configureEffects(effects)

            if hasTap {
                engine.inputNode.removeTap(onBus: 0)
                hasTap = false
            }

            let detector = self.detector
            engine.inputNode.installTap(
                onBus: 0,
                bufferSize: 2_048,
                format: nil
            ) { [weak self] buffer, time in
                guard let channels =
                    buffer.floatChannelData,
                    buffer.frameLength > 0 else {
                    return
                }

                let frameCount = Int(buffer.frameLength)
                let samples = Array(
                    UnsafeBufferPointer(
                        start: channels[0],
                        count: frameCount
                    )
                )
                let sampleRate = buffer.format.sampleRate

                // Estimate high-frequency content before passing dry
                // samples to the existing pitch detector.
                var differences: Float = 0
                var energy: Float = 0
                if frameCount > 1 {
                    for index in stride(from: 1, to: frameCount, by: 4) {
                        let value = samples[index]
                        let change = value - samples[index - 1]
                        differences += change * change
                        energy += value * value
                    }
                }
                let roughness = energy > 0.00001
                    ? min(1, sqrt(differences / energy) / 1.5) : 0
                let level = sqrt(
                    energy / Float(max(1, (frameCount - 1) / 4))
                )


                let timestamp = Int64(
                    DispatchTime.now().uptimeNanoseconds / 1_000_000
                )
                // Sibilants are often unvoiced, so update the de-esser
                // even if the pitch detector cannot resolve a note.
                let detected = detector.detect(
                    samples: samples,
                    sampleRate: sampleRate
                )

                // The dry scoring path receives only valid pitch
                // observations. FX never modifies these input samples.
                let observation = detected.map { value in
                    PitchObservation(
                        timestampMilliseconds: timestamp,
                        frequencyHz: value.frequencyHz,
                        confidence: value.confidence,
                        rms: value.rms
                    )
                }

                Task { @MainActor [weak self] in
                    guard let self, self.captureGeneration == generation,
                          self.status == .running else { return }
                    self.updateSibilance(roughness: roughness)
                    self.inputRMS = min(max(level, 0), 1)
                    self.observation = observation
                    // Old scoring observations must not linger through silence.
                    self.applyPitchCorrection(detected)
                }
            }
            hasTap = true

            engine.prepare()
            try engine.start()
            status = .running
            onDiagnosticEvent?(.running)
            refreshMonitorSafety()
        } catch {
            if hasTap {
                engine.inputNode.removeTap(onBus: 0)
                hasTap = false
            }
            engine.stop()
            status = .failed(error.localizedDescription)
            onCaptureActivityChanged?(false)
            onDiagnosticEvent?(.failed)
            refreshMonitorSafety()
        }
    }

    public func stop() {
        let wasActive = status != .idle || isMonitorAudible
        captureGeneration = UUID()
        if hasTap {
            engine.inputNode.removeTap(onBus: 0)
            hasTap = false
        }
        // Immediate hard mute on leaving the Host or revoking permission.
        voiceMixer.outputVolume = 0
        soundpipeCorrect.amount = 0
        pitchFollower.reset()
        voicePitch.pitch = 0
        isMonitorAudible = false
        engine.stop()
        observation = nil
        inputRMS = 0
        status = .idle
        onCaptureActivityChanged?(false)
        if wasActive { onDiagnosticEvent?(.stopped) }
    }

    private func requestRecordPermission() async -> Bool {
        #if os(iOS)
        await withCheckedContinuation { continuation in
            AVAudioSession.sharedInstance()
                .requestRecordPermission { granted in
                    continuation.resume(
                        returning: granted
                    )
                }
        }
        #else
        true
        #endif
    }
}

