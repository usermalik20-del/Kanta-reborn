## Stage search strip, typing fix, results with singers (2026-11-27)

The app-header search field was removed as the Stage tab never shows the app header;
the search affordance now lives on the Stage itself. A transparent, single-line strip
sits on the stage's bottom line in line with the Controls anchor (tag
kanta.stage.search). It shows a soft visible hint ("Search songs, artists, or enter
code") and, while typing, the in-progress entry; the entry auto-clears 5 s after the
last keystroke (scheduleStageSearchClear). Tapping it opens the dashboard search
(openStageSearch) - it is a trigger, never a second input, and never shows the system
keyboard; the dashboard search field plus the inline keypad remain the only typing
surface. Search results now include the singer when known (code · title · artist),
the Title · Singer stage header is on by default (SettingsStore lyric.showTitle
default true) and also renders on the embedded stage's no-lyrics placeholder branch,
and the Intermission mini-stage preview no longer occupies the Photo/Video grid tabs.
Intermission message typing is fixed: while the interlude message field holds focus,
session publishes refresh only the live preview instead of rebuilding the page
(intermissionMessageFocused guard in render()), so the IME session survives every
keystroke.

Files changed:
- apps/android/app/src/main/java/ph/kanta/reborn/MainActivity.java - stage search
  strip in addStageChrome, auto-clear + sync helpers, intermission focus guard,
  header search removal.
- apps/android/app/src/main/java/ph/kanta/reborn/ui/HomeScreen.java - results show
  code · title · artist.
- apps/android/app/src/main/java/ph/kanta/reborn/ui/KantaVisualsScreen.java -
  preview only on Lyrics/Interlude tabs.
- apps/android/app/src/main/java/ph/kanta/reborn/settings/SettingsStore.java -
  lyric.showTitle defaults on.
- apps/android/app/src/main/java/ph/kanta/reborn/stage/LyricStage.java - title
  header on the no-lyrics branch.
- apps/android/app/src/main/res/values/strings.xml - stage_search_hint.

## Embedded renderer receives the persisted style (2026-11-26)

`StagePresenterImpl.refresh()` pushes a `StageStyle` value snapshot into
`LyricStage` on every session publish, exactly the way the fullscreen/PiP
`StageView` reads the session directly. The `songTitleHeader(Song)` helper
("Title · Singer") is shared by all renderers, and the presenter hands the
vocal-countdown window to `LyricStage.setCountdownFade(startMs, durationMs)`
so the renderer owns the interlude fade clock; both countdown cancel paths
reset it. `StageView` and `LyricStage` both apply the fixed-row cross-fade
(previous sung pair dissolves in place while the successor fades in at the
same anchor) and the interlude fade across the countdown. The embedded
marquee keeps its seconds-based time base, matching the fullscreen stage.

`MainActivity` builds the upgraded Lyrics/Interlude editors (font chips via
`addLyricsFontChips`, styling toggles, swatches, pills, overlay switches);
every control writes through `SessionController` accessors into
`SettingsStore` (keys under `lyric.*` and `interlude.*`), publishes, and
repaints the shared `MiniStagePreview` via `refreshVisualsPreview`. The
Retro LED face is the OFL-licensed VT323 bundled at
`assets/lyric-fonts/vt323.ttf` (see PROVENANCE.json); `kanta_submenu_type`
and the old Change-font/Save-message strings were removed with the buttons
they styled.

The native SoundFont library (`soundfont.c`, built by the CMake step into
`libkanta_audio.so` for arm64-v8a/armeabi-v7a/x86_64) now caps tracked
allocations at 3072 MiB; `tests/soundfont-test.c` pins the constant and its
boundary accounting. Local validation note: the host unit-test compile needs
a JDK whose module set includes `javax.sound.sampled` and the Java 11 file
APIs; where the sandbox JDK is stripped, compile the test sources with the
full JDK API and run `ph.kanta.reborn.core.CoreRegressionTests` directly with
`<assets>/party`, a scratch file, and the androidTest song fixtures as
arguments.
## PiP surface shows only the Stage (2026-11-26)

`onPictureInPictureModeChanged` delegates to `applyPipLayout(boolean)`. In PiP
mode it closes the inline Stage Controls, hides `appHeader`,
`stageAdjustments`, `pageScroll` and `navigationTabs`, forces `stagePane`
visible (even when the current tab hides it) with weight 1, and marks the
stage presenter visually active so the window keeps drawing. On exit it
restores the scroll, tabs and adjustments readout, re-weights the split for
the current orientation (0.50/0.58 portrait, 0.56/0.44 landscape) and defers
all tab-dependent state to `updatePrimaryStageVisibility()`.
`onUserLeaveHint` calls `closeFullscreen()` before `enterStagePip()` because
the fullscreen Stage is a separate dialog window that would remain pinned
over the PiP surface, and `onConfigurationChanged` re-applies
`applyPipLayout(true)` after an in-PiP orientation rebuild.


## Visual editors with live stage preview (2026-11-26)

`ui/MiniStagePreview` is a canvas-drawn, scaled-down stage (frame, scrim,
wordmark, song chip, mode-specific content) that owns no session state and
performs no I/O; every setter repaints. `KantaVisualsScreen` gained Lyrics and
Interlude tabs and embeds the preview in all four tabs; the host fills it via
the new `Listener.onPreviewReady` (stage photo file, imported-video frame via
`MediaMetadataRetriever`, bundled-scene thumbnail, the current lyric line in
the selected font, or the interlude text + image).

Interlude state lives in `SessionController` (`interludeText`,
`interludeTextSizeSp`, `interludeImage`, `setInterludeText`,
`setInterludeImage`, `clearInterludeImage`, `resetInterlude`), persisted by
`SettingsStore` and stored by `core/media/InterludeStore` (one app-private
file, atomic replace, 16 MB cap, PNG/JPEG/WebP/GIF, animated via
`AnimatedImageDrawable` on API 28+). Both `StageView` (fullscreen) and
`stage/LyricStage` (embedded) render the interlude in the pre-song window.

`DocumentTreeScanner.scanClassified` classifies a whole folder (songs,
background videos, background photos, legacy index+blob) in one bounded
walk (10,000-entry cap) and `SessionController.importKaraokeFolder` runs the
import steps sequentially on the folder worker. The two-step IDX/BLOB picker
chain (`LegacyFolderPicker`, `pickLegacyIndex`/`pickLegacyBlob`,
`startLegacyPair`, `lastLegacyIndex`/`lastLegacyBlob`) is removed; the
manual-key fallback now uses the pair the folder scan found
(`pendingLegacyIndexUri`/`pendingLegacyBlobUri`).

The portrait Stage split is 50/50; the App update panel is a themed elevated
card (no system gray); leaving the app during an in-progress song enters
Picture-in-Picture (`supportsPictureInPicture`, source-rect hint,
`setAutoEnterEnabled` on API 31+). `helpPage` renders eight numbered
icon-tile steps matching the real menu paths. Lint is at 0 findings
(zero-debt budget); core regression 73,285; 299 host tests.

## Search-results dock overlaps Stage (2026-11-24)

`MainActivity` docks the search-results panel on the Stage as a persistent overlay; `RuntimeChecks.assertPersistentStage` exempts `kanta.stage.search-results` from the below-the-Stage geometry check.

## Legacy MKMD decompression safety (2026-11-16)

The paired `.shubidx`/`.shubblob` importer validates each record independently.
Raw-deflate decoding now rejects dictionary-required, truncated and zero-progress
streams instead of looping indefinitely. The inflater's native zlib state is ended
in a `finally` path, including malformed-data and declared-size failures. A bad
record remains a skipped record with an error; valid indexed records can still import.
The regression uses a deliberately stalled inflater and verifies prompt rejection
and cleanup through the existing `:app:coreRegression` gate. Import cancellation is
checked during index validation, each record and every DEFLATE iteration; any files
staged earlier in a cancelled pair are deleted.

## Pinned browser transport teardown (2026-11-16)

The WebView JavaScript bridge and native screen lifecycle can overlap: a bridge call
may pass its open-state check just before the owning screen shuts down the transport's
single-thread executor. `SafeExecutorSubmission` treats the resulting
`RejectedExecutionException` as a revoked late request and releases the busy latch.
Transport close also clears that latch and disconnects active HTTPS connections.
This does not expand commands or destinations and does not change certificate pinning,
origin checks, token validation or request-size limits. The regression is part of
`BrowserRemoteRegressionTests`, which the existing `:app:coreRegression` task runs.

## Mobile-first responsive enhancements (2026-11-16)

- **Header**: Sticky with blur backdrop (rgba(8,11,20,0.92) + 16px blur)
- **Buttons**: Full-width on mobile (actions, download cards), auto-width on desktop
- **Feature cards**: Single column mobile, 2 columns tablet (768px), 3 columns desktop (900px), 4 columns large (1200px)
- **Footer**: Stacks on mobile, 2-col tablet, 5-col desktop
- **Sections**: Improved mobile spacing (40px/56px margins), hero padding 32px/48px
- **Split banner**: Stacks on mobile with centered content
- **Navigation**: Preserved original responsive pattern (site-nav with overflow-x: auto on mobile)
- **Accessibility**: Added mobile nav drawer HTML/JS as progressive enhancement

---
## Landscape mode and camera rotation lifecycle (2026-11-16)

- `MainActivity.onCreate()` initializes `lastOrientation`, so the first configuration
  change is not skipped.
- `onConfigurationChanged()` passes `newConfig.orientation` to `buildWindow(int)`
  instead of reading potentially stale resource configuration.
- Camera ownership is checked by exact target identity through `CameraStage.ownsSurface()`.
  Only a preview attached to the old primary `StagePresenterImpl` is rebound after render;
  fullscreen and external-display `StageSurface` targets remain owners during primary rotation.
- `RuntimeChecks` simulates a first orientation transition and restoration while the
  camera is enabled, checking layout direction, stage replacement, primary rebind, external
  target preservation, and release from superseded stages. Its persistent-stage baseline
  refreshes only on an orientation change, not on ordinary navigation. Physical-device rotation remains a separate acceptance.

---
## Compact landscape Home: no duplicate queue card (2026-11-24)

`HomeScreen` now sets the queue card (tag `kanta.home.reservations`) to `GONE` in landscape from construction, matching what `onMeasure` already enforced afterwards; the CI emulator runtime acceptance verifies it before the first measurement.

## Compact landscape Home: no duplicate queue card (2026-11-24)

`HomeScreen` now sets the queue card (tag `kanta.home.reservations`) to `GONE` in landscape from construction, matching what `onMeasure` already enforced afterwards; the CI emulator runtime acceptance verifies it before the first measurement.

## Compact keypad backspace icon (2026-11-24)

`InlineKeyboard.setCompact` swaps the Delete key to the ⌫ foreground glyph in compact row heights (restoring the DEL text in normal mode); the content description is unchanged, and `InlineKeyboardChecks` verifies the icon plus spoken label in the CI emulator runtime acceptance.

## Keypad press sounds & standby interlude (2026-11-14)

`KeypadSounds` (SoundPool, 1 stream, `USAGE_ASSISTANCE_SONIFICATION`) plays a tone per keypad key. `SessionController.playKeypadSound()` gates on `!isPlaying()` so feedback never masks karaoke. Twelve MP3 assets live in `assets/keypad/`.

The standby interlude (`BundledInterlude`, `assets/interlude/standby-loop.mp3`) is the default between-song source, plays on the empty stage, and stops for karaoke. Party Mode offers "Override with your own track" and "Use the bundled standby track."

## Keypad sounds, interlude once, BGV access, SoundFont progress (2026-11-14)

- **Keypad sounds**: Fixed `SoundPool.OnLoadCompleteListener` to use anonymous class for D8 desugaring compatibility; keypad tones now load on IO thread for faster readiness.
- **Standby interlude**: Bundled welcome track now plays once on startup (not looping); custom intermission tracks still loop via `PartyAudio.loop()`.
- **Background Video (BGV)**: Added prominent entry in More ▸ Background Video for managing stage background videos.
- **SoundFont downloads**: Added progress dialog with live percentage for FluidR3 GM, MuseScore General, and SGM-v2.01 downloads via `SessionController.DownloadProgressListener`.

---
## Now Playing divider removed & scroll fade (2026-11-13)

The stray `|` divider between Key and Tempo columns in `KantaNowPlayingScreen` was removed. `MainActivity` adds a vertical fading edge (24 dp) to the main `ScrollView` so content fades into the floating bottom dock.

## Current Android layout status — published preview

PR [#202](https://github.com/usermalik20-del/Kanta-reborn/pull/202) merged as `df87c03a4c11d6fee6ba514334bb940e651b2d92`. Exact-head
[PR validation](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43328747178) and post-merge
[Android validation](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43331249690) passed build/lint, 55,735 core checks
and 555 emulator checks. Home and playback screenshots were reviewed after the fixes.
The code-introduction/first-publication evidence below is immutable; later docs-only
rebuilds may have a newer packaging source. Use the [download page](https://kanta-public.vercel.app/android)
for the latest package rather than treating this evidence tag as a moving latest link.
The [publisher](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43332183448) verified the signed APK and published
[`v0.10.0.845-preview-df87c03a`](https://github.com/usermalik20-del/Kanta-reborn/releases/tag/v0.10.0.845-preview-df87c03a);
[website deployment](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43332262432) succeeded.
This supersedes the local/pending status in the dated candidate notes below.
Physical-device, large-text/rotation, long-session and actual external-display acceptance
remain open; independent review workers did not return a usable review.

## Distinct monitor-effect presets (K1)

Small Room and Concert Hall now have their own tuning rather than duplicating Room and
Hall. The existing Room/Hall tuning, enum identities, picker order and saved selections
are unchanged. Effects remain monitor-only: pitch detection and scoring receive dry
microphone audio. Automated response tests do not establish perceived sound quality or
hardware monitoring latency; those still require listening on a physical device.

## Bundled standby selection reload repair (2026-11-10)

Run `42797615517` compiled the lifecycle/frame-callback repair but correctly failed
runtime acceptance. Further tracing found that `restoreStage()` wrote
`bgv_default.video`, then `StageMediaStore.load()` rejected it because its validation
accepted only SHA-256-named imports. The store now also accepts exact bundled catalog
IDs with the `.video` suffix and requires the file to exist; arbitrary filenames,
unknown IDs and path traversal remain rejected. Startup scenic initialization no longer
replaces an already-restored non-scenic selection.

Runtime checks now explicitly reload the persisted video selection and check the BGV
source/texture before waiting for actual frame updates, giving actionable failures.
The new source-contract regression fails against the old filename validator and passes
with this repair. Exact-head emulator acceptance is still required; no release or merge
has been performed.

## Android standby video lifecycle and truthful runtime evidence (2026-11-10)

Follow-up to the ordering repair in PR #192: `MainActivity.onStart()` now registers
its session listener (which synchronously creates the primary stage) before activating
visuals. Previously the first-launch activation saw a null presenter and left the newly
created `BackgroundStage` inactive, so it never prepared standby video. Existing
stop/start and fullscreen visibility ownership remain unchanged.

Standby live-frame evidence now advances only from `TextureView.onSurfaceTextureUpdated`
in the embedded and fullscreen renderers, not from playback or UI refresh timers.
The embedded renderer gates notification on active BGV media; the fullscreen renderer
requires an active foreground prepared player. Frozen/absent video cannot pass merely
because a timer runs. Playback, queue and media selection stay controller-owned.

The Android emulator runtime step is now blocking: instrumentation failure fails the
job. Diagnostic collection and artifact upload retain `if: always()` so failures still
preserve logs and available artifacts. This supersedes the historical non-blocking
runtime-step description below; no signing, merge or publication authority is changed.

Three source-contract regression guards failed before this repair and pass afterward:
first-launch activation order, actual-texture frame evidence, and CI failure propagation.
Local static/Python checks are not emulator acceptance; exact-head CI must prove the
standby assertion and every required UI capture before claiming runtime verification.

## Runtime evidence regression: standby checks ran before the stage was resolved (2026-11-10) — fixed

The Android emulator run attached to merge commit `818a73f` produced **one screenshot**
(`browser-remote.png`) instead of the nineteen UI captures the artifact list declares. It
happened because a check introduced in that same commit ran before the view it inspects was
assigned, so the instrumentation process crashed on its first assertion — before the first
`capture()` call — and every acceptance step downstream was skipped.

### Root cause

`RuntimeChecks.onStart()` called `stageStandbyChecks()` three lines **above** the statement
that assigns `persistentPrimaryStage`. That helper reads the field directly:

    View stage = persistentPrimaryStage;
    check(stage != null, "Standby check needs the persistent stage");

so it threw `AssertionError: Standby check needs the persistent stage` on the main thread.
Logcat confirms the failure and the stack:

    E AndroidRuntime: FATAL EXCEPTION: main
    E AndroidRuntime: Process: ph.kanta.reborn, PID: 3001
    E AndroidRuntime: java.lang.AssertionError: Standby check needs the persistent stage
    E AndroidRuntime:   at ph.kanta.reborn.RuntimeChecks.check(RuntimeChecks.java:34)
    E AndroidRuntime:   at ph.kanta.reborn.RuntimeChecks.lambda$stageStandbyChecks$252(RuntimeChecks.java:1280)

`adb shell am instrument` then reported `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`,
`tools/run-android-runtime.py` raised *"Runtime instrumentation did not report acceptance"*,
and the screenshot loop after it never ran.

### Why CI still showed green

The step *"Android emulator runtime checks"* carries `continue-on-error: true`
(`.github/workflows/android-validation.yml:167`), so the job and the run still concluded
`success`, and the artifact step uses `if: always()`. The evidence directory therefore
uploaded with the log, the two APKs, lint reports and `browser-remote.png` while silently
missing every UI screenshot. For comparison, run `798` on `6fd4b0a8` (before this work)
logged `KANTA runtime: 416 checks passed` and read all nineteen captures.

### Fix

`RuntimeChecks.onStart()` now resolves `persistentPrimaryStage` and calls
`assertPersistentStage(null)` **first**, then runs `stageStandbyChecks()`, then
`capture("home_dashboard.png")`. Order matters because the standby assertions inspect the
resolved view rather than looking the stage up themselves. The redundant
`await(() -> session.stageReady, ...)` that preceded the call was removed; `stageReady` is
already awaited at line 108.

### Regression guard

`tools/tests/test_android_runtime_runner.py` gains
`test_standby_checks_run_after_the_stage_view_is_resolved`, which asserts that the
`persistentPrimaryStage` assignment appears before `stageStandbyChecks();` inside `onStart()`,
and that both standby helpers still read the field. Verified by reintroducing the original
ordering: the test fails, and passes again once the fix is restored.

### Consequence for the release page

The `00000` standby stage, the live-BGV frame counter and the control deck are still only
*statically* verified on this source. The emulator evidence for them must be re-collected from
a green run after this fix; until then the website must not present those captures as
existing. The site copy already states that the standby stage and control deck are asserted by
emulator checks without claiming screenshots, which remains accurate.

## Android: karaoke-rooted UI, stage standby, and incremental versions (2026-11-09) — local candidate

Fixes real-world UI ambiguity across the Android app, keeps the product unmistakably a karaoke
app, makes the empty stage look like a karaoke machine waiting for a selection, and stops every
preview release from sharing one version.

### Stage standby is karaoke, not wallpaper
- New `stage/StagePresenceOverlay.java`: when no song is on the stage it draws a hardware-remote
  style `00000` song-code prompt, a pulsing `KARAOKE READY` badge, an animated equaliser, and the
  single instruction "Enter a song code to sing / or find a song in Sing".
- `StagePresenterImpl` adds it directly above `BackgroundStage` and below every functional overlay,
  and binds it from the truthful empty-playback state (`session.current == null && !isPlaying`), so
  live lyrics, countdown, score and search always win.
- The background is the bundled BGV video, so the standby stage is moving video rather than a
  frozen still. `StagePresenceOverlay.liveFrame()` publishes a monotonic counter that only advances
  while a BGV `TextureView` is attached, available and the visual layer is active — the runtime
  check proves the standby stage is showing captured video, not a wallpaper.
- `stage/LyricStage.java` gained `setStandby(boolean)` and no longer paints a placeholder over the
  standby card. Its own placeholder is now the truthful "No timed lyrics for this song" (only
  reached when a song IS loaded but has no timed cues).
- `StageSurface` (fullscreen/external stage) shows the same standby card and reports live frames.
  `StageView` gained `setStandbyMode(boolean)` so the legacy "Your music. Your stage." /
  "Reserve your next song in CONTROL" copy cannot stack over the standby card.

### The app says karaoke
- Top app header now carries the brand block that used to sit on the dashboard: the KANTA wordmark
  with the `SING MORE. TOGETHER.` tagline, plus Help. The baybayin subtitle (`KANTA · ᜃᜈ᜔ᜆ`) and the
  second in-dashboard logo/tagline were removed, so there is exactly one brand lockup on screen.
- The header logo (`kanta.header.logo`) still toggles the control deck.

### Ambiguity and consistency fixes
- `InlineKeyboard`: `CLR`/`RES` became the standard ⌫ (backspace) and `OK`, on both the numeric and
  letter layouts.
- Search/keyboard toggles read `Letters` / `Numbers` instead of the cryptic `ABC` / `123`
  (Home and the Search destination). The decorative magnifier no longer advertises itself as an
  unlabelled image; it is described as "Search".
- Home typing now opens the real Search destination with the term applied (`pendingSearchQuery`)
  instead of silently driving a stage overlay. Search is a proper destination with back navigation,
  and nothing is layered over the karaoke standby stage.
- Mic & Voice: status and action can no longer contradict. A requested-but-unconfirmed capture
  reads "Starting microphone…" with a "Starting…" button, not "Microphone off" above "Turn on
  microphone".
- Queue: the empty state reads "Queue is empty. Add a song to start your karaoke set." and the
  action is "+ Add Songs to Queue".
- Now Playing: one truthful empty state — the `Nothing playing` headline plus `Reserve a song to
  begin`, with the lyric area showing "KANTA Karaoke · your stage is ready" instead of a competing
  "No timed lyrics loaded".
- Pitch & Tempo: the non-interactive monospace `♭♭ ♭ 0 ♯ ♯♯` row became a readable semitone legend
  ("Semitone scale · ♭♭ lower · 0 original · ♯♯ higher") so it no longer reads as tiny controls.

### Incremental versions
- `apps/android/app/build.gradle` no longer freezes `versionName` at `0.10.0-reconstruction`.
  `versionCode` keeps the CI allocation (bounded `kantaVersionCode`, workflow run number) and
  `versionName` is now derived from the same allocation:
  `<kantaVersionBase>.<versionCode>-preview` (base stays on the current line, default `0.10.0`), with
  `-PkantaVersionName` available to pin an exact name. Shape stays inside the published
  `[A-Za-z0-9.+-]{1,64}` rule enforced by `UpdateManifest` and `tools/prepare-update-feed.py`.
- Result: every published preview now has a distinct `versionName` and therefore a distinct
  durable release tag (`v<version>-preview-<sha8>`), instead of all sharing `v0.10.0-preview-*`.
- `tools/verify-ota-publication.py` is unchanged: it still pins `versionCode` to the CI allocation
  and requires strictly increasing versions and descendant source history.

### Validation
- Local guards: java-structure (146 files), android workspace contract 55/55, product branding 4/4,
  companion interface, ios presentation contract, verify-website, verify-test-layout, and the
  161-test Python suite — all PASS.
- Not run here: Android compile/lint, emulator `RuntimeChecks`, iOS build, signing, deployment.

## Android: Kanta-Connect QR Code on Stage (2026-11-09) — local candidate

Moved Kanta-Connect pairing QR code from an AlertDialog to the persistent karaoke stage overlay:
- `StagePresenter` interface gains `showKantaConnectQR`/`hideKantaConnectQR` with `KantaConnectQRInfo` data class
- `StagePresenterImpl` adds a `QrCodeView` overlay on the stage with host address, QR code, and grouped pairing code
- `MainActivity.kantaConnect()` now calls `stagePresenter.showKantaConnectQR()` instead of showing an AlertDialog
- Tap the stage overlay to dismiss; hosting continues until "Stop Kanta-Connect"
- Auto-hides on playback start (`prepare()`/`stopPlayback()`) and when Kanta-Connect is stopped
- `RemoteStatusView` on stage reflects ADVERTISING/CONNECTED/DISCONNECTED state
- Uses existing `QrEncoder` (byte mode, ECC level M, versions 1-10) and `QrCodeView` (4-module quiet zone, integer module scaling)
- Enables scan-to-pair workflow with Kanta-Connect phone app — no manual 32-char code entry needed

<!-- Documentation sync: 2026-11-09 -->
## Android: Fix FluidR3 GM Download String Literal (2026-11-09)

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.

Fixed unclosed string literal in SessionController.readAssetText():
- Fixed '\\n' escape sequence for newline in StringBuilder
- Required by Java compiler for proper string literal escaping
- SessionController now compiles correctly with downloadFluidR3GM()
## Android: Fix FluidR3 GM Download Compilation (2026-11-09)

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.

Fixed FluidR3 GM SoundFont download compilation errors:

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.
- Moved full downloadFluidR3GM() logic from MainActivity to SessionController
- MainActivity now delegates via session.downloadFluidR3GM()
- Added formatBytes() and readAssetText() helper methods to SessionController
- Kept FLUIDR3_GM_NAME constant in MainActivity for UI display
- Fixes 'cannot find symbol' errors for io, repository, main, FLUIDR3_GM_SIZE, FLUIDR3_GM_URL, FLUIDR3_GM_LICENSE
- Download logic uses session's io executor, repository, and main handler
- Progress updates via session.message and publish()
- Validation with NativeSoundFont.open() before saving
- License file (fluidr3gm-license.txt) saved alongside SoundFont
## Android: Add FluidR3 GM Download to SessionController (2026-11-09)

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.

Added downloadFluidR3GM() method to SessionController:
- Downloads FluidR3 GM SoundFont from official fluidsynth mirror (ftp.osuosl.org)

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.
- Visual progress: percentage + bytes downloaded (updates session.message)
- Validates with NativeSoundFont.open() before saving
- Saves license file (fluidr3gm-license.txt) alongside SoundFont
- Not shipped with app - downloaded on demand to user's local media folder
- Reuses existing import progress UI (session.message + publish())
- Delegated from MainActivity via session.downloadFluidR3GM()

This refactoring fixes 'cannot find symbol' errors by moving download logic to SessionController where io, repository, and main are accessible.
## Android responsive stage

The Stage tab keeps the lyrics and keypad together. Menu opens the other pages
without the stage taking up space; playback keeps going. Both tabs use a shorter
navigation dock, and the main layouts use the full width inside Android's system insets.
PLAY, RES, and CLR sit below the stage and above Search. KEY changes the pitch by
one semitone per tap; TEM changes the tempo by five percentage points. Both sit beside
Space below the keyboard letters.

**Stage & Visual** has separate **Background photos** and **Background videos**
tabs. Their shuffle switches are off by default. Choose a photo or video first;
shuffle can change it at the start of a new song without replacing your saved
choice. Camera and Fullscreen have their own labels and icons beside the Stage.

**Stage Controls** expands a translucent panel beneath the Stage rather than
replacing the lyric area. The eight Party Pads are immediately visible, with
microphone, monitor, scoring, queue and remote shortcuts arranged below. Use
Back to close it in embedded mode; the panel never covers the keyboard or hides
a Pad behind another tap. **Menu → Party Pads** opens the same pad page with
clip assignment, reset and volume.

My Set shows the imported-song count and one Notices and licenses
entry. KANTA no longer adds starter songs to the library.

The compact countdown starts when the song is ready. Its last cue stays visible until
Android reports playback progress. This is not a measurement of when sound reaches
speakers or headphones; output buffering and silence in the song can affect that.
Source-numbered legacy songs accept equivalent leading-zero spellings on the keypad.
Standalone imports use the code KANTA displays for the song. For karaoke monitoring,
keep the music and
voice levels separate: lower the backing track until you can hear yourself clearly,
then raise voice monitoring gradually on wired headphones or a USB headset. Bluetooth
call-style monitoring stops for song playback so it cannot take over the music route.
Physical timing, routing and audition still need device checks.

At startup, KANTA shows the launch artwork and reports progress while opening the
song library, preparing sounds, and loading the saved stage background. It then
opens the Stage automatically.

Song matches show below the Stage input as you type, including single digits and
partial song numbers. Tap a match to reserve it. **Menu** opens Presets, KANTA
Songbook, Stage & Visual, Intermission, Party Pads, Voice & Scoring, Settings,
Browser Remote, settings backup, and help.

## Android SoundFont download compatibility

FluidR3 GM and MuseScore General use the same HTTPS mirror. On older Android
versions the app can validate the mirror using its bundled public root; other
sites still use only Android’s built-in trust. No device settings changes are needed.

## Android: Move FluidR3 GM Download to SessionController (2026-11-09)

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.

Refactored FluidR3 GM download logic from MainActivity to SessionController:

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.
- Fixes 'cannot find symbol' errors for io, repository, main variables
- MainActivity now delegates to session.downloadFluidR3GM()
- Keeps FLUIDR3_GM_NAME constant in MainActivity for UI display
- Download logic uses session's io executor, repository, and main handler
- Progress updates via session.message and publish()
- Validation with NativeSoundFont.open() before saving
- License file (fluidr3gm-license.txt) saved alongside SoundFont
## Android: Fix MainActivity Syntax Errors (2026-11-09)

Fixed two compilation errors in MainActivity.java:
1. 'else' without 'if' - moved "Download FluidR3 GM" button outside the for loop in mySet()

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.
2. Unclosed string literal in readAssetText() - fixed newline escaping (\\n)

Both fixes address Android build compilation errors that were causing CI failures.
## Android: FluidR3 GM Download Option (2026-11-09)

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.

Added FluidR3 GM SoundFont as downloadable option in My Set → SoundFonts:

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.
- Button "Download FluidR3 GM (~140 MB)" appears after listed SoundFonts

### Additional SoundFont Downloads (2026-11-11)

Two new one-tap soundfont downloads have been added to the My Set → SoundFonts screen:

| SoundFont | Size | Description | Source |
|-----------|------|-------------|--------|
| **MuseScore General** | ~216 MB | Richer orchestral tones, direct .sf2 download | OSU OSL mirror |
| **SGM-v2.01 Yamaha Grand** | ~528 MB | Studio-grade piano, bass, guitar | Hugging Face |

Both include license files (musescore-license.txt, sgm-license.txt) and are validated via NativeSoundFont.open() before activation.
- Downloads from official fluidsynth mirror (ftp.osuosl.org)
- Visual progress: percentage + bytes downloaded (updates session.message)
- Validates with NativeSoundFont.open() before saving
- Saves license file (fluidr3gm-license.txt) alongside SoundFont
- Not shipped with app - downloaded on demand to user's local media folder
- Reuses existing import progress UI (session.message + publish())
## Home hero greeting/tagline removed — local candidate

The Home dashboard's hero banner (dynamic greeting + `Music brings people together.` headline) is removed. Its messaging duplicated the KANTA header tagline above it; the dashboard now flows header → search → Continue Singing. Android runtime checks gate Home return on the stable search hint instead of the removed banner text.

Also pruned for redundancy: the `My Set`/`Favorites`/`Recent` quick-cards row (every card opened the same My Set destination) and the header Settings gear (a duplicate of the persistent More dock tab).

The duplicate bottom dock is also removed: HomeScreen's own 5-tab dock (tags `kanta.home.nav.*`) duplicated the persistent MainActivity dock (`kanta.nav.*`) on Home. The internal dock is gone; the persistent dock is the single bottom navigation surface.

Home dashboard now includes persistent search with an inline keyboard (`CLR` clear, `RES` reserve). Text/code queries dynamically stream search results to the karaoke stage.

## Premium icon system and floating navigation dock — local candidate

Android's icon set was replaced wholesale with a coherent outline family from the KANTA Premium icon pack: 24x24 viewBox, 1.8dp monoline stroke, primary cyan (`#29D9FF`) plus a single violet (`#7657FF`) `<path>` detail. New dedicated drawables include `kanta_sing`, `kanta_performance`, `kanta_experience`, `kanta_companion`, `kanta_diagnostics`, `kanta_help`, `kanta_my_songs`, `kanta_favorites`, `kanta_recent`, `kanta_lyrics`, `kanta_key_tempo`, `kanta_coach`, `kanta_background`, `kanta_camera`, `kanta_effects`, `kanta_realm`, `kanta_party`, `kanta_display`. The five primary tabs are now one `BG_ELEVATED` floating dock (tag `kanta.nav.*`) with muted-inactive / cyan-active states; GroupScreen cards use a soft edge-lit deep-navy background (`KantaTheme.edgeLitCard`).

# KANTA Android reconstruction preview

The launch splash is a full-bleed `KantaTheme` window background
(`res/drawable/kanta_splash_screen.xml`): a portrait image
(`drawable-nodpi/kanta_splash_image.webp`) by default and a landscape image
(`drawable-land-nodpi/kanta_splash_image.webp`) that Android picks automatically for tablets
in landscape and Android TV. The prior centered `kanta_splash_image.png` was replaced.

## Persistent stage and lower control panel — local candidate

The primary `StagePresenterImpl` tagged `kanta.stage.primary` remains attached and visible while navigation changes the utility content below it. Now Playing no longer creates a competing `StageSurface` or takes camera ownership. Menus, background selection and playback controls project `SessionController` state; they do not start a new playback attempt or alter reservations simply by opening. Fullscreen explicitly suspends the embedded visual projection and returns camera ownership to the primary presenter on dismissal.

The always-visible duplicate utility rows are removed. The persistent five-tab navigation owns primary navigation, **More** expands the six workspace destinations, and the KANTA header logo toggles a lower-panel-only hardware-inspired Control Deck. The deck delegates master volume, transport, microphone/monitor, camera, key/tempo and destination actions to existing owners; it never creates playback, queue, stage or microphone state. Logo re-tap and Android Back restore the prior destination.

The Stage shows the effective key and tempo in a small readout above the visual area, including neutral values. Fullscreen shows the same values in its header; adjustment controls remain separate. In **Menu → Presets → Add Karaoke Folder**, select a folder with one recognized IDX file and one BLOB directly inside it. This uses the existing paired importer; select the files individually if a folder has ambiguous names or more than one pair.

Acceptance requires a fresh APK build and instrumentation on portrait and landscape displays: keep the stage visible above each panel, preview background changes in place, retain playback identity and audible-clock progress, dismiss panels with predictive/system Back, and verify camera/video handoff through fullscreen. Local source/static checks are not emulator or physical-device acceptance.

## Large SoundFont RAM ceiling — local candidate

TinySoundFont tracked allocations are capped at **1536 MiB (1.5 GiB) per worker**, raised from 128 MiB. PCM16 banks expand to float samples (approximately twice their sample-data bytes), with additional tables, channels and voices. This ceiling does not allocate RAM in advance, change Android heap/process limits, or guarantee that a bank fits a low-memory phone/TV. Allocation failures remain failures and malformed-bank checks remain enabled; the 128-voice bound is unchanged. Large-bank memory-pressure and sustained playback acceptance require a capable physical Android device. No SoundFont asset or source media is modified by this change.

## Compact Workspace back-navigation contract

The primary stage stays attached and visible for Home and all utility destinations. Navigation changes only the lower menu/control area; it must not hide the live preview to avoid duplicate controls. Android system/predictive Back dismisses the active utility first through the lifecycle-bound AndroidX `OnBackPressedDispatcher` callback. Keep stage visibility, lower-panel placement and Back behavior covered by `RuntimeChecks.workspaceBackNavigationChecks` and `tools/check-android-workspace-contract.py`.

## Companion Remote candidate in PR #51 — October 18, 2026

Owner-approved requirement: the supplied KANTA remote design opens inside this same app when Companion Remote is selected, with no separate remote app. Both iOS and Android must support it, plus paired browser guests entering the host's LAN address. Host/source queue ownership, authentication and explicit sensor permissions remain authoritative. The owner moved implementation into PR #51. HTTPS hosting and a shared locally served browser/built-in remote are now candidate source; native/browser/device acceptance is pending and this is not released. Expanded unsupported controls remain explicitly unavailable. See the [shared roadmap and original design reference](../../docs/COMPANION_REMOTE_ROADMAP.md).


New native Java/Android Views source reconstructed from documented behavior and original first-party assets. This is not recovered original Android source or a complete feature-parity release.

Implemented in source: original starter songs and scenic images; app-private MIDI/KAR/MP3/WAV import with a visible My Set song list and direct Reserve actions; native SF2 MIDI synthesis and platform MP3/WAV playback; persisted SoundFont inventory with an explicit active-bank selector; folder-tree import with cancellation; tempo/pitch/volume controls; source-timed highlighted lyric fragments, adjustable type and optional Realm lights; recorded/custom party pads and opt-in between-song music; one singer/FIFO reservation queue; saved playlists and played-session Favorites; starter remove/restore; keypad/RES and optional device-provider voice search; fullscreen/TV stage; explicit CSV/private encrypted Songbook export; offline privacy/rights notices and audio-focus handling.

New personal-media source: byte-preserving private photo/video backgrounds, sampled EXIF-oriented photos, muted looping video, persisted selection and stable stage/navigation lifecycle. Accepted personal-media source passed run 38825170997 (201 domain and 47 emulator checks).

Remaining: physical scoring calibration, physical monitor/pitch-correction acceptance, companion networking. Optional dry pitch-following estimates are implemented in the accepted 0.8 source. Optional camera preview passed run 38826875086 with 201 domain and 60 emulator checks. Loop and Realm now expose their implemented audio and animated-stage controls. No YouTube player or ad blocker is implemented.

Hosted validation passed Gradle APK assembly, Android lint, all 46 domain regression checks and browser interoperability for the encrypted Songbook export. Debug APK and lint reports are retained in CI artifacts for seven days. This validated baseline is merged as PR #1. Native media source 87e5aa21 passed cloud run 38805798316: NDK APK/instrumentation assembly, lint, 167 core assertions, native sanitizer/malformed-bank checks, browser interoperability and 13 real API 35 emulator checks. Physical-device playback acceptance remains open.

Build: AGP 8.13.2, verified Gradle 8.13 wrapper, Java 17, minSdk 26, targetSdk 36. The new reconstruction package is `ph.kanta.reborn`; the original Android package/signing identity has not been recovered. No signing keys are bundled. TinySoundFont is included under MIT; see THIRD_PARTY_NOTICES.md. Native builds use NDK 27.2.12479018 and CMake 3.22.1. The pinned vendor header is fork `usermalik20-del/tinysoundfont` revision `a272fe30` (release `kanta-vendor-a272fe30`, hash-pinned in `app/src/main/cpp/vendor/PROVENANCE.json`): hardening patch plus the quality engine (Catmull-Rom interpolation default, ramped low-pass, SF2 modulators, chorus/reverb stages, SF3 loader pad fix, `tsf_playback.h` wrapper).

See ../../docs/ARCHITECTURE.md and ../../docs/CODEBASE_CONTEXT_PACKET.md.

Import bounds: MIDI/KAR files have no fixed file-size, event-count or duration caps (structural SMF 0/1 rules still apply; individual text events are capped at 16 MiB and files above 2 GB are not mapped); SF2 has no fixed file-size cap, but import preflights reported size, rechecks available storage while streaming, and preserves a 64 MiB free-space reserve before strict native structure/sample validation; folder scan 10,000 entries, 1,024 directories and depth 16. Negative coarse sample offsets (unsupported safely by the pinned loader) and unsupported/oversized banks are rejected; SF3/Ogg banks are supported through the pinned stb_vorbis decoder with post-decode sample accounting (`synth->sampleTotal`), a synthetic SF3 fixture in the native regression, and a render-tail guard that rejects banks whose region windows come within one float of the decoded buffer end (the Catmull-Rom interpolator reads two floats ahead). The pinned engine implements SF2 modulators and chorus/reverb stages (release `kanta-vendor-a272fe30`; `PROVENANCE.json` pins the revision and file hashes), but KANTA has not wired those engine paths to app controls yet and MIDI poly (key) pressure remains untracked by the engine wrapper. Engine-level validation lives in the fork's own CI; KANTA's native regression compiles the pinned header with `-Wall -Wextra -Werror`. Folder selection makes private copies; it does not watch or edit the source. Bank changes apply to subsequent playback. KAR lyric text encoding is detected (UTF-8, Shift_JIS, GBK, Big5, CP949 with windows-1252 fallback); @T/@A/@C metadata feeds title/artist/copyright, @P pause codes shift lyric cues and \ echo lines are dimmed on the stage. Imported songs and SoundFont banks can be removed from My Set, missing private copies are repaired on load or re-import, and stage search includes the active library. The transport Previous button restarts the current song: seek-to-top is supported for MIDI/KAR synthesis and MP3/WAV playback. Key/tempo adjustments made while a song is playing are remembered for that song and re-applied the next time it plays; Reset in the Pitch & Tempo screen clears the per-song memory (or the app-wide default when the song has no override). My Set lists imported songs and offers Reserve, lists every retained SoundFont with an Active indicator and Use action, and persists bank selection across restart. Paired ShubBlob/kantablob and index-container import is available from My Set by selecting the index and then its paired blob. Single-file container selection remains rejected so a blob is never mistaken for a standalone song; the file-backed MKMD path validates per-title record addresses and rejects ambiguous or invalid layouts rather than guessing physical order; exact-head runtime validation remains required.

Party pads are bundled, edited CC0 WAV recordings (source details are included in the app assets) with at most four concurrent SoundPool voices. Import a short MP3/WAV in Presets or Intermission, then assign it in Party Pads (8 MiB / 30 seconds maximum, also bounded below SoundPool’s 1 MiB decoded limit). Between-song music selects an imported MP3/WAV, starts only when explicitly enabled and the karaoke queue is idle, and stops for karaoke, focus loss or leaving the foreground. Enabling is not restored automatically. Party volume is independent of karaoke volume.

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

## Local playback and upgraded queues

Android plays songs from the local library. The former YouTube preview and its entry points are removed. When loading the existing library, old online queue rows are skipped; local reservations keep their order, reservation IDs and singer names. The next library write keeps only the restored local rows. Companion control, optional LRCLIB lyric lookup and app updates still use the network.

<!-- party-sounds-assets-committed -->
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

The eight bundled recorded pads are Applause, Cheering, Drum Roll, Air Horn, Laughter, Ta-Da, Ding and Sad Trombone. The five presented score bands are Excellent Singer (100), Great Singer (90–99), Good Singer (80–89), Rising Star (65–79) and Keep Practicing (50–64). `ScoreSink.overall()` floors the displayed result at 50; `rawOverall()` retains the underlying 0–100 estimate. This is a presentation floor, not a measured minimum performance or a guarantee that every song receives a score.

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


## Kanta-Connect pairing-code logging boundary — 2026-11-09

Kanta-Connect creates a single-session 128-bit pairing code. It is disclosed through the owner-facing QR/typed pairing UI only; Logcat and host status callbacks receive no pairing secret. The pairing protocol and UI flow are unchanged. `tools.tests.test_companion_security_contract` guards the logging boundary and confirms the UI still reads the code from `KantaConnectHost.pairingCode()`.

## Reference-led native UI integration — validation candidate

The Home dashboard and dedicated Search, Queue, Pitch & Tempo, Mic & Voice, Visuals and Now Playing screens are mounted as native Android Views destinations. They delegate playback, FIFO reservations, SoundFont selection, imports, microphone/monitor state and stage changes to the existing `SessionController` and repository owners; they do not introduce a second queue or playback state machine. The supplied artwork and drawable mapping are listed in [KANTA Android adaptive assets](../../docs/KANTA_ANDROID_ADAPTIVE_ASSETS.md).

The Android runtime walkthrough captures `home_dashboard.png`, `home_dashboard_restored.png`, `search_screen.png`, `queue_screen.png`, `pitch_tempo_screen.png`, `visuals_screen.png`, `mic_voice_screen.png`, `fullscreen_stage.png` and `now_playing_screen.png`. It also retains `my-set-imported-library.png` and `my-set-active-soundfont.png` to prove imported-song visibility and active SoundFont selection. Each destination capture must prove that its tagged view is actually visible before taking the screenshot. These are CI acceptance artifacts, not a claim of physical-device acceptance or a published APK.

## PR #134 merge reconciliation — 2026-11-02

The reference-led Android UI and companion changes were reconciled onto current `main`. `MainActivity` remains the composition boundary; `HomeScreen` and destination screens delegate playback, queue, import, microphone and stage state to existing owners. Exact-head Android runtime validation is required after reconciliation.

The reconciled PR retains current-main `SessionController` SoundFont inventory, active-bank selection and import-progress APIs; the reference-led screens remain presentation-only adapters.

The karaoke-stage candidate initializes the reference-led Home dashboard on launch; saved navigation is applied only after the dashboard default is established.

## Three-group navigation candidate

The current Android UI candidate uses five primary destinations: **Home | Sing | Performance | Experience | More**. Sing is the doorway to My Set/playlists, Favorites and Recent. Performance owns the Now Playing/lyrics surface plus Voice, Key & Tempo and Singing Coach. Experience owns Background, Camera, Effects, Scenic, Party Mode and Display. More contains advanced settings and tools. The grouping is presentation-only; `SessionController`, `StagePresenterImpl`, catalog persistence and the microphone owner remain authoritative.

Search uses an embedded KANTA keypad toggled from the search row. Numeric and compact letter layouts share a fixed-height footprint; the search field suppresses the system IME so the device keyboard cannot cover results.

## Supplied UI and SoundFont assets

The UI upgrade pack's multicolor VectorDrawables are used directly by Home and Voice navigation/action surfaces; no tint or color filter is applied to those resources. The two supplied procedural banks are bundled under `app/src/main/assets/starter/`, installed into the existing app-private media inventory on first load, and remain selectable through My Set without changing the authoritative playback owner. Their declared SHA-256 values are recorded in `starter/SOUNDFONT_LICENSES.txt`; runtime distribution follows the restricted KANTA OpenStage Procedural Asset License 1.0. The OpenStage candidate lock/audit files are documentation only until individual sample licenses are independently verified.


## Paired ShubBlob import candidate

My Set exposes `Import legacy idx + blob`: choose the paired index, then the blob. The importer resolves per-title 2 KiB block references from title-record byte 8 and validates every bounded MKMD header before creating output. Valid trailing byte-offset layouts are still supported; conflicting, duplicate or invalid addresses fail closed. Physical scan order is never used to attach song names. Supported MIDI/WAV/MP3 records are materialized through `LibraryRepository`. A synthetic integration test verifies names against distinct decrypted payloads in a different physical order with extra unreferenced records. A real pair was independently decoded locally using title addresses, without saving or uploading media. Hosted validation for this mapping revision is pending. Paired-file emulator import, bundled-key unlock, storage/cancellation and physical playback remain open.

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
- The feed is **live**: `apps/web/updates.json` now points to release `v0.10.0.947-preview-bac94877`, source `bac94877`, and is served through the `vercel.json` routing table; the app reads it from Controls → App update. The published APK SHA-256 is `428ca3afa96251f18f41ab7c40e0a4d1f434a41d1a81338bcf7ba9b1166a39e4`.
- `signingCertSha256` is emitted from `apksigner` metadata for the exact signed APK and is checked against the app manifest contract; the published certificate fingerprint is `04488bab3deaf6c9702605e27b7e5469e5f1b6bcf2d440b0bd2b507202d385fc`.
- Local Gradle builds and physical-device instrumentation were also exercised during the 2026-11-25 repair loop. Hosted Android validation remains the release gate; full physical-device acceptance and update-install flow remain open.
- The GitHub Release asset creation makes every post-merge Android build a public release asset automatically; this is a publication side effect, not a release claim.
- App update is user-initiated (off by default), HTTPS-only, SHA-256 verified, staging is app-private; install needs the separate Android "Install unknown apps" permission.


### OTA versioning and signing gate

## Emulator acceptance: fullscreen transport counts action buttons (2026-11-25)

The shipped fullscreen transport row also holds the artist branding (a `TextView` at the bottom-right), so the runtime check now counts only `Button`-typed transport actions (restart, play/pause, next) rather than asserting a fixed total child count.

## Emulator acceptance: fullscreen has no Stage Controls panel (2026-11-25)

The shipped fullscreen Stage is deliberately free of the controls panel (`StageDialog` clears it and `fullscreenControls` stays null). The runtime check now asserts `kanta.fullscreen.controls` is absent, then verifies the fullscreen transport and close buttons.

## Emulator acceptance: Stage Controls pad tag mismatch (2026-11-25)

The pad-visibility check derived the child tags as controlsTag + ".pad.N" (kanta.stage.controls.pad.0), but the app tags pads and controls from the bare stage prefix: kanta.stage.pad.N and kanta.stage.control.X (only the panel carries the .controls segment, kanta.stage.controls.panel). A tag-tree dump from the failing run proved all 8 pads present as Buttons; the onCreate queue shortcut had already verified the same scheme at runtime. The check now derives the child prefix from controlsTag with the .controls suffix removed, for both the embedded and fullscreen panels. No app change.

## Emulator acceptance: Stage Controls bounds polled after layout (2026-11-25)

The below-stage bounds assertion ran synchronously right after opening the panel, before the layout pass that follows `setVisibility(VISIBLE)`, so it read stale geometry and failed. The check is now polled via `await` (`stageControlsBelowStage`, 50 ms / 15 s) between the open and the content/collapse verification; all assertions are unchanged.

## Emulator acceptance: Stage Controls below-stage geometry (2026-11-24)

The shipped portrait layout places the Stage Controls panel as a sibling below the Stage pane (`main.addView(embeddedControls)`), never layered over the Stage surface and never inside the keyboard scroll. The emulator walk's `checkStagePartyPads` now verifies that geometry (panel top at or below the Stage bottom) instead of the pre-v34 overlay expectation (FrameLayout parent inside Stage bounds), which the current product shape deliberately does not use. The pad/count/translucency assertions are unchanged.

## Emulator runtime acceptance: settings volume spec (2026-11-24)

The music volume slider `kanta.settings.music-volume` lives on the Voice & Scoring page (advanced options), not the Settings page. `hasSettingsWithoutDuplicateControls` therefore only asserts the absence of duplicate key/tempo/voice/intermission/background controls on Settings; the slider accessibility + persistence check runs on Voice & Scoring.

CI passes `kantaVersionCode` from its monotonic workflow allocation. Release metadata comes from the signed release APK (`aapt`/`apksigner`); publication verifies the pinned prior APK digest and requires the candidate to match the owner-approved signer fingerprint. Release signing uses repository Actions secrets only on trusted main runs; PR builds do not receive the key. CI resolves AAPT2 from Maven via AGP; machine-specific AAPT2 paths must never be committed (2026-11-24: a committed `/opt/kanta-local/aapt2` override broke `:app:processDebugResources` on runners) — local builds pass `-Pandroid.aapt2FromMavenOverride=...` on the command line. This is an intentional signer transition: APKs published before the new key cannot update in place and may need uninstall/reinstall, losing private app data. Physical-device acceptance of the new signer remains unverified; see `../../docs/OTA_UPGRADE_ACCEPTANCE.md`.
### Search keyboard
The Home/Search utility uses an app-owned persistent keyboard with a working number/letter switch and system-IME suppression.
<!-- nav removal doc sync -->
<!-- nav removal doc sync v2 -->

## Android bundled legacy key library

In every build, My Set's "Import legacy idx + blob" starts importing as soon as
both files are selected: the app carries a small labeled key table
(`app/src/main/assets/legacy-import-keys.txt`) and tries each real entry in
order, probing one record before the full import. If no bundled entry unlocks
the pair, a masked prompt asks for the key and IV supplied for those files;
that entry is never saved. Cancel or background the prompt to clear it. Only
import files you are authorized to use.

Owner decision (2026-11-24): the distributed shubidx/shubblob library is in
public circulation and KANTA is the player for it, so the key material ships
with the app; the container's AES layer is obfuscation, not a gate. Future
container versions get a table entry — a labeled block with a 64-hex-digit key
and 32-hex-digit IV, or a replacement of a `key=PLACEHOLDER` slot (limits:
64 KiB, 32 entries, pinned by `LegacyKeyTableRegressionTests`).

The **KANTA Private** build type (`:app:assemblePrivateLegacy`; separate
application ID and launcher label via `app/src/privateLegacy/res`, no public
updater) no longer requires an external key directory. The local-only
`kantaPrivateLegacyAssets` property can still shadow the table with a
48-byte `legacy-import-key.bin` in a directory outside the checkout, and CI
still refuses it.
See [ownership details](../../docs/ARCHITECTURE.md#android-bundled-legacy-key-library).

## Local B1/B2/D5/D8 work (not the published build)

The [local candidate notes](../../docs/ANDROID_LOCAL_B1_B2_D5_D8.md) track PCM
spectrum analysis, custom lyric fonts and remaining validation. The owner chose
to retain platform background-video playback (D5) and remove the nonfunctional
melody/guide sliders (D8), not implement separate mixing. Key, tempo and the
control deck's master volume remain supported. Nothing in this local candidate
has been pushed or accepted on a device.

## Local optional LRCLIB lyric lookup and font integration

See [LRCLIB behavior/privacy/test limits](../../docs/LRCLIB_LYRICS.md),
[B1 spectrum design](../../docs/B1-spectrum.md) and
[B2 font integration](../../docs/B2-custom-fonts.md).
The lookup is explicit, identified HTTPS on a worker, with user version selection,
existing-lyric preservation and playback/foreground generation fences. LRC line
timing is distinct from word sync; untimed text is previewed, not falsely timed.
Host signal/LRC and device font/JSON checks are registered but not executed locally.
Python/source checks are not a Java build or Android acceptance. No push, merge,
signing or deployment is authorized.

## Lyric-font persisted permission lint repair

The lyric-font folder result validates the returned read grant before persisting only `Intent.FLAG_GRANT_READ_URI_PERMISSION`. Passing that explicit allowed constant satisfies Android lint without broadening folder access or suppressing the check.

The first repair run (43237289035) passed build/lint but exposed malformed-font acceptance in the emulator: `Typeface.createFromFile` silently returned a default face. Font loading now uses API-26 `Typeface.Builder(File).build()`, which returns null for decode failure, preserving the existing malformed-font fallback assertion and selection-clearing behavior. Hosted revalidation is required.

## Android layout improvements — historical candidate notes

Home shows up to three reserved songs and the total queue count between search and the
lower keypad, with a View all action to the existing Queue screen. Empty queues show a
short explanation. Regular session updates preserve an in-progress code/title entry.
Now Playing separates transport and key/tempo controls to reduce crowding, adapting to
available width. Stages show a scrolling Up next strip from the existing reservation
queue, hidden when empty. No playback or queue ownership changes are intended.
These notes record the pre-validation candidate. See current published status above; device acceptance remains pending.

Home now has one Letters/Numbers toggle instead of a duplicate code icon. The shared
keyboard shows DEL for one-character deletion; Home labels its submit key RES. Exact,
unambiguous numeric codes reserve through the existing controller without starting
playback. Blank input is ignored; titles, unknown codes and duplicate codes open Search
for selection. Numeric lookup runs on the existing worker and is fenced against edits,
navigation, foreground loss and library replacement. Leading zeros remain significant.
The Java core runner covers exact, partial, missing, duplicate and zero-prefixed codes;
added instrumentation covers DEL/RES labels and deletion. These Java/runtime checks
remain unexecuted locally because the iSH JVM cannot start.


### Screenshot review correction before merge

The first PR run [43324277350](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43324277350)
passed build/lint, 55,735 core checks and 548 emulator checks. Screenshot review still
found unnecessary phone key/tempo stacking and default full-width adjustment columns.
Columns now use intrinsic widths and sit side by side when they actually fit, otherwise
stacking. Stronger instrumentation checks their horizontal bounds and normal-phone fit.
Home explicitly disables system IME after configuring input type; instrumentation also
checks that the system keyboard remains hidden after inline-keyboard interaction.
This correction requires a new exact-head CI run; the first run is not its acceptance.


### Window auto-IME regression

Run [43327147562](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43327147562)
passed build/lint/core and the stronger geometry assertions but failed the new system-IME
visibility assertion. Logcat records `SHOW_AUTO_EDITOR_FORWARD_NAV` from the server on
initial activity focus: field-level `setShowSoftInputOnFocus(false)` alone does not govern
that window policy. MainActivity now declares `stateAlwaysHidden|adjustResize`, preventing
window auto-show while preserving explicit editor/dialog requests. The failing emulator
assertion stays blocking; a new source guard pins the manifest policy. Revalidation is
required before merge, not a blind rerun of the failing commit.

## Android lint hardening — published preview

PR #204 introduced zero-warning debug/release lint and final APK native alignment
gates. Exact-head and post-merge validation, signed preview publication and website
deployment passed. See [evidence and remaining device limits](../../docs/ANDROID_LINT_HARDENING.md).
<!-- keypad-sounds+interlude: 2026-11-14 -->



## Legacy MKMD decoder malformed-stream handling (2026-11-16)

`MkmdDecoder` rejects raw-DEFLATE streams that exhaust input, require an unsupported
preset dictionary, or return no output without reaching a finished state. It also
releases `Inflater` native state in `finally`, including output-cap and malformed-data
failures. This prevents a corrupt encrypted legacy payload from spinning the import
worker indefinitely. The framework-free core regression uses an encrypted MKMD
envelope with zero compressed bytes and requires a prompt decode failure.

## Android Stage and Menu (v35)

Stage controls now use text with distinct icons: one stateful Play/Pause, Play on an individual result, Camera and Fullscreen each have their own glyph, and Camera shows state. The Stage Controls action expands a translucent panel directly below the Stage. All eight Party Pads remain visible at once, and the other controls each get a labeled icon row. The panel bounds to a share of the Stage area rather than replacing lyrics.

Menu uses a consistent MenuSurface card/header/row style for top-level pages and submenus, with original Android vectors for each action. The Menu order is Presets, KANTA Songbook, Stage & Visual, Intermission, Party Pads, Voice & Scoring, Settings, Browser Remote, Settings backup, Legal & Privacy, and Help. Stage & Visual has Background photos and Background videos tabs. Intermission owns between-song music. Party Pads is a first-class Menu page for pad triggers, clip assignment, reset, and volume. Settings no longer duplicates Stage visual, Intermission, Party, Voice, or Scoring controls. The control deck and standalone Search screen were removed; Home and Stage share the same persistent stage and controls.

The bundled welcome artwork now uses the provided landscape composition on landscape devices and the portrait composition on portrait devices, selected by live configuration. The landscape variant replaces the previous letterboxed portrait artwork.
<!-- CI runtime acceptance: companion search uses imported fixture display title. -->
## Android emulator SQLite compatibility (2026-11-25)

The Android runtime SQLite FTS4 implementation rejects the optional `content_rowid` parameter. The catalog now uses the default FTS4 content rowid, which is already aliased by the `songs.song_id INTEGER PRIMARY KEY`, and bumps the catalog schema version so existing databases recreate safely.
## Android PDF swipe compatibility (2026-11-25)

`PdfPageView` now dispatches a horizontal page swipe from release displacement when Android does not classify a synthetic or low-velocity drag as `GestureDetector.onFling()`. The existing fling path remains active and is guarded against duplicate page changes.
## Physical Android startup acceptance timing (2026-11-25)

The runtime harness allows 30 seconds for first Stage readiness. On the Sony SO-51A Android 12 device, cold startup completes after roughly 20 seconds under memory pressure; the app reaches the normal Stage and remains responsive. This increases harness synchronization time only and does not change product behavior or acceptance assertions.
## Animated-stage runtime screenshots (2026-11-25)

Runtime screenshot capture no longer treats `UiAutomation.waitForIdle()` timing out as a failure when a visible video or fullscreen stage is continuously rendering. Required target visibility, geometry, and control assertions still run; capture proceeds with the next compositor frame.
## Android FTS4 search compatibility (2026-11-25)

The Android catalog search query now avoids the unsupported SQLite FTS `rank` column. Matching remains bounded by the existing result limit; Android FTS4 does not provide this ranking column on the device implementation.
## Browser companion fixture title (2026-11-25)

The browser companion runtime check searches the imported fixture by its provider display title (`Original KAR`), matching the Android companion check and the catalog import contract.
## Missing-media recovery runtime fixture (2026-11-25)

The missing-media recovery check now seeds its synthetic absent-file reservation directly in the authoritative queue. The public `SessionController.reserve` API correctly accepts only catalog songs; the recovery scenario models a previously persisted reservation whose file later disappeared.
## Emulator runtime timeout (2026-11-25)

The Android runtime runner now allows up to 600 seconds for `RuntimeChecks` instrumentation. The emulator can spend over a minute booting and the full acceptance walk includes real media, browser companion, fullscreen, and screenshot checks; the previous 180-second subprocess limit terminated a still-running test before it could report results.
## Public updater menu entry (2026-11-25)

The public Android build exposes the existing verified OTA updater from **More → Support → App update**. The private legacy build continues to block public OTA installation because it uses a separate package/signing identity.
