#!/usr/bin/env python3
"""Package one platform's accepted artifact for independent publication."""
from __future__ import annotations
import importlib.util
import json
import os
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("verified_prepare", ROOT / "prepare-verified-downloads.py")
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)

def main():
    platform = os.environ.get("PLATFORM", "")
    source_sha = (os.environ.get("SOURCE_SHA") or "").lower()
    if platform not in ("android", "ios"):
        raise ValueError("PLATFORM must be android or ios")
    if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
        raise ValueError("SOURCE_SHA must be a full commit SHA")
    if platform == "android":
        run_name = prepare.REQUIRED_RUNS["android_validation"]
        artifact_name = prepare.ARTIFACT_NAMES["android"]
        run = prepare.find_source_run(source_sha, run_name)
        artifact = prepare.find_artifact(run, artifact_name)
        expected = {"run": run["id"], "artifact": artifact["id"], "source": source_sha}
        prepare.accepted(run, artifact, expected)
        source = {"run": run, "artifact": artifact, "expected": expected}
        walkthrough = None
    else:
        run_name = prepare.REQUIRED_RUNS["ios_unsigned"]
        artifact_name = prepare.ARTIFACT_NAMES["ios"]
        run = prepare.find_source_run(source_sha, run_name)
        walkthrough = prepare.find_source_run(source_sha, prepare.REQUIRED_RUNS["walkthrough"])
        artifact = prepare.find_artifact(run, artifact_name)
        expected = {"run": run["id"], "artifact": artifact["id"], "source": source_sha}
        prepare.accepted(run, artifact, expected)
        source = {"run": run, "artifact": artifact, "expected": expected}
    application = prepare.application_bytes(platform, source)
    suffix = ".apk" if platform == "android" else ".ipa"
    name = "KANTA-" + ("Android-preview-" if platform == "android" else "iOS-personal-unsigned-") + source_sha[:8] + suffix
    output = Path("verified-downloads") / platform
    output.mkdir(parents=True, exist_ok=True)
    (output / name).write_bytes(application)
    checksum = __import__("hashlib").sha256(application).hexdigest()
    (output / "SHA256.txt").write_text(checksum + "  " + name + "\n")
    details = {"platform": platform, "source": source_sha, "run": int(run["id"]),
               "artifact": int(artifact["id"]), "file": name, "sha256": checksum,
               "bytes": len(application), "signed": "debug" if platform == "android" else False}
    if walkthrough:
        details["walkthroughRun"] = int(walkthrough["id"])
    (output / "build-info.json").write_text(json.dumps(details, indent=2) + "\n")
    print(json.dumps(details))
if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError) as exc:
        print("platform packaging refused: " + str(exc), file=sys.stderr)
        raise SystemExit(1)
