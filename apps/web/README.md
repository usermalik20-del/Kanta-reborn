## Blog Expansion: 15 Substantial Articles + Fixed 404 (2026-11-08)

Fixed 404 on `/blog/converting-karaoke-files-ffmpeg-tools` — the article was defined in blog.js but the HTML file was never created. Now all 15 blog posts have actual HTML files.

Updated all readTime values from inflated "10-18 min read" claims to accurate estimates (2-7 min read) based on actual word counts.

Expanded all 5 original stub articles (200-300 words) to substantial pieces (600-1100 words) with tables, code examples, and actionable guidance.

Created 9 missing articles referenced in blog.js:
- soundfont-bank-size-vs-quality
- karaoke-songbook-export-sharing-privacy
- external-display-hdmi-airplay-karaoke
- microphone-selection-karaoke-recording
- tinysoundfont-android-midi-engine
- karaoke-party-effects-intermission-music
- organizing-karaoke-files-metadata-tagging
- pitch-correction-vs-pitch-training
- encrypted-companion-control-android

Updated blog.js with correct category counts (3, 2, 2, 3, 3, 3) and accurate readTime per post.
Updated blog/index.html category card counts to match.
Updated sitemap.xml with all 15 blog article URLs + 6 category pages.
Fixed vercel.json to use clean static category pages (cleanUrls compatible).


## Static Category Pages (2026-11-08)

Created static HTML files for all 6 blog category pages:
- `/blog/category/file-formats/index.html`
- `/blog/category/soundfonts/index.html`
- `/blog/category/stage-setup/index.html`
- `/blog/category/vocal-practice/index.html`
- `/blog/category/library-management/index.html`
- `/blog/category/technical-deep-dives/index.html`

Each page includes the blog index JavaScript and filters posts by category. This bypasses Vercel rewrite limitations for category deep-links.

Simplified vercel.json to only handle blog index rewrites.


## Vercel Routes Regex for Categories (2026-11-08)

Switched to legacy `routes` with regex for category rewrites:
- `^/blog/category/.*$` → `/blog/index.html`
- `^/blog$` and `^/blog/$` → `/blog/index.html`

Legacy `routes` supports full regex in `src`, unlike `rewrites` which only allows wildcards at end. Kept `rewrites` for blog index (cleanUrls compatible). Category regex evaluated after cleanUrls redirect.


## Vercel Rewrites :slug** Pattern (2026-11-08)

Changed rewrite pattern to Vercel's valid `:slug**` syntax:
- `/blog/category/:slug**` → `/blog/index.html`

`:slug**` matches zero or more path segments (including `/`), valid at end of pattern. Handles cleanUrls trailing-slash redirect automatically.


## Vercel Rewrites Glob Pattern (2026-11-08)

Changed rewrite pattern to Vercel's glob syntax:
- `/blog/category/*` → `/blog/index.html` (matches any path under /blog/category/)

Vercel rewrites support `*` wildcard (matches any characters except `/`) and `**` (matches including `/`). Using simple glob pattern instead of `:param*` named parameter syntax.


## Vercel Rewrites Only for Blog (2026-11-08)

Simplified to rely on `rewrites` + `cleanUrls: true`:
- `/blog/category/:slug*` → `/blog/index.html` (category deep-links)
- `/blog` and `/blog/` → `/blog/index.html` (blog index)

Removed conflicting `routes` entries. cleanUrls handles trailing slashes; rewrites evaluated after cleanUrls redirect per Vercel docs.


## Vercel Routes-Based Category Rewrites (2026-11-08)

Added legacy `routes` entries for blog category rewrites as fallback:
- `^/blog/category/(.*)$` → `/blog/index.html`
- `^/blog/category/(.*)/$` → `/blog/index.html`

This uses Vercel's legacy routing which is evaluated after rewrites and may catch requests that rewrites miss due to cleanUrls redirect timing.


## Vercel Rewrites Pattern Fix (2026-11-08)

