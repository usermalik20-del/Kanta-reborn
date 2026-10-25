import SwiftUI

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
            if let code = code, !code.isEmpty {
                Text(code)
                    .font(KantaTypography.badge)
                    .foregroundStyle(KantaColors.live)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(KantaColors.elevated, in: RoundedRectangle(cornerRadius: 8))
            } else if isLive {
                Circle()
                    .fill(KantaColors.live)
                    .frame(width: 10, height: 10)
                    .shadow(color: KantaColors.live.opacity(0.5), radius: 4)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(KantaTypography.songTitle)
                    .foregroundStyle(KantaColors.textPrimary)
                    .lineLimit(1)

                Text(subtitle)
                    .font(KantaTypography.metadata)
                    .foregroundStyle(KantaColors.textSecondary)
                    .lineLimit(1)
            }

            Spacer()

            Button(action: onAction) {
                Label(actionTitle, systemImage: actionIcon)
                    .font(KantaTypography.metadata)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .tint(isLive ? KantaColors.live : KantaColors.primary)
            .accessibilityLabel(actionTitle + " " + title)
        }
        .padding(12)
        .background(isLive ? KantaColors.elevated.opacity(0.8) : KantaColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(isLive ? KantaColors.live.opacity(0.4) : .white.opacity(0.06), lineWidth: isLive ? 1.5 : 1)
        }
    }
}
