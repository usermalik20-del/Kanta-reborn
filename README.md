## 2026-11-28 — Bundled font device check emits a bounded diagnostic
While the CI emulator font check is being resolved, `FontStoreChecks` now prints, to stdout (captured in the CI job log, not only the logcat artifact), which bundled-font sub-step fails on a runtime: the APK asset stream read, the `Typeface.Builder(File)` decode of the copied bytes, and the `Typeface.Builder(AssetManager)` path guarded against hanging. This is diagnostic only; all assertions are unchanged.

## 2026-11-28 — Bundled font load is file-first (970)
The 969 asset-then-file fallback still failed the CI emulator font check: on the software-rendered x86_64 image the stored-asset path for `Typeface.Builder(AssetManager, …)` does not complete, so an asset-FIRST order blocks the single font thread and the file fallback is never reached. `BundledLyricFonts.load` now builds from a temp file first (the proven path the custom-font loader already uses) and only falls back to the stored asset; the result is cached by `FontStore`, so the small copy happens once per face. This also keeps the fast, portable path on real devices.

## 2026-11-28 — Bundled font loading hardened (969)
Following the 968 `noCompress` fix, bundled lyric fonts now load through the asset
manager first and, where a runtime cannot map the stored asset (notably the
software-rendered x86_64 CI emulator), fall back to copying the font bytes to a temp
file and building from the file — the same proven path custom fonts already use. The
device font check deadline was raised to 30 s to suit the 2-core / 2 GB CI emulator.

## 2026-11-28 — Bundled lyric fonts now load; stable two-line lyrics (968)
**Bundled lyric fonts fixed.** All bundled lyric TTFs were shipped deflate-compressed
in the APK, but asset-manager font loading (`Typeface.Builder` over `assets/`) requires
the font to be stored uncompressed — so every bundled font (and the default selection)
failed with "could not be loaded". `build.gradle` now marks `ttf` as `noCompress`, so the
fonts are stored and map cleanly.
**Two-line lyrics stabilised.** The next line now renders at the same size as the sung
line, so the two-line block keeps a stable height and the rows no longer grow or jump as
the active cue advances. The sung-portion reveal is a fixed bright amber, visible against
any active lyric colour (a lightened copy of a warm/gold active colour was invisible).


## 2026-11-28 - CI build fix
The AAPT2 override that unblocked local ARM64 builds was accidentally committed to `gradle.properties`; CI failed trying to exec a path that only exists on the local host. The override now lives only in the ignored `local.properties`, restoring CI's native AAPT2.

## 2026-11-28 - AAPT2 override kept out of tracked build config
The machine-specific AAPT2 override that unblocked local ARM64 builds was left in `apps/android/gradle.properties`, and CI failed every AAR transform trying to exec a path that only exists on the local host. The override now lives only in the git-ignored `local.properties`; CI resolves AAPT2 from its own build-tools again, and `kantaVersionCode` remains the only version knob in the tracked file.
## 2026-11-28 - Stage search row, mic input controls, large SoundFonts, font picker (967)
Four upgrades. The Stage screen now has one search bar between the Controls button and the Fullscreen button, right above the Play/Reserve/Next/Stop row - tap it to type a song title or code. Voice & Scoring gains a microphone picker (automatic, phone, wired, USB or Bluetooth input), an input gain slider (-12 to +18 dB), a noise gate for the voice you hear in your headphones, and a red CLIP indicator when the microphone signal is too hot. Large professional SoundFonts (for example the 4.2 GB HiDef bank) now pass the structure check, and a bank that simply needs more free memory than the device has is reported as such instead of as a broken file. The font picker shows every built-in style drawn in its own typeface, the full set of 31 bundled styles is restored, and the stage preview updates the moment a font is chosen.

## 2026-11-27 - Interlude timer: auto-start with visible countdown (966)
The last piece of the interlude spec (G4): the Interlude tab gains an Interlude Timer (Off / 5s / 10s / 15s / 30s, default Off). When armed, after a song ends the next reserved song starts automatically when the countdown finishes, and a countdown bar appears on the stage ("Next song starts in 0:15" with a progress bar and a Start now button). Start now - or any manual start - skips the wait. Off keeps the traditional 3-second auto-advance.

