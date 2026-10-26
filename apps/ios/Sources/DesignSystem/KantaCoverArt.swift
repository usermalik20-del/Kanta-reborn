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

    /// Four diagonals, chosen independently of the colour pair, so two seeds
    /// that land on the same pair still read as different artwork.
    /// One mixed hash per seed, sliced into independent fields.
    ///
    /// FNV-1a alone left correlated low bits: "Sing Together" and
    /// "Starlight Echo" landed on the same colour pair AND the same axis, so
    /// two tiles rendered identically. The avalanche finaliser separates them.
    private var seedHash: UInt32 {
        var hash: UInt32 = 2166136261
        for byte in seed.utf8 {
            hash ^= UInt32(byte)
            hash &*= 16777619
        }
        hash ^= hash >> 16
        hash &*= 0x85ebca6b
        hash ^= hash >> 13
        hash &*= 0xc2b2ae35
        hash ^= hash >> 16
        return hash
    }

    /// Three sheen strengths, so even two seeds that match on pair and axis
    /// still differ in lighting.
    private var sheenOpacity: Double {
        [0.10, 0.18, 0.26][Int((seedHash >> 5) % 3)]
    }

    private var selectedAxis: (UnitPoint, UnitPoint) {
        let axes: [(UnitPoint, UnitPoint)] = [
            (.topLeading, .bottomTrailing),
            (.bottomLeading, .topTrailing),
            (.top, .bottom),
            (.leading, .trailing)
        ]
        return axes[Int((seedHash >> 3) % UInt32(axes.count))]
    }

    private var selectedPair: (Color, Color) {
        Self.gradientPairs[Int(seedHash % UInt32(Self.gradientPairs.count))]
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

    var body: some View {
        let pair = selectedPair
        ZStack(alignment: .topTrailing) {
            LinearGradient(
                colors: [pair.0, pair.1],
                startPoint: selectedAxis.0,
                endPoint: selectedAxis.1
            )

            RadialGradient(
                colors: [.white.opacity(sheenOpacity), .clear],
                center: .topLeading,
                startRadius: 0,
                endRadius: 180
            )

            Text(resolvedInitials)
                .font(.system(size: 38, weight: .black, design: .rounded))
                .foregroundStyle(.white.opacity(0.26))
                .padding(.trailing, 12)
                .padding(.top, 8)
        }
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(KantaColors.hairline, lineWidth: 1)
        }
    }
}
