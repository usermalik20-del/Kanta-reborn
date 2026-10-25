# Roadmap

## Companion Remote candidate in PR #51 — October 18, 2026

Owner-approved requirement: the supplied KANTA remote design opens inside this same app when Companion Remote is selected, with no separate remote app. Both iOS and Android must support it, plus paired browser guests entering the host's LAN address. Host/source queue ownership, authentication and explicit sensor permissions remain authoritative. The owner moved implementation into PR #51. HTTPS hosting and a shared locally served browser/built-in remote are now candidate source; native/browser/device acceptance is pending and this is not released. Expanded unsupported controls remain explicitly unavailable. See the [shared roadmap and original design reference](../../../docs/COMPANION_REMOTE_ROADMAP.md).


**Current merged source baseline (October 10, 2026):** `kanta-ph/Gelo-Karaoke-iOS` `main`, merge commit [`6dcdf5f`](https://github.com/kanta-ph/Gelo-Karaoke-iOS/commit/6dcdf5f675572771f0377fe2197c2cee41b4bf5c), PR #44 merged and PR #40 closed as superseded. The last green Core CI ([run #38003895381](https://github.com/kanta-ph/Gelo-Karaoke-iOS/actions/runs/38003895381)) validated **173 Swift tests**, an unsigned Release iOS Simulator build, and live Host/CONTROL simulator startup on an earlier PR head; it **did not rebuild the final in-app Legal & Privacy edit or this merge commit**. KANTA has **no verified signed TestFlight upload or physical-device acceptance** for this merged batch. Website `/legal` and `/privacy` are live and were checked on production. Historical draft/pending paragraphs below record their original dates and must not supersede this status.


## Current status — 2026-10-10 (source merged; release gates open)

The offline playback path is implemented for loose MIDI/KAR/MP3/WAV,
including synchronized lyrics, local stage backgrounds, Host/Remote LAN
control, and playback-aligned live scoring. Legacy third-party karaoke container
compatibility engineering is complete for the proven offset-table +
protected payload v1 path: validated title offsets resolve selected spans lazily,
and protected payload v1 payloads are decrypted/inflated only on demand into a
temporary local playback cache. A matching authorized current
`media container` is still required for final real-library acceptance testing;
the older public sample is a different addressing/protection generation
and is deliberately rejected rather than guessed.

## Phase 0 — Reference analysis
- Catalog supplied APKs and library files. **Confirmed.**
- Map permissions, media formats, folder conventions, and LAN behavior.
  **Implemented for the paths required by current iOS parity; remaining
  opaque Android-only metadata is documented but not treated as a
  shipping dependency.**
- Document `index/container/meg*`, MIDI/KAR, and SoundFont findings.
  **Implemented for confirmed current `index/container` playback;
  unproven `meg*` variants remain fail-closed.**
- Feature-parity matrix. **Maintained.**

## Phase 1 — Native iOS foundation
- SwiftUI app shell. **Implemented.**
- Dependency composition/domain protocols. **Implemented.**
- Files/iCloud Drive folder import + persistent security-scoped
  bookmarks. **Implemented as Bring Your Own Library.**
- Commercial asset guard preventing tracked third-party karaoke payload binaries.
  **Implemented in hosted CI.**
- Shared bookmarked playback library. **Implemented.**
- Stable loose-file catalog/search. **Implemented.**
- Shared local controller. **Implemented.**
- Routed file/MIDI playback. **Implemented.**
- Host UI wired to actual imported songs. **Implemented.**
- Automated tests + iOS Simulator CI. **Implemented.**

## Phase 2 — Karaoke playback
- Standard MIDI/KAR parsing. **Implemented.**
- Tempo-aware lyric timing. **Implemented.**
- MIDI playback plan/state reconstruction. **Implemented.**
- SF2 output. **AVAudioUnitSampler baseline implemented; real-device
  fidelity validation with the user's preferred SoundFonts remains a
  release-validation task rather than a code blocker.**
- MP3/WAV playback. **Implemented.**
- Pitch/tempo/volume. **Implemented.**
- Host queue/transport UI. **Implemented with an inline non-obstructive CONTROL ᜃᜓᜈ᜔ᜆ᜔ᜍᜓᜎ᜔; second-device remote is optional.**
- Lyrics presentation/highlighting. **Implemented baseline with
  playback-synchronized previous/current/next cues.**
- Background photo/video. **Implemented baseline with deterministic local image/video resolution and muted looping Host presentation. UI is intentionally de-emphasized to a simple Background visuals toggle.**

## Phase 3 — Legacy library compatibility
- Decode legacy index metadata. **Complete for catalog/playback:
  version-tolerant title parsing plus the validated trailing offset
  table are implemented; unused opaque fields remain intentionally
  unnamed rather than guessed.**
- Map title-index/trailing table to `media container`. **Complete: strict
  cardinality/order/blob-bound validation and lazy per-record spans are
  implemented.**
- Direct-open existing container library. **Complete for the confirmed
  current format: native MIDI/WAVE/ID3-MP3 spans direct-open, and protected payload
  v1 spans use bounded AES-CBC + raw-DEFLATE materialization into the
  temporary cache. Unproven ZIP/GZIP/arbitrary variants fail closed.**
- Compatibility tests against representative fixtures. **Complete for
  the implementation boundary: synthetic byte-exact protected payload
  decrypt/inflate coverage, malformed/truncated cases, offset mapping,
  and fail-closed type-9/unsupported-layout tests are hosted in CI.
  Final acceptance against a matching authorized current encrypted
  library is tracked as release validation.**
- Conversion fallback only if direct-open is impractical.
  **Not required for the confirmed current format; retained as a future
  fallback for genuinely incompatible variants.**

**Phase 3 engineering status: complete.** The parser, validated
offset-to-blob mapping, bounded protected payload v1 materializer, catalog/playback
integration, failure policy, tests, and architecture documentation are
mergeable when normal Swift/iOS CI passes. Real-library acceptance is
a product-hardening gate and must use a matching authorized current
`index/container` pair; an older public sample must not be substituted
for that proof.

## Phase 4 — Unified Host + Remote
- Primary same-device CONTROL ᜃᜓᜈ᜔ᜆ᜔ᜍᜓᜎ᜔. **Implemented inline in Host with search, queue, transport, volume, pitch, tempo, and background toggle; it expands without covering the stage.**
- Host authoritative controller. **Implemented locally.**
- Bonjour/mDNS discovery. **Implemented with Network.framework host
  advertisement and Remote browsing.**
- Pairing/PIN or QR bootstrap. **Six-digit host PIN plus authenticated
  framed PIN exchange implemented; QR is optional polish.**
- Protocol v2 framed domain envelope. **Implemented with bounded
  length-prefixed JSON, version validation, and fail-closed sequencing.**
- second-device CONTROL search/queue/transport controls. **Implemented baseline:
  paired Remote supports bounded catalog search, enqueue/remove,
  transport, volume, pitch, tempo, now-playing, and queue state.**
- Multi-controller state synchronization. **Authoritative snapshots are
  consumed by paired remotes every 500 ms and after accepted commands;
  presence/conflict UX remains polish.**

## Phase 5 — Scoring and performance
- Microphone pipeline. **Local AVAudioEngine pitch monitor implemented
  with explicit permission/start; observations stay in memory and audio
  is not recorded/uploaded.**
- Pitch/timing scoring. **Implemented baseline: conservative MIDI target
  extraction feeds a playback-aligned live scoring session; Host
  presents overall/pitch/timing results and resets safely on seeks or
  pitch changes.**
- Latency calibration. **Playback/microphone clock alignment and bounded
  manual compensation implemented; automated per-device calibration
  remains real-device hardening.**
- Battery/memory profiling. **Pending real-device profiling and
  optimization of microphone/autocorrelation coexistence with playback.**

## Phase 6 — Product hardening
- Device compatibility floor. **Defined: iPhone XR and newer on iOS 17+, and iPad 9th generation and newer on iPadOS 17+. KANTA is maintained against current SDKs through iOS/iPadOS 27. Adaptive performance protects audio/control/scoring before visual effects, and user media is never recompressed to meet device limits.**
- Recovery/corrupted library handling. **Implemented baseline:
  unreadable registry state is quarantined as a preserved backup;
  failed asset resolution/load/play does not silently consume queued
  songs; controller state changes commit only after playback succeeds;
  and library/protected payload failures expose actionable messages. Broader
  real-device malformed-media acceptance remains a release test.**
- Accessibility and iPad layout polish. **Implemented baseline:
  adaptive NavigationSplitView shell for iPad, scalable stage sizing,
  VoiceOver labels/values for Host and Remote transport/volume controls,
  and lyric-first accessibility semantics. Real-device Dynamic Type,
  VoiceOver, Switch Control, and iPad orientation validation remain a
  release test.**
- Privacy/permissions review. **Implemented baseline: explicit privacy
  manifest, CI permission/privacy guard, microphone/local-network/Files
  data-flow review, and no unused sensitive permission declarations.
  Authenticated LAN transport is still plaintext and remains a Phase 6
  confidentiality hardening gate.**
- Third-party license audit. **Current runtime dependency inventory is
  documented and contains no vendored third-party runtime SDK/content.
  Commercial BYOL and third-party-format interoperability legal review
  remains required before release.**
- Current legacy-library acceptance. **Pending a matching authorized
  current `index/container` pair for end-to-end protected payload validation; the
  public 1.0.0 sample is intentionally excluded because its type-9
  addressing/protection generation differs from the supported format.**
- Signing/distribution. **Pre-signing pipeline implemented: release
  metadata is CI-validated, the generated iOS target builds under the
  Release configuration without signing, and the TestFlight/App Store
  acceptance checklist is documented. Apple Developer signing,
  physical-device archive validation, and upload remain account-bound
  release tasks.**



## 2026-10-06 reliability batch (proposed, validation pending)

Completion identity, controller transaction serialization, failed-handoff recovery,
SoundFont prewarm/cache safety, verified iCloud replacement, privacy reasons, and
manual/draft-aware CI are implemented on `fix/reliability-hardening-handover`.
Regression tests are added but have not run under Swift/Xcode. Do not mark these
release gates complete until hosted and hardware acceptance succeeds.
See [the per-change handover](HARDENING_HANDOVER.md) for next steps and remaining gaps.


## 2026-10-07 repository-wide review (Manila)

A further hardening batch covers app imports, storage transactions/reload scope,
import responsiveness, microphone cancellation, remote lifecycle callbacks,
paused MIDI behavior, SoundFont metadata/fallback, parsing and numeric bounds.
Ten regression cases were added. Static guards passed; Swift tests and the iOS
app build are deliberately deferred per owner instruction. Core CI #109 remains
107/108 passing; its backup correction has not been confirmed by a new run.
Do not close release gates based on this source review.



## Current release milestone — 2026-10-07

PR #25's simulator/UI validation is complete at baseline beb63b5. Current work is
internal TestFlight readiness and physical-device acceptance, not feature expansion.
Follow DEVICE_ACCEPTANCE.md and LAN_SECURITY_DECISION.md. Apple signing inputs,
actual device results, authenticated LAN transport, publisher identity/contact,
and final legal review remain open. New release-tooling changes require their own CI.



## 2026-10-08 — original bundled still-image pack

Ten owner-selected generated PNGs replace the ten procedural still slots in the
existing stage picker. Original bytes are bundled for offline Host/fullscreen
use; persisted selection identifiers remain stable. Motion presets remain
procedural animations, not bundled video files. Static integrity/provenance
checks are required; exact-head hosted iOS build and device acceptance must be
reported separately.

## 2026-10-08 — centered karaoke lyric presentation

Shared centered two/three-line page presentation, green source-cue highlighting,
and a small lower-right KANTA watermark at 75% opacity are implemented. KAR
line markers and timed syllable fragments are preserved; line-only sources
highlight whole lines. Accurate synchronization requires source timed lyrics;
untimed MP3/WAV files do not gain inferred lyrics. Device/audio-output latency
acceptance remains separate from deterministic timeline tests and screenshots.

## 2026-10-08 — responsive singing screen

Portrait presents permanently available controls BELOW centered lyrics, in a
scrollable footer. A landscape switch requests public UIWindowScene geometry
rotation, and landscape prioritizes an immersive stage with on-demand controls.
Image and video surfaces preserve their playback identity and adapt by bounding
the aspect-fill stage to the new dimensions without transcoding/resizing files.
Automated screenshots cover both portrait devices and a normalized, real-scene
landscape screenshot on iPhone. iPadOS windowing can deny programmatic rotation;
iPad landscape requires physical/window-resize acceptance. Physical-device rotation, background-video and amplifier acceptance
remain release-validation tasks.

## 2026-10-08 — scalable indexed-library catalog (48k songs)

Implemented a metadata-only on-device SQLite/FTS5 song search index,
transactionally refreshed from validated indexed/loose BYOL libraries,
with digest-based no-op re-indexing. CONTROL now browses 24 results per
page and paired LAN Remote browses 50 per page with authenticated,
bounded and backward-compatible protocol-v2 offsets. User media/SF2
is not indexed or bundled; playback remains on-demand through the
existing controller. Synthetic 48k-entry index, rollback, malicious-query
and pagination regressions are added. Completion requires hosted Core
CI/Release simulator build plus authorized real-library/device acceptance.

## 2026-10-08 — title-only search from an imported library index

A previously supplied index contained 47,998 parsed titles but was not
accepted as a playable song library by the strict media-offset validator.
The search import now recognizes standalone structurally valid indexes,
extracts catalog-only titles into the existing SQLite/FTS5 cache, and makes
them available in CONTROL and paired LAN Remote with a clear unavailable
label. Stable song IDs are shared with the independently validated playable
path; Reserve/Play remain unavailable until compatible media is present.
Synthetic 47,998-title, opaque-identifier, SQLite-status and controller-queue
regressions verify separation. Hosted CI and user-owned container playback
validation remain distinct release gates.

## 2026-10-08 — 48k index acceptance follow-up

Completed a source-count UX and LAN search burst-hardening batch.
Library cards display imported searchable/playable/catalog-only totals
from the active library actor, including large standalone indexes.
Paired Remote search debounces keystrokes, preserving request-ID
correlation and existing bounded pagination. Added metadata-heavy
standalone-index and 47,998-title status regression tests.
The owner's previously supplied private index was locally verified
without distributing it. Remaining: physical device Files/iCloud import,
48k first-index timing/memory, valid matching media playback and
signed internal TestFlight build (Apple credentials required).

## 2026-10-08 — printable KANTA songbook + numeric code search

Added one unique five-digit, one-based KANTA code per title in each indexed
library, preserving source-scoped SongID and the playable/catalog-only
boundary. CONTROL and paired LAN Remote display reference codes and search
by them through existing SQLite/FTS5. The main navigation and CONTROL now
open a paginated KANTA Songbook with title/code/artist/status lookup and
full CSV export/ShareLink. The owner's private index was used outside the
repository to produce a 47,998-title, 473-page printable A4 PDF plus CSV.
No third-party title corpus is checked into the app. Synthetic 48k export
and row-ordinal code tests enforce matching code/search behavior. Physical
iOS device import, user library refresh, share sheet behavior and original
blob playback remain open acceptance gates.

## 2026-10-08 — post-session Favorites and Songbook artist names

Implemented playback-aware session history; optional end-of-session prompt to
save played (not merely queued) songs as a custom-named Favorites collection;
persist, rename, delete and reload collections into Up Next from both Home
and Host. Requeue resolves against current library and skips missing/unplayable
media, preserving existing queue entries. Songbook now explicitly displays
known artist names or "Artist not provided" and lets users safely set missing
artists locally; modified artist metadata joins FTS lookup and CSV export.
Original user-supplied 47,998-title index does not provide reliable native
artist fields, so downloadable PDF/CSV can include the column but must not
invent performers. Hosted Core CI/Release simulator and hardware checks remain
release validation gates.

## October 2026 — Soundcard / Live Vocal FX

Implemented optional live mic FX using a shared scoring input graph:
Dry, Room, Studio, Hall, Cathedral, Plate, Echo, plus wet reverb, echo,
bass, treble and mic-volume controls from Host. Settings/privacy disclose
what is and is not processed. Input monitoring is fail-closed and off
on launch; wired/USB or line-out only, no automatic speaker/Bluetooth
loopback. Native AVAudioUnit reverb/echo/EQ and pure parameter/route
tests require no third-party plug-in or network service.

Release gates: hosted Swift test + iOS Release simulator build; actual
wired/USB mic monitoring, latency, amplified room feedback/route change,
device memory and soundfont playback interaction unverified until
real-hardware acceptance. Music-output mastering EQ is a separate
future option, not part of this vocal monitoring batch.

## 2026-10-08 — consolidated documentation and public website sync

Reviewed the merged KANTA feature milestones from PRs #30–#38 against current
`main`. Updated the public static pages: Home landing, detailed Features,
Support and Privacy; aligned README, feature inventory, App Store metadata,
Support, privacy review, release/device acceptance and engineering AGENTS.
There were no preexisting DOCX files in the accessible repo/Library, so new
dated, render-reviewed Word documents were produced as conversation artifacts:
product specification, end-user guide and engineering handover.

Source implementation and passing simulator CI are consistently separated from
TestFlight signing, actual 47,998-title import/playback, on-device Favorites
and live wired/USB mic Soundcard testing. The site does not include library
contents, third-party song catalogs, private contacts, or claims of encrypted
LAN remote control. Static hosting deployment and path smoke tests are tracked
separately from the source PR merge.

## 2026-10-08 — Phase 1 + 2 implemented, Phase 3 placeholder

Phase 1: conservative vocal polish adds dynamic compression,
downward expansion/noise-gate control, high-frequency sibilance reduction,
short-delay doubling, configurable echo time and peak limiter, plus
OPM/Pop/Rock/Duet/Studio/Party presets. It shares the existing dry
room-mic scoring input and opt-in wired/USB monitor gate.

Phase 2: Host party pad with eight KANTA-generated samples, volume,
Stop SFX and four-voice overlap cap, short Files custom clip imports
and original restore; paired LAN Remote triggers the Host's SFX.
The Host's own audio sample bytes remain private and are never sent
over the current unencrypted LAN transport.

Phase 3: render disabled placeholder for pitch correction,
harmonies and tempo-aware effects. **Not implemented or active.**

Before any TestFlight/release claim: real device mic/USB latency,
feedback/routing, volume, synthesis, Files import, Remote pairing,
interruption/recovery and thermal acceptance NOT RUN.

## October 2026 — Stage / Singer / Intermission milestone

Implementation scope on the current working branch:
- Batch A: dedicated noninteractive iOS external-display stage scene
  (HDMI/USB-C; compatible extended-output AirPlay destinations only);
  linked lyric/visual rendering with controller UI kept on the main device;
  in-app input routing, level meter and scoring-offset diagnostics.
- Batch B: singer-labeled Host/Remote reservations, move-up/down within
  authoritative PlaybackQueue, editable/paged playlists stored beside
  existing session Favorites, and opt-in user-owned looping intermission
  track with fade on karaoke transport transitions.
- Phase 3 / Batch C future: Vocal Fit with consent-based singer range
  profiling, evidence-gated melody search and suggested transpose; optional
  quizzes on authorized metadata; real-time pitch correction/harmonizer
  after latency and microphone/amp hardware validation. **Not shipped.**

Gates: one exact-head Swift Core + Release iOS Simulator run; actual
HDMI/USB-C extended display and nonmirrored AirPlay, route changes, mic
capture under amplified external audio, singer queue ordering, 48K-scale
playlist pagination, intermission fade and external output loudness are
NOT RUN until hardware acceptance. Avoid promotional claims before CI/merge.

## 2026-10-08 — Private web Songbook (draft PR, not yet released)

A separate private website reader is staged on `feat/private-web-songbook-flipbook`:
`PublicSite/songbook.html`, `songbook.css` and `songbook.js`. On iPhone/iPad,
`KantaSongbookView` now offers **Create private website copy**. This calls
`BookmarkedKaraokeLibrary.exportSongbookCSV()` for user-owned code/title/
known artist/availability metadata, then `KantaPrivateSongbook` creates a fresh
AES-256-GCM encrypted `.kanta-book` and a random 128-bit / 32-hex-character
access code visible on the phone. The envelope is
`{format:"kanta-private-songbook",version:1,payload:base64(nonce|ciphertext|tag)}`;
the browser derives the AES key from SHA-256 of UTF-8
`KANTA-SONGBOOK-V1:` plus the uppercase ungrouped code, and decrypts via WebCrypto.

The website itself is public but contains **zero publicly exposed song titles**.
The owner must provide BOTH the encrypted file and the correct phone-displayed
code. Search by title/artist/KANTA code, responsive animated page turns, plaintext
CSV download and browser Print/Save as PDF work locally. The site does not
upload the file, secret, searches or catalog; no account, server-side library,
localStorage or cookies were added. Do not call this six-digit PIN verification,
server authentication, expiring OTP, automatic cloud sync or a revocable session.
The access code is export-specific, cryptographically strong and does not expire.
Browser memory, downloads, OS print history and shared files are outside
KANTA's ability to revoke/securely erase. CSV/PDF are plaintext once exported;
users must protect them. Unverified unknown artist fields are never guessed;
catalog-only songs cannot become playable from web pages.

**Acceptance:** Swift CryptoKit codec regressions were added; exact-head Swift
unit tests, iOS simulator build, mobile/desktop browser WebCrypto round-trip,
print-to-PDF, real iPhone/iPad export/share flow, hosting preview and deployment
remain **not run/not confirmed**. The draft branch must not be represented as
merged, public-live or TestFlight tested. Respect limited Actions minutes;
no GitHub CI was launched as part of this batch.

## 2026-10-08 — Native voice title/artist/code search (review stage)

Staged: voice search entry points in Host CONTROL, Songbook and paired
Remote; explicit Filipino/English (PH) locale choices; speech/mic
permissions; brief recording task with lifecycle cleanup; title/artist
transcription and Tagalog/English speech command normalization;
spoken KANTA numeric code support; existing bounded FTS5 or paired-host
search and user review before Reserve. Plain text search remains
available without a network connection or speech permission.

**Real hardware/accuracy gate:** build and review on iPhone XR and
supported iPad; run labeled sample phrases across at least quiet and
amplified rooms, Filipino, English and Taglish, title/artist/number
queries, varied accents and noise, permission denial and unavailable
locale. Measure top-1/title matching and fallback success rates; don't
promise 90–100% without empirical evidence. iOS Speech locale availability
and potential network processing must remain visible.


## 2026-10-08 — Filipino model download implementation increment (draft)

User approved an optional one-time Filipino language-model download.
Staged the real local Core ML model manager/capture and settings:
WhisperKit `v1.1.0` exact SPM pin, official multilingual
`openai_whisper-base` Hugging Face snapshot (~147 MB + cache/tokenizer),
manual Download/retry/Remove, shared download status, app-local
backup-excluded storage, temporary ~8-second mic capture, `tl`
decoding, and existing title/artist/code search. Native Apple English
(Philippines) speech continues separately. No models or songs are
shipped in the app bundle, and normal karaoke/search still runs without
having downloaded the pack.

**Quality milestone remains open:** verify on XR / iPad 9 physical
devices, run the Filipino/Taglish corpus with ≥90% usable@3 per language
and noise cohort, check p95 inference latency/thermal, download
integrity and offline re-use, mic/Soundcard route transitions, voice
accessibility, third-party licenses, and App Review data disclosures.
Draft code does not equal CI-green or a shipped TestFlight build.

## PR #40/#44 integration history — 2026-10-09 (merged October 10)

Optional key/scale vocal pitch correction and controls now coexist with
existing compressor, expander, de-esser, doubler, echo, reverb and limiter
in merged source. Harmonizer is still Coming Later. Earlier-head Swift Core
(#38003895381) passed 173 tests, Release iOS Simulator and live Host/CONTROL
startup after a symbolicated intermission volume stack-overflow fix. The final
Legal & Privacy SwiftUI changes were merged without another exact-head build.
Device microphone/latency/scoring, model accuracy and signing remain open;
no signed app delivery claimed.


## Reconciled release milestones — 2026-10-10

- **Source merged:** website-matched visual branding, English/Filipino/Taglish searchable speech with optional offline model, experimental monophonic pitch correction, vocal polish/SFX, Host SIGSEGV initialization fix, customer-friendly Legal & Privacy, private encrypted Songbook source, and previously merged stage/control features.
- **Web deployed:** customer /legal, /privacy and static public Songbook reader; no publicly hosted user catalogue.
- **Verification:** earlier-head run #38003895381 passed 173 Swift tests plus Release iOS Simulator and live Host/CONTROL on simulator. Do not represent this as validation of final legal SwiftUI edits.
- **Still gated:** exact-merged-head signed build, TestFlight/internal delivery, iPhone XR/iPad 9 performance and offline speech acceptance, mic/amp route and pitch-correction fidelity, encryption decision for LAN, privacy/legal contact and signing. Documentation synchronization alone does not close any hardware gate.

## October 10, 2026 — PR #45 starter pack

Source integration complete for three original offline starters, bundled procedural
sound, existing-controller playback/queue and persistent remove/restore. Hosted
Swift/Release simulator validation, merge and existing internal TestFlight release
remain pending. See Docs/STARTER_KARAOKE_PACK.md for the acceptance contract.


### Starter karaoke pack status — October 10, 2026

- Completed in PR #45: three first-party KARs, procedural tone, one store/catalog/controller, timed lyrics, My Set controls, durable remove/restore and deterministic/provenance guards.
- Core CI #242 (run 38019883726) passed on 1ee391e: 182 Swift Testing cases, an unsigned Release iOS Simulator build, Host/CONTROL liveness, all three starter songs playing with the native sampler, timed lyrics, pause/seek, FIFO queue, persistent removal and offline restore after process restart. Simulator screenshots and JSON are in artifact #11657971189. The separate UI Screenshots workflow was skipped, not passed.
- Pre-merge: align three Word docs, active Markdown, all relevant website pages and in-app Legal view; rerun exact-head CI for SwiftUI edits.
- Post-merge: reconcile live site deployment; gated Apple-signed internal TestFlight and physical device acceptance. External public shipment remains separately blocked.

## 2026-10-10 — Legal pitch DSP enhancement (draft branch)

- **Source staged, not merged/released:** platform-neutral
  `VocalPitchFollower` adds note-hysteresis, safe silence/key resets and
  focused Swift Testing coverage to the existing native Apple TimePitch mic
  correction pipeline. No new third-party runtime binaries or audio graph.
- **OSS path selected for evaluation only:** Signalsmith Stretch (MIT) as
  isolated quality/formant benchmark, SoundpipeAudioKit/AudioKit (MIT) as
  alternatives subject to transitive-license and single-engine compatibility
  review. No package/vendor copy is included yet.
- **Unfinished gates:** exact-head Swift and Release iOS CI (owner authorization
  required), physical XR/iPad 9 wired/USB input and amplifier routing, latency,
  feedback, sample-rate transitions, attribution and commercial release review.
  No merged/deployed/TestFlight claim.
- **Decision record:** `Docs/VOCAL_DSP_LICENSE_AND_ACCEPTANCE.md`.

## PR #46 progression — actual MIT PitchCorrect and guided onboarding

- **Source implemented:** SoundpipeAudioKit v5.7.4 `PitchCorrect`
  as an AVAudioUnit in the existing microphone graph; backend selector
  defaults to this DSP only after correction is explicitly enabled.
  Apple Native is an available comparison/fallback.
- **Backwards compatibility:** settings decode, note-scale parameters,
  original dry scoring, feature ownership, route mute and no second
  audio engine stay intact.
- **Legal work staged:** SoundpipeAudioKit, AudioKit, AudioKitEX and Tonic
  full MIT notices; KissFFT BSD-3-Clause full notice, bundled in-app
  and rendered at `PublicSite/licenses.html`. Transitive exact resolved
  versions still require audit.
- **Customer onboarding staged:** new illustrated `/guide` page
  with USB/Lightning/USB-C wiring topologies, DSP walkthrough,
  amplifier gain safety, first sound check, other feature guides
  and accordion troubleshooting. No authentic device screenshots
  or live deployment claim.
- **Open:** exact-head macOS CI permission, hardware audio latency/
  thermal/hardware-route acceptance and UX checks, iPhone/iPad
  release captures, Word companion sync, site production deployment,
  signed TestFlight gates and commercial legal review. PR stays draft.

## Full MIT DSP integration / visual onboarding — PR #46

Implemented on the draft source branch: actual SoundpipeAudioKit
v5.7.4 PitchCorrect effect with an Apple native backend selector,
key/scale control, default-off safety gating, and MIT/BSD license
notices in app and public site source. New dedicated illustrated
`PublicSite/guide.html` explains interfaces, cables, amps, effects,
Songbook, scoring, Stage Display and troubleshooting.

Open gates: exact-head CI/iOS build, real-device DSP latency and
feedback, transitive dependencies and nested asset licensing,
Word companions, site accessibility/preview/production deployment,
TestFlight signing and Apple commercial legal review. Do not mark
this milestone publicly shipped until these are satisfied.

## Optional private diagnostics and Apple-signed beta testing (draft PR #46)

- Source staged: opted-in bounded event timeline, local iOS-protected
  storage, explicit user review/share/erase UI, real mic/audio route and
  DSP breadcrumb events, no automatic upload.
- Source staged: separate manually dispatched Apple Ad Hoc signing
  workflow producing an IPA artifact for later BetaDrop **hosting**,
  not signing. Existing TestFlight path preserved.
- Source staged: support, legal, privacy and visual website guide updated
  for opt-in debugging and privacy-consistent reporting.
- Awaiting exact-head Core CI, real iPhone/iPad diagnostics lifecycle
  and privacy checks, signing/account/device profile configuration and
  authenticated IPA export, site preview/production checks and Word docs.
- BetaDrop legal and security limitations in
  `Docs/BETA_SIGNING_AND_SUPPORT_DIAGNOSTICS.md`.



## October 11 — Stage-first UI batch (implemented, pending iOS validation)

- Adaptive iPhone/iPad stage, landscape/portrait controls, no progress bar or Previous.
- In-page code/text/voice search, full numeric keypad and explicit Reserve/Cancel.
- Opt-in front-camera preview BGV, video-only, ephemeral and cancellation-fenced.
- Preserve detailed CONTROL, existing queue/scoring and PR #47 capture workflow.
- Next gate: owner-approved consolidated Core build plus opt-in UI captures;
  physical-device camera/orientation/audio acceptance before release.


PR #51 lifecycle source update (October 18, 2026): foreground native player retirement and per-preview identity are implemented in the working branch with domain regressions. Changed-head hosted validation, iOS native lifecycle acceptance, independent audio-focus coordination and real-device embed checks remain open. This is not released YouTube playback.


## PR #51 iOS audio handoff candidate — October 18

Shared audio arbitration, controller-serialized online entry, auxiliary shutdown and capture/foreground/identity fencing are implemented in the working branch with domain/native regressions. Current-head hosted compilation/tests/native smoke remain required before acceptance. Next executable task: validate the candidate and repair actual failures; then exercise SwiftUI online entry/dismissal and physical embed/interruption/ads acceptance. Companion Remote implementation is now in PR #51; PR #53 preserves the design reference.


### Accepted hosted iOS audio coordination — October 18

Source `6f7a0ece9e42cc500ce4f52cc414c58e5e11b58e` passed reconstruction 39112387766 (178 Swift tests, 54 tooling tests, unsigned simulator app build and Android validation) and smoke 39112387680 (all five native cases plus focused navigation, explicit named-pass gate). The iOS session/auxiliary implementation and hosted regression gap is closed. Actual SwiftUI online-sheet/media flow and physical embed/interruption/privacy/ads acceptance remain open; no zero-overlap timing, merge, signing or release is implied. See docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md for evidence and next steps.


PR #51 source 594cd94 passed reconstruction 39114570402 and expanded smoke 39114570339: all five native and three UI cases executed, including actual online-sheet lifecycle and paused-starter/local-resume flows. That iPhone UI acceptance gap is closed. Full candidate-matched seven-case iPhone/iPad release walkthrough, physical media/ads/interruption/privacy and signing remain open. Companion Remote implementation is now in PR #51; PR #53 preserves the design reference.
