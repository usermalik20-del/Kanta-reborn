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
HOMEPAGE_CSP = {
    "default-src": frozenset({"'none'"}),
    "img-src": frozenset({"'self'"}),
    "script-src": frozenset({"'self'", "https://www.googletagmanager.com", "https://pagead2.googlesyndication.com"}),
    "style-src": frozenset({"'self'", "'unsafe-inline'"}),
    "connect-src": frozenset({"https://www.google-analytics.com"}),
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

    def test_homepage_uses_allowlisted_google_origins_without_inline_scripts(self):
        self.assertEqual(policy_for("/"), HOMEPAGE_CSP)
        self.assertNotIn("'unsafe-inline'", policy_for("/")["script-src"])

        html = (WEB / "index.html").read_text(encoding="utf-8")
        self.assertIn('<script defer src="/analytics.js"></script>', html)
        self.assertIsNone(re.search(r"<script\b(?![^>]*\bsrc\s*=)[^>]*>", html, re.IGNORECASE))

        analytics = (WEB / "analytics.js").read_text(encoding="utf-8")
        self.assertIn("G-L0QV01MWR4", analytics)
        self.assertIn("https://www.googletagmanager.com/gtag/js", analytics)
        self.assertNotIn("eval(", analytics)

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

if __name__ == "__main__":
    unittest.main()
