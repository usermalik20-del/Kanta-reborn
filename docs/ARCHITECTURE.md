# KANTA Reborn architecture

## Repository boundaries

`apps/ios` retains the original Swift app structure, project.yml, Package.swift, Sources, Resources and Tests. The original stage-ui snapshot is the selected baseline because it includes the latest recovered stage implementation; missing paths have been restored individually and absent infrastructure reconstructed; hosted Swift tests and unsigned simulator compilation have passed; device/release acceptance remains open. Original Apple bundle identities and iOS 17 minimum remain subject to the recovered project configuration.

`apps/android` contains a new native Java/Android Views reconstruction, explicitly distinct from original source, with its own Gradle build and signing boundary. See its README for implemented and missing features.

`recovery/ios/snapshots` contains other original snapshots without nested Git databases. They are evidence, not dependencies. Complete recovery ZIPs remain preserved separately. Historical source variants must be reconciled file by file.

`docs/evidence/website/2026-10-15` stores exact public-site bytes, extracted readable text and URL/SHA-256 manifest. It remains immutable evidence; the active restored website is maintained separately under apps/web. No public song catalog may be added.

## Runtime contracts

Each platform has one playback controller and FIFO reservation queue. UI, lyrics, fullscreen/TV stage and companion control project that owner's state. Import and persistence run away from latency-sensitive playback/UI work; commits are atomic and stale async results cannot replace newer state. Retry suppression applies to the same operation ID, never separate intentional reservations.
The iOS fullscreen stage is a UIKit-owned modal: its presenter must be attached to present, but must still dismiss its owned player when UIKit detaches the presenter view. The shared gate releases fullscreen ownership only after UIKit confirms dismissal. Embedded Stage Search/Queue presentation failures must emit bounded `KANTA_STAGE_PRESENTATION` lifecycle diagnostics, and the simulator runner must retain filtered unified logs plus a compact iPad evidence artifact; do not infer a UIKit root cause from a missing keypad alone.

Scoring is default off and observes dry microphone input without storing audio. Completion scores belong to immutable playback identities and are emitted only once. Monitoring and pitch processing are separate opt-in services and must mute on unsafe output routes. Source media and user SF2 are byte-preserved.

Companion commands require explicit pairing, authorization, bounded replay protection and revocation. Website descriptions conflict between older plaintext PIN control and newer encrypted Android pairing; do not implement or advertise cross-platform transport compatibility without an explicit reviewed protocol and tests.

## Planned built-in and browser Companion Remote

The owner-approved [Companion Remote roadmap](COMPANION_REMOTE_ROADMAP.md) defines the supplied remote design as a built-in role in both native apps, with host-address browser access for guests without KANTA. Pairing and host authorization are mandatory; both interfaces project the existing host/source owners and never create a queue. Native iOS hosting, browser transport/trust bootstrap and expanded controls remain planned. The reference is preserved in docs/design-references/companion-remote-reference.jpeg. The owner moved this implementation into PR #51 on October 18. The initial planning-only status is superseded by the candidate described below.

## Build isolation

Root workflows validate relevant pull requests once and validate main after merge. `reconstruction-validation.yml` owns Swift package/source guards, unsigned iOS simulator compilation and Android build/tests. `ios-smoke.yml` adds focused iPhone simulator navigation acceptance on relevant iOS pull requests; it is not a replacement for the full device walkthrough. The smoke workflow is limited to KANTA-owned source guards, native lifecycle tests, named simulator tests, and evidence uploads; it must not install unrelated external CLIs or mislabel them as audio analysis. Audio-quality and latency acceptance require a separate KANTA-specific measurement contract. `website-validation.yml` and `verified-downloads.yml` run focused website and download-contract checks. Feature-branch push events do not duplicate PR validation.

Unsigned iOS personal-signing IPA packaging is an explicitly dispatched, separate `ios-runtime-artifacts.yml` workflow. `ios-visual-walkthrough.yml` runs complete iPhone/iPad acceptance manually, weekly, or on `ios-rc-*` tags. Release acceptance requires evidence for the exact candidate SHA; neither simulator checks nor unsigned archives establish signing, device audio quality, or deployment. `automated-failure-triage.yml` is dispatched with a failed run ID rather than launching after every successful run. Workflow permissions, bounded timeouts, action pinning and no signing credentials in Git remain mandatory. See `docs/TESTING.md` for authoritative triggers and gates.

## Shared material

Shared future files are limited to independently verified first-party assets and documented interoperability formats. Native playback state and UI code remain platform specific. The captured Songbook JavaScript may inform a compatibility specification; a claim of compatibility requires cross-platform fixture tests.

## Reconstructed storage and export boundaries

On iOS, SavedKaraokeSetStore performs synchronous atomic write-before-publication transactions inside its actor, retaining durable Save operation keys. SQLiteSongCatalog is owned solely by BookmarkedKaraokeLibrary; validated metadata replaces atomically and query terms are bound. CSV is metadata-only with quoting/formula neutralization. Private Songbooks derive an AES-GCM key from a random per-export 128-bit code using the captured browser format. The SFX actor owns at most four independent AVAudioPlayer voices and bounded private clip copies. The C codec uses bounded streaming buffers and private exclusive temporary files; Apple compilation passed; legacy licensing acceptance remains open. Older LAN pagination and singer APIs were reconciled; its transport remains plaintext.

## Android ownership and media

KantaApplication owns one SessionController. MediaPlayer creation, commands and callbacks stay on the main Looper. ReservationQueue gives intentional reservations distinct UUIDs with bounded replay tracking. A serial IO executor owns LibraryRepository writes and heavy file work. Search uses cached normalized song keys, immutable snapshots, a separate executor and stale-result fencing. Visible song results and reservation rows are paginated.

SAF imports create hash-identified, byte-preserved private files; AtomicFile commits metadata, queue references, saved sets and starter tombstones. SMF 0/1 lyric parsing bounds files, tracks, events, text and timing while merging tempo across tracks. MIDI channel events feed a dedicated TinySoundFont/AudioTrack worker; imported SF2 selects the next song's bank. MP3/WAV retain main-Looper MediaPlayer. Explicit backend start acknowledgement commits the reservation. RenderedPlaybackClock tracks audible frames through buffered tempo changes. Playback preparation and SAF tree scanning have separate workers from serial catalog IO. Failed prepare retains the reservation; stale or removed reservations cannot start. Native StageView, fullscreen and TV Presentation project the same playback time without a second queue or progress bar.

