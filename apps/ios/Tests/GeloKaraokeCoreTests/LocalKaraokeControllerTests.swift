import Foundation
import Testing
@testable import GeloKaraokeCore

@Test func localControllerDrivesQueueAndHistory() async throws {
    let first = LibrarySong(
        id: SongID(rawValue: "one"),
        title: "One",
        mediaKind: .wav
    )
    let second = LibrarySong(
        id: SongID(rawValue: "two"),
        title: "Two",
        mediaKind: .mp3
    )

    let library = FakeKaraokeLibrary(songs: [first, second])
    let engine = FakeAudioPlaybackEngine()
    let controller = LocalKaraokeController(
        engine: engine,
        library: library
    )

    try await controller.send(.enqueue(songID: first.id))
    try await controller.send(.enqueue(songID: second.id))
    try await controller.send(.play)

    var snapshot = await controller.snapshot()
    #expect(snapshot.transport == .playing)
    #expect(snapshot.nowPlaying?.id == first.id)
    #expect(snapshot.queue.map(\.song.id) == [second.id])

    try await controller.send(.next)
    snapshot = await controller.snapshot()
    #expect(snapshot.nowPlaying?.id == second.id)
    #expect(snapshot.queue.isEmpty)

    try await controller.send(.previous)
    snapshot = await controller.snapshot()
    #expect(snapshot.nowPlaying?.id == first.id)
    #expect(snapshot.queue.map(\.song.id) == [second.id])
}

@Test func localControllerDoesNotDiscardCurrentSongWhenNextQueueIsEmpty() async throws {
    let song = LibrarySong(
        id: SongID(rawValue: "one"),
        title: "One",
        mediaKind: .wav
    )

    let controller = LocalKaraokeController(
        engine: FakeAudioPlaybackEngine(),
        library: FakeKaraokeLibrary(songs: [song])
    )

    try await controller.send(.enqueue(songID: song.id))
    try await controller.send(.play)

    await #expect(throws: LocalKaraokeControllerError.queueEmpty) {
        try await controller.send(.next)
    }

    let snapshot = await controller.snapshot()
    #expect(snapshot.nowPlaying?.id == song.id)
}


@Test func localControllerAutoAdvancesReservedSongAfterNaturalCompletion() async throws {
    let first = LibrarySong(
        id: SongID(rawValue: "first"),
        title: "First",
        mediaKind: .wav
    )
    let second = LibrarySong(
        id: SongID(rawValue: "second"),
        title: "Second",
        mediaKind: .mp3
    )

    let engine = FakeAudioPlaybackEngine()
    let controller = LocalKaraokeController(
        engine: engine,
        library: FakeKaraokeLibrary(songs: [first, second])
    )

    try await controller.send(.enqueue(songID: first.id))
    try await controller.send(.enqueue(songID: second.id))
    try await controller.send(.play)

    await engine.finishCurrentNaturally()

    let snapshot = await controller.snapshot()
    #expect(snapshot.transport == .playing)
    #expect(snapshot.nowPlaying?.id == second.id)
    #expect(snapshot.queue.isEmpty)
}

@Test func localControllerPropagatesPlaybackControls() async throws {
    let song = LibrarySong(
        id: SongID(rawValue: "song"),
        title: "Song",
        mediaKind: .wav
    )

    let library = FakeKaraokeLibrary(songs: [song])
    let engine = FakeAudioPlaybackEngine()
    let controller = LocalKaraokeController(
        engine: engine,
        library: library
    )

    try await controller.send(.enqueue(songID: song.id))
    try await controller.send(.play)
    try await controller.send(.seek(milliseconds: 12_345))
    try await controller.send(.setVolume(0.25))
    try await controller.send(.setPitch(semitones: -2))
    try await controller.send(.setTempo(ratio: 1.1))

    let snapshot = await controller.snapshot()
    #expect(snapshot.positionMilliseconds == 12_345)
    #expect(snapshot.volume == 0.25)
    #expect(snapshot.pitchSemitones == -2)
    #expect(snapshot.tempoRatio == 1.1)
}

