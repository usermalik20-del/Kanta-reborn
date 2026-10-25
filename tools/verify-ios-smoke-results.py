#!/usr/bin/env python3
"""Require explicit native and navigation XCTest passes; reject restarted-run false greens."""
import re
import sys
from pathlib import Path

EXPECTED = frozenset({
    "KantaUITests.KantaVisualWalkthroughUITests testBrowserCompanionRoleAndInputRejection",
    "KantaUITests.KantaVisualWalkthroughUITests testOnlinePreviewEntryCloseReopenAndForegroundLifecycle",
    "KantaUITests.KantaVisualWalkthroughUITests testOnlinePreviewPreservesPausedStarterAndLocalResume",
    "KantaNativeTests.YouTubeEmbeddedPlayerLifecycleTests testNativeAudioClaimRetiresWebViewBeforeSessionConfiguration",
    "KantaNativeTests.YouTubeEmbeddedPlayerLifecycleTests testIntermissionOnlineHandoffRequiresExplicitRestart",
    "KantaNativeTests.YouTubeEmbeddedPlayerLifecycleTests testReplacementRetiresOldNativeViewBeforeAttachingNext",
    "KantaNativeTests.YouTubeEmbeddedPlayerLifecycleTests testForegroundLossRetiresNativeViewAndDoesNotRestoreIt",
    "KantaNativeTests.YouTubeEmbeddedPlayerLifecycleTests testProcessFailureRetiresExactlyOnceAndOwnerCanReopen",
    "KantaUITests.KantaVisualWalkthroughUITests testFocusedNavigationSmoke",
})
PASSED = re.compile(r"^Test Case '-\[([^\]]+)\]' passed ")
FAILED = re.compile(
    r"^Test Case .* failed \(|:\d+: error:|^Testing failed:|"
    r"\*\* TEST (?:EXECUTE )?FAILED \*\*|Restarting after unexpected exit"
)


def verify(path: Path) -> set[str]:
    passed = set()
    failures = []
    for line in path.read_text(errors="replace").splitlines():
        match = PASSED.search(line)
        if match:
            passed.add(match.group(1))
        if FAILED.search(line):
            failures.append(line)
    missing = EXPECTED - passed
    if missing or failures:
        raise ValueError(f"Missing XCTest passes: {sorted(missing)}; failures: {failures[-10:]}")
    return passed & EXPECTED


if __name__ == "__main__":
    print("Verified XCTest passes:", sorted(verify(Path(sys.argv[1]))))
