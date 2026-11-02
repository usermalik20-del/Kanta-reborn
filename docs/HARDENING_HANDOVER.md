# Recovery import handover — 2026-10-15

## Current engineering handover — 2026-10-27

The latest main commit `b8e2822` adds Android Compact Workspace, but its reconstruction run [#40485807248](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40485807248) failed Android lint at `MainActivity.java:177`: API 36 predictive-back lint rejects the legacy `Activity.onBackPressed()` override. iOS reconstruction passed. Website validation [#40485807191](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40485807191), Vercel deployment [#40485807267](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40485807267) and unsigned iOS archive [#40485807103](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40485807103) passed. The full iPhone/iPad walkthrough [#40485807099](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40485807099) was still running at inspection.

Candidate branch `fix/android-predictive-back-workspace-20261027` replaces the deprecated override with an accessory-scoped AndroidX dispatcher callback and adds source/runtime regression coverage. The candidate is not yet validated or merged. Next: complete the targeted edit, verify Android lint/domain and emulator runtime, then confirm iOS archive and both walkthroughs on the exact candidate SHA. Keep public download provenance pinned to `41c8ddd` until all exact-SHA gates pass.


Prepared platform separation, original iOS baseline, separate snapshots, captured website evidence, provenance manifest and reconstruction ledger. No original source file was edited and no snapshot was blindly overlaid. Android source remains absent. Imported-file SHA-256 comparisons passed; no runtime/build claim. The kanta-gelo connection now has verified admin/write access. Next: publish recovery branch, audit iOS missing references, and build native Android foundation.


## Native reconstruction checkpoint — October 15, 2026

Recovered 78 missing iOS paths, including 60 Swift files, individually without overwriting the stage baseline. Reconstructed absent Favorites/playlists, SQLite catalog, CSV/private Songbook, SFX and C compression infrastructure; repaired older LAN pagination and singer interfaces and restored asset-catalog metadata. New focused Swift storage/export tests require Apple/Swift execution.

Android now has a native Java/Gradle foundation for app-private imports, MIDI/KAR/audio playback, timed lyric lines, FIFO/singer queues, saved sets, scenic/fullscreen/TV stage, keypad/voice search and metadata exports. Missing advanced features are explicit in apps/android/README.md. Owner coding/context guide and conservative publishing policy are retained. Official YouTube playback is queued; no ad blocking or DNS workaround is included.

Actual checks: 50 recovered release-tooling tests, four starter-generation tests and resource guard passed. Nine Android runtime Java files compiled against official API 36 references and Java 17 libraries; 46 domain regression checks passed. New Swift files parsed with tree-sitter; five untouched original files exceed that grammar, so this is not a full Swift syntax/build result. C compiled on Linux for its unsupported-codec branch; Apple codec is untested. No Gradle APK/lint, simulator/device launch, CI run, signing, merge or deployment is claimed. Next gate: authorized cloud build validation before release claims.

Actual browser interoperability check also passed: a fresh synthetic Android export decrypted through the captured browser function; wrong-code and tampered-payload checks rejected. The owner-gated cloud workflow is prepared with pinned actions and no push/synchronize trigger; it has not been run.


## Existing comment review — October 15, 2026

Reviewed active iOS and Android source comments under the shared coding standard. Tightened comments in eleven existing Swift files, corrected the obsolete FTS5 fallback description, and removed an unmeasured 48,000-title responsiveness claim. Android comments already describe concrete ownership and bounds. Required attribution and recovery snapshots remain unchanged. The shared instructions explicitly cover existing and new code.

Validation: non-comment lines are byte-identical in every changed source file; all 50 release-tooling tests and git diff whitespace checks passed. No runtime behavior changed. Platform builds remain unverified; the next build gate is the previously prepared, owner-authorized consolidated CI run.


## Standing CI authorization — October 15, 2026

The owner removed the per-run CI approval and run-count limit. CI and necessary repair reruns are authorized without repeated confirmation. Consolidate coherent changes and investigate failures before retries. Platform quotas, policy denials, signing and release acceptance remain separate boundaries. Root AGENTS.md supersedes historical per-run approval instructions.


## First cloud validation — October 15, 2026

Run 38741919509 passed iOS source guards (50 release-tooling tests, four starter-generator tests and resource guard), then stopped before compilation: macos-15 selected Xcode 16.4 / Swift 6.1, while Package.swift requires Swift 6.2. The workflow now selects the runner-installed Xcode 26.2 explicitly. Android validation is still in progress. No Apple compile or simulator acceptance is claimed.

Android run 38741919509 assembled the debug APK, then lint stopped on MainActivity's literal font-style argument (WrongConstant). Replaced the value with Typeface.BOLD without changing rendering semantics. The run reported six additional lint warnings; these remain for review. Domain regression and browser interoperability steps did not execute after the lint failure. Both corrections require fresh validation on the changed head.


## Second cloud validation — October 15, 2026

Run 38742284862: Android assembleDebug, lintDebug, domain regressions, captured browser interoperability and artifact upload passed. iOS source guards passed using Xcode 26.2, then Swift test compilation found the absent RemoteCommandReplayCache referenced by recovered tests. Reconstructed the bounded cache, wired it into the existing connection after pairing validation, and added saturation coverage so executing commands cannot be evicted. Cached failures replay without re-execution; successful retries return current state. This protection is connection-scoped; reconnect/durable replay and transport encryption remain open. Removed a stale package exclude for absent Core/README.md. Fresh Swift execution and simulator build are pending.


## Third cloud validation repair — October 15, 2026

Run 38743316223 passed Android again. iOS test compilation reached RoomScoreReliabilityTests and found completedScore missing from the active LiveKaraokeScoringSession. The preserved scoring-023d470e1608 implementation differs only in completed versus elapsed target selection and clearing observations after latency changes; reconciled those exact changes and connected final Host scoring to the reliability gate. Existing room-score tests cover unheard song endings and latency invalidation. Apple test execution and simulator build require the next cloud run; no green claim is carried forward.


## Full test/source reconciliation — October 15, 2026

Following the owner request, audited the complete active test layout (36 Swift files, seven SwiftPM test targets plus Xcode UI suite) and explicit local type-qualified references. The remaining definite missing static APIs were IndexedContainerCatalog.indexRelativePath and catalogOnlySongs. Added bounded metadata-only decoding and a fixture asserting standalone/unsupported titles remain non-playable; stable IDs align with validated entries. No test assertions were removed.

Added test-layout preflight, documented all suite boundaries in TESTING.md, and separated cloud test compilation from execution. The app build now attempts independently after package failure; logs upload even on failure, exposing multiple integration errors in one run. Layout check and 50 release-tooling tests passed locally. Fourth run 38744293195 passed Android and reached the missing catalog APIs in iOS; fresh cloud compilation/execution remains required. This audit cannot guarantee absence of runtime or future integration defects.


## Expanded validation diagnostics — October 15, 2026

Run 38744977603 compiled all Swift package/test targets successfully, then test execution terminated with signal 5 after starting concurrent native audio and domain tests; the log supplied no stack or failed assertion. The independent simulator build exposed two scoringMessage references introduced during reconciliation; removed these unsupported status assignments while retaining the completed-score reliability gate. The next run executes every package test serially with Swift backtraces enabled, uploads package logs before the app build, and retains recent package-process IPS reports. Crash causality is not yet established; no tests were skipped and no runtime pass is claimed.


## Serial scoring diagnosis — October 15, 2026

Run 38746167309 compiled all package/test targets. Early package artifact showed native audio cases passing, duplicate burst/boundary score assertions failing, then signal 5 at scorerRejectsInvalidPitchAndLatencyOverflow. The active scorer subtracted Int64.min without overflow handling. Reconciled the full scorer hardening from scoring-023d470e1608 while preserving the newer finite MIDI-note conversion. Also made expected-slot calculation integer-safe for Int64.max durations and rejected invalid mutable target windows; added focused extreme-value coverage. Existing assertions are retained. Removed SWIFT_BACKTRACE injection after the runner reported privileged-executable backtraces unsupported. Apple revalidation is pending.

Run 38746167309 also completed the full unsigned Release iOS Simulator app build successfully; no package-process IPS report was produced. The next run validates the scorer repair on a changed head. Native audio tests, including non-silent original starter SF2 rendering, passed in the serial run before the scoring crash.


## Complete package execution — October 15, 2026

Run 38747387142 executed all 157 Swift package tests without the prior crash: 156 passed, one live-score assertion failed. Half-open note windows correctly reject note-ending samples, but the live window clipped its end to the latest sample timestamp and excluded that sample. The live cutoff now includes that tick with saturation at Int64.max while still clipping to the source note end. Added live/source-boundary regression coverage; completed scoring remains unchanged. No assertions were removed. Android passed again; simulator validation on this run is still in progress.


## Validated reconstruction test checkpoint — October 15, 2026

Cloud run 38748040876 passed on source head 56c9fedbb70b79ebc350e4678d5620554d53c983: all 158 Swift package tests, 50 release-tooling tests, four starter-generator tests, starter/layout guards, unsigned Release iOS Simulator build, Android APK/lint/46 domain regressions and browser interoperability. No assertions or suites were disabled. The test-layout guard and TESTING.md contracts are now required by root engineering instructions.

This checkpoint updates documentation only after the validated source commit; application source remains byte-identical to that passing head. UI automation, physical-device audio/camera/performance, secure companion transport, signing and release/deployment remain unverified. Advanced Android parity and official YouTube playback remain queued separately. PR #1 subsequently merged at 1720a54f31123f2ef59d41383dc41ef06c5855d5.


## Native Android media follow-up — October 15, 2026

`feature/native-media-parity` replaces system MIDI playback with bounded, revision-pinned MIT TinySoundFont synthesis while preserving the one controller/FIFO owner. Separate preparation and folder workers keep Next independent of import IO. Start acknowledgement commits reservations; cancellation/error retain them. SAF folder/SF2 import is transactional, source bytes remain unchanged, and failed metadata writes roll back new copies. The output-frame clock preserves audible lyric timing across tempo changes. Pitch/tempo/volume controls apply to both backends subject to platform support.

Local validation: 167 domain assertions, browser interoperability, runtime/instrumentation Java compilation against API 36 and native ASan/UBSan checks passed, including an audible generated tone, octave transposition, targeted invalid references, truncation and 2,000 mutations. Local LeakSanitizer is restricted by process inspection; allocator-balance assertions pass. The cloud workflow adds NDK builds and actual Android emulator instrumentation with screenshot/log artifacts. Changed-head cloud execution is pending. This does not establish physical audio acceptance or remaining advanced parity.


Cloud run 38801573776 passed native sanitizer checks, all 167 domain assertions, browser interoperability, NDK APK and instrumentation assembly, and lint. The emulator setup stopped before app launch because `adb` was absent from PATH. The workflow now installs platform-tools explicitly, exports its path and collects logcat independently after failure. No emulator pass is claimed from that attempt. Native review also added a pre-loader rejection for negative coarse offsets and invalid tuning/root-key ranges; vendor bytes remain unchanged. Fresh changed-head execution is required.


## iOS online runtime/artifact follow-up — October 16, 2026

Prepared a separate owner-labeled iPhone/iPad runtime workflow, actual simulator recordings/screenshots and an unsigned device IPA packaging gate. The Xcode UI suite now isolates interactions on MainActor, selects the Morning Light starter by stable identity and waits for actual current-song/playing state before pause/resume. This addresses asynchronous preparation rather than weakening the playback assertion. Device archive uses the existing personal-signing flag and omits iCloud entitlements, retaining all original Apple bundle identities. No signing material is requested or exposed. These changes require hosted UI compilation and execution; no runtime/IPA pass is claimed yet.


## Android stage and party follow-up — October 16, 2026

Added source-timed KAR fragment highlighting (including inline markers and unmarked line timing), cached native text layout, persistent lyric sizing and optional first-party perspective lights. Eight synthesized WAV pads, independent volumes, bounded four-voice SoundPool playback, stop-all and custom short-clip assignment now project the single SessionController. Intermission selects existing MP3/WAV media, is opt-in and stops for active karaoke, focus loss or background; no reservation queue is duplicated. Per-slot generations fence async metadata/load results and custom selection persists after decoder acknowledgement. Party generation follows main-library readiness rather than blocking it.

Local checks: 197 domain assertions, deterministic/non-silent bounded WAV checks via Java's independent audio decoder, source lyric-boundary cases and API 36 runtime/instrumentation compilation pass. Emulator instrumentation now includes party loading, voice bounds, stop-all and unchanged reservations. Hosted validation remains pending; custom clip/loop physical audio, camera/video and microphone parity remain open.

Expanded Android runtime coverage with a signature-protected test-only provider: nested/cyclic SAF scans, mixed-validity imports, staged cancellation and durable reopen, byte identity, controller audio import, custom pad acknowledgement/reset and idle/interrupted intermission. Runtime and provider source compile against API 36; actual emulator execution remains pending. Debug-only fixture permission does not enter the release manifest.

Custom pad review added a decoded PCM budget below Android SoundPool's documented 1 MiB truncation limit; metadata-only rate/channel inspection runs off-main and rejects oversized clips. The original media stays untouched. MediaMetadataRetriever uses release() for API 26, and intermission observation projects acknowledged start state rather than querying a preparing MediaPlayer. Local core checks now total 201.


Run 38802025283 passed iOS package tests and simulator compilation again, plus Android NDK/APK, lint, native/domain/browser checks. Android emulator never launched: its log reports Unknown AVD name because avdmanager and emulator resolved different default AVD homes. The runtime helper now shares an explicit isolated ANDROID_AVD_HOME across both tools, checks emulator process liveness, bounds every device call/startup, and retains logs/screenshots with cleanup. Android-only owner labeling permits focused infrastructure repair runs without rebuilding unchanged iOS source. No app runtime acceptance is claimed until the repaired runner executes it.


Run 38802630169 compiled the complete app/UI target. All three iPhone tests passed, including real Morning Light playback, transport and menus. iPad menu walkthrough passed; landscape keypad hittability failed, leaving orientation state for the next starter case and an invalid popover hit target. Captures show a stale window/orientation geometry. UI tests now restore portrait before/after each case, await a stable visible control frame/window orientation, and retain a capture before landscape hit assertions. Assertions are preserved. The workflow now uploads iPhone evidence before independent iPad execution. Device IPA remains blocked until both pass; no UI or signing success is carried onto the changed head.


## Validated native-media milestone — October 16, 2026

Run 38805798316 passed on `87e5aa21dc4dc3d2e2e043e8c1cbdd1beafc4ce5`: Android NDK/APK/instrumentation assembly, lint, 167 domain assertions, browser interoperability, native sanitizer tests (439.3 Hz tone / 880.3 Hz transposed, truncation, invalid references and 2,000 mutations) and 13 actual API 35 emulator checks. The explicit AVD-home repair enabled real JNI/AudioTrack playback, cancellation/FIFO, controls, pause/resume, failed preparation and native activity rendering. iOS source is unchanged from passing run 38802025283 (158 package tests and unsigned simulator app build). Only documentation changes follow this validated source head. PR #3 iPad runtime repair and PR #4 stage/party additions remain separately unverified. No signing, physical-device or deployment claim is made.


The native media baseline is merged as PR #2 (`52e1cf4d5fbc3e8666e87f41631ef8718d3b3c74`). Stage/party source now includes the repaired, bounded emulator helper and the independent iOS runtime diagnostics branch. The 201 local domain assertions and updated runtime/instrumentation source compile; the stage/party cloud run still must pass before merge. This consolidation retains the earlier accepted native source and the pending iPad repair rather than overlaying snapshots.


Latest iOS checkpoint: run 38806377805 passed on 3c12a1fb962ac956250a8f238c01eace13f560d2, including all three UI walkthrough tests on both iPhone and iPad, app/UI compilation and unsigned personal-signing device archive/IPA packaging. No assertions were removed. See IOS_RUNTIME_CHECKPOINT.md for retained artifact identities and acceptance boundaries. Earlier pending statements above describe their dated attempts; signing, installation and physical-device acceptance remain open. Only documentation follows that validated source.


Validated Android stage/party checkpoint: source b3a6cd7c795e2aaea4e48254ede290b4f616d671 passed run 38807243721 with 201 domain assertions, 28 actual API 35 emulator checks, NDK APK/instrumentation assembly, lint, native sanitizer checks and browser interoperability. Covered nested/cyclic SAF traversal, partial failure, cancellation/reopen, byte identity, custom pad decode/reset, bounded voices, stop-all, intermission gating and real karaoke playback. Only Markdown reconciliation follows that source. Personal photo/video, camera, microphone, companion and official YouTube work remain separate; no physical-device or signed-release claim is made.

## Website and clean download checkpoint — October 15, 2026 UTC

PR #5 merged verified download packaging; PR #6 merged the restored static website. Android and iOS remain separate native projects, with `apps/web` as a third independent directory. Main after these merges is `12f59c9e62d7abcea5743bec7d2fac7441a1cf0a`.

Run 38812761100 passed five packaging tests, checked original successful run/artifact identities and retained original app bytes. Separate 30-day Android and unsigned iOS bundles include SHA-256 and build records, require GitHub sign-in, and exclude the Android instrumentation APK. The unsigned personal IPA still requires signing and has no iCloud Save.

Website run 38813213292 passed static checks. Production deployment `dpl_2ar7Pidc2z7m29vGRDJGGRdjkaJb` is READY on https://kanta-public.vercel.app. All ten public pages and four public assets match validated source over HTTP. The private browser reader remains unchanged and CSP still blocks network connections. Visual inspection was attempted but blocked by `environment_offline`.

The local branch `feature/android-stage-media` contains uncommitted private photo/video stage, lifecycle, source-byte preservation, stable UI subtree/navigation and fixture/runtime-test work. It compiled against API 36 before the environment disconnected, but has not been published or runtime validated. Preserve that draft when workspace access returns; do not reset the checkout or claim the feature shipped.

Next implementation work: resume and publish that bounded media draft, run build/lint/domain/native/browser/emulator checks, then address optional camera, microphone and companion features individually. Keep official YouTube integration under its documented playback plan. Physical-device and Apple-signing tasks remain outside completed online acceptance. Necessary CI/reruns are not capped by the user.

## Resumed Android personal-media work — October 16, 2026

The workspace is online again. The local draft was checkpointed before review. A truncated local deepLagoon PNG was preserved separately and restored from the tracked original bytes; that restoration does not change the published artwork.

The Android 0.4.0 batch adds private photo/video stage imports, persisted selection, bounded validation and generation-fenced muted video lifecycle. MainActivity retains its stage/transport/navigation subtree and fits all five tabs on narrow phones. The original app mark is reused without modification. A 1,917-byte original solid-color MP4 replaces unvalidated runtime codec fixture generation. Existing emulator diagnostics are no longer overwritten by a post-shutdown ADB fallback.

Local API 36 source/instrumentation compilation, 201 domain checks, browser interoperability and test-layout preflight passed. Cloud run 38825170997 passed APK/lint/native/browser checks, 201 domain assertions and 47 emulator checks; no release, physical audio/camera acceptance or signed distribution is claimed. Existing public downloads continue pointing to the earlier accepted builds until this source passes its checks.

## Camera source prepared — October 16, 2026

Android photo/video source passed run 38825170997 and is merged in PR #8. Refreshed clean download packaging passed run 38825727083 and merged in PR #9, with Android artifact 12015246566 and unsigned iOS artifact 12014309230. Public website links have not yet been switched to these bundles.

A separate camera batch adds lifecycle-bound, default-off front preview, explicit permission handling, fullscreen transfer, background shutoff and no recording. AndroidX Activity/CameraX versions are pinned; embedded runtime licenses/notices are copied into an offline in-app asset. The merged-APK permission gate permits CAMERA as its only Android platform permission. No microphone, networking, queue/audio-owner or signing change is included.

Host Gradle could not download through the Java wrapper's network path; this is not a cloud-build failure. Test-layout preflight and source formatting passed. Hosted run 38826875086 passed compilation/lint/dependency and permission/notices checks, native sanitizers, 201 domain assertions, browser interoperability and 60 actual emulator checks. The retained capture shows original KAR playback/lyrics over the emulated front-camera feed; the audible clock continued advancing during preview. Website desktop inspection now rendered the homepage and Android download page correctly, followed navigation and confirmed the Songbook missing-file alert; mobile and successful interactive unlock remain separate.

## Accepted camera downloads and website refresh — October 16, 2026

PR #10 merged the 60-check camera source; PR #11 merged verified 0.5.0 packaging. Run 38837064251 passed, yielding Android bundle 12019382511, unsigned iOS bundle 12019507393 and provenance manifest 12019980230, expiring November 15 UTC. Original app bytes are unchanged.

The website refresh points to those actual bundles and accurately describes personal media, opt-in camera, no microphone/network permission, and retained original/runtime software notices. It also repairs footer iPhone links and reconciles the current README/context packet. Static run 38837349896 passed. All 15 public preview pages/assets matched submitted source and CSP. Desktop Android download inspection and synthetic browser Songbook unlock/search/CSV/Lock passed; export text matches the native fixture with its expected UTF-8 BOM. Native source and original Songbook reader are unchanged. Production deployment follows this verified preview.

## Production 0.5 website verified

Deployment dpl_2QZNigGA5F8A4rpmpupxyqrzZXw7 is READY on kanta-public.vercel.app, source c5ae03eae522f9b1262ec44a7e23b37c809f35a2. All ten pages and five public assets match validated source with CSP intact. Current website downloads point to accepted Android 0.5.0 and unchanged unsigned iOS packages; sign-in and expiry are disclosed. Microphone monitoring/scoring/DSP, companion networking and official YouTube remain the next implementation work; physical-device/signing acceptance is still open.

## Android microphone input batch (in progress)

Based on accepted main a4bd5dd. Adds foreground opt-in AudioRecord input, dry input level/pitch, request fencing and asynchronous bounded cleanup. No speaker output, recording, effects, score, internet permission or Apple source change. Version 0.6.0 is not yet the site's accepted download. Local core checks and API compilation are being verified; changed-head hosted APK/lint/runtime acceptance is required before merge/download changes. All existing tests remain enabled; new runtime cases cover permission/default-off, real input frames, stop/release, Home/foreground, stale grant, rapid replacement and MIDI/camera coexistence.

Accepted microphone input source c961cb3bbe76ef77bea8052529cb93a552d15a53 passed run 38839639544: APK/instrumentation build, lint, native sanitizers, 233 domain checks, browser interoperability, permission/notices gate and 78 actual API 35 emulator checks. No old assertions were removed. Only Markdown checkpoint reconciliation follows. No physical microphone acceptance, monitored output, effects or completed-song score is claimed. See ANDROID_MICROPHONE_CHECKPOINT.md.

## Android 0.6 downloads and production website verified

PR #14 merged source c961cb3b after 233 domain and 78 API 35 emulator checks. Packaging run 38840276706 passed with unchanged accepted APK/unsigned IPA bytes, Android artifact 12021485187 and iOS artifact 12020562170 (30 days; sign-in required). PR #16 refreshed platform pages, feature/guide/privacy/support text and manifest; static run 38840496599 passed. Production dpl_8bDFSvary6YedbRCvZMS4S6kbEZq is READY at kanta-public.vercel.app, source f2d4141498339b60cc0b26a4496f7c0b752aeb6d. All 15 public pages/assets are byte-exact with CSP, and real browser download identity/navigation was checked. See WEBSITE_06_CHECKPOINT.md. Monitoring/effects/scoring and companion/YouTube remain follow-ups.

## Android monitor/FX candidate

Based on d4f9d84, adds separate opt-in monitored output with actual-route checks, terminal disconnect/reconnect handling, bounded clock-drift interpolation and monitor-only Dry/Room/Hall/Echo. Existing dry input and authoritative karaoke queue remain separate. No new dependency or Android permission; no Apple, media-byte or website change. Local 268 domain assertions and service/controller API compilation pass. Full changed-head build/lint/native/browser/emulator acceptance is pending. Emulation can reject unsafe speaker routes and verify unchanged dry capture; it cannot establish physical wired/USB output quality, feedback or latency. The website keeps accepted 0.6.0 until a new accepted package exists.

Accepted monitor source 1ddee2416b6efe56562f5826ad479a6373ecf86c passed run 38842165486 with 268 domain and 90 actual API 35 emulator checks, APK/instrumentation build, lint, native sanitizers, browser interoperability and permission/notices gates. No old assertions were removed. The emulator verifies unsafe-output rejection, not physical wired/USB audibility or safety. Only Markdown reconciliation follows; see ANDROID_MONITOR_CHECKPOINT.md.

## Android 0.7 package and website preview accepted

Packaging run 38842836405 passed five contracts and original artifact/source/checksum checks. Android bundle 12021832205 and unsigned iOS bundle 12022495243 preserve accepted app bytes and last 30 days with sign-in required. Web source f9286778 passed static run 38843086888; READY preview dpl_5fpZgodmFawvWrAKgyJPicvcMBqD passed all 15 page/asset content+CSP checks. Browser inspection confirmed current download identity, 268/90 counts and experimental monitor boundaries. Original private reader/styles/notices remain unchanged. Production publication follows verification; see WEBSITE_07_CHECKPOINT.md.

## Production 0.7 website verified

Deployment dpl_EXhw2bKNmFpj2U1nskumYDKZ8oYU is READY on https://kanta-public.vercel.app, source 79e7816af7421e951868b543c53caa78b9caa909. All ten public pages and five assets returned 200 and matched validated source exactly with CSP intact. Actual browser navigation confirmed the Android 0.7 download identity/counts and current voice setup guide. Website 0.7 and its accepted APK are live; iOS app bytes are unchanged and still require personal signing. Physical monitoring and calibrated scoring, pitch correction, secure companion and official YouTube remain open.

## Android scoring candidate

Based on main 023dfff, version 0.8.0 adds default-off dry pitch-following scores for a single conservatively identified monophonic MIDI target. Existing reservation UUIDs own completion; all target windows remain in coverage, with bounded incremental observations and reliability gating. Timing/key/tempo changes reset observations; actual audible frame acknowledgement fences buffered controls and post-source-end input is rejected. No MP3/WAV target, recording, new permission/dependency or Apple source change. The UI offers a timing offset and labels physical timing/backing-music limits. Local 328 domain assertions, browser interoperability and complete API 36 production/instrumentation compilation pass; changed-head hosted build/lint/runtime remains pending. Physical scoring accuracy, monitored output, latency and signing remain open. Website/download stays on accepted 0.7.0.


Accepted Android 0.8 source `57dc0611cb54435802a4bd7d226e00983dd3c8f6` passed run [38845590252](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38845590252): APK/instrumentation assembly, lint, native sanitizers, all 328 domain assertions, browser interoperability, permission/notices gate and 110 actual API 35 emulator checks. PR #21 merged as `5e6d5dc09f4bbfed25566d6dc17dfc20fe0cf25b`. No prior assertions were removed. Physical timing/scoring accuracy remains unverified; verified download packaging is separate. See ANDROID_SCORING_CHECKPOINT.md.


## Android 0.8 packages and website preview accepted

Scoring PR #21 and packaging PR #22 are merged. Run 38846121392 passed all five contracts and original source/run/artifact/checksum verification. Android bundle 12022594247 preserves the accepted 26,956,746-byte APK; unsigned iOS bundle 12023730003 preserves unchanged accepted bytes. Manifest artifact 12022649276 was downloaded and its archive digest verified. Sign-in and November 15 UTC expiry remain disclosed.

Website source 941e95002b012024c3b385ca1c420469de4ec5f3 passed static run 38846304650. READY preview dpl_6jPfDy2dTb6bN6h75zsJefxGoL3z passed all 15 exact page/asset and CSP checks; actual browser inspection confirmed Android 0.8, source/download identity, 328/110 counts and scoring setup navigation. Public text distinguishes dry input, separate scoring/monitor opt-ins, supported references, insufficient-input rejection, natural-completion estimates and physical calibration/backing-music limits. Original private reader/styles/notices are unchanged. Production publication follows this accepted preview; see WEBSITE_08_CHECKPOINT.md. Remaining software: pitch correction, secure companion and official YouTube. Physical scoring/audio/camera/external-display and signing gates remain open.


## Production 0.8 website verified

Deployment `dpl_HoVLX9W368oH6fgath4pbH6LHSDb` is READY on https://kanta-public.vercel.app, source `75ddb7b767bcbc2484c4732c7d13a37ac3fc620e`. All ten pages and five public assets returned 200, matched validated source byte-for-byte and retained CSP. Actual browser inspection confirmed Android 0.8 source 57dc0611, the accepted 328/110 counts and download bundle 12022594247 from packaging run 38846121392. Both platform pages use current accepted packages; iOS bytes remain unchanged and unsigned. The original private reader/styles and notices are unchanged.

Scoring implementation, accepted packaging and website publication are complete. Remaining software work is Android pitch correction, secure companion networking and official visible YouTube playback under YOUTUBE_PLAYBACK_PLAN.md. Physical scoring calibration, singer/backing-music separation, wired/USB audio quality/feedback/latency, camera/external-display acceptance and production signing remain open. This checkpoint changes Markdown only after the verified deployment; it introduces no new app/site runtime, workflow or dependency change.


## Android 0.9 pitch correction candidate

Starting from main d3fc2075. Adds a separate experimental chromatic-correction opt-in after actual wired/USB route verification. A bounded first-party two-tap processor precedes monitor effects and never changes dry analysis/scoring, imported bytes or authoritative playback/FIFO. A new monitor run starts off; stopping input/monitoring, route loss or backgrounding clears activation. Unreliable/stale/silent estimates relax to unshifted delayed input. No new permission/dependency, networking, recording or Apple change.

Local 364 domain assertions pass, including detector-to-processor tones with independent spectral verification, finite/limited output, smoothing/discontinuity checks, uncertainty/timing bounds, hysteresis and history reset. Complete API 36 source/instrumentation compilation, browser interoperability and test-layout guard pass. Six new emulator cases cover default-off, disabled control before verified monitoring, rejected activation with missing/speaker routes, unchanged dry capture and lifecycle non-restart. Changed-head hosted acceptance remains pending; the site keeps accepted 0.8. Physical voice timbre/artifacts, feedback and total latency remain unverified. Secure companion and official YouTube remain queued.


## Hosted Android 0.9 acceptance

Source `4f48e051dcd815e878205eda43bf9331591e279d` passed run [38881735518](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38881735518): all 364 domain assertions and 116 actual API 35 emulator checks, APK/instrumentation assembly, lint, native ASan/UBSan checks, browser interoperability and merged permission/offline-notices checks. PR #25 merged as `2851a0b81f43698a99b036b8dd28aa25c0d495eb`. Existing assertions remain enabled. The accepted output is artifact `12038226827`; checksum-verified packaging must preserve these APK bytes before changing public downloads. Physical corrected voice quality, artifacts, feedback and total latency remain unverified. Secure companion and official YouTube remain queued. This entry supersedes candidate hosted-pending statements above.


## Verified Android 0.9 download packaging

Run 38882359866 verified source/run/artifact identity, original application bytes and archive digests. Android bundle 12038451005 preserves source 4f48e051, APK SHA-256 85952b6db76dd2c418013a78895d6161cfa9310b14f57fe8b75095b4a2db1474 (26,957,042 bytes). iOS bundle 12037574662 preserves the unchanged unsigned IPA. Downloaded manifest artifact 12038027446 matched its archive digest. Website pages/manifest now select these packages with separate experimental-correction instructions; hosted website validation and production publication are pending. See WEBSITE_09_CHECKPOINT.md.


## Production Android 0.9 verified

Website source `95855ff18e9c1758e53a4315bf460b9f29ee876b` passed static run `38882633589` and READY preview `dpl_ESS6EucV9dVBs8x7y9tKzjGSqxK8`. All 15 preview pages/assets matched source with CSP, and real browser inspection confirmed Android 0.9, the 364/116 checks, source/download identity and setup navigation. PR #27 merged as `4c8e8066dc9f480de1922410628990eb788df4d5`. Production deployment `dpl_4xB8tKDdT61KhkSMnTRLx21HS4de` is READY on https://kanta-public.vercel.app; all 15 production pages/assets match validated bytes with CSP. The browser independently confirmed the public Android 0.9 page and bundle `12038451005` from packaging run `38882359866`. Original private reader/styles/notices and unsigned iOS application bytes are unchanged.

Post-deploy verification caught a competing production deployment returning 404. The public alias was restored to the verified deployment, and the persistent Vercel project settings now explicitly use root `apps/web`, output `.`, no framework, and empty install/build commands. The corrected public alias and all 15 responses were verified after restoration. Deployment READY alone is insufficient: check the public alias, content and security headers before declaring completion.

Pitch correction implementation, acceptance, verified packaging and website publication are complete. The next software task is secure companion control, followed by official YouTube playback. Physical voice quality/timbre/artifacts/feedback/latency, scoring calibration, camera/external-display acceptance and iOS production signing remain open. This checkpoint changes Markdown only and requires no additional native build.


## Android companion candidate

Android 0.10 adds foreground-only, explicit private-IPv4 companion hosting/client control through the existing SessionController/FIFO. The actual Java 17/Views, storage, imports, audio engines, CameraX and platform separation remain unchanged. INTERNET is added for private Java sockets; no new dependency, cloud service, discovery broadcast or iOS protocol change is introduced. KCP1 uses a 128-bit random expiring pairing secret, mutually authenticated HMAC handshake, HKDF direction keys and ordered AES-GCM records. Commands are limited to bounded search/queue metadata, reserve/cancel and Play/Pause/Next; sensors and file access are excluded. A single worker owns socket work; main-Looper mutations check active foreground run and cancellation. Background/Stop revokes immediately, with no automatic reconnect. See ANDROID_COMPANION_CHECKPOINT.md for exact framing, bounds, replay scope, no-forward-secrecy limitation and acceptance.

Candidate core compilation and all 443 domain assertions passed (79 new companion checks); browser interoperability and Swift layout guards pass. Hosted build/lint and actual emulator socket/queue/lifecycle checks remain pending. Physical two-device networking, iOS compatibility and independent security review remain open. Public downloads remain accepted 0.9 until separately verified packaging/publication.


## Hosted Android 0.10 acceptance

Final source `d777334eec73996116fdf2c01d97ac73a3a53958` passed run `38885716463`: all 448 domain assertions (84 companion cases), 139 actual API 35 emulator checks, APK/instrumentation assembly, lint, native ASan/UBSan, captured browser interoperability and the CAMERA/RECORD_AUDIO/INTERNET permission/notices gate. Initial source also passed the complete suite; the final source adds bounded peer JSON nesting and strict UTF-8 checks. An independent Python cryptography client verified the JCA handshake, HKDF, framing and both direction keys. PR #28 merged as `51b57e0052c8f01d08fac04f12cccae06b189ad5`. Accepted original artifact is `12039807731`. No existing assertion was disabled. Physical two-device networking, iOS interoperability, forward secrecy and independent security review remain open; Android acceptance does not establish those properties. Public downloads remain verified 0.9 until checksum-verified packaging and website publication.


## Production Android 0.10 verified

Website source `f2bd2b517f0a9059084b2604cddcdc9b2cf1e66c` passed hosted run `38886674120`; preview `dpl_2bG6QWnkFVbkT7YLVuwyxBgLAr13` is READY. All fifteen preview routes/assets returned 200 and retained CSP. Fourteen responses matched source exactly; the homepage matched source plus Vercel's appended preview feedback-script tag, which is blocked by the unchanged CSP. Browser navigation confirmed Android 0.10 source d777334e, the 448/139 counts, accepted download identity and companion setup guide.

PR #30 merged as `0a11058b2d70182b2956d57b2f320edbe19762ee`. Production deployment `dpl_H9fF42EU9QobF613s7Y4d7Rvfkbt` is READY on https://kanta-public.vercel.app, using explicit `apps/web` root, `.` output and empty install/build commands. All fifteen public responses returned 200, matched validated source exactly and retained CSP; production has no appended feedback tag. Browser inspection of canonical `/android` independently confirmed version 0.10.0, source d777334e, run 38885716463 and Android bundle 12040160062. Both platform download pages use successful packaging run 38886416353; iOS bundle 12038684955 preserves the unchanged unsigned IPA. The 26,974,938-byte Android APK SHA-256 is 7743d1994c500b472f86829ea041307303f0f908d0e5505b788c35ca76026d3d. The downloaded packaging manifest archive digest was checked independently.

Android companion implementation, automated acceptance, original-byte packaging and website publication are complete. This is an experimental Android-to-Android milestone, not cross-platform or independently audited security acceptance. The recovered iOS plaintext protocol remains incompatible; encrypted iOS interoperability is the next companion milestone, followed by the queued official YouTube integration. Physical two-device networking, audio quality/feedback/latency, scoring calibration, camera/external displays and production app signing remain open. Final handover updates are Markdown only and introduce no runtime, workflow, dependency or asset change.


## Android online preview follows authoritative playback publication — October 18, 2026

Baseline: PR #51 `5c1b0f4`, whose reconstruction and focused iPhone smoke checks passed. The online/local overlap guard previously ran only after primary Play/Next button callbacks. Encrypted companion commands bypass those callbacks, and backend start acknowledgement is asynchronous, so those paths could leave a visible online player alive after local playback started.

MainActivity now reconciles the preview at the start of the existing SessionController listener's render callback. All published local starts/resumes retire the online instance; primary buttons again issue only controller commands. The preview owner does not pause local transport, consume reservations or introduce a playback queue. Replacement/dismissal guards remain unchanged. This closes a controller-observation gap, not general OS audio-focus arbitration or a guarantee of zero overlapping audio frames. SFX/intermission, iOS native runtime and physical audio/session acceptance remain separate.

The Android instrumentation uses real KCP1 socket commands to reserve/start a local starter while a preview is open, waits for backend start acknowledgement, verifies native preview retirement, pauses, explicitly reopens the preview, resumes remotely and verifies retirement again. Reservation alone must preserve the preview, normal FIFO consumption remains checked, and local Stop must not reopen online playback. New-head APK/lint/emulator acceptance is pending. No website, dependency, permission, persistence or remote-protocol change.


## PR #51 Android audio ownership handoff — October 18, 2026

Baseline `fa5dcbc` passed reconstruction run 39108033371, including native KCP1 remote start/resume preview retirement. The next candidate adds a token-matched online audio lease at SessionController's existing audio-focus boundary. It stops native auxiliary audio on online claim, rejects claim during active/preparing karaoke or microphone/monitoring, and destroys online playback before explicit native audio acquisition. No local pause/queue command is sent by opening a video. MainActivity releases the exact token on close/error/background; stale old dialogs cannot release a newer lease.

New native instrumentation covers real effects/intermission, microphone start/blocked online opening, stale release, explicit native output, background cleanup and reservation identity. Local verification passed all 479 Java domain assertions, captured browser decrypt/wrong-code/tamper checks, 50 Python release-tooling tests, layout/presentation guards and whitespace review. Native Android compilation/lint/emulator acceptance remains pending on the candidate head; no Android SDK is present here. iOS audio-session coordination and native online lifecycle tests remain next, followed by physical embed/interruption/privacy/ads acceptance. No signing, merge, packaging, deployment or release.


## PR #51 iOS native lifetime regression batch — October 18, 2026

Added KantaNativeTests and three application-hosted WKWebView component cases for replacement/background/stale callbacks/failure/reopen. The bundle compiles the exact production component source; no release-app testability or runtime testing API is added. The existing focused smoke builds/selects this native bundle alongside its unchanged navigation case. A named-pass gate rejects missing native cases, zero-test restarts and earlier source assertion failures; four Python regressions verify that gate.

Local validation: 39 Swift test files are wired; presentation guards, YAML target/source/host/selection review, all 54 Python release-tooling tests and whitespace checks passed. No local Swift compiler/Xcode is available. Required next evidence: new-head package/app compilation and the three native cases plus navigation case in hosted simulator smoke. Native tests are not physical media/interruption/ads or end-to-end sheet acceptance. iOS shared audio-session/auxiliary coordination remains unfinished. No merge, signing, packaging, deployment or release.


## Companion takeover — October 18, 2026

PR #51 imports PR #53's six-file approved design/roadmap batch, preserving the JPEG bytes and provenance. Owner scope now includes native Companion Remote and browser access in PR #51; prior instructions to defer it are superseded. The current native foundations are Android KCP1 hosting/client and iOS encrypted Android-host client. iOS encrypted hosting, multi-client support, browser transport/trust bootstrap and the approved remote screen remain unimplemented. Next executable work: define the browser trust/bootstrap and versioned capability contract against existing host command owners, then implement native/browser adapters and interoperability tests. Do not expose a plaintext browser endpoint as encrypted pairing or use a public website as a media relay. Existing YouTube validation does not establish companion acceptance.

PR #52 remains isolated. Its HostView inference repair is 6476ff8; main conflict reconciliation is 26c9aaa. Reconstruction 39125720584 and focused smoke 39125720644 were observed running; no passing result is claimed.

The takeover state above predates the implementation candidate below.

## PR #51 built-in and browser remote candidate — October 18, 2026

Owner explicitly moved the approved Companion Remote UI and browser implementation into PR #51. Source now adds native role entry, locally served responsive remote assets, private-interface HTTPS with per-session identities and fingerprint trust bootstrap, bounded guest authentication/replay/expiry, native pinned clients and controller-owned local commands. No decoder, persistent media/queue format, native dependency, sensor permission, signing or website release change. Existing KCP1/legacy paths remain separate. The shared HTML assets are platform-owned copies checked for equality; neither native build depends on the other platform.

Local Java core: 524 checks passed, including 45 actual TLS checks. API 36 compilation of PinnedBrowserTransport, 55 tooling tests, Swift layout/presentation/starter guards and captured Songbook interop passed. Local Chrome was blocked by the environment's Unix socket restriction; UI is not accepted from syntax/transport checks. Native builds, nine-case simulator smoke, full eight-case iPhone/iPad walkthrough, real-browser tests, cross-platform/device trust/security/media acceptance remain pending. Unsupported expanded shortcuts are disabled, not represented as completed features. See docs/COMPANION_BROWSER_CHECKPOINT.md (root-relative) for exact trust, expiry, limits, source and acceptance scope. No physical latency/performance result, merge, signing, deployment or release is implied.


### First-head diagnostics and repair candidate

Source 339b8f6 passed hosted Android APK/instrumentation assembly, lint, native sanitizer, 524 core checks and permission/notices checks. Reconstruction 39126848617 failed Swift test compilation (missing explicit throwing-parser try) and Chrome's first unverified-pairing assertion; emulator execution was skipped. Smoke 39126848616 failed the lazy AppServices initializer's isolation diagnostic. Repairs preserve every assertion, compose the host explicitly, match WebKit completion annotations, establish real-browser document/foreground readiness and retain nonsecret diagnostics. Android preparation is claimed before dispatch, and oversized replies now enter the operation ledger as bounded errors on both platforms to prevent duplicate side effects. The expanded local core suite passes 528 checks, including 49 real TLS checks. Changed-head hosted results remain pending; see the failure/repair ledger for confirmed causes versus hypotheses.


## Android SoundFont storage-budget repair — 2026-11-02

**Finding (CONFIRMED):** SF2 import double-counted the copied file in its post-copy free-space condition, rejecting valid large banks. It also copied before checking available storage, so a provider with unknown or inaccurate size could temporarily fill app storage.

**Root cause:** the post-copy comparison used total copied bytes against free bytes that already excluded those same bytes, and there was no reported-size preflight or streaming reserve check.

**Fix:** add a pure SoundFontStoragePolicy, preflight provider-reported size, periodically recheck available bytes during streaming, and require at least 64 MiB remaining after copy. Keep native structure/sample validation and the existing no-fixed-file-size policy.

**Regression protection:** CoreRegressionTests cover a 600 MiB bank with 1 GiB available, rejection when the reserve would be consumed, and the streaming-chunk boundary. Hosted Android build, lint, core regression and emulator runtime checks are pending on the PR head.
