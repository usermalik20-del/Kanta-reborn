# Android lint hardening — local candidate (not published)

Branch `fix/android-lint-regressions-local`, based on `main` at `4d9c1a6`. This
is local source work; it has **not** been built, signed, installed, pushed or
released. The uploaded 2026-11-13 AGP 8.13.2 debug lint report contains 295
warnings and no errors. Passing earlier CI lint meant no error-level findings;
it never meant zero warnings.

## Build contract

`assembleDebug`, `assembleRelease`, `bundleDebug`, and `bundleRelease` require
variant-specific Android lint plus `tools/verify-android-lint.py`. The checked-in
`tools/android-lint-debt.json` has an **empty** warning allowance: any warning or
error fails the build. No automatic baseline refresh is possible. All lint checks
stay enabled, and reports remain available. Narrow `SuppressLint` annotations
are allowed only for the exact local TLS-pinning manager, required JavaScript in
the two hardened embedded WebViews, and programmatic-only views. Source guards
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

## Evidence and blocker

`python3 -m unittest discover -s tools/tests -p 'test_android_lint*.py'` covers
lint gate negative controls, final APK ELF/ZIP alignment, security exceptions,
programmatic-view XML absence and build wiring. The full Python suite, workspace
contract and Java structure checks pass locally. The official LiteRT AAR was
independently checked for 16 KB ELF segment alignment. A locally runnable
Android build is unavailable here (the JVM fails on unsupported `getcpu(2)`;
platform-36 SDK/AAPT2 are absent). Therefore source work and tests **do not prove
a zero-warning Android build**. Hosted exact-head debug/release lint, native APK
checks, core tests and emulator acceptance must pass before merge/release.
