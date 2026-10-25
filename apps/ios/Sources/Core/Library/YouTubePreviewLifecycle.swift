import Foundation

/// Presentation identity only; local transport and reservations remain independent.
public struct YouTubePreviewLifecycle: Sendable {
    public struct Session: Identifiable, Equatable, Sendable {
        public let id: UUID
        public let reference: YouTubeVideoReference
    }

    public private(set) var active: Session?
    public init() {}

    @discardableResult
    public mutating func open(_ reference: YouTubeVideoReference) -> Session {
        let session = Session(id: UUID(), reference: reference)
        active = session
        return session
    }

    /// A disposed player's late failure must not close its replacement.
    @discardableResult
    public mutating func close(_ id: UUID) -> Bool {
        guard active?.id == id else { return false }
        active = nil
        return true
    }

    public mutating func close() { active = nil }
}
