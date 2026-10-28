#!/usr/bin/env python3
"""Fail closed when Android CI could mask a failed Gradle build or miss branches."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
WORKFLOW = ROOT / ".github" / "workflows" / "android-validation.yml"
text = WORKFLOW.read_text(encoding="utf-8")
GRADLE = (ROOT / "apps" / "android" / "app" / "build.gradle").read_text(encoding="utf-8")
GATE_WORKFLOWS = [
    ROOT / ".github" / "workflows" / "ios-validation.yml",
    ROOT / ".github" / "workflows" / "ios-smoke.yml",
    ROOT / ".github" / "workflows" / "ios-runtime-artifacts.yml",
    ROOT / ".github" / "workflows" / "ios-visual-walkthrough.yml",
]
GATE_TEXTS = [path.read_text(encoding="utf-8") for path in GATE_WORKFLOWS]
def concurrency_group(source):
    return next((line.strip() for line in source.splitlines() if line.strip().startswith("group:")), "")

checks = {
    "slash-containing branch names are matched": "branches: ['**']" in text,
    "Gradle pipeline propagates the Gradle exit code": "set -o pipefail" in text,
    "Android workspace source contract runs": "python3 tools/check-android-workspace-contract.py" in text,
    "workflow changes trigger validation": "'.github/workflows/**'" in text,
    "iOS source changes share exact-SHA native validation": "'apps/ios/**'" in text,
    "build logs are uploaded on failure": "if: failure()" in text and "apps/android/build.log" in text,
    "host-JVM core regression excludes Android-backed SQLite catalog": "exclude 'ph/kanta/reborn/core/catalog/SqliteSongCatalog.java'" in GRADLE,
    "all native gate concurrency groups isolate rerun attempts": all(
        "github.run_attempt" in concurrency_group(source)
        for source in [text, *GATE_TEXTS]
    ),
}

for description, passed in checks.items():
    print(("PASS" if passed else "FAIL") + ": " + description)

failed = [description for description, passed in checks.items() if not passed]
if failed:
    print(f"\n{len(checks) - len(failed)} passed, {len(failed)} failed")
    sys.exit(1)
print(f"\n{len(checks)} checks passed, 0 failed")
