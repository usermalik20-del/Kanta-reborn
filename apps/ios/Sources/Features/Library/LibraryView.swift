import GeloKaraokeCore
import LibraryStorage
import SwiftUI
import UniformTypeIdentifiers

@MainActor
struct LibraryView: View {
    @StateObject private var model: LibraryViewModel
    @State private var showingLibraryImporter = false
    @State private var showingBackgroundImporter = false
    @State private var backgroundTarget: LibrarySourceRecord?
    @State private var showingSoundFontImporter = false
    @State private var soundFontTarget: LibrarySourceRecord?

    init(services: AppServices) {
        _model = StateObject(
            wrappedValue: LibraryViewModel(
                registry: services.registry,
                library: services.library,
                controller: services.controller,
                prewarmSoundFont: { selection in
                    await services.prewarmSoundFont(
                        selection
                    )
                }
            )
        )
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                heroCard

                if model.isWorking {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("Checking and updating your library…")
                            .font(KantaTypography.subheadline if hasattr(KantaTypography, 'subheadline') else .subheadline)
                            .foregroundStyle(KantaColors.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 4)
                }

                librariesSection
                starterSongsSection
                indexedLibraryGuideCard
                supportedMediaCard
            }
            .padding()
            .frame(maxWidth: 900)
            .frame(maxWidth: .infinity)
        }
        .background(GeloVisualStyle.pageGradient)
        .navigationTitle("Library")
        .navigationBarTitleDisplayMode(.large)
        .task {
            await model.load()
        }
        .fileImporter(
            isPresented: $showingLibraryImporter,
            allowedContentTypes: [.folder],
            allowsMultipleSelection: false
        ) { result in
            handleLibraryImport(result)
        }
        .fileImporter(
            isPresented: $showingBackgroundImporter,
            allowedContentTypes: [.movie, .image],
            allowsMultipleSelection: true
        ) { result in
            handleBackgroundImport(result)
        }
        .fileImporter(
            isPresented: $showingSoundFontImporter,
            allowedContentTypes: soundFontContentTypes,
            allowsMultipleSelection: true
        ) { result in
            handleSoundFontImport(result)
        }
        .sheet(item: $model.importSummary) { summary in
            BackgroundImportSummaryView(summary: summary)
        }
        .sheet(item: $model.soundFontImportSummary) { summary in
            SoundFontImportSummaryView(summary: summary)
        }
        .alert(
            "Library Error",
            isPresented: errorAlertBinding
        ) {
            Button("OK", role: .cancel) {
                model.errorMessage = nil
            }
        } message: {
            Text(model.errorMessage ?? "")
        }
    }

    private var starterSongsSection: some View {
        KantaCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .firstTextBaseline) {
                    Text("Starter Songs")
                        .font(KantaTypography.sectionHeader)
                        .foregroundStyle(KantaColors.textPrimary)
                    Spacer()
                    Button("See all") {
                        // Action
                    }
                    .font(KantaTypography.metadata)
                    .foregroundStyle(KantaColors.cyan)
                }

                Text("Three original songs with timed lyrics and built-in sound. Play offline, or use your imported SoundFont.")
                    .font(KantaTypography.metadata)
                    .foregroundStyle(KantaColors.textSecondary)

                LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 16) {
                    ForEach(StarterKaraokeStore.identifiers, id: \.self) { identifier in
                        let song = StarterKaraokeStore.song(identifier)
                        let isAvailable = model.availableStarters.contains(identifier)

                        VStack(alignment: .leading, spacing: 8) {
                            ZStack(alignment: .bottomTrailing) {
                                KantaCoverArt(
                                    seed: song.title + " " + song.artist,
                                    cornerRadius: 22
                                )
                                .frame(height: 150)

                                if isAvailable {
                                    HStack(spacing: 8) {
                                        Button {
                                            Task { await model.playStarter(identifier, queueOnly: false) }
                                        } label: {
                                            Image(systemName: "play.fill")
                                                .font(.system(size: 14, weight: .bold))
                                                .foregroundStyle(.white)
                                                .frame(width: 40, height: 40)
                                                .background(KantaColors.primaryGradient, in: Circle())
                                        }
                                        .accessibilityIdentifier("starter.play." + identifier)

                                        Button {
                                            Task { await model.playStarter(identifier, queueOnly: true) }
                                        } label: {
                                            Image(systemName: "text.badge.plus")
                                                .font(.system(size: 14, weight: .bold))
                                                .foregroundStyle(.white)
                                                .frame(width: 40, height: 40)
                                                .background(.ultraThinMaterial, in: Circle())
                                        }
                                    }
                                    .padding(12)
                                } else {
                                    Button("Restore") {
                                        Task { await model.changeStarter(identifier, restore: true) }
                                    }
                                    .font(KantaTypography.caption)
                                    .buttonStyle(.borderedProminent)
                                    .tint(KantaColors.primary)
                                    .padding(12)
                                }
                            }

                            Text(song.title)
                                .font(KantaTypography.songTitle)
                                .foregroundStyle(KantaColors.textPrimary)
                                .lineLimit(1)

                            Text("Starter · 3:12")
                                .font(KantaTypography.metadata)
                                .foregroundStyle(KantaColors.textSecondary)
                        }
                        .accessibilityElement(children: .contain)
                        .accessibilityLabel(song.title)
                    }
                }

                if model.availableStarters.count < StarterKaraokeStore.identifiers.count {
                    Button("Restore All Missing Songs") {
                        Task { await model.changeStarter(nil, restore: true) }
                    }
                    .disabled(model.isWorking)
                    .font(KantaTypography.songTitle)
                    .tint(KantaColors.primary)
                }

                if let feedback = model.starterFeedback {
                    Text(feedback)
                        .font(KantaTypography.caption)
                        .foregroundStyle(KantaColors.textSecondary)
                }
            }
        }
    }

    private var heroCard: some View {
        KantaCard {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Your karaoke, your storage")
                        .font(KantaTypography.sectionHeader)
                        .foregroundStyle(KantaColors.textPrimary)
                    Text(
                        "Bring your own library from Files or iCloud Drive. " +
                        "KANTA keeps playback local and does not upload your media."
                    )
                    .font(KantaTypography.metadata)
                    .foregroundStyle(KantaColors.textSecondary)
                }

                HStack(alignment: .center, spacing: 14) {
                    Image(systemName: "music.note.house.fill")
                        .font(.system(size: 38))
                        .foregroundStyle(KantaColors.primary)
                        .frame(width: 58, height: 58)
                        .background(
                            KantaColors.primary.opacity(0.12),
                            in: RoundedRectangle(
                                cornerRadius: 16,
                                style: .continuous
                            )
                        )

                    VStack(alignment: .leading, spacing: 5) {
                        Text("Add a karaoke folder")
                            .font(KantaTypography.songTitle)
                            .foregroundStyle(KantaColors.textPrimary)
                        Text(
                            "Put your library-index file, media-container file, and optional .sf2 " +
                            "SoundFont files in one folder, then select that " +
                            "folder here. KANTA detects and links them automatically."
                        )
                        .font(KantaTypography.metadata)
                        .foregroundStyle(KantaColors.textSecondary)
                    }
                }

                VStack(spacing: 10) {
                    Button {
                        showingLibraryImporter = true
                    } label: {
                        Label(
                            "Add Karaoke Folder",
                            systemImage: "folder.badge.plus"
                        )
                        .font(KantaTypography.songTitle)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(KantaColors.primaryGradient, in: Capsule())
                    }
                    .accessibilityIdentifier("library.addFolder")
                    .disabled(model.isWorking)

                    HStack(spacing: 12) {
                        Image(systemName: "externaldrive.badge.icloud")
                            .font(.title3)
                            .foregroundStyle(.secondary)
                            .frame(width: 34)

                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 7) {
                                Text("Google Drive")
                                    .font(KantaTypography.songTitle)
                                    .foregroundStyle(KantaColors.textPrimary)

                                Text("COMING LATER")
                                    .font(KantaTypography.caption)
                                    .foregroundStyle(.secondary)
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 3)
                                    .background(
                                        .thinMaterial,
                                        in: Capsule()
                                    )
                            }

                            Text(
                                "Planned cloud import source. No Google sign-in " +
                                "or Drive access is enabled in this version."
                            )
                            .font(KantaTypography.caption)
                            .foregroundStyle(.secondary)
                        }

                        Spacer(minLength: 8)

                        Image(systemName: "lock.fill")
                            .foregroundStyle(.tertiary)
                    }
                    .padding(12)
                    .background(
                        .thinMaterial,
                        in: RoundedRectangle(
                            cornerRadius: 16,
                            style: .continuous
                        )
                    )
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(
                        "Google Drive, coming later. Not available in this version."
                    )
                }
            }
        }
    }

    @ViewBuilder
    private var librariesSection: some View {
        if model.sources.isEmpty {
            KantaCard {
                KantaEmptyState(
                    icon: "folder",
                    title: "Choose a Folder",
                    description: "Use Files or iCloud Drive. KANTA stores a security-scoped bookmark so the library can be reopened later.",
                    actionTitle: "Choose a Folder",
                    action: { showingLibraryImporter = true },
                    style: .compact
                )
                .accessibilityIdentifier("library.chooseFolder")
            }
        } else {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Connected Libraries")
                        .font(KantaTypography.sectionHeader)
                        .foregroundStyle(KantaColors.textPrimary)
                    Spacer()
                }
                .padding(.horizontal, 4)

                ForEach(model.sources) { source in
                    sourceCard(source)
                }
            }
        }
    }

    private func sourceCard(
        _ source: LibrarySourceRecord
    ) -> some View {
        KantaCard {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(source.displayName)
                        .font(KantaTypography.sectionHeader)
                        .foregroundStyle(KantaColors.textPrimary)
                    Text("User-supplied Files/iCloud library")
                        .font(KantaTypography.metadata)
                        .foregroundStyle(KantaColors.textSecondary)
                }

                if let scan = source.lastScan {
                    if let counts = model.songCounts[source.id] {
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            Text(counts.searchableCount.formatted())
                                .font(KantaTypography.title)
                                .monospacedDigit()
                                .foregroundStyle(KantaColors.textPrimary)
                            Text("searchable titles")
                                .font(KantaTypography.metadata)
                                .foregroundStyle(KantaColors.textSecondary)
                        }
                        .accessibilityElement(children: .combine)

                        Text(
                            counts.playableCount.formatted() + " playable · "
                            + counts.catalogOnlyCount.formatted() + " catalog only"
                        )
                        .font(KantaTypography.caption)
                        .foregroundStyle(KantaColors.textSecondary)
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            GeloStatusPill(
                                title:
                                    "\(scan.looseSongCount) loose songs",
                                systemImage: "music.note"
                            )
                            if scan.hasIndexedLibrary {
                                GeloStatusPill(
                                    title: "Compatible library ready",
                                    systemImage: "checkmark.seal"
                                )
                            } else if scan.hasLibraryIndex {
                                GeloStatusPill(
                                    title: "Search-only titles · media unavailable",
                                    systemImage: "exclamationmark.triangle"
                                )
                            } else if scan.hasLibraryContainer {
                                GeloStatusPill(
                                    title: "Library index not detected",
                                    systemImage: "exclamationmark.triangle"
                                )
                            }
                            if scan.soundFontCount > 0 {
                                GeloStatusPill(
                                    title:
                                        "\(scan.soundFontCount) SoundFont" +
                                        (scan.soundFontCount == 1 ? "" : "s"),
                                    systemImage: "waveform"
                                )
                            }
                        }
                    }
                } else {
                    Text("This library has not been scanned yet.")
                        .font(KantaTypography.metadata)
                        .foregroundStyle(KantaColors.textSecondary)
                }

                HStack {
                    Menu {
                        Button {
                            soundFontTarget = source
                            showingSoundFontImporter = true
                        } label: {
                            Label(
                                "Import SoundFonts",
                                systemImage: "waveform.badge.plus"
                            )
                        }

                        if !soundFontAssets(for: source).isEmpty {
                            Menu("Preferred SoundFont") {
                                Button {
                                    Task {
                                        await model.setPreferredSoundFont(
                                            nil,
                                            for: source
                                        )
                                    }
                                } label: {
                                    preferredSoundFontMenuLabel(
                                        title: "Automatic",
                                        selected:
                                            source
                                                .preferredSoundFontRelativePath
                                                == nil
                                    )
                                }

                                ForEach(
                                    soundFontAssets(for: source),
                                    id: \.relativePath
                                ) { asset in
                                    Button {
                                        Task {
                                            await model.setPreferredSoundFont(
                                                asset.relativePath,
                                                for: source
                                            )
                                        }
                                    } label: {
                                        preferredSoundFontMenuLabel(
                                            title:
                                                URL(
                                                    fileURLWithPath:
                                                        asset.relativePath
                                                )
                                                .lastPathComponent,
                                            selected:
                                                source
                                                    .preferredSoundFontRelativePath?
                                                    .caseInsensitiveCompare(
                                                        asset.relativePath
                                                    ) == .orderedSame
                                        )
                                    }
                                }
                            }
                        }

                        Button {
                            backgroundTarget = source
                            showingBackgroundImporter = true
                        } label: {
                            Label(
                                "Add to " + KantaTerminology.mySet,
                                systemImage: "photo.badge.plus"
                            )
                        }

                        Divider()

                        Button {
                            Task {
                                await model.rescan(source)
                            }
                        } label: {
                            Label(
                                "Rescan",
                                systemImage: "arrow.clockwise"
                            )
                        }

                        Button(role: .destructive) {
                            Task {
                                await model.remove(source)
                            }
                        } label: {
                            Label(
                                "Remove Library",
                                systemImage: "trash"
                            )
                        }
                    } label: {
                        Label(
                            "Library Options",
                            systemImage: "ellipsis.circle"
                        )
                    }
                    .accessibilityLabel("Library options")
                }
            }
        }
    }

    private var indexedLibraryGuideCard: some View {
        KantaCard {
            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Compatible library setup")
                        .font(KantaTypography.sectionHeader)
                        .foregroundStyle(KantaColors.textPrimary)
                    Text("One folder is enough. KANTA scans and connects the files automatically.")
                        .font(KantaTypography.metadata)
                        .foregroundStyle(KantaColors.textSecondary)
                }

                VStack(alignment: .leading, spacing: 14) {
                    instruction(
                        number: "1",
                        title: "Put the files together",
                        text:
                            "Create one folder in Files or iCloud Drive and place " +
                            "your compatible title index inside it. Add the matching media container " +
                            "to enable playback, and your " +
                            ".sf2 SoundFont file in the same folder if the library " +
                            "uses MIDI/KAR songs."
                    )

                    VStack(alignment: .leading, spacing: 6) {
                        Text("Example")
                            .font(KantaTypography.songTitle)
                            .foregroundStyle(KantaColors.textPrimary)

                        Text(
                            """
                            My Karaoke Library/
                            ├─ <compatible index file>
                            ├─ <matching media container>
                            ├─ any-name.sf2
                            └─ optional-second-bank.sf2
                            """
                        )
                        .font(KantaTypography.badge)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                    }
                    .padding(12)
                    .background(
                        .thinMaterial,
                        in: RoundedRectangle(
                            cornerRadius: 14,
                            style: .continuous
                        )
                    )

                    instruction(
                        number: "2",
                        title: "Select the folder in KANTA",
                        text:
                            "Tap Add Karaoke Folder and choose that folder. " +
                            "KANTA scans it recursively and makes recognized index titles " +
                            "searchable even without playable media. A validated matching " +
                            "media container enables Reserve and playback. KANTA detects " +
                            "available SoundFonts and builds the song list " +
                            "without modifying the original files."
                    )

                    instruction(
                        number: "3",
                        title: "Start singing",
                        text:
                            "Open Host → CONTROL, search the imported song list, " +
                            "and tap Reserve. The first reserved song plays next " +
                            "after the current song finishes."
                    )

                    Divider()

                    VStack(alignment: .leading, spacing: 10) {
                        Label(
                            "Automatic checks",
                            systemImage: "checkmark.shield"
                        )
                        .font(KantaTypography.songTitle)
                        .foregroundStyle(KantaColors.textPrimary)

                        troubleshootingRow(
                            title: "Indexed library",
                            text:
                                "KANTA shows Compatible library ready when both library-index file " +
                                "and media-container file are present. If one is missing, the " +
                                "library card tells you which file is needed."
                        )

                        troubleshootingRow(
                            title: "SoundFont",
                            text:
                                "If one .sf2 is available, KANTA can use it " +
                                "automatically for MIDI/KAR playback. If several " +
                                "are present, Automatic selection stays available " +
                                "and you can choose another under Library Options."
                        )

                        troubleshootingRow(
                            title: "No songs appear",
                            text:
                                "Make sure matching index and media container files are a matching pair, " +
                                "then use Library Options → Rescan. KANTA never edits " +
                                "either file."
                        )
                    }

                    Text(
                        "KANTA treats these files as Bring Your Own Library content. " +
                        "Only use libraries and SoundFonts you are authorized to use."
                    )
                    .font(KantaTypography.caption)
                    .foregroundStyle(KantaColors.textSecondary)
                }
            }
        }
    }

    private var supportedMediaCard: some View {
        KantaCard {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Song formats")
                        .font(KantaTypography.sectionHeader)
                        .foregroundStyle(KantaColors.textPrimary)
                    Text("Playback support available in the current offline engine.")
                        .font(KantaTypography.metadata)
                        .foregroundStyle(KantaColors.textSecondary)
                }

                let names = MediaKind.allCases.map(displayName)
                ViewThatFits {
                    HStack(spacing: 8) {
                        ForEach(names, id: \.self) { name in
                            GeloStatusPill(
                                title: name,
                                systemImage: "waveform"
                            )
                        }
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(names, id: \.self) { name in
                            GeloStatusPill(
                                title: name,
                                systemImage: "waveform"
                            )
                        }
                    }
                }
            }
        }
    }

    private var soundFontContentTypes: [UTType] {
        [
            UTType(filenameExtension: "sf2")
                ?? .data
        ]
    }

    private func soundFontAssets(
        for source: LibrarySourceRecord
    ) -> [DiscoveredLibraryAsset] {
        (source.lastScan?.assets ?? [])
            .filter {
                $0.kind == .soundFont
                && $0.relativePath
                    .lowercased()
                    .hasSuffix(".sf2")
            }
            .sorted {
                $0.relativePath.localizedStandardCompare(
                    $1.relativePath
                ) == .orderedAscending
            }
    }

    @ViewBuilder
    private func preferredSoundFontMenuLabel(
        title: String,
        selected: Bool
    ) -> some View {
        if selected {
            Label(title, systemImage: "checkmark")
        } else {
            Text(title)
        }
    }

    private var errorAlertBinding: Binding<Bool> {
        Binding(
            get: { model.errorMessage != nil },
            set: { isPresented in
                if !isPresented {
                    model.errorMessage = nil
                }
            }
        )
    }

    private func handleLibraryImport(
        _ result: Result<[URL], Error>
    ) {
        switch result {
        case let .success(urls):
            guard let url = urls.first else { return }
            Task {
                await model.importFolder(url)
            }

        case let .failure(error):
            model.errorMessage = error.localizedDescription
        }
    }

    private func handleBackgroundImport(
        _ result: Result<[URL], Error>
    ) {
        guard let target = backgroundTarget else {
            model.errorMessage =
                "Choose a destination library before adding backgrounds."
            return
        }

        switch result {
        case let .success(urls):
            Task {
                await model.importBackgroundFiles(
                    urls,
                    to: target
                )
                backgroundTarget = nil
            }

        case let .failure(error):
            backgroundTarget = nil
            model.errorMessage = error.localizedDescription
        }
    }

    private func handleSoundFontImport(
        _ result: Result<[URL], Error>
    ) {
        guard let target = soundFontTarget else {
            model.errorMessage =
                "Choose a destination library before importing SoundFonts."
            return
        }

        switch result {
        case let .success(urls):
            Task {
                await model.importSoundFonts(
                    urls,
                    to: target
                )
                soundFontTarget = nil
            }

        case let .failure(error):
            soundFontTarget = nil
            model.errorMessage = error.localizedDescription
        }
    }

    private func troubleshootingRow(
        title: String,
        text: String
    ) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(KantaColors.primary)
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(KantaTypography.songTitle)
                    .foregroundStyle(KantaColors.textPrimary)
                Text(text)
                    .font(KantaTypography.caption)
                    .foregroundStyle(KantaColors.textSecondary)
            }
        }
    }

    private func instruction(
        number: String,
        title: String,
        text: String
    ) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(number)
                .font(KantaTypography.songTitle)
                .foregroundStyle(.white)
                .frame(width: 30, height: 30)
                .background(
                    KantaColors.primary,
                    in: Circle()
                )

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(KantaTypography.songTitle)
                    .foregroundStyle(KantaColors.textPrimary)
                Text(text)
                    .font(KantaTypography.metadata)
                    .foregroundStyle(KantaColors.textSecondary)
            }
        }
    }

    private func displayName(
        for kind: MediaKind
    ) -> String {
        switch kind {
        case .midi:
            "MIDI"
        case .kar:
            "KAR"
        case .mp3:
            "MP3"
        case .wav:
            "WAV"
        case .legacyContainer:
            "Legacy"
        case .unknown:
            "Inspect"
        }
    }
}

