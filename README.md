# KANTA Reborn

Native Android and iOS karaoke applications built around the **Compact Workspace** redesign, together with their public website.

**Current release:** `v0.1.0-workspace-redesign` at SHA `41c8ddde`.

## Android reference assets — safe-branch integration

The in-progress Android reference-led UI branch includes selected native VectorDrawable icons and optimized phone/landscape stage artwork from the owner-supplied adaptive asset pack. Home, Search, Queue, Pitch & Tempo, Mic & Voice and Visuals are mounted as real destinations backed by existing session, queue, microphone, camera and media-import owners. The browser remote has a responsive desktop sidebar/library/Now Playing layout. This remains source integration, not a released APK or completed visual acceptance. See [the Android asset manifest](docs/KANTA_ANDROID_ADAPTIVE_ASSETS.md). CI instrumentation captures all six destinations, and the runtime collector plus Android workflow artifact allowlist preserve each named screenshot.

## What shipped

The Compact Workspace redesign implements an **"Always on Stage"** architecture: the karaoke player stays visible and live as the base layer, while utility panels appear and disappear as transient overlays above it.

| Capability | iOS | Android |
| --- | --- | --- |
| Persistent stage / player | Yes | Yes |
| Transient accessory panels (Search, Queue, Visuals, More) | Yes | Yes |
| Single-active accessory (no stacking) | Yes | Yes |
| Device-specific presentation (iPhone sheet / iPad popover; handset sheet / tablet popover) | Yes | Yes |
| Workspace toolbar (Search, Queue, Visuals, More) | Yes | Yes |
| Accessibility identity containment fix (RC1) | Yes | Yes |
| Toolbar label collision fix (RC2) | Yes | Yes |

**Release artifacts** (`v0.1.0-workspace-redesign`):

- Android APK — `KANTA-Android-preview-41c8ddde.apk`
- iOS IPA — `KANTA-iOS-personal-unsigned-41c8ddde.ipa` (unsigned; requires trusted personal signing)
- Public website — <https://kanta-public.vercel.app>

Download pages under `apps/web/` (`ios-download.html`, `android.html`, `downloads.json`) link to the verified GitHub Actions artifacts and to the GitHub Release, with matching SHA-256 checksums and artifact IDs.

## Website privacy and advertising

The public website retains Google Analytics (`G-L0QV01MWR4`) and Google AdSense (`ca-pub-6290349358884298`) behind an explicit optional-consent choice. Visitors can accept, reject or later change their choice. The private browser Songbook is excluded. These integrations are website-only: neither native app bundles the website scripts or loads website ads. Ad delivery remains subject to Google's publisher approval and account configuration; see [website operations](apps/web/README.md) and [privacy](apps/web/privacy.html).

## Website crawler and scraping defense

The public site declares an AI-training opt-out and refuses the declared AI training, data-mining and SEO-crawler user agents at two layers: a `routes` refusal in `apps/web/vercel.json` that also covers static assets, and the request-time gate in `apps/web/api/page.js`. Search-engine indexing is deliberately preserved, live-user AI fetchers stay allowed, and `robots.txt` carries the canonical sitemap plus a `Content-Signal` preference. The `X-Robots-Tag: noai, noimageai` preference is set twice on purpose: the `vercel.json` headers rule reaches static responses, while the renderer sets it on the HTML routes, because a rewrite to the function replaces the response. `robots.txt` is a declared policy, not an enforcement mechanism, and this is not claimed to make the site unscrapable. The token list is deliberately duplicated across the three files and `tools/verify-website.py` fails when they drift apart. Verified live on 2026-11-01: `/robots.txt` serves the policy, a declared crawler receives `403` on both an HTML route and a static asset, `Googlebot` and a normal browser still receive the rendered page, and `X-Robots-Tag` is present on both static and rendered responses. See [scraping defense](docs/WEBSITE_SCRAPING_DEFENSE.md).

## Repository layout

| Location | Purpose |
| --- | --- |
| `apps/android/` | Native Java/Views app with independent Gradle build |
| `apps/ios/` | Reconciled Swift app and independent Apple build |
| `apps/web/` | Static website and private browser Songbook |
| `recovery/ios/` | Preserved historical source snapshots |
| `docs/evidence/website/2026-10-15/` | Immutable original website evidence |
## KANTA reference-led UI work (Android)

The Android reference-led redesign is being integrated into the existing native Java / Android Views application. The design-system source introduced under `ph.kanta.reborn.ui` is not evidence that every screen is active: the initial screen classes were added without being mounted by `MainActivity`.

The current safe-branch integration makes the Home dashboard the landing destination, mounts it from `MainActivity`, and routes its actions into the existing workspace, library and session owners. The dashboard now uses the reference's 3-column by 2-row feature grid. On Home, the legacy stage pane and legacy tab bar are hidden rather than competing with the dashboard; the stage/session objects remain alive and are restored when navigating to the existing destinations.

