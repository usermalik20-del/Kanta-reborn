import Foundation

struct BrowserRemotePairingAdmission {
    enum Decision: Equatable {
        case accepted
        case pairingClosed
        case capacityReached
        case invalidCode
    }

    static let maximumGuests = 4
    static let maximumInvalidCodeAttempts = 3

    private(set) var invalidCodeAttempts = 0

    mutating func evaluate(
        pairingWindowOpen: Bool,
        guestCount: Int,
        codeMatches: Bool
    ) -> Decision {
        guard pairingWindowOpen, invalidCodeAttempts < Self.maximumInvalidCodeAttempts else {
            return .pairingClosed
        }
        guard guestCount < Self.maximumGuests else {
            return .capacityReached
        }
        guard codeMatches else {
            invalidCodeAttempts += 1
            return .invalidCode
        }
        return .accepted
    }
}
