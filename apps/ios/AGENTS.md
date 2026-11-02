# KANTA engineering instructions

**Current merged source baseline (October 10, 2026):** `kanta-ph/Gelo-Karaoke-iOS` `main`, merge commit [`6dcdf5f`](https://github.com/kanta-ph/Gelo-Karaoke-iOS/commit/6dcdf5f675572771f0377fe2197c2cee41b4bf5c), PR #44 merged and PR #40 closed as superseded. The last green Core CI ([run #38003895381](https://github.com/kanta-ph/Gelo-Karaoke-iOS/actions/runs/38003895381)) validated **173 Swift tests**, an unsigned Release iOS Simulator build, and live Host/CONTROL simulator startup on an earlier PR head; it **did not rebuild the final in-app Legal & Privacy edit or this merge commit**. KANTA has **no verified signed TestFlight upload or physical-device acceptance** for this merged batch. Website `/legal` and `/privacy` are live and were checked on production. Historical draft/pending paragraphs below record their original dates and must not supersede this status.

**Documentation rule:** Treat `Docs/PROJECT_STATUS_2026-10-10.md` as the present-state ledger; do not copy old Oct 7/8 draft language into customer docs. Documentation-only commits must include `[skip ci]` and may not run macOS Actions; real implementation/merge code requires explicit owner consent for testing.


## Current source and verified baseline — 2026-10-08

- Main now includes KANTA Songbook codes/search and local CSV export (#36), saved session Favorites and manual artist corrections (#37), and optional wired/USB Vocal Soundcard (#38). The corresponding Swift Core CI + Release iOS Simulator runs #176, #179 and #182 passed before merge.
- **DO NOT** infer TestFlight signing, physical input/output testing, actual 47,998-title hardware import, playable protected media or encrypted LAN from those results.
- Latest source-of-truth feature inventory: `Docs/FEATURE_PARITY.md`; public docs: `PublicSite/`.
- This current note supersedes the dated PR #25 baseline below for the most recent feature scope.

## Archived 2026-10-07 baseline

- Repository: `kanta-gelo/Gelo-Karaoke-iOS`; production branch: `main`.
- PR #25 merged as `beb63b5a4a4d8284705ceb7f12fcaf144cd05401`.
- Core CI #130 passed on that merged commit. PR-head Core #129 and UI Screenshots #48 passed; 120 Swift package tests were reported in the accepted batch.
- Manual TestFlight run `37575087891` passed guards/tests/XcodeGen, then failed because signing inputs were absent. No signed archive, IPA, or upload was produced.
- Subsequent changes must earn their own validation. Do not carry baseline green status onto a changed commit.
- Read `Docs/HARDENING_HANDOVER.md` for the latest batch and `Docs/RELEASE_CHECKLIST.md` for release gates.
- `Docs/ARCHIVED_TRANSFER_HANDOVER.md` is historical; its pending-build claims and old repo identity are superseded.

## Product and architecture invariants

Read `Docs/ARCHITECTURE.md` before changing system boundaries. Update it in the same batch when boundaries change; update `Docs/ROADMAP.md` for milestone movement.

- KANTA is one-device-first, offline-first BYOL karaoke. Full local CONTROL remains primary; a companion remote is optional.
- `LocalKaraokeController` owns playback and FIFO reservations. Views, fullscreen, lyrics, and remote surfaces project its state; never add parallel queues/state machines.
- Natural completion auto-starts Up Next and captures an immutable score once per playback identity. Pause, manual Next, and repeated reservations of the same song must not create false scores or reuse a scoring session.
- Scoring defaults OFF. Microphone analysis stays in memory on device: no recording/upload. Ambiguous melody targets remain playable and unscored.
- Folder bookmarks and symlinks stay bounded to the user-selected root. Reject unsafe paths and unsupported indexed-container variants rather than guessing offsets.
- Source media/SF2 remains byte-preserved. No transcoding, resizing, whole-container import, or bundled user libraries. Follow `Docs/COMMERCIAL_DISTRIBUTION.md` and provenance guards.
- Large SF2 preparation must preserve control responsiveness, active playback, and selected preference; reuse a hot bank and retain bounded fallback behavior. Do not claim all 1 GB banks fit device memory.
- Runtime priorities: audio, controls/queue, lyrics, scoring, stage visuals. Reduce disposable visual workload first.
- Bundle `com.gelo.karaoke`, iCloud container `iCloud.com.gelo.karaoke`, deployment target iOS/iPadOS 17 remain stable. GitHub transfer/rebranding does not authorize changing Apple identities.
- Test baseline is iPhone XR and iPad 9th generation, plus newer supported hardware. A simulator screenshot is not device performance evidence.
- iCloud Save copies selected categories; large categories default OFF. Prepared files are not proof of completed upload. Delete only KANTA's private backup tree/settings, never source folders.
- Functional controls use Latin text; Baybayin requires deliberately supported artwork/fonts. Preserve the accepted compact iPhone and bounded iPad Reserve geometry.

## Release scope and security

The current manual workflow is INTERNAL TestFlight only. Its export settings must retain `testFlightInternalTestingOnly=true` until a separately reviewed release batch closes device, public-contact/legal, and LAN confidentiality gates. It does not invite testers or approve external release.

The current remote is PIN-authorized plaintext TCP for trusted LANs. Do not describe it as encrypted, expose it over the internet, or treat a PIN as confidentiality. See `Docs/LAN_SECURITY_DECISION.md` for the release decision and acceptance criteria. TLS/secure pairing implementation is still outstanding.

Signing inputs belong in GitHub Actions secrets, never chat, source, logs, or artifacts. The temporary keychain password is generated per job and no longer needs a repository secret. The manual workflow must fail its Linux preflight before allocating macOS when signing inputs are missing.

## Proactive error recovery and FIFO execution

- When an error, failed check, runtime crash, regression, or deployment defect appears during an already-authorized implementation, **investigate and fix it immediately** as part of that same task. Do not require the owner to say "fix it" before acting. Keep the active task ahead of newly queued implementation requests unless the owner reprioritizes.
- Inspect diagnostics, identify a plausible cause, apply the smallest architecture-consistent fix on the existing working PR, and run a focused validation before restarting broader CI. Continue the original test → merge → deploy workflow only after release gates pass.
- If the failure is external (runner allocation, billing, credentials, signing, device-only acceptance), distinguish it from application defects, preserve evidence, and report the concrete blocker. Do not burn limited macOS minutes on blind reruns or claim an unverified fix is complete.
- Pauses are for genuine new approvals, missing permissions, irreversible actions outside granted scope, or safety/release gates; status requests alone must not replace authorized debugging work.

## Validation and CI conservation

- Inspect current head, PRs, and existing runs before editing; do not overwrite another agent's work.
- Review the affected subsystem and related failure modes, not only the first error.
- Batch changes on a non-production branch; avoid incremental CI-triggering commits and empty reruns.
- Run deterministic release-tooling tests and static guards first. One Core CI cycle on the finished batch; screenshots are opt-in for visual changes.
- A package-test pass is not an iOS app build pass. A simulator build is not signing, iCloud, physical audio, or TestFlight evidence.
- Every coherent change appends files/behavior, actual checks, blockers, and exact next steps to `Docs/HARDENING_HANDOVER.md`.
- Report written, pushed, CI-passed, merged, deployed, and device-tested as separate states. Never report plans or an empty hosting project as delivered work.

## Private Songbook architecture (draft branch)

PublicSite/songbook.* must never include user catalog rows or transmit an
imported .kanta-book, access code or searches. The page is only an encrypted-file
reader, not a server authorization service. The iOS exporter creates a fresh
random 128-bit hex code and AES-GCM encrypted metadata per file; WebCrypto
decrypts using the documented SHA-256 domain-separated key. Keep zero public
songs and no third-party JS. Do not reuse the six-digit LAN Remote PIN. Only
user-requested CSV/PDF export is plaintext. Ensure docs and public privacy text
match; validate secure browser and real phone before reporting shipping.


## Filipino speech pack (PR #44, draft)

- The owner approved a one-time *opt-in* multilingual Whisper Base
  download; keep it in the same FIFO PR #44 as the palette/AppIcon and
  title-first voice search until validated. No unattended CI runs.
- `project.yml` pins Argmax OSS WhisperKit 1.1.0 exactly for the iOS
  app target. Do not add model weights to Git, IPA, public website,
  iCloud Save, background asset bundles, or the domain Swift package.
- `KantaFilipinoModelStore` controls the download/preparation/cache,
  removable install marker and offline inference; `KantaFilipinoPackControls`
  is the Settings/Host/Songbook/Remote UI. `KantaFilipinoVoiceCapture`
  holds only an 8-second temporary mic clip. Keep native Apple English
  speech and scoring Soundcard routing separate.
- Never advertise 90–100% Filipino/Taglish recognition accuracy until
  `Docs/VOICE_SEARCH_ACCEPTANCE.md` is met with real XR/iPad 9 noise,
  accent and timing benchmarks; native Filipino iOS ASR is not
  universally available. No auto-reservation from recognized text.
- Verify license/trust of downloaded models and actual no-Internet
  second-session behavior; app privacy copy must reflect model host
  download metadata and Apple Speech online caveat.

## Mandatory concurrency and idempotency hardening — 2026-10-09

Apply this policy to EVERY subsequent feature, repair, migration, and release:
- Identify a single authoritative state owner. Review actor reentrancy at
  every `await`; actor isolation alone is not a multi-step transaction.
- Give retriable side effects stable request IDs and bound replay caches.
  Suppress the SAME request, not distinct intentional actions (for example,
  two singer reservations of the same song).
- Claim in-flight UI state before suspension for Save, import, queue-load,
  export, audio/model operations and other nonrepeatable workflows.
- Make cancellation promptly retire pending continuations, invalidate stale
  callbacks, and release files/audio resources only after consumers stop.
- Prefer actor-isolated atomic persistence; make retry identity durable where
  users can retry a completed action after reopening storage. Use unique
  temporary output files for concurrent exports.
- Include targeted concurrent/replay/cancellation/failure/restart tests in the
  same batch. Update `Docs/ARCHITECTURE.md` and `Docs/HARDENING_HANDOVER.md`
  for each behavioral contract or boundary change, not just this fix.
- Preserve FIFO and release gates: do not launch more paid macOS GitHub
  Actions while capacity is exhausted. Source checks are not simulator/device
  proof. Never merge/deploy a runtime-unverified crash fix as validated.

## Ongoing Soundcard integration contract — 2026-10-09

Monophonic pitch correction is optional, default-off, and upstream of
PR #44's compressor/expander/de-esser/doubler/reverb/echo/limiter.
Dry scoring remains upstream, and any unsafe output route hard mutes
monitoring and resets pitch. Keep older preference decoding functional.
Consolidation source checks cannot close the preexisting Host SIGSEGV or
physical-device acceptance. Preserve FIFO and one-CI-only authorization.


## Vocal Pitch DSP safety contract — 2026-10-10

The legal open-source DSP review is in `Docs/VOCAL_DSP_LICENSE_AND_ACCEPTANCE.md`. Current pitch stabilization is KANTA-owned `VocalPitchFollower` feeding the existing Apple TimePitch node; **no Signalsmith/AudioKit/Soundpipe effect code has been linked**. Do not advertise an MIT backend as shipping until exact-version licensing, notices, transitive assets, audio render safety, CI and physical wired/USB latency tests pass. Keep the existing graph, dry scoring isolation, neutral reset on unsafe route, and opt-in default-off behavior. No paid Actions without renewed authorization.

## Original KAR starter-pack invariant — October 10, 2026

Only three KANTA-original KAR demos and one generated tone are permitted in the exact-path deterministic starter resource allowlist. Route their managed copies through AppServices → StarterKaraokeStore → BookmarkedKaraokeLibrary → existing LocalKaraokeController and native audio. Never duplicate the queue, delete BYOL content on removal, or broaden asset distribution permission. Keep the app LegalView, PublicSite five customer pages, all affected active Markdown and three Word companions consistent. Because the in-app SwiftUI LegalView changed after 1ee391e, this new PR head requires fresh green CI before merge. Source/website updates are not evidence of live production deployment. Apple signing, signed internal TestFlight upload, physical iPhone/iPad audio, accessibility, public support/publisher identity, LAN encryption and commercial legal review remain open.

## Current PR #46 MIT vocal DSP + public setup guide (2026-10-10)

The next branch after merged main is `feature/legal-vocal-pitch-stability-20261010`
(PR #46, draft). It now **does link** SoundpipeAudioKit 5.7.4 (MIT)
and AudioKit 5.7.2 (MIT) through the `MicrophoneScoring` package. The
actual `PitchCorrect` Audio Unit is connected to the existing single
AVAudioEngine before Apple's fallback TimePitch and before vocal polish
and limiter; only one tuning backend has nonzero parameters. The native
fallback remains selectable, dry scoring stays upstream, live output
requires wired/USB/line-out and is off by default. New backend preference
is backward-compatible. KissFFT is **BSD 3-Clause**, NOT MIT; exact
notices are bundled in `Resources/Licenses`, exposed in `LegalView`
and `PublicSite/licenses.html`. Keep license inventory accurate.

`PublicSite/guide.html` is the new visually illustrated customer
guide for Lightning/USB-C iPhone/iPad, interfaces, mixer/amplifier,
scoring, remote, stage, Songbook, FX and troubleshooting. All diagram
screens are marked illustrations, not authentic simulator captures.
Follow `Docs/VOCAL_DSP_LICENSE_AND_ACCEPTANCE.md`.

**No CI/iOS build/physical audio/website production deployment verified
on this PR.** Do not mark it released, merge it, or run macOS Actions
without approval. Source gates include third-party transitive license
and `Package.resolved` review, concurrent AudioUnit parameter safety,
audio quality/latency, and synchronized library Word companions.

## PR #46 current soundcard/site contract — 2026-10-10

The current draft **now includes real linked SoundpipeAudioKit 5.7.4
PitchCorrect** and AudioKit 5.7.2 (MIT). It keeps the original single
AVAudioEngine with a native fallback and isolated dry scoring. The audio
graph is never rewired while live. Full audio license notices are bundled
and displayed in app/website; KissFFT is BSD-3-Clause, not MIT.
The new `PublicSite/guide.html` explains physical connections with
illustrations, not screenshot evidence. Earlier native-only staging
paragraphs above are historical and superseded.

Keep PR #46 draft: no exact-head CI, physical audio acceptance, Word
companion update or production site promotion has been verified.
Before release inspect resolved transitive revisions and physical
AudioUnit timing, then synchronize docs and explicitly authorize CI.

## PR #46 verified website publication boundary — October 10

Static website was deployed independently: Vercel production deployment
`dpl_CrzkRL3ojpmxGUnkjTj2oAjpbJtE`, with live verified
`https://kanta-public.vercel.app/guide` and `/licenses`.
The app DSP code remains draft/unmerged and unverified.
Three current KANTA Word companions, suffix `(6)`, were updated
in the user's Library. Preserve these separations in status messages.

## Private diagnostics and Ad Hoc testing invariant — October 10, 2026

The current draft PR #46 also contains `KantaSupportDiagnostics` and
`KantaDiagnosticTimeline`: diagnostics are **opt-in (OFF by default)**,
strictly enumerated technical event codes, capped to 200 over 7 days,
stored privately with iOS file protection, excluded from backups, and
exported only through an explicit user-reviewed Share Sheet.
Never log raw audio, song names, user paths, network/PIN identifiers,
credentials, error.localizedDescription or arbitrary strings; never issue
diagnostic writes on a real-time audio callback. A previous foreground
interruption **does not prove a crash**. Apple .ips crash reports require
a separate explicit user share or TestFlight facility.

`.github/workflows/manual-ad-hoc-ipa.yml` is **manual-only** and gated
for verified main with Apple-owned Ad Hoc profile/registered device
preflight before macOS billing. It may export signed IPA privately but
**never uploads keys or IPA to BetaDrop**. BetaDrop is a hosting service,
**not** signing. Do not trigger a paid signing/build workflow without
the owner's authorization or merge unvalidated DSP. Full policy:
`Docs/BETA_SIGNING_AND_SUPPORT_DIAGNOSTICS.md`.



## Stage-first UI and camera contract — October 11, 2026

The owner approved the stage design and explicitly prohibited a playback progress
bar and Previous button on the main/fullscreen player. Keep one Play/Pause toggle
plus Next. KantaMainPlayerView and its in-page numeric/text/voice search project
the existing HostViewModel/LocalKaraokeController; never add another FIFO queue.
Camera BGV is opt-in front-camera preview only, never microphone capture,
recording, upload, backup or a persisted enabled preference. Serialize capture
operations off-main and fence stale permission callbacks. Preserve audio session
ownership by the karaoke/scoring/voice services. Keep privacy text synchronized.
PR #47's real-capture work remains included; its updated tests require fresh
macOS CI execution before merge. See Docs/STAGE_UI_ACCEPTANCE.md.


## Cancellation-safe playback transactions — 2026-11-02

The LocalKaraokeController remains the sole playback and FIFO owner. User-initiated commands must recheck cancellation after asynchronous library lookups and native preparation awaits, and before committing queue/history/playback identity. If cancellation arrives before engine.load begins, leave the committed backend playing. Once engine.load begins, stop a potentially replaced backend, retain the prior controller item and reservation, and mark the current item for reload before the next Play. Natural-completion auto-advance is controller-owned and must not inherit cancellation from a short-lived UI snapshot poll. Keep regressions for canceled enqueue lookup, Next asset resolution, native preparation, recovery of the prior song, and natural auto-advance surviving a canceled snapshot poll.
