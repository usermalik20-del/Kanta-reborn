# KANTA architecture and documentation contract

**Normative design target — 2026-10-17.** This is not evidence that planned components are implemented. Active source and validated tests define current behavior. Historical recovery snapshots are immutable evidence.

## Mandatory engineering process

Before coding, read `AGENTS.md`, this contract, `docs/ARCHITECTURE.md`, `docs/CODEBASE_CONTEXT_PACKET.md`, and the relevant platform source/architecture. Trace entry point, call sites, actual state owner, async callbacks, presentation lifecycle, persistence and failure paths. Record invariants and test expectations before changing code. Implement at the owning boundary, preserve idempotency and cancellation, update affected documentation in the same PR, and report compilation, unit tests, simulator UI, physical device, signing and deployment separately. Do not add retries/sleeps to mask a lifecycle bug.

## Ownership and dependency rules

| Boundary | Contract |
| --- | --- |
| iOS app composition (`apps/ios/Sources/App`) | Own service construction, scene lifecycle and root navigation; never duplicate playback decisions |
| iOS domain and audio (`GeloKaraokeCore`, `LocalKaraokeController`) | One authoritative playback state, FIFO queue, immutable playback identity and timed lyric projection |
| iOS Library (`Features/Library`, `LibraryStorage`) | Import/index BYOL and issue controller commands; no stage presentation authority |
| iOS Stage (`Features/Host`) | Render shared snapshots and dispatch typed user intents; no second queue |
| iOS presentation (**active contract**) | One `@MainActor` coordinator serializes navigation, embedded Search/Queue, CONTROL, Favorites, session prompts and fullscreen. `HostView` owns the SwiftUI sheet for Host panels; embedded Stage Search/Queue use a stable UIKit presenter mounted in the Host background and complete `.stage` ownership from UIKit dismissal callbacks. The UIKit fullscreen presenter retains its separate child Search/Queue sheet. Library file pickers/summaries are subordinate |
| Android (`apps/android`) | Native UI observes authoritative Android controller; independent native audio, permissions and optional DSP adapters; same product invariants |
| Web (`apps/web`) | Only advertise verified platform capabilities, downloadable checksummed artifacts and reviewed legal/privacy copy |
| Root CI/tooling | Bounded validated checks, immutable failure evidence, separate signing/release approval |
| `recovery/` | Provenance only, never runtime dependency or wholesale overlay |

Dependency direction: UI/composition → domain interfaces → platform adapters. Domain must not depend on SwiftUI/Android views. Async work has explicit owner, cancellation and generation/operation identity. No playback snapshot refresh may dismiss or present navigation. Every reservation has a unique identity; scoring completion is idempotent.

## Presentation state machine — required; PR #36 candidate implementation

States `stage`, `presenting(destination)`, `presented(destination)`, and `dismissing(destination)`. Destinations include navigation, Search, Queue, CONTROL, Favorites, session save and fullscreen. Serialize requests; dismissal completes only from the owning presenter callback, never elapsed time. A second request during transition must be explicitly queued or rejected with accessible feedback; no silent dropped tap. Nested Library import/summary UI cannot outlive Library. UIKit and SwiftUI presentation share the same coordinator. The prior single-Host-SwiftUI-sheet candidate still failed to expose embedded Stage Search's keypad on iPad. The active repair candidate gives embedded Stage Search/Queue a stable UIKit modal presenter, while Host panels retain their SwiftUI sheet and fullscreen Search/Queue retain their separate child sheet. Preserve focus and accessibility. Unit-test transitions, cancellation and concurrent playback updates; require exact-SHA iPhone/iPad acceptance before release.