## 2026-11-27 - Interlude typography, media, and video (964)
Batch F: the Interlude tab shares the lyric font picker (KANTA Default, Bold Videoke, Clean Sans, Retro LED, Classic Serif, custom .ttf), adds a glow toggle and a drop-shadow slider for text legibility over bright media, a marquee speed (Slow/Normal/Fast) and direction (RTL/LTR) selector, an image scale slider (50%-150%), and image placement (Above / Dimmed background / Side-by-side in landscape). A new looping-video slot (silent, muted, REPEAT_MODE_ONE, paused when backgrounded) renders behind the interlude in both the embedded stage and fullscreen.## 2026-11-27 - Interlude & stage overlays (965)
Batch G of the interlude/overlay spec: (1) Up Next banner now has a style picker (Ticker Bar / Floating Card / Corner Badge presentation variants) plus an optional songs-remaining count; (2) the reactive interlude visualizer gained a style selector (Classic EQ Bars / Circular Bass Ring / Smooth Waveform) and a sensitivity slider; (3) new Ambient interlude audio section with a Background BGM loop toggle (gates the existing standby lounge loop) and a Transition Chime that plays the Ding pad when the interlude hands off into a vocal countdown. Auto-start interlude timer deferred to the next sub-batch.




## Editable interlude quick messages + live tokens (2026-11-27)

The three static interlude quick-message chips became a horizontal editable set: the
same three built-ins plus unlimited user messages, persisted in user order as a JSON
array in SettingsStore (no new backend). Tapping any chip fills the message field and
the live preview; long-pressing a custom chip opens a Rename / Delete sheet; a "+ Add"
chip prompts for a new message. The interlude message also supports live placeholder
tokens resolved at render time in all three surfaces (embedded LyricStage via the
presenter, fullscreen StageView, and the MiniStagePreview): {next_song} (queue head
title), {next_artist} (queue head singer), {queue_count} (reserved songs), and {time}
(HH:mm), so hosts can write phrases like "Next up: {next_song}" or "It is {time} -
break". Resolution lives in SessionController.resolveInterludeTokens/resolvedInterludeText
and is applied where the text is pushed/drawn, never in the stored raw value.

Files changed:
- settings/SettingsStore.java - interlude.quickMessages key + raw accessors.
- SessionController.java - quick-message raw accessors + resolveInterludeTokens /
  resolvedInterludeText (queue + clock based).
- stage/StagePresenterImpl.java, StageView.java, ui/MiniStagePreview.java push path -
  resolved interlude text.
- MainActivity.java - addQuickMessageChips + add/edit/delete dialogs.
- res/values/lyrics_style_strings.xml - add/rename/delete labels.

## Theme presets + display & session controls (2026-11-27)

User theme presets: a horizontal preset bar sits above the Intermission editors with a
"Save current" chip and one chip per preset. Three built-ins ship non-deletable
(Classic Videoke, High-Contrast TV, Minimal Dark); user presets are unlimited and
persist as a JSON array in SettingsStore (no new storage backend). Tapping a chip
applies it instantly to both the live preview and the active stage; long-press opens
Rename / Overwrite / Delete (built-ins only allow Overwrite). A preset captures every
lyric-styling property (font, alignment, lines, caps, bold, outline, colors, upcoming
opacity, title, sync offset) plus interlude/background settings (color, animation,
size, image scale/placement, dim, blur, slideshow, safe zone) via KantaThemePreset
capture/apply over SettingsStore.

Display & session controls: a new Display section in the Control page holds an
orientation lock (Auto / Landscape / Portrait, applied via setRequestedOrientation at
start and on change), a keep-screen-on toggle (the previously unconditional
FLAG_KEEP_SCREEN_ON is now held only while the toggle is on and a song is playing or
loaded, refreshed from render()), an OLED burn-in shift toggle (a 60-second handler in
both the embedded StagePresenterImpl and the fullscreen StageView randomizes the
static chrome - song title header and KANTA watermark - by up to +-4dp without moving
the lyric rows), and a TV safe-zone margin slider (0-10% inset applied to the lyric
layout width/height, clip rect and chrome anchors in both LyricStage and StageView so
mirrored/overscanned displays keep the text inside the picture). The embedded
LyricStage also finally consumes the Batch A sync offset (cue index and word position
now add style.syncOffsetMs), so the offset shift works on the primary stage, not only
the fullscreen surface.

