import SwiftUI

/// App-level navigation. Purely presentational: it reports the tapped
/// destination and owns no navigation state of its own.
struct KantaTabBar: View {
    enum Destination: String, CaseIterable, Identifiable {
        case stage, library, queue, more

        var id: String { rawValue }

        var title: String {
            switch self {
            case .stage: return "Stage"
            case .library: return "Library"
            case .queue: return "Queue"
            case .more: return "More"
            }
        }

        var icon: String {
            switch self {
            case .stage: return "music.note"
            case .library: return "square.grid.2x2"
            case .queue: return "list.bullet"
            case .more: return "ellipsis"
            }
        }
    }

    let selection: Destination
    let onSelect: (Destination) -> Void

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Destination.allCases) { destination in
                Button {
                    onSelect(destination)
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: destination.icon)
                            .font(.system(size: 19, weight: .semibold))
                        Text(destination.title)
                            .font(KantaTypography.caption)
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .foregroundStyle(
                        destination == selection
                            ? KantaColors.textPrimary
                            : KantaColors.textTertiary
                    )
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("tab." + destination.rawValue)
                .accessibilityAddTraits(destination == selection ? [.isSelected] : [])
            }
        }
        .padding(.horizontal, 8)
        .padding(.top, 10)
        .padding(.bottom, 4)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(KantaColors.hairline)
                .frame(height: 1)
        }
    }
}
