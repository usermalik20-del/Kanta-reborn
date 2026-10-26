import AVFoundationPlayback
import GeloKaraokeCore
import LocalNetworkControl
import MicrophoneScoring
import SwiftUI

@MainActor
struct HostView: View {
    private let onOpenNavigation: () -> Void
    @ObservedObject var presentationGate: KantaPresentationGate
    let navigationSheetPresented: Bool
    @StateObject private var camera = KantaCameraBackground()
    @State private var pendingFullscreen = false
    @State private var dismissedHostSheet: HostSheet?
    @State private var activeHostSheet: HostSheet? =
        ProcessInfo.processInfo.environment["KANTA_CONTROL_EXPANDED"] == "1" ? .controls : nil
    @State private var activeEmbeddedStageSheet: KantaStageSheet?
    private enum HostSheet: String, Identifiable {
        case controls, sessionSave, favorites
        var id: String { rawValue }

        var presentationOwner: KantaPresentationGate.Owner { .host }
    }
    @StateObject private var model: HostViewModel
    @ObservedObject private var networkHost: BonjourHostAdvertiser
    @ObservedObject private var microphonePitchMonitor:
        MicrophonePitchMonitor
    @ObservedObject private var settings: KantaUserSettings
    @ObservedObject private var externalStage = KantaExternalStageCoordinator.shared
    @State private var singerName = ""
    @State private var showingYouTubePreview = false
    @StateObject private var audioInputs = KantaAudioInputDiagnostics()
    @ObservedObject private var intermission: KantaIntermissionMusic
    private let controller: LocalKaraokeController?
    private let audioOwnership: KaraokeAudioOwnership
    private let partySFX: KantaPartySFXEngine
    private let diagnostics: KantaSupportDiagnostics
    @State private var showControlCenter =
        ProcessInfo.processInfo.environment[
            "KANTA_CONTROL_EXPANDED"
        ] == "1"
    @State private var showingFullscreenPlayer =
        ProcessInfo.processInfo.environment[
            "KANTA_FULLSCREEN"
        ] == "1"

    init(services: AppServices, presentationGate: KantaPresentationGate = KantaPresentationGate(), navigationSheetPresented: Bool = false, onOpenNavigation: @escaping () -> Void = {}) {
        self.presentationGate = presentationGate
        self.navigationSheetPresented = navigationSheetPresented
        self.onOpenNavigation = onOpenNavigation
        controller = services.controller
        audioOwnership = services.audioOwnership
        _intermission = ObservedObject(wrappedValue: services.intermission)
        partySFX = services.partySFX
        diagnostics = services.diagnostics
        _networkHost = ObservedObject(
            wrappedValue: services.networkHost
        )
        _microphonePitchMonitor = ObservedObject(
            wrappedValue: services.microphonePitchMonitor
        )
        _settings = ObservedObject(
            wrappedValue: services.settings
        )
        _model = StateObject(
            wrappedValue: HostViewModel(
                controller: services.controller,
                library: services.library,
                savedSets: services.savedSets
            )
        )
    }

    @State private var remoteExpanded = false

