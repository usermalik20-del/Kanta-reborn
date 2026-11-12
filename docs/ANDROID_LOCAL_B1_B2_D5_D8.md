# Android B1/B2/D5/D8 — historical local-candidate record


**Superseded status:** the local-only/no-release statements below describe the original
candidate, not current authorization or shipping state. Those source changes are in
public preview `eed8020e`; Android validation
[43260500332](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43260500332)
passed build/lint/core/emulator and owner-signed APK creation. See
[unfinished work](UNFINISHED_WORK.md) for still-open physical-device font and legacy-import
acceptance. The later screenshot-driven UI work has its own
[layout validation record](ANDROID_HOME_LAYOUT.md).

Base: `00d97f0` on local branch `work/android-b1-b2-d5-d8-local`.
This is uncommitted engineering work, not the published Android build. No push,
merge, signing, release, deployment or hosted workflow is authorized.

## Scope and owner decisions

- **B1:** real-PCM framework-free frequency-band analysis, tests and a documented
  tap of the existing opt-in microphone analysis path; no second capture owner,
  playback owner or unwired spectrum view.
- **B2:** user-selected lyric fonts from the content root, persisted through
  `SettingsStore`, with malformed/missing-font fallback to the built-in face.
- **D5:** the owner selected **Keep current**. Retain the existing platform
  background-video renderers, not Media3. See [backend decision](D5-video-backend.md).
- **D8:** the owner selected **Remove**. Separate melody/guide mixing is unsupported;
  the placeholder sliders and listener callbacks are removed. Key, tempo and the
  control deck's working master volume remain. Existing unused preference keys
  are not purged or migrated, so this does not erase user settings.

## Validation requirements

Local Python guards are not an Android build or proof of rendered audio/video.
Before committing, review the final diff and run `tools/preflight.py` and the full
Python suite. Run `:app:coreRegression`, Android compilation/lint and instrumentation
later on a supported toolchain, on one exact candidate commit. Physical-device
font rendering, capture overhead, audio safety and long-session video soak remain
separate acceptance gates. The unfinished-work entries remain open until their
named-commit evidence is recorded.

Local environment: `java -version` fails during VM initialization with
`getcpu(2) system call not supported by kernel`; the Android doctor also reports
missing platform/build tools. No local APK, emulator or device acceptance is claimed.

## Added scope: optional LRCLIB lyrics

Owner requested on-demand Android lyric fetching and supplied its public application
ID. The setup query returned HTTP 200 / 20 results. See [LRCLIB integration](LRCLIB_LYRICS.md).
This extends the same local-only boundary; it does not authorize publishing.
B1 [analysis design](B1-spectrum.md) and B2 [font design](B2-custom-fonts.md) describe
the final candidate and separate runtime tests still required.
