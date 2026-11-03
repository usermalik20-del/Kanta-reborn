# KANTA Reborn

## Android SoundFont RAM ceiling — local candidate

The Android native renderer now permits up to **1536 MiB (1.5 GiB)** of tracked TinySoundFont allocations per worker, raised from 128 MiB for large decoded banks. This is a hard ceiling, not reserved or guaranteed memory: the operating system can refuse smaller allocations or terminate a memory-heavy process. Full-tail piano banks can require over 1 GiB before other app work. Format validation, voice limits, storage checks and source-byte preservation remain unchanged. Local native regression passes; APK, sanitizer and large-bank device acceptance remain separate gates. No new bank is bundled.

Native Android and iOS karaoke applications built around the **Compact Workspace** redesign, together with their public website.

**Tagged release (separate from current previews):** `v0.1.0-workspace-redesign` at SHA `41c8ddde`.

## What shipped

The Compact Workspace redesign implements an **"Always on Stage"** architecture: the karaoke player stays visible and live as the base layer, while utility panels appear and disappear as transient overlays above it.

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
- **Reusable components**: KantaCard, KantaButton, KantaIconButton, KantaSearchBar, KantaProgressBar,
  KantaStepper, KantaSectionHeader, KantaTabSelector, KantaSongArtwork, KantaMediaRow,
  KantaTransportControls

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

GitHub Actions is partitioned by source ownership. Website changes run website validation; Android changes run Android validation; iOS changes run iOS validation. The lightweight documentation-sync check runs on every pull request and fails when an implementation or workflow change is missing its required owner documentation. Android-only changes do not trigger iOS archives, walkthroughs or IPA publication; each platform retains its own validation and download provenance. Vercel production deployment is separate from pull-request validation and runs only from `main` or an explicit dispatch. The platform-download publisher filters its `workflow_run` trigger to `main`, while retaining job-level success and workflow-name guards, so feature-branch PR completions do not create skipped publisher runs. See [workflow ownership and testing](docs/TESTING.md). CI status is commit-specific; a green result on an earlier SHA is not evidence for a later commit.

## Open release gates
- **Pitch Settings UI** (Controls → Pitch Settings)- **Pitch Settings UI (Android, compiles in the build)**  exposes sensitivity slider (0.1–3.0), presets (Beginner/Normal/Pro), note picker (C4–B5), and custom Hz input (20–2000 Hz). Settings persist via `SettingsStore` (`pitch.assistantSensitivity`, `pitch.targetHz`, `pitch.targetName`). Voice Coach (`LearningAssistant`) scales thresholds with sensitivity; Pitch Practice (`PitchGameOverlay`) uses stored target Hz as fallback.

- `InAppControlCenterView` still uses the unrestyled `GeloVisualStyle` (14 references) — the last unrestyled surface.
- **Android lyric-stage animation candidate (not yet cloud-validated).** `LyricStage` and the fullscreen `StageView` keep the existing scrolling window, but the active line now fills continuously across each source-timed MIDI fragment instead of switching a whole word, and only the fragment being sung receives a per-letter wave driven by the existing single-owner pitch estimate. No word timings are invented and pitch never changes cue time or scoring. `CountdownStage` renders `Ready ⏱️ → 1 → 2 → 3 → 4 → Kanta 🎤` with a reduced-motion path; `SettingsStore.getCountdownLength()` defaults to 4 and `MainActivity.playWithCountdown()` starts a new reservation through it while resuming a prepared song immediately. The authoring environment has no working JVM (`getcpu`), so compiler, emulator and device acceptance remain open. See [architecture](docs/ARCHITECTURE.md), [reconstruction](docs/RECONSTRUCTION.md) and [testing](docs/TESTING.md).

- **Android Bluetooth headset monitoring candidate (not yet device-validated).** The optional voice-output route now accepts Bluetooth SCO and BLE headsets next to wired, USB and analog/aux line-out, and asks Android for the communication route on API 31 and newer so the processed voice actually leaves the phone. Speaker-class routes (built-in speaker, Bluetooth A2DP speakers, HDMI/ARC) stay blocked because the microphone hears them. Bluetooth audio is mono call quality with higher delay, and the Bluetooth device's own microphone is still not captured by the app. Compiler, emulator and device acceptance for this candidate are open. See [architecture](docs/ARCHITECTURE.md), [reconstruction](docs/RECONSTRUCTION.md) and [testing](docs/TESTING.md).

