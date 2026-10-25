import SwiftUI

struct KantaEmptyState: View {
    let icon: String
    let title: String
    let description: String
    let actionTitle: String?
    let action: (() -> Void)?

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 48))
                .foregroundStyle(KantaColors.primary)
                .padding(.bottom, 4)

            Text(title)
                .font(KantaTypography.sectionHeader)
                .foregroundStyle(KantaColors.textPrimary)
                .multilineTextAlignment(.center)

            Text(description)
                .font(KantaTypography.metadata)
                .foregroundStyle(KantaColors.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

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
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(KantaColors.canvas)
    }
}
