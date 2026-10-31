# Reconstruction validation

Tests and production source must describe the same behavior. Keep recovered tests that enforce product invariants; reconcile missing APIs from preserved source or implement the documented contract. Never remove assertions, disable lint or accept a baseline merely to make validation green.

## Suite boundaries

| Suite | Contract | Execution |
| --- | --- | --- |
| GeloKaraokeCoreTests | MIDI/lyrics, one FIFO owner, playback identity, failure-safe handoff, scoring and remote serialization | SwiftPM on macOS |
| LegacyCompatibilityTests | Bounded binary reads, validated spans and malformed inputs | SwiftPM on macOS |
| LibraryStorageTests | Safe import roots, starter removal/restore, backup integrity, persistence, search and metadata exports | SwiftPM on macOS |
| LocalNetworkControlTests | Framing, pairing, bounded search and connection-scoped replay suppression | SwiftPM on macOS |
| AVFoundationPlaybackTests / AVFoundationMIDIPlaybackTests | Format rejection, controls and original starter synthesis | SwiftPM on macOS; physical audio remains separate |
| MicrophoneScoringTests | Pitch detection and room gate | SwiftPM on macOS; microphone/device acceptance remains separate |
| KantaUITests | Actual iOS stage/control walkthrough | Separate simulator UI run; not covered by an unsigned build |
| KantaNativeTests | Real WKWebView lifecycle plus app-hosted session certificate/private-key Keychain installation and removal | Application-hosted simulator XCTest in focused smoke; no remote media or device-audio acceptance |
| ReleaseToolingTests / starter generator | Release contracts, privacy/source guards and deterministic first-party resources | Python locally and in CI |
| Android core regression | FIFO retry identity, Unicode search, bounded MIDI timing and encrypted exports | Gradle JavaExec |
| Android native SoundFont | Validated SF2 references, bounded allocations, audible pitch, truncation and mutation handling | C11 ASan/UBSan host harness |
| Android runtime instrumentation | Real JNI/AudioTrack, FIFO commit, cancellation, controls, pause/resume, failed prepare, persistent-stage rendering, dispatcher-based workspace back dismissal, and durable catalog/media rollback after cancelled multi-file imports | Clean API 35 emulator install; custom Instrumentation, no external test library |
| Browser interoperability | Fresh Android export through the captured browser decrypt function | Node after Android regression fixture generation |
| Prompt bank contract | Pinned upstream metadata, cache boundary outside the repository, no vendored upstream text | Python unittest in the Android validation job |

## Website route and responsive navigation contract

The website validation job runs `tools/verify-website.py` and `tools/tests/test_website_security_contract.py`. These checks cover all ten public routes, local links and anchors, route-specific CSP, verified download provenance, the shared six-link primary navigation, visible wrapping behavior at narrow viewports, adjacent duplicate feature headings, and stylesheet source hygiene. After a production deployment, inspect the canonical Vercel alias and affected routes; a successful build/deploy alone does not prove mobile usability.

## Documentation synchronization gate

Every pull request runs the lightweight the `documentation-sync` job in `website-validation.yml` workflow. It compares the full PR diff against `tools/check-documentation-sync.py` and fails when required documentation was not changed in the same PR. Web implementation requires `README.md`, `apps/web/README.md`, `docs/ARCHITECTURE.md` and `docs/TESTING.md`; Android requires the root and Android README plus architecture, testing and reconstruction; iOS requires the root README, `apps/ios/AGENTS.md` plus architecture, testing and reconstruction; workflow edits require the root README, `AGENTS.md`, architecture and testing; tooling changes require testing documentation. Privacy-related changes also require the platform privacy disclosure. The check verifies a substantive diff in each required file; whitespace-only edits do not satisfy the requirement. It also runs for fork pull requests with read-only permissions and no persisted checkout credentials.

The same guard runs as a step inside existing main-branch platform and deployment jobs. It adds no extra Android, Xcode or simulator jobs. Pull requests get one small Ubuntu documentation check regardless of platform; platform build workflows retain their independent path filters. A documentation-only PR runs the documentation check without allocating native runners.

## Validation order

1. Run `python3 tools/verify-test-layout.py` to reject orphaned Swift test files, absent target directories and unknown local `@testable` imports. This checks wiring, not Swift types or behavior.
2. Run Python release/starter guards. Preserve synthetic fixtures and isolate temporary directories.
3. On Xcode 26.2 / Swift 6.2, run `swift build --build-tests` in `apps/ios` to compile production modules and every package test target before execution.
4. Run `swift test --skip-build --no-parallel`. A successful compilation alone does not establish passing behavior.
5. Attempt the unsigned simulator app build even when package validation fails. This reports app integration errors in the same run rather than hiding them behind earlier failures. Retain the three diagnostic logs as seven-day artifacts.
6. Android runs APK assembly, lint, domain regression and browser interoperability. Do not treat an APK build as device playback proof.

All package suites remain enabled. Build failures stay failures, including when later diagnostic steps succeed. UI automation, signing, physical audio/camera, LAN confidentiality and device performance require separate acceptance; no static audit can guarantee that future mismatches will never occur.

