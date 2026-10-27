# Legacy indexed-container format notes (read-only interoperability)

**Status:** research notes + the Android compatibility layer that implements them.
**Scope:** read-only structural understanding of the legacy `shubidx` index and `shubblob` payload container, so KANTA can list a songbook it already owns.
**Not in scope:** decoding protected song payloads, re-implementing the proprietary native engine, or shipping any legacy media.

---

## 1. How this was established

| evidence | what it gave |
| --- | --- |
| `jpdcs/SongHub-Android` (owner-owned, public) | README with the content folder contract; `manifest.json` describing the downloadable content pack and app-update flow. No application source. |
| `SongHub.apk` 2.42.67 (owner-supplied) | manifest, DEX class/method/string inventory. Decompiled at the bytecode level for the five container classes: `MegidxDatabase`, `MegmidReader`, `MegvolReader`, `MegSong`, `MegSinger`. |
| `SongHub Remote.apk` 3.0 (owner-supplied) | companion protocol surface. |
| Bundled content pack (owner-supplied) | `idx/shubidx` (1,797,052 bytes), `idx/shubblob` (606,828,544 bytes), soundfonts, background clips, photos. |
| **KANTA's own iOS implementation** | `apps/ios/Sources/Infrastructure/LegacyCompatibility/` — `ShubIndexProbe`, `ShubBlobOffsetCorrelator`, `ShubBlobSpanIndex`, `ShubBlobPayloadProbe`, `ShubBlobPayloadResolver`, `ShubMKMDDecoder`, `IndexedContainerCompatibility`, with `LegacyCompatibilityTests`. This is the authoritative interpretation used by KANTA. |

**The Android work mirrors the iOS compatibility layer, it does not invent a second interpretation.**

---

## 2. What the legacy application does with the files

From the decompiled bytecode of the owner's own build:

- `MegidxDatabase.load(path)` maps the index file read-only (`FileChannel.map`, little-endian) and rejects files shorter than 56 bytes.
- The application keeps three candidate index-start offsets (from the header) and accepts the first whose first record byte is a known record kind (`1,2,3,4,5,6,7,9`).
- The index is a sequence of length-prefixed records. KANTA's iOS probe established the layout that the Android port now mirrors:

```
header: 7 little-endian words; word[5] is the metadata table offset
record: kind(1) | totalLength(1) | opaque prefix | text payload
  kind 2,3 -> 6 byte prefix      kind 4,5 -> 5 byte prefix
  kind 6,7 -> 4 byte prefix      kind 1   -> title index record (>= 12 bytes)
title record: opaqueA(u32) | opaqueB(u32) | title text
trailing table: one u32 per title record = payload offset inside the container
```

- `MegmidReader.readMidi(...)` reads a song's bytes from its `megmid` file at `(localByteOffset, localByteLength)` and then calls `NativeLib.processMidiData(...)` — the proprietary native library. When that call returns null the application reports *"This song is not supported in this app."*
- `MegvolReader.readMp3(...)` reads MP3 bytes from a volume file at `(mp3LocalByteOffset, mp3LocalByteLength)`.
- `MegSong` carries `code`, `name`, `singerIdx`, `langId`, `langType`, `style`, `midiPosSectors`, `midiSizeSectors`, `mp3PosSectors`, `mp3SizeSectors`, `mp3Vol`; the sector unit is 2048 bytes (`ALIGNMENT_SIZE`).

---

## 3. What the bundled files actually contain

Measured on the owner's pack:

| file | observation |
| --- | --- |
| `idx/shubidx` | 1.8 MB. Header words (LE): `0, 55, 207, 389, 317841, 8348, 8478, 0xbb7e3db3, …`. The metadata table starts at **8348** (word 5) and contains readable records (`%1 WORDS` … `0 WORDS`, `Female`, `Male`, language and style names). Title records follow, then a trailing `u32` offset table. |
| `idx/shubblob` | 607 MB. Magic `MKMD` + version byte `0x01`, then declared sizes. **No `MThd`, `RIFF` or `ID3` signature appears in the first 16 MB** — the payloads are not stored in the clear, consistent with the proprietary native processing step. |
| `sound/*.sf2` | standard RIFF `sfbk` SoundFont containers — readable by any SF2 loader, including KANTA's TinySoundFont. |
| `bgv/**`, `bgphoto/**` | ordinary MP4/JPEG media. |

