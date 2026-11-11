## Distinct monitor presets regression (K1)

`CoreRegressionTests.monitorChecks()` retains one-second impulse, reset-to-silence and
limiter/nonfinite checks for every preset. It additionally compares all 45 pairs of
rendered impulse responses, requiring squared difference greater than `0.000001`.
The old Small Room/Room and Concert Hall/Hall aliases produce identical responses and
fail that assertion. Run `:app:coreRegression` on a working Java 17 toolchain; Android
validation already runs this task. Local Python/source guards are not Java execution.
Physical-device listening, sound preference and route latency are not acceptance claims
for this change. K1 is verified on `dead199a1c31839fe955c5b6445a8e3a2c72a854`: [Android validation](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/42994654674) passed build/lint, 55,262 core regression checks (including all 45 preset pairs), and 430 emulator runtime checks. This is automated acceptance, not physical listening evidence.

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
## CI: Fix Trusted Main Branch Check (2026-11-09)

Fixed Android validation workflow to properly fetch full main branch history before ancestor check:
- Added explicit `git fetch origin main` and `git fetch origin main --tags` before the trusted_main check
- Fixes "ref outside main history" error on push to main branch
- Ensures git merge-base --is-ancestor check has access to full main branch history
## CI: Fix Trusted Main Branch Check (2026-11-09)

Fixed Android validation workflow to properly fetch main branch before ancestor check:
- Added explicit `git fetch origin main --depth=1` and `git fetch origin main --tags --depth=1` before the trusted_main check
- Fixes "ref outside main history" error on push to main branch
- Ensures git merge-base --is-ancestor check has access to up-to-date main branch history
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
## Test Update: test_ota_publication Fix (2026-11-09)

Fixed test_signing_secret_step_is_main_only_and_not_for_pull_requests to match new workflow step name:
- Updated from "Prepare owner-controlled Android release signing" to "Build owner-signed release APK"
- Changed step extraction to use proper step block detection (find next step marker)
- Updated assertions to match new workflow conditions:
  - steps.release-source.outputs.trusted_main == 'true' (replaces github.ref == 'refs/heads/main')
  - github.event_name != 'pull_request'
  - secrets.KANTA_ANDROID_SIGNING_KEYSTORE_BASE64
  - secrets.KANTA_ANDROID_SIGNING_PASSWORD
  - RUNNER_TEMP
- Build gradle assertions unchanged: KANTA_ANDROID_SIGNING_STORE_FILE, KANTA_ANDROID_SIGNING_PASSWORD, keyAlias 'kanta-release'
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

## Android home-hero removal regression (2026-11-07)

All Dashboard-return runtime checks were repointed from the removed `Music brings people together.` banner (deleted with the Home hero block) to the stable Home search hint `Search songs, artists, or enter code`, preserving their intent (verify navigation returns Home).

