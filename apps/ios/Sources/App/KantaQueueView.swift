import GeloKaraokeCore
import SwiftUI

/// Tonight's reservations.
///
/// A read-only projection of the shared controller snapshot: it issues no
/// commands and owns no playback state, so it cannot become a second queue.
struct KantaQueueView: View {
    let services: AppServices

    @State private var queue: [QueueItem] = []
    @State private var nowPlayingID: SongID?
    @State private var loaded = false

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                if queue.isEmpty {
                    if loaded {
                        KantaEmptyState(
                            icon: "list.bullet",
                            title: "Queue is empty",
                            description: "Reserve a song from the Library and it lines up here.",
                            actionTitle: nil,
                            action: nil,
                            style: .compact
                        )
                    }
                } else {
                    ForEach(queue) { item in
                        row(for: item)
                    }
                }
            }
            .padding(20)
        }
        .background { KantaBackground().allowsHitTesting(false) }
        .navigationTitle("Queue")
        .refreshable { await load() }
        .task { await load() }
    }

    private func row(for item: QueueItem) -> some View {
        HStack(spacing: 12) {
            KantaCoverArt(
                seed: item.song.title + " " + (item.song.artist ?? ""),
                cornerRadius: 13
            )
            .frame(width: 48, height: 48)

            VStack(alignment: .leading, spacing: 3) {
                Text(item.song.title)
                    .font(KantaTypography.songTitle)
                    .foregroundStyle(KantaColors.textPrimary)
                    .lineLimit(1)

                Text(subtitle(for: item))
                    .font(KantaTypography.metadata)
                    .foregroundStyle(KantaColors.textSecondary)
                    .lineLimit(1)
            }

            Spacer(minLength: 8)

            if item.song.id == nowPlayingID {
                Text("ON STAGE")
                    .font(KantaTypography.badge)
                    .foregroundStyle(KantaColors.live)
            } else if let position = position(of: item) {
                Text("#" + String(position))
                    .font(KantaTypography.badge)
                    .foregroundStyle(KantaColors.textTertiary)
            }
        }
        .padding(12)
        .background(
            KantaColors.surface,
            in: RoundedRectangle(cornerRadius: 18, style: .continuous)
        )
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(KantaColors.hairline, lineWidth: 1)
        }
    }

    private func subtitle(for item: QueueItem) -> String {
        if let singer = item.requestedBy, !singer.isEmpty {
            return singer
        }
        return item.source == .local ? "Local library" : "Online"
    }

    private func position(of item: QueueItem) -> Int? {
        guard let index = queue.firstIndex(where: { $0.id == item.id }) else { return nil }
        return index + 1
    }

    private func load() async {
        guard let controller = services.controller else {
            loaded = true
            return
        }
        let snapshot = await controller.snapshot()
        queue = snapshot.queue
        nowPlayingID = snapshot.nowPlaying?.id
        loaded = true
    }
}
