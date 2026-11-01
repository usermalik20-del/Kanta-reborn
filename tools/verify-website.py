#!/usr/bin/env python3
"""Check public-site routes, anchors, immutable reader assets and release metadata."""
import json
import re
from datetime import date
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / "apps/web"
PUBLIC_BASE_URL = "https://kanta-public.vercel.app"
ADS_DECLARATION = "google.com, pub-6290349358884298, DIRECT, f08c47fec0942fa0"
SITEMAP_NAMESPACE = "http://www.sitemaps.org/schemas/sitemap/0.9"
PUBLIC_ROUTES = {
    "/": "index.html",
    "/ios-download": "ios-download.html",
    "/android": "android.html",
    "/guide": "guide.html",
    "/features": "features.html",
    "/privacy": "privacy.html",
    "/legal": "legal.html",
    "/licenses": "licenses.html",
    "/support": "support.html",
    "/songbook": "songbook.html",
}
SITEMAP_CHANGEFREQS = {"always", "hourly", "daily", "weekly", "monthly", "yearly", "never"}

def validate_csp_config(config):
    routes = {rule.get("src"): rule.get("dest") for rule in config.get("routes", [])}
    assert routes.get("^/$") == "/api/page?route=%2F"
    for route in PUBLIC_ROUTES:
        if route == "/":
            continue
        assert routes.get("^" + route + "$") == "/api/page?route=%2F" + route.lstrip("/")
    assert config.get("functions", {}).get("api/page.js", {}).get("includeFiles") == "*.html"
    assert not any(
        header.get("key", "").lower() == "content-security-policy"
        for rule in config.get("headers", []) for header in rule.get("headers", [])
    ), "CSP must be generated per request by the page function"
    renderer = (WEB / "api/page.js").read_text(encoding="utf-8")
    assert "randomBytes(18)" in renderer and "'strict-dynamic'" in renderer
    assert 'route === "/songbook"' in renderer