The remaining dedicated screen classes are still presentation candidates, not accepted production screens. Search and Queue continue to use the existing `StagePresenterImpl` overlays; Controls, camera, Scenic, Realm, playback and companion functionality remain owned by their existing implementation. Do not wire demo-only controls or substitute sample scores for live state.

The browser companion must be evaluated from its real HTML/CSS/JS and its actual supported command allowlist. A visual redesign must preserve the pairing/fingerprint checks, bounded message format, host-owned state and honest disabled state for unsupported commands.

See [architecture](docs/ARCHITECTURE.md), [testing](docs/TESTING.md) and [reconstruction evidence](docs/RECONSTRUCTION.md) for implementation and acceptance status.

### Compact Workspace implementation

- iOS: `apps/ios/Sources/Features/RootView.swift` instantiates `KantaWorkspace(services:presentationState:)` as the root view body.
- Android: `apps/android/app/src/main/java/ph/kanta/reborn/KantaWorkspace.java` is the root container; `MainActivity.java` sets it as the content view and routes toolbar buttons through `workspace.activateAccessory(...)`.

## CI ownership and release gates

GitHub Actions is partitioned by source ownership. Website changes run website validation; Android changes run Android validation; iOS changes run iOS validation. The lightweight documentation-sync check runs on every pull request and fails when an implementation or workflow change is missing its required owner documentation. Cross-platform iOS archive and iPhone/iPad simulator release evidence for Android-only changes is collected on `main` after merge, not on every Android pull request. Vercel production deployment is separate from pull-request validation and runs only from `main` or an explicit dispatch. See [workflow ownership and testing](docs/TESTING.md). CI status is commit-specific; a green result on an earlier SHA is not evidence for a later commit.

## Open release gates
- **Pitch Settings UI** (Controls → Pitch Settings)- **Pitch Settings UI (Android, compiles in the build)**  exposes sensitivity slider (0.1–3.0), presets (Beginner/Normal/Pro), note picker (C4–B5), and custom Hz input (20–2000 Hz). Settings persist via `SettingsStore` (`pitch.assistantSensitivity`, `pitch.targetHz`, `pitch.targetName`). Voice Coach (`LearningAssistant`) scales thresholds with sensitivity; Pitch Practice (`PitchGameOverlay`) uses stored target Hz as fallback.

- `InAppControlCenterView` still uses the unrestyled `GeloVisualStyle` (14 references) — the last unrestyled surface.
- **Android lyric-stage animation candidate (not yet cloud-validated).** `LyricStage` and the fullscreen `StageView` keep the existing scrolling window, but the active line now fills continuously across each source-timed MIDI fragment instead of switching a whole word, and only the fragment being sung receives a per-letter wave driven by the existing single-owner pitch estimate. No word timings are invented and pitch never changes cue time or scoring. `CountdownStage` renders `Ready ⏱️ → 1 → 2 → 3 → 4 → Kanta 🎤` with a reduced-motion path; `SettingsStore.getCountdownLength()` defaults to 4 and `MainActivity.playWithCountdown()` starts a new reservation through it while resuming a prepared song immediately. The authoring environment has no working JVM (`getcpu`), so compiler, emulator and device acceptance remain open. See [architecture](docs/ARCHITECTURE.md), [reconstruction](docs/RECONSTRUCTION.md) and [testing](docs/TESTING.md).

- **Android Bluetooth headset monitoring candidate (not yet device-validated).** The optional voice-output route now accepts Bluetooth SCO and BLE headsets next to wired, USB and analog/aux line-out, and asks Android for the communication route on API 31 and newer so the processed voice actually leaves the phone. Speaker-class routes (built-in speaker, Bluetooth A2DP speakers, HDMI/ARC) stay blocked because the microphone hears them. Bluetooth audio is mono call quality with higher delay, and the Bluetooth device's own microphone is still not captured by the app. Compiler, emulator and device acceptance for this candidate are open. See [architecture](docs/ARCHITECTURE.md), [reconstruction](docs/RECONSTRUCTION.md) and [testing](docs/TESTING.md).

- The six diagnostic `NSLog` probes previously present in iOS shipping source are confirmed absent from the current tree.
- **Website crawler policy: deployed and verified on 2026-11-01, with follow-ups open.** `/robots.txt` serves the policy, a declared crawler receives `403` on an HTML route and on static assets, `Googlebot` and a normal browser still receive the rendered page, and `X-Robots-Tag` is present on static responses and, after the follow-up fix, on rendered ones. Rate limiting and per-request gating of static assets still need an edge/WAF layer, and no legal clause exists yet. See [scraping defense](docs/WEBSITE_SCRAPING_DEFENSE.md).
- Physical-device quality, physical two-device companion networking, voice quality/feedback/latency, scoring calibration, camera/external-display acceptance and production signing remain separate open gates.
- Companion control is experimental: Android-to-Android only, no forward secrecy, no independent security review, and no iOS interoperability.