Explicit Android exports use the shared CSV/AES-GCM browser format. The manifest has no Internet permission; optional CAMERA use is described below for the accepted 0.5.0 source, and the 0.6.0 input batch adds explicitly requested RECORD_AUDIO. Device-provider speech is user initiated and disclosed. SAF folder import and custom bank selection are implemented in the current Android branch; camera/video are implemented as described below; monitored audio, calibrated scoring and encrypted companion transport remain unfinished. See CODEBASE_CONTEXT_PACKET.md for bounds and validation status. The official YouTube integration is queued in docs/YOUTUBE_PLAYBACK_PLAN.md.


The recovered iOS LAN connection now suppresses duplicate command envelope IDs through a bounded 512-entry cache on the main actor. Completed results may be evicted; in-flight commands stay pinned. This is connection-scoped, not durable replay protection across reconnects, and does not encrypt the recovered plaintext protocol.

Final scoring uses the complete target timeline, including unheard ending windows; live scoring uses elapsed windows. Latency changes invalidate accumulated observations. Host completion applies RoomScoreReliability before publishing a final estimate. These recovered scoring contracts are reconciled from the scoring snapshot without replacing stage UI.

The validation pipeline compiles all Swift package test targets before running them and attempts the iOS app build independently of package failures. Test layout verification prevents orphaned suites; compiler and runtime checks remain authoritative. See TESTING.md for module contracts and acceptance boundaries. Indexed metadata fallback supplies stable non-playable songs when container offsets cannot be verified.


## Online runtime and packaging evidence

PR #36 passed final-head reconstruction and full iPhone/iPad walkthrough validation, with unsigned IPA evidence, before merging as `59258ee`. PR #42 added a separate focused iPhone simulator smoke test and merged as `714bfd6`. PR #43 separated costly packaging and eliminated duplicate branch push/PR runs, merging as `ad95e8b`; post-merge reconstruction run 39051661330 passed. These results establish hosted CI behavior only, not physical-device, signing, or production release acceptance.

## Android stage and auxiliary sound

MIDI lyric cues retain original fragment timestamps and character boundaries. StageView uses those source events for highlighting, the existing audible clock for position, and cached StaticLayout objects for stable text. No word timings are invented. Adjustable lyric size and optional first-party perspective lights are projections of SessionController state; animation owns no playback clock.

PartyAudio owns at most four SoundPool voices and one separate MediaPlayer for explicitly enabled idle intermission music. SessionController alone decides eligibility using foreground, audio focus, current song, loading state and the existing FIFO. It stops auxiliary audio on focus loss/background and never consumes reservations for SFX. Builtin synthesis runs after the main library becomes ready. Custom pad validation runs on IO; per-slot generations retire stale validation/load results, and preference changes commit only after decode acknowledgement. Original media is reused byte-identically from My Set.

## Android personal stage media

StageMediaStore is owned by the serial IO executor. It copies selected files into hash-named private storage, validates content and bounds before committing versioned AtomicFile selection metadata, preserves original bytes and rolls back failed/cancelled imports. Photos are limited to 32 MiB and 100 megapixels/16,384 pixels per dimension; display decode samples to at most 2,048 pixels and honors EXIF orientation without rewriting the source. Videos are limited to 512 MiB, 8,192 pixels per dimension and six hours. Only the replaced same-kind private copy is removed after commit; unknown files are not swept.

SessionController remains the sole queue/transport owner and publishes the selected visual through independent operation and display generations. StageSurface owns only a lifecycle-bound muted looping MediaPlayer, its TextureView surface and the existing lyric projection. It starts visual video only in the foreground, releases it when inactive/detached, retains Scenic behind failed/unrendered video and fences stale preparation callbacks. Fullscreen suppresses the redundant primary video renderer; a separate external-display projection may own another decoder. No camera, audio monitoring, network permission or second karaoke queue is introduced.

MainActivity retains its stage/transport/navigation tree across controller publication and rebuilds only the selected control panel. Five weighted navigation tabs remain visible on narrow phones. The app icon reuses the original recovered iOS artwork byte-for-byte. The test-only solid-color silent MP4 is procedurally generated and has documented provenance; no third-party clip is bundled. Cloud run 38825170997 passed source 2ced06c5c6063e15e227cc094b2462a36a03e7f2: APK/instrumentation assembly, lint, native sanitizer checks, all 201 domain checks, browser interoperability and 47 actual API 35 runtime checks. Physical-device acceptance remains open.

## Optional Android front-camera preview

The 0.5.0 source adds pinned AndroidX Activity 1.10.1 and CameraX 1.5.3. MainActivity becomes a ComponentActivity so CameraX binds to the actual host lifecycle. CameraStage binds only Preview with DEFAULT_FRONT_CAMERA and COMPATIBLE PreviewView rendering; no ImageCapture, ImageAnalysis or VideoCapture use case exists.

Activation requires an explicit user action and CAMERA grant. A transient request flag and generation reject late permission/provider callbacks. The one active preview moves between the primary/fullscreen projection; external display uses a still fallback. Leaving the foreground explicitly unbinds, removes observers/views, cancels the readiness deadline and disables activation. Returning or recreating the activity does not opt in again. Missing/busy/failed cameras leave saved media unchanged, with no retry loop. Camera hardware is optional, and microphone/internet/shared-storage permissions remain absent.

Build-generated runtime notices retain dependency coordinates and the exact embedded upstream LICENSE/NOTICE text, accessible offline in My Set. APK validation inspects merged permissions and the generated asset. Run 38826875086 passed on source 4da765035dd3cbeec6e69f38581138225b706e6d: APK/instrumentation assembly, lint, permission/notices gate, native sanitizers, 201 domain assertions, browser interoperability and all 60 emulator checks; emulator results cannot establish physical orientation, camera quality or thermal behavior.

## Android dry microphone input

MicrophoneInput is composed by SessionController and owns no output, queue, playback clock or file writer. Explicit permission/foreground activation creates a transient run identity. Nonblocking mono PCM16 capture at 48 kHz runs on its own worker with bounded input buffers, a five-second no-data bound and privacy-sensitive capture on API 30+. API 29+ silenced-client state disables the run. UNPROCESSED is selected when advertised, otherwise VOICE_RECOGNITION; vendor preprocessing remains possible.

At most one 4,096-sample dry frame is analyzed at a time on a separate worker, no more often than every 125 ms. A bounded YIN difference workspace estimates 70–1,000 Hz periods after silence/nonfinite/aperiodic rejection, searching shorter periods to reject high-frequency fundamentals rather than labeling subharmonics. Immutable measurement snapshots expire after 750 ms and are read by the existing UI tick without rebuilding the stage or control panel per frame. Run-local data and identity checks retire late analysis; stop immediately removes public readings and the capture worker releases AudioRecord independently. Backgrounding, focus loss and noisy-route interruption disable input; returning requires a new explicit action.

