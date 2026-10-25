import Foundation

public struct SongID: RawRepresentable, Hashable, Codable, Sendable, CustomStringConvertible {
    public let rawValue: String

    public init(rawValue: String) {
        self.rawValue = rawValue
    }

    public var description: String { rawValue }
}

public enum MediaKind: String, Codable, CaseIterable, Sendable {
    case midi
    case kar
    case mp3
    case wav
    case legacyContainer
    case unknown
}

public struct LibrarySong: Identifiable, Hashable, Codable, Sendable {
    public let id: SongID
    public var code: String?
    public var title: String
    public var artist: String?
    public var mediaKind: MediaKind

    /// A browsable title is not automatically a validated playback asset.
    /// Standalone/unsupported indexed libraries are catalog-only.
    /// Older saved and Remote protocol-v2 payloads default to playable.
    public var isPlayable: Bool

    public init(
        id: SongID,
        code: String? = nil,
        title: String,
        artist: String? = nil,
        mediaKind: MediaKind,
        isPlayable: Bool = true
    ) {
        self.id = id
        self.code = code
        self.title = title
        self.artist = artist
        self.mediaKind = mediaKind
        self.isPlayable = isPlayable
    }

    private enum CodingKeys: String, CodingKey {
        case id, code, title, artist, mediaKind, isPlayable
    }

    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(SongID.self, forKey: .id)
        code = try values.decodeIfPresent(String.self, forKey: .code)
        title = try values.decode(String.self, forKey: .title)
        artist = try values.decodeIfPresent(String.self, forKey: .artist)
        mediaKind = try values.decode(MediaKind.self, forKey: .mediaKind)
        isPlayable = try values.decodeIfPresent(Bool.self, forKey: .isPlayable) ?? true
    }
}

/// A reservation's media origin is explicit; a web reference is never a local file.
public enum QueueMediaSource: Hashable, Codable, Sendable {
    case local
    case youtube(YouTubeVideoReference)
}

public struct QueueItem: Identifiable, Hashable, Codable, Sendable {
    public let id: UUID
    public let song: LibrarySong
    public let requestedBy: String?
    public let source: QueueMediaSource

    public init(id: UUID = UUID(), song: LibrarySong, requestedBy: String? = nil, source: QueueMediaSource = .local) {
        self.id = id
        self.song = song
        self.requestedBy = requestedBy
        self.source = source
    }

    private enum CodingKeys: String, CodingKey { case id, song, requestedBy, source }

    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decode(UUID.self, forKey: .id)
        song = try values.decode(LibrarySong.self, forKey: .song)
        requestedBy = try values.decodeIfPresent(String.self, forKey: .requestedBy)
        source = try values.decodeIfPresent(QueueMediaSource.self, forKey: .source) ?? .local
    }
}

