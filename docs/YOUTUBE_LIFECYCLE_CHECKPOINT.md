# PR #51 foreground player lifetime repair — October 18, 2026

Based on PR #51 source `77f9f1078efc05cf91cc06db4fbd93780007c87e`. The preceding reconstruction and focused iPhone smoke runs passed that base only. This batch does not inherit their green status.

Each online preview receives a distinct presentation UUID, including repeated links. `YouTubePreviewLifecycle` owns only that transient identity; it has no local transport, reservation, persistence or decoder reference. Token-matched retirement prevents a late failure/dismissal from closing a replacement. Native views have separate terminal retirement guards.

Android MainActivity synchronously releases/detaches the old WebView before constructing a replacement. Activity pause retires both preview and link-entry surfaces, and foreground return never reopens them. Dismissal/error cleanup is idempotent, main-frame load/HTTP errors are recoverable by explicit reopen, and renderer loss removes/destroys the unusable WebView without further load commands. Inactive callbacks cannot construct a new player. Android runtime instrumentation exercises native replace/repeat/pause/reopen/error/late-callback paths without depending on a remote response.

iOS uses a per-sheet native player owner to retire an existing WKWebView before attaching its replacement. UUID-based SwiftUI identity prevents same-link reuse across lifetimes. Resigning active, sheet disappearance, Stop and Done retire the native instance; suspension/blanking and delegate invalidation precede asynchronous cancellation callbacks. Picture-in-picture is disabled. Main-frame failures and WebKit process termination return to link entry with a recoverable notice. The domain tests cover repeated links, replacement, stale failure, background retirement, reopening, invalid-link preservation and distinct local reservation identities. These tests do not establish actual WKWebView/media teardown timing; iOS runtime lifecycle acceptance remains required.

Pre-push checks: Swift test-layout and presentation guards passed; 50 Python release-tooling tests and four starter-generator tests passed; deterministic starter guard and diff whitespace check passed. The Java 17 compiler module compiled the complete domain regression suite; all 479 assertions and captured-browser decrypt/wrong-code/tamper checks passed. No Swift compiler or Android SDK is available in this execution environment. Hosted Swift package/app build, Android lint/build/domain/runtime and iPhone smoke must validate the resulting head. No physical-device, signing, merge, packaging, deployment or release is claimed.

Native diagnostic: the unchanged ASan/UBSan harness compiled, but LeakSanitizer cannot enumerate process tasks under this execution environment. A diagnostic rerun with leak detection disabled passed audible pitch, transposition, truncation and 2,000 mutation checks. This does not establish leak-check success; hosted CI retains its unchanged full sanitizer command.

Remaining PR gates: audio-focus arbitration without forwarding transport commands, iOS native lifecycle runtime coverage, hosted changed-head checks, and physical-device embed/interruption/privacy/advertisement acceptance. This change does not claim simultaneous audio is prevented. Local/YouTube queues remain independent; the older mixed-source design is superseded by the owner decision in YOUTUBE_PLAYBACK_PLAN.md. UI restoration remains separate in PR #52.


## Validation follow-up — October 18, 2026

PR head `92e838c` passed Android validation (including emulator runtime), unsigned iOS simulator app build, and focused iPhone smoke run `39103776922`. Reconstruction run `39103777006` failed while compiling package tests: six direct mutating calls inside Swift Testing expectations expanded to immutable receivers. The test-only repair evaluates each call before asserting its Boolean result, preserving every assertion and production lifecycle behavior. Hosted compilation and package execution must validate the repair head before green status is recorded. See CI_FAILURE_REPAIR_LEDGER.md for the exact diagnostic and run/job identities.

## iOS local/online audio coexistence guard

Commits `2500f0bd1b951ec3f3cbabcc5882e11cc98f61be` and `ab2e2336171694ecc7e04583336b59cad7ebe2d5` pass the authoritative HostView transport into the opt-in online preview. While local transport is playing, opening an online video is disabled; if local playback becomes active during an online preview, the native player owner retires its WebView and clears its preview identity. No local queue mutations or remote transport forwarding are introduced.

This is an iOS UI/lifecycle guard, not complete audio-session arbitration: Android coordination, remote playback races, intermission/SFX, simulator runtime checks and physical-device audio interruption behavior remain open. The change has not yet been compiled or tested on its new head. Do not claim that simultaneous audio is universally prevented.


## Verified hosted checkpoint — October 18, 2026

Source `5c1b0f4eea9f92196b836a8bbf2980a5bb6793ad` passed reconstruction run [39106105837](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39106105837) and focused iPhone smoke run [39106105865](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39106105865). Reconstruction job steps confirm Swift package/test compilation, package execution, unsigned simulator application build, Android assembly/lint/domain regression, full native sanitizer gate and emulator runtime checks passed. This closes the earlier Swift Testing macro compilation failure after repair `1ca7454`; no assertions were removed. It also validates the subsequently added iOS/Android local-playing preview-entry guards on this source.

