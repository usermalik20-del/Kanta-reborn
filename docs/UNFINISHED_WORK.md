# Unfinished work

**Last updated:** 2026-10-29 · **main:** `9a6177e` · **public release source:** `41c8ddde`

This is the single list of what is **not** done. It exists so that outstanding work is
visible in the repository rather than living in a chat transcript.

## How to use this file

- **An item is only done when its acceptance criterion has been demonstrated on one named
  commit SHA and the evidence is linked here or in `docs/RECONSTRUCTION.md`.** A green
  build is not device acceptance, and a passing unit test is not a passing integration.
- Do not delete an item. Move it to [§F Recently closed](#f-recently-closed) with the SHA
  and the evidence, so the trail survives.
- Anything in [§E Explicitly not claimed](#e-explicitly-not-claimed) must not appear in the
  website, store listings or README as if it worked.

Effort is a rough guide only: **S** under an hour, **M** a focused session, **L** more than
a day or blocked on hardware/signing.

New sections are appended before [§F](#f-recently-closed), so IDs that are already referenced
elsewhere never change meaning.

---

## A. Release blockers — owner-controlled

| ID | Item | Why it matters | Current evidence | Acceptance criterion | Effort |
| --- | --- | --- | --- | --- | --- |
| A1 | **Signed production iOS IPA** | The shipped IPA is unsigned and needs trusted personal signing; it is not installable or TestFlight-able. | `README.md` "Open release gates"; `ios-runtime-artifacts.yml` builds with `CODE_SIGNING_ALLOWED=NO` and `personal_signing: true`. | A signed IPA installs on a non-development device, or the owner explicitly decides unsigned personal-signing builds remain the shipping form. **Requires owner certificate and approval — do not do this without an explicit instruction.** | M |
| A2 | **Decide the production domain wiring** | Branch `chore/configure-is-a-dev-domain` (`54478c7`, 4 commits) changes `vercel-deploy.yml` to configure the KANTA is-a.dev domain plus its workflow guard. Changing the production domain is an owner boundary. | Branch exists locally and on the remote; not merged. It is someone else's in-flight work — **leave it untouched**. | Owner approves or rejects the domain change; on approval the branch is reviewed and merged, on rejection it is closed with the reason recorded in `docs/CI_FAILURE_REPAIR_LEDGER.md`. | S |
| A3 | **New exact-source packaging run** | The durable release `v0.1.0-workspace-redesign` and the public manifest are pinned to native source `41c8ddde`, which predates every milestone in this ledger. The live site therefore describes an older build. | `apps/web/downloads.json`; `docs/VERIFIED_DOWNLOADS.md`. | An exact-SHA packaging run produces identified artifacts for a current main SHA, checksums are recorded, website contract validation passes, and the manifest plus the release are advanced together. | M |
| A4 | **Physical-device acceptance (Android and iOS)** | Every acceptance claim in this repository is host-CI evidence. No milestone has been run on a real handset. | `docs/TESTING.md`: a green build is not device acceptance. | One real Android device and one real iOS device complete: search → reserve → play with lyrics → countdown → score → queue advance, plus a background photo and the front camera, with a screenshot or logcat excerpt attached. | L |

## B. Milestone 4 remainder

| ID | Item | Why it matters | Current evidence | Acceptance criterion | Effort |
| --- | --- | --- | --- | --- | --- |
| B1 | **Spectrum analyser** | Listed in the Milestone 4 scope; nothing exists yet. | No `SpectrumView` in `apps/android/.../stage/`; the mic path is owned by `MicrophoneInput`/`MicrophoneMonitor`/`MonitorBuffer`. | A band plan derived from real PCM, unit-tested framework-free like `ScoreCelebrationPlan`, **plus** a documented decision on how it taps the audio path without breaking the one-owner rule for playback/monitor. Do not add an unwired view. | L |
| B2 | **Custom fonts** | `ContentRoot.FONT_DIR` (`font/`) is scanned but no font is ever loaded or applied. | `ContentRoot` declares `FONT_DIR` and the scanner collects `fontFiles`; nothing consumes them. | A user font in the content root changes lyric rendering, the choice persists through `SettingsStore`, and a malformed font falls back to the built-in typeface instead of crashing. | M |

## C. Milestone 5 remainder

| ID | Item | Why it matters | Current evidence | Acceptance criterion | Effort |
| --- | --- | --- | --- | --- | --- |
| C1 | **Kanta-Connect phone app** (`ph.kanta.reborn.companion`) | The host side is complete and shows a QR, but nothing can scan it. Pairing is untestable end-to-end without a client. | `core/companion/KantaConnectHost.java` serves WebSocket clients and dispatches snapshot/search/reserve/cancel/play/pause/next; `core/connect/KantaConnectInfo.java` defines the link. No client exists. | A separate app installs on a second Android device, discovers the host over NSD `_kanta._tcp` **or** reads the QR, pairs with the code, and drives the queue and transport. Evidence: a two-device session with the host's logcat and the client's screen. | L |
| C2 | **Public review mining → prioritised backlog** | The second half of Milestone 5: fix what users of comparable apps complain about. | **Report written:** [`docs/reviews/2026-10-29-karaoke-review-mining.md`](reviews/2026-10-29-karaoke-review-mining.md) — 100 reviews, 37 negative, 15 app/store sources. Accepted rows carried into [§G](#g-from-review-mining-2026-10-29). | Owner reviews the ranked table; the rejected rows are recorded with reasons (done in the report). | M |
| C3 | **On-device two-host pairing test** | `KantaConnectHost` has never accepted a real client; only the request/response contract is shaped. | No test drives `WebSocket` accept, the pairing code check and the dispatch path together. | Two devices pair over real Wi-Fi and a reserve made on the client appears in the host's Up Next. | M |

## D. Known defects and unproven claims

| ID | Item | Why it matters | Current evidence | Acceptance criterion | Effort |
| --- | --- | --- | --- | --- | --- |
| D1 | **iOS Song Library transition is intermittently broken** | The original failure was never fixed — a candidate repair (PR #113) made it deterministically worse and was reverted. The underlying defect is still there. | `docs/TESTING.md`: walkthrough [#40686820501](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40686820501) failed `testMenuSongLibrary` on iPhone while iPad passed. PR #113 turned that into an eight-case two-device failure and was reverted in `b7d69f2`; the walkthrough then passed on `b7d69f2` and `5345d06`. | A repair whose focused smoke **and** both device walkthroughs pass on one exact SHA, on a branch validated before merge. Reproduce the intermittent failure first. | M |
| D2 | **MKMD decoder has never decoded a real container** | The legacy importer is the riskiest component in the tree and is only validated against synthetic fixtures. | `MkmdDecoderRegressionTests` covers round-trip and adversarial inputs on a JVM; no real `kantablob` payload has been decoded anywhere. | A real Meg container imported on a device, with the decoded files played back. **Note the format is undocumented and the payloads are encrypted — treat a negative result as a finding, not a bug.** | L |
| D3 | **QR code has never been scanned by a real client** | Correctness is proven against an independent scanner (Apple Vision), but not against the app that will actually consume it. | `QrEncoderGoldenVectors` pins matrices decoded by Apple Vision for versions 1, 5, 6, 7, 8, 9 and 10. | The Kanta-Connect client (C1) pairs successfully from a screen-displayed QR. | S |
| D4 | **Meg importer has no UI surface** | `SqliteSongCatalog.importFromLegacyContainer(...)` is public but nothing calls it, so a user cannot reach the feature. | Only reference is the method's own declaration in `SqliteSongCatalog.java`. | A user-initiated entry point (Controls or My Set) that reads `kantidx`/`kantablob` from the content root, supplies key material at runtime, reports decoded/skipped counts, and never writes key material anywhere. | M |
| D5 | **Media3 is not a dependency** | The Milestone 2 plan named Media3 for BGV video; the tree renders background video through the original `StageSurface`/`StageView` path instead. Either the plan or the code is wrong. | `app/build.gradle` dependencies are `activity`, `camera-camera2`, `camera-lifecycle`, `camera-view`, `recyclerview`. | Owner decides: keep the existing path and correct the plan, or adopt Media3 with `THIRD_PARTY_NOTICES.md` updated. Record the decision. | S |
| D6 | **iOS `InAppControlCenterView` is still unrestyled** | The last iOS surface not on the current visual style. | 43 `GeloVisualStyle` references remain in `apps/ios/Sources`, 14 of them in `Features/Host/InAppControlCenterView.swift`. | The view uses the current design system and `tools/check-ios-presentation-contract.py` still passes. | M |
| D7 | **Companion control is experimental** | Users could reasonably assume it is finished. | `README.md`: Android-to-Android only, no forward secrecy, no independent security review, no iOS interoperability. | Either the limitations stay prominent in user-facing copy, or they are closed by work (forward secrecy, a security review, an iOS client). | L |
| D8 | **The melody and guide volume sliders do nothing** | A control that appears to work but changes nothing is worse than no control; users of other apps report exactly this class of betrayal over backing vocals. | `StagePresenterImpl` handles `onMelodyVolumeChange`/`onGuideVolumeChange` with empty bodies (≈ lines 137–142), and `SoundFontPlayback` exposes only a master `volume` with no per-voice mixing. | Either the sliders change the rendered mix audibly and the value persists, or the sliders are removed and the removal is recorded here. | M |

## E. Explicitly not claimed

These must never be presented as working. They are deliberately out of scope or unproven:

- **Legacy Meg container payload decoding on a device** (D2) and, by extension, importing a
  third-party songbook.
- **The proprietary native audio/scoring component** of the original app. KANTA keeps its own
  SoundFont synthesis and its own scoring (`THIRD_PARTY_NOTICES.md`, `docs/ARCHITECTURE.md`).
- **The legacy bundled media.** KANTA ships no third-party songbank or clip by licence design.
- **Signed or store-distributed builds.** No signed IPA, no Play/App Store submission, no
  TestFlight.
- **iOS ↔ Android companion interoperability.**
- **Any correctness claim about a Meg payload's contents**, because the format is undocumented.

## G. From review mining (2026-10-29)

Evidence, verbatim quotes and the rejected rows are in
[`docs/reviews/2026-10-29-karaoke-review-mining.md`](reviews/2026-10-29-karaoke-review-mining.md).
These are the accepted rows; the ranking rationale and why each complaint was accepted or
rejected live there.

| ID | Item | Acceptance criterion | Effort |
| --- | --- | --- | --- |
| G1 | Long-session soak: a full set with background video plus the front camera, watching for stalls, leaked surfaces and dropped frames | A 30-minute soak on a real device shows no stall, no unbounded memory growth and no dropped audio, with logs | L |
| G2 | Melody/guide volume: implement per-voice mixing **or** remove the sliders (same defect as D8) | Moving the slider changes the mix audibly and persists, or the slider is gone | M |
| G3 | Lyric timing: prove the rendered clock holds through a tempo change and across a long song, and add a persistent per-song lyric offset for badly authored files | A test asserts lyric position against the rendered frame clock; the offset survives restart | M |
| G4 | Pitch accuracy: calibrate `DryPitchDetector` on real recorded vocals rather than synthetic tones, and label the scorer honestly | A calibration report shows octave-error rate and latency on real vocals; the score panel does not overstate accuracy | M |
| G5 | Local state durability: the queue, settings and library survive kill, upgrade and a simulated write failure | Kill mid-session and relaunch with queue, settings and library intact; a write failure surfaces a message, never silence | M |
| G6 | Recording: decide explicitly between opt-in local recording with honest playback, or documenting that KANTA does not record | Either a local recording replays and deletes with no network path, or the absence is documented and the UI never implies recording | L |
| G7 | Copy honesty: never say "unlimited", and make an empty search distinguish "no match in your library" from "no library configured" | Both states are distinguishable in the UI and no string claims an unlimited catalogue | S |
| G8 | Own-track import stays first-class: the folder scan remains the primary path, and the legacy importer is reachable rather than required (D4) | A user file added to the content root is searchable with no conversion step | S |
| G9 | No paywall, ever: nothing gates a feature behind payment, and the site states the offline-first model plainly | No user-facing surface gates a feature behind payment | S |
| G10 | Keep the compact-workspace layout stable, and favour information density over large imagery on list surfaces | Song, artist and singer all visible without opening an item; workspace structure unchanged across two consecutive releases | S |
| G11 | Keep Kanta-Connect LAN-only, coded and single-session; add no profiles, chat or public sharing | An unknown party on the network cannot discover, message or control a device without the displayed code | S |
| G12 | Every failure state is actionable in one line: what happened, and the one next thing to do | No bare "something went wrong"; each error names a cause and one next action | S |

## F. Recently closed

| Item | Closed by | Evidence |
| --- | --- | --- |
| Milestone 0 — rebrand foundation | `8d14c91` | `tools/check-product-branding.py` 4/4 |
| Milestone 1 — content / catalog / stage overlays | `fa37794` | Android validation on `3a81dd6` |
| Milestone 2 — stage binding, score sink, MKMD decode, Meg importer | `1dfecd7`, `2a01654` | Android validation [#40760610063](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40760610063) |
| Milestone 3 — Kanta-Connect host, settings persistence | `2a01654` | Android validation green; `KantaConnectHost` dispatches snapshot/search/reserve/cancel/play/pause/next |
| Milestone 4 — score celebration | `5345d06` | Android validation [#40792870953](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40792870953); `ScoreCelebrationRegressionTests` on a JVM |
| Milestone 4 — diagnostics (S14) | `f6fd0b6` | Android validation [#40796411592](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40796411592); the guard asserts the pairing code is withheld |
| Milestone 5 — Kanta-Connect naming + QR pairing | `dc03bf8` | Android validation [#40826684007](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40826684007); golden matrices decoded by an independent scanner |
| Android CI was failing **open** | `c784fe5` | `set -o pipefail` on the Gradle step; two real defects surfaced immediately |
| `MkmdDecoder` magic constant and AES padding | `1dfecd7`, `2a01654` | Every real payload was rejected before this |
| iOS PR #113 navigation regression | `b7d69f2` | Walkthrough green before `80ae286`, red after, green again after the revert |
