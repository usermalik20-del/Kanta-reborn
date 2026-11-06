# KANTA Android reconstruction preview

## Persistent stage and lower control panel — local candidate

The primary `StagePresenterImpl` tagged `kanta.stage.primary` remains attached and visible while navigation changes the utility content below it. Now Playing no longer creates a competing `StageSurface` or takes camera ownership. Menus, background selection and playback controls project `SessionController` state; they do not start a new playback attempt or alter reservations simply by opening. Fullscreen explicitly suspends the embedded visual projection and returns camera ownership to the primary presenter on dismissal.

Acceptance requires a fresh APK build and instrumentation on portrait and landscape displays: keep the stage visible above each panel, preview background changes in place, retain playback identity and audible-clock progress, dismiss panels with predictive/system Back, and verify camera/video handoff through fullscreen. Local source/static checks are not emulator or physical-device acceptance.

## Large SoundFont RAM ceiling — local candidate

TinySoundFont tracked allocations are capped at **1536 MiB (1.5 GiB) per worker**, raised from 128 MiB. PCM16 banks expand to float samples (approximately twice their sample-data bytes), with additional tables, channels and voices. This ceiling does not allocate RAM in advance, change Android heap/process limits, or guarantee that a bank fits a low-memory phone/TV. Allocation failures remain failures and malformed-bank checks remain enabled; the 128-voice bound is unchanged. Large-bank memory-pressure and sustained playback acceptance require a capable physical Android device. No SoundFont asset or source media is modified by this change.

## Compact Workspace back-navigation contract

The primary stage stays attached and visible for Home and all utility destinations. Navigation changes only the lower menu/control area; it must not hide the live preview to avoid duplicate controls. Android system/predictive Back dismisses the active utility first through the lifecycle-bound AndroidX `OnBackPressedDispatcher` callback. Keep stage visibility, lower-panel placement and Back behavior covered by `RuntimeChecks.workspaceBackNavigationChecks` and `tools/check-android-workspace-contract.py`.

## Companion Remote candidate in PR #51 — October 18, 2026

Owner-approved requirement: the supplied KANTA remote design opens inside this same app when Companion Remote is selected, with no separate remote app. Both iOS and Android must support it, plus paired browser guests entering the host's LAN address. Host/source queue ownership, authentication and explicit sensor permissions remain authoritative. The owner moved implementation into PR #51. HTTPS hosting and a shared locally served browser/built-in remote are now candidate source; native/browser/device acceptance is pending and this is not released. Expanded unsupported controls remain explicitly unavailable. See the [shared roadmap and original design reference](../../docs/COMPANION_REMOTE_ROADMAP.md).


New native Java/Android Views source reconstructed from documented behavior and original first-party assets. This is not recovered original Android source or a complete feature-parity release.

Implemented in source: original starter songs and scenic images; app-private MIDI/KAR/MP3/WAV import with a visible My Set song list and direct Reserve actions; native SF2 MIDI synthesis and platform MP3/WAV playback; persisted SoundFont inventory with an explicit active-bank selector; folder-tree import with cancellation; tempo/pitch/volume controls; source-timed highlighted lyric fragments, adjustable type and optional Realm lights; synthesized/custom party pads and opt-in between-song music; one singer/FIFO reservation queue; saved playlists and played-session Favorites; starter remove/restore; keypad/RES and optional device-provider voice search; fullscreen/TV stage; explicit CSV/private encrypted Songbook export; offline privacy/rights notices and audio-focus handling.

New personal-media source: byte-preserving private photo/video backgrounds, sampled EXIF-oriented photos, muted looping video, persisted selection and stable stage/navigation lifecycle. Accepted personal-media source passed run 38825170997 (201 domain and 47 emulator checks).

Remaining: physical scoring calibration, physical monitor/pitch-correction acceptance, companion networking and official YouTube playback. Optional dry pitch-following estimates are implemented in the accepted 0.8 source. Optional camera preview passed run 38826875086 with 201 domain and 60 emulator checks. Loop and Realm now expose their implemented audio and animated-stage controls. No YouTube player or ad blocker is implemented.

Hosted validation passed Gradle APK assembly, Android lint, all 46 domain regression checks and browser interoperability for the encrypted Songbook export. Debug APK and lint reports are retained in CI artifacts for seven days. This validated baseline is merged as PR #1. Native media source 87e5aa21 passed cloud run 38805798316: NDK APK/instrumentation assembly, lint, 167 core assertions, native sanitizer/malformed-bank checks, browser interoperability and 13 real API 35 emulator checks. Physical-device playback acceptance remains open.

