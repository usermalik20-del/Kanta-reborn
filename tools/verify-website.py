#!/usr/bin/env python3
"""Check public-site routes, anchors, immutable reader assets and release metadata."""
import json
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

STRICT_CSP_DIRECTIVES = {
    "default-src": frozenset({"'none'"}),
    "img-src": frozenset({"'self'"}),
    "script-src": frozenset({"'self'"}),
    "style-src": frozenset({"'self'", "'unsafe-inline'"}),
    "connect-src": frozenset({"'none'"}),
    "base-uri": frozenset({"'none'"}),
    "form-action": frozenset({"'none'"}),
    "frame-ancestors": frozenset({"'none'"}),
}
HOMEPAGE_CSP_DIRECTIVES = {
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
            raise ValueError("Duplicate Content-Security-Policy directive: " + name)
        directives[name] = frozenset(sources)
    return directives


def validate_csp_config(config):
    rules = config.get("headers", [])
    csp_key = "content-security-policy"
    csp_rules = [
        rule for rule in rules
        if any(header.get("key", "").lower() == csp_key for header in rule.get("headers", []))
    ]
    assert len(csp_rules) == len(PUBLIC_ROUTES), (
        "Every public route must have exactly one explicit CSP rule",
        [rule.get("source") for rule in csp_rules],
    )
    sources = [rule.get("source") for rule in csp_rules]
    assert len(sources) == len(set(sources)), ("Duplicate route-specific CSP rules", sources)
    assert set(sources) == set(PUBLIC_ROUTES), ("CSP routes differ from the public route map", sources)

    wildcard_rules = [rule for rule in rules if rule.get("source") == "/(.*)"]
    assert len(wildcard_rules) == 1, "Expected one wildcard rule for non-CSP security headers"
    assert not any(
        header.get("key", "").lower() == csp_key
        for header in wildcard_rules[0].get("headers", [])
    ), "The wildcard rule must not broaden the Content-Security-Policy"

    for rule in csp_rules:
        policy_values = [
            header["value"] for header in rule.get("headers", [])
            if header.get("key", "").lower() == csp_key
        ]
        assert len(policy_values) == 1, ("Route must have one CSP value", rule.get("source"))
        expected = HOMEPAGE_CSP_DIRECTIVES if rule["source"] == "/" else STRICT_CSP_DIRECTIVES
        parsed = parse_csp(policy_values[0])
        assert parsed == expected, ("Unexpected Content-Security-Policy", rule["source"], parsed)


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

    config = json.loads((WEB / "vercel.json").read_text(encoding="utf-8"))
    validate_csp_config(config)
    assert "/analytics.js" in pages["index.html"].scripts, "Homepage analytics must use the same-origin script"
    assert all(pages["index.html"].scripts), "Homepage inline scripts bypass the route-specific CSP"
    assert (WEB / "analytics.js").is_file(), "Missing same-origin analytics initializer"

    manifest = json.loads((WEB / "downloads.json").read_text(encoding="utf-8"))
    assert manifest["repository"] == "usermalik20-del/Kanta-reborn"
    assert manifest["signInRequired"] is True and manifest["retentionDays"] == 7
    for platform, filename in (("android", "android.html"), ("ios", "ios-download.html")):
        build = manifest[platform]
        expected = "https://github.com/" + manifest["repository"] + "/actions/runs/" + str(build["run"]) + "/artifacts/" + str(build["artifact"])
        assert expected in (WEB / filename).read_text(encoding="utf-8"), filename + " artifact differs from manifest"
    assert manifest["ios"]["signed"] is False and manifest["ios"]["personalSigningRequired"]
    assert not manifest["ios"]["icloudAvailable"]
    assert manifest["android"]["source"][:8] in (WEB / "android.html").read_text(encoding="utf-8")
    assert manifest["ios"]["source"][:8] in (WEB / "ios-download.html").read_text(encoding="utf-8")
    print(
        "10 public pages, local routes/anchors, lowercase ads.txt, canonical sitemap, "
        "route-scoped CSP, immutable Songbook reader and verified download provenance passed"
    )


if __name__ == "__main__":
    main()
