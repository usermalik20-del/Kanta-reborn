# Handoff — Android runtime evidence regression (KANTA Reborn)

Repo: `/var/minis/Kanta-reborn` · Date: 2026-11-10 · Branch: `fix/runtime-standby-check-order`
Open PR: **#192** (https://github.com/usermalik20-del/Kanta-reborn/pull/192) — **do not merge yet**

## The user's question

> "The android validation did not have the screenshots now, what happened?"

## Answer

The Android emulator artifact for merge commit `818a73f` (run 801) contained **10 entries, 1 PNG**
(`browser-remote.png`) instead of the 19 UI screenshots the workflow declares. Cause: a
check introduced in that same commit ran before the view it inspects was assigned, so
instrumentation **crashed on its first assertion** — before any `capture()` — and every
acceptance step downstream was skipped.

```
E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: java.lang.AssertionError: Standby check needs the persistent stage
E AndroidRuntime:   at ph.kanta.reborn.RuntimeChecks.check(RuntimeChecks.java:34)
E AndroidRuntime:   at ph.kanta.reborn.RuntimeChecks.lambda$stageStandbyChecks$252(RuntimeChecks.java:1280)
```

`am instrument` → `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`
`tools/run-android-runtime.py` → *"Runtime instrumentation did not report acceptance"*.
Run 798 (`6fd4b0a8`, before this work) logged `KANTA runtime: 416 checks passed` + all 19 captures.

## Why CI showed green (two masking mechanisms)

1. Step *"Android emulator runtime checks"* has `continue-on-error: true`
   (`.github/workflows/android-validation.yml:167`).
2. Artifact upload uses `if: always()` (line ~180), so evidence uploaded with logs + APKs
   while silently missing every screenshot.
3. `tools/run-android-runtime.py` raises on failure, but per (1) that never fails the job.

**A green Android run therefore does NOT prove the runtime checks passed.**

## Work completed in this segment

### Fixed and pushed (commit `d1d1a4b`)

`RuntimeChecks.onStart()` reordered so the stage is resolved before it is inspected:

```java
await(() -> session.ready, "Library did not open");
await(() -> session.stageReady, "Stage media store did not become ready");
capture("library_ready.png");
check(state(() -> contains(..., "Search songs, artists, or enter code")), ...);
runOnMainSync(() -> persistentPrimaryStage = ...findViewWithTag("kanta.stage.primary"));
assertPersistentStage(null);
stageStandbyChecks();          // <-- must come after the assignment
capture("home_dashboard.png");
```

Removed a redundant `await(() -> session.stageReady, ...)` that preceded the call.

**Regression guard** added: `tools/tests/test_android_runtime_runner.py`
`test_standby_checks_run_after_the_stage_view_is_resolved` — asserts the assignment
precedes the call in `onStart()` and that both standby helpers still read the field.
Verified by reintroducing the old ordering (test fails) and restoring (test passes).
Note: file now defines `ROOT = parents[1]` and `REPO_ROOT = ROOT.parent`.

### Local validation passed

- `check-java-structure` — 147 files, no nested method declarations
- `check-android-workspace-contract` — 59 checks passed, 0 failed
- `python3 -m unittest discover -s tools/tests` — 162 tests OK
- `preflight` — PASS
- All five Android owner docs prepended with a fix note (synced)

## ⚠️ The fix exposed a SECOND, UNRESOLVED failure

Run on PR #192 head `d1d1a4b` (run id **42789716656**) — job concluded `success`, but the
runtime step log shows the FIRST assertion now passes and a **new** one fails:

```
java.lang.AssertionError: Karaoke standby stage showed a frozen background instead of live BGV video
	at ph.kanta.reborn.RuntimeChecks.check(RuntimeChecks.java:34)
	at ph.kanta.reborn.RuntimeChecks.await(RuntimeChecks.java:46)
	at ph.kanta.reborn.RuntimeChecks.stageStandbyChecks(RuntimeChecks.java:1309)
	at ph.kanta.reborn.RuntimeChecks.onStart(RuntimeChecks.java:120)
```

Assertion source (`RuntimeChecks.java:1306-1309`):

```java
final long[] first = new long[1];
runOnMainSync(() -> first[0] = standbyLiveFrame());
check(first[0] >= 0, "Standby stage did not expose a live video frame counter");
await(() -> standbyLiveFrame() > first[0] + 5,
    "Karaoke standby stage showed a frozen background instead of live BGV video");
```

**Root cause NOT yet determined.** Three candidates, evidence below. Do not assume which.

### Candidate A — the counter is never driven at standby (instrumentation/design)

`standbyLiveFrame()` reads `StagePresenceOverlay.liveFrame()`, incremented **only** by
`recordLiveFrame()`. On the persistent stage that call lives inside `frameTicker`
(`stage/StagePresenterImpl.java:226-236`):

```java
if (presenceOverlay.getVisibility() == View.VISIBLE) {
  if (backgroundStage.getSource() == BackgroundStage.Source.BGV
      && backgroundStage.getVideoSurface() != null
      && backgroundStage.getVideoSurface().isAvailable()) {
    presenceOverlay.recordLiveFrame();
    presenceOverlay.invalidate();
  }
}
```

`frameTicker` is posted **only** by `startPlayback()` (line 316) and `resumePlayback()`
(line 331), and removed by `pausePlayback()` (325), `stopPlayback()` (338),
`onDetachedFromWindow()` (806). **At standby nothing is playing, so the ticker is not
running and the counter can never advance.** If so, the assertion is unpassable by
construction at standby — the "live video" proof needs a driver that runs while idle.

### Candidate B — BGV is not the source at standby (real product bug)

