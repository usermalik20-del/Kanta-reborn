#if canImport(AVFoundation)
import AVFoundation
import Foundation
import GeloKaraokeCore

public actor AVFoundationAudioPlaybackEngine: AudioPlaybackEngine {
    private let engine = AVAudioEngine()
    private let player = AVAudioPlayerNode()
    private let timePitch = AVAudioUnitTimePitch()

    private var file: AVAudioFile?
    private var asset: AudioPlaybackAsset?
    private var fileLength: AVAudioFramePosition = 0
    private var sampleRate: Double = 0
    private var scheduledStartFrame: AVAudioFramePosition = 0
    private var restingFrame: AVAudioFramePosition = 0
    private var transport: TransportState = .stopped
    private var didFinishNaturally = false
    private var volume: Float = 1
    private var pitchSemitones = 0
    private var tempoRatio = 1.0
    private var scheduleGeneration: UInt64 = 0
    private var isScheduled = false

    public init() {
        engine.attach(player)
        engine.attach(timePitch)
        engine.connect(player, to: timePitch, format: nil)
        engine.connect(timePitch, to: engine.mainMixerNode, format: nil)
        player.volume = volume
        timePitch.pitch = 0
        timePitch.rate = 1
    }

    public func load(_ asset: AudioPlaybackAsset) async throws {
        guard asset.mediaKind == .mp3 || asset.mediaKind == .wav else {
            throw AudioPlaybackEngineError.unsupportedMediaKind(asset.mediaKind)
        }

        let openedFile = try AVAudioFile(forReading: asset.url)
        guard openedFile.processingFormat.sampleRate > 0,
              openedFile.length > 0 else {
            throw AudioPlaybackEngineError.invalidDuration
        }

        player.stop()
        scheduleGeneration &+= 1

        self.file = openedFile
        didFinishNaturally = false
        self.asset = asset
        fileLength = openedFile.length
        sampleRate = openedFile.processingFormat.sampleRate
        scheduledStartFrame = 0
        restingFrame = 0
        transport = .stopped
        try schedule(from: 0)
    }

    public func play() async throws {
        didFinishNaturally = false
        guard file != nil else {
            throw AudioPlaybackEngineError.noAssetLoaded
        }

        if !isScheduled || restingFrame >= fileLength {
            try schedule(from: restingFrame >= fileLength ? 0 : restingFrame)
        }

        if !engine.isRunning {
            try engine.start()
        }

        player.play()
        transport = .playing
    }

    public func pause() async {
        guard file != nil else { return }
        restingFrame = currentFrame()
        player.pause()
        transport = .paused
    }

    public func stop() async {
        didFinishNaturally = false
        guard file != nil else { return }
        player.stop()
        scheduleGeneration &+= 1
        restingFrame = 0
        scheduledStartFrame = 0
        isScheduled = false
        transport = .stopped
        try? schedule(from: 0)
    }

    public func seek(toMilliseconds milliseconds: Int64) async throws {
        didFinishNaturally = false
        guard file != nil else {
            throw AudioPlaybackEngineError.noAssetLoaded
        }

        let wasPlaying = transport == .playing
        let target = frame(forMilliseconds: max(0, milliseconds))
        try schedule(from: min(target, fileLength))
        restingFrame = min(target, fileLength)

        if wasPlaying, restingFrame < fileLength {
            if !engine.isRunning {
                try engine.start()
            }
            player.play()
            transport = .playing
        } else {
            transport = .paused
        }
    }

    public func setVolume(_ value: Float) async {
        volume = min(max(value, 0), 1)
        player.volume = volume
    }

    public func setPitch(semitones: Int) async {
        pitchSemitones = min(max(semitones, -12), 12)
        timePitch.pitch = Float(pitchSemitones * 100)
    }

    public func setTempo(ratio: Double) async {
        tempoRatio = min(max(ratio, 0.5), 2.0)
        timePitch.rate = Float(tempoRatio)
    }

    public func suspendForOnlinePlayback() async throws {
        guard transport != .playing else { throw CocoaError(.userCancelled) }
        engine.pause()
    }

    public func snapshot() async -> AudioPlaybackSnapshot {
        AudioPlaybackSnapshot(
            transport: transport,
            songID: asset?.songID,
            positionMilliseconds: milliseconds(forFrame: currentFrame()),
            durationMilliseconds: file.map { _ in milliseconds(forFrame: fileLength) },
            volume: volume,
            pitchSemitones: pitchSemitones,
            tempoRatio: tempoRatio,
            didFinishNaturally: didFinishNaturally
        )
    }

    private func schedule(from requestedFrame: AVAudioFramePosition) throws {
        guard let file else {
            throw AudioPlaybackEngineError.noAssetLoaded
        }

        player.stop()
        scheduleGeneration &+= 1
        let generation = scheduleGeneration

        let startFrame = min(max(0, requestedFrame), fileLength)
        scheduledStartFrame = startFrame
        restingFrame = startFrame

        let remaining = max(0, fileLength - startFrame)
        guard remaining > 0 else {
            isScheduled = false
            return
        }

        let cappedFrameCount = min(remaining, AVAudioFramePosition(UInt32.max))
        let frameCount = AVAudioFrameCount(cappedFrameCount)
        isScheduled = true

        player.scheduleSegment(
            file,
            startingFrame: startFrame,
            frameCount: frameCount,
            at: nil,
            completionCallbackType: .dataPlayedBack
        ) { [weak self] _ in
            guard let self else { return }
            Task {
                await self.handleCompletion(generation: generation)
            }
        }
    }

    private func handleCompletion(generation: UInt64) {
        guard generation == scheduleGeneration else { return }
        restingFrame = fileLength
        didFinishNaturally = true
        isScheduled = false
        transport = .stopped
    }

    private func currentFrame() -> AVAudioFramePosition {
        guard transport == .playing,
              let renderTime = player.lastRenderTime,
              let playerTime = player.playerTime(forNodeTime: renderTime) else {
            return min(max(0, restingFrame), fileLength)
        }

        let frame = scheduledStartFrame + AVAudioFramePosition(playerTime.sampleTime)
        return min(max(0, frame), fileLength)
    }

    private func frame(forMilliseconds milliseconds: Int64) -> AVAudioFramePosition {
        guard sampleRate.isFinite, sampleRate > 0 else { return 0 }
        let frame = (Double(max(0, milliseconds)) / 1_000 * sampleRate).rounded(.towardZero)
        if frame >= Double(fileLength) { return fileLength }
        return AVAudioFramePosition(frame)
    }

    private func milliseconds(forFrame frame: AVAudioFramePosition) -> Int64 {
        guard sampleRate.isFinite, sampleRate > 0 else { return 0 }
        let milliseconds = (Double(max(0, frame)) / sampleRate * 1_000).rounded(.towardZero)
        return milliseconds >= Double(Int64.max) ? Int64.max : Int64(milliseconds)
    }
}
#endif