## Documentation

Start with [architecture](docs/ARCHITECTURE.md), [engineering architecture contract](docs/ENGINEERING_ARCHITECTURE_CONTRACT.md), [testing](docs/TESTING.md) and [handover](docs/HARDENING_HANDOVER.md). Current checkpoints supersede dated recovery notes.

| Document | Scope |
| --- | --- |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Cross-platform architecture, state ownership, module boundaries |
| [ENGINEERING_ARCHITECTURE_CONTRACT.md](docs/ENGINEERING_ARCHITECTURE_CONTRACT.md) | Rules distinguishing existing functionality, pending extension points and release gates |
| [RECONSTRUCTION.md](docs/RECONSTRUCTION.md) | What was recovered vs reconstructed vs compiled |
| [TESTING.md](docs/TESTING.md) | Test suites, gates and acceptance criteria |
| [VERIFIED_DOWNLOADS.md](docs/VERIFIED_DOWNLOADS.md) | Download provenance and checksums |
| [HARDENING_HANDOVER.md](docs/HARDENING_HANDOVER.md) | Current handover state |
| [AI_PROMPT_BANK.md](docs/AI_PROMPT_BANK.md) | External model prompt bank: pinned metadata, helper, measured behaviour |
| [CI_FAILURE_REPAIR_LEDGER.md](docs/CI_FAILURE_REPAIR_LEDGER.md) | CI incident history and repairs |
| [UNFINISHED_WORK.md](docs/UNFINISHED_WORK.md) | Everything still outstanding, with acceptance criteria |
| [WEBSITE_SCRAPING_DEFENSE.md](docs/WEBSITE_SCRAPING_DEFENSE.md) | Crawler policy, edge refusal, live-user exceptions, unverified live behavior |
| [GITHUB_PUBLISHING_POLICY.md](docs/GITHUB_PUBLISHING_POLICY.md) | Publishing and release gates |

Platform checkpoints: [Android companion](docs/ANDROID_COMPANION_CHECKPOINT.md), [Android camera](docs/ANDROID_CAMERA_CHECKPOINT.md), [Android monitor](docs/ANDROID_MONITOR_CHECKPOINT.md), [Android pitch correction](docs/ANDROID_PITCH_CORRECTION_CHECKPOINT.md), [Android scoring](docs/ANDROID_SCORING_CHECKPOINT.md), [Android personal media](docs/ANDROID_PERSONAL_MEDIA_CHECKPOINT.md), [Android stage](docs/ANDROID_STAGE_CHECKPOINT.md), [Android microphone](docs/ANDROID_MICROPHONE_CHECKPOINT.md), [companion browser](docs/COMPANION_BROWSER_CHECKPOINT.md), [iOS runtime](docs/IOS_RUNTIME_CHECKPOINT.md), [iOS companion](docs/IOS_COMPANION_CHECKPOINT.md), [YouTube lifecycle](docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md).

Website checkpoints: [website 0.10](docs/WEBSITE_010_CHECKPOINT.md), [website 0.9](docs/WEBSITE_09_CHECKPOINT.md), [website 0.8](docs/WEBSITE_08_CHECKPOINT.md), [website 0.7](docs/WEBSITE_07_CHECKPOINT.md), [website 0.6](docs/WEBSITE_06_CHECKPOINT.md), [website 0.5](docs/WEBSITE_05_CHECKPOINT.md).

## Notes for users

GitHub sign-in is required for Actions artifact downloads. GitHub Release assets are durable and do not expire. Separate CI runs can use different Android debug certificates; uninstalling to change builds removes private app state. Keep original media and explicit exports backed up. These are not store releases.


