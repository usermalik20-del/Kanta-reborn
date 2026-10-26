#!/usr/bin/env python3
"""Check iOS presentation ownership and regression coverage without a simulator."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / 'apps/ios/Sources'
TEST = ROOT / 'apps/ios/Tests/KantaUITests/KantaVisualWalkthroughUITests.swift'


def verify():
    root = (APP / 'App/RootView.swift').read_text()
    host = (APP / 'Features/Host/HostView.swift').read_text()
    stage = (APP / 'Features/Host/KantaMainPlayerView.swift').read_text()
    stage_presentation = (APP / 'Features/Host/KantaStageSheetPresentation.swift').read_text()
    stage_background = stage.split('@ViewBuilder private var stageBackground: some View {', 1)[1].split('\n    private var header:', 1)[0]
    embedded_player = stage.split('private var embeddedPlayer: some View {', 1)[1].split('private var nowSinging: some View {', 1)[0]
    controls_more = stage.split('private var moreButton: some View {', 1)[1].split('private var playButton: some View {', 1)[0]
    fullscreen = (APP / 'Features/Host/FullscreenKaraokePlayerView.swift').read_text()
    fullscreen_presenter = (APP / 'Features/Host/SingingFullscreenPresentation.swift').read_text()
    remote = (APP / 'Features/Remote/EncryptedCompanionView.swift').read_text()
    companion_protocol = (ROOT / 'apps/ios/Sources/Infrastructure/LocalNetworkControl/CompanionProtocol.swift').read_text()
    companion_tests = (ROOT / 'apps/ios/Tests/LocalNetworkControlTests/CompanionProtocolTests.swift').read_text()
    test = TEST.read_text()
    runner = (ROOT / 'tools/capture-ios-runtime.py').read_text()
    workflow = (ROOT / '.github/workflows/ios-visual-walkthrough.yml').read_text()
    checks = {
        'root owns navigation sheet identity': '.sheet(item: $activeNavigation' in root and 'onDismiss:' in root,
        'root queues menu requests through the shared presentation gate': (
            'guard activeNavigation == nil else { return }' in root
            and 'presentationGate.request(.navigation)' in root
            and 'func request(_ requested: Owner' in root),
        'host receives navigation presentation state': 'navigationSheetPresented: activeNavigation != nil' in root and 'navigationSheetPresented' in host,
        'single authoritative navigation state': 'navigationPresentation' not in root and 'navigationTransition' not in root,
        'host uses one sheet coordinator': (
            '.sheet(item: $activeHostSheet' in host
            and '.sheet(item: $activeStageSheet' not in host
            and 'showingHostControls' not in host
        ),
        'stage uses one sheet coordinator': '.sheet(item: $activeStageSheet' in stage and 'showingSearch' not in stage and 'showingQueue' not in stage,
        'embedded Stage Search/Queue use a stable UIKit presenter': (
            '@State private var activeEmbeddedStageSheet: KantaStageSheet?' in host
            and 'KantaEmbeddedStageSheetPresentation(' in host
            and 'activeEmbeddedStageSheet = stageSheet' in host
            and 'case .stageSearch' not in host
            and 'case .stageQueue' not in host
            and 'struct KantaEmbeddedStageSheetPresentation: UIViewControllerRepresentable' in stage_presentation
            and 'UIAdaptivePresentationControllerDelegate' in stage_presentation
            and 'presentationControllerDidDismiss' in stage_presentation
            and 'completeDismissal(.stage)' in stage_presentation
            and 'setEmbeddedStageSheet' in stage
            and 'ZStack {\n            hostContent' in host
        ),
        'embedded presenter retries layout and reports blocked ownership': (
            '.frame(maxWidth: .infinity, maxHeight: .infinity)' in host
            and 'override func viewDidLayoutSubviews()' in stage_presentation
            and 'presenter-not-in-window' in stage_presentation
            and 'presenter-already-presenting' in stage_presentation
            and 'KANTA_STAGE_PRESENTATION' in stage_presentation
        ),
        'demo keypad assertions wait for asynchronous presentation': (
            'waitForExistence(timeout: 10), "Keypad digit' in test
        ),
        'embedded Stage presenter failures emit and retain diagnostics': (
            '[KANTA_STAGE_PRESENTATION]' in stage_presentation
            and 'request sheet=' in stage
            and 'activate sheet=' in stage
            and 'capture_presentation_diagnostics' in runner
            and 'eventMessage CONTAINS[c] "KANTA_STAGE_PRESENTATION"' in runner
            and 'ios-ipad-presentation-diagnostics' in workflow
        ),
        'fullscreen Search/Queue keep a separate child presenter': (
            'fullscreenPlayer' in stage
            and '.sheet(item: $activeStageSheet' in stage
            and 'KantaStageSheetContent' in stage
        ),
        'fullscreen Search has a unique accessibility identity': (
            'stage.search.fullscreen' in stage
            and 'let fullscreenSearch = app.buttons["stage.search.fullscreen"]' in test
            and 'let search = app.buttons["stage.search"]' in test
        ),
        'fullscreen reuses host presentation gate': (
            'presentationGate: presentationGate' in host
            and 'presentationGate: presentationGate' in fullscreen_presenter
            and '@ObservedObject var presentationGate: KantaPresentationGate' in fullscreen
        ),
        'fullscreen is gate-owned through UIKit dismissal callback': (
            'presentationGate.request(.fullscreen)' in fullscreen_presenter
            and 'presentationGate.beginDismissal(.fullscreen)' in fullscreen_presenter
            and 'presentationGate?.completeDismissal(.fullscreen)' in fullscreen_presenter
            and 'self.finishDismissal()' in fullscreen_presenter
        ),
        'fullscreen child sheets restore parent ownership': (
            'fullscreenChildActive' in root and 'owner = .fullscreen' in root and 'requested == .stage' in root
        ),
        'fullscreen nested presentation regression coverage': (
            'Search did not present inside fullscreen' in test
            and all(marker in test for marker in (
                'fullscreenSearchDismissed',
                'fullscreenPlayerDismissed',
                'stageSearchReady',
                'Stage Search must present after the fullscreen player has fully dismissed',
            ))
        ),
        'playback does not mutate root navigation': 'snapshot.transport' not in root,
        'main menu destinations have independent XCTest budgets': (
            all(f'func {name}() throws' in test for name in (
                'testMenuSongLibrary',
                'testMenuSongbook',
                'testMenuSettings',
                'testMenuCompanionRemote',
                'testMenuICloudSave',
                'testMenuLegalPrivacy',
            ))
            and 'func testMainMenuButtonsAndScreens()' not in test
            and all(f'"{name}"' in runner for name in (
                'testMenuSongLibrary',
                'testMenuSongbook',
                'testMenuSettings',
                'testMenuCompanionRemote',
                'testMenuICloudSave',
                'testMenuLegalPrivacy',
            ))
            and '"testMainMenuButtonsAndScreens"' not in runner
        ),
        'real playback walkthrough': 'func testStarterPlaybackAndControls()' in test,
        'library dismissal then search coverage': 'Search did not present the keypad after library dismissal' in test,
        'stage search accessibility identifier': 'stage.search' in stage,
        'compact workspace keeps stable More identity and existing Host ownership': (
            'stageButton("More", icon: "ellipsis") { camera.stop(); onMore() }' in embedded_player
            and '.accessibilityIdentifier("stage.more")' in embedded_player
            and '.accessibilityIdentifier("stage.controls.more")' in controls_more
            and 'app.buttons["stage.more"]' in test
        ),
        'compact workspace keeps Stage and player persistently visible': (
            '.accessibilityIdentifier("stage.persistentSurface")' in embedded_player
            and '.accessibilityIdentifier("stage.persistentPlayer")' in embedded_player
            and 'if compact {' in embedded_player
            and 'stageButton("Expand", icon: "arrow.up.left.and.arrow.down.right", action: onExpand)' in embedded_player
        ),
        'compact workspace retains the Host controls entry': (
            '.accessibilityIdentifier("stage.controls")' in embedded_player
            and 'onControls()' in embedded_player
        ),
        'KANTA Host branding and logo are visible': (
            '.navigationTitle("KANTA Karaoke")' in host
            and 'Text("KANTA KARAOKE")' in host
            and 'KantaBrandMark(size: 28)' in host
            and 'Text("KANTA").font(.title3.bold())' in stage
            and 'KantaBrandMark(size: 30)' in stage
        ),
        'embedded player is edge-to-edge and has no decorative border': (
            '.padding(.zero)' in embedded_player
            and '.overlay {' not in embedded_player
            and '.stroke(KantaColors.hairline' not in embedded_player
            and '.accessibilityIdentifier("stage.persistentSurface")' in embedded_player
            and '.accessibilityIdentifier("stage.persistentPlayer")' in embedded_player
        ),
        'host branding uses KANTA Karaoke for network and playback errors': (
            'displayName: String = "KANTA Karaoke"' in (APP / 'Infrastructure/LocalNetworkControl/BonjourHostAdvertiser.swift').read_text()
            and 'KANTA Karaoke could not create a temporary playback file.' in (APP / 'Infrastructure/LegacyCompatibility/ShubBlobPayloadResolver.swift').read_text()
            and 'Open KANTA Karaoke on the connected device.' in (APP / 'Features/Host/KantaExternalStageScene.swift').read_text()
        ),
        'compact workspace preserves approved transport controls': (
            'private var playButton: some View' in stage
            and 'private var nextButton: some View' in stage
            and 'Previous' not in embedded_player
            and 'ProgressView' not in embedded_player
        ),
        'library dismissal leaves session save opt-in': (
            'onSaveSession: {' in host
            and 'guard model.showingSessionSavePrompt else { return }' in host
            and 'guard !navigationSheetPresented && activeHostSheet == nil else { return }' in host
            and 'presentationGate.request(.host) { activeHostSheet = .sessionSave }' in host
            and 'A completed session must not replace Library dismissal with a save sheet' in test
            and 'The deferred session must remain available for explicit saving' in test
        ),
        'decorative stage visuals cannot intercept control taps': (
            stage_background.count('.allowsHitTesting(false)') == 2
            and 'KantaCameraPreview(capture: camera.capture)' in stage_background
            and 'KaraokeStageView(asset:' in stage_background
        ),
        'encrypted companion UI and handshake share bounded pairing validation': (
            'CompanionProtocol.isValidPairingInput(address: address, code: code)' in remote
            and 'public static func isValidPairingCode' in companion_protocol
            and 'public static func isValidPairingInput' in companion_protocol
            and 'guard isValidPairingCode(code)' in companion_protocol
            and 'companionPairingInputRequiresPrivateAddressAndFullHexSecret' in companion_tests
        ),
        # The tap/gate probes were diagnosis-only instrumentation for the iPad
        # dead-tap investigation. They must not return to shipping source: they
        # attached a window-wide gesture recogniser and logged on every touch.
        'no diagnostic tap or gate probes in shipping source': (
            'KANTA_PROBE' not in stage
            and 'KANTA_TAP' not in stage
            and 'KantaWindowTapProbe' not in stage
            and 'addGestureRecognizer' not in stage
            and 'KANTA_GATE' not in root
        ),
    }
    for name, passed in checks.items():
        print(('PASS' if passed else 'FAIL') + ': ' + name)
    if not all(checks.values()):
        raise SystemExit(1)


if __name__ == '__main__':
    verify()
