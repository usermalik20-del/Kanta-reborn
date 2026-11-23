import Testing
@testable import LocalNetworkControl

@Test func browserRemoteCapacityRejectionsDoNotConsumeInvalidCodeBudget() {
    var admission = BrowserRemotePairingAdmission()

    let fullWithWrongCode = admission.evaluate(
        pairingWindowOpen: true,
        guestCount: BrowserRemotePairingAdmission.maximumGuests,
        codeMatches: false
    )
    #expect(fullWithWrongCode == .capacityReached)
    #expect(admission.invalidCodeAttempts == 0)

    let validAfterGuestLeaves = admission.evaluate(
        pairingWindowOpen: true,
        guestCount: BrowserRemotePairingAdmission.maximumGuests - 1,
        codeMatches: true
    )
    #expect(validAfterGuestLeaves == .accepted)
}

@Test func browserRemoteClosesAfterThreeIncorrectCodes() {
    var admission = BrowserRemotePairingAdmission()

    for _ in 0..<BrowserRemotePairingAdmission.maximumInvalidCodeAttempts {
        let decision = admission.evaluate(
            pairingWindowOpen: true,
            guestCount: 0,
            codeMatches: false
        )
        #expect(decision == .invalidCode)
    }

    #expect(admission.invalidCodeAttempts == BrowserRemotePairingAdmission.maximumInvalidCodeAttempts)
    let correctCodeAfterBudget = admission.evaluate(
        pairingWindowOpen: true,
        guestCount: 0,
        codeMatches: true
    )
    #expect(correctCodeAfterBudget == .pairingClosed)
}

@Test func browserRemoteClosedWindowDoesNotConsumeInvalidCodeBudget() {
    var admission = BrowserRemotePairingAdmission()
    let decision = admission.evaluate(
        pairingWindowOpen: false,
        guestCount: 0,
        codeMatches: false
    )
    #expect(decision == .pairingClosed)
    #expect(admission.invalidCodeAttempts == 0)
}