This batch has no microphone AudioTrack, effects, recording, completed-song score or singer identification. Input estimates can contain backing music. Hardware input compatibility, acoustic quality and latency remain acceptance items. Monitoring/output route enforcement and calibrated dry scoring remain follow-ups; this input service must not become another playback owner.

## Android optional monitored output (0.7.0 candidate)

MicrophoneMonitor owns one independent AudioTrack and receives copies of the existing AudioRecord PCM blocks. It never modifies dry analysis, opens another input or owns karaoke reservations. Explicit foreground/input-active selection accepts only wired headphones/headsets, USB device/headset and analog line-out. Enumeration alone is insufficient: AudioTrack starts with zeroes and zero volume, and actual routed output must match the selection within 1.5 seconds. API 36 checks the complete routed-device list and rejects multiple outputs. A closed MonitorRouteGuard cannot resume after route loss; a new explicit action creates a new guard. Routing notifications, selected-device removal, worker checks and parent lifecycle/focus cancellation mute and retire the activation. Callback/native/hardware latency means transient physical feedback cannot be categorically excluded without equipment testing.

A bounded 8,192-frame MonitorBuffer starts at a 1,440-frame input floor and uses linear interpolation with occupancy feedback bounded to +/-0.5% consumption adjustment for independent nominal-48-kHz clocks. Overrun drops stale buffered input; underrun emits silence and re-primes. Discontinuities reset effect tails. One output worker owns reusable 480-frame blocks and fixed delay-network memory; input offers run separately with bounded synchronized copies. Nonblocking output writes have a 500 ms no-progress bound. No audio is saved or networked.

Dry/Room/Hall/Echo are first-party monitor-only effects with finite input sanitization and a +/-0.9 sample ceiling. Output volume starts at 10% per activation and clamps at 35%; these software bounds do not control external amplifier gain. Effect/volume changes cannot opt in to output. Stop immediately mutes, invalidates the run and clears queued input; the worker pauses, flushes and releases the track and its callbacks. No automated success claim is made for physical wired/USB playback, acoustic safety or low latency. Pitch correction, calibrated melody scoring, companion and official YouTube remain follow-ups.

Routing limit: before API 36, AudioRouting exposes only getRoutedDevice; this implementation can verify that single reported route but cannot categorically exclude an additional unreported output. API 36 uses the complete AudioTrack.getRoutedDevices list. The privacy/UI text discloses this limit and the monitor remains experimental/default-off. Preferred-device selection is never treated as proof of actual routing.

## Android optional pitch-following scoring (0.8.0 accepted source)

The existing bounded SMF parser now retains track index, note-channel masks, bounded track/instrument labels and real lyric evidence without changing event timing, MIDI rendering or imported bytes. MelodyTarget requires exactly one lyric-bearing or explicitly vocal/melody-labelled track with one non-drum note-bearing channel. It rejects ambiguous candidates, overlapping or incomplete note lifetimes and noncentral channel pitch bends (including bends from another track). No target is invented for MP3/WAV or unlabelled high notes. Extraction runs with MIDI preparation off-main.

SessionController remains the single owner. Each started reservation installs its distinct UUID as the playback identity and its complete immutable target. A separate opt-in creates MelodyScoreSession off-main with stale-request and identity fencing. Natural completion finalizes at most once; manual stop, replacement or preparation failure cannot publish a score. A last result retains title/singer/identity and a bounded numerical summary in session memory only. Insufficient/silent input shows no reportable numerical score.

Scoring observes the existing dry pitch estimate on the 100 ms foreground tick regardless of whether voice controls are expanded. Whole-frame/epoch fences reject data spanning start/resume/calibration/control changes. Timing uses the estimated dry-frame center, its arrival age, audible source position, source tempo and a manually adjustable -500..1,500 ms offset. This is an estimate requiring physical calibration, not measured input latency. Source-end-clamped playback rejects later frames, including the MIDI release tail. Key/tempo/calibration changes cancel and rebuild the accumulator; pause/resume and foreground transitions retire crossing frames.

SoundFontPlayback exposes an audible control fence: main-thread key/tempo requests receive identities, the audio worker records the first newly rendered frame, and only the actual playback head can acknowledge it. An old acknowledgement cannot settle a newer request. Scoring waits for that fence and a subsequent complete dry frame, rather than comparing new targets against buffered old-key/old-tempo audio. Volume alone does not reset scoring. Existing rendered source-clock ownership is unchanged.

The scorer uses binary target lookup and constant-size incremental best-confidence slot aggregation, without a growing observation array. Source slots scale with tempo (200 ms at normal pace). Half-open note windows avoid double counting boundaries; final expected coverage always includes the complete target, including unobserved endings. At least 20 matched slots and 10% coverage are needed before reporting a finite estimate. Invalid/nonfinite, low-confidence, silent, out-of-order and foreign-identity samples are rejected. Pitch accuracy and timing coverage form an informal estimate; backing music can be mistaken for a singer and no ability assessment or physical scoring accuracy is claimed. Monitoring/effects remain separate, with no recording or networking. Pitch correction, secure companion and official YouTube remain follow-ups.

Oversized optional track/instrument labels are skipped without rejecting otherwise playable MIDI. Overlong decoded labels are not truncated into explicit vocal-name evidence. Lyric/event bounds and original media bytes remain unchanged. Regression fixtures cover both oversized metadata and misleading name truncation.


Accepted Android 0.8 source `57dc0611cb54435802a4bd7d226e00983dd3c8f6` passed run [38845590252](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38845590252): APK/instrumentation assembly, lint, native sanitizers, all 328 domain assertions, browser interoperability, permission/notices gate and 110 actual API 35 emulator checks. PR #21 merged as `5e6d5dc09f4bbfed25566d6dc17dfc20fe0cf25b`. No prior assertions were removed. Physical timing/scoring accuracy remains unverified; verified download packaging is separate. See ANDROID_SCORING_CHECKPOINT.md.


## Android optional chromatic correction (0.9.0 candidate)

MicrophoneMonitor composes ChromaticPitchCorrector before its existing Dry/Room/Hall/Echo processor on the one output worker. No second capture, song queue, playback clock, file writer, native library or dependency is introduced. Off bypasses the corrector completely. The existing dry AudioRecord analysis and scoring path are unchanged. Each monitor run owns an immutable correction selection/epoch; activation requires an already verified allowed output and separate explicit user action. Stop, input loss, route loss and backgrounding retire that run and its correction selection. A fresh run starts off. A single generation-fenced readiness publication enables the UI after actual routing is verified.