@Test func localControllerRejectsUnknownSong() async {
    let controller = LocalKaraokeController(
        engine: FakeAudioPlaybackEngine(),
        library: FakeKaraokeLibrary(songs: [])
    )

    let missing = SongID(rawValue: "missing")
    await #expect(
        throws: LocalKaraokeControllerError.songNotFound(missing)
    ) {
        try await controller.send(.enqueue(songID: missing))
    }
}


@Test func localControllerKeepsQueuedSongWhenInitialPlayFails() async throws {
    let song = LibrarySong(
        id: SongID(rawValue: "broken-play"),
        title: "Broken Play",
        mediaKind: .wav
    )

    let controller = LocalKaraokeController(
        engine: FakeAudioPlaybackEngine(
            failingPlaySongIDs: [song.id]
        ),
        library: FakeKaraokeLibrary(songs: [song])
    )

    try await controller.send(.enqueue(songID: song.id))

    await #expect(
        throws: TestPlaybackError.forcedPlay(song.id)
    ) {
        try await controller.send(.play)
    }

    let snapshot = await controller.snapshot()
    #expect(snapshot.nowPlaying == nil)
    #expect(snapshot.queue.map(\.song.id) == [song.id])
}

@Test func localControllerKeepsQueuedSongWhenAssetResolutionFails() async throws {
    let song = LibrarySong(
        id: SongID(rawValue: "broken-asset"),
        title: "Broken Asset",
        mediaKind: .midi
    )

    let controller = LocalKaraokeController(
        engine: FakeAudioPlaybackEngine(),
        library: FakeKaraokeLibrary(
            songs: [song],
            failingAssetSongIDs: [song.id]
        )
    )

    try await controller.send(.enqueue(songID: song.id))

    await #expect(
        throws: TestPlaybackError.forcedAsset(song.id)
    ) {
        try await controller.send(.play)
    }

    let snapshot = await controller.snapshot()
    #expect(snapshot.nowPlaying == nil)
    #expect(snapshot.queue.map(\.song.id) == [song.id])
}

@Test func localControllerKeepsCurrentAndNextSongWhenNextLoadFails() async throws {
    let first = LibrarySong(
        id: SongID(rawValue: "current"),
        title: "Current",
        mediaKind: .wav
    )
    let second = LibrarySong(
        id: SongID(rawValue: "broken-next"),
        title: "Broken Next",
        mediaKind: .mp3
    )

    let controller = LocalKaraokeController(
        engine: FakeAudioPlaybackEngine(
            failingLoadSongIDs: [second.id]
        ),
        library: FakeKaraokeLibrary(songs: [first, second])
    )

    try await controller.send(.enqueue(songID: first.id))
    try await controller.send(.enqueue(songID: second.id))
    try await controller.send(.play)

    await #expect(
        throws: TestPlaybackError.forcedLoad(second.id)
    ) {
        try await controller.send(.next)
    }

    let snapshot = await controller.snapshot()
    #expect(snapshot.nowPlaying?.id == first.id)
    #expect(snapshot.queue.map(\.song.id) == [second.id])
}

private enum TestPlaybackError: Error, Equatable, Sendable {
    case forcedAsset(SongID)
    case forcedLoad(SongID)
    case forcedPlay(SongID)
}

