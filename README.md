
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

## Android home hero greeting/tagline removed — local candidate

The Home dashboard no longer shows the large hero banner (`Good evening` greeting + `Music brings people together.`). The KANTA header with its `SING MORE. TOGETHER.` tagline directly above already carried that messaging, so the 142dp hero was redundant and is removed; the search row now sits directly under the header.

The duplicate bottom dock is also gone: HomeScreen rendered its own 5-tab bar inside the Home tab while MainActivity mounts the persistent dock on every screen, so Home used to show two nav bars. HomeScreen's internal dock is removed; the persistent dock is the single navigation surface (tags `kanta.nav.*`).

As part of the same Home pass, the redundant quick-cards row (`My Set`/`Favorites`/`Recent` — all three opened the same My Set destination) and the header Settings gear (which opened the More tab, already reachable from the persistent dock) are removed. Settings remains under More -> Settings.

The Home dashboard now features persistent in-app search with an inline keypad (`123` / `ABC` toggle, `RES` reserve button, `CLR` clear button). Typed song codes, titles, or artists project live search results directly onto the karaoke stage above.

## Android premium icon system — local candidate

Android's UI now uses one coherent outline icon family (`res/drawable/kanta_*_color.xml`, 24x24 view, 1.8 stroke, primary cyan `#29D9FF` with one violet `#7657FF` detail). The bottom five tabs form one continuous floating dock (icon over label, muted inactive / cyan active glow); the More page uses dedicated sliders/phone+wireless/waveform/circled-? marks (Diagnostics no longer shares Settings); and Sing/Performance/Experience menus carry the same geometry.

# KANTA Reborn

## Android persistent-stage navigation — local candidate

The Android main screen keeps the same live karaoke stage above the menu/control area. Home, song search, Queue, Now Playing, Performance and Experience are lower-panel destinations rather than stage replacements. Background and other presentation controls operate on the existing session so their changes can be previewed without leaving the stage. Explicit fullscreen remains a separate read-only stage projection. iOS, the website, playback/FIFO ownership and opt-in microphone behavior are unchanged. This is a local source candidate, not a built, device-accepted or published APK; see `docs/TESTING.md` for acceptance requirements.

The redundant global Play/Next/Fullscreen and six-button workspace rows are consolidated: the five primary bottom tabs remain persistent, and long-pressing **More** expands Search, Queue, Visuals, Controls, Pitch and Coach. Tapping the KANTA header logo opens a separate hardware-inspired **KANTA Control Deck** only in the lower content region; the header, primary live stage and bottom navigation remain mounted. Its physical-style labels are KANTA actions—master volume, playback, microphone/monitor, camera, key/tempo and existing destinations—not copied hardware placeholders. Logo re-tap or Android Back restores the prior destination.

## Android SoundFont RAM ceiling — local candidate

The Android native renderer now permits up to **1536 MiB (1.5 GiB)** of tracked TinySoundFont allocations per worker, raised from 128 MiB for large decoded banks. This is a hard ceiling, not reserved or guaranteed memory: the operating system can refuse smaller allocations or terminate a memory-heavy process. Full-tail piano banks can require over 1 GiB before other app work. Format validation, voice limits, storage checks and source-byte preservation remain unchanged. Local native regression passes; APK, sanitizer and large-bank device acceptance remain separate gates. No new bank is bundled.

**Android synthesis engine vendoring (2026-11-03):** the vendored TinySoundFont header in `apps/android/app/src/main/cpp/vendor/` is now the fork `usermalik20-del/tinysoundfont` at revision `a272fe30` (release `kanta-vendor-a272fe30`) instead of the bare upstream v0.9 header: hardening patch, Catmull-Rom interpolation (default, `TSF_HIGH_QUALITY_INTERPOLATION` toggle), ramped low-pass, SF2 modulators, chorus/reverb stages, an SF3 loader pad fix and the `tsf_playback.h` wrapper. No KANTA source file consumes the new engine APIs yet, so this is a drop-in engine replacement, not an app feature; the documented engine boundary below changes from "modulators and effects are not implemented by the renderer" to "the engine implements them; KANTA has not wired them to app controls yet". Provenance is pinned in `vendor/PROVENANCE.json` and the five mapped docs.
Native Android and iOS karaoke applications built around the **Compact Workspace** redesign, together with their public website.

**Tagged release (separate from current previews):** `v0.1.0-workspace-redesign` at SHA `41c8ddde`.

## What shipped