Files changed:
- apps/android/app/src/main/java/ph/kanta/reborn/settings/KantaThemePreset.java (new)
  - JSON value model + 3 built-ins + capture/apply over SettingsStore.
- settings/SettingsStore.java - themes.presets key + display.orientation,
  display.keepScreenOn, display.oledShift, display.safeZonePercent.
- SessionController.java - preset list/save/rename/overwrite/delete/apply + display
  accessors.
- stage/LyricStage.java - safeZonePercent/safeInset + burnShift fields, geometry
  insets, title shift, sync-offset consumption.
- stage/StagePresenterImpl.java - safe zone + OLED armed state pushed on refresh.
- StageView.java - safe inset, chrome shifts, 60s OLED roll.
- MainActivity.java - preset bar + menu, Display section, orientation + wake lock.
- res/values/strings.xml - preset + display labels.

## Stage backgrounds: dim, blur, photo slideshow (2026-11-27)

The Stage & Visual Photos tab gained a "Stage readability" section: a Dim slider
(0-85%) that projects a top-level scrim over any background source (photo, BGV video,
camera, gradient) in both the embedded BackgroundStage (a dedicated scrim child added
above the swapped media views, re-attached after setSource) and the fullscreen StageView
canvas (dimPaint rect after the base scrims, before the lyrics), and a Blur slider
(0-20dp) applied as a RenderEffect on the whole BackgroundStage subtree on API 31+
(no-op below). A Photo slideshow toggle cycles the imported photos every 5/10/20s
(interval pills) with a 400ms crossfade between two ImageViews; BackgroundStage owns
the timer (Handler on the main looper), pauses it when the stage is not visually
active or detached, and keeps its position across refresh() re-invocations via a
file-signature guard (same slides + interval only reschedules). Slideshow needs at
least two imported photos and is overridden by an explicit camera or video selection;
single photo/video imports clear it. The seekListener now flags draggingVisualsControl
and the render() editor fast-path was generalized to the Stage & Visual page as well as
Intermission, so dragging any of these sliders live-refreshes only the preview instead
of rebuilding the page.

Files changed:
- apps/android/app/src/main/java/ph/kanta/reborn/settings/SettingsStore.java -
  stage.dimAlpha, stage.blurDp, stage.slideshow, stage.slideshowIntervalSec.
- apps/android/app/src/main/java/ph/kanta/reborn/SessionController.java - clamped
  accessors + slideshowPhotoFiles() from the imported photo entries.
- apps/android/app/src/main/java/ph/kanta/reborn/stage/BackgroundStage.java -
  dim scrim child, RenderEffect blur, slideshow state machine.
- apps/android/app/src/main/java/ph/kanta/reborn/stage/StagePresenterImpl.java -
  projects dim/blur/slideshow into the background renderer on refresh.
- apps/android/app/src/main/java/ph/kanta/reborn/StageView.java - fullscreen dim
  scrim (dimPaint).
- apps/android/app/src/main/java/ph/kanta/reborn/MainActivity.java - readability UI,
  drag flag, generalized editor guard.
- apps/android/app/src/main/res/values/strings.xml - readability labels.

## Lyric sync offset with stage HUD pill (2026-11-27)

Singers can now correct lyric timing without re-importing. The Lyrics editor gained a
"Lyric sync" section: a -50/+50ms stepper, a 0..4000 slider (mapped to -2000..+2000ms)
and a tap-to-reset value label. The same correction is available mid-song from a compact
pill on the Stage HUD (tag kanta.stage.sync, top-right): -/value/+ where the value
applies a per-song override (keyed by song id in SettingsStore lyric.syncOffsetOverrides)
and tapping the value clears it back to the global default (lyric.syncOffsetMs, clamped
+-2000). The engine applies the effective offset to the timed-cue lookup only:
SessionController.lyricPositionMs() feeds StageView's activeIndex/highlight and
LyricStage receives the per-song offset through the StageStyle snapshot
(syncOffsetMs field), so cue selection, word highlighting and pitch marking all move
together while audio position, scoring and countdown stay on raw playback time. The
pill renders only while a song with timed cues is on stage and refreshes on every
render. While the slider is dragged the Intermission fast-path in render() also skips
the rebuild (draggingLyricSync), keeping the page and preview alive.

