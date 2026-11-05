# PR: Safe validation and presentation-contract guard for PR #52

This branch implements safe, automatable validation steps for PR #52 and records the manual validation gates that remain.

> **Correction (2026-11-05):** no workflow named `reconstruction-validation.yml` exists in this repository. The presentation guard this plan describes now runs as `tools/check-ios-presentation-contract.py` inside `ios-validation.yml` (and `ios-smoke.yml`). The text below is kept as the record of the plan as written.

Summary of changes in branch fix/pr52-safe-validation-plan-20261019:

- Host presentation guard
  - Prevent automatic presentation of the Host session-save sheet during navigation handoff to avoid blocking the Library → Done → Search tap. The pending session state is preserved; the Save session sheet is only presented via an explicit action.
  - File modified: apps/ios/Sources/Features/Host/HostView.swift

- CI validation
  - Extend tools/check-ios-presentation-contract.py to include a check that the library dismissal does not auto-open the session-save sheet.
  - Enable reconstruction-validation workflow to run on this feature branch so CI will execute the presentation-contract check.
  - Files modified: tools/check-ios-presentation-contract.py, .github/workflows/reconstruction-validation.yml

Purpose

- Make the Library → Done → Search path robust in CI by preventing a topmost Host sheet from blocking the first tap after library dismissal.
- Surface modal/presentation regressions in automation (no device required) so fixes are visible and reproducible.

Manual validation gates (must be executed by a reviewer/owner before merging)

1. Simulator visuals (iPhone & iPad)
   - Capture and verify focused screenshots for:
     - Library → Done → Search (first tap opens keypad)
     - CONTROL expanded with fullscreen entry
     - Fullscreen dismissal then stage Search accessibility
   - Verify the Stage Save Session button is visible when a pending session exists and that Save/Don't Save behave correctly.

2. Unsigned simulator APK/IPA and xcresult evidence
   - Run the unsigned simulator app build on both iPhone & iPad simulator targets and preserve xcresult artifacts.
   - Upload artifacts for PR inspection.

3. Physical-device and real-media acceptance (manual)
   - Verify audio playback behavior, external displays, real microphone/monitor behavior and scoring on an iPhone and iPad.
   - Confirm rotation and orientation behavior in real devices (not just simulator screenshots).

4. Release gates (do not bypass)
   - Do not merge or sign until the owner confirms manual validations above. Signing, provisioning and release remain gated.

Next steps I recommend (automatable)

- Open a PR from fix/pr52-safe-validation-plan-20261019 -> design/restore-library-host-20261018 with this PR body.
- Request reviewer: kanta-gelo and optionally others.
- Allow CI to run; I will fetch logs and report any failing checks.

