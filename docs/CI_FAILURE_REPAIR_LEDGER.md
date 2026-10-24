# KANTA CI failure and repair ledger

This is the durable, append-only engineering record for recurring failures. Keep exact evidence, fix commits, and **the first green run on the fixed SHA**. Do not promote a repair from `candidate` to `verified` merely because code was pushed, a different SHA passed, or compilation succeeded. When an issue recurs, search this ledger before editing.

## Mandatory repair record

Every failed CI job with an engineering fix must receive a record containing:

- **Fingerprint:** platform, workflow/job, test/assertion or compiler diagnostic, and distinctive log phrase.
- **Affected SHA / first observed run:** link to job, logs and retained screenshot/xcresult where relevant.
- **Cause:** confirmed mechanism from code and evidence; distinguish hypotheses explicitly.
- **Fix:** changed file(s), exact state/invariant repaired, commit SHA and PR.
- **Regression coverage:** test name and the failure that would occur if the fix were removed.
- **Validation:** first passing CI run ID, job, tested head SHA, scope (source/static, package tests, simulator UI, hardware, signed release), date. If pending or failing, mark `candidate`.
- **Recurrence rule:** grep keywords, related prior records and what *not* to do.
- **Status:** `observed`, `diagnosing`, `candidate`, `verified`, `regressed`, or `superseded`.

After a relevant CI run becomes green, update this ledger **in a separate docs-only commit** recording the actual run ID and SHA, then confirm the documentation commit did not alter production behavior. If a docs-only commit triggers a new workflow, don't imply the prior green result validated the new SHA's source; state the distinction. If validation fails, retain the candidate record and continue the repair. Never rewrite a historical failure as though it never occurred.

## iOS — Search inaccessible after leaving Library

**Fingerprint:** `KantaVisualWalkthroughUITests.testStarterPlaybackAndControls`; `Search is blocked after leaving the library`; iPhone/iPad simulator runtime walkthrough.

**First documented failure:** [runtime run 39014560050](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39014560050), source SHA `6273e16b0b1c66b50a62e1026d87fbcb9c5ad7df`. Runtime failure on both iPhone and iPad. Artifacts: `ios-runtime-evidence` and `ios-iphone-runtime-evidence` on that run. Build/compilation success is not a passing walkthrough.

**Investigation:** `RootView`, `HostView`, `KantaMainPlayerView` and `SingingModalPresenter` have independently owned presentation paths. `HostViewModel.run()` refreshes playback snapshots every 100 ms; library starter playback uses the shared `LocalKaraokeController`. The test previously waited for `navigationBars["Song Library"]` to disappear, but `LibraryView` declares `.navigationTitle("Library")`; the wait could pass before the actual sheet dismissed. This test mismatch is **confirmed**. The exact view obstructing Search in the failed run remains **unconfirmed**; do not claim the ownership refactor is proven root cause until runtime evidence verifies it.

