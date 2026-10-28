#!/usr/bin/env python3
"""Package exact-SHA artifacts only after all release acceptance gates pass."""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import plistlib
import re
import sys
import time
import urllib.error
import urllib.request
import zipfile

REPO = os.environ.get("GITHUB_REPOSITORY", "usermalik20-del/Kanta-reborn")
LIMIT = 128 * 1024 * 1024
SOURCE_WAIT_SECONDS = 120
POLL_INTERVAL_SECONDS = 5

REQUIRED_RUNS = {
    "android": "KANTA Android validation",
    "ios_validation": "KANTA iOS validation",
    "ios_smoke": "KANTA iOS focused simulator smoke",
    "ios_unsigned": "KANTA iOS unsigned artifacts",
    "walkthrough": "KANTA iOS visual walkthrough",
}
ARTIFACT_NAMES = {
    "android": "android-reconstruction-checks",
    "ios": "KANTA-personal-unsigned",
}


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, newurl):
        redirected = super().redirect_request(request, fp, code, message, headers, newurl)
        if redirected is not None:
            redirected.remove_header("Authorization")
        return redirected


def api(path: str, *, binary=False):
    request = urllib.request.Request(
        "https://api.github.com/repos/" + REPO + "/" + path,
        headers={
            "Authorization": "Bearer " + os.environ["GITHUB_TOKEN"],
            "Accept": "application/vnd.github+json",
            "User-Agent": "KANTA-verified-downloads",
        },
    )
    try:
        with urllib.request.build_opener(SafeRedirect()).open(request, timeout=90) as response:
            data = response.read(LIMIT + 1)
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            raise ValueError(
                "GitHub resource not found for " + path
                + "; verify that the source run succeeded and its artifact has not expired"
            ) from exc
        raise
    if len(data) > LIMIT:
        raise ValueError("Artifact response exceeded bound")
    return data if binary else json.loads(data)


def runs_for_source(source_sha: str, workflow_name: str) -> list[dict]:
    payload = api("actions/runs?head_sha=" + source_sha + "&per_page=100")
    runs = [
        run for run in payload.get("workflow_runs", [])
        if run.get("name") == workflow_name
        and run.get("head_branch") == "main"
        and run.get("head_sha") == source_sha
    ]
    return sorted(
        runs,
        key=lambda run: (run.get("created_at", ""), int(run.get("id", 0))),
        reverse=True,
    )


def find_source_run(source_sha: str, workflow_name: str, *, wait_seconds=SOURCE_WAIT_SECONDS) -> dict:
    """Require the newest run for this exact main-branch SHA to have passed."""
    deadline = time.monotonic() + wait_seconds
    last_state = "no matching run is visible"
    while True:
        matches = runs_for_source(source_sha, workflow_name)
        if matches:
            latest = matches[0]
            status = latest.get("status")
            conclusion = latest.get("conclusion")
            if status == "completed":
                if conclusion != "success":
                    raise ValueError(
                        workflow_name + " did not pass for " + source_sha[:12]
                        + ": latest run " + str(latest.get("id"))
                        + " concluded " + str(conclusion)
                    )
                return latest
            last_state = (
                "latest run " + str(latest.get("id")) + " is " + str(status)
            )
        if time.monotonic() >= deadline:
            raise ValueError(
                "Timed out waiting for " + workflow_name + " on exact main SHA "
                + source_sha + ": " + last_state
            )
        time.sleep(POLL_INTERVAL_SECONDS)


def find_artifact(run: dict, artifact_name: str) -> dict:
    payload = api("actions/runs/" + str(run["id"]) + "/artifacts")
    matches = [
        artifact for artifact in payload.get("artifacts", [])
        if artifact.get("name") == artifact_name
    ]
    if len(matches) != 1:
        raise ValueError(
            "Expected exactly one " + artifact_name + " artifact from run "
            + str(run["id"]) + "; found " + str(len(matches))
        )
    artifact = matches[0]
    if artifact.get("expired"):
        raise ValueError(
            artifact_name + " from run " + str(run["id"]) + " has expired"
        )
    return artifact