private actor FakeAudioPlaybackEngine: AudioPlaybackEngine {
    private var state = AudioPlaybackSnapshot()
    private let failingLoadSongIDs: Set<SongID>
    private let failingPlaySongIDs: Set<SongID>

    init(
        failingLoadSongIDs: Set<SongID> = [],
        failingPlaySongIDs: Set<SongID> = []
    ) {
        self.failingLoadSongIDs = failingLoadSongIDs
        self.failingPlaySongIDs = failingPlaySongIDs
    }

    private var audioOwnership: KaraokeAudioOwnership?
    private var nativeStartOwnership: [Bool] = []
    func checkOwnership(_ value: KaraokeAudioOwnership) { audioOwnership = value }
    func ownershipAtNativeStarts() -> [Bool] { nativeStartOwnership }
    private var suspendCount = 0
    func numberOfSuspensions() -> Int { suspendCount }
    func suspendForOnlinePlayback() async throws { suspendCount += 1 }
    private func recordOwnership() async {
        if let audioOwnership { nativeStartOwnership.append(await audioOwnership.onlineActive) }
    }

    private var blockLoad = false
    private var loadWaiter: CheckedContinuation<Void, Never>?
    func blockNextLoad() { blockLoad = true }
    func isLoadBlocked() -> Bool { loadWaiter != nil }
    func releaseLoad() { loadWaiter?.resume(); loadWaiter = nil }
    private var loadCount = 0
    private var blockSnapshot = false
    private var snapshotWaiter: CheckedContinuation<Void, Never>?
    func numberOfLoads() -> Int { loadCount }
    func blockNextSnapshot() { blockSnapshot = true }
    func isSnapshotBlocked() -> Bool { snapshotWaiter != nil }
    func releaseSnapshot() {
        snapshotWaiter?.resume()
        snapshotWaiter = nil
    }

    func load(_ asset: AudioPlaybackAsset) async throws {
        await recordOwnership()
        if blockLoad {
            blockLoad = false
            await withCheckedContinuation { loadWaiter = $0 }
        }
        loadCount += 1
        await Task.yield()
        if failingLoadSongIDs.contains(asset.songID) {
            throw TestPlaybackError.forcedLoad(asset.songID)
        }

        state = AudioPlaybackSnapshot(
            transport: .stopped,
            songID: asset.songID,
            positionMilliseconds: 0,
            durationMilliseconds: 60_000,
            volume: state.volume,
            pitchSemitones: state.pitchSemitones,
            tempoRatio: state.tempoRatio
        )
    }

    func play() async throws {
        await recordOwnership()
        guard let songID = state.songID else {
            throw AudioPlaybackEngineError.noAssetLoaded
        }
        if failingPlaySongIDs.contains(songID) {
            throw TestPlaybackError.forcedPlay(songID)
        }
        state.didFinishNaturally = false
        state.transport = .playing
    }

    func pause() async {
        state.transport = .paused
    }

    func stop() async {
        state.transport = .stopped
        state.positionMilliseconds = 0
    }

    func seek(toMilliseconds milliseconds: Int64) async throws {
        guard state.songID != nil else {
            throw AudioPlaybackEngineError.noAssetLoaded
        }
        state.positionMilliseconds = max(0, milliseconds)
    }

    func setVolume(_ value: Float) async {
        state.volume = min(max(value, 0), 1)
    }

    func setPitch(semitones: Int) async {
        state.pitchSemitones = min(max(semitones, -12), 12)
    }

    func setTempo(ratio: Double) async {
        state.tempoRatio = min(max(ratio, 0.5), 2)
    }

    func finishCurrentNaturally() {
        guard let duration = state.durationMilliseconds else { return }
        state.didFinishNaturally = true
        state.positionMilliseconds = duration
        state.transport = .stopped
    }

    func snapshot() async -> AudioPlaybackSnapshot {
        if blockSnapshot {
            blockSnapshot = false
            await withCheckedContinuation {
                (continuation: CheckedContinuation<Void, Never>) in
                snapshotWaiter = continuation
            }
        }
        return state
    }
}

private struct FakeKaraokeLibrary: KaraokeLibraryAccess {
    private let songsByID: [SongID: LibrarySong]
    private let failingAssetSongIDs: Set<SongID>

    init(
        songs: [LibrarySong],
        failingAssetSongIDs: Set<SongID> = []
    ) {
        songsByID = Dictionary(
            uniqueKeysWithValues: songs.map { ($0.id, $0) }
        )
        self.failingAssetSongIDs = failingAssetSongIDs
    }

    func song(id: SongID) async throws -> LibrarySong? {
        songsByID[id]
    }

    func playbackAsset(for songID: SongID) async throws -> AudioPlaybackAsset {
        if failingAssetSongIDs.contains(songID) {
            throw TestPlaybackError.forcedAsset(songID)
        }

        guard let song = songsByID[songID] else {
            throw LocalKaraokeControllerError.songNotFound(songID)
        }

        return AudioPlaybackAsset(
            songID: songID,
            url: URL(fileURLWithPath: "/tmp/\(songID.rawValue)"),
            mediaKind: song.mediaKind
        )
    }
}

