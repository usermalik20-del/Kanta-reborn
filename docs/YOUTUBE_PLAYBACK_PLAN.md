# YouTube playback — queued after reconstruction

Owner request: 2026-10-15. Preserve FIFO: finish and publish the current iOS restoration / Android foundation batch before implementing this integration.

## Product decision

Use the official visible embedded YouTube player, with its normal ads, controls, branding, links and playback verification intact. Do not add ad blocking, content extraction, audio separation, stream proxying, downloading or background playback. No claim of ad-free playback. Keep optional YouTube use separate from offline-first local playback.

## Functional mapping

| KANTA feature | YouTube integration boundary |
| --- | --- |
| Import | Add a validated YouTube video link as metadata; do not copy media bytes |
| Reservations / singers / Favorites | Route video references through the existing authoritative queue; preserve duplicate reservation identities |
| Play / pause / Next / volume | Official IFrame API commands and state events; Next selects the next authorized reservation |
| Tempo | Only playback rates returned by the official player's available-rate API |
| Pitch / stem isolation | Unsupported for YouTube media; disable those controls instead of extracting audio |
| Timed lyrics | Use lyrics already in the visible video; do not scrape captions or fabricate KAR timing |
| Scoring | Requires a separately licensed, time-aligned melody reference and validation; not automatically available from a YouTube video |
| Scenic / camera / branding | Must not cover, replace or obscure the YouTube player; use other UI regions |
| Fullscreen / TV | Use supported visible player presentation and verify platform restrictions; never create audio-only playback |
| Offline | Local licensed media only; no YouTube download cache |
| Privacy | Explicit opt-in Internet playback, YouTube/Google privacy links, correct embed identity/referrer; platform consent and policy review |
| Remote | Commands project the same host controller state; do not add another playback queue |

## Implementation acceptance

1. Finish current native reconstruction milestone and inspect exact APIs at that revision.
2. Add an explicit video-source type without breaking persisted local song IDs or queue semantics.
3. Use official WebView/IFrame integration on each native platform, correctly identified and sized, with lifecycle cleanup and stale-callback protection.
4. Validate state changes, unavailable/age/region/embed restrictions, interruption, cancellation, failed media preparation and mixed local/YouTube FIFO transitions.
5. Update privacy, rights, feature matrix and user guide before release. No API keys in clients or Git; in-app discovery via Data API requires separate quota/credential design.

## Primary references checked

- https://developers.google.com/youtube/terms/developer-policies — advertisements and player integrity restrictions.
- https://developers.google.com/youtube/terms/developer-policies-guide — no separated media, downloads or background play through the API.
- https://developers.google.com/youtube/iframe_api_reference — supported commands, playback rates and state events.
- https://developers.google.com/youtube/player_parameters — visible embedded-player parameters and minimum sizing.

This is a queued specification, not an implemented or released feature.

DNS filtering was checked: AdGuard documents that DNS filtering cannot reliably distinguish YouTube video ads from content on shared domains. Do not change device DNS or promise that a DNS provider removes in-video ads. Reference: https://adguard-dns.io/en/blog/dns-content-blocking-at-scale.html

## Link-reference groundwork — October 18, 2026

Candidate PR introduces separate metadata-only YouTube URL validation on both native platforms and Java regression assertions for accepted link forms, invalid IDs, duplicate video parameters, HTTP and host spoofing. It does not add a player, queue integration, persistence, Internet requests, media extraction or API credentials. iOS Swift validation now has explicit cases in the existing GeloKaraokeCoreTests target; the Android reference is included in the existing core source tree. The existing FIFO/local playback architecture remains unchanged. Compilation, current-head tests, UI acceptance, privacy review and production release are pending. Ads and standard YouTube controls must remain visible when a compliant official player is implemented later.

## Visible player shell candidate — October 18, 2026