**Release-blocking regression (validated on PR #36 final head):** Stage → More → Library → play starter → Done → Search (one tap) → keypad → Cancel → CONTROL, on iPhone and iPad, repeated with playback snapshot updates and rotation. On failure retain screenshot, full accessibility hierarchy and `.xcresult`. PR #36 subsequently passed the final-head iPhone/iPad runtime walkthrough and merged as `59258ee`. PR #42 added a focused iPhone navigation smoke regression and merged as `714bfd6`. The full walkthrough remains required for each release candidate's exact SHA. PR #36 replaced those independent request paths with a shared gate candidate. The UIKit presenter now acquires/releases fullscreen ownership through the gate and acknowledges dismissal from UIKit's completion callback.

## Planned extension points and pending tasks

| Feature | Architectural placement | Evidence/gate |
| --- | --- | --- |
| Offline MIDI/KAR/SF2, lyric timeline, BYOL | Native domain controller + library adapter | Existing implementation; regression and device audio |
| Search/Reserve/Cancel, stage controls, FIFO | Stage intents → controller; overlays → presentation coordinator | Shared gate candidate; iPhone/iPad runtime gate pending |
| Camera BGV, personal photos/videos | Optional permission-gated media/capture adapter | iOS/Android device acceptance |
| Fullscreen, external display, scenic/realm visuals | Read-only stage projection and presentation coordinator | Rotation/display tests |
| Microphone, monitor, scoring, pitch correction/Auto-Tune-style DSP | Opt-in audio capture/processing graph; route/feedback safeguards. One capture owner (`MicrophoneInput`); DSP adapters plug into the framework-free `core.PitchSource` port, so the optional SPICE/TFLite estimator (`SpicePitchEstimator`) adds no capture and degrades to the frame detector when its model cannot load | Latency, tuning, safety and hardware validation. SPICE DSP is implemented and host-JVM regression-checked (`SpicePitchRegressionTests`); confidence threshold, device latency and calibrated accuracy still need device acceptance |
| Companion remote | Authenticated, versioned protocol adapter, never separate queue | iOS/Android interoperability and two-device test |
| YouTube both platforms | Independent local and official visible online transport; identity-fenced foreground player owner, separate audio-focus coordinator | PR #51 foreground-lifetime candidate; audio focus and runtime/device acceptance open. Updated owner decision in `docs/YOUTUBE_PLAYBACK_PLAN.md` supersedes mixed-source FIFO; no ad suppression/extraction |
| Voice search | Permission-gated query provider into existing search | Language/device acceptance |
| Favorites, songbook, session save | Library metadata + idempotent persistence, modal intent | Data and lifecycle regression |
| Download pages, onboarding, privacy/legal | Platform-specific verified feature manifest + release artifacts | IPA/APK checksum, signing and deployment gates |
| Screenshots, recordings, simulator/device CI | Diagnostic/test tooling | Evidence is not a release artifact |

Do not add fake functional controls for pending features. Add typed interfaces and explicit unavailable states when needed; implement and verify one adapter at a time. Update this table with concrete source/tests as milestones land. Detailed backlog remains in platform roadmaps and checkpoints.

## Documentation authority and drift review

1. This document: mandatory design and workflow.
2. `docs/ARCHITECTURE.md`: active repository design and historical change notes.
3. `apps/ios/Docs/ARCHITECTURE.md`, `apps/android/README.md`, `apps/web/README.md`: platform source mapping.
4. `docs/CODEBASE_CONTEXT_PACKET.md`, `docs/*CHECKPOINT.md`, `docs/TESTING.md`: dated evidence, not blanket release approval.
5. `apps/ios/Docs/ROADMAP.md`, `docs/YOUTUBE_PLAYBACK_PLAN.md`: pending work.
6. `recovery/`: immutable historical context.

**Repository documentation audit, 2026-10-17:** Reviewed the tracked file tree (761 entries), 50+ Markdown documents including recovery copies, and the principal architecture, context, testing, automation, roadmap and YouTube plans. Identified: (a) chronological architecture documents mix proposed/merged/validated milestones; (b) iOS roadmap still leads with an October 10 state despite active reconstruction; (c) Android/iOS parity lacks a single evidence-backed feature matrix; (d) current modal source violates intended single-owner contract; (e) platform checkpoints use inconsistent acceptance terminology. These are **open documentation reconciliation findings**, not completed repairs. Do not rewrite provenance or promote historical claims to current status.

### Required PR review checklist

- [ ] Trace actual code paths, owner, dependency direction and side effects
- [ ] Test actor isolation, cancellation, lifecycle, idempotency and stale callbacks
- [ ] Preserve FIFO, source bytes, permissions, privacy and licensing
- [ ] Prevent competing modal owners and inaccessible stage controls
- [ ] Identify iOS/Android parity adapters and pending acceptance
- [ ] Update architecture, roadmap, checkpoint, README and website claims as applicable
- [ ] Report static/unit/build/UI/device/signing/deployment results independently

## PR #36 implementation checkpoint — 2026-10-17

RootView now tracks navigation dismissal until the SwiftUI sheet's `onDismiss` callback, and exposes `navigation.done` as a stable accessibility identifier. This is an incremental lifecycle guard, **not** the final unified presentation coordinator: HostView, KantaMainPlayerView, nested Library presentations and UIKit fullscreen still require coordinated ownership. The walkthrough's previous `navigationBars["Song Library"]` wait was invalid because LibraryView's title is `Library`; the test now waits on the real title and retains a failure-time accessibility dump. The later PR #36 final head passed iPhone/iPad walkthroughs and unsigned IPA packaging; deployment and physical-device acceptance remain unverified.

## Verified CI and presentation reconciliation — 2026-10-18

PR #36 final-head hosted validation passed and merged as `59258ee`; PR #42's focused iPhone smoke test passed and merged as `714bfd6`; PR #43's CI cost controls passed and merged as `ad95e8b`. The post-merge reconstruction run `39051661330` succeeded. The presentation-coordinator architecture above remains a design contract, not proof of every lifecycle or real-device scenario. See `docs/TESTING.md` for current trigger and release-gate definitions. Older candidate descriptions are historical checkpoints and must not be interpreted as current CI failures.
