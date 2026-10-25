#if canImport(AVFoundation)
import AVFoundation
import Dispatch
import Foundation
import GeloKaraokeCore

public actor AVFoundationMIDIPlaybackEngine: AudioPlaybackEngine {
    private let engine = AVAudioEngine()
    private let sampler = AVAudioUnitSampler()
    private let defaultSoundFontURL: URL?

    private var asset: AudioPlaybackAsset?
    private var plan: MIDIPlaybackPlan?
    private var transport: TransportState = .stopped
    private var didFinishNaturally = false
    private var positionMilliseconds: Int64 = 0
    private var messageIndex = 0
    private var volume: Float = 1
    private var pitchSemitones = 0
    private var tempoRatio = 1.0

    private var anchorPositionMilliseconds: Int64 = 0
    private var anchorUptimeNanoseconds: UInt64?
    private var schedulerGeneration: UInt64 = 0
    private var schedulerTask: Task<Void, Never>?
    private var loadedSoundFontIdentity:
        SoundFontIdentity?

    private var preparedCandidatePaths: [String] = []
    private var preparedCandidateIdentities: [SoundFontIdentity] = []

    public init(defaultSoundFontURL: URL? = nil) {
        self.defaultSoundFontURL = defaultSoundFontURL
        engine.attach(sampler)
        engine.connect(sampler, to: engine.mainMixerNode, format: nil)
        sampler.volume = volume
    }

    deinit {
        schedulerTask?.cancel()
    }

    public func load(_ asset: AudioPlaybackAsset) async throws {
        guard asset.mediaKind == .midi || asset.mediaKind == .kar else {
            throw AudioPlaybackEngineError.unsupportedMediaKind(
                asset.mediaKind
            )
        }

        let soundFontURL = asset.soundFontURL ?? defaultSoundFontURL
        guard let soundFontURL else {
            throw AudioPlaybackEngineError.missingSoundFont
        }

        let data = try Data(contentsOf: asset.url, options: [.mappedIfSafe])
        let midiFile = try StandardMIDIParser.parse(data)
        let newPlan = MIDIPlaybackPlan(file: midiFile)

        stopScheduler()
        allNotesOff()

        try loadSoundFont(
            at: soundFontURL,
            fallbackURLs: asset.fallbackSoundFontURLs
        )

        didFinishNaturally = false
        self.asset = asset
        plan = newPlan
        transport = .stopped
        positionMilliseconds = 0
        messageIndex = 0
        anchorPositionMilliseconds = 0
        anchorUptimeNanoseconds = nil
    }


    public func prepareSoundFont(
        at primaryURL: URL,
        fallbackURLs: [URL] = []
    ) async throws {
        guard transport == .stopped else { return }
        try loadSoundFont(at: primaryURL, fallbackURLs: fallbackURLs)
    }

    private func loadSoundFont(at primaryURL: URL, fallbackURLs: [URL]) throws {
        let candidates = [primaryURL] + fallbackURLs
        let paths = candidates.map { $0.standardizedFileURL.path }
        let identities = candidates.compactMap { try? SoundFontIdentity(url: $0) }
        if paths == preparedCandidatePaths,
           identities == preparedCandidateIdentities,
           let loadedSoundFontIdentity,
           identities.contains(loadedSoundFontIdentity) {
            return
        }
        var seen = Set<String>()
        var lastError: Error?

        for candidate in candidates {
            let standardized =
                candidate.standardizedFileURL
            let key = standardized.path
            guard seen.insert(key).inserted else {
                continue
            }

            do {
                let identity = try SoundFontIdentity(
                    url: standardized
                )

                if loadedSoundFontIdentity == identity {
                    preparedCandidatePaths = paths
                    preparedCandidateIdentities = identities
                    return
                }

                // A failed load may mutate the sampler. Never retain a stale cache identity.
                loadedSoundFontIdentity = nil
                try sampler.loadSoundBankInstrument(
                    at: standardized,
                    program: 0,
                    bankMSB: 0x79,
                    bankLSB: 0
                )

                loadedSoundFontIdentity = identity
                preparedCandidatePaths = paths
                preparedCandidateIdentities = identities
                return
            } catch {
                lastError = error
            }
        }

        if let lastError {
            throw lastError
        }

        throw AudioPlaybackEngineError.missingSoundFont
    }

    public func loadedSoundFontURL() -> URL? {
        loadedSoundFontIdentity?.url
    }

    public func play() async throws {
        guard transport != .playing else { return }
        didFinishNaturally = false
        guard let plan, asset != nil else {
            throw AudioPlaybackEngineError.noAssetLoaded
        }

        if positionMilliseconds >= plan.durationMilliseconds {
            positionMilliseconds = 0
            messageIndex = 0
        }

        if !engine.isRunning {
            try engine.start()
        }

        rehydrate(at: positionMilliseconds)
        anchorPositionMilliseconds = positionMilliseconds
        anchorUptimeNanoseconds = DispatchTime.now().uptimeNanoseconds
        transport = .playing
        startScheduler()
    }

    public func pause() async {
        guard asset != nil else { return }
        updatePositionFromClock()
        stopScheduler()
        allNotesOff()
        transport = .paused
    }

    public func stop() async {
        didFinishNaturally = false
        guard asset != nil else { return }
        stopScheduler()
        allNotesOff()
        positionMilliseconds = 0
        messageIndex = 0
        anchorPositionMilliseconds = 0
        anchorUptimeNanoseconds = nil
        transport = .stopped
    }

    public func seek(toMilliseconds milliseconds: Int64) async throws {
        didFinishNaturally = false
        guard let plan, asset != nil else {
            throw AudioPlaybackEngineError.noAssetLoaded
        }

        let wasPlaying = transport == .playing
        if wasPlaying {
            updatePositionFromClock()
        }

        stopScheduler()
        allNotesOff()

        positionMilliseconds = min(
            max(0, milliseconds),
            plan.durationMilliseconds
        )
        messageIndex = plan.messageIndex(
            atOrAfter: positionMilliseconds
        )

        if wasPlaying, positionMilliseconds < plan.durationMilliseconds {
            rehydrate(at: positionMilliseconds)
        }

        if wasPlaying,
           positionMilliseconds < plan.durationMilliseconds {
            if !engine.isRunning {
                try engine.start()
            }
            anchorPositionMilliseconds = positionMilliseconds
            anchorUptimeNanoseconds = DispatchTime.now().uptimeNanoseconds
            transport = .playing
            startScheduler()
        } else {
            anchorUptimeNanoseconds = nil
            transport = positionMilliseconds >= plan.durationMilliseconds
                ? .stopped
                : .paused
        }
    }

    public func setVolume(_ value: Float) async {
        volume = min(max(value, 0), 1)
        sampler.volume = volume
    }

    public func setPitch(semitones: Int) async {
        let newValue = min(max(semitones, -12), 12)
        guard newValue != pitchSemitones else { return }

        if transport == .playing {
            updatePositionFromClock()
        }

        pitchSemitones = newValue
        allNotesOff()

        if asset != nil, transport == .playing {
            rehydrate(at: positionMilliseconds)
        }

        if transport == .playing {
            anchorPositionMilliseconds = positionMilliseconds
            anchorUptimeNanoseconds = DispatchTime.now().uptimeNanoseconds
        }
    }

    public func setTempo(ratio: Double) async {
        let newValue = min(max(ratio, 0.5), 2.0)
        guard newValue != tempoRatio else { return }

        let wasPlaying = transport == .playing
        if wasPlaying {
            updatePositionFromClock()
            stopScheduler()
        }

        tempoRatio = newValue

        if wasPlaying {
            anchorPositionMilliseconds = positionMilliseconds
            anchorUptimeNanoseconds = DispatchTime.now().uptimeNanoseconds
            transport = .playing
            startScheduler()
        }
    }

    public func suspendForOnlinePlayback() async throws {
        guard transport != .playing else { throw CocoaError(.userCancelled) }
        engine.pause()
    }

    public func snapshot() async -> AudioPlaybackSnapshot {
        if transport == .playing {
            updatePositionFromClock()
        }

        return AudioPlaybackSnapshot(
            transport: transport,
            songID: asset?.songID,
            positionMilliseconds: positionMilliseconds,
            durationMilliseconds: plan?.durationMilliseconds,
            volume: volume,
            pitchSemitones: pitchSemitones,
            tempoRatio: tempoRatio,
            didFinishNaturally: didFinishNaturally
        )
    }

    private func startScheduler() {
        stopScheduler(incrementGeneration: false)
        schedulerGeneration &+= 1
        let generation = schedulerGeneration

        schedulerTask = Task { [weak self] in
            guard let self else { return }
            await self.schedulerLoop(generation: generation)
        }
    }

    private func schedulerLoop(generation: UInt64) async {
        while generation == schedulerGeneration,
              transport == .playing,
              let plan {
            updatePositionFromClock()

            while messageIndex < plan.messages.count,
                  plan.messages[messageIndex].milliseconds
                    <= positionMilliseconds {
                dispatch(plan.messages[messageIndex].message)
                messageIndex += 1
            }

            if positionMilliseconds >= plan.durationMilliseconds {
                positionMilliseconds = plan.durationMilliseconds
                didFinishNaturally = true
                allNotesOff()
                anchorUptimeNanoseconds = nil
                transport = .stopped
                schedulerTask = nil
                return
            }

            let waitMilliseconds: Int64
            if messageIndex < plan.messages.count {
                let delta = max(
                    1,
                    plan.messages[messageIndex].milliseconds
                        - positionMilliseconds
                )
                waitMilliseconds = max(
                    1,
                    Int64(
                        (Double(delta) / tempoRatio)
                            .rounded(.towardZero)
                    )
                )
            } else {
                let delta = max(
                    1,
                    plan.durationMilliseconds
                        - positionMilliseconds
                )
                waitMilliseconds = max(
                    1,
                    Int64(
                        (Double(delta) / tempoRatio)
                            .rounded(.towardZero)
                    )
                )
            }

            do {
                try await Task.sleep(
                    nanoseconds:
                        UInt64(min(waitMilliseconds, 250))
                        * 1_000_000
                )
            } catch {
                return
            }
        }
    }

    private func stopScheduler(
        incrementGeneration: Bool = true
    ) {
        schedulerTask?.cancel()
        schedulerTask = nil
        if incrementGeneration {
            schedulerGeneration &+= 1
        }
    }

    private func updatePositionFromClock() {
        guard transport == .playing,
              let anchorUptimeNanoseconds else {
            return
        }

        let now = DispatchTime.now().uptimeNanoseconds
        let elapsedNanoseconds =
            now >= anchorUptimeNanoseconds
            ? now - anchorUptimeNanoseconds
            : 0
        let elapsedMilliseconds =
            Double(elapsedNanoseconds) / 1_000_000

        let calculated =
            Double(anchorPositionMilliseconds)
            + elapsedMilliseconds * tempoRatio

        let duration = plan?.durationMilliseconds ?? Int64.max
        positionMilliseconds = min(
            duration,
            max(
                0,
                Int64(calculated.rounded(.towardZero))
            )
        )
    }

    private func rehydrate(at milliseconds: Int64) {
        guard let plan else { return }

        let state = plan.resumeState(before: milliseconds)
        for event in state.stateMessages {
            dispatch(event.message)
        }
        for note in state.activeNotes {
            let key = transposedKey(
                note.key,
                channel: note.channel
            )
            sampler.startNote(
                key,
                withVelocity: note.velocity,
                onChannel: note.channel
            )
        }

        messageIndex = plan.messageIndex(
            atOrAfter: milliseconds
        )
    }

    private func dispatch(_ message: MIDIMessage) {
        switch message {
        case let .channel(channelMessage):
            dispatchChannel(channelMessage)

        case let .systemExclusive(data):
            sampler.sendMIDISysExEvent(data)

        case .meta:
            break
        }
    }

    private func dispatchChannel(_ message: GeloKaraokeCore.MIDIChannelMessage) {
        switch message {
        case let .noteOff(channel, key, _):
            sampler.stopNote(
                transposedKey(key, channel: channel),
                onChannel: channel
            )

        case let .noteOn(channel, key, velocity):
            let transposed = transposedKey(
                key,
                channel: channel
            )
            if velocity == 0 {
                sampler.stopNote(
                    transposed,
                    onChannel: channel
                )
            } else {
                sampler.startNote(
                    transposed,
                    withVelocity: velocity,
                    onChannel: channel
                )
            }

        case let .polyphonicKeyPressure(
            channel,
            key,
            pressure
        ):
            sampler.sendPressure(
                forKey: transposedKey(key, channel: channel),
                withValue: pressure,
                onChannel: channel
            )

        case let .controlChange(
            channel,
            controller,
            value
        ):
            sampler.sendController(
                controller,
                withValue: value,
                onChannel: channel
            )

        case let .programChange(channel, program):
            sampler.sendProgramChange(
                program,
                onChannel: channel
            )

        case let .channelPressure(channel, pressure):
            sampler.sendPressure(
                pressure,
                onChannel: channel
            )

        case let .pitchBend(channel, value14):
            sampler.sendPitchBend(
                value14,
                onChannel: channel
            )
        }
    }

    private func transposedKey(
        _ key: UInt8,
        channel: UInt8
    ) -> UInt8 {
        // General MIDI channel 10 (zero-based 9) is percussion and
        // should not be chromatically transposed.
        guard channel != 9 else { return key }
        return UInt8(
            min(
                127,
                max(
                    0,
                    Int(key) + pitchSemitones
                )
            )
        )
    }

    private func allNotesOff() {
        for channel in UInt8(0)...UInt8(15) {
            sampler.sendController(64, withValue: 0, onChannel: channel)
            sampler.sendController(120, withValue: 0, onChannel: channel)
            sampler.sendController(
                123,
                withValue: 0,
                onChannel: channel
            )
        }
    }
}

private struct SoundFontIdentity: Equatable {
    let url: URL
    let fileSize: Int64
    let modificationDate: Date?

    init(url: URL) throws {
        var freshURL = url
        freshURL.removeAllCachedResourceValues()
        let values = try freshURL.resourceValues(
            forKeys: [
                .fileSizeKey,
                .contentModificationDateKey,
                .isRegularFileKey
            ]
        )

        guard values.isRegularFile == true else {
            throw CocoaError(.fileReadNoSuchFile)
        }

        self.url = url.standardizedFileURL
        self.fileSize = Int64(values.fileSize ?? 0)
        self.modificationDate =
            values.contentModificationDate
    }
}

#endif