private actor BlockingKaraokeLibrary: KaraokeLibraryAccess {
    private enum Lookup: Equatable { case song, asset }
    private let songsByID: [SongID: LibrarySong]
    private var blockedLookup: Lookup?
    private var waiter: CheckedContinuation<Void, Never>?

    init(songs: [LibrarySong]) {
        songsByID = Dictionary(uniqueKeysWithValues: songs.map { ($0.id, $0) })
    }

    func blockNextSongLookup() { blockedLookup = .song }
    func blockNextAssetLookup() { blockedLookup = .asset }
    func isBlocked() -> Bool { waiter != nil }

    func releaseBlockedLookup() {
        waiter?.resume()
        waiter = nil
    }

    private func waitIfRequested(_ lookup: Lookup) async {
        guard blockedLookup == lookup else { return }
        blockedLookup = nil
        await withCheckedContinuation {
            (continuation: CheckedContinuation<Void, Never>) in
            waiter = continuation
        }
    }

    func song(id: SongID) async throws -> LibrarySong? {
        await waitIfRequested(.song)
        return songsByID[id]
    }

    func playbackAsset(for songID: SongID) async throws -> AudioPlaybackAsset {
        await waitIfRequested(.asset)
        guard let song = songsByID[songID] else {
            throw LocalKaraokeControllerError.songNotFound(songID)
        }
        return AudioPlaybackAsset(
            songID: songID,
            url: URL(fileURLWithPath: "/tmp/\(songID.rawValue)"),
            mediaKind: song.mediaKind
        )
    }
}

@Test func canceledEnqueueDoesNotMutateQueueAfterLibraryLookup() async throws {
    let song = LibrarySong(id: SongID(rawValue: "cancel-enqueue"), title: "Cancel", mediaKind: .wav)
    let library = BlockingKaraokeLibrary(songs: [song])
    let controller = LocalKaraokeController(
        engine: FakeAudioPlaybackEngine(), library: library
    )
    await library.blockNextSongLookup()
    let pending = Task { try await controller.send(.enqueue(songID: song.id)) }
    for _ in 0..<1_000 {
        if await library.isBlocked() { break }
        await Task.yield()
    }
    #expect(await library.isBlocked())
    pending.cancel()
    await library.releaseBlockedLookup()
    await #expect(throws: CancellationError.self) { try await pending.value }
    let snapshot = await controller.snapshot()
    #expect(snapshot.queue.isEmpty)
}

@Test func canceledNextDoesNotStartResolvedAssetOrConsumeReservation() async throws {
    let first = LibrarySong(id: SongID(rawValue: "cancel-current"), title: "Current", mediaKind: .wav)
    let second = LibrarySong(id: SongID(rawValue: "cancel-next"), title: "Next", mediaKind: .wav)
    let library = BlockingKaraokeLibrary(songs: [first, second])
    let engine = FakeAudioPlaybackEngine()
    let controller = LocalKaraokeController(engine: engine, library: library)
    try await controller.send(.enqueue(songID: first.id))
    try await controller.send(.enqueue(songID: second.id))
    try await controller.send(.play)
    let before = await controller.snapshot()

    await library.blockNextAssetLookup()
    let pending = Task { try await controller.send(.next) }
    for _ in 0..<1_000 {
        if await library.isBlocked() { break }
        await Task.yield()
    }
    #expect(await library.isBlocked())
    pending.cancel()
    await library.releaseBlockedLookup()
    await #expect(throws: CancellationError.self) { try await pending.value }

    let after = await controller.snapshot()
    #expect(after.nowPlaying?.id == first.id)
    #expect(after.queue.map(\.song.id) == [second.id])
    #expect(after.playbackID == before.playbackID)
    #expect(await engine.numberOfLoads() == 1)
}

