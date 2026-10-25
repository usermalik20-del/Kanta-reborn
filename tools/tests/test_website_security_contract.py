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


if __name__ == "__main__":
    unittest.main()