The first-party processor uses two complementary Hann-weighted fractional delay taps, an 8,192-float history and a precomputed 4,097-float window at 48 kHz. Input/output block lengths remain equal, with no growing buffer or audio-worker allocation. The 4,096-frame sweep plus 480-frame minimum creates roughly 10–95.3 ms of algorithmic tap delay; unshifted fallback has a fixed 2,528-frame (52.7 ms) delay. These are mathematical buffer lengths, not measured device or total round-trip latency. Ratio and wet transitions smooth over nominal 40 ms and 10 ms time constants. Output is finite and limited to +/-0.9 before existing effects. On discontinuity or selection replacement, histories/effect tails are reset; final cleanup clears them.

Stable existing dry estimates in 70–1,000 Hz, confidence at least .85 and RMS at least .008 select a nearby equal-tempered semitone. A .6-semitone hysteresis threshold prevents boundary chatter and bounds shifts to +/-60 cents. Estimates must be no older than 300 ms and their complete 86 ms frame must follow the activation/discontinuity epoch. Invalid, stale, silent or uncertain estimates relax to equally delayed unshifted input. Correction is chromatic, not song-melody/scale guidance or formant-preserving processing; it can color the voice and produce artifacts. Physical voice quality, latency and feedback remain unverified. No audio is saved or uploaded.

Algorithm references (concepts only; no third-party source copied): Miller Puckette, The Theory and Technique of Electronic Music, pitch-shifter section, https://msp.ucsd.edu/techniques/v0.05/book-html/node115.html ; Android low-latency guidance, https://developer.android.com/games/sdk/oboe/low-latency-audio and https://developer.android.com/ndk/guides/audio/audio-latency . This change retains the existing AudioTrack worker; it does not claim Oboe/AAudio implementation or measured low latency.


## Hosted Android 0.9 acceptance

Source `4f48e051dcd815e878205eda43bf9331591e279d` passed run [38881735518](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38881735518): all 364 domain assertions and 116 actual API 35 emulator checks, APK/instrumentation assembly, lint, native ASan/UBSan checks, browser interoperability and merged permission/offline-notices checks. PR #25 merged as `2851a0b81f43698a99b036b8dd28aa25c0d495eb`. Existing assertions remain enabled. The accepted output is artifact `12038226827`; checksum-verified packaging must preserve these APK bytes before changing public downloads. Physical corrected voice quality, artifacts, feedback and total latency remain unverified. Secure companion and official YouTube remain queued. This entry supersedes candidate hosted-pending statements above.


## Production Android 0.9 verified

Website source `95855ff18e9c1758e53a4315bf460b9f29ee876b` passed static run `38882633589` and READY preview `dpl_ESS6EucV9dVBs8x7y9tKzjGSqxK8`. All 15 preview pages/assets matched source with CSP, and real browser inspection confirmed Android 0.9, the 364/116 checks, source/download identity and setup navigation. PR #27 merged as `4c8e8066dc9f480de1922410628990eb788df4d5`. Production deployment `dpl_4xB8tKDdT61KhkSMnTRLx21HS4de` is READY on https://kanta-public.vercel.app; all 15 production pages/assets match validated bytes with CSP. The browser independently confirmed the public Android 0.9 page and bundle `12038451005` from packaging run `38882359866`. Original private reader/styles/notices and unsigned iOS application bytes are unchanged.

Post-deploy verification caught a competing production deployment returning 404. The public alias was restored to the verified deployment, and the persistent Vercel project settings now explicitly use root `apps/web`, output `.`, no framework, and empty install/build commands. The corrected public alias and all 15 responses were verified after restoration. Deployment READY alone is insufficient: check the public alias, content and security headers before declaring completion.

Pitch correction implementation, acceptance, verified packaging and website publication are complete. The next software task is secure companion control, followed by official YouTube playback. Physical voice quality/timbre/artifacts/feedback/latency, scoring calibration, camera/external-display acceptance and iOS production signing remain open. This checkpoint changes Markdown only and requires no additional native build.


## Android optional companion boundary

Android 0.10 adds foreground-only, explicit private-IPv4 companion hosting/client control through the existing SessionController/FIFO. The actual Java 17/Views, storage, imports, audio engines, CameraX and platform separation remain unchanged. INTERNET is added for private Java sockets; no new dependency, cloud service, discovery broadcast or iOS protocol change is introduced. KCP1 uses a 128-bit random expiring pairing secret, mutually authenticated HMAC handshake, HKDF direction keys and ordered AES-GCM records. Commands are limited to bounded search/queue metadata, reserve/cancel and Play/Pause/Next; sensors and file access are excluded. A single worker owns socket work; main-Looper mutations check active foreground run and cancellation. Background/Stop revokes immediately, with no automatic reconnect. See ANDROID_COMPANION_CHECKPOINT.md for exact framing, bounds, replay scope, no-forward-secrecy limitation and acceptance.

Candidate core compilation and all 443 domain assertions passed (79 new companion checks); browser interoperability and Swift layout guards pass. Hosted build/lint and actual emulator socket/queue/lifecycle checks remain pending. Physical two-device networking, iOS compatibility and independent security review remain open. Public downloads remain accepted 0.9 until separately verified packaging/publication.


## Hosted Android 0.10 acceptance

Final source `d777334eec73996116fdf2c01d97ac73a3a53958` passed run `38885716463`: all 448 domain assertions (84 companion cases), 139 actual API 35 emulator checks, APK/instrumentation assembly, lint, native ASan/UBSan, captured browser interoperability and the CAMERA/RECORD_AUDIO/INTERNET permission/notices gate. Initial source also passed the complete suite; the final source adds bounded peer JSON nesting and strict UTF-8 checks. An independent Python cryptography client verified the JCA handshake, HKDF, framing and both direction keys. PR #28 merged as `51b57e0052c8f01d08fac04f12cccae06b189ad5`. Accepted original artifact is `12039807731`. No existing assertion was disabled. Physical two-device networking, iOS interoperability, forward secrecy and independent security review remain open; Android acceptance does not establish those properties. Public downloads remain verified 0.9 until checksum-verified packaging and website publication.


## Production Android 0.10 verified

Website source `f2bd2b517f0a9059084b2604cddcdc9b2cf1e66c` passed hosted run `38886674120`; preview `dpl_2bG6QWnkFVbkT7YLVuwyxBgLAr13` is READY. All fifteen preview routes/assets returned 200 and retained CSP. Fourteen responses matched source exactly; the homepage matched source plus Vercel's appended preview feedback-script tag, which is blocked by the unchanged CSP. Browser navigation confirmed Android 0.10 source d777334e, the 448/139 counts, accepted download identity and companion setup guide.