Files changed:
- apps/android/app/src/main/java/ph/kanta/reborn/settings/SettingsStore.java -
  lyric.syncOffsetMs + JSON override map with clamped accessors.
- apps/android/app/src/main/java/ph/kanta/reborn/SessionController.java -
  lyricSyncOffsetMs, lyricOffsetForCurrentSong, setSongLyricOffsetMs, lyricPositionMs.
- apps/android/app/src/main/java/ph/kanta/reborn/stage/StageStyle.java - syncOffsetMs
  snapshot field.
- apps/android/app/src/main/java/ph/kanta/reborn/stage/LyricStage.java - offset in the
  two cue-position reads; default snapshot updated.
- apps/android/app/src/main/java/ph/kanta/reborn/stage/StagePresenterImpl.java -
  pushes the effective offset into the snapshot.
- apps/android/app/src/main/java/ph/kanta/reborn/StageView.java - lyricPositionMs in
  refresh + draw.
- apps/android/app/src/main/java/ph/kanta/reborn/MainActivity.java - editor section,
  HUD pill, refresh + helpers, drag guard.
- apps/android/app/src/main/res/values/strings.xml - lyrics_sync_* labels.

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

## Embedded Stage gets the full style, and the stage motion calms down (2026-11-26)

The Stage that sits under Menu now renders exactly what the fullscreen/PiP
stage and the Intermission preview show: the selected font, ALL CAPS/Bold and
outline, the active and upcoming colors, alignment, line mode, the song
title with the singer, the vocal-countdown dots and the styled interlude.

Two readability fixes: the sung lyric line now fades out **in place** while
the next line fades in on the same row (nothing jumps up), and the standby
interlude fades out across the vocal countdown so it no longer blocks the
first sung line. The scrolling-marquee interlude scrolls at a readable
text-proportional speed, and the score celebration sound (about 12 seconds)
now plays to natural completion instead of stopping when the next song starts.
The SoundFont memory cap is now dynamic: a 3 GiB floor that grows with the
device's RAM, so large banks (for example a 4.2 GiB file) can be imported on
phones with enough memory.

The Lyrics tab now offers a horizontal font row (KANTA Default, Bold Videoke,
Clean Sans, Retro LED, Classic Serif, plus custom font import), ALL CAPS /
Bold / outline toggles, active-lyric color swatches, an upcoming-lyric dim
slider, Center/Bottom/Left alignment, 2-lines vs Multi-line, stage overlays
(song title, KANTA watermark, vocal countdown dots) and a reset. The Interlude
tab saves the message as you type, adds quick-message presets, message color,
Static/Pulse/Scrolling-Marquee animation, image size and placement controls,
and toggles for the Up Next banner, reactive visualizer bars and watermark.
Every control updates the live Stage preview and persists across restarts.

The SoundFont memory budget raised from 1.5 to 3 GiB per worker so the larger
bundled GM banks keep full headroom; allocation failures still fail safely.
## PiP shows only the Stage (2026-11-26)

Minimizing during a song now opens the Picture-in-Picture window with just the
stage screen: the brand header, the key/tempo readout, the utility list and the
bottom navigation are hidden while the app is in PiP, and the full window
returns when you tap the PiP window. Leaving the app while the fullscreen
Stage is open falls back to the embedded Stage first, so no dialog stays
pinned over the PiP window.


## Android visual editors, interlude, unified folder import (2026-11-26)

The Stage & Visual destination now previews every choice: a small faithful stage
rendering sits above the Photos, Videos, Lyrics and Interlude tabs, showing the
current background (photo, video frame or bundled scene) with the content being
edited. The Interlude — the message and image shown while a reserved song waits
for its first cue — is fully customizable (message, size, PNG/GIF image) and
renders in both the embedded and fullscreen Stage. "Add Karaoke Folder" imports
songs, background photos, background videos and a legacy IDX + BLOB pair from a
single folder in one pass. The portrait Stage now splits 50/50, the App update
panel uses the app theme, minimizing during a song enters Picture-in-Picture,
and the Help page is an illustrated step-by-step guide.

