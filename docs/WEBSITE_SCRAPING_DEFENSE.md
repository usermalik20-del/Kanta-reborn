# Website crawler and scraping defense

**Status: implemented on branch `feat/website-scraping-defense` (committed locally, based on
`main` `8210b26`, not pushed and not merged).**
Not yet deployed: the edge rules and headers below take effect only on the next production
deployment of `apps/web`.

## 1. What this defends against, and what it cannot

The public site is a static marketing site plus one request-time renderer
(`apps/web/api/page.js`). The realistic threat is not a hostile human reading the pages; it is
automated collection: AI training corpora, bulk data-mining resellers and copy-paste site
cloning.

Three honest limits apply, and no amount of configuration removes them:

1. **Anything a browser can render, a headless browser can read.** A scraper that uses
   residential proxies and a real browser user agent is indistinguishable from a visitor at
   the HTTP layer. This plan raises cost and removes the easy majority; it does not stop a
   determined collector.
2. **`robots.txt` is a declared policy, not a control.** It only constrains crawlers that
   choose to honor it. It is still worth writing precisely, because it is the mechanism
   good-faith crawlers and future opt-out standards read.
3. **Vercel serves static assets without running the page function.** `style.css`,
   `songbook.js`, `downloads.json` and the image assets bypass `api/page.js`, so the
   user-agent gate inside the function does not cover them. The `vercel.json` route rule
   added here is what covers those paths; a full per-request gate on static assets requires
   an edge layer (§4.1).

`X-Robots-Tag: noai, noimageai` is emitted as a preference expression. Treat it as a signal,
not as an enforcement mechanism. It deliberately does **not** include `noindex`, which would
remove the site from organic search — that would be a self-inflicted wound, and
`tools/verify-website.py` now fails the build if `noindex` is ever added to it.

## 2. Implemented layers

### 2.1 Declared policy — `apps/web/robots.txt`

Three semantic groups, so the policy can be tuned by moving one line:

| Group | Intent | Members |
| --- | --- | --- |
| Search engines and unfurlers | Indexing welcome | `Googlebot`, `Bingbot`, `DuckDuckBot`, `Slurp`, `YandexBot`, `Applebot`, `Twitterbot`, `facebookexternalhit` |
| AI training, data-mining and commercial SEO crawling | `Disallow: /` | `GPTBot`, `Google-Extended`, `Applebot-Extended`, `ClaudeBot`, `anthropic-ai`, `CCBot`, `Bytespider`, `meta-externalagent`, `meta-webindexer`, `Amazonbot`, `cohere-ai`, `cohere-training-data-crawler`, `Diffbot`, `ImagesiftBot`, `Omgilibot`, `Timpibot`, `Webzio-Extended`, `YouBot`, `DataForSeoBot`, `Scrapy` |
| Live-user AI fetchers | Allowed by default | `ChatGPT-User`, `OAI-SearchBot`, `Claude-User`, `Claude-SearchBot`, `PerplexityBot`, `Perplexity-User`, `DuckAssistBot`, `MistralAI-User`, `Google-CloudVertexBot` |

The live-user group exists as a deliberate product decision, not an oversight: those agents
fetch a page because a human asked for it, and blocking them removes referral traffic without
protecting anything. Moving a token into the block group is a one-line change if that
trade-off is revisited.

`Content-Signal: search=yes, ai-train=no, ai-input=no` states the machine-readable preference.
The `User-agent: *` group allows crawling and disallows only `/api/`, which is the internal
renderer path that no visitor or crawler needs.

### 2.2 Edge refusal — `apps/web/vercel.json`

A `routes` entry evaluated before the page rewrites refuses the declared crawlers with `403`
for every path, including static assets:

```json
{
  "src": "/(.*)",
  "has": [
    { "type": "header", "key": "user-agent",
      "value": "(?i).*(gptbot|claudebot|...|scrapy).*" }
  ],
  "status": 403
}
```

`status` on a `routes` entry and `has` with `type: "header"` are both present in Vercel's
published configuration schema. The `X-Robots-Tag: noai, noimageai` response header is added
to the existing global `headers` rule.

### 2.3 Request-time gate — `apps/web/api/page.js`

`isBlockedCrawler(userAgent)` matches the declared tokens against the request user agent and
randomizes nothing: it is a pure function, exported for tests. `crawlerGate(request)` calls it
inside `try`/`catch` and returns `false` on any error, so a gate failure can never turn a
normal page view into an error response. Blocked requests receive `403` with a plain-text body
pointing at `robots.txt`, and never reach `fs.readFile`.

### 2.4 One policy, three files, enforced

The token list exists in `robots.txt`, in the `vercel.json` pattern and in the handler. That
duplication is deliberate — the three mechanisms need different syntaxes — and it is guarded:
`tools/verify-website.py` and `tools/tests/test_website_security_contract.py` fail when the
three lists drift apart, when a refusal rule loses its user-agent condition, when the fallback
group is disallowed, or when `X-Robots-Tag` gains `noindex`.

## 2.5 Token provenance

A misspelled user-agent token is a silent no-op, so sourcing matters. The tokens below were
re-read from the vendor's own documentation on 2026-10-31:

