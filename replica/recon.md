# KANTA Karaoke — Recon map

**Target product:** KANTA Karaoke (the owner's KANTA Android app, `ph.kanta.reborn`, current app label `KANTA`)
**Recon subject:** SongHub Android — the owner's own build, plus its Smart Link companion
**Recon date:** 2026-10-28
**Method:** static analysis of the owner-supplied APKs (manifest, DEX class/method/string inventory, resource tables, layouts) + the owner's upstream public repos (README, release manifest) + the owner's bundled content pack.
**Status:** recon only. Nothing implemented, nothing built, nothing tested.

---

## 1. Scope

| Question | Answer | Basis |
| --- | --- | --- |
| Which app | SongHub Android (`com.songhub.android`) + SongHub Smart Link Remote (`com.songhub.smartlinkapp`) | owner-supplied APKs; `AndroidManifest.xml` |
| Which platform | Android only — phone, tablet, Android/Google TV | `uses-feature` leanback, `PlayerActivityATV`, README |
| Which slice | Full parity (owner decision) | owner answer |
| Who it is for | The KANTA product owner; shipped inside the existing KANTA Android app as **KANTA Karaoke** | owner answer |
| Versions analysed | SongHub Android **2.42.67** (versionCode 242), Remote **3.0** | APK manifests |
| Upstream current release | SongHub Android **4.16.67** (versionCode 416) | `jpdcs/SongHub-Android` `manifest.json` |

**Ownership / clean-room status.** The owner states SongHub is their own build. The upstream public repositories (`jpdcs/SongHub-Android`, `SongHub-Smart-Link`, `SongHub-Karaoke-PC`, `SongHub-Legacy`) contain **README + release manifest only — no application source**. The Android app source is not public, so the evidence base is the owner's own binary. Because this is the owner's own code, the port may reuse its behaviour and assets; the KANTA naming sweep in §10 still applies in full.

**Version drift (important).** The bundled APK is 2.42.67; the public manifest advertises 4.16.67 with a changelog mentioning MTV video cropping, playback sync and XLS export fixes. The recon below describes **2.42.67**. Features added between 2.42.67 and 4.16.67 are **not** in this map and must be re-recon'd against a newer APK before parity is claimed.

---

## 2. Sources

| source | what it gives | notes |
| --- | --- | --- |
| `SongHub.apk` (owner-supplied, 39.8 MB) | manifest, 614 app classes / 113k DEX strings, 157 layouts, 226 string resources, native libs | primary evidence |
| `SongHub Remote.apk` (owner-supplied, 67.8 MB) | companion app: 224 app classes, WebSocket client, Room cache | primary evidence |
| Content pack (owner-supplied, 2.0 GB) | `idx/shubidx`, `idx/shubblob`, 2× SoundFont, 10× BGV video, 3× background photo | primary evidence |
| https://github.com/jpdcs/SongHub-Android | feature list, folder contract, content manifest, update flow | public, owner-owned |
| https://github.com/jpdcs/SongHub-Smart-Link | Smart Link concept | public, owner-owned |
| https://github.com/jpdcs/SongHub-Karaoke-PC | PC sibling cross-check | public, owner-owned |
| https://play.google.com/store/apps/details?id=com.songhub.android | — | **not retrievable** (page returns no listing content) |

**Evidence limitations (recorded, not worked around).**
1. **No dynamic capture.** No Android SDK, no emulator and no working JVM exist in this environment (`java` segfaults), so the app was never run. No screen-by-screen screenshots exist. All screen knowledge is static.
2. **No decompilation.** DEX was read at the class/method/string level, not decompiled to source. Method names and literals are reliable; exact algorithms are not.
3. **No public screenshots.** The only public image is the README wordmark (`replica/screens/songhub_readme_banner.png`).

---

## 3. Screen inventory

Screens are numbered `Sxx`; every later artifact refers to these IDs. "Evidence" cites the class that implements the screen.

| ID | screen | route / how you get there | purpose | key components (evidence) | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | Splash / bootstrap | launcher (`MAIN`/`LAUNCHER`, also `LEANBACK_LAUNCHER`) | permissions, storage/folder discovery, content index warm-up | `SplashActivity`, `StorageScanner`, `SongLibrary`, `DirectoryPicker` | loading · no-permission · **"No 'SongHub' folder found."** · index load fail · ready |
| S02 | Menu / hub | from Splash | entry point to every area; clock, wifi state, disclaimer | `MenuActivity` (`scaleCard`, `updateTime`, `updateWifiStatus`, `showDisclaimerIfNeeded`), `item_setting_group`, `layout_audio_controls` | normal · disclaimer shown once (`dialog_disclaimer`) |
| S03 | Song list | from Menu | browse/filter/sort the songbook | `SongListActivity` (`FilterType`, `SortType`, `SortOrder`), `item_song`, `SearchFabView` | empty · populated · filtered · permission-denied |
| S04 | New songs | from Menu | recently added songs, filter/sort cycles | `NewSongsActivity` (`cycleFilterType`, `cycleSortOrder`, `cycleSortType`, `openByCode`, `openSong`) | empty · populated · filtered |
| S05 | Artist list | from Menu | browse songs by artist | `ArtistListActivity` (`groupArtists`, `updateList`), `item_artist` | empty · grouped list |
| S06 | Ranking / charts | from Menu | ranked song list | `RankingListActivity`, `RankingManager`, `item_ranking` | empty · populated |
| S07 | Karaoke player (handset) | reserve from S03/S04/S05, or from remote | sing: lyrics, countdown, score, background, controls | `PlayerActivity`, `LyricView`, `LyricLine`, `CountdownView`/`CountdownGrouper`, `ScoreScreenView`, `BgvView`, `InterludeView`, `SongIntroView`, `AdjustmentOverlayView`, `SpectrumView`, `WavyTimeBar`, `QueueBarView` | intro · countdown · playing · interlude · scoring · result · queue-full · mic-denied |
| S08 | Karaoke player (TV / ATV) | same, on leanback devices | D-pad driven player | `PlayerActivityATV` (landscape, `configChanges`), `PortraitKeypadView`, `TouchKeypadView` | same as S07, focus-driven |
| S09 | Media library | from Menu | browse user videos/audio, pick background or play standalone | `MediaPlayerActivity` (+`MediaListFragment`, `MediaAdapter`, `MediaItem$Type`), `fragment_media_list`, `item_media`, ExoPlayer | empty · populated · loading metadata · error |
| S10 | Video player | from S09 | full-screen user video | `VideoPlayerActivity` (portrait-locked), `BgvView` | playing · paused · error |
| S11 | Audio player | from S09 | user MP3/WAV playback with metadata | `AudioPlayerActivity`, `layout_audio_controls` | playing · paused · error |
| S12 | Settings | from Menu | grouped preferences | `SettingsActivity`, `item_setting`, `item_setting_group`, `dialog_color_picker`, `dialog_lyric_font_size`, `dialog_volume_slider`, `dialog_download_prebuilt*` | default · edited · **experimental rows gated by password** (`DiagnosticActivity.promptPasswordForBypassMewTwo`) |
| S13 | Help / about | from Menu | usage instructions and about text | `HelpActivity` | static |
| S14 | Diagnostic mode | exported activity (`MAIN`/`DEFAULT`, label "Diagnostic Mode") | engine/content self-test | `DiagnosticActivity` (`checkStatuses`, `formatBassVersion`, `runDatabaseCheck`, `playMidiFromAssets`, `playMp3FromAssets`) | pass/fail per subsystem |
| S15 | Remote (Smart Link) status | overlay inside S07 | shows link state, connected clients | `SmartLinkStatusView`, `RemoteBroadcastingService` (`getConnectedCount`, `getLocalIpAddress`) | disconnected · advertising · connected |
| S16 | Search / reserve overlays | modal over S07/S08 | type-ahead search, reserve song, control pad | `SongSearchOverlayView` (`applyFilter`, `startVoiceSearch`, `commitSelected`), `SongEntryOverlayView`, `SongReservedOverlayView`, `SongSelectionDialogView` | empty · results · selection · committed · idle mode |
| S17 | Control adjustment overlay | modal over S07 | live key / tempo / melody / volume changes | `AdjustmentOverlayView$Type`, `TouchKeypadView` (`onKeyChange`, `onMelodyChange`, `onTempoChange`), `PortraitKeypadView` | collapsed · expanded |

---

## 4. User flows

| ID | goal | path | edge cases (evidence) |
| --- | --- | --- | --- |
| F01 | First run: grant permissions, create the content folder | S01 → permission prompts → `DirectoryPicker` → "No 'SongHub' folder found." → create/scan | permission denied · no folder · no `.sf2` in `sound/` · no `shubblob`/megmid container |
| F02 | Find and reserve a song | S02 → S03 → `SongSearchOverlayView` filter → `SongReservedOverlayView` → reserve (`onReserve`, `onReserveFirst`) | empty library · filter yields nothing · queue already holds the song |
| F03 | Sing the song (core loop) | S07 → `SongIntroView` → `CountdownView` → `BassMidiEngine` + `SoundFontManager` → `LyricView` (line highlight) → `ScoreCalculator` → `ScoreScreenView` result | no soundfont selected · MIDI parse failure · mic permission denied · missing lyrics |
| F04 | Adjust while singing | S07 → S17 → key / melody / tempo / volume (`onKeyChange`, `onMelodyChange`, `onTempoChange`) → `RemoteProtocol.packClockAnchor` re-anchors clock | tempo change invalidates score (`TempoUtils`) |
| F05 | Change the background visual | S07 → S09 → pick BGV video / background photo / camera feed (`onChangeBgv`, `BgvSettings`) | missing file · decode failure · camera denied |
| F06 | Play personal media | S02 → S09 → S10 (video) or S11 (audio) → ExoPlayer | unsupported codec · metadata missing · audio focus loss |
| F07 | Control from a phone (Smart Link) | S07 starts `RemoteBroadcastingService` (NSD `_songhub._tcp` + UDP beacon) → remote app connects over WebSocket → `RemoteCommandListener` → commands mutate the host | host offline · client already connected · malformed frame · stale clock anchor |
| F08 | Download prebuilt content | S12 → `dialog_download_prebuilt` → `PrebuiltDownloader` → `manifest.json` from GitHub raw → writes into the content folder | no network · interrupted download · content licence warning (`prebuilt_option_songs_note`) |
| F09 | Update the app | S12 → `AppUpdateManager` → `manifest.json` `appUpdate` block → `REQUEST_INSTALL_PACKAGES` | unknown-app install blocked ("Please enable 'Install unknown apps' for SongHub…") · newer version available · already latest |
| F10 | Navigate with a TV remote / D-pad | S01 → S02 → S03 → S07, all focus-driven (`setupFocusAnimation`, `dispatchKeyEvent`, `handleKeypadInput`) | on-screen keyboard fights D-pad · focus lost after list refresh |
| F11 | Explore rankings / new songs | S02 → S06 / S04 | ranking empty · new-song list stale after library reload |
| F12 | Diagnose the install | S14 → subsystem checks (BASS version, database, MIDI/MP3 sample playback) | missing native lib · database mismatch · password-gated experimental rows |

**Happy-path click count (F03, handset):** launch → menu → song list → song → intro → countdown → sing → result ≈ 5 user actions before singing starts.

---

## 5. Component inventory

| component | evidence | variants / states | used by |
| --- | --- | --- | --- |
| Song row | `item_song` | default · focused · reserved · playing | S03, S04, S16 |
| Artist row | `item_artist` | default · focused | S05 |
| Ranking row | `item_ranking` | rank position · focused | S06 |
| Media row | `item_media` | video · audio · thumbnail states | S09 |
| Setting row / group | `item_setting`, `item_setting_group` | switch · slider · picker · action · experimental | S12 |
| Diagnostic row | `item_diagnostic_row`, `item_diagnostic_header`, `item_diagnostic_experimental` | pass · fail · header · gated | S14 |
| Lyric view | `LyricView`, `LyricLine`, `LineBuilder` (`ProcessedText`), `LyricsSyncManager` | idle · highlighted line · segment flip (2-line / 4-line) · font-size variants | S07, S08, remote mirror |
| Countdown view | `CountdownView`, `CountdownGroup`, `CountdownGrouper` | 3 / 4 / 5 digits (`raw/num_0..num_9`) | S07 |
| Score screen | `ScoreScreenView$Phase`, `raw/score_61_75…score_100` | stars · confetti · balloons · likes burst · shake · shuffle | S07 |
| Background video view | `BgvView` (`applyAnimation`) | video · photo slideshow · camera feed | S07, S09, S10 |
| Song intro | `SongIntroView` (`randomizeTitleStyle`) | title styles · visible/invisible | S07 |
| Interlude view | `InterludeView` | show/hide | S07 |
| Queue bar | `QueueBarView` (`setQueue`, `setTypingCode`, `recalculateCycles`) | landscape/portrait · badge · info area | S07, S08 |
| Search overlay | `SongSearchOverlayView` | idle · results · voice-search active · orientation-specific pills | S07, S16 |
| Reserved overlay | `SongReservedOverlayView` | idle · list · selection movement | S07 |
| Song selection dialog | `SongSelectionDialogView$Mode` | play-now / reserve / reserve-first / cancel | S16 |
| Adjustment overlay | `AdjustmentOverlayView$Type` | key · melody · tempo · volume | S17 |
| Keypads | `PortraitKeypadView`, `TouchKeypadView` | portrait (3×4) · touch (collapsible) · control keys | S08, S17 |
| Spectrum | `SpectrumView` (FFT + waveform capture) | live bars | S07 |
| Wavy time bar | `WavyTimeBar` (`onScrubStart/Move/Stop`) | idle · scrubbing | S07 |
| Music controls | `MusicControlsView`, `ControlBarView` | play/pause · next · prev · stop · record | S07 |
| Smart Link status | `SmartLinkStatusView` | off · advertising · connected | S15 |
| Dialogs | `custom_dialog`, `dialog_color_picker`, `dialog_volume_slider`, `dialog_lyric_font_size`, `dialog_disclaimer`, `dialog_download_prebuilt*` | — | S12 |
| Search fab | `SearchFabView` | idle · focused | S03 |

---

## 6. Inferred data model

```
Song
    code (public reservation code), title, artist, volume/file reference,
    duration, hasLyrics, hasVideo, source (megmid | mp3 | kar), isReserved
    evidence: SongLibrary$LibrarySong (code/title/isMp3), MegSong, QueueBarView.padCodeWithZeros
    confidence: high

Artist
    name, songCount, song list
    evidence: ArtistListActivity$Artist, groupArtists
    confidence: high

Volume / container
    volume id, index offset, blob offset
    evidence: SongLibrary.resolveLocalOffset / resolveVolume / findVolumes,
              MegvolReader, MegidxDatabase
    confidence: medium (format undocumented)

Index database
    idx/shubidx — flat binary index (1.8 MB), read by MegidxDatabase
    idx/shubblob — container, magic "MKMD" (607 MB), read by MegmidReader
    content: per-song MIDI + lyric data ("megmid" containers)
    evidence: literal '.shubidx', 'shubblob',
              'No shubblob/megmid container files found for ', MegidxDatabase, MegmidReader
    confidence: high (existence), low (binary layout)

Singer credit
    name per song
    evidence: MegSinger, remote 'getCurrentArtist'
    confidence: high

Reservation / queue entry
    song code, reservation order, state
    evidence: QueueManager, QueueBarView, remote 'getQueueList'
    confidence: high

Score result
    score value, phase, difficulty, mode
    evidence: ScoreCalculator.computeMicScore / generateRandomScore,
              ScoreSettings$Difficulty, ScoreSettings$ScoreMode, ScoreScreenView$Phase
    confidence: high

Settings (grouped, persisted)
    AudioSettings (+EqPreset), BehaviorSettings, BgvSettings, LyricSettings,
    MicSettings, ScoreSettings, RemoteSettings
    evidence: class names; prefs file 'songhub_settings'
    confidence: high

Remote session
    host ip, connected client count, clock anchor, tempo map, countdown,
    current line indices, queue list, search results
    evidence: RemoteBroadcastingService, RemoteProtocol.pack*,
              remote KaraokeViewModel getters
    confidence: high
```

**Content folder contract** (owner README + DEX literals):

```
<KANTA>/                 (currently "SongHub")
├── songs/    .mid .kar .mp3 song files
├── sound/    .sf2 soundfonts              (bundled: songhub.sf2 + "yamaha x korg x ketron x dream sam …".sf2)
├── videos/   .mp4 .avi .mkv               (media player)
├── mp3s/     .mp3 .wav .wma               (media player)
├── bgv/      background videos (+ optional subfolders)   (bundled: 3D/, Motion/, Nature/)
├── bgphoto/  .jpg .png backgrounds        (bundled: 1..3.jpg)
├── assets/   countdown_0.png … countdown_4.png
├── idx/      shubidx + shubblob           (bundled content index)
└── font/     custom .ttf
```

---

## 7. Native and third-party dependencies

| dependency | evidence | what it does | port difficulty |
| --- | --- | --- | --- |
| BASS audio engine (`libbass.so`, `libbass_fx.so`, `libbassmidi.so`) | `lib/<abi>/libbass*.so`, `NativeLib`, `BassMidiEngine`, `DiagnosticActivity.formatBassVersion` | MIDI/SF2 synthesis, EQ presets, effects | **high** — proprietary native lib; KANTA already uses TinySoundFont (C) instead → decide: keep BASS (licence!) or port the feature set onto TinySoundFont |
| `libMewtwo.so` | `lib/<abi>/libMewtwo.so`, `promptPasswordForBypassMewTwo`, `playMidiFromAssets` | proprietary karaoke/scoring engine, gated behind a password | **high** — undocumented binary; KANTA's own scoring engine is the substitute |
| ExoPlayer / Media3 | `exo_*` layouts, `AudioPlayerActivity` listener surface, `exo_download_*` strings | user video/audio playback | low — KANTA can add Media3 |
| CameraX | `androidx.camera.core.impl.MetadataHolderService`, `CAMERA` permission | live camera background | low — KANTA already has `CameraStage` |
| OkHttp WebSocket (remote app) / Java-WebSocket (host) | remote `KaraokeClient` (OkHttp), host `RemoteBroadcastingService` (`onOpen/onMessage`), `TooTallNate/Java-WebSocket` doc link in strings | Smart Link transport | medium — protocol is binary and undocumented; both ends must be re-implemented together |
| Room (remote app) | `AppDatabase`, `SongDao`, `MultiInstanceInvalidationService` | remote-side song cache | low |
| NSD + UDP beacon + Wifi/Multicast locks | `registerNsdService`, `sendUdpBeacon`, `'SongHub:MulticastLock'`, `'SongHub:WifiLock'`, `CHANGE_WIFI_MULTICAST_STATE` | host discovery for Smart Link | medium — KANTA's companion already uses a different transport; see §9 |
| OkHttp/`SSLUtils` | `SSLUtils` class, `usesCleartextTraffic="true"` | TLS handling | low |
| Voice search | `VoiceSearchHelper` (`SpeechRecognizer`), `queries` for `android.speech.SpeechRecognizer` | hands-free search | low |
| Material Components / AndroidX | `res` tables | UI | low |

---

## 8. Feature matrix

Machine-readable matrix: `replica/features.csv` (48 rows: `feature, area, priority, original, clone, notes`).
`clone` is `no` for every row — it is filled in during the build, then scored by `/replica-diff`.

Priorities follow the owner's "full parity" decision, but the **must** set is the core karaoke loop only:

- **must** — reserve → play MIDI/SF2 with lyrics → countdown → score → queue → background visual.
- **should** — search/filter/sort, artists, rankings, new songs, settings groups, media player, remote control, TV/D-pad navigation, prebuilt content download, app update.
- **could** — voice search, spectrum analyser, confetti/balloon score celebration, diagnostic mode, custom fonts, background photos, app self-update.

---

## 9. What cannot be cloned as-is (skip rows)

| item | reason | handling |
| --- | --- | --- |
| `yamaha x korg x ketron x dream sam (custom made by jc).sf2` | third-party instrument soundbanks (Yamaha / Korg / Ketron / Dream), custom-made by "jc" — not owner's IP to ship | owner confirmed reuse; **verify the licence before any public release**; KANTA ships the owner's own `.sf2` by default |
| `idx/shubblob` (607 MB) + `idx/shubidx` (1.8 MB) | proprietary "Meg" karaoke container + binary index format, built from licensed songbooks | owner confirmed reuse; format is undocumented → **must be reverse-engineered or replaced**; content licence note already in the app ("Bilang saklaw pa rin ng Copyright…") |
| Music catalogue inside the index | publisher-licensed song content | never shipped publicly; owner-confirmed local use only |
| BGV videos `sayaw1/2.mp4`, `motion1-4.mp4`, `Video1-4.mp4` | third-party clip footage | owner confirmed reuse; verify licence before public release |
| "MTV" / YouTube video feature (in 4.16.67 changelog) | streaming-service content, not ownable | out of scope for KANTA; KANTA keeps its own official YouTube integration boundary |
| SongHub PC version (`SongHub-Karaoke-PC`) | separate product | out of scope |
| `manifest.json` hosting on `raw.githubusercontent.com/jpdcs/SongHub-Android` | owner's infra; KANTA must not depend on it | KANTA gets its own manifest URL (or none) |
| Smart Link WebSocket/binary protocol | undocumented binary format, must match host+remote exactly | reimplement both ends inside KANTA, or keep KANTA's existing encrypted companion protocol (per `docs/ARCHITECTURE.md`) and drop SongHub's transport |

---

## 10. Rebrand map — every "SongHub" occurrence to "KANTA"

Counted from the DEX string tables of both APKs. **43 distinct user-visible/identifier literals** plus the class tree.

| occurrence | location | kind | KANTA replacement |
| --- | --- | --- | --- |
| `com.songhub.android.*` (614 classes) | DEX class tree, `AndroidManifest.xml` | package | `ph.kanta.reborn.*` (existing KANTA package) |
| `com.songhub.android.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | manifest | permission | `ph.kanta.reborn.…` |
| `com.songhub.android.fileprovider` | manifest FileProvider authority | authority | `ph.kanta.reborn.fileprovider` |
| `com.songhub.android.androidx-startup` | manifest provider authority | authority | `ph.kanta.reborn.androidx-startup` |
| `com.songhub.smartlinkapp.*` (224 classes) | Remote DEX | package | `ph.kanta.reborn.companion` |
| `android:label="SongHub"` | manifest `<application>` | UI string | `KANTA Karaoke` |
| `android:label="Diagnostic Mode"` | manifest | UI string | keep |
| `'SongHub'` (content folder name) | DEX literal, README | path | `KANTA` |
| `'No SongHub folder found'`, `'No 'SongHub' folder found.'`, `'SongHub folder not found'`, `'Missing SongHub path'`, `'Directory not found. Please check if the SongHub folder'` | DEX literals | error text | `KANTA` |
| `'Scanning storage for SongHub/idx...'` | DEX literal | log/status | `Scanning storage for KANTA/idx...` |
| `'Tap to scan SongHub/sound/ for new .sf2 files'` | DEX literal | UI hint | `KANTA/sound/` |
| `'No .sf2 files found in any SongHub/sound/ folder'` | DEX literal | error | `KANTA/sound/` |
| `'No soundfont found in SongHub/sound/ or selected in Settings.'` | DEX literal | error | `KANTA/sound/` |
| `'Custom (.ttf) from SongHub/font/'` | DEX literal | UI hint | `KANTA/font/` |
| `'shubidx'`, `'.shubidx'` | DEX literals, file name | file name | `kantidx` / `kanta.idx` |
| `'shubblob'` | DEX literal, file name | file name | `kantablob` |
| `'No shubblob/megmid container files found for '` | DEX literal | error | `kantablob` |
| `'About SongHub'` | DEX literal | UI string | `About KANTA Karaoke` |
| `'SongHub Karaoke'` | DEX literal | UI string | `KANTA Karaoke` |
| `'SongHub Player ('` | DEX literal | UI string | `KANTA Player (` |
| `'SongHub - Lyric Demo'` | DEX literal | UI string | `KANTA Karaoke - Lyric Demo` |
| `'SongHub Android 2.1.67'` | DEX literal | version banner | `KANTA Karaoke <version>` (from BuildConfig) |
| `'Downloading SongHub v'`, `'You are using the latest version of SongHub (v'` | DEX literals | update UI | `KANTA Karaoke` |
| `"Please enable 'Install unknown apps' for SongHub to complete update."` | DEX literal | error | `KANTA Karaoke` |
| `'SONGHUB_SERVER:'` | DEX literal | log tag | `KANTA_SERVER:` |
| `'SongHub:MulticastLock'`, `'SongHub:WifiLock'` | DEX literals | lock names | `KANTA:MulticastLock`, `KANTA:WifiLock` |
| `'_songhub._tcp'` | DEX literal (NSD service type) | discovery | `_kanta._tcp` |
| `'songhub_settings'` | DEX literal (prefs file) | storage | `kanta_settings` |
| `'songhub_prefs'` | Remote DEX literal | storage | `kanta_prefs` |
| `'songs(com.songhub.smartlinkapp.Song).'` | Remote DEX literal (Room schema) | DB identity | `songs(ph.kanta.reborn.companion.Song).` → requires Room identity hash + migration |
| `'Smart Link'` | Remote DEX literal | feature name | keep or rename to `KANTA Link` |
| `'Songhub Remote Diagnostic'` | Remote DEX literal | UI string | `KANTA Remote Diagnostic` |
| `https://raw.githubusercontent.com/jpdcs/SongHub-Android/refs/heads/main/manifest.json` | DEX literal | endpoint | KANTA's own manifest URL (or removed) |
| `songhub.sf2` | bundled asset name | file | `kanta.sf2` |
| `SongHub/` content folder in the bundle | bundled content pack | path | `KANTA/` |

Also: the bundled `SongHub/` folder name, the release asset names on GitHub, and the upstream repo/README branding are outside the APK and need a separate rename if the public distribution channel is reused.

---

## 11. Size estimate

| dimension | count |
| --- | --- |
| screens | 17 (incl. 4 modal overlays) |
| flows | 12 |
| entities | ~8 (Song, Artist, Volume, Index DB, Reservation, Score, Settings, Remote session) |
| native/third-party surfaces | 9 (BASS ×3, Mewtwo, ExoPlayer, CameraX, OkHttp/WS, Room, NSD/UDP) |

**Hardest parts (ranked):**
1. `idx/shubblob` + `idx/shubidx` "Meg" content format — undocumented, 607 MB of real content; without a spec, this is the critical path.
2. MIDI/SF2 playback parity — BASS + proprietary `libMewtwo` vs KANTA's TinySoundFont; the scoring engine (`Mewtwo`) has no open equivalent in KANTA yet.
3. Smart Link binary protocol — host + remote must be rewritten as a matched pair.
4. TV/D-pad focus behaviour across 17 screens (`setupFocusAnimation`, `dispatchKeyEvent`) — high defect surface, exactly the class of bug KANTA already hit on iPad.
5. 2.0 GB content bundle — installation, download and licensing.

**Size judgement: L (a quarter)** for Android-only full parity, and **XL** if the content-format reverse engineering is included. Recommendation: ship the core karaoke loop first as a vertical slice (reserve → play → lyrics → score → queue → background), then the remote, then media/TV extras.

---

## 12. Evidence index

| artefact | path (this session) |
| --- | --- |
| manifest (main) | `out_songhub/manifest.xml` |
| manifest (remote) | `out_remote/manifest.xml` |
| app class/method inventory | `out_songhub/dex_classes_songhub.txt` (614 classes) |
| DEX string table | `out_songhub/dex_strings.txt` (113k strings) |
| string resources | `out_songhub/strings.tsv` (12 318 values / 226 app strings) |
| resource names | `out_songhub/resource_names.txt` (157 layouts, 430 drawables, 723 ids, 15 raw) |
| naming sweep | `out_songhub/naming_hits.txt`, `out_remote/naming_hits.txt` |
| upstream docs | `src_songhub/README.md`, `src_songhub/manifest.json` |
| content pack | `media/SongHub/**` (sf2 ×2, mp4 ×10, jpg ×3, idx ×2) |
| logo reference | `screens/songhub_readme_banner.png` (kept in the session workspace; **not committed** — third-party artwork stays out of the repository) |

All paths are under `/var/minis/workspace/songhub/` (scratch workspace, not part of the repository).

---

## 13. Next step

`/replica-architect` — turn the §6 model into a KANTA-side architecture: package layout under `ph.kanta.reborn`, the Meg index reader, the playback owner boundary (KANTA already has exactly one playback controller + FIFO reservation queue — SongHub's `QueueManager` must be projected onto it, never duplicated), the content-root policy (`KANTA/` folder), and the companion-transport decision (SongHub Smart Link vs KANTA's existing encrypted companion).

**Do not** start `/replica-build` until the architecture names the single playback/queue owner and the rebrand map (§10) has been applied to the planned package layout.
