# Android Home layout, keypad and stage queue hint

## Scope

- Home: up to three reserved-song rows, total queue count, empty state and View all;
  the weighted queue panel uses spare height above the lower inline keypad.
- One Letters/Numbers toggle; duplicate nonfunctional code icon removed.
- DEL deletes one character. RES reserves a unique exact numeric song code without
  starting playback. Titles, unknown and ambiguous codes require Search selection.
  Leading zeros are significant. Lookup runs off the UI thread with stale-result guards.
- Now Playing: spaced transport buttons, fixed circular action targets, responsive
  key/tempo separation and a distinct gap before the online-lyrics action.
- Embedded/fullscreen/external stages: bounded native Up next marquee showing queue
  order and singers, hidden when empty. Playback already consumes the current entry;
  intentional repeated-song reservations remain visible. The measured strip height is
  reserved below lyrics. No additional playback/FIFO owner, timer or permission.

## Candidate and evidence

Base: `22b8e375`. Branch: `work/android-home-player-spacing`.

Local preflight passes: 196 Python tests, 157 production Java structure checks,
59 Android workspace checks, companion and branding contracts, and whitespace checks.
Seven initial Home guards reject the pre-change source. Eleven Java core code-match
assertions and Android geometry/input/ticker instrumentation are added, but cannot run
on the local iSH JVM (`getcpu(2)` unsupported). Android platform/AAPT2/D8 are absent;
ADB has no device. No local APK build, emulator or physical-device acceptance.

**Hosted validation and publication: pending.** Owner authorized pushing the branch,
merging only after green checks, and the existing main signing/publisher/deploy pipeline.
Independent review workers did not return usable final reviews; no independent acceptance
is claimed. Hosted evidence will be added after it exists, not inferred from local checks.

## Remaining acceptance

- Exact-head Android build/lint, core regressions and blocking emulator instrumentation.
- Physical phone/tablet: Home queue/keypad fit, both keyboard modes, RES success and
  ambiguity fallback, rapid edits/navigation/backgrounding, rotation and large text.
- Stage: readable scrolling/lyric clearance with long and duplicate titles, reorder,
  empty queue, foreground transitions, fullscreen and an actual external display.
- Visual screenshots need review; passing source assertions is not visual acceptance.
- No iOS behavior changes or new physical-device audio/camera acceptance are claimed.
