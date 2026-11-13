# Android lint hardening — published preview

PR [#204](https://github.com/usermalik20-del/Kanta-reborn/pull/204) merged as
`802b4a376434d28c749626e29a045ff590a48bea` after exact-head Android validation.
The original 2026-11-13 debug report contained 295 warnings; the final hosted
debug and release lint gates pass with an empty accepted-warning inventory.
Earlier green CI had allowed warnings; the new gate does not.

## Build contract

`assembleDebug`, `assembleRelease`, `bundleDebug`, and `bundleRelease` require
variant-specific Android lint plus `tools/verify-android-lint.py`. The checked-in
`tools/android-lint-debt.json` has an **empty** warning allowance: any warning or
error fails the build. No automatic baseline refresh is possible. All lint checks
stay enabled, and reports remain available. Narrow `SuppressLint` annotations
are allowed only for the exact local TLS-pinning manager, required JavaScript in
the two hardened embedded WebViews, the application-context-only font singleton,
and programmatic-only views. Source guards
verify those boundaries and that the views are absent from XML. The hosted job
also builds unsigned debug/release variants; signing remains confined to the
trusted-main-only step.

Both APKs run `tools/verify-android-native-alignment.py` against packaged 64-bit
ELF PT_LOAD segments and uncompressed ZIP entries. It rejects missing
arm64-v8a/x86_64 libraries and requires 16 KB alignment. NDK r27 uses flexible
page sizes for KANTA's own SoundFont library. SPICE moved from
`org.tensorflow:tensorflow-lite:2.16.1` to Google's
`com.google.ai.edge.litert:litert:1.4.0`: the downloaded official AAR includes
the existing `org.tensorflow.lite.Interpreter` Java class, and its two 64-bit
JNI objects have 0x4000 PT_LOAD alignment. API/model inference and final APK
compatibility are **not proven until hosted CI**; 16 KB device testing remains
open.

## Changes from the 295-warning report

Locale-sensitive file-extension, cache-key and artist matching now use
`Locale.ROOT`; user-facing numbers use the device locale. Hardcoded text and
formatted values were extracted into string resources. The resource report's
123 unused entries were individually checked for static Java/XML references
(and no dynamic resource lookup exists), then removed. The redundant `v26`
launcher qualifier was merged into `mipmap-anydpi`; the Gradle wrapper moved to
8.14.7 with its published distribution checksum. Draw geometry/shaders/paints
are cached across frames, RecyclerView adapters dispatch identity-aware diffs,
relative gravity is used, and AndroidX ExifInterface replaces the platform API.

`PinnedBrowserTransport` pins the exact self-signed leaf SHA-256, checks
certificate validity and rejects mismatches; it is not a trust-all TLS manager.
`FontStore` stores only application context. The local companion remote and
official YouTube embedded player need JavaScript; per-site navigation,
filesystem, permission and TLS boundaries remain guarded. Deleting a Scenic
entry no longer replays the incoming Intent. Imported media temp and rename
fallback files use atomic owner-only permissions. Android 12+ backup and
transfer rules exclude app storage; older OSes retain `allowBackup=false`.

## Hosted evidence and remaining acceptance

- Final PR head `3d3c2f985387cfac0539f45491d712bd92f2b8d7` passed
  [Android validation 43373300585](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43373300585).
- Merge source `802b4a37` passed [post-merge validation 43374761810](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43374761810),
  including debug/release zero-warning lint, packaged 16 KB alignment, core tests,
  emulator checks and the owner-signed release build.
- [Publisher 43375517109](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43375517109)
  passed signer continuity and immutable asset checks; [Vercel 43375582622](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/43375582622)
  deployed the matching Android page and manifest.
- First code-publication release: [`v0.10.0.851-preview-802b4a37`](https://github.com/usermalik20-del/Kanta-reborn/releases/tag/v0.10.0.851-preview-802b4a37).
  APK SHA-256: `87d7687f5944e19fb80f5455d9cc01533b83db3d4d3a6c26d6afc69ea67dff23`.
  The live manifest and GitHub release digest matched. Later documentation-only
  rebuilds may have a newer packaging source; use the [download page](https://kanta-public.vercel.app/android)
  for the latest package rather than treating this immutable evidence as a moving link.

The first PR run exposed missing legacy backup rules, a plural-count resource and
six ellipsis characters after extraction; the second exposed the release-only
monochrome adaptive icon requirement. These were fixed without changing the empty
lint allowance or bypassing checks. Tests now guard legacy/modern backup parity,
pluralization, typography and the dedicated monochrome icon.

Physical-device listening, actual SPICE model inference/latency and real 16 KB
page-device acceptance remain open. The local iSH JVM/SDK limitations still apply,
but they no longer prevent the separate claim of successful hosted validation.
No physical-device acceptance is claimed by emulator or source checks.