class Page(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids = set()
        self.links = []
        self.errors = []
        self.scripts = []

    def handle_starttag(self, tag, attributes):
        attrs = dict(attributes)
        if tag == "script":
            self.scripts.append(attrs.get("src"))
        if "id" in attrs:
            if attrs["id"] in self.ids:
                self.errors.append("Duplicate ID: " + attrs["id"])
            self.ids.add(attrs["id"])
        for attribute in ("href", "src"):
            if attribute in attrs:
                self.links.append(attrs[attribute])


def validate_public_resources():
    ads_path = WEB / "ads.txt"
    uppercase_ads_path = WEB / "Ads.txt"
    assert ads_path.is_file(), "Missing lowercase public resource: apps/web/ads.txt"
    assert not uppercase_ads_path.exists(), (
        "Remove apps/web/Ads.txt; case-sensitive hosts publish it at the wrong URL"
    )
    assert ads_path.read_text(encoding="utf-8").strip() == ADS_DECLARATION, (
        "apps/web/ads.txt differs from the approved publisher declaration"
    )

    sitemap_path = WEB / "sitemap.xml"
    assert sitemap_path.is_file(), "Missing public sitemap: apps/web/sitemap.xml"
    root = ET.parse(sitemap_path).getroot()
    expected_root = f"{{{SITEMAP_NAMESPACE}}}urlset"
    assert root.tag == expected_root, "sitemap.xml must use the standard sitemap urlset namespace"

    url_tag = f"{{{SITEMAP_NAMESPACE}}}url"
    loc_tag = f"{{{SITEMAP_NAMESPACE}}}loc"
    lastmod_tag = f"{{{SITEMAP_NAMESPACE}}}lastmod"
    changefreq_tag = f"{{{SITEMAP_NAMESPACE}}}changefreq"
    priority_tag = f"{{{SITEMAP_NAMESPACE}}}priority"
    entries = root.findall(url_tag)
    locations = []

    for entry in entries:
        loc_nodes = entry.findall(loc_tag)
        assert len(loc_nodes) == 1 and (loc_nodes[0].text or "").strip(), (
            "Each sitemap URL must have exactly one non-empty loc"
        )
        location = loc_nodes[0].text.strip()
        parsed = urlsplit(location)
        assert (
            parsed.scheme == "https"
            and parsed.netloc == "kanta-public.vercel.app"
            and not parsed.query
            and not parsed.fragment
            and not parsed.username
            and not parsed.password
        ), ("Non-canonical sitemap URL", location)
        assert parsed.path in PUBLIC_ROUTES, ("Unknown sitemap route", location)
        assert location == PUBLIC_BASE_URL + parsed.path, ("Non-canonical sitemap URL", location)
        locations.append(location)

        lastmod_nodes = entry.findall(lastmod_tag)
        assert len(lastmod_nodes) == 1 and (lastmod_nodes[0].text or "").strip(), (
            "Each sitemap URL must have one lastmod date"
        )
        date.fromisoformat(lastmod_nodes[0].text.strip())

        changefreq_nodes = entry.findall(changefreq_tag)
        assert len(changefreq_nodes) == 1 and (changefreq_nodes[0].text or "").strip() in SITEMAP_CHANGEFREQS, (
            "Each sitemap URL must have a valid changefreq"
        )

        priority_nodes = entry.findall(priority_tag)
        assert len(priority_nodes) == 1, "Each sitemap URL must have one priority"
        priority = float(priority_nodes[0].text or "")
        assert 0.0 <= priority <= 1.0, ("Invalid sitemap priority", location, priority)

    expected_locations = {PUBLIC_BASE_URL + route for route in PUBLIC_ROUTES}
    assert len(locations) == len(set(locations)), "sitemap.xml contains duplicate URLs"
    assert set(locations) == expected_locations, (
        "sitemap.xml must contain exactly the ten canonical public routes",
        sorted(expected_locations - set(locations)),
        sorted(set(locations) - expected_locations),
    )



def validate_navigation_contract(source, name):
    header_match = re.search(r"<header\b[\s\S]*?</header>", source, re.IGNORECASE)
    assert header_match, (name, "missing shared site header")
    header = header_match.group(0)
    for marker in (
        'class="site-header"',
        'class="brand"',
        'aria-label="KANTA home"',
    ):
        assert marker in header, (name, "missing shared navigation contract: " + marker)

    nav = re.search(r"<nav\b([^>]*)>([\s\S]*?)</nav>", header, re.IGNORECASE)
    assert nav, (name, "missing primary navigation")
    attributes, body = nav.groups()
    class_match = re.search(r'\bclass\s*=\s*["\']([^"\']+)["\']', attributes, re.IGNORECASE)
    assert class_match, (name, "primary navigation has no class")
    classes = set(class_match.group(1).split())
    assert classes.intersection({"site-nav", "nav-links"}), (
        name, "primary navigation must use a supported shared class", sorted(classes)
    )
    assert re.search(r'\baria-label\s*=\s*["\']Main navigation["\']', attributes, re.IGNORECASE), (
        name, "primary navigation must have an accessible name"
    )

    expected_links = {
        "/ios-download", "/android", "/guide", "/features", "/songbook", "/support",
    }
    links = re.findall(r'<a\b[^>]*\bhref=["\']([^"\']+)["\']', body, re.IGNORECASE)
    assert len(links) == len(set(links)) and set(links) == expected_links, (
        name, "primary navigation routes differ from the shared contract", links
    )
    assert not re.search(r"(<h3>[^<]+</h3>)\s*\1", source, re.IGNORECASE), (
        name, "adjacent duplicate feature heading"
    )


def validate_responsive_navigation_styles():
    source = (WEB / "style.css").read_text(encoding="utf-8")
    nav_rule = re.search(
        r"(?:^|})\s*nav\s*,\s*\.site-nav\s*,\s*\.nav-links\s*\{([^}]*)\}",
        source, re.IGNORECASE | re.DOTALL
    )
    assert nav_rule, "Shared navigation selectors must have a common style rule"
    declarations = nav_rule.group(1)
    assert re.search(r"\bdisplay\s*:\s*flex\b", declarations, re.IGNORECASE), (
        "Primary navigation must remain a flex layout"
    )
    assert re.search(r"\bflex-wrap\s*:\s*wrap\b", declarations, re.IGNORECASE), (
        "Primary navigation must wrap instead of overflowing narrow viewports"
    )

    for selector, declarations in re.findall(r"([^{}]+)\{([^{}]*)\}", source, re.DOTALL):
        if re.search(r"(?<![\w-])(?:\.site-nav|\.nav-links)\b", selector):
            assert not re.search(r"\bdisplay\s*:\s*none\b", declarations, re.IGNORECASE), (
                "Primary navigation must not be hidden without an accessible replacement",
                selector.strip()
            )
    assert not re.search(r"(?m)^\s*ap:anywhere\}\s*$", source), (
        "Stylesheet must not contain the orphaned ap:anywhere fragment"
    )


def parse_robots_groups(source):
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


def validate_crawler_policy():
    """robots.txt, vercel.json and the page handler must express one policy."""
    robots_path = WEB / "robots.txt"
    assert robots_path.is_file(), "Missing public crawler policy: apps/web/robots.txt"
    robots = robots_path.read_text(encoding="utf-8")
    groups = parse_robots_groups(robots)

    assert "Sitemap: " + PUBLIC_BASE_URL + "/sitemap.xml" in robots, (
        "robots.txt must advertise the canonical sitemap on " + PUBLIC_BASE_URL
    )
    assert re.search(r"(?m)^Content-Signal:\s*search=yes", robots), (
        "robots.txt must declare a search-allowed Content-Signal"
    )
    assert re.search(r"ai-train=no", robots), "robots.txt must declare ai-train=no"

    # Search engines must stay allowed; blocking them would remove the site from
    # organic results, which is the opposite of the intended policy.
    for agent in ("*", "googlebot", "bingbot", "duckduckbot", "applebot", "facebookexternalhit"):
        assert agent in groups, "robots.txt is missing a group for " + agent
        assert ("disallow", "/") not in groups[agent], (
            "robots.txt must not disallow all crawling for " + agent
        )

    for agent in ("gptbot", "claudebot", "anthropic-ai", "ccbot", "bytespider",
                  "meta-externalagent", "amazonbot", "cohere-ai", "diffbot",
                  "imagesiftbot", "omgilibot", "timpibot", "youbot"):
        assert agent in groups, "robots.txt is missing an AI crawler group for " + agent
        assert ("disallow", "/") in groups[agent], (
            "robots.txt must disallow crawling for " + agent
        )

    # Comments are stripped above, so the sentinel paragraph must survive as a comment block.
    assert "not permitted" in robots, "robots.txt must state the policy in prose"

    blocked = {token for token, rules in groups.items() if ("disallow", "/") in rules}
    handler = (WEB / "api/page.js").read_text(encoding="utf-8")
    handler_block = re.search(r"const BLOCKED_USER_AGENT_TOKENS = \[([^\]]*)\];", handler)
    assert handler_block, "api/page.js must declare BLOCKED_USER_AGENT_TOKENS"
    handler_tokens = {token.lower() for token in re.findall(r'"([^"]+)"', handler_block.group(1))}
    robots_only = re.search(r"const ROBOTS_ONLY_TOKENS = \[([^\]]*)\];", handler)
    assert robots_only, "api/page.js must declare ROBOTS_ONLY_TOKENS"
    robots_only_tokens = {token.lower() for token in re.findall(r'"([^"]+)"', robots_only.group(1))}

    assert handler_tokens, "api/page.js declares an empty crawler block list"
    assert not (handler_tokens & {"googlebot", "bingbot", "duckduckbot", "applebot"}), (
        "api/page.js must not block search-engine user agents"
    )
    missing_in_handler = (blocked - robots_only_tokens) - handler_tokens
    assert not missing_in_handler, (
        "api/page.js does not enforce robots.txt block tokens", sorted(missing_in_handler)
    )
    undeclared = handler_tokens - blocked
    assert not undeclared, (
        "api/page.js blocks tokens that robots.txt does not declare", sorted(undeclared)
    )
    assert 'isBlockedCrawler' in handler and "crawlerGate(req)" in handler, (
        "api/page.js must apply the crawler gate before rendering"
    )

    config = json.loads((WEB / "vercel.json").read_text(encoding="utf-8"))
    header_values = [
        header.get("value", "")
        for rule in config.get("headers", [])
        for header in rule.get("headers", [])
        if header.get("key", "").lower() == "x-robots-tag"
    ]
    assert header_values, "vercel.json must publish an X-Robots-Tag crawler preference"
    assert all(rule.get("source") == "/(.*)" for rule, header in (
        (rule, header) for rule in config.get("headers", []) for header in rule.get("headers", [])
        if header.get("key", "").lower() == "x-robots-tag"
    )), "X-Robots-Tag must apply to every path"
    combined = " ".join(header_values).lower()
    assert "noai" in combined and "noimageai" in combined, (
        "X-Robots-Tag must declare the noai and noimageai preferences"
    )
    assert "noindex" not in combined, (
        "X-Robots-Tag must not deindex the public site; use robots.txt groups instead"
    )
    # Observed on the 2026-11-01 production deployment: the vercel.json headers rule
    # reached /style.css and /robots.txt but not the rewritten HTML routes, so the
    # renderer has to set the preference on its own response. Scope the check to the
    # handler body: the refusal path sets the same header, so an unscoped search would
    # pass even with the rendered-page header removed.
    handler_body = handler[handler.index("async function handler"):]
    assert 'res.setHeader("X-Robots-Tag", "noai, noimageai")' in handler_body, (
        "api/page.js must set X-Robots-Tag on rendered responses; the vercel.json rule "
        "does not reach a rewritten function response"
    )
    assert handler_body.index('res.setHeader("X-Robots-Tag"') < handler_body.index("res.statusCode = 200"), (
        "api/page.js must set X-Robots-Tag before returning the rendered page"
    )

    edge_pattern = ""
    for rule in config.get("routes", []):
        status = rule.get("status")
        if status is None:
            continue
        assert status >= 400, ("Only refusal rules may set a status", status)
        conditions = [
            condition for condition in rule.get("has", [])
            if condition.get("type") == "header"
            and condition.get("key", "").lower() == "user-agent"
            and condition.get("value")
        ]
        assert conditions, (
            "A route status rule without a user-agent condition would refuse every visitor"
        )
        edge_pattern += " " + " ".join(condition["value"] for condition in conditions)
    assert edge_pattern, "vercel.json must refuse declared AI crawlers at the edge"
    edge_lower = edge_pattern.lower()
    for token in sorted(handler_tokens):
        assert token in edge_lower, (
            "vercel.json edge rule does not cover a handler-blocked token", token
        )
    assert "(?i)" in edge_pattern, "The edge user-agent pattern must match case-insensitively"


def main():
    pages = {}
    for path in WEB.glob("*.html"):
        page = Page()
        source = path.read_text(encoding="utf-8")
        page.feed(source)
        assert not page.errors, (path.name, page.errors)
        assert '<html lang="en"' in source and 'name="viewport"' in source, path.name
        assert "<title>" in source and 'id="content"' in source, path.name
        assert "github.com/kanta-ph/" not in source, path.name
        assert "github.com/kanta-gelo/" not in source, path.name
        validate_navigation_contract(source, path.name)
        pages[path.name] = page

    assert set(pages) == set(PUBLIC_ROUTES.values()), (
        "Public HTML pages differ from the canonical route map",
        sorted(set(PUBLIC_ROUTES.values()) - set(pages)),
        sorted(set(pages) - set(PUBLIC_ROUTES.values())),
    )

    for name, page in pages.items():
        for link in page.links:
            url = urlsplit(link)
            if url.scheme or url.netloc:
                assert url.scheme in ("https", "mailto"), (name, link)
                continue
            destination = url.path.lstrip("/") if url.path else name
            if not destination:
                destination = "index.html"
            elif not Path(destination).suffix:
                destination += ".html"
            assert (WEB / destination).is_file(), (name, link)
            if url.fragment:
                assert destination in pages and url.fragment in pages[destination].ids, (name, link)

    original = ROOT / "docs/evidence/website/2026-10-15"
    for name in ("songbook.js", "songbook.css"):
        assert (WEB / name).read_bytes() == (original / name).read_bytes(), name + " reader changed"

    validate_public_resources()
    validate_responsive_navigation_styles()
    validate_crawler_policy()

    config = json.loads((WEB / "vercel.json").read_text(encoding="utf-8"))
    validate_csp_config(config)
    assert (WEB / "analytics.js").is_file(), "Missing same-origin analytics initializer"
    assert (WEB / "site-consent.js").is_file(), "Missing website consent controller"
    assert (WEB / "site-consent.css").is_file(), "Missing website consent styles"

    manifest = json.loads((WEB / "downloads.json").read_text(encoding="utf-8"))
    assert manifest["repository"] == "usermalik20-del/Kanta-reborn"
    if "packagingRun" in manifest:
        assert manifest["signInRequired"] is True and 1 <= manifest["retentionDays"] <= 30
        source = manifest["source"]
        assert re.fullmatch(r"[0-9a-f]{40}", source), "Download manifest source SHA is malformed"
        packaging_run_url = "https://github.com/" + manifest["repository"] + "/actions/runs/" + str(manifest["packagingRun"])
        assert manifest["packagingRunUrl"] == packaging_run_url
        for platform, filename, app_digest_key, app_label in (
            ("android", "android.html", "apkSha256", "APK"),
            ("ios", "ios-download.html", "ipaSha256", "IPA"),
        ):
            build = manifest[platform]
            platform_source = build["source"]
            assert re.fullmatch(r"[0-9a-f]{40}", platform_source), filename + " source SHA is malformed"
            platform_run_url = "https://github.com/" + manifest["repository"] + "/actions/runs/" + str(build["packagingRun"])
            source_artifact_url = "https://github.com/" + manifest["repository"] + "/actions/runs/" + str(build["run"])
            packaged_artifact_url = platform_run_url + "/artifacts/" + str(build["packagingArtifact"])
            page_source = (WEB / filename).read_text(encoding="utf-8")
            assert build["downloadUrl"] == packaged_artifact_url, filename + " packaged artifact differs from manifest"
            assert packaged_artifact_url in page_source, filename + " packaged artifact link differs from manifest"
            assert str(build["retentionDays"]) + "-day retention" in page_source, filename + " published retention differs from manifest"
            assert source_artifact_url in page_source, filename + " source artifact provenance link is missing"
            assert platform_source[:8] in page_source, filename + " source revision differs from manifest"
            assert re.fullmatch(r"[0-9a-f]{64}", build["sha256"]), filename + " packaged ZIP digest is malformed"
            assert "Artifact ZIP SHA-256: <code>" + build["sha256"] + "</code>" in page_source, filename + " packaged ZIP digest differs from manifest"
            assert re.fullmatch(r"[0-9a-f]{64}", build[app_digest_key]), filename + " application checksum is malformed"
            assert app_label + " SHA-256: <code>" + build[app_digest_key] + "</code>" in page_source, filename + " application checksum differs from manifest"
        assert manifest["ios"]["signed"] is False and manifest["ios"]["personalSigningRequired"]
        assert not manifest["ios"]["icloudAvailable"]
        ios_page = (WEB / "ios-download.html").read_text(encoding="utf-8")
        walkthrough_url = "https://github.com/" + manifest["repository"] + "/actions/runs/" + str(manifest["ios"]["walkthroughRun"])
        assert walkthrough_url in ios_page, "iOS download page must link to the recorded simulator walkthrough"
        assert "Both iPhone and iPad simulator walkthroughs passed" in ios_page, "iOS page must state the verified simulator result"
        assert "later visual walkthrough attempt failed" not in ios_page.lower(), "Stale failed-walkthrough claim must not be published"
        assert "coverage remains under investigation" not in ios_page.lower(), "Stale walkthrough investigation claim must not be published"
        android_page = (WEB / "android.html").read_text(encoding="utf-8")
        assert "SHA256.txt is included" in android_page, "Android bundle checksum contents must be described accurately"
    else:
        # Accept the currently published seven-day source-artifact manifest until
        # the first automated 30-day packaged bundle is successfully published.
        assert manifest["signInRequired"] is True and manifest["retentionDays"] == 7
        for platform, filename in (("android", "android.html"), ("ios", "ios-download.html")):
            build = manifest[platform]
            expected = "https://github.com/" + manifest["repository"] + "/actions/runs/" + str(build["run"]) + "/artifacts/" + str(build["artifact"])
            page_source = (WEB / filename).read_text(encoding="utf-8")
            assert expected in page_source, filename + " artifact differs from manifest"
            assert str(manifest["retentionDays"]) + "-day retention" in page_source, filename + " published retention differs from manifest"
            assert build["source"][:8] in page_source, filename + " source revision differs from manifest"
            assert re.fullmatch(r"[0-9a-f]{64}", build["sha256"]), filename + " artifact ZIP digest is malformed"
            assert "Artifact ZIP SHA-256: <code>" + build["sha256"] + "</code>" in page_source, filename + " artifact ZIP digest differs from manifest"
        assert manifest["android"]["source"] == manifest["ios"]["source"], "Platform downloads must describe the same accepted source revision"
        assert manifest["ios"]["signed"] is False and manifest["ios"]["personalSigningRequired"]
        assert not manifest["ios"]["icloudAvailable"]
        ios_page = (WEB / "ios-download.html").read_text(encoding="utf-8")
        walkthrough_url = "https://github.com/" + manifest["repository"] + "/actions/runs/" + str(manifest["ios"]["walkthroughRun"])
        assert walkthrough_url in ios_page, "iOS download page must link to the recorded simulator walkthrough"
        assert "Both iPhone and iPad simulator walkthroughs passed" in ios_page, "iOS page must state the verified device result"
        assert "later visual walkthrough attempt failed" not in ios_page.lower(), "Stale failed-walkthrough claim must not be published"
        assert "coverage remains under investigation" not in ios_page.lower(), "Stale walkthrough investigation claim must not be published"
        android_page = (WEB / "android.html").read_text(encoding="utf-8")
        assert "no standalone <code>sha256.txt</code> is included" in android_page.lower(), "Android artifact contents must be described accurately"
    print(
        "10 public pages, local routes/anchors, lowercase ads.txt, canonical sitemap, "
        "route-scoped CSP, declared crawler policy, immutable Songbook reader and "
        "verified download provenance passed"
    )


if __name__ == "__main__":
    main()