## Reconciled contracts

- Completed room scores include the entire target timeline; live scores include elapsed targets. Changing latency clears prior observations.
- Duplicate envelope IDs execute once within the bounded connection cache; active requests cannot be evicted.
- Unsupported indexed media remains searchable with stable IDs and `isPlayable == false`; title discovery never invents playback offsets.

The immutable recovery snapshots retain provenance. Active source and tests are reconciled together on the current PR.

Native and domain cases run serially for clear failure attribution. All tests remain enabled. Early package-log upload and recent package-process IPS reports support diagnosis. The signal-5 crash was isolated to scoring latency overflow and repaired; native audio cases also pass.

The serial run isolated the signal-5 stop to extreme-latency arithmetic in the scorer and exposed duplicate-slot and adjacent-boundary failures. Restored finite-input filtering, overflow-reporting latency subtraction, half-open windows and one observation per slot. Added extreme-duration/invalid-window coverage; retained the newer finite MIDI-note conversion. Swift backtrace environment injection was removed because this runner rejects it for privileged toolchain executables.

Live scoring includes the most recently received sample while source note ends remain exclusive; completed scores always use original target windows. The explicit source-boundary regression prevents a future fix from extending notes to recover the latest sample.

## Verified checkpoint

Cloud run [38748040876](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38748040876), source commit `56c9fedbb70b79ebc350e4678d5620554d53c983`: all 158 Swift package tests, 50 release-tooling tests, four starter-generator tests, starter/layout guards, unsigned Release simulator app build, Android APK/lint/46 domain checks and browser interoperability passed. UI automation and physical-device acceptance remain separate.


## iOS online runtime workflow

The `ios-runtime-artifacts.yml` workflow builds an unsigned iPhoneOS personal-signing input for iOS build inputs on pull requests; main pushes also include Android source and exact-SHA release-packaging inputs. Manual dispatch remains available. Website-only tooling (`tools/verify-website.py` and `tools/tests/test_website_security_contract.py`) is excluded. It does not sign or publish the application. The `ios-visual-walkthrough.yml` workflow builds all Xcode UI tests and executes the complete walkthrough suite on iPhone and iPad in separate jobs. It runs for iOS source changes on pull requests, the capture tool and Android companion-interface guard; Android source and release-packaging tools trigger it on main after merge. Manual dispatch, the weekly schedule and `ios-rc-*` tags remain available; website-only and workflow-only edits do not start simulator jobs. Android-source triggers remain intentional for exact-SHA release provenance on main after Android-only changes; Android pull requests do not start iOS archive or full walkthrough jobs. The iOS package test still runs for the single shared Android companion-protocol source file. Logs, results and video remain available for seven days. Signing and hardware acceptance remain separate.

Workflow ownership is enforced by `tools/tests/test_workflow_path_ownership.py`, run by `verified-downloads.yml` whenever workflow definitions change. `android-validation.yml` uses explicit Android-owned tool/test paths rather than `tools/**`. `ios-validation.yml` uses pull-request events for feature branches and main-only push validation, preventing duplicate feature-branch runs. `website-validation.yml` owns web and public legal/privacy-copy checks; `verified-downloads.yml` owns workflow-trigger and release-contract tests; `vercel-deploy.yml` has no pull-request trigger and deploys only from main or explicit dispatch. A website-only or iOS-documentation-only pull request must not start Android builds, unsigned iOS archives or iPhone/iPad simulator walkthroughs. Android's bundled `app/src/main/assets/privacy.txt` is an actual APK input, so a change to that file correctly starts Android validation; the iOS privacy Markdown and `PublicSite` copy are excluded from Xcode builds.

Android runtime fixtures live in the test APK behind a signature permission declared only by debug builds. The read-only provider exposes synthetic/original starter bytes and a bounded cyclic SAF tree; no user storage is exposed. Instrumentation checks nested traversal, partial failure, cancellation rollback/reopen, byte integrity, real controller import, custom pad decode/reset and idle intermission gating. Fixture code is excluded from release builds.

Pitch Settings UI test coverage added to `RuntimeChecks`: workspace accessory activation for `ACCESSORY_PITCH_GAME` and `ACCESSORY_LEARNING_ASSISTANT` from Controls; `showPitchSettings()` dialog renders sensitivity slider, note spinner, custom Hz input with validation; `SettingsStore` persistence verified for `pitch.assistantSensitivity`, `pitch.targetHz`, `pitch.targetName`; `LearningAssistant.setSensitivity()` threshold scaling verified via synthetic pitch vectors.



Android emulator startup uses an explicit shared AVD directory for avdmanager and emulator. The helper detects early process exits, bounds boot/ADB/instrumentation, always collects diagnostics and retires the emulator. Use `owner-approved-android-validation` for Android-only repairs; `owner-approved-validation` still runs both native platforms. No source tests are skipped by the focused label.

## iOS KCP1 controller candidate