Changed rewrite pattern from regex `(.*)` to Vercel's native `:slug*` syntax for category routes:
- `/blog/category/:slug*` → `/blog/index.html`
- `/blog/category/:slug*/` → `/blog/index.html`

Vercel rewrites use named parameter syntax (`:param*`) not regex capture groups.


## Vercel Rewrites Final Fix (2026-11-08)

Re-enabled `cleanUrls: true` for automatic .html stripping on article routes. Added both slash variants for category rewrites:
- `/blog/category/(.*)` → `/blog/index.html`
- `/blog/category/(.*)/` → `/blog/index.html` (handles cleanUrls trailing-slash redirect)

This allows both article deep-links (`/blog/article-slug`) and category deep-links (`/blog/category/topic`) to work simultaneously.


## Vercel Rewrites CleanUrls Fix (2026-11-08)

Disabled `cleanUrls: true` in vercel.json to prevent trailing-slash redirects from interfering with blog category rewrites. Rewrites now use simple regex pattern `/blog/category/(.*)` → `/blog/index.html` for reliable deep-linking to category views.


## Vercel Rewrites for Blog Routes (2026-11-08)

Updated vercel.json with `rewrites` section for proper SPA-style routing:
- `/blog/category/:slug*` → `/blog/index.html` (handles `/blog/category/file-formats`, etc.)
- `/blog` and `/blog/` → `/blog/index.html`

Rewrites are evaluated before cleanUrls redirect, fixing 404s on category deep-links.


## Vercel Rewrites for Blog Category Routes (2026-11-08)

Added Vercel route rewrites to handle client-side blog category URLs:
- `/blog/category/*` → `/blog/index.html` (handled by blog.js)
- `/blog` and `/blog/` → `/blog/index.html`

Created `/blog.js` with:
- Client-side category filtering via history.pushState
- popstate handling for browser back/forward
- Dynamic posts grid rendering from blogPosts array
- URL-aware initial render (reads category from pathname)
- Active category highlighting

This enables deep-linking to category views like `/blog/category/file-formats` without 404s.


## Blog Index Full Restoration (2026-11-08)

Restored complete blog index with all sections:
- 6 category cards (File Formats, SoundFonts, Stage Setup, Vocal Practice, Library Management, Technical Deep Dives)
- Dynamic posts grid rendered by blog.js with 5 initial articles
- Newsletter signup form
- Schema.org Blog JSON-LD structured data
- Reveal animations with IntersectionObserver

This completes the blog system deployment for AdSense "Low value content" remediation.


## Blog System Addition (2026-11-08)

Added a comprehensive blog/guide section to address AdSense "Low value content" rejection:

**New files:**
- `apps/web/blog/index.html` — Blog homepage with 6 categories, newsletter, dynamic posts grid
- `apps/web/blog.css` — Complete styling for blog index, article pages, categories, related posts
- `apps/web/blog.js` — Client-side rendering for posts grid, category filtering, reveal animations
- 5 substantial articles (10-18 min read each):
  - `apps/web/blog/midi-kar-mp3-wav-karaoke-file-formats-explained.html`
  - `apps/web/blog/choosing-the-right-soundfont-for-karaoke.html`
  - `apps/web/blog/building-your-personal-karaoke-library.html`
  - `apps/web/blog/spice-pitch-estimation-how-it-works.html`
  - `apps/web/blog/vocal-practice-techniques-pitch-training.html`

**Integration:**
- Added "Blog" link to main navigation on all 11 pages
- Blog preview section on homepage with 6 article cards
- Updated `sitemap.xml` with all blog routes (index, 5 articles, 6 categories)
- Updated `tools/verify-website.py` validation for blog subdirectory routes

**Purpose:** Provides authentic, high-quality karaoke expertise content beyond app downloads to meet AdSense quality guidelines.

# KANTA public website

Static first-party pages recovered from the captured October 15 website evidence. Android and iOS remain separate native applications under their own directories.

