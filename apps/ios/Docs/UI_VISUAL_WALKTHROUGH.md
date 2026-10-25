# KANTA real simulator visual walkthrough

Updated October 25, 2026. The hosted candidate workflow runs independent iPhone and iPad jobs; each job builds the UI-test bundle, executes the required cases, and preserves device-specific runtime evidence. A passing build is not a passing walkthrough.

- `testStarterPlaybackAndControls` uses bundled Morning Light through More → Song Library, then verifies Play/Pause, resumed playback, navigation dismissal, and Search recovery.
- The six More destinations are separate XCTest cases: `testMenuSongLibrary`, `testMenuSongbook`, `testMenuSettings`, `testMenuCompanionRemote`, `testMenuICloudSave`, and `testMenuLegalPrivacy`. Each asserts its navigation title, captures the screen, dismisses via `navigation.done`, and verifies that Stage More returns. The split gives each destination its own XCTest execution allowance without removing coverage.
- `testDemoKeypadCancelAndRepeatedIntentionalReservations` is explicitly DEMO evidence. It checks all digits including zero, Cancel, duplicate-tap suppression, distinct repeated reservations, and landscape keypad reachability.
- Online-preview lifecycle and encrypted/browser companion tests remain required. `tools/capture-ios-runtime.py` rejects a run that misses any required passing test, including a test runner that restarts after a timeout.
- Attachment names separate `real-*` from `demo-*`. Simulator video may be silent.
- Camera hardware is unavailable on simulator: never label an illustrative camera image or permission alert as a working camera capture.

The full iPhone and iPad jobs must both pass on the same exact candidate SHA before release acceptance. Physical-device checks remain separate: front-camera permission/denial, portrait/landscape, stop while requesting, background/foreground, interruptions, and simultaneous karaoke/audio/scoring on iPhone XR and iPad 9 or newer. See `Docs/STAGE_UI_ACCEPTANCE.md`.
