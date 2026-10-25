import SwiftUI

struct KantaEmptyState: View {
    /// `full` for a whole-screen state, `compact` for a panel or List row.
    enum Style {
        case full
        case compact
    }

    let icon: String
    let title: String
    let description: String
    let actionTitle: String?
    let action: (() -> Void)?
    var style: Style = .full

    private var isCompact: Bool { style == .compact }

    var body: some View {
        VStack(spacing: isCompact ? 8 : 16) {
            Image(systemName: icon)
                .font(.system(size: isCompact ? 22 : 48))
                .foregroundStyle(KantaColors.primary)
                .padding(.bottom, isCompact ? 0 : 4)

            Text(title)
                .font(isCompact ? KantaTypography.songTitle : KantaTypography.sectionHeader)
                .foregroundStyle(KantaColors.textPrimary)
                .multilineTextAlignment(.center)

            Text(description)
                .font(isCompact ? KantaTypography.caption : KantaTypography.metadata)
                .foregroundStyle(KantaColors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, isCompact ? 0 : 24)

            if let actionTitle = actionTitle, let action = action {
                Button(action: action) {
                    Text(actionTitle)
                        .font(KantaTypography.songTitle)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 10)
                }
                .buttonStyle(.borderedProminent)
                .tint(KantaColors.primary)
                .padding(.top, 8)
            }
        }
        .padding(isCompact ? 14 : 32)
        // Layout-agnostic on purpose: callers place this in a List row or a
        // full-screen container, so it must not claim height or paint its own
        // canvas.
        .frame(maxWidth: .infinity)
    }
}