    var body: some View {
        ZStack {
            hostContent
        }
        .sheet(item: $activeHostSheet, onDismiss: handleHostSheetDismissal, content: hostSheetContent)
        .navigationTitle("Karaoke Host")
        .navigationBarTitleDisplayMode(.large)
        .background { hostPresentationAnchors }
        .background { KantaBackground().allowsHitTesting(false) }
        .onChange(of: model.snapshot.transport) {
            intermission.sync(model.snapshot.transport)
        }
        .onChange(of: settings.scoringEnabled) {
            diagnostics.record(
                settings.scoringEnabled ? .scoringEnabled : .scoringDisabled
            )
            microphonePitchMonitor.configureEffects(settings.vocalEffects)
            model.setScoringEnabled(
                settings.scoringEnabled
            )

            if settings.scoringEnabled || settings.vocalEffects.monitorEnabled {
                Task {
                    await microphonePitchMonitor.start()
                }
            } else {
                microphonePitchMonitor.stop()
            }
        }
        .onChange(of: settings.scoringLatencyMilliseconds) {
            model.setScoringLatencyMilliseconds(
                Int64(settings.scoringLatencyMilliseconds)
            )
        }
        .onChange(of: settings.vocalEffects) {
            microphonePitchMonitor.configureEffects(settings.vocalEffects)
            if settings.scoringEnabled || settings.vocalEffects.monitorEnabled {
                Task {
                    await microphonePitchMonitor.start()
                }
            } else {
                microphonePitchMonitor.stop()
            }
        }
        .onChange(
            of: microphonePitchMonitor.observation
        ) {
            if let observation =
                microphonePitchMonitor.observation {
                model.ingestPitchObservation(observation)
            }
        }
        .task {
            diagnostics.record(.hostOpened)
            externalStage.activate(host: model, settings: settings)
            intermission.activate()
            if !isDemoMode {
                networkHost.start()
            }
            model.setScoringEnabled(
                settings.scoringEnabled
            )
            model.setScoringLatencyMilliseconds(
                Int64(settings.scoringLatencyMilliseconds)
            )

            if settings.scoringEnabled || settings.vocalEffects.monitorEnabled {
                await microphonePitchMonitor.start()
            }

            await model.run()
            intermission.sync(model.snapshot.transport)
        }
        .onChange(of: activeHostSheet) { previous, current in
            if let previous, current == nil {
                presentationGate.beginDismissal(previous.presentationOwner)
                dismissedHostSheet = previous
            }
        }
        .onChange(of: model.showingSessionSavePrompt) { _, pending in
            if !pending && activeHostSheet == .sessionSave {
                presentationGate.beginDismissal(.host)
                activeHostSheet = nil
            }
        }
        .onDisappear {
            camera.stop()
            diagnostics.record(.hostClosed)
            externalStage.deactivate(host: model)
            intermission.deactivate()
            Task { await partySFX.stopAll() }
            microphonePitchMonitor.stop()
            if !isDemoMode {
                networkHost.stop()
            }
        }
        .alert(
            "Playback Error",
            isPresented: Binding(
                get: { model.errorMessage != nil },
                set: {
                    if !$0 {
                        model.errorMessage = nil
                    }
                }
            )
        ) {
            Button("OK", role: .cancel) {
                model.errorMessage = nil
            }
        } message: {
            Text(model.errorMessage ?? "")
        }
    }



    private func handleHostSheetDismissal() {
        if dismissedHostSheet == .sessionSave {
            model.showingSessionSavePrompt = false
        }
        dismissedHostSheet = nil
        if pendingFullscreen {
            pendingFullscreen = false
            // Queue fullscreen before releasing CONTROL so the gate owns the
            // transition continuously, including while the sheet animates out.
            presentationGate.request(.fullscreen) { showingFullscreenPlayer = true }
        }
        presentationGate.completeDismissal(.host)
    }

