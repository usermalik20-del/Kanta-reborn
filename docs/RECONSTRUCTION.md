# Reconstruction evidence and work ledger

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

The existing workspace/StagePresenterImpl/transport/toolbar/body structure is preserved. The new
design-system screens are presented as an overlay via contentContainer; the existing UI remains
fully functional.

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
