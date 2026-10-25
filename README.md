# KANTA Reborn

Separate native Android and iPhone/iPad karaoke applications, together with their restored public website.

**Testing reconstruction:** Android 0.10.0 includes private photo/video stages, opt-in front-camera preview and optional dry microphone input level/pitch and experimental wired/USB monitoring with local voice effects and separately enabled chromatic pitch correction. Experimental encrypted Android-to-Android companion control is available through explicit private-network pairing; iOS interoperability remains unavailable. Optional dry pitch-following scores require a single identified MIDI melody and device timing calibration. iOS combines recovered original modules with reconstructed infrastructure. Physical-device quality and production app signing remain incomplete.

| Location | Purpose |
| --- | --- |
| `apps/android/` | Native Java/Views app with independent Gradle build |
| `apps/ios/` | Reconciled Swift app and independent Apple build |
| `apps/web/` | Static website and private browser Songbook |
| `recovery/ios/` | Preserved historical source snapshots |
| `docs/evidence/website/2026-10-15/` | Immutable original website evidence |
| `docs/CODEBASE_CONTEXT_PACKET.md` | Actual stack, storage, playback, networking and performance risks |

## Verified downloads

- [Android 0.10.0 bundle](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38886416353/artifacts/12040160062): debug APK, checksum and build record.
- [iPhone/iPad unsigned bundle](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38886416353/artifacts/12038684955): requires separate trusted personal signing; iCloud Save unavailable.
- [Public website](https://kanta-public.vercel.app): platform availability, instructions and private browser Songbook.

GitHub sign-in is required. Bundles last 30 days from October 16, 2026 and preserve original accepted app bytes. Separate CI runs can use different Android debug certificates; uninstalling to change builds removes private app state. Keep original media and explicit exports backed up. These are not store releases.

## Accepted checks

| Scope | Result |
| --- | --- |
| Android [38885716463](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38885716463) | 448 domain assertions, 139 API 35 emulator checks, APK/instrumentation build, lint, native sanitizers, browser interoperability and merged-APK permission/notices gate |
| iOS package [38802025283](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38802025283) | 158 Swift package tests and unsigned simulator compilation |
| iPhone/iPad [38806377805](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38806377805) | All three walkthroughs on each simulator, app/UI compilation and unsigned iPhoneOS archive/IPA |
| Download packaging [38886416353](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38886416353) | Five contracts and original artifact/source/checksum verification |
| Website 0.10 [38886674120](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38886674120) | Ten-page routes/anchors, metadata, CSP, preserved reader, download provenance and JavaScript syntax |

The original browser reader and styles remain unchanged. Synthetic browser unlock, matching/no-match search, CSV export and Lock passed at the 0.5 checkpoint; its CSV text matches the native fixture with the expected spreadsheet BOM. The 0.8 website passed static run 38846304650, all 15 preview page/asset content and CSP checks, and browser download/guide inspection. Android 0.10, its verified download bundles and the updated website are live. All fifteen production page/asset responses match validated source exactly with CSP. See [website 0.10 checkpoint](docs/WEBSITE_010_CHECKPOINT.md).

Android 0.10 companion control is implemented, accepted in run 38885716463 and published with verified packaging run 38886416353. One explicitly paired Android device can search bounded metadata, reserve/cancel distinct turns and send Play/Pause/Next over authenticated encrypted records. Stop/backgrounding revokes the session; it never restarts automatically. This experimental protocol has no forward secrecy or independent security review. Encrypted iPhone/iPad interoperability and official YouTube playback remain unfinished. Physical two-device networking, voice quality/feedback/latency, scoring calibration, camera/external-display acceptance and production signing remain separate open gates.

See [companion checkpoint](docs/ANDROID_COMPANION_CHECKPOINT.md), [architecture](docs/ARCHITECTURE.md), [testing](docs/TESTING.md), [pitch correction checkpoint](docs/ANDROID_PITCH_CORRECTION_CHECKPOINT.md), [scoring checkpoint](docs/ANDROID_SCORING_CHECKPOINT.md), [monitor checkpoint](docs/ANDROID_MONITOR_CHECKPOINT.md), [camera checkpoint](docs/ANDROID_CAMERA_CHECKPOINT.md), [personal-media checkpoint](docs/ANDROID_PERSONAL_MEDIA_CHECKPOINT.md), [iOS checkpoint](docs/IOS_RUNTIME_CHECKPOINT.md), [website checkpoint](docs/WEBSITE_07_CHECKPOINT.md) and [handover](docs/HARDENING_HANDOVER.md). Current checkpoints supersede dated recovery notes.

PR #51 candidate cross-platform feature: [built-in Companion Remote and host-address browser access](docs/COMPANION_REMOTE_ROADMAP.md), with the owner's approved visual reference and pairing requirements. HTTPS host and built-in/browser remote source are implemented with pending native/browser/device acceptance; not a shipping capability.

Architecture and implementation rules: [Engineering architecture contract](docs/ENGINEERING_ARCHITECTURE_CONTRACT.md). This distinguishes existing functionality, pending extension points, and release gates.
