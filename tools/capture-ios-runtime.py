#!/usr/bin/env python3
"""Capture real iPhone/iPad interactions and preserve failed XCTest evidence."""
from __future__ import annotations
import json
import argparse
from collections import deque
import os
import re
from pathlib import Path
import signal
import subprocess

OUT = Path("runtime-captures")
# The full UI suite needs a bounded deadline separate from per-case limits.
SUITE_TIMEOUT_SECONDS = 1080
EXPECTED_TESTS = frozenset({
    "testFocusedNavigationSmoke",
    "testBrowserCompanionRoleAndInputRejection",
    "testOnlinePreviewEntryCloseReopenAndForegroundLifecycle",
    "testOnlinePreviewPreservesPausedStarterAndLocalResume",
    "testStarterPlaybackAndControls",
    "testMenuSongLibrary",
    "testMenuSongbook",
    "testMenuSettings",
    "testMenuCompanionRemote",
    "testMenuICloudSave",
    "testMenuLegalPrivacy",
    "testEncryptedCompanionPairingScreenAndInputRejection",
    "testDemoKeypadCancelAndRepeatedIntentionalReservations",
})
TEST_SUCCESS = re.compile(
    r"^Test Case '-\[KantaUITests\.KantaVisualWalkthroughUITests (test\w+)\]' passed "
)
TEST_FAILURE = re.compile(
    r"(?:^Test Case .* failed \(|:\d+: error:|^Testing failed:|"
    r"\*\* TEST FAILED \*\*|Restarting after unexpected exit)"
)

def inspect_test_log(path: Path) -> tuple[set[str], list[str]]:
    passed: set[str] = set()
    failures: list[str] = []
    if path.is_file():
        with path.open(errors="replace") as log:
            for line in log:
                match = TEST_SUCCESS.search(line)
                if match:
                    passed.add(match.group(1))
                if TEST_FAILURE.search(line):
                    failures.append(line.rstrip())
    return passed, failures[-25:]

def run(*args: str, **kwargs):
    print("+", " ".join(args), flush=True)
    return subprocess.run(args, check=True, **kwargs)


