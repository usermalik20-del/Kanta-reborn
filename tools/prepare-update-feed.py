#!/usr/bin/env python3
"""Generate the OTA update feed from the most recently published Android build.

The feed is the single source of truth the Android app's self-updater polls at
https://kanta-public.vercel.app/updates.json. It is produced only after a
verified Android build exists, and every value in it is taken from evidence
already in the repository or in the publishing job:

  * apps/web/downloads.json                -- which source SHA is published
  * verified-downloads/android/build-info.json -- APK file, SHA-256 and size
  * verified-downloads/android/apk-metadata.json -- versionCode / versionName /
    minSdk / signerSha256, read from the exact APK rather than from Gradle source

The durable apkUrl is a GitHub release-asset URL under the repo's existing
naming scheme (v<version>-preview-<sha8>), never an Actions artifact URL:
artifacts require sign-in and expire, so a feed pointing at one could never
update a device.

Output shape mirrors the framework-free UpdateManifest contract in
apps/android/app/src/main/java/ph/kanta/reborn/core/update/; the URL rules
duplicate UpdatePolicy.isAcceptedApkUrl so the tool cannot generate a feed the
app would refuse.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = "usermalik20-del/Kanta-reborn"
RELEASES = "https://github.com/" + REPOSITORY + "/releases/download/"
CHANNEL = "stable-reconstruction"
FORMAT = "kanta-update-feed"
FORMAT_VERSION = 1
ANDROID_MIN_SDK = 26
HEX40 = re.compile(r"[0-9a-f]{40}")
HEX64 = re.compile(r"[0-9a-f]{64}")


class FeedError(ValueError):
    """Refusal to emit an update feed (never silent, never partial)."""


def load_json(path: Path) -> dict:
    if not path.is_file():
        raise FeedError("missing " + str(path))
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, UnicodeDecodeError) as failure:
        raise FeedError("unreadable JSON " + str(path) + ": " + str(failure))
    if not isinstance(value, dict):
        raise FeedError("not a JSON object: " + str(path))
    return value


def published_android_source(downloads: dict) -> str:
    """Full lowercase SHA of the currently published Android build."""
    platforms = downloads.get("platforms")
    candidate = None
    if isinstance(platforms, dict) and isinstance(platforms.get("android"), dict):
        candidate = platforms["android"].get("source")
    if not isinstance(candidate, str) or not HEX40.fullmatch(candidate):
        legacy = downloads.get("android")
        if isinstance(legacy, dict) and isinstance(legacy.get("source"), str):
            candidate = legacy["source"]
    if not isinstance(candidate, str) or not HEX40.fullmatch(candidate):
        raise FeedError("downloads.json has no published android source SHA")
    return candidate


def build_identity(source_sha: str, root: Path = ROOT) -> dict:
    """Read identity extracted from the exact APK, never Gradle source."""
    metadata = load_json(root / "verified-downloads/android/apk-metadata.json")
    if metadata.get("source") not in (None, source_sha):
        raise FeedError("APK metadata source does not match published source")
    required = ("versionCode", "versionName", "minSdk", "signerSha256")
    if any(key not in metadata for key in required):
        raise FeedError("verified APK metadata is incomplete")
    if (isinstance(metadata["versionCode"], bool) or not isinstance(metadata["versionCode"], int)
            or metadata["versionCode"] < 1 or metadata["versionCode"] > 2100000000):
        raise FeedError("verified APK versionCode is outside the bounded range")
    if not isinstance(metadata["versionName"], str) or not re.fullmatch(r"[A-Za-z0-9.+-]{1,64}", metadata["versionName"]):
        raise FeedError("verified APK versionName is outside the allowed shape")
    if (isinstance(metadata["minSdk"], bool) or not isinstance(metadata["minSdk"], int)
            or metadata["minSdk"] < ANDROID_MIN_SDK):
        raise FeedError("verified APK minSdk is below the supported minimum")
    if not isinstance(metadata["signerSha256"], str) or not HEX64.fullmatch(metadata["signerSha256"]):
        raise FeedError("verified APK signer certificate is missing")
    return {key: metadata[key] for key in required}

def release_tag(source_sha: str, version_name: str) -> str:
    return "v" + version_name.split("-")[0] + "-preview-" + source_sha[:8]


def release_apk_url(source_sha: str, version_name: str, file_name: str) -> str:
    """Canonical durable URL; mirrors UpdatePolicy.isAcceptedApkUrl's allowlist."""
    return RELEASES + release_tag(source_sha, version_name) + "/" + file_name


