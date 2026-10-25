import SwiftUI

/// One song or reservation.
///
/// The trailing action is the row's only interactive element; artwork and text
/// are decoration so the row never competes with it for taps.
struct KantaSongRow: View {
    let code: String?
    let title: String
    let subtitle: String
    let isLive: Bool
    let actionTitle: String
    let actionIcon: String
    let onAction: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            KantaCoverArt(seed: title + " " + subtitle, cornerRadius: 13)
                .frame(width: 48, height: 48)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(KantaTypography.songTitle)
                    .foregroundStyle(KantaColors.textPrimary)
                    .lineLimit(1)

                HStack(spacing: 6) {
                    if isLive {
                        Circle()
                            .fill(KantaColors.live)
                            .frame(width: 7, height: 7)
                        Text("LIVE")
                            .font(KantaTypography.badge)
                            .foregroundStyle(KantaColors.live)
                    }

                    if let code, !code.isEmpty {
                        Text(code)
                            .font(KantaTypography.badge)
                            .foregroundStyle(KantaColors.cyan)
                    }

                    Text(subtitle)
                        .font(KantaTypography.metadata)
                        .foregroundStyle(KantaColors.textSecondary)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 8)

            Button(action: onAction) {
                Label(actionTitle, systemImage: actionIcon)
                    .font(KantaTypography.metadata)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .foregroundStyle(isLive ? KantaColors.canvas : KantaColors.textPrimary)
                    .background {
                        if isLive {
                            Capsule().fill(KantaColors.primaryGradient)
                        } else {
                            Capsule().fill(KantaColors.elevated)
                        }
                    }
                    .overlay {
                        Capsule().stroke(KantaColors.hairline, lineWidth: 1)
                    }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(actionTitle + " " + title)
        }
        .padding(12)
        .background(
            isLive ? KantaColors.surfaceHigh : KantaColors.surface,
            in: RoundedRectangle(cornerRadius: 18, style: .continuous)
        )
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(
                    isLive ? KantaColors.primaryHot.opacity(0.45) : KantaColors.hairline,
                    lineWidth: isLive ? 1.5 : 1
                )
        }
    }
}