## Search-results dock overlaps Stage (2026-11-24)

The in-app search-results panel is docked as a persistent overlay on the Stage (rather than below it), which the runtime acceptance now accounts for.

## Compact landscape Home: no duplicate queue card (2026-11-24)

A compact landscape Home now hides the Up next queue card from construction (previously only after the first measurement), so it never duplicates the Stage's Up next ticker in any state, including the first frame.

## Compact landscape Home: no duplicate queue card (2026-11-24)

A compact landscape Home now hides the Up next queue card from construction (previously only after the first measurement), so it never duplicates the Stage's Up next ticker in any state, including the first frame.

## Compact keypad backspace icon (2026-11-24)

In compact landscape row heights the Delete key now shows the ⌫ glyph instead of the DEL text (too short to stay legible); normal rows keep DEL. The spoken label ("Delete one character") is unchanged. The runtime acceptance check for this behavior first executed on the CI emulator when the consolidated tree reached main.

## Android CI AAPT2 portability fix (2026-11-24)

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

A machine-specific AAPT2 override (`android.aapt2FromMavenOverride=/opt/kanta-local/aapt2`) had been committed in `apps/android/gradle.properties`; the path exists only on one build machine, so GitHub runners failed AAR resource compilation with "Specified AAPT2 executable does not exist". The override is removed: CI uses the AAPT2 that AGP resolves from Maven, and local builds that pin an AAPT2 pass `-Pandroid.aapt2FromMavenOverride=...` on the command line instead of a tracked file.

## Android legacy import progress and file order (2026-11-21)

A full 48,000-record legacy pair import takes minutes on a phone, and the pair
picker gave no hint about which file is which. The import now reports
"Importing legacy songs: done/total…" live, the two bounded copy steps identify
their role (and detect the swap by the BLOB's MKMD prefix, with a specific
"select the IDX first / BLOB second" message), and a step-1 dialog tells the
user the smaller IDX file is requested first. Host verification imported all
47,997 decodable records of a real pair in 318 s; device acceptance is tracked
with the private build.
## Android private-variant launcher label (2026-11-20)

The opt-in **KANTA Private** Android build now gets its launcher label from the
variant resource source set (`apps/android/app/src/privateLegacy/res`) instead of a
manifest placeholder. Public builds keep the same `app_name` string, ID, and
behavior; the change resolves an unused-resource lint finding in the private
variant and keeps the manifest reference statically visible to lint.

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
## Landscape mode and camera rotation (2026-11-16)

- Android reflows between portrait and landscape without restarting the karaoke session.
- A primary-stage camera preview rebinds to the replacement primary stage. Fullscreen or
  external-stage previews stay on their selected stage during primary-screen rotation.
- Physical-device rotation and camera acceptance remain outstanding; emulator checks are not
  a substitute for testing on the target phone/tablet.

---
# KANTA Reborn

**Karaoke with your own songs, on Android, iPhone and iPad.**

Import your music, follow the lyrics when available, and line up songs for your next singing session. Personal backgrounds and optional voice tools help you make it your own. Features differ between Android and iPhone/iPad.

## Try KANTA

