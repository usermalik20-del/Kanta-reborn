import SwiftUI

/// KANTA Stage palette.
///
/// These values are the approved redesign. The previous near-black canvas and
/// muted accent are deliberately absent: a token swap that only nudges hue is
/// invisible on screen.
enum KantaColors {
    static let canvas = Color(hex: "0A0614")
    static let canvasTop = Color(hex: "140A26")
    static let canvasBottom = Color(hex: "0C0618")

    static let bloomPurple = Color(hex: "A24BFF").opacity(0.55)
    static let bloomPink = Color(hex: "FF3D8B").opacity(0.34)
    static let bloomCyan = Color(hex: "4DE3F0").opacity(0.22)

    static let surface = Color(hex: "1B1030")
    static let surfaceHigh = Color(hex: "241540")
    static let elevated = Color(hex: "2A1A47")

    static let primary = Color(hex: "A24BFF")
    static let primaryHot = Color(hex: "FF3D8B")
    static let violet = Color(hex: "6D3BFF")
    static let live = Color(hex: "3DE0A8")
    static let cyan = Color(hex: "4DE3F0")
    static let amber = Color(hex: "FFB03A")

    static let success = Color(hex: "3DE0A8")
    static let warning = Color(hex: "FFB03A")
    static let destructive = Color(hex: "FF5A6E")

    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.66)
    static let textTertiary = Color.white.opacity(0.40)
    static let hairline = Color.white.opacity(0.10)

    static let primaryGradient = LinearGradient(colors: [
        Color(hex: "FF3D8B"),
        Color(hex: "A24BFF")
    ], startPoint: .topLeading, endPoint: .bottomTrailing)

    static let accentGradient = LinearGradient(colors: [
        Color(hex: "A24BFF"),
        Color(hex: "6D3BFF")
    ], startPoint: .topLeading, endPoint: .bottomTrailing)
}

extension Color {
    init(hex: String) {
        let scanner = Scanner(string: hex)
        var rgbValue: UInt64 = 0
        scanner.scanHexInt64(&rgbValue)
        let r = Double((rgbValue & 0xFF0000) >> 16) / 255.0
        let g = Double((rgbValue & 0x00FF00) >> 8) / 255.0
        let b = Double(rgbValue & 0x0000FF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}
