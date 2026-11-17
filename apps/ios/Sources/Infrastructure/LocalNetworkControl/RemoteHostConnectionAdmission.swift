import Foundation

/// Bounds the number of accepted native Bonjour remote sessions, including
/// peers that have not completed the PIN exchange yet.
struct RemoteHostConnectionAdmission: Sendable {
    static let defaultMaximumConnections = 4

    private(set) var activeIDs: Set<UUID> = []
    let maximumConnections: Int

    init(maximumConnections: Int = Self.defaultMaximumConnections) {
        self.maximumConnections = max(0, maximumConnections)
    }

    var count: Int { activeIDs.count }

    mutating func admit(_ id: UUID) -> Bool {
        guard maximumConnections > 0,
              activeIDs.count < maximumConnections,
              !activeIDs.contains(id)
        else {
            return false
        }
        activeIDs.insert(id)
        return true
    }

    mutating func release(_ id: UUID) {
        activeIDs.remove(id)
    }

    mutating func reset() {
        activeIDs.removeAll(keepingCapacity: false)
    }
}
