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

workspace = read(WORKSPACE)
activity = read(ACTIVITY)
runtime = read(RUNTIME_CHECKS)

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
check(
    "workspace.activateAccessory(accessoryId)" in activity,
    "Android MainActivity presents accessories through the workspace",
)

# --- Kanta-Connect pairing surface -------------------------------------------------
# The pairing sheet must offer both routes - a scannable QR and a typed address - and the
# single-session code must never be written into the shareable diagnostics report.

check(
    "QrCodeView" in activity and "QrEncoder.encode(info.link())" in activity,
    "Android pairing sheet renders a QR of the Kanta-Connect link",
)
check(
    "KantaConnectInfo.of(" in activity and "chooseHost(localPrivateAddresses())" in activity,
    "Android pairing sheet picks a reachable local address for the link",
)
check(
    "info.endpoint()" in activity and "info.instructions()" in activity,
    "Android pairing sheet shows the typed address and the connection steps",
)
check(
    "info.code" in activity and "Copy code" in activity and "Copy address" in activity,
    "Android pairing sheet copies the code and the address separately",
)
check(
    "KantaConnectInfo.chooseHost(hosts)" not in activity,
    "Android pairing sheet does not fabricate a host without enumerating interfaces",
)

diagnostics_body = activity[activity.find("private void diagnostics()") :]
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
    'b.setContentDescription("KANTA " + label)' in activity,
    "Android workspace toolbar buttons own their own identities",
)
check(
    'b.setTag("kanta.workspace." + accessoryId)' in activity,
    "Android workspace toolbar buttons carry stable test identities",
)

toolbar = activity[activity.find("private LinearLayout workspaceToolbar()") :]
toolbar = toolbar[: toolbar.find("private Button workspaceButton(")]
check(
    'workspaceButton("Controls"' in toolbar,
    'Android workspace toolbar control entry is labelled "Controls"',
)
check(
    'workspaceButton("Settings"' not in toolbar,
    'Android workspace toolbar does not label its control entry "Settings"',
)

# --- Regression guard: the redesign must not delete shipped functionality ----

for method in (
    "private void control()",
    "private void search()",
    "private void queue()",
    "private void mySet()",
    "private void scenic()",
    "private void loop()",
    "private void realm()",
    "private void openYouTubeVideo()",
    "private void voiceControls()",
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
    'for (String name : new String[] {"My Set", "Loop", "Scenic", "Realm", "Control"})' in activity,
    "Android retains the five existing navigation destinations",
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
    "Android instrumentation exercises dispatcher-based accessory dismissal",
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