This is hosted evidence for the named SHA only. The smoke test is navigation coverage, not iOS native online-player lifecycle acceptance. General audio-focus/session arbitration, Android remote/asynchronous-start preview reconciliation, iOS runtime online lifecycle coverage and physical-device embed/interruption/ads acceptance remain open. No merge, signing, packaging, deployment or release is claimed.


## Android online preview follows authoritative playback publication — October 18, 2026

Baseline: PR #51 `5c1b0f4`, whose reconstruction and focused iPhone smoke checks passed. The online/local overlap guard previously ran only after primary Play/Next button callbacks. Encrypted companion commands bypass those callbacks, and backend start acknowledgement is asynchronous, so those paths could leave a visible online player alive after local playback started.

MainActivity now reconciles the preview at the start of the existing SessionController listener's render callback. All published local starts/resumes retire the online instance; primary buttons again issue only controller commands. The preview owner does not pause local transport, consume reservations or introduce a playback queue. Replacement/dismissal guards remain unchanged. This closes a controller-observation gap, not general OS audio-focus arbitration or a guarantee of zero overlapping audio frames. SFX/intermission, iOS native runtime and physical audio/session acceptance remain separate.

The Android instrumentation uses real KCP1 socket commands to reserve/start a local starter while a preview is open, waits for backend start acknowledgement, verifies native preview retirement, pauses, explicitly reopens the preview, resumes remotely and verifies retirement again. Reservation alone must preserve the preview, normal FIFO consumption remains checked, and local Stop must not reopen online playback. New-head APK/lint/emulator acceptance is pending. No website, dependency, permission, persistence or remote-protocol change.


## Verified controller-observer repair — October 18, 2026

Source `fa5dcbcf7aa070d924d86ed6c3e832af3618cebb` passed reconstruction run [39108033371](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39108033371): Swift package/test compilation and execution, unsigned simulator build, Android build/lint/domain/native sanitizers/browser interoperability and actual emulator runtime checks. The added KCP1 remote start/resume regression passed. Earlier Android-only run `39107996743` was cancelled by the superseding run, not a regression failure. Focused iPhone smoke run `39108033323` was still in progress at this checkpoint; no smoke, physical-device, signing or release result is inferred from reconstruction success.


## Android audio ownership candidate — October 18, 2026

SessionController holds only a temporary online-audio UUID and native-retirement callback. A claim is refused while karaoke is playing/preparing or microphone/monitoring is enabled. Successful claim stops effects/intermission, disables intermission auto-restart and abandons idle native focus; it does not pause local karaoke or mutate current song, reservations, settings or source bytes. The visible official WebView remains user-gesture controlled. Every explicit native focus acquisition synchronously retires the WebView before requesting focus. Enabling intermission explicitly also retires it, while passive state publication cannot reclaim online ownership.

MainActivity releases by UUID after native teardown; replacement, load failure, dismissal and background release only their own lifetime. Controller revocation clears the token before invoking its native callback, so synchronous cleanup cannot recursively retire a replacement. All callbacks and native focus requests remain on the main Looper. No callback forwards YouTube transport into local karaoke.

Runtime instrumentation adds effects → online → effects, stale prior-token release, online → microphone, refusal during active microphone input, intermission → online → explicit intermission, background/reopen and FIFO preservation. Existing encrypted remote start/resume checks continue to exercise actual local playback. Remote responses are not fixtures: native preview tests immediately block WebView network loads and use local HTML.

All 479 domain assertions, browser interoperability and 50 Python release-tooling tests passed locally; layout/presentation guards and whitespace review passed. Native compilation/lint/emulator checks are pending on the resulting commit. This is Android coordination only; iOS audio-session/auxiliary parity, native iOS online lifecycle coverage, external focus/interruption behavior, physical audio timing and real embeds/ads still require acceptance. Reference reviewed: https://developer.android.com/media/optimize/audio-focus .


## Verified Android audio-coordination checkpoint — October 18, 2026

Source `99f3898d35394a26d1879149c3f8637acd48ef56` passed reconstruction run [39108899667](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39108899667). Both Android and iOS jobs succeeded. Android native emulator execution includes the new effects/intermission/microphone/stale-owner/background/FIFO cases, plus the existing encrypted-remote start/resume cases. Swift package compilation/tests and unsigned simulator app build passed. Focused iPhone smoke run `39108899640` remained in progress at this checkpoint; its outcome is not inferred. This validates the Android candidate in hosted checks, not physical output timing, real YouTube media/ads or iOS audio-session parity. No merge/signing/release.