| Vendor | Source | Tokens confirmed |
| --- | --- | --- |
| OpenAI | `platform.openai.com/docs/bots` | `GPTBot`, `ChatGPT-User`, `OAI-SearchBot` |
| Anthropic | Anthropic support article on web crawling | `ClaudeBot`, `Claude-User`, `Claude-SearchBot` |
| Perplexity | `docs.perplexity.ai/guides/bots` | `PerplexityBot`, `Perplexity-User` |
| Meta | `developers.facebook.com/docs/sharing/webmasters/web-crawlers` | `meta-externalagent`, `meta-webindexer`, `facebookexternalhit` |
| Common Crawl | `commoncrawl.org/ccbot` | `CCBot` |
| Amazon | `developer.amazon.com/amazonbot` | `Amazonbot` |

The remaining tokens come from the vendors' published opt-out lists, third-party crawler
directories or long-standing convention, and were **not** individually re-verified in this
session: `Google-Extended`, `Applebot-Extended`, `anthropic-ai`, `Bytespider`,
`cohere-ai`, `cohere-training-data-crawler`, `Diffbot`, `ImagesiftBot`, `Omgilibot`,
`Timpibot`, `Webzio-Extended`, `YouBot`, `DataForSeoBot`, `Scrapy`, `DuckAssistBot`,
`MistralAI-User`, `Google-CloudVertexBot`, `Googlebot`, `Bingbot`, `DuckDuckBot`, `Slurp`,
`YandexBot`, `Applebot`, `Twitterbot`.

Two consequences follow, and they are the reason this section exists rather than a footnote.
First, an unverified token is a best-effort declaration: `robots.txt` and the block list are
still correct in intent, but treat a specific entry as unproven until its vendor page is
checked. Second, `Google-Extended` and `Applebot-Extended` are **robots.txt-only controls**, not
user agents that request pages — they are declared in `apps/web/robots.txt` and deliberately
excluded from the handler block list, which `ROBOTS_ONLY_TOKENS` encodes and the verifier
enforces.

## 3. Validation performed locally

| Check | Result |
| --- | --- |
| `python3 tools/verify-website.py` | pass |
| `python3 -m unittest tools.tests.test_website_security_contract` | 11 tests pass |
| Full local tools suite (`python3 -m unittest discover -s tools/tests`) | 88 tests pass |
| `python3 tools/check-documentation-sync.py` on the new commit | PASS, all five required owner docs present |
| `vercel.json` against Vercel's published schema (Draft 4) | 0 errors; negative controls rejected |
| `apps/web/api/page.js` gate executed with QuickJS (real source, extracted functions) | 19 user-agent vectors correct; fail-open paths allow; refusal is `403` + `noai, noimageai` |
| Python oracle of `isBlockedCrawler` over every allow-group token | 0 collisions with a blocked token |
| `vercel.json` user-agent regex evaluated over blocked and allowed clients | matches every declared crawler, matches no allowed client |
| Negative controls (5 policy breakages re-injected) | every breakage rejected |

**Not validated, and not claimed:**

- `node` scripts do not execute in this authoring environment. `node --check`, `node --test` and
  `node --test tools/tests/test_site_page_handler.test.js` exit `0` without running anything, so
  the committed JavaScript test file is **unexecuted locally**. The QuickJS run above exercises
  the real source functions but is not a Node result; CI runs the real Node checks.
- **No deployment has happened and the production behaviour of the edge rule is unverified.**
  Confirming that Vercel refuses a `GPTBot` user agent on a real deployment is an acceptance
  step for the next preview, not something this change proves.

## 4. Staged follow-ups (not implemented)

### 4.1 Edge/WAF layer — covers static assets and rate limits

The function gate cannot see static assets, and neither layer can rate-limit. Both need an
edge layer:

- **Vercel Firewall / WAF**: managed bot rules and challenge modes, configured in the dashboard
  or API rather than `vercel.json`. Requires the applicable plan tier — check before promising it.
- **Cloudflare in front of Vercel**: offers bot management, per-path rate limiting and
  request logging. It changes DNS ownership of the domain, so it is an owner decision and
  interacts with the pending is-a.bot registration.
- **Edge Middleware** (`apps/web/middleware.js`): a per-request gate in front of static assets.
  It is the only option that runs before static file serving, but it also introduces a runtime
  failure mode for every asset request, and it cannot be validated without a preview deploy.
  Do not add it without a preview environment to prove it.

Rate limiting on the renderer path needs shared state (KV or an edge counter). A stateless
function cannot do it.

### 4.2 Legal clause — needs owner approval

The site's terms should state that automated collection for model training is not permitted.
The wording is an owner decision; no clause has been written into `apps/web/legal.html` by
this change. Note also that `apps/web/legal.html` is not a privacy document, so the
documentation-sync guard does not couple it to the privacy copies.

### 4.3 Honest limitation to publish

Nothing here should be described publicly as making the site "unscrapable". The accurate
statement is: the site declares an AI-training opt-out, refuses the declared crawler user
agents at the edge and in the renderer, and keeps search-engine indexing intact.

## 5. How to adapt

| Want | Change |
| --- | --- |
| Block or unblock one crawler | Move its token between groups in `robots.txt` and update the two lists; the guard will fail until all three agree |
| Block live-user AI fetchers too | Move their tokens into the block group; nothing else changes |
| Stop publishing the AI preference | Remove the `X-Robots-Tag` entry; the guard fails if the header stays but loses `noai`/`noimageai` |
| Add rate limiting | Requires §4.1; do not fake it in the function |
| Rebrand to the final domain | `robots.txt` carries the canonical hostname (`Sitemap:` line); update it with the rest of the domain sweep, item A5 in `docs/UNFINISHED_WORK.md` |
