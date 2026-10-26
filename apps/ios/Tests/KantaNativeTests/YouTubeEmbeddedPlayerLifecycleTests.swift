import Foundation
import GeloKaraokeCore
import UIKit
import WebKit
import XCTest

/// Native component lifetime; no remote video, account or advertisement is a fixture.
final class YouTubeEmbeddedPlayerLifecycleTests: XCTestCase {
    @MainActor
    private func makeSession() throws -> YouTubePreviewLifecycle.Session {
        let reference = try XCTUnwrap(YouTubeVideoReference(url: "https://youtu.be/abcdefghijk"))
        var lifecycle = YouTubePreviewLifecycle()
        return lifecycle.open(reference)
    }

    @MainActor
    private func makeView() -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .nonPersistent()
        return WKWebView(frame: .zero, configuration: configuration)
    }

    @MainActor
    func testReplacementRetiresOldNativeViewBeforeAttachingNext() throws {
        let owner = YouTubeEmbeddedPlayerOwner()
        let firstSession = try makeSession()
        let secondSession = try makeSession()
        XCTAssertNotEqual(firstSession.id, secondSession.id)
        var failures: [UUID] = []
        let first = YouTubeEmbeddedPlayerView.Coordinator(session: firstSession) { failures.append($0) }
        let second = YouTubeEmbeddedPlayerView.Coordinator(session: secondSession) { failures.append($0) }
        let firstView = makeView()
        let secondView = makeView()
        defer { owner.retire() }
        owner.claim(first)
        first.attach(firstView)
        XCTAssertTrue(firstView.navigationDelegate === first)
        owner.claim(second)
        XCTAssertNil(firstView.navigationDelegate, "Replacement left the old native delegate active")
        second.attach(secondView)
        XCTAssertTrue(secondView.navigationDelegate === second)
        first.webViewWebContentProcessDidTerminate(firstView)
        XCTAssertTrue(failures.isEmpty, "Retired player reported a late failure")
        XCTAssertTrue(secondView.navigationDelegate === second, "Late callback affected replacement")
        owner.retire()
        owner.retire()
        XCTAssertNil(secondView.navigationDelegate)
    }

    @MainActor
    func testRetirementDetachesNativeViewSynchronously() throws {
        let owner = YouTubeEmbeddedPlayerOwner()
        let session = try makeSession()
        let coordinator = YouTubeEmbeddedPlayerView.Coordinator(session: session) { _ in
            XCTFail("Retired view emitted a failure")
        }
        let container = UIView(frame: CGRect(x: 0, y: 0, width: 320, height: 180))
        let view = makeView()
        container.addSubview(view)
        owner.claim(coordinator)
        coordinator.attach(view)
        XCTAssertTrue(view.superview === container)

        owner.retire()

        XCTAssertNil(view.navigationDelegate, "Retirement must invalidate callbacks synchronously")
        XCTAssertNil(view.superview, "Retirement must detach WKWebView before deferred WebKit cleanup")
    }

    @MainActor
    func testForegroundLossRetiresNativeViewAndDoesNotRestoreIt() throws {
        let owner = YouTubeEmbeddedPlayerOwner()
        var failures: [UUID] = []
        let session = try makeSession()
        let coordinator = YouTubeEmbeddedPlayerView.Coordinator(session: session) { failures.append($0) }
        let view = makeView()
        defer { owner.retire() }
        owner.claim(coordinator)
        coordinator.attach(view)
        NotificationCenter.default.post(name: UIApplication.willResignActiveNotification, object: nil)
        XCTAssertNil(view.navigationDelegate, "Foreground loss left the online delegate attached")
        NotificationCenter.default.post(name: UIApplication.didBecomeActiveNotification, object: nil)
        XCTAssertNil(view.navigationDelegate, "Foreground return resurrected online playback")
        coordinator.webViewWebContentProcessDidTerminate(view)
        XCTAssertTrue(failures.isEmpty, "Backgrounded player reported stale failure")
    }

    @MainActor
    func testProcessFailureRetiresExactlyOnceAndOwnerCanReopen() throws {
        let owner = YouTubeEmbeddedPlayerOwner()
        var failures: [UUID] = []
        let failedSession = try makeSession()
        let failed = YouTubeEmbeddedPlayerView.Coordinator(session: failedSession) { failures.append($0) }
        let failedView = makeView()
        defer { owner.retire() }
        owner.claim(failed)
        failed.attach(failedView)
        failed.webViewWebContentProcessDidTerminate(failedView)
        XCTAssertEqual(failures, [failedSession.id])
        XCTAssertNil(failedView.navigationDelegate)
        failed.webViewWebContentProcessDidTerminate(failedView)
        failed.retire()
        XCTAssertEqual(failures, [failedSession.id], "Duplicate native failure escaped terminal retirement")
        let reopenedSession = try makeSession()
        let reopened = YouTubeEmbeddedPlayerView.Coordinator(session: reopenedSession) { failures.append($0) }
        let reopenedView = makeView()
        owner.claim(reopened)
        reopened.attach(reopenedView)
        XCTAssertNotEqual(reopenedSession.id, failedSession.id)
        XCTAssertTrue(reopenedView.navigationDelegate === reopened)
        failed.webViewWebContentProcessDidTerminate(failedView)
        XCTAssertEqual(failures, [failedSession.id])
        XCTAssertTrue(reopenedView.navigationDelegate === reopened)
    }

    @MainActor
    func testNativeAudioClaimRetiresWebViewBeforeSessionConfiguration() throws {
        let ownership = KaraokeAudioOwnership()
        let owner = YouTubeEmbeddedPlayerOwner()
        let session = try makeSession()
        let coordinator = YouTubeEmbeddedPlayerView.Coordinator(session: session) { _ in
            XCTFail("Retired view emitted a failure")
        }
        let view = makeView()
        let accepted = try ownership.claimOnlineAudio(session.id) { owner.retire() }
        XCTAssertTrue(accepted)
        owner.claim(coordinator)
        coordinator.attach(view)
        var configured = false
        ownership.configurePlaybackSession = {
            XCTAssertNil(view.navigationDelegate, "Native session configured before WebKit retirement")
            configured = true
        }
        try ownership.prepareNativeAudio()
        XCTAssertTrue(configured)
        XCTAssertFalse(ownership.onlineActive)
        coordinator.webViewWebContentProcessDidTerminate(view)
        // Microphone permission starts also retire the native view synchronously.
        let second = try makeSession()
        let secondCoordinator = YouTubeEmbeddedPlayerView.Coordinator(session: second) { _ in XCTFail() }
        ownership.configurePlaybackSession = nil
        let reopened = try ownership.claimOnlineAudio(second.id) { owner.retire() }
        XCTAssertTrue(reopened)
        owner.claim(secondCoordinator)
        secondCoordinator.attach(view)
        ownership.setMicrophoneBusy(true)
        XCTAssertNil(view.navigationDelegate)
        XCTAssertFalse(ownership.onlineActive)
    }

    @MainActor
    func testIntermissionOnlineHandoffRequiresExplicitRestart() throws {
        let ownership = KaraokeAudioOwnership()
        let suite = "KantaIntermissionTest-" + UUID().uuidString
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer {
            defaults.removePersistentDomain(forName: suite)
            try? FileManager.default.removeItem(at: root)
        }
        let intermission = KantaIntermissionMusic(audioOwnership: ownership,
            defaults: defaults, directory: root.appendingPathComponent("copied"))
        ownership.stopAuxiliaryAudio = { [weak intermission] in intermission?.stopForOnlinePlayback() }
        // Synthetic silence exercises real AVAudioPlayer ownership without user media.
        let source = root.appendingPathComponent("silence.wav")
        var bytes = Data("RIFF".utf8)
        func u32(_ value: UInt32) {
            var value = value.littleEndian
            withUnsafeBytes(of: &value) { bytes.append(contentsOf: $0) }
        }
        func u16(_ value: UInt16) {
            var value = value.littleEndian
            withUnsafeBytes(of: &value) { bytes.append(contentsOf: $0) }
        }
        u32(36 + 44_100 * 2); bytes.append(Data("WAVEfmt ".utf8)); u32(16)
        u16(1); u16(1); u32(44_100); u32(88_200); u16(2); u16(16)
        bytes.append(Data("data".utf8)); u32(44_100 * 2)
        bytes.append(Data(repeating: 0, count: 44_100 * 2))
        try bytes.write(to: source)
        try intermission.importMusic(source)
        intermission.activate()
        intermission.setEnabled(true)
        defer { intermission.deactivate() }
        XCTAssertTrue(intermission.isPlaying)
        let onlineID = UUID()
        var retired = 0
        let accepted = try ownership.claimOnlineAudio(onlineID) { retired += 1 }
        XCTAssertTrue(accepted)
        XCTAssertFalse(intermission.enabled)
        XCTAssertFalse(intermission.isPlaying)
        intermission.sync(.playing)
        intermission.sync(.stopped)
        XCTAssertFalse(intermission.isPlaying, "Snapshot polling restarted intermission over online playback")
        XCTAssertTrue(ownership.ownsOnlineAudio(onlineID))
        intermission.setEnabled(true)
        XCTAssertEqual(retired, 1)
        XCTAssertTrue(intermission.isPlaying)
        XCTAssertFalse(ownership.onlineActive)
    }

}
