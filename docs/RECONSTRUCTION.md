# Reconstruction evidence and work ledger

## Current checkpoint — 2026-10-28 (main `5d7d157`; public release source `41c8ddde`)

Main [`5d7d157`](https://github.com/usermalik20-del/Kanta-reborn/commit/5d7d157d952e2e158645223871408b926133dbda) attempted Android StagePresenter composition and failed Android compilation in [run #40692835569](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40692835569). The failure comes from package-private session access, nonexistent Song metadata fields, a score-result type mismatch, stale MainActivity stage references, and bypassing the existing StageSurface media lifecycle. [PR #114](https://github.com/usermalik20-del/Kanta-reborn/pull/114) rolls back only the unvalidated integration wiring and preserves the rest of main's tree.

The earlier host-JVM source-set/pipefail and exact-SHA release-gate repairs are in [PR #111](https://github.com/usermalik20-del/Kanta-reborn/pull/111); Android validation [#40691415623](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40691415623) passed, but focused iOS smoke [#40691415666](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40691415666) failed at the Song Library menu transition. [PR #113](https://github.com/usermalik20-del/Kanta-reborn/pull/113) now uses a typed NavigationStack path and asserts the Library content marker. Its Android check was blocked by the existing SEO branding violation that PR #111 also repairs. [PR #112](https://github.com/usermalik20-del/Kanta-reborn/pull/112) defers AdSense until nonce-based CSP support is available.

Main's latest unsigned iOS archive [#40692799169](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40692799169) was still running and the iPhone/iPad walkthrough [#40692799396](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40692799396) was not yet complete at the last check. Do not claim exact-SHA device acceptance until both jobs finish.

The durable [KANTA v0.1.0 release](https://github.com/usermalik20-del/Kanta-reborn/releases/tag/v0.1.0-workspace-redesign) and [public download manifest](https://github.com/usermalik20-del/Kanta-reborn/blob/main/apps/web/downloads.json) remain pinned to source `41c8ddde`. No new release, signed production IPA or physical-device acceptance is established.

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

**Next: Milestone 2** — StagePresenter binding to SessionController, ScoreOverlay integration, IntroCard countdown flow, SearchOverlay SongCatalog wiring, SettingsStore persistence.