PR #30 merged as `0a11058b2d70182b2956d57b2f320edbe19762ee`. Production deployment `dpl_H9fF42EU9QobF613s7Y4d7Rvfkbt` is READY on https://kanta-public.vercel.app, using explicit `apps/web` root, `.` output and empty install/build commands. All fifteen public responses returned 200, matched validated source exactly and retained CSP; production has no appended feedback tag. Browser inspection of canonical `/android` independently confirmed version 0.10.0, source d777334e, run 38885716463 and Android bundle 12040160062. Both platform download pages use successful packaging run 38886416353; iOS bundle 12038684955 preserves the unchanged unsigned IPA. The 26,974,938-byte Android APK SHA-256 is 7743d1994c500b472f86829ea041307303f0f908d0e5505b788c35ca76026d3d. The downloaded packaging manifest archive digest was checked independently.

Android companion implementation, automated acceptance, original-byte packaging and website publication are complete. This is an experimental Android-to-Android milestone, not cross-platform or independently audited security acceptance. The recovered iOS plaintext protocol remains incompatible; encrypted iOS interoperability is the next companion milestone, followed by the queued official YouTube integration. Physical two-device networking, audio quality/feedback/latency, scoring calibration, camera/external displays and production app signing remain open. Final handover updates are Markdown only and introduce no runtime, workflow, dependency or asset change.

## iOS encrypted Android-host controller candidate

`CompanionProtocol` uses system CryptoKit to implement the accepted KCP1 client handshake, HKDF-SHA256 labels and AES-256-GCM ordered records. `EncryptedCompanionClient` uses Network.framework for explicit RFC1918 IPv4/45731 connections, serial request/response ownership on MainActor, generation fencing after every network suspension, 5-second connect/handshake and 15-second exchange deadlines, 5-second polling, 512-request and 10-minute limits. Stop, closing the screen or scene inactivity cancels the socket, clears snapshots/channel state and requires explicit new pairing. No background operation, DNS/discovery, reconnect or plaintext fallback is added. The 128-bit host code is entered through SecureField, cleared after submission and never persisted. CryptoKit manages key storage; best-effort variable release is not a claim of guaranteed memory zeroization.

`EncryptedCompanionView`, reached from Control's remote screen, controls an Android host's existing SessionController/FIFO with search (10 results), reserve, cancellation and Play/Pause/Next. It never owns another queue or requests sensors, files, SFX, effects or full catalogs. Typed replies are capped before JSON decoding (32 KiB and eight levels), correlated to request UUID and validated for field/list bounds and distinct row IDs. Reservation UUIDs come from the host. Busy taps are ignored instead of racing exchanges; repeated deliberate reservations get different request identities. A lost response is not retried across sessions.

This milestone is iPhone/iPad controller → Android host only. The recovered Bonjour/PIN iOS host remains separate, plaintext, explicitly labeled legacy and incompatible. It is not upgraded by this client. Android public 0.10 bytes/website and current unsigned iOS download remain unchanged pending changed-source acceptance and separately verified packaging. Protocol lacks forward secrecy and independent review; physical cross-device behavior remains unverified. Tests include independent fixed Python vectors, adversarial records and a real TCP Swift client exchange with the unmodified production Java codec and a synthetic peer queue. That peer is not Android runtime/device acceptance.


## Fullscreen stage presentation-gate wiring (PR #36 repair)

The Host-owned `KantaPresentationGate` must be passed through `SingingFullscreenPresentation` and `FullscreenKaraokePlayerView` into the fullscreen `KantaMainPlayerView`. Both stage projections share the same modal owner; neither creates a replacement gate. The iOS source guard checks this complete dependency path to prevent missing-argument simulator compilation regressions. The earlier PR #36 run 39022026489 failed simulator compilation because fullscreen omitted the new required dependency; this patch is a build repair candidate, not runtime acceptance. Both iPhone and iPad navigation walkthroughs remain required before merge or release.

## Fullscreen modal ownership repair

The background SingingModalPresenter now retains a weak identity for only the karaoke player it creates and dismisses that player directly. UIKit's presentedViewController may refer to an ancestor's presentation, so it must never be used as permission to dismiss unrelated SwiftUI menus or sheets. The active-playback walkthrough verifies that More remains visible across redraws, then enters/exits the library without changing playback. UI harness waits for rendered controls and observed menu presentation, with bounded retries only for the idempotent open-menu action. This repair changes app presentation only; package/core source, FIFO ownership and audio semantics remain unchanged. Changed-source simulator execution remains required. Reference: https://developer.apple.com/documentation/uikit/uiviewcontroller/presentedviewcontroller .

### Persistent KANTA route presentation (iOS PR #31)

The root stage and sheet destination routes share one state-owned SwiftUI sheet. The More menu is a persistent native list instead of a transient confirmation dialog, so frequent now-playing updates cannot tear down route presentation or collide with a second sheet presenter. Dedicated fullscreen UIKit still dismisses only its own player. The encrypted Android companion form places IP/code inputs before setup prose and validates private-address and 32-hex-character requirements in the UI and again at the network client boundary. This UI state does not own transport, queue, media or secrets persisted outside the in-memory form; invalid public addresses are rejected before socket allocation. Device/UI acceptance remains pending for this candidate.

### PR #31 sheet and remote navigation ownership repair

After [iOS run 38923007543](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38923007543), the prior route-as-optional sheet binding was replaced by two explicit root presentation states: a stable `isSheetPresented` Boolean that owns sheet lifetime and a `sheetRoute` that selects its content. The More stage action opens the sheet; switching between destinations does not dismiss it; Done closes it; onDismiss resets the selected destination. This isolates playback-driven HostView refreshes from sheet dismissal.

RemoteView's encrypted `NavigationLink` now uses the native navigation tap without a competing simultaneous gesture. Its existing onDisappear cleans up the legacy Bonjour/PIN connection (and clears the typed PIN); `EncryptedCompanionView` retains independent foreground-only KCP1 session revocation. The Android FIFO/session authority and media playback are unaffected. iPhone/iPad simulator acceptance is required before the change is considered verified.


> **Design authority:** [Engineering architecture contract](ENGINEERING_ARCHITECTURE_CONTRACT.md) defines mandatory ownership, presentation-state and planned-feature boundaries. Historical status sections below are not proof of current release acceptance.


## iOS presentation ownership — candidate repair (2026-10-17)

Root, Host and Stage now route SwiftUI sheet requests through one main-actor gate. Requests are retained by owner while another sheet is transitioning and activated only after its `onDismiss` callback; duplicate queued requests from the same owner are coalesced. This addresses the one-tap Search race after Library dismissal without changing the playback controller or FIFO. UIKit fullscreen now acquires and releases through the shared gate in the PR #36 candidate; current-head simulator walkthroughs remain required. Simulator walkthroughs on this source head are required before this candidate is considered verified.