@Test func canceledNextDuringNativePreparationRetiresUncommittedAsset() async throws {
    let first = LibrarySong(id: SongID(rawValue: "cancel-load-current"), title: "Current", mediaKind: .wav)
    let second = LibrarySong(id: SongID(rawValue: "cancel-load-next"), title: "Next", mediaKind: .wav)
    let engine = FakeAudioPlaybackEngine()
    let controller = LocalKaraokeController(
        engine: engine, library: FakeKaraokeLibrary(songs: [first, second])
    )
    try await controller.send(.enqueue(songID: first.id))
    try await controller.send(.enqueue(songID: second.id))
    try await controller.send(.play)
    let before = await controller.snapshot()

    await engine.blockNextLoad()
    let pending = Task { try await controller.send(.next) }
    for _ in 0..<1_000 {
        if await engine.isLoadBlocked() { break }
        await Task.yield()
    }
    #expect(await engine.isLoadBlocked())
    pending.cancel()
    await engine.releaseLoad()
    await #expect(throws: CancellationError.self) { try await pending.value }

    let interrupted = await controller.snapshot()
    #expect(interrupted.nowPlaying?.id == first.id)
    #expect(interrupted.queue.map(\.song.id) == [second.id])
    #expect(interrupted.playbackID == before.playbackID)
    #expect(interrupted.transport == .stopped)

    try await controller.send(.play)
    let resumed = await controller.snapshot()
    #expect(resumed.nowPlaying?.id == first.id)
    #expect(resumed.transport == .playing)
    #expect(resumed.queue.map(\.song.id) == [second.id])
}


@Test func canceledSnapshotPollDoesNotAbortNaturalAutoAdvance() async throws {
    let first = LibrarySong(id: SongID(rawValue: "cancel-poll-current"), title: "Current", mediaKind: .wav)
    let second = LibrarySong(id: SongID(rawValue: "cancel-poll-next"), title: "Next", mediaKind: .wav)
    let library = BlockingKaraokeLibrary(songs: [first, second])
    let engine = FakeAudioPlaybackEngine()
    let controller = LocalKaraokeController(engine: engine, library: library)
    try await controller.send(.enqueue(songID: first.id))
    try await controller.send(.enqueue(songID: second.id))
    try await controller.send(.play)
    await engine.finishCurrentNaturally()

    await library.blockNextAssetLookup()
    let poll = Task { await controller.snapshot() }
    for _ in 0..<1_000 {
        if await library.isBlocked() { break }
        await Task.yield()
    }
    #expect(await library.isBlocked())
    poll.cancel()
    await library.releaseBlockedLookup()

    let snapshot = await poll.value
    #expect(snapshot.nowPlaying?.id == second.id)
    #expect(snapshot.queue.isEmpty)
    #expect(snapshot.transport == .playing)
}



@Test func concurrentSnapshotsAdvanceExactlyOneReservation() async throws {
    let song = LibrarySong(id: SongID(rawValue: "repeat"), title: "Repeat", mediaKind: .wav)
    let engine = FakeAudioPlaybackEngine()
    let controller = LocalKaraokeController(engine: engine, library: FakeKaraokeLibrary(songs: [song]))
    for _ in 0..<3 { try await controller.send(.enqueue(songID: song.id)) }
    try await controller.send(.play)
    let initial = await controller.snapshot()
    await engine.finishCurrentNaturally()
    await withTaskGroup(of: KaraokeSnapshot.self) { group in
        for _ in 0..<20 { group.addTask { await controller.snapshot() } }
        for await snapshot in group {
            #expect(snapshot.queue.count == 1)
            #expect(snapshot.playbackID != initial.playbackID)
            #expect(snapshot.lastCompletion?.playbackID == initial.playbackID)
        }
    }
    #expect(await engine.numberOfLoads() == 2)
}

@Test func failedAutomaticHandoffDoesNotRetryEveryPoll() async throws {
    let first = LibrarySong(id: SongID(rawValue: "first"), title: "First", mediaKind: .wav)
    let broken = LibrarySong(id: SongID(rawValue: "broken"), title: "Broken", mediaKind: .wav)
    let engine = FakeAudioPlaybackEngine(failingLoadSongIDs: [broken.id])
    let controller = LocalKaraokeController(engine: engine, library: FakeKaraokeLibrary(songs: [first, broken]))
    try await controller.send(.enqueue(songID: first.id))
    try await controller.send(.enqueue(songID: broken.id))
    try await controller.send(.play)
    await engine.finishCurrentNaturally()
    for _ in 0..<10 {
        let snapshot = await controller.snapshot()
        #expect(snapshot.queue.map(\.song.id) == [broken.id])
        #expect(snapshot.playbackError != nil)
        #expect(snapshot.lastCompletion?.song == first)
    }
    #expect(await engine.numberOfLoads() == 2)
    await #expect(throws: TestPlaybackError.forcedLoad(broken.id)) {
        try await controller.send(.next)
    }
    #expect(await engine.numberOfLoads() == 3)
}

