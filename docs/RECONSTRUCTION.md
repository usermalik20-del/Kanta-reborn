## K1 closed — distinct monitor effects (2026-11-11)

Small Room and Concert Hall have distinct tuning; legacy Room and Hall values remain
unchanged. Added all-pairs impulse-response regression coverage in the existing
framework-free core runner, preserving its reset and bounded-output checks. This is a
verified code/test repair on `dead199a1c31839fe955c5b6445a8e3a2c72a854`: [Android validation](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/42994654674) passed build/lint, 55,262 core regression checks and 430 emulator checks. No physical listening, signing, publication or device acceptance is implied.

## Bundled standby selection reload repair (2026-11-10)

Run `42797615517` compiled the lifecycle/frame-callback repair but correctly failed
runtime acceptance. Further tracing found that `restoreStage()` wrote
`bgv_default.video`, then `StageMediaStore.load()` rejected it because its validation
accepted only SHA-256-named imports. The store now also accepts exact bundled catalog
IDs with the `.video` suffix and requires the file to exist; arbitrary filenames,
unknown IDs and path traversal remain rejected. Startup scenic initialization no longer
replaces an already-restored non-scenic selection.

Runtime checks now explicitly reload the persisted video selection and check the BGV
source/texture before waiting for actual frame updates, giving actionable failures.
The new source-contract regression fails against the old filename validator and passes
with this repair. Exact-head emulator acceptance is still required; no release or merge
has been performed.

## Android standby video lifecycle and truthful runtime evidence (2026-11-10)

Follow-up to the ordering repair in PR #192: `MainActivity.onStart()` now registers
its session listener (which synchronously creates the primary stage) before activating
visuals. Previously the first-launch activation saw a null presenter and left the newly
created `BackgroundStage` inactive, so it never prepared standby video. Existing
stop/start and fullscreen visibility ownership remain unchanged.

Standby live-frame evidence now advances only from `TextureView.onSurfaceTextureUpdated`
in the embedded and fullscreen renderers, not from playback or UI refresh timers.
The embedded renderer gates notification on active BGV media; the fullscreen renderer
requires an active foreground prepared player. Frozen/absent video cannot pass merely
because a timer runs. Playback, queue and media selection stay controller-owned.

The Android emulator runtime step is now blocking: instrumentation failure fails the
job. Diagnostic collection and artifact upload retain `if: always()` so failures still
preserve logs and available artifacts. This supersedes the historical non-blocking
runtime-step description below; no signing, merge or publication authority is changed.

Three source-contract regression guards failed before this repair and pass afterward:
first-launch activation order, actual-texture frame evidence, and CI failure propagation.
Local static/Python checks are not emulator acceptance; exact-head CI must prove the
standby assertion and all nineteen UI captures before claiming runtime verification.

## Runtime evidence regression: standby checks ran before the stage was resolved (2026-11-10) — fixed

The Android emulator run attached to merge commit `818a73f` produced **one screenshot**
(`browser-remote.png`) instead of the nineteen UI captures the artifact list declares. It
happened because a check introduced in that same commit ran before the view it inspects was
assigned, so the instrumentation process crashed on its first assertion — before the first
`capture()` call — and every acceptance step downstream was skipped.

### Root cause

`RuntimeChecks.onStart()` called `stageStandbyChecks()` three lines **above** the statement
that assigns `persistentPrimaryStage`. That helper reads the field directly:

    View stage = persistentPrimaryStage;
    check(stage != null, "Standby check needs the persistent stage");

so it threw `AssertionError: Standby check needs the persistent stage` on the main thread.
Logcat confirms the failure and the stack:

    E AndroidRuntime: FATAL EXCEPTION: main
    E AndroidRuntime: Process: ph.kanta.reborn, PID: 3001
    E AndroidRuntime: java.lang.AssertionError: Standby check needs the persistent stage
    E AndroidRuntime:   at ph.kanta.reborn.RuntimeChecks.check(RuntimeChecks.java:34)
    E AndroidRuntime:   at ph.kanta.reborn.RuntimeChecks.lambda$stageStandbyChecks$252(RuntimeChecks.java:1280)

`adb shell am instrument` then reported `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`,
`tools/run-android-runtime.py` raised *"Runtime instrumentation did not report acceptance"*,
and the screenshot loop after it never ran.

### Why CI still showed green

The step *"Android emulator runtime checks"* carries `continue-on-error: true`
(`.github/workflows/android-validation.yml:167`), so the job and the run still concluded
`success`, and the artifact step uses `if: always()`. The evidence directory therefore
uploaded with the log, the two APKs, lint reports and `browser-remote.png` while silently
missing every UI screenshot. For comparison, run `798` on `6fd4b0a8` (before this work)
logged `KANTA runtime: 416 checks passed` and read all nineteen captures.

### Fix

`RuntimeChecks.onStart()` now resolves `persistentPrimaryStage` and calls
`assertPersistentStage(null)` **first**, then runs `stageStandbyChecks()`, then
`capture("home_dashboard.png")`. Order matters because the standby assertions inspect the
resolved view rather than looking the stage up themselves. The redundant
`await(() -> session.stageReady, ...)` that preceded the call was removed; `stageReady` is
already awaited at line 108.

### Regression guard

`tools/tests/test_android_runtime_runner.py` gains
`test_standby_checks_run_after_the_stage_view_is_resolved`, which asserts that the
`persistentPrimaryStage` assignment appears before `stageStandbyChecks();` inside `onStart()`,
and that both standby helpers still read the field. Verified by reintroducing the original
ordering: the test fails, and passes again once the fix is restored.

### Consequence for the release page

The `00000` standby stage, the live-BGV frame counter and the control deck are still only
*statically* verified on this source. The emulator evidence for them must be re-collected from
a green run after this fix; until then the website must not present those captures as
existing. The site copy already states that the standby stage and control deck are asserted by
emulator checks without claiming screenshots, which remains accurate.

## Website: feature copy resynced with the current preview (2026-11-10) — local candidate

The public site's feature pages had drifted from the app. They described the build at
`6db012c3`, 182 commits behind `main`, and several features that shipped since were absent
from every page. This pass re-anchors the copy to the current source without overclaiming.

### What was stale
- `apps/web/features.html` still cited `6db012c3` for its "Android karaoke stage preview"
  section and listed no feature newer than the SPICE pitch work.
- `apps/web/android.html` headed its release notes "New in this preview (source 35bf48b)",
  a revision that is neither the published build nor the current source. The download
  section above it is correct and generated by `tools/publish-platform-download.py`.
- The Home page, the features table and the how-to guide did not mention the standby stage,
  the bundled background video, the control deck, on-stage QR pairing or the one-tap
  FluidR3 GM download.
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar

### Features now documented (each verified against source first)
- **Standby stage** — `stage/StagePresenceOverlay.java`: the `00000` prompt, `KARAOKE READY`
  badge and hint, visible only while nothing is playing.
- **Bundled background video** — `BundledBgvCatalog.java` ships five clips; auto-shuffle
  defaults on with a 300 s interval (`SettingsStore.getBgvAutoShuffle`, `getBgvShuffleInterval`).
- **Control deck** — `ui/KantaControlDeck.java`, opened from the header word mark
  (`kanta.header.logo` -> `toggleControlDeck`), with the master volume dial and 22 controls.
- **On-stage QR pairing** — `MainActivity.kantaConnect()` renders the Kanta-Connect QR on the
  persistent stage; address and code stay separately copyable.
- **FluidR3 GM download** — `SessionController.downloadFluidR3GM()`, ~140 MB with progress and
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar
  a skip when the bank already exists.

### Claims checked and left unchanged
The comparison table's existing statements were re-verified rather than rewritten: companion
encryption (KCP1 AES-GCM, `core/CompanionProtocol.java`), the pairing-code and session TTLs
(5 and 10 minutes, `CompanionControl.java`), the five score bands (`ScoreResultTier.java`), the
ten scenic presets, the eight party pads, the ten monitoring names, the ten scenic clip
descriptors with no in-app picker (`ScenicCatalog.BUILT_IN`, 10 entries, no UI caller for
`downloadScenic`), and the SPICE model hash and TFLite runtime, which are unchanged and still
match `assets/spice.tflite`.

### Sitemap
`lastmod` for `/`, `/android`, `/guide` and `/features` advanced to 2026-11-10; no route was
added or removed, so the guard's route set is untouched.

## Android: karaoke-rooted UI, stage standby, and incremental versions (2026-11-09) — local candidate

Fixes real-world UI ambiguity across the Android app, keeps the product unmistakably a karaoke
app, makes the empty stage look like a karaoke machine waiting for a selection, and stops every
preview release from sharing one version.

### Stage standby is karaoke, not wallpaper
- New `stage/StagePresenceOverlay.java`: when no song is on the stage it draws a hardware-remote
  style `00000` song-code prompt, a pulsing `KARAOKE READY` badge, an animated equaliser, and the
  single instruction "Enter a song code to sing / or find a song in Sing".
- `StagePresenterImpl` adds it directly above `BackgroundStage` and below every functional overlay,
  and binds it from the truthful empty-playback state (`session.current == null && !isPlaying`), so
  live lyrics, countdown, score and search always win.
- The background is the bundled BGV video, so the standby stage is moving video rather than a
  frozen still. `StagePresenceOverlay.liveFrame()` publishes a monotonic counter that only advances
  while a BGV `TextureView` is attached, available and the visual layer is active — the runtime
  check proves the standby stage is showing captured video, not a wallpaper.
- `stage/LyricStage.java` gained `setStandby(boolean)` and no longer paints a placeholder over the
  standby card. Its own placeholder is now the truthful "No timed lyrics for this song" (only
  reached when a song IS loaded but has no timed cues).
- `StageSurface` (fullscreen/external stage) shows the same standby card and reports live frames.
  `StageView` gained `setStandbyMode(boolean)` so the legacy "Your music. Your stage." /
  "Reserve your next song in CONTROL" copy cannot stack over the standby card.

