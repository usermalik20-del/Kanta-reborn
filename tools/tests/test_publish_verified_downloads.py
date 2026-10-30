import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location(
    "publisher", ROOT / "publish-verified-downloads.py"
)
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


def fixture_manifest():
    source = "a" * 40
    return {
        "source_sha": source,
        "walkthrough_run": 303,
        "builds": {
            "android": {
                "source": source, "run": 101, "artifact": 201,
                "sha256": "b" * 64, "bytes": 12345, "signed": "debug",
            },
            "ios": {
                "source": source, "run": 102, "artifact": 202,
                "sha256": "c" * 64, "bytes": 23456, "signed": False,
            },
        },
    }


class VerifiedDownloadPublisherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous_cwd = os.getcwd()
        os.chdir(self.temp.name)
        self.root = Path(self.temp.name)
        (self.root / "verified-downloads").mkdir()
        (self.root / "apps/web").mkdir(parents=True)
        (self.root / "verified-downloads/downloads.json").write_text(
            json.dumps(fixture_manifest()), encoding="utf-8"
        )
        for name in ("android.html", "ios-download.html"):
            (self.root / "apps/web" / name).write_text(
                '<html><section class="section" id="download"><h2>Old download</h2></section><section id="rest">Keep me</section></html>',
                encoding="utf-8",
            )
        self.env = patch.dict(os.environ, {
            "GITHUB_RUN_ID": "404",
            "ANDROID_PACKAGE_ARTIFACT_ID": "501",
            "IOS_PACKAGE_ARTIFACT_ID": "502",
            "GITHUB_REPOSITORY": "usermalik20-del/Kanta-reborn",
            "VERIFIED_AT": "2026-10-31",
        })
        self.env.start()
        self.root_patch = patch.object(publisher, "ROOT", self.root)
        self.root_patch.start()

    def tearDown(self):
        self.root_patch.stop()
        self.env.stop()
        os.chdir(self.previous_cwd)
        self.temp.cleanup()

    def test_publishes_same_sha_manifest_and_artifact_links(self):
        result = publisher.publish()
        self.assertEqual(result["source"], "a" * 40)
        self.assertEqual(result["android"]["downloadUrl"],
                         "https://github.com/usermalik20-del/Kanta-reborn/actions/runs/404/artifacts/501")
        self.assertEqual(result["ios"]["downloadUrl"],
                         "https://github.com/usermalik20-del/Kanta-reborn/actions/runs/404/artifacts/502")
        saved = json.loads((self.root / "apps/web/downloads.json").read_text())
        self.assertEqual(saved["source"], "a" * 40)
        self.assertFalse(saved["ios"]["signed"])
        self.assertTrue(saved["ios"]["personalSigningRequired"])
        self.assertEqual(saved["retentionDays"], 30)
        android_page = (self.root / "apps/web/android.html").read_text()
        ios_page = (self.root / "apps/web/ios-download.html").read_text()
        self.assertIn("/artifacts/501", android_page)
        self.assertIn("/artifacts/502", ios_page)
        self.assertIn("Keep me", android_page)
        self.assertIn("Keep me", ios_page)
        self.assertIn("debug-signed APK", android_page)
        self.assertIn("not a Safari installer", ios_page)

    def test_rejects_mixed_source_revisions(self):
        data = fixture_manifest()
        data["builds"]["ios"]["source"] = "d" * 40
        (self.root / "verified-downloads/downloads.json").write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "share one exact source SHA"):
            publisher.publish()

    def test_rejects_signed_ipa(self):
        data = fixture_manifest()
        data["builds"]["ios"]["signed"] = True
        (self.root / "verified-downloads/downloads.json").write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "iOS must remain unsigned"):
            publisher.publish()

    def test_rejects_missing_walkthrough_provenance(self):
        data = fixture_manifest()
        del data["walkthrough_run"]
        (self.root / "verified-downloads/downloads.json").write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "walkthrough"):
            publisher.publish()


if __name__ == "__main__":
    unittest.main()