## iOS native lifetime component coverage candidate — October 18, 2026

`KantaNativeTests.YouTubeEmbeddedPlayerLifecycleTests` uses real WKWebView instances in an application-hosted simulator bundle. Cases verify native delegate retirement before replacement attaches, distinct repeated-video identities, stale-process callbacks not affecting replacements, background retirement with no automatic restore, exactly-once process failure and explicit reopen. No remote video, playback timing or advertisement is a fixture. The unchanged production component source is compiled directly into the test bundle, avoiding any release-app testability setting or production-only test hook.

`ios-smoke.yml` retains its existing navigation test and adds the three native cases, with explicit named-pass verification. A missing native case or an earlier failure/restart keeps the gate red even if a later zero-test attempt reports success. All test assertions and prior package/UI target wiring are preserved. Full iPhone/iPad walkthrough remains separate.

Local checks passed: test layout (39 Swift files), presentation guards, target/host/source/test-selection YAML review, all 54 Python release-tooling tests including four log-gate regressions, and diff whitespace. Native compilation/execution is pending on the resulting head. This advances native component evidence only; iOS audio-session coordination, actual SwiftUI sheet/media teardown timing and physical embed/interruption/privacy/ads remain open.


## Verified iOS native component checkpoint — October 18, 2026

Source `2302703481810824d75b0ae9a4b84b417a6c39ac` passed reconstruction run [39109936325](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39109936325) and native/navigation smoke run [39109936327](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39109936327), job `117623681797`. The retained log explicitly reports passes for `testForegroundLossRetiresNativeViewAndDoesNotRestoreIt`, `testProcessFailureRetiresExactlyOnceAndOwnerCanReopen`, `testReplacementRetiresOldNativeViewBeforeAttachingNext`, and the unchanged `testFocusedNavigationSmoke`. The new named-pass verifier also succeeded. Thus the new Xcode target compiled and all three native cases actually executed; this is not a zero-test or navigation-only green.

Reconstruction passed both native platform jobs, including Swift package/test compilation/execution, unsigned iOS simulator build and Android build/lint/domain/sanitizer/emulator acceptance. This closes the native WebKit component coverage candidate. It does not establish SwiftUI sheet/real-media timing, iOS shared audio-session and auxiliary coordination, physical embeds/interruption/privacy/advertisements, signing or release. Those PR #51 gates remain open; the Companion Remote design/roadmap stays separate in PR #53.


## iOS audio handoff candidate — October 18, 2026

Based on PR #51 head `5d25f62993c8b003099c345b5387172a1fdf5af7`. AppServices constructs one MainActor KaraokeAudioOwnership alongside the existing LocalKaraokeController and a shared intermission owner. The coordinator owns only an ephemeral online UUID/retirement callback and foreground/microphone eligibility; it owns no queue, local transport, persistence or YouTube commands.

Online claims acquire the controller's existing cancellation-aware operation gate, reject actual playing state, stop SFX, suspend both idle rendering engines without Pause/Stop or position changes, then atomically recheck microphone/foreground/monitor eligibility on MainActor before stopping intermission and configuring the shared playback session. Local load/resume/playing seek and SFX retire the online native owner before native output. Remote commands and natural advance use these same controller paths. Capture announces activity synchronously before its permission await and releases it on denial/failure/stop; native output preserves capture's playAndRecord category. Session errors fail visibly. Foreground loss retires online ownership; returning does not reopen it.

The sheet cancels pending claims on close and rechecks its request UUID/foreground/ownership after suspension. The representable also checks ownership before attachment/load, preventing a revoked pre-mount session from constructing an active player. Stale release cannot clear a replacement. Intermission stops immediately, invalidates fades, and requires explicit re-enabling; automatic snapshot updates cannot reclaim online audio. Source files and reservation/playback identities are unchanged.

Regression coverage adds controller preparation/cancellation/microphone races, paused song/FIFO identity, explicit Next/SFX handoff, stale release, foreground, session failure and both rendering routes. Native smoke adds real WKWebView retirement-before-session configuration and synthetic AVAudioPlayer intermission handoff/restart; the named-pass verifier now requires all five native cases plus navigation. The test target compiles the two production components directly; no app testability, dependency or permission change.

Local layout, presentation, starter resources/generation and whitespace checks passed. Hosted Swift compilation, package execution, unsigned simulator app build and expanded native/navigation smoke are pending on the resulting source. Physical audio timing, real embeds/ads, interruptions, privacy and full iPhone/iPad release acceptance remain open. No zero-overlap timing, signing, merge, deployment or release is claimed.


