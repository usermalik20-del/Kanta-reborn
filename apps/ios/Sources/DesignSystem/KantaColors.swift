import SwiftUI

enum KantaColors {
    static let canvas = Color(hex: "070810")
    static let surface = Color(hex: "11131D")
    static let elevated = Color(hex: "191C29")

    static let primary = Color(hex: "BD5CDE")
    static let primaryHot = Color(hex: "D96CF1")
    static let live = Color(hex: "63DDE8")

    static let textPrimary = Color.white
    static let textSecondary = Color.white.opacity(0.62)
    static let textTertiary = Color.white.opacity(0.38)

    static let success = Color(hex: "57D69A")
    static let warning = Color(hex: "FFCA68")
    static let destructive = Color(hex: "FF626A")
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
