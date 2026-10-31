"""Regression checks for KANTA's route-specific Content Security Policy."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "apps/web"
CONFIG = json.loads((WEB / "vercel.json").read_text(encoding="utf-8"))

PUBLIC_ROUTES = {
    "/", "/ios-download", "/android", "/guide", "/features",
    "/privacy", "/legal", "/licenses", "/support", "/songbook",
}
STRICT_CSP = {
    "default-src": frozenset({"'none'"}),
    "script-src": frozenset({"'self'"}),
    "style-src": frozenset({"'self'", "'unsafe-inline'"}),
    "img-src": frozenset({"'self'"}),
    "connect-src": frozenset({"'none'"}),
    "base-uri": frozenset({"'none'"}),
    "form-action": frozenset({"'none'"}),
    "frame-ancestors": frozenset({"'none'"}),
}

def parse_csp(policy):
    directives = {}
    for segment in policy.split(";"):
        tokens = segment.strip().split()
        if not tokens:
            continue
        name, *sources = tokens
        if name in directives:
            raise ValueError("Duplicate CSP directive: " + name)
        directives[name] = frozenset(sources)
    return directives


def csp_rules():
    return [
        rule for rule in CONFIG["headers"]
        if any(header.get("key", "").lower() == "content-security-policy" for header in rule.get("headers", []))
    ]


def policy_for(route):
    matches = [
        header["value"]
        for rule in csp_rules() if rule.get("source") == route
        for header in rule.get("headers", [])
        if header.get("key", "").lower() == "content-security-policy"
    ]
    if len(matches) != 1:
        raise AssertionError(f"{route!r} must have exactly one CSP rule, found {len(matches)}")
    return parse_csp(matches[0])


class WebsiteSecurityContractTests(unittest.TestCase):
    def test_every_public_route_has_one_explicit_csp_rule(self):
        rules = csp_rules()
        sources = [rule.get("source") for rule in rules]
        self.assertEqual(len(sources), len(PUBLIC_ROUTES))
        self.assertEqual(set(sources), PUBLIC_ROUTES)
        self.assertEqual(len(sources), len(set(sources)))

        wildcard = [rule for rule in CONFIG["headers"] if rule.get("source") == "/(.*)"]
        self.assertEqual(len(wildcard), 1)
        self.assertFalse(any(
            header.get("key", "").lower() == "content-security-policy"
            for header in wildcard[0].get("headers", [])
        ))

    def test_homepage_uses_strict_csp_without_analytics(self):
        self.assertEqual(policy_for("/"), STRICT_CSP)
        self.assertNotIn("'unsafe-inline'", policy_for("/")[ "script-src" ])

        html = (WEB / "index.html").read_text(encoding="utf-8")
        self.assertNotIn("/analytics.js", html)
        self.assertIsNone(re.search(r"<script\b(?![^>]*\bsrc\s*=)[^>]*>", html, re.IGNORECASE))
        self.assertFalse((WEB / "analytics.js").exists())

        for route in sorted(PUBLIC_ROUTES):
            filename = "index.html" if route == "/" else route.lstrip("/") + ".html"
            source = (WEB / filename).read_text(encoding="utf-8").lower()
            with self.subTest(route=route):
                self.assertNotIn("googletagmanager.com", source)
                self.assertNotIn("google-analytics.com", source)

    def test_adsense_stays_disabled_until_nonce_csp_is_available(self):
        # AdSense documents nonce-based strict CSP and a nonce on every script.
        # This static Vercel site cannot currently issue per-response nonces, so
        # loading the third-party script would be both blocked and unsafe on the
        # private client-side Songbook. Keep the integration disabled fail-closed.
        for route in sorted(PUBLIC_ROUTES):
            filename = "index.html" if route == "/" else route.lstrip("/") + ".html"
            source = (WEB / filename).read_text(encoding="utf-8")
            with self.subTest(route=route):
                self.assertNotIn("adsbygoogle.js", source)
        self.assertNotIn("pagead2.googlesyndication.com", policy_for("/")["script-src"])

    def test_non_home_pages_keep_network_access_disabled(self):
        for route in sorted(PUBLIC_ROUTES - {"/"}):
            with self.subTest(route=route):
                self.assertEqual(policy_for(route), STRICT_CSP)
                self.assertEqual(policy_for(route)["connect-src"], frozenset({"'none'"}))
                self.assertEqual(policy_for(route)["script-src"], frozenset({"'self'"}))



    def test_all_public_pages_share_the_responsive_primary_navigation(self):
        expected_links = {
            "/ios-download", "/android", "/guide", "/features", "/songbook", "/support",
        }
        for route in sorted(PUBLIC_ROUTES):
            filename = "index.html" if route == "/" else route.lstrip("/") + ".html"
            source = (WEB / filename).read_text(encoding="utf-8")
            with self.subTest(route=route):
                header_match = re.search(r"<header\b[\s\S]*?</header>", source, re.IGNORECASE)
                self.assertIsNotNone(header_match, filename)
                header = header_match.group(0)
                for marker in (
                    'class="site-header"',
                    'class="brand"',
                    'aria-label="KANTA home"',
                ):
                    self.assertIn(marker, header, (filename, marker))

                nav = re.search(r"<nav\b([^>]*)>([\s\S]*?)</nav>", header, re.IGNORECASE)
                self.assertIsNotNone(nav, filename)
                attributes, body = nav.groups()
                class_match = re.search(r'\bclass\s*=\s*["\']([^"\']+)["\']', attributes, re.IGNORECASE)
                self.assertIsNotNone(class_match, filename)
                self.assertTrue(
                    {"site-nav", "nav-links"}.intersection(class_match.group(1).split()),
                    (filename, "unsupported primary-navigation class"),
                )
                self.assertRegex(attributes, r'\baria-label\s*=\s*["\']Main navigation["\']')
                links = re.findall(r'<a\b[^>]*\bhref=["\']([^"\']+)["\']', body, re.IGNORECASE)
                self.assertEqual(set(links), expected_links, filename)
                self.assertEqual(len(links), len(expected_links), filename)

    def test_public_pages_do_not_repeat_adjacent_feature_headings(self):
        for route in sorted(PUBLIC_ROUTES):
            filename = "index.html" if route == "/" else route.lstrip("/") + ".html"
            source = (WEB / filename).read_text(encoding="utf-8")
            with self.subTest(filename=filename):
                self.assertIsNone(
                    re.search(r"(<h3>[^<]+</h3>)\s*\1", source, re.IGNORECASE),
                    filename,
                )

    def test_primary_navigation_wraps_and_remains_visible_on_mobile(self):
        source = (WEB / "style.css").read_text(encoding="utf-8")
        self.assertRegex(
            source,
            r"nav\s*,\s*\.site-nav\s*,\s*\.nav-links\s*\{[^}]*display\s*:\s*flex[^}]*flex-wrap\s*:\s*wrap",
        )
        self.assertNotRegex(
            source,
            r"(?s)(?:\.site-nav|\.nav-links)[^{}]*\{[^}]*display\s*:\s*none",
        )
        self.assertNotRegex(source, r"(?m)^\s*ap:anywhere\}\s*$")

    def test_download_pages_match_verified_artifact_provenance(self):
        manifest = json.loads((WEB / "downloads.json").read_text(encoding="utf-8"))
        if "packagingRun" not in manifest:
            # Legacy source-run artifacts remain published until the first exact-SHA
            # 30-day package is produced by the automated workflow.
            self.assertEqual(manifest["android"]["source"], manifest["ios"]["source"])
            for platform, filename in (("android", "android.html"), ("ios", "ios-download.html")):
                build = manifest[platform]
                page = (WEB / filename).read_text(encoding="utf-8")
                artifact_url = (
                    "https://github.com/" + manifest["repository"] + "/actions/runs/"
                    + str(build["run"]) + "/artifacts/" + str(build["artifact"])
                )
                self.assertIn(artifact_url, page)
                self.assertIn(str(manifest["retentionDays"]) + "-day retention", page)
                self.assertIn(build["source"][:8], page)
                self.assertRegex(build["sha256"], r"^[0-9a-f]{64}$")
                self.assertIn("Artifact ZIP SHA-256: <code>" + build["sha256"] + "</code>", page)
            ios_page = (WEB / "ios-download.html").read_text(encoding="utf-8")
            walkthrough_url = (
                "https://github.com/" + manifest["repository"] + "/actions/runs/"
                + str(manifest["ios"]["walkthroughRun"])
            )
            self.assertIn(walkthrough_url, ios_page)
            self.assertIn("Both iPhone and iPad simulator walkthroughs passed", ios_page)
            android_page = (WEB / "android.html").read_text(encoding="utf-8")
            self.assertIn("no standalone <code>sha256.txt</code> is included", android_page.lower())
            self.assertIn("instrumentation-test APK, lint report and runtime diagnostics", android_page)
            return

        source = manifest["source"]
        self.assertRegex(source, r"^[0-9a-f]{40}$")
        self.assertEqual(manifest["android"]["source"], source)
        self.assertEqual(manifest["ios"]["source"], source)
        self.assertGreaterEqual(manifest["retentionDays"], 1)
        self.assertLessEqual(manifest["retentionDays"], 30)
        packaging_run_url = (
            "https://github.com/" + manifest["repository"] + "/actions/runs/"
            + str(manifest["packagingRun"])
        )
        self.assertEqual(manifest["packagingRunUrl"], packaging_run_url)
        for platform, filename, app_digest_key, app_label in (
            ("android", "android.html", "apkSha256", "APK"),
            ("ios", "ios-download.html", "ipaSha256", "IPA"),
        ):
            build = manifest[platform]
            page = (WEB / filename).read_text(encoding="utf-8")
            source_artifact_url = (
                "https://github.com/" + manifest["repository"] + "/actions/runs/"
                + str(build["run"])
            )
            packaged_artifact_url = packaging_run_url + "/artifacts/" + str(build["packagingArtifact"])
            self.assertEqual(build["source"], source)
            self.assertEqual(build["downloadUrl"], packaged_artifact_url)
            self.assertIn(packaged_artifact_url, page)
            self.assertIn(str(manifest["retentionDays"]) + "-day retention", page)
            self.assertIn(source_artifact_url, page)
            self.assertIn(source[:8], page)
            self.assertRegex(build["sha256"], r"^[0-9a-f]{64}$")
            self.assertIn("Artifact ZIP SHA-256: <code>" + build["sha256"] + "</code>", page)
            self.assertRegex(build[app_digest_key], r"^[0-9a-f]{64}$")
            self.assertIn(app_label + " SHA-256: <code>" + build[app_digest_key] + "</code>", page)

        ios_page = (WEB / "ios-download.html").read_text(encoding="utf-8")
        walkthrough_url = (
            "https://github.com/" + manifest["repository"] + "/actions/runs/"
            + str(manifest["ios"]["walkthroughRun"])
        )
        self.assertIn(walkthrough_url, ios_page)
        self.assertIn("Both iPhone and iPad simulator walkthroughs passed", ios_page)
        self.assertFalse(manifest["ios"]["signed"])
        self.assertTrue(manifest["ios"]["personalSigningRequired"])
        self.assertFalse(manifest["ios"]["icloudAvailable"])
        self.assertNotIn("later visual walkthrough attempt failed", ios_page.lower())
        self.assertNotIn("coverage remains under investigation", ios_page.lower())
        android_page = (WEB / "android.html").read_text(encoding="utf-8")
        self.assertIn("SHA256.txt is included", android_page)

    def test_download_manifest_matches_current_accepted_native_baseline(self):
        manifest = json.loads((WEB / "downloads.json").read_text(encoding="utf-8"))
        if "packagingRun" not in manifest:
            # Current baseline remains valid until a successful package replaces it.
            source = "35bf48ba9f43a4483becfaa36b66bc62ccdf1b8d"
            self.assertEqual(manifest["android"]["source"], source)
            self.assertEqual(manifest["ios"]["source"], source)
            self.assertEqual(manifest["android"]["run"], 41029928610)
            self.assertEqual(manifest["android"]["artifact"], 12965267288)
            self.assertEqual(manifest["android"]["sha256"], "e628856e9ec9d49c1269655dbdc2fe518135a14b40ae45f0b9747a6724cb7c2f")
            self.assertEqual(manifest["ios"]["run"], 41029928516)
            self.assertEqual(manifest["ios"]["artifact"], 12964814338)
            self.assertEqual(manifest["ios"]["sha256"], "a4e6e681b1c4fcffd5cd2cbe80a170746e000085c9218a0aac2633e256f63d27")
            self.assertEqual(manifest["ios"]["walkthroughRun"], 41029928637)
            return

        source = manifest["source"]
        self.assertRegex(source, r"^[0-9a-f]{40}$")
        self.assertEqual(manifest["android"]["source"], source)
        self.assertEqual(manifest["ios"]["source"], source)
        self.assertGreaterEqual(manifest["retentionDays"], 1)
        self.assertLessEqual(manifest["retentionDays"], 30)
        self.assertGreater(manifest["packagingRun"], 0)
        self.assertGreater(manifest["ios"]["walkthroughRun"], 0)
        self.assertTrue(manifest["android"]["downloadUrl"].endswith(
            "/artifacts/" + str(manifest["android"]["packagingArtifact"])
        ))
        self.assertTrue(manifest["ios"]["downloadUrl"].endswith(
            "/artifacts/" + str(manifest["ios"]["packagingArtifact"])
        ))
        self.assertFalse(manifest["ios"]["signed"])
        self.assertTrue(manifest["ios"]["personalSigningRequired"])


if __name__ == "__main__":
    unittest.main()
