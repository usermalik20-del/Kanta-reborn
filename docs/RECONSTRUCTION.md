# Reconstruction evidence and work ledger

Date: 2026-10-25. Destination: kanta-gelo/Kanta-reborn (canonical repository: [usermalik20-del/Kanta-reborn](https://github.com/usermalik20-del/Kanta-reborn)).

## Current checkpoint

Main remains at [`d01504e`](https://github.com/usermalik20-del/Kanta-reborn/commit/d01504e42b3e00735bb429b05e9ec886149911fe), the merged Search hit-testing repair. Main reconstruction [#40136025964](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025964) and unsigned archive [#40136025858](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025858) passed, but full walkthrough [#40136025937](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025937) failed on the iPad main-menu test. Search/keypad and other iPad cases passed; the remaining failure is the aggregate menu test not recording a clean pass within its XCTest allowance.

PR #71's previous head [`dc60e51`](https://github.com/usermalik20-del/Kanta-reborn/commit/dc60e513c4829226d8bf763c60d5e047e0beb5a4) passed reconstruction [#40146182458](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182458), focused smoke [#40146182470](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182470), and unsigned archive [#40146182453](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182453). Its full walkthrough [#40146182462](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182462) failed on iPad because `testMainMenuButtonsAndScreens` still exceeded its single-test budget, despite exporting all six destination screenshots.

PR #72's workflow-only candidate [`428f14e`](https://github.com/usermalik20-del/Kanta-reborn/commit/428f14ecd8f0e2d6c40b62b4ec2cf3bc3b9219ea) separates iPhone and iPad into independent jobs; reconstruction [#40148389958](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40148389958) passed, and the iPhone job in [walkthrough #40148389843](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40148389843) passed. At the last inspection the iPad job was still running. That workflow-only change does not itself repair the per-test timeout.

The current PR #71 repair batch combines six independently budgeted menu cases with the per-device workflow, synchronizes the runner's required-test manifest and source guard, and records this failure in the ledger. The resulting head still needs exact-SHA hosted validation; no pass is claimed for the new candidate.

The latest main unsigned archive is artifact [#12576634630](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025858/artifacts/12576634630). It is an unsigned personal-signing input, not a signed or directly installable release. The releases endpoint is empty; no signed IPA or published GitHub Release is verified.

## Completed preparation

- Read the original recovery report and architecture/engineering instructions.
- Selected the original stage-ui-66ac7381630c snapshot as the iOS working baseline without modifying its bytes.
- Retained scoring, backgrounds and starter-pack snapshots separately; omitted nested Git metadata and caches from the new repository import.
- Captured ten live HTML pages plus CSS and the private Songbook JavaScript from https://kanta-public.vercel.app. URL, size and SHA-256 are recorded in the website manifest.
- Compared every imported file byte-for-byte by SHA-256 with its source. docs/IMPORT_MANIFEST.json records provenance.

## Evidence boundaries

Recovered iOS source is partial. Across four original snapshots there were 131 distinct Swift paths. The recovery report lists 58 paths absent relative to an older cached tree. Original upstream history and the latest source are unavailable. The selected baseline is not verified buildable. Android source is absent.

Website claims are product evidence, not proof that reconstructed code works. Homepage/platform pages identify Android test build 5c555260a6f9 and unsigned iOS experimental build 0b287c76ccf2. They explicitly leave physical-device, signing, accessibility and security acceptance open. Some pages contain older plaintext-remote wording while Android describes encrypted companion pairing; resolve this by source/protocol tests, not guesswork. Captured download links still point at the old kanta-ph account and are not newly working releases.

## Website-derived requirements

| Area | Required behavior / acceptance boundary |
| --- | --- |
| Playback | Offline-first user-owned MIDI/KAR plus SF2 and MP3/WAV; unsupported catalog-only rows cannot be queued |
| Starter songs | Morning Light, Sing Together, Starlight Echo; synced lyrics, generated tone, remove/restore |
| Karaoke stage | Bold centered timed lyrics, responsive phone/tablet controls, fullscreen, independent TV stage |
| CONTROL | Title/artist/code search, number pad, reserve/cancel, combined play/pause and Next; user-approved voice search |
| Queue | FIFO with singer names, reorder, saved sessions, Favorites and editable playlists |
| Visuals | First-party scenic assets, user photos/videos and optional front-camera preview; no live video recording |
| Soundcard | Safe wired/USB routes, monitoring, FX, dry scoring, calibration and experimental pitch correction |
| Party | Eight first-party SFX, stop-all, independent volume, optional user clips and intermission music |
| Songbook | Private encrypted metadata export and local browser unlock, search, CSV/print; no public catalog |
| Companion | Pairing, authorization, revocation and replay protection; real-device cross-platform security acceptance open |
| Privacy | Local library, opt-in sensors, speech-provider disclosure, offline policies and software notices |
| Future | Vocal Fit, harmony and quizzes are future plans, not recovered shipping functionality |

## Next implementation sequence

1. Publish the recovery import using the now-verified kanta-gelo connection, on a branch without triggering CI.
2. Audit iOS references and missing dependencies; reconcile original snapshot variants and restore missing storage/compression/SFX infrastructure before UI additions.
3. Establish a native Android project and a tested domain model for imports, lyrics, playback identity and the authoritative FIFO controller.
4. Reconstruct native playback and KAR/SF2 handling, then responsive stage/control and persistence. Use original assets only with verified provenance.
5. Define interoperable encrypted Songbook fixtures and a reviewed companion protocol; implement neither by copying unsupported marketing assumptions.
6. Add opt-in microphone/camera/effects with permissions and route safety, legal/privacy screens and focused tests.
7. Run authorized cloud builds; retain failed acceptance items explicitly. Signing and release links require actual new artifacts.

## Checks actually performed

Public destination repository was read successfully. SHA-256 import verification passed. No Swift/Xcode/Android build, GitHub Actions run, merge, deployment or signing occurred. The kanta-gelo connection now reports admin/write access. Publication uses a dedicated recovery branch; the earlier suspension/authentication blocker is resolved.


## Native reconstruction checkpoint — October 15, 2026

Recovered 78 missing iOS paths, including 60 Swift files, individually without overwriting the stage baseline. Reconstructed absent Favorites/playlists, SQLite catalog, CSV/private Songbook, SFX and C compression infrastructure; repaired older LAN pagination and singer interfaces and restored asset-catalog metadata. New focused Swift storage/export tests require Apple/Swift execution.

Android now has a native Java/Gradle foundation for app-private imports, MIDI/KAR/audio playback, timed lyric lines, FIFO/singer queues, saved sets, scenic/fullscreen/TV stage, keypad/voice search and metadata exports. Missing advanced features are explicit in apps/android/README.md. Owner coding/context guide and conservative publishing policy are retained. Official YouTube playback is queued; no ad blocking or DNS workaround is included.

Actual checks: 50 recovered release-tooling tests, four starter-generation tests and resource guard passed. Nine Android runtime Java files compiled against official API 36 references and Java 17 libraries; 46 domain regression checks passed. New Swift files parsed with tree-sitter; five untouched original files exceed that grammar, so this is not a full Swift syntax/build result. C compiled on Linux for its unsupported-codec branch; Apple codec is untested. No Gradle APK/lint, simulator/device launch, CI run, signing, merge or deployment is claimed. Next gate: authorized cloud build validation before release claims.

Actual browser interoperability check also passed: a fresh synthetic Android export decrypted through the captured browser function; wrong-code and tampered-payload checks rejected. Current root workflows now run relevant reconstruction checks automatically for feature/main pushes and pull requests, with the full iOS runtime suite following same-repository iOS pull requests and successful main validation. Website checks and download-packaging contract tests are also automatic on relevant changes. Packaging accepted binaries remains owner-gated. Automatic trigger configuration is not evidence that a run has executed; inspect the final commit's Actions runs before reporting validation.


## PR #51 foreground player lifetime repair — October 18, 2026

PR #51 foreground-lifetime source now retires replaced/backgrounded online players and fences late callbacks using independent preview UUIDs. Domain regressions and Android native instrumentation are wired. Static/Python checks passed; changed-head native compilation/runtime, iOS lifecycle acceptance and audio-focus arbitration remain open. No merge/release claim. See `YOUTUBE_LIFECYCLE_CHECKPOINT.md`.


## PR #51 built-in and browser remote candidate — October 18, 2026

Owner explicitly moved the approved Companion Remote UI and browser implementation into PR #51. Source now adds native role entry, locally served responsive remote assets, private-interface HTTPS with per-session identities and fingerprint trust bootstrap, bounded guest authentication/replay/expiry, native pinned clients and controller-owned local commands. No decoder, persistent media/queue format, native dependency, sensor permission, signing or website release change. Existing KCP1/legacy paths remain separate. The shared HTML assets are platform-owned copies checked for equality; neither native build depends on the other platform.

Local Java core: 524 checks passed, including 45 actual TLS checks. API 36 compilation of PinnedBrowserTransport, 55 tooling tests, Swift layout/presentation/starter guards and captured Songbook interop passed. Local Chrome was blocked by the environment's Unix socket restriction; UI is not accepted from syntax/transport checks. Native builds, nine-case simulator smoke, full eight-case iPhone/iPad walkthrough, real-browser tests, cross-platform/device trust/security/media acceptance remain pending. Unsupported expanded shortcuts are disabled, not represented as completed features. See docs/COMPANION_BROWSER_CHECKPOINT.md (root-relative) for exact trust, expiry, limits, source and acceptance scope. No physical latency/performance result, merge, signing, deployment or release is implied.


## Post-merge iPad Search acceptance follow-up — October 25, 2026

Main is at [`d01504e`](https://github.com/usermalik20-del/Kanta-reborn/commit/d01504e42b3e00735bb429b05e9ec886149911fe), the merge commit for PR #70's display-only hit-testing repair. Post-merge reconstruction [#40136025964](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025964) and unsigned archive [#40136025858](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025858) passed. Full walkthrough [#40136025937](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025937) remains failed on the iPad's main-menu test timeout, not on Search/keypad interactions.

The compact iPad diagnostics [artifact #12577317727](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40136025937/artifacts/12577317727) records Search hit testing and the request/activation/start of the Search presentation. All other required iPad tests passed, including the demo keypad, focused navigation, online-preview lifecycle, and starter playback cases. The runner failure is caused by `testMainMenuButtonsAndScreens` cold-launching the app for six menu destinations and exceeding XCTest's 120-second allowance on iPad.

Repair candidate branch: `fix/ios-menu-walkthrough-timeout-20261025`. The test now uses one app launch and explicitly dismisses each of the same six destinations with `navigation.done`, checking that More returns before proceeding. Testing and CI failure-ledger entries record the exact run, cause, and acceptance gate. The candidate is **not yet verified**; keep release acceptance blocked until reconstruction and the full iPhone/iPad walkthrough pass on the same final candidate SHA.


## Current candidate checkpoint — October 25, 2026

The earlier PR #71 head `dc60e513c4829226d8bf763c60d5e047e0beb5a4` passed reconstruction, focused smoke, and unsigned archive but failed the full iPad walkthrough because the aggregate six-destination menu test did not complete within its XCTest allowance. The candidate has now been reworked into six separately named destination tests and the iPhone/iPad workflow has been split into independent jobs. The runtime manifest and source-contract checker require all six new cases.

This is still a candidate repair. The new exact-head CI results are pending; do not mark it verified until reconstruction, focused smoke, unsigned archive, and both device jobs pass on the same head. No main-branch change, merge, signing, or release is implied.
