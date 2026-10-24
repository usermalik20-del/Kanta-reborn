# Codebase Context Packet — KANTA Reborn

## Current source and acceptance snapshot — October 25, 2026

Canonical repository: [usermalik20-del/Kanta-reborn](https://github.com/usermalik20-del/Kanta-reborn). Main is [`c63116e`](https://github.com/usermalik20-del/Kanta-reborn/commit/c63116e6c2fc39e1e8971b4da89f4befcfab7b0b), the merge of PR #69.

Reconstruction [#40126620995](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126620995) passed the iOS and Android jobs. Unsigned iOS archive [#40126621029](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621029) succeeded; artifact [#12573507853](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621029/artifacts/12573507853) is 25,076,354 bytes with GitHub ZIP digest `sha256:9a89fecf24f77b25c6c86898a7a8bc7335868ad66fd4534980f2093865478e1f`, expiring 2026-10-31 19:47:33 UTC. It is unsigned and not directly installable.

Full walkthrough [#40126621040](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621040) failed on the same main SHA. The iPad failed four keypad cases; the iPhone walkthrough did not record `testMainMenuButtonsAndScreens` as passing. Earlier candidate walkthrough [#40122872536](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40122872536) and diagnostic artifact [#12572620221](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40122872536/artifacts/12572620221) pinpointed the iPad interaction fault: the Search button's reported center was hit-tested to an oversized `_UIGraphicsView` from the decorative stage canvas. The stage background is now being made non-hit-testable on both the scenic/video and camera-preview branches, with a source-contract guard and updated architecture/testing/failure records on repair branch `fix/ios-stage-visual-hit-testing-20261025`.

The new candidate is not yet accepted. Require source guards, Swift package tests, simulator build, focused smoke, reconstruction, unsigned archive, and complete iPhone/iPad walkthrough on one exact candidate SHA. No signed IPA or published GitHub Release is verified; the public releases endpoint currently returns an empty list.

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
