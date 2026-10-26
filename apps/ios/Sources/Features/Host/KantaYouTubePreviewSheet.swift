import GeloKaraokeCore
import SwiftUI

/// Explicit opt-in online preview. Queue integration is intentionally separate.
@MainActor
struct KantaYouTubePreviewSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var link = ""
    @FocusState private var linkFocused: Bool
    @StateObject private var playerOwner = YouTubeEmbeddedPlayerOwner()
    @State private var lifecycle = YouTubePreviewLifecycle()
    @State private var playerError: String?
    @State private var showInvalidLink = false
    let localPlaybackActive: Bool
    let controller: LocalKaraokeController?
    let audioOwnership: KaraokeAudioOwnership
    @State private var opening: Task<Void, Never>?
    @State private var pendingID: UUID?

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if let selected = lifecycle.active {
                    YouTubeEmbeddedPlayerView(session: selected, owner: playerOwner, audioOwnership: audioOwnership) { id in
                        if lifecycle.close(id) {
                            audioOwnership.releaseOnlineAudio(id)
                            playerError = "Online video could not load. Check your connection and try again."
                        }
                    }
                        .id(selected.id)
                        .frame(minHeight: 220)
                        .aspectRatio(16.0 / 9.0, contentMode: .fit)
                    Button("Stop online video") {
                        closePreview()
                    }
                        .accessibilityIdentifier("youtube.preview.stop")
                        .buttonStyle(.bordered)
                } else {
                    Text("Online videos use YouTube/Google services, may share playback data and may show ads. Local karaoke works offline. YouTube has separate playback controls. Effects and intermission music stop when you open a video. Local audio actions close it. Stop microphone capture and monitoring first.")
                        .font(.footnote)
                    if let playerError {
                        Text(playerError).font(.footnote).accessibilityIdentifier("youtube.preview.error")
                    }
                    TextField("YouTube HTTPS video link", text: $link)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .focused($linkFocused)
                        .submitLabel(.done)
                        .onSubmit { linkFocused = false }
                        .textFieldStyle(.roundedBorder)
                        .accessibilityIdentifier("youtube.preview.link")
                    if localPlaybackActive {
                        Text("Pause local karaoke before opening an online video.")
                            .font(.footnote)
                            .accessibilityIdentifier("youtube.preview.localPlaybackNotice")
                    }
                    Button("Continue to YouTube") {
                        linkFocused = false
                        guard !localPlaybackActive else { return }
                        guard let reference = YouTubeVideoReference(url: link.trimmingCharacters(in: .whitespacesAndNewlines)) else {
                            showInvalidLink = true
                            return
                        }
                        guard scenePhase == .active else { return }
                        openPreview(reference)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("youtube.preview.continue")
                    .disabled(localPlaybackActive || opening != nil || controller == nil)
                    .alert("Invalid YouTube link", isPresented: $showInvalidLink) {
                        Button("OK", role: .cancel) {}
                    } message: {
                        Text("Enter a valid HTTPS YouTube video URL.")
                    }
                }
                Spacer()
            }
            .padding()
            .navigationTitle("Online video")
            .onChange(of: localPlaybackActive) { _, active in
                if active {
                    closePreview()
                }
            }
            .onChange(of: scenePhase) { _, phase in
                if phase != .active {
                    closePreview()
                }
            }
            .onDisappear {
                closePreview()
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        closePreview()
                        dismiss()
                    }
                    .accessibilityIdentifier("youtube.preview.done")
                }
            }
        }
    }

    private func closePreview() {
        opening?.cancel()
        opening = nil
        if let pendingID { audioOwnership.releaseOnlineAudio(pendingID) }
        pendingID = nil
        if let id = lifecycle.active?.id { audioOwnership.releaseOnlineAudio(id) }

        // Clear the selected presentation first; the owner then synchronously
        // detaches the native view before scheduling WebKit cleanup.
        lifecycle.close()
        playerOwner.retire()
    }

    private func openPreview(_ reference: YouTubeVideoReference) {
        guard opening == nil, let controller else { return }
        var candidate = YouTubePreviewLifecycle()
        let selected = candidate.open(reference)
        pendingID = selected.id
        playerError = nil
        opening = Task { @MainActor in
            do {
                let accepted = try await controller.claimOnlineAudio(selected.id) {
                    playerOwner.retire()
                    lifecycle.close(selected.id)
                }
                guard !Task.isCancelled, pendingID == selected.id,
                      scenePhase == .active, accepted,
                      audioOwnership.ownsOnlineAudio(selected.id) else {
                    audioOwnership.releaseOnlineAudio(selected.id)
                    if pendingID == selected.id {
                        playerError = "Pause local karaoke and stop microphone capture and monitoring before opening online video."
                        pendingID = nil
                        opening = nil
                    }
                    return
                }
                lifecycle = candidate
            } catch {
                audioOwnership.releaseOnlineAudio(selected.id)
                if pendingID == selected.id {
                    playerError = "Online audio could not start. Stop microphone capture and monitoring, then try again."
                }
            }
            if pendingID == selected.id {
                pendingID = nil
                opening = nil
            }
        }
    }

}
