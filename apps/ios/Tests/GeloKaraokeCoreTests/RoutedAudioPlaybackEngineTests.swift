import Foundation
import Testing
@testable import GeloKaraokeCore

@Test func routedEngineSelectsFileAndMIDIBackends() async throws {
    let fileEngine = RecordingPlaybackEngine()
    let midiEngine = RecordingPlaybackEngine()
    let routed = RoutedAudioPlaybackEngine(
        fileEngine: fileEngine,
        midiEngine: midiEngine
    )

    let fileAsset = AudioPlaybackAsset(
        songID: SongID(rawValue: "audio"),
        url: URL(fileURLWithPath: "/tmp/audio.wav"),
        mediaKind: .wav
    )
    try await routed.load(fileAsset)

    #expect(await fileEngine.loadedKinds() == [.wav])
    #expect(await midiEngine.loadedKinds().isEmpty)

    let midiAsset = AudioPlaybackAsset(
        songID: SongID(rawValue: "midi"),
        url: URL(fileURLWithPath: "/tmp/song.mid"),
        mediaKind: .midi,
        soundFontURL: URL(fileURLWithPath: "/tmp/bank.sf2")
    )
    try await routed.load(midiAsset)

    #expect(await fileEngine.stopCount() == 1)
    #expect(await midiEngine.loadedKinds() == [.midi])
}

@Test func routedEngineRetainsControlsAcrossRouteChanges() async throws {
    let fileEngine = RecordingPlaybackEngine()
    let midiEngine = RecordingPlaybackEngine()
    let routed = RoutedAudioPlaybackEngine(
        fileEngine: fileEngine,
        midiEngine: midiEngine
    )

    await routed.setVolume(0.3)
    await routed.setPitch(semitones: -3)
    await routed.setTempo(ratio: 1.2)

    try await routed.load(
        AudioPlaybackAsset(
            songID: SongID(rawValue: "midi"),
            url: URL(fileURLWithPath: "/tmp/song.kar"),
            mediaKind: .kar,
            soundFontURL: URL(fileURLWithPath: "/tmp/bank.sf2")
        )
    )

    let state = await midiEngine.snapshot()
    #expect(state.volume == 0.3)
    #expect(state.pitchSemitones == -3)
    #expect(state.tempoRatio == 1.2)
}

private actor RecordingPlaybackEngine: AudioPlaybackEngine {
    private var kinds: [MediaKind] = []
    private var stops = 0
    private var suspensions = 0
    func suspendForOnlinePlayback() async throws { suspensions += 1 }
    func suspensionCount() -> Int { suspensions }
    private var state = AudioPlaybackSnapshot()

    func load(_ asset: AudioPlaybackAsset) async throws {
        kinds.append(asset.mediaKind)
        state.songID = asset.songID
    }

    func play() async throws {
        state.transport = .playing
    }

    func pause() async {
        state.transport = .paused
    }

    func stop() async {
        stops += 1
        state.transport = .stopped
    }

    func seek(toMilliseconds milliseconds: Int64) async throws {
        state.positionMilliseconds = milliseconds
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

    func snapshot() async -> AudioPlaybackSnapshot {
        state
    }

    func loadedKinds() -> [MediaKind] {
        kinds
    }

    func stopCount() -> Int {
        stops
    }
}


@Test func onlineHandoffSuspendsBothRenderingBackendsWithoutTransportCommands() async throws {
    let file = RecordingPlaybackEngine(), midi = RecordingPlaybackEngine()
    let routed = RoutedAudioPlaybackEngine(fileEngine: file, midiEngine: midi)
    try await routed.load(AudioPlaybackAsset(songID: SongID(rawValue: "paused"),
        url: URL(fileURLWithPath: "/tmp/paused.wav"), mediaKind: .wav))
    try await routed.seek(toMilliseconds: 1_234)
    let before = await routed.snapshot()
    try await routed.suspendForOnlinePlayback()
    #expect(await routed.snapshot() == before)
    #expect(await file.suspensionCount() == 1)
    #expect(await midi.suspensionCount() == 1)
    #expect(await file.stopCount() == 0)
    #expect(await midi.stopCount() == 0)
}