- **[Android download and installation](https://kanta-public.vercel.app/android)**
- **[iPhone / iPad download and installation](https://kanta-public.vercel.app/ios-download)**
- **[How to use KANTA](https://kanta-public.vercel.app/guide)**
- **[Help](https://kanta-public.vercel.app/support)**

**These are test versions, not Google Play or App Store releases.** Check the download page for what is included and any installation requirements.

The iPhone/iPad download cannot be installed directly: it needs a separate, trusted tool to prepare it for installation using your own Apple account. KANTA's website does not ask for your Apple password.

## Keypad sounds, interlude once, BGV access, SoundFont progress (2026-11-14)

- **Keypad sounds**: Fixed `SoundPool.OnLoadCompleteListener` to use anonymous class for D8 desugaring compatibility; keypad tones now load on IO thread for faster readiness.
- **Standby interlude**: Bundled welcome track now plays once on startup (not looping); custom intermission tracks still loop via `PartyAudio.loop()`.
- **Background Video (BGV)**: Added prominent entry in More ▸ Background Video for managing stage background videos.
- **SoundFont downloads**: Added progress dialog with live percentage for FluidR3 GM, MuseScore General, and SGM-v2.01 downloads via `SessionController.DownloadProgressListener`.

---
## What you can do

- **Bring your own songs:** import karaoke files (MIDI/KAR) or audio files (MP3/WAV). Lyrics depend on what the file contains. Android can also import KANTA song libraries (an IDX + BLOB pair): the shipped library unlocks automatically, and for any other library you are asked for the unlock details supplied with it, which KANTA does not save. Malformed or truncated MKMD data in either direct decoding or paired legacy import is rejected promptly; a damaged legacy record is skipped without stalling the import. Party sounds are bundled recordings. Legacy-library compatibility is experimental and may not work with every library or play every song.
- **Plan your session:** search your library, line up songs and keep saved song sets. The Android preview now shows reserved songs above the keypad, accepts exact song codes with RES, and displays an Up next hint on stage. Playback controls have more room.
- **Personalize the screen:** choose backgrounds or use your own photos and videos. On Android, open Menu → Stage & Visual to shuffle photos or videos. You can also choose a lyric font from a folder you grant read access to.
- **Try optional voice tools:** microphone input, voice effects and scoring are available for testing, with different support on each platform. Android includes separate Small Room, Room, Hall and Concert Hall effects.

For music files that need an instrument sound library, KANTA uses files called **SoundFonts**. See the guide for setup.

<!-- party-sounds-assets-committed -->
## Keypad press sounds & standby interlude (2026-11-14)

The on-screen keypad now plays a short tone for each key press (0–9, Delete, Reserve). Tones are silent while a song is playing on stage. A bundled standby welcome (backing track + branding line) plays between songs on the empty stage; the owner can override with a custom track or disable it in Party Mode.

## Now Playing divider removed & scroll fade (2026-11-13)

The stray `|` divider between Key and Tempo columns in the Now Playing header was removed. A vertical fading edge (24 dp) was added to the main scroll view so content fades softly into the floating bottom dock instead of a hard cutoff.

## Before you install

- **Back up your music and saved work.** Switching Android test versions may require uninstalling the old app, which can erase its saved data.
- **Download access varies by platform and build.** Each download page shows whether a GitHub account is required and any expiry limits; always use the current page rather than an old build link.
- **Real-device testing is not complete.** Voice quality, microphone delay, camera use and external displays still need testing on physical devices. Passing automated tests does not guarantee they work on your device.
- **Remote control is experimental.** Compatibility and setup differ between platforms; use a trusted private network and share pairing codes only with people you trust. Closing the host screen or leaving the app disconnects the session; commands are not replayed after reconnect.

- **Remote control is experimental.** The legacy iOS Bonjour remote is plaintext and intended only for a trusted local network; its new connection cap and pairing timeout limit idle sessions but do not encrypt traffic or rate-limit PIN guesses. Browser Remote and the encrypted Android companion are separate paths, not upgrades to that legacy channel. Share pairing codes only with people you trust.
- **YouTube playback is not available.**

## Privacy

Camera and microphone features are optional. The website asks before enabling optional analytics and advertising; those website scripts and ads are not included in the native apps.

Read the [privacy information](https://kanta-public.vercel.app/privacy) before using KANTA.

## For contributors

Build instructions, test results and engineering details live in the developer documentation—not on this front page. A daily automated check re-verifies the website, download contracts, and Android/iOS builds every day; see [testing and release requirements](docs/TESTING.md).

- [Android development](apps/android/README.md) · [iOS development](apps/ios/AGENTS.md) · [Website development](apps/web/README.md)
- [Architecture](docs/ARCHITECTURE.md) · [Engineering rules](docs/ENGINEERING_ARCHITECTURE_CONTRACT.md)
- [Testing and release requirements](docs/TESTING.md) · [Download verification](docs/VERIFIED_DOWNLOADS.md)
- [Outstanding work](docs/UNFINISHED_WORK.md) · [Android engineering records and testing limits](docs/ANDROID_LOCAL_B1_B2_D5_D8.md) · [Private Android v34](docs/ANDROID_PRIVATE_V34.md)
- [Plain-language writing guide](docs/USER_FACING_LANGUAGE.md)
- [Android lint validation and release evidence](docs/ANDROID_LINT_HARDENING.md) · [Android layout and stage search](apps/android/README.md#android-responsive-stage) · [Android SoundFont downloads](apps/android/README.md#android-soundfont-download-compatibility) · [Bundled legacy key library](apps/android/README.md#android-bundled-legacy-key-library)
- [Archived engineering notes from the previous README](docs/README_HISTORY.md) — historical records, not current download or feature guidance.
## AdSense policy truthfulness pass (2026-11-17)

Fixed public-site trust and content-quality issues ahead of AdSense re-review:
- Removed inflated reading times: all 15 blog articles now show estimates matching their
  actual word counts and the `blog.js` values (previously 8–18 min claims on 2–7 min pages).
- Added self-referential `rel="canonical"` links to every main page and blog article
  (category pages already had them).
- Removed the unsupported "ultimate karaoke app" superlative from the home description.
- Added `/about` (factual site-identity page: what KANTA is, what is tested vs open,
  how the site is maintained, GitHub issue tracker as the contact path); registered in
  `vercel.json` routes, `api/page.js` PAGES, `sitemap.xml` and `tools/verify-website.py`
  PUBLIC_ROUTES; linked from every footer.
- Corrected blog claims that contradicted the verified feature copy: party pad list and
  intermission behaviors (party-effects article), companion pairing/session/security facts
  (companion article, matches support.html), external-display settings path and
  dual-screen status, microphone setup flow (no calibration routine exists), SPICE
  accuracy wording and mic-capture phrasing, `.kanta-book` scope in two articles
  (metadata-only catalog; browser reader; no in-app import/restore flow).

Validation: `python3 tools/verify-website.py` passes; website security, Vercel workflow,
documentation-sync, customer-legal-copy and site-page-handler suites pass locally.
Node `--check` runs are unreliable in this iSH environment (intermittent hangs), so the
Node steps in `website-validation.yml` remain the authoritative gate for those files.
No ad code, consent gating, or ads.txt changes; no claims of AdSense approval.

**Production 404 fix (same pass):** all 15 blog articles, `/blog/all` and the six blog
category pages returned 404 in production: `vercel.json` dispatches those routes to
`/api/page`, but `PAGES` in `api/page.js` contained no blog entries (pre-existing since
the blog routes were added). Added the 22 missing `PAGES` entries, exported `PAGES`, and
added two regression tests: every `/api/page` route in `vercel.json` must resolve to a
`PAGES` entry, and every blog entry must point at an existing file.

**Function bundle fix (follow-up):** after adding blog `PAGES` entries, production
returned 500 on those routes because `functions["api/page.js"].includeFiles` was `*.html`,
which bundles only root-level HTML — the function never received `blog/*.html`. Changed
to `**/*.html` and updated both guards (`tools/verify-website.py`,
`tools/tests/test_website_security_contract.py`).

## Website validation fixes (2026-11-12)

Fixed verify-website.py entry-point and link/sitemap/CSP validation for blog routes. No user-facing changes.

## CI fix (2026-11-12)

Fixed trusted_main check in Android validation workflow by updating tracking branch before ancestor check.
<!-- keypad-sounds+interlude: 2026-11-14 -->

**Publisher tag-race fix (2026-11-18):** `platform-download-publishing.yml` now passes
`--target ${SOURCE_SHA}` to `gh release create`. Without it, the tag defaults to the
runner's checkout head, which can be a descendant of the claimed source SHA when main
advances between build and publish — making the tag object disagree with its
`-preview-<sha>` suffix and tripping the release-notes guard.


## Private Songbook CSV safety

The browser Songbook keeps its AES-GCM envelope, key derivation and decrypt routine
byte-identical to the archived reader reference. After authenticated decryption, the
four catalog fields are parsed and re-serialized for export; formula-leading cells
(including values with leading whitespace/control characters) are forced to text.
Exports do not reuse the raw decrypted CSV string, and the parser enforces the song
count limit even when the final row has no trailing newline.

## Private Songbook interaction safety

The browser Songbook keeps unlock/lock, page navigation and keyboard controls in its
core reader script. Decorative effects must not reveal the reader before decryption
succeeds or replace the core page-navigation state. The existing CSS flip-sheet
animation is driven by the core page state; decorative JavaScript only owns effects.
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