@Test func manualNextNearEndDoesNotPublishCompletion() async throws {
    let song = LibrarySong(id: SongID(rawValue: "skip"), title: "Skip", mediaKind: .wav)
    let engine = FakeAudioPlaybackEngine()
    let controller = LocalKaraokeController(engine: engine, library: FakeKaraokeLibrary(songs: [song]))
    for _ in 0..<2 { try await controller.send(.enqueue(songID: song.id)) }
    try await controller.send(.play)
    try await controller.send(.seek(milliseconds: 59_999))
    try await controller.send(.next)
    #expect(await controller.snapshot().lastCompletion == nil)
}

@Test func failedNextPlayDoesNotExposeOrResumeUncommittedAsset() async throws {
    let first = LibrarySong(id: SongID(rawValue: "good"), title: "Good", mediaKind: .wav)
    let broken = LibrarySong(id: SongID(rawValue: "bad-play"), title: "Bad", mediaKind: .wav)
    let engine = FakeAudioPlaybackEngine(failingPlaySongIDs: [broken.id])
    let controller = LocalKaraokeController(engine: engine, library: FakeKaraokeLibrary(songs: [first, broken]))
    try await controller.send(.enqueue(songID: first.id))
    try await controller.send(.enqueue(songID: broken.id))
    try await controller.send(.play)
    await #expect(throws: TestPlaybackError.forcedPlay(broken.id)) {
        try await controller.send(.next)
    }
    let stopped = await controller.snapshot()
    #expect(stopped.nowPlaying == first)
    #expect(stopped.transport == .stopped)
    #expect(stopped.lastCompletion == nil)
    try await controller.send(.play)
    #expect(await engine.snapshot().songID == first.id)
    #expect(await controller.snapshot().queue.map(\.song.id) == [broken.id])
}


@Test func catalogOnlyTitlesCannotBeEnqueuedEvenByPairedRemoteCommands() async throws {
    let title = LibrarySong(
        id: SongID(rawValue: "indexed:catalog:7"),
        title: "Browse Only", mediaKind: .legacyContainer,
        isPlayable: false
    )
    let controller = LocalKaraokeController(
        engine: FakeAudioPlaybackEngine(),
        library: FakeKaraokeLibrary(songs: [title])
    )
    await #expect(throws: LocalKaraokeControllerError.catalogOnlySong(title.id)) {
        try await controller.send(.enqueue(songID: title.id))
    }
    #expect(await controller.snapshot().queue.isEmpty)
}

@Test func librarySongLegacyJSONDefaultsToPlayableAndNewJSONPreservesStatus() throws {
    let song = LibrarySong(
        id: SongID(rawValue: "catalog:1"), title: "Title",
        mediaKind: .legacyContainer, isPlayable: false
    )
    let encoded = try JSONEncoder().encode(song)
    #expect(try JSONDecoder().decode(LibrarySong.self, from: encoded)
            .isPlayable == false)

    var dictionary = try #require(
        JSONSerialization.jsonObject(with: encoded) as? [String: Any]
    )
    dictionary.removeValue(forKey: "isPlayable")
    let oldJSON = try JSONSerialization.data(withJSONObject: dictionary)
    #expect(try JSONDecoder().decode(LibrarySong.self, from: oldJSON)
            .isPlayable)
}

