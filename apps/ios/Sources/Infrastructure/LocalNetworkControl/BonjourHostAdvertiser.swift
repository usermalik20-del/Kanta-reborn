import Combine
import Foundation
import GeloKaraokeCore
import Network

public enum BonjourAdvertiserStatus:
    Equatable,
    Sendable
{
    case idle
    case starting
    case ready
    case failed(String)
}

@MainActor
public final class BonjourHostAdvertiser: ObservableObject {
    @Published public private(set) var status:
        BonjourAdvertiserStatus = .idle
    @Published public private(set) var pairingCode:
        PairingCode
    @Published public private(set) var pairedControllerCount = 0

    public let displayName: String

    private let controller: (any KaraokeController)?
    private let catalogSearch:
        (@Sendable (String, Int, Int) async -> [LibrarySong])?
    private var lifecycleGeneration = UUID()
    private var listener: NWListener?
    private var sessions: [UUID: HostRemoteConnection] = [:]
    private var snapshotTask: Task<Void, Never>?
    private let queue = DispatchQueue(
        label: "com.gelo.karaoke.bonjour.host"
    )

    public init(
        controller: (any KaraokeController)? = nil,
        catalogSearch:
            (@Sendable (String, Int, Int) async -> [LibrarySong])? = nil,
        displayName: String = "KANTA Karaoke",
        pairingCode: PairingCode = .generate()
    ) {
        self.controller = controller
        self.catalogSearch = catalogSearch
        self.displayName = displayName
        self.pairingCode = pairingCode
    }

    deinit {
        snapshotTask?.cancel()
        listener?.cancel()
    }

    public func start() {
        guard listener == nil else { return }
        let generation = UUID()
        lifecycleGeneration = generation

        guard controller != nil else {
            status = .failed(
                "The local karaoke controller is unavailable."
            )
            return
        }

        do {
            status = .starting

            let listener = try NWListener(using: .tcp)
            listener.service = NWListener.Service(
                name: displayName,
                type: GeloBonjour.serviceType
            )

            listener.stateUpdateHandler = {
                [weak self] state in
                let nextStatus: BonjourAdvertiserStatus
                switch state {
                case .setup, .waiting:
                    nextStatus = .starting
                case .ready:
                    nextStatus = .ready
                case let .failed(error):
                    nextStatus = .failed(
                        error.localizedDescription
                    )
                case .cancelled:
                    nextStatus = .idle
                @unknown default:
                    nextStatus = .starting
                }

                Task { @MainActor [weak self] in
                    guard let self, self.lifecycleGeneration == generation else { return }
                    self.status = nextStatus
                }
            }

            listener.newConnectionHandler = {
                [weak self] connection in
                Task { @MainActor [weak self] in
                    guard let self, self.lifecycleGeneration == generation else {
                        connection.cancel()
                        return
                    }
                    self.accept(connection)
                }
            }

            self.listener = listener
            listener.start(queue: queue)
            startSnapshotPump()
        } catch {
            status = .failed(error.localizedDescription)
        }
    }

    public func stop() {
        lifecycleGeneration = UUID()
        snapshotTask?.cancel()
        snapshotTask = nil

        let activeSessions = Array(sessions.values)
        sessions.removeAll(keepingCapacity: false)
        pairedControllerCount = 0

        for session in activeSessions {
            session.onClose = nil
            session.cancel()
        }

        listener?.cancel()
        listener = nil
        status = .idle
    }

    public func rotatePairingCode() {
        pairingCode = .generate()

        // Rotating the PIN is also an explicit revocation action.
        // Existing remote sessions must authenticate again.
        let activeSessions = Array(sessions.values)
        sessions.removeAll(keepingCapacity: false)
        pairedControllerCount = 0

        for session in activeSessions {
            session.onClose = nil
            session.cancel()
        }
    }

    private func accept(_ connection: NWConnection) {
        guard let controller else {
            connection.cancel()
            return
        }

        let session = HostRemoteConnection(
            connection: connection,
            controller: controller,
            catalogSearch: catalogSearch,
            expectedPairingCode: pairingCode,
            queue: queue
        )

        session.onClose = { [weak self] id in
            self?.sessions.removeValue(forKey: id)
            self?.refreshPairedControllerCount()
        }
        session.onSnapshot = { [weak self] snapshot in
            self?.broadcast(snapshot)
        }

        sessions[session.id] = session
        session.start()
    }

    private func startSnapshotPump() {
        snapshotTask?.cancel()

        snapshotTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let controller = self?.controller else {
                    return
                }

                let snapshot = await controller.snapshot()
                self?.broadcast(snapshot)

                try? await Task.sleep(
                    nanoseconds: 500_000_000
                )
            }
        }
    }

    private func broadcast(_ snapshot: KaraokeSnapshot) {
        let activeSessions = Array(sessions.values)
        for session in activeSessions {
            session.sendSnapshot(snapshot)
        }
        refreshPairedControllerCount()
    }

    private func refreshPairedControllerCount() {
        pairedControllerCount = sessions.values.reduce(
            into: 0
        ) { count, session in
            if session.isPaired {
                count += 1
            }
        }
    }
}