**Conclusion:** the *index metadata* is readable; the *song payloads* are protected by a proprietary binary component that KANTA does not and will not ship.

---

## 4. KANTA's decisions (already recorded in `docs/ARCHITECTURE.md`)

- FluidSynth is the chosen MIDI/SoundFont engine for KANTA. **BASS / BASSMIDI / BASS_FX must not be integrated** as part of SongHub compatibility work.
- **No proprietary SongHub APK libraries or bundled media may be copied into KANTA.**
- `shubidx`/`shubblob` parsing is an independent **read-only** interoperability task and does not require BASS.

Consequences for KANTA Karaoke:

1. **Metadata path (Beta 1, shipped).** `ph.kanta.reborn.core.songbook` reads the legacy index read-only and exposes titles plus the trailing offset table through a neutral facade. A songbook can therefore be *listed* on a device that owns the files.
2. **Payload path (not shipped).** Protected payloads stay protected. KANTA plays the songs the user actually owns in plain form: MIDI/KAR/MP3/WAV imported through the existing SAF flow, or files the owner places in the `KANTA/` folder. KANTA never copies the legacy soundbanks, clips or catalogue.
3. **Compatibility layer.** The Android classes mirror the iOS ones so both platforms describe the same container identically, and the interpretation stays reviewable in one place per platform.

---

## 5. Android compatibility layer (this change)

`apps/android/app/src/main/java/ph/kanta/reborn/core/songbook/`

| class | role |
| --- | --- |
| `ShubIndexProbe` | structural probe: header words, metadata records, title records, trailing offset table |
| `ShubBlobOffsetCorrelator` | conservative verdict (`UNAVAILABLE` / `REJECTED` / `STRUCTURALLY_PLAUSIBLE`) for the offset table against a known container size |
| `ShubBlobSpanIndex` | payload spans derived from a plausible offset table; invalid spans raise instead of guessing |
| `ShubBlobPayloadProbe` | bounded prefix classification (`STANDARD_MIDI`, `WAVE`, `MP3_ID3`, `MKMD_V1`, `ZIP`, `GZIP`, `UNKNOWN`) |
| `IndexedContainerCompatibility` | neutral facade; format details never leak into playback, library or UI owners |

Tests: `apps/android/app/src/test/java/ph/kanta/reborn/core/songbook/ShubCompatibilityRegressionTests.java` — synthetic fixtures only, run by the existing `coreRegression` Gradle task on a server JVM. No song catalogue enters the repository.

Guards: `tools/check-product-branding.py` fails the build if the legacy **product** name reappears anywhere under `apps/`, if the legacy container name reaches the public website, if an Android type outside the compatibility package carries the container name, or if the Android application id changes.

---

## 6. Open items

| item | why it is open | next step |
| --- | --- | --- |
| MKMD payload decoding on Android | the iOS `ShubMKMDDecoder` + `GeloLegacyCompression` implement a compressed/encrypted container; the Android equivalent is not ported | port only if a legal and technical case appears; it is not required for the metadata path |
| Native engine (`libMewtwo`) | proprietary; explicitly out of bounds for KANTA | none — KANTA uses its own scoring path |
| Bundled media reuse | the owner asked to reuse the pack, but the repository records *"no proprietary SongHub APK libraries or bundled media may be copied into KANTA"* | owner decision required; see `replica/architecture.md` §0 |
| Version drift | the bundled APK is 2.42.67 while the public release is 4.16.67 | re-run the probe against 4.16.67 before claiming index parity |
