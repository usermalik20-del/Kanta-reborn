#!/usr/bin/env python3
"""Static source guard for the Android Compact Workspace.

The Android compact workspace must satisfy the same architecture and
accessibility invariants that `tools/check-ios-presentation-contract.py`
enforces on iOS:

* The stage is the persistent base layer and is never detached or rebuilt
  while an accessory is presented.
* Accessories are transient, and only one may be active at a time.
* A container never carries the identity of its actionable children.
* The control-opening toolbar entry is labelled "Controls", never "Settings",
  because Settings is a separate navigation destination.
* The compact workspace does not remove existing product functionality.

Run from the repository root:

    python3 tools/check-android-workspace-contract.py
"""

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ANDROID = os.path.join(ROOT, "apps", "android", "app", "src", "main", "java", "ph", "kanta", "reborn")
WORKSPACE = os.path.join(ANDROID, "KantaWorkspace.java")
ACTIVITY = os.path.join(ANDROID, "MainActivity.java")
RUNTIME_CHECKS = os.path.join(
    ROOT, "apps", "android", "app", "src", "androidTest", "java", "ph", "kanta", "reborn", "RuntimeChecks.java"
)

failures = []
passes = []


def check(condition, message):
    if condition:
        passes.append(message)
    else:
        failures.append(message)


def read(path):
    with io.open(path, encoding="utf-8") as handle:
        return handle.read()


if not os.path.exists(WORKSPACE):
    print("FAIL: Android compact workspace container is missing")
    print("  expected: %s" % WORKSPACE)
    sys.exit(1)

STAGE_PRESENTER = os.path.join(ANDROID, "stage", "StagePresenterImpl.java")

workspace = read(WORKSPACE)
activity = read(ACTIVITY)
runtime = read(RUNTIME_CHECKS)
stage_presenter = read(STAGE_PRESENTER)

# --- Architecture: the persistent stage and transient accessories ------------

check(
    "public final class KantaWorkspace extends FrameLayout" in workspace,
    "Android compact workspace container exists",
)
check(
    "addPersistentLayer" in workspace and "activateAccessory" in workspace,
    "Android workspace exposes a persistent layer and transient accessories",
)
check(
    "accessoryContent()" in workspace,
    "Android workspace exposes an accessory content host",
)
check(
    "deactivateAccessory()" in workspace and "activeAccessory == null" in workspace,
    "Android workspace can dismiss the active accessory",
)
check(
    "if (accessoryId.equals(activeAccessory))" in workspace
    and "deactivateAccessory();\n      return;" in workspace,
    "Android workspace enforces a single active accessory",
)
check(
    "accessorySheet.setVisibility(GONE)" in workspace,
    "Android workspace hides the accessory sheet when inactive",
)
check(
    "Gravity.BOTTOM" in workspace and "Gravity.END" in workspace,
    "Android workspace adapts the accessory to handset and large-screen layouts",
)
check(
    "new KantaWorkspace(this, null)" in activity
    and "workspace.addPersistentLayer(stagePresenter)" in activity,
    "Android MainActivity mounts the workspace and installs the stage as the persistent layer",
)
routing = activity.split("private void presentAccessory(String accessoryId)", 1)[1]
routing = routing.split("\n  }", 1)[0]
check(
    "workspace.activateAccessory(" not in routing
    and "lowerUtilityAccessory = accessoryId;" in routing and "render();" in routing,
    "Android workspace actions open lower utility destinations without covering the stage",
)
check(
    "stagePane.setVisibility(visible ? View.VISIBLE : View.GONE)" in activity
    and "main.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL)" in activity
    and "hideStage" not in activity,
    "Android stage visibility follows the active destination",
)
check(
    "nowPlayingStage" not in activity and 'new StageSurface(this, session)' not in
    activity[activity.find("private void nowPlayingScreen()"):activity.find("private void refreshNowPlayingScreen()")],
    "Android Now Playing does not create a competing primary stage",
)
check(
    "assertPersistentStage(" in runtime and "persistentStagePlaybackChecks();" in runtime
    and '"Menu navigation changed authoritative reservations"' in runtime,
    "Android runtime checks stage visibility and uninterrupted playback while navigating",
)

# --- Local remote surfaces ---------------------------------------------------------

