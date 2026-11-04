from __future__ import annotations

import importlib.util
import json
import subprocess
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("update_feed", ROOT / "prepare-update-feed.py")
feed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(feed)

SOURCE = ("0123456789abcdef" * 2) + "01234567"
SHA64 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
GRADLE = (
    "plugins { id 'com.android.application' }\n"
    "android {\n"
    "    namespace 'ph.kanta.reborn'\n"
    "    compileSdk 36\n"
    "    defaultConfig {\n"
    "        applicationId 'ph.kanta.reborn'\n"
    "        minSdk 26\n"
    "        targetSdk 36\n"
    "        versionCode 10\n"
    "        versionName '0.10.0-reconstruction'\n"
    "    }\n"
    "}\n"
)


def downloads_manifest(**overrides) -> dict:
    manifest = {
        "repository": "usermalik20-del/Kanta-reborn",
        "android": {"source": SOURCE, "run": 100, "artifact": 101},
        "platforms": {"android": {"source": SOURCE, "run": 100}},
    }
    manifest.update(overrides)
    return manifest


def build_info(**overrides) -> dict:
    info = {
        "platform": "android",
        "source": SOURCE,
        "run": 100,
        "artifact": 101,
        "file": "KANTA-Android-preview-" + SOURCE[:8] + ".apk",
        "sha256": SHA64,
        "bytes": 52_767_115,
        "signed": "debug",
    }
    info.update(overrides)
    return info


NOW = datetime.now(timezone.utc).replace(microsecond=0)


class FeedContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "apps/web").mkdir(parents=True)
        (self.root / "verified-downloads/android").mkdir(parents=True)
        self.git("init", "--quiet")
        self.git("config", "user.name", "KANTA tests")
        self.git("config", "user.email", "kanta-tests@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        (self.root / "apps/android/app/build.gradle").parent.mkdir(parents=True)
        (self.root / "apps/android/app/build.gradle").write_text(GRADLE)
        self.git("add", ".")
        self.git("commit", "-m", "source with build config")
        self.source_sha = self.git("rev-parse", "HEAD")

    def git(self, *args):
        result = subprocess.run(
            ["git", *args], cwd=self.root, check=True, capture_output=True, text=True
        )
        return result.stdout.strip()

    def tearDown(self):
        self.temp.cleanup()

    def test_canonical_feed_matches_app_contract_shapes(self):
        (self.root / "apps/web/downloads.json").write_text(
            json.dumps(downloads_manifest()))
        (self.root / "verified-downloads/android/build-info.json").write_text(
            json.dumps(build_info(source=self.source_sha)))
        rendered = feed.render_feed(
            downloads_manifest(),
            build_info(
                source=self.source_sha,
                file="KANTA-Android-preview-" + self.source_sha[:8] + ".apk"),
            self.source_sha,
            NOW,
            self.root,
        )
        self.assertEqual(rendered["format"], "kanta-update-feed")
        self.assertEqual(rendered["formatVersion"], 1)
        self.assertEqual(rendered["channel"], "stable-reconstruction")
        self.assertRegex(rendered["publishedAt"], r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z")
        self.assertEqual(rendered["sourceCommit"], self.source_sha)
        android = rendered["android"]
        self.assertEqual(android["versionCode"], 10)
        self.assertEqual(android["versionName"], "0.10.0-reconstruction")
        self.assertEqual(android["minSdk"], 26)
        self.assertTrue(feed.is_accepted_apk_url(android["apkUrl"]))
        self.assertEqual(android["apkBytes"], 52_767_115)
        self.assertEqual(android["apkSha256"], SHA64)
        self.assertFalse(android["mandatory"])
        # The durable URL follows the repo's existing release-asset naming.
        self.assertTrue(
            android["apkUrl"].endswith(
                "/releases/download/v0.10.0-preview-"
                + self.source_sha[:8]
                + "/KANTA-Android-preview-"
                + self.source_sha[:8]
                + ".apk"
            )
        )

    def test_release_tag_and_url_are_deterministic(self):
        self.assertEqual(feed.release_tag(SOURCE, "0.10.0-reconstruction"),
                         "v0.10.0-preview-" + SOURCE[:8])
        url = feed.release_apk_url(SOURCE, "0.10.0-reconstruction", "KANTA.apk")
        self.assertEqual(
            url,
            "https://github.com/usermalik20-del/Kanta-reborn/releases/download/"
            "v0.10.0-preview-" + SOURCE[:8] + "/KANTA.apk",
        )

    def test_url_policy_mirrors_update_policy(self):
        canonical = (
            "https://github.com/usermalik20-del/Kanta-reborn/releases/download/"
            "v0.10.0-preview-01234567/KANTA-Android-preview-01234567.apk"
        )
        self.assertTrue(feed.is_accepted_apk_url(canonical))
        self.assertTrue(
            feed.is_accepted_apk_url(
                "https://release-assets.githubusercontent.com/x/y?sig=1"))
        self.assertTrue(
            feed.is_accepted_apk_url(
                "https://objects.githubusercontent.com/asset?sig=1"))
        self.assertFalse(
            feed.is_accepted_apk_url(
                "https://github.com/usermalik20-del/Kanta-reborn/actions/runs/1/artifacts/2"))
        self.assertFalse(feed.is_accepted_apk_url("http://" + canonical[len("https://"):]))
        self.assertFalse(
            feed.is_accepted_apk_url(
                "https://github.com/other/repo/releases/download/t/f.apk"))
        self.assertFalse(
            feed.is_accepted_apk_url(
                canonical + "?x=1"))

    def test_published_source_prefers_canonical_platforms_map(self):
        manifest = downloads_manifest(
            android={"source": "a" * 40},
            platforms={"android": {"source": SOURCE}},
        )
        self.assertEqual(feed.published_android_source(manifest), SOURCE)
        legacy = downloads_manifest(
            android={"source": SOURCE},
            platforms={"android": {"source": "z" * 40}})
        self.assertEqual(feed.published_android_source(legacy), SOURCE)

    def test_missing_or_malformed_inputs_are_refused(self):
        with self.assertRaises(feed.FeedError):
            feed.published_android_source({})
        with self.assertRaises(feed.FeedError):
            feed.render_feed(downloads_manifest(), build_info(platform="ios"), SOURCE, NOW)
        with self.assertRaises(feed.FeedError):
            feed.render_feed(downloads_manifest(), build_info(sha256="zzz"), SOURCE, NOW)
        with self.assertRaises(feed.FeedError):
            feed.render_feed(downloads_manifest(), build_info(bytes=-1), SOURCE, NOW)
        with self.assertRaises(feed.FeedError):
            feed.render_feed(downloads_manifest(), build_info(bytes=512 * 1024 * 1024), SOURCE, NOW)
        with self.assertRaises(feed.FeedError):
            feed.render_feed(downloads_manifest(), build_info(file=42), SOURCE, NOW)

    def test_published_gradle_must_declare_min_sdk(self):
        self.assertEqual(
            feed.parse_build_identity(GRADLE),
            {"versionCode": 10, "versionName": "0.10.0-reconstruction", "minSdk": 26},
        )
        # A published build.gradle without minSdk must be refused with FeedError,
        # never with the TypeError the old None comparison raised.
        with self.assertRaises(feed.FeedError):
            feed.parse_build_identity(GRADLE.replace("        minSdk 26\n", ""))
        with self.assertRaises(feed.FeedError):
            feed.parse_build_identity("android { }")

    def test_build_identity_comes_from_published_source(self):
        identity = feed.build_identity(self.source_sha, self.root)
        self.assertEqual(identity["versionCode"], 10)
        self.assertEqual(identity["versionName"], "0.10.0-reconstruction")
        self.assertEqual(identity["minSdk"], 26)

    def test_unreadable_published_source_is_refused(self):
        garbage = build_info(source=self.source_sha)
        with self.assertRaises(feed.FeedError):
            feed.render_feed(downloads_manifest(), garbage, "f" * 40, NOW)

    def test_main_writes_feed_atomically(self):
        published = {"android": {"source": self.source_sha, "run": 100},
                     "platforms": {"android": {"source": self.source_sha, "run": 100}}}
        (self.root / "apps/web/downloads.json").write_text(
            json.dumps(published))
        (self.root / "verified-downloads/android/build-info.json").write_text(
            json.dumps(build_info(source=self.source_sha)))
        original = feed.ROOT
        feed.ROOT = self.root
        try:
            feed.main()
        finally:
            feed.ROOT = original
        output = self.root / "apps/web/updates.json"
        self.assertTrue(output.is_file())
        parsed = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(parsed["sourceCommit"], self.source_sha)
        self.assertEqual(parsed["android"]["versionCode"], 10)

    def test_refusal_never_writes_feed(self):
        (self.root / "apps/web/downloads.json").write_text(
            json.dumps(downloads_manifest()))
        (self.root / "verified-downloads/android/build-info.json").write_text(
            json.dumps(build_info(sha256="bad")))
        original = feed.ROOT
        feed.ROOT = self.root
        try:
            with self.assertRaises(feed.FeedError):
                feed.main()
        finally:
            feed.ROOT = original
        self.assertFalse((self.root / "apps/web/updates.json").exists())


if __name__ == "__main__":
    unittest.main()