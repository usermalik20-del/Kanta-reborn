import GeloKaraokeCore
import SwiftUI

@MainActor
struct InAppControlCenterView: View {
    @ObservedObject var model: HostViewModel
    @State private var searchTask: Task<Void, Never>?
    @State private var showingSongbook = false
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Binding var backgroundsEnabled: Bool
    @Binding var selectedPreset: KantaBuiltInVisualPreset
    @Binding var singerName: String
    var microphoneBusy = false

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            nowPlaying
            Divider()
            TextField("Singer name for new reservations (optional)", text: $singerName)
                .textInputAutocapitalization(.words)
                .font(.subheadline)
            search
            Divider()
            queue
            Divider()
            playback
        }
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .sheet(isPresented: $showingSongbook) {
            KantaSongbookView(library: model.songbookLibrary)
        }
    }

    private var nowPlaying: some View {
        Group {
            if let song = model.snapshot.nowPlaying {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "mic.fill")
                            .font(.title2.weight(.bold))
                            .foregroundStyle(.white)
                            .frame(width: 46, height: 46)
                            .background(
                                GeloVisualStyle.accent,
                                in: Circle()
                            )

                        VStack(alignment: .leading, spacing: 4) {
                            Text("NOW SINGING")
                                .font(.caption2.weight(.black))
                                .tracking(1.4)
                                .foregroundStyle(GeloVisualStyle.accent)

                            if let singer = model.snapshot.nowSinging,
                               !singer.isEmpty {
                                Text("Singer · " + singer)
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(GeloVisualStyle.accent)
                            }

                            Text(song.title)
                                .font(
                                    .system(
                                        size: 26,
                                        weight: .bold,
                                        design: .rounded
                                    )
                                )
                                .lineLimit(2)
                                .minimumScaleFactor(0.78)

                            if let artist = song.artist,
                               !artist.isEmpty {
                                Text(artist)
                                    .font(.subheadline.weight(.medium))
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                        }

                        Spacer(minLength: 0)
                    }

                    HStack(spacing: 18) {
                        Spacer(minLength: 0)

                        Button {
                            Task { await model.togglePlayPause() }
                        } label: {
                            Image(
                                systemName:
                                    model.snapshot.transport == .playing
                                    ? "pause.fill"
                                    : "play.fill"
                            )
                            .font(.system(size: 25, weight: .bold))
                            .frame(width: 62, height: 62)
                            .background(
                                GeloVisualStyle.accent,
                                in: Circle()
                            )
                            .foregroundStyle(.white)
                            .shadow(
                                color: GeloVisualStyle.accent
                                    .opacity(0.30),
                                radius: 12,
                                y: 5
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(
                            model.snapshot.transport == .playing
                                ? "Pause"
                                : "Play"
                        )

                        transportButton(
                            systemName: "forward.fill",
                            accessibilityLabel: "Next song"
                        ) {
                            Task { await model.next() }
                        }

                        Spacer(minLength: 0)
                    }
                    .disabled(!model.isAvailable)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    LinearGradient(
                        colors: [
                            GeloVisualStyle.accent.opacity(0.14),
                            Color.purple.opacity(0.08),
                            Color.clear
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    ),
                    in: RoundedRectangle(
                        cornerRadius: 22,
                        style: .continuous
                    )
                )
                .overlay {
                    RoundedRectangle(
                        cornerRadius: 22,
                        style: .continuous
                    )
                    .stroke(
                        GeloVisualStyle.accent.opacity(0.16),
                        lineWidth: 1
                    )
                }
            } else {
                HStack(spacing: 12) {
                    Image(systemName: "mic")
                        .foregroundStyle(GeloVisualStyle.accent)
                    Text("Choose a song to start the karaoke stage")
                        .font(.headline)
                        .foregroundStyle(.secondary)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    .thinMaterial,
                    in: RoundedRectangle(
                        cornerRadius: 18,
                        style: .continuous
                    )
                )
            }
        }
    }

    private func transportButton(
        systemName: String,
        accessibilityLabel: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.headline)
                .frame(width: 46, height: 46)
                .background(
                    .thinMaterial,
                    in: Circle()
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityLabel)
    }



    private var search: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label("Find a song", systemImage: "magnifyingglass")
                    .font(.headline)
                Spacer()
                Button {
                    showingSongbook = true
                } label: {
                    Label("Songbook", systemImage: "books.vertical")
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }

            TextField(
                "Search song title or code",
                text: $model.query
            )
            .textFieldStyle(.roundedBorder)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .onChange(of: model.query) {
                searchTask?.cancel()
                searchTask = Task {
                    try? await Task.sleep(for: .milliseconds(160))
                    guard !Task.isCancelled else { return }
                    await model.search()
                }
            }

            KantaVoiceSearchAccessory(
                query: $model.query,
                microphoneBusy: microphoneBusy
            )

            if model.isLoading {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("Loading library…")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } else if model.songs.isEmpty {
                Text(
                    model.query.isEmpty
                        ? "No playable songs in the connected library."
                        : "No songs match this search."
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            } else {
                ForEach(model.songs) { song in
                    HStack(spacing: 10) {
                        Image(systemName: "music.note")
                            .foregroundStyle(.secondary)
                            .frame(width: 22)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(song.title)
                                .lineLimit(1)
                                .minimumScaleFactor(0.80)
                            HStack(spacing: 7) {
                                if let code = song.code {
                                    Text("Code " + code)
                                        .font(.caption2.monospacedDigit().bold())
                                        .foregroundStyle(GeloVisualStyle.accent)
                                }
                                Text(song.mediaKind.rawValue.uppercased())
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .frame(
                            maxWidth: .infinity,
                            alignment: .leading
                        )
                        .layoutPriority(1)

                        reserveButton(for: song)
                    }
                    .padding(.vertical, 2)
                    .frame(maxWidth: .infinity)
                }

                if model.hasMoreSongs {
                    Button {
                        Task { await model.loadMoreSongs() }
                    } label: {
                        Label("Load more songs", systemImage: "arrow.down.circle")
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(GeloVisualStyle.accent)
                }
                Text("Showing \(model.songs.count) matching songs")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }

    @ViewBuilder
    private func reserveButton(
        for song: LibrarySong
    ) -> some View {
        if !song.isPlayable {
            Text("Catalog only")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.secondary)
                .accessibilityLabel("Song title only. Media unavailable.")
        } else if horizontalSizeClass == .compact {
            Button {
                Task { await model.enqueue(song, singer: singerName) }
            } label: {
                Image(systemName: "plus")
                    .font(.headline.weight(.bold))
                    .frame(width: 38, height: 38)
                    .background(
                        GeloVisualStyle.accent.opacity(0.12),
                        in: Circle()
                    )
            }
            .buttonStyle(.plain)
            .foregroundStyle(GeloVisualStyle.accent)
            .fixedSize()
            .accessibilityLabel("Reserve " + song.title)
        } else {
            Button {
                Task { await model.enqueue(song, singer: singerName) }
            } label: {
                Label(
                    "Reserve",
                    systemImage: "plus.circle"
                )
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
                .foregroundStyle(GeloVisualStyle.accent)
                .frame(width: 104, height: 38)
                .background(
                    GeloVisualStyle.accent.opacity(0.12),
                    in: Capsule()
                )
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .frame(width: 104, height: 38)
            .accessibilityLabel("Reserve " + song.title)
        }
    }

    private var queue: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(
                "Up Next (\(model.snapshot.queue.count))",
                systemImage: "text.line.first.and.arrowtriangle.forward"
            )
            .font(.headline)

            if model.snapshot.queue.isEmpty {
                KantaEmptyState(
                    icon: "list.bullet",
                    title: "Queue is empty",
                    description: "Reserve a song to line one up.",
                    actionTitle: nil,
                    action: nil,
                    style: .compact
                )
            } else {
                ForEach(Array(model.snapshot.queue.prefix(6))) { item in
                    HStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.song.title)
                                .font(KantaTypography.songTitle)
                                .foregroundStyle(KantaColors.textPrimary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.80)
                            if let singer = item.requestedBy {
                                Text("Singer · " + singer)
                                    .font(KantaTypography.caption)
                                    .foregroundStyle(KantaColors.textSecondary)
                            }
                        }
                        .frame(
                            maxWidth: .infinity, alignment: .leading
                        )
                        .layoutPriority(1)

                        Button(role: .destructive) {
                            Task {
                                await model.removeQueueItem(item.id)
                            }
                        } label: {
                            Image(systemName: "minus.circle")
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(
                            "Remove " + item.song.title + " from queue"
                        )
                    }
                }

                if model.snapshot.queue.count > 6 {
                    Text(
                        "+\(model.snapshot.queue.count - 6) more queued"
                    )
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var playback: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("Playback", systemImage: "waveform")
                .font(.headline)

            Toggle(isOn: $backgroundsEnabled) {
                Label(
                    "Stage visuals",
                    systemImage: "photo.on.rectangle"
                )
            }

            Text(
                "Optional. Turn this off for a clean lyric-only stage. " +
                "Your selected media stays unchanged."
            )
            .font(.caption)
            .foregroundStyle(.secondary)

            if backgroundsEnabled {
                Menu {
                    Section("Images · 10 backgrounds") {
                        ForEach(
                            KantaBuiltInVisualPreset.stillPresets
                        ) { preset in
                            Button {
                                selectedPreset = preset
                            } label: {
                                if selectedPreset == preset {
                                    Label(
                                        preset.displayName,
                                        systemImage: "checkmark"
                                    )
                                } else {
                                    Text(preset.displayName)
                                }
                            }
                        }
                    }

                    Section("Animated · 10 HQ presets") {
                        ForEach(
                            KantaBuiltInVisualPreset.motionPresets
                        ) { preset in
                            Button {
                                selectedPreset = preset
                            } label: {
                                if selectedPreset == preset {
                                    Label(
                                        preset.displayName,
                                        systemImage: "checkmark"
                                    )
                                } else {
                                    Text(preset.displayName)
                                }
                            }
                        }
                    }
                } label: {
                    HStack {
                        Label(
                            selectedPreset.displayName,
                            systemImage:
                                selectedPreset.kind == .motion
                                ? "sparkles.tv"
                                : "photo"
                        )
                        .lineLimit(2)
                        .layoutPriority(1)
                        Spacer()
                        Text(selectedPreset.categoryLabel)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(
                    "Stage visual preset, " +
                    selectedPreset.displayName
                )

                Text(
                    "Ten original background images are included for offline use. " +
                    "Animated presets are generated on device. " +
                    "Your imported media stays unchanged."
                )
                .font(.caption2)
                .foregroundStyle(.secondary)
            }

            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text("Volume")
                    Spacer()
                    Text("\(Int(model.snapshot.volume * 100))%")
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                }

                Slider(
                    value: Binding(
                        get: {
                            Double(model.snapshot.volume)
                        },
                        set: { value in
                            Task {
                                await model.setVolume(Float(value))
                            }
                        }
                    ),
                    in: 0...1
                )
                .accessibilityLabel("Playback volume")
            }

            ViewThatFits(in: .horizontal) {
                HStack(spacing: 12) {
                    pitchStepper
                    tempoStepper
                }

                VStack(spacing: 10) {
                    pitchStepper
                    tempoStepper
                }
            }
        }
    }

    private var pitchStepper: some View {
        compactStepper(
            title: "Pitch",
            value: "\(model.snapshot.pitchSemitones) st",
            decrease: {
                Task { await model.changePitch(by: -1) }
            },
            increase: {
                Task { await model.changePitch(by: 1) }
            }
        )
    }

    private var tempoStepper: some View {
        compactStepper(
            title: "Tempo",
            value: String(
                format: "%.2fx",
                model.snapshot.tempoRatio
            ),
            decrease: {
                Task { await model.changeTempo(by: -0.05) }
            },
            increase: {
                Task { await model.changeTempo(by: 0.05) }
            }
        )
    }

    private func compactStepper(
        title: String,
        value: String,
        decrease: @escaping () -> Void,
        increase: @escaping () -> Void
    ) -> some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack(spacing: 10) {
                Button(action: decrease) {
                    Image(systemName: "minus")
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.bordered)

                Text(value)
                    .font(.subheadline.monospacedDigit())
                    .frame(minWidth: 48)

                Button(action: increase) {
                    Image(systemName: "plus")
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.bordered)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(10)
        .background(
            .thinMaterial,
            in: RoundedRectangle(
                cornerRadius: 14,
                style: .continuous
            )
        )
    }
}