The initial iOS audio-handoff source b3e26e5 passed all 178 Swift package tests and Android validation, but both the application and native-smoke bundle failed compilation on intermission's shadowed optional directory parameter. The parameter is renamed internally while keeping its external label. No native execution is claimed on that source. See the failure ledger; validate the corrected commit before acceptance.


## Verified iOS audio handoff — October 18, 2026

Source `6f7a0ece9e42cc500ce4f52cc414c58e5e11b58e` passed [reconstruction 39112387766](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39112387766) and [native/navigation smoke 39112387680](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39112387680). Reconstruction compiled every Swift package test target, executed all 178 Swift tests and 54 release-tooling tests, built the unsigned Release iOS simulator app and passed Android build/lint/domain/sanitizer/browser/emulator checks.

Smoke job `117631072361` explicitly passed all five YouTubeEmbeddedPlayerLifecycleTests cases and testFocusedNavigationSmoke. The new intermission handoff case passed at 08:49:55 UTC (0.070 s); WebKit retirement-before-session configuration passed at 08:49:55 (0.008 s); focused navigation passed at 08:50:40 (35.553 s). The named-pass verifier accepted all six cases at 08:51:24. This closes the hosted iOS audio-arbitration implementation/coverage gap and the initializer compilation repair; no assertions were removed. The earlier failed b3e26e5 remains recorded in the failure ledger.

The coordinator preserves local song/position/playback/FIFO identities, retires online ownership before native output requests, stops auxiliary audio and fences microphone permission, cancellation, foreground and stale owners. Tests establish the named domain/native behavior, not real-media zero-overlap timing. Actual SwiftUI online entry/dismissal, physical embeds/interruptions/privacy/ads and full exact-candidate iPhone/iPad release acceptance remain open. PR #51 stays draft. Next executable task: exercise the actual online-sheet entry/close/reopen/error flow through the host UI, retaining device/media acceptance as separate gates. No merge, signing, packaging, deployment or release occurred.


## Online-screen UI coverage candidate — October 18, 2026

PR #51 baseline 24db7a1 now adds two real XCUIApplication flows through Stage CONTROL's actual online sheet. They cover invalid-link rejection before WebView construction, explicit valid entry, stop/reopen, background/return without resurrection, Done/reentry and navigation recovery, plus preserving a paused original starter and explicitly resuming it afterward. Stable accessibility identifiers and link-keyboard Done dismissal support the real UI; no fake player or launch-environment bypass is introduced.

The synthetic video ID never autoplays. External YouTube availability is not a fixture: valid entry must reach either actual native player controls with a WebView or the production recoverable load-error state, with retained outcome screenshots. A local audio-claim failure is not accepted as an external-load error. This verifies UI lifecycle/recovery, not successful remote media or deterministic external-error delivery. Existing native process-failure, audio-handoff and controller/FIFO regressions remain required.

Focused smoke retains all five native cases and navigation and now requires both named online-screen cases (eight passes total). The full release walkthrough gate also requires all seven methods in the UI suite; timeouts, serial execution, failure detection, evidence retention, signing and deployment boundaries are unchanged. Local layout/presentation/YAML/whitespace and 55 tooling tests passed. Hosted app/test compilation and actual simulator execution are pending on the resulting source. Physical embed/ads/interruption/privacy and exact-candidate full iPhone/iPad release gates remain open.


## Verified online-sheet UI acceptance — October 18, 2026

Source `594cd94c2c78d12fcf8fd21d0ece1cd5949c2c72` passed [reconstruction 39114570402](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39114570402) and [expanded smoke 39114570339](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39114570339). Both reconstruction platform jobs succeeded. Smoke job `117637615146` explicitly passed all five native cases, focused navigation, testOnlinePreviewEntryCloseReopenAndForegroundLifecycle (105.968 seconds) and testOnlinePreviewPreservesPausedStarterAndLocalResume (54.616 seconds). The eight-name verifier succeeded at 09:36:18 UTC; this is execution evidence, not build-only acceptance. No assertions or previous cases were removed.

Actual host-sheet navigation, invalid-link validation, explicit entry/stop/reopen, foreground cleanup, dismissal/reentry/navigation recovery and paused-original-starter preservation/local resume are accepted on that iPhone simulator source. External availability was deliberately not treated as playable-media evidence: the UI cases accept either real native player controls or the production recoverable external-load error. Successful YouTube playback, ads, hardware interruption/privacy/audio timing and the updated seven-case full iPhone/iPad release walkthrough remain separate open gates. PR #51 remains draft and unmerged; no signing, deployment or release occurred.

Next executable acceptance: full seven-case iPhone/iPad walkthrough on the exact candidate source, followed by physical-device/real-media acceptance. No new failure occurred on this candidate. The earlier initializer repair history remains unchanged.