## iOS presentation-gate candidate (2026-10-17)

The shared main-actor gate now serializes navigation, Host and Stage sheets, and UIKit fullscreen requests. Fullscreen Search/Queue are modeled as a child stage owner: dismissing the child restores fullscreen ownership, while dismissing the UIKit player releases ownership only from its actual completion callback. Requests arriving during dismissal remain queued by owner. The presenter dismisses only the player controller it created. This is still a candidate until changed-source compilation and complete iPhone/iPad walkthroughs pass.

### Noninterruptive session-save handoff (iOS PR #36 candidate)

Natural completion may leave an optional pending favorites save while Library is presented. The stage must not automatically replace Library dismissal with the session-save sheet: an explicit `Save session` action opens that Host-owned sheet. A pending save must not suppress observation of subsequent playback; later finished sessions are accumulated until Save/Don't Save resolves the decision. The Host presentation gate still serializes the explicit sheet request. Both iPhone and iPad runtime walkthroughs must verify the first Search tap and the deferred save action before this is accepted.


### Fullscreen entry from CONTROL

The CONTROL sheet keeps its fullscreen action in a bottom safe-area inset outside the scrolling List, where it remains reachable while detailed controls are expanded. The stage-preview affordance and CONTROL inset action both call the Host-owned transition, which marks the Host sheet as dismissing and queues fullscreen through the shared presentation gate. UI walkthroughs target the stable CONTROL identifier, wait for the fullscreen child sheet and player to leave the accessibility tree, then verify stage Search is hittable after exit.

## CONTROL fullscreen accessibility (PR #36)

CONTROL presents its fullscreen action in a bottom safe-area inset outside the scrolling `List`. The action retains accessibility identifier `controls.openFullscreen` and delegates to `HostView.openFullscreenPlayer()`, preserving the shared presentation gate and UIKit dismissal callback. Keep this entry point persistent on iPhone and iPad; do not put the only fullscreen action in a conditional list section or navigation toolbar. This change requires fresh iPhone/iPad walkthrough evidence and does not by itself establish that fullscreen dismissal or subsequent Search is correct.

