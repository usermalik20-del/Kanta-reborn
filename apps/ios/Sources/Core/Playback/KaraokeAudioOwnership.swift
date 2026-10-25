import Foundation

/// Audio arbitration only. Local transport and reservations remain controller-owned.
/// Online claims must run inside LocalKaraokeController's serialized operation gate.
@MainActor
public final class KaraokeAudioOwnership {
    private var onlineID: UUID?
    private var retireOnline: (@MainActor @Sendable () -> Void)?
    public private(set) var microphoneBusy = false
    public private(set) var isForeground = true
    public var onlineEntryAllowed: @MainActor () -> Bool = { true }
    public var stopAuxiliaryAudio: (@MainActor () -> Void)?
    public var configurePlaybackSession: (@MainActor () throws -> Void)?

    public init() {}
    public var onlineActive: Bool { onlineID != nil }
    public func ownsOnlineAudio(_ id: UUID) -> Bool { onlineID == id }

    public func setForeground(_ active: Bool) {
        isForeground = active
        if !active { retire() }
    }

    /// Called synchronously before requesting permission, not after capture starts.
    public func setMicrophoneBusy(_ busy: Bool) {
        microphoneBusy = busy
        if busy { retire() }
    }

    public func prepareNativeAudio() throws {
        retire()
        // Capture owns playAndRecord while active; never overwrite its routing.
        if !microphoneBusy { try configurePlaybackSession?() }
    }

    public func claimOnlineAudio(_ id: UUID,
        retire: @escaping @MainActor @Sendable () -> Void
    ) throws -> Bool {
        guard isForeground, !microphoneBusy, onlineEntryAllowed() else { return false }
        self.retire()
        stopAuxiliaryAudio?()
        try configurePlaybackSession?()
        onlineID = id
        retireOnline = retire
        return true
    }

    public func releaseOnlineAudio(_ id: UUID) {
        guard onlineID == id else { return }
        retire()
    }

    private func retire() {
        let callback = retireOnline
        onlineID = nil
        retireOnline = nil
        callback?()
    }
}
