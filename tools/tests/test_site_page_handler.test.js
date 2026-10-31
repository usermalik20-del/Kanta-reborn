"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { renderHtml, makeCsp } = require("../../apps/web/api/page.js");

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
