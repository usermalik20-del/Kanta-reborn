## Android reference-led UI integration context — 2026-11-01

**Working branch:** `feat/android-kanta-reference-fidelity`. The previous UI batch introduced `ph.kanta.reborn.ui` screens and components but did not mount them in the production `MainActivity` composition. The actual app still uses a native Java / Android Views root: `MainActivity` composes `KantaWorkspace`, a persistent `StagePresenterImpl`, a session strip/transport and a scrollable destination panel. `SessionController` remains the playback/session/queue owner. The initial safe-branch correction mounts `HomeScreen` as the Home destination and routes its actions to existing owners. It hides (does not destroy) the stage pane and legacy navigation row while Home is selected, restoring them for other destinations. Opening a workspace accessory from Home records a one-shot return intent; the workspace dismissal callback restores Home after closing the accessory. Switching active accessories clears that intent. The feature grid is now 3 columns × 2 rows.

**Important integration boundary:** `HomeScreen`, `KantaSearchScreen`, `KantaQueueScreen`, `KantaPitchTempoScreen`, `KantaMicVoiceScreen` and `KantaVisualsScreen` are mounted. Search filters the live SessionController catalog and reserves through the session owner; Queue projects ReservationQueue and mutates by stable reservation UUID; Pitch & Tempo routes changes through SessionController.controls. Mic & Voice delegates to MicrophoneInput, verified-route MicrophoneMonitor and actual score status; Visuals uses the ten existing scenic assets, CameraStage and the existing stage-media import workflow. YouTube catalog discovery is not implemented. `KantaNowPlayingScreen` is mounted when the user taps the persistent stage and embeds a live `StageSurface` for the selected scene/video/camera and synchronized lyrics. Its metadata, progress, transport and performance controls project SessionController. Fullscreen uses a separate StageSurface with a bottom control overlay; both preserve StageSurface as the lyric renderer. `KantaFullscreenStage` remains an unused presentation candidate.

**Browser companion:** `index.html`, `remote.css` and `remote.js` now provide a desktop sidebar/library/Now Playing layout and narrow-screen single-column reflow. The pinned transport, pairing, certificate verification, command allowlist, host-owned state and session expiry remain unchanged. Unsupported host controls remain disabled rather than issuing invented protocol commands.

**Acceptance still pending:** current-head Android build/lint/core regression/emulator workflow; screenshot comparison of all six mounted destinations and the browser remote; visual verification at controlled handset/tablet/desktop widths; remaining Now Playing and fullscreen visual comparison; APK artifact inspection. No visual-fidelity, emulator, device or release claim is made until those checks pass.

---

# Codebase Context Packet — KANTA Reborn

## Android camera lifecycle repair context — 2026-11-01

**Candidate source:** `fix/android-camera-preview-release-pr130`, derived from PR #130 head `accfa77fc3a1cdd3a72cfbc354b17675e204f608`. The Android app is Java 17 with Android Views, minSdk 26 / targetSdk 36, Android Gradle Plugin 8.13.2, Activity 1.10.1 and CameraX 1.5.3 (`camera-camera2`, `camera-lifecycle`, `camera-view`). `MainActivity` owns `CameraStage`; `SessionController` owns the opt-in foreground state; CameraStage binds only CameraX Preview and targets either the primary `StagePresenterImpl` / `BackgroundStage` or fullscreen `StageSurface`.

**Lifecycle contract:** CameraStage.stop() is the sole teardown path for explicit disable, stage transfer, permission/provider failure and foreground loss. It invalidates generation-fenced callbacks, removes CameraX observers, unbinds Preview, clears its surface provider and detaches the view from the exact owning stage. The primary presenter retains the prior non-camera BackgroundStage.Source and restores it on release. BackgroundStage visibility is true only when CAMERA is active, the PreviewView is attached to that BackgroundStage and CameraX reports STREAMING. This prevents a detached view's stale stream state from being interpreted as a visible camera.

**Regression contract:** runtime instrumentation requires primary camera startup, transfer to fullscreen, return to primary, fullscreen unmount, explicit primary disable and no implicit reactivation after Home/foreground. The updated test waits on lifecycle predicates, not fixed sleeps. Hosted Android compile, lint, domain checks and emulator acceptance are pending for the resulting repair commit; no physical-device result or performance measurement is implied.


## Current source and acceptance snapshot — October 29, 2026

