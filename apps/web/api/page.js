"use strict";

const fs = require("node:fs/promises");
const path = require("node:path");
const { randomBytes } = require("node:crypto");

const PAGES = {
  "/": "index.html",
  "/ios-download": "ios-download.html",
  "/android": "android.html",
  "/guide": "guide.html",
  "/features": "features.html",
  "/privacy": "privacy.html",
  "/legal": "legal.html",
  "/licenses": "licenses.html",
  "/support": "support.html",
  "/about": "about.html",
  "/songbook": "songbook.html",
  // Blog routes are also dispatched to this function by vercel.json. Keep every
  // blog entry in vercel.json routes in sync with this map: a route whose dest is
  // /api/page but which is absent here returns 404 in production.
  "/blog/all": "blog/all.html",
  "/blog/category/file-formats": "blog/category/file-formats/index.html",
  "/blog/category/soundfonts": "blog/category/soundfonts/index.html",
  "/blog/category/stage-setup": "blog/category/stage-setup/index.html",
  "/blog/category/vocal-practice": "blog/category/vocal-practice/index.html",
  "/blog/category/library-management": "blog/category/library-management/index.html",
  "/blog/category/technical-deep-dives": "blog/category/technical-deep-dives/index.html",
  "/blog/midi-kar-mp3-wav-karaoke-file-formats-explained": "blog/midi-kar-mp3-wav-karaoke-file-formats-explained.html",
  "/blog/choosing-the-right-soundfont-for-karaoke": "blog/choosing-the-right-soundfont-for-karaoke.html",
  "/blog/building-your-personal-karaoke-library": "blog/building-your-personal-karaoke-library.html",
  "/blog/spice-pitch-estimation-how-it-works": "blog/spice-pitch-estimation-how-it-works.html",
  "/blog/vocal-practice-techniques-pitch-training": "blog/vocal-practice-techniques-pitch-training.html",
  "/blog/converting-karaoke-files-ffmpeg-tools": "blog/converting-karaoke-files-ffmpeg-tools.html",
  "/blog/soundfont-bank-size-vs-quality": "blog/soundfont-bank-size-vs-quality.html",
  "/blog/karaoke-songbook-export-sharing-privacy": "blog/karaoke-songbook-export-sharing-privacy.html",
  "/blog/external-display-hdmi-airplay-karaoke": "blog/external-display-hdmi-airplay-karaoke.html",
  "/blog/microphone-selection-karaoke-recording": "blog/microphone-selection-karaoke-recording.html",
  "/blog/tinysoundfont-android-midi-engine": "blog/tinysoundfont-android-midi-engine.html",
  "/blog/karaoke-party-effects-intermission-music": "blog/karaoke-party-effects-intermission-music.html",
  "/blog/organizing-karaoke-files-metadata-tagging": "blog/organizing-karaoke-files-metadata-tagging.html",
  "/blog/pitch-correction-vs-pitch-training": "blog/pitch-correction-vs-pitch-training.html",
  "/blog/encrypted-companion-control-android": "blog/encrypted-companion-control-android.html"
};
// Crawler policy enforced on the rendered-page path. Keep in sync with
// apps/web/robots.txt: the block list below must be exactly the real
// user-agent tokens of the robots.txt block group, minus ROBOTS_ONLY_TOKENS
// (tokens such as Google-Extended and Applebot-Extended are robots.txt
// controls, not user agents that request a page).
const BLOCKED_USER_AGENT_TOKENS = [
  "gptbot",
  "claudebot",
  "anthropic-ai",
  "ccbot",
  "bytespider",
  "meta-externalagent",
  "meta-webindexer",
  "amazonbot",
  "cohere-ai",
  "cohere-training-data-crawler",
  "diffbot",
  "imagesiftbot",
  "omgilibot",
  "timpibot",
  "webzio-extended",
  "youbot",
  "dataforseobot",
  "scrapy"
];
const ROBOTS_ONLY_TOKENS = ["google-extended", "applebot-extended"];
const CRAWLER_BLOCK_BODY =
  "Automated access for model training or bulk indexing is not permitted. " +
  "See https://kanta-public.vercel.app/robots.txt";

// Returns true only for a user agent that matches the declared block list.
// An absent or empty user agent is not blocked: ordinary tools send no user
// agent-name match, and a false positive would take the public site down.
function isBlockedCrawler(userAgent) {
  if (typeof userAgent !== "string") {
    return false;
  }
  const normalized = userAgent.toLowerCase();
  if (!normalized.trim()) {
    return false;
  }
  return BLOCKED_USER_AGENT_TOKENS.some((token) => normalized.includes(token));
}

