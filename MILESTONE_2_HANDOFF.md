# Milestone 2 Handoff — KANTA Android Reconstruction

**Date:** 2026-10-28  
**Agent:** Astra (taking over from previous agent)  
**Branch:** `main`  
**Head:** `4e52508 Fix ios-runtime-artifacts path filter: remove tools/**`

## Status
Changes are in the working tree, **uncommitted and unpushed**. Two files modified:

1. `docs/ARCHITECTURE.md`
2. `apps/android/app/src/main/java/ph/kanta/reborn/core/catalog/SqliteSongCatalog.java`

## Changes Applied

### 1. Audio synthesis engine: TinySoundFont confirmed (dim-10 compliant)
**File:** `docs/ARCHITECTURE.md` (lines 267–269 revised)

Changed the engine decision from FluidSynth to TinySoundFont for Android:
- TinySoundFont is the MIDI/SoundFont engine on Android (MIT-licensed, already integrated via `app/src/main/cpp/soundfont.c` + JNI `NativeSoundFont`, declared in `THIRD_PARTY_NOTICES.md`)
- Satisfies dim-10 (old KANTA native deps: TinySoundFont + CameraX only)
- iOS retains AudioKit/Tonic/SoundpipeAudioKit; no FluidSynth integration on either platform
- BASS/BASSMIDI/BASS_FX remain prohibited
- No code change needed — TinySoundFont was already the integrated engine

### 2. Dim-3 Option 3b: Folder scan as single authoritative catalog
**Files:** `SqliteSongCatalog.java` + `ARCHITECTURE.md`

`SqliteSongCatalog.java`:
- `rebuild()` now scans and indexes **only** from the folder tree (`songs/`)
- The legacy Meg container (`kantidx`/`kantablob`) is **no longer indexed during `rebuild()`**
- `indexFromLegacyContainer()` retained as a **documented placeholder** for the future user-initiated importer flow — **not called** from `rebuild()`
- Class Javadoc + method Javadoc updated for dim-3 Option 3b catalog ownership

`ARCHITECTURE.md`:
- Added "Dim-3 catalog ownership (Option 3b, accepted 2026-10-28)" section documenting:
  - Folder scan = single authoritative catalog
  - Meg container = optional user-initiated importer (separate flow)
  - Constraints: one catalog, one playback path; decoder never touches queue/clock owners; key at runtime only; MKMD decode in `core/songbook/`, fixture-tested via `coreRegression`

### 3. miniaudio / Freeverb
No change — neither is required for current scope. Both are permissive-licensed but not in the tree.

## Verification
- `tools/check-product-branding.py`: **4 checks passed, 0 failed**
- `git diff`: exactly 2 files changed, 46 insertions / 19 deletions

## Open Items
- THIRD_PARTY_NOTICES.md entry for ported MKMD decoder
- Regression test coverage for MKMD round-trip + adversarial inputs
- User-initiated importer flow implementation (separate from `rebuild()`)
- Device acceptance (no local JVM — hosted CI only)

## Constraints Preserved
- One catalog, one playback path
- Decoder never touches `ReservationQueue` or `RenderedPlaybackClock`
- Key material at runtime only, never in repo/CI (Option B)
- `applicationId` stays `ph.kanta.reborn`
- One `SessionController`, one `ReservationQueue`, one `RenderedPlaybackClock`
- No proprietary SongHub APK libraries or bundled media
- `tools/check-product-branding.py` must continue to pass

## Next Executable Task
Implement the user-initiated Meg container importer flow (separate from `SqliteSongCatalog.rebuild()`), then add regression coverage and THIRD_PARTY_NOTICES entry.