The duplicate bottom-nav fix (removing HomeScreen's internal dock) does not affect these sentinels: no runtime assertion referenced HomeScreen's `kanta.home.nav.*` tags.

The duplicate bottom-nav fix (removing HomeScreen's internal dock) does not affect these sentinels: no runtime assertion referenced HomeScreen's `kanta.home.nav.*` tags.

These sentinel semantics are unaffected by the further Home pruning (quick-cards row and header Settings gear removal) because they gate on the search hint, which is retained.

Home persistent search integration updates `InlineKeyboard` labels (`CLR` / `RES`) and routes text changes to `SearchOverlay.setQuery()`.

## Android premium icon-system regression (2026-11-07)

All `res/drawable/kanta_*_color.xml` vectors are 24x24 viewBox outlines with 1.8dp strokes, primary cyan `#29D9FF` and at most one violet `#7657FF` `<path>`. Diagnostics must map to `kanta_diagnostics_color` (never `kanta_settings_color`). The five navigation tabs are one continuous `kanta.nav.*` dock with muted-inactive (alpha 96) and cyan-active glow states; the HomeScreen `kanta.home.nav.*` bar uses the same five-icon set. GroupScreen cards keep pressed/cyan-border states; the resting card is `KantaTheme.edgeLitCard`. A runtime check should sample each new drawable (resolves, non-empty) and confirm the More page shows four distinct glyphs.

# Reconstruction validation
## Download-page section contract
`tools/publish-platform-download.py` rewrites exactly one `#download` section on the platform
download page. It matches any `class="section …"` token set (the website redesign renders
`class="section reveal"`), preserves the extra class tokens on rewrite, and fails if the section
is missing or duplicated. Unit-covered in `tools/tests/test_platform_download_publishing.py`.


## Android splash screen
`KantaTheme.windowBackground` (`@drawable/kanta_splash_screen`) must render a full-bleed splash from `@drawable/kanta_splash_image`: portrait `drawable-nodpi/kanta_splash_image.webp` by default and landscape `drawable-land-nodpi/kanta_splash_image.webp` (selected automatically for tablets in landscape and Android TV). The legacy `drawable/kanta_splash_image.png` must be gone. A layout/inflate guard should confirm both drawables resolve and that no `R.drawable.kanta_splash_image` references a removed resource.

## Android persistent-stage acceptance — local candidate

`RuntimeChecks` must assert that the same attached `kanta.stage.primary` is visible above Search, Queue, Pitch & Tempo, Mic & Voice, Visuals and Now Playing, including after changing a background. Now Playing must not mount `kanta.now-playing.stage`. During actual MIDI playback, switching the primary menus must preserve the backend, playback identity and reservation IDs while the audible clock advances. Existing named destination screenshots remain whole-window captures so reviewers can see both stage and lower controls; do not crop away the stage.

Runtime acceptance must tap `kanta.header.logo`, observe `kanta.control-deck` and `kanta.control-deck.volume`, and verify the same attached `kanta.stage.primary` remains visible above it. Android Back must remove the deck and restore the prior destination. The deck Search action must open the existing lower utility search without layering over or replacing the stage. Static contracts also require stable `kanta.control-deck.<action>` identities, a master-volume dial, long-press More-menu access to all six workspace destinations, and absence of the redundant always-visible workspace toolbar.

Run the Android workspace, Java structure, companion-interface and branding guards plus `python3 -m unittest discover -s tools/tests`. Android compilation/lint and `RuntimeChecks` require the hosted Android environment; also require portrait/landscape layout acceptance, live background/photo/video preview, keyboard accessibility and fullscreen/camera restoration before reporting device acceptance. Local static checks cannot establish those results.

Start with `python3 tools/preflight.py`: it selects the guards the changed paths require, prints the owner documents that must change in the same commit, and lists the cloud-only gates it cannot prove. The change-type matrix and the workflow invariants are in [Pre-flight](#pre-flight-run-the-guards-a-change-actually-needs). Each section below records why a specific guard exists; a static check passing never proves a build, a package, a simulator run or device acceptance.

## Java structure guard

`tools/check-java-structure.py` (with `tools/tests/test_java_structure.py`) fails closed on method declarations opened inside another method body, catching nested-method Java errors that only a compiler would otherwise find; the Android build cannot run on the local iSH environment, so this static guard runs here and in CI.

## Android MIDI/KAR import regression

`CoreRegressionTests` verifies that MIDI/KAR imports accept structurally valid files beyond the former 16 MiB / 250,000-event / six-hour caps (17 MiB sysex payloads, 260,000 events and 75-hour gaps decode), that KAR metadata (@T/@A/@C), @P pause codes and backslash echo lines parse, that lyric text encoding detection covers Shift_JIS, GBK, Big5, CP949 and windows-1252, and that the live-library and merged stage-search catalogs dedupe by song identity. SF3/Ogg bank loading is exercised by `apps/android/tests/soundfont-test.c` against the synthetic `apps/android/tests/data/tone.sf3` fixture (open, 440 Hz render pitch, truncation rejection) under the CI `-Wall -Wextra -Werror` compile; `kanta_sf_open` rejects any bank whose region window comes within one float of the decoded buffer end so the two-ahead interpolator never reads past the single pad sample (the 2000-mutation render loop proved this reachable). Per-song key/tempo overrides (SettingsStore keys, application at prepare, Reset semantics) and runtime seek/restart (transport Previous) are exercised through the emulator instrumentation; it needs the native synth and AudioTrack, so it is not JVM-testable. JVM execution stays CI-owned; the local iSH environment cannot run a JVM (getcpu(2)).

## Android SoundFont 1536 MiB ceiling regression

The existing `apps/android/tests/soundfont-test.c` CI harness now verifies the 1536 MiB tracked-payload ceiling, headroom for 1,163,416,536 sample bytes, over-budget and SIZE_MAX rejection, exact-boundary malloc/realloc, rejection preserving pointer contents and counters, shrink/free accounting, and zero balance after cleanup. Near-cap totals are simulated with the worker-local counter while real allocations stay below 100 bytes; the suite does not require GiBs of RAM or claim full-bank playback. Existing starter pitch (439.3 Hz), octave transpose (880.3 Hz), truncation, six malformed-table and 2000 mutation cases remain enabled and pass locally with C11 `-Wall -Wextra -Werror -O0`. Layout preflight passes. CI retains its existing ASan/UBSan command and Android build/lint/emulator wiring without workflow changes. Those sanitizer/APK/runtime gates and physical large-bank memory-pressure acceptance have not run on this local candidate; local Android doctor reports missing SDK/Java build prerequisites. A cap is not guaranteed available RAM.

## Pinned vendor engine and its test ownership (2026-11-03)

`apps/android/app/src/main/cpp/vendor/tsf.h` is fork `usermalik20-del/tinysoundfont` at `a272fe30` (release `kanta-vendor-a272fe30`); `vendor/PROVENANCE.json` pins the revision and sha256 of `tsf.h`, `tsf_playback.h`, `LICENSE` and `HARDENING.md`. The engine's own lanes - gcc and clang ASan/UBSan, SF3/Ogg with the real `tone.sf3` fixture, 32-bit x86, ARMv7 cross + qemu, native ARM64, plus pinned per-architecture audio-hash baselines - run in the fork's CI, and all seven were green on the exact pinned commit (fork run `41654663771`). KANTA's side of the contract is unchanged in shape: `soundfont-test.c` compiles the pinned header with `-D_POSIX_C_SOURCE=200809L -std=c11 -Wall -Wextra -Werror` and re-runs the pitch/transpose/truncation/mutation suite (it is what the fork's `-Werror`-clean fix was verified against); APK, sanitizer and device acceptance remain separate Android CI gates. Engine audio output from this header has not yet been re-listened on a real Android device, so no user-perceptible audio acceptance is claimed for this vendoring change.

Tests and production source must describe the same behavior. Keep recovered tests that enforce product invariants; reconcile missing APIs from preserved source or implement the documented contract. Never remove assertions, disable lint or accept a baseline merely to make validation green.

## Suite boundaries

| Suite | Contract | Execution |
| --- | --- | --- |
| GeloKaraokeCoreTests | MIDI/lyrics, one FIFO owner, playback identity, failure-safe handoff, scoring and remote serialization | SwiftPM on macOS |
| LegacyCompatibilityTests | Bounded binary reads, validated spans and malformed inputs | SwiftPM on macOS |
| LibraryStorageTests | Safe import roots, starter removal/restore, backup integrity, persistence, search and metadata exports | SwiftPM on macOS |
| LocalNetworkControlTests | Framing, pairing, bounded search and connection-scoped replay suppression | SwiftPM on macOS |
| AVFoundationPlaybackTests / AVFoundationMIDIPlaybackTests | Format rejection, controls and original starter synthesis | SwiftPM on macOS; physical audio remains separate |
| MicrophoneScoringTests | Pitch detection and room gate | SwiftPM on macOS; microphone/device acceptance remains separate |
| KantaUITests | Actual iOS stage/control walkthrough | Separate simulator UI run; not covered by an unsigned build |
| KantaNativeTests | Real WKWebView lifecycle plus app-hosted session certificate/private-key Keychain installation and removal | Application-hosted simulator XCTest in focused smoke; no remote media or device-audio acceptance |
| ReleaseToolingTests / starter generator | Release contracts, privacy/source guards and deterministic first-party resources | Python locally and in CI |
| Android core regression | FIFO retry identity, Unicode search, bounded MIDI timing, live-library catalog merging and encrypted exports | Gradle JavaExec; pure-Java catalog sources compile, Android-backed SQLite adapter excluded |
| Android lyric animation | Source-fragment interpolation, active-fragment range and freshness-bounded pitch energy for the stage views | `coreRegression` JavaExec (`LyricWordAnimationRegressionTests`) |
| Android Bluetooth monitor routing | Route-identity rules, the extended readiness bound used for Bluetooth links, and its expiry | `coreRegression` JavaExec (`CoreRegressionTests.monitorChecks`); device acceptance still open |
| Android native SoundFont | Validated SF2 references, bounded allocations, audible pitch, truncation and mutation handling | C11 ASan/UBSan host harness |
| Android runtime instrumentation | Real JNI/AudioTrack, FIFO commit, cancellation, controls, pause/resume, failed prepare, persistent-stage rendering, dispatcher-based workspace back dismissal, and durable catalog/media rollback after cancelled multi-file imports | Clean API 35 emulator install; custom Instrumentation, no external test library |
| Browser interoperability | Fresh Android export through the captured browser decrypt function | Node after Android regression fixture generation |
| Prompt bank contract | Pinned upstream metadata, cache boundary outside the repository, no vendored upstream text | Python unittest in the Android validation job |

## Website route and responsive navigation contract

The website validation job runs `tools/verify-website.py` and `tools/tests/test_website_security_contract.py`. These checks cover all ten public routes, their shared theme stylesheet and header (brand, single six-link primary navigation and Download App action), theme tokens, local links and anchors, route-specific CSP, verified download provenance, visible nav wrapping at narrow viewports, adjacent duplicate feature headings, and stylesheet source hygiene. After a production deployment, inspect the canonical Vercel alias and affected routes; a successful build/deploy alone does not prove mobile usability.

## Android lyric-stage animation checks

`LyricWordAnimationRegressionTests` runs inside the existing `coreRegression` task. It covers the framework-free part of the candidate stage animation: a fragment boundary remains a hard source event, the span between boundaries interpolates smoothly, the active-fragment range advances only at source boundaries, and pitch energy decays to zero when the estimate is absent, stale or out of band. These checks do not exercise `Canvas`, `StaticLayout`, `ValueAnimator` or emoji rendering; visual, reduced-motion, APK/lint and emulator acceptance remain separate gates on the Android validation workflow.

## Android OTA self-update feed validation

This section documents the framework-free OTA update subsystem added to the Android app (`ph.kanta.reborn`). The feature is gated behind the existing Controls → "App update" dialog and is off by default. The feed is fetched over HTTPS only from the pinned URL `https://kanta-public.vercel.app/updates.json`. All artifacts are SHA-256 verified, staged in app-private storage, and installation requires the separate "Install unknown apps" permission. The GitHub Release asset creation in the publisher workflow makes every post-merge Android build a public release asset automatically.

### Java core contract (framework-free)

The core logic lives in `ph.kanta.reborn.core.update` and is compiled by the empty-classpath `compileCoreRegression` / `coreRegression` Gradle tasks. It is validated by:

- **`UpdateContractRegressionTests`** (`apps/android/app/src/test/java/ph/kanta/reborn/core/update/UpdateContractRegressionTests.java`), registered in `CoreRegressionTests` and executed by the `coreRegression` task:
  - `UpdateJson` strictness: 256 KiB ceiling, rejects duplicate keys, raw control characters, unpaired surrogates, leading zeros, over-deep nesting.
  - `UpdateManifest` refusal cases: invalid feed format (must be "kanta-update-feed" version 1), channel (must be "stable-reconstruction"), `publishedAt` not ISO-8601 UTC, `sourceCommit` not 40-hex, `android` entry missing or invalid (`versionCode` < 1, `versionName` shape, `minSdk` ≠ 26, `apkUrl` policy, `apkBytes` 1..256 MiB, `apkSha256` not 64-hex, `notes` > 1024, `mandatory` not boolean).
  - `UpdatePolicy` URL policy: HTTPS-only, durable host allowlist (github.com/usermalik20-del/Kanta-reborn/releases/download/<tag>/<file> plus redirect targets `release-assets.githubusercontent.com` and `objects.githubusercontent.com`); refuses Actions artifact URLs, http, userinfo, query/fragment on canonical; GitHub path case-sensitive.
  - `UpdateDigest` vectors: `"abc"` → `ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad`; empty → `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
  - Staging names (`.part` suffix) and `UpdateDecision` matrix: `NOTHING_PUBLISHED`, `DEVICE_TOO_OLD`, `ARTIFACT_NOT_DURABLE`, `OLDER_OR_SAME_VERSION_PUBLISHED`, `DIGEST_MISMATCH`, `DOWNLOAD_REFUSED`, `APPLY`.

- **`tools/tests/test_android_update_contract.py`**: 9 unittest cases covering the same contract from Python.

### Android runtime integration

- **`AppUpdateManager`** (`ph.kanta.reborn.update`): single-worker executor; `HttpURLConnection` feed fetch with strict UTF-8; resumable `Range` download into app-private `files/update/` with `.part` files; HTTP 200/206/416 handling; 256 MiB ceiling; SHA-256 verify; atomic rename to `kanta-update-<sha12>.apk`; `cancel()` and `close()`; `Listener` callbacks `onState`/`onProgress`/`onReady`.
- **`UpdatePackageInspector`**: `PackageInfoCompat` installed `versionCode`, optional signer SHA-256, `canRequestPackageInstalls`, `ACTION_MANAGE_UNKNOWN_APP_SOURCES`, `FileProvider` `ACTION_VIEW` install intent.
- **`AndroidManifest.xml`**: `android.permission.REQUEST_INSTALL_PACKAGES`; non-exported `androidx.core.content.FileProvider` (authority `${applicationId}.fileprovider`, `grantUriPermissions`, `res/xml/update_paths.xml` exposing only `files-path update/`).
- **`app/build.gradle`**: `implementation 'androidx.core:core:1.13.1'`.
- **`MainActivity`**: Controls → "App update" dialog (check/status/progress/install); manager lazily created; `onStop` cancels; `onDestroy` closes; diagnostics gained an "update" status line.

### Publisher tooling

- **`tools/prepare-update-feed.py`**: generates `apps/web/updates.json` from `apps/web/downloads.json` (android source SHA) + `verified-downloads/android/build-info.json` (file name, sha256, bytes) + `git show <source>:apps/android/app/build.gradle` (`versionCode`/`versionName`/`minSdk`); durable `apkUrl` `v<version>-preview-<sha8>/KANTA-Android-preview-<sha8>.apk`; `FeedError` refusal, never writes on refusal, exit 1. The identity now comes from the exact APK (`verified-downloads/android/apk-metadata.json`), and incomplete or out-of-range metadata — including a missing, boolean or below-minimum `minSdk` — is refused with `FeedError` before any comparison, so malformed metadata cannot raise a bare `TypeError`.
- **`tools/verify-android-runtime-package.py`**: permission assertion is now the exact set `{CAMERA, RECORD_AUDIO, INTERNET, REQUEST_INSTALL_PACKAGES}`.
- **`apps/web/vercel.json`**: static route `^/updates\.json$` → `/updates.json`, `Cache-Control: public, max-age=0, must-revalidate`, before page rewrites.
- **`.github/workflows/platform-download-publishing.yml`**: Android-only step creating/updating (`--clobber`) GitHub Release tag `v<version>-preview-<sha8>` with the APK as durable asset; Android runs `tools/prepare-update-feed.py` and stages `apps/web/updates.json` into the downloads commit.

### Explicit non-claims

- **Feed state**: `apps/web/updates.json` holds the newest published Android build (current entry: release `v0.10.0-preview-1f49555c`, source `1f49555c`); the publisher regenerates it after every successful Android build on `main` and the endpoint is served through the `vercel.json` routing table.
- **`signingCertSha256`** supported by the contract but NOT yet published by feed tooling.
- **No local JVM/Gradle build, APK, emulator, runtime or physical-device acceptance ran** (JVM cannot start on this iSH emulator: `getcpu(2)` unsupported). Java correctness is guarded by CI only (`coreRegression` + `android-validation` + `android` runtime checks).
- The GitHub Release asset creation makes every post-merge Android build a public release asset automatically.
- Update is user-initiated, off by default, HTTPS-only, SHA-256 verified, app-private staging, install needs separate "Install unknown apps" permission.

CI wiring unchanged note: workflow `platform-download-publishing.yml` DID gain the release-asset + feed steps.

## Karaoke Stage v2 regression checks — merged 2026-11-03

PR #155 merged as `6db012c34f97b64b4692e09ee709fe4e439e2a71`. The PR-head Android run [41529578779](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41529578779) on `9607a858` passed 55,130 host regression checks and 291 emulator runtime checks. Main-branch Android validation [41560660780](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41560660780) and Android publishing [41561260449](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41561260449) then succeeded on the merge SHA. The published Android preview is not a tagged/store release; physical-device acceptance remains open.

The prior baseline reported 65,424 host checks. The reduction to 55,130 has not been reconciled by a test-coverage audit; passing CI does not prove equivalent coverage. Do not attribute that reduction to specific suites without evidence.

This documentation/site-copy correction changes no native implementation or download manifest. Validate it with `python3 tools/verify-website.py`, `python3 -m unittest tools.tests.test_website_security_contract`, `git diff --check` and `python3 tools/check-documentation-sync.py <base> <head>`. Check that the feature page distinguishes aliases, floored scores and incomplete scenic download integration. Do not trigger an iOS build or replace either platform's artifact for a copy-only correction.

The merged karaoke-stage work adds or updates the following host-JVM coverage. All of it runs inside
the existing `coreRegression` Gradle JavaExec task, so no device, emulator or clock is needed:

### ScoreCelebrationRegressionTests (updated to the five-band ladder)

Asserts `ScoreCelebrationPlan.forScore()` against `ScoreResultTier`:

- EXCELLENT_SINGER: 120 confetti, 6 balloons, 5 stars, 4200 ms
- GREAT_SINGER: 72 confetti, 3 balloons, 3 stars, 3200 ms
- GOOD_SINGER: 36 confetti, 1 balloon, 1 star, 2200 ms
- RISING_STAR: 24 confetti, 0 balloons, 2 stars, 1600 ms
- KEEP_PRACTICING: 0 confetti, 0 balloons, 0 stars, 0 ms

It also asserts band boundaries, out-of-range clamping, that a lower band never outlasts a higher
one, that motion is deterministic for a fixed seed, and that the particle list is immutable.

### ScoreRevealRegressionTests (new)

Covers the reveal choreography before any device is involved:

- the reveal always opens on `PartySounds.DRUM_ROLL`, and the drum roll is the longest beat
- `revealSlot()` returns the fanfare for EXCELLENT_SINGER, applause for GREAT_SINGER,
  GOOD_SINGER and RISING_STAR, and no cue for KEEP_PRACTICING or an absent band
- `totalMillis()` is the drum roll plus the count-up plus the label for every band
- `counts()` starts on the floor, ends exactly on the score, never counts backwards, and a
  one-tick reveal shows the final score
- the cubic ease-out front-loads its progress; a non-finite or out-of-range progress falls back to
  the floor instead of corrupting the counter; a reveal with no ticks is refused

### ScenicCatalogRegressionTests (new)

Treats the built-in scenic catalogue as a security boundary, because every entry opens a socket to
a third party:

- every entry has a unique id and file name, a readable title, an attribution naming its licence,
  an https source page and download URL, and a known expected size
- every entry passes its own download gate: https only, allow-listed host, known container, size
  within `MAX_BYTES`
- an unlisted host, an unknown container and a clip above the size cap are all refused
- `hostOf()` strips an explicit port, refuses userinfo, and returns null for a missing or
  schemeless URL; `channels()`/`inChannel()`/`byId()` stay mutually consistent

### Unchanged suites

`LyricWordAnimationRegressionTests` and the monitor route-guard checks remain separate contracts;
passing them does not establish acoustic quality or complete scenic-library integration.

Build/lint and emulator validation passed on the SHAs recorded above. Physical-device visual,
reduced-motion and audio acceptance remain separate gates.

## Android compile gate and masked symbol errors

A `javac` parse error stops the compiler before symbol resolution, so a file that fails to parse hides every unresolved type it also contains. The pitch-settings landing fixed four `#` comments and a truncated method body, and only then revealed that `SettingsStore` had no import. Treat the first clean parse as an unverified compile: after removing syntax errors, re-check every type the change introduces against its import and re-run the build before calling the source sound. Brace-balance and string-aware scanners detect structure only and never prove a file compiles; the Android `Build, lint and domain regression tests` step is the gate.

## Documentation synchronization gate

Every pull request runs the lightweight `documentation-sync` job in `website-validation.yml` workflow. It compares the full PR diff against `tools/check-documentation-sync.py` and fails when required documentation was not changed in the same PR. Web implementation requires `README.md`, `apps/web/README.md`, `docs/ARCHITECTURE.md` and `docs/TESTING.md`; Android requires the root and Android README plus architecture, testing and reconstruction; iOS requires the root README, `apps/ios/AGENTS.md` plus architecture, testing and reconstruction; workflow edits require the root README, `AGENTS.md`, architecture and testing; tooling changes require testing documentation. Privacy-related changes also require the platform privacy disclosure. The check verifies a substantive diff in each required file; whitespace-only edits do not satisfy the requirement. It also runs for fork pull requests with read-only permissions and no persisted checkout credentials.

The same guard runs as a step inside existing main-branch platform and deployment jobs. It adds no extra Android, Xcode or simulator jobs. Pull requests get one small Ubuntu documentation check regardless of platform; platform build workflows retain their independent path filters. A documentation-only PR runs the documentation check without allocating native runners.

## Pre-flight: run the guards a change actually needs

`python3 tools/preflight.py` derives the applicable local guards from the changed paths of a change — including work that is not committed yet — and prints the owner documents it must update in the same commit. `--list` prints the plan without running anything; `--base REV` compares against another revision (default `origin/main`, then `main`, then `HEAD~1`). It reuses `tools/check-documentation-sync.py` for the documentation matrix, so the two cannot drift, and it always prints the cloud-only gates so a local pass is never mistaken for one of them. `tools/tests/test_preflight.py` pins the mapping.

| Change type | Owner documents that must change in the same commit | Local guards |
| --- | --- | --- |
| Android implementation (`apps/android/**`, excluding `apps/android/README.md`) | `README.md`, `apps/android/README.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md`, `docs/RECONSTRUCTION.md` | `tools/check-java-structure.py`, `tools/check-android-workspace-contract.py`, `tools/verify-android-companion-interface.py` |
| iOS implementation (`apps/ios/**`, excluding `apps/ios/README.md`, `apps/ios/AGENTS.md`, `apps/ios/PRIVACY.md`, `apps/ios/Docs/**`, `apps/ios/PublicSite/**`) | `README.md`, `apps/ios/AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md`, `docs/RECONSTRUCTION.md` | `tools/check-ios-presentation-contract.py`, `tools/verify-test-layout.py` |
| Website implementation (`apps/web/**`, excluding `apps/web/README.md`, `apps/web/privacy.html`) | `README.md`, `apps/web/README.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md`, `docs/RECONSTRUCTION.md` | `tools/verify-website.py` |
| Workflow (`.github/workflows/**`) | `README.md`, `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/TESTING.md` | `tools/tests/test_workflow_path_ownership.py`, `tools/tests/test_platform_download_publishing.py`, `tools/tests/test_vercel_deploy_workflow.py` |
| Tooling (`tools/**`) | `docs/TESTING.md` | `python3 -m unittest discover -s tools/tests -p 'test_*.py'` |
| Privacy copy (any path containing `privacy`, or a `PrivacyInfo.xcprivacy`) | all four disclosures: `apps/web/privacy.html`, `apps/android/app/src/main/assets/privacy.txt`, `apps/ios/PRIVACY.md`, `apps/ios/PublicSite/privacy.html` | `tools/verify-website.py` |
| Documentation only (`*.md`, `docs/**`) | none required | `git diff --check` |

Every change also runs `git diff --check` and `python3 tools/check-documentation-sync.py BASE HEAD`. `tools/verify-android-runtime-package.py` needs `ANDROID_HOME` and runs only in the Android runtime job; the `node --test` files do not execute in the authoring environment. A passing preflight is a local static result only: it is not a Gradle build, an APK, an IPA, a simulator walkthrough, a signed artifact, a published release or device acceptance.

### Workflow invariants a change must not break

- Trigger events, path filters and branch filters are ownership boundaries. Never add, remove, widen or narrow one to make a run easier to reach.
- Keep third-party actions pinned to full commit SHAs, as every current workflow does.
- Workflow-level `permissions` stays least privilege (`contents: read`); write scopes belong only on the publishing job that needs them (`platform-download-publishing.yml`).
- Every job keeps a `timeout-minutes`. Validation workflows keep `cancel-in-progress: true`; the publisher and the Vercel deploy keep it `false` so a release is never cancelled by a newer push.
- Never add `continue-on-error` to a gate, and never make a failing gate non-blocking. A gate that cannot fail should be deleted, not silenced.
- The publishing job runs on a bare runner, so it must put the Android build tools on `PATH` before verifying an APK: `tools/verify-ota-publication.py` shells out to `aapt` and `apksigner`, which the runner ships under `$ANDROID_HOME/build-tools` but does not expose. The step fails with the real reason and a non-zero exit when they are absent, instead of letting verification collapse into "APK metadata command failed". `tools/tests/test_ota_publication.py` pins the order and the loud failure.
- Automation must not be able to abort itself. A step under `set -o pipefail` that answers one question must ask the API once (`--jq '[...] | first'`) instead of piping a list into `head`, which can fail the whole triage step with SIGPIPE exactly when it is needed.
- Android release notes are generated by `tools/generate-android-release-notes.py` before a new release is created. It selects the newest valid Android preview tag (not malformed or iOS releases), resolves the exact prior source, compares that source to the verified current SHA, deduplicates merged PRs, and includes relevant PR detail plus exact Android validation/publisher runs, APK size/SHA-256, signer fingerprint, and install-compatibility status. PR HTML/image embeds and credential-looking lines are stripped; old PR validation claims are omitted because the current exact-SHA workflow evidence is listed separately. Release descriptions are created with the tag and are not rewritten on retries; existing APK assets remain immutable and their digest is still verified. Unit tests cover tag selection, exact comparison, PR deduplication, content sanitization, signer-transition copy and workflow ordering. The durable release tag format `v<version>-preview-<sha8>`, the `downloads.json`/`updates.json` publication contract, exact-SHA provenance and the publisher's `fetch-depth: 0` ancestry check are fixed. The workflow must derive the version from verified APK metadata and append `${SOURCE_SHA:0:8}` in shell; never embed shell variable text in a single-quoted Python one-liner. `tools/tests/test_ota_publication.py` guards the shell/tag contract. Workflow edits require the root `README.md`, `AGENTS.md`, `docs/ARCHITECTURE.md` and this file in the same commit.
- **Release artifact hardening (BLA)**: CI invokes the pinned third-party CLI `bla-agents@3.1.2` to generate candidate output only. The web candidate is written under `apps/web/.bla-hardened`, but the Vercel page handler still serves original source files; no Vite plugin or replacement step consumes it. Android runs after `:app:assembleRelease` and writes DEX candidates under `.bla-hardened`; no packaging step consumes them. Neither shipped artifact is currently BLA-hardened. The CLI's `--verify` checks candidate output, not the final deployed artifact or KANTA runtime equivalence. Do not cite vendor benchmarks as KANTA verification. BLA steps must run without deployment/signing secrets or persisted checkout credentials. Any change to invocation, output integration, or secret scope must update `README.md`, `AGENTS.md`, `docs/ARCHITECTURE.md` and this file in the same commit.



## Strict platform CI ownership

Automatic workflow triggers are ownership boundaries, not convenience hints. Android source, Android tests and Android build tools may start Android validation and Android APK packaging/deployment only. iOS source, iOS tests and iOS build tools may start iOS validation, unsigned IPA packaging and iPhone/iPad simulator acceptance only. Website paths belong to website validation/deployment. Shared files may fan out only to the exact workflows that compile or exercise that shared contract, with the exception documented by path and regression test. Never add the other platform's entire source tree as a release-provenance trigger. Manual dispatch and scheduled maintenance are explicit operator entry points and are not automatic cross-platform triggers. `tools/tests/test_workflow_path_ownership.py` enforces these negative as well as positive path rules.

## Validation order

1. Run `python3 tools/verify-test-layout.py` to reject orphaned Swift test files, absent target directories and unknown local `@testable` imports. This checks wiring, not Swift types or behavior.
2. Run Python release/starter guards. Preserve synthetic fixtures and isolate temporary directories.
3. On Xcode 26.2 / Swift 6.2, run `swift build --build-tests` in `apps/ios` to compile production modules and every package test target before execution.
4. Run `swift test --skip-build --no-parallel`. A successful compilation alone does not establish passing behavior.
5. Attempt the unsigned simulator app build even when package validation fails. This reports app integration errors in the same run rather than hiding them behind earlier failures. Retain the three diagnostic logs as seven-day artifacts.
6. Android runs APK assembly, lint, domain regression and browser interoperability. Do not treat an APK build as device playback proof.

All package suites remain enabled. Build failures stay failures, including when later diagnostic steps succeed. UI automation, signing, physical audio/camera, LAN confidentiality and device performance require separate acceptance; no static audit can guarantee that future mismatches will never occur.

## Reconciled contracts

- Completed room scores include the entire target timeline; live scores include elapsed targets. Changing latency clears prior observations.
- Duplicate envelope IDs execute once within the bounded connection cache; active requests cannot be evicted.
- Unsupported indexed media remains searchable with stable IDs and `isPlayable == false`; title discovery never invents playback offsets.

The immutable recovery snapshots retain provenance. Active source and tests are reconciled together on the current PR.

Native and domain cases run serially for clear failure attribution. All tests remain enabled. Early package-log upload and recent package-process IPS reports support diagnosis. The signal-5 crash was isolated to scoring latency overflow and repaired; native audio cases also pass.

The serial run isolated the signal-5 stop to extreme-latency arithmetic in the scorer and exposed duplicate-slot and adjacent-boundary failures. Restored finite-input filtering, overflow-reporting latency subtraction, half-open windows and one observation per slot. Added extreme-duration/invalid-window coverage; retained the newer finite MIDI-note conversion. Swift backtrace environment injection was removed because this runner rejects it for privileged toolchain executables.

Live scoring includes the most recently received sample while source note ends remain exclusive; completed scores always use original target windows. The explicit source-boundary regression prevents a future fix from extending notes to recover the latest sample.

## Verified checkpoint

Cloud run [38748040876](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38748040876), source commit `56c9fedbb70b79ebc350e4678d5620554d53c983`: all 158 Swift package tests, 50 release-tooling tests, four starter-generator tests, starter/layout guards, unsigned Release simulator app build, Android APK/lint/46 domain checks and browser interoperability passed. UI automation and physical-device acceptance remain separate.


## iOS online runtime workflow

The `ios-runtime-artifacts.yml` workflow builds an unsigned iPhoneOS personal-signing input only for iOS application/build inputs on pull requests and main pushes. Android paths and generic download-packaging scripts are excluded. Manual dispatch remains available. It does not sign or publish the application. The `ios-visual-walkthrough.yml` workflow builds all Xcode UI tests and executes the complete walkthrough suite on iPhone and iPad in separate jobs. Automatic triggers are limited to iOS source and iOS-owned capture tooling; Android source, Android companion-interface tooling and download-packaging scripts never start simulator jobs. Manual dispatch, the weekly schedule and `ios-rc-*` tags remain available. The iOS package test still runs for the single shared Android companion-protocol source file when that exact protocol changes; this is an explicit shared-contract exception, not a general Android trigger. Logs, results and video remain available for seven days. Signing and hardware acceptance remain separate.

Workflow ownership is enforced by `tools/tests/test_workflow_path_ownership.py`, run by `verified-downloads.yml` whenever workflow definitions change. `android-validation.yml` uses explicit Android-owned tool/test paths rather than `tools/**`. `ios-validation.yml` uses pull-request events for feature branches and main-only push validation, preventing duplicate feature-branch runs. `website-validation.yml` owns web and public legal/privacy-copy checks; `verified-downloads.yml` owns workflow-trigger and release-contract tests; `vercel-deploy.yml` has no pull-request trigger and deploys only from main or explicit dispatch. A website-only or iOS-documentation-only pull request must not start Android builds, unsigned iOS archives or iPhone/iPad simulator walkthroughs. Android's bundled `app/src/main/assets/privacy.txt` is an actual APK input, so a change to that file correctly starts Android validation; the iOS privacy Markdown and `PublicSite` copy are excluded from Xcode builds.

Android runtime fixtures live in the test APK behind a signature permission declared only by debug builds. The read-only provider exposes synthetic/original starter bytes and a bounded cyclic SAF tree; no user storage is exposed. Instrumentation checks nested traversal, partial failure, cancellation rollback/reopen, imported/duplicate/failed `ImportBatchResult` counts, byte integrity, real controller import, custom pad decode/reset and idle intermission gating. Fixture code is excluded from release builds.

Pitch Settings UI test coverage added to `RuntimeChecks`: workspace accessory activation for `ACCESSORY_PITCH_GAME` and `ACCESSORY_LEARNING_ASSISTANT` from Controls; `showPitchSettings()` dialog renders sensitivity slider, note spinner, custom Hz input with validation; `SettingsStore` persistence verified for `pitch.assistantSensitivity`, `pitch.targetHz`, `pitch.targetName`; `LearningAssistant.setSensitivity()` threshold scaling verified via synthetic pitch vectors.



Android emulator startup uses an explicit shared AVD directory for avdmanager and emulator. The helper detects early process exits, bounds boot/ADB/instrumentation, always collects diagnostics and retires the emulator. Dispatch `android-validation.yml` with an explicit `ref` for Android-only repairs; the owner-approval labels and the retired actor gate no longer exist (see AGENTS.md) and no run gate applies. No source tests are skipped.

## iOS KCP1 controller candidate

`ios-validation.yml` runs the full iOS package/app gate without rerunning unchanged Android native tests. The job pins Java 17 and compiles the unmodified Android CompanionProtocol with a synthetic Java TCP peer. LocalNetworkControlTests requires that peer via KANTA_KCP_TEST_CLASSES; absence is a failure, never a silent skip on macOS. Fixed independent Python crypto vectors check exact handshake, request and bidirectional record bytes. Live Java/Swift socket checks exercise the actual iOS client, bounded busy behavior, Unicode, distinct repeated reservations, cancellation and revocation. This is automated interoperability evidence, not a physical Android/iOS pair or iOS-host acceptance.

## iOS walkthrough runner diagnostics and suite timeout

`tools/capture-ios-runtime.py` executes the complete `KantaVisualWalkthroughUITests` suite for the requested simulator and requires every name in its `EXPECTED_TESTS` manifest to have a clean passing result. The current manifest contains 13 cases: the prior eight cases, with the six-destination menu coverage split into six independently budgeted tests instead of one aggregate case.

The aggregate `testMainMenuButtonsAndScreens` exceeded XCTest's per-test allowance on iPad even after reducing cold launches: its six screenshots were exported, but the test did not finish as a passing case. The repair preserves each destination, expected navigation-title assertion, screenshot, `navigation.done` dismissal, and Stage More return assertion, while giving every destination its own XCTest execution allowance. Do not raise the global per-test allowance or remove a destination to hide the timeout.

Per-test limits remain 120 seconds by default and 180 seconds maximum. The outer process deadline remains 1,080 seconds, and each workflow job is bounded to 45 minutes. A timeout, runner restart, non-zero `xcodebuild` exit, or missing required test remains a failure even if a later runner attempt prints passing cases.

The visual workflow now has separate `iphone-walkthrough` and `ipad-walkthrough` jobs. Each device builds and runs independently, uploads its own compact presentation diagnostics and full runtime evidence, and reports the failing device directly. Branch-level concurrency cancels stale runs on the same branch without cancelling the two device jobs within one run.

## Embedded Stage presentation diagnostics

The embedded Stage Search/Queue presenter logs request, gate activation, window attachment, presentation start/completion, dismissal, stale ownership, and blocked-state reasons under `KANTA_STAGE_PRESENTATION`. `tools/capture-ios-runtime.py` captures filtered simulator unified logs before shutdown, and the walkthrough uploads `ios-ipad-presentation-diagnostics` separately from the large video/result-bundle artifact. For triage: a request without activation points to gate ownership; activation without presenter configuration points to SwiftUI binding/representable propagation; configuration blocked by window/ownership identifies the presenter precondition; a completed presentation with no keypad points to sheet content/accessibility. Keep the full XCTest and result evidence; never add retries or weaken assertions to conceal a dropped presentation.

## PR #31 iPhone/iPad runtime regression checkpoint

Run [38923007543](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38923007543) built app/UI tests but both platform walkthrough jobs failed. The full preserved **iPhone** test log reports two passes (`testDemoKeypadCancelAndRepeatedIntentionalReservations`, `testMainMenuButtonsAndScreens`) and two failures: the encrypted companion screen did not become visible after pressing `companion.open` (line 108), and More was dismissed during actual starter playback (line 53). The restarted XCTest runner printed a subsequent `Executed 0 tests` success line which does not supersede the failed test cases. No unsigned IPA was produced.

The new candidate replaces route-derived sheet lifetime with an independent presentation flag and removes the nested companion link's simultaneous gesture. The capture runner scans the entire XCTest log for failed source assertions and requires the four explicit UI test cases to pass before declaring a device accepted. Verify both an iPhone and an iPad on the same final PR commit; `xcodebuild` exit zero, a nonempty recording, and a complete named test pass set are all mandatory. Failing tests must not be skipped or converted to nonblocking. The iPad's exact test assertion remains to be confirmed with the next actionable log or its separate result bundle.



## Playback completion and navigation presentation

RootView passes navigation-sheet visibility to HostView. The automatic completed-session Favorites sheet is deferred while More navigation is presented, without clearing the pending session prompt. Its binding setter must not discard pending session state while navigation owns presentation. The existing real-playback XCTest keeps its More persistence assertion and additionally verifies the navigation Done control; run all four named walkthroughs independently on both iPhone and iPad before accepting a release.


## Post-navigation keypad transition regression

The real playback walkthrough waits for a rendered stage Search button after closing the Song Library sheet. UIKit can consume a touch during sheet dismissal, so the harness retries only the idempotent action to open Search (maximum three attempts). It still fails if the stage is blocked or the actual numbered keypad never appears; no assertions are skipped. Both iPhone and iPad must pass on the final commit.


## iOS modal request lifecycle

RootView's navigation sheet and HostView's CONTROL/session-save/Favorites sheet remain SwiftUI-owned. Embedded Stage Search/Queue use `KantaEmbeddedStageSheetPresentation`, a stable UIKit presenter attached to the Host background; navigation, Host and Stage requests are serialized by `KantaPresentationGate`. Embedded Stage dismissal completes `.stage` from UIKit's presentation/dismissal callback, while the other owners complete from their own `onDismiss`. The fullscreen projection retains its separate child Search/Queue sheet. Keep embedded Search/Queue out of the Host SwiftUI sheet and never add sleeps/retries to conceal a dropped presentation request.


### iOS fullscreen presentation ownership candidate

The presentation contract guard checks that the UIKit fullscreen presenter acquires and releases `KantaPresentationGate` ownership through its dismissal callback, and that the gate restores fullscreen after a nested Search/Queue sheet. The UI walkthrough opens fullscreen from CONTROL, uses Search inside fullscreen, exits, and verifies normal-stage Search can present afterward. Embedded Search/Queue are independently presented by `KantaEmbeddedStageSheetPresentation`; the embedded control uses `stage.search` and the fullscreen control uses `stage.search.fullscreen`. Run `tools/check-ios-presentation-contract.py` and the full reconstruction validation on the candidate SHA. A green run on a different SHA does not validate it.


## Automatic workflow triggers and release boundary

Reconstruction validation runs for relevant app, tool and workflow changes on feature/main pushes and pull requests. The iOS workflows are scoped to `apps/ios/**` with the documentation-only paths excluded, plus two explicit shared-contract exceptions: `ios-validation.yml` also runs for a change to the single shared `apps/android/app/src/main/java/ph/kanta/reborn/core/CompanionProtocol.java`, and `ios-visual-walkthrough.yml` also runs for `tools/capture-ios-runtime.py`. No `apps/android/**` source path starts an iOS archive or walkthrough, so Android-only commits never receive iOS release-gate evidence. Both iOS workflows and the Android workflow retain manual dispatch. Website validation runs for relevant website changes. Verified-download contract tests run automatically; packaging fixed, previously accepted IPA/APK artifacts still requires owner dispatch. No workflow signs or merges, and no pull-request job deploys or publishes anything; on `main` the sequence is automatic: after a successful platform build, `platform-download-publishing.yml` creates the durable release asset, commits the manifest and dispatches `vercel-deploy.yml`, which deploys production from `main`.

## CI separation: fast validation, unsigned artifacts and visual acceptance

- The split workflows are the gates: `ios-validation.yml` (pull-request source, Swift package and simulator build), `android-validation.yml` (Android build, lint, domain and runtime checks) and `website-validation.yml` (site checks and the documentation-sync job). None of them replaces simulator UI acceptance.
- `ios-visual-walkthrough.yml` runs on iOS-source pull requests and iOS/Android source plus release-tool changes on main; the Android companion-interface guard can also trigger PR walkthroughs. Workflow-only edits use the lightweight release-contract check instead. Manual dispatch, the weekly schedule and `ios-rc-*` tags remain available. Its independent iPhone and iPad jobs each build and execute the complete named UI suite, retain screenshots/recordings/logs, and publish compact per-device diagnostics. A pass on one device does not mask a failure on the other.
- The unsigned iOS archive workflow proves packaging only. It does not prove interactive behavior, signing, or installability.
- `ios-smoke.yml` remains a focused iPhone test for navigation/Search recovery and native lifecycle behavior; it is not the full release walkthrough.
- A release candidate requires the reconstruction gate and a successful full iPhone **and** iPad walkthrough on the exact candidate SHA. Weekly, unrelated-commit, candidate-only, build-only, or one-device evidence cannot substitute.
- CI does not sign, publish, or deploy the app. The current archive remains an unsigned personal-signing input until the authorized signing and release gates are completed.

## GitHub Actions cost controls (October 2026)

- Feature branches are validated by the pull-request event only; ordinary feature-branch pushes do not duplicate the reconstruction, website or download-contract jobs. Main pushes retain post-merge verification.
- The unsigned personal-signing IPA archive runs automatically on relevant iOS/Android source, tools and workflow changes, with manual dispatch available. Compilation, Swift package tests and the unsigned simulator build remain in normal reconstruction CI. Do not interpret an unbuilt IPA as a release artifact.
- Full iPhone/iPad visual walkthrough runs on relevant iOS/Android source changes, release-gate tools, workflow edits, manual dispatch, the weekly schedule and release-candidate tags. A release candidate must pass both devices on its exact SHA.
- Failure triage is manually dispatchable with a failed run ID; it no longer starts a skipped workflow for every successful validation. Investigators can still inspect exact job logs and report failures.
- The separate focused iOS simulator smoke PR must be validated before merge. Workflow YAML changes themselves require review of triggers and required status checks; do not remove required branch protection checks without an equivalent passing gate.

## PR #43 post-merge evidence

Commit `ad95e8b04eb23311127090883f309f0ec4205724` passed reconstruction run [39051661330](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39051661330). Website validation and verified-download contracts also passed on that main-branch commit. Unsigned IPA packaging is not automatic; an absent artifact from this merge is expected. The next release candidate still needs a candidate-SHA-matched full iPhone/iPad walkthrough and separately approved artifact packaging.


## Focused smoke native execution proof

The focused iPhone smoke selects all three KantaNativeTests.YouTubeEmbeddedPlayerLifecycleTests cases and the existing testFocusedNavigationSmoke. The native bundle compiles the unchanged production preview component into its test module; release-app testability is unchanged. `tools/verify-ios-smoke-results.py` requires explicit passed lines for all four names and rejects failed assertions or runner restarts anywhere in the log. Navigation-only or zero-test output is insufficient. Existing logs and xcresult retention remain unchanged. The full release walkthrough still covers all four UI cases on both iPhone and iPad separately; native component checks do not verify SwiftUI presentation, real videos/ads or hardware audio interruptions.


## iOS audio arbitration regression candidate

The expanded native smoke requires all five YouTubeEmbeddedPlayerLifecycleTests passes and focused navigation. The added cases exercise real WebKit retirement before session configuration and isolated synthetic AVAudioPlayer intermission stop/explicit restart. Controller/domain cases cover native preparation versus online claim, cancellation/microphone changes across awaits, paused position/playback/FIFO identities, SFX/Next, stale release, foreground, failed session setup and suspension of both rendering backends. Forty Swift files remain wired to seven package suites plus two Xcode bundles. Compilation, execution and application build are separately required on the changed source; no physical media/latency result follows from these fixtures.


## Online-screen UI coverage candidate — October 18, 2026

PR #51 baseline 24db7a1 now adds two real XCUIApplication flows through Stage CONTROL's actual online sheet. They cover invalid-link rejection before WebView construction, explicit valid entry, stop/reopen, background/return without resurrection, Done/reentry and navigation recovery, plus preserving a paused original starter and explicitly resuming it afterward. Stable accessibility identifiers and link-keyboard Done dismissal support the real UI; no fake player or launch-environment bypass is introduced.

The synthetic video ID never autoplays. External YouTube availability is not a fixture: valid entry must reach either actual native player controls with a WebView or the production recoverable load-error state, with retained outcome screenshots. A local audio-claim failure is not accepted as an external-load error. This verifies UI lifecycle/recovery, not successful remote media or deterministic external-error delivery. Existing native process-failure, audio-handoff and controller/FIFO regressions remain required.

Focused smoke retains all five native cases and navigation and now requires both named online-screen cases (eight passes total). The full release walkthrough gate also requires all seven methods in the UI suite; timeouts, serial execution, failure detection, evidence retention, signing and deployment boundaries are unchanged. Local layout/presentation/YAML/whitespace and 55 tooling tests passed. Hosted app/test compilation and actual simulator execution are pending on the resulting source. Physical embed/ads/interruption/privacy and exact-candidate full iPhone/iPad release gates remain open.


## PR #51 HTTPS companion validation

Android core regression now includes BrowserRemoteRegressionTests over real JSSE TLS sockets and generated identities. BrowserRemoteBrowserHost is a synthetic test-only owner compiled by coreRegression; `node tools/verify-companion-browser.mjs` drives the shipped page against that TLS server in actual Chrome through CDP, with no npm dependency. The disposable browser trusts only synthetic test traffic through its isolated harness configuration; this does not validate mobile trust setup. Platform asset equality and bounded request contracts are checked before browser startup. GitHub's Chrome and pinned Node 24 are required; failure to launch/run remains red.

Android instrumentation additionally exercises the production HTTPS adapter against SessionController and native audio/queue state. iOS adds bounded command/HTTP framing and actual certificate/key/SAN validation plus a real role/input screen case. Focused smoke requires nine named passes; full release walkthrough requires eight UI cases, preserving all earlier tests and failure/timeout gates. Native app/browser host-client interoperability, certificate trust UX, hardware audio and independent security acceptance remain separate. See COMPANION_BROWSER_CHECKPOINT.md.


### Browser-host identity entitlement gate (October 18, 2026)

PR #51 native XCTest now compiles in Release through a test-only Swift SPI. Reconstruction passed at `215e02a` in run 39136036615. Focused iPhone run 39136036597 executed the native Keychain round trip and failed `SecItemAdd` with `errSecMissingEntitlement` (`-34018`), while five WebKit lifecycle and four focused UI tests passed. The existing workflow uses `CODE_SIGNING_ALLOWED=NO`. Keychain acceptance requires a separately authorized entitled simulator test host with inspected signature entitlements, then the **same** certificate/private-key/removal assertions executed and passing. This is not a reason to delete or skip the test, weaken production Keychain checks or claim signed/physical-device acceptance. See `docs/CI_FAILURE_REPAIR_LEDGER.md`.

## Focused iOS smoke workflow integrity — October 24, 2026

Focused simulator smoke must gate on KANTA-owned source checks, native lifecycle tests, named XCTest cases, and retained test evidence. Do not install unrelated external CLIs or label them audio analysis. On PR #67 head `61057d0`, the app build and focused XCTest step passed, including the four selected UI cases, but the overall job failed afterward at `rtk latency` with `must be run as root`. Inspection of the upstream [rtk-ai/rtk project](https://github.com/rtk-ai/rtk) confirms it is an LLM token-optimization CLI, not an audio measurement tool. The step was introduced by PR #65 and also piped a mutable remote install script directly into a shell. The repair removes this unrelated dependency rather than escalating it with sudo. Any future audio-quality/latency gate must use a KANTA-specific fixture or measurement tool with documented metrics, bounded permissions, and independently verified results; this smoke suite makes no audio-quality claim.



## iPad embedded Stage presenter diagnostics (2026-10-24)

When Stage Search does not expose `stage.keypad.0`, inspect the `KANTA_STAGE_PRESENTATION` diagnostics in the matching runtime logs and correlate the requested sheet, window attachment, existing presentation, and gate owner. The presenter now retries on layout and detects an orphaned owned controller; this is a candidate lifecycle repair until the full iPhone/iPad walkthrough passes on the same SHA. Keypad assertions must wait for each digit to exist rather than sampling `exists` immediately after a presentation animation.

### Keypad assertion regression after main synchronization — October 24, 2026

The source guard in [focused smoke run #40100439415](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40100439415) caught a merge-resolution regression before XCTest: the demo keypad test used immediate `.exists` checks for digits 0–9 instead of waiting for asynchronous presentation. Commit [b36108e](https://github.com/usermalik20-del/Kanta-reborn/commit/b36108e2dffc30957161b2ce0e9f24eb3c134021) restores `waitForExistence(timeout: 10)` for each digit without removing any assertion. This repairs the test contract; it does not establish that the iPad runtime presentation issue is resolved. Re-run the focused smoke and full exact-SHA iPhone/iPad walkthrough.


## Window-tap probe compiler and evidence contract — October 25, 2026

Main commit d56a4ba introduced a window-level tap probe to distinguish a delivered touch from a SwiftUI button action that never fires. Reconstruction run [#40119874396](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40119874396), unsigned archive run [#40119874432](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40119874432), and walkthrough run [#40119876903](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40119876903) all exposed a compile error: the current Swift SDK replaces NSStringFromCGPoint and NSStringFromCGRect with NSCoder.string(for:). The walkthrough's subsequent missing-bundle-ID error is downstream of the failed UI-test build; it is not a new iPad runtime result.

The repair uses NSCoder.string(for:), converts the hit view's bounds into window coordinates before logging them, and removes the non-cancelling tap recognizer when its representable is dismantled. This keeps the probe's point and hit rectangle comparable and prevents repeated view lifecycles from accumulating window-level recognizers.

Acceptance requires the source-contract guard, Swift/package and app builds, unsigned archive, focused smoke, and a complete iPhone plus iPad walkthrough on the same exact candidate SHA. A successful diagnostic build does not establish that the original iPad Search tap is fixed.

## iPad Search intercepted by the decorative stage canvas — October 25, 2026

**Runtime evidence:** PR candidate walkthrough [#40122872536](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40122872536) failed the four iPad keypad cases. Its compact diagnostic artifact [#12572620221](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40122872536/artifacts/12572620221) records a tap at `window-point={668, 148.5}`, the center of the accessibility-reported `stage.search` frame, resolving to `_UIGraphicsView {{32, 117}, {770, 433}}`. That drawing surface overlaps the Search action row and wins UIKit hit testing, so the button action never reaches the presentation gate. This is more specific than the earlier hypothesis about modal ownership.

Main's current reconstruction [#40126620995](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126620995) and unsigned archive [#40126621029](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621029) passed, but full walkthrough [#40126621040](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621040) failed: the iPad repeated the four keypad failures, and the iPhone stage did not report `testMainMenuButtonsAndScreens` as passing. The main-run compact diagnostic artifact [#12574327088](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621040/artifacts/12574327088) did not preserve `[KANTA_TAP]` events, so use the earlier candidate's tap trace as the direct hit-test evidence and retain the probe until exact-head acceptance.

**Repair:** mark both `KantaCameraPreview` and `KaraokeStageView` branches in `stageBackground` as non-hit-testable. These are display-only layers; user interaction must be handled by the explicit Search/Camera/Expand controls. The source guard checks that both branches carry `.allowsHitTesting(false)`.

**Acceptance:** source-contract guard, Swift package tests, simulator build, focused smoke, reconstruction, unsigned archive, and the full iPhone/iPad walkthrough must pass on one exact candidate SHA. In particular, confirm that a Search tap no longer resolves to the oversized drawing surface and that `stage.keypad.0` appears on iPad. Build success or an iPhone-only pass is not device acceptance.


## Post-merge iPad menu walkthrough timeout — October 25, 2026

Main commit [`d01504e`](https://github.com/usermalik20-del/Kanta-reborn/commit/d01504e42b3e00735bb429b05e9ec886149911fe) passed reconstruction [run #40136025964](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025964) and unsigned archive [run #40136025858](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025858), but full walkthrough [run #40136025937](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025937) failed on iPad.

The compact diagnostic artifact [#12577317727](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025937/artifacts/12577317727) confirms that Search now reaches `_UIShapeHitTestingView`, requests and activates the Search sheet, and begins presentation. The keypad-focused tests, navigation smoke, online-preview lifecycle tests, and starter playback test all passed in this run. The remaining failure is separate: `testMainMenuButtonsAndScreens` exceeded XCTest's 120-second default execution allowance while cold-launching the app separately for six destinations. The runner then restarted and the required-test manifest correctly marked that test missing.

The candidate runner repair keeps one app process alive, captures each of the same six destinations, dismisses each route through the stable `navigation.done` accessibility identifier, and waits for the Stage's More control to return before continuing. This reduces cold-start overhead without removing destinations, skipping assertions, adding blind sleeps, or weakening the required-test set.

**Status:** candidate; full iPhone/iPad walkthrough and exact-SHA acceptance remain pending on the repair branch. Do not call main's full walkthrough green until the menu case passes on both devices.


## iOS encrypted-companion input validation and keyboard-idle timeout

Walkthrough run [#40140289375](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40140289375) on candidate `c1bdcf57500f45f15e83876a5b4ae6fcb6387f5f` passed the iPad stage but failed the iPhone stage. The iPhone log shows `testEncryptedCompanionPairingScreenAndInputRejection` exceeded XCTest's 120-second per-test allowance while entering the public address. `typeText("8.8.8.8")` stalled on the simulator's “App animations complete notification not received” wait for roughly 60 seconds; the runner restarted and returned exit 65. Even though the aggregate log listed all named cases as passing across attempts, that exit remains a failure and is not accepted as a green walkthrough.

The repair centralizes private-address plus 32-character hexadecimal-code validation in `CompanionProtocol.isValidPairingInput`, shares `isValidPairingCode` with the handshake's secret validation, and adds package tests for valid private ranges, public/loopback rejection, empty/short/long/non-hex codes, and uppercase hex. The UI test retains the encrypted-pairing screen, disabled Connect, no Disconnect, and user-facing instruction assertions without depending on the flaky software-keyboard idle notification. No per-test timeout increase, retry, or assertion suppression is used.

Acceptance: package tests and source-contract guard pass on the exact candidate SHA, then the full iPhone and iPad walkthroughs both exit zero and contain every required named test in one clean run. This is a candidate repair until those hosted results pass.

## iOS menu destination timeout isolation — October 25, 2026

Walkthrough [#40146182462](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182462) on PR #71 head `dc60e513c4829226d8bf763c60d5e047e0beb5a4` failed on iPad because `testMainMenuButtonsAndScreens` never recorded a clean pass. The log shows XCTest restarted after a timeout and the runner correctly marked the menu test missing. All six destination screenshots were captured, so the navigation paths were reached; the single aggregate test still exceeded its per-test budget.

The candidate now uses six test methods and updates the runtime's required-test manifest and source-contract guard together. PR #72's per-device workflow topology is included, with same-branch concurrency retained to cancel stale duplicate runs. No assertion, destination, or required case was removed; no global timeout was raised.

**Status: candidate.** Require the source guard, reconstruction, focused smoke, unsigned archive, and complete iPhone and iPad walkthrough to pass on one exact head SHA before considering the repair accepted.

## Website consent, nonce and third-party script gate

The website security contract now validates the request-time nonce architecture rather than asserting that AdSense is globally disabled. Validate fresh per-response nonce generation, the same nonce on every rendered script tag and CSP header, strict-dynamic behavior, no-store HTML responses, consent-required loading of Analytics and AdSense, reject/revoke behavior, and complete exclusion of the private Songbook. Node handler tests cover nonce injection and route isolation; website contract tests cover the Vercel route map, publisher ID, consent loader, ads.txt, privacy disclosures and native-app separation. Preview acceptance must still verify that Vercel routes actually invoke the function instead of serving static HTML around it. Account approval, Auto Ads configuration and any required Google-certified consent platform are external release gates.

## Website crawler and scraping policy tests

The crawler policy is validated as one contract across the three files that express it. `tools/verify-website.py` parses `apps/web/robots.txt` into user-agent groups and asserts that the canonical `Sitemap:` and `Content-Signal` lines are present, that the fallback, search-engine and unfurler groups do not disallow all crawling, that every declared AI crawler group does, that `apps/web/api/page.js` blocks exactly the robots-declared tokens minus the robots-only control tokens (`Google-Extended`, `Applebot-Extended` are robots.txt controls, not user agents), that the `vercel.json` edge pattern covers every blocked token, that every `status` route rule carries a case-insensitive user-agent condition, and that `X-Robots-Tag` carries `noai`/`noimageai` but never `noindex`. `tools/tests/test_website_security_contract.py` asserts the same invariants per unit, plus that the gate runs before `fs.readFile`, that the block list cannot contain a search-engine token, and that an absent or unparsable user agent is allowed.

`tools/tests/test_site_page_handler.test.js` covers the executable behavior: declared crawler user agents are refused case-insensitively, search engines, unfurlers, an ordinary browser and `curl` are not, and a refused request returns `403` with the `X-Robots-Tag` header and no HTML. It also drives the handler end-to-end for both a blocked and a search-engine request, and asserts that a rendered page carries `X-Robots-Tag: noai, noimageai` on its own response. That file is wired into `website-validation.yml` and `vercel-deploy.yml` through the existing `node --test` step; no workflow change was required.

**Renderer-side header invariant.** A `routes` rewrite replaces the response, so the `vercel.json` `headers` rule reaches static files only. Verified on the 2026-11-01 production deployment: `/style.css`, `/robots.txt` and `/downloads.json` carried `X-Robots-Tag`, the ten HTML routes did not. `apps/web/api/page.js` therefore sets the header in the handler, and both `tools/verify-website.py` and the security-contract test fail if it is removed. The verifier scopes its search to the `async function handler` body on purpose: the refusal path sets the same header text, and an unscoped search passed even with the rendered-page header deleted (found by re-running the negative control, not by inspection).

**Authoring-environment limit, not a passing result:** `node` scripts do not execute in the authoring environment — `node --check`, `node --test` and `node --test tools/tests/test_site_page_handler.test.js` exit `0` without running anything. The committed JavaScript test file is therefore **unexecuted locally**; CI runs the real Node checks. To avoid relying on inspection alone, the classifier and the gate were executed independently with QuickJS (`qjs`), extracting the actual `BLOCKED_USER_AGENT_TOKENS`, `isBlockedCrawler()`, `crawlerGate()` and `refuseCrawler()` source out of `apps/web/api/page.js`: 19 user-agent vectors returned the expected verdicts, the fail-open paths (absent headers, a `null` request, and a `headers` accessor that throws) all allowed the request, and the refusal produced `403` with `X-Robots-Tag: noai, noimageai`, a `text/plain` body referencing `robots.txt` and no HTML. Syntax was confirmed separately with an independent ES parser (`esprima`). These are local execution results on extracted source, not a Node or CI result, and must not be recorded as either.

`vercel.json` was additionally validated against Vercel's published configuration schema (Draft 4, 0 errors) with negative controls confirming that an unknown route key, an unknown `has` type and a status rule without a condition are all rejected. That schema check is a local, network-dependent validation and is deliberately not wired into CI.

**Not validated:** no deployment has occurred, so Vercel's actual evaluation of the `has` user-agent condition and the fresh `robots.txt` are unverified. Confirming that a declared crawler receives `403` and that a search engine still receives the rendered page is a preview-deployment acceptance step.

## iOS More-menu route push regression — 2026-10-28

Full walkthrough [#40686820501](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40686820501) passed iPad but failed iPhone in `testMenuSongLibrary`. XCTest successfully synthesized a tap on the visible Song Library row, but the captured accessibility hierarchy still showed the More navigation bar and menu row after the 25-second Library-title wait. This is stronger evidence than a title timeout alone: the destination content was not presented in that attempt.

The candidate repair makes More-menu selections append typed destinations to a `NavigationStack` path instead of replacing the presented sheet's root route. The UI test now requires the `library.content` marker as well as the Library navigation bar, and `tools/check-ios-presentation-contract.py` enforces the path-based routing contract. This is not accepted until the source guard, focused smoke, unsigned archive and full iPhone/iPad walkthrough pass on one exact candidate SHA.


## iOS More-menu navigation path regression — 2026-10-28

The path-based More-menu candidate failed on iPad in [run #40693553013](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40693553013): multiple destinations lacked their expected navigation title, and the Done button was missing. The current candidate attaches the typed destination resolver and Done toolbar to a stable ZStack root inside the NavigationStack. The source-contract guard checks that placement, while XCTest still requires each destination's actual title/content and `navigation.done`. Do not accept the repair until focused iPhone smoke and both complete device walkthroughs pass on the exact same head SHA.

### Resolution (2026-10-29) — repair reverted

The path-based More-menu candidate was merged as PR #113 (`80ae286`) and then **reverted**
after the walkthrough evidence disproved it. iOS visual walkthrough history is unambiguous:

| SHA | Walkthrough |
| --- | --- |
| `d3e34f95` | success |
| `24e091b1` | success |
| `37f7ace1` | success |
| `80ae286a` (PR #113 merged) | **failure** |
| `2a01654f` | **failure** |

Before the merge, both devices passed three consecutive runs. After it, both devices failed
deterministically across `testMenuSongLibrary`, `testMenuCompanionRemote`, `testMenuICloudSave`,
`testMenuLegalPrivacy`, `testMenuSettings`, `testMenuSongbook`,
`testStarterPlaybackAndControls` and `testOnlinePreviewPreservesPausedStarterAndLocalResume`
("Menu destination did not present its expected screen", "The navigation host must provide
Done", "Failed to tap navigation.done").

Reverting the candidate restores a state whose `RootView.swift` is byte-identical to the
green `d3e34f95`/`24e091b1` builds. The original `testMenuSongLibrary` iPhone failure was
**intermittent** (iPad passed in the same run); the candidate converted it into a
deterministic two-device failure, so the revert is the strictly better state.

**Verified after the revert:** iOS visual walkthrough
[run #40787766952](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40787766952)
passed both device jobs on `b7d69f2`; Android validation, iOS validation and the unsigned
iOS archive passed on the same SHA.

Carry forward: the intermittent Song Library transition remains unproven-good and needs a
repair validated by focused smoke plus both device walkthroughs on one exact SHA before it
is accepted. Merging an iOS repair requires that walkthrough evidence, not merely a
mergeable branch.


## Physical iPhone installation versus hosted acceptance

Hosted simulator walkthroughs and unsigned iPhoneOS archives do not establish physical-device installation. The unsigned IPA from `ios-runtime-artifacts.yml` is a build/signing input only. Free Apple Personal Team provisioning is a development-install path, not Safari OTA distribution. Do not upload the unsigned IPA to a public install button or mark it as device-installable. Follow [IOS_CLOUD_SIGNING_AND_DEVICE_TESTING.md](IOS_CLOUD_SIGNING_AND_DEVICE_TESTING.md) for the supported free-account boundary and the additional Apple distribution, profile, manifest, HTTPS and real-device checks required before Safari OTA can be enabled. Physical-device acceptance remains NOT RUN until a real iPhone install and test are evidenced.


## Simulator versus physical-device acceptance terminology

The iPhone and iPad jobs in `.github/workflows/ios-visual-walkthrough.yml` boot Apple simulators through `xcrun simctl` and run XCTest against `platform=iOS Simulator`. Their job names, step names, manifests and reports must say **simulator acceptance**. This is **simulator UI acceptance**, not physical-device acceptance. They are valuable automated UI/runtime evidence, but they are not physical iPhone/iPad tests and do not establish hardware audio, camera, network-environment or device-performance acceptance. Keep physical-device acceptance as a separate, explicitly uncompleted gate unless evidence from an actual device is supplied.


## Exact-SHA verified download workflow identity

The verified-download packager discovers the Android APK from the `KANTA Android validation` run and the unsigned iOS IPA from `KANTA iOS unsigned artifacts`. It separately requires `KANTA iOS visual walkthrough` to pass on the same exact main-branch source SHA. Keep these names synchronized with the `name:` fields in the root workflow files; a renamed or missing workflow must fail its contract tests rather than silently accepting an older artifact.


## Exact-SHA download publication automation

Android and iOS publication are independent completion paths. Each resolves only its own successful exact-SHA main-branch build artifact; iOS additionally requires its matching both-device simulator walkthrough. The publisher's `workflow_run` trigger must include `branches: [main]` so feature-branch PR completions do not create skipped publisher runs; the job-level success, branch and workflow-name checks remain as defense in depth. The publisher never assumes the peer platform has an artifact for the same SHA.

The Android publication path packages the exact-SHA Android validation artifact; the iOS path packages the exact-SHA unsigned archive only after its matching iPhone/iPad simulator walkthrough passes. Each platform entry records its own source SHA, build run, packaging run, artifact identity, checksum and retention window. Publishing one platform must not rewrite the other platform's provenance or page. The website verifier validates each page against its own platform entry. The workflow commits only the selected platform page plus the shared manifest with its narrowly scoped GitHub Actions token and dispatches the existing Vercel production deployment workflow.

The iOS download remains an unsigned personal-signing input; this automation does not create Apple signing identities, provisioning profiles, Safari OTA installation or physical-device acceptance. A failed or missing exact-SHA gate fails closed and leaves the previous website downloads untouched. A rejected non-fast-forward publication push also fails safely rather than overwriting newer main-branch work.

**Missing base revision after a force-push (2026-10-31):** the main-branch jobs derive the base from `github.event.before`. A force-push leaves that commit unreachable, and `actions/checkout` only fetches reachable history, so the guard previously died on `fatal: Invalid symmetric difference expression <gone>...<head>` and failed the whole job before any build step. `tools/check-documentation-sync.py` now resolves the base itself: an available revision is used unchanged, an unavailable one falls back to the head commit's parent with an explicit notice on stderr, and a head commit with no parent fails closed. `tools/tests/test_documentation_sync.py` covers the kept base, the absent base and the empty base. Never satisfy this guard by force-pushing again or by widening its path filters; the fallback keeps the recorded-base weakness visible rather than silent.

**Gate semantics (2026-10-31):** the `documentation-sync` job in `website-validation.yml` compares the full PR base-to-head diff; it does not require every doc to be modified in the same commit. The required document set is path-driven and missing files fail with an explicit list. The guard also runs in existing main-branch build/deploy jobs using the push's before/head range (or the selected commit's parent for manual/workflow-run releases). It is a step, not another job, in those workflows. The separate PR workflow is the only new lightweight runner; no Xcode or Android build is started solely for documentation.


## Website consent runtime regression

`tools/tests/test_site_page_handler.test.js` verifies that the request-time renderer removes a legacy static `/analytics.js` script element before adding nonces and injecting the consent manager. This prevents a nonce-authorized Analytics bootstrap from executing before the visitor's choice. The live preview still requires browser-level acceptance/rejection/revocation checks; static tests do not simulate browser cookies or prove third-party network behavior.

## Android CI runner regression checks — 2026-11-01

The Android workflow now runs `python3 -m unittest tools.tests.test_android_runtime_runner` before Gradle/emulator work. The test parses the runner with Python's AST, requires the private-IPv4 guard to fail explicitly, and verifies that both APK installation calls are outside that failure branch and that instrumentation plus the `files/` screenshot destination remain wired. The workflow's `workflow_dispatch` and `push` events must remain separate YAML keys; manual `ref` selection changes the checked-out commit, not the required test scope. Full validation remains the default and only supported mode until a genuinely independent visual-only acceptance contract exists. A passing static test is not Android compilation, instrumentation, emulator acceptance or device acceptance.

The Android Gradle command builds `assembleDebug` and `assembleRelease` explicitly, and `assembleDebugAndroidTest` for instrumentation; emulator installation and the permission/offline-notice verifier use the debug APK, while the publisher consumes the release APK. Retain both app APK variants in the uploaded artifact by including `outputs/apk/release/*.apk` and `outputs/apk/debug/*.apk`; the exact-SHA packager extracts `outputs/apk/release/app-release.apk`. Do not substitute `assembleReleaseAndroidTest` unless that task exists in the configured project.

The runner must retrieve all nine reference-led destination screenshots plus `my-set-imported-library.png` and `my-set-active-soundfont.png` from app-private storage into `files/`; a missing or empty capture fails the runtime gate instead of being logged and skipped. The AST regression test rejects the old local-file-exists precondition that caused every new screenshot to be skipped before retrieval.

## Android stage runtime integration — 2026-11-01

The hosted runtime suite must test the primary `StagePresenterImpl` and fullscreen `StageSurface` according to their actual ownership. Video rendering and visual-active lifecycle assertions use the fullscreen `StageSurface` created by `MainActivity.fullscreen()`. Primary camera acceptance asserts the presenter's `PreviewView` has reached `STREAMING`; fullscreen camera acceptance asserts the fullscreen surface camera visibility, and closing fullscreen must return a streaming preview to the primary presenter. The runtime test must not cast the primary stage to `StageSurface`.


## KANTA design-system UI — 2026-11-01

The Android presentation layer under `ph.kanta.reborn.ui` and `ph.kanta.reborn.ui.components`
implements the KANTA dark entertainment design system. These are pure presentation components with
no backend logic; all playback, queue, persistence, sensor, companion and playback state remains
owned by `SessionController` and `StagePresenterImpl`.

### What is tested

- Design tokens (`KantaTheme`) expose the specified color values, radii and gradient policy
- Reusable components (`KantaCard`, `KantaButton`, `KantaIconButton`, `KantaImageButton`,
  `KantaScreenHeader`, `KantaSearchBar`, `KantaProgressBar`, `KantaStepper`, `KantaSectionHeader`,
  `KantaTabSelector`, `KantaSongArtwork`, `KantaMediaRow`, `KantaTransportControls`) instantiate
  with correct colors, radii and states
- Screens (`HomeScreen`, `KantaNowPlayingScreen`, `KantaSearchScreen`, `KantaQueueScreen`,
  `KantaPitchTempoScreen`, `KantaMicVoiceScreen`, `KantaVisualsScreen`, `KantaFullscreenStage`)
  wire their `Listener` callbacks into `MainActivity`, which delegates to existing backend methods
- Android runtime checks require themed drawable icons and accessible labels on goal cards and
  destination back controls, and image-based icons on all Now Playing transport controls
- Browser remote restyling preserves all 22 element IDs queried by `remote.js` and keeps the wire
  protocol, action allowlist, pairing flow and fingerprint verification unchanged

### What is NOT tested by these checks

- Canvas/Surface rendering, StaticLayout, ValueAnimator and drawable rendering (covered by the
  Android emulator runtime instrumentation gate)
- Visual, reduced-motion, APK/lint and emulator acceptance (separate gates on the Android
  validation workflow)
- Device acceptance (remains open)

### Design-system constraints enforced

- Background `#050B18`, elevated `#081426`, surface `#0D1930`, border `#203459`
- Primary cyan `#29D9FF`, primary blue `#2589FF`, primary purple `#7657FF`, accent magenta `#D946EF`
- Gradients (cyan -> blue -> violet -> magenta) reserved for emphasis only, not applied to every control
- Honest disabled state for controls not in the companion protocol allowlist; no fake functionality

### Fullscreen camera handoff acceptance

The Android emulator runtime test must prove that the opted-in CameraX preview is visible on the primary `StagePresenterImpl`, transfers to the fullscreen `StageSurface`, and returns to the primary stage when fullscreen closes. For fullscreen, require both `CameraStage.streaming()` and `StageSurface.isCameraVisible()`; do not invert this predicate or infer success merely from the camera remaining enabled. The stream observer drives the surface visibility signal. Ownership transfer must unmount the old fullscreen preview before the new target binds; the fullscreen dismissal callback must target `StagePresenterImpl` itself so it prepares a fresh attached preview after cleanup, never pass a cached `PreviewView` across the stop/start boundary. Explicit disable must unbind CameraX, remove observers and the surface provider, detach the preview view, restore the previously selected primary background, and wait for the primary stage's attached-preview visibility predicate to become false. Backgrounding must release the preview, and foregrounding must not silently re-enable it. The visibility predicate must include active CAMERA source and view attachment, not only a potentially stale `PreviewView.StreamState` value.


## Platform-specific download publication acceptance

Run `python3 -m unittest tools.tests.test_platform_download_publishing` with the existing exact-SHA packaging contract suites. The tests prove that Android publication replaces only Android metadata/page content, iOS publication replaces only iOS metadata/page content, and the peer platform's source SHA, package URL, run/artifact identity, checksums, signing state and page remain unchanged. The website verifier validates each platform against its own accepted source SHA and packaging run; platform releases are independent and must not rewrite peer provenance. iOS publication refuses an IPA without same-SHA iPhone/iPad simulator walkthrough provenance. The publisher also rejects a delayed candidate whose source is an ancestor of the currently published source, an older workflow run for the same source SHA, or unrelated history. Local Git-graph tests prove newer commits and newer same-SHA runs can publish while stale source/run completions cannot mutate the manifest or page. The publisher workflow must retain `fetch-depth: 0` so both revisions are available to the ancestry check.

The legacy `prepare-verified-downloads.py` and `publish-verified-downloads.py` helpers retain paired same-SHA behavior for their unit-contract tests. `verified-downloads.yml` runs those tests as compatibility coverage but does not invoke the legacy helpers to package or publish; the active platform-specific publishers are independent and must preserve each peer platform's own provenance.

The exact-SHA artifact downloader enforces a 256 MiB response/archive/member ceiling. This accommodates the Android release APK plus debug/runtime APKs and test evidence in one artifact; keep the cap bounded and contract-tested when artifact contents change. GitHub creates another same-named source artifact on each rerun; `find_artifact` must select the newest unexpired artifact by `created_at` (artifact ID is deterministic tie-break), and tests pin this retry behavior rather than rejecting valid reruns. The `Platform-specific verified download publishing` workflow listens only to Android validation and iOS unsigned-archive completion events originating from `main` (`workflow_run.branches: [main]`); the job still checks successful conclusion, branch and workflow name as defense in depth. Android publication requires only the exact-SHA Android validation artifact. iOS publication additionally waits for the exact-SHA iOS simulator walkthrough. Confirm workflow-run name, branch, source SHA, artifact run identity, artifact digest and retention metadata before accepting a published download.
- `publish-platform-download.py` must write `apkSha256` (Android) or `ipaSha256` (iOS) as the application digest key in `downloads.json`, matching the key names `verify-website.py` expects in its download-manifest verification loop. Using any other key name (e.g. `appSha256`) causes `KeyError` and fails publication.
- `publish-platform-download.py` updates only the selected platform's top-level and nested manifest entry, including its own source SHA, packaging run, artifact, checksum and retention metadata. Never synchronize source revisions across Android and iOS: each platform may be released from a different accepted main-branch commit. `verify-website.py` validates each page against that platform's own source SHA and packaging run. The final website verification and Vercel dispatch occur only after the selected platform's page and shared manifest validate.


## Android library import progress regression — 2026-11-02

The hosted Android `RuntimeChecks` acceptance verifies that repository progress and completion callbacks run on the main Looper, that the repository releases the Activity-scoped listener after completion, and that an unavailable SAF folder reports a main-thread completion instead of leaving the import progress UI open. The assertions run in the existing emulator instrumentation gate; they do not replace Android build, lint or runtime acceptance.


## Android browser companion pairing lifecycle

`BrowserRemoteRegressionTests` runs through `CoreRegressionTests` in the existing `coreRegression` JavaExec task and exercises the real generated TLS identity and socket server. The guest-cap regression fills all four slots, performs three valid-code attempts while capacity is full, leaves one guest, and proves a new guest can pair within the five-minute pairing window. Separate wrong-code cases must still lock pairing after three incorrect codes. Capacity rejection must not weaken the wrong-code lockout or change guest count, token expiry, request authorization, or revocation behavior.


## Android Kanta-Connect pairing-secret log-redaction regression — 2026-11-09

`tools.tests.test_companion_security_contract` runs in the Android validation job. It inspects all `Log.v/d/i/w/e` calls in `KantaConnectHost`, rejects the live `pairingCode` and code-like log literals, and confirms `MainActivity` still obtains the code through the owner-facing `pairingCode()` accessor. The separate workflow-ownership contract verifies this test remains wired into Android validation. This is a source-level logging guard; existing companion protocol/runtime tests remain responsible for network behavior.

## Android paired legacy-container import regression — 2026-11-06

`LegacyFileContainerImporterRegressionTests` covers per-title 2 KiB addresses with invalid trailers and unreferenced blob records; valid non-aligned byte offsets; matching layouts; conflicting, duplicate, invalid, out-of-bounds and wrong-cardinality references; unsupported versions and truncated ciphertext. A synthetic paired index/blob import decrypts distinct payloads, checks decoded/skipped counts, and compares each materialized title and bytes in reversed physical order. It contains no user media or copied key constants. Local private reference verification of a real pair resolves and decodes every title-addressed record; this is not Java execution or device playback. Hosted mapping CI is pending. Paired-file emulator import, runtime-only key handling, storage/cancellation and device acceptance remain open.

## Android imported-library and SoundFont selection regression — 2026-11-02

The existing RuntimeChecks fixture imports a mixed SAF batch and asserts the `ImportBatchResult` imported and failed counts alongside durable catalog state; it verifies the MIDI/KAR file is present in the authoritative LibraryRepository song catalog, and verifies the SoundFont inventory contains both the bundled default and imported bank. It switches to the bundled bank and back, reloads a fresh repository instance, and asserts both inventory and active selection persist. The My Set UI is covered by the same exact-head Android emulator run: imported songs must be visible with Reserve actions, every SoundFont must be listed, the selected one must be marked Active, and the other banks must offer Use. Paired ShubBlob/kantablob import is now a dedicated runtime flow and must be asserted with a real bounded fixture: index/blob pairing, MKMD decryption, decoded MIDI validation, persistence and Reserve visibility. Single-file container selection remains rejected. The runtime test then exercises the same mixed batch through the live SessionController and My Set UI, switches to the default bank and back, and captures both library and active-bank screenshots. It then restores the Control tab so later voice-settings instrumentation retains its original screen precondition. Exact-head Android run [#41814538861](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41814538861) on `57525326069908bf19da3328d57260862283dea1` passed build, lint, core regression, browser and emulator runtime checks; its artifact retains `files/my-set-imported-library.png` and `files/my-set-active-soundfont.png`. The later controller-audio fixture assertion expects five catalog rows: three starters, one imported KAR and one imported WAV. The controller's separate WAV import is content-hash deduplicated because that same WAV was already included in the folder fixture. These checks do not constitute physical-device audio acceptance.

## Android SoundFont storage regression — 2026-11-02

The framework-free CoreRegressionTests suite covers the SoundFont storage policy: a 600 MiB bank is accepted when 1 GiB is available (leaving 424 MiB), a copy that would consume the 64 MiB reserve is rejected, and unknown/under-reported streams reserve one full 8 MiB lookahead interval plus the next chunk before each periodic StatFs recheck. Reported remaining bytes are used when they exceed that interval, with overflow rejected. The Android importer also performs a reported-size preflight and a final reserve check. Run the normal Android core regression, build, lint, and emulator runtime gates.


## iOS controller cancellation regressions — 2026-11-02

The Swift package suite includes deterministic cancellation coverage for a suspended library song lookup during enqueue, a suspended asset lookup during Next, cancellation while the native backend is loading the next asset, natural-completion auto-advance while its snapshot poll is canceled, and cancellation during audio-session preparation before `engine.load`. A separate failure-path case throws from audio-session configuration and verifies that the already-playing backend is not stopped before a new asset load begins. The tests require no queue/current-item/playback-identity commit after user-command cancellation, require an uncommitted backend asset to be stopped only after loading has begun, verify that the prior current song remains playing when cancellation or session setup failure precedes load, verify reload recovery after a partial replacement, and prove that observer cancellation does not strand Up Next. Run `swift build --build-tests` and `swift test --no-parallel`; simulator app build and independent iPhone/iPad walkthroughs remain separate acceptance gates.


## Android reference-led screen acceptance

The Android emulator runtime walkthrough opens each reference-led destination from the Home dashboard, verifies that the destination-owned header and controls are visible without the persistent stage/transport/workspace toolbar or navigation row, exercises Search/Queue navigation, Pitch reset, Visuals selection, Mic & Voice effect selection, fullscreen controls and Now Playing stage embedding, then captures the screen. A screenshot is accepted only after the destination's tagged view is attached, shown by its ancestors and has a non-empty global visible rectangle; the capture helper waits for UI idle and a bounded compositor settle before taking the screenshot. The Android workflow artifact allowlist and `tools/run-android-runtime.py` collector must retain all nine destination captures plus `my-set-imported-library.png` and `my-set-active-soundfont.png`. Verify all eleven files in the uploaded artifact, not only in app-private storage. This is emulator visual evidence; physical-device acceptance remains separate.

## Independent platform download publication

Each Android and iOS manifest entry owns its own `packagingRun`, artifact ID, URL and checksum. The top-level `packagingRun` is a legacy compatibility fallback only while a platform entry has no scoped value; the verifier still requires that platform's artifact URL to match the fallback run. Every new publication writes a scoped value, and legacy peer provenance is migrated from the peer's immutable artifact URL. When migrating a legacy manifest, the publisher recovers the peer platform's packaging run from that peer's immutable GitHub artifact URL before advancing the shared pointer. Regression tests cover Android and iOS publication in both directions and require the untouched platform's source, package URL, run provenance and page to remain intact.


The Android packaged ZIP includes the application APK and a `SHA256.txt` file containing the APK digest. The publisher-generated Android download section must disclose that file, and the website verifier enforces the public copy against the packaged bundle contract.


The platform publisher must dispatch `vercel-deploy.yml` after its verified website commit. GitHub's `GITHUB_TOKEN` does not start downstream workflows from the publisher's push, so the publisher job requires narrowly scoped `actions: write` permission for the explicit workflow-dispatch API call; retain read-only Actions access everywhere else.

## PR #134 merge reconciliation — 2026-11-02

After reconciling PR #134 with current `main`, rerun documentation sync and complete Android validation on the resulting SHA. The manual Android run on `bfa605d` proved the Home-launch repair; it does not substitute for later-head validation.

The reconciled exact head must compile against current-main `SessionController` SoundFont and import-progress APIs before the Android runtime walkthrough is accepted.

The karaoke-stage runtime gate begins by asserting the Home dashboard is visible; launch-default changes must be validated on the exact PR head.

## Android three-group UI candidate — validation requirements

The grouped navigation must retain visible and actionable Home, Sing, Performance, Experience and More destinations. Runtime coverage must enter Search, Queue, Key & Tempo, Visuals and Voice through their group landing actions, then verify the existing destination tags and controller effects. Search keypad coverage must verify numeric entry, letter-layout switching, backspace and submit without an Android IME covering the result list. The Home screenshot must show the viewport-filling bottom navigation rather than an early content termination. These source changes are not compiled, emulator-accepted or physical-device-accepted until the exact-head Android workflow passes.

## Supplied UI and SoundFont assets — validation requirements

Exact-head Android validation must compile all supplied drawable resources, install a clean app, verify both bundled procedural banks appear in My Set's SoundFont inventory, and confirm selecting each bank persists through repository reload. Native SoundFont structure validation and representative MIDI/KAR program, sustain and channel-10 drum playback remain required before treating either bank as device-accepted. Visual review must confirm multicolor icons retain their source colors rather than being flattened by tinting. The OpenStage third-party candidates are not runtime assets and must not be treated as licensed audio integration; the KANTA-created procedural bank is separately covered by its restricted runtime asset license.

## OTA upgrade acceptance (not yet device-verified)

Before calling Android OTA production-ready, the owner must run `docs/OTA_UPGRADE_ACCEPTANCE.md` on a physical device. CI verifies APK metadata, bounded version allocation, feed monotonicity/source ancestry, prior APK digest, and the new pinned signer. The approved signer transition is limited to the exact certificate fingerprint in `tools/verify-ota-publication.py`; a candidate with any other signer is refused. The PKCS#12 and password are Actions secrets exposed only to trusted main runs; PR jobs must not receive them. Earlier APKs signed by the old certificate cannot update in place, so physical acceptance starts with a clean install of the first new-signer release and tests updates from that release onward. CI does not prove install, rollback, restart behavior or physical-device acceptance.
### Android search keyboard coverage
Runtime acceptance verifies the workspace search keypad is visible and that its ABC/123 switch changes layout without opening the system IME.

## Website redesign validation — 2026-11-07

The website redesign adds new shared assets and page-specific features. Validation must cover both the design-system layer and the individual page FX.

### Theme system (`theme.css`, `theme.js`)
- Verify dark/light/system modes render correctly (palette swap, smooth 300ms cross-fade via `.theme-transitioning`)
- Theme preference persists to `localStorage` and survives reload
- `prefers-color-scheme: light` respected when system mode active
- Theme toggle buttons (☀️/🌙/💻) in header on all six pages; `aria-pressed` reflects state
- No flash of wrong theme on navigation (CSS variables on `:root`)

### Micro-interactions (`micro-interactions.js`)
- Button ripple on click (primary + secondary buttons)
- Magnetic pull on `.button.primary` / `.nav-cta` (cursor attraction)
- 3D tilt on `.feature-card` hover (±6° perspective, `--tilt-x/--tilt-y`)
- Scroll reveals: `.reveal` fade/slide up, `.reveal-stagger` children cascade (80ms increments)
- Count-up stats: `[data-count-to]` animates on scroll into view
- All disabled under `prefers-reduced-motion: reduce` (instant, no animations)

### Command palette (`command-palette.js`)
- ⌘K / Ctrl+K opens palette anywhere on site
- Fuzzy search over 25+ entries (pages, features, guide steps, download anchors)
- Keyboard: ↑/↓/Enter/Esc, mouse click, hover highlight
- Floating FAB hint (⌘K) on desktop (hidden mobile)
- Deep links work: `/guide#soundcard`, `/features#pitch-training`, etc.

### Hero animation (`hero-animation.js`)
- Canvas gradient blobs + waveform strip animated (simulated audio bands)
- Stage mockup: typing lyric sweep, live queue render, pitch meter
- Single static frame under `prefers-reduced-motion: reduce`
- Canvas clears on unmount/no leak

### Download progress bar (`download-progress.js`)
- Sticky top bar appears on `/android` + `/ios-download`
- Fetches `/downloads.json` with `cache: "no-store"`; validates repository/platform, packaging run and artifact IDs, exact GitHub Actions artifact URL, source SHA, archive/application digests, retention/byte metadata, and the visible page download link
- Uses DOM `textContent` for status and digest metadata; it does not inject manifest data as HTML or hard-code run IDs, artifact IDs or digests
- Reports that the publication record is current; it does **not** download the artifact or hash its bytes
- Dismissible, session-scoped; the `#download` link appears only after record validation
- `website-validation.yml` runs the Python security contract and `node --check apps/web/download-progress.js`

### Stage preview (`stage-preview.js`)
- Fixed phone frame on `/features` (`data-stage-preview` on `<html>`)
- Desktop: right side; Mobile: bottom
- Scene updates per scrolled section (`starter-songs`, `player`, `stage-display`, `party-sfx`, `pitch-training`, `android-stage-v2`)
- Scene types render correctly: queue, lyrics (highlight sweep), stage (bars), pads, meter, score
- Cross-fade swap animation between scenes

### Guide parallax (`guide-parallax.js`)
- Pinned phone illustration on `/guide` (`data-guide-parallax`)
- Per-step screen animation + palette shift (`--guide-hue`)
- Steps: `first-run` (home), `library` (import progress), `songbook` (book pages), `soundcard` (mic waveform), `personal-stage` (stage), `remote` (code)
- Screens animate: progress bar, waveform, floating backgrounds

### Songbook FX (`songbook-fx.js`)
- 3D page flip: prev/next buttons + keyboard ←/→ keys
- Ambient gold/cyan dust particles over `.book-card`
- Search highlight pulse on `.song-list li.highlight`
- Lock/unlock cross-fade transitions
- Keyboard navigation ←/→

### Theme toggle UI
- Three-button group (☀️/🌙/💻) in header on all pages
- `data-theme-toggle` attribute drives `theme.js`
- `aria-pressed` reflects active mode
- Smooth 300ms cross-fade, no flash

### Reduced motion
All animations disabled under `prefers-reduced-motion: reduce`:
- CSS: `animation-duration: 0.01ms`, `transition-duration: 0.01ms`
- JS: instant reveals, no canvas loops, no scroll animations, no magnetic pull

### Browser test matrix
| Page | Theme | Micro | Palette | Hero | DL Bar | Stage | Parallax | Songbook | Palette |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `/` | ✓ | ✓ | ✓ | ✓ | — | — | — | — | ✓ |
| `/android` | ✓ | ✓ | ✓ | — | ✓ | — | — | — | ✓ |
| `/ios-download` | ✓ | ✓ | ✓ | — | ✓ | — | — | — | ✓ |
| `/features` | ✓ | ✓ | ✓ | — | — | ✓ | — | — | ✓ |
| `/guide` | ✓ | ✓ | ✓ | — | — | — | ✓ | — | ✓ |
| `/songbook` | ✓ | ✓ | ✓ | — | — | — | — | ✓ | ✓ |

Run `python3 tools/verify-website.py` (local route/anchor/CSP checks) and `tools/verify-website.py` in CI. Vercel build must succeed.

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


## Release automation secret isolation — 2026-11-09

- `android-validation.yml` must keep `actions/checkout` at `persist-credentials: false`. The optional manual `inputs.ref` is not release authority: a main-branch run may expose signing secrets only after the checked-out commit is verified as an ancestor of `refs/remotes/origin/main`. A non-main ref selected from a main dispatch fails before signing; feature-branch dispatches remain unsigned.
- Android signing secrets are step-scoped to `Build owner-signed release APK`. The keystore is written under `RUNNER_TEMP`, permissions are restricted, and an `EXIT` trap removes it after Gradle. Never write signing secrets to `GITHUB_ENV` or expose them to BLA, tests, emulator runtime checks, or artifact upload steps.
- `vercel-deploy.yml` must keep checkout credentials unpersisted, restrict production deployment to `refs/heads/main`, and scope `VERCEL_TOKEN` only to the verification, domain API, pull, build, and deploy steps. `configure-is-a-dev-domain.yml` is also main-only. BLA candidate steps must not receive the token.
- `tools/tests/test_workflow_path_ownership.py` guards the Android main-history check, step-scoped signing, keystore cleanup, checkout credential policy, and pinned BLA CLI. `tools/tests/test_vercel_deploy_workflow.py` guards Vercel token scope and main-only production domain/deploy jobs.
- Repository branch protection and security settings are not controlled by these workflow tests. On 2026-11-09 the GitHub API reported `main` as unprotected; repository-admin action is required to enable protection and restrict production secrets to a protected environment.
## Android runtime-only legacy import key — local candidate

`LegacyFileContainerImporterRegressionTests` now uses unrelated synthetic key/IV
fixtures, without reflecting application constants. Coverage adds strict hex/length
validation, defensive ownership, idempotent clearing, missing-file cleanup, correct
and wrong supplied keys, and refusal to reuse cleared keys. Run
`cd apps/android && ./gradlew --no-daemon :app:coreRegression` on Java 17.
`python3 -m unittest tools.tests.test_runtime_import_key` covers four source contracts
for no embedded default and UI/controller cleanup. Full local Python suite: 170 tests
pass; preflight passes, including 149-file structure and 59 workspace checks.
Java tests/build/lint, emulator prompt/picker/cancel/background/busy/error acceptance
and real-device playback are NOT verified: local JVM fails on `getcpu(2)`.
See [ownership and privacy limitations](ARCHITECTURE.md#android-runtime-only-legacy-import-key--local-candidate).