def capture_presentation_diagnostics(udid: str, label: str) -> None:
    """Retain filtered UIKit presentation logs without masking XCTest failures."""
    path = OUT / f"{label}-presentation-diagnostics.log"
    predicate = (
        'eventMessage CONTAINS[c] "KANTA_STAGE_PRESENTATION" OR '
        'eventMessage CONTAINS[c] "KANTA_GATE" OR '
        'eventMessage CONTAINS[c] "KANTA_PROBE" OR '
        'eventMessage CONTAINS[c] "KANTA_TAP" OR '
        'eventMessage CONTAINS[c] "Attempt to present" OR '
        'eventMessage CONTAINS[c] "not in the window hierarchy"'
    )
    command = [
        "xcrun", "simctl", "spawn", udid, "log", "show",
        "--last", "15m", "--style", "compact", "--info",
        "--predicate", predicate,
    ]
    try:
        with path.open("w") as log:
            result = subprocess.run(
                command, stdout=log, stderr=subprocess.STDOUT, text=True,
                timeout=60, check=False,
            )
            if result.returncode:
                log.write(
                    f"Simulator log capture exited with status {result.returncode}\\n"
                )
    except (OSError, subprocess.SubprocessError) as error:
        try:
            path.write_text(f"Simulator log capture unavailable: {error}\\n")
        except OSError:
            pass
    print(f"Presentation diagnostics: {path}", flush=True)

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", choices=("iphone", "ipad"))
    requested = parser.parse_args().device
    OUT.mkdir(exist_ok=True)
    devices = json.loads(subprocess.check_output(
        ["xcrun", "simctl", "list", "devices", "available", "--json"], text=True))
    available = [(d["name"], d["udid"]) for runtime, rows in devices["devices"].items()
                 if "iOS" in runtime for d in rows if d.get("isAvailable", True)]
    selected = []
    for label, prefix, preferred in (("iphone", "iPhone", "iPhone 16 Pro"), ("ipad", "iPad", "iPad (9th generation)")):
        if requested and requested != label: continue
        choices = [(name, udid) for name, udid in available if name.startswith(prefix)]
        if not choices: raise RuntimeError(f"No available {label} simulator")
        name, udid = next((item for item in choices if item[0] == preferred), choices[0])
        selected.append(dict(label=label, name=name, udid=udid))
    results = []
    for device in selected:
        label, udid = device["label"], device["udid"]
        recorder = None
        passed = False
        test_log = OUT / f"{label}-tests.log"
        try:
            subprocess.run(["xcrun", "simctl", "boot", udid], check=False)
            run("xcrun", "simctl", "bootstatus", udid, "-b", timeout=180)
            with (OUT / f"{label}-recording.log").open("w") as recording_log:
                recorder = subprocess.Popen(["xcrun", "simctl", "io", udid, "recordVideo", "--codec=h264",
                    str(OUT / f"{label}-walkthrough.mp4")], stdout=recording_log, stderr=subprocess.STDOUT)
                with test_log.open("w") as log:
                    run("xcodebuild", "test-without-building", "-project", "GeloKaraoke.xcodeproj",
                        "-scheme", "GeloKaraoke", "-configuration", "Release", "-destination",
                        f"platform=iOS Simulator,id={udid}", "-derivedDataPath", "build/DerivedData",
                        "-resultBundlePath", str(OUT / f"{label}.xcresult"), "-parallel-testing-enabled", "NO",
                        "-test-timeouts-enabled", "YES", "-default-test-execution-time-allowance", "120",
                        "-maximum-test-execution-time-allowance", "180",
                        "-only-testing:KantaUITests/KantaVisualWalkthroughUITests", "CODE_SIGNING_ALLOWED=NO",
                        stdout=log, stderr=subprocess.STDOUT, timeout=SUITE_TIMEOUT_SECONDS)
                passed = True
        except (OSError, subprocess.SubprocessError) as error:
            print(f"{label}: {error}", flush=True)
            if test_log.is_file():
                print(f"--- {label} XCTest log tail ---", flush=True)
                passed_cases, failures = inspect_test_log(test_log)
                print(f"--- {label} XCTest failures ---", flush=True)
                for line in failures:
                    print(line, flush=True)
                print(f"Passed cases: {sorted(passed_cases)}", flush=True)
                print(f"--- end {label} XCTest failures ---", flush=True)
                with test_log.open(errors="replace") as log:
                    for line in deque(log, maxlen=100):
                        print(line.rstrip(), flush=True)
                print(f"--- end {label} XCTest log tail ---", flush=True)
        finally:
            if recorder is not None:
                if recorder.poll() is None: recorder.send_signal(signal.SIGINT)
                try: recorder.wait(timeout=20)
                except subprocess.TimeoutExpired:
                    recorder.kill()
                    recorder.wait()
            capture_presentation_diagnostics(udid, label)
            result = OUT / f"{label}.xcresult"
            if (result / "Info.plist").is_file():
                subprocess.run(["xcrun", "xcresulttool", "export", "attachments", "--path", str(result),
                    "--output-path", str(OUT / f"{label}-screenshots")], check=False, timeout=90)
            subprocess.run(["xcrun", "simctl", "shutdown", udid], check=False, timeout=30)
        video = OUT / f"{label}-walkthrough.mp4"
        completed_cases, _ = inspect_test_log(test_log)
        missing = EXPECTED_TESTS - completed_cases
        if missing:
            print(f"{label}: missing passing walkthroughs: {sorted(missing)}", flush=True)
        passed = passed and not missing and video.exists() and video.stat().st_size > 0
        results.append(dict(**device, passed=passed, passed_tests=sorted(completed_cases),
            missing_tests=sorted(missing)))
        print(f"{label}: {'PASS' if passed else 'FAIL'}", flush=True)
    (OUT / (f"manifest-{requested}.json" if requested else "manifest.json")).write_text(json.dumps(dict(source_sha=os.environ.get("GITHUB_SHA"),
        results=results, boundary="Real simulator interactions; demo tests are named separately. Videos do not prove hardware audio."), indent=2) + "\n")
    return 0 if all(item["passed"] for item in results) else 1

if __name__ == "__main__": raise SystemExit(main())