@Test func canceledPlaybackWaiterDoesNotBlockFutureCommands() async throws {
    let song = LibrarySong(
        id: SongID(rawValue: "gate-test"), title: "Gate", mediaKind: .wav
    )
    let engine = FakeAudioPlaybackEngine()
    let controller = LocalKaraokeController(
        engine: engine, library: FakeKaraokeLibrary(songs: [song])
    )
    await engine.blockNextSnapshot()
    let holding = Task { await controller.snapshot() }
    for _ in 0..<1_000 {
        if await engine.isSnapshotBlocked() { break }
        await Task.yield()
    }
    #expect(await engine.isSnapshotBlocked())

    let waiting = Task { try await controller.send(.setVolume(0.3)) }
    await Task.yield()
    waiting.cancel()
    await engine.releaseSnapshot()
    _ = await holding.value
    await #expect(throws: CancellationError.self) { try await waiting.value }

    try await controller.send(.setVolume(0.7))
    #expect(await controller.snapshot().volume == 0.7)
}


@Test func playNowPreservesReservationsAndDoesNotCommitFailedSong() async throws {
    let first = LibrarySong(id: SongID(rawValue: "first"), title: "First", mediaKind: .kar)
    let second = LibrarySong(id: SongID(rawValue: "second"), title: "Second", mediaKind: .kar)
    let controller = LocalKaraokeController(engine: FakeAudioPlaybackEngine(),
        library: FakeKaraokeLibrary(songs: [first, second]))
    try await controller.send(.enqueue(songID: second.id))
    try await controller.playNow(songID: first.id)
    let snapshot = await controller.snapshot()
    #expect(snapshot.nowPlaying?.id == first.id)
    #expect(snapshot.queue.map(\.song.id) == [second.id])
    await #expect(throws: (any Error).self) { try await controller.playNow(songID: SongID(rawValue: "missing")) }
    let after = await controller.snapshot()
    #expect(after.nowPlaying?.id == first.id)
    #expect(after.queue == snapshot.queue)
}


@MainActor
@Test func onlineHandoffPreservesPausedSongQueueAndRetiresBeforeNativeResume() async throws {
    let song = LibrarySong(id: SongID(rawValue: "handoff"), title: "Handoff", mediaKind: .wav)
    let engine = FakeAudioPlaybackEngine()
    let ownership = KaraokeAudioOwnership()
    await engine.checkOwnership(ownership)
    let controller = LocalKaraokeController(engine: engine,
        library: FakeKaraokeLibrary(songs: [song]), audioOwnership: ownership)
    for _ in 0..<3 { try await controller.send(.enqueue(songID: song.id)) }
    try await controller.send(.play)
    let rejectedID = UUID()
    let rejected = try await controller.claimOnlineAudio(rejectedID) { Issue.record("Playing local accepted online") }
    #expect(!rejected)
    try await controller.send(.seek(milliseconds: 12_345))
    try await controller.send(.pause)
    let before = await controller.snapshot()
    let onlineID = UUID()
    var retirements = 0
    let accepted = try await controller.claimOnlineAudio(onlineID) { retirements += 1 }
    #expect(accepted)
    #expect(await controller.snapshot() == before)
    #expect(await engine.numberOfSuspensions() == 1)
    // A reservation is metadata only; it must not revoke online output.
    try await controller.send(.enqueue(songID: song.id))
    #expect(ownership.ownsOnlineAudio(onlineID))
    try await controller.send(.play) // Same entry used by authenticated LAN commands.
    #expect(retirements == 1)
    #expect(!ownership.onlineActive)
    #expect(await engine.ownershipAtNativeStarts().allSatisfy { !$0 })
    let after = await controller.snapshot()
    #expect(after.playbackID == before.playbackID)
    #expect(after.positionMilliseconds == before.positionMilliseconds)
    #expect(after.queue.prefix(2).map(\.id) == before.queue.map(\.id))
    #expect(after.lastCompletion == before.lastCompletion)
}

