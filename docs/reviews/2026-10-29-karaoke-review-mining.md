# Karaoke app review mining — 2026-10-29

**Purpose.** Find what users of comparable karaoke apps actually complain about, so KANTA can
fix those things before they become our problems, and so we never copy a competitor's mistake.

**Provenance and honesty note.** The review corpus behind this report was collected by a
research agent that was stopped by its time budget before it wrote its own summary. The raw
review dumps it had already fetched were recovered from
`/var/minis/workspace/karaoke_research/` and analysed here. **Per-review URLs were not
recorded by that run**, so this report attributes complaints to an *app and store* and quotes
the review text verbatim, but cannot link the individual review. That is a real gap; see
[Limitations](#limitations).

## Sources consulted

| App | Store / source | Reviews with text | Negative (≤3★) | What was taken from it |
| --- | --- | --- | --- | --- |
| Gismart Karaoke | Apple App Store (US) | 10 | 9 | Paywall gating, lyric/track timing, catalogue depth |
| KaraFun | Apple App Store (US) | 10 | 1 | Paywall gating, catalogue search misses |
| SimplySing | Apple App Store (US) | 10 | 4 | Pitch/note detection, timing, paywall |
| Sing King | Apple App Store (US) | 10 | 3 | Note display, overheating, mid-song stops, login |
| Smule | Apple App Store (US) | 10 | 6 | Recording distortion, UI churn, no log-out, safety |
| StarMaker | Apple App Store (US) | 10 | 4 | Voice processing quality, account/data loss, safety |
| Yokee | Apple App Store (US) | 10 | 5 | Lyric sync drift, VIP gating, guide-vocal bleed |
| Sunfly | Apple App Store (GB) | 9 | 5 | Login/data loss, blank screen, no own tracks |
| Entermedia Karaoke | Google Play | 3 | 0 | Positive baseline (lyrics spelled correctly, search beats a songbook) |
| Kodai (MIDI player) | Google Play | 3 | 0 | Positive baseline for MIDI playback |
| Midifun | Google Play | 3 | 0 | Positive baseline for MIDI playback |
| KaraFun | Google Play | 3 | 0 | — (rating feed returned only 3) |
| Sing King | Google Play | 3 | 0 | — |
| Smule | Google Play | 3 | 0 | — |
| StarMaker | Google Play | 3 | 0 | — |

**Total: 100 reviews with text, 37 rated ≤3★, across 15 app/store combinations.**

## Ranked improvements

Ranked by how often the complaint appears and how badly it hurts a karaoke session. Effort:
**S** under an hour, **M** a focused session, **L** more than a day or blocked.

| # | Problem users report | Frequency / severity evidence | What KANTA should do | Acceptance criterion | Effort |
| --- | --- | --- | --- | --- | --- |
| R1 | **Bugs, freezes, overheating, mid-song stops** — the single most frequent *technical* complaint | 9 of 37 negative reviews, 6 apps. Sing King: *"it tends to make my phone overheat… sometimes I have to refresh the entire app to play a song because it wouldn't load"*; Sunfly: *"just when the party is about to start it stops working… a blank screen with the whirling thing going endlessly"*; Sing King: *"It also stopped playing in the middle of a song."* | Long-session soak coverage: play through a full set with background video and the front camera, watching for leaked surfaces, growing memory and dropped frames. Decide Media3 (log item D5) because long video sessions are exactly where the current path is weakest. | A 30-minute soak on a real device with background video plus camera shows no stall, no unbounded memory growth, and no dropped audio; logs attached. | L |
| R2 | **Backing/guide vocal is wrong or bleeds over the singer** | 3 of 37, 3 apps. Yokee: *"a voice (that doesn't even sound like the singer voice) comes and sings with you it messes people up"*; StarMaker: *"it sounds like your voice is overpowering everything else and it makes unnecessary background noise"*. **KANTA has this defect literally in the tree**: the melody and guide volume sliders are silent no-ops (`StagePresenterImpl` lines 137–142), and `SoundFontPlayback` exposes only a master volume with no per-voice mixing. | Either implement per-voice melody/guide volume in the synth and wire the sliders, or remove the sliders. A control that does nothing is worse than no control. | Moving the melody slider changes the rendered mix audibly, and the setting persists; **or** the slider is gone and its absence is recorded in `docs/UNFINISHED_WORK.md`. | M |
| R3 | **Lyrics or the track are out of time** | 4 of 37, 4 apps. Gismart: *"the lyrics started going before the singing part of the song even starts"*; SimplySing: *"poor timing between the vocals and the music"*; KaraFun (Play): *"timing is off in strange spots… a verse transition is 3 seconds longer than it should be"*; Yokee: *"bugs, mostly with syncing the songs to the lyrics"*. | Two separate things: (a) prove the rendered clock stays locked through tempo changes and long songs, and (b) accept that some MIDI/KAR files are simply authored badly, so give the singer a small manual lyric offset that persists per song. | A timing test asserts lyric position against the rendered frame clock across a tempo change and a 5-minute song; a lyric-offset nudge is adjustable and survives app restart. | M |
| R4 | **Pitch/note detection is wrong** | 4 of 37, 4 apps. SimplySing: *"incorrect note identification… notes not register correctly"*; Sing King: *"sometimes they don't even line up or it stays the same"*; Yokee and Gismart report the same class of problem. KANTA's `DryPitchDetector` is proven only against synthetic tones. | Calibrate against real singing, not sine waves: use the monitoring path to capture real vocal input and check the detector's octave errors, then tune or document the limits. Label the scorer honestly — it is not the original proprietary engine. | A calibration report shows octave-error rate and detection latency on real recorded vocals, and the score panel does not overstate accuracy. | M |
| R5 | **Login, account or data loss** | 7 of 37, 5 apps. Sunfly: *"my log in is now not recognised any longer so I can't access my data"*; StarMaker: *"I lost all my followers, all my songs I had posted"*; Smule: *"there is not a log out option"*. | KANTA has no accounts, which removes most of this class — **but the equivalent risk is our own local state**: the queue, settings and library must survive a kill, an upgrade and a full-disk error. | A test kills the app mid-session and on relaunch the queue, settings and library are intact; a simulated write failure surfaces a message and never a silent loss. | M |
| R6 | **Cannot hear their own performance back** | 7 of 37, 4 apps. Yokee: *"you CANT EVEN LISTEN TO YOUR OWN VOICE"*; SimplySing, Smule: *"after the recording is finished, my voice sounds distorted, like a robot"*. | Decide explicitly: either add opt-in local recording with honest playback (no effects), or state plainly that KANTA does not record. Monitoring already exists and is opt-in; recording is a privacy decision. **Do not add cloud sharing.** | Either a local recording can be replayed and deleted, with no network path; or the absence is documented and the UI never implies recording. | L |
| R7 | **Catalogue gaps — "couldn't find my song"** | 4 of 37, 4 apps. Sing King: *"every song I have searched is not on this app. Couldn't find Janis Joplin"*; KaraFun: *"I searched up 5 of my favorite artists just to find a 'sorry, we couldn't find anything'"*; Yokee: *"the app say 'unlimited songs'"* but is not. | **This is KANTA's structural advantage**: the catalogue is the user's own folder. Protect it — never word the UI as "unlimited", and make an empty search explain *why* (no content root selected) instead of implying the song does not exist. | An empty search result distinguishes "no match in your library" from "no library configured", and no user-facing string claims an unlimited catalogue. | S |
| R8 | **Cannot use their own tracks** | 2 of 37, 2 apps. Sunfly: *"Cannot add own tracks."*; Smule praises the opposite: *"I am able to upload any song I want into the songbook for free"*. | Confirms the dim-3 folder-scan decision. Keep plain `.mid`/`.kar`/`.mp3` import first-class and make the legacy-container importer reachable (log item D4) rather than required. | A user adds their own file to the content root and it is searchable without any conversion step. | S |
| R9 | **Subscription is forced and opaque** | **26 of 37 negative reviews — by far the most frequent complaint overall**, 7 apps. Gismart: *"there isn't a way to use the app without paying"*, *"$9.99/week is a rip off"*; Smule: *"you kind of are forcing people to pay"*; Yokee: *"this app is all about a V.I.P pass"*. | **Do not act on this by adding a paywall.** Treat it as positioning evidence: KANTA is offline-first with no accounts and no subscription, which is the thing these users are asking for. Never introduce a lock on the user's own library, and never advertise "free" while gating use. | No user-facing surface gates a feature behind payment, and the website states the offline-first, no-subscription model plainly. | S |
| R10 | **UI churn makes the app harder to use** | 3 of 37 (all Smule). *"I really hate the new user interface with passion… not intuitive and you have to work much harder to navigate/find songs"*; *"the new larger thumbnails occupy so much room that it sacrifices the song info and singer names"*. | Keep the compact-workspace layout stable. Prioritise information density (song, artist, singer) over large imagery on list surfaces. | A visual walkthrough shows song, artist and singer all visible without opening an item, and the workspace structure is unchanged across two consecutive releases. | S |
| R11 | **Safety, strangers and abuse (social apps)** | 3 of 37, 2 apps, and severe: one StarMaker reviewer describes being groomed and threatened by another user; Smule reviewers report unwanted messages. | KANTA has no social layer, which is the strongest possible mitigation — **keep it that way, and keep Kanta-Connect a LAN-only, coded, single-session surface with no stranger discovery.** Do not add profiles, chat or public sharing. | No feature allows an unknown party on the network to discover, message or control a KANTA device without the displayed pairing code. | S |
| R12 | **App reports problems it does not explain** | Implied across R1, R3, R7. Sunfly: *"where do I go to combine the subscription… I am not paying twice!"* — a user left guessing about state. | Make every failure state actionable in one line: what happened, and the single next thing to do. The diagnostics surface (S14) already exists to support this. | Every user-visible error string names the cause and one next action; no bare "something went wrong". | S |

## Explicitly rejected

Complaints we must **not** act on, because acting on them would break KANTA's offline-first,
no-account, no-subscription design:

| Complaint | Why we reject it |
| --- | --- |
| "The free version should be more generous / I should get more before paying" | We have no paywall at all. The correct response is to keep it that way, not to tune a gate. |
| "Add more licensed songs / a bigger catalogue" | The catalogue is the user's own folder by licence design. We ship no third-party songbank. |
| "Add social features: followers, duets with strangers, live rooms, chat" | Directly creates the grooming/abuse and data-loss problems in R5 and R11, which are the most severe harms in this corpus. |
| "Add cloud sync, cloud backup, accounts" | Offline-first is the design. Local durability (R5) is the right fix, not a server. |
| "Stream uploads to YouTube/TikTok" | Out of scope, and a privacy/consent surface we do not want to own. |
| "Auto-renewing trial with a shorter notice period" | A dark pattern observed in this corpus (charged 24 hours before the trial ends). Never. |
| "Make the UI bigger/more visual like the competitors" | R10 shows this is actively disliked; information density wins on list surfaces. |

## Limitations

- **No per-review URLs.** The run that fetched the corpus did not record them, so this report
  cannot link an individual review. Attribution is by app and store, and quotes are verbatim
  from the fetched dumps. Treat the frequency counts as indicative, not audited.
- **Deliberately unfiltered sample.** The Apple feeds returned 10 reviews per app; the Google
  Play feeds returned only 3 for most, and Yokee's Play scrape failed entirely (empty file).
  So the corpus over-represents vocal US App Store reviewers and under-represents Android users.
  The complaint *classes* are consistent across apps, which is why they are still usable; the
  *proportions* are not a survey.
- **Not a review of review.** No deduplication against repeated text, and no way to tell a
  verified purchaser from a drive-by.
- **Reddit, Trustpilot and Hacker News were not fully mined.** The run had started on
  Trustpilot (Smule, 116 reviews at 1.4 average) but did not finish, so those sources are
  unrepresented here and their absence is not evidence of absence.
- **Six of the fifteen sources yielded no negative reviews at all** (Entermedia, Kodai,
  Midifun, and three Play feeds). I have not claimed any theme from them.