### The app says karaoke
- Top app header now carries the brand block that used to sit on the dashboard: the KANTA wordmark
  with the `SING MORE. TOGETHER.` tagline, plus Help. The baybayin subtitle (`KANTA · ᜃᜈ᜔ᜆ`) and the
  second in-dashboard logo/tagline were removed, so there is exactly one brand lockup on screen.
- The header logo (`kanta.header.logo`) still toggles the control deck.

### Ambiguity and consistency fixes
- `InlineKeyboard`: `CLR`/`RES` became the standard ⌫ (backspace) and `OK`, on both the numeric and
  letter layouts.
- Search/keyboard toggles read `Letters` / `Numbers` instead of the cryptic `ABC` / `123`
  (Home and the Search destination). The decorative magnifier no longer advertises itself as an
  unlabelled image; it is described as "Search".
- Home typing now opens the real Search destination with the term applied (`pendingSearchQuery`)
  instead of silently driving a stage overlay. Search is a proper destination with back navigation,
  and nothing is layered over the karaoke standby stage.
- Mic & Voice: status and action can no longer contradict. A requested-but-unconfirmed capture
  reads "Starting microphone…" with a "Starting…" button, not "Microphone off" above "Turn on
  microphone".
- Queue: the empty state reads "Queue is empty. Add a song to start your karaoke set." and the
  action is "+ Add Songs to Queue".
- Now Playing: one truthful empty state — the `Nothing playing` headline plus `Reserve a song to
  begin`, with the lyric area showing "KANTA Karaoke · your stage is ready" instead of a competing
  "No timed lyrics loaded".
- Pitch & Tempo: the non-interactive monospace `♭♭ ♭ 0 ♯ ♯♯` row became a readable semitone legend
  ("Semitone scale · ♭♭ lower · 0 original · ♯♯ higher") so it no longer reads as tiny controls.

### Incremental versions
- `apps/android/app/build.gradle` no longer freezes `versionName` at `0.10.0-reconstruction`.
  `versionCode` keeps the CI allocation (bounded `kantaVersionCode`, workflow run number) and
  `versionName` is now derived from the same allocation:
  `<kantaVersionBase>.<versionCode>-preview` (base stays on the current line, default `0.10.0`), with
  `-PkantaVersionName` available to pin an exact name. Shape stays inside the published
  `[A-Za-z0-9.+-]{1,64}` rule enforced by `UpdateManifest` and `tools/prepare-update-feed.py`.
- Result: every published preview now has a distinct `versionName` and therefore a distinct
  durable release tag (`v<version>-preview-<sha8>`), instead of all sharing `v0.10.0-preview-*`.
- `tools/verify-ota-publication.py` is unchanged: it still pins `versionCode` to the CI allocation
  and requires strictly increasing versions and descendant source history.

### Validation
- Local guards: java-structure (146 files), android workspace contract 55/55, product branding 4/4,
  companion interface, ios presentation contract, verify-website, verify-test-layout, and the
  161-test Python suite — all PASS.
- Not run here: Android compile/lint, emulator `RuntimeChecks`, iOS build, signing, deployment.

## Android: Kanta-Connect QR Code on Stage (2026-11-09)

Moved Kanta-Connect pairing QR code from an AlertDialog to the persistent karaoke stage overlay:
- `StagePresenter` interface gains `showKantaConnectQR`/`hideKantaConnectQR` with `KantaConnectQRInfo` data class
- `StagePresenterImpl` adds a `QrCodeView` overlay on the stage with host address, QR code, and grouped pairing code
- `MainActivity.kantaConnect()` now calls `stagePresenter.showKantaConnectQR()` instead of showing an AlertDialog
- Tap the stage overlay to dismiss; hosting continues until "Stop Kanta-Connect"
- Auto-hides on playback start (`prepare()`/`stopPlayback()`) and when Kanta-Connect is stopped
- `RemoteStatusView` on stage reflects ADVERTISING/CONNECTED/DISCONNECTED state
- Uses existing `QrEncoder` (byte mode, ECC level M, versions 1-10) and `QrCodeView` (4-module quiet zone, integer module scaling)
- Enables scan-to-pair workflow with Kanta-Connect phone app — no manual 32-char code entry needed

<!-- Documentation sync: 2026-11-09 -->
## Android: Fix FluidR3 GM Download String Literal (2026-11-09)
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar

Fixed unclosed string literal in SessionController.readAssetText():
- Fixed '\\n' escape sequence for newline in StringBuilder
- Required by Java compiler for proper string literal escaping
- SessionController now compiles correctly with downloadFluidR3GM()
## Android: Fix FluidR3 GM Download Compilation (2026-11-09)
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar

Fixed FluidR3 GM SoundFont download compilation errors:
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar
- Moved full downloadFluidR3GM() logic from MainActivity to SessionController
- MainActivity now delegates via session.downloadFluidR3GM()
- Added formatBytes() and readAssetText() helper methods to SessionController
- Kept FLUIDR3_GM_NAME constant in MainActivity for UI display
- Fixes 'cannot find symbol' errors for io, repository, main, FLUIDR3_GM_SIZE, FLUIDR3_GM_URL, FLUIDR3_GM_LICENSE
- Download logic uses session's io executor, repository, and main handler
- Progress updates via session.message and publish()
- Validation with NativeSoundFont.open() before saving
- License file (fluidr3gm-license.txt) saved alongside SoundFont
## Android: Add FluidR3 GM Download to SessionController (2026-11-09)
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar

Added downloadFluidR3GM() method to SessionController:
- Downloads FluidR3 GM SoundFont from official fluidsynth mirror (ftp.osuosl.org)
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar
- Visual progress: percentage + bytes downloaded (updates session.message)
- Validates with NativeSoundFont.open() before saving
- Saves license file (fluidr3gm-license.txt) alongside SoundFont
- Not shipped with app - downloaded on demand to user's local media folder
- Reuses existing import progress UI (session.message + publish())
- Delegated from MainActivity via session.downloadFluidR3GM()

This refactoring fixes 'cannot find symbol' errors by moving download logic to SessionController where io, repository, and main are accessible.
## Android: Move FluidR3 GM Download to SessionController (2026-11-09)
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar

Refactored FluidR3 GM download logic from MainActivity to SessionController:
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar
- Fixes 'cannot find symbol' errors for io, repository, main variables
- MainActivity now delegates to session.downloadFluidR3GM()
- Keeps FLUIDR3_GM_NAME constant in MainActivity for UI display
- Download logic uses session's io executor, repository, and main handler
- Progress updates via session.message and publish()
- Validation with NativeSoundFont.open() before saving
- License file (fluidr3gm-license.txt) saved alongside SoundFont
## Android: Fix MainActivity Syntax Errors (2026-11-09)

Fixed two compilation errors in MainActivity.java:
1. 'else' without 'if' - moved "Download FluidR3 GM" button outside the for loop in mySet()
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar
2. Unclosed string literal in readAssetText() - fixed newline escaping (\\n)

Both fixes address Android build compilation errors that were causing CI failures.
## Android: FluidR3 GM Download Option (2026-11-09)
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar

Added FluidR3 GM SoundFont as downloadable option in My Set → SoundFonts:
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar
- Button "Download FluidR3 GM (~140 MB)" appears after listed SoundFonts
  * **MuseScore General** (~216 MB) – richer orchestral tones, direct download
  * **SGM-v2.01 Yamaha Grand** (~528 MB) – studio-grade piano, bass, guitar
- Downloads from official fluidsynth mirror (ftp.osuosl.org)
- Visual progress: percentage + bytes downloaded (updates session.message)
- Validates with NativeSoundFont.open() before saving
- Saves license file (fluidr3gm-license.txt) alongside SoundFont
- Not shipped with app - downloaded on demand to user's local media folder
- Reuses existing import progress UI (session.message + publish())
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

## Android home hero greeting/tagline removed (2026-11-07)

The Home hero (dynamic greeting + `Music brings people together.` tagline banner) was an awkward duplicate of the KANTA header `SING MORE. TOGETHER.` shown directly above it. It is removed, leaving header → search as the dashboard start; the 142dp vertical space is reclaimed. Runtime tests updated to assert the stable search hint.

