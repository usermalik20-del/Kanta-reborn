import hashlib
import importlib.util
import io
import pathlib
import plistlib
import re
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location(
    "prepare",
    pathlib.Path(__file__).parents[1] / "prepare-verified-downloads.py",
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

SOURCE = "a" * 40


def run_record(run_id=41, name="KANTA reconstruction validation", source=SOURCE,
               conclusion="success", created="2026-10-24T00:00:00Z", branch="main"):
    return {
        "id": run_id,
        "name": name,
        "head_sha": source,
        "head_branch": branch,
        "status": "completed",
        "conclusion": conclusion,
        "created_at": created,
    }


def artifact_record(artifact_id=81, run_id=41, name="android-reconstruction-checks",
                    source=SOURCE, expired=False):
    return {
        "id": artifact_id,
        "name": name,
        "expired": expired,
        "digest": "",
        "workflow_run": {
            "id": run_id,
            "head_sha": source,
            "head_branch": "main",
        },
    }


class DownloadContracts(unittest.TestCase):
    def test_latest_exact_main_run_must_succeed(self):
        good = run_record()
        newer_failure = run_record(
            run_id=42, conclusion="failure", created="2026-10-24T00:01:00Z"
        )
        with patch.object(
            module, "api", return_value={"workflow_runs": [good, newer_failure]}
        ):
            with self.assertRaisesRegex(ValueError, "latest run 42 concluded failure"):
                module.find_source_run(SOURCE, "KANTA reconstruction validation", wait_seconds=0)

    def test_wrong_sha_and_feature_branch_are_not_accepted(self):
        wrong_sha = run_record(run_id=50, source="b" * 40)
        feature = run_record(run_id=51, branch="feature/test")
        with patch.object(
            module, "api", return_value={"workflow_runs": [wrong_sha, feature]}
        ):
            with self.assertRaisesRegex(ValueError, "no matching run is visible"):
                module.find_source_run(SOURCE, "KANTA reconstruction validation", wait_seconds=0)

    def test_waits_for_a_run_to_become_visible(self):
        with patch.object(
            module, "api",
            side_effect=[
                {"workflow_runs": []},
                {"workflow_runs": [run_record()]},
            ],
        ), patch.object(module.time, "sleep"):
            found = module.find_source_run(
                SOURCE, "KANTA reconstruction validation",
                wait_seconds=2,
            )
        self.assertEqual(found["id"], 41)

    def test_artifact_is_discovered_from_its_exact_successful_run(self):
        run = run_record()
        artifact = artifact_record()
        with patch.object(module, "api", return_value={"artifacts": [artifact]}):
            found = module.find_artifact(run, "android-reconstruction-checks")
        expected = {"run": 41, "artifact": 81, "source": SOURCE}
        module.accepted(run, found, expected)

    def test_expired_or_ambiguous_artifacts_are_rejected(self):
        run = run_record()
        with patch.object(
            module, "api",
            return_value={"artifacts": [artifact_record(expired=True)]},
        ):
            with self.assertRaisesRegex(ValueError, "has expired"):
                module.find_artifact(run, "android-reconstruction-checks")
        with patch.object(
            module, "api",
            return_value={"artifacts": [
                artifact_record(81), artifact_record(82)
            ]},
        ):
            with self.assertRaisesRegex(ValueError, "found 2"):
                module.find_artifact(run, "android-reconstruction-checks")

    def test_resolver_requires_walkthrough_smoke_and_both_builds_for_same_sha(self):
        walkthrough = run_record(61, "KANTA iOS visual walkthrough")
        reconstruction = run_record(62, "KANTA reconstruction validation")
        ios = run_record(63, "KANTA iOS unsigned artifacts")
        smoke = run_record(64, "KANTA iOS focused simulator smoke")
        android_artifact = artifact_record(82, 62, "android-reconstruction-checks")
        ios_artifact = artifact_record(83, 63, "KANTA-personal-unsigned")
        with patch.object(
            module, "find_source_run",
            side_effect=[walkthrough, reconstruction, ios, smoke],
        ) as find_run, patch.object(
            module, "find_artifact",
            side_effect=[android_artifact, ios_artifact],
        ):
            found_walkthrough, found_smoke, android, device = module.resolve_source(SOURCE)
        self.assertEqual(found_walkthrough["id"], 61)
        self.assertEqual(found_smoke["id"], 64)
        self.assertEqual(android["expected"], {
            "run": 62, "artifact": 82, "source": SOURCE
        })
        self.assertEqual(device["expected"], {
            "run": 63, "artifact": 83, "source": SOURCE
        })
        self.assertEqual(find_run.call_count, 4)
    def test_invalid_source_sha_is_rejected_before_api_access(self):
        with patch.object(module, "api") as api_call:
            with self.assertRaisesRegex(ValueError, "full 40-character"):
                module.resolve_source("not-a-sha")
        api_call.assert_not_called()

    def test_wrong_run_or_source_identity_is_rejected(self):
        expected = {"run": 41, "artifact": 81, "source": SOURCE}
        with self.assertRaisesRegex(ValueError, "exact main revision"):
            module.accepted(
                run_record(source="b" * 40), artifact_record(), expected
            )
        with self.assertRaisesRegex(ValueError, "identity mismatch"):
            module.accepted(
                run_record(), artifact_record(82), expected
            )

    def test_unique_artifact_member_and_redirect_credentials(self):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as archive:
            archive.writestr("KANTA-personal-unsigned.ipa", b"device app")
        with zipfile.ZipFile(io.BytesIO(data.getvalue())) as archive:
            self.assertEqual(
                module.one(archive, "/KANTA-personal-unsigned.ipa"),
                b"device app",
            )
        request = module.urllib.request.Request(
            "https://api.github.com/artifact",
            headers={"Authorization": "Bearer fixture"},
        )
        redirected = module.SafeRedirect().redirect_request(
            request, None, 302, "Found", {},
            "https://storage.example/artifact.zip",
        )
        self.assertIsNone(redirected.get_header("Authorization"))

    def test_ambiguous_application_is_rejected(self):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as archive:
            archive.writestr("one/app-debug.apk", b"one")
            archive.writestr("two/app-debug.apk", b"two")
        with zipfile.ZipFile(io.BytesIO(data.getvalue())) as archive:
            with self.assertRaisesRegex(ValueError, "ambiguous"):
                module.one(archive, "/app-debug.apk")

    def test_unsigned_device_identity_and_checksum(self):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as archive:
            archive.writestr(
                "Payload/KANTA.app/Info.plist",
                plistlib.dumps({
                    "CFBundleIdentifier": "com.gelo.karaoke",
                    "CFBundleSupportedPlatforms": ["iPhoneOS"],
                }),
            )
            archive.writestr(
                "Payload/KANTA.app/THIRD_PARTY_AUDIO_NOTICES.txt",
                "fixture notices",
            )
        app = data.getvalue()
        expected = {"run": 63, "source": SOURCE}
        info = {
            "run_id": "63",
            "source_sha": SOURCE,
            "platform": "iPhoneOS",
            "signed": False,
            "personal_signing": True,
            "icloud_available": False,
        }
        checksum = hashlib.sha256(app).hexdigest()
        module.validate_ipa(app, info, checksum, expected)
        with self.assertRaisesRegex(ValueError, "checksum"):
            module.validate_ipa(app + b"changed", info, checksum, expected)
        with self.assertRaisesRegex(ValueError, "source identity"):
            module.validate_ipa(app, dict(info, source_sha="b" * 40), checksum, expected)
        with self.assertRaisesRegex(ValueError, "source identity"):
            module.validate_ipa(app, dict(info, signed=True), checksum, expected)

def workflow_event_section(source, event):
    triggers = source.split("\njobs:", 1)[0]
    match = re.search(
        rf"(?ms)^  {re.escape(event)}:\n(.*?)(?=^  [A-Za-z0-9_-]+:\n|^permissions:|\Z)",
        triggers,
    )
    if match is None:
        raise AssertionError(f"Missing {event} trigger in workflow")
    return match.group(1)


class ExactShaWorkflowTriggerContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        root = pathlib.Path(__file__).resolve().parents[2]
        cls.walkthrough = (root / ".github/workflows/ios-visual-walkthrough.yml").read_text(encoding="utf-8")
        cls.archive = (root / ".github/workflows/ios-runtime-artifacts.yml").read_text(encoding="utf-8")
        cls.smoke = (root / ".github/workflows/ios-smoke.yml").read_text(encoding="utf-8")

    def test_android_source_changes_trigger_all_ios_acceptance_gates(self):
        for workflow_name, source in (("walkthrough", self.walkthrough), ("archive", self.archive), ("focused smoke", self.smoke)):
            for event in ("push", "pull_request"):
                with self.subTest(workflow=workflow_name, event=event):
                    section = workflow_event_section(source, event)
                    self.assertIn("'apps/android/**'", section)

    def test_android_companion_guard_changes_trigger_ios_smoke_and_walkthrough(self):
        for workflow_name, source in (("focused smoke", self.smoke), ("walkthrough", self.walkthrough)):
            for event in ("push", "pull_request"):
                with self.subTest(workflow=workflow_name, event=event):
                    section = workflow_event_section(source, event)
                    self.assertIn("'tools/verify-android-companion-interface.py'", section)

    def test_android_companion_guard_changes_trigger_ios_archive(self):
        for event in ("push", "pull_request"):
            with self.subTest(event=event):
                section = workflow_event_section(self.archive, event)
                self.assertIn("'tools/**'", section)


if __name__ == "__main__":
    unittest.main()
