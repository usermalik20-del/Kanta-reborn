# KANTA Reborn

Native Android and iOS karaoke applications built around the **Compact Workspace** redesign, together with their public website.

**Current release:** `v0.1.0-workspace-redesign` at SHA `41c8ddde`.

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

## Repository layout

| Location | Purpose |
| --- | --- |
| `apps/android/` | Native Java/Views app with independent Gradle build |
| `apps/ios/` | Reconciled Swift app and independent Apple build |
| `apps/web/` | Static website and private browser Songbook |
| `recovery/ios/` | Preserved historical source snapshots |
| `docs/evidence/website/2026-10-15/` | Immutable original website evidence |
| `docs/CODEBASE_CONTEXT_PACKET.md` | Actual stack, storage, playback, networking and performance risks |

### Compact Workspace implementation

- iOS: `apps/ios/Sources/Features/RootView.swift` instantiates `KantaWorkspace(services:presentationState:)` as the root view body.
- Android: `apps/android/app/src/main/java/ph/kanta/reborn/KantaWorkspace.java` is the root container; `MainActivity.java` sets it as the content view and routes toolbar buttons through `workspace.activateAccessory(...)`.

## CI ownership and release gates

GitHub Actions is partitioned by source ownership. Website changes run website validation; Android changes run Android validation; iOS changes run iOS validation. The lightweight documentation-sync check runs on every pull request and fails when an implementation or workflow change is missing its required owner documentation. Cross-platform iOS archive and iPhone/iPad simulator release evidence for Android-only changes is collected on `main` after merge, not on every Android pull request. Vercel production deployment is separate from pull-request validation and runs only from `main` or an explicit dispatch. See [workflow ownership and testing](docs/TESTING.md). CI status is commit-specific; a green result on an earlier SHA is not evidence for a later commit.

## Open release gates

- `InAppControlCenterView` still uses the unrestyled `GeloVisualStyle` (14 references) — the last unrestyled surface.
- The six diagnostic `NSLog` probes previously present in iOS shipping source are confirmed absent from the current tree.
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
| [GITHUB_PUBLISHING_POLICY.md](docs/GITHUB_PUBLISHING_POLICY.md) | Publishing and release gates |

Platform checkpoints: [Android companion](docs/ANDROID_COMPANION_CHECKPOINT.md), [Android camera](docs/ANDROID_CAMERA_CHECKPOINT.md), [Android monitor](docs/ANDROID_MONITOR_CHECKPOINT.md), [Android pitch correction](docs/ANDROID_PITCH_CORRECTION_CHECKPOINT.md), [Android scoring](docs/ANDROID_SCORING_CHECKPOINT.md), [Android personal media](docs/ANDROID_PERSONAL_MEDIA_CHECKPOINT.md), [Android stage](docs/ANDROID_STAGE_CHECKPOINT.md), [Android microphone](docs/ANDROID_MICROPHONE_CHECKPOINT.md), [companion browser](docs/COMPANION_BROWSER_CHECKPOINT.md), [iOS runtime](docs/IOS_RUNTIME_CHECKPOINT.md), [iOS companion](docs/IOS_COMPANION_CHECKPOINT.md), [YouTube lifecycle](docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md).

Website checkpoints: [website 0.10](docs/WEBSITE_010_CHECKPOINT.md), [website 0.9](docs/WEBSITE_09_CHECKPOINT.md), [website 0.8](docs/WEBSITE_08_CHECKPOINT.md), [website 0.7](docs/WEBSITE_07_CHECKPOINT.md), [website 0.6](docs/WEBSITE_06_CHECKPOINT.md), [website 0.5](docs/WEBSITE_05_CHECKPOINT.md).

## Notes for users

GitHub sign-in is required for Actions artifact downloads. GitHub Release assets are durable and do not expire. Separate CI runs can use different Android debug certificates; uninstalling to change builds removes private app state. Keep original media and explicit exports backed up. These are not store releases.
