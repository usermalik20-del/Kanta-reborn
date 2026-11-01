"""Website nonce, consent, advertising, crawler-policy and native-isolation contracts."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
WEB = ROOT / "apps/web"
CONFIG = json.loads((WEB / "vercel.json").read_text(encoding="utf-8"))
ROUTES = {"/", "/ios-download", "/android", "/guide", "/features", "/privacy", "/legal", "/licenses", "/support", "/songbook"}
PAGE_SOURCE = (WEB / "api/page.js").read_text(encoding="utf-8")
ROBOTS_SOURCE = (WEB / "robots.txt").read_text(encoding="utf-8")


def robots_groups(source):
    """Return {agent token: set of (field, value)} for blank-line separated groups."""
    groups = {}
    agents = []
    directives = []
    for raw in source.splitlines() + [""]:
        line = raw.split("#", 1)[0].strip()
        if not line:
            for agent in agents:
                groups.setdefault(agent, set()).update(directives)
            agents, directives = [], []
            continue
        field, _, value = line.partition(":")
        field = field.strip().lower()
        value = value.strip()
        if field == "user-agent":
            if directives:
                for agent in agents:
                    groups.setdefault(agent, set()).update(directives)
                agents, directives = [], []
            agents.append(value.lower())
        elif field in ("allow", "disallow"):
            directives.append((field, value))
    return groups


def declared_tokens(source, name):
    match = re.search(r"const " + name + r" = \[([^\]]*)\];", source)
    assert match, "api/page.js must declare " + name
    return {token.lower() for token in re.findall(r'"([^"]+)"', match.group(1))}


class WebsiteSecurityContractTests(unittest.TestCase):
    def test_all_html_routes_use_request_time_renderer(self):
        rewrites = {r["src"]: r["dest"] for r in CONFIG["routes"] if "dest" in r}
        self.assertEqual(rewrites["^/$"], "/api/page?route=%2F")
        for route in ROUTES - {"/"}:
            self.assertIn("^" + route + "$", rewrites)
        self.assertEqual(CONFIG["functions"]["api/page.js"]["includeFiles"], "*.html")

    def test_nonce_renderer_and_songbook_exclusion(self):
        source = PAGE_SOURCE
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
        self.assertIn("data-consent=\"reject\"", PAGE_SOURCE)
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

    def test_robots_txt_declares_a_crawlable_sitemap_and_ai_opt_out(self):
        groups = robots_groups(ROBOTS_SOURCE)
        self.assertIn("Sitemap: https://kanta-public.vercel.app/sitemap.xml", ROBOTS_SOURCE)
        self.assertRegex(ROBOTS_SOURCE, r"(?m)^Content-Signal:\s*search=yes")
        self.assertIn("ai-train=no", ROBOTS_SOURCE)
        self.assertIn("ai-input=no", ROBOTS_SOURCE)
        # A "Disallow: /" in the fallback group would remove the site from search.
        for agent in ("*", "googlebot", "bingbot", "duckduckbot", "applebot"):
            self.assertIn(agent, groups, agent)
            self.assertNotIn(("disallow", "/"), groups[agent], agent)
        self.assertIn(("disallow", "/api/"), groups["*"])
        for agent in ("gptbot", "claudebot", "ccbot", "bytespider", "meta-externalagent",
                      "amazonbot", "cohere-ai", "diffbot", "imagesiftbot", "omgilibot",
                      "timpibot", "youbot", "anthropic-ai"):
            self.assertIn(agent, groups, agent)
            self.assertIn(("disallow", "/"), groups[agent], agent)

    def test_declared_block_lists_agree_across_robots_edge_and_handler(self):
        groups = robots_groups(ROBOTS_SOURCE)
        blocked = {token for token, rules in groups.items() if ("disallow", "/") in rules}
        handler_tokens = declared_tokens(PAGE_SOURCE, "BLOCKED_USER_AGENT_TOKENS")
        robots_only = declared_tokens(PAGE_SOURCE, "ROBOTS_ONLY_TOKENS")

        self.assertTrue(handler_tokens)
        # Tokens such as Google-Extended are robots.txt controls, not user agents.
        self.assertTrue(robots_only <= blocked, sorted(robots_only - blocked))
        self.assertEqual(handler_tokens, blocked - robots_only)
        self.assertFalse(handler_tokens & {"googlebot", "bingbot", "duckduckbot", "applebot"})

        edge_pattern = " ".join(
            condition["value"]
            for rule in CONFIG["routes"]
            for condition in rule.get("has", [])
            if condition.get("type") == "header"
            and condition.get("key", "").lower() == "user-agent"
        )
        edge_lower = edge_pattern.lower()
        self.assertTrue(edge_pattern, "vercel.json must refuse declared AI crawlers at the edge")
        for token in sorted(handler_tokens):
            self.assertIn(token, edge_lower)

    def test_crawler_gate_runs_before_rendering_and_cannot_break_a_page_view(self):
        self.assertIn("crawlerGate(req)", PAGE_SOURCE)
        self.assertLess(
            PAGE_SOURCE.index("crawlerGate(req)"),
            PAGE_SOURCE.index("fs.readFile"),
            "The crawler gate must run before the page is read from disk",
        )
        gate = PAGE_SOURCE[PAGE_SOURCE.index("function isBlockedCrawler"):PAGE_SOURCE.index("function crawlerGate")]
        self.assertIn("typeof userAgent !== \"string\"", gate)
        self.assertIn("!normalized.trim()", gate)
        # Fail open: a classification error must not deny a normal visitor.
        self.assertIn("catch (error)", PAGE_SOURCE)
        refusal = PAGE_SOURCE[PAGE_SOURCE.index("function refuseCrawler"):PAGE_SOURCE.index("const CONSENT_UI")]
        self.assertIn("403", refusal)
        self.assertIn("noai, noimageai", refusal)

    def test_x_robots_tag_is_set_by_both_the_config_and_the_renderer(self):
        values = [
            header["value"]
            for rule in CONFIG["headers"]
            for header in rule["headers"]
            if header.get("key", "").lower() == "x-robots-tag"
        ]
        self.assertTrue(values, "vercel.json must publish an X-Robots-Tag preference")
        combined = " ".join(values).lower()
        self.assertIn("noai", combined)
        self.assertIn("noimageai", combined)
        self.assertNotIn("noindex", combined, "X-Robots-Tag must not deindex the public site")
        # A rewrite to the function replaces the response, so the config rule alone is
        # not enough: the rendered response must set the header itself.
        rendered = PAGE_SOURCE[PAGE_SOURCE.index("async function handler"):]
        self.assertIn(
            'res.setHeader("X-Robots-Tag", "noai, noimageai")',
            rendered,
            "Rendered responses must set X-Robots-Tag; the vercel.json rule does not reach them",
        )
        self.assertLess(
            rendered.index('res.setHeader("X-Robots-Tag"'),
            rendered.index("res.statusCode = 200"),
            "The rendered preference must be set before the page is returned",
        )

    def test_every_status_route_rule_requires_a_user_agent_condition(self):
        for rule in CONFIG["routes"]:
            if "status" not in rule:
                continue
            self.assertGreaterEqual(rule["status"], 400)
            conditions = [
                condition for condition in rule.get("has", [])
                if condition.get("type") == "header"
                and condition.get("key", "").lower() == "user-agent"
                and condition.get("value")
            ]
            self.assertTrue(
                conditions,
                "A route status rule without a user-agent condition would refuse every visitor",
            )
            self.assertIn("(?i)", conditions[0]["value"], "User-agent matching must ignore case")


if __name__ == "__main__":
    unittest.main()
