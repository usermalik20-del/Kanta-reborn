#!/usr/bin/env python3
"""Publish only verified exact-SHA build artifacts to the static download pages."""
from __future__ import annotations

import json
import os
from datetime import datetime, timezone
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
REPO = os.environ.get("GITHUB_REPOSITORY", "usermalik20-del/Kanta-reborn")


def required_int(name: str) -> int:
    value = os.environ.get(name, "")
    if not re.fullmatch(r"[1-9][0-9]*", value):
        raise ValueError(name + " must be a positive artifact/run ID")
    return int(value)


def required_sha256(name: str) -> str:
    value = os.environ.get(name, "")
    if value.startswith("sha256:"):
        value = value[7:]
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError(name + " must be a SHA-256 artifact digest")
    return value


def actual_retention_days(prefix: str) -> int:
    created = os.environ.get(prefix + "_CREATED_AT", "")
    expires = os.environ.get(prefix + "_EXPIRES_AT", "")
    try:
        created_at = datetime.fromisoformat(created.replace("Z", "+00:00"))
        expires_at = datetime.fromisoformat(expires.replace("Z", "+00:00"))
        if created_at.tzinfo is None or expires_at.tzinfo is None:
            raise ValueError("artifact timestamps must include timezone offsets")
    except (TypeError, ValueError) as exc:
        raise ValueError(prefix + " artifact creation/expiry timestamps are invalid") from exc
    days = round((expires_at - created_at).total_seconds() / 86400)
    if days < 1 or days > 30:
        raise ValueError(prefix + " artifact retention is outside the requested 1–30 day range")
    return days


