# SHUB compatibility handoff

## Result and evidence boundaries

Static inspection of the supplied SongHub APK located the native decoder's 32-byte
AES key and 16-byte IV. Both match the existing Android
`LegacyFileContainerImporter` constants byte-for-byte. No new key is needed for
this APK version. Values are intentionally omitted from this document and evidence.
This is static parity, not proof of successful Android import or playback.

See [native comparison](docs/shub-evidence/native-comparison.json) for APK/library
SHA-256 fingerprints, ABI, addresses, method and comparison results.
The APK contains the string `SongHub Android 2.1.67`; manifest version was not decoded.

## Corrections to the earlier handoff/chat

The previous draft overstated APK findings. Do not use it as a format specification.
- Same-basename pairing and replacing filename extensions were not established.
- `findVolumes(File,String)` exists; its name does not prove it scans storage devices.
- A combined MIDI+MP3 record inside shubblob was not established. Do not add splitting
  logic without evidence.
- `verifyDiagnosticPassword(String)` exists; its callers and role are unknown.
  It is not established as DRM, licensing or an import prerequisite. Do not port it.
- KANTA's 1 GiB blob / 64 MiB payload limits are KANTA policy, not verified SongHub limits.
- KANTA's block/byte-offset conflict checks were not demonstrated in SongHub.
- A 32-byte AES key is AES-256, not AES-128. No first-16-byte truncation is indicated.
- `AES/CBC/NoPadding` plus reading the declared compressed length does not establish
  PKCS#7 padding. SongHub's native ciphertext-length calculation must be reviewed
  separately, especially for block-aligned compressed lengths.
- No key extraction via Frida or physical Android device was needed for static parity.

## Native trace (arm64-v8a)

`Java_com_songhub_android_database_NativeLib_processMidiData` begins at VA `0x2ef8`.
At `0x3044..0x3060`, q0 and q1 load 16 bytes each from VA `0x1200` and `0x1220`,
then store contiguously at frame pointer minus 48. q2 loads 16 IV bytes from `0x11e0`
and stores at frame pointer minus 64. At `0x3068`, x1 points to the assembled key;
at `0x306c`, x2 points to the IV; `0x3070` calls `AES_init_ctx_iv`.
The initializer (`0x3bec`) reads key bytes through offset 31 (`0x3cf8`).
`0x3080` calls `AES_CBC_decrypt_buffer`.

These findings establish the actual constants passed into AES on that branch, not
just a string match. The library also imports zlib inflate functions. Exact boolean
argument semantics, full decode control flow and Java call sites remain untraced.

## APK structural observations

The APK contains three DEX files, BASS/BASSMIDI libraries and `libMewtwo.so` for
arm64-v8a, armeabi-v7a, x86 and x86_64. Only arm64 native key parity was checked.
DEX class/method tables expose:
- `com.songhub.android.song.SongLibrary`: `load`, `loadFromLocation`,
  `loadOneDatabase`, `findVolumes`, `resolveVolume`, `resolveLocalOffset`.
- `MegidxDatabase`: `load`, `parse`, `parseLegacyHeader`, `parseExtendedHeader`,
  `parseSongBlock`, `isValidIndexStart`, text-decoding helpers.
- `MegmidReader.readMidi`: overloads accepting a LibrarySong or path/offset/length.
- `MegvolReader.readMp3`: LibrarySong and path/offset/length overloads.
- `NativeLib.processMidiData(byte[],boolean)` returning byte[].

Strings include `.shubidx`, `.megidx`, `shubblob`, `megmid`, `megvol`,
`No shubblob/megmid container files found for `, and bounds-error messages.
These support an indexed-container architecture but do not prove exact pairing,
folder placement, volume ordering or parsing behavior. JADX could not run because
the local JVM fails on unsupported `getcpu(2)`. LIEF exposed method tables, not
Java method bodies in the inspected interface.

## Existing KANTA implementation — reuse, do not duplicate

Android paths under `apps/android/app/src/main/java/ph/kanta/reborn/`:
- `core/legacy/LegacyFileContainerImporter.java`: active bounded paired-file importer;
  candidate title addresses, AES/decompression, decoded media output and errors.
- `core/legacy/MkmdDecoder.java`: separate decoder accepting key/IV arguments.
- `core/legacy/MegContainerImporter.java`: inspect this adapter before assuming a
  separate legacy filename-family implementation.
- `core/songbook/ShubIndexProbe.java`: title/metadata parsing and trailing table probe.
- `core/songbook/ShubBlobOffsetCorrelator.java`, `ShubBlobSpanIndex.java`,
  `ShubBlobPayloadProbe.java`: conservative inspection helpers; their layout
  assumptions are not interchangeable with the active importer.
