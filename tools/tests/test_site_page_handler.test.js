"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const handler = require("../../apps/web/api/page.js");
const { renderHtml, makeCsp, isBlockedCrawler } = handler;

function fakeResponse() {
  return {
    headers: {},
    statusCode: 200,
    setHeader(key, value) { this.headers[key.toLowerCase()] = value; },
    end(body) { this.body = body; }
  };
}

function fakeRequest(userAgent, url) {
  return { method: "GET", url: url || "/api/page?route=%2F", headers: { "user-agent": userAgent } };
}

test("nonce renderer marks every existing script and adds consent loader", () => {
  const nonce = "random-per-response";
  const html = renderHtml('<html><head><script src="/nav.js"></script></head><body><script>run()</script></body></html>', nonce, true);
  assert.equal((html.match(/<script nonce="random-per-response"/g) || []).length, 3);
  assert.match(html, /Privacy choices/);
});
test("CSP binds scripts to the response nonce and enables strict-dynamic", () => {
  const policy = makeCsp("fresh-nonce");
  assert.match(policy, /script-src 'nonce-fresh-nonce'/);
  assert.match(policy, /'strict-dynamic'/);
  assert.match(policy, /object-src 'none'/);
  assert.match(policy, /base-uri 'none'/);
});

test("legacy eager Analytics tags are removed until consent is granted", () => {
  const nonce = "consent-gated-nonce";
  const html = renderHtml(
    '<html><head><script defer src="/analytics.js"></script></head><body></body></html>',
    nonce,
    true
  );
  assert.doesNotMatch(html, /<script\b[^>]*src=["']\/analytics\.js["']/i);
  assert.match(html, /site-consent\.js/);
  assert.match(html, /data-consent="accept"/);
  assert.match(html, /data-consent="reject"/);
});

test("crawler gate refuses declared AI and data-mining user agents case-insensitively", () => {
  for (const userAgent of [
    "Mozilla/5.0 (compatible; GPTBot/1.2; +https://openai.com/gptbot)",
    "CCBot/2.0 (https://commoncrawl.org/faq/)",
    "Mozilla/5.0 (compatible; Bytespider; spider-feedback@bytedance.com)",
    "meta-externalagent/1.1",
    "python-requests/2.31 with Scrapy/2.11",
    "ClaudeBot/1.0"
  ]) {
    assert.equal(isBlockedCrawler(userAgent), true, userAgent);
  }
});

test("crawler gate never refuses search engines, unfurlers or ordinary browsers", () => {
  for (const userAgent of [
    "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
    "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
    "Mozilla/5.0 (compatible; Applebot/0.1; +http://www.apple.com/go/applebot)",
    "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1",
    "curl/8.4.0"
  ]) {
    assert.equal(isBlockedCrawler(userAgent), false, userAgent);
  }
});

test("crawler gate fails open when the user agent is absent or unusable", () => {
  assert.equal(isBlockedCrawler(undefined), false);
  assert.equal(isBlockedCrawler(null), false);
  assert.equal(isBlockedCrawler(""), false);
  assert.equal(isBlockedCrawler("   "), false);
  assert.equal(isBlockedCrawler(42), false);
});

test("blocked crawler requests are refused before any page is rendered", async () => {
  const res = fakeResponse();
  await handler(fakeRequest("Mozilla/5.0 (compatible; GPTBot/1.2)"), res);
  assert.equal(res.statusCode, 403);
  assert.match(res.headers["x-robots-tag"], /noai, noimageai/);
  assert.match(res.body, /robots\.txt/);
  assert.doesNotMatch(res.body, /<html/i);
});

test("rendered pages carry the crawler preference on the response itself", async () => {
  const res = fakeResponse();
  await handler(fakeRequest("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"), res);
  assert.equal(res.statusCode, 200);
  // The vercel.json headers rule does not reach a rewritten function response.
  assert.equal(res.headers["x-robots-tag"], "noai, noimageai");
  assert.match(res.body, /<html/i);
});
