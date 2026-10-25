import XCTest

/// Actual starter playback and separate explicitly labeled demo interaction tests.
final class KantaVisualWalkthroughUITests: XCTestCase {
    private let waitSeconds: TimeInterval = 25
    override func setUp() async throws {
        continueAfterFailure = false
        await MainActor.run { XCUIDevice.shared.orientation = .portrait }
    }

    override func tearDown() async throws {
        await MainActor.run { XCUIDevice.shared.orientation = .portrait }
    }

    @MainActor
    func testStarterPlaybackAndControls() throws {
        let app = XCUIApplication()
        app.launchEnvironment["KANTA_DEMO_MODE"] = "0"
        app.launch()
        capture("01-real-main-stage", app)
        openMenu(in: app, expecting: "Song Library")
        let library = app.buttons["Song Library"]
        waitForRendered(library, in: app)
        library.tap()
        let title = app.staticTexts["Morning Light"].firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: waitSeconds))
        for _ in 0..<8 where !title.isHittable { app.swipeUp() }
        capture("02-real-starter-library", app)
        let play = app.buttons["starter.play.morning-light"]
        for _ in 0..<5 where !play.isHittable { app.swipeUp() }
        XCTAssertTrue(play.isHittable)
        play.tap()
        app.buttons["Done"].firstMatch.tap()
        let transport = app.buttons["stage.playPause"]
        XCTAssertTrue(transport.waitForExistence(timeout: 10))
        expectation(for: NSPredicate(format: "enabled == true AND label == 'Pause'"), evaluatedWith: transport)
        waitForExpectations(timeout: waitSeconds)
        let currentSong = app.staticTexts["stage.currentSong"]
        expectation(for: NSPredicate(format: "label CONTAINS %@", "Morning Light"), evaluatedWith: currentSong)
        waitForExpectations(timeout: waitSeconds)
        capture("03-real-starter-playing", app)
        transport.tap()
        expectation(for: NSPredicate(format: "label == 'Play'"), evaluatedWith: transport)
        waitForExpectations(timeout: 5)
        capture("04-real-paused", app)
        transport.tap()
        expectation(for: NSPredicate(format: "label == 'Pause'"), evaluatedWith: transport)
        waitForExpectations(timeout: 5)
        capture("05-real-resumed", app)
        openMenu(in: app, expecting: "Song Library")
        let deadline = Date().addingTimeInterval(1)
        while Date() < deadline {
            XCTAssertTrue(app.buttons["Song Library"].exists, "Playback updates dismissed the menu")
            XCTAssertTrue(app.buttons["Done"].firstMatch.exists, "Playback replaced navigation sheet")
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        capture("real-menu-stays-open-during-playback", app)
        app.buttons["Song Library"].tap()
        XCTAssertTrue(app.buttons["Done"].firstMatch.waitForExistence(timeout: 5))
        app.buttons["Done"].firstMatch.tap()
        // LibraryView sets its navigation title to "Library", not "Song Library".
        // Wait for the actual presented navigation host to leave the hierarchy.
        let libraryNavigation = app.navigationBars["Library"]
        let dismissed = NSPredicate(format: "exists == false")
        expectation(for: dismissed, evaluatedWith: libraryNavigation)
        waitForExpectations(timeout: 10)
        XCTAssertFalse(app.sliders["Song position"].exists)
        XCTAssertFalse(app.buttons["Previous song"].exists)
        XCTAssertFalse(app.navigationBars["Save to Favorites"].exists,
                       "A completed session must not replace Library dismissal with a save sheet")
        let search = app.buttons["stage.search"]
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        let keypadZero = app.buttons["stage.keypad.0"]
        if !search.isHittable {
            capture("search-blocked-after-library", app)
            let hierarchy = app.debugDescription
            let attachment = XCTAttachment(string: hierarchy)
            attachment.name = "search-blocked-accessibility-hierarchy"
            attachment.lifetime = .keepAlways
            add(attachment)
            XCTFail("Search is blocked after leaving the library; accessibility hierarchy attached")
            return
        }
        search.tap()
        XCTAssertTrue(keypadZero.waitForExistence(timeout: 15),
                      "Search did not present the keypad after library dismissal")
        let keypadZeroPredicate = NSPredicate(format: "hittable == true")
        expectation(for: keypadZeroPredicate, evaluatedWith: keypadZero)
        waitForExpectations(timeout: 10)
        capture("06-real-search-overlay", app)
        app.buttons["Cancel"].tap()
        let saveSession = app.buttons["stage.saveSession"]
        if saveSession.exists {
            saveSession.tap()
            XCTAssertTrue(app.navigationBars["Save to Favorites"].waitForExistence(timeout: 5),
                          "The deferred session must remain available for explicit saving")
            app.buttons["Don't Save"].tap()
            let saveSheetGone = NSPredicate(format: "exists == false")
            expectation(for: saveSheetGone, evaluatedWith: app.navigationBars["Save to Favorites"])
            waitForExpectations(timeout: 10)
        }
        app.buttons["stage.controls"].tap()
        capture("07-real-controls", app)
        for index in 0..<3 {
            app.swipeUp()
            capture("08-real-controls-\(index)", app)
        }
        let openFullscreen = app.buttons["controls.openFullscreen"]
        XCTAssertTrue(
            openFullscreen.waitForExistence(timeout: waitSeconds),
            "The fullscreen action must remain available in the CONTROL bottom action area"
        )
        XCTAssertTrue(openFullscreen.isHittable)
        openFullscreen.tap()
        let fullscreenExit = app.buttons["fullscreen.exit"]
        XCTAssertTrue(fullscreenExit.waitForExistence(timeout: waitSeconds))
        // Fullscreen Search must expose a unique accessibility identity so the
        // presentation-contract guard can prove the fullscreen child presenter
        // is distinct from the embedded Stage panel.
        let fullscreenSearch = app.buttons["stage.search.fullscreen"]
        XCTAssertTrue(fullscreenSearch.waitForExistence(timeout: waitSeconds))
        fullscreenSearch.tap()
        XCTAssertTrue(keypadZero.waitForExistence(timeout: 10), "Search did not present inside fullscreen")
        app.buttons["Cancel"].tap()
        let fullscreenSearchDismissed = NSPredicate(format: "exists == false")
        expectation(for: fullscreenSearchDismissed, evaluatedWith: keypadZero)
        waitForExpectations(timeout: waitSeconds)

        XCTAssertTrue(fullscreenExit.isHittable)
        fullscreenExit.tap()
        let fullscreenPlayerDismissed = NSPredicate(format: "exists == false")
        expectation(for: fullscreenPlayerDismissed, evaluatedWith: fullscreenExit)
        waitForExpectations(timeout: waitSeconds)

        let stageSearchReady = NSPredicate(format: "exists == true AND hittable == true")
        expectation(for: stageSearchReady, evaluatedWith: search)
        waitForExpectations(timeout: waitSeconds)
        search.tap()
        XCTAssertTrue(keypadZero.waitForExistence(timeout: 10), "Stage Search must present after the fullscreen player has fully dismissed")
        app.buttons["Cancel"].tap()
    }

    @MainActor
    func testFocusedNavigationSmoke() throws {
        let app = XCUIApplication()
        app.launchEnvironment["KANTA_DEMO_MODE"] = "0"
        app.launch()
        let search = app.buttons["stage.search"]
        XCTAssertTrue(search.waitForExistence(timeout: waitSeconds))
        search.tap()
        let keypad = app.buttons["stage.keypad.0"]
        XCTAssertTrue(keypad.waitForExistence(timeout: 10), "Stage Search must open the keypad")
        app.buttons["Cancel"].tap()
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: keypad)
        waitForExpectations(timeout: 10)

        app.buttons["stage.controls"].tap()
        for _ in 0..<3 { app.swipeUp() }
        let fullscreen = app.buttons["controls.openFullscreen"]
        XCTAssertTrue(fullscreen.waitForExistence(timeout: waitSeconds))
        XCTAssertTrue(fullscreen.isHittable)
        fullscreen.tap()
        let exit = app.buttons["fullscreen.exit"]
        XCTAssertTrue(exit.waitForExistence(timeout: waitSeconds))
        exit.tap()
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: exit)
        waitForExpectations(timeout: waitSeconds)
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        search.tap()
        XCTAssertTrue(keypad.waitForExistence(timeout: 10),
                      "Stage Search must work after fullscreen dismissal")
        app.buttons["Cancel"].tap()
    }

    @MainActor
    func testOnlinePreviewEntryCloseReopenAndForegroundLifecycle() throws {
        let app = XCUIApplication()
        app.launchEnvironment["KANTA_DEMO_MODE"] = "0"
        app.launch()
        openOnlinePreview(in: app)
        let field = app.textFields["youtube.preview.link"]
        field.tap()
        field.typeText("invalid\n")
        app.buttons["youtube.preview.continue"].tap()
        let invalid = app.alerts["Invalid YouTube link"]
        XCTAssertTrue(invalid.waitForExistence(timeout: 5))
        XCTAssertFalse(app.webViews.firstMatch.exists, "Invalid input constructed a web player")
        invalid.buttons["OK"].tap()
        field.tap()
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: 7))
        field.typeText("https://youtu.be/abcdefghijk\n")
        requestOnlinePreview(in: app, evidence: "online-first-open")
        closeOnlineAttempt(in: app)
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        XCTAssertFalse(app.webViews.firstMatch.exists, "Stop left a native web view attached")
        requestOnlinePreview(in: app, evidence: "online-explicit-reopen")
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(field.waitForExistence(timeout: waitSeconds), "Foreground return restored an old player")
        XCTAssertFalse(app.buttons["youtube.preview.stop"].exists)
        XCTAssertFalse(app.webViews.firstMatch.exists)
        XCTAssertTrue(app.buttons["youtube.preview.continue"].isEnabled)
        capture("online-foreground-requires-explicit-reopen", app)
        app.buttons["youtube.preview.done"].tap()
        XCTAssertTrue(app.buttons["youtube.preview.open"].waitForExistence(timeout: 10))
        app.buttons["youtube.preview.open"].tap()
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["youtube.preview.continue"].isEnabled)
        XCTAssertFalse(app.webViews.firstMatch.exists)
        app.buttons["youtube.preview.done"].tap()
        app.buttons["controls.done"].tap()
        let search = app.buttons["stage.search"]
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        search.tap()
        XCTAssertTrue(app.buttons["stage.keypad.0"].waitForExistence(timeout: 10))
        app.buttons["Cancel"].tap()
    }

    @MainActor
    func testOnlinePreviewPreservesPausedStarterAndLocalResume() throws {
        let app = XCUIApplication()
        app.launchEnvironment["KANTA_DEMO_MODE"] = "0"
        app.launch()
        openMenu(in: app, expecting: "Song Library")
        app.buttons["Song Library"].tap()
        let play = app.buttons["starter.play.morning-light"]
        XCTAssertTrue(play.waitForExistence(timeout: waitSeconds))
        for _ in 0..<5 where !play.isHittable { app.swipeUp() }
        XCTAssertTrue(play.isHittable)
        play.tap()
        app.buttons["navigation.done"].tap()
        let transport = app.buttons["stage.playPause"]
        expectation(for: NSPredicate(format: "label == 'Pause' AND enabled == true"), evaluatedWith: transport)
        waitForExpectations(timeout: waitSeconds)
        transport.tap()
        expectation(for: NSPredicate(format: "label == 'Play'"), evaluatedWith: transport)
        waitForExpectations(timeout: 5)
        let song = app.staticTexts["stage.currentSong"]
        let pausedTitle = song.label
        XCTAssertTrue(pausedTitle.contains("Morning Light"))
        openOnlinePreview(in: app)
        let field = app.textFields["youtube.preview.link"]
        field.tap()
        field.typeText("https://youtu.be/abcdefghijk\n")
        requestOnlinePreview(in: app, evidence: "online-preserves-paused-starter")
        app.buttons["youtube.preview.done"].tap()
        XCTAssertTrue(app.buttons["controls.done"].waitForExistence(timeout: 10))
        app.buttons["controls.done"].tap()
        XCTAssertTrue(transport.waitForExistence(timeout: 10))
        XCTAssertEqual(transport.label, "Play", "Opening online playback changed local transport")
        XCTAssertEqual(song.label, pausedTitle, "Opening online playback replaced the local song")
        XCTAssertFalse(app.webViews.firstMatch.exists)
        transport.tap()
        expectation(for: NSPredicate(format: "label == 'Pause'"), evaluatedWith: transport)
        waitForExpectations(timeout: 5)
        capture("online-dismissed-local-starter-resumes", app)
        transport.tap()
    }

    @MainActor
    private func openOnlinePreview(in app: XCUIApplication) {
        let controls = app.buttons["stage.controls"]
        XCTAssertTrue(controls.waitForExistence(timeout: waitSeconds))
        controls.tap()
        let online = app.buttons["youtube.preview.open"]
        XCTAssertTrue(online.waitForExistence(timeout: 10))
        XCTAssertTrue(online.isHittable)
        online.tap()
        XCTAssertTrue(app.textFields["youtube.preview.link"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.webViews.firstMatch.exists, "Online content loaded before explicit consent")
    }

    @MainActor
    private func requestOnlinePreview(in app: XCUIApplication, evidence: String) {
        let proceed = app.buttons["youtube.preview.continue"]
        XCTAssertTrue(proceed.isEnabled)
        proceed.tap()
        let stop = app.buttons["youtube.preview.stop"]
        let error = app.staticTexts["youtube.preview.error"]
        // External availability is not a media fixture. Both paths must leave a usable screen.
        let deadline = Date().addingTimeInterval(waitSeconds)
        while Date() < deadline {
            if stop.exists && stop.isHittable && app.webViews.firstMatch.exists {
                capture(evidence + "-native-player", app)
                return
            }
            if error.exists && proceed.exists && proceed.isEnabled {
                XCTAssertTrue(error.label.contains("Online video could not load"), "Native claim failed instead of a recoverable external-load error")
                XCTAssertFalse(app.webViews.firstMatch.exists)
                capture(evidence + "-external-load-error", app)
                return
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        capture(evidence + "-missing-outcome", app)
        XCTFail("Online entry reached neither visible native player controls nor a recoverable external-load error")
    }

    @MainActor
    private func closeOnlineAttempt(in app: XCUIApplication) {
        let stop = app.buttons["youtube.preview.stop"]
        if stop.exists { stop.tap() }
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: app.webViews.firstMatch)
        waitForExpectations(timeout: 10)
    }

    @MainActor
    func testMenuSongLibrary() throws {
        assertMenuDestination("Song Library", expectedTitle: "Library")
    }

    @MainActor
    func testMenuSongbook() throws {
        assertMenuDestination("KANTA Songbook", expectedTitle: "KANTA Songbook")
    }

    @MainActor
    func testMenuSettings() throws {
        assertMenuDestination("Settings", expectedTitle: "Settings")
    }

    @MainActor
    func testMenuCompanionRemote() throws {
        assertMenuDestination("Companion Remote", expectedTitle: "CONTROL")
    }

    @MainActor
    func testMenuICloudSave() throws {
        assertMenuDestination("iCloud Save", expectedTitle: "iCloud Save")
    }

    @MainActor
    func testMenuLegalPrivacy() throws {
        assertMenuDestination("Legal & Privacy", expectedTitle: "Legal & Privacy")
    }

    @MainActor
    private func assertMenuDestination(_ destination: String, expectedTitle: String) {
        let app = XCUIApplication()
        app.launchEnvironment["KANTA_DEMO_MODE"] = "0"
        app.launch()
        XCTAssertTrue(app.buttons["stage.more"].waitForExistence(timeout: waitSeconds))

        openMenu(in: app, expecting: destination)
        let item = app.buttons[destination].firstMatch
        XCTAssertTrue(item.waitForExistence(timeout: 5))
        waitForRendered(item, in: app)
        item.tap()

        XCTAssertTrue(app.navigationBars[expectedTitle].waitForExistence(timeout: waitSeconds),
                      "Menu destination did not present its expected screen: " + destination)
        capture("menu-" + destination, app)

        let done = app.buttons["navigation.done"]
        XCTAssertTrue(done.waitForExistence(timeout: waitSeconds),
                      "The navigation host must provide Done for: " + destination)
        waitForRendered(done, in: app)
        done.tap()

        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: done)
        waitForExpectations(timeout: waitSeconds)
        let more = app.buttons["stage.more"].firstMatch
        XCTAssertTrue(more.waitForExistence(timeout: waitSeconds),
                      "The Stage must return after dismissing: " + destination)
        waitForRendered(more, in: app)
    }

    @MainActor
    func testBrowserCompanionRoleAndInputRejection() throws {
        let app = XCUIApplication()
        app.launchEnvironment["KANTA_DEMO_MODE"] = "0"
        app.launch()
        XCTAssertTrue(app.buttons["stage.more"].waitForExistence(timeout: waitSeconds))
        openMenu(in: app, expecting: "Companion Remote")
        app.buttons["Companion Remote"].tap()
        let browser = app.buttons["browser.companion.open"]
        waitForRendered(browser, in: app)
        browser.tap()
        let hostAddress = app.textFields["browser.host.address"]
        let host = app.buttons["browser.host.start"]
        let remote = app.buttons["browser.remote.open"]
        XCTAssertTrue(hostAddress.waitForExistence(timeout: waitSeconds))
        XCTAssertFalse(host.isEnabled, "Hosting must not start without explicit valid input")
        XCTAssertFalse(remote.isEnabled)
        XCTAssertTrue(app.staticTexts["Browser companion is off"].exists)
        hostAddress.tap()
        hostAddress.typeText("0.0.0.0")
        XCTAssertFalse(host.isEnabled, "Wildcard hosting must be rejected before networking")
        XCTAssertFalse(app.buttons["browser.host.stop"].exists)
        capture("browser-companion-input-rejection", app)
    }

    @MainActor
    func testEncryptedCompanionPairingScreenAndInputRejection() throws {
        let app = XCUIApplication()
        app.launchEnvironment["KANTA_DEMO_MODE"] = "0"
        app.launch()
        XCTAssertTrue(app.buttons["stage.more"].waitForExistence(timeout: waitSeconds))
        openMenu(in: app, expecting: "Companion Remote")
        let remote = app.buttons["Companion Remote"]
        waitForRendered(remote, in: app)
        remote.tap()
        let encrypted = app.buttons["companion.open"]
        waitForRendered(encrypted, in: app)
        encrypted.tap()
        let address = app.textFields["companion.address"]
        let code = app.secureTextFields["companion.code"]
        let connect = app.buttons["companion.connect"]
        XCTAssertTrue(address.waitForExistence(timeout: waitSeconds))
        XCTAssertTrue(code.exists)
        XCTAssertFalse(connect.isEnabled)
        capture("companion-encrypted-pairing", app)
        // The shared protocol validator is covered with public, loopback, malformed,
        // short and non-hex inputs in CompanionProtocolTests. Keep this UI check on
        // the empty form so simulator keyboard-idle stalls cannot mask other cases.
        XCTAssertFalse(connect.isEnabled,
                      "Connect must stay disabled until a private address and full pairing code are supplied")
        XCTAssertTrue(app.staticTexts["Enter the Android host's private IPv4 address and full 32-character hexadecimal pairing code."]
            .waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Disconnect"].exists)
        capture("companion-pairing-input-required", app)
    }

    @MainActor
    func testDemoKeypadCancelAndRepeatedIntentionalReservations() throws {
        let app = XCUIApplication()
        app.launchEnvironment["KANTA_DEMO_MODE"] = "1"
        app.launch()
        let queue = app.buttons["stage.queue"]
        XCTAssertTrue(queue.waitForExistence(timeout: waitSeconds))
        let queueValuePredicate = NSPredicate(format: "value == %@", "2 reservations")
        expectation(for: queueValuePredicate, evaluatedWith: queue)
        waitForExpectations(timeout: waitSeconds)
        app.buttons["stage.search"].tap()
        for digit in 0...9 {
            let key = app.buttons["stage.keypad.\(digit)"]
            XCTAssertTrue(key.waitForExistence(timeout: 10), "Keypad digit \(digit) did not appear")
        }
        app.buttons["Cancel"].tap()
        expectation(for: queueValuePredicate, evaluatedWith: queue)
        waitForExpectations(timeout: waitSeconds)
        // The same song may legitimately be reserved twice in two separate flows.
        for count in 3...4 {
            app.buttons["stage.search"].tap()
            let keypad0 = app.buttons["stage.keypad.0"]
            XCTAssertTrue(keypad0.waitForExistence(timeout: 10), "Keypad did not appear in time")
            XCTAssertTrue(keypad0.isHittable, "Keypad is not hittable")
            app.buttons["stage.keypad.Clear"].tap()
            for key in ["0", "0", "3"] { app.buttons["stage.keypad." + key].tap() }
            let result = app.buttons["Select Tadhana"]
            XCTAssertTrue(result.waitForExistence(timeout: 10))
            let enabled = NSPredicate(format: "enabled == true")
            expectation(for: enabled, evaluatedWith: result)
            waitForExpectations(timeout: 10)
            result.tap()
            capture("demo-selected-song-\(count)", app)
            app.buttons["stage.reserve"].doubleTap()
            let updated = NSPredicate(format: "value == %@", "\(count) reservations")
            expectation(for: updated, evaluatedWith: queue)
            waitForExpectations(timeout: 10)
            expectation(for: NSPredicate(format: "exists == false"),
                        evaluatedWith: app.buttons["stage.reserve"])
            waitForExpectations(timeout: 5)
        }
        XCUIDevice.shared.orientation = .landscapeLeft
        let window = app.windows.firstMatch
        let deadline = Date().addingTimeInterval(waitSeconds)
        while window.frame.width <= window.frame.height && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        XCTAssertGreaterThan(window.frame.width, window.frame.height)
        waitForRendered(app.buttons["stage.search"], in: app)
        app.buttons["stage.search"].tap()
        waitForRendered(app.buttons["stage.keypad.0"], in: app)
        capture("demo-landscape-keypad-before-hit-test", app)
        XCTAssertTrue(app.buttons["stage.keypad.0"].isHittable)
        XCTAssertTrue(app.buttons["stage.reserve"].isHittable)
        capture("demo-landscape-keypad", app)
        XCUIDevice.shared.orientation = .portrait
    }

    @MainActor
    private func openMenu(in app: XCUIApplication, expecting destination: String) {
        let more = app.buttons["stage.more"].firstMatch
        waitForRendered(more, in: app)
        let item = app.buttons[destination].firstMatch
        XCTAssertTrue(more.isHittable, "More is obstructed before opening: " + destination)
        more.tap()
        if !item.waitForExistence(timeout: 10) {
            capture("menu-did-not-present", app)
            XCTFail("More did not present the requested menu item: " + destination)
        }
    }

    @MainActor
    private func waitForRendered(_ element: XCUIElement, in app: XCUIApplication) {
        XCTAssertTrue(element.waitForExistence(timeout: waitSeconds))
        let deadline = Date().addingTimeInterval(waitSeconds)
        var stable = 0
        while Date() < deadline {
            let frame = element.frame
            let window = app.windows.firstMatch.frame
            let valid = frame.origin.x.isFinite && frame.origin.y.isFinite
                && frame.width.isFinite && frame.height.isFinite
                && frame.width > 0 && frame.height > 0 && frame.intersects(window)
            stable = valid ? stable + 1 : 0
            if stable >= 3 { return }
            RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        }
        capture("unrendered-control", app)
        XCTFail("Control has no stable visible frame: " + element.identifier)
    }

    @MainActor
    private func capture(_ name: String, _ app: XCUIApplication) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