native_companion = read(os.path.join(ANDROID, "CompanionDialog.java"))
browser_remote = read(os.path.join(ANDROID, "BrowserCompanionDialog.java"))
check(
    "BrowserCompanionDialog" not in native_companion and "QrEncoder" not in native_companion
    and '"kanta.companion.host"' in native_companion,
    "Android Companion retains typed native pairing and its host automation tag",
)
check(
    re.findall(r"QrEncoder\.encode\(([^)]+)\)", browser_remote) == ["payload"]
    and "BrowserPairingQr.encode(hostAddress,c.fingerprint,c.code)" in browser_remote
    and "boolean hasAddress=c.pairingOpen()&&!c.address.isEmpty();" in browser_remote
    and "hostDetails.setVisibility(hasAddress ? View.VISIBLE : View.GONE)" in browser_remote
    and "addressQr.setSymbol(null);" in browser_remote,
    "Browser Remote shows its private pairing QR only while pairing is open",
)
check(
    '"kanta.browser.host"' in browser_remote
    and "FLAG_SECURE" in native_companion and browser_remote.count("FLAG_SECURE") == 2,
    "Native and browser dialogs retain secure windows and the browser host automation tag",
)
check(
    all(marker not in activity for marker in ("startKantaConnectHost(", "showKantaConnectQR(", "kantaConnect()")),
    "Android does not launch the separate Kanta-Connect smart link from its controls",
)

diagnostics_body = activity[activity.find("private void diagnostics(boolean inMenu)") :]
diagnostics_body = diagnostics_body[: diagnostics_body.find("\n  }")]
check(
    "pairingCode(" not in diagnostics_body,
    "Diagnostics never prints the Kanta-Connect pairing code",
)
check(
    "code withheld" in diagnostics_body,
    "Diagnostics states that the Kanta-Connect code is withheld",
)
check(
    "workspace.deactivateAccessory()" in activity,
    "Android MainActivity dismisses accessories through the workspace",
)

# --- Accessibility identity invariants (RC1 / RC2) ---------------------------

check(
    "setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO)" in workspace,
    "Android workspace container does not override its children's identities",
)
check(
    'label.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO)' in workspace,
    "Android accessory header label does not override the actionable child",
)
check(
    'close.setContentDescription("Close " + title)' in workspace,
    "Android accessory dismiss action owns its own identity",
)
check(
    'String[] labels = {"Search", "Queue", "Stage Visuals", "Controls", "Pitch", "Coach", "Menu"}' in activity,
    'Android More menu integrates the six workspace actions',
)
check(
    all(route in activity for route in (
        '"Search".equals(label)) openStageSearch();',
    ))
    and 'openPage("Search")' not in activity
    and 'presentAccessory(KantaWorkspace.ACCESSORY_SEARCH' not in activity
    and 'openDeckAccessory(KantaWorkspace.ACCESSORY_SEARCH' not in activity
    and 'kanta.utility.search-input' not in activity
    and 'kanta.screen.search' not in activity,
    'Android song-search shortcuts focus Stage instead of opening another page or accessory',
)
menu_group = activity.split('private void moreGroup()', 1)[1].split('\n  }', 1)[0]
check(
    re.findall(r'addMenuRow\(\w+, "([^"]+)"', menu_group) == [
        'my-songs', 'import-pdf', 'stage-visual', 'intermission', 'party-pads', 'voice-scoring', 'settings',
        'browser-remote', 'backup', 'legal', 'help',
    ] and 'MenuSurface.header(' in menu_group and menu_group.count('MenuSurface.section(') == 3,
    'Android Menu has single Stage & Visual and Voice & Scoring destinations plus Intermission and Party Pads',
)
stage_controls = activity.split('private StageControlsPanel makeStageControls(', 1)[1].split('private Button addStageControl(', 1)[0]
check(
    all(f'case "{entry}":\n        {launcher}();' in activity and f'{launcher}();' in stage_controls
        for entry, launcher in (('browser-remote', 'openBrowserRemote'),))
    and 'browserRemoteDialog = BrowserCompanionDialog.show(this, session)' in activity,
    'Menu and Stage Controls launch the browser remote dialog directly',
)
settings = activity.split('private void control()', 1)[1].split('\n  }\n', 1)[0]
mic_voice = activity.split('private void addMicVoiceOptions(', 1)[1].split('\n  }', 1)[0]
check(
    '"kanta.settings.intermission"' not in settings and 'menu_visuals_summary' not in settings
    and 'case "stage-visual":' in activity and 'openPage(MENU_STAGE_VISUAL);' in activity
    and 'case "intermission":' in activity and 'openPage(MENU_INTERMISSION);' in activity
    and 'partyPadTrigger(slot, result.tagPrefix + ".pad.")' in stage_controls
    and 'case "party-pads":\n        openPage(MENU_PARTY_PADS);' in activity
    and 'MenuSurface.actionStyle(legacyFolder, true)' in activity
    and 'Intent.ACTION_OPEN_DOCUMENT_TREE), LEGACY_FOLDER' in activity,
    'Android keeps Stage & Visual and Intermission in Menu, Party Pads in Stage Controls, and the folder importer in Presets',
)
check(
    '"kanta.companion.settings"' not in settings and 'this::openAndroidCompanion' not in settings
    and 'this::openBrowserRemote' not in settings
    and '() -> diagnostics(true)' in settings,
    'Android Settings avoids duplicate remote links and retains diagnostics',
)
check(
    'menu_voice_scoring' not in settings and 'case "voice-scoring":\n        openPage(MENU_VOICE_SCORING);' in activity
    and 'case MENU_VOICE_SCORING:\n        micVoiceScreen();' in activity
    and 'new ph.kanta.reborn.ui.KantaMicVoiceScreen(' in activity
    and 'voiceControls()' not in activity and 'voiceExpanded' not in activity,
    'Android Menu opens the shared Voice & Scoring destination without a copy in Settings',
)
check(
    'addMicVoiceOptions();' in activity
    and 'session.monitor.correction(!session.monitor.correctionEnabled())' in activity
    and 'micCorrection.setEnabled(session.monitor.correctionAvailable())' in activity
    and 'session.scoreLatency(bar.getProgress() * 10 - 500)' in activity
    and 'SessionController.CompletedScore completed = session.lastScore;' in activity
    and 'completed == null || !session.scoringEnabled' in activity
    and all(tag not in settings for tag in ('kanta.pitchcorrection.toggle', 'kanta.score.timing', 'kanta.score.last')),
    'Android shared Mic & Voice retains verified pitch correction, score timing and completed scores',
)
check(
    'volume.setContentDescription("Music volume");' in mic_voice and 'session.setVolume(value / 100f)' in mic_voice
    and 'volume.setContentDescription("Music volume");' not in settings
    and not any(token in settings for token in ('session.controls(', 'session.pitch', 'session.tempo'))
    and 'pitchTempoScreen' not in activity
    and 'onTempoChanged(float value)' not in activity,
    'Android Voice & Scoring keeps music volume; key and tempo moved to Stage',
)
check(
    'body.addView(workspaceToolbar())' not in activity,
    'Android removes the redundant always-visible workspace toolbar',
)
check(
    'toggleControlDeck' not in activity and 'renderControlDeck' not in activity,
    'Android removes the Control Deck and leaves the KANTA logo passive',
)

