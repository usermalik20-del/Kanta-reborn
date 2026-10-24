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
    fullscreen = (APP / 'Features/Host/FullscreenKaraokePlayerView.swift').read_text()
    fullscreen_presenter = (APP / 'Features/Host/SingingFullscreenPresentation.swift').read_text()
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
        'real playback walkthrough': 'func testStarterPlaybackAndControls()' in test,
        'library dismissal then search coverage': 'Search did not present the keypad after library dismissal' in test,
        'stage search accessibility identifier': 'stage.search' in stage,
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
        'window tap probe uses supported, comparable geometry and removes its recognizer': (
            'NSCoder.string(for: point)' in stage
            and 'hitView.convert(hitView.bounds, to: view)' in stage
            and 'static func dismantleUIView' in stage
            and 'coordinator.detach()' in stage
            and 'NSStringFromCGPoint' not in stage
            and 'NSStringFromCGRect' not in stage
        ),
    }
    for name, passed in checks.items():
        print(('PASS' if passed else 'FAIL') + ': ' + name)
    if not all(checks.values()):
        raise SystemExit(1)


if __name__ == '__main__':
    verify()