**Consent regression repair (PR #121).** The request-time HTML renderer removes the legacy static `/analytics.js` tag before adding response nonces. The consent manager is now the only entry point that can inject Analytics or AdSense after explicit acceptance. The regression test covers the previously eager script path; preview validation must still exercise accept, reject and revocation in a real browser.

## Android CI runner integrity — 2026-11-01

The Android validation workflow is a full acceptance gate, not a screenshot-only shortcut. Manual dispatch may select an explicit Git ref; ordinary pushes and pull requests validate their event SHA. The workflow definition must keep `workflow_dispatch` and `push` as separate top-level events. The emulator runner must fail only when boot/network prerequisites are absent, then continue to install both APKs, execute instrumentation, and preserve screenshots under `files/`. See [the CI repair ledger](docs/CI_FAILURE_REPAIR_LEDGER.md) and [testing](docs/TESTING.md). The November 1 repair is a candidate until the complete Android validation run passes on its exact PR head.

**Android emulator repair follow-up (2026-11-01):** Android run #400 passed build, lint, native and browser gates but failed emulator instrumentation because `RuntimeChecks` cast the primary `StagePresenterImpl` to the separate fullscreen `StageSurface` class. The follow-up camera acceptance exposed a teardown defect: unbinding CameraX did not detach the preview from its owning stage, restore the primary background or invalidate stale visibility state. The repair candidate explicitly releases both primary and fullscreen previews and strengthens the visibility predicate and emulator assertions. It remains unverified until a new complete Android emulator run passes. See `docs/CI_FAILURE_REPAIR_LEDGER.md` and `docs/RECONSTRUCTION.md`.

## Platform CI ownership

Android validation and APK packaging/deployment are triggered by Android-owned source and build inputs. iOS validation, unsigned IPA packaging and iPhone/iPad simulator acceptance are triggered by iOS-owned inputs. Website validation and production deployment remain website-owned. Cross-platform fan-out is limited to explicitly shared protocol files that a second platform directly compiles or tests. An Android-only change must not start Xcode builds or IPA deployment, and an iOS-only change must not start Android builds or APK deployment. See `AGENTS.md`, `docs/ARCHITECTURE.md` and `docs/TESTING.md` for the enforced policy and regression checks.


### Independent Android and iOS downloads

Android APK publication follows successful Android validation only. iOS unsigned IPA publication follows the iOS archive and additionally requires same-commit iPhone and iPad simulator walkthrough acceptance. Each release updates only its own download page and preserves the other platform's source revision and published artifact. The manifest and verifier validate each platform against its own build and packaging provenance. If a publication attempt uploaded an artifact before failing, a retry may produce multiple same-name artifacts; the workflow must select the newest by `created_at` rather than assuming the name is unique. The website is deployed after the selected platform's metadata passes website validation.

## Android Mic & Voice and Visuals destination integration — 2026-11-01

The safe reference branch now mounts `KantaMicVoiceScreen` and `KantaVisualsScreen` as full-screen Home destinations. Mic & Voice delegates microphone permission/start/stop to `MainActivity.toggleMicrophone()` and `SessionController.startMicrophone/stopMicrophone`; monitoring output selection and teardown use the existing verified-route `MicrophoneMonitor`, gain and the implemented Dry/Room/Hall/Echo modes. The screen polls the real input meter and `SessionController.scoreStatus()`; it never invents coach scores. The previous screen's drawn-but-inert reverb/echo tracks and unsupported Warm/Bright FX labels were removed.

Visuals now renders thumbnails from the ten existing `assets/scenic/*.png` images, selects those existing scenes through `SessionController.scene()`, and routes Camera through the existing CameraStage permission/lifecycle owner. Custom media opens the existing user-selected photo/video import path. Stage post-processing effects are explicitly reported as not implemented rather than exposing nonfunctional controls. New generated KANTA artwork can later replace/add to these thumbnails without changing the scene-selection or camera ownership contracts.

## Mic & Voice and Visuals runtime acceptance — 2026-11-01

Android instrumentation now captures the mounted Mic & Voice and Visuals destinations in addition to Home, Search, Queue and Pitch & Tempo. It selects the existing Bamboo Mist scenic asset and asserts the selection reaches SessionController, then selects the Room vocal-monitor processor mode and asserts the existing MicrophoneMonitor owner receives it. The test does not request microphone/camera permissions or fabricate input/coach readings. Both named screenshots are required by the Android runtime collector and artifact allowlist.

The Android runtime screenshot artifact allowlist is updated in lockstep with the required capture collector: `visuals_screen.png` and `mic_voice_screen.png` are mandatory captures, not optional files.

## Browser remote desktop layout — safe branch in progress

The Android-hosted browser companion now uses a reference-aligned responsive shell: a desktop sidebar, central host-library/search/queue column and right-side Now Playing panel. Narrow viewports collapse to a single-column mobile controller. The existing element IDs, pairing verification, fingerprint-pinned transport, session expiry, command identities, request bounds, host-owned state and protocol allowlist are retained. Unsupported Remotes/Settings, camera, microphone, key/tempo and fullscreen controls remain visibly unavailable; the remote does not fabricate commands or duplicate Android state.

## Shared browser companion asset contract — 2026-11-01

The Android and iOS apps package the same browser companion files from `apps/android/app/src/main/assets/companion/` and `apps/ios/Resources/CompanionRemote/`. Keep `index.html`, `remote.css` and `remote.js` byte-identical. The companion remains a bounded LAN controller with explicit fingerprint verification, host-owned state, pairing/session expiry and no cloud relay. Its responsive desktop layout is presentation-only; it must not add commands outside the existing protocol allowlist or claim host-only features are remotely available. The shared asset parity and real-Chrome browser contract are checked by `tools/verify-companion-browser.mjs`.