// A gate failure must never break a normal page view, so classification is
// best-effort and the request continues when it throws.
function crawlerGate(request) {
  try {
    return isBlockedCrawler(request.headers && request.headers["user-agent"]);
  } catch (error) {
    return false;
  }
}

function refuseCrawler(res) {
  res.setHeader("Content-Type", "text/plain; charset=utf-8");
  res.setHeader("X-Robots-Tag", "noai, noimageai");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Cache-Control", "no-store, max-age=0");
  res.statusCode = 403;
  return res.end(CRAWLER_BLOCK_BODY);
}

const CONSENT_UI = `
<div id="kanta-cookie-consent" class="kanta-cookie-consent" role="region" aria-label="Website privacy choices" hidden>
  <div class="kanta-cookie-copy"><strong>Privacy choices</strong><p>KANTA uses Google Analytics and Google AdSense on this website only. With your permission, Google may use cookies or similar technologies to measure visits and show ads. These tools are not part of the KANTA iOS or Android apps. Read our <a href="/privacy#website">website privacy notice</a>.</p></div>
  <div class="kanta-cookie-actions"><button type="button" data-consent="reject">Reject optional</button><button type="button" data-consent="accept">Accept optional</button></div>
</div>
<button id="kanta-cookie-settings" class="kanta-cookie-settings" type="button" aria-label="Open website privacy choices">Privacy choices</button>
<link rel="stylesheet" href="/site-consent.css">
<script defer src="/site-consent.js"></script>`;

function makeCsp(nonce) {
  return [
    "default-src 'self'",
    "script-src 'nonce-" + nonce + "' 'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:",
    "style-src 'self' 'unsafe-inline' https:",
    "img-src 'self' data: blob: https:",
    "font-src 'self' data: https:",
    "connect-src 'self' https:",
    "frame-src https:",
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "upgrade-insecure-requests"
  ].join("; ");
}

function renderHtml(source, nonce, includeGoogleTools) {
  // Legacy static pages may still contain the old eager Analytics tag. Remove it
  // before adding nonces; only the consent manager may load Analytics after opt-in.
  const consentSafeSource = source.replace(/<script\b(?=[^>]*\bsrc=["'][^"']*\/?analytics\.js(?:[?#][^"']*)?["'])[^>]*>\s*<\/script>/gi, "");
  let html = consentSafeSource.replace(/<script\b([^>]*)>/gi, (tag, attrs) =>
    /\bnonce\s*=/i.test(attrs) ? tag : '<script nonce="' + nonce + '"' + attrs + ">"
  );
  if (includeGoogleTools) {
    html = html.replace("</body>", CONSENT_UI.replace("<script defer src=\"/site-consent.js\"></script>", '<script nonce="' + nonce + '" defer src="/site-consent.js"></script>') + "</body>");
  }
  return html;
}

async function handler(req, res) {
  if (req.method !== "GET" && req.method !== "HEAD") {
    res.setHeader("Allow", "GET, HEAD");
    res.statusCode = 405;
    return res.end("Method not allowed");
  }
  if (crawlerGate(req)) {
    return refuseCrawler(res);
  }
  const requestUrl = new URL(req.url, "https://kanta.invalid");
  const route = requestUrl.searchParams.get("route");
  const filename = PAGES[route];
  if (!filename) {
    res.statusCode = 404;
    return res.end("Not found");
  }
  try {
    const source = await fs.readFile(path.join(__dirname, "..", filename), "utf8");
    const isSongbook = route === "/songbook";
    const nonce = randomBytes(18).toString("base64");
    const html = isSongbook ? source : renderHtml(source, nonce, true);
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
    // The vercel.json headers rule reaches static files only: a rewrite to this
    // function replaces the response, so the crawler preference has to be set
    // here for the rendered routes. Observed live on the 2026-11-01 production
    // deployment, where /style.css carried X-Robots-Tag and / did not.
    res.setHeader("X-Robots-Tag", "noai, noimageai");
    res.setHeader("Cache-Control", "private, no-store, max-age=0");
    res.setHeader("Vary", "Cookie");
    res.setHeader("Content-Security-Policy", isSongbook
      ? "default-src 'none'; img-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
      : makeCsp(nonce));
    res.statusCode = 200;
    return res.end(req.method === "HEAD" ? undefined : html);
  } catch (error) {
    res.statusCode = 500;
    return res.end("Unable to render this page");
  }
}

module.exports = handler;
module.exports.renderHtml = renderHtml;
module.exports.makeCsp = makeCsp;
module.exports.isBlockedCrawler = isBlockedCrawler;
module.exports.PAGES = PAGES;
module.exports.BLOCKED_USER_AGENT_TOKENS = BLOCKED_USER_AGENT_TOKENS;
module.exports.ROBOTS_ONLY_TOKENS = ROBOTS_ONLY_TOKENS;
