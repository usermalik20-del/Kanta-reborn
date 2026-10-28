# Verified preview downloads


## Current mainline checkpoint — 2026-10-28

Main head is [`86dde8e`](https://github.com/usermalik20-del/Kanta-reborn/commit/86dde8e5dbf079bf4815e1b8dc460ff13936e5a3), which merged [PR #105](https://github.com/usermalik20-del/Kanta-reborn/pull/105) for read-only Android legacy songbook probes and the product-branding guard.

- Reconstruction [run #40614166494](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40614166494) passed Android and iOS jobs on `86dde8e`; Android bundle [artifact #12784457872](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40614166494/artifacts/12784457872), 28,594,997 bytes, ZIP SHA-256 `94954892e95547a5b5b2a354578ce782319eb425dd88bcefb0e51bb284a85dc7`.
- Unsigned iOS archive [run #40614166497](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40614166497) passed; artifact [#12783878073](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40614166497/artifacts/12783878073), 25,137,222 bytes, ZIP SHA-256 `2cc2dd797081a33a88a2f61955a99700ae75216ddbbe9daf807596ef2f1a7992`.
- Website validation [run #40614166398](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40614166398) and production Vercel deployment [run #40614166370](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40614166370) passed.
- Full iPhone/iPad walkthrough [run #40614166372](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40614166372) had both simulator jobs compiling and running the actual walkthroughs at the time of this checkpoint. No final device result is claimed by this entry.

**Release blocker discovered:** the focused iOS smoke workflow did not trigger on the `86dde8e` main push because `.github/workflows/ios-smoke.yml` had only manual and pull-request events. The download packager also required only reconstruction, unsigned archive and full walkthrough, so it could accept a source SHA without focused smoke evidence. [PR #106](https://github.com/usermalik20-del/Kanta-reborn/pull/106), branch `fix/exact-sha-ios-smoke-gate-20261028`, adds a main-push trigger for relevant iOS/Android source changes, requires the focused smoke run on the same SHA, records that run ID in the generated manifest/build notes, and adds regression tests. This repair is a candidate until its fresh CI passes and the fix is merged.

The public manifest and durable release remain pinned to `41c8ddde`. Do not update public links until all four exact-SHA gates pass, the owner-approved packaging workflow validates actual artifact identities/digests, website validation passes, and the production deployment is verified. The current Android bundle is not a standalone production APK; the iOS archive is unsigned. No signed production IPA or physical-device acceptance is established.

## Current accepted preview baseline — 2026-10-27

Main head is merge commit [`9a2a0fc`](https://github.com/usermalik20-del/Kanta-reborn/commit/9a2a0fca3778ff10eb549c319d7f400f33661b28), which merged [PR #101 — Fix Android predictive-back dispatch](https://github.com/usermalik20-del/Kanta-reborn/pull/101). The Android Compact Workspace now dismisses active accessories through a lifecycle-bound AndroidX `OnBackPressedCallback`; the deprecated `Activity.onBackPressed()` override and API 36 `GestureBackNavigation` lint failure are removed.

**Exact-SHA acceptance on `9a2a0fc`:**
- **Android + iOS reconstruction:** [run #40499591316](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591316) — both jobs passed, including Android build/lint/domain checks, browser interoperability, private-interface validation and emulator runtime. Android bundle [artifact #12731834290](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591316/artifacts/12731834290), 28,565,463 bytes, ZIP SHA-256 `4fa347bcfdad8cfcc7d2063323ce4e48a9951100273efedf6eb4f01be9a32f55`.
- **Unsigned iOS archive:** [run #40499591326](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591326) — passed. Archive [artifact #12732093588](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591326/artifacts/12732093588), 25,137,220 bytes, ZIP SHA-256 `603d70d71f3eb320152b20c65be4e06629f1e6f1d37da2bcbf9cdb2ac592f219`.
- **Full iPhone/iPad walkthrough:** [run #40499591484](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591484) — both simulator jobs passed on the same SHA. iPhone runtime [artifact #12733327427](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591484/artifacts/12733327427); iPad runtime [artifact #12733063065](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40499591484/artifacts/12733063065). Presentation diagnostics were also uploaded for both devices.

The three required native acceptance gates now pass on one exact SHA. This does **not** mean a new public release has been published: the public manifest at [`/downloads.json`](https://kanta-public.vercel.app/downloads.json), platform download pages and durable GitHub Release [`v0.1.0-workspace-redesign`](https://github.com/usermalik20-del/Kanta-reborn/releases/tag/v0.1.0-workspace-redesign) remain pinned to native source `41c8ddde`. Keep those links unchanged until a new exact-source packaging run produces identified artifacts and website contract validation plus production deployment pass.

The existing release carries `KANTA-Android-preview-41c8ddde.apk` (debug-signed) and `KANTA-iOS-personal-unsigned-41c8ddde.ipa` (unsigned; trusted personal signing required). No signed production IPA is verified. Actions artifacts are temporary and require GitHub sign-in; they are not durable Release assets.

## Superseded baseline — 2026-10-26

The accepted native source was [`0867134`](https://github.com/usermalik20-del/Kanta-reborn/commit/086713405002f75ab5c7fc49cd815cf2a3469041), which removes temporary diagnostic tap/window probes and tightens the no-probes source contract. Later commits on that baseline changed website HTML/CSS only.

- **Android reconstruction:** [run #40320555135](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320555135) passed on the exact source SHA. Artifact [#12648928676](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320555135/artifacts/12648928676) (`android-reconstruction-checks`) is 28,698,941 bytes; ZIP SHA-256 `928cbc8603b918a81257ef54327bf90238d27cab791ecb02ace3dea9eca4b2dc`.
- **Unsigned iOS device package:** [run #40320554747](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554747) passed on the same SHA. Artifact [#12649740030](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554747/artifacts/12649740030) (`KANTA-personal-unsigned`) is 25,130,708 bytes; ZIP SHA-256 `6a53aa37227ebd0561d064a18918aeeb6da9ffce8c14818135cb6e21144a734b`.
- **iPhone/iPad walkthrough:** [run #40320554824](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554824) passed both simulator jobs on the same SHA.


The read-only packaging workflow preserves accepted application bytes without compilation or signing. Android 0.5.0 source `4da765035dd3cbeec6e69f38581138225b706e6d` passed run [38826875086](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38826875086), including 201 domain assertions, 60 emulator checks and the merged-APK permission/runtime-notice gate. iOS source `3c12a1fb962ac956250a8f238c01eace13f560d2` passed device packaging and both simulator walkthroughs in run [38806377805](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38806377805).

Each platform bundle contains one application, SHA256.txt, build-info.json and README.txt. The instrumentation APK is excluded. Packaging checks successful run/source/artifact identity, expiry, available artifact digest, archive bounds and app contents. The IPA retains its original checksum, bundle identity, software notices and unsigned personal-signing flags. Authorization headers are stripped on storage redirects.

Only kanta-gelo dispatch or the owner-approved-download-packaging PR label runs packaging. GitHub Release assets are durable and do not expire; Actions artifacts are ephemeral and require GitHub sign-in. A failure never replaces accepted app bytes or creates a public release. iOS requires separate trusted personal signing and has no iCloud Save. Android remains debug-signed. Test builds from separate CI machines can use different debug signing certificates, so installing another preview may require uninstalling and losing app-private state; keep original media and explicit exports backed up.

Website download links must change only after packaging succeeds and provides actual artifact identities. Earlier bundles retain their original source/run metadata. Physical-device and production-distribution acceptance remain open.
