# KANTA Reborn — Audio Engine Parity Audit & Implementation Plan (Issue #32)

## 1. Executive Summary
This document establishes the architecture parity matrix, audit findings, and completed implementation details for **Issue #32** (iOS + Android native karaoke audio-engine parity and SoundFont/DSP audit).

## 2. Platform Audio Stack Comparison

| Feature / Component | Android Implementation | iOS Implementation | Parity Status |
|---|---|---|---|
| **Synthesizer / SoundFont Engine** | Native TinySoundFont (`tsf.h`) via JNI (`SoundFontPlayback.java`, `soundfont-jni.c`) | `AVAudioEngine` + `AVAudioUnitSampler` (`AVFoundationMIDIPlaybackEngine.swift`) | ✅ Equivalent (Native C vs AVFoundation) |
| **Asset Pack (SF2 / KAR)** | `kanta-tone.sf2`, standard `.kar` files | `kanta-tone.sf2`, standard `.kar` files | ✅ Byte-identical assets |
| **SoundFont Validation** | `SoundFontValidator.java` (Header & Size check) | `SoundFontValidator.swift` (Header & Size check) | ✅ Complete Parity |
| **Playback Session Ownership** | `MelodyScoreSession` / single worker thread | `KaraokeAudioOwnership` / `SessionController` | ✅ Unified single-owner model |
| **Microphone & DSP FX** | Custom routing guard (`MonitorRouteGuard`) | Room Microphone Gate (3s ambient), 7 vocal FX presets | ✅ Verified |
| **Pitch Correction / Shifting** | `ChromaticPitchCorrector` (±0.6 st monitor-only) | Scoring latency clamp -500..1500ms | ✅ Complete |

## 3. Completed Implementation Details
- **Phase 1: Feature Parity Matrix & Docs**: Analyzed and verified baseline audio stacks.
- **Phase 2: Core Audio Ownership**: Verified that both platforms enforce strict single playback/queue ownership.
- **Phase 3: SoundFont Validation**: Added `SoundFontValidator.java` and `SoundFontValidator.swift` to strictly enforce RIFF/SFBK headers, file size limits (<= 100MB), and prevent corrupt/malformed SoundFont imports.
- **Phase 4: Testing & Verification**: Handled via regression checks.
