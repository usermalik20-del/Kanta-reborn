import Foundation

public struct AudioPlaybackAsset: Hashable, Sendable {
    public let songID: SongID
    public let url: URL
    public let mediaKind: MediaKind
    public let soundFontURL: URL?
    public let fallbackSoundFontURLs: [URL]

    public init(
        songID: SongID,
        url: URL,
        mediaKind: MediaKind,
        soundFontURL: URL? = nil,
        fallbackSoundFontURLs: [URL] = []
    ) {
        self.songID = songID
        self.url = url
        self.mediaKind = mediaKind
        self.soundFontURL = soundFontURL
        self.fallbackSoundFontURLs =
            fallbackSoundFontURLs
    }
}

public struct AudioPlaybackSnapshot: Equatable, Sendable {
    public var transport: TransportState
    public var songID: SongID?
    public var positionMilliseconds: Int64
    public var durationMilliseconds: Int64?
    public var volume: Float
    public var pitchSemitones: Int
    public var tempoRatio: Double
    /// Set only by the engine's natural completion callback, never by seek/stop.
    public var didFinishNaturally: Bool

    public init(
        transport: TransportState = .stopped,
        songID: SongID? = nil,
        positionMilliseconds: Int64 = 0,
        durationMilliseconds: Int64? = nil,
        volume: Float = 1,
        pitchSemitones: Int = 0,
        tempoRatio: Double = 1,
        didFinishNaturally: Bool = false
    ) {
        self.transport = transport
        self.songID = songID
        self.positionMilliseconds = max(0, positionMilliseconds)
        self.durationMilliseconds = durationMilliseconds.map { max(0, $0) }
        self.volume = min(max(volume, 0), 1)
        self.pitchSemitones = min(max(pitchSemitones, -12), 12)
        self.tempoRatio = min(max(tempoRatio, 0.5), 2.0)
        self.didFinishNaturally = didFinishNaturally
    }
}

public enum AudioPlaybackEngineError: Error, Equatable, Sendable {
    case noAssetLoaded
    case unsupportedMediaKind(MediaKind)
    case invalidDuration
    case missingSoundFont
}

public protocol AudioPlaybackEngine: Sendable {
    func load(_ asset: AudioPlaybackAsset) async throws
    func play() async throws
    func pause() async
    func stop() async
    func seek(toMilliseconds: Int64) async throws
    func setVolume(_ value: Float) async
    func setPitch(semitones: Int) async
    func setTempo(ratio: Double) async
    func snapshot() async -> AudioPlaybackSnapshot
    /// Suspend idle rendering without changing position, transport or completion identity.
    func suspendForOnlinePlayback() async throws
}

extension AudioPlaybackEngine {
    public func suspendForOnlinePlayback() async throws {}
}