    @ViewBuilder
    private func hostSheetContent(_ sheet: HostSheet) -> some View {
        switch sheet {
        case .controls:
            NavigationStack {
                hostControls
                    .sheet(isPresented: $showingYouTubePreview) {
                        KantaYouTubePreviewSheet(
                            localPlaybackActive: model.snapshot.transport == .playing,
                            controller: controller,
                            audioOwnership: audioOwnership
                        )
                    }
                    .safeAreaInset(edge: .bottom) {
                        Button(action: openFullscreenPlayer) {
                            Label("Open fullscreen player", systemImage: "arrow.up.left.and.arrow.down.right")
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        }
                        .buttonStyle(.borderedProminent)
                        .accessibilityIdentifier("controls.openFullscreen")
                        .padding(.horizontal)
                        .padding(.vertical, 8)
                        .background(.regularMaterial)
                    }
                    .navigationTitle("CONTROL")
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Done") {
                                presentationGate.beginDismissal(.host)
                                activeHostSheet = nil
                            }
                            .accessibilityIdentifier("controls.done")
                        }
                    }
            }
        case .sessionSave:
            SaveSessionFavoritesView(model: model)
        case .favorites:
            SavedFavoritesView(model: model)
        }
    }

    private var hostPresentationAnchors: some View {
        ZStack {
            SingingFullscreenPresentation(
                isPresented: $showingFullscreenPlayer,
                model: model,
                backgroundsEnabled: settings.stageVisualsEnabled,
                selectedPreset: settings.selectedVisualPreset,
                camera: camera,
                presentationGate: presentationGate,
                microphoneBusy: microphonePitchMonitor.status == .running
                    || microphonePitchMonitor.status == .requestingPermission
            )
            .frame(width: 1, height: 1)

            KantaEmbeddedStageSheetPresentation(
                sheet: $activeEmbeddedStageSheet,
                model: model,
                singerName: $singerName,
                microphoneBusy: microphonePitchMonitor.status == .running
                    || microphonePitchMonitor.status == .requestingPermission,
                presentationGate: presentationGate
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .allowsHitTesting(false)
    }

    private var hostContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                stageGreeting
                embeddedStage
                inlineRemote

                GeloSectionCard("External TV / Stage Display") {
                    Text(externalStage.isPresenting ? "Extended screen active" : "No independent display detected")
                    Text("Connect HDMI/USB-C or an AirPlay display with extended-screen support. The TV shows lyrics and backgrounds; controls stay here. Mirroring also shows this device’s controls.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(16)
            .frame(maxWidth: 900)
            .frame(maxWidth: .infinity)
        }
        // No opaque fill here: the gradient canvas and its blooms are attached
        // behind the whole Host view and must stay visible.
    }

    /// Operator-facing header: a greeting, who is hosting, and their initial.
    private var stageGreeting: some View {
        HStack(alignment: .center, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(greetingText)
                    .font(KantaTypography.caption)
                    .foregroundStyle(KantaColors.textSecondary)

                // The stage header below already names the screen, so this
                // line must not repeat it.
                Text(singerName.isEmpty ? "Ready when you are" : singerName)
                    .font(KantaTypography.title)
                    .foregroundStyle(KantaColors.textPrimary)
            }

            Spacer(minLength: 0)

            Text(hostInitials)
                .font(KantaTypography.songTitle)
                .foregroundStyle(KantaColors.textPrimary)
                .frame(width: 46, height: 46)
                .background(KantaColors.primaryGradient, in: Circle())
                .overlay(Circle().stroke(KantaColors.hairline, lineWidth: 1))
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("host.greeting")
    }

    private var greetingText: String {
        switch Calendar.current.component(.hour, from: Date()) {
        case 0..<12: return "Good morning"
        case 12..<18: return "Good afternoon"
        default: return "Good evening"
        }
    }

    private var hostInitials: String {
        let words = singerName.split { !$0.isLetter && !$0.isNumber }
        let letters = words.prefix(2).compactMap { $0.first.map(String.init) }
        let combined = letters.joined().uppercased()
        return combined.isEmpty ? "KA" : combined
    }

    private var embeddedStage: some View {
        KantaMainPlayerView(
            model: model, camera: camera, presentationGate: presentationGate,
            backgroundsEnabled: settings.stageVisualsEnabled,
            selectedPreset: settings.selectedVisualPreset,
            singerName: $singerName,
            microphoneBusy: microphonePitchMonitor.status == .running
                || microphonePitchMonitor.status == .requestingPermission,
            onControls: {
                presentationGate.request(.host) { activeHostSheet = .controls }
            },
            onMore: onOpenNavigation,
            onSaveSession: {
                guard model.showingSessionSavePrompt else { return }
                // Keep the pending save available without stacking a Host sheet
                // over navigation or another active Host presentation.
                guard !navigationSheetPresented && activeHostSheet == nil else { return }
                presentationGate.request(.host) { activeHostSheet = .sessionSave }
            },
            embedded: true,
            onExpand: openFullscreenPlayer,
            setEmbeddedStageSheet: { stageSheet in
                activeEmbeddedStageSheet = stageSheet
            }
        )
    }

    private var inlineRemote: some View {
        DisclosureGroup(isExpanded: $remoteExpanded) {
            InAppControlCenterView(
                model: model,
                backgroundsEnabled: $settings.stageVisualsEnabled,
                selectedPreset: $settings.selectedVisualPreset,
                singerName: $singerName,
                microphoneBusy: microphonePitchMonitor.status == .running
                    || microphonePitchMonitor.status == .requestingPermission
            )
            .padding(.top, 16)
        } label: {
            Label("Remote Control", systemImage: "rectangle.and.hand.point.up.left")
                .font(.headline)
                .accessibilityIdentifier("host.remotePanel.toggle")
        }
        .padding(18)
        .background(KantaColors.surface, in: RoundedRectangle(cornerRadius: 20))
    }

    private func openFullscreenPlayer() {
        guard activeHostSheet != nil else {
            presentationGate.request(.fullscreen) { showingFullscreenPlayer = true }
            return
        }
        pendingFullscreen = true
        presentationGate.beginDismissal(.host)
        activeHostSheet = nil
    }

    private var hostControls: some View {
        List {
            Section("Online video") {
                Button("Open YouTube video") { showingYouTubePreview = true }
                    .accessibilityIdentifier("youtube.preview.open")
            }
            Section {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        showControlCenter.toggle()
                    }
                } label: {
                    HStack {
                        Label(
                            KantaTerminology.control,
                            systemImage: "slider.horizontal.3"
                        )
                        .font(.headline)

                        Spacer()

                        Image(
                            systemName:
                                showControlCenter
                                ? "chevron.up"
                                : "chevron.down"
                        )
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityValue(
                    showControlCenter ? "Expanded" : "Collapsed"
                )

                if showControlCenter {
                    InAppControlCenterView(
                        model: model,
                        backgroundsEnabled: $settings.stageVisualsEnabled,
                        selectedPreset: $settings.selectedVisualPreset,
                        singerName: $singerName,
                        microphoneBusy: microphonePitchMonitor.status == .running
                            || microphonePitchMonitor.status == .requestingPermission
                    )
                    .listRowInsets(
                        EdgeInsets(
                            top: 12,
                            leading: 14,
                            bottom: 12,
                            trailing: 14
                        )
                    )
                }
            } footer: {
                Text(controlFooterText)
            }

            Section("Stage Display · External TV") {
                LabeledContent(
                    "External stage",
                    value: externalStage.isPresenting
                        ? "Extended screen active"
                        : externalStage.isConnected
                            ? "Connected · open Host"
                            : "No independent display detected"
                )
                Text(
                    "Connect HDMI/USB-C or an AirPlay display that supports " +
                    "extended-screen output. The TV shows only lyrics and " +
                    "backgrounds; this device remains CONTROL. If iOS " +
                    "mirrors instead of extending, KANTA cannot hide " +
                    "controls on the mirrored screen."
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            Section("Between-Song Music") {
                KantaIntermissionPanel(intermission: intermission)
            }

            Section("Singer Reservations") {
                TextField("Singer name (optional)", text: $singerName)
                    .textInputAutocapitalization(.words)
                Text(
                    "The selected singer is attached to new reservations; " +
                    "each song keeps its singer when the queue is reordered."
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            Section("Your Session") {
                if let notice = model.sessionNotice {
                    Text(notice)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .accessibilityAddTraits(.updatesFrequently)
                }
                LabeledContent(
                    "Played this session",
                    value: String(model.currentSessionSongCount)
                )

                Button {
                    Task { await model.endSession() }
                } label: {
                    Label("End Session", systemImage: "stop.circle")
                }
                .disabled(model.currentSessionSongCount == 0)

                Button {
                    presentationGate.request(.host) { activeHostSheet = .favorites }
                } label: {
                    Label(
                        "Saved Favorites (\(model.savedFavorites.count))",
                        systemImage: "heart.text.square"
                    )
                }

                Text(
                    "When you end the session, KANTA asks whether to " +
                    "save the songs you played. Saved collections can be " +
                    "renamed and loaded back into Up Next."
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            Section(secondDeviceControlTitle) {
                LabeledContent(
                    "Bonjour",
                    value: networkHostStatusText
                )

                LabeledContent("Pairing PIN") {
                    Text(networkHost.pairingCode.value)
                        .font(.title3.monospacedDigit())
                        .fontWeight(.semibold)
                        .textSelection(.enabled)
                        .accessibilityLabel("Pairing PIN")
                        .accessibilityValue(
                            networkHost.pairingCode.value
                        )
                }

                LabeledContent(
                    "Paired remotes",
                    value: String(
                        networkHost.pairedControllerCount
                    )
                )

                Button("Rotate Pairing PIN") {
                    networkHost.rotatePairingCode()
                }

                Text(secondDeviceControlHelp)
                .font(.footnote)
                .foregroundStyle(.secondary)
            }

            if let completedScore = model.completedScore {
                Section("Last Score") {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(completedScore.song.title)
                                    .font(.headline)
                                Text(
                                    String(
                                        format: "%.0f / 100",
                                        completedScore.score.overall
                                    )
                                )
                                .font(.largeTitle.bold())
                            }

                            Spacer()

                            Button {
                                model.completedScore = nil
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundStyle(.secondary)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Dismiss last score")
                        }

                        HStack {
                            LabeledContent(
                                "Pitch",
                                value: String(
                                    format: "%.0f%%",
                                    completedScore.score.pitchAccuracy
                                )
                            )
                            LabeledContent(
                                "Coverage",
                                value: String(
                                    format: "%.0f%%",
                                    completedScore.score.timingCoverage
                                )
                            )
                        }
                        .font(.subheadline)

                        Text(
                            "\(completedScore.observationCount) scored samples"
                        )
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }

            Section("Now Playing") {
                if let song = model.snapshot.nowPlaying {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(song.title)
                            .font(.headline)
                    }
                } else {
                    Text("Nothing playing")
                        .foregroundStyle(.secondary)
                }

                // Transport lives in the always-on bar pinned below, so the
                // sheet does not present a second, scroll-away copy.
            }

            if model.snapshot.nowPlaying != nil {
                Section("Stage") {
                    ZStack(alignment: .topTrailing) {
                        KaraokeStageView(
                            asset:
                                settings.stageVisualsEnabled
                                ? model.backgroundAsset
                                : nil,
                            builtInPreset:
                                settings.stageVisualsEnabled
                                ? settings.selectedVisualPreset
                                : nil,
                            isPlaying:
                                model.snapshot.transport == .playing,
                            lyricPage: model.lyricPage
                        )
                        .frame(
                            minHeight: 220,
                            idealHeight: 320,
                            maxHeight: 420
                        )

                        Button {
                            openFullscreenPlayer()
                        } label: {
                            Image(
                                systemName:
                                    "arrow.up.left.and.arrow.down.right"
                            )
                            .font(.headline)
                            .padding(11)
                            .background(
                                .ultraThinMaterial,
                                in: Circle()
                            )
                        }
                        .buttonStyle(.plain)
                        .padding(10)
                        .accessibilityLabel("Open fullscreen player")
                    }
                }
            }

            if model.hasLyrics {
                Section("Lyrics") {
                    if let previous =
                        model.lyricWindow.previous {
                        Text(previous.text)
                            .font(.body.weight(.medium))
                            .foregroundStyle(.secondary)
                            .frame(
                                maxWidth: .infinity,
                                alignment: .center
                            )
                            .multilineTextAlignment(.center)
                    }

                    if let current =
                        model.lyricWindow.current {
                        Text(current.text)
                            .font(
                                .system(
                                    size: 34,
                                    weight: .heavy,
                                    design: .rounded
                                )
                            )
                            .lineLimit(3)
                            .minimumScaleFactor(0.72)
                            .frame(
                                maxWidth: .infinity,
                                alignment: .center
                            )
                            .multilineTextAlignment(.center)
                            .accessibilityLabel(
                                "Current lyric: " + current.text
                            )
                    }

                    if let next = model.lyricWindow.next {
                        Text(next.text)
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .frame(
                                maxWidth: .infinity,
                                alignment: .center
                            )
                            .multilineTextAlignment(.center)
                    }
                }
            }

            if settings.scoringEnabled {
                Section("Scoring") {
                    LabeledContent(
                        "Microphone",
                        value: microphoneStatusText
                    )

                    LabeledContent(
                        "Song target",
                        value: scoringTargetText
                    )

                    if let score = model.liveScore {
                        LabeledContent(
                            "Live score",
                            value: String(
                                format: "%.0f / 100",
                                score.overall
                            )
                        )
                        LabeledContent(
                            "Pitch accuracy",
                            value: String(
                                format: "%.0f%%",
                                score.pitchAccuracy
                            )
                        )
                        LabeledContent(
                            "Timing coverage",
                            value: String(
                                format: "%.0f%%",
                                score.timingCoverage
                            )
                        )
                        LabeledContent(
                            "Scored samples",
                            value: String(
                                model.scoringObservationCount
                            )
                        )
                    }

                    if let observation =
                        microphonePitchMonitor.observation {
                        LabeledContent(
                            "Detected pitch",
                            value: String(
                                format: "%.1f Hz",
                                observation.frequencyHz
                            )
                        )

                        if let note =
                            observation.nearestMIDINote {
                            LabeledContent(
                                "Nearest MIDI note",
                                value: String(note)
                            )
                        }

                        if let cents =
                            observation.centsFromNearestNote {
                            LabeledContent(
                                "Pitch offset",
                                value: String(
                                    format: "%+.0f cents",
                                    cents
                                )
                            )
                        }

                        LabeledContent(
                            "Confidence",
                            value: String(
                                format: "%.0f%%",
                                observation.confidence * 100
                            )
                        )
                    }

                    Stepper(
                        value: Binding(
                            get: {
                                model.scoringLatencyMilliseconds
                            },
                            set: { value in
                                model.setScoringLatencyMilliseconds(
                                    value
                                )
                                settings.scoringLatencyMilliseconds =
                                    Int(value)
                            }
                        ),
                        in: -500...1_500,
                        step: 10
                    ) {
                        LabeledContent(
                            "Latency compensation",
                            value:
                                "\(model.scoringLatencyMilliseconds) ms"
                        )
                    }

                    if model.scoringObservationCount > 0 {
                        Button("Reset Live Score") {
                            model.resetLiveScore()
                        }
                    }

                    if microphonePitchMonitor.status != .running {
                        Button("Retry Microphone") {
                            Task {
                                await microphonePitchMonitor.start()
                            }
                        }
                        .disabled(
                            microphonePitchMonitor.status
                                == .requestingPermission
                        )
                    }

                    scoringPrivacyDisclosure
                }
            }

            partySFXSection

            Section("Audio Inputs & Diagnostics") {
                KantaAudioInputPanel(
                    diagnostics: audioInputs,
                    microphone: microphonePitchMonitor,
                    settings: settings
                )
            }

            vocalFXSection

            Section("Playback") {
                LabeledContent(
                    "Volume",
                    value:
                        "\(Int(model.snapshot.volume * 100))%"
                )
                Slider(
                    value: Binding(
                        get: {
                            Double(model.snapshot.volume)
                        },
                        set: { value in
                            Task {
                                await model.setVolume(
                                    Float(value)
                                )
                            }
                        }
                    ),
                    in: 0...1
                )
                .accessibilityLabel("Playback volume")
                .accessibilityValue(
                    "\(Int(model.snapshot.volume * 100)) percent"
                )

                HStack {
                    Button("Pitch −") {
                        Task {
                            await model.changePitch(by: -1)
                        }
                    }
                    Spacer()
                    Text(
                        "\(model.snapshot.pitchSemitones) st"
                    )
                    .monospacedDigit()
                    Spacer()
                    Button("Pitch +") {
                        Task {
                            await model.changePitch(by: 1)
                        }
                    }
                }

                HStack {
                    Button("Tempo −") {
                        Task {
                            await model.changeTempo(by: -0.05)
                        }
                    }
                    Spacer()
                    Text(
                        String(
                            format: "%.2fx",
                            model.snapshot.tempoRatio
                        )
                    )
                    .monospacedDigit()
                    Spacer()
                    Button("Tempo +") {
                        Task {
                            await model.changeTempo(by: 0.05)
                        }
                    }
                }
            }

            Section(
                "Queue (\(model.snapshot.queue.count))"
            ) {
                if model.snapshot.queue.isEmpty {
                    KantaEmptyState(
                        icon: "list.bullet",
                        title: "Queue is empty",
                        description: "Reserve a song from Songs to line one up.",
                        actionTitle: nil,
                        action: nil
                    )
                } else {
                    ForEach(model.snapshot.queue) { item in
                        HStack(spacing: 8) {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(item.song.title)
                                if let singer = item.requestedBy {
                                    Text("Singer · " + singer)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            Button {
                                Task {
                                    await model.moveQueueItem(item.id, up: true)
                                }
                            } label: {
                                Image(systemName: "arrow.up")
                            }
                            .accessibilityLabel("Move " + item.song.title + " up")
                            Button {
                                Task {
                                    await model.moveQueueItem(item.id, up: false)
                                }
                            } label: {
                                Image(systemName: "arrow.down")
                            }
                            .accessibilityLabel("Move " + item.song.title + " down")
                        }
                        .swipeActions {
                            Button(role: .destructive) {
                                Task { await model.removeQueueItem(item.id) }
                            } label: {
                                Label("Remove", systemImage: "trash")
                            }
                        }
                    }
                }
            }

            Section("Songs") {
                if model.isLoading {
                    HStack {
                        ProgressView()
                        Text("Loading library…")
                    }
                } else if model.songs.isEmpty {
                    ContentUnavailableView(
                        model.query.isEmpty
                            ? "No Playable Songs"
                            : "No Matching Songs",
                        systemImage: "music.note",
                        description: Text(
                            model.query.isEmpty
                                ? "Add a folder containing MIDI/KAR, MP3, WAV, " +
                                  "or a compatible indexed library in Song Library."
                                : "Change the search in CONTROL to show more songs."
                        )
                    )
                } else {
                    ForEach(model.songs) { song in
                        KantaSongRow(
                            code: song.code,
                            title: song.title,
                            subtitle: song.mediaKind.rawValue.uppercased(),
                            isLive: model.snapshot.nowPlaying?.id == song.id,
                            actionTitle: "Reserve",
                            actionIcon: "plus.circle"
                        ) {
                            Task {
                                await model.enqueue(song, singer: singerName)
                            }
                        }
                        .listRowInsets(
                            EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8)
                        )
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background { KantaBackground().allowsHitTesting(false) }
        .safeAreaInset(edge: .bottom) {
            KantaTransportBar(
                snapshot: model.snapshot,
                isEnabled: model.isAvailable,
                onTogglePlayPause: {
                    Task { await model.togglePlayPause() }
                },
                onNext: {
                    Task { await model.next() }
                }
            )
        }
    }

    // Keep SwiftUI's main List result builder small and type-checkable
    // under the Release iOS compiler.
    private var scoringPrivacyDisclosure: some View {
        Text(
            "Pitch detection and scoring run locally; audio is not " +
            "recorded or uploaded. Live scoring uses validated MIDI " +
            "melody targets and playback-time alignment. Device-specific " +
            "latency calibration and profiling remain required."
        )
        .font(.footnote)
        .foregroundStyle(.secondary)
    }

    private var partySFXSection: some View {
        Section("Party SFX Pad") {
            KantaPartySFXPad(
                model: model,
                settings: settings,
                engine: partySFX
            )
        }
    }

    private var vocalFXSection: some View {
        Section("Soundcard · Vocal FX") {
            KantaVocalFXControls(
                settings: settings,
                monitor: microphonePitchMonitor
            )
        }
    }

    private var isDemoMode: Bool {
        ProcessInfo.processInfo.environment[
            "KANTA_DEMO_MODE"
        ] == "1"
    }

    private var controlFooterText: String {
        "Open \(KantaTerminology.control) when you want to search, " +
        "manage the queue, or adjust playback. It expands inline and " +
        "does not cover the karaoke stage."
    }

    private var secondDeviceControlTitle: String {
        "Second-Device \(KantaTerminology.control) — Optional"
    }

    private var secondDeviceControlHelp: String {
        "A second phone or tablet can optionally control this session " +
        "over the local network. This is not required—the main device " +
        "has the full \(KantaTerminology.control) panel. The PIN stays " +
        "local and is not advertised through Bonjour."
    }

    private var scoringTargetText: String {
        guard let extraction =
            model.scoringTargetExtraction else {
            return model.snapshot.nowPlaying == nil
                ? "No song"
                : "Unavailable for this media"
        }

        switch extraction {
        case let .resolved(target):
            let source =
                target.evidence == .lyricBearingTrack
                ? "lyric track"
                : "named vocal track"
            return String(target.windows.count)
                + " notes · " + source

        case let .unavailable(reason):
            switch reason {
            case .noStrongCandidate:
                return "No validated melody track"
            case .multipleStrongCandidates:
                return "Ambiguous melody tracks"
            case .candidateIsPolyphonic:
                return "Target track is polyphonic"
            case .candidateUsesPitchBend:
                return "Pitch-bend target unsupported"
            case .malformedNoteLifecycle:
                return "Malformed target notes"
            case .noTargetNotes:
                return "No target notes"
            }
        }
    }

    private var microphoneStatusText: String {
        switch microphonePitchMonitor.status {
        case .idle:
            return "Off"
        case .requestingPermission:
            return "Requesting permission"
        case .running:
            return "Listening locally"
        case .denied:
            return "Permission denied"
        case let .failed(message):
            return "Unavailable: " + message
        }
    }

    private var networkHostStatusText: String {
        switch networkHost.status {
        case .idle:
            return "Off"
        case .starting:
            return "Starting"
        case .ready:
            return "Discoverable"
        case let .failed(message):
            return "Unavailable: " + message
        }
    }



    private func format(milliseconds: Int64) -> String {
        let totalSeconds = max(0, milliseconds) / 1_000
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(
            format: "%d:%02d",
            minutes,
            seconds
        )
    }
}