**Candidate repair commits:** `e88204f` captures screenshot and full accessibility hierarchy on blocked Search; `74270b1` corrects the navigation-title wait; `ed3dd56` tracks root navigation dismissal through `onDismiss`; `2bc19cb`, `63116bb`, `84be430` introduce a shared main-actor presentation gate for Root/Host/Stage. PR [#36](https://github.com/kanta-gelo/Kanta-reborn/pull/36).

**Regression:** one-tap Search after More → Library → starter playback → Done, then keypad → Cancel → CONTROL on iPhone and iPad. Preserve failure-time hierarchy and screenshots. Additional required coverage: simultaneous sheet requests, dismissal callbacks, session-save prompt and fullscreen UIKit presenter ownership.

**Validation status:** `candidate`. [Reconstruction run 39021531256](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39021531256) was in progress when this record was authored, head `84be43061ffee747580ffe6c854e7ed8b1bc610b`. No passing runtime walkthrough or release IPA is established by this record. Recheck run before marking verified.

**Recurrence rule:** inspect the current Library title, actual topmost presentation and attached accessibility hierarchy before modifying XCTest waits. Do not add blind sleeps, repeat taps, or claim source guards prove simulator interaction. Review `docs/ENGINEERING_ARCHITECTURE_CONTRACT.md` and the modal lifecycle source before any fix.

## Future entries

Add a new dated section for each distinct fingerprint. When a previous fingerprint returns, add a **recurrence subsection to the existing record**, linking the new SHA/run and explaining why the earlier repair did not hold. Never delete previous attempts or passing-run evidence.

## iOS — Presentation contract guard mismatch (2026-10-17)

**Fingerprint:** `tools/check-ios-presentation-contract.py`; `FAIL: root rejects overlapping menu presentations`; reconstruction validation iOS source guards.

**Observed:** [run 39021723583](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39021723583), source SHA `2365d4723f4117da61b6a05e1f30126bd5c2d0da`.

**Cause:** the static contract still matched the obsolete exact string `guard activeNavigation == nil else { return }` while `RootView` now guards `activeNavigation == nil && !navigationDismissing` and also requires `presentationGate.acquire(.navigation)`. The new guards reject overlapping requests; the stale checker was rejecting the stronger implementation.

**Candidate fix:** align the checker with both current guards rather than deleting the contract assertion. PR [#36](https://github.com/kanta-gelo/Kanta-reborn/pull/36). No application behavior changed.

**Regression:** the source guard fails if either the dismissal check or shared gate acquisition is removed. **Validation:** `candidate`; CI on this fix SHA pending. This does not establish passing iPhone/iPad walkthroughs.
undefined

## iOS — UIKit fullscreen bypasses presentation gate (2026-10-17)

**Fingerprint:** fullscreen UIKit player can overlap SwiftUI sheet requests; UIKit presenter has no shared owner acquisition or dismissal acknowledgment.

**Cause:** confirmed by current source inspection. `SingingModalPresenter` presents a UIKit controller independently of `KantaPresentationGate`. Simply holding the gate for fullscreen would block Search/Queue sheets hosted inside that fullscreen stage.

**Candidate repair:** PR #36 adds fullscreen gate acquisition, waits for the UIKit dismissal completion before releasing ownership, queues a fullscreen request before the CONTROL sheet releases its owner, and models fullscreen Search/Queue as a nested stage owner which restores fullscreen after child dismissal. Adds walkthrough coverage for Search inside fullscreen and Search after exiting fullscreen. The UIKit presenter continues to dismiss only its own player controller.

**Regression coverage:** `KantaVisualWalkthroughUITests.testStarterPlaybackAndControls` opens fullscreen from CONTROL, opens and dismisses Search inside fullscreen, exits, then opens Search on the normal stage.

**Validation:** candidate. Current-head source guards, simulator compilation, iPhone/iPad walkthrough and unsigned IPA remain unverified. Do not merge based only on a passing earlier SHA or source inspection.

**Recurrence rule:** inspect both UIKit player identity and nested SwiftUI sheet ownership. Do not acquire a fullscreen lock without a child-presentation path; do not release the gate before UIKit's completion callback.


## GitHub Actions — duplicate push trigger keys (2026-10-17)

**Fingerprint:** `.github/workflows/verified-downloads.yml` and `.github/workflows/website-validation.yml` appeared as failed runs with zero jobs on source SHA `325c3792c2cc7d570c0390c50450df4a8b8da5b7`: [verified-downloads run 39030157542](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39030157542) and [website-validation run 39030156916](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39030156916).

**Cause:** the feature-branch trigger was inserted as a second root `push` key instead of being combined with the existing main-branch trigger. GitHub could not load either workflow, so both runs failed before creating jobs. The iOS runtime workflow on the same SHA loaded and began normally.

**Fix:** consolidate each workflow to one path-filtered `push` trigger covering relevant changes across branches, and keep their pull-request triggers. Download packaging remains gated; only its contract-test job runs automatically.

**Regression coverage:** GitHub Actions must create actual jobs for website validation and verified-download contracts on a relevant feature-branch push. Check run event, job list and final conclusion; zero-job workflow failures are not validation passes.

**Validation:** candidate on the corrected workflow SHA. Recheck both workflows on the new head. Do not rerun unchanged SHA `325c3792c2cc7d570c0390c50450df4a8b8da5b7`.

## iOS — Session-save sheet obstructs Search after Library (2026-10-17)

**Fingerprint:** `testStarterPlaybackAndControls` line 79, `Search is blocked after leaving the library`; [runtime run 39030285248](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39030285248), SHA `a37a2d37a3b78367579f952bd79eef8c3f956a4d`. Both iPhone 16 Pro and iPad walkthroughs failed this assertion; the other three walkthrough cases passed on each device.

**Confirmed iPhone cause:** the retained `search-blocked-accessibility-hierarchy` and screenshot show a topmost **Save to Favorites** host sheet covering the stage Search button. The Library navigation bar was already gone. `HostView` automatically presented a pending session-save sheet when `navigationSheetPresented` changed to false. `HostViewModel` marks a completed session when playback stops with an empty queue; this can happen while Library is open. The earlier dismissal-animation hypothesis does not explain the captured obstruction. The iPad shares the same assertion and presentation code, but its retained hierarchy has not yet been independently inspected.

**Candidate repair:** remove automatic save-sheet presentation during navigation handoff; keep the pending save offer as a visible, explicit stage action. Preserve the saved session until the user chooses Save/Don't Save, and continue tracking subsequent completed songs while a save decision is pending. Close the save sheet when the model clears the pending decision. Changed `HostView`, `KantaMainPlayerView`, `HostViewModel` and UI regression. PR [#36](https://github.com/kanta-gelo/Kanta-reborn/pull/36). Commit and runtime validation are pending at the time of this record.

**Regression coverage:** after Library → Done, Search must be accessible on the first tap without dismissing another modal. If the pending-session action is visible, opening it must show Save to Favorites and Don't Save must close it. Preserve both device screenshots/hierarchies. No blind sleeps or repeated Search taps were added.

**Status:** `candidate`; compile, iPhone/iPad simulator acceptance, merge and release are not yet verified for this repair.


## iOS — Fullscreen action unreachable in expanded CONTROL (2026-10-17)

**Fingerprint:** iPhone and iPad runtime walkthrough run [39031958909](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39031958909), current head `0b00cc3d4f13ab24051be7051f59dc050ec876ec`; `testStarterPlaybackAndControls` failed at the fullscreen button hit-test (test line 107). Reconstruction validation [39031958919](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39031958919) passed on the same head. iOS compilation passed; the walkthrough failed on both device sizes before attempting fullscreen.

**Cause:** the test expanded the detailed CONTROL section and then searched for the fullscreen action in the Stage row below a long scrolling list. The action remained outside the hittable viewport after the test's bounded swipes, making the path fragile on both device layouts.

**Repair:** expose a labeled, identified fullscreen action in the CONTROL navigation toolbar. Keep the Stage-preview affordance and route both entry points through one Host method that dismisses CONTROL and queues fullscreen with the shared presentation gate. The walkthrough now targets `controls.openFullscreen` directly, then continues to verify fullscreen Search and gate release after exit.

**Validation:** candidate fix pushed to the PR branch; automatic iPhone/iPad runtime and reconstruction results are pending. Do not merge until both walkthroughs pass on the updated head.


## iOS — Post-fullscreen Search assertion raced dismissal (2026-10-17)

**Fingerprint:** runtime run [39034162526](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39034162526), head `c6a5f2a4ff3b47e2efc3c8ad70687bdfee9860fd`; both iPhone and iPad reported `Fullscreen dismissal did not release stage presentation ownership` at the final Search-after-exit check. App and UI tests compiled; all other walkthrough cases passed.

**Evidence:** the walkthrough tapped Cancel and immediately checked that `fullscreen.exit` existed, although that button remains in the underlying fullscreen view during child-sheet dismissal. After tapping Exit it checked only that the stage Search element existed; the same accessibility identifier also exists under the fullscreen projection and in the underlying Host. Neither existence check proved either dismissal had completed.

**Repair:** wait for the fullscreen Search keypad to disappear after Cancel, wait for the unique `fullscreen.exit` control to disappear after Exit, and require the stage Search button to be hittable before tapping it. This preserves the real gate-release assertion while removing ambiguous accessibility queries and transition races; no fixed sleeps or relaxed checks are added.

**Validation:** strengthened walkthrough pushed to PR #36; automatic current-head runtime and reconstruction checks are pending. If stage Search remains blocked after the fullscreen exit control is gone, trace and repair the app's gate release callback before merge.


## iOS — Fullscreen presenter dismissed its nested child, not the player (2026-10-17)

**Fingerprint:** runtime run [39036089941](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39036089941), head `b1322ffc1620144eac4ab4e84e3243e22415073b`. Build passed; both iPhone 16 Pro and iPad failed at the assertion waiting for `fullscreen.exit` to disappear after tapping Exit. Reconstruction validation [39036089948](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39036089948) passed; Android emulator checks passed.

**Cause:** `SingingModalPresenter.updatePresentation()` began dismissing its player as soon as the Host binding became false, even while a nested Search/Queue sheet still owned the presentation gate. UIKit can complete dismissal of that child first; the presenter then treated that callback as fullscreen teardown, cleared its player identity and left the parent fullscreen controller visible. The prior test only waited for Search's keypad to leave the accessibility tree, which can precede the sheet's `onDismiss` gate handoff.

**Repair:** observe shared presentation-gate changes. If the child still owns the gate, defer fullscreen teardown. Once the child `onDismiss` restores `.fullscreen` ownership with dismissal requested, the presenter dismisses its exact player and completes ownership release from that dismissal callback. The test's explicit wait for the unique fullscreen exit control to disappear remains in place.

**Validation:** presenter change is applied on the latest PR head; automatic current-head runtime and reconstruction checks are pending. Do not merge until both device walkthroughs pass.


**Follow-up after run 39038432004:** the gate-observation guard compiled but the runtime still left `fullscreen.exit` visible on both devices. The Exit action now calls the UIKit presenter directly in the same main-actor turn that clears the SwiftUI binding. The presenter records the dismissal intent, enters the gate's serialized dismissal path and waits for child ownership restoration before dismissing its exact player. This addresses the missing presenter update signal shown by the failed assertion; fresh iPhone/iPad runtime validation is pending.

### 2026-10-17 — fullscreen exit remains visible after tap

- Failed runtime run: `39038432004`, source SHA `8e5c2560b0a6503f35101b8da93debc177e0c50b`; both iPhone and iPad `KantaVisualWalkthroughUITests.testStarterPlaybackAndControls` timed out at line 127 waiting for `fullscreen.exit` to disappear.
- Source-level cause candidate: `SingingModalPresenter.updatePresentation()` refused to call UIKit dismissal unless `presentationGate.owner == .fullscreen` and `presentationGate.dismissing == true`. Fullscreen child Search/Queue can temporarily own the gate; the exit intent must not be dropped while that child transition completes.
- Fix commit: `b2bdc3810e9470e7f87a8788b033d7c74e64969f` removes the blocking guard while preserving the existing dismissal callback and gate completion.
- Verification: pending latest-head iPhone/iPad simulator walkthrough; no green runtime result claimed.


### 2026-10-17 — fullscreen Exit still visible after tap (run 39041512052)

- iPhone 16 Pro failed at UI test line 127 after successfully tapping `fullscreen.exit`; the XCTest wait expired because the button remained in the accessibility tree. The failure log is in the [iOS runtime run](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39041512052).
- Cause candidate: `updatePresentation()` required the presenter's view to remain attached to a window for both presentation and dismissal. UIKit can detach the presenting view during a full-screen presentation, so that guard can block the Exit dismissal path.
- Fix commit: `fa635ab4eb33546c543ca61322a36bdb3c05ede0` moves the window attachment requirement into the presentation branch; dismissal remains available while the presenter is off-window.
- Validation: pending fresh iPhone/iPad walkthrough and reconstruction checks on the updated head. Do not merge until both device walkthroughs pass.


### 2026-10-18 — PR #51 has no validation run after lifecycle repair

- Source: `7fb5e2a8f8e2bd805c2ecf5b5820ad2208fe42ed`; main advanced to `56f10c505ed654c57e0b6b4b3412b082181a77c4`. GitHub reports `mergeable_state: dirty`; both the commit-filtered Actions query and PR-run query returned no runs. There is no failed run ID because validation never started.
- Confirmed integration blocker: both branches appended independent sections to `docs/ARCHITECTURE.md`. Git reports that file as the sole merge conflict. Pull-request workflows require a mergeable PR; draft status is not excluded by the current workflow.
- Repair: merge the current main ancestry into the existing feature branch, preserving the online-player lifecycle section and all approved library-folder, SongHub import and FluidSynth decisions. No application, workflow or assertion changes. No history rewrite or main-branch merge.
- Verification: conflict-marker and whitespace review, test-layout and presentation guards on the resolved tree. Reconstruction and focused iPhone smoke must run on the resulting head; earlier green runs apply only to `77f9f10`. Hosted results remain pending.


### 2026-10-18 — Swift Testing macro rejects mutating lifecycle calls

- Failed run: [39103777006](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39103777006), iOS job `117605226475`, PR head `92e838c6fe7307d10eea9620832a00e5156a4038` (checkout merge `be14bbccc7b03b407ee37228db1ece3a49807fe0`). Deterministic test compilation failure; package execution never started. Android validation and focused iPhone smoke run `39103776922` passed; the unsigned iOS simulator app build also passed.
- Confirmed cause: six `#expect` expressions in `YouTubeVideoReferenceTests.swift` directly invoked mutating `YouTubePreviewLifecycle.close(_:)`. Swift Testing expanded the receiver into an immutable `$0`, producing `cannot use mutating member on immutable value`. This is test-expression incompatibility, not evidence of a runtime lifecycle failure.
- Repair: execute each close exactly once into a named Boolean before asserting the result. Preserve all stale-token, repeated-close, background and replacement assertions and production value semantics. No production, dependency, workflow or architecture change.
- Local verification: test-layout and presentation guards, unchanged assertion count, and bounded diff/whitespace review. Swift is unavailable in this execution environment; fresh hosted package compilation and execution remain required on the repair commit. No unchanged deterministic rerun.
- Follow-up: append green evidence only after the repaired head passes. Audio-focus coordination and native iOS lifecycle/device acceptance remain separate PR gates.


## Verified hosted checkpoint — October 18, 2026

Source `5c1b0f4eea9f92196b836a8bbf2980a5bb6793ad` passed reconstruction run [39106105837](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39106105837) and focused iPhone smoke run [39106105865](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39106105865). Reconstruction job steps confirm Swift package/test compilation, package execution, unsigned simulator application build, Android assembly/lint/domain regression, full native sanitizer gate and emulator runtime checks passed. This closes the earlier Swift Testing macro compilation failure after repair `1ca7454`; no assertions were removed. It also validates the subsequently added iOS/Android local-playing preview-entry guards on this source.

This is hosted evidence for the named SHA only. The smoke test is navigation coverage, not iOS native online-player lifecycle acceptance. General audio-focus/session arbitration, Android remote/asynchronous-start preview reconciliation, iOS runtime online lifecycle coverage and physical-device embed/interruption/ads acceptance remain open. No merge, signing, packaging, deployment or release is claimed.


## Verified controller-observer repair — October 18, 2026

Source `fa5dcbcf7aa070d924d86ed6c3e832af3618cebb` passed reconstruction run [39108033371](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39108033371): Swift package/test compilation and execution, unsigned simulator build, Android build/lint/domain/native sanitizers/browser interoperability and actual emulator runtime checks. The added KCP1 remote start/resume regression passed. Earlier Android-only run `39107996743` was cancelled by the superseding run, not a regression failure. Focused iPhone smoke run `39108033323` was still in progress at this checkpoint; no smoke, physical-device, signing or release result is inferred from reconstruction success.


## Verified Android audio-coordination checkpoint — October 18, 2026

Source `99f3898d35394a26d1879149c3f8637acd48ef56` passed reconstruction run [39108899667](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39108899667). Both Android and iOS jobs succeeded. Android native emulator execution includes the new effects/intermission/microphone/stale-owner/background/FIFO cases, plus the existing encrypted-remote start/resume cases. Swift package compilation/tests and unsigned simulator app build passed. Focused iPhone smoke run `39108899640` remained in progress at this checkpoint; its outcome is not inferred. This validates the Android candidate in hosted checks, not physical output timing, real YouTube media/ads or iOS audio-session parity. No merge/signing/release.


## Verified iOS native component checkpoint — October 18, 2026

Source `2302703481810824d75b0ae9a4b84b417a6c39ac` passed reconstruction run [39109936325](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39109936325) and native/navigation smoke run [39109936327](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39109936327), job `117623681797`. The retained log explicitly reports passes for `testForegroundLossRetiresNativeViewAndDoesNotRestoreIt`, `testProcessFailureRetiresExactlyOnceAndOwnerCanReopen`, `testReplacementRetiresOldNativeViewBeforeAttachingNext`, and the unchanged `testFocusedNavigationSmoke`. The new named-pass verifier also succeeded. Thus the new Xcode target compiled and all three native cases actually executed; this is not a zero-test or navigation-only green.

Reconstruction passed both native platform jobs, including Swift package/test compilation/execution, unsigned iOS simulator build and Android build/lint/domain/sanitizer/emulator acceptance. This closes the native WebKit component coverage candidate. It does not establish SwiftUI sheet/real-media timing, iOS shared audio-session and auxiliary coordination, physical embeds/interruption/privacy/advertisements, signing or release. Those PR #51 gates remain open; the Companion Remote design/roadmap stays separate in PR #53.


## Local source-contract fixture repair — October 18, 2026

- Baseline: PR #51 `5d25f62`, uncommitted iOS audio-handoff candidate.
- Observed: local ReleaseToolingTests ran 54 tests, with one error in test_saved_intermission_track_does_not_decode_on_host_entry: IndexError while splitting the removed literal `init() {` signature.
- Cause: intermission now receives the shared audio coordinator and optional isolated storage. The source guard assumed a parameterless constructor; production did not eagerly decode a track.
- Repair: delimit the actual coordinator-injected initializer through the next private method. Preserve every lazy-decoder assertion and add native synthetic intermission handoff coverage. No test removed or suppressed.
- Result: rerun recorded after the repair; hosted Swift and simulator validation remain required. This was a local fixture error, not a hosted CI failure.


## iOS intermission initializer shadowing — October 18, 2026

- Failed source: `b3e26e573e91aaa52fe4d52ff6ed0466270fd7ae`, reconstruction [39111947485](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39111947485), iOS job `117629764652`; native/navigation smoke [39111947495](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39111947495), job `117629764528`.
- Fingerprint: KantaIntermissionMusic.swift:46:23, optional URL must be unwrapped for appendingPathComponent. Both application/test-bundle compilation stopped before native execution.
- Confirmed cause: the new optional injected `directory` parameter shadows the nonoptional stored directory in the initializer's saved-track restoration. This is deterministic app-only source compilation; the file is not part of the Swift package.
- Repair: retain the external `directory:` label but name the local argument `storageDirectory`, restoring nonoptional property lookup during track restoration. Preserve the same runtime defaults, test storage isolation, every assertion and both target source entries.
- Other evidence on failed source: all 178 Swift package tests passed after all package targets compiled; Android validation passed. Those results do not supersede failed application/native-smoke compilation.
- Regression gates: lazy intermission source guard, real native intermission fixture and named-pass smoke remain enabled. Corrected-source compilation/execution required; append green evidence only after it actually passes.


## Verified iOS audio handoff — October 18, 2026

Source `6f7a0ece9e42cc500ce4f52cc414c58e5e11b58e` passed [reconstruction 39112387766](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39112387766) and [native/navigation smoke 39112387680](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39112387680). Reconstruction compiled every Swift package test target, executed all 178 Swift tests and 54 release-tooling tests, built the unsigned Release iOS simulator app and passed Android build/lint/domain/sanitizer/browser/emulator checks.

Smoke job `117631072361` explicitly passed all five YouTubeEmbeddedPlayerLifecycleTests cases and testFocusedNavigationSmoke. The new intermission handoff case passed at 08:49:55 UTC (0.070 s); WebKit retirement-before-session configuration passed at 08:49:55 (0.008 s); focused navigation passed at 08:50:40 (35.553 s). The named-pass verifier accepted all six cases at 08:51:24. This closes the hosted iOS audio-arbitration implementation/coverage gap and the initializer compilation repair; no assertions were removed. The earlier failed b3e26e5 remains recorded in the failure ledger.

The coordinator preserves local song/position/playback/FIFO identities, retires online ownership before native output requests, stops auxiliary audio and fences microphone permission, cancellation, foreground and stale owners. Tests establish the named domain/native behavior, not real-media zero-overlap timing. Actual SwiftUI online entry/dismissal, physical embeds/interruptions/privacy/ads and full exact-candidate iPhone/iPad release acceptance remain open. PR #51 stays draft. Next executable task: exercise the actual online-sheet entry/close/reopen/error flow through the host UI, retaining device/media acceptance as separate gates. No merge, signing, packaging, deployment or release occurred.


## Verified online-sheet UI acceptance — October 18, 2026

Source `594cd94c2c78d12fcf8fd21d0ece1cd5949c2c72` passed [reconstruction 39114570402](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39114570402) and [expanded smoke 39114570339](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39114570339). Both reconstruction platform jobs succeeded. Smoke job `117637615146` explicitly passed all five native cases, focused navigation, testOnlinePreviewEntryCloseReopenAndForegroundLifecycle (105.968 seconds) and testOnlinePreviewPreservesPausedStarterAndLocalResume (54.616 seconds). The eight-name verifier succeeded at 09:36:18 UTC; this is execution evidence, not build-only acceptance. No assertions or previous cases were removed.

Actual host-sheet navigation, invalid-link validation, explicit entry/stop/reopen, foreground cleanup, dismissal/reentry/navigation recovery and paused-original-starter preservation/local resume are accepted on that iPhone simulator source. External availability was deliberately not treated as playable-media evidence: the UI cases accept either real native player controls or the production recoverable external-load error. Successful YouTube playback, ads, hardware interruption/privacy/audio timing and the updated seven-case full iPhone/iPad release walkthrough remain separate open gates. PR #51 remains draft and unmerged; no signing, deployment or release occurred.

Next executable acceptance: full seven-case iPhone/iPad walkthrough on the exact candidate source, followed by physical-device/real-media acceptance. No new failure occurred on this candidate. The earlier initializer repair history remains unchanged.


## Local browser environment restriction — October 18, 2026

Candidate: PR #51 companion implementation based on b46f6c7. No hosted run yet. Local Chrome 155 could not start: `chrome/browser/process_singleton_posix.cc:297 socket() failed: Operation not permitted`. The restricted execution environment disallows its Unix-domain profile singleton socket. This is an environment blocker, not evidence of an application UI failure or success. The first Playwright browser download also returned an invalid archive; the direct Chrome package downloaded and extracted successfully, isolating startup from acquisition. Do not remove browser assertions or call static transport tests UI acceptance. The unchanged real Chrome harness is wired to hosted Linux validation, where native browser startup is supported. Hosted outcome must be recorded against the resulting source SHA; device/browser trust acceptance remains open.


## PR #51 companion first-head validation — 339b8f6

Reconstruction run 39126848617: Android APK and instrumentation assembly, lint, 524 domain checks, native sanitizer and merged permission/notices checks passed. Actual Chrome launched but the first unverified-pairing notice assertion failed; emulator execution was consequently skipped. Confirmed test defect: the harness waited only for a form element, which does not establish that deferred production scripts have finished, and did not explicitly establish foreground focus even though pairing correctly requires a visible document. Repair: wait for document completion and foreground, bring the tested page to front, retain the fingerprint assertion, and capture bounded nonsecret readiness/exception diagnostics. Whether another production issue remains must be determined by the changed-head browser run; no browser pass is claimed.

The same run's Swift test compilation failed at BrowserRemoteProtocolTests.swift:22: a throwing parseHTTP call inside #require lacked its own try. Repair evaluates the throwing parser first, then unwraps its optional with #require; all parser assertions remain. Local address conversion also replaces the newly deprecated cString array initializer. Swift package execution and native app/smoke results are not inferred from Android build success.

Independent source review found two bounded follow-ups included in the repair batch: Android repeated Host taps could enqueue identity preparation before a server became active (claim preparing before dispatch and fence stale work); an oversized post-mutation response could escape before entering the replay ledger (cache a bounded error reply on both platforms instead, with actual TLS duplicate-side-effect regression coverage). Final changed-head CI evidence remains required.


The first-head iPhone smoke build (39126848616, job 117673666097) independently exposed Swift's conflicting-isolation diagnostic on the lazy AppServices browser-host initializer. The repair moves construction into the existing composition initializer through an explicit MainActor factory; controller and library captures remain immutable. Its WebKit callbacks also emitted near-match warnings because the new SDK requires MainActor/Sendable completion closures; all three handlers now use the exact protocol closure annotations rather than suppressing warnings. These are changed-source repairs, not acceptance claims.

## PR #51 session identity test failure — 9e0519e (October 18, 2026)

Reconstruction run 39127315432 completed Android validation but failed one of 182 Swift package tests: `BrowserRemoteProtocolTests.sessionIdentityCanBeRetrievedAndRemoved` threw `invalidInput` during `BrowserRemoteIdentity.install`. The independent certificate generation/trust test passed, so the failure is within Keychain insertion or identity retrieval, not certificate construction. The previous implementation used iOS-only Keychain accessibility attributes on the macOS SwiftPM runner and searched for an identity using the certificate's custom label. Neither is a reliable macOS identity lookup contract. The repair candidate omits the iOS accessibility attribute only on macOS, preserves device-only accessibility on iOS, and enumerates identities to match the exact generated certificate bytes rather than accepting an unrelated identity. It also retains the OSStatus of failed Keychain operations without logging keys, certificates, pairing codes or identities. The existing regression test continues to verify certificate equality and private-key retrieval; no assertion is removed.

This is a targeted repair candidate until its exact commit passes hosted Swift package tests and simulator compilation. Run 39127315433 independently passed the focused iPhone smoke on the original failing source; that does not clear the Swift package test failure. No full iPhone/iPad walkthrough, signing or release is implied.

## PR #51 macOS SwiftPM Keychain entitlement boundary — b77d3e6 (October 18, 2026)

Repair attempt b77d3e6 preserved successful Android validation (39128897852) and produced a more precise Swift failure: `SecItemAdd` returned OSStatus `-34018` (`errSecMissingEntitlement`) when the unsigned macOS SwiftPM test runner attempted to install a private session key. This is a macOS test-host entitlement limitation, not evidence that the iOS application Keychain path works or fails. Merely adjusting certificate lookup or macOS accessibility attributes cannot supply an absent entitlement. The preceding patch remains valuable for exact-certificate identity selection and nonsecret OSStatus diagnostics, but it did not resolve the test environment.

The next candidate preserves the complete identity round-trip assertions for entitled macOS hosts, allows only the exact missing-entitlement condition on the unsigned macOS runner, and adds the same certificate/private-key installation plus removal assertions to app-hosted iOS simulator XCTest, with an explicit LocalNetworkControl test dependency. The simulator test must actually execute and pass; build-only success or an ignored macOS entitlement error is not acceptance. Do not remove the new native test or claim Keychain support without its named passing result. Exact-head CI and device acceptance remain pending.


## PR #51 Release-mode native test visibility — October 18, 2026

Focused iPhone smoke [39129484883](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39129484883) failed at `build-for-testing` on head `2e67879` because `BrowserRemoteIdentityNativeTests` used `@testable import LocalNetworkControl` while the Swift package dependency was compiled in Release without `-enable-testing`. Reconstruction [39129484882](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39129484882) passed on the same head, so the failure is isolated to native XCTest's import visibility, not SwiftPM production compilation. The repair candidate exports the existing identity operations through a narrowly named Swift `@_spi(Testing)` interface and switches only the native test import to that SPI. It preserves Release optimization, production Keychain behavior, certificate/private-key assertions and removal checks; it does not enable testability globally or weaken the test. Hosted exact-head compilation and execution of `testSessionIdentityInstallationAndRemoval` are required before marking the repair verified. App-hosted unsigned simulator Keychain entitlements may still be a separate runtime gate. No release or device acceptance is implied.


## PR #51 SwiftPM SPI import follow-up — October 18, 2026

The first repair candidate `1b93450` exposed the existing native Keychain test seam through `@_spi(Testing)` and changed the app-hosted XCTest import. Reconstruction [39135792551](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39135792551) immediately reported `cannot find 'BrowserRemoteIdentity' in scope` while compiling `LocalNetworkControlTests`: marking the type as SPI also hides it from the ordinary `@testable import` used by the SwiftPM suite. This is a test-import visibility regression, not a runtime Keychain failure. The follow-up changes the existing SwiftPM test import to `@_spi(Testing) @testable import LocalNetworkControl`, retaining internal access needed for `Material.privateKey` and all certificate/private-key assertions. The syntax was independently compiled in a minimal Swift module. Exact-head hosted SwiftPM, Release iOS native compilation and simulator execution remain pending; no test has been skipped or removed.


## PR #51 unsigned iPhone Keychain entitlement gate — October 18, 2026

On exact source `215e02a`, [reconstruction 39136036615](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39136036615) passed Android and iOS jobs, including all Swift package tests and unsigned simulator compilation. [Focused iPhone smoke 39136036597](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39136036597) compiled its native/UI bundles, then ran the new `BrowserRemoteIdentityNativeTests.testSessionIdentityInstallationAndRemoval`: `SecItemAdd` failed with `errSecMissingEntitlement` (`-34018`) at `BrowserRemoteIdentity.install`. Five WebKit lifecycle tests and all four focused UI tests passed, but the smoke workflow correctly failed because the Keychain test did not. The workflow explicitly builds and runs with `CODE_SIGNING_ALLOWED=NO`; its test host therefore cannot be assumed to have the Keychain access-group entitlement. This is a **confirmed entitlement failure**, not proof of a production Keychain implementation defect or successful runtime acceptance. The first candidate's SPI import failure was corrected by `215e02a`; this is the next independent gate.

Next action requires an authorized, simulator-only signed test-host configuration with inspectable entitlements (or an equivalent entitled app-hosted environment), followed by execution of the unchanged Keychain round-trip/deletion assertions on that exact signed candidate. Do not skip, mock, suppress or mark the test passed. Production signing credentials, release distribution and real-device acceptance remain separately gated. No blind rerun of the same unsigned configuration is justified.


## Main-branch fullscreen Search identity regression — October 24, 2026

- Failed source: `ca23b87871feb5b384c1613d8ba9d75457575456`; reconstruction [run 40083690132](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40083690132) failed the iOS source guard, and [walkthrough 40081230815](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40081230815) failed the iPhone fullscreen Search step.
- Confirmed cause: production gives the embedded Search button `stage.search` and the fullscreen Search button `stage.search.fullscreen`, but the fullscreen XCTest still queried `stage.search`. The source-contract check also failed to enforce the distinction. This explains the iPhone fullscreen lookup failure and the deterministic guard failure; it does **not** explain the separate iPad missing-keypad failures.
- Repair candidate: query `stage.search.fullscreen` only for the fullscreen button, assert that the embedded `stage.search` query remains in the suite, and make the source guard require both exact query expressions. Update the testing contract and retain the original runtime assertions.
- Acceptance: run the source guard, Swift package tests, focused iPhone smoke, and full iPhone/iPad walkthrough on the exact repair commit. The iPad keypad/presentation failures remain open until their own runtime evidence passes.

## iOS — embedded Stage keypad absent on iPad after Host-sheet consolidation (2026-10-24)

- Failed source: main `ca23b87871feb5b384c1613d8ba9d75457575456`; full walkthrough [#40081230815](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40081230815). The iPhone also failed the fullscreen Search lookup; the iPad failed the embedded keypad cases.
- Confirmed: the fullscreen test queried `stage.search`, while production assigns `stage.search.fullscreen`. The iPad hierarchy showed the embedded Host stage and Search control but no `stage.keypad.0` element after the tap. The precise UIKit presentation path is not logged, so do not claim a proven low-level cause.
- Repair candidate in [PR #67](https://github.com/usermalik20-del/Kanta-reborn/pull/67): correct the fullscreen identifier and its source guard; replace the embedded Stage SwiftUI sheet with a dedicated `KantaEmbeddedStageSheetPresentation` UIKit owner attached to HostView's background. Host panels and fullscreen child sheets retain their existing owners; all use the shared gate and authoritative HostViewModel. Add a source guard for the presenter and retain all runtime assertions.
- Status: candidate only. The full walkthrough and reconstruction must pass on the exact final PR head. The earlier green archive is build evidence only, not device acceptance.

## Focused iOS smoke false failure from unrelated RTK CLI — October 24, 2026

- Evidence: [focused smoke run #40085904995](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40085904995), PR #67 candidate `61057d0531b3c12316851c9091a5addcec69526d`. App/UI-test compilation and the focused XCTest step succeeded; the four selected UI tests passed with zero failures, and the verification script listed the expected native lifecycle/UI cases. The job failed only in the subsequent `Run RTK Audio Analysis Suite` step at `rtk latency`: `must be run as root`.
- Confirmed root cause: merged PR #65 added an installer for [rtk-ai/rtk](https://github.com/rtk-ai/rtk), whose repository describes a CLI for reducing LLM token consumption, not an audio-analysis tool. The step was incorrectly labelled as gain/latency/spectral audio validation and ran a mutable `curl | sh` installer. The root-only latency command returned exit 1 and, with shell fail-fast behavior, stopped the remaining step. This is a CI workflow defect, not a KANTA playback regression.
- Repair: remove the unrelated installation, pseudo audio-analysis commands, and their artifact upload. Do not run the unrelated command as root. Keep all KANTA source guards, native lifecycle tests, focused XCTest assertions, and evidence uploads intact. A future audio-quality gate must use an independently reviewed KANTA-specific measurement fixture and explicit acceptance thresholds.
- Acceptance: rerun focused smoke on the repaired exact SHA and inspect the full iPhone/iPad walkthrough separately. This workflow fix does not resolve the still-open iPad embedded-keypad failure.



## 2026-10-24 — iPad embedded Stage presenter readiness

**Evidence:** PR #67 walkthrough run [#40093013311](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40093013311) passed the iPhone stage but failed four iPad walkthroughs; Search taps did not expose `stage.keypad.0`. The log does not contain a UIKit rejection trace, so the exact low-level cause remains unconfirmed.

**Candidate repair:** mount the embedded UIKit presenter at full Host bounds, retry its state machine after layout, recover an orphaned owned controller, and log deduplicated blocked states. The demo keypad test now waits for each digit to appear. Validate the source guards, Swift tests, focused iPhone smoke, and full iPhone/iPad walkthrough on one exact head. Do not merge or release based on compilation or iPhone-only success.

## PR #67 keypad assertion contract regressed during main synchronization — October 24, 2026

- Evidence: [focused iPhone smoke #40100439415](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40100439415) failed in the pre-XCTest source-contract step on candidate `a09be411`; native lifecycle and UI tests were skipped because the build/gate step stopped first.
- Confirmed cause: the main conflict-resolution commit `92a5aab` replaced the ten per-digit asynchronous waits with a compact `XCTAssertTrue(...exists)` loop. The PR's stronger source guard correctly rejected that regression before simulator execution.
- Repair: [commit `b36108e`](https://github.com/usermalik20-del/Kanta-reborn/commit/b36108e2dffc30957161b2ce0e9f24eb3c134021) restores `waitForExistence(timeout: 10)` and a digit-specific failure message for all ten keypad buttons. No assertions are removed or weakened.
- Acceptance: the corrected source guard, focused smoke, reconstruction, and full iPhone/iPad walkthrough must pass on the same final candidate SHA. The current main walkthrough [#40099286186](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40099286186) has passed iPhone and is still running iPad at this snapshot; do not infer iPad acceptance from the earlier iPhone pass.

## iPad embedded Stage presentation remains unresolved — October 24, 2026

Main walkthrough [#40099286186](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40099286186) and candidate walkthrough [#40100730941](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40100730941) both passed the iPhone stage and failed all four expected iPad walkthrough cases. The failures consistently occur after tapping embedded Search: `stage.keypad.0` never appears. The candidate XCTest now waits for the keypad rather than making an immediate existence assertion, so the remaining failure is not explained by the prior assertion-timing defect.

The presenter source already contains blocked-state logging, but the runner did not preserve simulator unified logs; the job output therefore does not establish whether the request was queued, the presenter lacked a window, another controller owned presentation, or UIKit rejected the presentation. The diagnostic repair adds request/gate/presenter lifecycle events, captures filtered simulator logs before shutdown, and uploads a compact `ios-ipad-presentation-diagnostics` artifact separately. The next exact-head walkthrough must classify the first missing lifecycle event before changing modal ownership again. This commit adds evidence collection only; it does not claim the iPad presentation bug is fixed.


## 2026-10-25 — Window-tap probe blocked native builds

**Evidence:** [reconstruction run #40119874396](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40119874396) passed source guards and Swift package tests but failed the unsigned simulator build. [Unsigned archive run #40119874432](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40119874432) and [walkthrough run #40119876903](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40119876903) failed at the same source lines. Xcode reported that NSStringFromCGPoint and NSStringFromCGRect have been replaced by NSCoder.string(for:). The walkthrough's missing test-runner bundle identifier followed the failed compile and is not independent iPad runtime evidence.

**Repair:** use NSCoder.string(for:) for the window point and hit rectangle; convert hit bounds to window coordinates so both values share a coordinate system; detach the non-cancelling window recognizer when the representable is dismantled; and add a source-contract check for SDK-compatible geometry formatting and recognizer cleanup. Update testing guidance to keep the compiler failure separate from the still-unresolved iPad Search hit-testing issue.

**Acceptance:** verify the source guard, Swift package tests, simulator app build, unsigned archive and focused smoke on one exact candidate SHA. Then run the full iPhone/iPad walkthrough on that same SHA. Do not claim iPad behavior is fixed from build success alone.

## iPad Search intercepted by the decorative stage canvas — October 25, 2026

**Runtime evidence:** PR candidate walkthrough [#40122872536](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40122872536) failed the four iPad keypad cases. Its compact diagnostic artifact [#12572620221](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40122872536/artifacts/12572620221) records a tap at `window-point={668, 148.5}`, the center of the accessibility-reported `stage.search` frame, resolving to `_UIGraphicsView {{32, 117}, {770, 433}}`. That drawing surface overlaps the Search action row and wins UIKit hit testing, so the button action never reaches the presentation gate. This is more specific than the earlier hypothesis about modal ownership.

Main's current reconstruction [#40126620995](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126620995) and unsigned archive [#40126621029](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621029) passed, but full walkthrough [#40126621040](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621040) failed: the iPad repeated the four keypad failures, and the iPhone stage did not report `testMainMenuButtonsAndScreens` as passing. The main-run compact diagnostic artifact [#12574327088](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621040/artifacts/12574327088) did not preserve `[KANTA_TAP]` events, so use the earlier candidate's tap trace as the direct hit-test evidence and retain the probe until exact-head acceptance.

**Repair:** mark both `KantaCameraPreview` and `KaraokeStageView` branches in `stageBackground` as non-hit-testable. These are display-only layers; user interaction must be handled by the explicit Search/Camera/Expand controls. The source guard checks that both branches carry `.allowsHitTesting(false)`.

**Acceptance:** source-contract guard, Swift package tests, simulator build, focused smoke, reconstruction, unsigned archive, and the full iPhone/iPad walkthrough must pass on one exact candidate SHA. In particular, confirm that a Search tap no longer resolves to the oversized drawing surface and that `stage.keypad.0` appears on iPad. Build success or an iPhone-only pass is not device acceptance.