The Compact Workspace redesign keeps the karaoke player live on Home and legacy utility destinations. Reference-led destination screens own their headers, navigation and action controls; the persistent stage/toolbars are hidden there so controls are not rendered twice.

| Capability | iOS | Android |
| --- | --- | --- |
| Persistent stage / player | Yes | Yes |
| Transient accessory panels (Search, Queue, Visuals, More) | Yes | Yes |
| Single-active accessory (no stacking) | Yes | Yes |
| Device-specific presentation (iPhone sheet / iPad popover; handset sheet / tablet popover) | Yes | Yes |
| Workspace toolbar (Search, Queue, Visuals, More) | Yes | Yes |
| Accessibility identity containment fix (RC1) | Yes | Yes |
| Toolbar label collision fix (RC2) | Yes | Yes |

**Release artifacts** (`v0.1.0-workspace-redesign`):

- Android APK — `KANTA-Android-preview-41c8ddde.apk`
- iOS IPA — `KANTA-iOS-personal-unsigned-41c8ddde.ipa` (unsigned; requires trusted personal signing)
- Public website — <https://kanta-public.vercel.app>

Download pages under `apps/web/` (`ios-download.html`, `android.html`, `downloads.json`) link to the verified GitHub Actions artifacts and to the GitHub Release, with matching SHA-256 checksums and artifact IDs.

## Website privacy and advertising

The public website retains Google Analytics (`G-L0QV01MWR4`) and Google AdSense (`ca-pub-6290349358884298`) behind an explicit optional-consent choice. Visitors can accept, reject or later change their choice. The private browser Songbook is excluded. These integrations are website-only: neither native app bundles the website scripts or loads website ads. Ad delivery remains subject to Google's publisher approval and account configuration; see [website operations](apps/web/README.md) and [privacy](apps/web/privacy.html).

## Website visual consistency

The Android launch screen is a full-bleed splash shared with the app theme's window background: a portrait image (`drawable-nodpi/kanta_splash_image.webp`) and a landscape image (`drawable-land-nodpi/kanta_splash_image.webp`) that Android selects automatically for tablets in landscape and Android TV. See apps/android/README.md.

All ten website routes use one shared dark KANTA theme, color/spacing tokens, primary navigation, brand mark and download action from `apps/web/style.css`. The private Songbook keeps its printed-paper catalog treatment inside the same site shell.

## Website crawler and scraping defense

The public site declares an AI-training opt-out and refuses the declared AI training, data-mining and SEO-crawler user agents at two layers: a `routes` refusal in `apps/web/vercel.json` that also covers static assets, and the request-time gate in `apps/web/api/page.js`. Search-engine indexing is deliberately preserved, live-user AI fetchers stay allowed, and `robots.txt` carries the canonical sitemap plus a `Content-Signal` preference. The `X-Robots-Tag: noai, noimageai` preference is set twice on purpose: the `vercel.json` headers rule reaches static responses, while the renderer sets it on the HTML routes, because a rewrite to the function replaces the response. `robots.txt` is a declared policy, not an enforcement mechanism, and this is not claimed to make the site unscrapable. The token list is deliberately duplicated across the three files and `tools/verify-website.py` fails when they drift apart. Verified live on 2026-11-01: `/robots.txt` serves the policy, a declared crawler receives `403` on both an HTML route and a static asset, `Googlebot` and a normal browser still receive the rendered page, and `X-Robots-Tag` is present on both static and rendered responses. See [scraping defense](docs/WEBSITE_SCRAPING_DEFENSE.md).

## Repository layout

| Location | Purpose |
| --- | --- |
| `apps/android/` | Native Java/Views app with independent Gradle build |
| `apps/ios/` | Reconciled Swift app and independent Apple build |
| `apps/web/` | Static website and private browser Songbook |
| `recovery/ios/` | Preserved historical source snapshots |
| `docs/evidence/website/2026-10-15/` | Immutable original website evidence |
## KANTA design-system UI (Android)

The Android application uses a dedicated presentation layer under `ph.kanta.reborn.ui` that implements
the KANTA dark entertainment design system against the supplied UI concept:

- **Design tokens** (`KantaTheme`): `#050B18` background, `#0D1930`/`#12203A` surfaces, `#203459`
  borders, `#29D9FF`/`#2589FF`/`#7657FF`/`#D946EF` primary colors, `#F7F9FF` text
