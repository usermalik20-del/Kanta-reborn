# Reconstruction evidence and work ledger

## Current checkpoint — 2026-10-27 (main `2b5e078`; released source `41c8ddde`)

Main is at [`2b5e078`](https://github.com/usermalik20-del/Kanta-reborn/commit/2b5e078d7911c2b6fe42df359be663eb26fdda2f). The released native source is [`41c8ddde`](https://github.com/usermalik20-del/Kanta-reborn/commit/41c8dddeebe7d096322994e8af09c8c48a76308a), which merged PR [#96 — Implement compact persistent KANTA workspace](https://github.com/usermalik20-del/Kanta-reborn/pull/96). PR #98 removed the unintegrated `KantaWorkspace.swift` scaffold that violated the source-target completeness gate; the compact workspace ships through the real composition boundary instead.

**Compact Workspace — "Always on Stage" (released on both platforms):** the karaoke stage is the persistent base layer and utilities are transient accessories presented above it, so the player is never lost. iOS implements this in `KantaMainPlayerView.swift` (`stage.persistentSurface`, `requestStageSheet`, single-active accessory, `More controls` label); Android implements it in `KantaWorkspace.java` mounted from `MainActivity.buildWindow()` (persistent `StageSurface` layer, `activateAccessory` single-active enforcement, handset bottom sheet / large-screen anchored panel, `Controls` label). Both platforms carry the two accessibility identity fixes: no container identifier that overrides its actionable children, and an unambiguous control-entry label that is not `Settings`.

**Release:** GitHub Release [`v0.1.0-workspace-redesign`](https://github.com/usermalik20-del/Kanta-reborn/releases/tag/v0.1.0-workspace-redesign) at source `41c8ddde`, carrying the Android APK `KANTA-Android-preview-41c8ddde.apk` and the unsigned iOS IPA `KANTA-iOS-personal-unsigned-41c8ddde.ipa`. The iOS IPA is unsigned and still requires trusted personal signing.

**All eight workflows on main `2b5e078` are green:** iOS unsigned artifacts, iPhone visual walkthrough, iPad visual walkthrough, reconstruction validation (iOS), reconstruction validation (Android), verified-downloads packaging, website validation and Vercel deploy. The public manifest at [`/downloads.json`](https://kanta-public.vercel.app/downloads.json) and the [iOS](https://kanta-public.vercel.app/ios-download) and [Android](https://kanta-public.vercel.app/android) pages agree on source `41c8ddde`, the Android artifact `#12701709708`, the iOS artifact `#12698769982` and the passing walkthrough run `#40424816930`. The website security-contract baseline was advanced to `41c8ddde` to match.

**Open gates:** physical-device quality, physical two-device companion networking, voice quality/feedback/latency, scoring calibration, camera/external-display acceptance and production signing remain separate. Companion control is experimental: Android-to-Android only, no forward secrecy, no independent security review, no iOS interoperability. `InAppControlCenterView` still uses the unrestyled `GeloVisualStyle`.

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

**Next: Milestone 3** — Should-haves (Beta 2):
- BGV video backgrounds (Media3), background photos, camera background via existing CameraStage
- Adjustment overlay: key / melody / tempo / volume, applied as intents to SessionController, with clock re-anchoring
- Settings groups: lyric, audio, mic, score, behavior, bgv (kanta_settings)
- Score result overlay + score settings (difficulty/mode from SongHub's ScoreSettings)
- Smart Link host side (NSD + beacon + WebSocket) with explicit pairing, replay protection and revocation
