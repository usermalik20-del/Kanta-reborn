import AVFoundation
import GeloKaraokeCore
import SwiftUI
import UniformTypeIdentifiers

/// Optional owner-provided music between karaoke performances. It never
/// joins the song queue, records microphone audio or fetches streaming media.
@MainActor
final class KantaIntermissionMusic: ObservableObject {
    @Published private(set) var enabled = false
    private let audioOwnership: KaraokeAudioOwnership
    // Self-assignment in didSet recurses through the synthesized @Published setter.
    @Published private(set) var volume: Float = 0.23
    @Published private(set) var songTitle: String?
    @Published private(set) var isPlaying = false
    @Published private(set) var message: String?
    @Published private(set) var hasTrack = false

    private let directory: URL
    private let defaults: UserDefaults
    private var player: AVAudioPlayer?
    private var trackURL: URL?
    private var transport: TransportState = .stopped
    private var isActiveHost = false
    private var fadeGeneration = 0

    private var shouldPlay: Bool {
        enabled && isActiveHost && !audioOwnership.onlineActive
            && transport == .stopped && trackURL != nil
    }

    init(audioOwnership: KaraokeAudioOwnership, defaults: UserDefaults = .standard,
         directory storageDirectory: URL? = nil) {
        self.audioOwnership = audioOwnership
        self.defaults = defaults
        self.directory = storageDirectory ?? (FileManager.default.urls(
            for: .applicationSupportDirectory, in: .userDomainMask
        ).first ?? FileManager.default.temporaryDirectory)
            .appendingPathComponent("KANTA/Intermission", isDirectory: true)
        let restoredVolume = defaults.object(forKey: "kanta.intermission.volume") == nil
            ? 0.23 : defaults.float(forKey: "kanta.intermission.volume")
        volume = Self.boundedVolume(restoredVolume)
        enabled = false // Always opt in per Host session.
        if let filename = defaults.string(forKey: "kanta.intermission.file"),
           !filename.isEmpty, (filename as NSString).lastPathComponent == filename {
            let url = directory.appendingPathComponent(filename)
            if FileManager.default.fileExists(atPath: url.path) {
                trackURL = url
                songTitle = defaults.string(
                    forKey: "kanta.intermission.title"
                ) ?? "Intermission Music"
                hasTrack = true
            }
        }
    }

    private static func boundedVolume(_ raw: Float) -> Float {
        guard raw.isFinite else { return 0 }
        return min(max(raw, 0), 0.6)
    }

    /// Exactly one validation/persistence path for slider changes. Publishing
    /// the new value never mutates it recursively through a property observer.
    func setVolume(_ requested: Float) {
        let bounded = Self.boundedVolume(requested)
        guard volume != bounded else { return }
        volume = bounded
        defaults.set(bounded, forKey: "kanta.intermission.volume")
        if shouldPlay { player?.setVolume(bounded, fadeDuration: 0.5) }
    }

    func setEnabled(_ value: Bool) {
        if value {
            do { try audioOwnership.prepareNativeAudio() }
            catch {
                message = "Intermission audio output is unavailable."
                return
            }
        }
        enabled = value
        updatePlayback()
    }

    /// Immediate handoff; invalidate pending fades and require a new explicit opt-in.
    func stopForOnlinePlayback() {
        enabled = false
        fadeGeneration += 1
        player?.stop()
        player = nil
        isPlaying = false
    }

    func activate() {
        isActiveHost = true
        updatePlayback()
    }

    func deactivate() {
        isActiveHost = false
        enabled = false
        player?.stop()
        player = nil
        isPlaying = false
        fadeGeneration += 1
    }

    func sync(_ nextTransport: TransportState) {
        guard nextTransport != transport else { return }
        transport = nextTransport
        updatePlayback()
    }

    /// The copied source is kept byte-for-byte; only owners may supply clips.
    /// The importer is local and never uploads the audio.
    func importMusic(_ url: URL) throws {
        let granted = url.startAccessingSecurityScopedResource()
        defer { if granted { url.stopAccessingSecurityScopedResource() } }
        let bytes = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard bytes > 0, bytes <= 150_000_000 else {
            throw KantaIntermissionError.invalidSource
        }
        let ext = url.pathExtension.lowercased()
        guard ["mp3", "wav", "m4a", "caf", "aif", "aiff"].contains(ext) else {
            throw KantaIntermissionError.invalidSource
        }

        try FileManager.default.createDirectory(
            at: directory, withIntermediateDirectories: true
        )
        let name = "intermission-" + UUID().uuidString + "." + ext
        let saved = directory.appendingPathComponent(name)
        try FileManager.default.copyItem(at: url, to: saved)
        do {
            let prepared = try AVAudioPlayer(contentsOf: saved)
            guard prepared.duration.isFinite, prepared.duration > 0 else {
                throw KantaIntermissionError.invalidSource
            }
            prepared.numberOfLoops = -1
            prepared.volume = 0

            fadeGeneration += 1
            player?.stop()
            if let old = defaults.string(forKey: "kanta.intermission.file"),
               old != name {
                try? FileManager.default.removeItem(
                    at: directory.appendingPathComponent(old)
                )
            }
            player = prepared
            trackURL = saved
            defaults.set(name, forKey: "kanta.intermission.file")
            songTitle = url.deletingPathExtension().lastPathComponent
            defaults.set(songTitle, forKey: "kanta.intermission.title")
            hasTrack = true
            message = "Intermission track imported locally."
            updatePlayback()
        } catch {
            try? FileManager.default.removeItem(at: saved)
            throw error
        }
    }