def accepted(run: dict, artifact: dict, expected: dict):
    workflow_run = artifact.get("workflow_run", {})
    if (
        run.get("conclusion") != "success"
        or run.get("status") != "completed"
        or run.get("head_branch") != "main"
        or run.get("head_sha") != expected["source"]
    ):
        raise ValueError("Source run is not the successful exact main revision")
    if run.get("id") != expected["run"] or artifact.get("id") != expected["artifact"]:
        raise ValueError("Run/artifact identity mismatch")
    if (
        artifact.get("expired")
        or workflow_run.get("id") != expected["run"]
        or workflow_run.get("head_sha") != expected["source"]
        or workflow_run.get("head_branch") != "main"
    ):
        raise ValueError("Artifact expired or belongs to another source run")


def resolve_source(source_sha: str) -> dict:
    if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
        raise ValueError("SOURCE_SHA must be a full 40-character Git commit SHA")

    # Every gate must be a successful main-branch run on the same exact SHA.
    # The native workflow split replaced the former reconstruction workflow.
    gates = {
        name: find_source_run(source_sha, workflow_name)
        for name, workflow_name in REQUIRED_RUNS.items()
    }

    android_run = gates["android"]
    ios_run = gates["ios_unsigned"]
    android_artifact = find_artifact(android_run, ARTIFACT_NAMES["android"])
    ios_artifact = find_artifact(ios_run, ARTIFACT_NAMES["ios"])

    android = {
        "run": android_run["id"],
        "artifact": android_artifact["id"],
        "source": source_sha,
    }
    ios = {
        "run": ios_run["id"],
        "artifact": ios_artifact["id"],
        "source": source_sha,
    }
    accepted(android_run, android_artifact, android)
    accepted(ios_run, ios_artifact, ios)
    return {
        "gates": gates,
        "android": {"run": android_run, "artifact": android_artifact, "expected": android},
        "ios": {"run": ios_run, "artifact": ios_artifact, "expected": ios},
    }


def one(archive, suffix):
    choices = [
        entry for entry in archive.infolist()
        if (entry.filename == suffix.lstrip("/") or entry.filename.endswith(suffix))
        and not entry.is_dir()
    ]
    if len(choices) != 1 or choices[0].file_size > LIMIT:
        raise ValueError("Missing, ambiguous or oversized artifact member: " + suffix)
    return archive.read(choices[0])


def validate_ipa(data, info, checksum, expected):
    if hashlib.sha256(data).hexdigest() != checksum.split()[0].lower():
        raise ValueError("IPA checksum differs from accepted build")
    if (
        str(info.get("run_id")) != str(expected["run"])
        or info.get("source_sha") != expected["source"]
        or info.get("platform") != "iPhoneOS"
        or info.get("signed") is not False
        or info.get("personal_signing") is not True
        or info.get("icloud_available") is not False
    ):
        raise ValueError("Unexpected IPA configuration or source identity")
    with zipfile.ZipFile(io.BytesIO(data)) as app:
        if app.testzip() is not None:
            raise ValueError("Corrupt IPA")
        names = app.namelist()
        if any(name.endswith("embedded.mobileprovision") for name in names):
            raise ValueError("Unexpected provisioning material")
        plist = plistlib.loads(app.read("Payload/KANTA.app/Info.plist"))
        if (
            plist.get("CFBundleIdentifier") != "com.gelo.karaoke"
            or "iPhoneOS" not in plist.get("CFBundleSupportedPlatforms", [])
        ):
            raise ValueError("Unexpected device app")
        if not any(name.endswith("THIRD_PARTY_AUDIO_NOTICES.txt") for name in names):
            raise ValueError("Required software notices missing")


