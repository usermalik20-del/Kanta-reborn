import importlib.util
import json
import os
import subprocess
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
        self.git("init", "--quiet")
        self.git("config", "user.name", "KANTA tests")
        self.git("config", "user.email", "kanta-tests@example.invalid")
        (self.root / "history.txt").write_text("older")
        self.git("add", "history.txt")
        self.git("commit", "-m", "older source")
        self.old_source = self.git("rev-parse", "HEAD")
        (self.root / "history.txt").write_text("newer")
        self.git("commit", "-am", "newer source")
        self.new_source = self.git("rev-parse", "HEAD")
        tree = self.git("write-tree")
        self.unrelated_source = self.git("commit-tree", tree, "-m", "unrelated source")
        (self.root / "apps/web/downloads.json").write_text(json.dumps({
            "repository": "usermalik20-del/Kanta-reborn",
            "android": {"source": self.old_source, "downloadUrl": "android-old"},
            "ios": {"source": self.old_source, "downloadUrl": "ios-old"},
            "platforms": {
                "android": {"source": self.old_source, "run": 10},
                "ios": {"source": self.old_source, "run": 20},
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
            "platform": "android", "source": self.new_source, "run": 100, "artifact": 101,
            "file": "test.apk", "sha256": "c" * 64, "bytes": 123, "signed": "debug",
        }))

    def git(self, *args):
        result = subprocess.run(
            ["git", *args],
            cwd=self.root,
            check=True,
            capture_output=True,
            text=True,
        )
        return result.stdout.strip()

    def tearDown(self):
        self.root_patch.stop()
        self.env.stop()
        self.temp.cleanup()

    def test_android_publish_preserves_existing_ios_entry_and_page(self):
        result = publisher.publish()
        self.assertEqual(result["android"]["source"], self.new_source)
        self.assertEqual(result["ios"]["source"], self.old_source)
        self.assertEqual(result["platforms"]["ios"]["source"], self.old_source)
        self.assertEqual(result["platforms"]["ios"]["run"], 20)
        self.assertEqual(result["ios"]["downloadUrl"], "ios-old")
        self.assertIn("Old", (self.root / "apps/web/ios-download.html").read_text())
        self.assertEqual(result["android"]["downloadUrl"].rsplit("/", 1)[-1], "901")
        self.assertIn("Keep page content", (self.root / "apps/web/android.html").read_text())
        self.assertIn("Old", (self.root / "apps/web/ios-download.html").read_text())

    def test_ios_publish_requires_walkthrough_and_preserves_android(self):
        with patch.dict(os.environ, {"PLATFORM": "ios"}):
            (self.root / "verified-downloads/ios/build-info.json").write_text(json.dumps({
                "platform": "ios", "source": self.new_source, "run": 200, "artifact": 201,
                "file": "test.ipa", "sha256": "e" * 64, "bytes": 456,
                "signed": False, "walkthroughRun": 202,
            }))
            result = publisher.publish()
        self.assertEqual(result["android"]["source"], self.old_source)
        self.assertEqual(result["platforms"]["android"]["source"], self.old_source)
        self.assertEqual(result["platforms"]["android"]["run"], 10)
        self.assertEqual(result["android"]["downloadUrl"], "android-old")
        self.assertIn("Old", (self.root / "apps/web/android.html").read_text())
        self.assertEqual(result["ios"]["source"], self.new_source)
        self.assertTrue(result["ios"]["personalSigningRequired"])

    def test_ios_without_walkthrough_is_rejected(self):
        with patch.dict(os.environ, {"PLATFORM": "ios"}):
            (self.root / "verified-downloads/ios/build-info.json").write_text(json.dumps({
                "platform": "ios", "source": self.new_source, "run": 200, "artifact": 201,
                "file": "test.ipa", "sha256": "e" * 64, "bytes": 456, "signed": False,
            }))
            with self.assertRaisesRegex(ValueError, "simulator walkthrough"):
                publisher.publish()

    def test_stale_completion_cannot_replace_newer_published_source(self):
        path = self.root / "apps/web/downloads.json"
        manifest = json.loads(path.read_text())
        manifest["android"]["source"] = self.new_source
        manifest["platforms"]["android"]["source"] = self.new_source
        path.write_text(json.dumps(manifest))
        build_info = self.root / "verified-downloads/android/build-info.json"
        stale = json.loads(build_info.read_text())
        stale["source"] = self.old_source
        build_info.write_text(json.dumps(stale))
        manifest_before = path.read_text()
        page_before = (self.root / "apps/web/android.html").read_text()

        with self.assertRaisesRegex(ValueError, "Refusing stale android publication"):
            publisher.publish()

        self.assertEqual(path.read_text(), manifest_before)
        self.assertEqual((self.root / "apps/web/android.html").read_text(), page_before)

    def test_unrelated_source_history_is_rejected(self):
        build_info = self.root / "verified-downloads/android/build-info.json"
        unrelated = json.loads(build_info.read_text())
        unrelated["source"] = self.unrelated_source
        build_info.write_text(json.dumps(unrelated))

        with self.assertRaisesRegex(ValueError, "unrelated source history"):
            publisher.publish()

    def test_older_retry_for_same_source_cannot_replace_newer_publication(self):
        path = self.root / "apps/web/downloads.json"
        manifest = json.loads(path.read_text())
        manifest["android"].update({"source": self.new_source, "run": 300})
        manifest["platforms"]["android"].update({"source": self.new_source, "run": 300})
        path.write_text(json.dumps(manifest))
        build_info = self.root / "verified-downloads/android/build-info.json"
        retry = json.loads(build_info.read_text())
        retry.update({"source": self.new_source, "run": 200})
        build_info.write_text(json.dumps(retry))
        before = path.read_text()
        page_before = (self.root / "apps/web/android.html").read_text()

        with self.assertRaisesRegex(ValueError, "Refusing older android source run"):
            publisher.publish()

        self.assertEqual(path.read_text(), before)
        self.assertEqual((self.root / "apps/web/android.html").read_text(), page_before)

    def test_newer_retry_for_same_source_can_replace_older_run(self):
        path = self.root / "apps/web/downloads.json"
        manifest = json.loads(path.read_text())
        manifest["android"].update({"source": self.new_source, "run": 100})
        manifest["platforms"]["android"].update({"source": self.new_source, "run": 100})
        path.write_text(json.dumps(manifest))
        build_info = self.root / "verified-downloads/android/build-info.json"
        retry = json.loads(build_info.read_text())
        retry.update({"source": self.new_source, "run": 200})
        build_info.write_text(json.dumps(retry))

        result = publisher.publish()

        self.assertEqual(result["android"]["source"], self.new_source)
        self.assertEqual(result["android"]["run"], 200)

if __name__ == "__main__":
    unittest.main()