`owner-approved-ios-validation` selects the full iOS package/app gate without rerunning unchanged Android native tests. The job pins Java 17 and compiles the unmodified Android CompanionProtocol with a synthetic Java TCP peer. LocalNetworkControlTests requires that peer via KANTA_KCP_TEST_CLASSES; absence is a failure, never a silent skip on macOS. Fixed independent Python crypto vectors check exact handshake, request and bidirectional record bytes. Live Java/Swift socket checks exercise the actual iOS client, bounded busy behavior, Unicode, distinct repeated reservations, cancellation and revocation. This is automated interoperability evidence, not a physical Android/iOS pair or iOS-host acceptance.

## iOS walkthrough runner diagnostics and suite timeout

`tools/capture-ios-runtime.py` executes the complete `KantaVisualWalkthroughUITests` suite for the requested simulator and requires every name in its `EXPECTED_TESTS` manifest to have a clean passing result. The current manifest contains 13 cases: the prior eight cases, with the six-destination menu coverage split into six independently budgeted tests instead of one aggregate case.

The aggregate `testMainMenuButtonsAndScreens` exceeded XCTest's per-test allowance on iPad even after reducing cold launches: its six screenshots were exported, but the test did not finish as a passing case. The repair preserves each destination, expected navigation-title assertion, screenshot, `navigation.done` dismissal, and Stage More return assertion, while giving every destination its own XCTest execution allowance. Do not raise the global per-test allowance or remove a destination to hide the timeout.

Per-test limits remain 120 seconds by default and 180 seconds maximum. The outer process deadline remains 1,080 seconds, and each workflow job is bounded to 45 minutes. A timeout, runner restart, non-zero `xcodebuild` exit, or missing required test remains a failure even if a later runner attempt prints passing cases.

The visual workflow now has separate `iphone-walkthrough` and `ipad-walkthrough` jobs. Each device builds and runs independently, uploads its own compact presentation diagnostics and full runtime evidence, and reports the failing device directly. Branch-level concurrency cancels stale runs on the same branch without cancelling the two device jobs within one run.

## Embedded Stage presentation diagnostics

The embedded Stage Search/Queue presenter logs request, gate activation, window attachment, presentation start/completion, dismissal, stale ownership, and blocked-state reasons under `KANTA_STAGE_PRESENTATION`. `tools/capture-ios-runtime.py` captures filtered simulator unified logs before shutdown, and the walkthrough uploads `ios-ipad-presentation-diagnostics` separately from the large video/result-bundle artifact. For triage: a request without activation points to gate ownership; activation without presenter configuration points to SwiftUI binding/representable propagation; configuration blocked by window/ownership identifies the presenter precondition; a completed presentation with no keypad points to sheet content/accessibility. Keep the full XCTest and result evidence; never add retries or weaken assertions to conceal a dropped presentation.

## PR #31 iPhone/iPad runtime regression checkpoint

Run [38923007543](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38923007543) built app/UI tests but both platform walkthrough jobs failed. The full preserved **iPhone** test log reports two passes (`testDemoKeypadCancelAndRepeatedIntentionalReservations`, `testMainMenuButtonsAndScreens`) and two failures: the encrypted companion screen did not become visible after pressing `companion.open` (line 108), and More was dismissed during actual starter playback (line 53). The restarted XCTest runner printed a subsequent `Executed 0 tests` success line which does not supersede the failed test cases. No unsigned IPA was produced.

The new candidate replaces route-derived sheet lifetime with an independent presentation flag and removes the nested companion link's simultaneous gesture. The capture runner scans the entire XCTest log for failed source assertions and requires the four explicit UI test cases to pass before declaring a device accepted. Verify both an iPhone and an iPad on the same final PR commit; `xcodebuild` exit zero, a nonempty recording, and a complete named test pass set are all mandatory. Failing tests must not be skipped or converted to nonblocking. The iPad's exact test assertion remains to be confirmed with the next actionable log or its separate result bundle.



## Playback completion and navigation presentation

RootView passes navigation-sheet visibility to HostView. The automatic completed-session Favorites sheet is deferred while More navigation is presented, without clearing the pending session prompt. Its binding setter must not discard pending session state while navigation owns presentation. The existing real-playback XCTest keeps its More persistence assertion and additionally verifies the navigation Done control; run all four named walkthroughs independently on both iPhone and iPad before accepting a release.


## Post-navigation keypad transition regression

The real playback walkthrough waits for a rendered stage Search button after closing the Song Library sheet. UIKit can consume a touch during sheet dismissal, so the harness retries only the idempotent action to open Search (maximum three attempts). It still fails if the stage is blocked or the actual numbered keypad never appears; no assertions are skipped. Both iPhone and iPad must pass on the final commit.


## iOS modal request lifecycle

RootView's navigation sheet and HostView's CONTROL/session-save/Favorites sheet remain SwiftUI-owned. Embedded Stage Search/Queue use `KantaEmbeddedStageSheetPresentation`, a stable UIKit presenter attached to the Host background; navigation, Host and Stage requests are serialized by `KantaPresentationGate`. Embedded Stage dismissal completes `.stage` from UIKit's presentation/dismissal callback, while the other owners complete from their own `onDismiss`. The fullscreen projection retains its separate child Search/Queue sheet. Keep embedded Search/Queue out of the Host SwiftUI sheet and never add sleeps/retries to conceal a dropped presentation request.


