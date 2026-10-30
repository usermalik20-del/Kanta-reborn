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
| A2 | **Attach the final domain once the is-a.bot registration completes** | The public site is moving from `https://kanta-public.vercel.app` to the KANTA domain registered through is-a.bot. **The wiring has landed** (see the closed table), so what remains is the operator action, not code. | The upstream **is-a.bot registration request is still in review**, so the final hostname is not yet fixed — the workflow takes it as a validated input precisely so this does not need a code change. | The owner dispatches `Configure KANTA Vercel domain` with the final hostname; the domain resolves to the site with a valid certificate, and then A5 follows. | S |
| A3 | **New exact-source packaging run** | The durable release `v0.1.0-workspace-redesign` and the public manifest are pinned to native source `41c8ddde`, which predates every milestone in this ledger. The live site therefore describes an older build. | `apps/web/downloads.json`; `docs/VERIFIED_DOWNLOADS.md`. | An exact-SHA packaging run produces identified artifacts for a current main SHA, checksums are recorded, website contract validation passes, and the manifest plus the release are advanced together. | M |
| A4 | **Physical-device acceptance (Android and iOS)** | Every acceptance claim in this repository is host-CI evidence. No milestone has been run on a real handset. | `docs/TESTING.md`: a green build is not device acceptance. | One real Android device and one real iOS device complete: search → reserve → play with lyrics → countdown → score → queue advance, plus a background photo and the front camera, with a screenshot or logcat excerpt attached. | L |
| A5 | **Sweep absolute URLs to the new domain** | A half-moved domain breaks canonical URLs and the in-app legal and support links users tap. **`tools/verify-website.py` hard-codes `PUBLIC_BASE_URL` and asserts every sitemap URL's netloc against it**, so the guard, `apps/web/sitemap.xml` and the page content must all change **together** or website validation fails. | **77 occurrences of `kanta-public.vercel.app` on main:** 10 in `apps/web/sitemap.xml`, 2 in `tools/verify-website.py`, and user-facing in `apps/ios/Sources/Features/Legal/LegalView.swift` (4), `apps/ios/PRIVACY.md` (3) and `apps/android/app/src/main/assets/privacy.txt`. Also 14 in `docs/evidence/website/2026-10-15/manifest.json` and refs under `recovery/`, which are historical records. | Every live reference resolves to the new domain; the guard's base URL, the sitemap and the in-app links are updated in one change; historical evidence under `docs/evidence/` and `recovery/` is left byte-identical with a note explaining why; website, branding and docs guards all pass. | M |

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

## H. Pitch features (Pitch Meter Game + Learning Assistant)

These landed on main in `2cf26f5`/`af0566f` and were repaired in `e8be580`/`cffa1ce`/`8210340`
after Android validation failed three times. What is left:

| ID | Item | Why it matters | Acceptance criterion | Effort |
| --- | --- | --- | --- | --- |
| H1 | **Switch pitch estimation to SPICE (TFLite)** | Landed (`ed7e222`): `SpicePitchEstimator` runs the SPICE model behind the framework-free `PitchSource` port, fed the captured block the monitor already gets, with the frame detector as the fallback. | **Remaining: device acceptance.** The confidence threshold (0.5), inference latency on real hardware and the calibrated accuracy of the estimate have not been measured on a device. | S |
| H2 | **Amend the architecture in writing for a TFLite dependency** | Landed: a "Pitch estimation decision" section in `docs/ARCHITECTURE.md` records the single-dependency exception to dim-10 with its reasoning, `apps/android/THIRD_PARTY_NOTICES.md` declares the runtime and the model, and the architecture contract's DSP row names the implementation and its tests. | **Remaining: nothing for this exception.** Any further dependency needs its own written exception. | - |
| H3 | **Resample and reframe for the model** | Landed: `core/SpicePitch` resamples 48 kHz to 16 kHz with a 33-tap Hann-windowed sinc low-pass at 4.5 kHz (20 kHz rejected by 65 dB) and applies the model calibration `semitones = 24.37 + 62.51 * pitch`. Checked by golden vectors in `SpicePitchRegressionTests` inside the host-JVM `coreRegression` task. | **Remaining: nothing.** | - |
| H4 | **Song-aware target pitch** | Landed (`c74f991`): `core/MidiLyrics` now reads key-signature meta `0x59` (first event wins) and exposes it on `Document`; `core/MidiKey` converts the fifths count into a tonic pitch class, a key name and a target note in the octave of middle C (every real key lands between 261.6 Hz and 493.9 Hz). `SessionController` carries the playing song's key and exposes `practiceTargetHz()`/`practiceKeyName()`; the toolbar and the learning assistant both use it. `MidiKeyRegressionTests` covers all 30 signatures, the names, the targets and end-to-end decoding of a generated SMF. | **Remaining: device acceptance** - that the target actually changes when a song with a key signature is played. | S |
| H5 | **`PitchMeterView` fabricates a reading** | Landed: the meter no longer seeds `currentPitchHz = targetPitchHz` with accuracy 1.0. It shows a neutral indicator and an em-dash until the microphone reports something, and a new target clears the previous reading so it cannot be read as a result. | **Remaining: nothing.** | - |
| H6 | **Relocate `AchievementSystem` out of `core/`** | It needs `SharedPreferences` and `Color`, so it is excluded from `compileCoreRegression`. `core/` is meant to be framework-free, and the exclusion list is growing instead of the boundary being enforced. | It lives beside the other Android-facing classes (`MicrophoneInput`, `PartyAudio`) and the exclusion entry is removed. | S |
| H7 | **Persist pitch settings** | Preferred target pitch and assistant sensitivity reset every session; listed in the owner's enhancement set. | Both survive an app restart through `SettingsStore`. | S |
| H8 | **Shortcut access to the pitch features** | They are reachable only from the workspace accessory bar. | Reachable from the Controls menu as well, without adding a second entry point that bypasses the single-accessory rule. | S |

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
| Domain wiring landed (the owner's `chore/configure-is-a-dev-domain`, with two fixes) | `065f956` | Merged after Android validation green on the branch (`32f2644`). Post-merge production deploy [#40886115873](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40886115873) ran with the domain step **skipped** and pull/build/deploy-to-production all succeeding, which is the push path this merge had to preserve. |
| Main was red: pitch scaffold did not compile | `8210340` | Android validation [#40918434667](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40918434667). Three rounds: syntax break in `StagePresenterImpl.hideIntro()`; a second microphone opened by `AudioServiceImpl` against a package-private `MicrophoneInput` and a non-streaming `DryPitchDetector`; then 22 further errors the syntax break had masked (duplicate `PANEL`, 18 `final` fields assigned from an `init()` helper, `unlock(String)` vs `unlock(Achievement)`) and an unreachable `case` that also made the Visuals accessory open Pitch Practice. |
| Android CI was failing **open** | `c784fe5` | `set -o pipefail` on the Gradle step; two real defects surfaced immediately |
| `MkmdDecoder` magic constant and AES padding | `1dfecd7`, `2a01654` | Every real payload was rejected before this |
| iOS PR #113 navigation regression | `b7d69f2` | Walkthrough green before `80ae286`, red after, green again after the revert |