check(
    all(re.search(r'youtube|youtu\.be', source, re.IGNORECASE) is None for source in (
        activity, read(os.path.join(ANDROID, 'ui', 'HomeScreen.java')),
        read(os.path.join(ROOT, 'apps', 'android', 'app', 'src', 'main',
                          'assets', 'companion', 'index.html')),
    )),
    'Android Stage, menus, and bundled companion offer no YouTube entry point',
)

# --- Regression guard: the redesign must not delete shipped functionality ----

visuals = read(os.path.join(ANDROID, "ui", "KantaVisualsScreen.java"))
bundled_bgv = read(os.path.join(ANDROID, "BundledBgvCatalog.java"))
main = os.path.join(ROOT, "apps", "android", "app", "src", "main")
aliases = activity.split('private String resolvePageRoute(', 1)[1].split('private void showPage(', 1)[0]
check(
    all(token in aliases for token in ('"Scenic".equals(destination)', '"Realm".equals(destination)',
                                      'KantaVisualsScreen.VIDEOS', 'return MENU_STAGE_VISUAL;'))
    and 'tab = resolvePageRoute(tab);' in activity
    and 'destination = resolvePageRoute(destination);' in activity
    and 'private void navigateBack()' in activity,
    'Android old Background Video and Realm routes resolve to Stage & Visual’s Background videos tab',
)
posters = re.findall(r'"(bgv_\w+)",\s*"[^"]+",\s*"(video_\w+\.mp4)",\s*\d+L,\s*R\.drawable\.(kanta_bgv_\w+)', bundled_bgv)
check(
    len(posters) == bundled_bgv.count('new Clip(') > 0
    and len({poster for _, _, poster in posters}) == len(posters)
    and all(os.path.isfile(os.path.join(main, 'assets', 'bgv', video))
            and os.path.isfile(os.path.join(main, 'res', 'drawable-nodpi', poster + '.webp'))
            for _, video, poster in posters)
    and 'thumbnail(clip.title, clip.thumbnail, "kanta.realm.video." + clip.id' in visuals
    and 'image.setImageResource(artwork);' in visuals
    and 'session.selectScenicClip(id);' in activity,
    'Android Realm tiles render distinct bundled clip thumbnails and select the same clip IDs',
)
check(
    all(token in activity for token in ('this::showScenicVideoLibrary', 'chooseStageMedia(video)',
                                       'session.chooseStageImport(entry.file, video)',
                                       'startActivityForResult(picker, video ? VIDEO : PHOTO);'))
    and '.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);' in activity.split(
        'private void chooseStageMedia(boolean video)', 1)[1].split('private void ', 1)[0]
    and 'Use still background' not in activity
    and visuals.count('new Still("bayanDawn"') == 1
    and 'addStillGrid(content, SCENIC_BACKGROUNDS, true);' in visuals
    and 'if (drawableResource == 0) session.scene(id);' in activity,
    'Android Stage Visuals retains video management and one canonical Bayan Dawn still',
)

