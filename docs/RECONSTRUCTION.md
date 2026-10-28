# Reconstruction evidence and work ledger

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

**Pull requests (2026-10-29).** #112 (AdSense deferred until a nonce-capable CSP exists) is
merged to main. #113 (iOS More-menu destinations pushed through the `NavigationStack` path)
was merged and then **reverted**: the iOS visual walkthrough was green on `d3e34f95`,
`24e091b1` and `37f7ace1`, and failed on both devices on the `80ae286` merge and on
`2a01654f`. The revert restores a `RootView.swift` byte-identical to the green builds;
the original Song Library transition failure was intermittent, and the candidate made it
deterministic. #104, #111, #114 and #115 are closed as superseded: their diagnoses were
accurate for the commits they were raised against, but every listed defect is fixed and
validated on main, and #114/#115 would have reverted validated work. The CI hardening they
described was adopted directly rather than through those branches.

**Milestone 3 implemented and validated on main.** `SmartLinkHost` (NSD `_kanta._tcp`
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

| Milestone | Scope | State |
| --- | --- | --- |
| 0 | Rebrand foundation | **Done** — merged `8d14c91`; branding sweep 4/4 |
| 1 | Vertical slice: content / catalog / stage overlays | **Done** — merged `fa37794` |
| 2 | StagePresenter binding, score sink, legacy MKMD decode + Meg importer | **Done** — merged; MKMD padding + magic defects fixed in `1dfecd7` / `2a01654` |
| 3 | Smart Link host (NSD + WebSocket), settings persistence | **Done** — `SmartLinkHost` + `SettingsStore` wiring on main, Android validation green on `2a01654` |
| 4 | Smart Link phone app, voice search, spectrum analyser, score celebration, custom fonts, diagnostics, help/about | **Not started** |
| 5 | Public-review mining applied to positioning and fixes | **Not started** |

### Milestone 3 detail (implemented, validated on `2a01654`)

- `core/companion/SmartLinkHost.java` — advertises `_kanta._tcp` through Android NSD,
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

### Not part of any milestone yet

- The Meg importer has no UI surface; it is reachable only through the catalog API.
- Media3 is not a dependency; BGV video currently renders through the existing
  `StageSurface`/`StageView` path, and the `BackgroundStage` overlay carries photo/camera
  presentation.

### Open risks

- `MkmdDecoder` is validated by JVM fixtures only. No real Meg container has been decoded
  on a device, so the port is not device-accepted.
- `SmartLinkHost` has no on-device two-host test; only the request/response contract is
  unit-shaped.
