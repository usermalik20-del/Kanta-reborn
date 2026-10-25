import GeloKaraokeCore
import LocalNetworkControl
import SwiftUI

@MainActor
struct RemoteView: View {
    @StateObject private var browser: BonjourHostBrowser
    @StateObject private var client: RemoteHostClient

    @State private var selectedHostID: String?
    @State private var pairingPIN = ""
    @State private var catalogQuery = ""
    @State private var singerName = ""
    @State private var pendingCatalogSearch: Task<Void, Never>?

    let browserHost: BrowserRemoteHost?

    init(browserHost: BrowserRemoteHost? = nil) {
        self.browserHost = browserHost
        _browser = StateObject(
            wrappedValue: BonjourHostBrowser()
        )
        _client = StateObject(
            wrappedValue: RemoteHostClient()
        )
    }

    var body: some View {
        List {
            if let browserHost {
                Section("Apps and browser guests") {
                    NavigationLink("Host / Companion Remote") { BrowserCompanionView(host: browserHost) }
                        .accessibilityIdentifier("browser.companion.open")
                    Text("Approved remote interface inside KANTA or a paired browser, with private HTTPS hosting on iOS and Android.").font(.caption)
                }
            }
            Section("Encrypted Android companion") {
                NavigationLink("Control an Android host") { EncryptedCompanionView() }
                    .accessibilityIdentifier("companion.open")
                Text("Uses the full Android pairing code. No plaintext fallback.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Section("Legacy iPhone remote") {
                Text("The nearby-host PIN connection below is plaintext and incompatible with Android. Use only on a trusted local network.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Section("Nearby hosts") {
                if browser.hosts.isEmpty {
                    emptyState
                } else {
                    ForEach(browser.hosts) { host in
                        Button {
                            selectedHostID = host.id
                            client.disconnect()
                            catalogQuery = ""
                        } label: {
                            HStack {
                                VStack(
                                    alignment: .leading,
                                    spacing: 4
                                ) {
                                    Label(
                                        host.name,
                                        systemImage:
                                            "dot.radiowaves.left.and.right"
                                    )
                                    .font(.headline)

                                    if let interfaceName =
                                        host.interfaceName {
                                        Text(interfaceName)
                                            .font(.caption)
                                            .foregroundStyle(
                                                .secondary
                                            )
                                    }
                                }

                                Spacer()

                                if selectedHostID == host.id {
                                    Image(
                                        systemName:
                                            "checkmark.circle.fill"
                                    )
                                }
                            }
                        }
                        .buttonStyle(.plain)
                        .padding(.vertical, 3)
                        .accessibilityHint(
                            "Select this host for PIN pairing."
                        )
                        .accessibilityValue(
                            selectedHostID == host.id
                                ? "Selected"
                                : "Not selected"
                        )
                    }
                }
            }

            if let selectedHost {
                Section("Pair with " + selectedHost.name) {
                    TextField(
                        "6-digit PIN",
                        text: $pairingPIN
                    )
                    .keyboardType(.numberPad)
                    .textContentType(.oneTimeCode)
                    .onChange(of: pairingPIN) {
                        pairingPIN = String(
                            pairingPIN
                                .filter(\.isNumber)
                                .prefix(PairingCode.digitCount)
                        )
                    }

                    if isPaired {
                        LabeledContent(
                            "Status",
                            value: "Paired"
                        )

                        Button(
                            "Disconnect",
                            role: .destructive
                        ) {
                            client.disconnect()
                        }
                    } else {
                        Button("Connect") {
                            client.connect(
                                to: selectedHost,
                                pairingCode: pairingPIN
                            )
                        }
                        .disabled(
                            pairingPIN.count
                                != PairingCode.digitCount
                        )

                        LabeledContent(
                            "Status",
                            value: clientStatusText
                        )
                    }

                    if let lastError = client.lastError {
                        Text(lastError)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            if isPaired {
                Section("Singer Reservation") {
                    TextField("Singer name (optional)", text: $singerName)
                        .textInputAutocapitalization(.words)
                    Text(
                        "New reservations use this singer name. " +
                        "Only paired devices can change the Host queue."
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

                Section("Songbook · Songs on Host") {
                    TextField(
                        "Search title, artist, or code",
                        text: $catalogQuery
                    )
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                    KantaVoiceSearchAccessory(query: $catalogQuery)

                    if client.isSearching && client.catalogSongs.isEmpty {
                        HStack {
                            ProgressView()
                            Text("Searching host library…")
                        }
                    } else if client.catalogSongs.isEmpty {
                        Text("No matching songs")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(client.catalogSongs) { song in
                            HStack {
                                VStack(
                                    alignment: .leading,
                                    spacing: 3
                                ) {
                                    Text(song.title)
                                    if let code = song.code {
                                        Text("Code " + code)
                                            .font(.caption.monospacedDigit().bold())
                                            .foregroundStyle(GeloVisualStyle.accent)
                                    }
                                    if let artist = song.artist,
                                       !artist.isEmpty {
                                        Text(artist)
                                            .font(.caption)
                                            .foregroundStyle(
                                                .secondary
                                            )
                                    }
                                }

                                Spacer()

                                if song.isPlayable {
                                    Button {
                                        client.enqueue(song, singer: singerName)
                                    } label: {
                                        Label(
                                            "Reserve",
                                            systemImage: "plus.circle"
                                        )
                                    }
                                    .buttonStyle(.bordered)
                                    .accessibilityLabel("Reserve " + song.title)
                                } else {
                                    Text("Catalog only")
                                        .font(.caption2.weight(.semibold))
                                        .foregroundStyle(.secondary)
                                        .accessibilityLabel(
                                            "Song title only. Media unavailable."
                                        )
                                }
                            }
                        }
                    }

                    if client.hasMoreSongs {
                        Button {
                            client.loadMoreSongs()
                        } label: {
                            Label("Load more songs", systemImage: "arrow.down.circle")
                        }
                        .disabled(client.isSearching)
                    }
                    Text("Showing \(client.catalogSongs.count) matching songs")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }

                Section("Party SFX Pad") {
                    Text(
                        "Play effects on the paired Host. Only the Host " +
                        "holds imported samples."
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)

                    LazyVGrid(
                        columns: [
                            GridItem(.adaptive(minimum: 115), spacing: 8)
                        ],
                        spacing: 8
                    ) {
                        ForEach(PartySFX.allCases) { sound in
                            Button {
                                client.send(.triggerSFX(sound))
                            } label: {
                                VStack(spacing: 5) {
                                    Image(systemName: sound.symbol)
                                    Text(sound.title)
                                        .font(.caption)
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.8)
                                }
                                .frame(maxWidth: .infinity, minHeight: 57)
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                    Button(role: .destructive) {
                        client.send(.stopSFX)
                    } label: {
                        Label("Stop All SFX", systemImage: "stop.fill")
                    }
                    .buttonStyle(.bordered)
                }

                Section("Now Playing") {
                    if let song = client.snapshot.nowPlaying {
                        VStack(
                            alignment: .leading,
                            spacing: 4
                        ) {
                            Text(song.title)
                                .font(.headline)
                            Text(
                                timeText(
                                    position:
                                        client.snapshot
                                            .positionMilliseconds,
                                    duration:
                                        client.snapshot
                                            .durationMilliseconds
                                )
                            )
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                        }
                    } else {
                        Text("Nothing playing")
                            .foregroundStyle(.secondary)
                    }

                    HStack {
                        Button {
                            client.send(
                                client.snapshot.transport
                                    == .playing
                                ? .pause
                                : .play
                            )
                        } label: {
                            Image(
                                systemName:
                                    client.snapshot.transport
                                        == .playing
                                    ? "pause.fill"
                                    : "play.fill"
                            )
                            .font(.title2)
                        }
                        .accessibilityLabel(
                            client.snapshot.transport == .playing
                                ? "Pause"
                                : "Play"
                        )

                        Spacer()

                        Button {
                            client.send(.next)
                        } label: {
                            Image(systemName: "forward.fill")
                        }
                        .accessibilityLabel("Next song")
                    }
                    .buttonStyle(.borderless)
                }

                Section("Playback") {
                    LabeledContent(
                        "Volume",
                        value:
                            String(
                                Int(
                                    client.snapshot.volume * 100
                                )
                            ) + "%"
                    )

                    Slider(
                        value: Binding(
                            get: {
                                Double(client.snapshot.volume)
                            },
                            set: { value in
                                client.send(
                                    .setVolume(Float(value))
                                )
                            }
                        ),
                        in: 0...1
                    )
                    .accessibilityLabel("Playback volume")
                    .accessibilityValue(
                        String(
                            Int(client.snapshot.volume * 100)
                        ) + " percent"
                    )

                    HStack {
                        Button("Pitch −") {
                            client.send(
                                .setPitch(
                                    semitones: max(
                                        -12,
                                        client.snapshot
                                            .pitchSemitones - 1
                                    )
                                )
                            )
                        }
                        Spacer()
                        Text(
                            String(
                                client.snapshot.pitchSemitones
                            ) + " st"
                        )
                        .monospacedDigit()
                        Spacer()
                        Button("Pitch +") {
                            client.send(
                                .setPitch(
                                    semitones: min(
                                        12,
                                        client.snapshot
                                            .pitchSemitones + 1
                                    )
                                )
                            )
                        }
                    }

                    HStack {
                        Button("Tempo −") {
                            client.send(
                                .setTempo(
                                    ratio: max(
                                        0.5,
                                        client.snapshot
                                            .tempoRatio - 0.05
                                    )
                                )
                            )
                        }
                        Spacer()
                        Text(
                            String(
                                format: "%.2fx",
                                client.snapshot.tempoRatio
                            )
                        )
                        .monospacedDigit()
                        Spacer()
                        Button("Tempo +") {
                            client.send(
                                .setTempo(
                                    ratio: min(
                                        2.0,
                                        client.snapshot
                                            .tempoRatio + 0.05
                                    )
                                )
                            )
                        }
                    }
                }

                Section(
                    "Queue (" +
                    String(client.snapshot.queue.count) +
                    ")"
                ) {
                    if client.snapshot.queue.isEmpty {
                        Text("Queue is empty")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(client.snapshot.queue) { item in
                            HStack(spacing: 8) {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(item.song.title)
                                    if let singer = item.requestedBy {
                                        Text("Singer · " + singer)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                Button {
                                    move(item, up: true)
                                } label: { Image(systemName: "arrow.up") }
                                .accessibilityLabel("Move song up")
                                Button {
                                    move(item, up: false)
                                } label: { Image(systemName: "arrow.down") }
                                .accessibilityLabel("Move song down")
                            }
                            .buttonStyle(.borderless)
                            .swipeActions {
                                Button(role: .destructive) {
                                    client.send(.removeQueueItem(item.id))
                                } label: {
                                    Label("Remove", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
            }

            Section("Network policy") {
                Text(
                    "Discovery, PIN pairing, remote search, and " +
                    "controls stay on the local network. KANTA " +
                    "Karaoke does not require a cloud account or " +
                    "Internet connection."
                )
                .foregroundStyle(.secondary)
            }
        }
        .navigationTitle(KantaTerminology.control)
        .task {
            browser.start()
        }
        .onChange(of: catalogQuery) {
            pendingCatalogSearch?.cancel()
            let submitted = catalogQuery
            pendingCatalogSearch = Task {
                try? await Task.sleep(for: .milliseconds(200))
                guard !Task.isCancelled, isPaired,
                      catalogQuery == submitted else { return }
                client.search(submitted)
            }
        }
        .onChange(of: client.status) {
            pendingCatalogSearch?.cancel()
            if isPaired {
                client.search(catalogQuery)
            }
        }
        .onDisappear {
            pendingCatalogSearch?.cancel()
            client.disconnect()
            pairingPIN = ""
            browser.stop()
        }
    }

    private var selectedHost: DiscoveredKaraokeHost? {
        guard let selectedHostID else { return nil }
        return browser.hosts.first {
            $0.id == selectedHostID
        }
    }

    private func move(_ item: QueueItem, up: Bool) {
        let queue = client.snapshot.queue
        guard let position = queue.firstIndex(where: { $0.id == item.id })
        else { return }
        if up {
            guard position > 0 else { return }
            client.send(.moveQueueItem(
                id: item.id, before: queue[position - 1].id
            ))
        } else {
            guard position + 1 < queue.count else { return }
            let afterNext = position + 2
            client.send(.moveQueueItem(
                id: item.id,
                before: afterNext < queue.count
                    ? queue[afterNext].id : nil
            ))
        }
    }

    private var isPaired: Bool {
        if case .paired = client.status {
            return true
        }
        return false
    }

    private var clientStatusText: String {
        switch client.status {
        case .idle:
            return "Not connected"
        case .connecting:
            return "Connecting"
        case .pairing:
            return "Pairing"
        case .paired:
            return "Paired"
        case .failed:
            return "Connection failed"
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        switch browser.status {
        case .idle, .searching:
            HStack {
                ProgressView()
                Text("Searching local network…")
            }

        case .ready:
            ContentUnavailableView(
                "No KANTA hosts found",
                systemImage:
                    "dot.radiowaves.left.and.right",
                description: Text(
                    "Open KANTA on another device and start a Host session " +
                    "on the same local network."
                )
            )

        case let .failed(message):
            ContentUnavailableView(
                "Local discovery unavailable",
                systemImage: "wifi.exclamationmark",
                description: Text(message)
            )
        }
    }

    private func timeText(
        position: Int64,
        duration: Int64?
    ) -> String {
        let current = format(milliseconds: position)
        guard let duration else { return current }
        return current + " / " + format(milliseconds: duration)
    }

    private func format(milliseconds: Int64) -> String {
        let totalSeconds = max(0, milliseconds) / 1_000
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(
            format: "%d:%02d",
            minutes,
            seconds
        )
    }
}

