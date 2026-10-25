#if canImport(AVFoundation)
import Foundation
import GeloKaraokeCore
import Testing
@testable import AVFoundationPlayback

@Test func fileAudioEngineLoadsWaveAndAppliesControls() async throws {
    let url = FileManager.default.temporaryDirectory
        .appendingPathComponent(UUID().uuidString)
        .appendingPathExtension("wav")
    defer { try? FileManager.default.removeItem(at: url) }

    try writePCM16MonoWave(
        to: url,
        sampleRate: 44_100,
        frameCount: 4_410
    )

    let engine = AVFoundationAudioPlaybackEngine()
    try await engine.load(
        AudioPlaybackAsset(
            songID: SongID(rawValue: "test-wave"),
            url: url,
            mediaKind: .wav
        )
    )
    await engine.setVolume(0.4)
    await engine.setPitch(semitones: 2)
    await engine.setTempo(ratio: 1.25)
    try await engine.seek(toMilliseconds: 50)

    let snapshot = await engine.snapshot()
    #expect(snapshot.songID == SongID(rawValue: "test-wave"))
    #expect(snapshot.durationMilliseconds == 100)
    #expect(snapshot.positionMilliseconds == 50)
    #expect(snapshot.volume == 0.4)
    #expect(snapshot.pitchSemitones == 2)
    #expect(snapshot.tempoRatio == 1.25)
    try await engine.suspendForOnlinePlayback()
    #expect(await engine.snapshot() == snapshot)
}

@Test func fileAudioEngineRejectsMidiAsset() async throws {
    let engine = AVFoundationAudioPlaybackEngine()
    let url = FileManager.default.temporaryDirectory
        .appendingPathComponent("song.mid")

    await #expect(
        throws: AudioPlaybackEngineError.unsupportedMediaKind(.midi)
    ) {
        try await engine.load(
            AudioPlaybackAsset(
                songID: SongID(rawValue: "midi"),
                url: url,
                mediaKind: .midi
            )
        )
    }
}

@Test func fileAudioEngineRequiresLoadedAssetForSeekAndPlay() async {
    let engine = AVFoundationAudioPlaybackEngine()

    await #expect(throws: AudioPlaybackEngineError.noAssetLoaded) {
        try await engine.seek(toMilliseconds: 10)
    }

    await #expect(throws: AudioPlaybackEngineError.noAssetLoaded) {
        try await engine.play()
    }
}

@Test func fileAudioEngineRejectsZeroLengthWave() async throws {
    let url = FileManager.default.temporaryDirectory
        .appendingPathComponent(UUID().uuidString)
        .appendingPathExtension("wav")
    defer { try? FileManager.default.removeItem(at: url) }

    try writePCM16MonoWave(to: url, sampleRate: 44_100, frameCount: 0)

    let engine = AVFoundationAudioPlaybackEngine()
    await #expect(throws: AudioPlaybackEngineError.invalidDuration) {
        try await engine.load(
            AudioPlaybackAsset(
                songID: SongID(rawValue: "empty-wave"),
                url: url,
                mediaKind: .wav
            )
        )
    }
}

private func writePCM16MonoWave(
    to url: URL,
    sampleRate: UInt32,
    frameCount: UInt32
) throws {
    let bytesPerSample: UInt16 = 2
    let channels: UInt16 = 1
    let blockAlign = channels * bytesPerSample
    let byteRate = sampleRate * UInt32(blockAlign)
    let dataSize = frameCount * UInt32(blockAlign)
    let riffSize = UInt32(36) + dataSize

    var data = Data()
    data.append(contentsOf: Array("RIFF".utf8))
    data.appendLittleEndian(riffSize)
    data.append(contentsOf: Array("WAVE".utf8))
    data.append(contentsOf: Array("fmt ".utf8))
    data.appendLittleEndian(UInt32(16))
    data.appendLittleEndian(UInt16(1))
    data.appendLittleEndian(channels)
    data.appendLittleEndian(sampleRate)
    data.appendLittleEndian(byteRate)
    data.appendLittleEndian(blockAlign)
    data.appendLittleEndian(UInt16(16))
    data.append(contentsOf: Array("data".utf8))
    data.appendLittleEndian(dataSize)
    data.append(Data(repeating: 0, count: Int(dataSize)))

    try data.write(to: url, options: .atomic)
}

private extension Data {
    mutating func appendLittleEndian(_ value: UInt16) {
        append(UInt8(truncatingIfNeeded: value))
        append(UInt8(truncatingIfNeeded: value >> 8))
    }

    mutating func appendLittleEndian(_ value: UInt32) {
        append(UInt8(truncatingIfNeeded: value))
        append(UInt8(truncatingIfNeeded: value >> 8))
        append(UInt8(truncatingIfNeeded: value >> 16))
        append(UInt8(truncatingIfNeeded: value >> 24))
    }
}
#endif