Fullscreen lifecycle hardening (PR #36): the UIKit presenter completes gate dismissal before updating the SwiftUI presentation binding, allowing the underlying Stage to request Search without inheriting stale fullscreen ownership. A cancelled queued acquisition must enter dismissal instead of presenting an obsolete fullscreen player. Both behaviors require iPhone/iPad runtime validation; source changes alone are not acceptance evidence.


### Fullscreen teardown waits for its child sheet

When a fullscreen Search or Queue sheet is open, its HostViewController remains the fullscreen player's presented controller until UIKit completes the sheet dismissal. A fullscreen Exit request is held by the shared presentation gate while the child owns the stage. The Exit action updates the binding and directly calls `SingingModalPresenter.requestDismissal()`. The presenter must not dismiss or release its player until the gate's stage `onDismiss` restores fullscreen ownership; it observes that gate handoff and resumes teardown only then. This prevents the child-sheet dismissal callback from being mistaken for completion of the parent fullscreen dismissal. The iPhone/iPad walkthrough closes the child, exits fullscreen, waits for the unique exit control to disappear, and then verifies the Host Search can open.


## PR #51 foreground player lifetime repair — October 18, 2026

Online preview lifetime is separate from local transport/FIFO. Each selection has a distinct UUID; native owners retire the previous WebView before replacement and on foreground loss. Teardown is terminal and errors cannot affect a newer preview. iOS uses a per-sheet MainActor owner; Android MainActivity synchronously releases before Dialog replacement and blocks inactive opens. Audio-focus arbitration remains open. See `YOUTUBE_LIFECYCLE_CHECKPOINT.md` for code, tests and unvalidated gates.

## Planned KANTA Library Folder auto-recognition (owner approved 2026-10-18)

**Status: requirement only; implementation and tests pending.** This is queued behind the active PR #51 independent YouTube player reliability batch; PR #52 remains the separate UI restoration effort. Do not claim this is shipped or silently introduce it into the playback PR.

When a user explicitly chooses a parent folder (named KANTA or otherwise), recognize known child directory names case-insensitively: `songs/` (.mid, .midi, .kar and supported karaoke audio), `sound/` (.sf2), `videos/` (ordinary media videos), `mp3s/` (ordinary audio), `bgv/` (stage background videos), `bgphoto/` (stage background images), `assets/` (optional user-provided visuals), and `idx/` (optional legacy index files). Missing folders are allowed; unrecognized directories must not cause failure. Do not treat index files as trusted executable content or as authoritative over validated metadata.

Use each platform's permissioned document/folder access APIs, with no assumption of unrestricted filesystem paths. Reuse existing import, media validation, catalog, and atomic persistence owners rather than creating a second library or queue. Classify by recognized folder plus verified supported media type; reject mismatches safely, skip unsupported items, preserve original bytes, and avoid duplicates through stable content identity. Provide progress, bounded traversal, cancellation, partial-failure reporting and idempotent retry. Keep single-file and ordinary-folder imports working. User media is not bundled or uploaded. iOS/Android parity is the acceptance target, subject to platform permission restrictions.

Before implementation, inspect current Android SAF tree traversal and iOS bookmarked library imports; write tests for partial folder layouts, mixed-case names, duplicate files, unsupported media, symlink/provider cycles, cancellation, interrupted import, and reimport. Update user-facing Library instructions and storage documentation when actual code lands. No CI or runtime acceptance is implied by this specification.

### Unified KANTA library with SongHub-compatible import (owner revision 2026-10-18)

Use **one KANTA-managed library and catalog**, not separate SongHub and KANTA libraries. The importer accepts both the native KANTA folder layout and compatible legacy SongHub folder trees (including a parent folder called SongHub) through a normalization layer. Legacy names are **input aliases only**; the user-facing brand, internal storage ownership, database and exports remain KANTA. No SongHub code, branding or proprietary media is bundled.

Normalize `songs` to karaoke-song candidates, `sound` to SF2 candidates, `videos` and `mp3s` to general media, `bgv` to stage video, `bgphoto` to stage photos, `assets` to optional visuals, and `idx` to untrusted optional legacy index input. Directory naming alone never establishes playable media or trust. Preserve source provenance as metadata, deduplicate bytes without collapsing intentional queue reservations, and leave original files untouched. Importing a SongHub tree must never overwrite existing KANTA selections, sound bank, queue, or backgrounds without explicit user choice. Folder structures can be incomplete and filenames may have mixed case.

The single import pipeline must scan with provider-safe limits, preview categorized results, allow explicit confirmation, validate formats and licensing expectations, commit atomically where practical, and report skipped/failed items. Keep existing one-file and arbitrary-folder imports. Test identical media imported from both layouts, invalid/mismatched extensions, duplicate catalog identities, and legacy `idx` files that are missing, corrupt, or contain unsafe paths. Platform storage APIs and existing catalog ownership remain authoritative. This is a **planned feature**, not a claim of working SongHub compatibility.

## Audio synthesis engine decision (owner approved 2026-10-18)

**FluidSynth is the chosen MIDI/SoundFont synthesis engine for KANTA on iOS and Android.** Do not integrate or license Un4seen BASS/BASSMIDI/BASS_FX as part of the planned SongHub compatibility work. Keep the synthesis adapter isolated behind the platform playback contract; retain native audio decoders for non-MIDI formats and the separate YouTube player. This decision does not establish that FluidSynth is already integrated on both platforms; audit the actual implementation before reporting that claim. Verify SF2 loading, MIDI/KAR events, lyrics synchronization, tempo and key controls, audio focus/session ownership, resource cleanup, and parity in platform tests. Review LGPL compliance for binary distribution, including relinking/replacement requirements and notices, before shipping. SongHub shubidx/shubblob parsing is an independent read-only interoperability task and does not require BASS. No proprietary SongHub APK libraries or bundled media may be copied into KANTA.


## Android online preview follows authoritative playback publication — October 18, 2026

Baseline: PR #51 `5c1b0f4`, whose reconstruction and focused iPhone smoke checks passed. The online/local overlap guard previously ran only after primary Play/Next button callbacks. Encrypted companion commands bypass those callbacks, and backend start acknowledgement is asynchronous, so those paths could leave a visible online player alive after local playback started.

MainActivity now reconciles the preview at the start of the existing SessionController listener's render callback. All published local starts/resumes retire the online instance; primary buttons again issue only controller commands. The preview owner does not pause local transport, consume reservations or introduce a playback queue. Replacement/dismissal guards remain unchanged. This closes a controller-observation gap, not general OS audio-focus arbitration or a guarantee of zero overlapping audio frames. SFX/intermission, iOS native runtime and physical audio/session acceptance remain separate.

The Android instrumentation uses real KCP1 socket commands to reserve/start a local starter while a preview is open, waits for backend start acknowledgement, verifies native preview retirement, pauses, explicitly reopens the preview, resumes remotely and verifies retirement again. Reservation alone must preserve the preview, normal FIFO consumption remains checked, and local Stop must not reopen online playback. New-head APK/lint/emulator acceptance is pending. No website, dependency, permission, persistence or remote-protocol change.


## Android local/online audio coordination candidate — October 18, 2026

SessionController owns a transient online-audio UUID and native-retirement callback; MainActivity owns the matching visible WebView. There is no second queue, decoder or local transport command. Claim rejects active/preparing karaoke or enabled microphone/monitoring, stops auxiliary effects/intermission, disables automatic intermission restart and abandons idle native audio focus. Every native audio-focus acquisition retires the online player synchronously first. Automatic intermission evaluation is suppressed while online owns audio; explicit effects, microphone, local start/resume and intermission actions yield online ownership. Matched release ignores stale lifetimes; background revokes before cleanup. The native player keeps its own official playback controls and user-gesture requirement.

This Android candidate does not establish iOS audio-session parity, WebView media/OS interruption behavior or acoustic silence timing. See YOUTUBE_LIFECYCLE_CHECKPOINT.md for regression coverage and pending hosted/device gates.


## iOS native online-player component test boundary — October 18, 2026

`KantaNativeTests` is an iOS simulator application-hosted XCTest bundle. It compiles the unchanged `YouTubeEmbeddedPlayerView.swift` directly into its own test module, with the existing core package dependency, so production release apps do not need testability enabled or public testing hooks. It exercises real WKWebView/owner/coordinator lifetimes without loading remote media. This is component-native coverage, not SwiftUI presentation, real video/audio, consent, account or advertisement acceptance. The existing smoke workflow selects these three tests plus the unchanged focused navigation test; explicit named-pass verification rejects missing execution or restarted-run false greens.


## iOS audio handoff candidate — October 18, 2026

Based on PR #51 head `5d25f62993c8b003099c345b5387172a1fdf5af7`. AppServices constructs one MainActor KaraokeAudioOwnership alongside the existing LocalKaraokeController and a shared intermission owner. The coordinator owns only an ephemeral online UUID/retirement callback and foreground/microphone eligibility; it owns no queue, local transport, persistence or YouTube commands.

Online claims acquire the controller's existing cancellation-aware operation gate, reject actual playing state, stop SFX, suspend both idle rendering engines without Pause/Stop or position changes, then atomically recheck microphone/foreground/monitor eligibility on MainActor before stopping intermission and configuring the shared playback session. Local load/resume/playing seek and SFX retire the online native owner before native output. Remote commands and natural advance use these same controller paths. Capture announces activity synchronously before its permission await and releases it on denial/failure/stop; native output preserves capture's playAndRecord category. Session errors fail visibly. Foreground loss retires online ownership; returning does not reopen it.

The sheet cancels pending claims on close and rechecks its request UUID/foreground/ownership after suspension. The representable also checks ownership before attachment/load, preventing a revoked pre-mount session from constructing an active player. Stale release cannot clear a replacement. Intermission stops immediately, invalidates fades, and requires explicit re-enabling; automatic snapshot updates cannot reclaim online audio. Source files and reservation/playback identities are unchanged.

Regression coverage adds controller preparation/cancellation/microphone races, paused song/FIFO identity, explicit Next/SFX handoff, stale release, foreground, session failure and both rendering routes. Native smoke adds real WKWebView retirement-before-session configuration and synthetic AVAudioPlayer intermission handoff/restart; the named-pass verifier now requires all five native cases plus navigation. The test target compiles the two production components directly; no app testability, dependency or permission change.

Local layout, presentation, starter resources/generation and whitespace checks passed. Hosted Swift compilation, package execution, unsigned simulator app build and expanded native/navigation smoke are pending on the resulting source. Physical audio timing, real embeds/ads, interruptions, privacy and full iPhone/iPad release acceptance remain open. No zero-overlap timing, signing, merge, deployment or release is claimed.


### Accepted hosted iOS audio coordination — October 18

Source `6f7a0ece9e42cc500ce4f52cc414c58e5e11b58e` passed reconstruction 39112387766 (178 Swift tests, 54 tooling tests, unsigned simulator app build and Android validation) and smoke 39112387680 (all five native cases plus focused navigation, explicit named-pass gate). The iOS session/auxiliary implementation and hosted regression gap is closed. Actual SwiftUI online-sheet/media flow and physical embed/interruption/privacy/ads acceptance remain open; no zero-overlap timing, merge, signing or release is implied. See docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md for evidence and next steps.


The PR #51 online-sheet UI candidate adds two actual host-navigation/foreground/paused-starter flows, retaining separate native/component coverage. Stable control identifiers and explicit link-keyboard dismissal do not change audio/FIFO ownership. Smoke requires eight named passes; full release walkthrough requires all seven UI cases. See YOUTUBE_LIFECYCLE_CHECKPOINT.md for scope, source and pending hosted/device evidence.


## PR #51 HTTPS companion candidate — October 18, 2026

Both platforms now have explicit Host / Companion Remote entry. BrowserRemoteHost (iOS) and BrowserCompanion → BrowserRemoteServer (Android) serve independent bundled copies of the same first-party HTML/CSS/JS on the selected private interface, HTTPS port 45732. The browser interface is also embedded inside each app. Assets are checked byte-for-byte across platforms, without a native cross-platform build/runtime dependency.

TLS uses a fresh, one-day, self-signed RSA-2048/SHA-256 certificate with an IP subject alternative name per hosting session. No private key or certificate is bundled or uploaded. iOS uses Security/Network.framework and device-only Keychain entries cleaned on stop/next initialization; Android uses in-memory JCA/JSSE. Native clients require an exact host SHA-256 certificate fingerprint: iOS WebKit server-trust delegate, Android per-request HttpsURLConnection trust manager for both resources and a bounded WebView command bridge. No accept-all SSL handler or plaintext downgrade is installed. Browser guests must verify the actual certificate fingerprint out of band against the host before entering its pairing code; browser warning acceptance alone proves no identity. Real Safari/Chrome trust/bootstrap usability and independent security review remain gates.

The separate KBR1 capability contract uses HTTPS POST with bounded KCP1-style binary command fields; it does not change KCP1 framing or make the old iOS plaintext host encrypted. A five-minute random 128-bit pairing code grants a random 256-bit per-guest bearer token. Up to four guests, ten-minute sessions, three rejected pairing attempts, thirty-minute foreground hosting, strict Host/Origin checks, no CORS, 8 KiB headers, 1 KiB request bodies, 16 KiB replies and no-store/CSP/frame/sensor restrictions bound the surface. Each guest pins completed operation identities and input bytes without eviction, capped at 512 replies/1 MiB; conflicting identity reuse is rejected. Disconnect/revocation never stops host playback. Backgrounding revokes hosting and clients; neither automatically reconnects.

Adapters dispatch into the existing LocalKaraokeController and SessionController. Search returns at most ten metadata results; queue projections include at most ten reservations, with stable distinct identities. Play/Pause/Next/Stop, reserve and cancellation target only the local engine. No media/files, microphone/camera access, online transport or second FIFO is exposed. Unsupported YouTube, Previous/seek, Favorites, Host Cam and FX controls remain labelled unavailable. See COMPANION_BROWSER_CHECKPOINT.md for source/tests and acceptance limits.


## Native Keychain test access boundary — October 18, 2026

`LocalNetworkControl` owns ephemeral browser-host session identity installation/removal. The Release-configured iOS app-hosted `KantaNativeTests` target accesses only the existing identity operations through the module's `@_spi(Testing)` surface; ordinary clients must use the host protocol, not the test SPI. This does not change session identity lifetime, transport security, Keychain accessibility or application signing. The native certificate/private-key round trip and deletion remain mandatory simulator execution gates, separate from Swift package compilation and physical-device acceptance.

## iOS embedded Stage presentation — candidate (2026-10-24)

Main `ca23b87871feb5b384c1613d8ba9d75457575456` still fails iPad embedded Search/keypad acceptance after the Host SwiftUI sheet was consolidated; [walkthrough #40081230815](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40081230815) shows the Host stage and Search control but no accessible `stage.keypad.0` after the tap. The internal presentation failure path is not logged, so the low-level cause remains unconfirmed.

The current repair candidate gives embedded Stage Search/Queue a stable UIKit presenter (`KantaEmbeddedStageSheetPresentation`) mounted in HostView's background. Host CONTROL/session-save/Favorites retain their SwiftUI sheet; fullscreen Search/Queue retain their own child sheet. All requests continue through `KantaPresentationGate` and reuse the existing `HostViewModel`; no playback, queue, media or persistence ownership changes. The candidate is not accepted until the source guard, reconstruction and full iPhone/iPad walkthrough pass on the exact same PR head.


## Embedded Stage presenter lifecycle contract (2026-10-24)

Embedded Search/Queue are owned by `KantaStageSheetModalPresenter`, not a competing SwiftUI `.sheet`. Its UIKit representable must occupy the Host bounds while remaining non-interactive, retry presentation when layout/attachment changes, and recover a stale owned controller instead of silently leaving the shared presentation gate held. Blocked presentation states are logged with the `KANTA_STAGE_PRESENTATION` prefix. The exact-head iPhone and iPad walkthroughs are required before acceptance; source checks alone do not prove runtime behavior.


## Window-level iPad tap diagnostics — October 25, 2026

The temporary Stage action-row probe is diagnostic instrumentation only: its recognizer must not cancel application touches, its logged touch point and hit rectangle must both be expressed in window coordinates, and its recognizer must be detached with the representable lifecycle. Keep this probe separate from presentation ownership; it reports where a touch lands but does not dispatch Search/Queue requests or change the shared presentation gate. The compiler-compatibility and cleanup contract is enforced by tools/check-ios-presentation-contract.py. See docs/TESTING.md and docs/CI_FAILURE_REPAIR_LEDGER.md for the failed-run evidence and exact-SHA acceptance requirements.

## Stage visual hit-testing invariant — October 25, 2026

The stage scene and camera preview are decorative surfaces, not interaction owners. In candidate walkthrough [#40122872536](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40122872536), the window tap probe recorded a touch at the center of the accessibility-reported Search button ({668, 148.5}) resolving to an oversized `_UIGraphicsView` frame ({32, 117}, 770 × 433) that overlapped the action row. The keypad never appeared. The same stage-background source is present in main commit [`c63116e`](https://github.com/usermalik20-del/Kanta-reborn/commit/c63116e6c2fc39e1e8971b4da89f4befcfab7b0b), whose current walkthrough [#40126621040](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621040) reproduces the four iPad keypad failures. Keep both the scenic/video and camera-preview branches non-interactive with `.allowsHitTesting(false)`; the stage controls and shared presentation gate remain the only owners of their respective actions. The source-contract checker enforces this invariant.