`StagePresenterImpl.refresh()` (lines 652-666) picks the source each tick:

```java
if ("camera".equals(session.stageKind()) || session.cameraPreviewEnabled()) → CAMERA
else if (("photo"|"scenic") && session.scenicBitmap() != null) → PHOTO
else if ("video".equals(session.stageKind()) && session.stageVideo() != null) → BGV
else → SOLID                     // <-- standby falls here
```

`SessionController.restoreStage()` seeds a default bundled clip when no saved stage exists
(`BundledBgvCatalog.defaultClip()` → `copyTo` → `stageStore.setVideo` →
`settings.setBgvDefaultSelected(true)` → `applyStage(selection)`). Whether that yields
`stageKind == "video"` at launch was **not yet confirmed** — check `applyStage()` and
whether `refresh()` runs before/after `stageReady`.

### Candidate C — emulator decode limitation

Bundled clips are H.264 (verified with ffprobe): 3× 1920×1080 and 2× 1280×720, 24 fps.
Emulator advertises `video/avc` (`c2.android`, `libcodec2_goldfish_avcdec`) via swiftshader.
In the **attached** logcat (which is run 801, the pre-fix run) the app window
`07:41:15–07:41:37` contains **zero** occurrences of `MediaPlayer`, `NuPlayer`,
`setDataSource`, `video_`, `video/avc`, `avc1` — no BGV MediaPlayer ever started. But that
run crashed early, so this is weak evidence; re-derive from the run-192 logcat.

## Key files

| Path | Why |
|---|---|
| `apps/android/app/src/androidTest/java/ph/kanta/reborn/RuntimeChecks.java` | `onStart()` ~99-130, `stageStandbyChecks()` ~1277-1322, `standbyLiveFrame()` ~1327 |
| `apps/android/app/src/main/java/ph/kanta/reborn/stage/StagePresenterImpl.java` | `refresh()` 652, `frameTicker` 211-239, start/stop 305-345, `maybeShuffleBgv()` 686 |
| `apps/android/app/src/main/java/ph/kanta/reborn/stage/BackgroundStage.java` | `setSource()` 78, `setVideo()` 166, `startVideoIfReady()` 180, `setVisualActive()` 264 |
| `apps/android/app/src/main/java/ph/kanta/reborn/stage/StagePresenceOverlay.java` | `recordLiveFrame()` ~105, `liveFrame()` ~110 |
| `apps/android/app/src/main/java/ph/kanta/reborn/SessionController.java` | `currentSong()` 113, `setBundledBgv()` 1908, `restoreStage()`, `applyStage()` |
| `apps/android/app/src/main/java/ph/kanta/reborn/StageSurface.java` | second stage; also calls `recordLiveFrame()` at ~92 (fullscreen path) |
| `tools/run-android-runtime.py` | screenshot loop ~124-140; must contain `KANTA runtime:` + `checks passed` |
| `.github/workflows/android-validation.yml` | runtime step 165-168 (`continue-on-error`), artifact 175-222 |

## Recommended next steps

1. **Fix the masking first.** A runtime-evidence failure must fail the run. Either drop
   `continue-on-error: true` from the runtime step, or add an explicit step that asserts the
   evidence exists (e.g. all 19 captures non-zero, `KANTA runtime:` present in
   `android-runtime.log`) and fails otherwise. Without this, every future regression is
   invisible. Note this needs owner sign-off if it changes release gating.
2. **Determine A vs B** by pulling the runtime logcat from run `42789716656` and checking for
   `MediaPlayer`/`setDataSource`/`video_` after the app starts, plus the value of
   `backgroundStage.getSource()`.
3. If **A**: drive the counter from the actual frame callback
   (`TextureView.SurfaceTextureListener.onSurfaceTextureUpdated`, currently an empty stub at
   `BackgroundStage.java:114`) rather than from the playback-gated ticker. That is honest
   evidence of real rendered frames and works while idle.
4. If **B**: fix the standby source selection so an idle stage actually shows the bundled BGV.
5. Re-run, confirm **artifact ZIP contains the 19 screenshots**, and only then treat the
   standby stage / control deck as emulator-verified. Until then they are **statically
   verified only** — the website copy already says they are "asserted by emulator checks"
   without claiming captures, which remains accurate.

## Environment gotchas (still in force)

- **No runnable JVM** in this sandbox (`getcpu(2) not supported by kernel`). Compilation and
  instrumented tests are **CI-only**.
- `file_write` into `/var/minis/Kanta-reborn/**` reports success but does **not** persist →
  write via `shell_execute` heredocs / `python3`. `file_read` on repo paths returns
  "No such file" — use shell/python.
- Shell output **mangles some identifiers**, printing bare `n` (e.g. `BundledBgvCatalog.defaultClip`
  renders as `ndefaultClip`). Always confirm with `python3`/`rg`.
- `python3 -m unittest discover -s tools/tests` **regenerates `apps/web/updates.json`** —
  revert with `git checkout -- apps/web/updates.json` before committing.
- `browser_use` **caches `minis://Kanta-reborn/...`**; copy to `/var/minis/workspace/` to see fresh HTML.
- Untracked junk in worktree: `"ebsite-robots-tag-rendered-pages: "`, `"ebsite-scraping-defense: "`.
- Memory pressure: this sandbox hit `MemoryError` mid-run. Clean `/tmp` and delete large
  downloads promptly. The user-supplied artifact is at
  `/var/minis/attachments/uploads/android-reconstruction-checks.zip` (205 MB, run 801).

## Do not claim

- That the screenshots are back — they are not; run `42789716656` fails the video assertion.
- That PR #192's green CI means the runtime checks pass — it does not.
- That the standby stage / control deck are emulator-verified on the current source.