This implementation candidate adds two platform-specific, visible YouTube embed surfaces using the official youtube-nocookie.com embed URL, with no ad interception, stream extraction, playback spoofing or autoplay. iOS uses a WKWebView SwiftUI representable that clears its content when dismantled; Android uses a WebView subclass with an explicit release method. Both retain their platform-native app/queue owners and use validated video identifiers. Navigation is limited to YouTube hosts, so login/external flows may be unavailable until separately reviewed. **Neither player is wired to the app navigation, authoritative FIFO queue, playback state, Favorites or companion commands.** No claim of working user-facing YouTube playback, completed lifecycle integration, YouTube policy acceptance or physical-device validation is made. Do not expose these surfaces in production before queue ownership, interruption, consent/privacy, error states and native lifecycle tests are complete. Ads may interrupt video playback for users without YouTube Premium.

## Player-shell privacy and lifecycle hardening — October 18, 2026

Candidate hardening: iOS uses an ephemeral WebKit website data store and navigates to a blank page when the view is dismantled; Android disables popup windows and file-origin access privileges and clears WebView history during release. Both shells still enforce their existing YouTube-host navigation restrictions. These changes do not wire either player to the app, authorize background playback, implement the official IFrame command/state API, or establish FIFO, consent, error-state or physical-device acceptance. Validate the resulting PR head before merging. Do not describe the player shells as user-facing production playback.


## Cohesive delivery batch — 2026-10-18

The owner selected feature-batch delivery instead of a PR per helper or lifecycle detail. PRs #48–#50 supplied metadata validation, visible player shells, and teardown foundations, but **do not constitute working user-facing playback**. The next implementation PR must deliver a cohesive, testable vertical slice on **both platforms** before requesting final CI:

1. **Explicit opt-in and link entry:** a user-facing entry surface accepts only validated YouTube video URLs; show the Internet/Google privacy notice before any player is constructed or remote content is loaded. Reject invalid links without changing local playback.
2. **Visible player and source handoff:** mount the official visible player in an unobscured area. Switching away, replacing a video, leaving the screen, or losing authorization must stop and dismantle the old player. Never keep an offscreen audio-only player.
3. **Authoritative mixed-source FIFO:** extend the existing host-owned reservation model with a discriminated local/YouTube source and unique reservation identity. Repeated reservations remain distinct. Play/Pause/Next and remote commands must go through the host owner, not a second web-player queue.
4. **Failure and lifecycle handling:** reject unavailable/blocked videos, ignore stale completion callbacks, handle background/foreground, player errors and rapid Next without skipping or duplicating reservations. Local playback must still work offline.
5. **Parity and evidence:** Swift and Java queue/source tests, Android build/tests, iOS package/build and focused UI smoke on the final head. Record manual acceptance gaps (real ad behavior, embed restrictions, physical-device audio focus) separately.

**Implementation order within the batch:** map the actual iOS LocalKaraokeController and Android SessionController state transitions; implement the source contract and identity tests; connect the host owner; add native entry and visible player; exercise lifecycle and error paths; update architecture and checkpoint; then run consolidated CI. Do not create a series of tiny merged PRs for intermediate helpers. Avoid starting a second unrelated feature batch while this one is open.

**Out of scope:** ad blocking, browser replacement, downloading, stream/audio extraction, hidden playback, automatic YouTube scoring, and unverified rate/pitch manipulation. Official player restrictions and advertisements remain intact.

**Release gate:** CI passing means only the named automated checks passed; embedded playback, account/region restrictions, privacy disclosures, app review, and real-device behavior require separate acceptance.


### Implementation checkpoint (unvalidated)

- iOS CONTROL now presents an opt-in online-video sheet with validated HTTPS link entry and visible player. Android exposes the equivalent opt-in preview from its control panel.
- Both preview surfaces are explicitly **not queue reservations** and cannot be claimed as FIFO playback or remote-controlled YouTube.
- The current preview implementations still require compilation, UI lifecycle tests, real embed verification, and architectural integration with each host's single authoritative queue before the complete feature batch can be accepted.
- Do not merge this draft as a finished YouTube playback feature merely because an isolated source or build check passes.