### iOS fullscreen presentation ownership candidate

The presentation contract guard checks that the UIKit fullscreen presenter acquires and releases `KantaPresentationGate` ownership through its dismissal callback, and that the gate restores fullscreen after a nested Search/Queue sheet. The UI walkthrough opens fullscreen from CONTROL, uses Search inside fullscreen, exits, and verifies normal-stage Search can present afterward. Embedded Search/Queue are independently presented by `KantaEmbeddedStageSheetPresentation`; the embedded control uses `stage.search` and the fullscreen control uses `stage.search.fullscreen`. Run `tools/check-ios-presentation-contract.py` and the full reconstruction validation on the candidate SHA. A green run on a different SHA does not validate it.


## Automatic workflow triggers and release boundary

Reconstruction validation runs for relevant app, tool and workflow changes on feature/main pushes and pull requests. The unsigned iOS archive and full iPhone/iPad walkthrough also trigger on `apps/android/**` changes, so Android-only commits receive same-SHA iOS release-gate evidence. The walkthrough additionally triggers on `tools/verify-android-companion-interface.py`; both iOS workflows retain manual dispatch. Website validation runs for relevant website changes. Verified-download contract tests run automatically; packaging fixed, previously accepted IPA/APK artifacts still requires owner dispatch. No workflow signs, merges, deploys or publishes production downloads automatically.

## CI separation: fast validation, unsigned artifacts and visual acceptance

- `reconstruction-validation.yml` remains the PR source, Swift package, simulator-build, and Android validation gate. It does not replace simulator UI acceptance.
- `ios-visual-walkthrough.yml` runs on iOS-source pull requests and iOS/Android source plus release-tool changes on main; the Android companion-interface guard can also trigger PR walkthroughs. Workflow-only edits use the lightweight release-contract check instead. Manual dispatch, the weekly schedule and `ios-rc-*` tags remain available. Its independent iPhone and iPad jobs each build and execute the complete named UI suite, retain screenshots/recordings/logs, and publish compact per-device diagnostics. A pass on one device does not mask a failure on the other.
- The unsigned iOS archive workflow proves packaging only. It does not prove interactive behavior, signing, or installability.
- `ios-smoke.yml` remains a focused iPhone test for navigation/Search recovery and native lifecycle behavior; it is not the full release walkthrough.
- A release candidate requires the reconstruction gate and a successful full iPhone **and** iPad walkthrough on the exact candidate SHA. Weekly, unrelated-commit, candidate-only, build-only, or one-device evidence cannot substitute.
- CI does not sign, publish, or deploy the app. The current archive remains an unsigned personal-signing input until the authorized signing and release gates are completed.

## GitHub Actions cost controls (October 2026)

- Feature branches are validated by the pull-request event only; ordinary feature-branch pushes do not duplicate the reconstruction, website or download-contract jobs. Main pushes retain post-merge verification.
- The unsigned personal-signing IPA archive runs automatically on relevant iOS/Android source, tools and workflow changes, with manual dispatch available. Compilation, Swift package tests and the unsigned simulator build remain in normal reconstruction CI. Do not interpret an unbuilt IPA as a release artifact.
- Full iPhone/iPad visual walkthrough runs on relevant iOS/Android source changes, release-gate tools, workflow edits, manual dispatch, the weekly schedule and release-candidate tags. A release candidate must pass both devices on its exact SHA.
- Failure triage is manually dispatchable with a failed run ID; it no longer starts a skipped workflow for every successful validation. Investigators can still inspect exact job logs and report failures.
- The separate focused iOS simulator smoke PR must be validated before merge. Workflow YAML changes themselves require review of triggers and required status checks; do not remove required branch protection checks without an equivalent passing gate.

## PR #43 post-merge evidence

Commit `ad95e8b04eb23311127090883f309f0ec4205724` passed reconstruction run [39051661330](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39051661330). Website validation and verified-download contracts also passed on that main-branch commit. Unsigned IPA packaging is not automatic; an absent artifact from this merge is expected. The next release candidate still needs a candidate-SHA-matched full iPhone/iPad walkthrough and separately approved artifact packaging.


## Focused smoke native execution proof

The focused iPhone smoke selects all three KantaNativeTests.YouTubeEmbeddedPlayerLifecycleTests cases and the existing testFocusedNavigationSmoke. The native bundle compiles the unchanged production preview component into its test module; release-app testability is unchanged. `tools/verify-ios-smoke-results.py` requires explicit passed lines for all four names and rejects failed assertions or runner restarts anywhere in the log. Navigation-only or zero-test output is insufficient. Existing logs and xcresult retention remain unchanged. The full release walkthrough still covers all four UI cases on both iPhone and iPad separately; native component checks do not verify SwiftUI presentation, real videos/ads or hardware audio interruptions.