Superseded snapshot below, kept for history. Current main is `b138ad6`; see `docs/RECONSTRUCTION.md` for the live checkpoint. The iOS menu repair described here as candidates was merged (PR #113) and then reverted after the visual walkthrough failed on both devices. The text that follows describes `5d7d157`. It adds the Android StagePresenter composition and timing/score bindings. Its Android validation [#40692835569](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40692835569) and unsigned iOS archive [#40692799169](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40692799169) were running at the last check; the iPad walkthrough had started and the iPhone job was queued in [#40692799396](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40692799396). The latest completed website validation and Vercel deployment remain [#40688354662](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40688354662) and [#40688354694](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40688354694) on web-equivalent tree source `4fd3d244`. The public release remains pinned to `41c8ddde`.

**Confirmed failures and active repairs:**
- Android validation [#40686852171](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40686852171) on `fa377948` failed because the framework-free host-JVM regression source set included Android-backed `SqliteSongCatalog`, producing 73 missing Android SDK type errors. The Gradle `tee` pipeline masked the compile failure; the later `BrowserRemoteBrowserHost` ClassNotFoundException was secondary.
- Full iPhone/iPad walkthrough [#40686820501](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40686820501) passed iPad and failed iPhone `testMenuSongLibrary`. After the synthesized tap, the captured accessibility hierarchy still showed More and the Song Library row. Evidence: [iPhone runtime #12817376027](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40686820501/artifacts/12817376027), [iPad runtime #12816601222](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40686820501/artifacts/12816601222), [iPhone diagnostics #12817555580](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40686820501/artifacts/12817555580).
- [PR #111](https://github.com/usermalik20-del/Kanta-reborn/pull/111) fixes the Android host-JVM source set and pipeline failure handling, removes retired product branding from SEO metadata, and aligns exact-SHA packaging with the live workflows. Its Android validation [#40691415623](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40691415623) passed; focused iOS smoke [#40691415666](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40691415666) and full device walkthrough [#40691415563](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40691415563) were still running at last inspection.
- [PR #112](https://github.com/usermalik20-del/Kanta-reborn/pull/112) defers AdSense until nonce-capable CSP support exists. Its website validation passed; its Android check was blocked by the same SEO branding violation addressed by PR #111.
- [PR #113](https://github.com/usermalik20-del/Kanta-reborn/pull/113) is the candidate for the iOS menu transition. It switches More destinations to typed NavigationStack path pushes and asserts actual Library content. Its first Android validation [#40693120914](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40693120914) failed at the pre-existing product-branding guard on main, before Android build tests; that check is not evidence of an iOS source defect. Fresh iOS smoke, validation, archive and device walkthroughs were queued on its original head and must be rechecked after rebasing.

No signed production IPA or physical-device acceptance is established. The latest durable GitHub Release is [`v0.1.0-workspace-redesign`](https://github.com/usermalik20-del/Kanta-reborn/releases/tag/v0.1.0-workspace-redesign), still pinned to `41c8ddde`; the public manifest remains on that source.

## Historical source and acceptance snapshot — October 26, 2026

Canonical repository: [usermalik20-del/Kanta-reborn](https://github.com/usermalik20-del/Kanta-reborn). The current code-bearing main baseline is merge commit [`abda7c2`](https://github.com/usermalik20-del/Kanta-reborn/commit/abda7c27251af9f1019aa23831c6770032d754c9), which merged PR [#83 — Stage v2 app-level navigation and Queue](https://github.com/usermalik20-del/Kanta-reborn/pull/83). Later documentation-only successors do not change that validated application source. The validated PR source head was [`3aae9e8`](https://github.com/usermalik20-del/Kanta-reborn/commit/3aae9e85abafa28856f6e5752e6ba9dac3506114).

### Exact-head and post-merge acceptance

PR #83 exact-head validation on `3aae9e8` passed:
- Reconstruction, iOS + Android: [#40254725851](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725851).
- Focused iPhone simulator smoke: [#40254725871](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725871).
- Full iPhone/iPad walkthrough, both jobs: [#40254725862](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725862).
- Unsigned archive: [#40254725860](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725860), artifact [#12622475729](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725860/artifacts/12622475729), 25,139,930 bytes, SHA-256 `41fc1dc3f8de2e98438b9e19a6a1ca4e31cbe9df570dac1dffca795439701707`.

Post-merge main on `abda7c2` is also green:
- Reconstruction [#40258066829](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066829): Android and iOS jobs passed.
- Full device walkthrough [#40258066852](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066852): iPhone and iPad jobs both passed, with per-device runtime evidence and presentation diagnostics.
- Unsigned archive [#40258066875](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066875), artifact [#12622974482](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066875/artifacts/12622974482), 25,139,693 bytes, SHA-256 `f17c0195c679270916febc2f0612ddb5d90d9f2c235097849615fcf26e7b00fb`, expires 2026-11-02 02:18:33 UTC.

Website validation [#40220534010](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40220534010) and production deployment [#40220534153](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40220534153) passed on `5ee1893`; no website source changed in the later native/UI merge. The [GitHub Releases page](https://github.com/usermalik20-del/Kanta-reborn/releases) remains empty. No signed production IPA or physical-device audio/camera acceptance is established. Branch protection remains an open governance follow-up.

## Current iOS navigation, Queue, and YouTube preview lifecycle

`KantaTabBar` is presentation-only; `RootView` owns selected-tab and route state and uses the shared presentation gate. `KantaQueueView` projects the authoritative controller snapshot and owns no queue mutations. Embedded and fullscreen More controls use distinct stable accessibility IDs. Online-player retirement synchronously invalidates callbacks and detaches the `WKWebView`, then defers media/blank-page cleanup. These ownership and lifecycle contracts are recorded in `docs/ARCHITECTURE.md`; current exact-SHA evidence is in `docs/RECONSTRUCTION.md`.


## Current iOS walkthrough test boundary — October 26, 2026

The visual runtime manifest requires 13 named UI cases. Song Library, KANTA Songbook, Settings, Companion Remote, iCloud Save, and Legal & Privacy each have a separate XCTest method so one destination cannot exhaust the aggregate menu test's 120-second default / 180-second maximum allowance. Each case still verifies the destination title, captures a screenshot, dismisses through `navigation.done`, and verifies Stage More returns.

The visual workflow uses independent iPhone and iPad jobs, each compiling/running its own complete suite and preserving per-device diagnostics. The runtime runner treats a non-zero `xcodebuild` exit, XCTest restart, or missing required case as failure even if a later attempt lists passing tests. The exact PR #83 candidate and post-merge main walkthroughs both passed on the recorded SHAs above. No signed IPA or published GitHub Release is verified.

## Historical implementation context

## Active iOS encrypted controller batch — 2026-10-16

Baseline main after Android 0.10 publication. No open pull requests were returned before editing. Swift 6.2 / iOS 17 SwiftUI and Network.framework with system CryptoKit; no new dependency. LocalKaraokeController remains the iOS queue authority; Android SessionController owns the remotely controlled queue. Existing SQLite/bookmarked storage, MIDI/AVFoundation audio and imported media are unchanged.

KCP1 client work connects only to explicit numeric RFC1918 IPv4 on port 45731. It will match the accepted Android handshake and direction-separated records, allow one outstanding request, expire/revoke on foreground loss and never fall back to the recovered plaintext remote. This batch implements iPhone/iPad controller → Android host; encrypted iOS hosting is a separate remaining milestone. Existing Bonjour/PIN remote remains explicitly legacy. No measured device latency or network reliability is inferred. Swift compilation, shared vectors, actual socket exchange and app build require cloud acceptance; local Python/layout checks are preliminary only.

## Prior baseline context

Current snapshot: merged Android stage/party and iOS runtime work, verified download packaging and restored website. Native source revisions and checks are listed below and in the root README. This is a testing reconstruction, not a signed production release.

## Current accepted state

Android 0.8.0 source `57dc0611cb54435802a4bd7d226e00983dd3c8f6` passed run 38845590252 with 328 domain assertions, 110 actual API 35 checks, APK/instrumentation build, lint, native sanitizers, browser interoperability and the permission/runtime-notice gate. Private media, front-camera preview, dry input, experimental monitoring/effects and optional pitch-following scores are merged. Physical timing/scoring accuracy and wired/USB output quality remain unverified.

iOS source `3c12a1fb962ac956250a8f238c01eace13f560d2` passed all three UI walkthroughs on both iPhone and iPad plus unsigned device packaging in run 38806377805; its package source passed 158 tests. No Apple source change is included in the Android follow-ups.

Packaging run 38842836405 passed five contract tests and original app identity/checksum verification. Android and unsigned iOS bundles last 30 days and require GitHub sign-in. The static website remains under apps/web; its private reader is byte-identical to evidence and CSP blocks network connections.

Control panels still rebuild on controller publication and search remains in-memory over normalized snapshots. These are source-review risks, not measured latency claims. Camera preview has no capture/analysis/recording output. Experimental monitored effects are implemented; physical output and calibrated scoring, secure companion and official YouTube remain unfinished. Physical quality/latency, external displays and signing remain open.

## Android stack

- Root: `apps/android/`; AGP **8.13.2**, compileSdk/targetSdk **36**, minSdk **26**, Java **17** source/target. Gradle 8.13 wrapper is included and its JAR/distribution hashes are verified.
- Native Android Views built in Java; no Compose, Hilt, Koin, Room or Kotlin. MIDI synthesis uses revision-pinned MIT TinySoundFont via C11/JNI, NDK 27.2.12479018 and CMake 3.22.1.
- Pitch estimation uses the optional SPICE DSP adapter behind the framework-free `core.PitchSource` port: `org.tensorflow:tensorflow-lite:2.16.1` (Apache 2.0, no permissions, no Play services) plus the 8.7 MB `assets/spice.tflite` model. `MicrophoneInput` stays the only `AudioRecord` owner and feeds the adapter the block it already hands the monitor. `core/SpicePitch` holds the 48 kHz to 16 kHz resampling and the model calibration, and is checked by `SpicePitchRegressionTests` in the host-JVM `coreRegression` task. See the pitch estimation decision in `docs/ARCHITECTURE.md`.
- `LibraryRepository.java`: app-private media copies plus versioned JSON metadata committed through `AtomicFile`. This is the current implementation, not a SQL/Room schema.
- Song fields: stable ID, title, artist, code, safe app-private filename, first-party starter flag. Content SHA-256 identifies imported files; starter IDs are allowlisted.
- Queue fields: distinct reservation UUID, song ID, optional singer. Saved sets contain ID, name and ordered song IDs. Starter removals are persistent tombstones.
- Search currently uses all query terms against cached normalized title/artist/code in the `Song.matchesTerms` domain helper. It is not FTS. Search runs off-main over immutable snapshots; results and reservation rows are paginated.

## Playback and timing

- `SessionController.java` is the one app-lifetime Android owner, composed by `KantaApplication`.
- All `MediaPlayer` instances and callbacks are accessed on the main Looper. File import, hashing, catalog persistence and image decoding use a serial IO executor. A separate preparation executor reads MIDI, and a folder worker scans selected SAF trees. These do not block Next behind a bulk import.
- MIDI/KAR channel events feed a dedicated worker owning TinySoundFont and streaming PCM_FLOAT AudioTrack. Imported SF2 replaces the bank for subsequent songs; original bytes are preserved. MP3/WAV use main-Looper MediaPlayer. Volume, tempo (0.5–2) and pitch (±12 semitones) share controller settings. MIDI drums are not transposed; file-audio behavior depends on platform PlaybackParams support.
- `core/MidiLyrics.java` parses SMF format 0/1, merges tempo events across tracks and derives lyric-line start times, typed channel messages and end-of-track duration. Limits: 16 MiB, 256 tracks, 250,000 events and six hours. The audible clock maps output frames across buffered tempo changes instead of showing render-ahead time. SMPTE/format 2 and malformed/unbounded inputs fail explicitly.
- Prepare-before-commit keeps reservations in Up Next until the backend acknowledges actual start. A generation token suppresses stale prepare callbacks; queue entries have distinct identities even for repeated songs.
- Audio focus and unplug handling pause playback. Front-camera, microphone scoring/DSP, video stages and hardware audio acceptance are not yet reconstructed on Android.

## Network / remote

The recovered iOS baseline contains optional PIN-authorized plaintext LAN control. Pagination and singer reservation interfaces are reconciled with the newer UI. Command retries use a bounded, connection-scoped replay cache that pins in-flight requests; reconnect/durable replay remains open. It must not be described as encrypted. Android companion transport has not been reconstructed; no Android server/socket implementation exists yet. The website's newer encrypted-Android/cross-platform claims are requirements/evidence, not this reconstruction's shipping capabilities.

## iOS recovery

`apps/ios/` retains SwiftUI/AVFoundation architecture, SwiftPM modules and XcodeGen configuration. The original stage UI snapshot has been supplemented with 78 individually selected missing paths, including 60 Swift files, from the historical snapshots. No existing baseline file was overwritten during that restoration.

Missing Favorites storage, SQLite catalog, CSV/private Songbook export, SFX engine and C compression bridge are newly reconstructed code, not recovered originals. All Swift package/test targets compile in hosted validation. The full unsigned Release simulator app build passed run 38748040876; all 158 package tests passed on head 56c9fedbb70b79ebc350e4678d5620554d53c983. Xcode 26.2 / Swift 6.2 is selected explicitly. The AES-GCM Songbook envelope follows the captured browser reader; new tests cover durable Save retries, failed writes, literal/Unicode search, CSV quoting and encryption round-trip.

## Current risks and bottlenecks

No device performance measurements exist for the reconstruction. Source-review risks include linear Android catalog search, whole MIDI reads bounded to 16 MiB, one IO executor shared by imports and ancillary work (playback preparation now separate), JSON catalog rewrite cost, and platform/lifecycle behavior requiring device checks. These are potential bottlenecks, not measured lag. Prioritize playback/control responsiveness before visuals or background jobs.

## Checks so far

- 50 recovered Python release-tooling tests passed.
- Four starter-generation/fixture tests and the starter resource guard passed.
- C bridge compiled for Apple in the hosted package/app builds; synthetic codec fixtures passed. The Linux unsupported-codec branch also compiled with warnings treated as errors. Physical user-library compatibility remains unverified.
- All nine runtime Java files compiled against official Android API 36 references and Java 17 host libraries. Forty-six core regression checks passed. Cloud run 38742284862 also passed Gradle APK/lint and captured browser interoperability; device launch/runtime acceptance remain pending.
- Hosted Swift execution passed all 158 package tests on run 38748040876, including native audio, persistence, encrypted exports, bounded replay, scoring overflow and live/source-boundary coverage. The same source head passed the unsigned Release simulator app build.
- Cloud validation is iterative under standing owner authorization. Android build/lint/domain/browser checks pass. All 36 Swift test files are wired into seven package suites plus the Xcode UI suite; layout preflight and diagnostic artifacts support reconciliation. PR #1 is merged. Signing and deployment are not claimed. The new Android branch adds emulator runtime instrumentation; its first cloud execution remains pending.

## Android media branch acceptance

The current source compiles against official API 36 references. All 167 core regression assertions and captured browser interoperability pass locally. C11 builds with warnings treated as errors; ASan/UBSan rendering, truncation, targeted table-reference checks and 2,000 mutations pass. LeakSanitizer cannot inspect processes in this managed local runtime; explicit allocator-balance assertions pass and cloud sanitizer execution retains normal leak checks. Gradle/NDK/lint and emulator execution remain required on this changed source.

SAF individual/folder imports accept MIDI/KAR, MP3/WAV and SF2. Folder scans are bounded to 10,000 entries, 1,024 directories and depth 16. Cancellation rolls back the batch's new copies and metadata; byte-identical orphan copies can be recovered after integrity verification. A folder is copied explicitly, not watched automatically. The selected bank is persisted independently of song names.

SF2 acceptance is deliberately bounded: 32 MiB input, 128 MiB native allocation budget per worker, 128 voices, 1,024 presets, 8,192 expanded regions and bounded traversal. Structural chunk/table/reference checks precede the unchanged third-party parser; final sample/loop bounds are checked before rendering. Negative coarse sample offsets are rejected before the pinned loader can perform a signed shift. Out-of-range coarse/fine tuning and root keys are rejected. Unsupported banks fail visibly rather than being treated as a successful override. Physical sound quality, latency, bank compatibility and long-session acceptance remain open.

The iOS runtime follow-up changes only accessibility identities, UI-test isolation/waits, and root validation/artifact orchestration. Core playback ownership and persisted formats are unchanged. Xcode UI-target compilation, both simulator walkthroughs and the unsigned device archive remain pending on that branch.

## Android stage/party work snapshot

The follow-up starts from the published native media and iOS runtime branches. StageView currently draws cached scenic bitmaps and line-level lyrics on a 100 ms UI refresh; it rebuilds wrapped text during drawing. Loop/Realm are explicit placeholders. No camera, video, party or intermission backend exists yet. The current work will retain SessionController as authority, keep source-timed lyric fragments, isolate optional stage/party resources, preserve imported bytes and add bounded storage/lifecycle rules. These are planned changes, not current acceptance claims.

## Android stage/party branch implementation

Source-timed fragment highlighting, cached native text layout, lyric sizing and opt-in procedural perspective lights are implemented. PartySounds generates eight deterministic two-second WAV pads; PartyAudio bounds SoundPool voices to four and owns a separate main-Looper intermission player. SessionController gates idle music and foreground/focus behavior; SharedPreferences retains selected local song IDs and independent volumes, never enabled monitoring/sensors. Custom MP3/WAV pads validate size (8 MiB) and duration (30 seconds) on IO, then persist after decode acknowledgement with stale-load fencing. No Android sensor, camera, video or companion implementation is implied. Local API 36 runtime/instrumentation compilation and 197 domain assertions pass; changed-head cloud/runtime validation remains pending.

SoundPool's documented 1 MiB decoded limit requires an additional conservative 900 KiB PCM estimate for custom pads. MediaExtractor validates audio track rate/channels off-main; oversized decoded clips are rejected rather than silently truncated. Metadata retriever cleanup uses release() for minSdk 26 compatibility; close()/AutoCloseable starts at API 29. Both native loops and test snapshots now read acknowledged playback state, not invalid MediaPlayer preparation-state queries. Primary references: Android SoundPool and API 29 MediaMetadataRetriever diff documentation.


## Validated native-media checkpoint

Source head `87e5aa21dc4dc3d2e2e043e8c1cbdd1beafc4ce5` passed run 38805798316: native sanitizer/tone/mutation checks, Gradle NDK APK and instrumentation build, lint, all 167 domain assertions, captured browser interoperability and 13 actual emulator checks (JNI/AudioTrack playback, FIFO, cancellation, controls, pause/resume, failed preparation and UI). iOS app/package source is unchanged from run 38802025283, which passed all 158 package tests and simulator compilation. This checkpoint changes documentation only. iPad runtime, advanced Android parity, signing and deployment remain separate.


Latest iOS checkpoint: run 38806377805 passed on 3c12a1fb962ac956250a8f238c01eace13f560d2, including all three UI walkthrough tests on both iPhone and iPad, app/UI compilation and unsigned personal-signing device archive/IPA packaging. No assertions were removed. See the IOS_RUNTIME_CHECKPOINT.md ledger for retained artifact identities and acceptance boundaries. Earlier pending statements above describe their dated attempts; signing, installation and physical-device acceptance remain open. Only documentation follows that validated source.


Validated Android stage/party checkpoint: source b3a6cd7c795e2aaea4e48254ede290b4f616d671 passed run 38807243721 with 201 domain assertions, 28 actual API 35 emulator checks, NDK APK/instrumentation assembly, lint, native sanitizer checks and browser interoperability. Covered nested/cyclic SAF traversal, partial failure, cancellation/reopen, byte identity, custom pad decode/reset, bounded voices, stop-all, intermission gating and real karaoke playback. Only Markdown reconciliation follows that source. Personal photo/video, camera, microphone, companion and official YouTube work remain separate; no physical-device or signed-release claim is made.

## Android personal-media implementation — October 16, 2026

Workspace access resumed. The previously local draft is being published as a separate Android-only batch, based on current main. StageMediaStore adds bounded private byte-preserving photo/video copies, atomic persisted selection and cancellation/rollback. StageSurface adds a main-Looper muted video-only projection with generation fencing, actual-first-frame visibility and release on deactivation/detach. Photos use sampled EXIF-oriented display decoding; original bytes are never rewritten.

The frequent publication path now retains the stage, transport and navigation subtree, avoiding video teardown/recreation on queue or option changes. Control panel rebuilding and in-memory library search remain source-review performance risks, not measured latency results. Five weighted tabs replace clipped horizontal navigation. No playback/FIFO or audio-clock ownership changes.

Local API 36 compilation of production and instrumentation sources passed; 201 domain checks and actual browser export/decrypt/wrong-code/tamper checks passed. Focused runtime cases cover persisted photo/video selection, malformed-media rollback, source-byte identity, stable stage identity, visible navigation and muted-video first-frame/deactivate/reactivate behavior. Hosted run 38825170997 passed APK/lint/native/browser checks and all 47 emulator checks on source 2ced06c5c6063e15e227cc094b2462a36a03e7f2. Camera, microphone processing, companion networking and official YouTube remain unfinished.

## Android camera batch — October 16, 2026

Current accepted media source remains 2ced06c5c6063e15e227cc094b2462a36a03e7f2 (201 domain checks and 47 emulator checks). New camera source uses ComponentActivity 1.10.1 and CameraX camera-camera2/camera-lifecycle/camera-view 1.5.3; Java/Views, AtomicFile/JSON storage, existing audio engines and authoritative queue ownership remain unchanged. No Compose, Room or DI framework is introduced.

CameraStage owns only a foreground opt-in Preview use case with asynchronous generation fencing, a 12-second readiness bound and one primary/fullscreen target. COMPATIBLE PreviewView supplies rotation/mirroring and an actual streaming visibility signal. The preview has no file/analysis/audio output. Stop removes its observers and unbinds; activation is not persisted or restored after backgrounding. The manifest adds CAMERA only and optional hardware declarations. A built-APK gate checks platform permissions and offline upstream notices.

Instrumentation retains all prior tests and adds fresh-install default-off/permission state, actual emulated front-preview rendering, fullscreen transfer/return, explicit disable/reopen and a real Home/foreground cycle with no automatic camera restart. New dependency compilation and all hosted checks passed in run 38826875086 (201 domain assertions, 60 emulator checks, APK/lint/native/browser and permission/notices gates); no hardware or latency measurement is claimed. Official references: https://developer.android.com/media/camera/camerax/preview and https://developer.android.com/reference/androidx/camera/view/PreviewView .

## Android microphone implementation context — October 16, 2026

Starting from main a4bd5dd0422da502709dcb342e309a963080860e. Java 17/Views, minSdk 26, targetSdk 36, CameraX 1.5.3, Activity 1.10.1 and AtomicFile/JSON storage remain unchanged. SessionController owns playback/FIFO; microphone capture will be an independent foreground opt-in input service with no AudioTrack, file output or network access in this first batch. RECORD_AUDIO will be requested only by explicit activation.

AudioRecord reads nonblocking bounded PCM16 buffers on a dedicated capture worker. A separate worker receives at most one outstanding 4,096-sample dry frame for pitch analysis; busy analysis drops frames rather than accumulating latency. UI reads immutable snapshots on its existing 100 ms tick. Stop clears visible measurements immediately; per-run identity fencing rejects late callbacks and observations. Input estimates are neither a singer-identification result nor a completed-song score. Monitoring/effects, calibrated timeline scoring, companion networking and official YouTube remain separate follow-ups. No measured device latency or microphone quality claim is made.

Android references reviewed: AudioRecord, AudioRouting and Android's Sharing audio input guide. The first capture batch deliberately has no microphone output route. Source and instrumentation acceptance remain pending until the changed head is built and exercised.

Accepted microphone input source c961cb3bbe76ef77bea8052529cb93a552d15a53 passed run 38839639544: APK/instrumentation build, lint, native sanitizers, 233 domain checks, browser interoperability, permission/notices gate and 78 actual API 35 emulator checks. No old assertions were removed. Only Markdown checkpoint reconciliation follows. No physical microphone acceptance, monitored output, effects or completed-song score is claimed. See ANDROID_MICROPHONE_CHECKPOINT.md.

## Android monitored-output implementation context — October 16, 2026

Starting from d4f9d84 (accepted 0.6 input and verified website). The Java/Views stack, storage, library search, AudioRecord dry path, CameraX and authoritative playback/FIFO remain unchanged. The next batch adds a separate opt-in AudioTrack monitor with no second input or song queue. Available-device enumeration is only a selection step; actual routed output must match that selection, with all API 36 routed outputs checked. Playback begins muted and route loss retires the activation. Speaker/Bluetooth routes are excluded.

Capture/output clocks are independent even at nominal 48 kHz. A bounded interpolation ring will adjust consumption for small clock drift and retire stale audio on overflow/underrun. Monitor-only Dry/Room/Hall/Echo processing never modifies the dry pitch frame. No native autotune, melody scoring, recording, network, new dependency or physical latency claim is included in this batch. Acceptance will distinguish domain DSP/routing checks, emulator rejection of unsafe output, and unverified physical wired/USB playback. References: Android AudioTrack/AudioRouting and NDK Audio latency guidance.

Candidate implementation now compiles all Android production and instrumentation Java against official API 36 and the exact 48 pinned runtime dependency coordinates from the accepted CameraX build. Local checks pass 268 domain assertions, browser export/wrong-code/tamper interoperability and test-layout wiring. Existing compatibility/deprecation/resource-analysis warnings remain; Android lint is still a separate hosted gate. Voice settings expand within Control to preserve search/reservation space. Physical output remains unverified.

Pre-API-36 routing exposes one reported route; any additional unreported outputs cannot be verified. API 36 checks the complete routed-device list. Privacy and experimental voice settings disclose this boundary. No absolute acoustic-safety claim is made.

Accepted monitor source 1ddee2416b6efe56562f5826ad479a6373ecf86c passed run 38842165486, all 268 domain and 90 actual API 35 checks plus APK/lint/native/browser/permission-notice gates. Only Markdown checkpoint reconciliation follows. Physical monitoring remains unverified; see ANDROID_MONITOR_CHECKPOINT.md.

## Android scoring implementation context — October 16, 2026

Starting from main 023dfff (accepted 0.7 source, downloads and production website). Java 17/Views, independent audio engines, AtomicFile/JSON storage and the one SessionController/FIFO remain unchanged. Scoring will be default off, use only the existing dry microphone estimate and never modify monitor processing. MIDI parsing will retain bounded track metadata for conservative monophonic melody identification; ambiguous/polyphonic/bent/malformed references must remain unavailable. MP3/WAV will not receive invented references.

Each natural completion must belong to the distinct reservation UUID and produce at most one result. Complete target windows remain in the final coverage denominator, including unobserved ending notes. Observation slots reject bursts, stale/out-of-order input and nonfinite values. Calibration/key/tempo changes invalidate accumulated observations. Input/frame and acoustic timing remain estimates requiring physical calibration; no device scoring accuracy is claimed. The recovered iOS extractor/scorer/reliability contracts inform the new independent Android implementation.

Scoring must remain bounded: target extraction off-main, logarithmic target lookup and constant-size incremental observation aggregation, without storing microphone audio or a growing observation list. Physical singer/backing-music separation, latency, monitoring, signing and external displays remain open. Companion and official YouTube follow this batch.

Candidate source now passes 328 domain checks (including melody evidence/rejection, full-ending coverage, boundary/slot uniqueness, invalid/extreme alignment, key/tempo handling, concurrent exactly-once completion and audible control fencing). Complete production/instrumentation API 36 compilation passed with the same 14 existing compatibility/resource-analysis warnings; hosted lint remains authoritative. Browser export/wrong-code/tamper checks passed. The controller/UI are wired with natural-completion emulator cases prepared; actual changed-head runtime acceptance is pending. No physical scoring accuracy is claimed.


Accepted Android 0.8 source `57dc0611cb54435802a4bd7d226e00983dd3c8f6` passed run [38845590252](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38845590252): APK/instrumentation assembly, lint, native sanitizers, all 328 domain assertions, browser interoperability, permission/notices gate and 110 actual API 35 emulator checks. PR #21 merged as `5e6d5dc09f4bbfed25566d6dc17dfc20fe0cf25b`. No prior assertions were removed. Physical timing/scoring accuracy remains unverified; verified download packaging is separate. See ANDROID_SCORING_CHECKPOINT.md.


Android 0.8 packaging run 38846121392 preserves the accepted 26,956,746-byte APK and unchanged unsigned IPA. Website source 941e9500 passed static run 38846304650 and READY preview dpl_6jPfDy2dTb6bN6h75zsJefxGoL3z. All 15 responses match source with CSP; browser download/guide navigation was inspected. See WEBSITE_08_CHECKPOINT.md. Production publication follows this preview; physical acceptance, pitch correction, secure companion and official YouTube remain open.


## Production 0.8 website verified

Deployment `dpl_HoVLX9W368oH6fgath4pbH6LHSDb` is READY on https://kanta-public.vercel.app, source `75ddb7b767bcbc2484c4732c7d13a37ac3fc620e`. All ten pages and five public assets returned 200, matched validated source byte-for-byte and retained CSP. Actual browser inspection confirmed Android 0.8 source 57dc0611, the accepted 328/110 counts and download bundle 12022594247 from packaging run 38846121392. Both platform pages use current accepted packages; iOS bytes remain unchanged and unsigned. The original private reader/styles and notices are unchanged.

Scoring implementation, accepted packaging and website publication are complete. Remaining software work is Android pitch correction, secure companion networking and official visible YouTube playback under YOUTUBE_PLAYBACK_PLAN.md. Physical scoring calibration, singer/backing-music separation, wired/USB audio quality/feedback/latency, camera/external-display acceptance and production signing remain open. This checkpoint changes Markdown only after the verified deployment; it introduces no new app/site runtime, workflow or dependency change.


## Android pitch correction implementation context — October 16, 2026

Based on accepted main d3fc2075 (0.8 dry scoring, verified packages and public website). The actual Java 17/Views, minSdk 26/targetSdk 36, CameraX, TinySoundFont/JNI, AtomicFile/JSON storage, normalized snapshot search and authoritative SessionController/FIFO are unchanged. No new library, permission, network or Apple source is introduced.

The next source batch inserts a bounded first-party chromatic corrector before existing monitor effects. Immutable per-run opt-in/epoch state requires actual-route readiness and resets with the existing monitor lifecycle. The audio worker reuses about 48 KiB of fixed sample/window storage; no arrays or detection jobs are allocated per block. It reads the existing immutable dry analysis result via a supplier without changing that result or scoring. Unknown/stale/unreliable input relaxes to unshifted delayed input. Two crossfaded variable-delay reads keep frame counts constant; range, smoothing, hysteresis and mathematical delay bounds are in ARCHITECTURE.md. Voice timbre, artifacts, thermal behavior and physical round-trip latency are acceptance items, not measured bottlenecks or established quality.

Local 364 domain checks pass, including an actual dry-detector-to-corrector chain on four detuned 73–880 Hz synthetic tones. Independent sine-projection frequency estimates are within eight cents of the expected equal-tempered targets. Tests cover delayed unshifted fallback, target hysteresis, stale/future/extreme input ages, silence/confidence/nonfinite rejection, finite limited samples, discontinuity bounds, history reset and bounded blocks. Full API 36 production/instrumentation source compiles against the exact existing dependency jars with 14 pre-existing compatibility/resource-analysis warnings. Browser interoperability and layout guards pass. Hosted lint/build and all real emulator checks are still required. The clean emulator has no supported wired/USB output, so new runtime cases verify control/default-off/route rejection/lifecycle behavior, not positive physical corrected playback.


## Hosted Android 0.9 acceptance

Source `4f48e051dcd815e878205eda43bf9331591e279d` passed run [38881735518](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38881735518): all 364 domain assertions and 116 actual API 35 emulator checks, APK/instrumentation assembly, lint, native ASan/UBSan checks, browser interoperability and merged permission/offline-notices checks. PR #25 merged as `2851a0b81f43698a99b036b8dd28aa25c0d495eb`. Existing assertions remain enabled. The accepted output is artifact `12038226827`; checksum-verified packaging must preserve these APK bytes before changing public downloads. Physical corrected voice quality, artifacts, feedback and total latency remain unverified. Secure companion and official YouTube remain queued. This entry supersedes candidate hosted-pending statements above.


## Verified Android 0.9 download packaging

Run 38882359866 verified source/run/artifact identity, original application bytes and archive digests. Android bundle 12038451005 preserves source 4f48e051, APK SHA-256 85952b6db76dd2c418013a78895d6161cfa9310b14f57fe8b75095b4a2db1474 (26,957,042 bytes). iOS bundle 12037574662 preserves the unchanged unsigned IPA. Downloaded manifest artifact 12038027446 matched its archive digest. Website pages/manifest now select these packages with separate experimental-correction instructions; hosted website validation and production publication are pending. See WEBSITE_09_CHECKPOINT.md.


## Production Android 0.9 verified

Website source `95855ff18e9c1758e53a4315bf460b9f29ee876b` passed static run `38882633589` and READY preview `dpl_ESS6EucV9dVBs8x7y9tKzjGSqxK8`. All 15 preview pages/assets matched source with CSP, and real browser inspection confirmed Android 0.9, the 364/116 checks, source/download identity and setup navigation. PR #27 merged as `4c8e8066dc9f480de1922410628990eb788df4d5`. Production deployment `dpl_4xB8tKDdT61KhkSMnTRLx21HS4de` is READY on https://kanta-public.vercel.app; all 15 production pages/assets match validated bytes with CSP. The browser independently confirmed the public Android 0.9 page and bundle `12038451005` from packaging run `38882359866`. Original private reader/styles/notices and unsigned iOS application bytes are unchanged.

Post-deploy verification caught a competing production deployment returning 404. The public alias was restored to the verified deployment, and the persistent Vercel project settings now explicitly use root `apps/web`, output `.`, no framework, and empty install/build commands. The corrected public alias and all 15 responses were verified after restoration. Deployment READY alone is insufficient: check the public alias, content and security headers before declaring completion.

Pitch correction implementation, acceptance, verified packaging and website publication are complete. The next software task is secure companion control, followed by official YouTube playback. Physical voice quality/timbre/artifacts/feedback/latency, scoring calibration, camera/external-display acceptance and iOS production signing remain open. This checkpoint changes Markdown only and requires no additional native build.


## Android companion implementation context

Android 0.10 adds foreground-only, explicit private-IPv4 companion hosting/client control through the existing SessionController/FIFO. The actual Java 17/Views, storage, imports, audio engines, CameraX and platform separation remain unchanged. INTERNET is added for private Java sockets; no new dependency, cloud service, discovery broadcast or iOS protocol change is introduced. KCP1 uses a 128-bit random expiring pairing secret, mutually authenticated HMAC handshake, HKDF direction keys and ordered AES-GCM records. Commands are limited to bounded search/queue metadata, reserve/cancel and Play/Pause/Next; sensors and file access are excluded. A single worker owns socket work; main-Looper mutations check active foreground run and cancellation. Background/Stop revokes immediately, with no automatic reconnect. See ANDROID_COMPANION_CHECKPOINT.md for exact framing, bounds, replay scope, no-forward-secrecy limitation and acceptance.

Candidate core compilation and all 443 domain assertions passed (79 new companion checks); browser interoperability and Swift layout guards pass. Hosted build/lint and actual emulator socket/queue/lifecycle checks remain pending. Physical two-device networking, iOS compatibility and independent security review remain open. Public downloads remain accepted 0.9 until separately verified packaging/publication.


## Hosted Android 0.10 acceptance

Final source `d777334eec73996116fdf2c01d97ac73a3a53958` passed run `38885716463`: all 448 domain assertions (84 companion cases), 139 actual API 35 emulator checks, APK/instrumentation assembly, lint, native ASan/UBSan, captured browser interoperability and the CAMERA/RECORD_AUDIO/INTERNET permission/notices gate. Initial source also passed the complete suite; the final source adds bounded peer JSON nesting and strict UTF-8 checks. An independent Python cryptography client verified the JCA handshake, HKDF, framing and both direction keys. PR #28 merged as `51b57e0052c8f01d08fac04f12cccae06b189ad5`. Accepted original artifact is `12039807731`. No existing assertion was disabled. Physical two-device networking, iOS interoperability, forward secrecy and independent security review remain open; Android acceptance does not establish those properties. Public downloads remain verified 0.9 until checksum-verified packaging and website publication.


## Production Android 0.10 verified

Website source `f2bd2b517f0a9059084b2604cddcdc9b2cf1e66c` passed hosted run `38886674120`; preview `dpl_2bG6QWnkFVbkT7YLVuwyxBgLAr13` is READY. All fifteen preview routes/assets returned 200 and retained CSP. Fourteen responses matched source exactly; the homepage matched source plus Vercel's appended preview feedback-script tag, which is blocked by the unchanged CSP. Browser navigation confirmed Android 0.10 source d777334e, the 448/139 counts, accepted download identity and companion setup guide.

PR #30 merged as `0a11058b2d70182b2956d57b2f320edbe19762ee`. Production deployment `dpl_H9fF42EU9QobF613s7Y4d7Rvfkbt` is READY on https://kanta-public.vercel.app, using explicit `apps/web` root, `.` output and empty install/build commands. All fifteen public responses returned 200, matched validated source exactly and retained CSP; production has no appended feedback tag. Browser inspection of canonical `/android` independently confirmed version 0.10.0, source d777334e, run 38885716463 and Android bundle 12040160062. Both platform download pages use successful packaging run 38886416353; iOS bundle 12038684955 preserves the unchanged unsigned IPA. The 26,974,938-byte Android APK SHA-256 is 7743d1994c500b472f86829ea041307303f0f908d0e5505b788c35ca76026d3d. The downloaded packaging manifest archive digest was checked independently.

Android companion implementation, automated acceptance, original-byte packaging and website publication are complete. This is an experimental Android-to-Android milestone, not cross-platform or independently audited security acceptance. The recovered iOS plaintext protocol remains incompatible; encrypted iOS interoperability is the next companion milestone, followed by the queued official YouTube integration. Physical two-device networking, audio quality/feedback/latency, scoring calibration, camera/external displays and production app signing remain open. Final handover updates are Markdown only and introduce no runtime, workflow, dependency or asset change.


## Active iOS presentation repair — 2026-10-17

Repository: `kanta-gelo/Kanta-reborn`, PR #36, branch `fix/ios-modal-ownership-preflight-20261017`. Candidate source commit: `27fed3a92416f0efce221f6bb9ad4c8c712a9f83`. RootView, HostView and KantaMainPlayerView share `KantaPresentationGate`; it queues one pending request per owner through a dismissal callback so a Search tap during the Library sheet transition is not discarded. Existing UI coverage exercises More → Library → play starter → Done → one-tap Search → keypad → Cancel → CONTROL. This is source-level reasoning only until the current head passes both iPhone and iPad UI walkthroughs. UIKit fullscreen ownership remains separate and unverified. No IPA, merge or deployment is established.


### iOS presentation lifecycle update — 2026-10-17 candidate

PR #36 now covers the previously separate UIKit fullscreen player in the shared presentation-gate candidate. The gate models Search/Queue presented inside fullscreen as a nested stage owner, restores fullscreen on child dismissal, and releases fullscreen only after UIKit's dismissal callback. The UI walkthrough adds nested Search and post-exit Search checks. Current-head CI and iPhone/iPad runtime evidence are still required; no release verification is implied.


## PR #51 foreground player lifetime repair — October 18, 2026

Source baseline: PR #51 `77f9f1078efc05cf91cc06db4fbd93780007c87e`. Source review confirms Swift 6.2/iOS 17 SwiftUI + WebKit; Java 17/Views, minSdk 26/targetSdk 36, existing Activity 1.10.1/CameraX 1.5.3 and direct system WebView. No dependency, permission, storage, search, decoder, remote-protocol, signing or workflow change. LocalKaraokeController and SessionController retain authoritative FIFO/transport. Transient preview UUIDs fence errors/dismissals; native retirement runs on the UI thread, with explicit WebKit suspension and Android terminal destroy. No measured audio latency or hardware behavior is inferred. Static/Python checks passed; hosted compilation/runtime and audio-focus coordination remain open. See `YOUTUBE_LIFECYCLE_CHECKPOINT.md`.


## Android online preview follows authoritative playback publication — October 18, 2026

Baseline: PR #51 `5c1b0f4`, whose reconstruction and focused iPhone smoke checks passed. The online/local overlap guard previously ran only after primary Play/Next button callbacks. Encrypted companion commands bypass those callbacks, and backend start acknowledgement is asynchronous, so those paths could leave a visible online player alive after local playback started.

MainActivity now reconciles the preview at the start of the existing SessionController listener's render callback. All published local starts/resumes retire the online instance; primary buttons again issue only controller commands. The preview owner does not pause local transport, consume reservations or introduce a playback queue. Replacement/dismissal guards remain unchanged. This closes a controller-observation gap, not general OS audio-focus arbitration or a guarantee of zero overlapping audio frames. SFX/intermission, iOS native runtime and physical audio/session acceptance remain separate.

The Android instrumentation uses real KCP1 socket commands to reserve/start a local starter while a preview is open, waits for backend start acknowledgement, verifies native preview retirement, pauses, explicitly reopens the preview, resumes remotely and verifies retirement again. Reservation alone must preserve the preview, normal FIFO consumption remains checked, and local Stop must not reopen online playback. New-head APK/lint/emulator acceptance is pending. No website, dependency, permission, persistence or remote-protocol change.


## Android audio ownership context — October 18, 2026

Based on PR #51 `fa5dcbcf7aa070d924d86ed6c3e832af3618cebb`. Java 17/Views, minSdk 26/targetSdk 36, Activity 1.10.1, CameraX 1.5.3, direct system WebView and existing AudioFocusRequest remain unchanged. No new dependency, permission, storage/search schema, decoder, remote protocol, signing or workflow. SessionController now owns a main-Looper-only online-audio UUID and retirement callback; MainActivity destroys only its matching visible preview. All five native focus call sites use one revocation-before-request helper. Auxiliary automatic intermission cannot reclaim focus while online is active. Microphone/monitoring must be stopped explicitly before opening online playback. Local queue/current song and decoder settings remain unchanged.

Regression instrumentation uses real native players/AudioRecord and KCP1 commands with network-blocked local WebView content. Tests cover stale release, explicit local output priority, auxiliary stop/restart, microphone preservation, foreground loss and distinct FIFO identities. No physical latency or zero-overlap claim is made. Reviewed Android's official audio-focus guidance: https://developer.android.com/media/optimize/audio-focus . Native focus requests remain checked immediately before native output; WebView playback remains gesture-controlled.


## iOS native WebKit coverage context — October 18, 2026

Baseline PR #51 `99f3898` has green reconstruction validation, including Android audio coordination. iOS app/runtime playback and dependency boundaries are unchanged. XcodeGen adds one iOS 17/Swift 6 hosted unit-test target using the system XCTest/UIKit/WebKit frameworks, the existing GeloKaraokeCore product and the unchanged production preview component source. No dependency upgrade, permission, persisted state, app identity, signing, package test removal or release-testability setting is introduced.

Three native cases cover replacement/same-video identity, foreground-loss cleanup/no resurrection, stale callbacks, process-failure idempotency and explicit reopen. No remote bytes are requested by those cases. Test-layout coverage includes both Xcode test roots; the smoke workflow builds and executes native and navigation tests serially within the existing timeout. The log gate requires all four named passes and no prior failure/restart. Local source/wiring guards and 54 Python release-tooling tests passed; Xcode/native simulator execution must validate the resulting head. iOS audio-session coordination remains separate.


## iOS audio handoff candidate — October 18, 2026

Based on PR #51 head `5d25f62993c8b003099c345b5387172a1fdf5af7`. AppServices constructs one MainActor KaraokeAudioOwnership alongside the existing LocalKaraokeController and a shared intermission owner. The coordinator owns only an ephemeral online UUID/retirement callback and foreground/microphone eligibility; it owns no queue, local transport, persistence or YouTube commands.

Online claims acquire the controller's existing cancellation-aware operation gate, reject actual playing state, stop SFX, suspend both idle rendering engines without Pause/Stop or position changes, then atomically recheck microphone/foreground/monitor eligibility on MainActor before stopping intermission and configuring the shared playback session. Local load/resume/playing seek and SFX retire the online native owner before native output. Remote commands and natural advance use these same controller paths. Capture announces activity synchronously before its permission await and releases it on denial/failure/stop; native output preserves capture's playAndRecord category. Session errors fail visibly. Foreground loss retires online ownership; returning does not reopen it.

The sheet cancels pending claims on close and rechecks its request UUID/foreground/ownership after suspension. The representable also checks ownership before attachment/load, preventing a revoked pre-mount session from constructing an active player. Stale release cannot clear a replacement. Intermission stops immediately, invalidates fades, and requires explicit re-enabling; automatic snapshot updates cannot reclaim online audio. Source files and reservation/playback identities are unchanged.

Regression coverage adds controller preparation/cancellation/microphone races, paused song/FIFO identity, explicit Next/SFX handoff, stale release, foreground, session failure and both rendering routes. Native smoke adds real WKWebView retirement-before-session configuration and synthetic AVAudioPlayer intermission handoff/restart; the named-pass verifier now requires all five native cases plus navigation. The test target compiles the two production components directly; no app testability, dependency or permission change.

Local layout, presentation, starter resources/generation and whitespace checks passed. Hosted Swift compilation, package execution, unsigned simulator app build and expanded native/navigation smoke are pending on the resulting source. Physical audio timing, real embeds/ads, interruptions, privacy and full iPhone/iPad release acceptance remain open. No zero-overlap timing, signing, merge, deployment or release is claimed.


### Accepted hosted iOS audio coordination — October 18

Source `6f7a0ece9e42cc500ce4f52cc414c58e5e11b58e` passed reconstruction 39112387766 (178 Swift tests, 54 tooling tests, unsigned simulator app build and Android validation) and smoke 39112387680 (all five native cases plus focused navigation, explicit named-pass gate). The iOS session/auxiliary implementation and hosted regression gap is closed. Actual SwiftUI online-sheet/media flow and physical embed/interruption/privacy/ads acceptance remain open; no zero-overlap timing, merge, signing or release is implied. See docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md for evidence and next steps.


## Online-sheet UI acceptance context — October 18, 2026

Baseline PR #51 24db7a1 contains source 6f7a0ec with accepted controller/native audio coordination. Actual UI entry is Stage CONTROL -> HostView's nested online sheet -> KantaYouTubePreviewSheet -> transient lifecycle/serialized controller claim -> WKWebView. The sheet owns only presentation; cancellation, foreground and ownership checks fence its asynchronous claim. AppServices owns shared services, while LocalKaraokeController still owns transport/FIFO.

This batch adds stable control identifiers and keyboard dismissal plus two real XCUIApplication flows: invalid/valid entry, stop/reopen, background cleanup, dismissal/reentry and paused-original-starter preservation/resume. Synthetic video metadata has no autoplay; an external request must yield visible player controls or a recoverable native load error. Neither accepted outcome proves playable media/ads. Existing native process-failure and handoff tests remain strict. Keep all previous smoke cases and require both new named UI passes; no injected fake player, test launch bypass, extra sensor permission or dependency. Full release walkthrough/device/media gates remain separate.


## PR #51 built-in and browser remote candidate — October 18, 2026

Owner explicitly moved the approved Companion Remote UI and browser implementation into PR #51. Source now adds native role entry, locally served responsive remote assets, private-interface HTTPS with per-session identities and fingerprint trust bootstrap, bounded guest authentication/replay/expiry, native pinned clients and controller-owned local commands. No decoder, persistent media/queue format, native dependency, sensor permission, signing or website release change. Existing KCP1/legacy paths remain separate. The shared HTML assets are platform-owned copies checked for equality; neither native build depends on the other platform.

Local Java core: 524 checks passed, including 45 actual TLS checks. API 36 compilation of PinnedBrowserTransport, 55 tooling tests, Swift layout/presentation/starter guards and captured Songbook interop passed. Local Chrome was blocked by the environment's Unix socket restriction; UI is not accepted from syntax/transport checks. Native builds, nine-case simulator smoke, full eight-case iPhone/iPad walkthrough, real-browser tests, cross-platform/device trust/security/media acceptance remain pending. Unsupported expanded shortcuts are disabled, not represented as completed features. See docs/COMPANION_BROWSER_CHECKPOINT.md (root-relative) for exact trust, expiry, limits, source and acceptance scope. No physical latency/performance result, merge, signing, deployment or release is implied.


## PR #51 native iOS Keychain test boundary — October 18, 2026

Source checkpoint `2e67879` compiles `LocalNetworkControl` as a Swift package dependency of the XcodeGen iOS host and native XCTest. `ios-smoke.yml` builds that target in Release without enabling `@testable` on the package. The app-hosted native test therefore requires a narrowly scoped Swift SPI to call the existing session-identity Keychain implementation without changing the production build configuration or introducing a parallel identity implementation. `BrowserRemoteIdentity` remains owned by `LocalNetworkControl`; its `@_spi(Testing)` surface is test access, not a supported client API. The native test still checks exact certificate bytes, private-key retrieval and removal. The previously failed smoke run is 39129484883; changed-head acceptance is pending. No new dependency or runtime queue/audio state owner is introduced.


## PR #51 iOS native Keychain acceptance boundary — October 18, 2026

The Release test-module SPI repair compiles and passes Swift package tests at `215e02a` (reconstruction 39136036615). The unsigned simulator smoke job 39136036597 executed the new app-hosted identity test but `SecItemAdd` returned `errSecMissingEntitlement` (`-34018`). Five native WebKit and four UI smoke tests passed; the Keychain acceptance gate did not. `ios-smoke.yml` uses `CODE_SIGNING_ALLOWED=NO`; no team identity, provisioning profile, signed IPA or new release was created. An authorized entitled simulator test host and explicit entitlement inspection are required before claiming native Keychain support. Production runtime and FIFO ownership are unchanged.

## Compact persistent workspace — 2026-10-26

Shipped in release `v0.1.0-workspace-redesign` at source `41c8ddde`: the iOS embedded Host composes a persistent Stage surface, a persistent now-playing strip, and width-aware contextual actions. Existing Search/Queue presentation, Host More navigation, and the Settings/CONTROL sheet remain authoritative. The no-Previous/no-progress-bar contract is preserved. Static source guard coverage was extended for stable accessibility IDs and layout invariants, and the hosted compiler plus iPhone/iPad walkthrough results passed on the merged head. The Android counterpart is `apps/android/app/src/main/java/ph/kanta/reborn/KantaWorkspace.java`, mounted from `MainActivity.buildWindow()`.


## KANTA Host branding and borderless player — 2026-10-26

The compact workspace now uses KANTA Karaoke branding in Host navigation/header, keeps the KANTA mark visible, renames the Bonjour-discovered Host and external-stage standby copy, and updates the user-facing legacy playback error. The embedded stage/player bleeds horizontally to device edges and removes the decorative card strokes. Internal GeloKaraokeCore module names remain unchanged. New source guards enforce visible brand/logo and borderless composition; the hosted iOS source build and iPhone/iPad walkthroughs passed on the merged head and the result shipped in `v0.1.0-workspace-redesign`.


## Compact Workspace acceptance — October 27, 2026

The compact persistent workspace's accessibility repair removes the identifier from the interactive transport parent and labels the Host-controls action “More controls” to avoid collision with Settings. The branch was synchronized with main while preserving the workspace implementation and its repair documentation, then merged as `41c8ddde`. Reconstruction, focused smoke, unsigned archive and both independent device walkthroughs passed on the merged source SHA, and the result shipped in GitHub Release `v0.1.0-workspace-redesign`.

The Android implementation mirrors the same architecture and the same two accessibility rules: `KantaWorkspace.java` owns the persistent stage layer and the single-active transient accessories, and each actionable child keeps its own `contentDescription` while the container does not override it. The Android toolbar's control-opening entry is labelled “Controls”, never “Settings”.

## Android Mic & Voice and Visuals destination integration — 2026-11-01

The safe reference branch now mounts `KantaMicVoiceScreen` and `KantaVisualsScreen` as full-screen Home destinations. Mic & Voice delegates microphone permission/start/stop to `MainActivity.toggleMicrophone()` and `SessionController.startMicrophone/stopMicrophone`; monitoring output selection and teardown use the existing verified-route `MicrophoneMonitor`, gain and the implemented Dry/Room/Hall/Echo modes. The screen polls the real input meter and `SessionController.scoreStatus()`; it never invents coach scores. The previous screen's drawn-but-inert reverb/echo tracks and unsupported Warm/Bright FX labels were removed.

Visuals now renders thumbnails from the ten existing `assets/scenic/*.png` images, selects those existing scenes through `SessionController.scene()`, and routes Camera through the existing CameraStage permission/lifecycle owner. Custom media opens the existing user-selected photo/video import path. Stage post-processing effects are explicitly reported as not implemented rather than exposing nonfunctional controls. New generated KANTA artwork can later replace/add to these thumbnails without changing the scene-selection or camera ownership contracts.


**Current safe-branch integration:** Search, Queue, Pitch & Tempo, Mic & Voice and Visuals are mounted full-screen destinations. Mic & Voice uses live microphone/monitor/score status and verified headset-only monitor output; Visuals uses the existing ten scenic assets, CameraStage and stage-media import. Generated image assets are still pending and are not a blocker to the functional wiring. Now Playing/fullscreen presentation and desktop browser remote redesign remain open.

## Mic & Voice and Visuals runtime acceptance — 2026-11-01

Android instrumentation now captures the mounted Mic & Voice and Visuals destinations in addition to Home, Search, Queue and Pitch & Tempo. It selects the existing Bamboo Mist scenic asset and asserts the selection reaches SessionController, then selects the Room vocal-monitor processor mode and asserts the existing MicrophoneMonitor owner receives it. The test does not request microphone/camera permissions or fabricate input/coach readings. Both named screenshots are required by the Android runtime collector and artifact allowlist.

## Browser remote desktop layout — safe branch in progress

The Android-hosted browser companion now uses a reference-aligned responsive shell: a desktop sidebar, central host-library/search/queue column and right-side Now Playing panel. Narrow viewports collapse to a single-column mobile controller. The existing element IDs, pairing verification, fingerprint-pinned transport, session expiry, command identities, request bounds, host-owned state and protocol allowlist are retained. Unsupported Remotes/Settings, camera, microphone, key/tempo and fullscreen controls remain visibly unavailable; the remote does not fabricate commands or duplicate Android state.

## Shared browser companion asset contract — 2026-11-01

The Android and iOS apps package the same browser companion files from `apps/android/app/src/main/assets/companion/` and `apps/ios/Resources/CompanionRemote/`. Keep `index.html`, `remote.css` and `remote.js` byte-identical. The companion remains a bounded LAN controller with explicit fingerprint verification, host-owned state, pairing/session expiry and no cloud relay. Its responsive desktop layout is presentation-only; it must not add commands outside the existing protocol allowlist or claim host-only features are remotely available. The shared asset parity and real-Chrome browser contract are checked by `tools/verify-companion-browser.mjs`.

## Fullscreen Now Playing stage integration — 2026-11-01

The Android fullscreen surface now keeps the real `StageSurface` as the full-bleed background/lyric owner and adds a live metadata/transport overlay. Song title, artist, playback time, MIDI duration when available, queue count, key and tempo are projected from `SessionController`; Play/Pause, Next, key and tempo commands delegate to that owner. The overlay does not add Previous, seek, shuffle or repeat controls that are absent from the active Android contract. Runtime instrumentation captures `fullscreen_stage.png`, exercises key increment/reset through the live session and verifies dismissal; the runner and Android workflow require the screenshot in the artifact. Audio formats without an available source duration display `--:--` rather than an invented duration.

## Fullscreen stage acceptance — 2026-11-01

The active fullscreen player is the existing `StageSurface` (lyrics/background/camera/video owner) with a live `SessionController` projection for title, artist, source time, MIDI duration when known, queue count, key and tempo. Play/Pause, Next, key and tempo controls delegate to the existing session. Previous and seeking remain absent; unsupported duration is shown as unknown. The runtime walkthrough opens the actual Dialog window, captures the fullscreen stage, exercises key increment/reset and verifies dismissal. `fullscreen_stage.png` is mandatory in the runner collector and CI artifact.


**Now Playing:** `MainActivity` opens the dedicated `KantaNowPlayingScreen` when the persistent stage is tapped. The screen reads the live `SessionController` song, singer, playback clock, backend duration and timed MIDI cues. Playback, Next, key, tempo, Queue, Mic & Voice and fullscreen are delegated to existing owners. Unsupported transport and favorite actions are disabled; no sample lyrics or demo song metadata are shipped.

Now Playing is a real destination opened by tapping the persistent stage. Its `KantaNowPlayingScreen` embeds a live `StageSurface` rather than a static mock backdrop; selected scenic/video/camera output and synchronized lyric rendering remain with the existing StageSurface owner. Session updates refresh the Now Playing metadata/transport projection without rebuilding the screen or restarting the stage surface. The runtime collector and Android artifact allowlist now require `now_playing_screen.png` as well as `fullscreen_stage.png`.

## Generated KANTA artwork integration — 2026-11-01

The existing drawable-nodpi art pack is now used by production UI rather than remaining an unused resource set. Home Quick Play uses the My Set/Favorites/Recent collection artwork; Search results and Queue rows use deterministic stable song-ID mappings to the eight generated covers. Visuals uses the seven supplied cinematic backgrounds plus the existing stage artwork for Default, and selecting a tile calls `SessionController.sceneResource` to decode/publish the actual drawable through the existing stage owner. The runtime test selects Stage Lights and waits for the new Bitmap instance to replace the prior scene. No additional upload is needed for these existing 18 assets; future new artwork can be added through the same resource mapping and screen APIs.
## Browser remote cover-art delivery — 2026-11-01

The eight supplied WebP covers are packaged alongside the browser companion in Android `assets/companion/art/covers/` and mirrored byte-for-byte into iOS `Resources/CompanionRemote/art/covers/`. The Android `BrowserRemoteServer` and iOS `BrowserRemoteHost` serve only the exact `/assets/covers/01.webp` through `/assets/covers/08.webp` allowlist, with per-file and aggregate byte limits; no new remote commands or host-state authority are introduced.

The real-Chrome companion check verifies that search-result covers and the Now Playing cover load successfully over the pinned HTTPS host. The browser host fixture uses the same bundled files, so a missing resource fails the browser contract instead of silently falling back to decorative placeholders.