def application_bytes(platform: str, source: dict) -> bytes:
    run = source["run"]
    artifact = source["artifact"]
    expected = source["expected"]
    archive_bytes = api("actions/artifacts/" + str(artifact["id"]) + "/zip", binary=True)
    digest = artifact.get("digest", "")
    if digest.startswith("sha256:") and digest[7:] != hashlib.sha256(archive_bytes).hexdigest():
        raise ValueError("Downloaded artifact digest mismatch")
    with zipfile.ZipFile(io.BytesIO(archive_bytes)) as archive:
        if sum(entry.file_size for entry in archive.infolist()) > LIMIT or archive.testzip() is not None:
            raise ValueError("Corrupt or oversized artifact archive")
        if platform == "android":
            application = one(archive, "/app-debug.apk")
            with zipfile.ZipFile(io.BytesIO(application)) as apk:
                if "AndroidManifest.xml" not in apk.namelist() or "classes.dex" not in apk.namelist():
                    raise ValueError("Not an Android application")
        else:
            application = one(archive, "/KANTA-personal-unsigned.ipa")
            info = json.loads(one(archive, "/artifact-info.json"))
            validate_ipa(
                application, info,
                one(archive, "/KANTA-personal-unsigned.sha256").decode(),
                expected,
            )
    return application


def main():
    source_sha = (os.environ.get("SOURCE_SHA") or os.environ.get("GITHUB_SHA") or "").lower()
    resolved = resolve_source(source_sha)
    gates = resolved["gates"]
    acceptance_runs = {
        key: {"workflow": REQUIRED_RUNS[key], "run": run["id"], "source_sha": source_sha}
        for key, run in gates.items()
    }
    output = Path("verified-downloads")
    manifest = {
        "repository": REPO,
        "source_sha": source_sha,
        "packaging_run": os.environ.get("GITHUB_RUN_ID"),
        "acceptance_gates": acceptance_runs,
        "walkthrough_run": gates["walkthrough"]["id"],
        "builds": {},
    }
    for platform in ("android", "ios"):
        source = resolved[platform]
        expected = source["expected"]
        application = application_bytes(platform, source)
        if platform == "android":
            name = "KANTA-Android-preview-" + source_sha[:8] + ".apk"
            note = "Android testing preview. The APK is byte-identical to the accepted build. The automated test APK is not included.\\n"
        else:
            name = "KANTA-iOS-personal-unsigned-" + source_sha[:8] + ".ipa"
            note = "Unsigned iPhoneOS personal-signing input. Separate trusted personal signing is required before installation. iCloud Save is unavailable in this build.\\n"
        destination = output / platform
        destination.mkdir(parents=True, exist_ok=True)
        (destination / name).write_bytes(application)
        checksum = hashlib.sha256(application).hexdigest()
        (destination / "SHA256.txt").write_text(checksum + "  " + name + "\\n")
        (destination / "README.txt").write_text(
            note + "Source: " + source_sha
            + "\\nValidated build run: " + str(expected["run"])
            + "\\nAcceptance gate runs: " + ", ".join(
                key + "=" + str(gates[key]["id"]) for key in REQUIRED_RUNS
            )
            + "\\n"
        )
        details = dict(
            expected, file=name, sha256=checksum, bytes=len(application),
            signed=False if platform == "ios" else "debug",
            acceptance_runs={key: run["id"] for key, run in gates.items()},
        )
        (destination / "build-info.json").write_text(json.dumps(details, indent=2) + "\\n")
        manifest["builds"][platform] = details
        print(
            platform + ": verified original application bytes from run "
            + str(expected["run"]) + ", " + str(len(application)) + " bytes",
            flush=True,
        )
    (output / "downloads.json").write_text(json.dumps(manifest, indent=2) + "\\n")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ValueError, KeyError, urllib.error.URLError) as exc:
        print("verified-download packaging refused: " + str(exc), file=sys.stderr)
        raise SystemExit(1)