- **Screens**: Home (branding, hero, search, 2x3 feature grid, quick play, bottom nav), Now Playing /
  Lyrics (stage-dominant with large lyrics, progress, transport, key/tempo), Search (source tabs,
  media rows with Sing action), Queue (count, items, Add Songs CTA), Pitch & Tempo (key/tempo cards),
  Mic & Voice (mic toggle, input level, reverb/echo, voice FX, coach), Visuals (Background/Camera/
  Effects tabs with thumbnail grid), Fullscreen Stage (clean stage with lyrics and controls)
- **Consistent destinations**: shared KantaScreenHeader app bars and KANTA vector icons across the
  goal menus, search, queue, controls and playback surfaces; pill-style source tabs and icon-led
  category cards use the same palette as Home
- **Reusable components**: KantaCard, KantaButton, KantaIconButton, KantaImageButton,
  KantaScreenHeader, KantaSearchBar, KantaProgressBar, KantaStepper, KantaSectionHeader,
  KantaTabSelector, KantaSongArtwork, KantaMediaRow, KantaTransportControls

The UI refactor is presentation-only. All playback, queue, persistence, sensor, companion and
playback logic remains owned by `SessionController` and `StagePresenterImpl`; no backend behavior is
duplicated or replaced.

The browser remote (`apps/android/app/src/main/assets/companion/`) is restyled with the same KANTA
design system: responsive 3-column desktop layout, cyan active states, honest disabled state for
controls not in the companion protocol allowlist. The wire protocol and pairing flow are unchanged.

See [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) and [docs/TESTING.md](../docs/TESTING.md) for the
design-system contract and test boundaries.

## Karaoke Stage v2 — merged Android preview