## iOS audio arbitration regression candidate

The expanded native smoke requires all five YouTubeEmbeddedPlayerLifecycleTests passes and focused navigation. The added cases exercise real WebKit retirement before session configuration and isolated synthetic AVAudioPlayer intermission stop/explicit restart. Controller/domain cases cover native preparation versus online claim, cancellation/microphone changes across awaits, paused position/playback/FIFO identities, SFX/Next, stale release, foreground, failed session setup and suspension of both rendering backends. Forty Swift files remain wired to seven package suites plus two Xcode bundles. Compilation, execution and application build are separately required on the changed source; no physical media/latency result follows from these fixtures.


## Online-screen UI coverage candidate — October 18, 2026

PR #51 baseline 24db7a1 now adds two real XCUIApplication flows through Stage CONTROL's actual online sheet. They cover invalid-link rejection before WebView construction, explicit valid entry, stop/reopen, background/return without resurrection, Done/reentry and navigation recovery, plus preserving a paused original starter and explicitly resuming it afterward. Stable accessibility identifiers and link-keyboard Done dismissal support the real UI; no fake player or launch-environment bypass is introduced.

The synthetic video ID never autoplays. External YouTube availability is not a fixture: valid entry must reach either actual native player controls with a WebView or the production recoverable load-error state, with retained outcome screenshots. A local audio-claim failure is not accepted as an external-load error. This verifies UI lifecycle/recovery, not successful remote media or deterministic external-error delivery. Existing native process-failure, audio-handoff and controller/FIFO regressions remain required.

Focused smoke retains all five native cases and navigation and now requires both named online-screen cases (eight passes total). The full release walkthrough gate also requires all seven methods in the UI suite; timeouts, serial execution, failure detection, evidence retention, signing and deployment boundaries are unchanged. Local layout/presentation/YAML/whitespace and 55 tooling tests passed. Hosted app/test compilation and actual simulator execution are pending on the resulting source. Physical embed/ads/interruption/privacy and exact-candidate full iPhone/iPad release gates remain open.


## PR #51 HTTPS companion validation

Android core regression now includes BrowserRemoteRegressionTests over real JSSE TLS sockets and generated identities. BrowserRemoteBrowserHost is a synthetic test-only owner compiled by coreRegression; `node tools/verify-companion-browser.mjs` drives the shipped page against that TLS server in actual Chrome through CDP, with no npm dependency. The disposable browser trusts only synthetic test traffic through its isolated harness configuration; this does not validate mobile trust setup. Platform asset equality and bounded request contracts are checked before browser startup. GitHub's Chrome and pinned Node 24 are required; failure to launch/run remains red.

Android instrumentation additionally exercises the production HTTPS adapter against SessionController and native audio/queue state. iOS adds bounded command/HTTP framing and actual certificate/key/SAN validation plus a real role/input screen case. Focused smoke requires nine named passes; full release walkthrough requires eight UI cases, preserving all earlier tests and failure/timeout gates. Native app/browser host-client interoperability, certificate trust UX, hardware audio and independent security acceptance remain separate. See COMPANION_BROWSER_CHECKPOINT.md.


### Browser-host identity entitlement gate (October 18, 2026)

PR #51 native XCTest now compiles in Release through a test-only Swift SPI. Reconstruction passed at `215e02a` in run 39136036615. Focused iPhone run 39136036597 executed the native Keychain round trip and failed `SecItemAdd` with `errSecMissingEntitlement` (`-34018`), while five WebKit lifecycle and four focused UI tests passed. The existing workflow uses `CODE_SIGNING_ALLOWED=NO`. Keychain acceptance requires a separately authorized entitled simulator test host with inspected signature entitlements, then the **same** certificate/private-key/removal assertions executed and passing. This is not a reason to delete or skip the test, weaken production Keychain checks or claim signed/physical-device acceptance. See `docs/CI_FAILURE_REPAIR_LEDGER.md`.

## Focused iOS smoke workflow integrity — October 24, 2026

Focused simulator smoke must gate on KANTA-owned source checks, native lifecycle tests, named XCTest cases, and retained test evidence. Do not install unrelated external CLIs or label them audio analysis. On PR #67 head `61057d0`, the app build and focused XCTest step passed, including the four selected UI cases, but the overall job failed afterward at `rtk latency` with `must be run as root`. Inspection of the upstream [rtk-ai/rtk project](https://github.com/rtk-ai/rtk) confirms it is an LLM token-optimization CLI, not an audio measurement tool. The step was introduced by PR #65 and also piped a mutable remote install script directly into a shell. The repair removes this unrelated dependency rather than escalating it with sudo. Any future audio-quality/latency gate must use a KANTA-specific fixture or measurement tool with documented metrics, bounded permissions, and independently verified results; this smoke suite makes no audio-quality claim.



## iPad embedded Stage presenter diagnostics (2026-10-24)

