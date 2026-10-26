# Architecture

**Current merged source baseline (October 10, 2026):** `kanta-ph/Gelo-Karaoke-iOS` `main`, merge commit [`6dcdf5f`](https://github.com/kanta-ph/Gelo-Karaoke-iOS/commit/6dcdf5f675572771f0377fe2197c2cee41b4bf5c), PR #44 merged and PR #40 closed as superseded. The last green Core CI ([run #38003895381](https://github.com/kanta-ph/Gelo-Karaoke-iOS/actions/runs/38003895381)) validated **173 Swift tests**, an unsigned Release iOS Simulator build, and live Host/CONTROL simulator startup on an earlier PR head; it **did not rebuild the final in-app Legal & Privacy edit or this merge commit**. KANTA has **no verified signed TestFlight upload or physical-device acceptance** for this merged batch. Website `/legal` and `/privacy` are live and were checked on production. Historical draft/pending paragraphs below record their original dates and must not supersede this status.


## Architectural principles

1. **Offline-first.** Core karaoke playback, library search, queueing, lyrics, scoring, backgrounds, and local control must work without Internet access.
2. **One app, primary local control.** The main KANTA Host UI exposes search, queue, transport, volume, pitch, and tempo directly. A second-device Companion Remote remains optional.
3. **Shared command surface.** UI features depend on `KaraokeController`, not directly on local playback or networking.
4. **Local transport only by default.** Remote discovery/control uses LAN technologies such as Bonjour/mDNS and Network.framework/WebSocket-style messaging; cloud is not required.
5. **Compatibility adapters are isolated.** Legacy format readers belong behind repository-owned interfaces and must not leak into presentation code.
6. **Renderer/UI is projection, not authority.** Playback/queue/library state lives in domain services; views observe state and issue commands.
7. **Bring Your Own Library.** Commercial builds read user-selected Files/iCloud libraries and do not redistribute third-party third-party karaoke payloads.
8. **Third-party notices remain intact where legally required.** Product branding can be Gelo-owned without misattributing external libraries/assets.
9. **Architecture changes require this document to change in the same PR/commit.**

## Build and module boundaries

The domain, storage, playback, and compatibility foundation is a Swift Package:

~~~text
GeloKaraoke
├── GeloKaraokeCore
│   ├── Control
│   ├── Library
│   ├── MIDI
│   ├── Playback
│   ├── Networking
│   └── Scoring
├── LibraryStorage -> GeloKaraokeCore
├── AVFoundationPlayback -> GeloKaraokeCore
├── AVFoundationMIDIPlayback -> GeloKaraokeCore
├── GeloLegacyCompression -> zlib + Apple CommonCrypto
├── LegacyCompatibility -> GeloKaraokeCore + GeloLegacyCompression
├── LocalNetworkControl -> GeloKaraokeCore
├── MicrophoneScoring -> GeloKaraokeCore
└── kanta-container-probe -> LegacyCompatibility
~~~

The SwiftUI application target composes these modules and does not own binary parsing or playback state machines.

Host now opens directly to `KantaMainPlayerView` on iPhone and iPad. The
same adaptive stage is reused by fullscreen. It projects `HostViewModel` and
`LocalKaraokeController`; it owns no playback clock or reservation queue.
The stage deliberately has **no playback progress/scrub bar or Previous button**.
One Play/Pause toggle and Next use the existing controller commands.
`RootView` keeps Host mounted while Library, Songbook, Settings, Remote,
iCloud and Legal open from More. CONTROL preserves detailed audio, scoring,
queue editing, Favorites and visual settings in a sheet.

`KantaStageSearchView` is an in-page overlay: text/code search, existing English
and Filipino/Taglish voice search, 0–9 keypad, explicit result selection,
Reserve and Cancel. Reserve appends to the existing FIFO without starting or
interrupting playback. An in-flight claim before suspension suppresses duplicate
taps, while distinct later reservations of the same song remain valid. Search
uses cancellation plus a generation token to reject stale results, including
A→B→A query races. No voice result auto-reserves. Voice keeps existing microphone
exclusivity with scoring/Soundcard. Failures leave the panel open for correction.

`KantaCameraBackground` is the main-actor owner of opt-in camera intent;
`KantaCameraCapture` confines session configuration/start/stop to one serial queue.
Permission and runtime callbacks carry a generation fence. Stop/background or
leaving the stage invalidates pending permission results and queued callbacks.
Only a front video input and mirrored preview layer exist: no microphone input,
movie/data output, recording, upload or storage. The camera session explicitly
opts out of audio-session configuration. Capture is bounded to a supported 720p
preset/30 fps, restores the selected visual when stopped or unavailable, and
never changes saved visual preferences. iPhone/iPad scene orientation drives
preview rotation. The independent external TV stage retains scenic visuals;
normal OS mirroring may display the camera preview. Physical camera, interruption,
rotation, heat and audio coexistence acceptance remains required.

## Device support and adaptive performance

The shipping deployment target remains **iOS/iPadOS 17.0** with both iPhone and iPad device families enabled.

Product support floor:

- iPhone XR and newer, when running iOS 17 or later.
- iPad (9th generation) and newer as the standard-iPad baseline.
- Supported iPad mini/Air/Pro models running iPadOS 17 or later.
- Forward compatibility is maintained against the current Apple SDK through iOS/iPadOS 27.

This is a support policy rather than a generation gate in Xcode; Apple does not provide a normal application entitlement for declaring a minimum iPhone/iPad generation. Runtime behavior therefore adapts by capability and resource pressure rather than hard-coding product model names.

Performance priority order is:

1. audio engine and transport timing,
2. lyrics and queue/control responsiveness,
3. scoring capture/analysis,
4. stage visuals and animation.

KANTA never solves device pressure by recompressing user media. Instead it reduces disposable visual work, evicts nonessential caches, keeps one active SoundFont sampler hot across MIDI/KAR songs, prewarms selected SoundFonts opportunistically, and may fall back to a smaller user-supplied SF2 if the preferred bank cannot be loaded safely.

## Application composition

`AppServices` is the app composition root. It creates one shared:

- `LibrarySourceRegistry`
- `BookmarkedKaraokeLibrary`
- `AVFoundationAudioPlaybackEngine`
- `AVFoundationMIDIPlaybackEngine`
- `RoutedAudioPlaybackEngine`
- `LocalKaraokeController`
- `BonjourHostAdvertiser`
- `MicrophonePitchMonitor`

Host and Library features receive those shared services. This prevents imported-folder state from diverging between screens.

~~~text
Files / iCloud Drive system folder picker
      ↓
LibrarySourceRegistry
      ↓
BookmarkedKaraokeLibrary
      ↓
KaraokeLibraryAccess
      ↓
LocalKaraokeController
      ↓
RoutedAudioPlaybackEngine
     ↙                     ↘
MP3/WAV                 MIDI/KAR + SF2
~~~

## Library access and security scope

The system document picker persists a bookmark for each user-selected directory. The picker can expose On My iPhone/iPad, iCloud Drive, and other Files providers without requiring a Gelo-owned cloud account or app-owned iCloud container. `BookmarkedKaraokeLibrary` resolves registered bookmarks and keeps successful security-scoped directory access active while the library service is in use.

A reload fetches the registry before changing active state, then rebuilds scopes and catalogs without suspending. A generation token rejects superseded/cancelled reloads. Stale bookmarks are persisted after publication through a bookmark-only update that cannot resurrect a removed source. Startup and Host share an initial load; returning to Host does not clear an active legacy payload cache. Removed sources lose their active scope on explicit reload. Each library instance owns its temporary payload directory.

`LibrarySourceRegistry` treats decode corruption as recoverable state rather than a fatal app-start condition. If the persisted registry bytes can be read but cannot be decoded, the original file is moved intact to a collision-safe `.corrupt-<uuid>` backup, the active registry starts empty, and `AppServices` surfaces a recovery notice so the user can re-add Files/iCloud folders. Filesystem read/move failures still fail closed instead of silently discarding state.

Catalog building uses scan metadata only. Stable loose-file song IDs combine source UUID + normalized relative path. Relative paths containing parent traversal or absolute paths are rejected before URL resolution.

For MIDI/KAR, SoundFont selection is deterministic:

1. `user-selected .sf2` in the same registered source, when present.
2. Otherwise the first normalized `.sf2` path.
3. `.sfz` remains discoverable metadata but is not passed to the current AVAudioUnitSampler SF2 loader.

The service does not import or duplicate the user's full library and validates resolved source URLs remain inside the selected root. For a legacy container song, `BookmarkedKaraokeLibrary` may materialize only the validated payload span needed for the selected song into an app-local temporary cache so AVFoundation can consume a normal file URL. That cache is cleared on library reload/close, is never uploaded, and is never part of the app bundle or a Gelo release. Commercial distribution rules are defined in `Docs/COMMERCIAL_DISTRIBUTION.md`; hosted CI rejects known third-party karaoke payload binaries if they are committed to source control.

## Host

`HostViewModel` projects `LocalKaraokeController` state. It does not own a second queue or playback state machine.

The first end-to-end Host flow supports:

- searchable loose MIDI/KAR/MP3/WAV catalog
- enqueue/remove queue item
- play/pause/previous/next
- volume
- semitone pitch changes
- tempo ratio changes
- periodic authoritative playback snapshots

Playback queue transitions are transactional at the controller boundary.
A queued item is not removed and does not become now-playing until its
asset has resolved, loaded, and (when requested) started successfully.
Malformed/unavailable media therefore remains visible for retry/removal
instead of being silently consumed. Infrastructure playback/legacy
errors conform to LocalizedError where they cross into user-facing Host
alerts.

Validated legacy `index/container` titles are projected into the same catalog. Playback resolves only the selected user-supplied payload span. Native MIDI/WAV/ID3-MP3 spans can play directly; confirmed current protected payload v1 spans are decrypted with AES-256-CBC and inflated as raw DEFLATE into the same temporary cache, with declared-size and decoded-signature verification before playback. Unproven container signatures fail closed.

## Synchronized lyrics

`BookmarkedKaraokeLibrary` exposes timed lyric cues for MIDI/KAR songs and caches parsed cue timelines per song until the library reloads. `KaraokeLyricSequence` is the platform-neutral presentation model that normalizes common KAR lyric control markers and resolves the previous/current/next cue from the authoritative playback position.

`HostViewModel` owns only the projected lyric window for the currently playing song. SwiftUI does not parse MIDI files or derive timing itself. Songs without timed MIDI/KAR lyrics continue playing normally with no lyrics section.

## Background media

Background media remains split between technical compatibility and legal
distribution provenance. `BackgroundMediaCompatibilityInspector` uses
Apple-native decode metadata to report whether user-selected images and
videos are usable on the current iOS playback path without modifying the
source. Compatible imports are copy-only: no transcoding, re-encoding,
resizing, or recompression is performed.

The storage compatibility paths remain `BGV/MOTION`, `BGV/NATURE`, `BGV/3D`, `BGV/USER` for video and `BG/MOTION`, `BG/NATURE`, `BG/3D`, `BG/USER` for images. They are not exposed as product labels. KANTA presents these functional categories as **MY SET**, **LOOP**, **SCENIC**, and **REALM** respectively. Baybayin equivalents remain available as intentional brand accents, but are not mixed into tappable system chrome so unsupported or inconsistent fallback fonts cannot reduce legibility. `BGV/USER` and `BG/USER` remain
user-controlled content. Gelo-distributed media is a separate legal
boundary and requires approved provenance under
`ThirdParty/media-provenance.tsv`.

`LooseMediaCatalog` resolves optional local background media deterministically without treating unrelated artwork as karaoke visuals. Resolution order is: same-directory/same-basename media, same-directory `background.*`, root `background.*`, then the conventional third-party karaoke `bgv/` or `bgphoto/` library pool. Video wins over image only when candidates satisfy the same resolution tier.

`BookmarkedKaraokeLibrary` resolves the selected visual under the same security-scoped source boundary as audio. Host renders images locally and uses a muted looping `AVPlayerLayer` for video. Visual playback follows the authoritative karaoke transport play/pause state and does not own audio transport or queue state.

## MIDI/KAR playback

`StandardMIDIParser`, `MIDITimeline`, and `MIDIPlaybackPlan` remain platform-neutral.

`AVFoundationMIDIPlaybackEngine` loads an explicit local SF2 through `AVAudioUnitSampler`, schedules domain MIDI events, restores channel/note state after seek, and keeps GM percussion channel 10 untransposed.

The engine boundary remains replaceable with a properly licensed alternate licensed MIDI backend adapter if real third-party karaoke SF2/multitimbral fidelity requires it.

## File-audio playback

`AVFoundationAudioPlaybackEngine` handles local MP3/WAV with seek, volume, pitch, and tempo.

## Local controls and companion remote

`InAppControlCenterView`, branded functionally in the UI as **CONTROL**, operates directly on the shared `HostViewModel`/`LocalKaraokeController`. Baybayin remains part of KANTA's brand identity but is kept out of primary control labels for predictable typography across supported iPhone/iPad system fonts. It is the primary control path and requires no network pairing. The panel expands inline and provides bounded search results, enqueue/remove queue actions, transport, volume, pitch, tempo, and the optional background-visual toggle.

The existing network Remote surface is retained as **Companion Remote** for a second phone/tablet and uses the same authoritative command model. It is optional, not required for normal one-device operation.

## Remote protocol

Remote Protocol v2 translates authenticated LAN commands into the same `KaraokeCommand` handled locally.

`Infrastructure/LocalNetworkControl` now owns Bonjour/mDNS presence and discovery using Network.framework with service type `_gelokaraoke._tcp`. Host mode advertises only while the Host screen is active; Remote mode browses the LAN and projects discovered service names into UI state. The iOS app declares `NSLocalNetworkUsageDescription` and `NSBonjourServices` in an explicit Info.plist so on-device discovery follows Apple's local-network permission model.

A six-digit `PairingCode` primitive is generated and displayed by Host mode but is deliberately not placed in Bonjour TXT metadata. `LocalNetworkControl` uses a bounded 4-byte big-endian length prefix around JSON `RemoteEnvelope` frames (maximum payload 1 MiB). A session must identify as `.controller`, complete the PIN exchange, and reach a paired session state before any `KaraokeCommand` can execute. Invalid framing, protocol versions, roles, sequencing, or PINs fail closed. Rotating the Host PIN revokes active remote sessions. Paired sessions receive authoritative `KaraokeSnapshot` updates every 500 ms and after accepted commands. Remote mode exposes transport, volume, pitch, tempo, now-playing, queue-state, bounded catalog search, enqueue, and queue removal. Catalog queries are accepted only after pairing, trimmed to 200 characters, capped at 100 results, correlated by request UUID so stale responses can be ignored, and return only `LibrarySong` metadata—never bookmarks or filesystem paths. The current TCP stream is application-authorized but not cryptographically confidential, so it is intended for trusted LAN use until encrypted-channel hardening is added.

Phase 6 treats transport confidentiality as an explicit release-hardening
gate. PIN authentication prevents unauthenticated command execution but
does not provide secrecy against a local-network observer.

## Scoring and microphone boundary

`GeloKaraokeCore/Scoring` owns platform-neutral pitch observations, explicit target-pitch windows, latency compensation input, and score aggregation. The current score model weights pitch accuracy more heavily than voiced/timing coverage; it is a deterministic foundation, not a claim that third-party karaoke scoring semantics have been reproduced.

`Infrastructure/MicrophoneScoring` owns local microphone capture and pitch detection. `MicrophonePitchMonitor` requests permission only after an explicit Host action, publishes pitch observations in memory, and neither records nor uploads microphone audio. `AutocorrelationPitchDetector` is a bounded pure-Swift baseline that rejects silence/low-correlation input and constrains expected singing frequency to a configured range.

Microphone observations now use monotonic uptime timestamps shared with Host playback anchors. `LiveKaraokeScoringSession` projects those observations into authoritative karaoke media time, interpolates playing position with the current tempo ratio, ignores paused input, bounds retained observations, and resets on material seek drift or pitch-transposition changes. Host exposes the resulting live score plus bounded manual latency compensation. Automated per-device calibration and real-device validation remain pending. `MIDIMelodyTargetExtractor` provides the conservative target-melody boundary. Automatic extraction resolves only when one strong candidate exists: a lyric-bearing track with exactly one non-percussion note channel, or a track explicitly named as a vocal/melody guide with exactly one non-percussion note channel. It rejects competing candidates, polyphonic targets, malformed note lifecycles, and non-center pitch-bend targets instead of guessing. `BookmarkedKaraokeLibrary` exposes that extraction per MIDI/KAR song and shares one parsed-MIDI cache between lyrics and scoring targets. Unsupported or ambiguous songs remain playable but are explicitly unscored.

The current detector allocates/normalizes a short buffer and performs autocorrelation per observation. Battery, memory, thermal behavior, and coexistence with playback audio-session routing require real-device profiling before shipping scoring.

## Indexed-library interoperability boundary

`DirectoryLibraryScanner` performs filename-neutral discovery. It recognizes
normal media and valid SF2 SoundFonts, then structurally probes otherwise
unclassified files for a supported indexed-library layout. A candidate index is
accepted only when its offsets safely correlate with exactly one media container
and bounded payload sampling confirms a supported path.

`IndexedContainerCompatibility.swift` is the neutral facade between storage
code and the isolated private format implementation. Higher layers use
`IndexedLibraryProbe`, `ContainerOffsetCorrelator`,
`ContainerSpanIndex`, and `ContainerPayloadProbe` through that facade rather
than depending on third-party product terminology.

`IndexedContainerCatalog` projects validated title rows into
`.legacyContainer` songs. `BookmarkedKaraokeLibrary` materializes only the
selected payload when playback needs a standalone media URL. Source files remain
inside the user-selected security-scoped folder and are never rewritten.

Low-level constants needed to recognize a compatible protected payload stay
confined to the private compatibility implementation. They are implementation
details, not product identity, public documentation, or App Store metadata.

## Testing strategy

- Core: MIDI parsing/timing/playback plans, queue/controller/routing,
  including failed asset/load/play rollback of user-visible queue state.
- Storage: safe traversal, catalog construction, registry persistence, and corrupt-registry quarantine/recovery.
- Playback: generated media + adapter preconditions.
- Legacy compatibility: synthetic protected payload byte-for-byte decrypt/inflate
  tests, malformed/truncated envelope rejection, strict offset-table
  mapping tests, and explicit fail-closed coverage for unproven type-9
  addressing. Matching-current-library acceptance is a release
  validation task, not a source-controlled or downloaded CI fixture.
- Integration: shared controller -> routed engine -> resolved media assets.
- Local network: frame boundaries/version limits, controller-only pairing gate, PIN rejection, authenticated command dispatch.
- Scoring: note/frequency conversion, latency-aware aggregation, playback-clock alignment, seek/pitch-change reset behavior, synthetic-tone pitch detection, and silence/out-of-range rejection.
- Privacy/configuration: plist lint, required permission strings,
  Bonjour declaration, tracking-disabled privacy manifest, and manifest
  inclusion in the generated iOS target.
- Hosted CI: `swift test` plus generated iOS Simulator build.


## Legal and licensing surface

The SwiftUI application exposes a first-class **Legal & Privacy**
destination. It summarizes BYOL ownership boundaries, background-media
quality/compatibility behavior, privacy posture, trademark neutrality,
third-party notice requirements, and the rule that Gelo-owned releases
must not redistribute third-party media without documented rights.

The in-app surface is explanatory; machine-enforced distribution policy
remains in CI and repository documentation.


### One-folder compatible-library BYOL onboarding

The preferred onboarding path is one-folder and non-destructive. Users place the
matching compatible index, media container, and any valid `.sf2` files in a
folder and select that folder through Files/iCloud Drive.

Detection is structural rather than filename-based. The scanner validates the
index/container relationship, discovers any valid SF2 regardless of name, and
keeps large source assets in user-controlled storage.

## Reliability hardening — proposed 2026-10-06

See [per-change handover](HARDENING_HANDOVER.md) for validation limits.

- Controller operations use an actor-owned asynchronous FIFO gate: actor isolation
  alone cannot serialize a transaction across library/engine awaits. Views and
  remotes continue to use the same controller; no second playback state exists.
- Adapters report explicit natural completion. Snapshots retain an optional
  completion record and per-run UUID, allowing scoring to distinguish manual skip,
  replay, and repeat reservations. No timing-proximity heuristic remains.
- Failed automatic handoffs retain reservations and publish an error once rather
  than retrying on every poll. Partially changed adapter state is stopped and must
  reload the committed song before resuming.
- SoundFont prewarming cannot mutate playing/paused MIDI. The resolved candidate
  chain stays cached until file metadata/selection changes; cache identity is
  invalidated before mutating sampler loads.
- VerifiedLibraryBackup belongs to LibraryStorage; iCloud orchestration stays in
  the app. Provider-coordinated reads/writes use UUID-based backup directories and
  verified temporary replacements. Older display-name backups are preserved.
  Cloud deletion is deliberately scoped to `Documents/KANTA Backup` plus KANTA's
  iCloud key-value settings key; it never deletes the user's selected source folders.
- Privacy declarations cover app settings, selected-file/app-container metadata,
  and in-app timing. Metadata is not used for fingerprinting.

These implementations await macOS/physical-device validation; they do not close
LAN confidentiality, signing, actual indexed-library acceptance, or performance release gates.


## Repository-wide hardening — 2026-10-07 Manila (validation pending)

- Registry mutations publish in-memory state only after an atomic disk write;
  duplicate source IDs enter the existing byte-preserving quarantine path.
- Relative media paths reject NUL and resolve symlinks before the root boundary
  check. SoundFont availability is evaluated by the MIDI adapter, allowing a
  missing preferred file to reach the existing fallback chain.
- Library import copies and provider-coordinated scans run on utility tasks;
  the caller retains security scope until completion. Copies use the same
  verified temporary-copy helper as backups and preserve source bytes.
- Microphone start/stop and remote/Bonjour callbacks carry lifecycle tokens.
  Late permission results, samples, and cancelled connection callbacks cannot
  mutate a replacement session. Scoring aggregation is published by Host's
  snapshot refresh rather than on every microphone buffer.
- MIDI seek/pitch changes restore notes only while playing; pause/stop release
  sustain and silence voices. Binary cursors support nonzero Data start indices;
  elapsed-time conversion saturates before integer conversion.

These are source changes, not completed device or hosted acceptance. See the
handover for exact validation and remaining release gates.


### Canonical legal disclosure — 2026-10-07

`Features/Legal/LegalView` owns the legal notice and its presentation content;
`App/LegalDisclosureView` supplies only the modal navigation/dismissal shell.
Both entry points therefore show the same BYOL, third-party, privacy and rights
wording. `KantaTerminology` supplies the public app name and full display identity.
Optional iCloud Save/provider sync and plaintext LAN behavior must be reflected
in disclosures whenever implementation changes. The notice is informational,
not a separate license agreement or a rights-verification mechanism.


## Filename-neutral SoundFont policy — 2026-10-07

SoundFont import and selection is provider-neutral. A user-selected file is
accepted as an SF2 only when its RIFF header identifies a SoundFont bank. No
special filename receives priority. Explicit user selection wins; otherwise
deterministic path order is used. Distribution CI never inspects runtime
Files/iCloud imports and therefore cannot reject a user-owned SF2 by name.


## Release asset and App Store boundary — 2026-10-07

The iOS target owns a single-size 1024×1024 `AppIcon` in
`Sources/App/Assets.xcassets`; Xcode derives smaller iPhone/iPad variants.
Release validation rejects a missing, wrong-size, or transparent icon and checks
the iCloud Documents entitlements used by
`FileManager.url(forUbiquityContainerIdentifier:)`.

The shipping Info.plist declares `ITSAppUsesNonExemptEncryption = false` for the
current build because KANTA currently relies on Apple-provided/system
cryptographic facilities rather than a non-exempt proprietary crypto
implementation. Adding third-party/custom cryptography or materially changing
transport security requires export-compliance re-review before submission.

App Store screenshot automation remains opt-in and targets an available
Dynamic-Island Pro Max iPhone plus a 13-inch/accepted 12.9-inch iPad class. The
workflow verifies pixel dimensions against App Store Connect requirements before
publishing artifacts.


## Responsive Host control policy — 2026-10-07

The Host screen has one search/query state, owned by `HostViewModel`. The
expanded CONTROL surface owns the visible search field; `HostView` does not
also attach a global `.searchable` bar because that system overlay can obscure
queue/playback content on compact iPhone heights.

CONTROL rows must remain width-bounded by their List row. Search-result actions use the horizontal size class explicitly: compact iPhone
layouts render a fixed-size circular reserve action, while regular-width iPad
layouts render the labeled **Reserve** control. This avoids SwiftUI fit
measurement stretching the action to the row height. Queue titles keep layout priority over destructive icons.
Pitch/tempo controls use `ViewThatFits` so compact devices stack controls
instead of forcing horizontal overflow.

Functional UI labels use Latin text for deterministic system typography.
Baybayin strings remain separate in `KantaTerminology` for deliberate brand
surfaces where their rendering can be reviewed rather than appearing as an
uncontrolled fallback inside buttons, menus, or navigation chrome.



## Release boundary — 2026-10-07 follow-up

Manual TestFlight has a cheap Linux preflight before the dependent macOS job.
Credential values are scoped to the steps that require them; temporary keychain
credentials are generated per run. The uploader receives API_PRIVATE_KEYS_DIR
explicitly. Run/attempt identity supplies the build version. Export is internal-only
until device/legal/LAN release gates close; this does not add network encryption.

Public policy/support pages are static, separately deployed from `PublicSite/`.
Only those reviewed files go to the public host; Swift sources, user assets,
credentials, and internal Docs never enter the deployment. PRIVACY.md and SUPPORT.md
are the content sources; Tools/build-public-site.py reproduces the HTML. App legal
links and App Store metadata use the stable public hostname. The site introduces
no application backend, analytics, or library upload path.


## Screenshot gallery publication — 2026-10-07

Successful manual screenshot captures on main publish the four validated PNGs to
`screenshots/latest` on the dedicated `ui-screenshots` branch. Its root README
renders a gallery and capture.json records source commit and run provenance.
The capture job remains read-only; only the separate Linux publishing job has
contents write access. PR and non-main captures remain artifacts only, preventing
unreviewed previews from replacing the accepted-main gallery. Failed captures
preserve the previous gallery; run ordering prevents stale retries replacing newer
captures. Atomic, non-force Git pushes preserve history and reject concurrent
external branch changes. Publication uses GITHUB_TOKEN, introduces no app runtime
changes, and does not trigger another app build. The ZIP artifact remains available.



## Bundled still backgrounds — 2026-10-08

`BG/BUNDLED` is an XcodeGen folder-reference resource in the application bundle.
`KantaBuiltInVisualPreset` retains its ten existing persisted static identifiers
and maps each directly to an original PNG using `Bundle.main`. Display names
match the supplied artwork. `KantaBuiltInVisualView` uses the existing asynchronous
`LocalBackgroundImage` reader, shared with user-selected stage images, so Host
and fullscreen have the same rendering path. Missing resources retain the
procedural fallback. Selection/settings, controller ownership, user-library
bookmarks and animated presets are unchanged. Only the selected image is read;
there is no startup scan, network fetch or copy into the user's library.
Original bytes and dimensions are preserved, with exact hashes in
`ThirdParty/bundled-backgrounds.json` and distribution entries in the existing
media-provenance registry. The static guard verifies the pack and bundle wiring.

## Centered source-timed lyric pages — 2026-10-08

`KaraokeLyricPageSequence` preserves original MIDI/KAR fragment timestamps and
explicit slash, backslash and newline line boundaries. Unmarked cues retain
line-level timing. Binary search projects a stable two/three-line page from the
controller playback position; no estimated word duration or independent lyric
clock is introduced. `HostViewModel` publishes this projection on its 100 ms
snapshot refresh and after playback commands. Existing lyric-window consumers
remain compatible. Song replacement and metadata failures clear the page source.

`KaraokeLyricBlock` is the shared centered, heavy, outlined-text presentation for
Host and fullscreen. Fragments turn green at their source timestamp; upcoming
text stays white. Pause, seek and tempo changes follow the controller clock.
Fullscreen starts with playback chrome hidden; tap the stage or the accessible
Show playback controls button to reveal it. A noninteractive KANTA text watermark
uses 0.75 opacity at the bottom-right, outside the lyric block and playback panel.
No reference-brand artwork, lyrics or QR code is copied into the application.

## Adaptive singing orientation — 2026-10-08

Both iPhone portrait/landscape directions and all iPad orientations are declared
in Info.plist. The fullscreen player requests orientation through its hosting
UIWindowScene, not a process-wide device rotation setting. It only restores
portrait on exit after explicitly requesting landscape. Rotation failures surface
an error instead of silently implying success.

One persistent KaraokeStageView remains mounted across orientation changes:
GeometryReader updates its bounds without re-decoding a PNG or rebuilding the
AVPlayerLooper for a video. Aspect fill crops edges if necessary but never
stretches or transforms the source file. The stage owns its centered source-timed
lyrics and 75%-opacity KANTA watermark in both modes.

In portrait a bounded stage sits above persistent, scrollable transport controls;
in landscape the stage occupies the singing surface, with transport controls on
demand and a reachable rotate-back/exit action. Audio, queue, latency and LAN
controller remain owned by the same HostViewModel. The existing four screenshot
gallery outputs remain unchanged, with one verified iPhone landscape artifact for
iPhone screenshot acceptance; iPad landscape requires device rotation or a wider window, as iPadOS multitasking can forbid programmatic scene rotation. Physical-device rotation lock, split-window
iPad, and external speaker/BYOL video continuity still need device validation.

### Native fullscreen presentation orientation boundary

Screenshot #76 revealed the intermittent UIKit error `Requested: landscapeLeft,
landscapeRight; Supported: portrait` despite permitted Info.plist orientations.
Apple determines rotation from the **topmost fullscreen view controller's**
supported mask as well as the scene/app masks. SwiftUI's ordinary
`.fullScreenCover` host was intermittently portrait-only on the simulator.

`SingingFullscreenPresentation` now creates exactly one UIKit fullscreen
`SingingRotationHostingController` per presentation, with a stable
`supportedInterfaceOrientations` mask (`.allButUpsideDown` on iPhone, `.all`
on iPad) and explicit autorotation. The SwiftUI `HostView` holds an invisible
1×1 presenter and a single `@State` binding for modal ownership. The native
controller hosts the existing singing view; its Exit action sets the binding
false so UIKit dismisses it. This removes dependence on transient default
SwiftUI sheet orientation while preserving the existing controller, queue,
lyric, visual, and playback state. UIWindowScene geometry still uses the hosting
scene and reports denied requests without forcing unsupported iPad windowing.

## Local song search catalog — 2026-10-08

KANTA retains its existing structural, filename-neutral indexed-library
reader and validated index-to-container mapping. LibraryStorage now owns an
optional, disposable SQLite3 + FTS5 metadata index under the application
support directory (`KANTA/song-catalog.sqlite`). The index stores only
`SongID`, title, available artist/code and media kind. **No song bytes,
SoundFonts, source bookmarks or decrypted payloads enter SQLite.**
It is marked excluded from device/iCloud backup. The selected BYOL library
remains authoritative for media resolution; every enqueue still validates its
SongID through LocalKaraokeController.

BookmarkedKaraokeLibrary alone owns and accesses SQLiteSongCatalog on its
actor. After each validated library reload, a stable SHA256 catalog digest
skips unchanged writes; changed catalogs replace both metadata and FTS rows
in a single BEGIN IMMEDIATE/COMMIT transaction. Failure rolls back; search
fails safely to bounded legacy in-memory scanning and never exposes stale
persisted song entries when current bookmarks/sources are invalid.
Index generation runs off the main actor with the existing library reload.
The DB is a cache, not a second catalog authority or a cloud database.

Search terms are sanitized as Unicode letter/number tokens for FTS5 prefix
matching; bound parameters prevent SQL injection. A safely escaped LIKE
fallback retains substring matching for partial titles/song codes. Result
queries are limited to <=500 songs at a time and use deterministic title/id
ordering, stable offsets, and a 200-character query bound. The CONTROL
search UI uses 24-result pages with a short typing debounce and visible
"Load more" button. The LAN Remote retains PIN pairing/trusted LAN scope
and requests 50-result pages, at most 100 per request and offset <=100,000.
RemoteSongSearchRequest.offset is optional to preserve legacy protocol-v2
decoding and is validated before dispatch; request IDs continue rejecting
obsolete/out-of-order responses. No catalog dump, file path or bookmark
is transmitted to the Remote, just bounded LibrarySong metadata. DB updates
and file access stay entirely on the Host.

48,000-song synthetic coverage exercises FTS lookup, deterministic page
boundaries, stable persisted reopen, unchanged-data no-op, transactional
rollback, removal, Unicode/code search and SQL syntax injection. Hardware
benchmark, iPad multiwindow, protected library acceptance, privacy/legal
review and network encryption remain separate release gates.

## Browsable standalone title indexes — 2026-10-08

Recognized imported index files can be searchable even without a validated
media container. `DirectoryLibraryScanner` now marks structurally complete,
filename-neutral title indexes as `libraryIndex` independent of the media
pairing step. The existing strict unique-container and payload-verification
boundary remains unchanged. An index with unsupported identifier ordering,
unavailable container or unrecognized media does **not** gain playable status.

`IndexedContainerCatalog.catalogOnlySongs` projects the complete validated
title table as `LibrarySong(isPlayable: false)`. Both this projection and
`entries` use one stable source-UUID/record-ordinal SongID generator; when
compatible media is later added and a library rescan succeeds, existing
identities switch to playable without changing their search references.
`BookmarkedKaraokeLibrary` loads playable entries when it can validate the
matching container; otherwise it retains catalog-only titles, without
inventing media offsets, and keeps them separate from the playable
container-entry map. The local SQLite/FTS5 index stores the availability flag
alongside title/artist/code/kind and migrates existing databases with
`ALTER TABLE`. Catalog-only metadata is still sourced only from user-selected
Files/iCloud folders, never bundled, downloaded, or uploaded to a server.

The same CONTROL and PIN-paired LAN Remote result DTO now carries
`isPlayable`. Old protocol-v2 LibrarySong JSON lacking this field decodes to
`true`, preserving prior peer compatibility; `LocalKaraokeController`
rejects attempts to reserve a title-only entry *regardless of the sender*.
UI labels such rows "Catalog only" with no Reserve action. Source media,
SoundFonts, bookmarks, transport, queue ordering, privacy and playback
validation remain unchanged. Status on unsupported real-library variants
must not be represented as full song playback compatibility. The proprietary
original index and full title corpus are NOT stored in the repository.

## Large-library import acceptance UI — 2026-10-08

`LibraryCatalogSummary` projects two per-source counts from the active
`BookmarkedKaraokeLibrary` actor maps: playable loose/validated-container
songs and standalone-index catalog-only entries. It never scans user media
again or queries the SQLite index solely to display the summary.
`LibraryViewModel` publishes these counts after initial load and each
import, rescan, SoundFont/preference refresh, and source removal.
The Library source card now displays the actual searchable title total
and separate playable/catalog-only totals instead of misleading users
with a loose-file count alone. This is a UI projection of the original
controller/library authority; it does not enable unverified playback.

Remote search input is debounced by 200ms and cancellation guarded,
so typing doesn't generate a LAN query for every keypress across 48k
entries. The paired protocol's original request-ID stale-result defense
and page limits remain authoritative. Existing CONTROL query behavior,
queue reservations, local-only SQLite storage and export/distribution
boundaries are unchanged. A real user-supplied 1,797,052-byte index
was verified privately using a local structural acceptance probe:
47,998 decoded titles, 15,853 metadata records and exactly four
trailing bytes per title; original bytes/title corpus are not committed.
This probe is NOT a signed device import or media playback test.

## KANTA printed songbook codes and in-app catalog (2026-10-08)

The user-imported indexed-title order is authoritative for generated KANTA
reference codes: record ordinal 0 is code `00001` and 47,997 is `47998`
for the 47,998-entry supplied index. The same `IndexedContainerCatalog`
factory stamps codes onto both playable and catalog-only `LibrarySong`
rows, so SQLite/FTS5, CONTROL, LAN Remote and a printed book always resolve
the *same* SongID for the same imported index snapshot.

These codes are **not claimed to be original vendor/publisher identifiers**.
The title index's opaque numeric fields have not been proven to be public
song codes. Reordering/replacing the index changes row-reference meanings.
Multiple user-supplied indexes can reuse an ordinal code; results retain
unique source-scoped SongIDs. Native song code/artist from other supported
library types is preserved instead of overwritten.

`KantaSongbookView`, reachable from the main sidebar and CONTROL, reads
bounded 100-row pages from `BookmarkedKaraokeLibrary`; it never decodes
audio, fetches every title into SwiftUI, or weakens PIN-paired Remote queue
validation. It shows code/title/artist and catalog-only availability.
For offline/print workflows, the library actor can produce a metadata-only
UTF-8 CSV of the currently imported collection under the app's temporary
directory, then shares it only on explicit user action via ShareLink.
CSV quoting and formula-prefixed titles are escaped. Searchable titles and
codes remain in the existing private SQLite/FTS5 cache, automatically
refreshed when the import changes; no 47,998-song title corpus is bundled
in releases or GitHub. Privacy and rights depend on the user's own content.

## Session Favorites and artist metadata — 2026-10-08

KANTA now tracks actual started performances through `KaraokeSessionHistory`
in `GeloKaraokeCore`. A playback UUID is recorded once per run, so pausing
and resuming does not create duplicates; a deliberate replay receives a new
run ID and is preserved in order. Merely adding a song to the queue does not
record it. Ending the Host session, or an actual stop after the final queued
song, opens a consent prompt to save the completed song sequence. Users can
decline without writing it. The stop path also works for paired Remote
transport commands seen through the Host's authoritative snapshot.

`SavedKaraokeSetStore` owns locally persisted, atomic JSON metadata snapshots
under the Host's Application Support folder. Entries include stable SongIDs,
display title/code/known artist, order, name, and creation date; no audio,
lyrics, raw media, or access bookmark is copied. Collections can be renamed,
deleted or appended to Up Next from Home or Host. At reload time the Host
resolves each saved SongID through the currently authorized library and
enqueues *only playable* results; expired/missing media is skipped and counts
are reported. Existing queues are not erased and playback is not interrupted.
Corrupt Favorites storage does not disable library/audio startup.

`BookmarkedKaraokeLibrary` retains optional user-entered artist overrides
under `Application Support/KANTA/song-artists.json` by stable SongID. Owners
may edit missing artist metadata in the Songbook. The override applies on
reload/search, updates SQLite/FTS5 when available, survives restarts, and
does not edit imported source files. If an index contains only song titles,
artist fields remain explicitly unknown until the owner supplies them;
the system never assumes title fragments identify performers. Songbook UI
and CSV both expose artist metadata when present. The index-position KANTA
reference codes, catalog-only safeguards and PIN-paired Remote protocol are
preserved. Physical-device acceptance and signed TestFlight are separate.

## Live Soundcard / Vocal FX — October 2026

The optional live microphone monitor shares the existing actor-main
`MicrophonePitchMonitor` AVAudioEngine with room microphone scoring.
The unchanged upstream `inputNode.installTap` observes raw input for
pitch scoring; the separately connected, zero-by-default output chain is:

`inputNode -> AVAudioUnitEQ (bass/treble shelving) ->
AVAudioUnitReverb -> AVAudioUnitDelay -> AVAudioMixerNode ->
mainMixerNode/outputNode`.

Preset mapping covers Dry, Room, Studio, Concert Hall, Cathedral,
Plate and Echo using Apple's native DSP only, with bounded wet mix,
echo mix, ±12 dB tone settings and 0...85% mic output gain. DSP nodes
are attached once and parameter changes do not rewire running graphs.
With no output monitoring, the output mixer is hard-muted while
scoring may continue. Host starts microphone capture only when the
user explicitly enables monitoring and/or scoring; no audio is saved
or uploaded. Scoring never analyzes post-reverb output.

Live monitor is gated at runtime to a single wired-headphone, USB-audio,
or line-out output; speaker, Bluetooth, AirPlay and mixed routes fail
closed. Output gains are muted on route-change notification and stop.
Monitor opt-in is off by default and forcibly disabled on app startup
and iCloud settings restore, even if stored in a prior run. User-provided
external audio interfaces and mixers must expose their microphone
signal as a device input for KANTA processing; a microphone connected
directly to an external amplifier bypasses this DSP.

This release affects microphone audio routed through KANTA, not backing
MP3/WAV or sampled MIDI/KAR outputs (which retain existing volume/pitch/
tempo controls). Monitoring is not an OS-wide hardware soundcard.
Real iPhone/iPad headset/USB IO testing, latency and feedback behavior,
route interruptions, performance on low-memory devices and HDMI output
require physical acceptance before release.

## 2026-10-08 — Phase 1 vocal polish and Phase 2 party soundboard

Phase 1 expands the existing, route-gated input FX graph with Apple's
DynamicsProcessor (thresholded compression plus downward expansion/noise
gate), a dynamically adjusted high-shelf sibilance EQ, bounded 28 ms
delay-based vocal doubling, configurable Echo delay and an output PeakLimiter.
Six explicit vocal styles (OPM Ballad, Pop Star, Rock Concert, Duet, Studio
Clean, Party Karaoke) apply conservative parameter combinations. Preset
edits select Custom. `VocalFXSettings` includes backward-compatible decode
defaults for older iCloud/UserDefaults snapshots; monitoring still starts OFF.
Pitch-scoring observations are taken from the raw microphone input node
before the DSP output chain. This is a practical vocal polishing path, not
offline mastering for backing tracks or a DAW-quality pitch corrector.

Phase 2 introduces a separate `PartySFXPlayback` boundary injected into
the authoritative `LocalKaraokeController`. The concrete
`KantaPartySFXEngine` uses its own AVAudioEngine and four bounded
AVAudioPlayerNode voices, cached deterministic short PCM tone/noise
compositions authored for KANTA, a stop-all command and a user volume
cap of 65%. The eight pads are Applause, Cheering, Drum Roll, Air Horn,
Laughter, Ta-Da, Ding and Sad Trombone. Users may long-press a Host pad
to import a user-owned clip of at most 12 MB and 12 seconds through
Files, validated with AVAudioFile, copied/transcoded into KANTA's
private Application Support folder, or restore the original. No
third-party effect bytes ship in source control or the binary.

SFX commands are new backward-compatible actions in the existing
version-2 framed PIN-authenticated Remote protocol, executed by the Host
controller. The Remote never transfers or receives private sample bytes.
SFX playback does not modify karaoke queue, transport, MIDI SF2 player,
or scoring samples; user audio from imported short clips is not uploaded.
Four voices cap overlap; Stop SFX and Host-disappear stop all playback.
Remote command payload sound enum and volume values are validated
server-side and fail closed.

Phase 3 (pitch correction/harmonizer/tempo-aware harmonies) appears only
as an inert, disabled UI placeholder, not enabled signal processing.
Real iPhone/iPad testing remains necessary for latency, multiengine
audio-session conflicts, imported sample formats, route changes and DSP
quality. Never claim hardware certification from Swift/simulator CI.

## Extended Stage Display and party workflow (October 2026)

An iOS noninteractive external-display UIWindowScene is registered through the
scene manifest. The KantaExternalStageSceneDelegate supplies a UIWindow to
KantaExternalStageCoordinator; the Host registers its existing HostViewModel
and visual settings. The external scene renders KaraokeStageView exclusively:
synchronized lyric pages, selected visuals and KANTA watermark. This is one
authoritative Host queue; external display does **not** own transport, timing,
music/audio routing or singer reservations. When Host disconnects, external
scene shows KANTA standby. UIKit scene attach/detach remains the truth for
extended-output status, not the presence of an AirPlay route: some AirPlay
destinations only mirror the controller screen.

Host/Remote singer reservations use KaraokeCommand.enqueueForSinger and the
existing QueueItem.requestedBy metadata; Host core validates media before
enqueue. KaraokeCommand.moveQueueItem reorders the same PlaybackQueue and
keeps each UUID/singer association. PIN-paired Remote maps both actions into
bounded RemoteCommand fields and validates singer limits; no independent
remote queue or playlist. KaraokeSnapshot.nowSinging is optional for decoding
older remote snapshots.

SavedKaraokeSetStore now distinguishes .session from .playlist with a
backward-compatible decoding default .session. Named playlists store only
song metadata; append/remove/reorder operations use the actor's atomic JSON
commit. PlaylistEditor pages the established SQLite song search instead of
materializing the 47,998-row index in a view. Requeue still checks each ID
against the currently authorized, playable library.

KantaAudioInputDiagnostics presents the current AVAudioSession input/output
ports, selected preferred microphone, approximate I/O buffer and dry input
RMS supplied by the *existing* MicrophonePitchMonitor input tap. It does not
instantiate another capture engine. Changing preferred input is an explicit
user action. Dry scoring and fail-closed vocal monitor output protections
remain unchanged. Reported I/O buffer time is not measured end-to-end
AirPlay or microphone-to-amp latency.

KantaIntermissionMusic runs one optional local AVAudioPlayer outside the
karaoke queue. Owner-supplied audio is copied byte-for-byte into private
Application Support storage and stays disabled on launch. It loops while
Host transport is stopped, fading out when paused/playing and fading back
in after the queue finishes. No cloud streaming, microphone capture, audio
transcoding or remote media transfer. Player and media revalidation must be
tested on actual HDMI/USB/Bluetooth routes; a passing simulator build only
establishes compilation.

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

## KANTA branded visual system — 2026-10-08

The public site's `PublicSite/style.css` remains the visual color reference. The
SwiftUI app now uses matching **midnight navy** (`#0e1020`), **plum/lavender**
primary accent (`#b866d7`), royal blue gradient (`#436bca`), **cyan**
(`#85e7ed`) and Filipino sun gold (`#f6c771`). `GeloVisualStyle`
owns the theme tokens, shared card chrome and `kantaListSurface()` modifier
for native lists/forms. `RootView` requests dark appearance; there is no
global UIKit appearance override, preserving system file-pickers.

The app icon and in-app `KantaBrandMark` must share the **same owner-selected
neon sun/three-stars/microphone artwork**, not a procedural placeholder.
`AppIcon.appiconset/AppIcon-1024.png` is the installed iOS icon;
`KantaBrandArtwork.imageset/KantaBrandArtwork.png` points to the **exact
same Git blob** and is the renderable SwiftUI image. A ReleaseTooling
regression guard checks image-byte parity and PNG dimensions. Karaoke
stage media and timed lyric colors remain independent for readability.
Stored CI screenshots require recapture when presentation changes.

## Opt-in Filipino/English speech-to-search — 2026-10-08

`KantaVoiceSearchController` (SwiftUI app presentation layer) owns one
bounded Speech.framework `SFSpeechRecognizer` task and a temporary
`AVAudioEngine` microphone tap. Search text is delivered to the existing
`HostViewModel.query`, `KantaSongbookView.query`, or paired
`RemoteView.catalogQuery`; there is **no parallel song database, no
automatic enqueue, and no change to the existing authenticated LAN protocol**.

`KantaSpeechQueryNormalizer` is a Foundation-only core helper: it
removes explicit Tagalog/English commands such as “hanapin ang,” “search
for,” and “patugtugin ang”; preserves unqualified title/artist names
(including “Play That Funky Music”); and maps explicit English/Filipino
spoken code digits to the existing metadata search. Package regressions
cover titles, artists, Taglish command patterns, leading-zero song codes,
and preserving ambiguous titles.

The selected recognizer locale is explicit and checked against
`SFSpeechRecognizer.supportedLocales()`: Filipino (`fil-PH`/`tl-PH`
when present) and English Philippines (`en-PH`, with `en-US` backup).
iOS Speech.framework recognizes one locale per request; the language
picker is **not an auto-detect/mixed-language accuracy guarantee**.
Recognition requests prefer on-device recognition when supported;
otherwise Apple's recognition service may receive audio. The UI and
permission strings disclose this. KANTA doesn't retain microphone
recordings; the resulting text is user-visible/editable. Mic/Speech
permissions are requested only upon tapping Voice Search; capture stops
on finish, timeout, permission failure or dismissal. Host disables
starting a second capture during live scoring/monitoring; Remote uses
its own phone microphone.

An accuracy target of 90–100% is **not verified** by source or CI:
Filipino availability differs by OS/model/installed languages, and
recognition of Taglish song titles, local names, noisy amplified rooms,
low-end phones and external audio routes needs a labeled real-device
validation corpus before any measured claim.


## Opt-in multilingual Filipino speech pack — 2026-10-08 (draft PR #44)

`project.yml` pins the open-source `WhisperKit` product from
`argmaxinc/argmax-oss-swift` at **exact version 1.1.0** as an **iOS
application-only** dependency. The domain Swift Package, song catalog,
FIFO reservations, microphone scoring/FX and LAN contract remain unchanged.

`KantaFilipinoModelStore` is a main-actor, user-opted-in manager with
`notInstalled → downloading → preparing → ready` states. It downloads
the **multilingual** `openai_whisper-base` Core ML variant from the official
`argmaxinc/whisperkit-coreml` Hugging Face repo via WhisperKit's snapshot
download (never the English-only `base.en`). Published model contents
are approximately **147 MB** plus tokenizer/cache. The app does not bundle
these model weights or initiate downloads during startup, typing, queueing
or app launch. Model data and tokenizer are put in app Application Support,
excluded from iCloud backup, with an atomic local install marker written
only after `WhisperKit.loadModels` succeeds. Cached files are rechecked for
the three expected compiled Core ML bundles and bounded beneath the private
storage root. The UI offers explicit download, progress, retry and removal;
removal is denied during transcription/download. A corrupt/incomplete
installation can be removed and reinstalled. WhisperKit's HTTPS/HF cache
is the download trust path; **strong upstream file-hash pinning/revision
provenance requires a further release audit**, so do not overstate SHA
verification.

`KantaFilipinoVoiceCapture` holds a short (~8 s maximum), permission-gated
mono LPCM recording in the app's temporary directory, stops input before
inference, restores AVAudioSession category/mode/options, and deletes the
captured file after decoding, errors or cancellation. Only the installed
local Core ML folder is passed to `WhisperKit` with `DecodingOptions(
task: .transcribe, language: "tl")`: output is the same editable
`KantaSpeechQueryNormalizer` title/artist/code query used by Host,
Songbook and paired Remote. This path does **not** require Apple's Filipino
`SFSpeechRecognizer.supportedLocales`. English (PH) continues through
the Apple Speech backend and may process audio online.

A single shared `KantaFilipinoPackControls` SwiftUI component is reused
in Settings and the search surfaces. The Host blocks starting a second
capture while room scoring/Soundcard is running. **Audio under playback
and route changes still need device testing**; do not infer concurrent
speech/scoring correctness from non-overlapping UI controls. KANTA does
not persist voice recordings or transmit audio to a developer endpoint.
Speech output is text submitted to local FTS5 / already-authorized
LAN search; it never directly reserves.

The quality gate remains `Docs/VOICE_SEARCH_ACCEPTANCE.md`: A12
iPhone XR/iPad 9 thermal and inference latency, the multilingual
Filipino/Taglish corpus, quiet/loud environments, word error rates,
usable@3 ≥90% per cohort and consent/network/storage checks must be
measured before advertising 90–100% success. The currently pinned
model is a capability baseline, **not a proven accuracy threshold**.

## Concurrency and idempotency invariants — 2026-10-09

Playback remains exclusively owned by `LocalKaraokeController`. Its operation
gate serializes commands and polling across engine/library suspension points;
canceled waiters are removed, while canceled snapshot polls return the last
authoritative publication. Distinct intentionally repeated reservations remain
supported.

Each PIN-paired LAN connection keeps a bounded (256-entry) command replay ledger
keyed by `RemoteEnvelope.id`. The same envelope does not execute a command
twice within its retention window: previous success returns an authoritative
snapshot, and failed operations return the recorded failure. The ledger is
per connection and is not cross-reconnection exactly-once delivery; the LAN
protocol v2 wire format and current plaintext confidentiality limitation
are unchanged.

Session Favorites Save claims UI in-flight state before suspension and carries
a stable session UUID into `SavedKaraokeSetStore`. Store writes are actor
serialized and retries with the same UUID return an existing collection, even
after reopening the file. Requests without a key create distinct collections.
A second playlist Load tap cannot overlap an in-progress multi-enqueue, while
a deliberate later Load is valid.

CSV Songbook exports receive unique OS-temporary URLs so a later share cannot
overwrite an earlier in-use export; share consumers own eventual cleanup.
Filipino voice transcription holds ownership of its temporary audio file until
WhisperKit finishes, even when cancellation invalidates its UI generation.

For future code changes, assess every actor suspension, retry identity,
cancellation boundary, atomic file update and concurrent failure test; preserve
the mandatory repository-wide contract in `AGENTS.md`.

## Draft integrated Soundcard pitch correction — 2026-10-09

PR #40's optional, default-off monophonic pitch correction is integrated
with PR #44's vocal polish chain, preserving its custom settings migration,
Filipino/Taglish voice search, UI branding, and concurrency/idempotency guards.
DSP route: raw input tap (dry scoring) -> native TimePitch -> EQ ->
compressor/expander -> de-esser -> doubler -> reverb -> echo ->
output peak limiter -> hard-muted monitor mixer -> output.
No graph rewiring while running. Confident frequency measurements map to
12 roots and five scales at bounded +/-200 cents with smoothing. Silence,
disabling the effect, stop, or an unsafe route resets pitch to neutral.
Optional settings decode from older Soundcard saves without activating
monitoring. Wired/USB/line-out route policy and scoring isolation remain.

This is not licensed Auto-Tune, formant-preserving correction, or
professionally verified low-latency vocal processing. The previously
observed Host/CONTROL SIGSEGV still requires runtime diagnosis.
Real physical microphone, latency/feedback, route, and user hardware
acceptance must precede release.

## Host startup audio-resource lifecycle — 2026-10-10 (draft)

Navigation to Host must not instantiate a microphone-route diagnostic observer or decode a persisted intermission audio file merely to render controls. KantaAudioInputDiagnostics activates AVAudioSession route observation only while the diagnostics panel appears and removes its token on disappearance. KantaIntermissionMusic stores a validated owner-file reference at Host construction, creates its AVAudioPlayer only after an explicit per-session enable while karaoke transport is stopped, and releases it on Host exit. Existing microphone permission/scoring paths, private media provenance and the authoritative playback controller do not change. VocalFXSettings ensures restored values are finite before native DSP parameters are configured. These changes reduce startup side effects but require crash-report/runtime verification, not optimistic acceptance.

## Same-run Host smoke in Swift Core CI — 2026-10-10 (draft)

Core CI previously finished after an unsigned Release simulator build; it never launched Host, allowing a source-compiled but SIGSEGV-crashing screen to pass. The **existing Core CI job**, not another workflow, now boots one available iPhone simulator after building once and launches demo Host and expanded CONTROL. Each screenshot is accompanied by a successful explicit simctl termination, rejecting a SpringBoard image after the process crashes. On failure it uploads bounded logs, IPS crash report(s), matching application and dSYMs. No simulator may be silently skipped. Tests for device selection and workflow ordering are in ReleaseToolingTests.

**Budget and accuracy:** This is part of the owner-approved single *future* Core CI attempt, which must remain untriggered until runner capacity is restored and the final branch is reviewed. It adds runtime execution to the same job and therefore may increase that one run's duration. Release smoke does not replace opt-in full Debug screenshot acceptance, iPad rotation, signed TestFlight or hardware audio. Do not merge if the new live Host/CONTROL gate fails, or call absent IPS evidence a pass.

## Intermission published-volume crash fix — 2026-10-10 (draft)

An actual iOS Simulator IPS from Core CI #38002988380 symbolicated a main-thread stack overflow from `KantaIntermissionMusic.volume.didset -> volume.setter` recursion while SwiftUI materialized Host's StateObject. A property observer must **never reassign its own `@Published` property**. Intermission volume is now `@Published private(set)` and updated through `setVolume`, which clamps non-finite/out-of-range input, persists valid owner preferences, updates a playing sound, and otherwise does nothing. Restored values are clamped in initialization before being published. This maintains single-owner state, idempotent UI writes, and opt-in audio semantics; no additional audio graph, automatic playback or privacy scope was added. Requires exact-head runtime CI before the crash is considered fixed.


## Post-merge architectural baseline (October 10, 2026)

The consolidated PR #44 architecture now exists in `main` (merge `6dcdf5f`): one authoritative karaoke controller and queue, security-scoped BYOL files and metadata-only SQLite Songbook, optional PIN-paired **unencrypted** LAN Remote, dry-tap scoring with safety-gated vocal DSP plus default-off experimental monophonic pitch correction, local speech search plus optional WhisperKit-based Filipino/Taglish model, separate Stage Display, generated SFX and privacy-constrained encrypted Songbook exports. Host audio diagnostic observers and intermission playback resources activate on demand; the in-app Legal screen shares customer wording with the public site. The separate static site hosts `/legal`, `/privacy` and an empty Songbook reader, not a user catalog.

**Evidence boundary:** #38003895381 validated earlier-head Swift tests, Release simulator build and Host/CONTROL liveness, including the `KantaIntermissionMusic.volume` recursion remedy; final in-app Legal view edits were not rebuilt on the merge SHA. Keep physical microphone/speech accuracy, encryption, accessibility, publishing/signing and legal reviewer gates open. Detailed earlier draft sections remain valuable implementation history, but they do not override this current status.

## Starter karaoke storage boundary (PR #45)

`LibraryStorage.StarterKaraokeStore` is an actor for KANTA-owned optional `.kar` copies only. Its allowlisted stable IDs prevent traversal into user libraries. It serializes install/remove/restore operations, stores removal tombstones atomically in the managed directory, and preserves explicit removal across relaunches. Source fixtures are separate from managed copies. This service does not introduce another playback controller or queue. **Pending:** package generated `.kar` resources in the app, project them through the existing library catalog and My Set UI, and verify lyric parsing and playback before release. Commercial rights review and simulator acceptance remain gates.

## PR #45 completed source integration — October 10, 2026 (validation pending)

This supersedes the pending starter paragraph above. AppServices composes one
StarterKaraokeStore into BookmarkedKaraokeLibrary. Bundled deterministic KAR files
and the original procedural SF2 are folder resources. The library loads private
managed copies into its existing catalog with stable `starter:` IDs, resolves
assets and lyrics through existing MIDI parsing, and prefers imported SoundFonts
with the bundled starter-only tone fallback. No new controller, queue or audio
engine exists. LibraryView projects LibraryViewModel state and never constructs
storage. LocalKaraokeController.playNow uses its existing operation gate and
prepare-before-commit transaction; intentional queue reservations are preserved.

File transactions are synchronous inside the store actor with atomic writes,
allowlisted IDs, symlink/overlap checks and fail-closed corrupt tombstones. A
monotonic store revision protects catalog publication across actor reentrancy.
Remove/restore updates only starter entries without invalidating BYOL scopes or
container payloads. Catalog paths can be injected for isolated storage tests.
The explicit simulator-only acceptance hook uses the real AppServices/engine and
writes local JSON; device builds exclude it. See STARTER_KARAOKE_PACK.md for exact
provenance, removal/queue behavior, coverage and evidence boundaries.


## Original bundled karaoke architecture — October 10, 2026

PR #45 follows one authoritative AppServices → StarterKaraokeStore → BookmarkedKaraokeLibrary → LocalKaraokeController → native MIDI sampler path. Three read-only first-party KAR resources are installed as private managed copies with stable starter IDs; removal remembers a durable tombstone and cannot touch user sources or imported SoundFonts. The procedurally generated tone falls back only for these first-party demos. Revisioned actor snapshots and atomic file transitions protect against concurrent stale catalog state.

Core CI #242 (run 38019883726) passed on 1ee391e: 182 Swift Testing cases, an unsigned Release iOS Simulator build, Host/CONTROL liveness, all three starter songs playing with the native sampler, timed lyrics, pause/seek, FIFO queue, persistent removal and offline restore after process restart. Simulator screenshots and JSON are in artifact #11657971189. The separate UI Screenshots workflow was skipped, not passed.

Customer LegalView and PublicSite index/features/support/legal/privacy are only presentation/policy surfaces; they do not create a hosted karaoke catalog. Because the in-app SwiftUI LegalView changed after 1ee391e, this new PR head requires fresh green CI before merge. Source/website updates are not evidence of live production deployment. Apple signing, signed internal TestFlight upload, physical iPhone/iPad audio, accessibility, public support/publisher identity, LAN encryption and commercial legal review remain open.

## Experimental vocal note-tracking stability — 2026-10-10

`VocalPitchQuantizer.nearestNote` shares confidence-gated scale targeting
with the new platform-neutral `VocalPitchFollower`, which maintains one
bounded, note-level state owner for live correction. It debounces a neighboring
note transition until two consecutive qualified observations agree, while
allowing immediate large melodic leaps. Missing/low-confidence observations
release correction and clear the lock within two tap updates; changed key or
scale, disabled correction, stop, and unsupported routes clear it immediately.
`MicrophonePitchMonitor` still drives only the existing native
`AVAudioUnitTimePitch` from the main actor. No DSP graph changes, storage,
altered dry-scoring tap or copied third-party DSP code are introduced.

MIT-license candidates and hardware-gated adoption are documented in
`Docs/VOCAL_DSP_LICENSE_AND_ACCEPTANCE.md`. Production Soundcard remains
experimental/default-off pending actual iPhone/iPad wired/USB audio testing.

## Source-wired MIT pitch correction — PR #46 (October 10, 2026)

Supersedes the earlier PR #46 native-only proposal. The current **draft**
inserts `SoundpipeAudioKit.PitchCorrect` (tag 5.7.4) as an actual
`AVAudioNode` in the existing `MicrophonePitchMonitor` engine.
The Soundpipe `AudioKit.Node` input adapter references the **same**
AVAudioInputNode; there is no second engine/input tap. The immutable
audio graph is `input → Soundpipe PitchCorrect → Apple TimePitch →
EQ → compressor → de-esser → doubler → reverb → echo → limiter →
hard-mute mixer`. Key/scale produce a bounded note-frequency table,
and the Soundpipe Audio Unit receives correction amount/speed.
Only the user-selected backend has nonzero correction. Native fallback
uses the existing hysteresis follower. Both backends return neutral on
disabled/unsafe monitoring routes; `observation` continues upstream
and unchanged for dry scoring. The existing Core player/FIFO queue,
remote state and local speech services are not modified.

`VocalPitchCorrection.backend` is an optional Codable field for migration
and defaults to Soundpipe when the correction is enabled. No new
microphone recording, audio upload, backup category, third-party media
catalog or network processing exists. The website `PublicSite/guide.html`
illustrates the audio path and UI, while `PublicSite/licenses.html`
carries exact full software notices. The app bundles matching notices
as an Xcode folder resource and exposes them in LegalView.

**Acceptance is unverified:** exact-head Swift CI, iOS Release build,
Audio Unit lifecycle/scale-update/thread timing tests, hardware USB/
Lightning/line-out latency, power and feedback, full simulator capture,
Word companions, licensing and signing. For details see
`Docs/VOCAL_DSP_LICENSE_AND_ACCEPTANCE.md`.

## SoundpipeAudioKit DSP integration — PR #46, October 10, 2026

This supersedes the earlier native-only candidate review. The current draft
connects the actual SoundpipeAudioKit 5.7.4 `PitchCorrect` node inside the
existing single `MicrophonePitchMonitor` AVAudioEngine, before Apple's
`AVAudioUnitTimePitch` fallback and before existing polish and limiter.
An AudioKit Node adapter wraps the same input node, **not a second audio
engine**. Key and scale feed allowed note frequencies; amount and speed
drive Soundpipe parameters when the user selects that backend.
The alternate Apple native backend uses `VocalPitchFollower`; only
one tuning backend has nonzero correction at a time.

The dry scoring tap and observation timeline, output route hard-mute,
record permission and default-off microphone monitoring remain.
`VocalPitchCorrection.backend` is optional for compatible settings
decoding, and defaults to the Soundpipe choice when enabled.
MIT/BSD notices are stored in `Resources/Licenses` and exposed through
LegalView and `PublicSite/licenses.html`. The illustrative
`PublicSite/guide.html` is staged in source.

CI build, live AudioUnit startup, voice performance, scale update safety,
hardware latency and feedback validation are outstanding. The branch
is draft/unmerged, not publicly deployed. See
`Docs/VOCAL_DSP_LICENSE_AND_ACCEPTANCE.md`.

## Local support diagnostics architecture — PR #46

`Sources/Core/Diagnostics/KantaDiagnosticTimeline.swift` defines a
finite, privacy-reviewed event vocabulary and prunes by 200-entry
capacity and a seven-day retention window. The only state owner is
`Sources/App/KantaSupportDiagnostics.swift` on MainActor. It persists
an opt-in, iOS protected, backup-excluded app-private event JSON and
builds a typed, reviewable report. User-level Settings own the explicit
enable → reproduce → prepare → ShareLink or erase flow. No SDK,
server, background telemetry or audio/PII-bearing event payloads.
`AppServices` maps safe mic/DSP/route callbacks from
`MicrophonePitchMonitor`; the audio render callback never writes logs.
Root/Host lifecycle events are discrete. App termination does not
guarantee a crash callback; an interrupted foreground session is a
reproduction hint only.

`manual-ad-hoc-ipa.yml` is a separate human-dispatched signing
workflow with Ubuntu credential/profile preflight, ephemeral macOS
keychain, explicit registered-device Ad Hoc provisioning and no
automatic BetaDrop upload. It does not modify the existing internal
TestFlight workflow or bypass Apple's signing rules. Neither workflow
has been triggered for the current PR.
See `Docs/BETA_SIGNING_AND_SUPPORT_DIAGNOSTICS.md`.

## Evidence-only XCUITest target (2026-10-10; unvalidated branch)

`KantaUITests` is an iOS UI test bundle generated by XcodeGen, dependent on
the existing `GeloKaraoke` app target. It exercises visible controls and
takes named XCTest screenshots while the actual composition root, starter
store, native KAR sampler, queue and Host controller remain authoritative.
No new playback queue, fixture audio engine or production shortcut is added.
The manual `ui-screenshots.yml` workflow can record the iPhone/iPad
simulator framebuffer. It is opt-in, its MP4 is normally silent, and
screenshots and actual-tap evidence must remain separately labeled.
See `Docs/UI_VISUAL_WALKTHROUGH.md`. No new runner test has been executed.

## October 11 stage implementation status

This batch preserves PR #47's opt-in screenshot/video workflow and updates its
XCUITest navigation for the new default stage. Real starter-song playback
captures and demo-only reservation/cancellation tests are separately named.
Syntax/source checks are not an iOS build or camera validation. No new paid
Actions, merge, signed IPA or release is claimed by this source update.


## Reborn recovery boundary — 2026-10-15

Root docs/ARCHITECTURE.md describes the new platform separation and reconstructed missing storage/export/SFX boundaries. Existing LocalKaraokeController remains authoritative. Missing paths were restored individually; absent infrastructure was newly authored. Historical CI/release claims above do not validate this batch.


## Reborn online runtime validation — October 16, 2026

The monorepo root owns the active workflows. `ios-runtime-artifacts.yml` is owner-labeled/manual only and compiles the Xcode UI target before iPhone/iPad interactions. Stable starter/current-song accessibility identifiers let tests await real controller playback without assuming a title appears immediately after preparation. Captures distinguish real playback from explicitly named demo fixtures. After both walkthroughs pass, a separate unsigned personal-signing iPhoneOS archive omits iCloud entitlements and uses the existing packager; it is not a signed release. See root `docs/ARCHITECTURE.md` and the current root handover instead of the historical source/status paragraphs above.

## Reconstructed encrypted Android controller — October 16, 2026

The optional `EncryptedCompanionView`/`EncryptedCompanionClient` path uses system CryptoKit KCP1 and Network.framework to control Android 0.10 hosts. It is independent from recovered Bonjour/PIN control, which remains plaintext and explicitly legacy. There is no protocol fallback or secure iOS host in this batch. The remote sends bounded metadata commands only; Android's existing SessionController/FIFO owns reservations and playback. Foreground loss, screen closure, timeout and explicit disconnect revoke connection state. Root docs/ARCHITECTURE.md and docs/TESTING.md define framing, limits and the changed-source interoperability gates; simulator/package results do not establish physical Wi-Fi or signing acceptance.


## Fullscreen modal ownership repair

The background SingingModalPresenter now retains a weak identity for only the karaoke player it creates and dismisses that player directly. UIKit's presentedViewController may refer to an ancestor's presentation, so it must never be used as permission to dismiss unrelated SwiftUI menus or sheets. The active-playback walkthrough verifies that More remains visible across redraws, then enters/exits the library without changing playback. UI harness waits for rendered controls and observed menu presentation, with bounded retries only for the idempotent open-menu action. This repair changes app presentation only; package/core source, FIFO ownership and audio semantics remain unchanged. Changed-source simulator execution remains required. Reference: https://developer.apple.com/documentation/uikit/uiviewcontroller/presentedviewcontroller .


## PR #51 foreground player lifetime repair — October 18, 2026

The online sheet owns transient preview identities and one MainActor native WebKit owner. Replacement retires the old coordinator before attachment; inactive/disappear/Stop/Done suspend and blank the WebView and invalidate callbacks. Repeated links get new native identities. LocalKaraokeController/FIFO and audio sessions are unchanged; audio-focus coordination remains open. See root `docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md`.


## Native online-player test boundary (PR #51)

KantaNativeTests compiles the unchanged Features/Host/YouTubeEmbeddedPlayerView.swift into a simulator-hosted XCTest bundle and exercises real native owner/coordinator cleanup. Release apps retain their existing testability, signing and dependency settings. The focused root smoke selects three native cases plus its existing navigation case and requires explicit pass evidence. This does not establish iOS audio-session arbitration or real YouTube/device acceptance; see ../../../docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md.


## iOS audio handoff candidate — October 18, 2026

Based on PR #51 head `5d25f62993c8b003099c345b5387172a1fdf5af7`. AppServices constructs one MainActor KaraokeAudioOwnership alongside the existing LocalKaraokeController and a shared intermission owner. The coordinator owns only an ephemeral online UUID/retirement callback and foreground/microphone eligibility; it owns no queue, local transport, persistence or YouTube commands.

Online claims acquire the controller's existing cancellation-aware operation gate, reject actual playing state, stop SFX, suspend both idle rendering engines without Pause/Stop or position changes, then atomically recheck microphone/foreground/monitor eligibility on MainActor before stopping intermission and configuring the shared playback session. Local load/resume/playing seek and SFX retire the online native owner before native output. Remote commands and natural advance use these same controller paths. Capture announces activity synchronously before its permission await and releases it on denial/failure/stop; native output preserves capture's playAndRecord category. Session errors fail visibly. Foreground loss retires online ownership; returning does not reopen it.

The sheet cancels pending claims on close and rechecks its request UUID/foreground/ownership after suspension. The representable also checks ownership before attachment/load, preventing a revoked pre-mount session from constructing an active player. Stale release cannot clear a replacement. Intermission stops immediately, invalidates fades, and requires explicit re-enabling; automatic snapshot updates cannot reclaim online audio. Source files and reservation/playback identities are unchanged.

Regression coverage adds controller preparation/cancellation/microphone races, paused song/FIFO identity, explicit Next/SFX handoff, stale release, foreground, session failure and both rendering routes. Native smoke adds real WKWebView retirement-before-session configuration and synthetic AVAudioPlayer intermission handoff/restart; the named-pass verifier now requires all five native cases plus navigation. The test target compiles the two production components directly; no app testability, dependency or permission change.

Local layout, presentation, starter resources/generation and whitespace checks passed. Hosted Swift compilation, package execution, unsigned simulator app build and expanded native/navigation smoke are pending on the resulting source. Physical audio timing, real embeds/ads, interruptions, privacy and full iPhone/iPad release acceptance remain open. No zero-overlap timing, signing, merge, deployment or release is claimed.


### Accepted hosted iOS audio coordination — October 18

Source `6f7a0ece9e42cc500ce4f52cc414c58e5e11b58e` passed reconstruction 39112387766 (178 Swift tests, 54 tooling tests, unsigned simulator app build and Android validation) and smoke 39112387680 (all five native cases plus focused navigation, explicit named-pass gate). The iOS session/auxiliary implementation and hosted regression gap is closed. Actual SwiftUI online-sheet/media flow and physical embed/interruption/privacy/ads acceptance remain open; no zero-overlap timing, merge, signing or release is implied. See docs/YOUTUBE_LIFECYCLE_CHECKPOINT.md for evidence and next steps.


## Online-screen UI coverage candidate — October 18, 2026

PR #51 baseline 24db7a1 now adds two real XCUIApplication flows through Stage CONTROL's actual online sheet. They cover invalid-link rejection before WebView construction, explicit valid entry, stop/reopen, background/return without resurrection, Done/reentry and navigation recovery, plus preserving a paused original starter and explicitly resuming it afterward. Stable accessibility identifiers and link-keyboard Done dismissal support the real UI; no fake player or launch-environment bypass is introduced.

The synthetic video ID never autoplays. External YouTube availability is not a fixture: valid entry must reach either actual native player controls with a WebView or the production recoverable load-error state, with retained outcome screenshots. A local audio-claim failure is not accepted as an external-load error. This verifies UI lifecycle/recovery, not successful remote media or deterministic external-error delivery. Existing native process-failure, audio-handoff and controller/FIFO regressions remain required.

Focused smoke retains all five native cases and navigation and now requires both named online-screen cases (eight passes total). The full release walkthrough gate also requires all seven methods in the UI suite; timeouts, serial execution, failure detection, evidence retention, signing and deployment boundaries are unchanged. Local layout/presentation/YAML/whitespace and 55 tooling tests passed. Hosted app/test compilation and actual simulator execution are pending on the resulting source. Physical embed/ads/interruption/privacy and exact-candidate full iPhone/iPad release gates remain open.


## PR #51 built-in and browser remote candidate — October 18, 2026

Owner explicitly moved the approved Companion Remote UI and browser implementation into PR #51. Source now adds native role entry, locally served responsive remote assets, private-interface HTTPS with per-session identities and fingerprint trust bootstrap, bounded guest authentication/replay/expiry, native pinned clients and controller-owned local commands. No decoder, persistent media/queue format, native dependency, sensor permission, signing or website release change. Existing KCP1/legacy paths remain separate. The shared HTML assets are platform-owned copies checked for equality; neither native build depends on the other platform.

Local Java core: 524 checks passed, including 45 actual TLS checks. API 36 compilation of PinnedBrowserTransport, 55 tooling tests, Swift layout/presentation/starter guards and captured Songbook interop passed. Local Chrome was blocked by the environment's Unix socket restriction; UI is not accepted from syntax/transport checks. Native builds, nine-case simulator smoke, full eight-case iPhone/iPad walkthrough, real-browser tests, cross-platform/device trust/security/media acceptance remain pending. Unsupported expanded shortcuts are disabled, not represented as completed features. See docs/COMPANION_BROWSER_CHECKPOINT.md (root-relative) for exact trust, expiry, limits, source and acceptance scope. No physical latency/performance result, merge, signing, deployment or release is implied.

## Embedded Stage UIKit presenter — candidate (2026-10-24)

The merged single-Host-SwiftUI-sheet approach did not resolve the iPad regression. On main source `ca23b87871feb5b384c1613d8ba9d75457575456`, the full walkthrough [#40081230815](https://github.com/usermalik20-del/Kanta-reborn/actions/runs/40081230815) showed the expected keypad missing after embedded Search was tapped. The failure-time hierarchy showed the Host stage and Search control but no accessible keypad; the internal SwiftUI/UIKit presentation path remains unconfirmed.

The current repair candidate moves only embedded Stage Search/Queue ownership to `KantaEmbeddedStageSheetPresentation`, a stable UIKit presenter mounted in the Host background. Host CONTROL/session-save/Favorites keep their SwiftUI sheet; fullscreen Search/Queue keep their separate child sheet. All use the existing `KantaPresentationGate` and the same `HostViewModel`; playback, queue, media and persistence ownership do not change. Interactive and button-driven dismissal both return ownership from UIKit callbacks. The existing keypad assertions remain enabled.

This is a candidate, not accepted runtime behavior. Require source-contract checks, Swift package tests, focused iPhone smoke, reconstruction and the full iPhone/iPad walkthrough on the exact final PR head. No merge, signing or release is implied.


## Embedded Stage presentation readiness (2026-10-24)

The UIKit-owned embedded Stage Search/Queue presenter is mounted as a full-bounds, non-interactive Host background anchor. A 1×1 anchor is not the presentation surface. The presenter must re-evaluate requests after both appearance and layout, recover an orphaned controller, and emit a deduplicated `KANTA_STAGE_PRESENTATION` diagnostic when the view is not in a window or another controller is already presented. These checks are lifecycle safeguards, not proof of iPad runtime acceptance; both device walkthroughs on the exact candidate SHA remain required.


## Compact persistent workspace — 2026-10-26

The embedded Host is a single persistent workspace rather than a destination screen: the stage visual remains present above a compact now-playing strip and the existing Search/Queue/More and Settings/CONTROL accessory flows. Width-aware layout places frequent actions in the header on iPad-sized layouts and in a bottom rail on compact layouts. Both layouts dispatch to the same `HostViewModel`, `KantaPresentationGate`, and `LocalKaraokeController`; no view-local playback owner or queue is permitted.

Keep the stage as the highest-priority visual surface and keep its background non-interactive so it cannot intercept control taps. Preserve the approved main-player transport invariant: one Play/Pause toggle plus Next, with no Previous or progress/scrub bar. Stage Visuals, Remote, Settings, and iCloud must continue to use their existing owners until implemented as separately validated accessories.

