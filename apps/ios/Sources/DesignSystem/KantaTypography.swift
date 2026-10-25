import SwiftUI

/// KANTA Stage type scale.
enum KantaTypography {
    static let hero = Font.system(size: 34, weight: .bold, design: .rounded)
    static let title = Font.system(size: 27, weight: .heavy, design: .rounded)
    static let sectionHeader = Font.system(size: 19, weight: .heavy, design: .default)
    static let songTitle = Font.system(size: 15, weight: .semibold, design: .default)
    static let metadata = Font.system(size: 13, weight: .medium, design: .default)
    static let caption = Font.system(size: 12, weight: .medium, design: .default)
    static let badge = Font.system(size: 12, weight: .bold, design: .monospaced)
}
