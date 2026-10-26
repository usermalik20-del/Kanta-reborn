# Verified preview downloads


## Current accepted preview baseline — 2026-10-26

The accepted native source is [`0867134`](https://github.com/usermalik20-del/Kanta-reborn/commit/086713405002f75ab5c7fc49cd815cf2a3469041), which removes temporary diagnostic tap/window probes and tightens the no-probes source contract. The later main commit [`865ed5f`](https://github.com/usermalik20-del/Kanta-reborn/commit/865ed5fdfbd3abacbba18b3014d0b1d5fb0cd062) changes website HTML/CSS only; the native source baseline remains `086713405002f75ab5c7fc49cd815cf2a3469041`.

- **Android reconstruction:** [run #40320555135](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320555135) passed on the exact source SHA. Artifact [#12648928676](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320555135/artifacts/12648928676) (`android-reconstruction-checks`) is 28,698,941 bytes; ZIP SHA-256 `928cbc8603b918a81257ef54327bf90238d27cab791ecb02ace3dea9eca4b2dc`. It contains the debug APK, instrumentation-test APK, lint output and runtime diagnostics.
- **Unsigned iOS device package:** [run #40320554747](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554747) passed on the same SHA. Artifact [#12649740030](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554747/artifacts/12649740030) (`KANTA-personal-unsigned`) is 25,130,708 bytes; ZIP SHA-256 `6a53aa37227ebd0561d064a18918aeeb6da9ffce8c14818135cb6e21144a734b`. It is unsigned and requires separate personal signing.
- **iPhone/iPad walkthrough:** [run #40320554824](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40320554824) passed both simulator jobs on the same SHA. The iPhone and iPad jobs uploaded runtime evidence and presentation diagnostics.
- Both raw Actions artifacts require GitHub sign-in and expire after seven days. They are preview/testing artifacts, not a public release.
- The website manifest and platform pages are being updated to these exact run/artifact identities. No signed production IPA or published GitHub Release is verified.


The read-only packaging workflow preserves accepted application bytes without compilation or signing. Android 0.5.0 source `4da765035dd3cbeec6e69f38581138225b706e6d` passed run [38826875086](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38826875086), including 201 domain assertions, 60 emulator checks and the merged-APK permission/runtime-notice gate. iOS source `3c12a1fb962ac956250a8f238c01eace13f560d2` passed device packaging and both simulator walkthroughs in run [38806377805](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/38806377805).

Each platform bundle contains one application, SHA256.txt, build-info.json and README.txt. The instrumentation APK is excluded. Packaging checks successful run/source/artifact identity, expiry, available artifact digest, archive bounds and app contents. The IPA retains its original checksum, bundle identity, software notices and unsigned personal-signing flags. Authorization headers are stripped on storage redirects.

Only kanta-gelo dispatch or the owner-approved-download-packaging PR label runs packaging. Outputs require GitHub sign-in and last 30 days. A failure never replaces accepted app bytes or creates a public release. iOS requires separate trusted personal signing and has no iCloud Save. Android remains debug-signed. Test builds from separate CI machines can use different debug signing certificates, so installing another preview may require uninstalling and losing app-private state; keep original media and explicit exports backed up.

Website download links must change only after packaging succeeds and provides actual artifact identities. Earlier bundles retain their original source/run metadata. Physical-device and production-distribution acceptance remain open.
