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
  "/songbook": "songbook.html"
};
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
  let html = source.replace(/<script\\b([^>]*)>/gi, (tag, attrs) =>
    /\\bnonce\\s*=/i.test(attrs) ? tag : '<script nonce="' + nonce + '"' + attrs + ">"
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
