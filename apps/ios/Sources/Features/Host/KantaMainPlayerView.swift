import GeloKaraokeCore
import SwiftUI
import UIKit

/// A projection of the existing Host model, never a second player or queue.
@MainActor
struct KantaMainPlayerView: View {
    @ObservedObject var model: HostViewModel
    @ObservedObject var camera: KantaCameraBackground
    @ObservedObject var presentationGate: KantaPresentationGate
    let backgroundsEnabled: Bool
    let selectedPreset: KantaBuiltInVisualPreset
    @Binding var singerName: String
    var microphoneBusy = false
    var onControls: () -> Void = {}
    var onMore: () -> Void = {}
    var onDisplay: () -> Void = {}
    var onSaveSession: () -> Void = {}
    var embedded = false
    var onExpand: () -> Void = {}
    var setEmbeddedStageSheet: (@MainActor (KantaStageSheet?) -> Void)? = nil
    @Environment(\.scenePhase) private var scenePhase
    @State private var activeStageSheet: KantaStageSheet?

    @State private var transportBusy = false

    var body: some View {
        Group {
            if embedded {
                embeddedPlayer
            } else {
                fullscreenPlayer
                    .sheet(item: $activeStageSheet, onDismiss: {
                        presentationGate.completeDismissal(.stage)
                    }) { sheet in
                        KantaStageSheetContent(
                            sheet: sheet,
                            model: model,
                            singerName: $singerName,
                            microphoneBusy: microphoneBusy,
                            onClose: closeStageSheet
                        )
                    }
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { camera.stop() }
        }
        .onDisappear { camera.stop() }
        .alert("Camera BGV", isPresented: Binding(
            get: { camera.message != nil },
            set: { if !$0 { camera.message = nil } }
        )) {
            Button("OK", role: .cancel) { camera.message = nil }
        } message: { Text(camera.message ?? "") }
    }

    private var fullscreenPlayer: some View {
        GeometryReader { geometry in
            let wide = geometry.size.width > geometry.size.height
            ZStack {
                stageBackground
                    .ignoresSafeArea()
                    .accessibilityHidden(true)
                LinearGradient(colors: [.black.opacity(0.24), .clear, .black.opacity(0.8)],
                               startPoint: .top, endPoint: .bottom)
                    .ignoresSafeArea().allowsHitTesting(false)
                VStack(spacing: 8) {
                    header
                    Group {
                        if model.hasLyrics {
                            KaraokeLyricBlock(page: model.lyricPage)
                        } else {
                            VStack(spacing: 12) {
                                Text(model.snapshot.nowPlaying?.title ?? "Your stage is ready")
                                    .font(.system(size: wide ? 32 : 36, weight: .black, design: .rounded))
                                    .multilineTextAlignment(.center)
                                Text(model.snapshot.nowPlaying?.artist ?? "Search a song, reserve it, then tap Play.")
                                    .font(.headline).foregroundStyle(.white.opacity(0.85))
                                    .multilineTextAlignment(.center)
                            }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .shadow(color: .black, radius: 2)
                        }
                    }
                    .padding(.horizontal, wide ? 36 : 12)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    if let song = model.snapshot.nowPlaying {
                        Text(song.title + (song.artist.map { " · " + $0 } ?? ""))
                            .font(.caption).lineLimit(1).foregroundStyle(.white.opacity(0.8))
                            .accessibilityIdentifier("stage.currentSong")
                    }
                    controls(wide: wide)
                    HStack {
                        if let notice = model.sessionNotice {
                            Text(notice).font(.caption2).lineLimit(2)
                        }
                        Spacer(minLength: 8)
                        if model.showingSessionSavePrompt {
                            Button("Save session", action: onSaveSession)
                                .font(.caption.weight(.semibold))
                                .buttonStyle(.bordered)
                                .accessibilityIdentifier("stage.saveSession")
                        }
                        KantaStageWatermark()
                    }
                }
                .padding(.horizontal, wide ? 18 : 16)
                .padding(.vertical, 8)

            }
            .foregroundStyle(.white)
        }
        .background(.black)
    }

    private var embeddedPlayer: some View {
        VStack(spacing: 18) {
            HStack {
                // The embedded stage reaches the menu through this chevron; the
                // fullscreen player uses stageButton("More"). They are never on
                // screen at the same time, so they share one identity.
                KantaBrandMark(size: 30)
                VStack(alignment: .leading, spacing: 2) {
                    Text("KANTA").font(.title2.bold()).foregroundStyle(KantaColors.textPrimary)
                    Text("More air. More focus. Always on stage.")
                        .font(.caption2).foregroundStyle(KantaColors.textSecondary)
                }
                Spacer(minLength: 0)
                Button(action: onControls) {
                    Image(systemName: "slider.horizontal.3").frame(width: 44, height: 44)
                }
                .accessibilityLabel("More controls")
                .accessibilityIdentifier("stage.controls")
            }
            VStack(alignment: .leading, spacing: 14) {
                // A single deterministic layout keeps exactly one interactive
                // instance of the stage action buttons. `ViewThatFits` builds
                // both candidates and the iPad walkthrough showed it selecting
                // the horizontal branch while taps on `stage.search` never
                // reached the button's action, so the controls are laid out
                // once here and the title yields space instead.
                nowSinging
                ZStack {
                    // With no stage visual or camera selected the tile shows real
                    // cover art instead of an empty placeholder. Selection still
                    // wins when the operator has chosen a background.
                    if camera.isEnabled || backgroundsEnabled {
                        stageBackground.accessibilityHidden(true)
                    } else {
                        KantaCoverArt(seed: stageArtSeed, cornerRadius: 0)
                            .accessibilityHidden(true)
                            .allowsHitTesting(false)
                    }
                    LinearGradient(colors: [.clear, .black.opacity(0.72)], startPoint: .top, endPoint: .bottom)
                        .allowsHitTesting(false)
                    if model.snapshot.transport == .playing {
                        VStack {
                            HStack {
                                stageLiveChip
                                Spacer(minLength: 0)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(14)
                        .allowsHitTesting(false)
                    }
                    Group {
                        if model.hasLyrics {
                            KaraokeLyricBlock(page: model.lyricPage)
                        } else {
                            Text("Search a song, reserve it, then tap Play.")
                                .font(.title3.bold()).multilineTextAlignment(.center)
                        }
                    }
                    .padding(20)
                    VStack { Spacer(); HStack { Spacer(); KantaStageWatermark() }.padding(12) }
                        .allowsHitTesting(false)
                }
                .frame(minHeight: 280)
                .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 30, style: .continuous)
                        .stroke(KantaColors.hairline, lineWidth: 1)
                }
                .shadow(color: KantaColors.violet.opacity(0.35), radius: 24, y: 12)
                workspaceToolbar
                HStack { playButton; Spacer(); nextButton; Spacer(); queueButton }
                if let next = model.snapshot.queue.first {
                    Text("Up Next · " + next.song.title)
                        .font(.subheadline).foregroundStyle(.secondary)
                } else {
                    Text("Up Next · No reservations").font(.subheadline).foregroundStyle(.secondary)
                }
            }
            .padding(16)
            .background(
                KantaColors.surface.opacity(0.55),
                in: RoundedRectangle(cornerRadius: 28, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .stroke(KantaColors.hairline, lineWidth: 1)
            }
            HStack {
                if let notice = model.sessionNotice {
                    Text(notice).font(.caption).foregroundStyle(.secondary)
                }
                Spacer()
                if model.showingSessionSavePrompt {
                    Button("Save session", action: onSaveSession)
                        .accessibilityIdentifier("stage.saveSession")
                }
            }
        }
        .foregroundStyle(.white)
    }

    private var nowSinging: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("NOW SINGING").font(KantaTypography.badge).tracking(2)
                .foregroundStyle(KantaColors.cyan)
            Text(model.snapshot.nowPlaying?.title ?? "Your stage is ready")
                .font(.title2.bold()).fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("stage.currentSong")
            if let artist = model.snapshot.nowPlaying?.artist {
                Text(artist).font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }

    /// Compact, persistent utility controls sit beside the player instead of
    /// expanding a separate CONTROL dashboard around the stage.
    private var workspaceToolbar: some View {
        HStack(spacing: 0) {
            searchButton
            Spacer(minLength: 4)
            cameraButton
            Spacer(minLength: 4)
            stageButton("Display", icon: "display", action: onDisplay)
                .accessibilityIdentifier("stage.display")
            Spacer(minLength: 4)
            stageButton("Expand", icon: "arrow.up.left.and.arrow.down.right", action: onExpand)
                .accessibilityIdentifier("stage.expand")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(Capsule().stroke(KantaColors.hairline, lineWidth: 1))
        .accessibilityIdentifier("stage.workspaceToolbar")
    }

    @ViewBuilder private var stageBackground: some View {
        // These layers render the stage; they never own user interaction.
        // SwiftUI drawing/video surfaces can extend their hit-test region beyond
        // the visible card, so taps must pass through to the controls above them.
        if camera.isEnabled {
            KantaCameraPreview(capture: camera.capture)
                .allowsHitTesting(false)
        } else {
            KaraokeStageView(asset: backgroundsEnabled ? model.backgroundAsset : nil,
                             builtInPreset: backgroundsEnabled ? selectedPreset : nil,
                             isPlaying: model.snapshot.transport == .playing,
                             showsWatermark: false)
                .allowsHitTesting(false)
        }
    }

    /// Stable artwork seed for the stage tile: the playing song when there is
    /// one, otherwise the app identity so the tile is never blank.
    private var stageArtSeed: String {
        guard let song = model.snapshot.nowPlaying else { return "KANTA Stage" }
        return song.title + " " + (song.artist ?? "")
    }

    private var stageLiveChip: some View {
        HStack(spacing: 7) {
            Circle()
                .fill(KantaColors.live)
                .frame(width: 7, height: 7)
                .shadow(color: KantaColors.live, radius: 5)
            Text("LIVE").font(KantaTypography.badge)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(.black.opacity(0.45), in: Capsule())
        .overlay(Capsule().stroke(KantaColors.hairline, lineWidth: 1))
        .foregroundStyle(KantaColors.textPrimary)
    }

    private var header: some View {
        HStack(spacing: 10) {
            HStack(spacing: 8) {
                KantaBrandMark(size: 30)
                Text("KANTA").font(.title3.bold()).foregroundStyle(GeloVisualStyle.gold)
            }
            Spacer(minLength: 0)
            if camera.isEnabled || camera.isStarting {
                Button { camera.stop() } label: {
                    Label(camera.isStarting ? "Cancel camera" : "Stop camera", systemImage: "video.slash")
                        .font(.caption.bold()).padding(10)
                        .background(.black.opacity(0.55), in: Capsule())
                }
                .accessibilityHint("Stops live front-camera background. No video is saved.")
            }
            Button { camera.stop(); onControls() } label: {
                Image(systemName: "slider.horizontal.3").frame(width: 44, height: 44)
                    .background(.black.opacity(0.55), in: Circle())
            }
            .accessibilityLabel("CONTROL")
            .accessibilityIdentifier("stage.controls")
        }
    }

    @ViewBuilder private func controls(wide: Bool) -> some View {
        if wide {
            HStack(spacing: 20) {
                searchButton
                queueButton
                playButton
                nextButton
                cameraButton
                moreButton
            }
            .padding(.horizontal, 24).padding(.vertical, 8)
            .background(.ultraThinMaterial, in: Capsule())
        } else {
            VStack(spacing: 16) {
                HStack(spacing: 30) { playButton; nextButton }
                HStack { searchButton; Spacer(); queueButton; Spacer(); cameraButton; Spacer(); moreButton }
            }
            .padding(12)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24))
        }
    }
    private func requestStageSheet(_ sheet: KantaStageSheet) {
        guard !embedded || setEmbeddedStageSheet != nil else {
            assertionFailure("Embedded stage sheets require a stable HostView presenter.")
            return
        }
        NSLog("[KANTA_STAGE_PRESENTATION] request sheet=%@ embedded=%@ gateOwner=%@ dismissing=%@",
              sheet.rawValue, embedded ? "true" : "false",
              String(describing: presentationGate.owner),
              presentationGate.dismissing ? "true" : "false")
        presentationGate.request(.stage) {
            NSLog("[KANTA_STAGE_PRESENTATION] activate sheet=%@ embedded=%@",
                  sheet.rawValue, embedded ? "true" : "false")
            if embedded {
                setEmbeddedStageSheet?(sheet)
            } else {
                activeStageSheet = sheet
            }
        }
    }

    private func closeStageSheet() {
        presentationGate.beginDismissal(.stage)
        if embedded {
            setEmbeddedStageSheet?(nil)
        } else {
            activeStageSheet = nil
        }
    }

    private var searchButton: some View {
        stageButton("Search", icon: "magnifyingglass") {
            requestStageSheet(.search)
        }
            .accessibilityIdentifier(embedded ? "stage.search" : "stage.search.fullscreen")
    }
    private var queueButton: some View {
        stageButton("Queue", icon: "list.bullet") {
            requestStageSheet(.queue)
        }
            .accessibilityValue("\(model.snapshot.queue.count) reservations")
            .accessibilityIdentifier("stage.queue")
    }
    private var cameraButton: some View {
        stageButton("Camera", icon: camera.isEnabled ? "video.fill" : "video") { camera.toggle() }
            .accessibilityValue(camera.isEnabled ? "Front camera live" : "Off")
            .accessibilityIdentifier("stage.camera")
    }
    private var moreButton: some View {
        stageButton("More", icon: "ellipsis") { camera.stop(); onMore() }
            .accessibilityIdentifier("stage.controls.more")
    }
    private var playButton: some View {
        Button {
            performTransport { await model.togglePlayPause() }
        } label: {
            Image(systemName: model.snapshot.transport == .playing ? "pause.fill" : "play.fill")
                .font(.system(size: 26, weight: .bold)).frame(width: 74, height: 74)
                .foregroundStyle(.white)
                .background(KantaColors.primaryGradient, in: Circle())
                .shadow(color: KantaColors.primaryHot.opacity(0.50), radius: 18)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(model.snapshot.transport == .playing ? "Pause" : "Play")
        .accessibilityIdentifier("stage.playPause")
        .disabled(!model.isAvailable || transportBusy
                  || (model.snapshot.nowPlaying == nil && model.snapshot.queue.isEmpty))
    }
    private var nextButton: some View {
        stageButton("Next", icon: "forward.end.fill") {
            performTransport { await model.next() }
        }
        .disabled(!model.isAvailable || transportBusy || model.snapshot.queue.isEmpty)
        .accessibilityIdentifier("stage.next")
    }
    private func performTransport(_ action: @escaping @MainActor () async -> Void) {
        guard !transportBusy else { return }
        transportBusy = true
        Task {
            await action()
            transportBusy = false
        }
    }
    private func stageButton(_ title: String, icon: String,
                             action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 3) {
                Image(systemName: icon).font(.system(size: 20, weight: .medium))
                    .frame(width: 52, height: 52)
                    .background(KantaColors.elevated, in: Circle())
                    .overlay(Circle().strokeBorder(KantaColors.hairline, lineWidth: 1))
                Text(title).font(.caption)
            }
            .frame(minWidth: 44)
            // With .plain button style SwiftUI derives the hit region from the
            // label's content, which here is a symbol, a stroked circle and a
            // caption rather than one solid shape. The iPad walkthrough tapped
            // stage.search at the centre of the reported frame and no gesture in
            // the row recognised it, so claim the whole label rectangle instead.
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain).accessibilityLabel(title)
    }
}
