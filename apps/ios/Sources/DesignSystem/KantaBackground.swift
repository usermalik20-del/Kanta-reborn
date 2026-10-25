import SwiftUI

struct KantaBackground: View {
    var body: some View {
        ZStack {
            KantaColors.canvas

            RadialGradient(
                colors: [
                    KantaColors.primary.opacity(0.18),
                    .clear
                ],
                center: .topLeading,
                startRadius: 0,
                endRadius: 430
            )

            RadialGradient(
                colors: [
                    Color.blue.opacity(0.10),
                    .clear
                ],
                center: .bottomTrailing,
                startRadius: 0,
                endRadius: 500
            )
        }
        .ignoresSafeArea()
    }
}
