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
| Android runtime instrumentation | Real JNI/AudioTrack, FIFO commit, cancellation, controls, pause/resume, failed prepare and activity rendering | Clean API 35 emulator install; custom Instrumentation, no external test library |
| Browser interoperability | Fresh Android export through the captured browser decrypt function | Node after Android regression fixture generation |

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

The root `ios-runtime-artifacts.yml` runs automatically for relevant feature-branch iOS pushes, same-repository pull requests and after successful main-branch reconstruction validation. Manual dispatch remains available for a deliberate rerun. It builds all Xcode UI tests, executes the complete walkthrough suite on an available iPhone and iPad separately, records each simulator and extracts XCTest attachments even on failures. Stable accessibility IDs identify the requested starter and current song, and predicates wait for actual playback. Logs, results and video remain available for seven days. Only after both walkthroughs pass does it build/package an unsigned iPhoneOS personal-signing input; signing and hardware acceptance remain separate.

Android runtime fixtures live in the test APK behind a signature permission declared only by debug builds. The read-only provider exposes synthetic/original starter bytes and a bounded cyclic SAF tree; no user storage is exposed. Instrumentation checks nested traversal, partial failure, cancellation rollback/reopen, byte integrity, real controller import, custom pad decode/reset and idle intermission gating. Fixture code is excluded from release builds.



Android emulator startup uses an explicit shared AVD directory for avdmanager and emulator. The helper detects early process exits, bounds boot/ADB/instrumentation, always collects diagnostics and retires the emulator. Use `owner-approved-android-validation` for Android-only repairs; `owner-approved-validation` still runs both native platforms. No source tests are skipped by the focused label.

## iOS KCP1 controller candidate

`owner-approved-ios-validation` selects the full iOS package/app gate without rerunning unchanged Android native tests. The job pins Java 17 and compiles the unmodified Android CompanionProtocol with a synthetic Java TCP peer. LocalNetworkControlTests requires that peer via KANTA_KCP_TEST_CLASSES; absence is a failure, never a silent skip on macOS. Fixed independent Python crypto vectors check exact handshake, request and bidirectional record bytes. Live Java/Swift socket checks exercise the actual iOS client, bounded busy behavior, Unicode, distinct repeated reservations, cancellation and revocation. This is automated interoperability evidence, not a physical Android/iOS pair or iOS-host acceptance.

## iOS walkthrough runner diagnostics and suite timeout

`tools/capture-ios-runtime.py` still executes the complete four-test `KantaVisualWalkthroughUITests` suite on each platform. XCTest per-case deadlines stay 120 seconds (180 seconds maximum), while the outer process deadline is 1,080 seconds so it cannot abort a legitimate multi-case run at 660 seconds. A failing process prints the last 100 lines from its test log in the Actions job and retains the full log and completed `.xcresult` attachments; partial result bundles are not passed to `xcresulttool`. The 55-minute workflow limit and existing red-on-failure/unsigned-artifact gates are unchanged. This addresses a confirmed iPad outer-runner timeout, not the separate iPhone UI assertion; the latest branch still requires an accepted iPhone/iPad runtime run before merging or updating public binaries.

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

Reconstruction validation runs for relevant app, tool and workflow changes on feature/main pushes and pull requests. iOS runtime, website and verified-download contract checks also run on relevant feature-branch pushes so a PR does not need a manually applied label to start. iOS simulator walkthroughs follow relevant same-repository pull requests and successful main reconstruction runs. Website validation runs for relevant website changes. Verified-download contract tests run automatically; packaging the fixed, previously accepted IPA/APK artifacts still requires owner dispatch or the packaging label. No workflow signs, merges, deploys or publishes production downloads automatically.

## CI separation: fast validation, unsigned artifacts and visual acceptance

- `reconstruction-validation.yml` remains the required PR source, Swift package, simulator-build, and Android validation gate. It does not execute the long XCTest UI walkthrough.
- `ios-runtime-artifacts.yml` builds the unsigned personal-signing IPA only on manual dispatch. Its success proves packaging, not simulator interactions, signing, or installability.
- `ios-visual-walkthrough.yml` runs the full four-case XCTest walkthrough separately on both iPhone and iPad, with screenshots, recordings and failure diagnostics. It is manually dispatchable, scheduled weekly (Tuesday 03:00 Asia/Manila; Monday 19:00 UTC), and triggered by `ios-rc-*` tags. It does not run on ordinary pull requests or documentation commits.
- A release candidate requires a successful visual walkthrough on the **exact candidate SHA**; weekly or unrelated-commit green evidence cannot substitute for release acceptance. CI does not sign, publish or deploy.
- `ios-smoke.yml` runs `testFocusedNavigationSmoke` on an iPhone simulator for relevant iOS PR changes. It checks Search, fullscreen dismissal and Search recovery. This is not the full iPhone/iPad release walkthrough.
- This split deliberately prevents the expensive walkthrough from blocking ordinary merges while keeping the full iPhone/iPad visual acceptance mandatory for release. The embedded Stage UIKit-presenter repair must pass the complete suite on both devices on the exact final candidate SHA; build-only and source-guard success are not device acceptance.

## GitHub Actions cost controls (October 2026)

- Feature branches are validated by the pull-request event only; ordinary feature-branch pushes do not duplicate the reconstruction, website or download-contract jobs. Main pushes retain post-merge verification.
- The unsigned personal-signing IPA archive is manually dispatched only; compilation, Swift package tests and the unsigned simulator build remain in normal reconstruction CI. Do not interpret an unbuilt IPA as a release artifact.
- Full iPhone/iPad visual walkthrough remains manual, weekly or release-candidate-tag gated. A release candidate must pass both devices on its exact SHA.
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