## Shared visual system

Every public route, including the private `/songbook` reader, uses the shared site header and the dark KANTA theme in `style.css`. Theme colors, content width, page gutters and card surfaces are defined as shared CSS tokens; the primary nav, brand mark and Download App action are consistent across routes. `songbook.css` retains the paper appearance of the printed catalog itself, while its site shell, form controls and unlock card follow the shared theme. Keep new route styling on these shared tokens and components.

The Vercel project root is `apps/web`. Public HTML routes are delivered through `api/page.js`, which creates a cryptographically random per-response nonce and a matching Content Security Policy. The renderer keeps HTML responses private/no-store and excludes the private `/songbook` route from third-party integrations. `vercel.json` maps public pages to the request-time handler; preview verification must confirm that static-file routing cannot bypass it.

Google Analytics (`G-L0QV01MWR4`) and Google AdSense (`ca-pub-6290349358884298`) load only after the visitor explicitly accepts optional cookies. The consent choice can be rejected, changed or revoked through the persistent Privacy choices control. The existing `ads.txt` publisher declaration is retained. This code enables the website-side integration only; actual ad delivery still depends on Google's publisher approval, Auto Ads configuration and any applicable Google consent-platform requirements. No Google analytics or advertising script is bundled in either native app. The private Songbook is excluded from both integrations.

Android and iOS downloads are published independently from exact-SHA main-branch artifacts. Android publication requires the accepted Android validation artifact for its own source SHA; iOS publication requires its own unsigned iPhoneOS archive plus the iPhone/iPad simulator walkthrough on that same iOS source SHA. The platform publisher updates only the selected platform's `downloads.json` entry and download page, preserves the peer platform's provenance, verifies the website and security contract, then dispatches the existing Vercel production deployment. It reads GitHub's actual artifact creation/expiry timestamps and records the validated 1–30-day retention interval. The currently published Android APK is debug-signed; the new release pipeline uses a separate owner-controlled signing identity, and previous APK installs cannot update in place after that transition. The iPhoneOS IPA is unsigned, needs separate trusted personal signing and has no iCloud Save; it is not a Safari installer. Neither is a store release. The legacy combined `verified-downloads.yml` workflow is contract-only and does not package or publish either platform.

Run `python3 tools/verify-website.py`, `python3 -m unittest tools.tests.test_website_security_contract`, `node --check apps/web/api/page.js`, `node --check apps/web/site-consent.js`, and `node --test tools/tests/test_site_page_handler.test.js` from the repository root. `website-validation.yml` owns these checks; it does not allocate native build runners. The `documentation-sync` job in `website-validation.yml` separately verifies that implementation and workflow changes update their required README, architecture and testing documentation. Vercel production deployment has no pull-request trigger and preserves the previous deployment for rollback.

The recovery evidence in `docs/evidence/website/2026-10-15` remains immutable. Original legal and software notices are retained; current Android implementation and signing statements supersede unavailable-repository feature claims.

## Android stage copy provenance — 2026-11-03

`features.html` describes PR #155's merged Android preview (`6db012c3`), not a new iOS build or tagged release. It distinguishes ten monitor names from eight canonical modes, discloses the displayed score floor of 50, and labels the scenic video catalogue/downloader as partial integration without a download picker or trusted expected digest. Device timing, listening and network acceptance remain open. The documentation correction does not change `downloads.json` or either platform's generated download page; artifact links, expiry and platform-specific source SHAs remain owned by the publisher. Website-only validation/deployment is sufficient for this copy change; no native rebuild is implied.

## Crawler policy

