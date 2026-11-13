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

## Published artifact

- Source: `df87c03a4c11d6fee6ba514334bb940e651b2d92` (PR #202).
- Preview: [`v0.10.0.845-preview-df87c03a`](https://github.com/usermalik20-del/Kanta-reborn/releases/tag/v0.10.0.845-preview-df87c03a).
- APK SHA-256: `eb67e3aebd355b601a71a6d9f81b97c8016edcf7ab5b3a769134e66590b66682`.
- Local final checks: 197 Python tests, Java structure 157 files, workspace 59 checks,
  documentation synchronization and whitespace checks passed.
- Java core and Android instrumentation have now executed in the linked hosted runs.
- The local iSH JVM/SDK limitations still apply; no local APK/device validation is claimed.

## Remaining acceptance

- Physical-device acceptance remains distinct from the completed hosted build/core/emulator gates.
- Physical phone/tablet: Home queue/keypad fit, both keyboard modes, RES success and
  ambiguity fallback, rapid edits/navigation/backgrounding, rotation and large text.
- Stage: readable scrolling/lyric clearance with long and duplicate titles, reorder,
  empty queue, foreground transitions, fullscreen and an actual external display.
- Visual screenshots need review; passing source assertions is not visual acceptance.
- No iOS behavior changes or new physical-device audio/camera acceptance are claimed.


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
