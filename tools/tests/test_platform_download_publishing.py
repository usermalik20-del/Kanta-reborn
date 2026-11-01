import importlib.util
import json
import os
from datetime import datetime, timedelta, timezone
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("platform_publisher", ROOT / "publish-platform-download.py")
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)

class PlatformPublisherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "apps/web").mkdir(parents=True)
        (self.root / "verified-downloads/android").mkdir(parents=True)
        (self.root / "verified-downloads/ios").mkdir(parents=True)
        (self.root / "apps/web/downloads.json").write_text(json.dumps({
            "repository": "usermalik20-del/Kanta-reborn",
            "android": {"source": "old-android", "downloadUrl": "android-old"},
            "ios": {"source": "old-ios", "downloadUrl": "ios-old"},
            "platforms": {
                "android": {"source": "old-android", "run": 10},
                "ios": {"source": "old-ios", "run": 20},
            },
        }))
        for page in ("android.html", "ios-download.html"):
            (self.root / "apps/web" / page).write_text(
                '<section class="section" id="download"><h2>Old</h2></section><p>Keep page content</p>'
            )
        self.now = datetime.now(timezone.utc).replace(microsecond=0)
        created = self.now - timedelta(hours=1)
        expires = created + timedelta(days=30)
        stamp = lambda value: value.isoformat().replace("+00:00", "Z")
        self.env = patch.dict(os.environ, {
            "PLATFORM": "android", "GITHUB_RUN_ID": "900",
            "PACKAGE_ARTIFACT_ID": "901", "PACKAGE_ARTIFACT_SHA256": "a" * 64,
            "PACKAGE_CREATED_AT": stamp(created), "PACKAGE_EXPIRES_AT": stamp(expires),
            "GITHUB_REPOSITORY": "usermalik20-del/Kanta-reborn",
        })
        self.env.start()
        self.root_patch = patch.object(publisher, "ROOT", self.root)
        self.root_patch.start()
        (self.root / "verified-downloads/android/build-info.json").write_text(json.dumps({
            "platform": "android", "source": "b" * 40, "run": 100, "artifact": 101,
            "file": "test.apk", "sha256": "c" * 64, "bytes": 123, "signed": "debug",
        }))

    def tearDown(self):
        self.root_patch.stop()
        self.env.stop()
        self.temp.cleanup()

    def test_android_publish_preserves_existing_ios_entry_and_page(self):
        result = publisher.publish()
        self.assertEqual(result["android"]["source"], "b" * 40)
        self.assertEqual(result["ios"]["source"], "b" * 40)
        self.assertEqual(result["platforms"]["ios"]["source"], "b" * 40)
        self.assertEqual(result["platforms"]["ios"]["run"], 20)
        self.assertEqual(result["android"]["downloadUrl"].rsplit("/", 1)[-1], "901")
        self.assertIn("Keep page content", (self.root / "apps/web/android.html").read_text())
        self.assertIn("Old", (self.root / "apps/web/ios-download.html").read_text())

    def test_ios_publish_requires_walkthrough_and_preserves_android(self):
        with patch.dict(os.environ, {"PLATFORM": "ios"}):
            (self.root / "verified-downloads/ios/build-info.json").write_text(json.dumps({
                "platform": "ios", "source": "d" * 40, "run": 200, "artifact": 201,
                "file": "test.ipa", "sha256": "e" * 64, "bytes": 456,
                "signed": False, "walkthroughRun": 202,
            }))
            result = publisher.publish()
        self.assertEqual(result["android"]["source"], "d" * 40)
        self.assertEqual(result["platforms"]["android"]["source"], "d" * 40)
        self.assertEqual(result["platforms"]["android"]["run"], 10)
        self.assertEqual(result["ios"]["source"], "d" * 40)
        self.assertTrue(result["ios"]["personalSigningRequired"])

    def test_ios_without_walkthrough_is_rejected(self):
        with patch.dict(os.environ, {"PLATFORM": "ios"}):
            (self.root / "verified-downloads/ios/build-info.json").write_text(json.dumps({
                "platform": "ios", "source": "d" * 40, "run": 200, "artifact": 201,
                "file": "test.ipa", "sha256": "e" * 64, "bytes": 456, "signed": False,
            }))
            with self.assertRaisesRegex(ValueError, "simulator walkthrough"):
                publisher.publish()

if __name__ == "__main__":
    unittest.main()