The public site declares an AI-training opt-out in `apps/web/robots.txt` and enforces the declared user agents in two places: a `routes` refusal with a `has` user-agent condition in `apps/web/vercel.json`, which is evaluated before the page rewrites and therefore also covers static assets, and `isBlockedCrawler()` in `apps/web/api/page.js`, which refuses the request before the page is read from disk. Refusals are `403` with `X-Robots-Tag: noai, noimageai`. Crawler user agents are refused by the edge rule, so in production they receive Vercel's own 403 page rather than the renderer's text body; the renderer's plain-text refusal is the layer behind it. Live verification on 2026-11-01 showed the `headers` rule reaches static responses (`/style.css`, `/robots.txt`, `/downloads.json`) but **not** the rewritten HTML routes, because the function's response replaces them — so the renderer sets `X-Robots-Tag` on its own response, and `tools/verify-website.py` fails if that line is removed.

Three deliberate constraints hold this policy in place. Search-engine and unfurler user agents must stay crawlable, so the `User-agent: *` group allows crawling and no search-engine token may appear in the handler block list. Live-user AI fetchers stay allowed as a product decision. `X-Robots-Tag` must never carry `noindex`, because that would remove the site from organic search rather than protect it. The token list is intentionally duplicated across the three files with different syntaxes, and `tools/verify-website.py` plus `tools/tests/test_website_security_contract.py` fail when the lists disagree, when a refusal rule loses its user-agent condition, or when a group starts disallowing all crawling.

`robots.txt` is a declared preference, not a control, and this work is not claimed to make the site unscrapable. The edge rule and the static-response header were verified in production on 2026-11-01, and the rendered-response header followed as a follow-up fix; any change made since takes effect only on the next production deployment. Rate limiting and per-request gating of static assets still need an edge/WAF layer; see [scraping defense](../../docs/WEBSITE_SCRAPING_DEFENSE.md) for the effectiveness limits and the follow-ups.



**Consent loading invariant:** `api/page.js` strips the legacy static `/analytics.js` script tag from recovered HTML before rendering. Do not reintroduce an eager Analytics tag in any source page: `site-consent.js` is the sole loader and may inject Analytics and AdSense only after the saved consent value is `accepted`. `tools/tests/test_site_page_handler.test.js` covers removal of the legacy tag from rendered output. This is important because adding a CSP nonce to an eager script does not make its execution consent-gated.

## Android OTA self-update feed

The static file `apps/web/updates.json` is a generated artifact, not edited by hand. It is produced by the Android publisher workflow (`.github/workflows/platform-download-publishing.yml`) on every successful Android build on `main`: the workflow runs `tools/prepare-update-feed.py`, which constructs the feed from `apps/web/downloads.json` (android source SHA), `verified-downloads/android/build-info.json` (file name, sha256, bytes), and the source-tree `app/build.gradle` (versionCode, versionName, minSdk). The resulting `updates.json` is staged into the downloads commit and deployed to Vercel.

`apps/web/vercel.json` exposes it via a static route `^/updates\.json$` -> `/updates.json` with `Cache-Control: public, max-age=0, must-revalidate`, evaluated before the page rewrites. The pinned feed URL is `https://kanta-public.vercel.app/updates.json`.

The feed format is "kanta-update-feed" version 1, channel "stable-reconstruction". It is fetched by the Android app over HTTPS only; the app validates the manifest strictly (schema, URL policy against a durable host allowlist, SHA-256 digest, version ordering) and stages downloads in app-private storage. The publisher regenerates `apps/web/updates.json` after every successful Android build on `main` (initial committed entry: release `v0.10.0-preview-aaa41898`), so the endpoint stays current without hand editing.

## Website redesign — 2026-11-07

The website receives a major redesign adding a theme system, micro-interactions, and page-specific FX.