When Stage Search does not expose `stage.keypad.0`, inspect the `KANTA_STAGE_PRESENTATION` diagnostics in the matching runtime logs and correlate the requested sheet, window attachment, existing presentation, and gate owner. The presenter now retries on layout and detects an orphaned owned controller; this is a candidate lifecycle repair until the full iPhone/iPad walkthrough passes on the same SHA. Keypad assertions must wait for each digit to exist rather than sampling `exists` immediately after a presentation animation.

### Keypad assertion regression after main synchronization — October 24, 2026

The source guard in [focused smoke run #40100439415](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40100439415) caught a merge-resolution regression before XCTest: the demo keypad test used immediate `.exists` checks for digits 0–9 instead of waiting for asynchronous presentation. Commit [b36108e](https://github.com/usermalik20-del/Kanta-reborn/commit/b36108e2dffc30957161b2ce0e9f24eb3c134021) restores `waitForExistence(timeout: 10)` for each digit without removing any assertion. This repairs the test contract; it does not establish that the iPad runtime presentation issue is resolved. Re-run the focused smoke and full exact-SHA iPhone/iPad walkthrough.


## Window-tap probe compiler and evidence contract — October 25, 2026

Main commit d56a4ba introduced a window-level tap probe to distinguish a delivered touch from a SwiftUI button action that never fires. Reconstruction run [#40119874396](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40119874396), unsigned archive run [#40119874432](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40119874432), and walkthrough run [#40119876903](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40119876903) all exposed a compile error: the current Swift SDK replaces NSStringFromCGPoint and NSStringFromCGRect with NSCoder.string(for:). The walkthrough's subsequent missing-bundle-ID error is downstream of the failed UI-test build; it is not a new iPad runtime result.

The repair uses NSCoder.string(for:), converts the hit view's bounds into window coordinates before logging them, and removes the non-cancelling tap recognizer when its representable is dismantled. This keeps the probe's point and hit rectangle comparable and prevents repeated view lifecycles from accumulating window-level recognizers.

Acceptance requires the source-contract guard, Swift/package and app builds, unsigned archive, focused smoke, and a complete iPhone plus iPad walkthrough on the same exact candidate SHA. A successful diagnostic build does not establish that the original iPad Search tap is fixed.

## iPad Search intercepted by the decorative stage canvas — October 25, 2026

**Runtime evidence:** PR candidate walkthrough [#40122872536](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40122872536) failed the four iPad keypad cases. Its compact diagnostic artifact [#12572620221](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40122872536/artifacts/12572620221) records a tap at `window-point={668, 148.5}`, the center of the accessibility-reported `stage.search` frame, resolving to `_UIGraphicsView {{32, 117}, {770, 433}}`. That drawing surface overlaps the Search action row and wins UIKit hit testing, so the button action never reaches the presentation gate. This is more specific than the earlier hypothesis about modal ownership.

Main's current reconstruction [#40126620995](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126620995) and unsigned archive [#40126621029](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621029) passed, but full walkthrough [#40126621040](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621040) failed: the iPad repeated the four keypad failures, and the iPhone stage did not report `testMainMenuButtonsAndScreens` as passing. The main-run compact diagnostic artifact [#12574327088](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40126621040/artifacts/12574327088) did not preserve `[KANTA_TAP]` events, so use the earlier candidate's tap trace as the direct hit-test evidence and retain the probe until exact-head acceptance.

**Repair:** mark both `KantaCameraPreview` and `KaraokeStageView` branches in `stageBackground` as non-hit-testable. These are display-only layers; user interaction must be handled by the explicit Search/Camera/Expand controls. The source guard checks that both branches carry `.allowsHitTesting(false)`.

**Acceptance:** source-contract guard, Swift package tests, simulator build, focused smoke, reconstruction, unsigned archive, and the full iPhone/iPad walkthrough must pass on one exact candidate SHA. In particular, confirm that a Search tap no longer resolves to the oversized drawing surface and that `stage.keypad.0` appears on iPad. Build success or an iPhone-only pass is not device acceptance.


## Post-merge iPad menu walkthrough timeout — October 25, 2026

Main commit [`d01504e`](https://github.com/usermalik20-del/Kanta-reborn/commit/d01504e42b3e00735bb429b05e9ec886149911fe) passed reconstruction [run #40136025964](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025964) and unsigned archive [run #40136025858](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025858), but full walkthrough [run #40136025937](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025937) failed on iPad.

The compact diagnostic artifact [#12577317727](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025937/artifacts/12577317727) confirms that Search now reaches `_UIShapeHitTestingView`, requests and activates the Search sheet, and begins presentation. The keypad-focused tests, navigation smoke, online-preview lifecycle tests, and starter playback test all passed in this run. The remaining failure is separate: `testMainMenuButtonsAndScreens` exceeded XCTest's 120-second default execution allowance while cold-launching the app separately for six destinations. The runner then restarted and the required-test manifest correctly marked that test missing.

The candidate runner repair keeps one app process alive, captures each of the same six destinations, dismisses each route through the stable `navigation.done` accessibility identifier, and waits for the Stage's More control to return before continuing. This reduces cold-start overhead without removing destinations, skipping assertions, adding blind sleeps, or weakening the required-test set.

**Status:** candidate; full iPhone/iPad walkthrough and exact-SHA acceptance remain pending on the repair branch. Do not call main's full walkthrough green until the menu case passes on both devices.


## iOS encrypted-companion input validation and keyboard-idle timeout

Walkthrough run [#40140289375](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40140289375) on candidate `c1bdcf57500f45f15e83876a5b4ae6fcb6387f5f` passed the iPad stage but failed the iPhone stage. The iPhone log shows `testEncryptedCompanionPairingScreenAndInputRejection` exceeded XCTest's 120-second per-test allowance while entering the public address. `typeText("8.8.8.8")` stalled on the simulator's “App animations complete notification not received” wait for roughly 60 seconds; the runner restarted and returned exit 65. Even though the aggregate log listed all named cases as passing across attempts, that exit remains a failure and is not accepted as a green walkthrough.

The repair centralizes private-address plus 32-character hexadecimal-code validation in `CompanionProtocol.isValidPairingInput`, shares `isValidPairingCode` with the handshake's secret validation, and adds package tests for valid private ranges, public/loopback rejection, empty/short/long/non-hex codes, and uppercase hex. The UI test retains the encrypted-pairing screen, disabled Connect, no Disconnect, and user-facing instruction assertions without depending on the flaky software-keyboard idle notification. No per-test timeout increase, retry, or assertion suppression is used.

Acceptance: package tests and source-contract guard pass on the exact candidate SHA, then the full iPhone and iPad walkthroughs both exit zero and contain every required named test in one clean run. This is a candidate repair until those hosted results pass.

## iOS menu destination timeout isolation — October 25, 2026

Walkthrough [#40146182462](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182462) on PR #71 head `dc60e513c4829226d8bf763c60d5e047e0beb5a4` failed on iPad because `testMainMenuButtonsAndScreens` never recorded a clean pass. The log shows XCTest restarted after a timeout and the runner correctly marked the menu test missing. All six destination screenshots were captured, so the navigation paths were reached; the single aggregate test still exceeded its per-test budget.

The candidate now uses six test methods and updates the runtime's required-test manifest and source-contract guard together. PR #72's per-device workflow topology is included, with same-branch concurrency retained to cancel stale duplicate runs. No assertion, destination, or required case was removed; no global timeout was raised.

**Status: candidate.** Require the source guard, reconstruction, focused smoke, unsigned archive, and complete iPhone and iPad walkthrough to pass on one exact head SHA before considering the repair accepted.

## Website consent, nonce and third-party script gate

The website security contract now validates the request-time nonce architecture rather than asserting that AdSense is globally disabled. Validate fresh per-response nonce generation, the same nonce on every rendered script tag and CSP header, strict-dynamic behavior, no-store HTML responses, consent-required loading of Analytics and AdSense, reject/revoke behavior, and complete exclusion of the private Songbook. Node handler tests cover nonce injection and route isolation; website contract tests cover the Vercel route map, publisher ID, consent loader, ads.txt, privacy disclosures and native-app separation. Preview acceptance must still verify that Vercel routes actually invoke the function instead of serving static HTML around it. Account approval, Auto Ads configuration and any required Google-certified consent platform are external release gates.

## iOS More-menu route push regression — 2026-10-28

Full walkthrough [#40686820501](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40686820501) passed iPad but failed iPhone in `testMenuSongLibrary`. XCTest successfully synthesized a tap on the visible Song Library row, but the captured accessibility hierarchy still showed the More navigation bar and menu row after the 25-second Library-title wait. This is stronger evidence than a title timeout alone: the destination content was not presented in that attempt.

The candidate repair makes More-menu selections append typed destinations to a `NavigationStack` path instead of replacing the presented sheet's root route. The UI test now requires the `library.content` marker as well as the Library navigation bar, and `tools/check-ios-presentation-contract.py` enforces the path-based routing contract. This is not accepted until the source guard, focused smoke, unsigned archive and full iPhone/iPad walkthrough pass on one exact candidate SHA.


## iOS More-menu navigation path regression — 2026-10-28

The path-based More-menu candidate failed on iPad in [run #40693553013](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40693553013): multiple destinations lacked their expected navigation title, and the Done button was missing. The current candidate attaches the typed destination resolver and Done toolbar to a stable ZStack root inside the NavigationStack. The source-contract guard checks that placement, while XCTest still requires each destination's actual title/content and `navigation.done`. Do not accept the repair until focused iPhone smoke and both complete device walkthroughs pass on the exact same head SHA.

### Resolution (2026-10-29) — repair reverted

The path-based More-menu candidate was merged as PR #113 (`80ae286`) and then **reverted**
after the walkthrough evidence disproved it. iOS visual walkthrough history is unambiguous:

| SHA | Walkthrough |
| --- | --- |
| `d3e34f95` | success |
| `24e091b1` | success |
| `37f7ace1` | success |
| `80ae286a` (PR #113 merged) | **failure** |
| `2a01654f` | **failure** |

Before the merge, both devices passed three consecutive runs. After it, both devices failed
deterministically across `testMenuSongLibrary`, `testMenuCompanionRemote`, `testMenuICloudSave`,
`testMenuLegalPrivacy`, `testMenuSettings`, `testMenuSongbook`,
`testStarterPlaybackAndControls` and `testOnlinePreviewPreservesPausedStarterAndLocalResume`
("Menu destination did not present its expected screen", "The navigation host must provide
Done", "Failed to tap navigation.done").

Reverting the candidate restores a state whose `RootView.swift` is byte-identical to the
green `d3e34f95`/`24e091b1` builds. The original `testMenuSongLibrary` iPhone failure was
**intermittent** (iPad passed in the same run); the candidate converted it into a
deterministic two-device failure, so the revert is the strictly better state.

**Verified after the revert:** iOS visual walkthrough
[run #40787766952](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40787766952)
passed both device jobs on `b7d69f2`; Android validation, iOS validation and the unsigned
iOS archive passed on the same SHA.

Carry forward: the intermittent Song Library transition remains unproven-good and needs a
repair validated by focused smoke plus both device walkthroughs on one exact SHA before it
is accepted. Merging an iOS repair requires that walkthrough evidence, not merely a
mergeable branch.


## Physical iPhone installation versus hosted acceptance

Hosted simulator walkthroughs and unsigned iPhoneOS archives do not establish physical-device installation. The unsigned IPA from `ios-runtime-artifacts.yml` is a build/signing input only. Free Apple Personal Team provisioning is a development-install path, not Safari OTA distribution. Do not upload the unsigned IPA to a public install button or mark it as device-installable. Follow [IOS_CLOUD_SIGNING_AND_DEVICE_TESTING.md](IOS_CLOUD_SIGNING_AND_DEVICE_TESTING.md) for the supported free-account boundary and the additional Apple distribution, profile, manifest, HTTPS and real-device checks required before Safari OTA can be enabled. Physical-device acceptance remains NOT RUN until a real iPhone install and test are evidenced.


## Simulator versus physical-device acceptance terminology

The iPhone and iPad jobs in `.github/workflows/ios-visual-walkthrough.yml` boot Apple simulators through `xcrun simctl` and run XCTest against `platform=iOS Simulator`. Their job names, step names, manifests and reports must say **simulator acceptance**. This is **simulator UI acceptance**, not physical-device acceptance. They are valuable automated UI/runtime evidence, but they are not physical iPhone/iPad tests and do not establish hardware audio, camera, network-environment or device-performance acceptance. Keep physical-device acceptance as a separate, explicitly uncompleted gate unless evidence from an actual device is supplied.


## Exact-SHA verified download workflow identity

The verified-download packager discovers the Android APK from the `KANTA Android validation` run and the unsigned iOS IPA from `KANTA iOS unsigned artifacts`. It separately requires `KANTA iOS visual walkthrough` to pass on the same exact main-branch source SHA. Keep these names synchronized with the `name:` fields in the root workflow files; a renamed or missing workflow must fail its contract tests rather than silently accepting an older artifact.


## Exact-SHA download publication automation

A successful KANTA iOS unsigned artifacts run on main is the release-pipeline completion signal. The Verified download packaging workflow then resolves the Android validation artifact, unsigned iPhoneOS IPA and both-device simulator walkthrough from the same exact main-branch SHA. It waits for in-flight exact-SHA gates instead of accepting an older successful artifact.

Only after all three gates pass does packaging create up-to-30-day, provenance-bearing Android and unsigned iOS bundles. The publication helper verifies that both builds share the same full source SHA, validates the expected debug/unsigned signing states, and writes apps/web/downloads.json plus the two platform download sections. The workflow commits those generated website files with its narrowly scoped GitHub Actions token and dispatches the existing Vercel production deployment workflow.

The iOS download remains an unsigned personal-signing input; this automation does not create Apple signing identities, provisioning profiles, Safari OTA installation or physical-device acceptance. A failed or missing exact-SHA gate fails closed and leaves the previous website downloads untouched. A rejected non-fast-forward publication push also fails safely rather than overwriting newer main-branch work.

**Gate semantics (2026-10-31):** the `documentation-sync` job in `website-validation.yml` compares the full PR base-to-head diff; it does not require every doc to be modified in the same commit. The required document set is path-driven and missing files fail with an explicit list. The guard also runs in existing main-branch build/deploy jobs using the push's before/head range (or the selected commit's parent for manual/workflow-run releases). It is a step, not another job, in those workflows. The separate PR workflow is the only new lightweight runner; no Xcode or Android build is started solely for documentation.


## Website consent runtime regression

`tools/tests/test_site_page_handler.test.js` verifies that the request-time renderer removes a legacy static `/analytics.js` script element before adding nonces and injecting the consent manager. This prevents a nonce-authorized Analytics bootstrap from executing before the visitor's choice. The live preview still requires browser-level acceptance/rejection/revocation checks; static tests do not simulate browser cookies or prove third-party network behavior.
