# Reconstruction evidence and work ledger

Date: 2026-10-25. Destination: kanta-gelo/Kanta-reborn (canonical repository: [usermalik20-del/Kanta-reborn](https://github.com/usermalik20-del/Kanta-reborn)).

## Current checkpoint — 2026-10-26

Main is at 9acf94f, merging PR #79's fresh website redesign. The merge changed apps/web/index.html and apps/web/style.css only; the native application source remains at the previously accepted KANTA Stage baseline [bf71e5d](https://github.com/usermalik20-del/Kanta-reborn/commit/bf71e5d72a4da627781021df0ae9af572e4b3f49).

PR #79's website validation [run #40212525044](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40212525044) failed because the redesigned homepage no longer supplied the architecture's six canonical primary-navigation routes; the Vercel contract check [run #40212525043](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40212525043) failed at the same validation gate and correctly skipped deployment. The repair candidate then exposed a second regression: PR #79 also removed the existing same-origin analytics script, caught by website validation [run #40217979337](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40217979337) and Vercel contract run [#40217979356](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40217979356). The candidate now restores the canonical navigation and analytics entrypoint; hosted validation is pending. The repair candidate is branch fix/website-navigation-contract-20261026; its hosted acceptance is pending.

The last completed full iPhone/iPad walkthrough on the unchanged native baseline is [run #40178236530](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40178236530); both device jobs passed on bf71e5d. The newer iOS visual redesign PR #80 is not accepted yet: reconstruction [run #40215507919](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40215507919) and unsigned archive [run #40215507907](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40215507907) passed, but focused smoke [run #40215508539](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40215508539) and the full device walkthrough [run #40215507943](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40215507943) failed on both devices. The captured accessibility hierarchy shows the Starter Songs section begins below the initial viewport; LazyVGrid does not instantiate its off-screen starter cells until scrolling, while two tests queried the Morning Light play control before scrolling to that section. The required playback assertions remain intact; the UI tests are being corrected to scroll to the real controls before querying them.

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