Build: AGP 8.13.2, verified Gradle 8.13 wrapper, Java 17, minSdk 26, targetSdk 36. The new reconstruction package is `ph.kanta.reborn`; the original Android package/signing identity has not been recovered. No signing keys are bundled. TinySoundFont is included under MIT; see THIRD_PARTY_NOTICES.md. Native builds use NDK 27.2.12479018 and CMake 3.22.1. The pinned vendor header is fork `usermalik20-del/tinysoundfont` revision `a272fe30` (release `kanta-vendor-a272fe30`, hash-pinned in `app/src/main/cpp/vendor/PROVENANCE.json`): hardening patch plus the quality engine (Catmull-Rom interpolation default, ramped low-pass, SF2 modulators, chorus/reverb stages, SF3 loader pad fix, `tsf_playback.h` wrapper).

See ../../docs/ARCHITECTURE.md and ../../docs/CODEBASE_CONTEXT_PACKET.md.

Import bounds: MIDI/KAR files have no fixed file-size, event-count or duration caps (structural SMF 0/1 rules still apply; individual text events are capped at 16 MiB and files above 2 GB are not mapped); SF2 has no fixed file-size cap, but import preflights reported size, rechecks available storage while streaming, and preserves a 64 MiB free-space reserve before strict native structure/sample validation; folder scan 10,000 entries, 1,024 directories and depth 16. Negative coarse sample offsets (unsupported safely by the pinned loader) and unsupported/oversized banks are rejected; SF3/Ogg banks are supported through the pinned stb_vorbis decoder with post-decode sample accounting (`synth->sampleTotal`), a synthetic SF3 fixture in the native regression, and a render-tail guard that rejects banks whose region windows come within one float of the decoded buffer end (the Catmull-Rom interpolator reads two floats ahead). The pinned engine implements SF2 modulators and chorus/reverb stages (release `kanta-vendor-a272fe30`; `PROVENANCE.json` pins the revision and file hashes), but KANTA has not wired those engine paths to app controls yet and MIDI poly (key) pressure remains untracked by the engine wrapper. Engine-level validation lives in the fork's own CI; KANTA's native regression compiles the pinned header with `-Wall -Wextra -Werror`. Folder selection makes private copies; it does not watch or edit the source. Bank changes apply to subsequent playback. KAR lyric text encoding is detected (UTF-8, Shift_JIS, GBK, Big5, CP949 with windows-1252 fallback); @T/@A/@C metadata feeds title/artist/copyright, @P pause codes shift lyric cues and \ echo lines are dimmed on the stage. Imported songs and SoundFont banks can be removed from My Set, missing private copies are repaired on load or re-import, and stage search includes the active library. The transport Previous button restarts the current song: seek-to-top is supported for MIDI/KAR synthesis and MP3/WAV playback. Key/tempo adjustments made while a song is playing are remembered for that song and re-applied the next time it plays; Reset in the Pitch & Tempo screen clears the per-song memory (or the app-wide default when the song has no override). My Set lists imported songs and offers Reserve, lists every retained SoundFont with an Active indicator and Use action, and persists bank selection across restart. Paired ShubBlob/kantablob and index-container import is available from My Set by selecting the index and then its paired blob. Single-file container selection remains rejected so a blob is never mistaken for a standalone song; the file-backed MKMD path now addresses records through the paired index's trailing offset table, with a bounded scan fallback for indexes without a usable table; exact-head runtime validation remains required.

Party pads are first-party synthesized WAVs with at most four concurrent SoundPool voices. Import a short MP3/WAV into My Set to assign a custom pad (8 MiB / 30 seconds maximum, also bounded below SoundPool’s 1 MiB decoded limit). Between-song music selects an imported MP3/WAV, starts only when explicitly enabled and the karaoke queue is idle, and stops for karaoke, focus loss or leaving the foreground. Enabling is not restored automatically. Party volume is independent of karaoke volume.

