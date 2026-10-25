import Foundation

public actor RoutedAudioPlaybackEngine: AudioPlaybackEngine {
    private enum Route {
        case file
        case midi
    }

    private let fileEngine: any AudioPlaybackEngine
    private let midiEngine: any AudioPlaybackEngine

    private var activeRoute: Route?
    private var volume: Float = 1
    private var pitchSemitones = 0
    private var tempoRatio = 1.0

    public init(
        fileEngine: any AudioPlaybackEngine,
        midiEngine: any AudioPlaybackEngine
    ) {
        self.fileEngine = fileEngine
        self.midiEngine = midiEngine
    }

    public func load(_ asset: AudioPlaybackAsset) async throws {
        let route: Route
        switch asset.mediaKind {
        case .mp3, .wav:
            route = .file
        case .midi, .kar:
            route = .midi
        default:
            throw AudioPlaybackEngineError.unsupportedMediaKind(
                asset.mediaKind
            )
        }

        if let activeRoute {
            await engine(for: activeRoute).stop()
        }

        let target = engine(for: route)
        try await target.load(asset)
        await target.setVolume(volume)
        await target.setPitch(semitones: pitchSemitones)
        await target.setTempo(ratio: tempoRatio)
        activeRoute = route
    }

    public func play() async throws {
        guard let activeRoute else {
            throw AudioPlaybackEngineError.noAssetLoaded
        }
        try await engine(for: activeRoute).play()
    }

    public func pause() async {
        guard let activeRoute else { return }
        await engine(for: activeRoute).pause()
    }

    public func stop() async {
        guard let activeRoute else { return }
        await engine(for: activeRoute).stop()
    }

    public func seek(toMilliseconds milliseconds: Int64) async throws {
        guard let activeRoute else {
            throw AudioPlaybackEngineError.noAssetLoaded
        }
        try await engine(for: activeRoute)
            .seek(toMilliseconds: milliseconds)
    }

    public func setVolume(_ value: Float) async {
        volume = min(max(value, 0), 1)
        await fileEngine.setVolume(volume)
        await midiEngine.setVolume(volume)
    }

    public func setPitch(semitones: Int) async {
        pitchSemitones = min(max(semitones, -12), 12)
        await fileEngine.setPitch(semitones: pitchSemitones)
        await midiEngine.setPitch(semitones: pitchSemitones)
    }

    public func setTempo(ratio: Double) async {
        tempoRatio = min(max(ratio, 0.5), 2.0)
        await fileEngine.setTempo(ratio: tempoRatio)
        await midiEngine.setTempo(ratio: tempoRatio)
    }

    public func snapshot() async -> AudioPlaybackSnapshot {
        guard let activeRoute else {
            return AudioPlaybackSnapshot(
                volume: volume,
                pitchSemitones: pitchSemitones,
                tempoRatio: tempoRatio
            )
        }
        return await engine(for: activeRoute).snapshot()
    }

    public func suspendForOnlinePlayback() async throws {
        try await fileEngine.suspendForOnlinePlayback()
        try await midiEngine.suspendForOnlinePlayback()
    }

    private func engine(
        for route: Route
    ) -> any AudioPlaybackEngine {
        switch route {
        case .file:
            return fileEngine
        case .midi:
            return midiEngine
        }
    }
}