    func report(_ error: Error) {
        message = error.localizedDescription
    }

    func clearMusic() {
        deactivate()
        if let old = defaults.string(forKey: "kanta.intermission.file") {
            try? FileManager.default.removeItem(
                at: directory.appendingPathComponent(old)
            )
        }
        defaults.removeObject(forKey: "kanta.intermission.file")
        defaults.removeObject(forKey: "kanta.intermission.title")
        songTitle = nil
        player = nil
        trackURL = nil
        hasTrack = false
        message = "Intermission music cleared."
    }

    private func updatePlayback() {
        defaults.set(volume, forKey: "kanta.intermission.volume")
        fadeGeneration += 1
        let generation = fadeGeneration
        // Opening Host or restoring a saved track must never initialize a
        // decoder or an output player before the owner enables intermission.
        if shouldPlay, player == nil, let trackURL {
            do {
                let prepared = try AVAudioPlayer(contentsOf: trackURL)
                guard prepared.duration.isFinite, prepared.duration > 0 else {
                    throw KantaIntermissionError.invalidSource
                }
                prepared.numberOfLoops = -1
                prepared.volume = 0
                player = prepared
            } catch {
                message = "Unable to play saved intermission music: "
                    + error.localizedDescription
                isPlaying = false
                return
            }
        }
        guard let player else {
            isPlaying = false
            return
        }
        if shouldPlay {
            if !player.isPlaying {
                player.volume = 0
                guard player.prepareToPlay(), player.play() else {
                    message = "Intermission audio output is unavailable."
                    isPlaying = false
                    return
                }
            }
            player.setVolume(volume, fadeDuration: 1.2)
            isPlaying = true
        } else {
            player.setVolume(0, fadeDuration: 0.7)
            isPlaying = false
            Task { @MainActor [weak self, weak player] in
                try? await Task.sleep(for: .milliseconds(800))
                guard let self, self.fadeGeneration == generation else {
                    return
                }
                player?.pause()
            }
        }
    }
}

enum KantaIntermissionError: LocalizedError {
    case invalidSource

    var errorDescription: String? {
        "Choose your own playable audio file (MP3/WAV/M4A/CAF/AIFF), " +
        "up to 150 MB. KANTA never compresses it."
    }
}

@MainActor
struct KantaIntermissionPanel: View {
    @ObservedObject var intermission: KantaIntermissionMusic
    @State private var showingFilePicker = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Toggle("Auto-play between songs", isOn: Binding(
                get: { intermission.enabled }, set: { intermission.setEnabled($0) }
            ))
                .disabled(!intermission.hasTrack)
            LabeledContent(
                "Selected music",
                value: intermission.songTitle ?? "None"
            )
            HStack {
                Button {
                    showingFilePicker = true
                } label: {
                    Label("Choose music", systemImage: "music.note.list")
                }
                if intermission.hasTrack {
                    Button("Remove", role: .destructive) {
                        intermission.clearMusic()
                    }
                }
            }
            .buttonStyle(.bordered)

            LabeledContent(
                "Music level",
                value: "\(Int(intermission.volume * 100))%"
            )
            Slider(
                value: Binding(
                    get: { Double(intermission.volume) },
                    set: { intermission.setVolume(Float($0)) }
                ),
                in: 0...0.6
            )
            .accessibilityLabel("Intermission music level")
            if let message = intermission.message {
                Text(message).font(.caption).foregroundStyle(.secondary)
            }
            Text(
                "Your own track loops only while playback is stopped. " +
                "When a karaoke song begins, background music fades out. " +
                "It is stored locally, never uploaded and never added " +
                "to the karaoke queue."
            )
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .fileImporter(
            isPresented: $showingFilePicker,
            allowedContentTypes: [.audio]
        ) { result in
            switch result {
            case let .success(url):
                do {
                    try intermission.importMusic(url)
                } catch {
                    intermission.report(error)
                }
            case let .failure(error):
                intermission.report(error)
            }
        }
    }
}