## Updated playback ownership policy — October 18, 2026

The owner supersedes the earlier proposal to send YouTube transport commands through the local playback controller. **Local and YouTube playback engines each own their Play/Pause/Stop and player-specific controls.** A lightweight session coordinator may arbitrate shared audio focus and visibility, but must not forward YouTube commands to the MIDI/KAR decoder or mutate local transport state as a side effect of opening a video. YouTube is an explicit opt-in foreground surface with native embedded-player controls; local playback continues to own its own queue and audio DSP. If mixed-source reservations are offered in the future, the coordinator selects a source without sharing decoder state, and no second implicit queue may silently consume local reservations. The existing queue metadata groundwork does not establish a working mixed-source coordinator.

The existing Android lifecycle cleanup and iOS sheet dismantling remain required. Test both independent player lifecycles, audio focus, visibility and policy compliance on real devices before release. Do not claim simultaneous playback is prevented until audio-focus arbitration is implemented and verified.

## Independent-player acceptance contract (October 18, 2026)

The updated owner decision supersedes earlier instructions in this document that implied YouTube Play/Pause/Next must be routed through the local host transport. **Do not implement that coupling.** The current implementation is an opt-in foreground preview, not an integrated mixed-source playback session.

| Event | Local engine | YouTube engine | Coordinator / host |
| --- | --- | --- | --- |
| Open online preview | Preserve queue, current item and transport state | Construct only after consent and URL validation | Request audio focus separately; opening alone must not invoke local pause |
| Close online preview | No transport command | Stop/destroy WebView or WKWebView | Release online audio ownership and invalidate callback generation |
| Local Play/Pause/Next | Handle with existing local controller | No command | Do not consume an online reservation implicitly |
| Online Play/Pause/Stop | No command | Official visible embedded controls only | Never dispatch commands through MIDI/KAR decoder |
| Background / activity stop | Follow existing local lifecycle policy | Tear down visible online player; no background playback | Ignore callbacks from disposed instances |
| Online load error or restricted video | Keep queue and current local song intact | Show recoverable error where supported | Release online focus; do not skip local FIFO |
| Rapid online A → B | Unchanged | Destroy A before B is mounted | Generation/token guards must ignore late callbacks from A |

### Required regression evidence before PR #51 exits draft

1. Android: open A, replace with B, dismiss B, stop activity, then attempt late callbacks from A; no resurrected WebView, no local queue mutation.
2. iOS: select A, background the app, return, select B; A is dismantled and cannot restart; local controls retain their previous state.
3. Both: invalid link, repeated link, close, reopen, online load error, and local Next while online preview is open must not change local reservation identities except when local Next is explicitly invoked.
4. Both: audio-focus arbitration must be explicitly implemented and tested before claiming that simultaneous audio is prevented. Audio focus is **not** a reason to send player transport commands across engines.
5. Validate current PR head in Android CI, iOS CI and iOS smoke, then record physical-device embed, interruption, privacy and ads checks separately. CI alone cannot verify these external behaviors.

**Current code-review observation:** Android `MainActivity.showYouTubeVideo` dismisses the previous dialog, releases the player on dismiss, and checks finishing/destroyed before opening. iOS `KantaYouTubePreviewSheet` clears its selected reference when the scene becomes inactive. Neither observation proves asynchronous stale-callback safety or audio-focus arbitration. Do not mark those requirements complete without implementation and tests.


## PR #51 foreground player lifetime repair — October 18, 2026

The working branch now implements terminal WebView retirement and transient preview identity fences on both platforms; replacement is retired before attachment, inactive callbacks cannot reopen players, and load/process errors support explicit recovery. Static/Python checks passed. Domain and Android native runtime regressions are wired but require changed-head hosted validation. iOS native lifecycle tests, audio-focus coordination and physical-device acceptance remain gates. See `YOUTUBE_LIFECYCLE_CHECKPOINT.md`. Earlier code-review observations above describe the pre-repair baseline.
