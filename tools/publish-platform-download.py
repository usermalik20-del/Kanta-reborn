#!/usr/bin/env python3
"""Merge one verified platform release without changing the other platform."""
from __future__ import annotations
import json
import os
from datetime import datetime, timezone
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
REPO = os.environ.get("GITHUB_REPOSITORY", "usermalik20-del/Kanta-reborn")

def positive(name):
    value = os.environ.get(name, "")
    if not re.fullmatch(r"[1-9][0-9]*", value):
        raise ValueError(name + " must be a positive integer")
    return int(value)

def publish():
    platform = os.environ.get("PLATFORM", "")
    if platform not in ("android", "ios"):
        raise ValueError("PLATFORM must be android or ios")
    info = json.loads((ROOT / "verified-downloads" / platform / "build-info.json").read_text())
    source = info.get("source", "")
    if not re.fullmatch(r"[0-9a-f]{40}", source) or info.get("platform") != platform:
        raise ValueError("Invalid platform package provenance")
    if platform == "ios" and (info.get("signed") is not False or not info.get("walkthroughRun")):
        raise ValueError("iOS publication requires an unsigned IPA and same-SHA simulator walkthrough")
    if platform == "android" and info.get("signed") != "debug":
        raise ValueError("Android publication requires the debug APK")
    run_id = positive("GITHUB_RUN_ID")
    package_id = positive("PACKAGE_ARTIFACT_ID")
    digest = os.environ.get("PACKAGE_ARTIFACT_SHA256", "").removeprefix("sha256:")
    if not re.fullmatch(r"[0-9a-f]{64}", digest):
        raise ValueError("PACKAGE_ARTIFACT_SHA256 must be a SHA-256 digest")
    created = datetime.fromisoformat(os.environ["PACKAGE_CREATED_AT"].replace("Z", "+00:00"))
    expires = datetime.fromisoformat(os.environ["PACKAGE_EXPIRES_AT"].replace("Z", "+00:00"))
    now = datetime.now(timezone.utc)
    if created.tzinfo is None or expires.tzinfo is None or created > now or expires <= now:
        raise ValueError("Package artifact is outside its valid retention window")
    days = round((expires-created).total_seconds()/86400)
    if not 1 <= days <= 30:
        raise ValueError("Package artifact retention must be 1–30 days")
    url = f"https://github.com/{REPO}/actions/runs/{run_id}/artifacts/{package_id}"
    path = ROOT / "apps/web/downloads.json"
    manifest = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
    manifest.setdefault("repository", REPO)
    manifest["verifiedAt"] = now.date().isoformat()
    manifest["signInRequired"] = True
    manifest.setdefault("platforms", {})
    entry = {"source": source, "run": int(info["run"]), "artifact": int(info["artifact"]),
             "packagingRun": run_id, "packagingArtifact": package_id, "downloadUrl": url,
             "artifactName": "KANTA-" + ("Android-preview-download" if platform == "android" else "iOS-personal-unsigned-download"),
             "signed": info["signed"], "sha256": digest,
         **({"apkSha256": info["sha256"]} if platform == "android" else {"ipaSha256": info["sha256"]}),
             "bytes": int(info["bytes"]), "retentionDays": days}
    if platform == "ios":
        entry.update({"personalSigningRequired": True, "icloudAvailable": False,
                      "walkthroughRun": int(info["walkthroughRun"])})
    manifest["platforms"][platform] = entry
    # Keep the historical top-level keys readable for existing consumers.
    manifest[platform] = entry
    # Keep both platforms' source in sync: verify-website.py requires
    # manifest["android"]["source"] == manifest["ios"]["source"], so when
    # publishing one platform, mirror its source onto the other platform's
    # entry (the other platform's run/artifact/signed fields are unchanged).
    other_platform = "ios" if platform == "android" else "android"
    if other_platform in manifest["platforms"]:
        manifest["platforms"][other_platform]["source"] = source
    manifest["source"] = source
    manifest["packagingRun"] = run_id
    manifest["packagingRunUrl"] = f"https://github.com/{REPO}/actions/runs/{run_id}"
    manifest["retentionDays"] = days
    path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    page_path = ROOT / ("apps/web/android.html" if platform == "android" else "apps/web/ios-download.html")
    page = page_path.read_text(encoding="utf-8")
    section = (f'<section class="section" id="download"><h2>{"Android preview download" if platform == "android" else "Unsigned iPhone / iPad package"}</h2>'
      f'<p><a class="button primary" href="{url}">Download {"verified Android bundle" if platform == "android" else "unsigned iOS bundle"} ↗</a></p>'
      f'<p class="release-note">Verified CI artifact · GitHub sign-in required · {days}-day retention.</p>'
      f'<p>Source <code>{source[:8]}</code> · Validated build run <a href="https://github.com/{REPO}/actions/runs/{int(info["run"])}">#{int(info["run"])}</a>.</p>'
      + ('<p>Debug-signed APK testing preview; not a Google Play release.</p>' if platform == "android" else '<p><strong>Separate trusted personal signing is required before installation.</strong> This is not a Safari installer, App Store app or TestFlight invitation. iCloud Save is unavailable.</p><p>Both iPhone and iPad simulator walkthroughs passed on this exact source in <a href="https://github.com/' + REPO + '/actions/runs/' + str(int(info["walkthroughRun"])) + '">run #' + str(int(info["walkthroughRun"])) + '</a>. Simulator results do not establish physical-device acceptance.</p>')
      + f'<p>Artifact ZIP SHA-256: <code>{digest}</code>. {"APK SHA-256" if platform == "android" else "IPA SHA-256"}: <code>{info["sha256"]}</code>.</p></section>')
    import re as regex
    updated, count = regex.subn(r'<section class="section" id="download">.*?</section>', lambda _: section, page, count=1, flags=regex.S)
    if count != 1:
        raise ValueError("Expected exactly one download section in " + str(page_path))
    page_path.write_text(updated, encoding="utf-8")
    return manifest
if __name__ == "__main__":
    try:
        result = publish()
        print("Published " + os.environ["PLATFORM"] + " artifact for " + result["source"])
    except (ValueError, KeyError, OSError, json.JSONDecodeError) as exc:
        print("platform publication refused: " + str(exc), file=sys.stderr)
        raise SystemExit(1)