@MainActor
@Test func onlineHandoffEffectsAndExplicitNextKeepDistinctReservations() async throws {
    let song = LibrarySong(id: SongID(rawValue: "effect-handoff"), title: "Effect", mediaKind: .wav)
    let ownership = KaraokeAudioOwnership()
    let effects = HandoffEffects(ownership: ownership)
    let engine = FakeAudioPlaybackEngine()
    await engine.checkOwnership(ownership)
    let controller = LocalKaraokeController(engine: engine,
        library: FakeKaraokeLibrary(songs: [song]), partySFX: effects, audioOwnership: ownership)
    for _ in 0..<2 { try await controller.send(.enqueue(songID: song.id)) }
    let before = await controller.snapshot()
    try await controller.send(.triggerSFX(.ding))
    let first = UUID()
    let accepted = try await controller.claimOnlineAudio(first) {}
    #expect(accepted)
    #expect(await effects.active == false)
    try await controller.send(.triggerSFX(.ding))
    #expect(!ownership.onlineActive)
    #expect(await effects.sawOnlineAtTrigger == false)
    let second = UUID()
    let reopened = try await controller.claimOnlineAudio(second) {}
    #expect(reopened)
    try await controller.send(.next)
    #expect(!ownership.onlineActive)
    let after = await controller.snapshot()
    #expect(after.queue.map(\.id) == [before.queue[1].id])
    #expect(await engine.ownershipAtNativeStarts().allSatisfy { !$0 })
}

@MainActor
@Test func pendingOnlineClaimRechecksMicrophoneAndCancellationAfterAwait() async throws {
    let ownership = KaraokeAudioOwnership()
    let effects = HandoffEffects(ownership: ownership)
    let controller = LocalKaraokeController(engine: FakeAudioPlaybackEngine(),
        library: FakeKaraokeLibrary(songs: []), partySFX: effects, audioOwnership: ownership)
    await effects.blockStop()
    let pending = Task { try await controller.claimOnlineAudio(UUID()) {} }
    for _ in 0..<1_000 {
        if await effects.stopBlocked { break }
        await Task.yield()
    }
    #expect(await effects.stopBlocked)
    ownership.setMicrophoneBusy(true)
    await effects.releaseStop()
    let accepted = try await pending.value
    #expect(!accepted)
    #expect(!ownership.onlineActive)
    ownership.setMicrophoneBusy(false)
    await effects.blockStop()
    let canceled = Task { try await controller.claimOnlineAudio(UUID()) {} }
    for _ in 0..<1_000 {
        if await effects.stopBlocked { break }
        await Task.yield()
    }
    #expect(await effects.stopBlocked)
    canceled.cancel()
    await effects.releaseStop()
    await #expect(throws: CancellationError.self) { try await canceled.value }
    #expect(!ownership.onlineActive)
    let reopened = try await controller.claimOnlineAudio(UUID()) {}
    #expect(reopened)
}

private actor HandoffEffects: PartySFXPlayback {
    let ownership: KaraokeAudioOwnership
    private(set) var active = false
    private(set) var sawOnlineAtTrigger = false
    private var shouldBlock = false
    private var waiter: CheckedContinuation<Void, Never>?
    var stopBlocked: Bool { waiter != nil }
    init(ownership: KaraokeAudioOwnership) { self.ownership = ownership }
    func blockStop() { shouldBlock = true }
    func releaseStop() { waiter?.resume(); waiter = nil }
    func trigger(_ sound: PartySFX) async throws {
        sawOnlineAtTrigger = await ownership.onlineActive
        active = true
    }
    func stopAll() async {
        if shouldBlock {
            shouldBlock = false
            await withCheckedContinuation { waiter = $0 }
        }
        active = false
    }
    func setSFXVolume(_ value: Float) async {}
}


@MainActor
@Test func onlineClaimCannotEnterDuringNativePreparation() async throws {
    let song = LibrarySong(id: SongID(rawValue: "preparing"), title: "Preparing", mediaKind: .wav)
    let ownership = KaraokeAudioOwnership()
    let engine = FakeAudioPlaybackEngine()
    let controller = LocalKaraokeController(engine: engine,
        library: FakeKaraokeLibrary(songs: [song]), audioOwnership: ownership)
    try await controller.send(.enqueue(songID: song.id))
    await engine.blockNextLoad()
    let play = Task { try await controller.send(.play) }
    for _ in 0..<1_000 {
        if await engine.isLoadBlocked() { break }
        await Task.yield()
    }
    #expect(await engine.isLoadBlocked())
    let online = Task { try await controller.claimOnlineAudio(UUID()) {} }
    await engine.releaseLoad()
    try await play.value
    let accepted = try await online.value
    #expect(!accepted)
    #expect(!ownership.onlineActive)
    #expect(await controller.snapshot().nowPlaying?.id == song.id)
}