def is_accepted_apk_url(url: str) -> bool:
    """Mirror of UpdatePolicy.isAcceptedApkUrl: canonical or CDN redirect URLs."""
    lower = url.lower()
    if not lower.startswith("https://"):
        return False
    # Hosts are case-insensitive, but the GitHub path is case-sensitive: keep
    # the path in its original case so only the exact repository spelling is
    # accepted under github.com.
    rest = url[len("https://"):]
    lower_rest = lower[len("https://"):]
    host, _, path = rest.partition("/")
    host = host.lower()
    path = "/" + path
    if host == "github.com":
        prefix = "/" + REPOSITORY + "/releases/download/"
        if not path.startswith(prefix):
            return False
        suffix = path[len(prefix):]
        segments = suffix.split("/")
        if len(segments) != 2 or not all(segments):
            return False
        return "@" not in url and "?" not in url and "#" not in url
    if host in ("release-assets.githubusercontent.com", "objects.githubusercontent.com"):
        return "@" not in url and path.startswith("/")
    return False


def render_feed(downloads: dict, build_info: dict, source_sha: str,
                now: datetime, root: Path | None = None) -> dict:
    if root is None:
        root = ROOT
    if build_info.get("platform") != "android":
        raise FeedError("build-info.json is not an android build")
    file_name = build_info.get("file")
    if not isinstance(file_name, str):
        raise FeedError("build-info.json has no apk file name")
    apk_sha256 = build_info.get("sha256")
    if not isinstance(apk_sha256, str) or not HEX64.fullmatch(apk_sha256):
        raise FeedError("build-info.json has no lowercase 64-hex apk SHA-256")
    apk_bytes = build_info.get("bytes")
    if not isinstance(apk_bytes, int) or apk_bytes < 1 or apk_bytes > 256 * 1024 * 1024:
        raise FeedError("build-info.json apk bytes out of range")
    identity = build_identity(source_sha, root)
    previous_path = root / "apps/web/updates.json"
    if previous_path.is_file():
        previous = load_json(previous_path)
        previous_android = previous.get("android")
        if not isinstance(previous_android, dict):
            raise FeedError("existing update feed has no android entry")
        old_code = previous_android.get("versionCode")
        if not isinstance(old_code, int) or identity["versionCode"] <= old_code:
            raise FeedError("versionCode must increase strictly over the published feed")
        old_source = previous.get("sourceCommit")
        if not isinstance(old_source, str) or not HEX40.fullmatch(old_source):
            raise FeedError("existing feed has no valid sourceCommit")
        try:
            ancestry = subprocess.run(["git", "merge-base", "--is-ancestor", old_source, source_sha], cwd=root, check=False)
        except OSError as failure:
            raise FeedError("cannot verify update source ancestry") from failure
        if ancestry.returncode != 0:
            raise FeedError("update source is not a descendant of the published source")
    apk_url = release_apk_url(source_sha, identity["versionName"], file_name)
    if not is_accepted_apk_url(apk_url):
        raise FeedError("derived apkUrl fails the app's own URL policy: " + apk_url)
    feed = {
        "format": FORMAT,
        "formatVersion": FORMAT_VERSION,
        "channel": CHANNEL,
        "publishedAt": now.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "sourceCommit": source_sha,
        "android": {
            "versionCode": identity["versionCode"],
            "versionName": identity["versionName"],
            "minSdk": identity["minSdk"],
            "signingCertSha256": identity["signerSha256"],
            "apkUrl": apk_url,
            "apkBytes": apk_bytes,
            "apkSha256": apk_sha256,
            "notes": "KANTA preview built from " + source_sha[:8],
            "mandatory": False,
        },
    }
    return feed


def main() -> None:
    downloads = load_json(ROOT / "apps/web/downloads.json")
    build_info = load_json(ROOT / "verified-downloads/android/build-info.json")
    source_sha = published_android_source(downloads)
    feed = render_feed(downloads, build_info, source_sha, datetime.now(timezone.utc))
    output = ROOT / "apps/web/updates.json"
    rendered = json.dumps(feed, indent=2, sort_keys=False) + "\n"
    temporary = output.with_suffix(".json.tmp")
    temporary.write_text(rendered, encoding="utf-8")
    temporary.replace(output)
    print("wrote " + str(output.relative_to(ROOT)) + " for " + source_sha[:12])


if __name__ == "__main__":
    try:
        main()
    except (FeedError, OSError, subprocess.SubprocessError) as failure:
        print("update feed refused: " + str(failure), file=sys.stderr)
        raise SystemExit(1)