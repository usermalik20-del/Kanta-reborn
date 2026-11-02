import Foundation

public protocol KaraokeLibraryAccess: Sendable {
    func song(id: SongID) async throws -> LibrarySong?
    func playbackAsset(for songID: SongID) async throws -> AudioPlaybackAsset
}

public enum LocalKaraokeControllerError: Error, Equatable, Sendable {
    case songNotFound(SongID)
    case catalogOnlySong(SongID)
    case queueEmpty
    case historyEmpty
}

extension LocalKaraokeControllerError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case let .songNotFound(songID):
            return "The selected song is no longer available (\(songID.rawValue))."
        case .catalogOnlySong:
            return "This song is listed in your library index, but playable media is not available."
        case .queueEmpty:
            return "There are no songs waiting in the queue."
        case .historyEmpty:
            return "There is no previous song to return to."
        }
    }
}

public actor LocalKaraokeController: KaraokeController {
    private let engine: any AudioPlaybackEngine
    private let library: any KaraokeLibraryAccess
    private let audioOwnership: KaraokeAudioOwnership?
    private let partySFX: (any PartySFXPlayback)?

    private var queue = PlaybackQueue()
    private var currentItem: QueueItem?
    private var history: [QueueItem] = []
    private var playbackID: UUID?
    private var lastCompletion: KaraokeCompletion?
    private var failedAutoAdvanceItemID: UUID?
    private var playbackError: String?
    private var needsPlaybackReload = false

    // Actor isolation alone does not make an async load/play transaction atomic.
    // Serialize commands and snapshots across suspension points without blocking a thread.
    private struct OperationWaiter {
        let id: UUID
        let continuation: CheckedContinuation<Void, Error>
    }

    private var operationInProgress = false
    private var operationWaiters: [OperationWaiter] = []
    private var lastPublishedSnapshot = KaraokeSnapshot()

    /// Serializes across await boundaries and promptly removes canceled waiters.
    private func acquireOperation() async throws {
        try Task.checkCancellation()
        if !operationInProgress {
            operationInProgress = true
            return
        }
        let id = UUID()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation {
                (continuation: CheckedContinuation<Void, Error>) in
                operationWaiters.append(
                    OperationWaiter(id: id, continuation: continuation)
                )
            }
        } onCancel: {
            Task { await self.cancelWaitingOperation(id) }
        }
        // Ownership can transfer just as cancellation arrives.
        if Task.isCancelled {
            releaseOperation()
            throw CancellationError()
        }
    }

    private func cancelWaitingOperation(_ id: UUID) {
        guard let index = operationWaiters.firstIndex(
            where: { $0.id == id }
        ) else { return }
        operationWaiters.remove(at: index).continuation.resume(
            throwing: CancellationError()
        )
    }

    private func releaseOperation() {
        if operationWaiters.isEmpty {
            operationInProgress = false
        } else {
            operationWaiters.removeFirst().continuation.resume()
        }
    }

    public init(
        engine: any AudioPlaybackEngine,
        library: any KaraokeLibraryAccess,
        partySFX: (any PartySFXPlayback)? = nil,
        audioOwnership: KaraokeAudioOwnership? = nil
    ) {
        self.engine = engine
        self.library = library
        self.partySFX = partySFX
        self.audioOwnership = audioOwnership
    }

    public func snapshot() async -> KaraokeSnapshot {
        do {
            try await acquireOperation()
        } catch {
            // Canceled polls cannot fabricate an empty queue.
            return lastPublishedSnapshot
        }
        defer { releaseOperation() }
        var audio = await engine.snapshot()

        if !needsPlaybackReload, audio.didFinishNaturally,
           let currentItem, let playbackID,
           audio.songID == currentItem.song.id,
           lastCompletion?.playbackID != playbackID {
            lastCompletion = KaraokeCompletion(playbackID: playbackID, song: currentItem.song)
        }

        if shouldAutoAdvance(audio),
           let next = queue.items.first,
           failedAutoAdvanceItemID != next.id {
            do {
                // Natural completion is controller-owned, not tied to a view poll's lifetime.
                try await startNext(shouldPlay: true, cancellationSensitive: false)
                audio = await engine.snapshot()
            } catch is CancellationError {
                // A canceled observer must not poison the reservation; a later snapshot retries.
                return lastPublishedSnapshot
            } catch {
                // Keep the reservation, surface the failure, and avoid retrying
                // expensive/broken media on every Host/Remote snapshot poll.
                failedAutoAdvanceItemID = next.id
                playbackError = error.localizedDescription
                audio = await engine.snapshot()
            }
        }

        // A failed adapter load/play may already have replaced its internal asset.
        // Never project that partially loaded song as the committed current song.
        if needsPlaybackReload {
            audio = AudioPlaybackSnapshot(songID: currentItem?.song.id,
                volume: audio.volume, pitchSemitones: audio.pitchSemitones,
                tempoRatio: audio.tempoRatio)
        }
        let published = KaraokeSnapshot(
            transport: audio.transport,
            nowPlaying: currentItem?.song,
            nowSinging: currentItem?.requestedBy,
            queue: queue.items,
            positionMilliseconds: audio.positionMilliseconds,
            durationMilliseconds: audio.durationMilliseconds,
            volume: audio.volume,
            pitchSemitones: audio.pitchSemitones,
            tempoRatio: audio.tempoRatio,
            playbackID: playbackID,
            lastCompletion: lastCompletion,
            playbackError: playbackError
        )
        lastPublishedSnapshot = published
        return published
    }

    private func shouldAutoAdvance(
        _ audio: AudioPlaybackSnapshot
    ) -> Bool {
        !needsPlaybackReload && currentItem != nil && audio.didFinishNaturally
            && audio.songID == currentItem?.song.id
    }

    public func send(_ command: KaraokeCommand) async throws {
        try await acquireOperation()
        defer { releaseOperation() }
        try Task.checkCancellation()
        switch command {
        case .play:
            if currentItem == nil {
                try await startNext(shouldPlay: true)
            } else {
                let before = await engine.snapshot()
                try Task.checkCancellation()
                if needsPlaybackReload, let currentItem {
                    let asset = try await library.playbackAsset(for: currentItem.song.id)
                    try Task.checkCancellation()
                    try await prepareAsset(asset, shouldPlay: true)
                    try await checkCancellationAfterPreparation()
                } else {
                    try await audioOwnership?.prepareNativeAudio()
                    try Task.checkCancellation()
                    do {
                        try await engine.play()
                        try Task.checkCancellation()
                    } catch {
                        await engine.pause()
                        throw error
                    }
                }
                if before.transport == .stopped {
                    playbackID = UUID()
                }
                playbackError = nil
                failedAutoAdvanceItemID = nil
            }

        case .pause:
            await engine.pause()

        case .stop:
            await engine.stop()

        case .next:
            try await startFollowingQueueItem()

        case .previous:
            try await startPrevious()

        case let .seek(milliseconds):
            let wasPlaying = await engine.snapshot().transport == .playing
            try Task.checkCancellation()
            if wasPlaying {
                try await audioOwnership?.prepareNativeAudio()
                try Task.checkCancellation()
            }
            try await engine.seek(toMilliseconds: milliseconds)

        case let .setVolume(value):
            await engine.setVolume(value)

        case let .setPitch(semitones):
            await engine.setPitch(semitones: semitones)

        case let .setTempo(ratio):
            await engine.setTempo(ratio: ratio)

        case let .enqueue(songID):
            let resolvedSong = try await library.song(id: songID)
            try Task.checkCancellation()
            guard let song = resolvedSong else {
                throw LocalKaraokeControllerError.songNotFound(songID)
            }
            guard song.isPlayable else {
                throw LocalKaraokeControllerError.catalogOnlySong(songID)
            }
            queue.enqueue(song)

        case let .enqueueForSinger(songID, singer):
            let resolvedSong = try await library.song(id: songID)
            try Task.checkCancellation()
            guard let song = resolvedSong else {
                throw LocalKaraokeControllerError.songNotFound(songID)
            }
            guard song.isPlayable else {
                throw LocalKaraokeControllerError.catalogOnlySong(songID)
            }
            let clean = String(singer.trimmingCharacters(
                in: .whitespacesAndNewlines
            ).prefix(60))
            queue.enqueue(song, requestedBy: clean.isEmpty ? nil : clean)

        case let .moveQueueItem(id, destination):
            _ = queue.move(id: id, before: destination)

        case let .removeQueueItem(id):
            _ = queue.remove(id: id)

        case let .triggerSFX(sound):
            try await audioOwnership?.prepareNativeAudio()
            try Task.checkCancellation()
            try await partySFX?.trigger(sound)

        case .stopSFX:
            await partySFX?.stopAll()

        case let .setSFXVolume(level):
            await partySFX?.setSFXVolume(level)
        }
    }

    /// Shares the command gate with local and LAN starts, including decoder preparation.
    /// Opening online playback never sends Pause/Stop or consumes a reservation.
    public func claimOnlineAudio(_ id: UUID,
        retire: @escaping @MainActor @Sendable () -> Void
    ) async throws -> Bool {
        try await acquireOperation()
        defer { releaseOperation() }
        guard let audioOwnership,
              await engine.snapshot().transport != .playing else { return false }
        await partySFX?.stopAll()
        try await engine.suspendForOnlinePlayback()
        try Task.checkCancellation()
        let claimed = try await audioOwnership.claimOnlineAudio(id, retire: retire)
        if Task.isCancelled {
            if claimed { await audioOwnership.releaseOnlineAudio(id) }
            throw CancellationError()
        }
        return claimed
    }

    /// Explicit local Play action. Existing FIFO reservations are preserved.
    /// Uses the same operation gate and preparation transaction as Next.
    public func playNow(songID: SongID) async throws {
        try await acquireOperation()
        defer { releaseOperation() }
        guard let song = try await library.song(id: songID) else {
            throw LocalKaraokeControllerError.songNotFound(songID)
        }
        guard song.isPlayable else { throw LocalKaraokeControllerError.catalogOnlySong(songID) }
        let asset = try await library.playbackAsset(for: songID)
        try Task.checkCancellation()
        try await prepareAsset(asset, shouldPlay: true)
        try await checkCancellationAfterPreparation()
        if let currentItem { history.append(currentItem) }
        currentItem = QueueItem(song: song)
        playbackID = UUID()
        playbackError = nil
        failedAutoAdvanceItemID = nil
    }

    private func startFollowingQueueItem() async throws {
        try await startNext(shouldPlay: true)
    }

    private func startNext(
        shouldPlay: Bool,
        cancellationSensitive: Bool = true
    ) async throws {
        guard let next = queue.items.first else {
            throw LocalKaraokeControllerError.queueEmpty
        }

        let asset = try await library.playbackAsset(for: next.song.id)
        if cancellationSensitive { try Task.checkCancellation() }
        try await prepareAsset(
            asset,
            shouldPlay: shouldPlay,
            cancellationSensitive: cancellationSensitive
        )
        if cancellationSensitive {
            try await checkCancellationAfterPreparation()
        }

        // Commit queue state only after preparation/play succeeds.
        // Failed or malformed media therefore remains visible and
        // retryable/removable instead of being silently discarded.
        if let currentItem { history.append(currentItem) }
        _ = queue.remove(id: next.id)
        currentItem = next
        playbackID = UUID()
        playbackError = nil
        failedAutoAdvanceItemID = nil
    }

    private func prepareAsset(
        _ asset: AudioPlaybackAsset,
        shouldPlay: Bool,
        cancellationSensitive: Bool = true
    ) async throws {
        do {
            try await audioOwnership?.prepareNativeAudio()
            if cancellationSensitive { try Task.checkCancellation() }
            try await engine.load(asset)
            if cancellationSensitive { try Task.checkCancellation() }
            if shouldPlay {
                try await engine.play()
                if cancellationSensitive { try Task.checkCancellation() }
            }
            needsPlaybackReload = false
        } catch {
            await engine.stop()
            needsPlaybackReload = true
            throw error
        }
    }

    /// Retires a prepared backend if cancellation arrives before the controller commits it.
    private func checkCancellationAfterPreparation() async throws {
        do {
            try Task.checkCancellation()
        } catch {
            await engine.stop()
            needsPlaybackReload = true
            throw error
        }
    }

    private func startPrevious() async throws {
        guard let previous = history.last else {
            throw LocalKaraokeControllerError.historyEmpty
        }

        let asset = try await library.playbackAsset(for: previous.song.id)
        try Task.checkCancellation()
        try await prepareAsset(asset, shouldPlay: true)
        try await checkCancellationAfterPreparation()

        history.removeLast()
        if let currentItem {
            queue.prepend(currentItem)
        }
        currentItem = previous
        playbackID = UUID()
        playbackError = nil
        failedAutoAdvanceItemID = nil
    }
}