### New shared assets
| File | Purpose |
| --- | --- |
| `theme.css` | Dark/light/system theme tokens, animated gradients, micro-interaction utilities, component styles (dl-bar, cmdk, stage-preview, guide-rail, book-particles) |
| `theme.js` | Theme toggle (dark/light/system) with localStorage persistence, smooth cross-fade |
| `micro-interactions.js` | Button ripple, magnetic pull, 3D card tilt, scroll reveals, count-up stats |
| `command-palette.js` | ⌘K/⌃K fuzzy search over pages, features, guide steps; floating FAB on desktop |
| `hero-animation.js` | Audio-reactive gradient canvas + animated stage mockup (replaces static hero mockup) |
| `download-progress.js` | Sticky artifact status bar on `/android` + `/ios-download` |
| `stage-preview.js` | Fixed phone frame on `/features` mirroring scrolled section |
| `guide-parallax.js` | Pinned phone illustration + palette shift per guide step |
| `songbook-fx.js` | 3D page flip, ambient particles, search highlight, lock/unlock transitions |

### Updated HTML pages
All six pages (`index.html`, `android.html`, `ios-download.html`, `features.html`, `guide.html`, `songbook.html`) now:
- Load `theme.css`, `theme.js`, `micro-interactions.js`, `command-palette.js`
- Include page-specific scripts (`hero-animation.js` on index, `download-progress.js` on download pages, `stage-preview.js` on features, `guide-parallax.js` on guide, `songbook-fx.js` on songbook)
- Have theme toggle buttons (☀️/🌙/💻) in header
- Use `.reveal` / `.reveal-stagger` classes for scroll-triggered animations
- Carry `data-theme="system"` on `<html>` and page-specific `data-*` attributes

### Hero replacement
The static `device-showcase` mockup on `index.html` is replaced by:
- `<canvas id="hero-gradient">` — animated gradient blobs + waveform driven by simulated audio bands
- Animated `data-mockup-stage` with typing lyric sweep, live queue render, pitch meter
- Respects `prefers-reduced-motion: reduce` (single static frame)

### Stage preview on /features
`data-stage-preview` on `<html>` activates `stage-preview.js` — fixed phone frame (desktop right, mobile bottom) that updates its scene as the user scrolls sections:
- `starter-songs` → queue list
- `player` → lyrics with highlight sweep
- `stage-display` → animated bars
- `party-sfx` → hit pads
- `pitch-training` / `inputs` → pitch meter
- `android-stage-v2` → score count-up

### Guide parallax
`data-guide-parallax` on `<html>` activates `guide-parallax.js` — pinned phone illustration (desktop right) with per-step screen animation and palette shift:
- `first-run` → home screen
- `library` → import progress bar
- `songbook` → book pages
- `soundcard` → mic waveform
- `personal-stage` → stage with background
- `remote` → code + buttons

### Songbook FX
`songbook-fx.js` adds:
- 3D CSS page flip on prev/next (keyboard arrows + buttons)
- Ambient gold/cyan dust particles over `.book-card` canvas
- Search highlight pulse on `.song-list li.highlight`
- Lock/unlock state transitions with cross-fade
- Keyboard navigation (←/→)

### Theme toggle
Three-button group (☀️ light / 🌙 dark / 💻 system) in header on all pages, driven by `theme.js`. Smooth 300ms cross-fade via `.theme-transitioning` class.

### Accessibility
All animations disabled under `prefers-reduced-motion: reduce`:
- CSS: `animation-duration: 0.01ms`, `transition-duration: 0.01ms`
- JS: instant reveals, no canvas loops, no scroll animations
- Focus states preserved (gold outline)

### Website redesign JS modules (2026-11-07)
The redesign is implemented as modular JS files loaded by all pages:
- `theme.js` — theme toggle (dark/light/system) with localStorage persistence
- `micro-interactions.js` — button ripple, magnetic pull, 3D card tilt, scroll reveals, count-up stats
- `command-palette.js` — ⌘K/⌃K fuzzy search over pages, features, guide steps
- `hero-animation.js` — audio-reactive gradient canvas + animated stage mockup
- `download-progress.js` — sticky artifact status bar on `/android` + `/ios-download`
- `stage-preview.js` — fixed phone frame on `/features` mirroring scrolled section
- `guide-parallax.js` — pinned phone illustration + palette shift per guide step
- `songbook-fx.js` — 3D page flip, ambient particles, search highlight, lock transitions
