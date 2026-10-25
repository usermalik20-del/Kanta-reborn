import SwiftUI

/// Decorative stage canvas: a deep indigo gradient with three soft colour
/// blooms.
///
/// Purely visual. Callers attach it behind their content, so every layer here
/// stays non-hit-testable and cannot swallow a control tap.
struct KantaBackground: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    KantaColors.canvasTop,
                    KantaColors.canvas,
                    KantaColors.canvasBottom
                ],
                startPoint: .top,
                endPoint: .bottom
            )

            bloom(KantaColors.bloomPurple, size: 420, x: -0.34, y: -0.16)
            bloom(KantaColors.bloomPink, size: 380, x: 0.38, y: 0.24)
            bloom(KantaColors.bloomCyan, size: 300, x: -0.26, y: 0.82)
        }
        .ignoresSafeArea()
    }

    /// Soft colour wash, positioned in unit coordinates so it adapts to any
    /// device size without hard-coded points.
    private func bloom(_ color: Color, size: CGFloat, x: CGFloat, y: CGFloat) -> some View {
        GeometryReader { geometry in
            RadialGradient(
                colors: [color, .clear],
                center: .center,
                startRadius: 0,
                endRadius: size / 2
            )
            .frame(width: size, height: size)
            .position(
                x: geometry.size.width * (0.5 + x),
                y: geometry.size.height * (0.5 + y)
            )
        }
        .allowsHitTesting(false)
    }
}