- The six diagnostic `NSLog` probes previously present in iOS shipping source are confirmed absent from the current tree.
- **Website crawler policy: deployed and verified on 2026-11-01, with follow-ups open.** `/robots.txt` serves the policy, a declared crawler receives `403` on an HTML route and on static assets, `Googlebot` and a normal browser still receive the rendered page, and `X-Robots-Tag` is present on static responses and, after the follow-up fix, on rendered ones. Rate limiting and per-request gating of static assets still need an edge/WAF layer, and no legal clause exists yet. See [scraping defense](docs/WEBSITE_SCRAPING_DEFENSE.md).
- Physical-device quality, physical two-device companion networking, voice quality/feedback/latency, scoring calibration, camera/external-display acceptance and production signing remain separate open gates.
- Companion control is experimental: Android-to-Android only, no forward secrecy, no independent security review, and no iOS interoperability. Browser guests are limited to four active sessions; a valid code rejected only because capacity is full does not consume the three-incorrect-code lockout budget.

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


**Consent regression repair (PR #121).** The request-time HTML renderer removes the legacy static `/analytics.js` tag before adding response nonces. The consent manager is now the only entry point that can inject Analytics or AdSense after explicit acceptance. The regression test covers the previously eager script path; preview validation must still exercise accept, reject and revocation in a real browser.

## Android CI runner integrity — 2026-11-01

The Android validation workflow is a full acceptance gate, not a screenshot-only shortcut. Manual dispatch may select an explicit Git ref; ordinary pushes and pull requests validate their event SHA. The workflow definition must keep `workflow_dispatch` and `push` as separate top-level events. The emulator runner must fail only when boot/network prerequisites are absent, then continue to install both APKs, execute instrumentation, and preserve screenshots under `files/`. See [the CI repair ledger](docs/CI_FAILURE_REPAIR_LEDGER.md) and [testing](docs/TESTING.md). The November 1 repair is a candidate until the complete Android validation run passes on its exact PR head.

**Android emulator repair follow-up (2026-11-01):** Android run #400 passed build, lint, native and browser gates but failed emulator instrumentation because `RuntimeChecks` cast the primary `StagePresenterImpl` to the separate fullscreen `StageSurface` class. The follow-up camera acceptance exposed a teardown defect: unbinding CameraX did not detach the preview from its owning stage, restore the primary background or invalidate stale visibility state. The repair candidate explicitly releases both primary and fullscreen previews and strengthens the visibility predicate and emulator assertions. It remains unverified until a new complete Android emulator run passes. See `docs/CI_FAILURE_REPAIR_LEDGER.md` and `docs/RECONSTRUCTION.md`.

## Platform CI ownership

Android validation and APK packaging/deployment are triggered by Android-owned source and build inputs. iOS validation, unsigned IPA packaging and iPhone/iPad simulator acceptance are triggered by iOS-owned inputs. Website validation and production deployment remain website-owned. Cross-platform fan-out is limited to explicitly shared protocol files that a second platform directly compiles or tests. An Android-only change must not start Xcode builds or IPA deployment, and an iOS-only change must not start Android builds or APK deployment. See `AGENTS.md`, `docs/ARCHITECTURE.md` and `docs/TESTING.md` for the enforced policy and regression checks.


### Independent Android and iOS downloads

Android APK publication follows successful Android validation only. iOS unsigned IPA publication follows the iOS archive and additionally requires same-commit iPhone and iPad simulator walkthrough acceptance. Each release updates only its own download page and preserves the other platform's source revision and published artifact. The manifest and verifier validate each platform against its own build and packaging provenance. If a publication attempt uploaded an artifact before failing, a retry may produce multiple same-name artifacts; the workflow must select the newest by `created_at` rather than assuming the name is unique. The website is deployed after the selected platform's metadata passes website validation.


## Android library import discoverability — 2026-11-02

The Android My Set surface now lists imported MIDI/KAR/audio items with direct Reserve actions and shows the installed SoundFont inventory with an explicit active-bank indicator and Use action. SoundFont inventory and active selection are persisted together; changing banks affects subsequent playback, not a song already in progress. Paired ShubBlob/kantablob and index-container import is now implemented as a separate My Set flow: choose the index, then the paired blob. The file-backed importer scans bounded MKMD records, decrypts and inflates only validated payloads, and materializes MIDI/WAV/MP3 copies through the existing LibraryRepository. Single-file selection still rejects container files; physical-device and exact-head Android validation remain required.

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
