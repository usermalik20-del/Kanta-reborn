"""Website nonce, consent, advertising and native-isolation contracts."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "apps/web"
CONFIG = json.loads((WEB / "vercel.json").read_text(encoding="utf-8"))
ROUTES = {"/", "/ios-download", "/android", "/guide", "/features", "/privacy", "/legal", "/licenses", "/support", "/songbook"}

class WebsiteSecurityContractTests(unittest.TestCase):
    def test_all_html_routes_use_request_time_renderer(self):
        routes = {r["src"]: r["dest"] for r in CONFIG["routes"]}
        self.assertEqual(routes["^/$"], "/api/page?route=%2F")
        for route in ROUTES - {"/"}:
            self.assertIn("^" + route + "$", routes)
        self.assertEqual(CONFIG["functions"]["api/page.js"]["includeFiles"], "*.html")

    def test_nonce_renderer_and_songbook_exclusion(self):
        source = (WEB / "api/page.js").read_text(encoding="utf-8")
        self.assertIn("randomBytes(18)", source)
        self.assertIn("makeCsp(nonce)", source)
        self.assertIn('route === "/songbook"', source)
        self.assertIn("private, no-store", source)
        self.assertIn("'strict-dynamic'", source)
        self.assertIn("object-src 'none'", source)

    def test_google_tools_are_consent_gated_and_website_only(self):
        consent = (WEB / "site-consent.js").read_text(encoding="utf-8")
        self.assertIn('readConsent() !== "accepted"', consent)
        self.assertIn("G-L0QV01MWR4", (WEB / "analytics.js").read_text(encoding="utf-8"))
        self.assertIn("ca-pub-6290349358884298", consent)
        self.assertIn("data-consent=\"reject\"", (WEB / "api/page.js").read_text(encoding="utf-8"))
        self.assertTrue((WEB / "ads.txt").is_file())

    def test_native_app_privacy_excludes_website_scripts(self):
        ios = (ROOT / "apps/ios/PRIVACY.md").read_text(encoding="utf-8")
        android = (ROOT / "apps/android/app/src/main/assets/privacy.txt").read_text(encoding="utf-8")
        self.assertIn("not either native app", ios)
        self.assertIn("not bundled with or executed by this Android app", android)

    def test_private_songbook_does_not_load_google_tools(self):
        page = (WEB / "songbook.html").read_text(encoding="utf-8")
        self.assertNotIn("adsbygoogle", page)
        self.assertNotIn("analytics.js", page)

    def test_every_public_page_has_shared_navigation(self):
        for route in ROUTES:
            filename = "index.html" if route == "/" else route.lstrip("/") + ".html"
            source = (WEB / filename).read_text(encoding="utf-8")
            self.assertIn('class="site-header"', source, filename)
            self.assertIn('aria-label="Main navigation"', source, filename)

if __name__ == "__main__":
    unittest.main()