@MainActor
private struct SoundFontImportSummaryView: View {
    let summary: SoundFontImportSummary
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    LabeledContent(
                        "Imported",
                        value: String(summary.importedCount)
                    )
                    LabeledContent(
                        "Skipped",
                        value: String(summary.skippedCount)
                    )
                }

                Section("SoundFonts") {
                    ForEach(summary.items) { item in
                        HStack(alignment: .top, spacing: 12) {
                            Image(
                                systemName:
                                    item.imported
                                    ? "checkmark.circle.fill"
                                    : "xmark.circle.fill"
                            )
                            .foregroundStyle(
                                item.imported
                                    ? .green
                                    : .orange
                            )

                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.fileName)
                                    .font(KantaTypography.songTitle)
                                    .foregroundStyle(KantaColors.textPrimary)
                                Text(item.detail)
                                    .font(KantaTypography.caption)
                                    .foregroundStyle(KantaColors.textSecondary)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                Section {
                    Text(
                        "BYOL SoundFonts remain user-supplied content. " +
                        "KANTA copies the original SF2 bytes without " +
                        "compression, conversion, or upload."
                    )
                    .font(KantaTypography.caption)
                    .foregroundStyle(KantaColors.textSecondary)
                }
            }
            .navigationTitle("SoundFont Import")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .tint(KantaColors.primary)
    }
}

@MainActor
private struct BackgroundImportSummaryView: View {
    let summary: BackgroundImportSummary
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    LabeledContent(
                        "Imported",
                        value: String(summary.importedCount)
                    )
                    LabeledContent(
                        "Skipped",
                        value: String(summary.skippedCount)
                    )
                }

                Section("Files") {
                    ForEach(summary.items) { item in
                        HStack(alignment: .top, spacing: 12) {
                            Image(
                                systemName:
                                    item.imported
                                    ? "checkmark.circle.fill"
                                    : "xmark.circle.fill"
                            )
                            .foregroundStyle(
                                item.imported
                                    ? .green
                                    : .orange
                            )

                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.fileName)
                                    .font(KantaTypography.songTitle)
                                    .foregroundStyle(KantaColors.textPrimary)
                                Text(item.detail)
                                    .font(KantaTypography.caption)
                                    .foregroundStyle(KantaColors.textSecondary)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }

                Section {
                    Text(
                        "No selected file was re-encoded, compressed, " +
                        "resized, or uploaded."
                    )
                    .font(KantaTypography.caption)
                    .foregroundStyle(KantaColors.textSecondary)
                }
            }
            .navigationTitle("Background Import")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .tint(KantaColors.primary)
    }
}