The duplicate-dock issue (HomeScreen's own bottom nav vs the persistent MainActivity dock) was resolved by removing the HomeScreen dock, leaving the persistent dock as the single nav bar.

The duplicate-dock issue (HomeScreen's own bottom nav vs the persistent MainActivity dock) was resolved by removing the HomeScreen dock, leaving the persistent dock as the single nav bar.

Also removed on the same pass: the quick-cards row (My Set/Favorites/Recent -> one destination) and the header Settings gear (already covered by the dock's More tab).

Persistent Home search with numeric/alpha inline keyboard replaces the static search placeholder button and hidden workspace search menu, projecting live search results directly onto the stage.

## Android premium icon system — local candidate (2026-11-07)

The icon layer was rebuilt as one coherent outline family derived from the KANTA Premium icon pack (24x24 viewBox, 1.8 stroke, cyan `#29D9FF`, one violet `#7657FF` detail). It replaced the mixed filled/gradient glyphs and the shared Settings icon reused by Diagnostics. New `res/drawable/kanta_*_color.xml` vectors (18 new + semantic rewrites) cover the five-tab dock (home/sing/performance/experience/more), the More page (settings sliders, companion phone+wireless, diagnostics waveform, help circled-?), and Sing/Performance/Experience destinations. Navigation is one floating dock; GroupScreen cards gained the soft edge-lit treatment.

# Reconstruction evidence and work ledger

## Android persistent-stage navigation — 2026-11-06 local candidate

Owner requested Android-only persistent karaoke playback above all menu/control content, including realtime background previews. Source inspection found `MainActivity.render()` hid the persistent stage for Home/reference destinations and Now Playing constructed another `StageSurface`. This candidate retains the primary stage and moves menu utilities below it, removing the duplicate Now Playing renderer and its camera handoff. Instrumentation is extended to check presenter identity, visible lower-panel placement and continued playback/backend/reservation identity during navigation. No iOS code, workflow, website, signing or publishing changes are part of this work. Local Android doctor reports missing Java 17, platform, AAPT2, D8 and apksigner; no APK build or emulator/device acceptance is claimed.

The owner clarified that the physical-party-speaker mock is a special screen, not the main UI, and that its reference labels are placeholders. The candidate therefore keeps existing destinations and opens a hardware-inspired lower-panel Control Deck only when the KANTA logo is tapped. The same primary stage and five-tab navigation remain visible. The center dial controls existing master volume; surrounding and asymmetric controls route to KANTA Search, Queue, Visuals, Controls, Pitch, Coach, playback, microphone/monitor, camera and key/tempo behavior. The redundant global utility rows and Now Playing sub-navigation are removed.

## Android large SoundFont RAM ceiling — local candidate

Owner requested a larger budget for full-tail SoundFonts. Changed only the native tracked-allocation ceiling from 128 MiB to 1536 MiB, with existing validation, overflow rejection and allocation-failure cleanup retained. Added lightweight exact-cap, over-cap, grow/shrink realloc and SIZE_MAX accounting cases to the existing native harness; they simulate live allocation totals rather than allocating gigabytes. Local C11 compilation with strict warnings passed; starter-tone pitch/transposition, truncation rejection, six malformed-table cases and 2000 mutation checks passed, and allocation accounting returned to zero. Layout preflight passed. Local Android doctor reports missing Java 17/platform/AAPT2/D8/apksigner; APK/lint/emulator and ASan/UBSan are not established by this run. The large external piano remains unbundled and playback/device acceptance is open. Raising a software cap cannot fix the local iSH malloc/mmap capacity limitation or guarantee low-memory Android TV operation. This is not deployed or released.

## Karaoke Stage v2 — merged Android preview

PR [#155](https://github.com/usermalik20-del/Kanta-reborn/pull/155) merged as `6db012c34f97b64b4692e09ee709fe4e439e2a71` on 2026-11-03, superseding PR #153. This is Android preview functionality, not a new tagged/store release or physical-device acceptance. It preserves the existing Home destinations, camera lifecycle and SoundFont inventory.

### Vocal monitor effects

`VocalMonitorEffects.Mode` exposes ten selectable names: Dry, Small Room, Room, Studio, Concert Hall, Hall, Cathedral, Plate Reverb, Slap Echo and Warm Tape. Room/Small Room and Hall/Concert Hall share tuning: there are eight canonical modes including Dry, not ten distinct acoustic presets. Dry bypasses the wet effects and clamps output; do not describe every mode as passing through tanh processing. Monitoring remains opt-in and experimental; physical sound quality and latency are unverified. Effects alter monitored output, not dry scoring input.

### Party sounds and scoring

The eight bundled recorded pads are Applause, Cheering, Drum Roll, Air Horn, Laughter, Ta-Da, Ding and Sad Trombone. The five presented score bands are Excellent Singer (100), Great Singer (90–99), Good Singer (80–89), Rising Star (65–79) and Keep Practicing (50–64). `ScoreSink.overall()` floors the displayed result at 50; `rawOverall()` retains the underlying 0–100 estimate. This is a presentation floor, not a measured minimum performance or a guarantee that every song receives a score.

`ScoreRevealPlan` defines a 1900 ms drumroll, 900 ms count-up and 420 ms label fade. `StagePresenterImpl` schedules the reveal; `ScoreOverlay` implements count-up and an instant-final-score reduced-motion path. Device timing, cue audibility and reduced-motion acceptance remain open.

### Scenic video library — partial integration

`ScenicCatalog` contains ten remote clip descriptors with source and licence attribution; these are separate from the bundled scenic still images. `ScenicClip` records HTTPS URLs, expected size and container, but no trusted expected SHA-256. `BgvLibrary` bounds downloads and records a computed SHA-256 in its private index; recording that digest is **not** verification against a trusted source checksum.

`SessionController` exposes download/status/list/delete APIs. The Scenic dialog lists already-downloaded entries with attribution and use/remove actions, but does not expose a catalogue download picker. Selection requests `SessionController.importStage(..., video=true)`; successful import/playback has not been device-accepted. Do not advertise ten ready-to-download videos, streaming backgrounds or verified network downloads. See `docs/UNFINISHED_WORK.md` section K for the remaining acceptance work.


---

## Android OTA self-update — active candidate (2026-11-04)

The framework-free OTA update subsystem (`ph.kanta.reborn.core.update` + `ph.kanta.reborn.update`) is integrated into the Android app. It is user-initiated, off by default, HTTPS-only, SHA-256 verified, app-private staging, and installation requires the separate "Install unknown apps" permission.

### Core contract (framework-free Java)

- Package `ph.kanta.reborn.core.update`: `UpdateJson` (strict bounded JSON; 256 KiB ceiling; rejects duplicate keys, raw control chars, unpaired surrogates, leading zeros, over-deep nesting), `UpdateManifest` (feed format "kanta-update-feed" version 1, channel "stable-reconstruction", publishedAt ISO-8601 UTC, sourceCommit 40-hex, android entry with versionCode>=1, versionName shape, minSdk must be at least 26, apkUrl policy, apkBytes 1..256 MiB, apkSha256 64-hex, optional signingCertSha256, notes<=1024, mandatory boolean), `UpdatePolicy` (pinned feed https://kanta-public.vercel.app/updates.json; HTTPS-only; durable host allowlist: github.com/usermalik20-del/Kanta-reborn/releases/download/<tag>/<file> plus redirect targets release-assets.githubusercontent.com and objects.githubusercontent.com; refuses Actions artifact URLs, http, userinfo, query/fragment on canonical; GitHub path case-sensitive), `UpdateDigest` (SHA-256 hex; .part staging names), `UpdateDecision` (NOTHING_PUBLISHED / DEVICE_TOO_OLD / ARTIFACT_NOT_DURABLE / OLDER_OR_SAME_VERSION_PUBLISHED / DIGEST_MISMATCH / DOWNLOAD_REFUSED / APPLY).
- Compiled by empty-classpath `compileCoreRegression` / `coreRegression` Gradle tasks.
- Validated by `UpdateContractRegressionTests` (registered in `CoreRegressionTests`, runs in `coreRegression` task) and `tools/tests/test_android_update_contract.py` (9 unittest cases).

### Android runtime integration

- `AppUpdateManager`: single-worker executor; HttpURLConnection feed fetch with strict UTF-8; resumable Range download into app-private files/update/ with .part files, HTTP 200/206/416 handling, 256 MiB ceiling; SHA-256 verify; atomic rename to kanta-update-<sha12>.apk; cancel() and close(); Listener onState/onProgress/onReady.
- `UpdatePackageInspector`: PackageInfoCompat installedVersionCode, optional signer SHA-256, canRequestPackageInstalls, ACTION_MANAGE_UNKNOWN_APP_SOURCES, FileProvider ACTION_VIEW install intent.
- AndroidManifest.xml: + android.permission.REQUEST_INSTALL_PACKAGES; non-exported androidx.core.content.FileProvider (authority ${applicationId}.fileprovider, grantUriPermissions, res/xml/update_paths.xml exposing only files-path update/).
- app/build.gradle: + implementation 'androidx.core:core:1.13.1'.
- MainActivity: Controls > "App update" dialog (check/status/progress/install); manager lazily created; onStop cancels; onDestroy closes; diagnostics gained an "update" status line.

### Publisher tooling

- tools/prepare-update-feed.py: generates apps/web/updates.json from apps/web/downloads.json (android source SHA) + verified-downloads/android/build-info.json (file name, sha256, bytes) + git show <source>:apps/android/app/build.gradle (versionCode/versionName/minSdk); durable apkUrl v<version>-preview-<sha8>/KANTA-Android-preview-<sha8>.apk; FeedError refusal, never writes on refusal, exit 1.
- tools/verify-android-runtime-package.py: permission assertion is now the exact set {CAMERA, RECORD_AUDIO, INTERNET, REQUEST_INSTALL_PACKAGES}.
- apps/web/vercel.json: static route ^/updates\.json$ -> /updates.json, Cache-Control public, max-age=0, must-revalidate, before page rewrites.
- .github/workflows/platform-download-publishing.yml: Android-only step creating/updating (--clobber) GitHub Release tag v<version>-preview-<sha8> with the APK as durable asset; Android runs tools/prepare-update-feed.py and stages apps/web/updates.json into the downloads commit.

### Explicit non-claims

- Feed state: apps/web/updates.json holds the newest published Android build (current entry: release v0.10.0-preview-1f49555c, source 1f49555c); the publisher regenerates it after every successful Android build on main and the endpoint is served through the vercel.json routing table.
- signingCertSha256 supported by the contract but NOT yet published by feed tooling.
- No local JVM/Gradle build, APK, emulator, runtime or physical-device acceptance ran (JVM cannot start on this iSH emulator: getcpu(2) unsupported). Java correctness is guarded by CI only (coreRegression + android-validation + android runtime checks).
- The GitHub Release asset creation makes every post-merge Android build a public release asset automatically.
- Update is user-initiated, off by default, HTTPS-only, SHA-256 verified, app-private staging, install needs separate "Install unknown apps" permission.

Pending CI: the feature will be validated by the next main-branch Android validation run after merge.

## Android TinySoundFont vendor sync — pinned fork release a272fe30 (2026-11-03)

`apps/android/app/src/main/cpp/vendor/tsf.h` moved from the bare upstream v0.9 header to fork `usermalik20-del/tinysoundfont` revision `a272fe30` (GitHub release `kanta-vendor-a272fe30`): hardening patch, Catmull-Rom interpolation (default; the linear path is still selectable at compile time), ramped low-pass, the full SF2 modulator model, chorus/reverb effect stages, the SF3 odd-chunk pad fix and the published `tsf_playback.h` single-owner wrapper. `PROVENANCE.json` pins the fork revision and sha256 of all four shipped files; no CMake or JNI edit was required because the new app-facing engine APIs are present but unwired. The stale boundary line in `apps/android/README.md` "modulators and effects are not implemented by the selected renderer" was rewritten: the engine implements them, KANTA has not exposed them, and poly (key) pressure remains untracked. Engine-level suites live in the fork CI (all seven lanes green on the pinned commit, audio-hash baselines strictly compared); KANTA re-runs the pinned header through its existing `-Werror` native regression. Not claimed: no user-listened audio comparison on a real device and no new KANTA app controls.

## Android library import discoverability — active repair candidate (2026-11-02)

User testing of the latest preview found that SF2 copy progress reaches 100% but the UI offers no discoverable way to apply the bank; the same post-import visibility concern applies to MIDI/KAR and legacy ShubBlob files. Source review confirmed that LibraryRepository already persisted songs and only one active SF2 reference, while My Set rendered neither imported-song rows nor a SoundFont inventory/selector. The legacy Meg/ShubBlob path is separate from the active LibraryRepository and SessionController: SqliteSongCatalog.importFromLegacyContainer(...) is not wired into the UI, and its decoder has only synthetic-fixture evidence. This repair adds a persisted SoundFont inventory and explicit next-song bank selection, exposes imported catalog songs with Reserve actions in My Set, and explained, at the time of that repair, that legacy containers were not supported by the preview rather than misclassifying a raw copy as a successful import. Paired `kantidx`/`kantablob` import landed afterwards (`MainActivity.java:2110`, `SessionController.importLegacyPair`); real-container decoding on a device is still unverified. The safe branch is fix/android-library-import-discoverability-20261102; The first exact-head Android run passed build, lint, core regressions, native sanitizers, Chrome and browser interoperability. The emulator then passed the new My Set/SoundFont UI interactions and failed a later pre-existing audio-import assertion because its expected catalog count did not include the two new imported test fixtures; the first correction to six still over-counted because the fixture folder already contains the same WAV later imported by the controller test; the expected total is now five, matching the three starters plus the imported KAR and WAV. The final count correction is five (three starters, one KAR and one WAV). The next emulator run then exposed a separate test-harness state leak: the My Set navigation test left the activity on My Set, so a later voice-settings assertion dereferenced a missing Control-only view. The instrumentation now restores Control immediately after the My Set assertions, and the Android validation artifact retains dedicated My Set/import and active-SoundFont screenshots. Both fixture-count and tab-state failures are recorded in the CI repair ledger. Re-run exact-head Android emulator checks and documentation validation before merge or preview publication.


## Website crawler and scraping defense — merged and deployed (2026-11-01)

Branch `feat/website-scraping-defense`, PR [#125](https://github.com/usermalik20-del/Kanta-reborn/pull/125),
**merge commit `ccd09fd`** on `main`. Website validation [run 41262424926](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41262424926),
verified-download packaging [run 41262424946](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41262424946)
and the production Vercel deploy [run 41262424955](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41262424955)
all succeeded on that SHA. The pull-request head `1d85d73` passed the full website job, which
includes `node --test tools/tests/test_site_page_handler.test.js` — the JavaScript assertions
that could not be executed in the authoring environment did run in CI and passed.

Three enforcement points express one crawler policy. `apps/web/robots.txt` declares it: an
allow group for search engines and unfurlers, a `Disallow: /` group for AI training and
data-mining crawlers, a separate allow group for live-user AI fetchers, an allow-by-default
fallback group that disallows only `/api/`, the canonical `Sitemap:` line and a
`Content-Signal: search=yes, ai-train=no, ai-input=no` preference. `apps/web/vercel.json`
publishes `X-Robots-Tag: noai, noimageai` and a `routes` entry that refuses the declared user
agents with `403` before the page rewrites. `apps/web/api/page.js` adds `isBlockedCrawler()`,
exported for tests, plus a fail-open `crawlerGate()` applied before `fs.readFile`.

**Production verification measured on that deployment.** `/robots.txt` returns `200` with the
policy, the `Content-Signal` line and the canonical sitemap. A `GPTBot` user agent receives
`403` on `/`, on `/style.css` and on `/downloads.json`; `Googlebot` and an iPhone Safari user
agent receive `200` with rendered HTML. `X-Robots-Tag: noai, noimageai` is present on static
responses and was **absent on the ten HTML routes**, because a `routes` rewrite replaces the
response and the `headers` rule does not reach it. That gap is repaired on branch
`fix/website-robots-tag-rendered-pages` (the renderer now sets the header itself) and both the
verifier and the contract test fail if the line is removed; the verifier scopes its search to
the handler body because the refusal path sets the same header text and an unscoped search
passed with the rendered-page header deleted.

**Deployed and verified: the declared-agent refusal, the policy file and the static-asset
header. Not verified: the rendered-page header on a production response after the follow-up
fix, and anything about a browser-impersonating client.** Rate limiting and rate-based
protection remain unimplemented; see [docs/WEBSITE_SCRAPING_DEFENSE.md](WEBSITE_SCRAPING_DEFENSE.md)
and section J of `docs/UNFINISHED_WORK.md`.

## KANTA design-system UI refactor — 2026-11-01

Android UI refactor implementing the KANTA dark entertainment design system faithfully against the
supplied concept. Presentation-only change; all backend logic (playback, queue, persistence, sensor,
companion) remains owned by `SessionController` and `StagePresenterImpl`.

### Files added

- `apps/android/app/src/main/java/ph/kanta/reborn/ui/KantaTheme.java` — design tokens (colors, radii,
  gradient policy, helper factories)
- `apps/android/app/src/main/java/ph/kanta/reborn/ui/components/` — 11 reusable components
  (KantaCard, KantaButton, KantaIconButton, KantaSearchBar, KantaProgressBar, KantaStepper,
  KantaSectionHeader, KantaTabSelector, KantaSongArtwork, KantaMediaRow, KantaTransportControls)
- `apps/android/app/src/main/java/ph/kanta/reborn/ui/` — 8 screens
  (HomeScreen, KantaNowPlayingScreen, KantaSearchScreen, KantaQueueScreen, KantaPitchTempoScreen,
  KantaMicVoiceScreen, KantaVisualsScreen, KantaFullscreenStage)

### Files modified

- `apps/android/app/src/main/java/ph/kanta/reborn/MainActivity.java` — buildWindow uses KantaTheme
  colors and adds a contentContainer overlay for design-system screens; screen management methods
  wire each screen's Listener into existing SessionController / StagePresenterImpl methods; render()
  updated to reflect current session state on active screens
- `apps/android/app/src/main/assets/companion/index.html` — restyled to KANTA design system
  (3-column desktop layout, sidebar, now-playing panel, bottom remote bar)
- `apps/android/app/src/main/assets/companion/remote.css` — KANTA design system colors, radii,
  responsive breakpoints at 1000px and 640px

### Files NOT modified

- All SessionController, StagePresenterImpl, playback, queue, microphone, camera, YouTube,
  local-file, visuals and companion backend logic — unchanged, no behavior duplicated or replaced

### Backward compatibility

The existing workspace/StagePresenterImpl/transport/toolbar/body structure remains the owner
for Home and legacy utility destinations. Reference-led design-system screens own their destination
controls; MainActivity hides the persistent stage and navigation layer while those screens are active
so the same actions are not rendered twice.

### Verification

- All new Java files parse (braces/parens/brackets balanced)
- MainActivity.java braces/parens/brackets balanced
- Browser remote: all 22 element IDs queried by remote.js exist in index.html; remote.js byte-identical
  (no wire/protocol changes); HTML tags balanced; node --check passes on remote.js

## Current checkpoint — 2026-10-29 (main `2a01654`; public release source `41c8ddde`)

Main head is [`2a01654`](https://github.com/usermalik20-del/Kanta-reborn/commit/2a01654).
Android validation [run #40760610063](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40760610063)
passes the complete acceptance suite on that exact SHA: the product-branding sweep, the
native SoundFont sanitizer regression, Gradle `assembleDebug` + `lintDebug` + the
framework-free `coreRegression` task + `assembleDebugAndroidTest`, the APK
permission/runtime-notice package check, the real-Chrome companion flow, captured-browser
interoperability, private IPv4 interface selection, and the Android emulator runtime checks
with fresh stage and camera captures. The unsigned iOS archive is green and the iPhone/iPad
walkthrough runs on the same SHA.

**Fail-closed CI (fixed on main, `c784fe5`).** `android-validation.yml` piped Gradle through
`tee` without `set -o pipefail`, so a failed build returned `tee`'s exit status and the job
could report success on a broken build. Android runs that reported green earlier in the
stage-composition work were therefore **not** trustworthy evidence. The build step now sets
`pipefail`; `branches: ['*']` was corrected to `['**']` (slash-containing branches were
never validated); concurrency groups are keyed by `github.run_attempt` so re-running an
older SHA cannot cancel the current acceptance run.

**Defects the fail-closed change exposed, all fixed on main:**
- `MkmdDecoder`'s magic constant was written `0x444B4D4D`, which little-endian decodes to
  `MMKD`, not `MKMD`. It rejected every real payload, so the importer could never decode
  anything. The constant is now derived from the ASCII bytes and guarded by a regression
  assertion.
- `MkmdDecoder` required the decrypted plaintext length to equal the declared compressed
  length exactly, but the format's ciphertext is `roundUp16(13 + compressedBytes)`, so a
  legal 0–15 bytes of trailing block padding made it reject valid payloads.
- Merging PR #112 re-introduced the legacy product name into eight public web pages through
  SEO meta/title strings. The pages are rebranded and `tools/check-product-branding.py`
  is 4/4 again.

**Pull requests (2026-10-29).** #112 (AdSense deferred until a nonce-capable CSP existed) was
merged to main; that historical restriction is now superseded by the nonce-capable,
consent-gated candidate in open PR #121, which remains unmerged and requires preview acceptance. #113 (iOS More-menu destinations pushed through the `NavigationStack` path)
was merged and then **reverted**: the iOS visual walkthrough was green on `d3e34f95`,
`24e091b1` and `37f7ace1`, and failed on both devices on the `80ae286` merge and on
`2a01654f`. The revert restores a `RootView.swift` byte-identical to the green builds;
the original Song Library transition failure was intermittent, and the candidate made it
deterministic. After the revert, the iOS visual walkthrough
[run #40787766952](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40787766952)
passed both device jobs on `b7d69f2`, with Android validation, iOS validation and the
unsigned iOS archive also green on that SHA. #104, #111, #114 and #115 are closed as superseded: their diagnoses were
accurate for the commits they were raised against, but every listed defect is fixed and
validated on main, and #114/#115 would have reverted validated work. The CI hardening they
described was adopted directly rather than through those branches.

**Milestone 3 implemented and validated on main.** `KantaConnectHost` (NSD `_kanta._tcp`
advertisement plus a WebSocket server on port 45731, pairing-code display, request dispatch
for snapshot/search/reserve/cancel/play/pause/next), `SessionController` audio-settings
persistence through `SettingsStore` (master volume, tempo, pitch), and the
`MegContainerImporter` user-initiated legacy-container import behind
`SqliteSongCatalog.importFromLegacyContainer(...)`.

**Not established.** No new release, no signed production IPA, no physical-device
acceptance, and no device run of the Meg importer against a real container. The public
download manifest and the durable [v0.1.0 release](https://github.com/usermalik20-del/Kanta-reborn/releases/tag/v0.1.0-workspace-redesign)
remain pinned to native source `41c8ddde`.

## Superseded checkpoint — 2026-10-26 (main `9ad68d4`; accepted native source `0867134`)

Main head is merge commit [`9a2a0fc`](https://github.com/usermalik20-del/Kanta-reborn/commit/9a2a0fca3778ff10eb549c319d7f400f33661b28), which merged [PR #101](https://github.com/usermalik20-del/Kanta-reborn/pull/101). The Android Compact Workspace now uses an AndroidX `OnBackPressedCallback` that is enabled only while a transient accessory is active. This replaces the deprecated `Activity.onBackPressed()` override that caused API 36 `GestureBackNavigation` lint failure on `b8e2822`.

**Exact-SHA post-merge acceptance on `9a2a0fc`:**
- Reconstruction [run #40499591316](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591316) passed Android and iOS jobs. Android build/lint/domain regression, real-Chrome companion flow, captured-browser interoperability, private-interface guard and emulator runtime passed; Swift package tests and unsigned simulator build passed.
- Unsigned iOS archive [run #40499591326](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591326) passed and produced [artifact #12732093588](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591326/artifacts/12732093588), 25,137,220 bytes, ZIP SHA-256 `603d70d71f3eb320152b20c65be4e06629f1e6f1d37da2bcbf9cdb2ac592f219`.
- Full iPhone/iPad walkthrough [run #40499591484](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591484) passed both device jobs. iPhone runtime [artifact #12733327427](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591484/artifacts/12733327427); iPad runtime [artifact #12733063065](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591484/artifacts/12733063065). Both device jobs also uploaded presentation diagnostics.
- Android reconstruction bundle [artifact #12731834290](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591316/artifacts/12731834290), 28,565,463 bytes, ZIP SHA-256 `4fa347bcfdad8cfcc7d2063323ce4e48a9951100273efedf6eb4f01be9a32f55`.

The predictive-back regression is resolved and the required native acceptance gates are green on the same merge SHA. The earlier failure remains documented as historical; do not erase it.


## Android legacy songbook interoperability — Milestone 1 (October 28, 2026)

**Milestone 0 (rebrand foundation):** App label `KANTA Karaoke`, content root `KANTA/` (was `SongHub/`), index files `kantidx`/`kantablob` (was `shubidx`/`shubblob`), prefs `kanta_settings`/`kanta_prefs` (was `songhub_settings`/`songhub_prefs`), NSD `_kanta._tcp`, lock names `KANTA:MulticastLock`/`KANTA:WifiLock`, log tag `KANTA_SERVER:`. `tools/check-product-branding.py` passes 4/4 checks with legacy fallback allowances for `content/` and `settings/` packages. Merged to main as commit `8d14c91`.

**Milestone 1 (vertical slice — content/catalog/stage):**
- **Content layer:** `ContentRoot` (SAF tree resolution with legacy SongHub/ fallback), `ContentScanner` (SAF scanning with 10k entry / 1k dir / 16 depth limits)
- **Catalog layer:** `SongCatalog` interface, `SqliteSongCatalog` with FTS4 search schema (volumes, songs, artists, song_search, index_meta tables + triggers)
- **Stage overlays (10 components):** `LyricStage`, `CountdownStage`, `ScoreOverlay`, `BackgroundStage`, `AdjustmentOverlay`, `IntroCard`, `SearchOverlay`, `QueueOverlay`, `RemoteStatusView`, `Keypad`
- **Contracts:** `StagePresenter`, `SettingsStore` (6 groups: audio, lyric, mic, score, behavior, bgv), `ScoreSink`
- **Resources:** Design system drawables/colors/dimens/styles copied to app `res/`

All Android build, lint, domain regression and instrumentation tests pass on main (`3a81dd6`). CI workflows split into separate `android-validation.yml` and `ios-validation.yml` with independent concurrency groups.

## Milestone status (2026-10-29)

The authoritative list of what is **not** done — with acceptance criteria and evidence for each
item — is [`docs/UNFINISHED_WORK.md`](UNFINISHED_WORK.md). This file records what shipped;
that one records what has not.

| Milestone | Scope | State |
| --- | --- | --- |
| 0 | Rebrand foundation | **Done** — merged `8d14c91`; branding sweep 4/4 |
| 1 | Vertical slice: content / catalog / stage overlays | **Done** — merged `fa37794` |
| 2 | StagePresenter binding, score sink, legacy MKMD decode + Meg importer | **Done** — merged; MKMD padding + magic defects fixed in `1dfecd7` / `2a01654` |
| 3 | Kanta-Connect host (NSD + WebSocket), settings persistence | **Done** — `KantaConnectHost` + `SettingsStore` wiring on main, Android validation green on `2a01654` |
| 4 | Kanta-Connect phone app, voice search, spectrum analyser, score celebration, custom fonts, diagnostics, help/about | **Started** — score celebration + diagnostics shipped; rest not started |
| 5 | Kanta-Connect naming + seamless pairing (QR and typed address); public-review mining | **Started** — naming and pairing shipped; review mining not started |

### Milestone 3 detail (implemented, validated on `2a01654`)

- `core/companion/KantaConnectHost.java` — advertises `_kanta._tcp` through Android NSD,
  serves WebSocket clients on port 45731 (HTTP upgrade handshake, masked text frames,
  extended 126/127 lengths, ping/pong), mints a 32-hex-character pairing code per hosting
  session, and dispatches `snapshot`, `search`, `reserve`, `cancel`, `play`, `pause` and
  `next` to `SessionController` as JSON. Hosting is explicitly user-initiated from the
  Controls accessory and shows the pairing code.
- `SessionController` — audio settings persist through `SettingsStore`
  (`kanta_settings`): master volume, tempo and pitch are loaded at construction and written
  on every `controls()` change. Persistence is best-effort and never blocks playback.
- `core/legacy/MegContainerImporter.java` + `core/legacy/MkmdDecoder.java` — the
  user-initiated legacy-container import behind
  `SqliteSongCatalog.importFromLegacyContainer(...)`, separate from `rebuild()` (dim-3
  Option 3b). Key material is supplied at runtime only.
- `stage/StagePresenterImpl.java` — owns the persistent stage layer
  (tag `kanta.stage.primary`) and binds the ten stage overlays to `SessionController`.

### Milestone 4 detail — lyric-stage animation and fun countdown (2026-10-31)

Candidate source on `feature/fun-lyrics-and-countdown` (local commit `3379caa`) adds
`core/LyricWordAnimation.java`, a framework-free helper for source-fragment interpolation,
active-fragment selection and pitch-energy decay. `stage/LyricStage.java` and the fullscreen
`StageView` keep their existing scrolling window and cached `StaticLayout` text, but the active
line now fills continuously across a source fragment instead of flipping a whole word, and the
fragment being sung receives a per-letter wave driven by the existing `PitchSource` estimate.
`stage/CountdownStage.java` becomes `Ready ⏱️ → 1 → 2 → 3 → 4 → Kanta 🎤` with a gradient ring,
orbit dots and confetti, and `SettingsStore.getCountdownLength()` defaults to four.
`MainActivity.playWithCountdown()` starts a new reservation through the countdown and still
reaches playback only through `SessionController.toggle()`; prepared songs and empty queues skip
the countdown. No word timings are invented, pitch cannot change cue timing or scoring, and the
stage still renders with the microphone off.

`LyricWordAnimationRegressionTests` is wired into the existing `coreRegression` task. The
authoring environment has no working local JVM (`getcpu(2) system call not supported by
kernel`), so this candidate is reconstructed and statically checked only; Gradle compilation,
lint, emulator runtime and device acceptance remain open.

### Milestone 4 detail — score celebration (2026-10-29)

`core/score/ScoreCelebrationPlan.java` holds the whole choreography as framework-free,
deterministic data: a tier derived from the overall score (Perfect ≥ 95, Great ≥ 85,
Good ≥ 70, else Miss), a confetti/balloon/star budget and duration per tier, a bounded
particle list derived from `(overall, seed)`, and `positionX`/`positionY`/`rotationDegrees`
helpers with gravity. Nothing reads a clock or a view, so the same input always produces
the same plan and the host-JVM `coreRegression` task verifies it
(`ScoreCelebrationRegressionTests`: tier boundaries, clamping, Miss never celebrating,
monotonic budgets by tier, seed reproducibility, seed sensitivity, radius/hue/origin bounds
across a 21-score × 25-seed sweep, star burst at the score-card centre, motion determinism,
the rotation bound, the particle budget ceiling, and list immutability).

`stage/ScoreCelebrationView.java` only converts normalised positions into pixels. It is
decoration: it never consumes a touch (`setClickable(false)`,
`IMPORTANT_FOR_ACCESSIBILITY_NO`) and it honours the system animation scale by drawing the
resolved end state instead of animating when "Reduce Motion" is on.
`StagePresenterImpl.showScore` starts it with a seed derived from the score and the song
identity, and cancels it when the score card is dismissed or playback stops.

`MainActivity.diagnostics()` adds the S14 diagnostics surface to the Controls accessory. It
builds a read-only report from existing session state — app version, device/API, library
counts, playback state, audio settings, microphone/monitor/scoring flags, stage background
and camera, companion and Kanta-Connect status, and the current message — and offers Copy.
It deliberately reports only *whether* Kanta-Connect is hosting, never the pairing code, so
the report is safe to share. It changes no audio, playback or lifecycle path.

`MicrophoneMonitor` now also accepts Bluetooth SCO and BLE headsets as monitored outputs and
routes them through the Android communication path on API 31 and newer, with a longer readiness
bound for the slower link. The microphone input itself is unchanged: it still comes from the
phone's own built-in microphone, so a Bluetooth headset's microphone is not yet captured by the
app and the Bluetooth route carries the processed voice out only.


**Pitch Settings UI (Controls accessory) — implemented and validated**



The Controls accessory now exposes pitch configuration:

- **Pitch Settings** dialog: sensitivity slider (0.1–3.0) with presets, note picker (C4–B5), custom Hz input (20–2000 Hz)

- **Pitch Practice** button → `ACCESSORY_PITCH_GAME`

- **Voice Coach** button → `ACCESSORY_LEARNING_ASSISTANT`



Settings persist via `SettingsStore`: `pitch.assistantSensitivity`, `pitch.targetHz`, `pitch.targetName`.
`LearningAssistant.setSensitivity()` scales thresholds; `PitchGameOverlay` uses stored `targetHz` as fallback.

**Compile repair after the first landing.** The Android build rejected this source: `showPitchSettings()` had been inserted over the body of `copyToClipboard(String, String)`, four section comments used `#` instead of `//`, and `SettingsStore` was used without an import. The missing import stayed hidden because `javac` reports parse errors first and never reaches symbol resolution. All three are repaired, and the diff against the last validated baseline now contains only the intended additions.

Still outstanding for Milestone 4: the Kanta-Connect phone app, voice search, the spectrum
analyser, custom fonts, diagnostics mode and help/about.

### Milestone 5 detail — Kanta-Connect naming and seamless pairing (2026-10-29)

The host feature is named **Kanta-Connect** throughout: `KantaConnectHost`, its `HostAdapter`
contract, `SessionController.startKantaConnect()` / `stopKantaConnect()`, the Controls
accessory entry, and the diagnostics report. No user-visible "Smart Link" string remains
(the one surviving mention in `apps/android/README.md` refers to the *legacy* third-party
transport and is historical context).

Pairing is now one action. Tapping **Start Kanta-Connect** mints a single-session code,
picks the device's reachable LAN address and presents both routes at once:

- a **QR code** encoding `kanta-connect://<host>:<port>?code=<32 hex>&v=1`, and
- the **typed route**: the address plus the code grouped into four blocks of eight.

`core/connect/QrEncoder.java` is an in-tree QR encoder (byte mode, error-correction level M,
versions 1–10, up to 216 payload bytes). It is in-tree rather than a dependency because the
project keeps its dependency surface small, the encoding is fully specified by ISO/IEC
18004, and it is small enough to test exhaustively. Correctness was established by rendering
the reference output to PNG and decoding every vector with an independent scanner (Apple
Vision) across versions 1, 5, 6, 7, 8, 9 and 10; `QrEncoderGoldenVectors` pins those exact
matrices so the Java encoder cannot drift from the verified reference.

`core/connect/KantaConnectInfo.java` is the link model: link construction and parsing, code
normalisation for typing, private-address detection, host ranking (a `192.168/16` Wi-Fi
address is preferred over VPN `10/8` and `172.16/12` ranges, which is what makes the
suggested address actually reachable) and the on-screen steps.

Privacy choices that are enforced by the contract guard: the QR's content description does
not repeat the link, the diagnostics report states the code is withheld rather than printing
it, and the pairing sheet copies the code and the address *separately* so a single
clipboard entry never carries both.

**Verified.** Android validation [run #40826684007](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40826684007)
passes on `dc03bf8`, which runs the new `coreRegression` suites on a real JVM: the golden
matrices for versions 1, 5, 6 and 8 match the externally decoded reference byte-for-byte,
format and version information round-trip, the structural patterns hold, and the link
model's parsing, normalisation, address ranking and privacy assertions all pass.

Two test-only bugs were caught by that run and fixed in `dc03bf8`: the version-information
check used the 15-bit format polynomial instead of the 18-bit version polynomial, and the
capacity boundary asserted a payload equal to the nominal capacity (which the mode and
length headers make unusable).

Still outstanding for Milestone 5: the Kanta-Connect phone app itself, and the
public-review mining pass.

#### Kanta-Connect pairing-code Logcat redaction — 2026-11-09

`KantaConnectHost.start()` previously included the live 128-bit pairing code in its startup log and an unused status-callback argument. Both paths now receive status/port information only. The owner-facing pairing dialog continues to read the code from `pairingCode()` for its QR and typed routes. A dedicated Android validation test guards the boundary; wire protocol and pairing behavior are unchanged.

### Not part of any milestone yet

- The Meg importer has no UI surface; it is reachable only through the catalog API.
- Media3 is not a dependency; BGV video currently renders through the existing
  `StageSurface`/`StageView` path, and the `BackgroundStage` overlay carries photo/camera
  presentation.

### Open risks

- `MkmdDecoder` is validated by JVM fixtures only. No real Meg container has been decoded
  on a device, so the port is not device-accepted.
- `KantaConnectHost` has no on-device two-host test; only the request/response contract is
  unit-shaped.

## Website consent and AdSense nonce candidate — 2026-10-31

PR #121 is an open draft candidate on `feat/website-consent-adsense-nonce-20261031`. It preserves the existing Google Analytics measurement ID and adds consent-gated AdSense using a request-time Vercel renderer with a fresh per-response CSP nonce. The visitor can accept, reject or later change/revoke optional consent. The private browser Songbook is excluded, and the native app binaries remain isolated from website analytics/ads. The website README, root README, architecture, testing, privacy disclosures and this reconstruction ledger are updated in the same PR.

**CI topology:** one lightweight documentation-sync job is embedded in `website-validation.yml` and runs on every PR; it compares the complete PR base/head diff, requires substantive changes in owner docs, and classifies website paths so website tests run only when website/privacy paths changed. Android and iOS build workflows retain independent source filters. Android privacy asset changes still trigger Android validation because the disclosure ships inside the APK. iOS PublicSite/privacy documentation does not allocate Xcode runners. The main-branch release/deploy workflows run the same documentation guard inside their existing jobs, without creating another platform runner. No production deployment or merge is authorized by this candidate.

**Status on PR head `c7564c3d73de8c6aaf3425dda6a693154b0a8dc8`:** documentation synchronization and the complete website validation suite passed [run #41165515126](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41165515126); workflow/release contract tests passed [run #41165515053](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41165515053). Android validation is still pending [run #41165515041](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41165515041) because its bundled privacy asset changed. Preview routing/nonce acceptance and Google's publisher approval, Auto Ads account settings and applicable consent-platform requirements remain separate gates; code is not proof of live ads.


### PR #121 — preview acceptance finding (2026-10-31)

The Vercel API was used to create a preview deployment for exact candidate SHA `f8aa9daaf89769b45752acc2ab870f0fec86702b` after no matching preview was returned by the deployment listing. The preview returned HTTP 200, a fresh response nonce matching the CSP, and `Cache-Control: private, no-store`. Remote inspection then found that the recovered homepage still contained a static `/analytics.js` tag: nonce decoration made it CSP-authorized, but it bypassed the consent manager and initialized Analytics before consent. The request-time renderer now strips that legacy tag, and a regression test protects the invariant. This repair is on the open PR branch; it is not yet validated by CI or a rebuilt preview. The Songbook preview returned HTTP 200 with its separate restrictive CSP and no Analytics/AdSense integration in its HTML. Do not merge until the new candidate is green and the rebuilt preview passes browser-level consent acceptance, rejection and revocation.

## Android CI runner repair candidate — 2026-11-01

The November 1 Android validation changes exposed two deterministic runner defects at source SHA `2e0cc5c6d60ab175937c372d9ef9b244f52e8b64`: malformed workflow YAML joined the optional manual-ref input to the `push` event declaration, and the runtime script placed APK installation/instrumentation after an unconditional network-failure raise inside the `if not network_ok` branch. The camera capture statement was also concatenated to a comment, and screenshot retrieval did not match the workflow's `files/*.png` artifact paths. A forward-only repair branch corrects the event structure, checks out the requested event/ref SHA, restores the acceptance sequence after the prerequisite guard, and aligns screenshot output with artifact paths. `tools/tests/test_android_runtime_runner.py` protects the runner's AST/control-flow contract. This is a source repair candidate only: no hosted run on the repaired PR head has yet established compilation, instrumentation or emulator acceptance.

A follow-up source review found that the initial candidate still checked whether each screenshot already existed in the host workspace before attempting to copy it from the emulator. Since the files exist only in app-private storage at that point, the check would silently skip all eight captures. The runner now retrieves every named screenshot unconditionally and fails on missing/empty evidence; the regression test protects against restoring the skip condition. This remains pending hosted validation.

## Android run #400 — primary stage presenter ClassCastException — 2026-11-01

The exact-head Android validation reached the emulator runtime gate after all earlier gates passed. Instrumentation crashed at `RuntimeChecks.java:255` with `ClassCastException: ph.kanta.reborn.stage.StagePresenterImpl cannot be cast to ph.kanta.reborn.StageSurface`. The cause was a stale runtime test assumption: `MainActivity` now installs `StagePresenterImpl` as the primary `kanta.stage.primary` view, while `StageSurface` exists only for fullscreen/external presentation. The test cast the primary view to the old class when asserting BGV visibility. The same ownership mismatch existed in camera handling: `CameraStage.start()` accepted `PreviewView` and `StageSurface`, but `MainActivity` passed the primary `StagePresenterImpl`, causing the camera start path to return without binding a preview. The candidate fix exercises video lifecycle through a real fullscreen `StageSurface`, adds primary-presenter camera-preview preparation/stream-state inspection, supports `StagePresenterImpl` as a camera target, and publishes camera stream state to fullscreen surfaces. The runtime test now checks primary camera streaming, movement to fullscreen, and return to the primary preview. This source repair is pending a fresh hosted Android validation run; do not mark it verified until emulator acceptance passes.


## Android camera preview teardown repair candidate — 2026-11-01

PR #130 exposed a second camera lifecycle defect after primary/fullscreen target support was added. CameraX unbinding and observer removal did not detach the PreviewView from the owning stage. The primary BackgroundStage also retained its CAMERA source and preview object, while the old fullscreen path did not call StageSurface.unmountCamera(). In addition, the primary visibility predicate trusted the PreviewView stream value without checking that the camera source was active and the view remained attached; that value can outlive the visible preview.

The repair candidate makes stage ownership explicit during teardown: CameraStage.stop() invalidates callbacks, removes observers, unbinds the preview, clears its surface provider, and calls the owning stage's release method. StagePresenterImpl preserves and restores the previously selected background, while BackgroundStage drops the old preview reference and reports visible only for an attached, actively streaming camera source. Emulator instrumentation now waits for camera visibility to clear after fullscreen ownership moves and after explicit disable. No delay or weakened assertion is used to hide a lifecycle defect.

**Status:** source and documentation candidate on fix/android-camera-preview-release-pr130, based on PR #130 head accfa77fc3a1cdd3a72cfbc354b17675e204f608. Android hosted build, lint, domain regression and emulator acceptance are pending on the resulting candidate SHA. No physical-device camera quality or lifecycle claim is made.


## Android library import progress lifecycle — 2026-11-02

Source review found that `LibraryRepository.importBatch` emits progress on the app-owned I/O executor while `MainActivity` directly updated `ProgressDialog` and `Toast`. A folder-scan exception/cancellation also bypassed the listener completion callback. The repair moves callback delivery to `SessionController`'s main Looper, keeps the current listener replaceable across Activity stop/start, clears it at every terminal path, and makes the folder scan report failure/cancellation completion. `RuntimeChecks` now protects main-thread delivery, listener release and scan-failure completion. The corrected PR #136 code head `ac5e393cff3d1dcee09b4ae424c634b6243bb6f3` passed hosted Android validation run [#41370798485](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41370798485), including build/lint, domain regression, Chrome/companion interoperability, private IPv4 and 228 emulator runtime checks; diagnostics artifact [#13109625430](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41370798485) was uploaded. Website validation [#41370798446](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41370798446) passed documentation synchronization. This is emulator acceptance only, not physical-device testing.


**PR #136 validation follow-up (2026-11-02):** the first hosted Android run, [#41370531275](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41370531275), caught a mutable-counter lambda capture before tests ran. The follow-up snapshots the success result before posting to the main Looper. Corrected code head `ac5e393cff3d1dcee09b4ae424c634b6243bb6f3` passed [Android validation #41370798485](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41370798485) with 65,402 core regression checks and 228 emulator runtime checks, and [website validation #41370798446](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41370798446) passed documentation synchronization. The compile failure and repair are retained in the CI failure ledger.


## Android browser companion pairing-capacity recovery — 2026-11-02

A current-main review confirmed that the browser host incremented its incorrect-pairing counter whenever a pairing request was rejected, including a correct code presented while all four guest slots were occupied. Three temporary capacity denials could therefore lock pairing for the remainder of the host session even after a guest left. The repair separates session/window lockout, incorrect-code accounting, and guest-capacity rejection: only an incorrect code increments the three-attempt budget. The real TLS `BrowserRemoteRegressionTests` now exercises three capacity denials, guest Leave, successful replacement pairing, and the existing three-wrong-code lockout. Hosted Android build, lint, core regression and emulator validation are required before this candidate is accepted; this note does not claim device or independent security acceptance.


## Android SoundFont storage safety — 2026-11-02

Current-main audit found that the SF2 post-copy check compared the copied file size against the remaining free space, effectively requiring roughly twice the file size plus a 64 MiB reserve. This rejected valid large banks even when sufficient space remained. The importer also had no preflight for known file sizes, so an unknown/oversized provider stream could temporarily exhaust app storage before the post-copy check. The repair preflights reported sizes, rechecks free space while streaming unknown/under-reported sources, and preserves a 64 MiB reserve without imposing a fixed file-size ceiling. CoreRegressionTests cover both the large-bank acceptance case and reserve enforcement. Exact-head Android CI/runtime validation is tracked by the dedicated PR; no emulator acceptance is claimed until those checks finish.


## iOS playback cancellation hardening — 2026-11-02

Current-main review found that user-initiated controller commands could resume after cancellation of an awaited library lookup or native preparation and still start/commit the next song. The controller now rechecks cancellation at those suspension boundaries, retires an uncommitted backend asset only after loading begins, and preserves the previous current song and FIFO reservation for recovery. Audio-session configuration failure before `engine.load` leaves current playback untouched. Natural-completion auto-advance remains independent of view-poll cancellation. Deterministic Swift regressions cover canceled enqueue, canceled Next during library resolution, canceled Next during native load/recovery, canceled snapshot polling during natural auto-advance, pre-load cancellation preserving the current playing asset, and audio-session setup failure preserving playback. The reconciled candidate requires fresh hosted package, smoke, unsigned archive and iPhone/iPad acceptance on its exact SHA.


## Android reference-led UI integration — 2026-11-02

The reference-led Android UI work is being reconciled from draft PR #134 onto a branch based on current `main`, rather than merging its 94-commit mixed-platform history. The native presentation slice mounts Home, Search, Queue, Pitch & Tempo, Mic & Voice, Visuals and Now Playing while retaining the latest main-branch `SessionController`, `LibraryRepository`, My Set import discoverability and SoundFont inventory/storage safety. The current PR #134 branch's latest source head `c039c1d` had a successful Android validation run (`41369103980`); the screenshot-capture compiler error recorded for earlier head `9255c24` was already corrected there. That historical green run does not validate the new reconciled branch. The browser companion markup, mirrored iOS resources and iOS host changes are deliberately held for a separate shared-contract review so this native UI PR does not trigger unrelated iOS workflows. Fresh exact-head Android runtime, screenshots, documentation sync and APK packaging checks are required before merge or publication.

The first reconciled PR #149 Android compile exposed one missing adapter: the new Visuals screen called `SessionController.sceneResource(String, int)`, which existed only on the old draft branch. The repair ports that single adapter into the current main-branch controller, preserving its latest import/SoundFont/storage changes and routing bitmap decode through the existing stage generation and publication owner. The original compile failure is recorded in `docs/CI_FAILURE_REPAIR_LEDGER.md`; exact-head Android validation must pass again after the repair.

**PR #149 screenshot artifact reconciliation (2026-11-02):** Exact-head emulator instrumentation generated `my-set-imported-library.png` and `my-set-active-soundfont.png`, but the reconciled collector and Android workflow artifact allowlist initially omitted them. The runner now retrieves both as required captures and the artifact allowlist retains them alongside the nine reference-led destination screenshots. Fresh exact-head Android validation must confirm all eleven files in the uploaded artifact; the earlier green run on `a5af972` predates this repair.

## PR #134 merge reconciliation — 2026-11-02

PR #134 remains a validation candidate, not a release claim. Its reference-led Android destinations and companion changes were merged with current `main`; the fixed Home default (`bfa605d`) and exact-head Android runtime result remain part of the evidence before merge.

The PR reconciliation restored current-main `SessionController` APIs after resolving the branch divergence. Android compile and runtime evidence must be collected on the resulting exact head.

The exact-head karaoke-stage candidate includes the Home-launch default repair; Android runtime validation must prove the dashboard is mounted before destination walkthroughs.

## Android three-group UI candidate — 2026-11-03

Physical-device screenshots showed that the previous Home dashboard and six-tab technical navigation exposed too many implementation concepts at once. The candidate replaces that structure with Home | Sing | Performance | Experience | More. Group landing cards are goal-oriented and route into the existing Search, Queue, My Set, Now Playing, Voice, Pitch & Tempo, Visuals, Loop/Party and advanced-control destinations without duplicating playback or queue state. The Home viewport now fills available height so its compact navigation does not leave an avoidable black tail.

The Search destination adds an in-app keypad toggle. Numeric and letter keys use the same compact bounded area, while the Android soft keyboard is suppressed; this is a UI candidate until exact-head Android build and emulator/physical-device acceptance pass.

## Supplied UI and SoundFont asset candidate — 2026-11-03

The supplied UI upgrade archive contributes multicolor Android VectorDrawables and gradient button selectors. Home and Voice use them directly without tinting. The two supplied procedural SF2 banks are bundled as starter assets and surfaced in the existing SoundFont inventory; they are not silently selected over the current bank. The OpenStage audit pack's lock file and license requirements are preserved under `docs/audio-candidates/openstage/`; the KANTA-created procedural bank is included under the restricted project asset license, while unverified third-party audio candidates are not copied into the application.


## Legacy ShubBlob paired import candidate — 2026-11-03

Real-pair investigation established that the index trailer is not a byte-offset table for this variant. Each title record instead identifies its payload using the unsigned word at byte 8 multiplied by 2048. All title references resolved unique MKMD records and an independent local reference decoded every referenced payload without persisting or uploading media. The importer now uses validated per-title addresses, retains support for validated byte-offset layouts, rejects ambiguity, and removes physical-scan pairing. Synthetic integration coverage preserves title/payload identity despite reversed title order and extra unreferenced records. Earlier scan-fallback CI does not validate this mapping revision; exact-head mapping CI is pending. Runtime-only key handling, paired-file emulator acceptance and physical playback remain open.

## 2026-11-04 — Android MIDI/KAR import rework (local, unmerged)

On `feat/midi-kar-import-rework` (no merge/push/deploy): removed fixed MIDI/KAR caps, added file-mapped decode, KAR metadata/pause/echo parsing, lyric charset detection, per-file import diagnostics with duplicate counting, imported song/bank deletion with tombstone rollback, missing-media repair on load and re-import, library-backed stage search, explicit archive rejection, a cancelable import dialog, and SF2 INFO/INAM bank re-labelling. Host-JVM checks live in `CoreRegressionTests`. Android validation run [#41806652704](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41806652704) failed because `compileCoreRegression` excluded the whole catalog package, including new pure-Java classes; remote commit `cab2e43` narrows the exclusion to Android-backed `SqliteSongCatalog`. Local execution then exposed two malformed KAR fixtures (missing line delimiters and a file test containing track bytes without an SMF header), corrected in the current candidate. Manual Java 21 compilation with `--release 17` and execution of all 55,217 core checks pass. This does not establish a Gradle app build, lint, APK/test-APK build or emulator acceptance. Exact-head hosted Android validation remains pending; local Gradle configuration is blocked by the missing Android SDK. See the CI failure ledger for the recorded failure and repair status.

**Android validation follow-up:** run [#41813457564](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41813457564) passed APK assembly and all 55,217 framework-free core checks after commit `cab2e43`, then failed instrumentation compilation because `RuntimeChecks` still expected the old `int[]` import result. The test now asserts the named imported/failed `ImportBatchResult` fields. Full Android validation passed on exact head [`5752532`](https://github.com/usermalik20-del/Kanta-reborn/commit/57525326069908bf19da3328d57260862283dea1) in run [#41814538861](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41814538861); see the failure-ledger validation follow-up.

## 2026-11-04 — Android seek/restart (local, unmerged)

Follow-up on the same branch: PlaybackBackend gains seekTo(); SoundFontPlayback seeks by flushing AudioTrack, silencing voices, re-rendering silently to the target source time and resetting the audible clock (head bias keeps the timeline linear); FileAudioPlayback wraps MediaPlayer.seekTo; the transport Previous button restarts the current song. Not JVM-testable locally; emulator instrumentation validates it in CI.

## 2026-11-04 — Android per-song key/tempo (local, unmerged)

Same branch: SettingsStore gains per-song override keys (song.pitch.&lt;id&gt; / song.tempo.&lt;id&gt;); SessionController applies the override at preparation, auto-remembers adjustments made during an active song (controls path), and exposes effectiveTempo/effectivePitch, currentSongHasOverride and clearCurrentSongOverride; Pitch & Tempo and Now Playing surfaces display and reset the effective values. Emulator instrumentation validates it in CI.

## 2026-11-04 — Android SF3/Ogg SoundFont support (local, unmerged)

Same branch: pinned `stb_vorbis.c` (nothings/stb v1.22, d14d46f6b) compiled inside the native wrapper; vendored `tsf.h` gains a documented local delta (`synth->sampleTotal`, the post-decode frame count) so regions are bounded against the real decoded buffer; `sf2-validation.h` validates compressed banks (container byte offsets, compressed sample types, loop ordering; no static decoded length exists) and `soundfont.c` arms the SF3 path behind STB_VORBIS_INCLUDE_STB_VORBIS_H with the vendor-silencing pragmas. Locally verified: gcc -Wall -Wextra -Werror clean, synthetic 440 Hz tone.sf3 opens, decodes 26,461 frames, renders audibly (RMS 0.049), truncations rejected, SF2 banks unchanged. Native CI (ASan/UBSan soundfont-test with the new fixture) validates on push.

## 2026-11-04 — SF3 render-tail guard (local, unmerged)

CI ASan found a heap-buffer-overflow in tsf_voice_interpolate: the Catmull-Rom interpolator reads input[pos+2] while the loader keeps one pad float; the 2000-mutation render loop produced a mutant whose combined-SF3 decode sized the sample buffer to the region end, and the render read one float past the allocation (deterministic 436-byte block, diag confirmed the SF3 fixture itself is fine: sampleTotal=28786 voices=128/128). kanta_sf_open now requires every region to keep its end and loop window at least two frames inside synth->sampleTotal; unsafe mutants are rejected instead of rendered. Verified locally: -Wall -Wextra -Werror clean, full soundfont-test.c CI-shape run passes (SF3 open/render + truncation + 2000 mutations), tone.sf3/SF2 unchanged. Pending the CI rerun.

## OTA/versioning reconstruction boundary

The Android OTA feed is a release-provenance surface. `tools/verify-ota-publication.py` is authoritative for APK metadata and signer continuity; `tools/prepare-update-feed.py` emits the contract field `signingCertSha256` from the exact APK metadata. The owner-approved signer pin permits only the new certificate fingerprint while still verifying the prior APK digest. This intentionally starts a new install identity: old-signer APKs cannot update in place. Release secrets are restricted to trusted main CI. Do not restore Gradle-source scraping or mutable release-asset behavior.
### Android search keyboard
The reconstructed Android search flow includes a persistent KANTA keypad, an ABC/123 layout switch, cursor-aware editing, and no phone keyboard invocation.

### Android splash screen (full-bleed, orientation-aware)
The launch window background `res/drawable/kanta_splash_screen.xml` now renders a full-bleed splash instead of a centered mark. Assets: `drawable-nodpi/kanta_splash_image.webp` (portrait) and `drawable-land-nodpi/kanta_splash_image.webp` (landscape, used automatically by tablets in landscape and Android TV). The old `drawable/kanta_splash_image.png` was removed.

## Website redesign — 2026-11-07

Major website redesign adding a design-system theme layer, micro-interactions, and page-specific visual features. Owner approved all changes except the interactive feature comparison table (item 4).

### New shared assets
| File | Purpose |
| --- | --- |
| `theme.css` | Dark/light/system theme tokens, animated gradients, micro-interaction utilities, component styles |
| `theme.js` | Theme toggle (dark/light/system) with localStorage persistence, smooth cross-fade |
| `micro-interactions.js` | Button ripple, magnetic pull, 3D card tilt, scroll reveals, count-up stats |
| `command-palette.js` | ⌘K/⌃K fuzzy search over pages, features, guide steps; floating FAB on desktop |
| `hero-animation.js` | Audio-reactive gradient canvas + animated stage mockup |
| `download-progress.js` | Sticky artifact status bar on `/android` + `/ios-download` |
| `stage-preview.js` | Fixed phone frame on `/features` mirroring scrolled section |
| `guide-parallax.js` | Pinned phone illustration + palette shift per guide step |
| `songbook-fx.js` | 3D page flip, ambient particles, search highlight, lock/unlock transitions |

### Updated HTML pages
All six pages (`index.html`, `android.html`, `ios-download.html`, `features.html`, `guide.html`, `songbook.html`) now:
- Load `theme.css`, `theme.js`, `micro-interactions.js`, `command-palette.js`
- Include page-specific scripts
- Have theme toggle buttons (☀️/🌙/💻) in header
- Use `.reveal` / `.reveal-stagger` for scroll animations
- Carry `data-theme="system"` and page-specific `data-*` attributes

### Hero replacement
Static `device-showcase` replaced by animated canvas + stage mockup (`hero-animation.js`). Respects `prefers-reduced-motion`.

### Stage preview on /features
`data-stage-preview` activates `stage-preview.js` — fixed phone frame updating scene per scrolled section.

### Guide parallax
`data-guide-parallax` activates `guide-parallax.js` — pinned phone with per-step animation and palette shift.

### Songbook FX
`songbook-fx.js` — 3D page flip, ambient particles, search highlight, lock/unlock transitions.

### Theme toggle UI
Three-button group (☀️/🌙/💻) in header on all pages.

### Accessibility
All animations disabled under `prefers-reduced-motion: reduce` via CSS and JS guards.

### Documentation updates
- `README.md` — website redesign section added
- `apps/web/README.md` — detailed asset inventory and integration notes added
- `docs/ARCHITECTURE.md` — theme system, micro-interactions, components documented

### Website redesign JS modules (2026-11-07)
The redesign is implemented as modular JS files loaded by all pages:
- `theme.js` — theme toggle (dark/light/system) with localStorage persistence
- `micro-interactions.js` — button ripple, magnetic pull, 3D card tilt, scroll reveals, count-up stats
- `command-palette.js` — ⌘K/⌃K fuzzy search over pages, features, guide steps
- `hero-animation.js` — audio-reactive gradient canvas + animated stage mockup
- `download-progress.js` — manifest-backed status bar on `/android` + `/ios-download`; validates current publication metadata and page-link parity without claiming to hash downloaded bytes
- `stage-preview.js` — fixed phone frame on `/features` mirroring scrolled section
- `guide-parallax.js` — pinned phone illustration + palette shift per guide step
- `songbook-fx.js` — 3D page flip, ambient particles, search highlight, lock transitions
<!-- nav removal doc sync -->
<!-- nav removal doc sync v2 -->
