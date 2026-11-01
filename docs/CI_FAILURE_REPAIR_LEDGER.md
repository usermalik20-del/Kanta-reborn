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

## All workflows — documentation-sync guard aborted on an unreachable base (2026-10-31)

**Fingerprint:** every job of `android-validation.yml`, `ios-validation.yml`, `ios-runtime-artifacts.yml` and `ios-visual-walkthrough.yml`; step `Enforce documentation synchronization before release`; log phrase `fatal: Invalid symmetric difference expression <sha>...<sha>` immediately followed by `##[error]Process completed with exit code 2.` The whole job dies in under 35 s and the artifact is the empty 234-byte bundle, because no build step ever runs.

**Affected SHA / first observed run:** introduced with `661e640` (Android run [#374](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41177031732)) and continuing through `3b62b2f`, `09e2b12`, `3688225`, `f6fcebe` and `076e2b0`. Confirmed on all four workflows at `076e2b0`.

**Cause (confirmed, two distinct defects):**
1. The step derives its base from `github.event.before`, which names the branch tip that a push replaces. A force-push makes that commit unreachable, and `actions/checkout` with `fetch-depth: 0` fetches reachable history only, so the object is absent from the runner. `git diff <absent>...<head>` aborts the process, so the guard surfaced a raw git crash instead of a documentation verdict and failed the job before any build, lint or emulator step. Reproduced exactly by passing a bogus base SHA to the checker.
2. The commits that landed the pitch feature genuinely omitted the owner documents the guard requires, which is the guard's intended verdict and was fixed by including them.

**Fix:** [`4ffe9e0`](https://github.com/usermalik20-del/Kanta-reborn/commit/4ffe9e0) — `tools/check-documentation-sync.py` now resolves the base itself: an available revision is used unchanged, an unavailable one falls back to the head commit's parent with an explicit notice on stderr, and a head commit with no parent fails closed. `docs/TESTING.md` records the behaviour and the rule that the guard must never be satisfied by force-pushing again or by widening its path filters.

**Regression coverage:** `tools/tests/test_documentation_sync.py` gains three cases — available base kept unchanged, absent base falls back to the head parent, empty base falls back. The tool always prints the fallback, so the weakened comparison is visible rather than silent.

**Validation:** `4ffe9e0` — `KANTA website validation` and `Verified download packaging` both green on the push, and Android validation advanced past this step into `Build, lint and domain regression tests`. Locally the exact failing input now exits 0 with the fallback notice where it previously exited 2.

**Recurrence rule:** never compare against `github.event.before` without verifying the object exists in the checkout, and never force-push a main-branch release path. Read the FIRST failing step before editing source: six consecutive commits were spent changing Java for a failure that occurred before any compiler ran.

## Android — pitch-settings source did not compile (2026-10-31)

**Fingerprint:** Android job, step `Build, lint and domain regression tests`; `Execution failed for task ':app:compileDebugJavaWithJavac'`; `MainActivity.java:727: error: illegal start of expression`, `:737/:771/:790/:826: error: illegal character: '#'`.

**Affected SHA / first observed run:** `4ffe9e0` (run [#41185625711](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41185625711)) — the first run in which this step executed at all, because every earlier run died in the documentation-sync step above.

**Cause (confirmed, three defects in one file):**
1. `showPitchSettings()` was inserted over the body of `copyToClipboard(String, String)`, truncating that method — `illegal start of expression`.
2. Four section comments were written with `#` instead of `//` (Python comment syntax in Java source) — `illegal character: '#'`.
3. `ph.kanta.reborn.settings.SettingsStore` was used without an import. This was masked because `javac` reports parse errors and never reaches symbol resolution, so it would have been the *next* failure after the syntax errors were cleared.

**Fix:** repaired on main; the diff of `MainActivity.java` against the last validated baseline (`fc6bb7a`, Android run #373) now contains only the intended additions — a `SettingsStore` import, the pitch buttons in `controlsAccessory`, and the new `showPitchSettings()` method. Nothing else in the file changed.

**Regression coverage:** the Android `Build, lint and domain regression tests` step itself, which now receives the source because the documentation-sync step no longer aborts. Brace-balance and string-aware scanners can detect structure only and never prove a file compiles.

**Validation:** pending the next Android validation run on the repaired SHA.

**Recurrence rule:** treat the first clean parse as an unverified compile — after clearing syntax errors, re-check every type the change introduces against its import. Never generate Java source through a Python string builder that also emits Python comments, and never insert a method by line index without re-reading the method it lands in.

## Android — predictive-back lint regression (2026-10-27)

**Fingerprint:** reconstruction [run #40485807248](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40485807248), Android job `android`, step `Build, lint and domain regression tests`; `GestureBackNavigation` at `apps/android/app/src/main/java/ph/kanta/reborn/MainActivity.java:177`.

**Cause (confirmed):** the Compact Workspace commit added an `Activity.onBackPressed()` override to dismiss transient accessories. The app targets API 36, where predictive-back gestures bypass the deprecated activity override. Android lint correctly failed the build; browser and emulator checks were skipped after that failure. The iOS job, website validation, deployment and unsigned iOS archive passed on the same source.

**Fix merged:** [PR #101](https://github.com/usermalik20-del/Kanta-reborn/pull/101), merged as [`9a2a0fc`](https://github.com/usermalik20-del/Kanta-reborn/commit/9a2a0fca3778ff10eb549c319d7f400f33661b28). The implementation registers a lifecycle-bound AndroidX `OnBackPressedCallback` and enables it only while an accessory is active. The workspace listener disables it when the accessory closes, allowing default dispatcher navigation when stage-only. No lint suppression or baseline change was used.

**Regression coverage:** `RuntimeChecks.workspaceBackNavigationChecks` opens Search, dispatches back through `OnBackPressedDispatcher`, and asserts Search closes, the persistent stage remains attached, and the callback is disabled. `tools/check-android-workspace-contract.py` rejects the legacy override and requires dispatcher registration and runtime coverage.

**Exact-candidate validation before merge:** reconstruction [#40488936819](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40488936819), unsigned archive [#40488936791](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40488936791), and iPhone/iPad walkthrough [#40488936570](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40488936570) passed on candidate `83b195b`; both device jobs passed.

**Post-merge validation:** reconstruction [#40499591316](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591316), unsigned archive [#40499591326](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591326), and full iPhone/iPad walkthrough [#40499591484](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591484) all passed on merge SHA `9a2a0fc`. Android lint/domain, browser interoperability, private-interface validation and emulator runtime passed; both iOS device jobs executed and passed. The defect is resolved. Keep the historical failure and this passing repair evidence together.

**Recurrence rule:** keep predictive back in AndroidX's dispatcher; never restore `Activity.onBackPressed()` or suppress `GestureBackNavigation`. Verify callback enablement follows accessory activation/dismissal so stage-only back falls through to normal navigation.

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


## iOS — Main-menu walkthrough exceeds the per-test budget (2026-10-25)

**Fingerprint:** `KantaVisualWalkthroughUITests.testMainMenuButtonsAndScreens`; XCTest message `exceeded execution time allowance of 2 minutes`; iPad simulator in [walkthrough run #40136025937](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025937), source SHA `d01504e42b3e00735bb429b05e9ec886149911fe`.

**Related results:** reconstruction [#40136025964](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025964) and unsigned archive [#40136025858](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025858) passed. The iPhone stage passed. The iPad compact evidence is [artifact #12577317727](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025937/artifacts/12577317727).

**Confirmed cause:** the main-menu test relaunches and terminates the app for each of six destinations. On the iPad simulator, the sixth navigation flow was still progressing at 120 seconds; XCTest timed out and restarted the runner. The required-test manifest correctly rejected the run because `testMainMenuButtonsAndScreens` had no passing completion. This is a test-budget/lifecycle inefficiency, not evidence that Search still fails: in the same run, the keypad-focused tests passed and the tap trace showed Search reaching `_UIShapeHitTestingView`, requesting/activating the Search sheet, and beginning presentation.

**Candidate fix:** keep one app process alive across the six menu destinations; after each screenshot, dismiss through the existing `navigation.done` accessibility identifier, wait for that destination to leave the hierarchy, and verify the Stage More button is visible again before opening the next destination. Preserve all six destinations and screenshot attachments. Do not raise the global timeout or weaken the required-test set merely to hide the repeated cold-start cost.

**Regression:** `testMainMenuButtonsAndScreens` must complete and appear in the passed-test manifest on both iPhone and iPad; each destination must be opened, captured, and dismissed. Search keypad tests remain required.

**Validation:** `candidate`; code change [`8e67e1e`](https://github.com/usermalik20-del/Kanta-reborn/commit/8e67e1e2953c0e13f077ac87421b7cd44cea1ab9). Hosted validation on the final repair SHA is pending. Do not mark verified until the full walkthrough and reconstruction pass on the exact accepted SHA.


## iOS — encrypted-companion UI keyboard-idle timeout (2026-10-25)

- **Failed run:** [#40140289375](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40140289375), candidate SHA `c1bdcf57500f45f15e83876a5b4ae6fcb6387f5f`; job `visual-walkthrough`, iPhone stage failed while iPad stage passed.
- **Fingerprint:** `testEncryptedCompanionPairingScreenAndInputRejection` exceeded XCTest's two-minute execution allowance. The iPhone log shows `typeText("8.8.8.8")` entering the host field, followed by “App animations complete notification not received” after about 60 seconds and an XCTest runner restart. The resulting `xcodebuild` exit code 65 remains a failed run even though the aggregate log listed all named cases as passing across attempts.
- **Cause:** the UI test coupled protocol validation to software-keyboard automation; a simulator keyboard-idle stall consumed the case budget before the input-rejection flow completed. This is not evidence of a pairing-protocol failure.
- **Repair candidate:** make `CompanionProtocol.isValidPairingInput(address:code:)` the shared Connect/handshake policy, add pure package tests for private/public/loopback address rules and exact 32-character hexadecimal secrets, and keep the UI test focused on the initial disabled state and explanatory copy rather than typing through a flaky simulator keyboard.
- **Regression coverage:** the same helper drives `EncryptedCompanionView` Connect enablement and handshake secret validation; the source-contract guard enforces that relationship. Existing encrypted pairing screen, disabled Connect, no Disconnect, and instructional-copy assertions remain.
- **Validation:** candidate only; do not mark verified until the full iPhone and iPad walkthroughs both exit zero on the exact final SHA and all required cases pass in a clean run. No timeout increase, runner retry, or ignored exit status.

## iOS — iPad menu walkthrough still exceeds one XCTest case budget (2026-10-25)

**Fingerprint:** `KantaVisualWalkthroughUITests.testMainMenuButtonsAndScreens`; iPad visual walkthrough; missing passing case after XCTest timeout/restart.

**Affected SHA and run:** PR #71 candidate `dc60e513c4829226d8bf763c60d5e047e0beb5a4`; [walkthrough #40146182462](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182462), with compact diagnostics [artifact #12580469774](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182462/artifacts/12580469774).

**Cause:** the earlier optimization kept all six More destinations inside one XCTest method. Although each destination screenshot was captured, the single method still failed to record a clean pass before XCTest's per-case allowance and runner restart. The aggregate run therefore remained red; screenshots or later retry output do not satisfy the named-pass manifest.

**Candidate repair:** split Song Library, KANTA Songbook, Settings, Companion Remote, iCloud Save, and Legal & Privacy into six named XCTest methods. Each retains its destination title assertion, screenshot, `navigation.done` dismissal, and Stage More return assertion. Update `tools/capture-ios-runtime.py` and `tools/check-ios-presentation-contract.py` together. Integrate PR #72's separate iPhone/iPad workflow jobs, preserving branch-level stale-run cancellation.

**Regression:** the source guard requires all six test names in both the XCTest source and runtime manifest, rejects the old aggregate method, and the runtime gate rejects any missing pass. No destination/assertion was removed; no global timeout was increased.

**Validation:** `candidate` until the new exact-head reconstruction, focused smoke, archive, and independent iPhone/iPad walkthrough jobs finish successfully. No green result is inferred from the previous SHA.

**Recurrence rule:** if a device reports a missing menu case, inspect that individual case's XCTest result bundle and job log. Do not increase the global timeout, suppress missing cases, or treat screenshot presence as a pass.


## Responsive website navigation contract and superseded drawer repair — October 25, 2026

Main advanced to [`00d2405`](https://github.com/usermalik20-del/Kanta-reborn/commit/00d2405b5c54d8ae56ee44fed99a98a9ab97d20e), incorporating responsive redesign commit [`770a6dc`](https://github.com/usermalik20-del/Kanta-reborn/commit/770a6dc7c8e51337349a75afddb5fcbf08f3e80b). Website validation [#40160483259](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40160483259) and Vercel deployment [#40160483212](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40160483212) passed. The new source standardizes the primary header across all ten public pages, removes the earlier adjacent duplicate feature headings and orphaned CSS fragment, and uses a visible wrapping navigation instead of a mobile drawer.

PR #74 was closed as superseded rather than merged because its original drawer markup was based on the previous design. Its first website unit-test run [#40160477572](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40160477572) failed because the new duplicate-heading test called `.values()` on the existing `PUBLIC_ROUTES` set. Commit [`bd1fff3`](https://github.com/usermalik20-del/Kanta-reborn/commit/bd1fff39c678b2bc6a922d3543fda8e4b210777e) corrected that harness bug, but the branch was not merged because the UI contract had changed.

The current test-only candidate `test/web-responsive-navigation-contract-20261025` checks the six shared primary routes, flex-wrapping and visible navigation rules, duplicate heading prevention, and stylesheet hygiene. It does not change website runtime markup or native app code. Hosted validation and post-deploy confirmation remain pending until this candidate is accepted.

## Main acceptance reconciliation — October 25, 2026, 13:57 PHT

This checkpoint supersedes the earlier **pending candidate** wording in the preceding entries; those entries remain as historical records of the failures and their repair sequence.

The validated application baseline before this documentation-only reconciliation is [`af178739`](https://github.com/usermalik20-del/Kanta-reborn/commit/af178739f9f457b917d7d0714963ccad22f4bcd2). Exact-head results for that baseline are green for [iOS/Android reconstruction #40165173586](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40165173586), [website validation #40165173693](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40165173693), [production deployment #40165173539](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40165173539), and [unsigned iOS archive #40165173529](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40165173529).

The latest completed full native walkthrough is [#40154268944](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40154268944) at native commit [`a543df2`](https://github.com/usermalik20-del/Kanta-reborn/commit/a543df231322ea495eef6e9d7ad8968d17490bc7); both iPhone and iPad jobs passed. The compare from that commit to `af178739` contains only website content/assets, website verification code, and documentation, with no native app source changes.

Main archive artifact [#12588545812](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40165173529/artifacts/12588545812) is 25,076,208 bytes with SHA-256 `857c60aaaf0a2ab6d17aee97f68441a35a645d5b88de6831853fb8ad64d6b042`; it expires 2026-11-01 05:25:49 UTC. It is unsigned and is not a directly installable production release. No signed IPA or published GitHub Release is verified.

Remaining gates: maintain the current architecture/test contracts, obtain repository-administration access before claiming branch protection is enforced, and keep signing/publication gated on a signed artifact with verified provenance.

## Website — homepage redesign diverged from the shared navigation contract (2026-10-26)

**Fingerprint:** tools/verify-website.py raises AssertionError for index.html because the redesigned navigation uses nav-links rather than the old single site-nav class.

**Affected commit / first observed runs:** main merge 9acf94f (PR #79). Website validation [run #40212525044](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40212525044) and deployment validation [run #40212525043](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40212525043); deployment was correctly skipped after its validation gate failed.

**Cause:** the new homepage uses nav-links and a five-item mix of anchors / .html routes, while the architecture requires the same six canonical destinations on all ten public pages. The validator and unit test also hard-coded the old single site-nav class and obsolete CSS selector strings, even though the active stylesheet already groups nav, .site-nav, and .nav-links under a flex-wrapping rule. This is a real route-contract violation plus a stale test contract, not a deployment-platform failure.

**Candidate fix:** branch fix/website-navigation-contract-20261026 restores the six canonical routes on the homepage, accepts either shared class token without weakening the route assertions, and validates the actual shared flex-wrap / visible-navigation CSS rule. Regression coverage remains in test_all_public_pages_share_the_responsive_primary_navigation and test_primary_navigation_wraps_and_remains_visible_on_mobile.

**Validation:** candidate; hosted checks on the repair head are pending. Do not mark verified until website validation passes on the exact PR head and the post-merge deployment succeeds.

**Recurrence rule:** when the site visual design changes, preserve the six canonical destinations and use semantic nav parsing; do not fix this failure by removing route-parity checks or by changing the expected set to match an incomplete homepage.

## Website — redesigned homepage dropped the same-origin analytics entrypoint (2026-10-26)

**Fingerprint:** website validation / Vercel deployment-contract validation raises “Homepage analytics must use the same-origin script”.

**Affected candidate / runs:** PR #81 candidate before the follow-up fix; website validation [run #40217979337](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40217979337) and Vercel validation [run #40217979356](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40217979356). Deployment was correctly skipped.

**Cause:** the PR #79 homepage redesign removed the existing same-origin analytics script tag, while analytics.js and the homepage's allowlisted CSP remained in the repository. This was an actual integration regression, independent of the navigation-contract failure.

**Candidate fix:** commit d7eec50 restores the existing same-origin analytics entrypoint; it does not add a third-party inline script or change CSP permissions.

**Validation:** candidate; rerun website validation on the current PR #81 head before marking verified.

**Recurrence rule:** homepage visual rewrites must retain the same-origin analytics entrypoint unless analytics is intentionally retired with coordinated CSP, code, privacy and contract updates.



## Reconciled acceptance — 2026-10-26, after PR #81 merge

Main commit [`5ee1893`](https://github.com/usermalik20-del/Kanta-reborn/commit/5ee1893974f30829e14eda9d3e233d6b40690066) restores the six canonical homepage routes and the same-origin analytics entrypoint. Post-merge reconstruction [#40220533959](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40220533959), website validation [#40220534010](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40220534010), Vercel deployment [#40220534153](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40220534153), and unsigned archive [#40220533986](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40220533986) all passed. Earlier entries recording PR #81 validation as pending are historical, not current state.

## iOS Stage v2 — LazyVGrid starter playback tests required an explicit scroll (2026-10-26)

**Failure evidence:** PR #80 focused smoke [#40215508539](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40215508539) and full walkthrough [#40215507943](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40215507943) failed before the Morning Light playback assertions because Starter Songs was below the initial viewport and LazyVGrid had not instantiated its off-screen cells.

**Repair:** candidate [`65e03c1`](https://github.com/usermalik20-del/Kanta-reborn/commit/65e03c170519112395d2ea514f04d12b369fed39) identifies `library.content`, scrolls to Starter Songs before querying the play control in both starter-playback flows, and adds a source guard for the scroll container/helper relationship. The playback assertions were preserved.

**Exact-candidate validation:** reconstruction [#40218224023](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40218224023), focused smoke [#40218223728](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40218223728), full iPhone/iPad walkthrough [#40218223740](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40218223740), and unsigned archive [#40218223776](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40218223776) all passed on `65e03c1`; both device jobs passed. Candidate artifact [#12608094930](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40218223776/artifacts/12608094930) is 25,120,093 bytes with SHA-256 `b4a3a25c95a3d63c0de5ac82ca7a7a6c6181571dfeb77e789bf4d58c5b49cdcd`, unsigned.

**Current acceptance:** PR #80 was 10 commits ahead and 12 behind main before synchronization. The synchronized merge head must pass all four workflows again on one exact SHA before the PR can merge. Prior candidate green results do not transfer to a new head.


## iOS Stage v2 — exact-head acceptance and merge (2026-10-26)

**Candidate:** PR [#80](https://github.com/usermalik20-del/Kanta-reborn/pull/80), head [`f0b4902`](https://github.com/usermalik20-del/Kanta-reborn/commit/f0b4902f745dcfc39b1a5a8bb64425ff660b4a25); merged as [`db53397`](https://github.com/usermalik20-del/Kanta-reborn/commit/db533979834a4614914bd215299bb636e52bde36).

**Original failure fingerprint:** `KantaVisualWalkthroughUITests` could not find the starter-song Play control because SwiftUI's lazy grid had not materialized off-screen cells. The iPad follow-up also asserted that the section heading itself must be hittable, which was not a reliable interaction contract.

**Confirmed cause and repair:** the tests queried controls before scrolling into the Library's starter-song section. The helper now locates `library.content`, scrolls until the actual Morning Light title and `starter.play.morning-light` button exist and are hittable, and preserves the playback assertions. The matching source guard enforces the scroll-to-controls behavior rather than the heading hit test. The redesign also removes a no-op “See all” button and replaces an unsupported fixed `3:12` duration with `KANTA original` metadata.

**Regression coverage:** `testStarterPlaybackAndControls`, `testOnlinePreviewPreservesPausedStarterAndLocalResume`, and the release-tooling design-system adoption tests. The playback assertions were not deleted or skipped.

**Exact-head validation on `f0b4902`:**
- Reconstruction, iOS + Android: [#40237740473](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40237740473) — passed.
- Focused iPhone smoke: [#40237740476](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40237740476) — passed.
- Full iPhone/iPad walkthrough: [#40237740472](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40237740472) — iPhone and iPad jobs both passed.
- Unsigned iOS archive: [#40237740485](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40237740485) — passed.

**Candidate artifact:** [#12615254075](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40237740485/artifacts/12615254075), 25,119,209 bytes, SHA-256 `e79598ff0f2966a113db82253dc5edbd188f19e8ca4f35b3572a657d3c411a35`, expiration 2026-11-01 21:58:39 UTC. Unsigned; not a signed production release.

**Post-merge validation:** merge commit `db53397` triggered reconstruction [#40242088043](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40242088043), iPhone/iPad walkthrough [#40242088004](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40242088004), and unsigned archive [#40242088000](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40242088000). All three were in progress at the time this record was added. Keep post-merge status distinct from the green PR-head evidence and append the final result when known.

**Status:** candidate validated and merged; post-merge acceptance pending. Signing and publication remain unverified.

**Recurrence rule:** scroll to real lazy content controls before querying them; do not use section-header hittability as a proxy for visible playable content, and do not weaken playback assertions to get a green walkthrough.


## Android browser validation — Chrome DevTools startup deadline too short (2026-10-26)

**Fingerprint:** reconstruction-validation Android job, step `Verify HTTPS companion in real Chrome`; `AssertionError: Chrome DevTools did not start`.

**Affected commit / first failure:** PR #80 merge commit [`db53397`](https://github.com/usermalik20-del/Kanta-reborn/commit/db533979834a4614914bd215299bb636e52bde36), reconstruction [run #40242088043](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40242088043). Android source guards, SoundFont sanitizer, Gradle build/lint/domain checks, and companion asset/request-contract checks passed before failure. The failure occurred before CDP navigation and browser assertions.

**Cause:** `tools/verify-companion-browser.mjs` waited only 10 seconds for `DevToolsActivePort` and discarded Chrome stderr. The same harness passed on earlier main run [#40220533959](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40220533959) after about 8.9 seconds. On the failing runner the port file did not appear inside the fixed window, and suppressed stderr prevented distinguishing slow startup from a browser launch error. This was a harness startup/diagnostic defect, not evidence of a companion protocol or app-source regression.

**Fix:** PR [#82](https://github.com/usermalik20-del/Kanta-reborn/pull/82), candidate commit [`bb2313c`](https://github.com/usermalik20-del/Kanta-reborn/commit/bb2313c774f880efe710e3c00772b360a4e1c24d), merged as [`bb42695`](https://github.com/usermalik20-del/Kanta-reborn/commit/bb42695401a952c479b4fd47a2bf317191a3ce86). The harness now waits up to 30 seconds for the port file, captures the last 12 KB of Chrome stderr, distinguishes spawn/exit/timeout, and retries the local DevTools HTTP endpoint for five more seconds. The workflow's three-minute step timeout remains in place.

**Regression coverage:** existing real-Chrome TLS pairing, unverified-pairing rejection, keypad, search, safe metadata, distinct reservations, cancellation, Play/Pause/Stop, disconnect and 320–1024px layout assertions remain unchanged.

**Exact-candidate validation:** reconstruction [#40242647204](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40242647204) passed on `bb2313c`; the Android job completed the real-Chrome browser flow, captured-browser interoperability and emulator runtime checks. The iOS job passed Swift package tests and unsigned simulator build. Unsigned archive [#40242647144](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40242647144) passed.

**Post-merge validation:** main reconstruction [#40243337754](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40243337754) and main unsigned archive [#40243337745](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40243337745) were queued after merge; update this record when their final results are available.

**Status:** `verified on candidate; post-merge main confirmation pending`.

**Recurrence rule:** if DevTools fails to start, inspect captured Chrome stderr and child exit state first. Do not suppress the browser assertion, skip real Chrome, or treat static asset checks as browser acceptance.


## iOS UI smoke — Stage More accessibility identifier attached to the wrong control (2026-10-26)

**Fingerprint:** workflow `KANTA iOS focused simulator smoke`, job `ios-smoke`, test `KantaVisualWalkthroughUITests.testOnlinePreviewPreservesPausedStarterAndLocalResume`; `XCTAssertTrue failed` in `waitForRendered` while querying `stage.more`.

**Affected SHA / first observed run:** PR #83 head `6941aec198ad07eb46c5f7d8093b9361d0821750`, [focused smoke run #40244262594](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40244262594), [job #121322606681](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40244262594/job/121322606681). The complete job log shows the failure at `KantaVisualWalkthroughUITests.swift:525` after the test's initial menu helper could not find `stage.more`.

**Cause (confirmed):** PR #83 changed menu-test queries from the accessible label `More` to `stage.more`, but the identifier was attached only to the separate `moreButton` used by the fullscreen player's `controls(wide:)`. The initial embedded player uses a different chevron button with `Button(action: onMore)` and accessibility label `More`, without that identifier. The UI test therefore waited for an identifier that the initial screen did not expose; the failing assertion occurred before the paused-starter playback assertions. This is an accessibility identity mismatch, not evidence that playback itself regressed.

**Fix candidate:** commit [`1c2aeaa`](https://github.com/usermalik20-del/Kanta-reborn/commit/1c2aeaa56d864001e5894e546ba390185e57fedf) assigns `stage.more` to the actual embedded navigation button and renames the fullscreen-only control to `stage.controls.more`. It also adds a source-contract check in `tools/check-ios-presentation-contract.py` that verifies the identifier is attached to the correct button and keeps the two controls distinct.

**Regression coverage:** the existing menu and online-preview walkthroughs continue to query `stage.more`; the presentation-contract guard now fails if the ID drifts back onto the fullscreen-only control. No playback assertions were removed or weakened.

**Validation:** `candidate`. Fresh exact-head CI was triggered by `1c2aeaa`; its run IDs and final results must be recorded after they complete. The earlier green reconstruction/archive and the still-running walkthrough on `6941aec` do not validate this fix.

**Recurrence rule:** when `stage.more` is missing, inspect identifier placement in `embeddedPlayer` versus `controls(wide:)`. Do not add sleeps or loosen assertions; give distinct accessible controls distinct stable identifiers and keep the source-contract guard aligned.

**Status:** `candidate`.
## Reconciled main acceptance and PR #83 Android JVM crash — 2026-10-26

This section supersedes the earlier pending wording above for the post-merge PR #82 reconstruction/archive and the PR #80 device walkthrough.

**Main acceptance confirmed:** main code-bearing commit [`bb42695`](https://github.com/usermalik20-del/Kanta-reborn/commit/bb42695401a952c479b4fd47a2bf317191a3ce86) passed reconstruction [#40243337754](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40243337754) with both Android and iOS jobs successful. The main unsigned archive [#40243337745](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40243337745) passed and produced artifact [#12617498284](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40243337745/artifacts/12617498284), 25,119,210 bytes, SHA-256 `f71d6affa7a572da95a9fd26f66e42addbea7a8199414c26bc11a3c9bdb071fb`, expiration 2026-11-01 23:12:45 UTC. Full iPhone/iPad walkthrough [#40242088004](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40242088004) passed both device jobs on `db53397`. No signed production release is implied.

## PR #83 — Android Gradle daemon JVM crash on pre-sync merge ref (2026-10-26)

**Fingerprint:** reconstruction Android job, step `Build, lint and domain regression tests`; Gradle reported `Gradle build daemon disappeared unexpectedly` after `coreRegression` and during lint analysis.

**Affected old merge ref / run:** PR #83 reconstruction [#40243129249](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40243129249), synthetic merge commit `61df370`, based on the older main parent `a6b69c6`. Source guards and the native SoundFont sanitizer passed before the Gradle step failed. The log says a JVM crash file `apps/android/hs_err_pid2233.log` was created, but that file was not included in the uploaded diagnostics artifact. The precise JVM failure signature (including any problematic native frame or memory exhaustion) is therefore unconfirmed; do not assert an application defect or prescribe a heap change without that evidence.

**Context:** the post-merge main Android reconstruction [#40243337754](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40243337754) passed the same build/lint/domain step, real Chrome companion flow, browser interoperability and emulator runtime checks. This makes the old PR failure non-reproduced so far, not conclusively fixed.

**Synchronization and fresh validation:** the PR branch was synchronized with current main by merge commit [`6941aec`](https://github.com/usermalik20-del/Kanta-reborn/commit/6941aec198ad07eb46c5f7d8093b9361d0821750); the compare now reports 3 commits ahead and 0 behind. New exact-head runs are reconstruction [#40244262616](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40244262616), focused iPhone smoke [#40244262594](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40244262594), full iPhone/iPad walkthrough [#40244262620](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40244262620), and unsigned archive [#40244262618](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40244262618). At this checkpoint, the fresh Android run had passed build/lint/domain regression and real-Chrome/browser checks and was running emulator runtime checks; iOS package tests had passed and unsigned simulator compilation was still in progress. Final acceptance remains pending.

**Next action if the JVM crash recurs:** extend the failure artifact path to retain `apps/android/hs_err_pid*.log`, then inspect the JVM fatal-error header, problematic frame, signal and resource state. Keep build/lint assertions enabled; do not mask the daemon exit or infer green from partial tests.



## PR #83 Stage More accessibility repair — exact-head and post-merge closure (2026-10-26)

This entry closes the earlier candidate record for the `stage.more` accessibility identity mismatch. The historical failed run and original diagnosis above remain unchanged.

**Original failure:** focused smoke [#40244262594](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40244262594), job [#121322606681](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40244262594/job/121322606681), on the pre-repair PR head. The test queried `stage.more`, but only the fullscreen More control exposed that identifier; the embedded chevron was the actual menu entry point.

**Fix:** [commit `1c2aeaa`](https://github.com/usermalik20-del/Kanta-reborn/commit/1c2aeaa56d864001e5894e546ba390185e57fedf) attached `stage.more` to the embedded button, assigned `stage.controls.more` to the fullscreen control, and added a source-contract regression check. The complete PR candidate was later tested at [`3aae9e8`](https://github.com/usermalik20-del/Kanta-reborn/commit/3aae9e85abafa28856f6e5752e6ba9dac3506114).

**First green exact-head validation:**
- Focused iPhone smoke [#40254725871](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725871), job #121354013803 — passed.
- Reconstruction [#40254725851](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725851): Android job #121354013913 and iOS job #121354014090 — both passed.
- Full walkthrough [#40254725862](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725862): iPhone job #121354236767 and iPad job #121354237065 — both passed.
- Unsigned archive [#40254725860](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725860), artifact [#12622475729](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725860/artifacts/12622475729), 25,139,930 bytes, SHA-256 `41fc1dc3f8de2e98438b9e19a6a1ca4e31cbe9df570dac1dffca795439701707`.

**Post-merge confirmation:** PR #83 merged as [`abda7c2`](https://github.com/usermalik20-del/Kanta-reborn/commit/abda7c27251af9f1019aa23831c6770032d754c9). Main reconstruction [#40258066829](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066829), full iPhone/iPad walkthrough [#40258066852](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066852), and unsigned archive [#40258066875](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066875) all passed. The main archive is artifact [#12622974482](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066875/artifacts/12622974482), 25,139,693 bytes, SHA-256 `f17c0195c679270916febc2f0612ddb5d90d9f2c235097849615fcf26e7b00fb`.

**Status:** `verified` for the accessibility mismatch repair on the exact PR candidate and post-merge main. This verifies simulator/UI and source-contract behavior only; it does not establish physical-device audio/camera quality or signing.

**Recurrence rule:** preserve the distinction between embedded `stage.more` and fullscreen `stage.controls.more`; keep the source-contract check and real device walkthroughs. Do not loosen the test to fall back to a generic label.

## PR #83 Android Gradle daemon crash — non-reproduction follow-up (2026-10-26)

The original crash remains documented at [run #40243129249](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40243129249). The log reported `Gradle build daemon disappeared unexpectedly` during lint and mentioned `apps/android/hs_err_pid2233.log`, but that crash file was not preserved, so the fatal JVM signature and exact cause remain unknown.

The failure did **not** reproduce on the synchronized PR candidate: Android reconstruction job #121354013913 passed in [run #40254725851](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40254725851). Post-merge Android reconstruction also passed in [run #40258066829](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066829), including build/lint/domain regression, real Chrome, browser interoperability and emulator runtime checks.

**Status:** `non-reproduced; root cause unconfirmed`, not a proven JVM fix. If it recurs, retain `apps/android/hs_err_pid*.log` and inspect the fatal-error header, signal, problematic frame and resource state before changing heap settings or application code.


## PR #90 homepage navigation regression — repair candidate (2026-10-26)

**Regression:** PR [#90 — Premium UI/UX redesign](https://github.com/usermalik20-del/Kanta-reborn/pull/90), merged as [`af2d2f1`](https://github.com/usermalik20-del/Kanta-reborn/commit/af2d2f1a21ecdee7acb9857d013c55f0025a4b27), replaced the homepage's shared six-route primary navigation with a partial set containing in-page anchors, legacy `.html` links, and a duplicated `#downloads` destination. This violated the already-documented public-site contract: every public page must expose the same six canonical primary destinations.

**Failure evidence:**
- Website validation [run #40276300823](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40276300823) failed in `tools/verify-website.py` at `validate_navigation_contract`, with observed links `['#features', 'guide.html', 'features.html', '#downloads', '#downloads']`.
- Vercel deploy workflow [run #40276301038](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40276301038) failed its pre-deployment validation for the same assertion. Its deploy job was skipped; no production deployment was attempted from this invalid candidate.

**Repair candidate:** [commit `69f27e3`](https://github.com/usermalik20-del/Kanta-reborn/commit/69f27e30272b5c5b9ec08f95d5ec7da40e434db9) restores the six unique canonical routes `/ios-download`, `/android`, `/guide`, `/features`, `/songbook`, and `/support` while preserving the redesign and its primary CTA. The existing validator and security/navigation tests remain enabled; no acceptance assertions were relaxed.

**Preflight:** source-level check confirmed exactly six unique primary-navigation hrefs and exact set parity with the shared contract. This is not a substitute for the hosted website/security tests. Exact-head validation and the production deployment remain pending on the repair branch.

**Next action:** require website validation and its security/deployment contract tests to pass on the PR head. Only then allow the existing gated Vercel deployment workflow to deploy, and verify the public homepage and canonical routes after deployment.


## PR #91 follow-up — restore the homepage analytics entrypoint (2026-10-26)

After the six-route navigation repair, website validation [run #40279129599](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40279129599) advanced to the next assertion and failed because `index.html` no longer loaded the same-origin `/analytics.js` entrypoint. Inspection confirmed the last accepted homepage at `abda7c2` included `<script defer src="/analytics.js">`; PR #90 had removed it while replacing the homepage markup. The validator correctly enforces this security/analytics contract, so the test was not weakened.

**Repair:** [commit `4c72824`](https://github.com/usermalik20-del/Kanta-reborn/commit/4c72824c18ae28bfcc7c52a4c5d30c627cb346a7) restores exactly one deferred same-origin `/analytics.js` script immediately before `</body>`. The existing script uses the approved same-origin entrypoint and its CSP allowance for the documented analytics provider remains unchanged.

**Preflight:** source inspection confirms one analytics script tag and the six unique canonical navigation hrefs. Hosted website/security/deployment-contract validation must pass on the new PR head before merge. The earlier PR #91 validation failure is retained as evidence, not reclassified as transient.


## Public download provenance drift after PR #91 deployment — repair candidate (2026-10-26)

**Detection:** the production Vercel deployment [`dpl_3CSNBDcFtyibuMv5Ba5nUqw7R8Hz`](https://vercel.com/gelo-tech/kanta-public/3CSNBDcFtyibuMv5Ba5nUqw7R8Hz) reached `READY` on main commit [`fd799b4`](https://github.com/usermalik20-del/Kanta-reborn/commit/fd799b46fab24425d8a18cfa008d9cd9651db47a). Live fetches of the homepage and all six canonical routes succeeded, but inspecting the published download pages exposed stale artifact metadata.

**Confirmed root causes:**
1. `apps/web/downloads.json` still had `verifiedAt: 2026-10-23`, Android source `e3051583` from PR branch `fix/vercel-deploy-gates-20261024`, and iOS source `7980e552` from PR branch `fix/vercel-root-directory-20261024`. These were not the latest accepted main-native artifacts.
2. The iOS page claimed a later visual walkthrough failed and coverage remained under investigation, even though main walkthrough [run #40258066852](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066852) passed both device jobs on `abda7c2`.
3. The Android page told users to extract `SHA256.txt` and build information from the `android-reconstruction-checks` artifact. The workflow uploads the debug APK, instrumentation-test APK, lint report and runtime diagnostics; it does not upload a standalone `SHA256.txt`. The GitHub artifact digest is the ZIP digest, not the APK checksum.
4. The support page claimed direct Actions artifacts expire after 30 days, while these linked build artifacts are configured for 7-day retention.

**Verified replacement evidence:** Android reconstruction [run #40258066829](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066829), artifact [#12622994480](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066829/artifacts/12622994480), source `abda7c2`, ZIP SHA-256 `792984910d58dbdb350e70b39136477c98de392f96921fe4f2d0cabcdde460a5`; iOS unsigned package [run #40258066875](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066875), artifact [#12622974482](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066875/artifacts/12622974482), source `abda7c2`, ZIP SHA-256 `f17c0195c679270916febc2f0612ddb5d90d9f2c235097849615fcf26e7b00fb`. The matching full iPhone/iPad walkthrough is [run #40258066852](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066852); both device jobs passed.

**Repair candidate:** branch [`fix/verified-download-provenance-20261026`](https://github.com/usermalik20-del/Kanta-reborn/tree/fix/verified-download-provenance-20261026) updates `apps/web/downloads.json`, the iOS/Android download sections and support retention copy. The iOS page links the verified successful walkthrough and distinguishes the ZIP digest from the inner IPA checksum. The Android page describes the actual artifact contents and labels the ZIP digest accurately. `tools/verify-website.py` and `tools/tests/test_website_security_contract.py` now enforce artifact URL/source/digest parity, exact walkthrough linkage and absence of the stale failed-walkthrough claim. `docs/ARCHITECTURE.md` records this public download-provenance contract.

**Validation status:** source changes are prepared on the repair branch; hosted website/security, Vercel contract and reconstruction validation must pass on the exact branch head before merge. The current main deployment is healthy; this is a content/provenance defect in the live download pages, not a deployment failure. Do not claim a signed or published release.


## Website — download manifest lagged accepted native baseline (2026-10-26)

**Fingerprint:** `apps/web/downloads.json` platform source SHA remains `abda7c2` while the latest accepted native-source merge is `0867134`; platform-page artifact links and checksums point to the older build.

**Observed:** merge [`0867134`](https://github.com/usermalik20-del/Kanta-reborn/commit/086713405002f75ab5c7fc49cd815cf2a3469041) removed diagnostic tap/window probes from `RootView.swift` and `KantaMainPlayerView.swift` and replaced the probe-specific source assertion. Exact-source reconstruction [run #40320555135](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320555135), unsigned iOS artifact [run #40320554747](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554747), and iPhone/iPad walkthrough [run #40320554824](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554824) all passed on `086713405002f75ab5c7fc49cd815cf2a3469041`. The current main commit [`865ed5f`](https://github.com/usermalik20-del/Kanta-reborn/commit/865ed5fdfbd3abacbba18b3014d0b1d5fb0cd062) changes only website HTML/CSS.

**Cause:** the website manifest was refreshed after PR #92, but a later native-source merge did not advance the manifest or platform-page links. Existing tests verified that the pages matched the manifest internally, not that the manifest matched the current accepted native baseline.

**Repair candidate:** the current PR updates `downloads.json`, both platform pages, the verified-download ledger and reconstruction checkpoint to Android artifact [#12648928676](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320555135/artifacts/12648928676) (ZIP SHA-256 `928cbc8603b918a81257ef54327bf90238d27cab791ecb02ace3dea9eca4b2dc`) and unsigned iOS artifact [#12649740030](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554747/artifacts/12649740030) (ZIP SHA-256 `6a53aa37227ebd0561d064a18918aeeb6da9ffce8c14818135cb6e21144a734b`). It adds a fail-closed source/run/artifact/digest pin to `test_download_manifest_matches_current_accepted_native_baseline`.

**Regression coverage:** website security-contract tests now fail if the public manifest is left on the prior accepted source or if either platform's exact run, artifact, digest or the iOS walkthrough identity is not advanced with the accepted baseline.

**Validation:** `candidate`; hosted validation on the repair PR is pending. The exact-source native runs above are already green, but they do not validate the new website metadata or tests.

**Recurrence rule:** after any native-source merge, resolve successful reconstruction, unsigned iOS artifact and full iPhone/iPad walkthrough runs for one identical main SHA. Update the manifest, pages and pinned contract together only after all three pass. Do not update the website to a merely compiled or unsigned-only candidate.

**Status:** candidate.



## 2026-10-27 — Main iOS compile failure from unintegrated KantaWorkspace scaffold

**Fingerprint:** `ios-unsigned-simulator-build / undefined workspace presentation types`.

**Failing source SHA:** [`ea24b63`](https://github.com/usermalik20-del/Kanta-reborn/commit/ea24b63a68ded8bbe7a8f6deba9a396d454b4dc6).

**Failure evidence:**
- Main reconstruction [run #40365687028](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40365687028), iOS job [#121722620353](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40365687028/job/121722620353) failed at **Unsigned simulator build** (exit 65).
- Main visual walkthrough [run #40365687013](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40365687013), iPhone job [#121722620227](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40365687013/job/121722620227) failed while compiling the app/UI-test bundle, so the actual iPhone walkthrough was skipped; the iPad job remained queued. Main unsigned archive run [#40365687280](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40365687280) was cancelled.

**Confirmed cause:** commit `ea24b63` added `apps/ios/Sources/DesignSystem/KantaWorkspace.swift` to a directory compiled by the app target, but the file was an unintegrated scaffold. Xcode reported missing `PersistentStageView`, `AccessoryPresentationLayer`, `WorkspaceAccessory`, `NavigationSheet`, `.workspaceToolbar`, and `.workspaceVisualHierarchy`; it also reported that `WorkspacePresentationState` did not satisfy `ObservableObject` for `@ObservedObject` and that its `NSObject.init` override was missing `override`. `HostView` did not instantiate the scaffold. This is a source-completeness/architecture failure, not an SDK transient.

**Repair candidate:** [PR #98](https://github.com/usermalik20-del/Kanta-reborn/pull/98), commit [`3e1eeb7`](https://github.com/usermalik20-del/Kanta-reborn/commit/3e1eeb7f81c36f0be4ed8759c7779c96905d91e6), removes the 54-line unused scaffold rather than adding placeholder types or changing the existing presentation owners.

**Regression/validation evidence on the repair candidate:**
- Reconstruction [run #40366928445](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40366928445): iOS and Android jobs passed.
- Unsigned iOS archive [run #40366928611](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40366928611): passed.
- Full walkthrough [run #40366929004](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40366929004): iPad passed; iPhone job was queued at this ledger update.
- Focused iOS smoke [run #40366928538](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40366928538): still running at this ledger update.

**Status:** candidate; partial validation green. Do not mark verified or merge until the focused iOS smoke and iPhone walkthrough complete successfully on the repair candidate. After the final gate, append the first green evidence and update the reconstruction checkpoint. Preserve the failed main runs as historical evidence.


## Recurrence — compact-workspace PR branch retains the same unbuildable scaffold (October 27, 2026)

**Affected candidate:** [PR #97](https://github.com/usermalik20-del/Kanta-reborn/pull/97), branch `feat/compact-workspace-redesign-20261026`, source [`9b052b8`](https://github.com/usermalik20-del/Kanta-reborn/commit/9b052b8f5ae38229799f521fed69115216958f0d).

**New failure evidence:**
- Full visual walkthrough [run #40367142976](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40367142976) failed during app/UI-test compilation on both iPhone and iPad; neither device walkthrough executed.
- Unsigned archive [run #40367143404](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40367143404) failed during archive compilation.
- Reconstruction [run #40367143008](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40367143008) and focused smoke [run #40367142939](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40367142939) were still running at this update.

**Confirmed recurrence:** the archive compiler reports the same unresolved types/modifiers in `apps/ios/Sources/DesignSystem/KantaWorkspace.swift` as main's `ea24b63` failure. The PR #97 branch is not a clean descendant of current main: it diverged from main's merge base `fa10eed`, is 23 commits ahead and one behind, and carries the scaffold independently. Removing the file only in PR #98 therefore does not repair PR #97.

**Required action:** do not merge PR #97 in this state. Reconcile its branch with the scaffold-removal repair (or remove the file there without disturbing the interactive Host implementation), then rerun reconstruction, focused smoke, unsigned archive, and both device walkthroughs on the reconciled code. Keep the accessibility-identifier repair intact and do not claim the compact workspace accepted until the full device suite passes.

**Status:** recurrence confirmed; PR #97 remains an unaccepted candidate.

## PR #96 compact workspace accessibility regression — 2026-10-27

**Failure evidence:** focused smoke [run #40348703963](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40348703963) and full iPhone/iPad walkthrough [run #40348710266](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40348710266), candidate head `9c2e12a`. Diagnostic artifact [#12665410375](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40348710266/artifacts/12665410375) contains the actual iPhone accessibility hierarchy.

**Confirmed root cause:** the interactive transport `HStack` applied `stage.persistentPlayer` to its descendants. The captured hierarchy shows the Play/Pause, Next and Queue buttons all carrying that parent identifier instead of `stage.playPause`, `stage.next` and `stage.queue`. The toolbar's Host-controls button also used the visible label `Settings`, colliding with the real Settings navigation destination.

**Candidate repair:** remove the identifier from the interactive parent, retain stable identifiers on each control, rename the toolbar label to `More controls`, and enforce these invariants in `tools/check-ios-presentation-contract.py`. The Search → keypad failure in `testDemoKeypadCancelAndRepeatedIntentionalReservations` is a separate observed failure; its exact runtime cause is not yet confirmed and this change must not be reported as fixing it. Reconstruction, focused smoke and both full device walkthroughs must pass on the exact new PR head before acceptance.


## PR #96 branch synchronization and missing-head validation — 2026-10-27

**Observed before synchronization:** candidate [`ee1a5e7`](https://github.com/usermalik20-del/Kanta-reborn/commit/ee1a5e7ccf692256df23eedbcd8162cb61f77bf9) was based on an older main history; current main was [`c23b88d`](https://github.com/usermalik20-del/Kanta-reborn/commit/c23b88d870fdf9c6fa685a840f3e9125bdc7e422). GitHub reported PR #96 as `mergeable_state=dirty`; compare showed 23 candidate commits ahead and 6 main commits behind merge base `fa10eed`. No checks or Actions runs were attached to exact head `ee1a5e7`; latest completed smoke [#40348703963](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40348703963) and device walkthrough [#40348710266](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40348710266) were on older head `9c2e12a`.

**Synchronization repair:** current main's architecture and CI-ledger updates were merged with the candidate's branding/accessibility documentation, preserving the compact workspace source and child-identifier repair. This resolves the known branch-behind condition; GitHub's refreshed mergeability and new-head workflow runs must still be checked before acceptance.

**Runtime gates remain open:** the Search-to-keypad interaction and starter-playback state transition are not declared fixed by this documentation/branch-sync repair. Reconstruction, focused smoke, unsigned archive, and both iPhone/iPad walkthrough jobs must pass on the synchronized code SHA. If no workflow is emitted for that head, treat it as an Actions-trigger blocker rather than success.

## Verified download packaging — rejected manual inputs and recovered artifact listing (October 27, 2026)

**Fingerprint:** `.github/workflows/verified-downloads.yml`, job `package`, step `Package exact-SHA accepted builds`; source is the accepted native SHA `41c8dddeebe7d096322994e8af09c8c48a76308a`. These were manual packaging attempts, not application runtime failures.

**Attempt 1 — abbreviated source SHA rejected:** [run #40432412427](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40432412427), package job `121953078778`. The dispatch input was `SOURCE_SHA=41c8ddde`; the script stopped with `SOURCE_SHA must be a full 40-character Git commit SHA`. This is the intended fail-closed input contract, not a regression. For a manual dispatch, supply the full 40-character SHA or leave the optional input blank to use the dispatch ref's SHA.

**Attempt 2 — ambiguous artifact listing rejected:** [run #40432687096](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40432687096), package job `121953963441`. The contract tests passed, but packaging stopped with `Expected exactly one android-reconstruction-checks artifact from run 40424817074; found 2`. The script correctly refused to choose an ambiguous artifact. A later GitHub artifact query returned one matching artifact, [#12701709708](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40424817074/artifacts/12701709708), for the same source run. The exact reason the earlier API response exposed two matches is **unconfirmed**; treat it as a transient listing inconsistency rather than weakening the uniqueness guard.

**Successful recovery:** [run #40432983408](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40432983408) passed both the contract and packaging jobs for the full SHA `41c8dddeebe7d096322994e8af09c8c48a76308a`. It verified the original Android bytes from reconstruction run #40424817074 and the original iOS bytes from unsigned archive run #40424816823, then produced:
- Android preview download [artifact #12703290582](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40432983408/artifacts/12703290582), 25,572,381 bytes, ZIP SHA-256 `cd94ef8d3f6a20d0b6633a8c86002eb2bf384b7ce704c89a84fe3aac282dc95e`.
- iOS unsigned download [artifact #12703260649](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40432983408/artifacts/12703260649), 25,137,519 bytes, ZIP SHA-256 `b8f2c4fc5a969d631289706f6ee813c96d8305d92d706d0fa8ea83972fd805d1`.
- Verified download manifest [artifact #12702981944](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40432983408/artifacts/12702981944), 544 bytes, ZIP SHA-256 `f56cc1f9a1b06a96b26fd355e5594430ef292aeb945d73783452cdf628e8ffcf`.

These artifacts are retained through November 26, 2026 UTC. They are workflow artifacts, not a new release; the published release still contains the two accepted `41c8ddd` binaries. No source-code repair was required: the short-SHA rejection was correct, and retrying after the artifact listing stabilized passed without relaxing exact-source or unique-artifact checks.

**Recurrence rule:** preserve full-SHA validation and fail-closed artifact uniqueness. If the artifact endpoint reports multiple matches for a completed source run, inspect the exact run/artifact metadata and retry the packaging workflow once; if ambiguity persists, stop and diagnose the API listing instead of selecting an arbitrary artifact.

**Status:** verified operational recovery; no application or release-gate source change.


## Exact-SHA release gate omitted Android-only changes — October 27, 2026

**Fingerprint:** `.github/workflows/ios-visual-walkthrough.yml` and `.github/workflows/ios-runtime-artifacts.yml` path filters; Android-only source changes could leave the latest full iPhone/iPad walkthrough and unsigned iOS archive on an older SHA even when reconstruction passed on the newer SHA.

**Observed state:** code-bearing main merge [`8d742da`](https://github.com/usermalik20-del/Kanta-reborn/commit/8d742da4fbc1dfa984466351e0f8dea68859120a) passed reconstruction [run #40440909666](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40440909666) and unsigned archive [run #40440909660](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40440909660), while the latest full iPhone/iPad walkthrough remained [run #40424816930](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40424816930) on native source `41c8ddd`. Inspection confirmed that the iOS workflow push/PR path filters excluded `apps/android/**`. This conflicts with the exact-SHA acceptance rule in `docs/ARCHITECTURE.md` and `apps/web/downloads.json`.

**Repair candidate:** add `apps/android/**` to push and pull-request path filters for both iOS workflows, add `tools/verify-android-companion-interface.py` to the full walkthrough filter, and add regression tests in `tools/tests/test_verified_downloads.py` to keep those paths present. Update architecture and testing documentation. This changes CI trigger coverage only; it does not change application runtime or advance the public download manifest.

**Acceptance:** on the exact PR head, verify reconstruction, unsigned archive and both iPhone/iPad walkthrough jobs actually start and pass. After merge, verify the same workflows start on the exact main commit. Keep `apps/web/downloads.json` and published release assets pinned to `41c8ddd` until all required gates pass for one SHA.

**Recurrence rule:** whenever an Android-only or release-gate-tool change lands, inspect the head SHA of reconstruction, archive and walkthrough runs before advancing public download provenance. A workflow that did not trigger is not a pass.

**Status:** candidate; hosted validation pending.
## PR #96 and PR #98 resolution — 2026-10-27 (compact workspace accepted and released)

**Resolution:** PR #98 removed the unintegrated `apps/ios/Sources/DesignSystem/KantaWorkspace.swift` scaffold, repairing the source-target completeness failure. PR #96 was synchronized onto current main and merged as [`41c8ddde`](https://github.com/usermalik20-del/Kanta-reborn/commit/41c8dddeebe7d096322994e8af09c8c48a76308a) with the accessibility-identifier repair intact: the interactive transport parent no longer carries `stage.persistentPlayer`, each actionable child keeps its own stable identifier, and the Host-controls toolbar entry is labelled `More controls` rather than `Settings`. The `tools/check-ios-presentation-contract.py` guard enforces all three rules.

**Accepted evidence on the merged source:** reconstruction, focused iOS smoke, unsigned archive and both independent iPhone and iPad walkthrough jobs passed on the validated head. The released artifacts are Android run [#40424817074](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40424817074) artifact [#12701709708](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40424817074/artifacts/12701709708), iOS run [#40424816823](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40424816823) artifact [#12698769982](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40424816823/artifacts/12698769982), and walkthrough run [#40424816930](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40424816930). GitHub Release `v0.1.0-workspace-redesign` carries the APK and the unsigned IPA.

**Resolved state of the earlier gates:** PR #97 was closed rather than merged because it independently carried the rejected scaffold; PR #93 was closed as superseded by the main website fixes. The compact workspace is no longer an unaccepted candidate. The scaffold file does not exist in the shipped source tree and must not be reintroduced: the compact workspace ships at the real composition boundary.

**Still open after this resolution:** the six diagnostic `NSLog` probes previously present in iOS shipping source (`[KANTA_PROBE]` and `[KANTA_TAP]` in `KantaMainPlayerView.swift`, `[KANTA_GATE]` in `RootView.swift`, four occurrences) are confirmed absent from the current source tree; they were removed at the accepted native baseline `0867134` and the released baseline is `41c8ddde`. `InAppControlCenterView` still uses the unrestyled `GeloVisualStyle` (14 references) and remains the last unrestyled surface. Physical-device quality, physical two-device companion networking, voice quality/feedback/latency, scoring calibration, camera/external-display acceptance and production signing remain separate open gates. The Android reconstruction job's private-network assertion is environmental emulator flakiness, confirmed green on re-run.

## PR #103 songbook offset-table regression fixture — 2026-10-28

**Failure:** reconstruction run [#40593831903](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40593831903) failed in `ShubCompatibilityRegressionTests.spanIndexRejectsUnusableOffsetTables` with `Non increasing offset table accepted`.

**Root cause:** the synthetic fixture contained one title record but two trailing offsets. That produced eight trailing bytes for one title, so the span index correctly rejected the malformed stride as `MALFORMED_TITLE_INDEX` before the correlator's non-increasing-offset rule could run. The assertion only accepted `OFFSET_CORRELATION_REJECTED`, so the fixture did not test the behavior it claimed to test.

**Repair:** the fixture now has two title records and two repeated offsets. Cardinality and stride are valid, leaving the non-increasing transition as the isolated reason for rejection. The production parser and its fail-closed behavior are unchanged. Validation on the updated PR head is pending.

## PR #103 predictive-back regression prevented — 2026-10-28

**Review finding:** the feature branch's ancestor [`e57508d`](https://github.com/usermalik20-del/Kanta-reborn/commit/e57508d1869b32ee0c923c0d2b2ad58633b734ee) still used the deprecated `Activity.onBackPressed()` override and did not carry the callback lifecycle wiring already present on main after [PR #101](https://github.com/usermalik20-del/Kanta-reborn/pull/101). Merging that stale implementation would risk reintroducing the previously observed `GestureBackNavigation` lint failure.

**Repair:** the candidate now registers an AndroidX `OnBackPressedCallback` in `onCreate`, enables it only while a workspace accessory is active, disables it on deactivation, and removes the deprecated override. The callback dismisses the accessory while leaving the persistent stage attached. This is a preventive code-review fix; fresh hosted validation is still required.

**Branch status:** PR #103 is 20 commits behind main and GitHub reports it as not mergeable. Reconcile the branch with current main and rerun reconstruction, archive, and iPhone/iPad walkthrough gates before considering merge.

## Website — AdSense loader conflicts with strict CSP and private Songbook boundary (2026-10-28)

**Fingerprint:** merged PR #109, [main commit `56e02e0`](https://github.com/usermalik20-del/Kanta-reborn/commit/56e02e0d50cebf41792f57cdbb280ff4f03130bd), followed by SEO commit `4fd3d24`.

**Cause (confirmed by source contract):** the AdSense loader was added to eight static HTML pages, including Help (`support.html`), open-source legal notices (`licenses.html`) and the client-side private Songbook (`songbook.html`). The route-specific CSP allows external scripts only on the homepage; other routes retain `script-src 'self'`, and `default-src 'none'` blocks unlisted frames. Therefore the ad loader is blocked on most routes and cannot reliably render on the homepage. The private Songbook also must not execute third-party JavaScript while decrypted user data is in the DOM.

Google's [official AdSense CSP guidance](https://support.google.com/adsense/answer/16283098) specifies nonce-based strict CSP and a nonce on every script tag. This static Vercel deployment currently has no per-response nonce mechanism. A fixed nonce or broad allowlist would weaken the security boundary and is not an acceptable workaround.

**Candidate fix:** remove the loader from all public pages and remove the unused AdSense origin from the homepage CSP. Add a regression test that rejects the loader until a nonce-capable response path is implemented. Keep the route-specific CSP unchanged otherwise. Candidate branch: `fix/adsense-strict-csp-privacy-20261028`.

**Validation:** candidate; website validation and Vercel pre-deploy checks must pass on the PR head. This does not claim ads are operational. Re-enable only after a reviewed nonce-capable architecture and privacy-policy update.

**Recurrence rule:** do not load external advertising code on the private Songbook or excluded Help/Legal/Privacy pages. Do not weaken CSP merely to make an unreviewed script run.

**Follow-up validation finding:** first PR #112 website validation run [#40689648844](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40689648844) failed because `tools/verify-website.py` still expected the now-removed AdSense origin in the homepage CSP. The production deployment job was correctly skipped after validation failed. Candidate commit `1f80afe` updates the verifier to match the intended strict policy. Re-run website validation before merging; the initial failure was a stale test expectation, not a runtime CSP regression.

## iOS — Song Library menu tap left the More root visible (2026-10-28)

**Failure:** [main walkthrough #40686820501](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40686820501) passed the iPad job but failed iPhone case `testMenuSongLibrary` at `KantaVisualWalkthroughUITests.swift:378`. The test tapped the visible Song Library row, then waited 25 seconds for the Library navigation bar. The preserved accessibility hierarchy still contained the More navigation bar and Song Library row, while the Library destination was absent. Other menu cases and the starter-playback walkthrough passed in that run, so the failure is intermittent rather than proof that every Library route is broken.

**Root-cause direction:** `RootView` was switching `sheetRoute` to replace the root view inside an already-presented sheet. The failure evidence shows that this root replacement did not take effect on the failing iPhone attempt. The architecture already uses a NavigationStack; menu selection should use its typed path rather than depend on replacing the presented root.

**Repair candidate:** More-menu buttons append typed `Route` values to `navigationPath`; direct tab/screenshot entry continues to select `sheetRoute`. The Song Library XCTest now asserts the actual `library.content` marker in addition to the navigation title, and the source-contract guard requires path-based menu pushes. This keeps the test diagnostic and avoids a retry/sleep that would mask a lifecycle defect.

**Status:** candidate branch `fix/ios-library-menu-navigation-20261028`; hosted exact-head validation pending. Do not mark this failure resolved until focused smoke and both device walkthroughs pass on the candidate SHA.


## iOS — More-menu pushed destinations missing on iPad (2026-10-28)

**Fingerprint:** PR #113 walkthrough [run #40693553013](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40693553013), iPad job; `testMenuCompanionRemote`, `testMenuICloudSave`, `testMenuLegalPrivacy`, `testMenuSettings`, and `testMenuSongbook` reported missing destination titles or missing `navigation.done`. `testMenuSongLibrary` also failed the Done-toolbar assertion.

**Observed cause:** the candidate appended route values to a bound path, but the failing iPad hierarchy did not expose the pushed destination and the Done control was absent. The precise SwiftUI propagation mechanism is not yet proven; the first candidate attached `navigationDestination` and the toolbar to the conditional `destination(sheetRoute)` view.

**Repair candidate:** attach both modifiers to a stable ZStack root inside the NavigationStack, and add a static contract check for that placement. Keep the actual destination-title and Done-button XCTest assertions; no retries, sleeps, or weakened assertions were added.

**Validation:** candidate. Fresh focused smoke and both iPhone/iPad walkthroughs must pass on the updated PR head before this is marked verified. The main walkthrough on `0556fbb` also had its real device-execution steps cancelled, so it does not supply replacement acceptance evidence.

## Resolution log — 2026-10-29 (main `c784fe5`+)

**iOS More-menu / Song Library navigation (PR #113) — MERGED THEN REVERTED.**
Merged as `80ae286`, reverted after the walkthrough proved it a regression. Walkthrough
history: `d3e34f95` success, `24e091b1` success, `37f7ace1` success, `80ae286a` failure,
`2a01654f` failure. The candidate replaced `sheetRoute` assignment with
`navigationPath.append(...)` inside the More `List`, and both devices then failed
deterministically across eight cases with "Menu destination did not present its expected
screen" / "The navigation host must provide Done" / "Failed to tap navigation.done".
`RootView.swift`, the added `library.content` assertion and the path-routing contract guard
were reverted to their `c784fe5` state, which is byte-identical to the green builds.

**Recurrence rule:** an iOS navigation repair is only accepted with focused-smoke plus
both-device walkthrough evidence on one exact SHA. A mergeable branch is not acceptance,
and a repair that turns an intermittent single-case failure into a deterministic
two-device failure must be reverted, not kept.

**Verified after the revert.** iOS visual walkthrough
[run #40787766952](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40787766952)
passed **both** device jobs on `b7d69f2`, and Android validation, iOS validation and the
unsigned iOS archive were green on the same SHA. This closes the loop: the same tree shape
that was green before `80ae286` is green again.

**Website AdSense vs strict CSP (PR #112).** Merged after reconciling this ledger and
`docs/ARCHITECTURE.md` with the dim-3 catalog-ownership section. The AdSense loader is
absent from every public page, the homepage CSP no longer lists the AdSense origin, and
`tools/tests/test_website_security_contract.py` rejects any re-introduction until a
nonce-capable response architecture exists.

**Android build fail-open defect (fixed on main).** `android-validation.yml` piped
Gradle through `tee` without `set -o pipefail`, so a failed build returned `tee`'s exit
status and the job could report success on a broken build. The build step now sets
`pipefail`; `branches: ['*']` was corrected to `['**']` so slash-containing branches are
validated at all; concurrency groups are keyed by `github.run_attempt` so re-running an
older SHA cannot cancel the current acceptance run.

**Android stage composition (PR #114 / PR #115 revert proposals).** Both proposals
correctly diagnosed the compile failures on `5d7d157` and the visibility patch. Every
one of those defects has since been fixed on main and the full Android acceptance suite
(lint, framework-free `coreRegression`, instrumentation runtime checks, camera checks,
workspace contract guard) passes on `1dfecd7`. The reverts would discard validated work,
so they are closed as superseded rather than merged.


## Verified download publisher fixture wording mismatch — 2026-10-30

PR #118's first contract run [#41094983755](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41094983755) failed in `test_rejects_mixed_source_revisions`. The publisher correctly rejected an Android/iOS source-SHA mismatch with the message `Android and iOS artifacts must share one exact source SHA`, while the new test expected the different phrase `same exact source SHA`. This was a stale assertion string, not a release-gate or publisher behavior defect.

**Repair:** commit [f318a13](https://github.com/usermalik20-del/Kanta-reborn/commit/f318a13b0dd97f2efda0c220e8718b113d0fa9ea) aligned the assertion with the explicit rejection message. The subsequent exact-SHA publisher/contract run [#41095375672](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41095375672) passed. The least-privilege permission refinement was included in that successful contract run. Subsequent polling, packaged-artifact checksum, and legacy-to-new website-manifest compatibility refinements are being revalidated on the latest PR head.

## PR #121 — documentation guard and workflow ownership repair (2026-10-31)

**Scope:** website consent/AdSense nonce candidate plus independent platform workflow routing and a fail-closed documentation synchronization gate.

**Failures found and repaired on the PR branch:**
- The first documentation-sync unit run could not import the hyphenated CLI filename as a Python module. The tests now load the CLI through `runpy.run_path`, while production execution remains a direct script invocation.
- The initial documentation matrix omitted `docs/RECONSTRUCTION.md` from the web owner set and one negative test still expected the older, shorter matrix. The matrix and test expectations were aligned; the guard now requires the root README, web README, architecture, testing and reconstruction ledger for web implementation changes.
- The workflow contract test initially searched for a literal backslash-n rather than the actual YAML newline. The assertion was corrected.
- A broad trigger edit accidentally altered the Vercel validation/deploy job conditions and broke the existing manual domain-dispatch contract. The validate job now runs on its declared push/manual events, production deploy remains main-only, and explicit domain configuration remains manual-dispatch-only. The regression test covers these conditions.
- The documentation check was first added as a new workflow file, which cannot provide a PR check until that workflow exists on the base branch. It was consolidated into the already-established `website-validation.yml` workflow: one lightweight PR-wide documentation job, plus a path-classified website job. This avoids an extra workflow run and keeps native builds independent.

**Current verified evidence:** on PR head `c7564c3d73de8c6aaf3425dda6a693154b0a8dc8`, the documentation-sync job and full website validation (site verifier, privacy/legal contracts, Node syntax and nonce handler tests) passed [run #41165515126](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41165515126). Workflow and exact-SHA release contract tests passed [run #41165515053](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41165515053). Android validation remains pending [run #41165515041](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41165515041) because the bundled Android `privacy.txt` asset changed; this is an intentional Android package input, not a web-only false trigger. No iOS archive/walkthrough or Vercel production deployment was started for the PR.

**Recurrence rules:** keep the documentation gate in the established PR workflow until a base-branch workflow can be safely introduced; compare full PR base/head, require substantive diffs in all owner docs, and update the mapping tests with every new source category. Do not broaden Android to `tools/**`, do not run iOS release gates on Android PRs, and do not change Vercel dispatch conditions without preserving the manual-domain and main-production tests. PR #121 was marked ready for review after exact-head validation passed; merge remains pending.


## Website — eager Analytics bypasses consent (2026-10-31)

**Fingerprint:** PR #121 Vercel preview `kanta-public-la107ai6r-gelo-tech.vercel.app`; homepage HTML contained `<script nonce="…" defer src="/analytics.js"></script>` before the consent manager, and the served `analytics.js` immediately initialized `gtag` and appended Google's tag loader.

**Cause (confirmed):** the request-time renderer added a nonce to every existing script but did not remove the legacy static Analytics tag in `index.html`. A CSP nonce authorizes a script; it does not represent visitor consent. This allowed Analytics to start before a consent choice and duplicated the post-acceptance loader path.

**Fix:** `apps/web/api/page.js` removes legacy static `/analytics.js` script elements before nonce decoration. `tools/tests/test_site_page_handler.test.js` asserts that the rendered response contains no eager Analytics tag and retains the consent manager's accept/reject controls.

**Validation:** exact-head website validation #41167460457 passed; preview inspection verified the rendered homepage, nonce/CSP response and consent accept/reject/revocation flow. Packet-level third-party network capture remains unavailable.

**Recurrence rule:** never treat a nonce as consent. Search every page/template and renderer for direct Analytics/AdSense script tags; only the consent manager may create third-party script elements after explicit acceptance. Keep Songbook excluded.


## Android multi-file import cancellation left durable partial state — 2026-10-31

**Finding:** `LibraryRepository.importBatch` previously called `persist()` from each successful song or SoundFont import before the batch completed. If a later item was cancelled or the batch failed, the catch path restored the in-memory catalog and deleted staged files but did not restore the already-written metadata. A fresh repository instance could therefore reload an imported song row whose media file had been deleted, or a SoundFont selection that no longer existed.

**Root cause:** per-item persistence bypassed the batch's existing rollback boundary. The batch's final `persist()` was not the only durable commit.

**Repair:** skip per-item persistence while `batching` is true. The batch performs one atomic `AtomicFile` commit after all items have completed; cancellation or commit failure restores the prior state and deletes staged files without publishing partial metadata.

**Regression protection:** Android runtime instrumentation now reloads the durable catalog after a cancelled batch and requires the original starter-only catalog with every referenced media file present.

**Validation:** exact merged-source Android validation #41168859914 passed, including build, lint, domain regression and API 35 emulator runtime checks.

## Android — malformed validation workflow and unreachable emulator acceptance (2026-11-01)

**Fingerprint:** source SHA `2e0cc5c6d60ab175937c372d9ef9b244f52e8b64`; workflow `.github/workflows/android-validation.yml` contains `required: false  push:` on one line, so the event declaration is malformed. Runner `tools/run-android-runtime.py` places the APK-install and instrumentation sequence inside `if not network_ok`, immediately after an unconditional `raise RuntimeError`; the sequence is therefore unreachable. The camera-capture `with` statement is concatenated to a comment, and the UI screenshot output paths do not match the workflow artifact paths.

**Affected run:** the GitHub connector's commit-run lookup returns only pull-request-triggered runs and returned no entries for the latest main-branch commits; the exact failing run ID and complete job log could not be retrieved through that endpoint. Do not invent a run ID or claim a log-based diagnosis. The source defects above are directly confirmed by the merged commit diff and full file contents.

**Cause:** malformed YAML prevents reliable workflow event registration. Independently, the emulator runner's indentation makes the acceptance sequence unreachable after the private-network check, and the screenshot directory mismatch prevents the expected walkthrough captures from being retained at their configured artifact paths.

**Repair candidate:** branch `fix/android-ci-workflow-runtime-20261101`. Workflow repair separates the manual-dispatch ref input from the push event and checks out the explicit ref or event SHA. Runtime repair restores the missing-network guard, places both APK installations and instrumentation after that guard, writes walkthrough captures under `files/`, and separates camera capture into a valid statement. Added `tools/tests/test_android_runtime_runner.py` and wired it into Android validation. Exact repair commit IDs are recorded in the branch history; the candidate must not be marked verified before the full hosted Android workflow passes on the PR head.

**Regression coverage:** Python AST test requires the network guard to raise on missing private IPv4 while keeping both APK installation calls outside the guard; it also requires the screenshot directory and instrumentation call. Workflow YAML must be parsed/loaded by GitHub Actions on the PR head, then Android build, lint, domain/native checks, browser checks and emulator instrumentation must all complete.

**Validation:** candidate; no fixed-head hosted run is yet available. Static source correction is not compilation or emulator acceptance.

**Recurrence rule:** do not put required steps after a raise in the same conditional branch. Review indentation in the full runner, not only changed lines. Keep workflow event declarations at the YAML root and keep artifact paths identical to the paths written by the runner.

## Android — stage presenter type mismatch and camera target gap (2026-11-01)

**Affected run:** Android validation run [#41273249978](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/41273249978), run number 400, job `android`, step `Android emulator runtime checks`. All preceding steps passed: branding, prompt bank, runtime-runner regression, SDK install, native SoundFont sanitizer, Gradle build/lint/domain regressions, camera permission/offline notices, real Chrome companion, captured browser interoperability and private IPv4 interface selection. Emulator instrumentation then returned `INSTRUMENTATION_RESULT: shortMsg=Process crashed`.

**Root cause:** captured `android-logcat.log` records `FATAL EXCEPTION: main` and `ClassCastException: ph.kanta.reborn.stage.StagePresenterImpl cannot be cast to ph.kanta.reborn.StageSurface` at `RuntimeChecks.java:255`. `MainActivity` uses `StagePresenterImpl` as its primary stage and creates `StageSurface` only for fullscreen/external output. The runtime test treated them as the same concrete type. Review also found `CameraStage.start()` accepted only `PreviewView` or `StageSurface`; its primary-stage call with `StagePresenterImpl` silently returned, leaving camera acceptance incomplete even after the cast was fixed.

**Repair candidate:** `RuntimeChecks` now uses fullscreen `StageSurface` for video lifecycle checks and retains the primary presenter for primary-stage checks. `StagePresenterImpl.getCameraPreview()` ensures the presenter's `BackgroundStage` has a real `PreviewView` and exposes its streaming state. `CameraStage` accepts the primary presenter as a target and forwards stream state to fullscreen `StageSurface`. The test checks primary camera startup, transfer to fullscreen and return to primary. No application test was removed or skipped.

**Regression coverage:** exact-head Android validation must run the complete emulator suite. Required assertions: primary-stage camera reaches `PreviewView.StreamState.STREAMING`; fullscreen video becomes visible, deactivates and resumes; camera moves to fullscreen and returns to primary; camera disable releases preview. Static source checks and successful compilation are not emulator acceptance.

**Validation:** candidate source repair committed to the existing PR branch; awaiting the next hosted Android run. The failed run remains a real failure and must not be overwritten in the ledger.