def publish() -> dict:
    package = json.loads(Path("verified-downloads/downloads.json").read_text(encoding="utf-8"))
    source = package.get("source_sha", "")
    if not re.fullmatch(r"[0-9a-f]{40}", source):
        raise ValueError("Package manifest has no full source SHA")
    builds = package.get("builds", {})
    android = builds.get("android", {})
    ios = builds.get("ios", {})
    if android.get("source") != source or ios.get("source") != source:
        raise ValueError("Android and iOS artifacts must share one exact source SHA")
    if android.get("signed") != "debug" or ios.get("signed") is not False:
        raise ValueError("Unexpected signing state; iOS must remain unsigned")
    if not android.get("run") or not android.get("artifact") or not android.get("sha256"):
        raise ValueError("Android artifact provenance is incomplete")
    if not ios.get("run") or not ios.get("artifact") or not ios.get("sha256"):
        raise ValueError("iOS artifact provenance is incomplete")
    walkthrough_run = package.get("walkthrough_run")
    if not walkthrough_run:
        raise ValueError("A successful same-SHA iPhone/iPad walkthrough is required")

    run_id = required_int("GITHUB_RUN_ID")
    android_package_artifact = required_int("ANDROID_PACKAGE_ARTIFACT_ID")
    ios_package_artifact = required_int("IOS_PACKAGE_ARTIFACT_ID")
    android_package_digest = required_sha256("ANDROID_PACKAGE_ARTIFACT_SHA256")
    ios_package_digest = required_sha256("IOS_PACKAGE_ARTIFACT_SHA256")
    retention_days = min(
        actual_retention_days("ANDROID_PACKAGE"),
        actual_retention_days("IOS_PACKAGE"),
    )
    run_url = "https://github.com/" + REPO + "/actions/runs/" + str(run_id)
    android_url = run_url + "/artifacts/" + str(android_package_artifact)
    ios_url = run_url + "/artifacts/" + str(ios_package_artifact)

    manifest = {
        "repository": REPO,
        "verifiedAt": os.environ.get("VERIFIED_AT") or datetime.now(timezone.utc).date().isoformat(),
        "retentionDays": retention_days,
        "signInRequired": True,
        "source": source,
        "packagingRun": run_id,
        "packagingRunUrl": run_url,
        "walkthroughRun": int(walkthrough_run),
        "android": {
            "source": source,
            "run": int(android["run"]),
            "artifact": int(android["artifact"]),
            "packagingArtifact": android_package_artifact,
            "artifactName": "KANTA-Android-preview-download",
            "downloadUrl": android_url,
            "signed": "debug",
            "sha256": android_package_digest,
            "apkSha256": android["sha256"],
            "bytes": int(android["bytes"]),
        },
        "ios": {
            "source": source,
            "run": int(ios["run"]),
            "artifact": int(ios["artifact"]),
            "packagingArtifact": ios_package_artifact,
            "artifactName": "KANTA-iOS-personal-unsigned-download",
            "downloadUrl": ios_url,
            "signed": False,
            "personalSigningRequired": True,
            "icloudAvailable": False,
            "sha256": ios_package_digest,
            "ipaSha256": ios["sha256"],
            "walkthroughRun": int(walkthrough_run),
            "bytes": int(ios["bytes"]),
        },
    }

    android_page = ROOT / "apps/web/android.html"
    ios_page = ROOT / "apps/web/ios-download.html"
    android_text = android_page.read_text(encoding="utf-8")
    ios_text = ios_page.read_text(encoding="utf-8")

    android_source_url = "https://github.com/" + REPO + "/actions/runs/" + str(int(android["run"]))
    ios_source_url = "https://github.com/" + REPO + "/actions/runs/" + str(int(ios["run"]))
    android_section = f'''<section class="section" id="download"><h2>Android preview download</h2>
<p><a class="button primary" href="{android_url}">Download verified Android bundle ↗</a></p>
<p class="release-note">Verified CI artifact · GitHub sign-in required · {retention_days}-day retention. The bundle contains the debug-signed APK, SHA256.txt checksum and build provenance. The APK is a testing preview, not a Google Play release.</p>
<p>Source <code>{source[:8]}</code> · Debug-signed APK. Validated Android run <a href="https://github.com/{REPO}/actions/runs/{int(android["run"])}">#{int(android["run"])}</a> · <a href="{android_source_url}">Original Android validation run</a>.</p>
<p>Artifact ZIP SHA-256: <code>{android_package_digest}</code>. APK SHA-256: <code>{android["sha256"]}</code>. SHA256.txt is included.</p>
<p>iPhone/iPad simulator acceptance also passed on this exact source in <a href="https://github.com/{REPO}/actions/runs/{int(walkthrough_run)}">walkthrough run #{int(walkthrough_run)}</a>.</p></section>'''
    ios_section = f'''<section class="section" id="download"><h2>Unsigned iPhone / iPad package</h2>
<p><a class="button primary" href="{ios_url}">Download unsigned iOS bundle ↗</a></p>
<p class="release-note">Verified CI artifact · GitHub sign-in required · {retention_days}-day retention. This bundle contains an unsigned iPhoneOS IPA, SHA256.txt checksum and build provenance.</p>
<p>Source <code>{source[:8]}</code> · iOS/iPadOS 17 or newer. <strong>Separate trusted personal signing is required before installation.</strong> This is not a Safari installer, App Store app or TestFlight invitation. iCloud Save is unavailable in this build. <a href="{ios_source_url}">Original unsigned archive run</a>.</p>
<p>Artifact ZIP SHA-256: <code>{ios_package_digest}</code>. IPA SHA-256: <code>{ios["sha256"]}</code>. SHA256.txt is included.</p>
<p>Both iPhone and iPad simulator walkthroughs passed on this exact source in <a href="https://github.com/{REPO}/actions/runs/{int(walkthrough_run)}">walkthrough run #{int(walkthrough_run)}</a>. Simulator results do not establish physical-device acceptance.</p></section>'''

    def replace_section(document: str, replacement: str) -> str:
        pattern = re.compile(r'<section class="section" id="download">.*?</section>', re.DOTALL)
        updated, count = pattern.subn(lambda _: replacement, document, count=1)
        if count != 1:
            raise ValueError("Expected exactly one download section in platform page")
        return updated

    android_page.write_text(replace_section(android_text, android_section), encoding="utf-8")
    ios_page.write_text(replace_section(ios_text, ios_section), encoding="utf-8")
    (ROOT / "apps/web/downloads.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    return manifest


if __name__ == "__main__":
    try:
        result = publish()
        print("Published exact-SHA download manifest for " + result["source"])
    except (ValueError, KeyError, OSError, json.JSONDecodeError) as exc:
        print("download publication refused: " + str(exc), file=sys.stderr)
        raise SystemExit(1)
