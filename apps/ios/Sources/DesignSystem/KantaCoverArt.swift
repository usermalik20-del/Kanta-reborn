import SwiftUI

struct KantaCoverArt: View {
    let seed: String
    var initials: String? = nil
    var cornerRadius: CGFloat = 22

    private static let gradientPairs: [(Color, Color)] = [
        (Color(hex: "FF5FA2"), Color(hex: "6D3BFF")), // 0: pink -> violet
        (Color(hex: "4DE3F0"), Color(hex: "2B4BFF")), // 1: cyan -> blue
        (Color(hex: "FFB03A"), Color(hex: "FF3D8B")), // 2: amber -> pink
        (Color(hex: "3DE0A8"), Color(hex: "1B7FA8")), // 3: mint -> teal
        (Color(hex: "6D3BFF"), Color(hex: "140A26")), // 4: violet -> indigo
        (Color(hex: "FF9500"), Color(hex: "FF5A6E")), // 5: orange -> crimson
        (Color(hex: "A24BFF"), Color(hex: "2B4BFF")), // 6: purple -> blue
        (Color(hex: "1B7FA8"), Color(hex: "3DE0A8"))  // 7: teal -> green
    ]

    private var selectedPair: (Color, Color) {
        let hash = fnv1aHash(seed)
        let index = Int(hash % UInt32(Self.gradientPairs.count))
        return Self.gradientPairs[index]
    }

    private var resolvedInitials: String {
        if let initials = initials, !initials.isEmpty {
            return String(initials.prefix(2)).uppercased()
        }
        let words = seed.split { !$0.isLetter && !$0.isNumber }
        if words.isEmpty {
            let cleaned = seed.filter { $0.isLetter || $0.isNumber }
            return String(cleaned.prefix(2)).uppercased()
        }
        let letters = words.prefix(2).compactMap { $0.first.map { String($0).uppercased() } }
        let combined = letters.joined()
        return combined.isEmpty ? "KA" : combined
    }

    private func fnv1aHash(_ string: String) -> UInt32 {
        var hash: UInt32 = 2166136261
        for byte in string.utf8 {
            hash ^= UInt32(byte)
            hash &*= 16777619
        }
        return hash
    }

    var body: some View {
        let pair = selectedPair
        ZStack(alignment: .bottomTrailing) {
            LinearGradient(
                colors: [pair.0, pair.1],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )

            RadialGradient(
                colors: [.white.opacity(0.18), .clear],
                center: .topLeading,
                startRadius: 0,
                endRadius: 180
            )

            Text(resolvedInitials)
                .font(.system(size: 38, weight: .black, design: .rounded))
                .foregroundStyle(.white.opacity(0.26))
                .padding(.trailing, 12)
                .padding(.bottom, 6)
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(KantaColors.hairline, lineWidth: 1)
        }
    }
}
