# Reconstruction evidence and work ledger

Date: 2026-10-26. Destination: kanta-gelo/Kanta-reborn (canonical repository: [usermalik20-del/Kanta-reborn](https://github.com/usermalik20-del/Kanta-reborn)).

## Current checkpoint — 2026-10-27 (main `2b5e078`; released source `41c8ddde`)

Main is at [`2b5e078`](https://github.com/usermalik20-del/Kanta-reborn/commit/2b5e078d7911c2b6fe42df359be663eb26fdda2f). The released native source is [`41c8ddde`](https://github.com/usermalik20-del/Kanta-reborn/commit/41c8dddeebe7d096322994e8af09c8c48a76308a), which merged PR [#96 — Implement compact persistent KANTA workspace](https://github.com/usermalik20-del/Kanta-reborn/pull/96). PR #98 removed the unintegrated `KantaWorkspace.swift` scaffold that violated the source-target completeness gate; the compact workspace ships through the real composition boundary instead.

**Compact Workspace — "Always on Stage" (released on both platforms):** the karaoke stage is the persistent base layer and utilities are transient accessories presented above it, so the player is never lost. iOS implements this in `KantaMainPlayerView.swift` (`stage.persistentSurface`, `requestStageSheet`, single-active accessory, `More controls` label); Android implements it in `KantaWorkspace.java` mounted from `MainActivity.buildWindow()` (persistent `StageSurface` layer, `activateAccessory` single-active enforcement, handset bottom sheet / large-screen anchored panel, `Controls` label). Both platforms carry the two accessibility identity fixes: no container identifier that overrides its actionable children, and an unambiguous control-entry label that is not `Settings`.

**Release:** GitHub Release [`v0.1.0-workspace-redesign`](https://github.com/usermalik20-del/Kanta-reborn/releases/tag/v0.1.0-workspace-redesign) at source `41c8ddde`, carrying the Android APK `KANTA-Android-preview-41c8ddde.apk` and the unsigned iOS IPA `KANTA-iOS-personal-unsigned-41c8ddde.ipa`. The iOS IPA is unsigned and still requires trusted personal signing.

**All eight workflows on main `2b5e078` are green:** iOS unsigned artifacts, iPhone visual walkthrough, iPad visual walkthrough, reconstruction validation (iOS), reconstruction validation (Android), verified-downloads packaging, website validation and Vercel deploy. The public manifest at [`/downloads.json`](https://kanta-public.vercel.app/downloads.json) and the [iOS](https://kanta-public.vercel.app/ios-download) and [Android](https://kanta-public.vercel.app/android) pages agree on source `41c8ddde`, the Android artifact `#12701709708`, the iOS artifact `#12698769982` and the passing walkthrough run `#40424816930`. The website security-contract baseline was advanced to `41c8ddde` to match.

**Open gates:** physical-device quality, physical two-device companion networking, voice quality/feedback/latency, scoring calibration, camera/external-display acceptance and production signing remain separate. Companion control is experimental: Android-to-Android only, no forward secrecy, no independent security review, no iOS interoperability. `InAppControlCenterView` still uses the unrestyled `GeloVisualStyle`.

## Superseded checkpoint — 2026-10-26 (main `9ad68d4`; accepted native source `0867134`)

Main is at merge commit [`9ad68d4`](https://github.com/usermalik20-del/Kanta-reborn/commit/9ad68d4f690aefcc5fcae91075475b8ff9eaec15), which merged PR [#94 — refresh public downloads to the accepted native baseline](https://github.com/usermalik20-del/Kanta-reborn/pull/94). The merge updates website download provenance, regression coverage and documentation; it does not change native app source. The accepted native source remains merge commit [`0867134`](https://github.com/usermalik20-del/Kanta-reborn/commit/086713405002f75ab5c7fc49cd815cf2a3469041), which removed temporary iOS diagnostic tap/window probes and updated the presentation-contract source guard.

**Post-merge validation on main `9ad68d4`:**
- Website/security validation [run #40333522127](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40333522127) passed, including the website verifier, Vercel deployment-contract and website security-contract tests, and JavaScript syntax checks.
- Production Vercel deployment [run #40333522045](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40333522045) passed both validation and deployment jobs.
- Reconstruction [run #40333521936](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40333521936) passed iOS and Android. iOS Swift package tests and unsigned simulator compilation passed. Android build, lint, SoundFont sanitizer, companion-browser verification, captured-browser interoperability and emulator runtime checks passed.
- Separate unsigned iOS packaging [run #40333522072](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40333522072) passed and produced artifact [#12656141770](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40333522072/artifacts/12656141770), 25,130,702 bytes, ZIP SHA-256 `d396e322101d82a592c42d84df086f54cd16f9fd34423c5695190cc3a11bd6fa`. It is unsigned and requires trusted personal signing before installation.
- Fresh main reconstruction artifact [#12657235158](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40333521936/artifacts/12657235158) is 28,762,759 bytes, ZIP SHA-256 `035a7d9fe0b48893bf9045822f43e3f00b8d80ca650b6ae99f11b1a8b1df5323`. It is a reconstruction bundle (debug APK, test APK and diagnostics), not a standalone production release.
- Latest full iPhone/iPad walkthrough [run #40320554824](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554824) passed both device jobs on native source `0867134`, with runtime evidence and presentation diagnostics uploaded. PR #94 changed no native source, so no new device walkthrough was required for this website/docs-only merge.

**Live download provenance:** after deployment, the public manifest at [`/downloads.json`](https://kanta-public.vercel.app/downloads.json) and the [iOS](https://kanta-public.vercel.app/ios-download) and [Android](https://kanta-public.vercel.app/android) pages were fetched successfully. They agree on accepted native source `086713405002f75ab5c7fc49cd815cf2a3469041`, artifact IDs, ZIP digests, seven-day retention and the passing walkthrough run. The manifest points to the exact-source accepted Android artifact [#12648928676](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320555135/artifacts/12648928676) and unsigned iOS artifact [#12649740030](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554747/artifacts/12649740030); these are the native-source artifacts, not the newly produced full-repository archive from `9ad68d4`.

**Release and governance:** GitHub Release [`v0.1.0-workspace-redesign`](https://github.com/usermalik20-del/Kanta-reborn/releases/tag/v0.1.0-workspace-redesign) carries the Android APK `KANTA-Android-preview-41c8ddde.apk` and the unsigned iOS IPA `KANTA-iOS-personal-unsigned-41c8ddde.ipa`. The iOS IPA is unsigned and still requires trusted personal signing; the Android APK remains debug-signed. Actions artifacts are ephemeral; Release assets are durable. Physical-device audio/microphone/camera/HDMI/AirPlay acceptance, repository branch protection and production signing remain open. Simulator checks and unsigned archives do not establish signing or physical-device quality.

**Current decision:** no new CI failure is present in the latest main runs. The earlier website navigation, analytics and stale-download regressions have fixes merged and post-merge website/deployment checks pass. Keep signing, physical-device acceptance and public release as separate gates.

## Historical checkpoint — 2026-10-26 (main `fd799b4`; superseded by the current checkpoint above)

The latest main commit is [`fd799b4`](https://github.com/usermalik20-del/Kanta-reborn/commit/fd799b46fab24425d8a18cfa008d9cd9651db47a), which merged PR [#91 — restore canonical homepage navigation and analytics](https://github.com/usermalik20-del/Kanta-reborn/pull/91). The native application baseline remains [`abda7c2`](https://github.com/usermalik20-del/Kanta-reborn/commit/abda7c27251af9f1019aa23831c6770032d754c9); commits after it changed website and engineering records, not native app source.

**Post-merge CI on `fd799b4`:**
- Website/security validation [run #40280952676](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40280952676) passed.
- Vercel workflow [run #40280952739](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40280952739) passed both deployment-contract validation and the production deployment job.
- Reconstruction [run #40280952865](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40280952865) passed both iOS and Android jobs. Android passed SoundFont sanitizer, build/lint/domain regression, real-Chrome companion, browser interoperability and emulator runtime checks; iOS Swift package tests and unsigned simulator compilation passed.

**Production deployment and live-site verification:** Vercel deployment [`dpl_3CSNBDcFtyibuMv5Ba5nUqw7R8Hz`](https://vercel.com/gelo-tech/kanta-public/3CSNBDcFtyibuMv5Ba5nUqw7R8Hz) is `READY`, aliases to [https://kanta-public.vercel.app](https://kanta-public.vercel.app), and records source SHA `fd799b46fab24425d8a18cfa008d9cd9651db47a`. A live content fetch succeeded for the homepage and all six canonical destinations: `/ios-download`, `/android`, `/guide`, `/features`, `/songbook`, and `/support`. No fetch errors were returned.

**Latest verified native acceptance and artifacts on `abda7c2`:**
- Reconstruction [run #40258066829](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066829) passed both platforms; its Android artifact [#12622994480](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066829/artifacts/12622994480) is `android-reconstruction-checks`, 28,722,375 bytes, ZIP SHA-256 `792984910d58dbdb350e70b39136477c98de392f96921fe4f2d0cabcdde460a5`. It contains `app-debug.apk`, the instrumentation-test APK, lint output and runtime diagnostics.
- Full iPhone/iPad walkthrough [run #40258066852](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066852) passed both device jobs and uploaded diagnostics.
- Unsigned iOS device package [run #40258066875](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066875), artifact [#12622974482](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40258066875/artifacts/12622974482), is 25,139,693 bytes with artifact ZIP SHA-256 `f17c0195c679270916febc2f0612ddb5d90d9f2c235097849615fcf26e7b00fb`. It includes the unsigned IPA and its separate IPA checksum; it is not a signed or directly installable production release.

**New release-page defect found in the live site:** `apps/web/downloads.json` was last verified October 23 and linked Android to PR branch source `e3051583` and iOS to a different PR branch source `7980e552`. The iOS page also claimed a later visual walkthrough failed and coverage remained under investigation, despite the later successful main walkthrough on `abda7c2`. The Android page promised a standalone `SHA256.txt` that is not present in its reconstruction artifact. This is stale provenance/content, not a new native CI failure.

**Repair candidate:** branch [`fix/verified-download-provenance-20261026`](https://github.com/usermalik20-del/Kanta-reborn/tree/fix/verified-download-provenance-20261026) updates the manifest and both platform pages to the latest verified native source `abda7c2`, shows artifact ZIP digests and correct contents/retention, links the passing iPhone/iPad walkthrough, and adds regression checks. It also updates the support-page retention claim, architecture contract, reconstruction checkpoint and repair ledger. Hosted validation is pending on the branch.

**Release and governance:** GitHub [Releases](https://github.com/usermalik20-del/Kanta-reborn/releases) remains empty. No signed production IPA or published release is verified. Physical-device audio, microphone latency, camera, HDMI/AirPlay acceptance and repository branch protection remain open.

**Next gate:** pass website/security, Vercel deployment-contract and reconstruction validation on the exact branch head; merge only after they are green, then verify the new production deployment and all canonical routes again. Keep signing and public release separate from simulator acceptance and unsigned artifact availability.

## Earlier checkpoint — 2026-10-25

Main is at [`00d2405`](https://github.com/usermalik20-del/Kanta-reborn/commit/00d2405b5c54d8ae56ee44fed99a98a9ab97d20e), which combines the web logo work with [`770a6dc`](https://github.com/usermalik20-del/Kanta-reborn/commit/770a6dc7c8e51337349a75afddb5fcbf08f3e80b), the newer responsive website redesign. Website validation [#40160483259](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40160483259) passed, and production deployment [#40160483212](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40160483212) passed. Its log confirms that `https://kanta-public.vercel.app` was aliased to `https://kanta-public-6grdxfkcs-gelo-tech.vercel.app`. Main reconstruction [#40160483119](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40160483119) was still running the unsigned simulator build and Android emulator checks at this checkpoint.

Source inspection confirms all ten public pages now share the same six-link primary navigation. The newer design keeps the navigation visible and lets it wrap at narrow widths; it no longer uses the drawer approach from the superseded PR #74 candidate. The adjacent duplicate feature headings and orphaned stylesheet fragment are absent from the current main source. The test-only branch `test/web-responsive-navigation-contract-20261025` adds regression checks for the current design; its hosted validation is pending.

The latest full iPhone/iPad walkthrough is [#40154268944](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40154268944) on iOS source commit [`a543df2`](https://github.com/usermalik20-del/Kanta-reborn/commit/a543df231322ea495eef6e9d7ad8968d17490bc7); both device jobs passed. Commits `d640700`, `770a6dc`, and `00d2405` contain no native iOS source changes, so the web redesign did not trigger a new device walkthrough.

The latest verified main archive remains artifact [#12584009935](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40155986612/artifacts/12584009935) from native source commit `d640700`, SHA-256 `fd3527de16d5567cadb061f4a819160e09f8f86447266ec5be9de833e0487982`. It is an unsigned personal-signing input, not a signed or directly installable release. The GitHub Releases page remains empty; no signed IPA or published release is verified.

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


## Current main acceptance checkpoint — October 25, 2026, 13:57 PHT

The validated application baseline for this documentation reconciliation is [`af178739`](https://github.com/usermalik20-del/Kanta-reborn/commit/af178739f9f457b917d7d0714963ccad22f4bcd2). This PR changes documentation only; after merge, its merge commit will be a docs-only successor and will not change the validated native or website runtime source. The baseline's latest validation is complete:

- Reconstruction (iOS + Android): [run #40165173586](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40165173586) — passed for both jobs.
- Website route/security/navigation validation: [run #40165173693](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40165173693) — passed.
- Production Vercel deployment: [run #40165173539](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40165173539) — passed.
- Unsigned iOS archive: [run #40165173529](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40165173529) — passed; artifact [#12588545812](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40165173529/artifacts/12588545812), 25,076,208 bytes, SHA-256 `857c60aaaf0a2ab6d17aee97f68441a35a645d5b88de6831853fb8ad64d6b042`, expires 2026-11-01 05:25:49 UTC.

The latest completed full device walkthrough is [run #40154268944](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40154268944) on native source commit [`a543df2`](https://github.com/usermalik20-del/Kanta-reborn/commit/a543df231322ea495eef6e9d7ad8968d17490bc7). Both the iPhone and iPad jobs completed successfully. The comparison from `a543df2` to `af178739` contains website pages/assets, website verification tests, and documentation only; no native iOS or Android source changed, so this website-only merge did not require a new native walkthrough.

The responsive website navigation contract is now merged and validated; the previous pending-candidate statements above are historical checkpoints, not current status. The archive is an unsigned personal-signing input only. No signed production IPA or published GitHub Release is verified. Packaging, signing, and public release remain separate acceptance steps.
