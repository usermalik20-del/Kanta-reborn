import Foundation
import Testing
@testable import GeloKaraokeCore

@MainActor
@Test func audioOwnershipFencesReplacementMicrophoneForegroundAndSessionFailure() throws {
    let ownership = KaraokeAudioOwnership()
    let first = UUID(), replacement = UUID()
    var firstRetired = 0, replacementRetired = 0, sessions = 0, auxiliaryStops = 0
    ownership.configurePlaybackSession = { sessions += 1 }
    ownership.stopAuxiliaryAudio = { auxiliaryStops += 1 }
    let acceptedFirst = try ownership.claimOnlineAudio(first) { firstRetired += 1 }
    let acceptedReplacement = try ownership.claimOnlineAudio(replacement) { replacementRetired += 1 }
    #expect(acceptedFirst && acceptedReplacement)
    #expect(firstRetired == 1)
    ownership.releaseOnlineAudio(first)
    #expect(ownership.ownsOnlineAudio(replacement))
    ownership.setMicrophoneBusy(true)
    #expect(replacementRetired == 1)
    #expect(!ownership.onlineActive)
    let blocked = try ownership.claimOnlineAudio(UUID()) {}
    #expect(!blocked)
    try ownership.prepareNativeAudio()
    #expect(sessions == 2, "Capture must retain its playAndRecord session")
    ownership.setMicrophoneBusy(false)
    ownership.setForeground(false)
    let background = try ownership.claimOnlineAudio(UUID()) {}
    #expect(!background)
    ownership.setForeground(true)
    #expect(!ownership.onlineActive, "Foreground cannot resurrect playback")
    ownership.onlineEntryAllowed = { false }
    let monitorBlocked = try ownership.claimOnlineAudio(UUID()) {}
    #expect(!monitorBlocked)
    ownership.onlineEntryAllowed = { true }
    ownership.configurePlaybackSession = { throw CocoaError(.userCancelled) }
    #expect(throws: CocoaError.self) { try ownership.claimOnlineAudio(UUID()) {} }
    #expect(!ownership.onlineActive)
    #expect(auxiliaryStops == 3)
}
