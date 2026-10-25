# KANTA reliability hardening handover

**Current merged source baseline (October 10, 2026):** `kanta-ph/Gelo-Karaoke-iOS` `main`, merge commit [`6dcdf5f`](https://github.com/kanta-ph/Gelo-Karaoke-iOS/commit/6dcdf5f675572771f0377fe2197c2cee41b4bf5c), PR #44 merged and PR #40 closed as superseded. The last green Core CI ([run #38003895381](https://github.com/kanta-ph/Gelo-Karaoke-iOS/actions/runs/38003895381)) validated **173 Swift tests**, an unsigned Release iOS Simulator build, and live Host/CONTROL simulator startup on an earlier PR head; it **did not rebuild the final in-app Legal & Privacy edit or this merge commit**. KANTA has **no verified signed TestFlight upload or physical-device acceptance** for this merged batch. Website `/legal` and `/privacy` are live and were checked on production. Historical draft/pending paragraphs below record their original dates and must not supersede this status.


**Current status — 2026-10-07:** PR #25 merged as `beb63b5`; Core CI #130
passed on main; UI Screenshots #48 passed on its PR head. Manual TestFlight
run `37575087891` stopped at missing signing inputs, before archive/upload.
Read the final dated entry below for the release follow-up. Earlier pending
validation statements are historical, not current instructions.

## Status and source

Base inspected: `481e99c977ee75d1ad54eaeb2abe5546ea05bcdb` on
`usermalik20-del/Gelo-Karaoke-iOS/main`. This batch is proposed on
`fix/reliability-hardening-handover`; it is not a validated production release.

The owner requested gap fixes and a handover after every change. Each coherent
change below records the cause, implementation, verification, and remaining work.
Future commits must append an entry here (or link a dated successor), update
architecture when needed, and retain honest pending-validation labels.

The latest existing main CI run inspected was `37483308006`, for `a39bc9c`:
failed before any job step ran, with no runner assigned. The existing AGENTS
handover records exhaustion of the old account's 2,000 included Actions minutes.
No paid capacity, account settings, secrets, Apple identifiers, or deployment
settings were changed. Do not repeatedly retry that run.

## Change 1 — explicit completion and playback identity

**Cause:** completion was inferred from the preceding UI snapshot being within
1.5 seconds of the end. Manual Next near the end could produce a false score;
delayed polling could miss a real completion. Consecutive reservations of the same
song did not rebuild the scoring session because presentation used only song ID.

**Implementation:** both AVFoundation adapters now expose `didFinishNaturally`,
set only by their actual playback completion path and cleared by load/play/stop/
seek. The authoritative controller retains a `KaraokeCompletion` and assigns a
UUID to each playback run. Optional snapshot fields keep old v2 snapshot decoding
compatible; older peers ignore added JSON fields. Scoring consumes new completion
IDs matching its prior run. Host rebuilds per playback UUID, prevents overlapping
snapshot refreshes, and timestamps playback anchors before awaiting metadata.

**Coverage added:** delayed polling, manual skip at 59,999/60,000 ms, pause/seek,
repeat-song completion, once-only delivery, legacy JSON decoding, repeated song
reservation, and concurrent completion polling. Swift tests are written but NOT
executed in this Linux workspace (Swift/Xcode absent).

**Next:** run Swift tests, then verify natural completion, replay, same song twice,
manual Next, seek-to-end, pause/resume, and supported/unsupported scoring on device.
Do not treat this as microphone accuracy or hardware-latency proof.

## Change 2 — serialized queue transitions and failed handoffs

**Cause:** Swift actors are reentrant across `await`; simultaneous Host/Remote
polls or commands could load the same reservation more than once. Failed automatic
handoffs retried on every poll without surfacing an error. A failed adapter play
could leave the engine on an asset that the controller had not committed.

**Implementation:** an actor-owned continuation gate serializes controller
commands/snapshots across async engine/library calls, without blocking a thread.
Queue transitions share one implementation. Failed automatic items are retained,
reported through `playbackError`, and suppressed from repeated automatic retry.
Manual Next explicitly retries. Failed engine preparation stops playback and marks
the committed song for reload on Play; snapshots do not expose an uncommitted asset.

**Coverage added:** 20 concurrent snapshots advance exactly once; failed handoff
load count remains bounded over repeated polls; manual retry; failed Next/play
keeps the queue and restores the committed song on Play. Not executed yet.

**Next:** validate multiple paired remotes plus rapid local commands. Check pause/
stop responsiveness while a large asset is preparing; commands are serialized, so
cancellation of a synchronous AV sampler load is not claimed.

## Change 3 — SoundFont prewarm/cache safety

**Cause:** opportunistic prewarm could change the live sampler during playing or
paused audio. A failed sampler load retained a potentially invalid cache identity.
A working fallback could still be preceded by another expensive preferred-bank
failure on each song.

**Implementation:** public prewarm runs only while stopped; song load uses its own
actor-isolated synchronous loader. Invalidate loaded identity before a mutating
bank load. Cache the resolved candidate chain and its metadata so an unchanged
successful fallback stays hot; changed paths/metadata trigger reevaluation. Keep
case-sensitive paths distinct. User preference is not rewritten.

**Verification:** source review only; no valid redistributable SF2 is bundled and
no physical-device memory test was available.

**Next:** use an authorized SF2: play/pause while changing preference, repeat songs,
modify/switch banks, force primary load failure, inspect actual fallback audio,
and profile iPhone XR/iPad 9th-generation memory. Large-file size is not a memory
budget; this does not guarantee near-1 GB banks fit. Do not claim universal GM
multitimbral fidelity from the sampler API alone.

## Change 4 — verified iCloud replacement and stable backup paths

**Cause:** the prior backup was deleted before copy/verification; display-name
paths collided; stale scan sizes could incorrectly reject a changed source; lexical
path normalization alone did not reject symlink escapes. UI implied completed
cloud upload when it had only prepared local ubiquitous files.

**Implementation:** `LibraryStorage/VerifiedLibraryBackup` validates resolved source
boundaries, copies to a sibling temporary file, checks fresh source size/mtime and
copied size, then replaces the destination. Existing data is untouched by read,
copy, or verification failure. iCloud coordinates source reads/destination writes.
Backup folders use stable registry UUIDs. Reentrant backup requests are rejected;
stale results are cleared. UI says prepared/submitted, with iOS upload timing noted.

**Migration:** old display-name backup folders are intentionally left intact; this
batch does not delete or migrate them. The first UUID-based backup may require
additional iCloud space. Byte count/mtime checks are not cryptographic hashing.

**Coverage added:** successful replacement, missing-source failure preserving the
previous backup, temporary cleanup, parent traversal and symlink escape rejection.
Tests not executed yet. Signed iCloud provisioning remains unverified.

**Next:** run storage tests on macOS and signed-device acceptance with Files/iCloud
providers; test interrupted/network-unavailable backup, equal display names,
source changes, stale bookmarks, and insufficient storage. No claim of transactional
all-library backup or whole-library restoration is made.

## Change 5 — privacy metadata and reproducible validation

**Cause:** the manifest listed no required-reason APIs despite settings and file
metadata use. Screenshot workflows had no manual trigger, and draft changes could
consume scarce macOS minutes.

**Implementation:** declare UserDefaults `CA92.1`, file metadata `C617.1`/`3B52.1`,
and timing `35F9.1`. Privacy guard now uses portable Python plist parsing and checks
these categories. Both workflows support manual dispatch, cancel superseded runs,
and skip macOS jobs for draft PRs; Ready for review triggers validation.

Apple references checked:
- https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitype
- https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitypereasons

**Passed here:** commercial asset guard, media provenance guard, privacy guard,
release metadata guard, YAML parsing, shell syntax, and git whitespace checks.
**Not run:** Swift package tests, XcodeGen, Release/Debug simulator compilation,
simulator screenshots, physical device playback, or signed iCloud/TestFlight.
No claim of App Store approval or a complete binary privacy audit.

## Exact next validation sequence

1. Verify destination repository/history and Actions allowance after transfer.
2. Open this existing draft PR in the destination and mark Ready for review once;
   do not create an empty commit or repeatedly trigger workflow runs.
3. Require both Core CI and screenshot jobs. Inspect concrete compiler failures and
   fix them coherently. Keep `swift test` plus Release build and Debug screenshots.
4. Once workflows exist on default branch, manual dispatch is available for future
   validation. Before merge, Ready for review is the intended validation trigger.
5. Inspect the four actual screenshots (phone/tablet CONTROL/fullscreen).
6. Merge only after required checks pass. Update this handover with run URLs,
   exact commit, results, and any remaining failures. Skip is not pass.
7. Complete authorized indexed-library/SF2 acceptance and physical-device scoring/performance.
8. Complete AppIcon, signed iCloud, LAN encryption, Apple signing/TestFlight and
   commercial interoperability review before public release claims.

## Explicit remaining gaps

- No successful post-batch iOS app build yet.
- Matching current indexed-library library acceptance still required; strict decoder unchanged.
- LAN TCP remains PIN-authorized but unencrypted; trusted LAN only.
- No measured low-end-device memory, thermal, battery or audio latency results.
- No final production AppIcon, signed iCloud validation, TestFlight or App Store release.
- No hardware or cloud validation was simulated by a mock or generated screenshot.

These blockers must remain visible. Continue hardening, not feature expansion.


## Transfer update — 2026-10-06 23:52 Asia/Manila

The owner confirmed transfer to **kanta-gelo/Gelo-Karaoke-iOS** and that Actions
capacity is available again. Repository ID 1406435710 and draft PR #25 were
verified under the new owner. Prior-account exhaustion above is historical;
the new account's exact remaining balance has not been independently queried.

Standing owner instruction: conserve GitHub Actions minutes from now on.
Batch coherent changes, inspect existing source/logs before running CI, avoid
empty commits and duplicate runs, and rerun only jobs needed for a concrete
failure. Keep draft work from consuming macOS jobs. Use one deliberate validation
cycle when ready. Never enable paid usage or alter billing limits without approval.
Continue leaving a per-change handover with verification and next steps.

This update changes documentation only. PR #25 remains draft; no build or screenshot
job was intentionally started for this note. Next engineering step is the single
post-transfer validation cycle described above, then concrete failure fixes.


## One-run validation update — 2026-10-06 23:54 Asia/Manila

Owner instruction for tonight: **only one CI attempt**, then pause until tomorrow.
The screenshot job is now explicitly opt-in via the `capture-ui` PR label (or
manual dispatch). It retains all build/capture steps. This prevents marking PR #25
Ready for review from starting a second macOS build. No screenshot label is added.
Ready for review tonight authorizes only Core CI: guards, Swift tests, XcodeGen,
and the Release simulator build. No retries, screenshots, merges, or follow-on
production runs tonight, regardless of result. This overrides the earlier
recommendation to trigger both workflows together. Tomorrow inspect this one
run's result/logs first and continue with the smallest necessary follow-up.

Validation for this workflow change: reviewed event/condition logic. Screenshot
checks skipped without the label are not visual acceptance. Existing regression
and release gates remain in force. The single run URL/result can be recovered
from PR #25 Checks; no code is pushed during the run to avoid cancellation or
an accidental additional attempt.


## Backup metadata correction — requested 2026-10-06 23:59 Asia/Manila

Owner authorized fixing the failure now and explicitly deferred testing until
tomorrow. PR #25 was returned to draft before editing. This commit uses
`[skip ci]` as an additional safeguard; no tests or hosted retries were requested.

**Observed:** Core CI #109 (run 37491611299) compiled the Swift packages and ran
108 tests; 107 passed and the backup replacement test threw Cocoa error 259.
The Release iOS app build was not reached. Controller/scoring regressions passed.

**Source diagnosis:** the backup's initial metadata snapshot used the supplied
URL's cached resource values; only the post-copy snapshot was refreshed.
Reusing the source URL after rewriting the source (as the failing test does)
can compare an old size/mtime with fresh values and reject a valid copy.
This is a concrete cache-consistency defect compatible with the failure.
The existing log did not identify the exact failing copy/guard, so the hosted
confirmation remains pending.

**Change:** one helper explicitly clears cached/temporary URL resource values
before all three verification reads: source before, source after, and temporary
copy. Verification and prior-backup preservation remain intact. Split the
source-changed and copy-size errors into descriptive messages so a future failure
identifies the violated condition instead of only returning generic error 259.

**Regression coverage:** the existing replacement test deliberately warms source
metadata before its different-size rewrite. A new same-size rewrite test uses
controlled modification dates to cover stale timestamp metadata independently.
No sleeps, weakened assertions, removed verification, or replacement of prior
backup before verification.

**Validation:** source/diff review only. Per owner instruction, no test execution
or second CI attempt tonight. Tomorrow remove draft status once to validate the
new head; do not rerun #109 because it tests the old commit. Keep the
`capture-ui` label absent so screenshots do not consume another build.
If backup tests pass, let that same Core CI run reach the Release app build.
Do not merge or claim a passing correction until the new run succeeds.

Apple cache behavior reference:
https://developer.apple.com/documentation/foundation/url/removeallcachedresourcevalues()


**CI skip-marker clarification:** correction commit `2c35dfd` and this note use
`[skip ci]`; do not assume changing draft status alone will run those commits.
On tomorrow's authorized continuation, review the correction and update this
handover with the actual validation scope/status in a normal documentation commit
without a skip marker while the PR is still draft. That is a substantive handover
update, not an empty CI-trigger commit. Then mark Ready for review once. Keep
screenshots opt-in and do not rerun the obsolete failing commit.


## 2026-10-07 Manila — whole-codebase review, no CI run

Owner scope: all codebase, fix concrete issues before CI, preserve Actions quota,
and leave a handover for each coherent change. The latest explicit test timing
is **test tomorrow**. This batch stays on draft PR #25, uses `[skip ci]`, and does
not start or schedule any workflow. The four portable static guards are not a
Swift test run. Future work must not interpret this handover as authorization to
retry CI repeatedly.

### Change A — app wiring and microphone lifecycle

Files: `KantaUserSettings`, `HostViewModel`, `LibraryViewModel`,
`MicrophonePitchMonitor`, `AutocorrelationPitchDetector`, `KaraokeScoring`.
Added explicit Combine imports for ObservableObject/@Published app-only files.
A capture generation invalidates permission continuations and queued samples on
stop, prevents duplicate starts, and honors task cancellation. Timestamp capture
precedes pitch detection. The Bluetooth option uses the iOS 17-compatible API
spelling. Reject nonfinite pitch inputs before integer conversion, and publish
live score aggregation on the existing snapshot cadence rather than every mic
buffer. Regression cases cover nonfinite detector/observation inputs.
Check next: turn scoring off while permission is open, deny/regrant, navigate
away, and verify no capture/observation after stop on a physical device. Confirm
routing, latency, and CPU usage; the detector still runs in the audio tap.

### Change B — storage commit and reload boundaries

Files: `LibrarySourceRegistry`, `BookmarkedKaraokeLibrary`, `AppServices`,
`HostViewModel`, storage regression tests.
Persist proposed registry records before exposing them in memory; quarantine
valid JSON with duplicate source IDs. Rebuild active sources/catalogs without
awaits after reading the registry; reject superseded reloads. Register acquired
security scopes before bookmark refresh can throw. Persist only refreshed
bookmark data afterward, without recreating deleted records or overwriting newer
scan/preferences. Deduplicate catalog IDs and isolate temporary cache roots per
library instance. Startup/Host share `loadIfNeeded` so Host reentry does not
remove a currently used legacy cache file. Added failed-upsert/remove, duplicate
ID, and removed-source bookmark regressions.
Check next: repeated Host entry, concurrent import/reload, stale bookmarks,
removed provider folders, and playback while explicitly rescanning/removing a
source. Explicit reload still invalidates caches; playback/provider coordination
under source removal needs device acceptance.

### Change C — provider paths and import responsiveness

Files: `BookmarkedKaraokeLibrary`, `LooseMediaCatalog`, `DirectoryLibraryScanner`,
`LibraryViewModel`, catalog tests.
Reject embedded NUL paths, enforce canonical symlink containment at media access,
and strip only the leading scan root prefix. Validate import destination folders
inside the selected root. Copies use provider coordination plus verified
byte-preserving temporary copies on a utility task. Scans likewise leave the
main actor; security scope remains held through the await. Prevent concurrent
import/rescan operations. No transcoding or source mutation. Added NUL-path test.
Check next: Files/iCloud providers, large SF2 imports and cancellation, same-name
imports, quota/full disk, symlink replacement races and import error UI. Scanner
provider enumeration errors and memory/cache limits remain follow-up review areas.

### Change D — playback and SoundFont boundaries

Files: both AVFoundation playback adapters, `PlaybackTimeline`, library resolver,
timeline tests.
MIDI seek/pitch while paused no longer restarts notes; repeated Play while already
playing is inert. Stop/pause release sustain and all voices. Refresh SF2 identity
metadata before reading it. A missing preferred SF2 reaches the adapter fallback
chain; invalid fallback candidates are skipped. Saturate time/frame conversions
before converting floating-point values to integers. Added extreme elapsed/rate
regression case.
Check next: macOS compilation, iOS Release build, generated-file seeking at
extreme positions, held/sustained notes while paused, and real user SF2 fallback
and multitimbral fidelity. No bundled SF2 or claim of real-bank validation.

### Change E — remote callback ownership

Files: `RemoteHostClient`, `BonjourHostBrowser`, `BonjourHostAdvertiser`.
Each connection/browser/listener lifecycle has a generation. Ignore obsolete
state, receive, result and send-completion callbacks. Reject obsolete incoming
connections; disconnect clears projected remote snapshot/search state. Only a
pairing session accepts pairing success and only a paired session accepts a
snapshot. Stop processing an envelope batch if it closes the current session.
Check next: rapid connect/disconnect/reconnect and Bonjour start/stop with two
real devices. Existing protocol unit tests do not simulate Network.framework
callback ordering. Encryption, pairing timeouts/rate limits and session-count
bounds remain release-hardening work; this batch does not claim LAN secrecy.

### Change F — binary parsing and span bounds

Files: `StandardMIDIParser`, `BinaryCursor`, `ContainerPayloadResolver`, parser tests.
Index sliced Data relative to its startIndex, reject spans beyond the actual blob
size, and cap prefix reads before converting UInt64 sizes to Int. Added sliced
MIDI/cursor and externally supplied UInt64.max-span regressions.
Check next: run the synthetic compatibility suite. Real current indexed-library acceptance
is still pending. protected payload materialization currently expects decoded MIDI; unsupported
variants must stay fail-closed pending fixtures/format evidence.

### Review coverage and validation

Repository-wide source review covered App/Features, Core control/queue/MIDI/
playback/network/scoring, Infrastructure storage/legacy compression/playback/
microphone/LAN, package tests, Config, Package.swift, project.yml, workflows and
release guards. This is a static review, not proof that every execution path is
bug-free. Remaining gates include AppIcon, signed iCloud, real indexed-library/SF2 fidelity,
XR/iPad 9 performance, microphone routing, LAN confidentiality and screenshots.

Actually executed this batch: `git diff --check`, Config plist parsing, commercial
asset guard, media provenance guard, iOS privacy guard, release metadata guard.
All passed. Ten new Swift regression cases are written but **not executed**.
No Swift/Xcode installed here; **zero new CI runs, no Swift tests, no merge**.
Last hosted result remains run 37491611299: 107 passed, one backup test failed,
and the app build was not reached. The earlier cached-resource-values correction
is preserved and still awaits confirmation.

Next authorized validation: review the final head, add a substantive handover
update without `[skip ci]` while draft, then start **one Core CI** on that head.
Do not rerun the old failing SHA. Keep `capture-ui` absent. Let the same run reach
the Release iOS build if package tests pass. Inspect its result before further
Actions spend; leave the PR unmerged until actual validation succeeds.


## 2026-10-07 Manila — legal branding and implementation-based privacy notice

Owner requested stronger legal wording, the correct app name, and a privacy
notice based on actual behavior. No new CI authorization was given.

**Change:** `LegalView` is now the canonical notice used by the modal
`LegalDisclosureView`; removed the divergent duplicated disclosure. Root entry
points and the screen title say **Legal & Privacy**. Brand constants identify
**KANTA — ᜃᜈ᜔ᜆ** / **KANTA**; no corporate or registered-trademark claim is
invented. Internal bundle/module identifiers remain intact.

The notice explains BYOL, possession versus copyright, separate public-use rights,
third-party non-affiliation, compatibility versus rights clearance, and mandatory
rights. Privacy copy follows source inspection: local microphone buffers (no
recordings/uploads), folder references and scan metadata, settings, temporary
legacy playback files, optional selected-category iCloud copies, provider sync,
PIN-paired plaintext LAN metadata, current absence of developer analytics, and
retention/deletion controls. It explicitly avoids the former false blanket “no
media uploads” claim and does not invent an in-app cloud-deletion feature.

**Docs:** corrected README/commercial/media/privacy claims; updated the stale
“no required-reason APIs” privacy review to match the existing manifest. Updated
architecture for one notice owner, feature docs and release checklist. Public
policy URL, verified publisher/contact, trademark/interoperability review and any
custom EULA remain release gates; these disclosures are not legal clearance.

**References:** IPOPHL copyright FAQ and Apple's custom-EULA minimum terms and
App Review privacy guidance (links in commercial/privacy docs). No third-party
terms were copied into the app or represented as KANTA's own EULA.

**Validation:** source-to-copy review, brand/duplicate-notice search,
`git diff --check`, commercial/media/privacy/release static guards. No new Swift
tests for this copy/presentation change. Swift/iOS rendering and VoiceOver/Dynamic
Type review remain pending the next authorized hosted/device validation. Keep the
PR draft and commit with `[skip ci]`; no workflow or merge requested.


## 2026-10-07 — Filename-neutral interoperability hardening

- Runtime SF2 import is now vendor-neutral: any filename is allowed when the
  selected file has a valid RIFF/SoundFont structure.
- Automatic SoundFont selection no longer contains a vendor-specific filename
  preference. Explicit user choice wins; otherwise normalized path order is
  deterministic.
- Indexed-library detection no longer depends on historical filenames. The
  scanner structurally identifies a candidate index, requires exactly one safely
  correlated media container, and samples bounded payload prefixes before
  publishing the catalog.
- User-facing UI, Host empty-state copy, legal copy, and public documentation now
  use neutral terms such as compatible indexed library, library index, media
  container, and SoundFont.
- The commercial asset guard is distribution-only. It blocks tracked karaoke
  media/SoundFonts from shipping with KANTA but never inspects or rejects a
  user's Files/iCloud runtime imports.
- Low-level constants that are technically required for compatibility remain
  isolated behind the private compatibility facade and are not product identity
  or App Store metadata.

Validation pending: no hosted CI was triggered for this documentation/code batch.


### Follow-up — neutral API test alignment

The indexed-container catalog regression tests were aligned with the renamed
`containerURL` API label after the main neutralization commit. This is part of
the same batch and did not trigger hosted CI.

Raw low-level format identifiers remain only inside the isolated
`LegacyCompatibility` implementation/tests where they are required to recognize
and validate the compatible byte format. Product UI, marketing copy, public
documentation, storage/catalog APIs, and distribution guards no longer depend on
third-party product names or historical filenames.


### Neutral developer tooling follow-up

The old format-specific probe entry point was replaced by
`kanta-container-probe`. It reports only neutral index/container structure and
bounded recognition status through the compatibility facade; the obsolete probe
source was removed. No hosted CI was triggered.


## 2026-10-07 — Apple release-prep completion batch

Code-side release preparation was completed on the reliability branch before the
single hosted validation run.

- Added the iCloud Documents ubiquity-container entitlement required by
  `FileManager.url(forUbiquityContainerIdentifier:)`.
- Added a destructive, confirmed **Delete KANTA iCloud Backup** action. It removes
  only `Documents/KANTA Backup` in KANTA's private iCloud container plus KANTA's
  iCloud key-value settings entry; original user-selected Files/iCloud folders are
  outside the deletion boundary.
- Added an explicit `ITSAppUsesNonExemptEncryption=false` declaration for the
  current Apple-system/exempt crypto posture. Re-review is mandatory if crypto
  dependencies or transport implementation change.
- Added canonical public `PRIVACY.md`, `SUPPORT.md`, and App Store metadata
  guidance; no seller identity/private contact was invented.
- Hardened release validation to require the AppIcon, exact 1024×1024
  nontransparent PNG properties, iCloud Documents entitlements, and export
  declaration.
- Screenshot CI remains opt-in, but now selects an App-Store-appropriate
  Dynamic-Island Pro Max iPhone and 13-inch/accepted 12.9-inch iPad class and
  verifies captured dimensions before publishing artifacts.
- Core CI now fails hosted release validation if the Xcode major version is below
  26, matching Apple's current upload requirement.

Run one Core CI on the exact final head. Do not mark the PR green or merge until
that run succeeds.


## 2026-10-07 — Compact Host UI follow-up

Screenshot CI exposed two release-facing UI defects on compact iPhone:

1. the global SwiftUI searchable bar covered the bottom of CONTROL/queue content,
   while wide Reserve/queue controls could force rows beyond the safe List width;
2. Baybayin appended directly to functional control labels depended on a system
   fallback font whose visual result was inconsistent with the surrounding
   KANTA typography.

Fixes in this batch:

- CONTROL now owns the only visible Host search field; the global searchable
  overlay was removed.
- expanded CONTROL receives explicit List insets and a width-bounded frame.
- search rows preserve song-title width and fall back from **Reserve + icon** to
  an icon-only reserve action when horizontal space is tight.
- queue rows prioritize title width over the remove action.
- pitch/tempo cards stack automatically when the horizontal pair does not fit.
- the duplicate nested CONTROL heading was removed.
- functional labels are Latin-only; Baybayin strings remain separately available
  in `KantaTerminology` for deliberate brand surfaces.

Validation target: one screenshot CI capture on iPhone/iPad, followed by one
Core CI run on the same final head before PR #25 is marked ready/merged.


### Final compact-UI validation request

PR #25 is ready for hosted validation on the completed compact Host UI batch.
Run the screenshot capture and Core CI against this same head. Do not merge on
a screenshot-only pass: require the Swift package tests, release simulator build,
and screenshot dimensions/capture to succeed together.


### Screenshot follow-up — reserve action sizing

Screenshot CI #43 confirmed the global search overlay and Baybayin control-label
issues were resolved, but exposed a SwiftUI measurement regression: the adaptive
Reserve action expanded vertically to the full search-result row height on both
iPhone and iPad.

The Reserve action now branches on horizontal size class instead of using
`ViewThatFits`: compact iPhone gets a fixed 38-point circular plus action;
regular-width iPad gets a fixed-size labeled Reserve button. This keeps each song
row compact and prevents vertical pill stretching.


### Final validation request after reserve-control correction

Run Core CI and KANTA UI Screenshots on this same head. The prior screenshot
regression was limited to Reserve button sizing; do not merge unless compact
iPhone rows remain compact, iPad labeled Reserve controls remain bounded, and
the existing fullscreen lyric hierarchy is unchanged.


### Screenshot follow-up — iPad bordered-button stretch

Screenshot CI #44 confirmed the compact iPhone Reserve controls are now correct,
but the regular-width iPad branch still inherited SwiftUI's bordered-button
vertical expansion inside the nested List row. The iPad Reserve control now uses
the same explicit, plain-button sizing strategy as compact iPhone: a fixed
38-point-high custom capsule with no system bordered style. This removes the
feedback loop that inflated song-result row height.


### Final validation request after iPad sizing correction

Run Core CI and KANTA UI Screenshots on this head. Acceptance requires compact
iPhone search rows, bounded labeled iPad Reserve controls, unchanged fullscreen
lyrics, and all existing release/test gates green.


### Screenshot CI #44 visual review follow-up

The successful #44 artifact was manually reviewed after capture. Compact iPhone
layout is correct and fullscreen lyric hierarchy remains intact. The artifact
still showed two presentation defects on iPad: the labeled Reserve capsule was
visually stretched despite intrinsic sizing, and the navigation brand rendered
Baybayin through an inconsistent fallback font.

The final correction makes regular-width Reserve geometry explicit at both the
button and label boundary (104×38 points) so parent-row proposals cannot stretch
the capsule. System navigation branding now renders plain `KANTA`; Baybayin
strings remain available only for deliberate artwork/screens that provide a
verified supporting font. Re-run screenshot and Core CI on one common head before
merge.



## 2026-10-07 — Release workflow, public pages, and acceptance boundary

Base: `beb63b5a4a4d8284705ceb7f12fcaf144cd05401` in
`kanta-gelo/Gelo-Karaoke-iOS`. This entry describes the follow-up batch, not a
successful signed release.

Implemented:
- Manual TestFlight now uses Linux signing-input preflight before allocating a
  macOS runner. Uploads require main; credentials are step-scoped. API key
  discovery is explicitly wired through API_PRIVATE_KEYS_DIR.
- Ephemeral keychain passwords are generated/masked per job; the obsolete
  APPLE_KEYCHAIN_PASSWORD repository-secret requirement is removed. Cleanup
  addresses fixed temporary paths even when certificate installation fails.
- Unique run/attempt build numbers, explicit Xcode 26+ gate, pre-upload validation,
  internal-only export, and retained IPA/dSYMs improve release reproducibility.
- Six targeted Python regression tests cover input redaction, missing/invalid
  credentials, wrapped base64, version bounds/uniqueness, and internal-only export.
  Core CI and manual preflight run these tests.
- Public policy/support are deployed from reviewed static PublicSite files only.
  Deployment `dpl_BJwaB5WyWzzUacy3yVP1zeSYteMu` is READY on Vercel project
  `kanta-public` under GeloTech. Privacy and support both loaded in a browser
  without a sign-in prompt at https://kanta-public.vercel.app/privacy and /support.
  No source repository, credentials, or user assets were published.
- App legal links, public documents, metadata, privacy review, architecture, and
  roadmap use the public URLs. Support documents TestFlight feedback; a general
  public contact/publisher identity remains unverified and must not be invented.
- AGENTS.md is now current. The obsolete transfer handover is preserved in
  Docs/ARCHIVED_TRANSFER_HANDOVER.md as historical material.
- DEVICE_ACCEPTANCE.md supplies an exact-build evidence matrix. Every physical
  row remains NOT RUN. LAN_SECURITY_DECISION.md restricts the current release
  path to internal testing; authenticated encryption is NOT implemented.

Checks actually run before publication:
- Six release-tooling regression tests: PASS.
- All workflow YAML parsed; every embedded run block passed bash -n.
- Privacy, release metadata/AppIcon, commercial assets and media provenance guards: PASS.
- git diff --check: PASS.
- Public privacy/support navigation and visible policy content: verified in browser.
- New Swift compilation/signing/device acceptance: pending hosted CI/Apple inputs.

Next steps:
1. Inspect the follow-up PR's exact-head Core CI before merging; screenshot CI is
   unnecessary for URL-only changes to the existing legal screen.
2. Configure the six documented signing inputs securely in GitHub web settings.
   Confirm Apple App ID/container ownership and App Store Connect record/roles.
   Dispatch once on merged main; do not retry the old missing-input run blindly.
3. Confirm App Store Connect processing and use the internal build to fill the
   device matrix with actual evidence. Do not claim delivery just from upload exit 0.
4. Implement/review authenticated LAN transport and device results in a separate
   coherent batch. No public-shipment exception has been granted.
5. Verify publisher identity/general public contact and legal/privacy declarations
   before creating an external distribution path.


## 2026-10-07 — repository screenshot gallery

Owner requested automatic repository storage instead of manually transferring CI
screenshots. Added a dependent Linux publisher to ui-screenshots.yml. Main manual
captures replace four fixed PNG paths on ui-screenshots, with README gallery and
capture.json provenance. PR/non-main captures remain downloadable artifacts.
Capture retains read-only permissions; publishing alone has contents:write.
Incomplete sets fail before mutation, stale/duplicate runs cannot roll back the
gallery, and pushes never force-overwrite concurrent changes. Updated architecture.

Validation: YAML parsing, embedded Python compilation, bash syntax, and a local
bare-Git integration exercise for first publication, source-file isolation, and
duplicate no-op passed. Hosted publication remains unverified until a capture runs
with the merged workflow. Run 37614978943 started with the prior workflow and
will only upload its artifact. No additional screenshot build was started.
Next: validate this batch with one Core CI; merge after green, then verify the next
owner-requested screenshot capture publishes all four images and provenance.

## 2026-10-07 — KANTA production app icon replacement

Owner selected the top-row far-right KANTA concept from the generated icon board.
Replaced `Sources/App/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`
at the existing AppIcon asset-catalog boundary; `Contents.json` and project icon
configuration are unchanged.

The selected artwork is a 1024×1024 RGB PNG with no alpha. It uses the app's
navy/blue/cyan/magenta neon palette, a retro karaoke microphone, the Kanta
wordmark, a Filipino sun motif, and exactly three stars, with no Philippine flag.
The source artwork was generated for this project in the owner conversation; no
third-party stock icon asset was introduced.

This is an asset-only replacement and does not change runtime behavior, data
contracts, entitlements, build settings, or application architecture, so
`Docs/ARCHITECTURE.md` remains unchanged.

Pre-push validation: repository icon wiring was re-checked (AppIcon asset catalog
is the sole application-icon source), Info.plist has no competing explicit icon
declaration, and the replacement PNG is 1024×1024 RGB/no-alpha. Hosted validation
is pending on the review branch. Next: run one Swift Core CI cycle on the exact
branch head; merge only if the icon/release metadata guards and existing tests are
green.

## 2026-10-08 — ten original bundled backgrounds

Owner supplied ten existing generated PNGs and requested app integration;
icon changes were explicitly excluded. BG/BUNDLED contains the original
1672×941 files (~21 MiB total), without resizing/recompression. Exact original
names and SHA-256 hashes are in ThirdParty/bundled-backgrounds.json.

KantaBuiltInVisualPreset keeps the existing static raw identifiers so saved
choices remain valid, with display names matching the artwork. XcodeGen copies
BUNDLED as a folder reference. Both Host and fullscreen use the shared
asynchronous LocalBackgroundImage reader through KantaBuiltInVisualView.
CONTROL > Playback > Stage visuals exposes Images · 10 backgrounds.
Motion presets remain procedural; this batch adds no video files.

Updated architecture, roadmap, BG instructions and both provenance registries.
The existing provenance gate also runs verify-bundled-backgrounds.py to reject
missing/changed PNG bytes, dimensions, or disconnected bundle/preset wiring.

Pre-push checks: all ten PNGs decoded and visually reviewed; source/destination
hashes match; commercial/provenance/bundled-pack guards and project YAML parsing
passed. GitHub blob hashes are verified during upload. No physical-device,
signed build or TestFlight evidence is claimed. One exact-head Core CI cycle
is required before merge; screenshots remain opt-in. Next: verify hosted iOS
build, then inspect the selectable artwork on iPhone/iPad in the next capture.

## 2026-10-08 — reference-style centered lyrics and watermark

Owner requested centered bold two/three-line synchronized lyrics and small
KANTA branding at 75% opacity in the lower-right. Added a pure core lyric-page
projection that retains KAR line markers and timed fragments, sorts timestamps
stably, and supports seek-safe two/three-line pages. Original lyric-window API
is retained. Host refresh is 100 ms; UI does not run a competing clock.

Host and fullscreen share KaraokeLyricBlock: heavy white text, dark outline,
green source-timed fragments, centered layout. Fullscreen starts with controls
hidden for the lyric-first reference view; tap or use Show playback controls.
The KANTA watermark is noninteractive, lower-right and exactly 0.75 opacity.
Demo uses synthetic timed fragments to show partial highlighting in captures.
No reference-brand content was bundled; current icon/backgrounds are preserved.

Validation before push: static source/call-site review, commercial/provenance/
bundled-image guards and whitespace review. Six core regression tests cover
syllables/markers, page transitions/seek, pre-roll, Unicode/CRLF, stable sorting
and empty sources. Swift/Xcode are unavailable in the editing environment;
these tests and the iOS build require one exact-head Core CI. Request one
opt-in iPhone/iPad screenshot capture for visual acceptance. Neither captures
nor timeline tests establish real-device audio-output latency.


### Screenshot #56 correction — bound image layout to the stage

Core CI #138 passed 126 Swift tests and Release iOS build on 970f91c.
Screenshot #56 completed, but review found that the imported image's aspect-fill
ideal size expanded fullscreen beyond the display: iPhone text was clipped and
the lower-right watermark was offscreen. LocalBackgroundImage now takes its
size from GeometryReader and clips the image inside that exact frame. This fixes
the shared image boundary for both bundled and BYOL images without changing their
bytes. Revalidate the corrected head with Core CI and one screenshot cycle;
previous success does not establish acceptance of this correction.

## 2026-10-08 — singing orientation UI

Owner asked for a landscape toggle during singing, transport below portrait
lyrics and responsive background media. New fullscreen structure preserves ONE
KaraokeStageView across orientation changes so decoded image state and the muted
AVPlayerLooper are not re-created simply due to resizing. Lyric timestamps and
the stage's 75%-opacity KANTA watermark remain unchanged.

Portrait uses a 60%-height bounded stage and a scrolling transport footer that
never overlays lyrics. Landscape uses the full stage with revealable playback
controls, but rotate-back and exit remain reachable. UIWindowScene lookup is
window-local, including iPad multi-window; manual rotation stays with the user,
while a player-requested landscape is returned to portrait upon exit. Info.plist
now declares valid iPhone/iPad rotation masks. Denied orientation requests show
an error rather than silently succeeding.

The release metadata guard checks device orientation declarations. The opt-in screenshot workflow retains its four original App Store/gallery
portrait PNGs, with an additional iPhone landscape capture validated against
the active scene's actual orientation. This is **code pushed to a working
branch**, not yet merged or device-tested. Require one exact-head Swift CI/Release
simulator build plus an opt-in screenshot run, then review for clipping/controls
before merge. Real iPhone XR/iPad 9th-generation and BYOL image/video,
orientation-lock, Bluetooth/audio routing, and multiwindow tests remain open.

### Screenshot CI #60 failure and fix

The Release iOS Simulator build and Core CI #142 passed. Screenshot #60
captured both devices but the iPhone landscape PNG was still 1320×2868,
so the dimension validation correctly failed. That image is not proof of a
successful orientation change. The initial screenshot-only rotation fired
from the UIView's didMoveToWindow callback, potentially before the
fullScreenCover was presented; this was a lifecycle race.

The correction waits until fullscreen onAppear plus window attachment,
allows presentation to settle, calls setNeedsUpdateOfSupportedInterfaceOrientations
on the root/presented UIKit controllers, then requests .landscape geometry.
The change retains public UIWindowScene API for manual rotation. CI now
copies a screenshot-mode-only JSON record of UIWindowScene.effectiveGeometry
(orientation, window dimensions and any error) from the simulator application
container. The verification step independently asserts iPhone scene and screenshot
landscape orientation and ALWAYS uploads captured PNGs even after a failed
dimension check, enabling concrete visual review rather than blind reruns.
No production media is recorded or uploaded; this diagnostic runs only under
KANTA_SCREENSHOT_LANDSCAPE in the opt-in UI screenshot workflow.

Screenshot #65 established a genuine iPhone landscape scene (956×440 points),
but simctl saved the framebuffer in portrait pixel order. The visual screenshot
contains correctly rotated UI; normalize the screenshot 270 degrees clockwise
for an upright landscape PNG. iPadOS denied programmatic rotation with
UISceneErrorDomain 101 because of the current windowing mode. Keep normal iPad
rotation/layout support without enabling deprecated UIRequiresFullScreen or
faking iPad landscape validation. Its rotation button now offers clear guidance
to rotate the iPad or widen the app window. The workflow asserts iPhone
landscape proof and marks iPad landscape as manual device acceptance.

Status: corrected head awaiting one Core CI and screenshot validation. Do
not merge if either still fails. Do not claim real-device rotation based on
simulator geometry alone.

### Screenshot #72 accepted, missing symbol corrected

Core CI #152 and screenshot CI #72 passed on the last complete validation
head. iPhone portrait captured the stage above controls; iPhone landscape
was validated against actual UIWindowScene geometry (not merely a rotated
portrait screenshot). iPad portrait passed; iPadOS windowed rotation is
still a physical-device acceptance gate.

Visual inspection identified an empty circular image for the portrait-mode
landscape toggle: the chosen SF Symbol is not reliably available in the
simulator. Changed the two orientation actions to explicit, VoiceOver-labeled
"Landscape" / "Portrait" text capsules. This affects only button chrome,
not scene geometry, playback, audio, image/video resizing or public app
orientation settings. Revalidate the final changed head in Core CI and one
opt-in visual screenshot capture before merging. Do not carry #72's green
result onto this subsequent commit.

### Screenshot #76 regression — topmost UIKit controller only allowed portrait

The label-only change passed Core CI #155, but screenshot CI #76's iPhone
request returned `None of the requested orientations are supported by the view
controller. Requested: landscapeLeft, landscapeRight; Supported: portrait`.
This reproduced intermittently despite the successful #72 landscape scene
capture. The missing boundary was the fullscreen SwiftUI hosting controller's
orientation mask, not Info.plist, image scaling or a slow simulator. No merge.

Replaced HostView's `.fullScreenCover` with
`SingingFullscreenPresentation`, a UIKit presenter that creates a
`SingingRotationHostingController` overriding supportedInterfaceOrientations
for its platform and shouldAutorotate=true. It remains mounted via a 1×1
UIViewControllerRepresentable and uses the existing showingFullscreenPlayer
binding to present/dismiss with no independent media state. A native hosting
controller is the actual topmost fullscreen controller UIKit queries for
rotation; attempts no longer depend on SwiftUI's transient default portrait
mask. The existing FullscreenKaraokePlayerView and its renderer, watermark,
lyrics, transport and source media remain unchanged apart from an explicit
onExit callback.

This final code change awaits Core CI and opt-in simulator capture on its own
head. iPad windowing restrictions, BYOL video, external speakers, sensor
rotation and old-device performance remain hardware acceptance gates.

## 2026-10-08 — large BYOL song catalog performance

**Scope:** ~48k song indexed library supplied separately by user. Added
persistent metadata-only SQLite3/FTS5 cache to existing LibraryStorage,
linked via SwiftPM's Apple platform sqlite3 library. No new backend,
third-party dependencies, cloud account, audio copy or protocol-version
bump. The validated indexed-library entry/SongID mapping remains the
sole playback authority. No specific proprietary media or real user's
48k index was embedded in tests or repository.

**Indexing:** stable SHA-256 of ordered verified LibrarySong DTOs.
Database stores ID/title/artist/code/kind only and is excluded from
iCloud/device backup. First scan or changed source commits the complete
metadata and FTS rebuild in one SQLite transaction; unchanged catalog
skips disk writes. Failed or unsupported FTS safely falls back to actor-
owned in-memory search (without serving old DB rows). Secure scoped
BYOL bookmarks and container offsets remain outside the database.

**UI:** CONTROL debounces input, pages 24 rows at a time and loads
more on demand without issuing 48k UI rows. Remote still searches
only after PIN pairing over existing trusted LAN transport; bounded
50-row pages, optional v2 offset and requestID prevent stale replies.
Host/Remote call the same library actor. Missing legacy protocol offset
decodes as zero, and invalid offset/limit are rejected. Enqueue still
goes through LocalKaraokeController. No payloads leave Host.

**Checks authored:** 48,000 synthetic SongIDs, persisted reopen,
digest no-op, page boundaries, removal/replacement, duplicate-key
rollback, Unicode/substring search, malicious MATCH/LIKE query syntax,
and legacy/new protocol request encoding. Real SHUBBIX/SHUBBLOB catalog
sample and end-to-end playback still need rights-cleared acceptance
on a supported device. No App Store or TestFlight deployment claimed.

**Validation gates:** one exact-head Core CI run (package tests +
Release Simulator build); do not start the optional screenshot CI
since the screen chrome changes are limited to "Load more" controls.
Follow-up after CI: app lifecycle retention, low-end device disk use,
index duration and latency measurement, and existing plaintext-LAN
security hardening.

## 2026-10-08 — real-index-informed searchable titles

**Input evidence:** Owner previously provided a 1,797,052-byte standalone
index; inspection found 47,998 titles with a four-byte trailing identifier
per title. The identifiers did not satisfy KANTA's existing playable-offset
ordering requirement. The original file and catalog text are not included
in the repository or app bundle, nor is any downloadable song content.

**Change:** The neutral structural scanner accepts a complete title index
without requiring a compatible media pair; its playable pairing logic and
strict offset/payload validation are unchanged. The library actor projects
catalog-only metadata on absent/unsupported media, preserving the same
stable SongIDs as later validated playable entries. SQLite stores
`is_playable` via schema migration; FTS remains searchable. New Codable
property defaults playable on old protocol-v2 JSON. CONTROL and paired
LAN Remote show "Catalog only" with no Reserve action, and the authoritative
controller rejects attempts to enqueue an unplayable ID. Errors now report
actual media/indexing issues rather than labeling every failure a Files
permission problem. Library onboarding copy explains search without media.

**Regression tests:** standalone index structural discovery, nearly 48k
synthetic title ingestion with non-playable identifiers, stable IDs,
SQLite persisted availability and status transition, legacy JSON
compatibility, and controller-level queue rejection. The fixture contains
no third-party song titles or bytes. Existing indexed-container playable
tests and commercial/legal guards remain in force.

**Status:** source branch pending one exact-head Core CI and Release
iOS Simulator build. Actual matching media-container acceptance, import
latency on iPhone XR/iPad 9, full BYOL search UI on hardware, and encrypted
LAN transport are still open release gates. Do not interpret successful
catalog-only search as proof of audio playback.

## 2026-10-08 — imported catalog visibility / LAN burst-control

**Base:** merged PR #33. Inspected current Library/CONTROL/Remote
data flow and existing Swift package tests. A locally held original
index was structurally checked without uploading it to GitHub:
1,797,052 bytes, 15,853 metadata records, 47,998 readable titles
and four trailing bytes per title. This does not verify any
corresponding media file, signed iOS Files-provider import or actual
device runtime.

**Problem:** LibraryView previously displayed only the number of
loose song files, misleading owners of large index-only libraries
into thinking their 47,998-title import contained zero songs.
RemoteView also sent network searches on every keystroke.

**Fix:** `BookmarkedKaraokeLibrary.sourceSongCounts()` returns
per-source searchable/playable/catalog-only totals from existing
actor-owned entries. LibraryViewModel refreshes this projection
after `loadIfNeeded` and after every import, rescan, SoundFont or
preference update and removal. LibraryView shows counts separately
without querying SQLite or reading any audio payload again.
RemoteView coalesces typing with a 200ms cancellable delay; new
queries still pass through the existing paired client, correlated
request ID and bounded 50-result pages.

**Tests added:** standalone index with metadata records and opaque
non-playable identifiers still scans correctly; 47,998-title summary
distinguishes browse-only from playable. Original titles and bytes
are excluded from source, CI fixtures and binary releases.

**Validation pending:** one exact-head Swift tests + Release iOS
simulator build. The app's 48k title import and search must still be
accepted on a real iPhone/iPad and real Files/iCloud provider.
No Apple signing configuration, TestFlight upload, hardware CPU/RAM
measurements, or actual song media acceptance is claimed.

## 2026-10-08 — Songbook feature and real index printout

Owner requested the complete songbook so printed reference numbers can be
searched in KANTA CONTROL and paired LAN Remote. The provided private
1,797,052-byte index has 47,998 titles. Local offline extraction created
A4 PDF/CSV copies in the conversation container only. No user title corpus,
index bytes, original identifiers or copyrighted recordings were added to
GitHub, shipping assets or test fixtures.

The index's trailing numeric field and two opaque record words are not
verified as public song codes. We therefore generate five-digit **KANTA
reference codes** deterministically from one-based record position; the
mapping is stable until that index is reordered. Multiple imported index
sources may share codes but use distinct source-scoped SongIDs. Both the
playable and catalog-only indexed import paths use the same factory.
SQLite FTS5 already searches code fields; results now display them in both
CONTROL and Remote. The authoritative controller still rejects Reserve
on unplayable titles.

A new paginated (100 rows) `KantaSongbookView` is accessible from the Home
menu and Host CONTROL. It shows title, code, available artist and playable
status, with metadata-only CSV export from the local actor and user-invoked
ShareLink. CSV values are quoted and suspicious formula prefixes are
escaped. Unit regressions cover index code mapping, 48,000-row export,
CSV quoting/injection and SQLite roundtrip. No TestFlight, paid account,
cloud service or external file hosting is required.

Pre-merge: one exact-head Swift core + Release simulator build.
Pending physical acceptance: iPhone/iPad import and refreshed code display,
paired Remote code search, native share sheet export, matching media
playback, and old-device peak memory during full 48k export. A source-index
reorder changes numeric code meanings and should prompt a replacement
songbook.

## 2026-10-08 — named played-song collections and artist enrichment

Owner requests a prompt after karaoke sessions to save all songs actually
played, with renameable Favorites and subsequent queue playback, plus artist
names in the Songbook.

**Capture:** core `KaraokeSessionHistory` observes Host playback snapshots
and records distinct playbackID runs in order. Pause/resume are deduplicated;
unsung queue reservations are not collected. End Session stops transport and
offers an explicit Save/Don't Save sheet; natural completion or a Host-observed
Remote Stop with empty queue can offer the same prompt. The Host preserves
ordered songs until the user makes a choice.

**Storage:** `SavedKaraokeSetStore` actor atomically persists named
metadata-only collections in Application Support. Rename/delete and a Home/
Host Saved Favorites view are supported. Reloading revalidates each SongID
from the current authorized Library actor; unavailable/catalog-only media
is skipped, and the current queue remains intact. A damaged Favorites JSON
file does not disable the user's playback stack. No audio, credentials,
payload or source paths are stored.

**Songbook:** user-provided index exposes 47,998 titles but no trustworthy
artists; none are inferred from song titles. Songbook shows artist when
supplied by the source; otherwise "Artist not provided". Owners can edit
missing artist metadata locally, persisted by SongID and applied to SQLite
search and future exports without modifying source files. Updating or
reimporting an index with a different source UUID can require reassigning
previous overrides and saved references.

**Coverage added:** session playback-ID deduplication/repeats/queue exclusion,
Favorites persistence/reopen/rename/delete and validation. Pending exact-head
Swift tests + Release iOS Simulator build; no additional screenshot job
unless visual review requires it. Physical Files-provider session saves,
LAN remote stop, iPhone XR memory pressure, share/export, and actual
47,998-title metadata acceptance remain hardware gates.

## October 2026 — Vocal Soundcard handover

Owner requested reverb/hall and other soundcard features, with external
amplifier expected in karaoke setups. Added an optional vocal-only
monitor DSP chain on MicrophonePitchMonitor's existing AVAudioEngine.
Scoring tap remains raw. Native Apple AVAudioUnitReverb (Room, Studio,
Hall, Cathedral, Plate), AVAudioUnitDelay (Echo) and two-band AVAudioUnitEQ
plus output mic level are controlled from Host. No new packages.

Safety: input permission precedes graph connection. Hard mute on stop,
mic output disabled by default and on every app relaunch/iCloud restore,
and route gate only allows wired headphones, USB audio or line out.
AVAudioSession route-change observer reevaluates the gate. Bluetooth,
AirPlay, speaker and multiple-output routes remain muted. The user is
advised to keep live speakers away from microphone even on an external
route. No mic recording/remote upload. External mixers bypass DSP
unless their mic audio is selected as iOS input.

Validate: Swift Core CI + Release simulator compile on exact PR head
before merge. Physical hardware acceptance essential: USB mic/headset,
amplifier/feedback, interruptions, latency, mic permission denial,
scoring signal independence, application lifecycle. Any room-mic
monitoring outside supported low-latency routes is intentionally blocked.

## 2026-10-08 — current-main documentation/site synchronization

**Task:** Bring every available KANTA user, engineering, release, public-site
and privacy document up to date with the last confirmed merged product
features, including #32–#38. The prior PublicSite landing and Support pages
omitted indexed search/Songbook, saved session Favorites and Vocal Soundcard;
PublicSite privacy omitted the new metadata and mic routing disclosures.

**Done on this documentation branch:** PublicSite/index.html, features.html
(new), style.css, support.html and privacy.html aligned with verified feature
behavior and release qualifications; README, Docs/FEATURE_PARITY.md,
Docs/APP_STORE_METADATA.md, Docs/RELEASE_CHECKLIST.md,
Docs/DEVICE_ACCEPTANCE.md, Docs/PRIVACY_REVIEW.md, SUPPORT.md,
PRIVACY.md and AGENTS.md updated. The website is kept static, no new
trackers/assets/scripts, and retains its restrictive security headers.

**Word files:** A search of the accessible Library and main repository found
no pre-existing DOCX artifacts. Three NEW render-reviewed DOCX files were
generated outside GitHub source control for delivery in the conversation:
KANTA_Feature_Specification_2026-10-08.docx,
KANTA_User_Guide_2026-10-08.docx, and
KANTA_Engineering_Handover_2026-10-08.docx. No unsupported claim of
in-place update to missing binary originals or GitHub upload.

**Validation/release distinction:** check website links and inline markup,
then merge the docs source after exact-head checks; separately deploy
PublicSite to the already-discovered Vercel project kanta-public. Confirm
live /, /features, /support, /privacy after deployment. None of this closes
device, audio latency, playback compatibility, Apple signing, privacy-contact
or LAN-encryption gates.

## 2026-10-08 — Vocal FX polish / Party SFX engineering batch

Owner authorized Phases 1 and 2 of Soundcard roadmap and Phase 3
UI placeholder. PR feature branch `feat/vocal-polish-party-sfx-pad`.

- Phase 1: existing microphone monitor gains native DynamicsProcessor,
  PeakLimiter, adaptive high-shelf de-esser, short-delay doubling and
  tempo-independent echo-time parameter, plus six presets. Dry input
  scoring untouched. Monitor remains opt-in and gated to wired/USB/
  line-out; fails closed on unsafe routes.
- Phase 2: injected party playback protocol/actor with four voices,
  bounded volume, original generated eight sounds, Stop SFX, custom
  locally imported 12-second/12-MB clips and restore original. Host
  and PIN-paired Remote controls; no sample file transfer over LAN.
- Remote protocol remains v2 with additional actions and validation.
  Old paired app versions should continue working unless they attempt
  unsupported commands; no new credentials or cloud services.
- Phase 3: disabled visual placeholder, not a hidden implementation.

Important hardware gates: output audio-session interactions with
AVFoundation playback and scoring, real wired/USB delay, volume/feedback
spikes, custom file-rate/channel format, file-provider permissions and
midplay route changes. Keep GitHub CI to one coherent batch and correct
concrete compiler/test failures before merging. All original SFX
samples are generated by code, not taken from third-party recordings.

## Batch A/B implementation handover — Stage, inputs, singers and playlists

Continue from the merged vocal polish/Party SFX foundation (PR #41).
Working branch introduces an external-only UIWindowScene that renders
KaraokeStageView using the same HostViewModel; do not mirror CONTROL onto
the TV when a true external scene is available. AirPlay may still mirror
at the OS level; never assert all AirPlay endpoints support extension.

AudioInputPanel reads AVAudioSession route and offers explicit preferred
input with live dry RMS from the scoring microphone engine; there is no
second capture graph. Do not automatically unmute blocked output routes
or infer round-trip audio latency from hardware buffer size.

Reuse PlaybackQueue.move and QueueItem.requestedBy for singer selection.
PIN-paired remote commands validate singers <=60 characters and preserve
queue-item UUID on reorder. Existing .enqueue semantics and transport/
playback recovery must be preserved.

SavedKaraokeSetStore decodes old Favorites without kind as .session.
Editable .playlist sets use the same atomic metadata-only file, and
search/export cannot convert catalog-only indexes into playable media.
KantaIntermissionMusic stores owner-selected media under private app
storage without compression and never records or transfers it. Music
volume starts conservatively and is muted/faded on active playback.
Do not bundle licensed intermission tracks.

Acceptance pending exact-head PR Swift tests/Release simulator, then
real iOS HDMI/USB/extended-AirPlay screen lifecycle, remote reconnect,
input-route diagnostics, idle music fade and thermal/memory acceptance.
No TestFlight build or physical acceptance may be claimed from simulator CI.

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

## 2026-10-08 — website-aligned native palette and screenshot logo parity

Repository audit found that merged PR #28 already installed the owner-selected
neon sun/three-stars/microphone icon in `AppIcon.appiconset`. However,
the iPhone/iPad Home and sidebar screenshots continued to display the old
procedural `KantaBrandMark` drawing. The issue was **in-app source mismatch**,
not an Xcode AppIcon compiler misconfiguration.

The new `KantaBrandArtwork` image set uses the **same exact PNG blob** as
the installed AppIcon, and KantaBrandMark now renders it. Native UI adopts
the web site's navy/plum/lavender/blue/cyan/gold colors through shared tokens;
the root opts into dark scheme and existing List/Form pages are aligned.
Media playback, lyric timing, user library, audio, networking and privacy
semantics are unchanged. No third-party art is introduced.

The ReleaseTooling parity test verifies the icon bytes and 1024 PNG header,
and prevents reintroduction of procedural branding. This branch is staged
as a **draft PR**, conserving GitHub Actions minutes. Hosted Core CI,
simulator screenshot recapture, hardware visual acceptance and contrast
review remain pending until explicitly requested. Existing screenshot
artifacts are historical and cannot be retroactively changed.

## 2026-10-08 — Filipino/English title-first voice search

Owner clarified that speech must search actual **song names** (and
artists), not just numeric codes. Added native opt-in Voice Search
in Host CONTROL, the app Songbook and paired Remote. The selected language
is Filipino (only when Speech.framework actually exposes a compatible
locale) or English (PH with US fallback). Spoken plain titles and
artist names go to the **existing** local / paired-host metadata search
after review; English/Filipino intent prefixes are removed, explicit
spoken KANTA codes normalized with leading zeros retained.
No voice command can directly reserve/queue a title.

The recognizer captures one short request (15-second maximum listening,
then bounded finalization), prompts separately for Speech and Microphone,
supports on-device recognition when available, discloses potential
Apple-hosted speech processing when it isn't, and provides keyboard
fallback on denial/unavailable locale. It avoids an overlapping mic
capture while Host room-scoring/Soundcard is active. Voice recognition
results aren't saved as recordings by KANTA. Permission/plist,
privacy review, website policy, product inventory and core normalizer
regressions were added in this coherent draft batch.

**Acceptance not yet achieved:** iOS simulator build/speech permission
UI, device locale availability (Filipino may not be installed/supported),
real quiet/noisy karaoke-room title/artist corpus and error rate,
on-device vs network speech path, scoring/voice audio-route coexistence,
VoiceOver/Dynamic Type, and iPhone/iPad screenshots. An
“at least 90% accurate” claim cannot be established from code alone.
No hosted CI/screenshot run triggered on the draft branch.

### Apple Speech locale correction — exact technical gate

Apple's current public iOS Dictation locale list contains English
(Philippines), not Filipino/Tagalog. Speech.framework's advertised
`supportedLocales()` is checked at runtime; the native Filipino
picker option is disabled rather than silently transcribing as English.
This staged batch **does not** satisfy the owner's Filipino/Taglish
recognition or >=90% usable retrieval target. A tested multilingual
speech model, corpus, baseline-device latency, and permission/audio
route acceptance are required before stating otherwise; see
`Docs/VOICE_SEARCH_ACCEPTANCE.md`. Existing functionality for
English titles, editable recognized queries and Tagalog text remains
an intermediate implementation, not a production sign-off.


## 2026-10-08 — Filipino language-model download accepted and staged

**Continuation of current FIFO batch, PR #44.** Owner approved the
Filipino/Taglish model download; kept the website color/AppIcon and
English/title search work together rather than abandoning the current
review branch. The model pack uses WhisperKit 1.1.0 exact SPM pin and
Whisper `base` multilingual Core ML (~147 MB excluding tokenizer/cache).
Its use requires explicit user consent, download/progress, preparation
via Core ML load, and a private non-iCloud installation marker. Re-download
and removal do not modify songs/Favorites; download is never automatic.
A short temporary microphone clip is recognized offline as `tl` and
deleted, with mic permission, cancellation, restore-of-audio-session,
and keyboard fallback. `KantaFilipinoPackControls` is shared by
Settings and voice-search screens.

**Engineering constraints:** the Argmax OSS SDK is MIT; the
official Core ML artifact repository advertises MIT; the underlying
OpenAI Whisper checkpoints are Apache-2.0. The actual downloadable
artifact and upstream redistribution/legal notices remain to be
signed off in `Docs/COMMERCIAL_DISTRIBUTION.md`. Snapshot source is
HTTPS/HF's cache. The installed model load guards missing Core ML
bundles but does not yet pin a complete upstream signed digest;
release gate remains open. Current code has *not* been compiled in
Xcode or validated on iPhone XR/iPad 9; no CI/screenshots were
launched in this draft update. Accuracy target and offline/route
validation in `Docs/VOICE_SEARCH_ACCEPTANCE.md` remain open.


## 2026-10-09 — PR #44 simulator Host runtime SIGSEGV (merge blocked)

**Evidence:** Swift Core CI passed on the app implementation; screenshots
of Home/Library/Settings displayed the approved icon/theme. However
launching Host on iPhone 17 Pro Max simulator returned to SpringBoard,
and RunningBoard confirmed `SIGSEGV(11)` instead of a valid Host UI.
A missing fullscreen orientation proof is a *downstream symptom*.
PR #44 must not merge until Host and CONTROL screenshots run as a
foreground app (not SpringBoard) and iPhone/iPad captures pass.

The original diagnostics pipeline masked the reason by using GNU
`timeout` on macOS where it is not installed; moved bounded log
capture into `Tools/capture-simulator-diagnostics.py`. The trace
shows eager Audio Unit/RemoteIO activity and an iCloud KVS signing
warning before the crash, but **neither is a symbolicated backtrace**.
Two cleanup changes target unnecessary Host startup side effects:
`KantaVoiceSearchController` now creates its AVAudioEngine only after
user microphone permission/Voice Search, releasing it on cleanup;
`ICloudSyncCoordinator` now accesses `NSUbiquitousKeyValueStore.default`
only when iCloud Save/Restore/Delete is explicitly requested.
`test_host_startup_side_effects.py` protects those invariants.

**Not yet accepted:** These defensive changes do not prove the root
SIGSEGV was fixed. Screenshot CI must confirm Host/CONTROL on both
device classes and capture one real landscape orientation proof; if
not, use a proper symbolicated simulator crash report before modifying
other engines or disabling runtime checks. Retain separate iPhone XR
hardware, microphone audio routing, and Filipino/Taglish ASR acceptance
requirements. Do not equate a simulator build with TestFlight signing.


## 2026-10-09 — PR #44 crash evidence retention repair

Continued from `5642bb3f72f36afed5a796bb56fb456c41e804bd`; the existing
startup fix remains intact and **runtime-unverified**.

Inspection of run `37858474703` found that its diagnostics upload contained
only one simulator log, not a complete IPS report. Run `37793263119`
printed only report tails. Neither is sufficient to identify the crashing
stack. Run `37858998365` allocated no named runner and executed no steps;
its cause remains external/unconfirmed, not a new Swift defect.

Changed `.github/workflows/ui-screenshots.yml` and added
`Tools/collect-simulator-crashes.py`:
- All normal, fullscreen and landscape captures now fail on unexpected app
  termination. The fullscreen path no longer ignores termination failure.
- Crash collection waits a bounded 30 seconds for delayed ReportCrash writes,
  checks host and selected simulator report directories, retains complete
  recent KANTA IPS/legacy reports, and emits an explicit missing-report manifest.
- IPS summaries retain exception, crashed thread and image UUID/base/offset
  evidence; raw reports remain authoritative. No claimed symbolication from logs.
- Debug capture builds request dSYMs; failures retain the exact app, symbols
  and checked-out source SHA for symbolication. Successful runs do not upload them.
- Orientation proof JSON is retained alongside PNGs. Missing proof uses bounded
  diagnostics instead of the prior unbounded log command.

Validation: five focused Python regression tests PASS (two-object IPS parsing,
faulting/triggered thread selection, recency/application filtering and filename
collisions, delayed partial write refresh, explicit absent-report status).
Workflow YAML parses and all embedded shell blocks pass `bash -n`.
These are tooling checks, **not Swift, simulator-runtime or device passes**.

PR remains draft without capture-ui; no macOS rerun was launched for this change.
The connector can read/write the repository; the browser session returned a
private-repository 404, blocking inspection of the account-level runner notice.
Next: obtain authorized browser account access, inspect the two failure
annotations on capture job `113589980923`, establish runner availability,
then dispatch one targeted latest-head screenshot run. If Host still crashes,
use the retained IPS + matching symbols to fix the actual stack before final
consolidated Core CI. Do not merge/promote the site or claim signed TestFlight,
Filipino accuracy, offline hardware inference or physical audio acceptance.


## 2026-10-09 — Actions blocker verified after owner sign-in

Supersedes the unknown runner/account cause above. The owner completed browser
sign-in and private repository access was verified. Run 37858998365 explicitly
reports that the job did not start because recent payments failed or the
spending limit needs increasing; a separate notice mentions macOS arm64 capacity.
Account billing inspection identifies the concrete active limits:
**2,000 / 2,000 included Actions minutes consumed**, reset displayed as
**24 days away**, and an **Actions budget of $0 with Stop usage enabled**.
This is an exhausted included allowance plus a zero paid-usage budget;
no new application failure or successful test occurred in that run.

No budget/payment changes or CI retries were made. Diagnostics implementation
remains at 11e4c434fc153908597e4d501d934fadacda1743, with five focused tooling
tests passed. PR stays draft/unmerged, runtime and hardware acceptance open.
The next hosted simulator validation requires restored Actions allowance or
owner-authorized paid usage. Spending authorization must identify a cap before
changing the budget. Keep production deployment blocked on mandatory gates.

## 2026-10-09 — Concurrency/idempotency fixes, source-only batch on PR #44

**Reason for sequencing:** User confirmed no remaining macOS GitHub Actions
capacity; simulator SIGSEGV runtime acceptance is on hold. Owner authorized
a focused reliability source batch without spending Actions minutes.

**Scope:** Cancellation-aware FIFO playback operation gate; per-session bounded
Remote command-envelope replay ledger; durable stable-ID Favorites Save with
duplicate-tap protection, concurrent playlist-load prevention; unique Songbook
CSV output filenames; owned cleanup of temporary Filipino transcription files
after canceled inference. Repeat song reservations retain deliberate semantics.

**Added (not yet executed) tests:** same/in-flight/failed LAN envelope replay
and bounded eviction; 24 concurrent Favorites Saves with one key, reopen/retry
and independent deliberate save; 100 unique CSV export URLs; canceled playback
gate waiter followed by a succeeding command.

**Verification/release:** Source is prepared for review; no Swift compilation,
simulator CI, phone testing, TestFlight, merge or production promotion has been
performed. Host SIGSEGV and deployment remain separately blocked. The
permanent acceptance policy lives in `AGENTS.md` and this Architecture.

## 2026-10-09 — FIFO consolidation of PR #40 into PR #44

Integrated PR #40's monophonic correction model, root/scale UI, raw tap
pitch measurement and native TimePitch node into PR #44's expanded DSP
graph. Resolved old/new Soundcard preference conflicts by retaining #44's
custom Codable migration plus a default-off optional pitch field. Kept
compressor, gate, de-esser, doubler, peak limiter, voice search, branding
and concurrency/idempotency/cancellation changes. Added checks for
pre-polish settings round-trips and non-finite smoothing inputs. PR #40
remains as traceable history; continue in draft PR #44.

Source checks cannot prove an iOS build, Host crash fix, hardware audio,
Whisper accuracy, signing or TestFlight. The previous Host/CONTROL SIGSEGV,
runner failure and limited GitHub Actions allowance are still release
blockers. Do not automatically trigger a second CI on ready-for-review
or merge-to-main, or a separate screenshot/TestFlight run.

## 2026-10-10 — Host startup isolation and nonfinite Vocal FX guard (draft PR #44)

**Why:** The Host screen eagerly created a microphone-device diagnostics session/route listener and decoded an optional saved intermission track before the user opened diagnostics or enabled intermission. Both introduce avoidable audio-session/output work in the path implicated by the earlier simulator SIGSEGV, but no prior .ips symbolicated stack proves they caused it. The Soundcard settings constructor also left NaN/infinite reverb, EQ and mic-gain fields unchecked, risking propagation to native Audio Units on restored or edited state.

**Change:** Audio input diagnostics subscribes to route changes only while its UI appears and removes the token on disappearance. Intermission restores only a validated app-private file reference/title at Host creation; its AVAudioPlayer is allocated upon an explicit enabled + idle Host state, released on Host exit, and handles player prepare/play failure. Missing files and old saved preferences remain recoverable. VocalFXSettings bounds all original EQ, wet-mix and mic output values to finite limits in both init and clamped paths. Added direct nonfinite regression and source-contract tests for lazy initialization and observer cleanup.

**Validation:** Source-reviewed on the draft branch only; no new GitHub CI, Xcode build, simulator execution, signed archive or hardware test. This is a side-effect isolation and crash-risk reduction, **not** a proven SIGSEGV root-cause fix. Restore Actions capacity, run one explicitly approved latest-head targeted Host/CONTROL runtime capture with full IPS/dSYMs, then the single consolidated Swift Core/Release CI as budget permits; do not treat skipped runs as green. Preserve all physical audio, signing, LAN and speech-quality gates.

## 2026-10-10 — Consolidated one-run Host simulator acceptance

Because prior Core CI compiled without running Host, `.github/workflows/ci.yml` now reuses its own Release build for a live Host and expanded CONTROL iPhone simulator launch, with screenshot + process-alive confirmation and crash symbols. `Tools/smoke-host-simulator.py` and selection/contract unit tests are added. No second CI workflow or automatic screenshot-label trigger is introduced. This source update is untested until the owner-approved one-shot run when macOS Actions quota is usable. Visual iPhone/iPad screenshots, physical mic/audio, TestFlight and shipping approval remain separate gates.

## 2026-10-10 — One authorized Core CI attempt and replay-cache test compilation fix

**Run:** https://github.com/kanta-ph/Gelo-Karaoke-iOS/actions/runs/38002439138
**Tested commit:** `c9992e99ca497b3b5464a0be63f207e7743d12c2`.

The authorized single Swift Core CI run actually started on a macOS runner. Commercial asset, background provenance, iOS privacy, release metadata, release-tooling Python tests, Swift and Xcode version guards **passed**. `swift test` failed at compile time in `Tests/LocalNetworkControlTests/RemoteTransportTests.swift` because `#expect(cache.begin(...))` expands into a closure with an immutable `$0` receiver while `RemoteCommandReplayCache.begin` is a mutating struct method. This was a regression-test expression problem, not evidence of failed replay or queue behavior. There were no Swift test execution results or iOS build/Host runtime results in that run.

**Correction staged without running CI again:** Every relevant `cache.begin(...)` result is now captured in a local Bool before `#expect`. The test still checks first/repeated/in-flight/succeeded/failed/evicted identities and does not weaken replay assertions or change implementation. This corrective commit uses `[skip ci]` because the user authorized **one** test run only. **A new latest-head CI run requires fresh permission and available GitHub Actions allowance.** Do not merge or deploy until that new head passes full Swift, Release simulator and live Host/CONTROL smoke validation. Physical/audio, signing and other gates remain open.

### Follow-up: scan other mutating Swift Testing expressions (same CI cycle)

A related review of Swift tests found three more direct macro patterns that could be rejected once the replay-cache compile issue was resolved: `RemoteFrameStreamDecoder.append`, `KaraokeSessionHistory.finish`, and `PlaybackQueue.remove/move`. Their results are now evaluated first and passed as immutable values into `#expect`. This preserves original decoded-frame, session-completion and queue invariants. The fix is **source-only, not CI verified**. Do not rerun without renewed owner authorization.

## 2026-10-10 — Owner-authorized second Core CI validation

The owner explicitly approved **one additional** Swift Core CI attempt after run 38002439138 failed to compile mutating Swift Testing expressions. The replay-cache, decoder, session-history and queue regression assertions have been corrected on the review branch without altering production replay/queue semantics. Added `test_swift_test_mutation_guards.py` to the Python release tooling suite to prevent those compiler-incompatible direct `#expect(mutatingMethod())` patterns from returning. This is a real source regression guard, not a no-op trigger commit.

The new attempt must validate its **own exact non-skip commit** with a single Swift Core CI job: static guards, Swift tests, Release iOS simulator build, and Host/CONTROL live-process smoke. The separate screenshot workflow is intentionally not labeled/triggered; TestFlight remains a separate billable release workflow. If this attempt fails, diagnose and correct source without rerunning until new authorization. The previous SIGSEGV and hardware/legal/signing gates cannot be treated as passed merely from a compile result.

## 2026-10-10 — Confirmed Host SIGSEGV root cause; source correction, NO CI rerun

**Exact-head Core CI:** [run 38002988380](https://github.com/kanta-ph/Gelo-Karaoke-iOS/actions/runs/38002988380), SHA `13644352eeea0ed91a257cb395cf7071530cf0b9`, one authorized second attempt. All release-tooling guards, `swift test`, XcodeGen and the Release iOS Simulator build **PASSED**. The new live Host/CONTROL smoke **FAILED**. Host launched (PID 11437) then exited before an explicit `simctl terminate`; CONTROL was never reached. Screenshot and complete IPS, exact binary and dSYMs were retained in artifact [11650380721](https://github.com/kanta-ph/Gelo-Karaoke-iOS/actions/runs/38002988380/artifacts/11650380721).

**Definitive fault evidence:** The KANTA IPS record `KANTA-2026-10-09-231353.ips` shows `EXC_BAD_ACCESS`, `SIGSEGV`, protection failure in the main-thread **stack guard**. The symbolicated KANTA frames repeat `KantaIntermissionMusic.volume.didset` and `KantaIntermissionMusic.volume.setter` from the `KantaIntermissionMusic.init()` frame, then SwiftUI's StateObject update. The former `@Published volume` observer assigned back to `volume` inside `didSet`, recursively invoking its synthesized setter until stack overflow. This is a verified cause of the crash, not speculative iCloud/RemoteIO/Whisper behavior.

**Source-only correction (NOT yet CI tested):** Replace recursive `@Published volume.didSet` with a published private-set value, finite/range-bounded restoration and one explicit `setVolume` method for slider writes, atomic preference update and optional player fade. Preserve startup opt-in OFF, lazy decoder, hardware route safety, and existing queue/monitor scoring. Add an invariant test ensuring no observer self-write and that the slider calls the explicit setter.

**Release gates:** This change must be validated on its new exact head with `swift test`, Release build, **both Host and expanded CONTROL live simulator launches** and artifact review. Two authorized CI attempts are now consumed; **do not launch another CI, screenshot or TestFlight workflow without fresh permission**. PR remains draft/unmerged; all physical-device, microphone, speech-model and signing gates remain open.

## 2026-10-10 — Third owner-authorized single CI validation of crash fix

The owner approved exactly **one more** Swift Core CI round to verify the
`KantaIntermissionMusic` published-volume recursion correction. The previously
pushed implementation commit `ea7f578` carries `[skip ci]` deliberately.
Before its next review event, a targeted source regression guard was added to
ensure `@Published volume` cannot regain an attached property observer. The
actual fixed runtime semantics remain unchanged. The resulting non-skip review
head is the one to test. Require Swift tests, Release iOS Simulator build and
live Host plus expanded CONTROL process survival on the same run. Do not launch
any additional screenshot, TestFlight or retry run without fresh approval.
The original crash is considered fixed only after the actual live smoke gate
passes. PR remains unmerged pending verification and separate release gates.


## 2026-10-10 — Merged engineering handover and documentation sync

- PR #44's consolidated implementation, including experimental PR #40 pitch controls, was merged to `kanta-ph/Gelo-Karaoke-iOS/main` at `6dcdf5f`; PR #40 closed as superseded.
- The original Host SIGSEGV was symbolicated as recursive `KantaIntermissionMusic.volume.didSet` while SwiftUI constructed Host StateObject. The fix uses a clamped explicit setter and `@Published private(set)` with no observer self-write.
- The **last green** Core CI #38003895381 passed 173 Swift tests, unsigned Release simulator build and live Host/CONTROL screenshot/process-survival. It tested the earlier review ref, not the subsequently merged customer-facing Legal SwiftUI edit.
- Public site now serves `/legal`, `/privacy` and an empty private Songbook reader. iOS features are **source merged only**: no signed TestFlight upload, hardware microphone/voice-model quality result, encrypted LAN, final publisher contact or public App Store release is established.
- All current-status docs must reference this section/`Docs/PROJECT_STATUS_2026-10-10.md`; older handover sections containing `draft`, `blocked`, or `not implemented` are **historical snapshots**, not a present-state substitute.
- This is a **documentation-only** follow-up with `[skip ci]`; no GitHub Actions workflow should be triggered by the update.

## PR #45 — Original starters integrated, hosted validation pending

October 10 owner takeover explicitly authorizes multiple CI/fix cycles, gated
merge and existing release pipeline; earlier one-run restrictions are superseded
for this batch. Existing feature branch and PR #45 are preserved.

- Generated/packaged all three actual KAR binaries, manifest and an original
  procedural SF2 for fresh-install offline playback; added source lyric markers.
- Replaced view-owned store with one composition-root store inside the existing
  BookmarkedKaraokeLibrary catalog. Added stable IDs, playback/lyrics/scoring lookup,
  imported-bank preference and starter-only fallback; no competing state owners.
- Completed Play/Queue/Remove/Restore/Restore All UI; gated Play Now preserves FIFO.
- Hardened atomic file writes, tombstone corruption/failures, symlink isolation,
  revisioned catalog publication, cancellation and duplicate-safe retries.
- Added actual-resource, parser/timing/seek, concurrency/persistence/isolation,
  controller reservation and native non-silent sampler tests. Core simulator smoke
  exercises real playback and process-restart persistence instead of demo data.
- Four Python resource tests and four existing smoke workflow tests passed in the
  cloud workspace. Swift/Xcode unavailable there; hosted validation not yet run.
- Next: exact-head Core CI; repair actual failures; merge only when required checks
  pass; then invoke the existing internal TestFlight pipeline and report its exact
  preflight/signing/upload result. No signed deployment or hardware proof claimed.

### PR #45 first Core CI and canonical-path correction

Run 38019616962 on PR head 4ad43e9 compiled Swift and executed 182 tests.
Native sampler non-silent rendering, actual KAR timing/seek and controller
reservation tests passed; storage cases failed with unsafeStorage. The guard
compared Foundation URL objects across directory creation (directory URL metadata
can change despite an identical resolved path). Compare resolved `.path` values
instead; actual symlink escapes still differ and remain rejected. All hosted
release/provenance guards, 37 release-tooling and 4 generator tests passed.
Release build/simulator were skipped after the storage failure, not passed.
The corrective head requires another owner-authorized Core CI cycle.

### PR #45 second Core CI and simulator-hook access correction

Run 38019726537 (head 499a2f7) passed all 182 Swift tests, including persistent
store, concurrent restore, imported-song isolation and native sampler rendering.
Release app compilation caught the simulator-only smoke extension attempting to
write AppServices' private-set startupMessage from a separate file. Preserve the
private setter; log evidence-write failure to the simulator console instead.
The smoke driver already requires its result file and fails if missing.

Related failure-path review also moves the store revision increment into `defer`
for each noncancelled transaction: even a partially completed failing batch retires
older snapshots, preventing equal-version stale catalog publication. The failure
test checks revision advancement. Added clear localized storage error messages.
No simulator runtime result exists for the failed build; the corrected batch must
pass a new authorized Core CI run.


## PR #45 exact-head green evidence and documentation takeover — October 10, 2026

- [Core CI #242, run 38019883726](https://github.com/kanta-ph/Gelo-Karaoke-iOS/actions/runs/38019883726) **passed** on `1ee391e819128e160845720a4d006731cb93b8bb`: 182 Swift Testing cases, resource/provenance/privacy/release guards, Release simulator app, Host/CONTROL process survival, real native sampler play of all three original KAR demos, seek/pause, timed lyrics, FIFO queue, durable removal and offline restart/restore. Real [artifact #11657971189](https://github.com/kanta-ph/Gelo-Karaoke-iOS/actions/runs/38019883726/artifacts/11657971189) contains 7 files of simulator evidence including screenshots/JSON. The separate opt-in full UI screenshot workflow was skipped; it is **not** a pass.
- The owner requested pre-merge synchronization of all **active, relevant** Markdown, three current Word documents, customer-facing website Legal/Privacy/Features/Support/home and the matching app Legal view. State clearly that the three demos plus generated tone are first-party samples, not licensed third-party content; the user still supplies other songs and must comply with use/performance rights.
- **Gate:** edited SwiftUI must pass exact new-head CI; the last green run applies only to `1ee391e`. Do not merge until checks pass. Site source updates are not proof of production deployment; Apple account signing/TestFlight, physical audio, LAN confidentiality, accessibility and legal contact gates remain open.

## 2026-10-10 — Experimental vocal note follower and legal OSS audit

**Branch:** `feature/legal-vocal-pitch-stability-20261010` (separate draft, not
the starter KAR completion branch). The user prioritized pitch improvement.

**Pushed source:** The original confidence/Hz/scale quantizer now shares its
target calculation with a KANTA-owned `VocalPitchFollower`. Neighbor target
oscillation must repeat before changing note; jumps above two semitones switch
immediately. A second missing/unvoiced frame clears the lock and neutralizes
pitch. Disable, changed key/scale, unsafe output and stop reset the follower.
The single existing `MicrophonePitchMonitor` feeds Apple's AVAudioUnitTimePitch,
without a second audio engine, retained audio buffers, altered dry scoring tap
or user-facing feature/setting migration.

**Tests staged:** New `VocalPitchFollowerTests.swift` covers jitter, leaps,
unvoiced input, key changes, disabling and finite bounds. These are source
tests awaiting the exact-head CI check, not a passing Swift/iOS run.

**Legal:** `Docs/VOCAL_DSP_LICENSE_AND_ACCEPTANCE.md` inventories MIT
candidates with exact-dependency, attribution and live audio acceptance gates.
No candidate library was copied or linked; native fallback stays operational.
Do not market this as a commercially licensed Auto-Tune plug-in or
formant-preserving production autotuner.

**Next:** finish static/source review, obtain owner-approved single Core CI
build and launch, then physical wired/USB benchmark. Keep draft/skip-CI
until explicitly approved. Merged, deployed, signed and device-accepted:
**NO** for this branch.

## 2026-10-10 — PR #46 expanded: actual MIT Soundpipe engine + website guide

**Current draft branch:** `feature/legal-vocal-pitch-stability-20261010`.
The previous native-only candidate review is superseded by the user's
request to **fully integrate MIT library pitch correction**.

**Source pushed:** `Package.swift` pins AudioKit 5.7.2 and
SoundpipeAudioKit 5.7.4. `MicrophoneScoring` directly imports both.
`MicrophonePitchMonitor` holds the real `PitchCorrect` Audio Unit and
passes the existing physical input node through an AudioKit Node adapter
on the *same* AVAudioEngine, upstream of Apple TimePitch and existing
vocal polish. Soundpipe receives bounded allowed note-frequency table
based on selected root and scale; amount/speed parameter changes are
exposed in `KantaVocalFXControls`. Backend selection is stored as an
optional migration-safe field; Apple's previous follower path remains
available. Dry scoring, monitored route restrictions, default-off mute,
stop-generation invalidation and speaker/Bluetooth/AirPlay block remain.

**Legal:** MIT copyright+permissions/disclaimers from SoundpipeAudioKit,
AudioKit, AudioKitEX and Tonic plus BSD-3-Clause KissFFT disclaimer
included verbatim as an app resource and on `PublicSite/licenses.html`.
Added expandable full notices to in-app LegalView. The transitive
version/embedded-code acceptance audit remains open; the commercial
Auto-Tune brand or GPL DSP code has not been copied.

**Site:** new `PublicSite/guide.html` with responsive, illustrative
phone/audio interface/amplifier diagrams, annotated conceptual Soundcard
panel, volume/feedback safety, Lightning vs USB-C adapters, step-by-step
feature guides and troubleshooting. Linked from navigation, features,
home, support and customer privacy/legal pages. Website is SOURCE STAGED;
not production deployed/verified.

**Validation:** Existing domain tests plus backend migration test and
release source/license assertions **authored**. No fresh Mac Actions,
Swift compilation, iOS Release simulator, real iPhone/iPad mic, latency
benchmark, screenshot CI, signed artifact or live site verification.
Do not merge from draft until source/CI/licensing/Word companion and
physical safety gates are satisfied. Exact verification is in
`Docs/VOCAL_DSP_LICENSE_AND_ACCEPTANCE.md`.

## PR #46 full MIT pitch engine and public visual guide — 2026-10-10

**Pushed:** replaced the native-only candidate stage with a true
SoundpipeAudioKit `PitchCorrect` Audio Unit on the existing
`MicrophonePitchMonitor` audio chain, exact versions 5.7.4 and
AudioKit 5.7.2. Added selectable `VocalPitchBackend` with safe
legacy Codable migration, tuning frequency table, and native fallback.
No second mic engine, karaoke queue or scoring owner. Source retains
wired/USB/line-out monitor safety, output hard mute and native reset.

**Licenses:** actual full MIT notices and BSD-3-Clause KissFFT
notice in bundled app resource, expandable LegalView and
`PublicSite/licenses.html`. Transitive source/provenance audit
and app resource delivery require final-build verification.

**Website:** new mobile-responsive `PublicSite/guide.html` with
illustrated Lightning/USB-C interface/microphone/mixer routing,
Soundcard controls, sound checks, feature use and troubleshooting;
linked through existing customer site pages. Not verified live.

**Validation:** source-only. Swift tests/source-contract assertions
updated but not run in macOS CI; actual live DSP quality/latency,
iPhone XR/iPad 9 physical audio, UI screenshot, Word companion
updates, signing and deployment remain open. PR must stay draft.

## October 10 — Preview deployment and Word companions verified

Vercel's separate nonproduction preview deployment is READY:
`dpl_E75skeg6rU9AT1VhA3h1Caxwpdoq`,
`https://kanta-public-9xkwtbu19-gelo-tech.vercel.app`.
The preview's `/guide` and `/licenses` pages were independently
fetched and verified to return the KANTA guide content and full
MIT/BSD audio software notices. Its file tree includes all expected
customer pages, styles and Songbook resources. This does not promote
the preview to the primary `kanta-public.vercel.app` production alias.

The three existing owner Library Word companions were updated in place
with source-stage-accurate MIT DSP architecture, licensing, physical
iPhone/iPad hookup instructions, release gates and preview links:
`KANTA_Engineering_Handover_2026-10-10(6).docx`,
`KANTA_User_Guide_2026-10-10(6).docx`, and
`KANTA_Feature_Specification_2026-10-10(6).docx`.
Each was rendered and visually reviewed before overwrite. The website
preview and Word synchronization are complete; iOS build CI and
real-device mic performance acceptance are still outstanding.

## October 10 — Static website production release completed

Separate Vercel **production** deployment
`dpl_CrzkRL3ojpmxGUnkjTj2oAjpbJtE` is READY and aliased to
`https://kanta-public.vercel.app` (also
`kanta-public-gelo-tech.vercel.app`). Direct independent HTTP
fetch verified the homepage, `/guide` and `/licenses` expected
customer-facing content. This **supersedes** the preview-only note
above; the iOS application was not deployed or merged.
Updated the three current `(6)` Word companions in the owner's Library
again with the verified production links and deployment ID, rendered
all three at five pages and inspected their final pages.
PR #46 still requires exact-head Swift/Xcode CI, dependency validation
and actual wired/USB device audio testing before merge/TestFlight.

## October 10, 2026 — PR #46 privacy-first bug collection and BetaDrop readiness

User requested legal BetaDrop signing and detailed application logs.
**Research result:** BetaDrop only hosts/distributes an already-signed
IPA; Apple Ad Hoc profiles must enumerate testers' devices. Enterprise
profiles are not suitable for ordinary public testers. No third-party
signing service should receive a developer private key.

**Committed:** `KantaDiagnosticTimeline` allowlisted code vocabulary,
7-day/200-event retention; `KantaSupportDiagnostics` opt-in local
device-protected/backup-excluded history, gated manual JSON ShareLink
export and erase; main-actor instrumentation from AppServices, Host,
RootView and microphone/DSP/route transitions; Settings, LegalView and
public privacy/support/guide disclosure. No raw microphone buffer,
media metadata, PINs, names, network IDs, arbitrary error strings,
telemetry SDK or auto upload.

**CI prep:** `.github/workflows/manual-ad-hoc-ipa.yml` is manual only,
validates Apple sign/profile data and requires main; the standard
TestFlight workflow stays separate. Private-artifact visibility follows
the repository's **private** GitHub permissions, with three-day expiry.
No signing secret/profile or real signed IPA was accessed. New Swift
timeline tests and Python source/privacy workflow guards authored.

**Still pending:** exact-head Swift/iOS CI, physical-device diagnostic
persistence/share/suspension/relaunch tests, full Apple crash report +
matching symbols, legal/privacy/provider review, signed Ad Hoc IPA,
BetaDrop upload, Word-companion updates, and verified production website
deploy. No successful build, app release or physical test claimed.
See `Docs/BETA_SIGNING_AND_SUPPORT_DIAGNOSTICS.md`.

## 2026-10-10 — Word companions synchronized for PR #46

The three latest KANTA Word companion documents were read from the owner's
Library, updated in place conceptually as **new revision (7)** files,
rendered and visually reviewed as six-page Word documents, then uploaded
back to the Library without deleting their preceding revision (6):

- `KANTA_User_Guide_2026-10-10(7).docx`
- `KANTA_Feature_Specification_2026-10-10(7).docx`
- `KANTA_Engineering_Handover_2026-10-10(7).docx`

Each revision documents real MIT Soundpipe PitchCorrect source integration,
Apple native fallback, detailed iPhone/iPad setup guide, BetaDrop's
**distribution-only** role, the manual Apple Ad Hoc workflow, opt-in
local-only report/retention/privacy, deliberate sharing, user deletion,
separate system crash reports, and no device/signing/CI claim. Previous
"Word companions pending" statements in earlier draft entries are now
superseded for this PR #46 batch.

KANTA's main public site `/guide` and `/licenses` are already
associated with an existing READY production deployment, but the
**additional diagnostic guidance edits on the PR #46 branch are not
deployed to production**. PR #46 remains DRAFT/UNMERGED and lacks
exact-head Swift/Xcode/macOS CI and hardware testing.

## October 10 — actual-click walkthrough capture, staged only

Feature branch `feature/real-ui-walkthrough-20261010` adds a manual-only
real original-`.kar` XCTest path to the existing iPhone/iPad screenshot
workflow, preserving the preexisting demo UI captures. It records silent
simulator MP4, named screenshot attachments and bounded logs. Adds only a
test bundle, no application state-machine or production playback changes.
Actions have **NOT** been dispatched; build, UI taps, fullscreen, voice or
physical-device acceptance are **NOT VERIFIED**. The October 10 Core CI
artifact was recovered separately and has Host/CONTROL demo screens and a
first-party starter smoke result; those are not a video of real taps.
Owner authorization is required before consuming macOS Actions capacity.
See `Docs/UI_VISUAL_WALKTHROUGH.md`.

## October 11 — approved stage UI, camera BGV and in-page reservations

Implemented on the existing PR #47 capture branch; preceding capture work is
preserved and its UI tests now follow the new stage/More navigation. The user
approved the visual concepts and explicitly removed the progress bar.

- Root defaults to adaptive KantaMainPlayerView, reused by fullscreen.
- No stage progress/seek bar or Previous control. One Play/Pause + Next.
- Same-page search supports the existing English/Filipino voice services,
  digits 0–9, explicit selection, Reserve/Cancel and singer attribution.
- Reserve claims before suspension and keeps the disabled panel briefly after
  success to absorb a second touch. Separate later reservations remain valid.
  Search generation plus cancellation rejects stale results and pagination.
- Main-actor camera intent and a serial capture queue fence permission/runtime
  callbacks. Front video preview only; no audio input, audio-session takeover,
  saved video, upload or persistent camera-on preference. Stop/background/menu
  restores the previous visual. Permission dialogs do not cancel pending intent
  merely because scenePhase temporarily becomes inactive.
- Existing app icon reused. Fresh visual preference uses bundled Ember Sunset;
  saved visual choices remain intact. Synchronized lyric highlighting is yellow.
- Architecture, roadmap, privacy (app/source/site), camera usage description,
  privacy guard, UI walkthrough and physical acceptance checklist updated.

Validation performed in the cloud workspace: all 50 existing Python release
regression tests passed; privacy guard passed; changed Swift sources passed
Tree-sitter parsing; git diff whitespace check passed. The parser is NOT Swift
compiler type checking. The first partial-source test attempt lacked assets;
fetching the exact main-revision inputs resolved those missing-file errors.
No macOS Actions, Swift/Xcode build, UI test execution, real screenshots,
camera device proof, merge or release was performed for this changed source.

Next: obtain owner approval for consolidated Core CI and the opt-in UI capture
run. Inspect actual iPhone/iPad artifacts, fix build/runtime failures, then
complete Docs/STAGE_UI_ACCEPTANCE.md on real hardware before camera release.
Do not apply green checks from earlier commits to this batch.


## Native reconstruction checkpoint — October 15, 2026

Recovered 78 missing iOS paths, including 60 Swift files, individually without overwriting the stage baseline. Reconstructed absent Favorites/playlists, SQLite catalog, CSV/private Songbook, SFX and C compression infrastructure; repaired older LAN pagination and singer interfaces and restored asset-catalog metadata. New focused Swift storage/export tests require Apple/Swift execution.

Android now has a native Java/Gradle foundation for app-private imports, MIDI/KAR/audio playback, timed lyric lines, FIFO/singer queues, saved sets, scenic/fullscreen/TV stage, keypad/voice search and metadata exports. Missing advanced features are explicit in apps/android/README.md. Owner coding/context guide and conservative publishing policy are retained. Official YouTube playback is queued; no ad blocking or DNS workaround is included.

Actual checks: 50 recovered release-tooling tests, four starter-generation tests and resource guard passed. Nine Android runtime Java files compiled against official API 36 references and Java 17 libraries; 46 domain regression checks passed. New Swift files parsed with tree-sitter; five untouched original files exceed that grammar, so this is not a full Swift syntax/build result. C compiled on Linux for its unsupported-codec branch; Apple codec is untested. No Gradle APK/lint, simulator/device launch, CI run, signing, merge or deployment is claimed. Next gate: authorized cloud build validation before release claims.


## Existing comment review — October 15, 2026

Reviewed active iOS and Android source comments under the shared coding standard. Tightened comments in eleven existing Swift files, corrected the obsolete FTS5 fallback description, and removed an unmeasured 48,000-title responsiveness claim. Android comments already describe concrete ownership and bounds. Required attribution and recovery snapshots remain unchanged. The shared instructions explicitly cover existing and new code.

Validation: non-comment lines are byte-identical in every changed source file; all 50 release-tooling tests and git diff whitespace checks passed. No runtime behavior changed. Platform builds remain unverified; the next build gate is the previously prepared, owner-authorized consolidated CI run.


## Reborn iPhone/iPad runtime and unsigned packaging — October 16, 2026

Current repository: `kanta-gelo/Kanta-reborn`. The active root workflow compiles the complete Xcode UI suite and captures actual iPhone/iPad walkthroughs. Starter selection and the playing title have stable accessibility identifiers; MainActor UI tests wait for the real selected song and Pause state before transport assertions. App playback ownership is unchanged. After both simulator walkthroughs pass, the workflow builds a separate unsigned personal-signing iPhoneOS archive with iCloud entitlements omitted, validates/package it using the existing packager and retains source/hash metadata. This is not Apple signing or installation evidence. Local release-tooling tests (50), test-layout guard, Python syntax and workflow YAML parsing pass. Hosted UI execution and IPA creation remain pending. Root documentation supersedes dated historical status claims above.

## October 16 — encrypted Android-host controller candidate

Added CryptoKit KCP1 client codec, Network.framework foreground controller and a separate encrypted pairing/control screen. Existing LocalKaraokeController and Android SessionController remain queue authorities; source media, sensors, audio, dependencies and Apple signing identity are unchanged. Legacy Bonjour/PIN remains explicitly plaintext. No secure iOS hosting is claimed. Generation fences, socket cancellation/deadlines, one outstanding request, strict peer metadata bounds and no reconnect preserve revocation and retry boundaries. Independent Python vectors, adversarial codec tests and production Java TCP peer regression cases are wired into LocalNetworkControlTests. The full iOS validation job now supports a focused iOS label and pinned Java fixture compilation. Preliminary local layout, 50 release tests, four starter tests and resource guard passed; Swift/app/socket cloud checks remain pending. Scratch lacks Swift and javac. No publication, merge, new IPA, physical pair, independent audit or website change is claimed in this candidate entry.


## PR #51 foreground player lifetime repair — October 18, 2026

PR #51 adds terminal native player retirement, replacement-before-attach, background cleanup and token-matched error recovery on both platforms. Domain tests and Android runtime cases are added; layout/presentation guards, 50 Python release-tooling and four generator tests pass. Changed-head hosted builds/runtime, iOS native lifecycle evidence and audio-focus coordination remain required. Exact next task: inspect cloud validation for this batch, fix any failure, then implement the independent audio-focus coordinator without transport forwarding. No merge/signing/release. See root `docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md`.


## PR #51 native online lifetime tests — October 18, 2026

Adds three hosted native WKWebView lifecycle cases and a mandatory named-pass gate to focused smoke. No production playback code is changed. Layout/presentation/YAML guards and 54 Python release-tooling tests pass locally; current-head Xcode compilation and simulator tests remain pending. iOS audio-session coordination and device media acceptance are still unfinished. Root docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md retains the authoritative coverage boundaries.


## iOS audio handoff candidate — October 18, 2026

Based on PR #51 head `5d25f62993c8b003099c345b5387172a1fdf5af7`. AppServices constructs one MainActor KaraokeAudioOwnership alongside the existing LocalKaraokeController and a shared intermission owner. The coordinator owns only an ephemeral online UUID/retirement callback and foreground/microphone eligibility; it owns no queue, local transport, persistence or YouTube commands.

Online claims acquire the controller's existing cancellation-aware operation gate, reject actual playing state, stop SFX, suspend both idle rendering engines without Pause/Stop or position changes, then atomically recheck microphone/foreground/monitor eligibility on MainActor before stopping intermission and configuring the shared playback session. Local load/resume/playing seek and SFX retire the online native owner before native output. Remote commands and natural advance use these same controller paths. Capture announces activity synchronously before its permission await and releases it on denial/failure/stop; native output preserves capture's playAndRecord category. Session errors fail visibly. Foreground loss retires online ownership; returning does not reopen it.

The sheet cancels pending claims on close and rechecks its request UUID/foreground/ownership after suspension. The representable also checks ownership before attachment/load, preventing a revoked pre-mount session from constructing an active player. Stale release cannot clear a replacement. Intermission stops immediately, invalidates fades, and requires explicit re-enabling; automatic snapshot updates cannot reclaim online audio. Source files and reservation/playback identities are unchanged.

Regression coverage adds controller preparation/cancellation/microphone races, paused song/FIFO identity, explicit Next/SFX handoff, stale release, foreground, session failure and both rendering routes. Native smoke adds real WKWebView retirement-before-session configuration and synthetic AVAudioPlayer intermission handoff/restart; the named-pass verifier now requires all five native cases plus navigation. The test target compiles the two production components directly; no app testability, dependency or permission change.

Local layout, presentation, starter resources/generation and whitespace checks passed. Hosted Swift compilation, package execution, unsigned simulator app build and expanded native/navigation smoke are pending on the resulting source. Physical audio timing, real embeds/ads, interruptions, privacy and full iPhone/iPad release acceptance remain open. No zero-overlap timing, signing, merge, deployment or release is claimed.


## Verified iOS audio handoff — October 18, 2026

Source `6f7a0ece9e42cc500ce4f52cc414c58e5e11b58e` passed [reconstruction 39112387766](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39112387766) and [native/navigation smoke 39112387680](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39112387680). Reconstruction compiled every Swift package test target, executed all 178 Swift tests and 54 release-tooling tests, built the unsigned Release iOS simulator app and passed Android build/lint/domain/sanitizer/browser/emulator checks.

Smoke job `117631072361` explicitly passed all five YouTubeEmbeddedPlayerLifecycleTests cases and testFocusedNavigationSmoke. The new intermission handoff case passed at 08:49:55 UTC (0.070 s); WebKit retirement-before-session configuration passed at 08:49:55 (0.008 s); focused navigation passed at 08:50:40 (35.553 s). The named-pass verifier accepted all six cases at 08:51:24. This closes the hosted iOS audio-arbitration implementation/coverage gap and the initializer compilation repair; no assertions were removed. The earlier failed b3e26e5 remains recorded in the failure ledger.

The coordinator preserves local song/position/playback/FIFO identities, retires online ownership before native output requests, stops auxiliary audio and fences microphone permission, cancellation, foreground and stale owners. Tests establish the named domain/native behavior, not real-media zero-overlap timing. Actual SwiftUI online entry/dismissal, physical embeds/interruptions/privacy/ads and full exact-candidate iPhone/iPad release acceptance remain open. PR #51 stays draft. Next executable task: exercise the actual online-sheet entry/close/reopen/error flow through the host UI, retaining device/media acceptance as separate gates. No merge, signing, packaging, deployment or release occurred.


## Online-screen UI coverage candidate — October 18, 2026

PR #51 baseline 24db7a1 now adds two real XCUIApplication flows through Stage CONTROL's actual online sheet. They cover invalid-link rejection before WebView construction, explicit valid entry, stop/reopen, background/return without resurrection, Done/reentry and navigation recovery, plus preserving a paused original starter and explicitly resuming it afterward. Stable accessibility identifiers and link-keyboard Done dismissal support the real UI; no fake player or launch-environment bypass is introduced.

The synthetic video ID never autoplays. External YouTube availability is not a fixture: valid entry must reach either actual native player controls with a WebView or the production recoverable load-error state, with retained outcome screenshots. A local audio-claim failure is not accepted as an external-load error. This verifies UI lifecycle/recovery, not successful remote media or deterministic external-error delivery. Existing native process-failure, audio-handoff and controller/FIFO regressions remain required.

Focused smoke retains all five native cases and navigation and now requires both named online-screen cases (eight passes total). The full release walkthrough gate also requires all seven methods in the UI suite; timeouts, serial execution, failure detection, evidence retention, signing and deployment boundaries are unchanged. Local layout/presentation/YAML/whitespace and 55 tooling tests passed. Hosted app/test compilation and actual simulator execution are pending on the resulting source. Physical embed/ads/interruption/privacy and exact-candidate full iPhone/iPad release gates remain open.


## Verified online-sheet UI acceptance — October 18, 2026

Source `594cd94c2c78d12fcf8fd21d0ece1cd5949c2c72` passed [reconstruction 39114570402](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39114570402) and [expanded smoke 39114570339](https://github.com/kanta-gelo/Kanta-reborn/actions/runs/39114570339). Both reconstruction platform jobs succeeded. Smoke job `117637615146` explicitly passed all five native cases, focused navigation, testOnlinePreviewEntryCloseReopenAndForegroundLifecycle (105.968 seconds) and testOnlinePreviewPreservesPausedStarterAndLocalResume (54.616 seconds). The eight-name verifier succeeded at 09:36:18 UTC; this is execution evidence, not build-only acceptance. No assertions or previous cases were removed.

Actual host-sheet navigation, invalid-link validation, explicit entry/stop/reopen, foreground cleanup, dismissal/reentry/navigation recovery and paused-original-starter preservation/local resume are accepted on that iPhone simulator source. External availability was deliberately not treated as playable-media evidence: the UI cases accept either real native player controls or the production recoverable external-load error. Successful YouTube playback, ads, hardware interruption/privacy/audio timing and the updated seven-case full iPhone/iPad release walkthrough remain separate open gates. PR #51 remains draft and unmerged; no signing, deployment or release occurred.

Next executable acceptance: full seven-case iPhone/iPad walkthrough on the exact candidate source, followed by physical-device/real-media acceptance. No new failure occurred on this candidate. The earlier initializer repair history remains unchanged.


## PR #51 built-in and browser remote candidate — October 18, 2026

Owner explicitly moved the approved Companion Remote UI and browser implementation into PR #51. Source now adds native role entry, locally served responsive remote assets, private-interface HTTPS with per-session identities and fingerprint trust bootstrap, bounded guest authentication/replay/expiry, native pinned clients and controller-owned local commands. No decoder, persistent media/queue format, native dependency, sensor permission, signing or website release change. Existing KCP1/legacy paths remain separate. The shared HTML assets are platform-owned copies checked for equality; neither native build depends on the other platform.

Local Java core: 524 checks passed, including 45 actual TLS checks. API 36 compilation of PinnedBrowserTransport, 55 tooling tests, Swift layout/presentation/starter guards and captured Songbook interop passed. Local Chrome was blocked by the environment's Unix socket restriction; UI is not accepted from syntax/transport checks. Native builds, nine-case simulator smoke, full eight-case iPhone/iPad walkthrough, real-browser tests, cross-platform/device trust/security/media acceptance remain pending. Unsupported expanded shortcuts are disabled, not represented as completed features. See docs/COMPANION_BROWSER_CHECKPOINT.md (root-relative) for exact trust, expiry, limits, source and acceptance scope. No physical latency/performance result, merge, signing, deployment or release is implied.


### First-head diagnostics and repair candidate

Source 339b8f6 passed hosted Android APK/instrumentation assembly, lint, native sanitizer, 524 core checks and permission/notices checks. Reconstruction 39126848617 failed Swift test compilation (missing explicit throwing-parser try) and Chrome's first unverified-pairing assertion; emulator execution was skipped. Smoke 39126848616 failed the lazy AppServices initializer's isolation diagnostic. Repairs preserve every assertion, compose the host explicitly, match WebKit completion annotations, establish real-browser document/foreground readiness and retain nonsecret diagnostics. Android preparation is claimed before dispatch, and oversized replies now enter the operation ledger as bounded errors on both platforms to prevent duplicate side effects. The expanded local core suite passes 528 checks, including 49 real TLS checks. Changed-head hosted results remain pending; see the failure/repair ledger for confirmed causes versus hypotheses.

## iPad menu walkthrough case isolation — October 25, 2026

**Observed failure:** PR #71 previous head `dc60e513c4829226d8bf763c60d5e047e0beb5a4` passed reconstruction [#40146182458](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182458), focused smoke [#40146182470](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182470), and unsigned archive [#40146182453](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182453), but full walkthrough [#40146182462](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40146182462) failed because `testMainMenuButtonsAndScreens` did not record a clean pass on iPad. Six destination screenshots existed, but the aggregate XCTest case exceeded its execution allowance and the runner restarted.

**Change in the current PR #71 batch:** split the six More destinations into separate XCTest cases; preserve title, screenshot, Done-dismissal and return-to-More assertions; update the required-test manifest and source-contract checker; integrate the separate iPhone/iPad jobs from PR #72 while retaining same-branch cancellation of stale runs. No production navigation or playback code changed. No assertion or destination was removed, and the global XCTest timeout was not increased.

**Actual checks in this batch:** online repository source and workflow were inspected; the prior failed job log and artifact manifest were reviewed. The new hosted validation has not yet completed, so no source-guard, package, simulator, or device pass is claimed for the new head.

**Blockers:** new exact-head reconstruction, focused smoke, unsigned archive, and independent iPhone/iPad walkthrough jobs must finish. The previous iPad timeout remains open until all 13 required UI cases pass cleanly on both devices on the same candidate SHA.

**Next executable task:** inspect the fresh per-device job summaries and failed-case logs; repair the specific failing destination if any, then record the first passing run in `docs/CI_FAILURE_REPAIR_LEDGER.md`. Signing, physical-device acceptance and production release remain separate gates.