Custom pads must also decode within a 900 KiB PCM allowance (with space below SoundPool's 1 MiB limit); high-quality stereo clips need to be shorter. Rejected files remain playable in My Set, and are never truncated or rewritten by KANTA. See https://developer.android.com/reference/android/media/SoundPool .

The 0.5.0 camera source adds explicitly enabled front-camera preview through pinned CameraX/ComponentActivity dependencies. Preview only: no camera capture or recording. The 0.6.0 microphone input batch adds a separate opt-in RECORD_AUDIO permission; internet permission remains absent. Leaving KANTA disables activation; primary/fullscreen share one binding while TV uses a still fallback. Hardware orientation and quality remain separate acceptance work. Hosted camera checks passed on the accepted 0.5.0 source. Runtime library notices are generated from original embedded resources and available offline in My Set.

The 0.6.0 input batch adds an explicit microphone switch in Control, ephemeral dry input level and bounded monophonic pitch estimation. It does not play the microphone through any output, save recordings, produce song scores or apply effects. Capture stops on backgrounding/focus loss and does not restart automatically. Off-thread analysis drops frames when busy; interrupted, silent or unreliable input is not assigned a pitch. AudioRecord uses UNPROCESSED when advertised, otherwise VOICE_RECOGNITION; hardware preprocessing and backing-music contamination remain possible. Hosted source c961cb3b passed run 38839639544: 233 domain and 78 actual emulator checks, APK/instrumentation build, lint, native sanitizer, browser and permission/notices gates. Physical input quality/latency remains open.

The 0.7.0 monitoring batch adds a separate explicit wired/USB/analog-line output choice with muted route verification, API 36 multi-output rejection, terminal route-loss/disconnect handling and no automatic reconnect. Speaker/Bluetooth/unknown routes are excluded. Capture PCM is copied into a bounded interpolating clock-drift buffer; optional Dry/Room/Hall/Echo effects operate only on monitor output. Volume starts at 10% and is bounded to 35%; equipment gain remains the user's responsibility. Output stops with input/background/focus loss. Physical routing, feedback, sound quality and round-trip latency remain unverified. Pitch correction and target-based song scores remain unfinished. Hosted source 1ddee241 passed run 38842165486: all 268 domain and 90 emulator checks, APK/instrumentation build, lint, native sanitizers, browser and permission/notices gates. The emulator cannot validate physical wired/USB playback. The website continues to offer accepted 0.6.0 until separate verified packaging.

The accepted 0.8.0 source adds separately enabled pitch-following estimates for a single identified monophonic MIDI melody. Silent/unreliable input does not produce a reportable score. Natural completions belong to distinct reservation UUIDs; stop/replace/failure cannot fabricate a result. Complete target notes remain in coverage, key/tempo/timing changes clear observations, and buffered controls must become audible before new frames are accepted. The timing offset is manual and requires device calibration; backing music can affect estimates. MP3/WAV without a melody reference, ambiguous/polyphonic/bent targets, pitch correction and companion/YouTube remain unavailable. Run 38845590252 passed 328 domain and 110 actual API 35 emulator checks, APK/instrumentation build, lint, native sanitizers, browser interoperability and permission/notices gates. The public site continues to offer accepted 0.7.0 until separate verified packaging.


The 0.9.0 candidate adds a separate experimental chromatic pitch-correction switch after wired/USB monitoring is verified. It nudges stable dry estimates toward a nearby semitone before monitor effects; stale/unreliable input stays unshifted with additional delay. It is not melody/scale-aware or formant-preserving. Each monitor activation starts off, and lifecycle/route loss retires the choice. Dry scoring and imported media remain unchanged. Source 4f48e051 passed hosted run 38881735518 with all 364 domain assertions, 116 API 35 emulator checks, APK/instrumentation build, lint, native sanitizers, browser interoperability and permission/notices checks. Voice quality, artifacts, feedback and physical round-trip latency remain open. Verified packaging run 38882359866 preserves the accepted APK bytes; website source 95855ff1 passed static run 38882633589 and preview/browser inspection; production 4c8e8066 is live with all 15 public responses matching validated source and CSP.


The 0.10 candidate adds Android-to-Android companion control in Control > Companion control. Hosting and joining are off by default, use a trusted private IPv4 network and require the full random 32-character code. One connection can search bounded metadata, reserve/cancel turns and send Play/Pause/Next through the one FIFO owner. Stop and backgrounding revoke the session without automatic reconnect. INTERNET is declared for these local sockets; no cloud service or media upload is added. This protocol is incompatible with the old iOS remote and has no forward secrecy or independent audit. See ../../docs/ANDROID_COMPANION_CHECKPOINT.md. All 443 domain assertions pass; hosted build/lint/emulator checks and physical two-device acceptance remain pending. Downloads remain on verified 0.9.


## Production Android 0.10 verified

Website source `f2bd2b517f0a9059084b2604cddcdc9b2cf1e66c` passed hosted run `38886674120`; preview `dpl_2bG6QWnkFVbkT7YLVuwyxBgLAr13` is READY. All fifteen preview routes/assets returned 200 and retained CSP. Fourteen responses matched source exactly; the homepage matched source plus Vercel's appended preview feedback-script tag, which is blocked by the unchanged CSP. Browser navigation confirmed Android 0.10 source d777334e, the 448/139 counts, accepted download identity and companion setup guide.

PR #30 merged as `0a11058b2d70182b2956d57b2f320edbe19762ee`. Production deployment `dpl_H9fF42EU9QobF613s7Y4d7Rvfkbt` is READY on https://kanta-public.vercel.app, using explicit `apps/web` root, `.` output and empty install/build commands. All fifteen public responses returned 200, matched validated source exactly and retained CSP; production has no appended feedback tag. Browser inspection of canonical `/android` independently confirmed version 0.10.0, source d777334e, run 38885716463 and Android bundle 12040160062. Both platform download pages use successful packaging run 38886416353; iOS bundle 12038684955 preserves the unchanged unsigned IPA. The 26,974,938-byte Android APK SHA-256 is 7743d1994c500b472f86829ea041307303f0f908d0e5505b788c35ca76026d3d. The downloaded packaging manifest archive digest was checked independently.

Android companion implementation, automated acceptance, original-byte packaging and website publication are complete. This is an experimental companion milestone with no independently audited security acceptance. The recovered Bonjour/PIN iOS path remains plaintext and incompatible with Android, but the encrypted KCP1 path now has an iOS client that controls an Android 0.10 host with wire-level tests against a Java peer; physical two-device pairing is still unaccepted. The queued official YouTube integration follows. Physical two-device networking, audio quality/feedback/latency, scoring calibration, camera/external displays and production app signing remain open. Final handover updates are Markdown only and introduce no runtime, workflow, dependency or asset change.



## Pitch Settings UI — Controls accessory integration



The **Controls** accessory now includes three pitch-related entries:



| Button | Action |

| --- | --- |

| **Pitch Settings** | Opens dialog with sensitivity slider, presets, note picker, custom Hz input |

| **Pitch Practice** | Opens existing Pitch Meter Game accessory (`ACCESSORY_PITCH_GAME`) |

| **Voice Coach** | Opens existing Learning Assistant accessory (`ACCESSORY_LEARNING_ASSISTANT`) |



### Pitch Settings dialog



- **Voice Coach Sensitivity** — SeekBar (0.1–3.0, default 1.0) with live value display; presets: Beginner (0.5), Normal (1.0), Pro (2.0). Scales `LearningAssistant` advice thresholds: higher = stricter.

- **Default Target Pitch** — Note spinner (C4–B5, 24 semitones) showing Hz; custom Hz input (20–2000 Hz) with validation.

- All values persist via `SettingsStore` keys:

  - `pitch.assistantSensitivity` (float, default 1.0)

  - `pitch.targetHz` (float, default 440.0)

  - `pitch.targetName` (String, default "A4")



### Integration



- `LearningAssistant.setSensitivity()` scales perfect/good/close thresholds.

- `PitchGameOverlay.startGame()` falls back to stored `targetHz` when no song key signature.

- `SessionController.practiceTargetHz()` still provides song-aware target from MIDI key signature (`0x59` meta).

The dialog needs `ph.kanta.reborn.settings.SettingsStore` imported into `MainActivity`; `LearningAssistantOverlay` and `PitchGameOverlay` read the same store from the `stage` package. `AchievementSystem` moved to `ph.kanta.reborn.feature.achievement` so `core/` stays framework-free and `compileCoreRegression` keeps its empty classpath.

## PR #51 foreground player lifetime repair — October 18, 2026

The online preview uses a distinct transient UUID per selection and idempotent terminal WebView release. Replacement detaches/destroys the previous player before mounting another. Activity pause clears online and link-entry dialogs; foreground return requires explicit reopen. Main-frame errors/renderer loss retire the preview without local transport/FIFO commands. Domain and native runtime checks cover replace/repeat/background/reopen/stale callbacks; changed-head cloud validation is pending. Audio-focus arbitration and real-device embeds/ads remain open. See ../../docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md.


### Online and local audio coordination candidate

Opening an online video requires paused local karaoke and disabled microphone/monitoring. It stops effects and between-song music. Starting local audio, an effect, microphone input or between-song music closes the online video; closing it does not restart those sounds automatically. The local song and queue are preserved. This PR #51 candidate requires fresh Android build/runtime validation and physical-device interruption/YouTube acceptance; see ../../docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md.

## KANTA Karaoke candidate — legacy songbook interoperability

KANTA Karaoke is the working name for bringing the karaoke loop the owner built as a
separate karaoke app into KANTA. The Android side starts with the part that can be done cleanly:
read-only interoperability with the legacy indexed songbook container.

Added in this candidate:

* `ph.kanta.reborn.core.songbook` — a framework-free mirror of the existing iOS
  `LegacyCompatibility` layer (`ShubIndexProbe`, `ShubBlobOffsetCorrelator`,
  `ShubBlobSpanIndex`, `ShubBlobPayloadProbe`, `IndexedContainerCompatibility`). It
  describes the legacy index structure, the trailing offset table and the payload
  spans, and classifies payload prefixes. It never decodes or executes a payload and
  never becomes a second source of truth for playback or the queue.
* `ShubCompatibilityRegressionTests` — synthetic-fixture domain checks that run in the
  existing `coreRegression` task on a server JVM. No song catalogue is committed.
* `tools/check-product-branding.py` — a static guard wired into
  `android-validation.yml`: the legacy product name may not reappear anywhere
  under `apps/`, the legacy container name may not reach the public website or an
  Android type outside the compatibility package, and the Android application id stays
  `ph.kanta.reborn`.

Not implemented and not claimed: legacy payload decoding, the proprietary native
audio/scoring component, the legacy bundled media, the legacy Smart Link transport, the
legacy phone remote application, and the legacy screens themselves. The engine
decision in `docs/ARCHITECTURE.md` stands: KANTA keeps its own synthesis and scoring
path and does not integrate BASS. Evidence and open items are recorded in
`replica/meg-format.md` and `replica/architecture.md`.

## Lyric-stage animation and fun countdown candidate

`stage/LyricStage.java` and the fullscreen `StageView` keep the existing scrolling window, but the active line now fills continuously across each source-timed MIDI lyric fragment instead of switching a whole word at a fragment boundary. `core/LyricWordAnimation.java` holds the framework-free interpolation and pitch-energy math: a fragment boundary remains the hard source event, the span between boundaries is interpolated for a smooth fill, and only the fragment currently being sung receives a small per-letter wave plus orbit sparks from the single microphone owner's `PitchSource` estimate. Pitch is visual energy only; it never changes cue timing, the playback clock or scoring, and the stage still renders when the microphone is off.

`stage/CountdownStage.java` now runs `Ready ⏱️ → 1 → 2 → 3 → 4 → Kanta 🎤` with a gradient ring, pop text, orbit dots and a final confetti burst, plus a reduced-motion path that shows the same labels without the motion. `SettingsStore.getCountdownLength()` defaults to four, and `MainActivity.playWithCountdown()` starts a new reservation through the countdown before calling the single playback owner; resuming an already-prepared song or requesting playback with no reservation skips it.

Validation state: `LyricWordAnimationRegressionTests` is wired into the existing `coreRegression` JavaExec task and covers fragment interpolation, active-fragment selection and pitch-energy decay. The authoring environment has no working local JVM (`getcpu(2) system call not supported by kernel`), so the Gradle APK/lint/emulator and device acceptance for this candidate are still required.

## Bluetooth headset monitoring candidate

The optional voice-output route accepts Bluetooth SCO and BLE headsets next to wired headphones, USB audio and analog/aux line-out. Headset-class devices are safe to monitor because the microphone belongs to the same worn device; speaker-class routes (built-in speaker, Bluetooth A2DP speakers, HDMI/ARC) stay blocked because the microphone hears them. A Bluetooth activation asks Android for the communication route (API 31 and newer) and plays the effect chain as voice-communication audio, then releases that request when monitoring stops. Bluetooth sound is mono call quality with higher delay, link negotiation takes one to three seconds, and platforms older than Android 12 are not offered a Bluetooth output at all. The Bluetooth device's own microphone is not captured by the app yet: microphone input still comes from the phone. Physical routing, feedback, sound quality and round-trip latency remain unverified on real hardware.

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



## Multi-file import durability

Multi-file song and SoundFont imports stage metadata until the complete batch succeeds. The repository performs one atomic metadata commit after all items finish; cancellation or commit failure restores the prior catalog and removes staged media. Regression coverage reloads the durable catalog after cancellation and verifies that only the original starter entries remain, with every referenced media file present. Single-file imports retain their immediate persistence behavior. Repository progress is delivered through the main-Looper `SessionController` dispatcher; Activity-scoped listeners detach on stop, rebind while a library import remains active, and clear after completion or folder-scan failure. Runtime instrumentation checks main-thread progress/completion delivery and the failure completion path.

Batch import returns `ImportBatchResult` with imported, duplicate and failed counts plus bounded per-file reasons. `RuntimeChecks` asserts the mixed SAF fixture's imported and failed counts against the durable catalog result.

## Android emulator acceptance runner — 2026-11-01

The hosted Android gate installs the debug APK and instrumentation APK only after the emulator has booted and acquired a private IPv4 address. A missing address is a terminal prerequisite failure; it must not contain or skip the acceptance sequence. After the prerequisite guard, the runner installs both APKs, executes `RuntimeChecks`, captures the stage and camera evidence, and copies the named UI walkthrough captures into the repository-root `files/` artifact directory. The CI workflow uploads that directory. The runner control-flow contract is covered by `tools/tests/test_android_runtime_runner.py` and is executed in Android validation. The repair is pending exact-head hosted acceptance.

The runner treats all nine reference-led destination screenshots plus the two dedicated My Set/SoundFont screenshots as required evidence. It retrieves each file from the app-private directory into `files/` and fails if a capture is absent or empty; it never silently skips a missing screenshot.

## Stage presenter and camera runtime ownership — 2026-11-01

The primary `kanta.stage.primary` view is `StagePresenterImpl`; fullscreen and external-display output use `StageSurface`. `CameraStage` supports both target types. The primary presenter's `BackgroundStage` owns its `PreviewView`, while fullscreen `StageSurface` receives a dedicated mounted preview and stream-state updates. Runtime instrumentation validates video on fullscreen `StageSurface` and validates camera start/transfer/return on the correct primary/fullscreen targets. Camera shutdown unbinds CameraX and explicitly detaches the preview from its owning stage; the primary presenter restores the background selected before camera opt-in. Visibility requires the active CAMERA source, an attached preview and CameraX `STREAMING`, preventing stale stream-state values from passing teardown acceptance.

## KANTA design-system UI — 2026-11-01

The Android application uses a dedicated presentation layer under `ph.kanta.reborn.ui` implementing
the KANTA dark entertainment design system against the supplied UI concept:

- **Design tokens** (`KantaTheme`): `#050B18` background, `#081426` elevated, `#0D1930` surface,
  `#12203A` surface elevated, `#203459` border, `#29D9FF`/`#2589FF`/`#7657FF`/`#D946EF` primaries,
  `#F7F9FF` text
- **Screens**: Home (branding, hero, search, 2x3 feature grid, quick play, bottom nav), Now Playing /
  Lyrics (stage-dominant with large lyrics, progress, transport, key/tempo), Search (source tabs,
  media rows with Sing action), Queue (count, items, Add Songs CTA), Pitch & Tempo (key/tempo
  cards), Mic & Voice (mic toggle, input level, reverb/echo, voice FX, coach), Visuals
  (Background/Camera/Effects tabs with thumbnail grid), Fullscreen Stage (clean stage with lyrics)
- **Consistent destinations**: shared KantaScreenHeader app bars, themed vector icons and pill-style
  source tabs unify goal menus, search, queue, controls and playback surfaces with Home
- **Components**: KantaCard, KantaButton, KantaIconButton, KantaImageButton, KantaScreenHeader,
  KantaSearchBar, KantaProgressBar, KantaStepper, KantaSectionHeader, KantaTabSelector,
  KantaSongArtwork, KantaMediaRow, KantaTransportControls

This is a presentation-only refactor. All playback, queue, persistence, sensor, companion and
playback logic remains owned by `SessionController` and `StagePresenterImpl`; no backend behavior is
duplicated or replaced. The existing workspace/StagePresenterImpl/transport/toolbar/body structure
is preserved; the new screens are presented as an overlay via `contentContainer`.

`MainActivity`'s legacy destination controls use the same KantaTheme surfaces and accent colors.
Android runtime checks verify category-card icons, accessible image back controls, and vector-based
Now Playing transport controls; hosted emulator acceptance remains a separate validation gate.

The browser remote (`apps/android/app/src/main/assets/companion/`) is restyled with the same KANTA
design system: responsive 3-column desktop layout collapsing below 1000px, cyan active states, and
honest disabled state for controls not in the companion protocol allowlist. The wire protocol and
pairing flow are unchanged.

See [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md) and [docs/TESTING.md](../../docs/TESTING.md).


## Browser companion guest pairing capacity — 2026-11-02

The HTTPS browser host permits up to four active guests. A correct pairing code rejected only because all guest slots are occupied does not consume the three-incorrect-code lockout budget. Once a guest leaves or expires while the five-minute pairing window remains open, another guest can pair. Three incorrect pairing codes still lock pairing until hosting restarts. The real TLS `BrowserRemoteRegressionTests` covers capacity denial, recovery after Leave, and the separate incorrect-code lockout through the existing `coreRegression` JavaExec suite.


## Reference-led native UI integration — validation candidate

The Home dashboard and dedicated Search, Queue, Pitch & Tempo, Mic & Voice, Visuals and Now Playing screens are mounted as native Android Views destinations. They delegate playback, FIFO reservations, SoundFont selection, imports, microphone/monitor state and stage changes to the existing `SessionController` and repository owners; they do not introduce a second queue or playback state machine. The supplied artwork and drawable mapping are listed in [KANTA Android adaptive assets](../../docs/KANTA_ANDROID_ADAPTIVE_ASSETS.md).

The Android runtime walkthrough captures `home_dashboard.png`, `home_dashboard_restored.png`, `search_screen.png`, `queue_screen.png`, `pitch_tempo_screen.png`, `visuals_screen.png`, `mic_voice_screen.png`, `fullscreen_stage.png` and `now_playing_screen.png`. It also retains `my-set-imported-library.png` and `my-set-active-soundfont.png` to prove imported-song visibility and active SoundFont selection. Each destination capture must prove that its tagged view is actually visible before taking the screenshot. These are CI acceptance artifacts, not a claim of physical-device acceptance or a published APK.

## PR #134 merge reconciliation — 2026-11-02

The reference-led Android UI and companion changes were reconciled onto current `main`. `MainActivity` remains the composition boundary; `HomeScreen` and destination screens delegate playback, queue, import, microphone and stage state to existing owners. Exact-head Android runtime validation is required after reconciliation.

The reconciled PR retains current-main `SessionController` SoundFont inventory, active-bank selection and import-progress APIs; the reference-led screens remain presentation-only adapters.

The karaoke-stage candidate initializes the reference-led Home dashboard on launch; saved navigation is applied only after the dashboard default is established.

## Three-group navigation candidate

The current Android UI candidate uses five primary destinations: **Home | Sing | Performance | Experience | More**. Sing is the doorway to KANTA, YouTube and Local discovery, My Set/playlists, Favorites, Recent and Queue. Performance owns the Now Playing/lyrics surface plus Voice, Key & Tempo and Singing Coach. Experience owns Background, Camera, Effects, Scenic, Party Mode and Display. More contains advanced settings and tools. The grouping is presentation-only; `SessionController`, `StagePresenterImpl`, catalog persistence and the microphone owner remain authoritative.

Search uses an embedded KANTA keypad toggled from the search row. Numeric and compact letter layouts share a fixed-height footprint; the search field suppresses the system IME so the device keyboard cannot cover results.

## Supplied UI and SoundFont assets

The UI upgrade pack's multicolor VectorDrawables are used directly by Home and Voice navigation/action surfaces; no tint or color filter is applied to those resources. The two supplied procedural banks are bundled under `app/src/main/assets/starter/`, installed into the existing app-private media inventory on first load, and remain selectable through My Set without changing the authoritative playback owner. Their declared SHA-256 values are recorded in `starter/SOUNDFONT_LICENSES.txt`; runtime distribution follows the restricted KANTA OpenStage Procedural Asset License 1.0. The OpenStage candidate lock/audit files are documentation only until individual sample licenses are independently verified.


## Paired ShubBlob import candidate

My Set now exposes `Import legacy idx + blob`: select the paired index first, then the payload blob. The file-backed importer reads the paired index's trailing offset table to address MKMD records deterministically, decrypts and inflates validated records, maps them to index titles, and persists materialized MIDI/WAV/MP3 files through the existing LibraryRepository. Indexes without a usable offset table use the bounded scan fallback. A standalone `.idx` or `.blob` selection remains rejected. This is not yet a device-accepted feature; exact-head Android build/runtime, cancellation/storage tests and physical playback remain required.

## Android OTA self-update (branch feat/android-ota-update)

The Android app includes a framework-free OTA update subsystem:

- **Core contract** (`ph.kanta.reborn.core.update`, pure JDK, compiled by `compileCoreRegression`/`coreRegression`; no Android imports): `UpdateJson` (strict bounded JSON: rejects duplicate keys, raw control chars, unpaired surrogates, leading zeros, over-nesting; max 256 KiB feed), `UpdateManifest` (validates feed shape "kanta-update-feed" version 1, channel "stable-reconstruction", publishedAt ISO-8601 UTC, sourceCommit 40-hex SHA, and android build requirements: versionCode >= 1, versionName shape, minSdk must be at least 26, apkUrl policy, apkBytes 1..256 MiB, apkSha256 64-hex, optional signingCertSha256 64-hex, notes <= 1024 chars, mandatory boolean), `UpdatePolicy` (pinned feed URL `https://kanta-public.vercel.app/updates.json`; HTTPS-only durable-artifact host allowlist: `https://github.com/usermalik20-del/Kanta-reborn/releases/download/<tag>/<file>` canonical URLs plus redirect targets `release-assets.githubusercontent.com` and `objects.githubusercontent.com`; refuses GitHub Actions artifact URLs, http, userinfo, query/fragment on the canonical host; path case-sensitive), `UpdateDigest` (SHA-256 hex + ".part" staging file naming), `UpdateDecision` (pure decision matrix: `NOTHING_PUBLISHED`, `DEVICE_TOO_OLD`, `ARTIFACT_NOT_DURABLE`, `OLDER_OR_SAME_VERSION_PUBLISHED`, `DIGEST_MISMATCH`, `DOWNLOAD_REFUSED`, `APPLY`).

- **App layer** (`ph.kanta.reborn.update`): `AppUpdateManager` (single-worker executor; feed check via `HttpURLConnection` with strict UTF-8 decode; resumable Range download into app-private `files/update/` staging dir reusing `.part` files with HTTP 200/206/416 handling and 256 MiB ceiling; SHA-256 verification; atomic rename to `kanta-update-<sha12>.apk`; `cancel()` and `close()`; Listener protocol `onState`/`onProgress`/`onReady`) and `UpdatePackageInspector` (installed `versionCode` via `androidx.core` `PackageInfoCompat`, optional signing-cert SHA-256 extraction, `canRequestPackageInstalls()`, `ACTION_MANAGE_UNKNOWN_APP_SOURCES` intent, `FileProvider`-based `ACTION_VIEW` install intent).

- **AndroidManifest.xml**: added `android.permission.REQUEST_INSTALL_PACKAGES` and a non-exported `androidx.core.content.FileProvider` (authority `${applicationId}.fileprovider`, `grantUriPermissions`, service `res/xml/update_paths.xml` exposing only `files-path update/`).

- **app/build.gradle**: added `implementation 'androidx.core:core:1.13.1'`.

- **MainActivity**: new Controls > "App update" entry (`controlsAccessory`) opening a programmatic dialog (check button, status text, horizontal progress bar, install button); manager created lazily with installed version code and platform API level; `onStop` cancels in-flight work, `onDestroy` closes worker; diagnostics report gained an "update" line with last status.

- **Feed generation** (`tools/prepare-update-feed.py`): generates `apps/web/updates.json` from `apps/web/downloads.json` (published android source SHA), `verified-downloads/android/build-info.json` (APK file name, 64-hex sha256, bytes) and `git show <source>:apps/android/app/build.gradle` (versionCode, versionName, minSdk); deterministic durable `apkUrl` `v<version>-preview-<sha8>/KANTA-Android-preview-<sha8>.apk`; refuses (`FeedError`) and never writes on any missing/malformed input; prints refusal to stderr and exits 1.

- **Tests**: `tools/tests/test_android_update_contract.py` (9 unittest cases: schema, deterministic URL, URL policy mirror, source selection, malformed-input refusal, build identity, atomic write, no-write-on-refusal). JVM suite `apps/android/app/src/test/java/ph/kanta/reborn/core/update/UpdateContractRegressionTests.java` registered in `CoreRegressionTests` (runs in `coreRegression` task).

- **CI integration** (`.github/workflows/platform-download-publishing.yml`): new Android-only step "Ensure a durable release asset for this Android build" creates (or `--clobber` updates) a GitHub Release tag `v<versionName-without-suffix>-preview-<sha8>` with the built APK as its asset; the existing publish step also runs `tools/prepare-update-feed.py` for Android and stages `apps/web/updates.json` into the downloads commit.

- **Privacy**: "Optional Android self-update" disclosure added to all four privacy files (`apps/android/app/src/main/assets/privacy.txt`, `apps/web/privacy.html`, `apps/ios/PRIVACY.md`, `apps/ios/PublicSite/privacy.html`).

**Honesty notes:**
- The feed is **live**: `apps/web/updates.json` is regenerated by the publisher after every successful Android build on `main`; the current entry is release `v0.10.0-preview-1f49555c`, source `1f49555c`, served through the `vercel.json` routing table, and the app reads it from Controls → App update.
- `signingCertSha256` is emitted from `apksigner` metadata for the exact signed APK and is checked against the app manifest contract.
- No local JVM/Gradle build was possible in this environment (JVM cannot start on iSH: `getcpu(2)` unsupported) and no APK, emulator, runtime or physical-device acceptance ran. Java correctness is guarded by CI (`coreRegression` + `android-validation`) only.
- The GitHub Release asset creation makes every post-merge Android build a public release asset automatically; this is a publication side effect, not a release claim.
- App update is user-initiated (off by default), HTTPS-only, SHA-256 verified, staging is app-private; install needs the separate Android "Install unknown apps" permission.


### OTA versioning and signing gate

CI passes `kantaVersionCode` from its monotonic workflow allocation. Release metadata comes from the signed release APK (`aapt`/`apksigner`); publication verifies the pinned prior APK digest and requires the candidate to match the owner-approved signer fingerprint. Release signing uses repository Actions secrets only on trusted main runs; PR builds do not receive the key. This is an intentional signer transition: APKs published before the new key cannot update in place and may need uninstall/reinstall, losing private app data. Physical-device acceptance of the new signer remains unverified; see `../../docs/OTA_UPGRADE_ACCEPTANCE.md`.