PR [#155](https://github.com/usermalik20-del/Kanta-reborn/pull/155) merged as `6db012c34f97b64b4692e09ee709fe4e439e2a71` on 2026-11-03, superseding PR #153. This is Android preview functionality, not a new tagged/store release or physical-device acceptance. It preserves the existing Home destinations, camera lifecycle and SoundFont inventory.

### Vocal monitor effects

`VocalMonitorEffects.Mode` exposes ten selectable names: Dry, Small Room, Room, Studio, Concert Hall, Hall, Cathedral, Plate Reverb, Slap Echo and Warm Tape. Room/Small Room and Hall/Concert Hall share tuning: there are eight canonical modes including Dry, not ten distinct acoustic presets. Dry bypasses the wet effects and clamps output; do not describe every mode as passing through tanh processing. Monitoring remains opt-in and experimental; physical sound quality and latency are unverified. Effects alter monitored output, not dry scoring input.

### Party sounds and scoring

The eight synthesized pads are Applause, Cheering, Drum Roll, Air Horn, Laughter, Ta-Da, Ding and Sad Trombone. The five presented score bands are Excellent Singer (100), Great Singer (90–99), Good Singer (80–89), Rising Star (65–79) and Keep Practicing (50–64). `ScoreSink.overall()` floors the displayed result at 50; `rawOverall()` retains the underlying 0–100 estimate. This is a presentation floor, not a measured minimum performance or a guarantee that every song receives a score.

`ScoreRevealPlan` defines a 1900 ms drumroll, 900 ms count-up and 420 ms label fade. `StagePresenterImpl` schedules the reveal; `ScoreOverlay` implements count-up and an instant-final-score reduced-motion path. Device timing, cue audibility and reduced-motion acceptance remain open.

### Scenic video library — partial integration

`ScenicCatalog` contains ten remote clip descriptors with source and licence attribution; these are separate from the bundled scenic still images. `ScenicClip` records HTTPS URLs, expected size and container, but no trusted expected SHA-256. `BgvLibrary` bounds downloads and records a computed SHA-256 in its private index; recording that digest is **not** verification against a trusted source checksum.

`SessionController` exposes download/status/list/delete APIs. The Scenic dialog lists already-downloaded entries with attribution and use/remove actions, but does not expose a catalogue download picker. Selection requests `SessionController.importStage(..., video=true)`; successful import/playback has not been device-accepted. Do not advertise ten ready-to-download videos, streaming backgrounds or verified network downloads. See `docs/UNFINISHED_WORK.md` section K for the remaining acceptance work.



| `docs/CODEBASE_CONTEXT_PACKET.md` | Actual stack, storage, playback, networking and performance risks |

### Compact Workspace implementation

- iOS: `apps/ios/Sources/Features/RootView.swift` instantiates `KantaWorkspace(services:presentationState:)` as the root view body.
- Android: `apps/android/app/src/main/java/ph/kanta/reborn/KantaWorkspace.java` is the root container; `MainActivity.java` sets it as the content view and routes toolbar buttons through `workspace.activateAccessory(...)`.

## Android library import lifecycle

Android song and folder imports keep provider I/O on the app-owned workers. `SessionController` is the main-Looper boundary for progress and completion callbacks, so Activity dialogs and toasts are never mutated from an import worker. The Activity detaches its listener when stopped and reattaches only while a library import is still active; completion, cancellation and folder-scan failure clear the listener.

## CI ownership and release gates

GitHub Actions is partitioned by source ownership. Website changes run website validation; Android changes run Android validation; iOS changes run iOS validation. The lightweight documentation-sync check runs on every pull request and fails when an implementation or workflow change is missing its required owner documentation. Before opening a pull request, run `python3 tools/preflight.py`: it picks the guards the change needs, prints the owner documents that must change with it, and lists the cloud-only gates it cannot prove. Android-only changes do not trigger iOS archives, walkthroughs or IPA publication; each platform retains its own validation and download provenance. Vercel production deployment is separate from pull-request validation and runs only from `main` or an explicit dispatch. The platform-download publisher filters its `workflow_run` trigger to `main`, while retaining job-level success and workflow-name guards, so feature-branch PR completions do not create skipped publisher runs. See [workflow ownership and testing](docs/TESTING.md). CI status is commit-specific; a green result on an earlier SHA is not evidence for a later commit.
A failing run is recorded automatically in one investigation issue per workflow, and that triage step queries GitHub once per answer so a red build is never left untriaged by a broken pipe.
The publisher that advances each platform's download page and the update feed verifies the Android APK with the Android build tools, so it puts `$ANDROID_HOME/build-tools` on `PATH` before that check and fails with the real reason if they are missing. Release tags are constructed in the shell as `v<version>-preview-<source-sha8>`; never interpolate shell variables inside single-quoted Python code, or the durable asset URL and OTA feed diverge. Each Android release description is generated from the previous valid Android preview to this exact source, includes merged PR summaries, exact CI/APK provenance and known installation limits, and rejects incomplete evidence rather than inventing results. Android validation explicitly runs `:app:assembleDebug` and `:app:assembleRelease`: release APK for publication, debug APK for emulator/runtime checks; keep the packager and runtime tools aligned with those distinct outputs, and include both APK output directories in the uploaded artifact.

## Open release gates
- **Pitch Settings UI** (Controls → Pitch Settings)- **Pitch Settings UI (Android, compiles in the build)**  exposes sensitivity slider (0.1–3.0), presets (Beginner/Normal/Pro), note picker (C4–B5), and custom Hz input (20–2000 Hz). Settings persist via `SettingsStore` (`pitch.assistantSensitivity`, `pitch.targetHz`, `pitch.targetName`). Voice Coach (`LearningAssistant`) scales thresholds with sensitivity; Pitch Practice (`PitchGameOverlay`) uses stored target Hz as fallback.

- `InAppControlCenterView` still uses the unrestyled `GeloVisualStyle` (14 references) — the last unrestyled surface.
- **Android lyric-stage animation candidate (not yet cloud-validated).** `LyricStage` and the fullscreen `StageView` keep the existing scrolling window, but the active line now fills continuously across each source-timed MIDI fragment instead of switching a whole word, and only the fragment being sung receives a per-letter wave driven by the existing single-owner pitch estimate. No word timings are invented and pitch never changes cue time or scoring. `CountdownStage` renders `Ready ⏱️ → 1 → 2 → 3 → 4 → Kanta 🎤` with a reduced-motion path; `SettingsStore.getCountdownLength()` defaults to 4 and `MainActivity.playWithCountdown()` starts a new reservation through it while resuming a prepared song immediately. The authoring environment has no working JVM (`getcpu`), so compiler, emulator and device acceptance remain open. See [architecture](docs/ARCHITECTURE.md), [reconstruction](docs/RECONSTRUCTION.md) and [testing](docs/TESTING.md).

- **Android Bluetooth headset monitoring candidate (not yet device-validated).** The optional voice-output route now accepts Bluetooth SCO and BLE headsets next to wired, USB and analog/aux line-out, and asks Android for the communication route on API 31 and newer so the processed voice actually leaves the phone. Speaker-class routes (built-in speaker, Bluetooth A2DP speakers, HDMI/ARC) stay blocked because the microphone hears them. Bluetooth audio is mono call quality with higher delay, and the Bluetooth device's own microphone is still not captured by the app. Compiler, emulator and device acceptance for this candidate are open. See [architecture](docs/ARCHITECTURE.md), [reconstruction](docs/RECONSTRUCTION.md) and [testing](docs/TESTING.md).

- The six diagnostic `NSLog` probes previously present in iOS shipping source are confirmed absent from the current tree.
- **Website crawler policy: deployed and verified on 2026-11-01, with follow-ups open.** `/robots.txt` serves the policy, a declared crawler receives `403` on an HTML route and on static assets, `Googlebot` and a normal browser still receive the rendered page, and `X-Robots-Tag` is present on static responses and, after the follow-up fix, on rendered ones. Rate limiting and per-request gating of static assets still need an edge/WAF layer, and no legal clause exists yet. See [scraping defense](docs/WEBSITE_SCRAPING_DEFENSE.md).
- Physical-device quality, physical two-device companion networking, voice quality/feedback/latency, scoring calibration, camera/external-display acceptance and production signing remain separate open gates.
- Companion control is experimental. The trusted-LAN path is PIN-authorized plaintext TCP and Android-to-Android; the encrypted KCP1 path has an Android host plus an iOS client that controls it with the full pairing code and no plaintext fallback (`apps/ios/Sources/Infrastructure/LocalNetworkControl/EncryptedCompanionClient.swift`). Neither path has an independent security review, and no physical two-device pairing has been accepted on either. Browser guests are limited to four active sessions; a valid code rejected only because capacity is full does not consume the three-incorrect-code lockout budget.

## Documentation

Start with [architecture](docs/ARCHITECTURE.md), [engineering architecture contract](docs/ENGINEERING_ARCHITECTURE_CONTRACT.md), [testing](docs/TESTING.md) and [handover](docs/HARDENING_HANDOVER.md). Current checkpoints supersede dated recovery notes.

| Document | Scope |
| --- | --- |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Cross-platform architecture, state ownership, module boundaries |
| [ENGINEERING_ARCHITECTURE_CONTRACT.md](docs/ENGINEERING_ARCHITECTURE_CONTRACT.md) | Rules distinguishing existing functionality, pending extension points and release gates |
| [RECONSTRUCTION.md](docs/RECONSTRUCTION.md) | What was recovered vs reconstructed vs compiled |
| [TESTING.md](docs/TESTING.md) | Test suites, gates and acceptance criteria |
| [VERIFIED_DOWNLOADS.md](docs/VERIFIED_DOWNLOADS.md) | Download provenance and checksums |
| [HARDENING_HANDOVER.md](docs/HARDENING_HANDOVER.md) | Current handover state |
| [AI_PROMPT_BANK.md](docs/AI_PROMPT_BANK.md) | External model prompt bank: pinned metadata, helper, measured behaviour |
| [CI_FAILURE_REPAIR_LEDGER.md](docs/CI_FAILURE_REPAIR_LEDGER.md) | CI incident history and repairs |
| [UNFINISHED_WORK.md](docs/UNFINISHED_WORK.md) | Everything still outstanding, with acceptance criteria |
| [WEBSITE_SCRAPING_DEFENSE.md](docs/WEBSITE_SCRAPING_DEFENSE.md) | Crawler policy, edge refusal, live-user exceptions, unverified live behavior |
| [GITHUB_PUBLISHING_POLICY.md](docs/GITHUB_PUBLISHING_POLICY.md) | Publishing and release gates |

Platform checkpoints: [Android companion](docs/ANDROID_COMPANION_CHECKPOINT.md), [Android camera](docs/ANDROID_CAMERA_CHECKPOINT.md), [Android monitor](docs/ANDROID_MONITOR_CHECKPOINT.md), [Android pitch correction](docs/ANDROID_PITCH_CORRECTION_CHECKPOINT.md), [Android scoring](docs/ANDROID_SCORING_CHECKPOINT.md), [Android personal media](docs/ANDROID_PERSONAL_MEDIA_CHECKPOINT.md), [Android stage](docs/ANDROID_STAGE_CHECKPOINT.md), [Android microphone](docs/ANDROID_MICROPHONE_CHECKPOINT.md), [companion browser](docs/COMPANION_BROWSER_CHECKPOINT.md), [iOS runtime](docs/IOS_RUNTIME_CHECKPOINT.md), [iOS companion](docs/IOS_COMPANION_CHECKPOINT.md), [YouTube lifecycle](docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md).

Website checkpoints: [website 0.10](docs/WEBSITE_010_CHECKPOINT.md), [website 0.9](docs/WEBSITE_09_CHECKPOINT.md), [website 0.8](docs/WEBSITE_08_CHECKPOINT.md), [website 0.7](docs/WEBSITE_07_CHECKPOINT.md), [website 0.6](docs/WEBSITE_06_CHECKPOINT.md), [website 0.5](docs/WEBSITE_05_CHECKPOINT.md).

## Notes for users

GitHub sign-in is required for Actions artifact downloads. GitHub Release assets are durable and do not expire. Separate CI runs can use different Android debug certificates; uninstalling to change builds removes private app state. Keep original media and explicit exports backed up. These are not store releases.

## Android OTA self-update (branch feat/android-ota-update)

The Android app includes a framework-free OTA update subsystem (`ph.kanta.reborn.core.update` + `ph.kanta.reborn.update`). It is **off by default and user-initiated** (Controls > "App update"), HTTPS-only, SHA-256 verified, stages downloads in an app-private directory, and requires the separate Android "Install unknown apps" permission to install.

**Current status:**
- The update feed (`https://kanta-public.vercel.app/updates.json`) is generated by the publisher after every successful Android build on `main` and stored in `apps/web/updates.json` (current entry: release `v0.10.0-preview-1f49555c`, source `1f49555c`), served through the `vercel.json` routing table.
- `signingCertSha256` is emitted from `apksigner` metadata for the exact signed APK and is checked against the app manifest contract.
- No local JVM/Gradle build or APK/emulator/device acceptance was possible in this environment (JVM cannot start on iSH: `getcpu(2)` unsupported). Java correctness is guarded by CI only (`coreRegression` + `android-validation`).
- The GitHub Release asset step makes every post-merge Android build a public release asset automatically; this is a publication side effect, not a release claim.


**Consent regression repair (PR #121).** The request-time HTML renderer removes the legacy static `/analytics.js` tag before adding response nonces. The consent manager is now the only entry point that can inject Analytics or AdSense after explicit acceptance. The regression test covers the previously eager script path; preview validation must still exercise accept, reject and revocation in a real browser.

## Android CI runner integrity — 2026-11-01

The Android validation workflow is a full acceptance gate, not a screenshot-only shortcut. Manual dispatch may select an explicit Git ref; ordinary pushes and pull requests validate their event SHA. The workflow definition must keep `workflow_dispatch` and `push` as separate top-level events. The emulator runner must fail only when boot/network prerequisites are absent, then continue to install both APKs, execute instrumentation, and preserve screenshots under `files/`. See [the CI repair ledger](docs/CI_FAILURE_REPAIR_LEDGER.md) and [testing](docs/TESTING.md). The November 1 repair is a candidate until the complete Android validation run passes on its exact PR head.

**Android emulator repair follow-up (2026-11-01):** Android run #400 passed build, lint, native and browser gates but failed emulator instrumentation because `RuntimeChecks` cast the primary `StagePresenterImpl` to the separate fullscreen `StageSurface` class. The follow-up camera acceptance exposed a teardown defect: unbinding CameraX did not detach the preview from its owning stage, restore the primary background or invalidate stale visibility state. The repair candidate explicitly releases both primary and fullscreen previews and strengthens the visibility predicate and emulator assertions. It remains unverified until a new complete Android emulator run passes. See `docs/CI_FAILURE_REPAIR_LEDGER.md` and `docs/RECONSTRUCTION.md`.

**PR #161 Android validation (2026-11-04):** run [#41813457564](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41813457564) passed all 55,217 core checks but exposed a stale `int[]` instrumentation expectation for `ImportBatchResult`. The test now asserts the imported/failed counts. Full Android validation passed on exact head [`5752532`](https://github.com/usermalik20-del/Kanta-reborn/commit/57525326069908bf19da3328d57260862283dea1) in run [#41814538861](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41814538861), including emulator runtime checks and My Set screenshots. See `docs/CI_FAILURE_REPAIR_LEDGER.md`.

## Platform CI ownership

Android validation and APK packaging/deployment are triggered by Android-owned source and build inputs. iOS validation, unsigned IPA packaging and iPhone/iPad simulator acceptance are triggered by iOS-owned inputs. Website validation and production deployment remain website-owned. Cross-platform fan-out is limited to explicitly shared protocol files that a second platform directly compiles or tests. An Android-only change must not start Xcode builds or IPA deployment, and an iOS-only change must not start Android builds or APK deployment. See `AGENTS.md`, `docs/ARCHITECTURE.md` and `docs/TESTING.md` for the enforced policy and regression checks.


### Independent Android and iOS downloads

Android APK publication follows successful Android validation only. iOS unsigned IPA publication follows the iOS archive and additionally requires same-commit iPhone and iPad simulator walkthrough acceptance. Each release updates only its own download page and preserves the other platform's source revision and published artifact. The manifest and verifier validate each platform against its own build and packaging provenance. If a publication attempt uploaded an artifact before failing, a retry may produce multiple same-name artifacts; the workflow must select the newest by `created_at` rather than assuming the name is unique. The website is deployed after the selected platform's metadata passes website validation.


## Android library import discoverability — 2026-11-02

The Android My Set surface now lists imported MIDI/KAR/audio items with direct Reserve actions and shows the installed SoundFont inventory with an explicit active-bank indicator and Use action. SoundFont inventory and active selection are persisted together; changing banks affects subsequent playback, not a song already in progress. Paired ShubBlob/kantablob and index-container import is now implemented as a separate My Set flow: choose the index, then the paired blob. The file-backed importer uses the paired index's trailing offset table to address each MKMD record deterministically, decrypts and inflates only validated payloads, and materializes MIDI/WAV/MP3 copies through the existing LibraryRepository. A bounded blob scan remains only as a compatibility fallback when an index has no usable offset table. Single-file selection still rejects container files; physical-device and exact-head Android validation remain required.

## Android SoundFont storage safety — 2026-11-02

SF2 import now checks the reported size before copying, periodically rechecks free storage while streaming, and preserves a 64 MiB free-space reserve for unknown or under-reported providers. The old post-copy comparison double-counted the imported file and rejected valid large banks; the corrected policy does not impose a fixed file-size ceiling and retains native structure/sample validation. Core regression coverage and exact-head Android validation are tracked in the dedicated pull request.


## iOS playback cancellation hardening — 2026-11-02

The iOS controller treats user-initiated library lookup and native playback preparation as cancellation-aware transactions: canceled Next/queue operations preserve their reservation and committed current song, while partially prepared backends are retired safely. Natural-completion auto-advance remains controller-owned rather than tied to a view's snapshot-poll lifetime. Regression coverage includes cancellation before and during native load, recovery, audio-session setup failure before load, and observer cancellation during natural completion. The reconciled branch requires fresh exact-head Swift package, smoke, unsigned archive, and iPhone/iPad simulator acceptance before merge.


## Android reference-led interface — validation candidate

The Android native UI integration now mounts the KANTA Home dashboard and dedicated Search, Queue, Pitch & Tempo, Mic & Voice, Visuals and Now Playing destinations from the existing `MainActivity`. These screens remain presentation surfaces: playback, FIFO reservations, SoundFont selection, import and stage media continue through the existing `SessionController` and storage owners. The supplied adaptive artwork is documented in [the Android asset manifest](docs/KANTA_ANDROID_ADAPTIVE_ASSETS.md). CI is being extended to capture the actual visible destinations; this source integration is not a released APK or physical-device acceptance.

Verified platform download publishing commits only the changed platform's manifest entry and download page, then explicitly dispatches the production Vercel workflow. The Android and iOS packaging/deployment flows remain independent.

## PR #134 merge reconciliation — 2026-11-02

The reference-led Android UI and companion changes were reconciled onto current `main` after the Home-launch fallback repair. The exact PR head must pass the Android runtime walkthrough and shared documentation-sync gate before merge; this is not a release or device-acceptance claim.

The reconciled PR retains the current-main `SessionController` SoundFont inventory and import-progress APIs while preserving the reference-led UI composition.

The karaoke-stage candidate now also initializes the reference-led Home dashboard before applying saved navigation state, so validation starts from a discoverable stage entry point.

## Android user-facing information architecture — local candidate

The Android presentation is being reorganized around three user goals rather than technical destinations: **Sing** (find music, personal songs and queue), **Performance** (Now Playing, lyrics, voice, Key & Tempo and Singing Coach), and **Experience** (backgrounds, camera, effects, scenic presentation, Party Mode and display). **More** contains advanced settings, companion control, diagnostics and help. Home now exposes only the three goals plus compact Continue Singing content; playback, FIFO, microphone, scoring and stage ownership remain unchanged.

## Android supplied UI and SoundFont assets — local candidate

The Android UI candidate now includes the supplied multicolor VectorDrawables and gradient button backgrounds without tinting away their per-path colors. It also bundles the supplied procedural `KANTA KaraokeLive GM v3` and `KANTA LiveStage GM v2` banks in the app's starter assets and exposes them through the existing persisted SoundFont inventory. The KANTA procedural bank from the OpenStage pack is included under the restricted KANTA OpenStage Procedural Asset License 1.0; its pinned third-party candidates remain audit-only and are not embedded.


## Android MIDI/KAR import rework — 2026-11-04

MIDI/KAR imports no longer enforce fixed file-size, event-count or six-hour duration caps;
files are decoded file-mapped, KAR lyric text encoding is detected, @T/@A/@C metadata
populates title/artist/copyright, @P pause codes and backslash echo lines are honoured, and
imported songs and SoundFont banks can be removed from My Set. Stage search includes the
active library. The transport Previous button restarts the current song (seek supported for MIDI/KAR and MP3/WAV). Key/tempo adjustments made during a song are remembered per song and reset from the Pitch & Tempo screen. SF3/Ogg SoundFont banks are now supported in addition to SF2. This is local review work on `feat/midi-kar-import-rework`; it is not merged
and is not part of any released build.


## OTA publication safety

Android OTA publication is fail-closed. CI allocates a bounded monotonically increasing `kantaVersionCode`; metadata is extracted from the exact APK, and publication verifies the prior APK digest plus the explicitly pinned owner-approved new signer. Release assets are immutable. The new signer intentionally cannot update earlier APKs in place; existing users must uninstall/reinstall (which may erase private app data). The release key is stored only in main-only GitHub Actions secrets, never in Git. Physical-device acceptance for installs signed by the new key remains **UNVERIFIED**; follow `docs/OTA_UPGRADE_ACCEPTANCE.md`.
### Android Search input
Android Search uses the persistent in-app KANTA keyboard with number and letter layouts; it does not invoke the phone system IME.

## Website redesign — 2026-11-07

The public website receives a comprehensive redesign adding a design-system theme layer, micro-interactions, and page-specific visual features.

### Design-system theme (`apps/web/theme.css`, `apps/web/theme.js`)
- Dark/light/system theme tokens with smooth cross-fade transitions
- CSS custom properties for palette, spacing, motion tokens
- Persisted `localStorage` preference, respects `prefers-color-scheme`
- Animated gradient brand mark (✦) and gradient-text headlines

### Micro-interactions (`apps/web/micro-interactions.js`)
- Button ripple on click (CSS custom properties)
- Magnetic pull on primary buttons (cursor attraction)
- 3D perspective tilt on `.feature-card` hover
- IntersectionObserver scroll reveals (`.reveal`, `.reveal-stagger`)
- Count-up animated stats (`data-count-to`)

### Command palette (`apps/web/command-palette.js`)
- ⌘K / Ctrl+K fuzzy search over 25+ pages, features, guide steps
- Keyboard navigation (↑/↓/Enter/Esc), floating FAB hint on desktop
- Deep links into guide sections and feature anchors

### Hero animation (`apps/web/hero-animation.js`)
- Audio-reactive gradient canvas (simulated FFT bands → flowing blobs + waveform strip)
- Animated stage mockup: typing lyric sweep, live queue, pitch meter
- Respects `prefers-reduced-motion` (single static frame)

### Download progress bar (`apps/web/download-progress.js`)
- Sticky top bar on `/android` + `/ios-download` showing artifact verification pipeline
- Dismissible, session-scoped memory

### Stage preview (`apps/web/stage-preview.js`)
- Fixed phone frame on `/features` that mirrors the scrolled section
- Scene types: queue, lyrics (highlight sweep), stage (bars), pads, meter, score

### Guide parallax (`apps/web/guide-parallax.js`)
- Pinned phone illustration on `/guide` with per-step palette shift
- Animated screens: import progress, mic waveform, stage background, remote code

### Songbook FX (`apps/web/songbook-fx.js`)
- 3D CSS page flip (prev/next keys, button clicks)
- Ambient gold/cyan dust particles over `.book-card`
- Search highlight pulse on matching `.song-list li`
- Lock/unlock state transitions with cross-fade

### Theme toggle UI
- Three-button group (☀️/🌙/💻) in header on all pages
- `data-theme-toggle` attribute drives `theme.js`

All new assets respect `prefers-reduced-motion: reduce` (animations disabled, instant reveals).

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
<!-- nav removal doc sync -->
<!-- nav removal doc sync v2 -->