- `SessionController.java`, `LibraryRepository.java`: import integration call sites;
  trace before changing storage or authoritative library ownership.

Android tests under `apps/android/app/src/test/java/ph/kanta/reborn/`:
- `core/legacy/LegacyFileContainerImporterRegressionTests.java`
- `core/legacy/MkmdDecoderRegressionTests.java`
- `core/songbook/ShubCompatibilityRegressionTests.java`
Synthetic fixtures already live in these sources; do not add private song catalogues.

Swift counterparts are under
`apps/ios/Sources/Infrastructure/LegacyCompatibility/`: `ShubIndexProbe.swift`,
`ShubBlobOffsetCorrelator.swift`, `ShubBlobSpanIndex.swift`,
`ShubBlobPayloadProbe.swift`, `ShubBlobPayloadResolver.swift`,
`ShubMKMDDecoder.swift`, `BinaryCursor.swift`, `IndexedContainerCompatibility.swift`.
Tests: `apps/ios/Tests/LegacyCompatibilityTests/`.
Compression bridge: `apps/ios/Sources/Infrastructure/LegacyCompression/`.
Do not assume Android fixes are already ported to Swift. `recovery/` is historical.

## KANTA-supported layout, not a universal SongHub specification

`ShubIndexProbe` synthetic fixtures use a 28-byte header with the sixth UInt32
(at byte 20, zero-based word index 5) pointing to metadata. Metadata can precede
title records. A title record has kind 1 at byte 0, total length at byte 1,
UInt32 words at bytes 4 and 8, and text from byte 12. Header words are not
universally established as reserved zeros. Text uses UTF-8 with ISO-8859-1 fallback.

The active Android importer tests title word at byte 8 multiplied by 2048 as an
MKMD address, and alternatively validates trailing UInt32 byte offsets. Conflicting
valid layouts are rejected; physical record order must never be substituted for
song identity. The trailing table is not universally a trusted byte-offset table.
MKMD v1 uses a 13-byte header: signature at 0, version at 4, decoded length at 5,
compressed length at 9 (UInt32 LE). The active importer decrypts block-aligned bytes
using AES-CBC/NoPadding, then raw DEFLATE using declared compressed length, and checks
decoded size/media signature. Review bounds, no-progress inflation, cancellation,
output collisions and partial-file cleanup before extending production use.

Earlier session notes report 47,998 real-pair addressed records and 47,997 MIDI
signatures plus one unsupported signature. Those counts were not rerun for this
handoff and do not independently prove semantic title/audio identity.

## Next-agent validation and open gates

Read `AGENTS.md`, `docs/ENGINEERING_ARCHITECTURE_CONTRACT.md`,
`docs/ARCHITECTURE.md`, `docs/TESTING.md`, `docs/UNFINISHED_WORK.md` (D2/D4),
and iOS-local instructions before changes.

On a working Android build host, CI uses:
```
cd apps/android
./gradlew --no-daemon :app:coreRegression
```
A JVM and Gradle setup are required; no root `:core:test` project was established.
No build or runtime regression was run for this documentation/static-analysis update.

Remaining: paired import through the app, accurate counts/library persistence,
semantic title/payload checks, real-device playback, runtime-only key handling
without persistence, and storage/cancellation behavior. Matching the existing
embedded constants does not close D2/D4 or authorize distributing content.

## Supporting files and exclusions

Included: `docs/shub-evidence/native-comparison.json` and a reproducible comparison
script `docs/shub-evidence/compare_native.py` (requires Python and LIEF).
The script reads the user-provided APK and existing importer in memory, emits only
comparison results and artifact hashes, and refuses an unrecognized native hash.

APK local source: `/var/minis/attachments/uploads/SongHub.apk`.
APK, DEX, native libraries, keys and private shub song files are deliberately not
copied into the repo. Other agents need authorized local access to the APK to
repeat analysis; raw offload logs may contain sensitive constants and are excluded.
No third-party binaries need to be linked or copied into KANTA for this finding.

## Runtime-key follow-up (local candidate)

Android now removes the embedded constants and requires `LegacyImportKey` for each
paired import. The parity report above describes the pre-change baseline, not
constants in the new source. `compare_native.py` compares against a pinned Git
source revision in memory so the historical result remains reproducible.
See the runtime-key section in `docs/ARCHITECTURE.md`. Build/device acceptance
remains pending; no runtime key is saved in this handoff.