for method in (
    "private void control()",
    "private void openStageSearch()",
    "private void queue()",
    "private void mySet()",
    "private void visualsScreen()",
    "private void loop()",
    "private void showScenicVideoLibrary()",
    "private void micVoiceScreen()",
    "private void voiceSearch()",
    "private void export(boolean encrypted)",
    "void toggleCamera()",
    "void toggleMonitoring()",
    "void toggleMicrophone()",
    "void fullscreen()",
    "void closeFullscreen()",
    "private void refreshDisplay()",
):
    check(method in activity, "Android retains %s" % method)

check(
    'for (String name : new String[] {"Home", "More"})' in activity,
    "Android exposes Stage and Menu as the two top-level tabs",
)
check(
    "new OnBackPressedCallback(false)" in activity
    and "getOnBackPressedDispatcher().addCallback(this, workspaceBackCallback)" in activity,
    "Android workspace registers a predictive-back-compatible dispatcher callback",
)
check(
    "workspaceBackCallback.setEnabled(true)" in activity
    and "workspaceBackCallback.setEnabled(false)" in activity,
    "Android workspace back callback follows accessory activation and dismissal",
)
check(
    "public void onBackPressed()" not in activity and "super.onBackPressed()" not in activity,
    "Android workspace does not override the deprecated Activity back handler",
)
check(
    "workspaceBackNavigationChecks();" in runtime
    and "getOnBackPressedDispatcher().onBackPressed()" in runtime,
    "Android instrumentation exercises dispatcher-based lower-panel dismissal",
)

# --- The stage subtree must stay attached across session updates -------------
# The Android instrumentation suite asserts that the view tagged
# "kanta.stage.primary" is the same instance before and after a publish.

check(
    'stagePresenter.setTag("kanta.stage.primary")' in activity,
    "Android primary stage keeps its stable identity tag",
)
check(
    "new StagePresenterImpl(this, session" in activity,
    "Android stage persistent layer is owned by StagePresenterImpl",
)
check(
    "StagePresenceOverlay" in stage_presenter
    and "kanta.stage.presence" in stage_presenter
    and "presenceOverlay.bind(" in stage_presenter,
    "Android standby stage shows a karaoke presence overlay bound to the empty playback state",
)
check(
    'CODE_PROMPT = "00000"' in read(os.path.join(ANDROID, "stage", "StagePresenceOverlay.java")),
    "Android standby stage shows the 00000 karaoke song-code prompt",
)
check(
    "stageStandbyChecks();" in runtime
    and "liveFrame()" in runtime
    and "frozen background instead of live BGV video" in runtime,
    "Android runtime checks prove the standby stage renders live BGV video, not wallpaper",
)
check(
    "setStandby(" in read(os.path.join(ANDROID, "stage", "LyricStage.java")),
    "Android lyric stage defers to the standby presence instead of painting over it",
)
check(
    "findViewWithTag(\"kanta.stage.primary\")" in runtime,
    "Android instrumentation still guards the persistent stage subtree",
)
check(
    "body.removeAllViews();" in activity and "renderPanel();" in activity,
    "Android panel refresh does not rebuild the stage subtree",
)

for message in passes:
    print("PASS: %s" % message)

if failures:
    for message in failures:
        print("FAIL: %s" % message)
    print("\n%d passed, %d failed" % (len(passes), len(failures)))
    sys.exit(1)

print("\n%d checks passed, 0 failed" % len(passes))
