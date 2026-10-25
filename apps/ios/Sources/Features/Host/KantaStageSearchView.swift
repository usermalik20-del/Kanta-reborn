import GeloKaraokeCore
import SwiftUI

@MainActor
struct KantaStageSearchView: View {
    @ObservedObject var model: HostViewModel
    @Binding var singerName: String
    var microphoneBusy: Bool
    let onClose: () -> Void
    @State private var selectedID: SongID?
    @State private var reserving = false
    @State private var searching = false
    @State private var showingVoice = false
    @FocusState private var typing: Bool

    private var selectedSong: LibrarySong? {
        model.songs.first { $0.id == selectedID && $0.isPlayable }
    }

    var body: some View {
        GeometryReader { geometry in
            let wide = geometry.size.width > 580
            VStack(spacing: 8) {
                HStack {
                    Text("Search songs")
                        .font(KantaTypography.sectionHeader)
                        .foregroundStyle(KantaColors.textPrimary)
                    Spacer()
                    Button { close() } label: {
                        Image(systemName: "xmark").frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Close search").disabled(reserving)
                }
                if wide {
                    HStack(alignment: .top, spacing: 16) {
                        searchAndResults.frame(maxWidth: .infinity)
                        keypad.frame(width: min(300, geometry.size.width * 0.43))
                    }
                } else {
                    searchAndResults
                    keypad
                }
                HStack(spacing: 12) {
                    Button("Cancel") { close() }
                        .buttonStyle(.bordered).disabled(reserving)
                        .frame(maxWidth: .infinity, minHeight: 44)
                    Button(reserving ? "Reserving…" : "Reserve") { reserve() }
                        .buttonStyle(.borderedProminent)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .disabled(reserving || searching || selectedSong == nil || !model.isAvailable)
                        .tint(KantaColors.primary)
                        .accessibilityIdentifier("stage.reserve")
                }
            }
            .padding(12)
        }
        .task(id: model.query) {
            selectedID = nil
            searching = true
            do { try await Task.sleep(for: .milliseconds(160)) }
            catch { return }
            await model.search()
            guard !Task.isCancelled else { return }
            searching = false
            // Even an exact code requires an explicit result tap before Reserve.
        }
        .onChange(of: model.query) { _, _ in
            selectedID = nil
            searching = true
        }
        .onDisappear { typing = false }
    }

    private var searchAndResults: some View {
        VStack(spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                TextField("Title, artist or number", text: $model.query)
                    .focused($typing).textInputAutocapitalization(.never)
                    .autocorrectionDisabled().submitLabel(.search)
                    .onSubmit { typing = false }
                    .accessibilityIdentifier("stage.searchQuery")
                Button {
                    typing = false
                    showingVoice.toggle()
                } label: {
                    Image(systemName: "mic.fill").frame(width: 44, height: 44)
                }
                .accessibilityLabel("Voice search")
            }
            .padding(.leading, 10)
            .background(KantaColors.elevated, in: RoundedRectangle(cornerRadius: 12))
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(KantaColors.primary.opacity(0.28), lineWidth: 1)
            }
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 8) {
                    if showingVoice {
                        KantaVoiceSearchAccessory(query: $model.query, microphoneBusy: microphoneBusy)
                    }
                    TextField("Singer name (optional)", text: $singerName)
                        .textInputAutocapitalization(.words).font(.caption)
                        .padding(.vertical, 8)
                    if searching || model.isLoading {
                        Text("Searching…").font(.caption)
                    } else if model.songs.isEmpty {
                        KantaEmptyState(
                            icon: "magnifyingglass",
                            title: "No matching songs",
                            description: "Add your songs through More → Song Library.",
                            actionTitle: nil,
                            action: nil,
                            style: .compact
                        )
                    }
                    ForEach(model.songs) { song in
                        Button {
                            selectedID = song.id
                            typing = false
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text((song.code.map { $0 + " · " } ?? "") + song.title)
                                        .font(KantaTypography.songTitle)
                                        .foregroundStyle(KantaColors.textPrimary)
                                        .lineLimit(2)
                                    Text(song.isPlayable ? (song.artist ?? "Local library") : "Catalog only · media unavailable")
                                        .font(KantaTypography.caption)
                                        .foregroundStyle(KantaColors.textSecondary)
                                }
                                Spacer(minLength: 4)
                                if selectedID == song.id { Image(systemName: "checkmark.circle.fill") }
                            }
                            .padding(10).frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .background(selectedID == song.id ? KantaColors.primary.opacity(0.25) : .clear,
                                        in: RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                        .disabled(!song.isPlayable || reserving || searching)
                        .accessibilityLabel("Select " + song.title)
                        .accessibilityAddTraits(selectedID == song.id ? .isSelected : [])
                    }
                    if model.hasMoreSongs {
                        Button("Load more") { Task { await model.loadMoreSongs() } }
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .disabled(reserving)
    }

    private var keypad: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
            ForEach(["1", "2", "3", "4", "5", "6", "7", "8", "9", "Clear", "0", "Delete"], id: \.self) { key in
                Button {
                    typing = false
                    selectedID = nil
                    switch key {
                    case "Clear": model.query = ""
                    case "Delete": if !model.query.isEmpty { model.query.removeLast() }
                    default:
                        if !model.query.allSatisfy(\.isNumber) { model.query = "" }
                        if model.query.count < 12 { model.query += key }
                    }
                } label: {
                    Group {
                        if key == "Delete" { Image(systemName: "delete.left") }
                        else { Text(key).font(key == "Clear" ? .caption.bold() : .title3.bold()) }
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(KantaColors.elevated, in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain).accessibilityLabel(key)
                .accessibilityIdentifier("stage.keypad." + key)
            }
        }
        .disabled(reserving)
    }

    private func close() {
        guard !reserving else { return }
        typing = false
        onClose()
    }
    private func reserve() {
        guard !reserving, !searching, let song = selectedSong else { return }
        // Claim synchronously before suspension. Distinct later reservations
        // remain legal, including the same song for a different singer.
        reserving = true
        typing = false
        Task {
            let succeeded = await model.enqueue(song, singer: singerName)
            if succeeded {
                model.sessionNotice = "Reserved " + song.title + "."
                // Keep the disabled panel through a rapid second touch so it
                // cannot land on an unrelated stage button after dismissal.
                try? await Task.sleep(for: .milliseconds(350))
                onClose()
            }
            reserving = false
        }
    }
}
