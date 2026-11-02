# KANTA Android reconstruction preview

## Compact Workspace back-navigation contract

The persistent stage stays attached while Search, Queue, Visuals or Controls is open. Android system/predictive back dismisses that active accessory first through the lifecycle-bound AndroidX `OnBackPressedDispatcher` callback; the callback is disabled when the workspace is stage-only so normal navigation remains available. Keep this behavior covered by `RuntimeChecks.workspaceBackNavigationChecks` and `tools/check-android-workspace-contract.py`.

## Companion Remote candidate in PR #51 — October 18, 2026

Owner-approved requirement: the supplied KANTA remote design opens inside this same app when Companion Remote is selected, with no separate remote app. Both iOS and Android must support it, plus paired browser guests entering the host's LAN address. Host/source queue ownership, authentication and explicit sensor permissions remain authoritative. The owner moved implementation into PR #51. HTTPS hosting and a shared locally served browser/built-in remote are now candidate source; native/browser/device acceptance is pending and this is not released. Expanded unsupported controls remain explicitly unavailable. See the [shared roadmap and original design reference](../../docs/COMPANION_REMOTE_ROADMAP.md).


New native Java/Android Views source reconstructed from documented behavior and original first-party assets. This is not recovered original Android source or a complete feature-parity release.

Implemented in source: original starter songs and scenic images; app-private MIDI/KAR/MP3/WAV import; native SF2 MIDI synthesis and platform MP3/WAV playback; folder-tree import with cancellation; tempo/pitch/volume controls; source-timed highlighted lyric fragments, adjustable type and optional Realm lights; synthesized/custom party pads and opt-in between-song music; one singer/FIFO reservation queue; saved playlists and played-session Favorites; starter remove/restore; keypad/RES and optional device-provider voice search; fullscreen/TV stage; explicit CSV/private encrypted Songbook export; offline privacy/rights notices and audio-focus handling.

New personal-media source: byte-preserving private photo/video backgrounds, sampled EXIF-oriented photos, muted looping video, persisted selection and stable stage/navigation lifecycle. Accepted personal-media source passed run 38825170997 (201 domain and 47 emulator checks).

Remaining: physical scoring calibration, physical monitor/pitch-correction acceptance, companion networking and official YouTube playback. Optional dry pitch-following estimates are implemented in the accepted 0.8 source. Optional camera preview passed run 38826875086 with 201 domain and 60 emulator checks. Loop and Realm now expose their implemented audio and animated-stage controls. No YouTube player or ad blocker is implemented.

Hosted validation passed Gradle APK assembly, Android lint, all 46 domain regression checks and browser interoperability for the encrypted Songbook export. Debug APK and lint reports are retained in CI artifacts for seven days. This validated baseline is merged as PR #1. Native media source 87e5aa21 passed cloud run 38805798316: NDK APK/instrumentation assembly, lint, 167 core assertions, native sanitizer/malformed-bank checks, browser interoperability and 13 real API 35 emulator checks. Physical-device playback acceptance remains open.

Build: AGP 8.13.2, verified Gradle 8.13 wrapper, Java 17, minSdk 26, targetSdk 36. The new reconstruction package is `ph.kanta.reborn`; the original Android package/signing identity has not been recovered. No signing keys are bundled. TinySoundFont is included under MIT; see THIRD_PARTY_NOTICES.md. Native builds use NDK 27.2.12479018 and CMake 3.22.1.

See ../../docs/ARCHITECTURE.md and ../../docs/CODEBASE_CONTEXT_PACKET.md.

Import bounds: MIDI 16 MiB / 250,000 events / six hours; SF2 has no fixed 32 MiB file-size cap, but import preflights reported size, rechecks available storage while streaming, and preserves a 64 MiB free-space reserve before strict native structure/sample validation; folder scan 10,000 entries, 1,024 directories and depth 16. SF3, negative coarse sample offsets (unsupported safely by the pinned loader), and unsupported/oversized banks are rejected. MIDI poly/channel pressure, SoundFont modulators and effects are not implemented by the selected renderer. Folder selection makes private copies; it does not watch or edit the source. Bank changes apply to subsequent playback.

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

Android companion implementation, automated acceptance, original-byte packaging and website publication are complete. This is an experimental Android-to-Android milestone, not cross-platform or independently audited security acceptance. The recovered iOS plaintext protocol remains incompatible; encrypted iOS interoperability is the next companion milestone, followed by the queued official YouTube integration. Physical two-device networking, audio quality/feedback/latency, scoring calibration, camera/external displays and production app signing remain open. Final handover updates are Markdown only and introduce no runtime, workflow, dependency or asset change.



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
  `reconstruction-validation.yml`: the legacy product name may not reappear anywhere
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

## Multi-file import durability

Multi-file song and SoundFont imports stage metadata until the complete batch succeeds. The repository performs one atomic metadata commit after all items finish; cancellation or commit failure restores the prior catalog and removes staged media. Regression coverage reloads the durable catalog after cancellation and verifies that only the original starter entries remain, with every referenced media file present. Single-file imports retain their immediate persistence behavior.

## Android emulator acceptance runner — 2026-11-01

The hosted Android gate installs the debug APK and instrumentation APK only after the emulator has booted and acquired a private IPv4 address. A missing address is a terminal prerequisite failure; it must not contain or skip the acceptance sequence. After the prerequisite guard, the runner installs both APKs, executes `RuntimeChecks`, captures the stage and camera evidence, and copies the named UI walkthrough captures into the repository-root `files/` artifact directory. The CI workflow uploads that directory. The runner control-flow contract is covered by `tools/tests/test_android_runtime_runner.py` and is executed in Android validation. The repair is pending exact-head hosted acceptance.

The runner treats all eight named UI walkthrough screenshots as required evidence. It retrieves each file from the app-private directory into `files/` and fails if a capture is absent or empty; it never silently skips a missing screenshot.

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
- **Components**: KantaCard, KantaButton, KantaIconButton, KantaSearchBar, KantaProgressBar,
  KantaStepper, KantaSectionHeader, KantaTabSelector, KantaSongArtwork, KantaMediaRow,
  KantaTransportControls

This is a presentation-only refactor. All playback, queue, persistence, sensor, companion and
playback logic remains owned by `SessionController` and `StagePresenterImpl`; no backend behavior is
duplicated or replaced. The existing workspace/StagePresenterImpl/transport/toolbar/body structure
is preserved; the new screens are presented as an overlay via `contentContainer`.

The browser remote (`apps/android/app/src/main/assets/companion/`) is restyled with the same KANTA
design system: responsive 3-column desktop layout collapsing below 1000px, cyan active states, and
honest disabled state for controls not in the companion protocol allowlist. The wire protocol and
pairing flow are unchanged.

See [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md) and [docs/TESTING.md](../../docs/TESTING.md).
