import GeloKaraokeCore
import SwiftUI

/// Always-available transport control for the Host control surface.
///
/// The bar renders the shared `KaraokeSnapshot` and forwards intents to the
/// owning `HostViewModel`; it deliberately owns no playback state, so the
/// snapshot remains the single source of truth for transport.
struct KantaTransportBar: View {
    let snapshot: KaraokeSnapshot
    let isEnabled: Bool
    let onTogglePlayPause: () -> Void
    let onNext: () -> Void

    private var isPlaying: Bool {
        snapshot.transport == .playing
    }

    private var canToggle: Bool {
        snapshot.nowPlaying != nil || !snapshot.queue.isEmpty
    }

    private var statusText: String {
        let status: String
        switch snapshot.transport {
        case .playing: status = "Playing"
        case .paused: status = "Paused"
        case .stopped: status = snapshot.nowPlaying == nil ? "Idle" : "Stopped"
        }
        let queued = snapshot.queue.count
        return queued > 0 ? status + " · " + String(queued) + " queued" : status
    }

    var body: some View {
        HStack(spacing: 12) {
            nowPlaying
            Spacer(minLength: 8)
            controls
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            .thinMaterial,
            in: RoundedRectangle(cornerRadius: 20, style: .continuous)
        )
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(
                    isPlaying ? KantaColors.live.opacity(0.45) : .white.opacity(0.08),
                    lineWidth: isPlaying ? 1.5 : 1
                )
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
        .accessibilityIdentifier("host.transport")
    }

    private var nowPlaying: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(snapshot.nowPlaying?.title ?? "Nothing playing")
                .font(KantaTypography.songTitle)
                .foregroundStyle(KantaColors.textPrimary)
                .lineLimit(1)

            Text(statusText)
                .font(KantaTypography.caption)
                .foregroundStyle(KantaColors.textSecondary)
                .lineLimit(1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("host.transport.nowPlaying")
    }

    private var controls: some View {
        HStack(spacing: 10) {
            Button(action: onTogglePlayPause) {
                Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(KantaColors.canvas)
                    .frame(width: 44, height: 44)
                    .background(KantaColors.primary, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isPlaying ? "Pause" : "Play")
            .accessibilityIdentifier("host.transport.playPause")
            .disabled(!isEnabled || !canToggle)

            Button(action: onNext) {
                Image(systemName: "forward.end.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(
                        isEnabled && !snapshot.queue.isEmpty
                            ? KantaColors.textPrimary
                            : KantaColors.textTertiary
                    )
                    .frame(width: 44, height: 44)
                    .background(KantaColors.elevated, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Skip to next song")
            .accessibilityIdentifier("host.transport.next")
            .disabled(!isEnabled || snapshot.queue.isEmpty)
        }
    }
}
