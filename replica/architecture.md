# Architecture: KANTA Karaoke (Android)

A rebuild of **SongHub Android**'s core features inside the existing **KANTA** Android app (`ph.kanta.reborn`), renamed **KANTA Karaoke**.

Inputs: `replica/recon.md`, `replica/features.csv` (SongHub 2.42.67).
Status: **plan only** — nothing implemented, nothing compiled, nothing tested.

---

## 0. Scope and decisions for this plan

| decision | value | source |
| --- | --- | --- |
| Target platform | Android only (phone + tablet + Android TV), inside `apps/android` | owner |
| App name | **KANTA Karaoke** (application label; package stays `ph.kanta.reborn`) | owner |
| Parity baseline | SongHub **2.42.67** (the supplied APK); upstream 4.16.67 re-recon deferred | owner ("start beta 1") |
| Delivery shape | **Beta 1** first: core karaoke loop end to end; later betas add the rest | owner |
| Companions | **Both**: KANTA's existing encrypted browser companion **and** a port of SongHub's Smart Link phone remote | owner |
| Content pack | **Owner request recorded, repository rule still binding:** `docs/ARCHITECTURE.md` states *"No proprietary SongHub APK libraries or bundled media may be copied into KANTA."* Beta 1 therefore ships **no** legacy media; the owner may re-authorise a specific, licence-cleared set in writing, which is then recorded in `apps/android/THIRD_PARTY_NOTICES.md` | owner request vs. repo rule — needs one written decision |
| Legacy container | Read-only **metadata interoperability** with the legacy `shubidx`/`shubblob` containers, mirroring KANTA's existing iOS `LegacyCompatibility` layer; no payload decoding, no proprietary engine | `docs/ARCHITECTURE.md`, `replica/meg-format.md` |
| Audio/scoring engine | KANTA's own: TinySoundFont (Android) / FluidSynth (iOS), `MelodyScoreSession` + `DryPitchDetector` for dry estimates. **No BASS/BASSMIDI/BASS_FX, no proprietary scoring binary** | `docs/ARCHITECTURE.md` (engine decision) |
| Companions | **One** command vocabulary (`core/CompanionProtocol`) and one pairing/replay/revocation model; the phone remote speaks the same protocol instead of introducing a second transport family | `AGENTS.md` §4, `docs/ANDROID_COMPANION_CHECKPOINT.md` |
| Rebrand | Every `songhub` occurrence removed from code, resources, storage, discovery and network, enforced by `tools/check-product-branding.py` in CI | recon §10 |

**Beta 1 in one line:** open `KANTA/`, read the songbook index, search, reserve, sing with lyrics + countdown + score + background visual, with the queue owned by KANTA's existing `ReservationQueue`.

**Beta 1 explicitly out:** media player (S09–S11), rankings/new-songs/artists (S04–S06), prebuilt downloader (F08), app self-update (F09), diagnostics (S14), TV/ATV polish (S08 partial), Smart Link phone remote (companion module comes later), spectrum/confetti/voice-search polish.

---

## 1. Stack

The skill's default stack (Next.js + Postgres + Supabase) does **not** apply: the target is an existing offline-first native Android app with no server, no accounts and no payments. Boring, already-proven local choices only.

| layer | choice | why |
| --- | --- | --- |
| app shell | Java 17 + Android Views, single Activity + custom views (`ph.kanta.reborn`) | already shipped, matches the existing codebase and its CI; no rewrite |
| build | Gradle + AGP 8.13.2, compileSdk 36, minSdk 26, NDK 27.2, CMake 3.22 | already configured; KANTA's minSdk 26 is stricter than SongHub's 23 — acceptable |
| playback | TinySoundFont (existing `app/src/main/cpp/soundfont.c`) + `AudioTrack` | KANTA already owns SF2 synthesis; SongHub's BASS/`libMewtwo` are proprietary natives we must not ship |
| lyrics/timing | `core/MidiLyrics` SMF parser + `RenderedPlaybackClock` | already implements bounded SMF parsing and audible-clock timing; SongHub's parser is replaced by it |
| content index | **SQLite** (`android.database.sqlite`, no ORM) for the songbook index cache | the Meg index is 600 MB of binary data; SQLite gives random-access lookups, FTS4 search and crash-safe writes with zero new dependencies |
| app state | `AtomicFile` JSON (`library-v1.json`) for library metadata, queue snapshot, saved sets | already the repo's persistence contract; atomic commit, bounded size |
| settings | `SharedPreferences` file `kanta_settings`, grouped keys | SongHub used `songhub_settings`; same mechanism, renamed |
| video | Media3/ExoPlayer (**new dependency**) | needed for BGV backgrounds and the media player; SongHub already used ExoPlayer |
| camera | CameraX 1.5.3 (already a dependency) | live camera background; `CameraStage` exists |
| companion (browser) | existing `core/CompanionProtocol` + `core/BrowserRemoteServer` + `PinnedBrowserTransport` | encrypted, paired, already covered by repo contracts |
| companion (phone) | new `remote/smartlink/` — NSD `_kanta._tcp` + UDP beacon + WebSocket server, **ported binary protocol** | owner wants both; must be paired and authorized like the browser companion |
| auth | none (single-user on-device app) | SongHub had none; KANTA's companion pairing is the only trust boundary |
| payments / email / jobs / hosting | none | out of scope for an offline karaoke app; no backend is introduced |
| CI | existing `reconstruction-validation.yml` Android build/test job + a **new naming-sweep check** | the rebrand must be enforced, not remembered |

**No new service, no network dependency at runtime.** Content download is optional and points at an owner-controlled manifest (Beta 2).

---

## 2. Package layout

New code lands under `ph.kanta.reborn`; **existing owners are not replaced**.

```
ph.kanta.reborn
├── SessionController.java          # UNCHANGED owner: playback + queue orchestration
├── LibraryRepository.java          # EXTENDED: content-root aware, Meg index wiring
├── core/
│   ├── ReservationQueue.java       # UNCHANGED owner: FIFO reservations (UUID identity)
│   ├── Song.java                   # EXTENDED: meg-backed song descriptor
│   ├── songbook/                   # NEW — framework-free, unit-testable
│   │   ├── MegIndexReader.java     # reads idx/kantidx (was shubidx)
│   │   ├── MegBlobReader.java      # random-access reads from idx/kantablob (MKMD container)
│   │   ├── MegVolumeTable.java     # volume → offset mapping
│   │   └── MegFormatException.java
│   ├── lyrics/ (existing MidiLyrics) # reused, extended for line-flip segments
│   └── ...
├── content/                        # NEW
│   ├── ContentRoot.java            # KANTA/ folder resolution (legacy SongHub/ read-only fallback)
│   ├── ContentScanner.java         # songs/, sound/, bgv/, bgphoto/, idx/, font/, assets/
│   └── PrebuiltDownloader.java     # Beta 2
├── settings/                       # NEW — grouped settings (audio, lyric, mic, score, behavior, bgv)
├── stage/                          # NEW — SongHub stage overlays projected onto KANTA state
│   ├── LyricStage.java  CountdownStage.java  ScoreOverlay.java
│   ├── BackgroundStage.java  AdjustmentOverlay.java  IntroCard.java
├── remote/
│   ├── smartlink/                  # NEW — port of SongHub Smart Link (Beta 3)
│   │   ├── SmartLinkHost.java      # NSD + UDP beacon + WebSocket server
│   │   ├── SmartLinkProtocol.java  # port of RemoteProtocol.pack*/unpack*
│   │   └── SmartLinkPairing.java   # explicit pairing + authorization + revocation
│   └── (existing CompanionControl / core.BrowserRemoteServer stay)
├── media/                          # NEW — Beta 2 media library (Media3)
└── tv/                             # NEW — Beta 2 D-pad/ATV focus handling
```

**Hard rule (repo invariant):** SongHub's `QueueManager` is **not** ported. SongHub's queue maps onto KANTA's `ReservationQueue`; every mutation is an intent to `SessionController`. No second queue, no second playback clock.

**Second hard rule:** the playback/queue/scoring owner invariant from `AGENTS.md` §4 is unchanged — one authoritative owner per state, distinct reservation identities, at-most-once completion credit per playback identity.

---

## 3. Schema

Two stores, deliberately separated:

1. **SQLite** — the songbook *index* (large, queryable, replaceable from files).
2. **`AtomicFile` JSON (`library-v1.json`)** — KANTA's own app state (library metadata, queue snapshot, saved sets, selected soundfont). Unchanged format; new fields are additive and version-gated.

### 3.1 Songbook index (SQLite)

```sql
-- Content index for the Meg songbook (idx/kantidx + idx/kantablob).
-- Rebuilt from files; never the source of truth for playback state.

create table if not exists volumes (
  id            integer primary key autoincrement,
  root_path     text not null unique,      -- absolute path of the volume that holds the content
  index_path    text not null,             -- <root>/idx/kantidx
  blob_path     text not null,             -- <root>/idx/kantablob
  index_bytes   integer not null,
  blob_bytes    integer not null,
  song_count    integer not null default 0,
  scanned_at    integer not null           -- epoch millis, UTC
);

create table if not exists songs (
  id            text primary key,          -- stable id: "<volume_id>:<song_ordinal>"
  volume_id     integer not null references volumes(id) on delete cascade,
  ordinal       integer not null,          -- position inside the Meg index
  code          text not null default '',  -- public reservation code (SongHub shows it on screen)
  title         text not null,
  artist        text not null default '',
  singer        text not null default '',
  duration_ms   integer not null default 0,
  song_offset   integer not null default 0,-- byte offset of the song payload inside the blob
  song_bytes    integer not null default 0,
  lyric_offset  integer not null default 0,
  lyric_bytes   integer not null default 0,
  has_lyrics    integer not null default 0,
  has_video     integer not null default 0,
  source        text not null default 'megmid'
                  check (source in ('megmid','midi','mp3','kar')),
  updated_at    integer not null,
  unique (volume_id, ordinal)
);
create index if not exists songs_volume_ordinal on songs (volume_id, ordinal);
create index if not exists songs_artist on songs (artist);
create index if not exists songs_code on songs (code);

create table if not exists artists (
  name        text primary key,
  song_count  integer not null default 0
);

-- Full-text search over the catalogue (Android ships SQLite with FTS4).
create virtual table if not exists song_search using fts4(
  title, artist, code, content='songs', content_rowid='rowid'
);
-- Rebuild after an import: insert into song_search(song_search) values('rebuild');

create table if not exists index_meta (
  key    text primary key,
  value  text not null
);
-- keys: format_version, content_root, schema_version, last_successful_scan
```

**Tables: 5 (+1 FTS shadow). Access rules: on-device single user — no RLS.** Rules that replace RLS:

| rule | detail |
| --- | --- |
| single writer | all index writes happen on the existing **serial IO executor**; the UI never touches SQLite directly |
| snapshot reads | search and list queries run on a background executor and return **immutable snapshots**; stale results are fenced by a scan generation counter (repo rule: stale async results must not replace newer state) |
| ownership | the index DB is app-private (`context.getDatabasePath`); no content provider, no exported access |
| size guard | `songs` row count bounded (150 000, matching `LibraryRepository`'s existing bound); index file size bounded before parse (1.8 MB today) |
| crash safety | SQLite WAL; index rebuild is transactional — a failed scan leaves the previous index intact |
| privacy | song metadata from the owner's own files only; **never** exported to the public website or committed (repo rule: no public song catalogue) |

### 3.1b Legacy indexed-container interoperability (read-only)

KANTA already ships an iOS compatibility layer for the legacy indexed container
(`apps/ios/Sources/Infrastructure/LegacyCompatibility/`). The Android port mirrors it one-for-one in
`ph.kanta.reborn.core.songbook`:

| class | role |
| --- | --- |
| `ShubIndexProbe` | header words, metadata records, title records, trailing offset table |
| `ShubBlobOffsetCorrelator` | conservative verdict for the offset table against a known container size |
| `ShubBlobSpanIndex` | payload spans from a plausible offset table; invalid spans raise instead of guessing |
| `ShubBlobPayloadProbe` | bounded prefix classification of a payload |
| `IndexedContainerCompatibility` | neutral facade used by library and UI owners |

Rules:

* Framework-free Java so the repository's existing `coreRegression` task exercises it on a server JVM.
* Synthetic fixtures only — **no song catalogue is committed** (`AGENTS.md` §6).
* Read-only: it never decodes or executes a protected payload, and it never becomes a second source of truth for playback.
* The interpretation lives in one place per platform; the facade keeps format details out of the playback and library owners.

### 3.2 App state (JSON, unchanged contract)

`library-v1.json` keeps `version`, `songs`, `removed`, `savedSets`, `queue`, `soundfont`, `soundfontName`. Additions for KANTA Karaoke are **additive and optional**:

```json
{
  "version": 1,
  "contentRoot": "/storage/emulated/0/KANTA",
  "contentRoots": ["/storage/emulated/0/KANTA", "/storage/emulated/0/SongHub"],
  "legacyRootNoticeShown": false,
  "backgroundMode": "bgv",
  "scoreMode": "mic",
  "remote": { "smartLinkEnabled": false, "pairedClients": [] }
}
```

Reading a legacy `SongHub/` folder is allowed for **one** release with a visible notice ("Legacy folder detected — rename to KANTA"); writes always go to `KANTA/`.

---

## 4. API / routes

This is a native app: "routes" are screens and internal contracts. The table maps every screen/flow from recon §3–§4 onto the KANTA owner that serves it.

| route (screen) | what it does | who calls it | input | output | flow |
| --- | --- | --- | --- | --- | --- |
| `S01 Splash` | resolve content root, scan index, warm soundfont list | launcher | storage permission, root path | ready / error state | F01 |
| `S02 Menu` | hub: clock, wifi, disclaimer, entry points | user | — | menu state | F01 |
| `S03 Song list` | paginated, filtered, sorted song list | user + companion `search` | query, filter, sort | `List<Song>` snapshot | F02 |
| `S04 New songs` | recently added songs | user | — | list snapshot | F11 (Beta 2) |
| `S05 Artist list` | songs grouped by artist | user | — | grouped list | F11 (Beta 2) |
| `S06 Ranking` | ranked songs | user | — | ranked list | F11 (Beta 2) |
| `S07 Player` | sing: intro → countdown → playback → score | user + companion | reservation id | stage state | F03, F04, F05 |
| `S08 Player (ATV)` | same, D-pad driven | user | reservation id | stage state | F10 (Beta 2) |
| `S09–S11 Media` | user video/audio library and playback | user | file uri | player state | F06 (Beta 2) |
| `S12 Settings` | grouped preferences | user | setting key/value | persisted state | F01… |
| `S13 Help` / `S14 Diagnostic` | docs / self-test | user | — | report | F12 (Beta 3) |
| `S15 Smart Link status` | link + paired client state | system | — | status | F07 |
| `S16 Search / reserve overlays` | type-ahead search, reserve, play-now | user + companion | query, code | selection | F02 |
| `S17 Adjustment overlay` | live key / melody / tempo / volume | user + companion | delta | applied intent | F04 |

### 4.1 Internal contracts (Java interfaces, the real "API")

| contract | direction | operations | owner |
| --- | --- | --- | --- |
| `SongCatalog` | UI/companion → index | `search(query, filter, sort, limit)`, `byCode(code)`, `byArtist(name)`, `newSongs()`, `rankings()` | new `core/songbook` + `LibraryRepository` |
| `ContentRoot` | UI → storage | `resolve()`, `songsDir()`, `soundDir()`, `bgvDir()`, `bgphotoDir()`, `idxDir()`, `fontDir()`, `assetsDir()` | new `content/` |
| `StagePresenter` (extends existing session projection) | UI → owner | `snapshot()`, `reserve(code)`, `playPause()`, `next()`, `previous()`, `stop()`, `adjustKey(n)`, `adjustTempo(n)`, `adjustMelody(n)`, `setVolume(v)`, `setBackground(mode)` | **existing `SessionController`** — no new owner |
| `ScoreSink` | mic → owner | `onPitch(tick, cents, confidence)`, `finalize(playbackId)` → at-most-once score | existing `core/MelodyScoreSession` / `DryPitchDetector` |
| `SettingsStore` | UI → prefs | `audio()`, `lyric()`, `mic()`, `score()`, `behavior()`, `bgv()` | new `settings/` (SharedPreferences `kanta_settings`) |

### 4.2 Companion protocol A — KANTA browser companion (existing, unchanged)

Transport: existing pinned browser transport + `core/CompanionProtocol` (UUID command id, action + argument + singer, encrypted session).
Allowed actions today: `snapshot`, `search`, `reserve`, `cancel`, `play`, `pause`, `next`.
KANTA Karaoke adds (Beta 1): `volume`, `key`, `melody`, `tempo`, `background`, `record`, `lyrics` — each added to the same allow-list, each rejected by default until implemented.

### 4.3 Companion protocol B — Smart Link phone remote (new, ported)

Transport: NSD service `_kanta._tcp` + UDP beacon (host IP discovery) + WebSocket server; client uses OkHttp WebSocket (the phone app becomes `ph.kanta.reborn.companion`).

Frame kinds ported from SongHub `RemoteProtocol`:

| frame | SongHub method | meaning | KANTA source of truth |
| --- | --- | --- | --- |
| clock anchor | `packClockAnchor` | bpm, tick, time anchor, frozen flag | `RenderedPlaybackClock` |
| countdown | `packCountdown` | remaining countdown text | stage state |
| database | `packDatabase` | song catalogue page | `SongCatalog` snapshot |
| line flip 2 / 4 | `packLineFlip2Line`, `packLineFlip4Line` | lyric line/segment changes | `MidiLyrics` events |
| score | (remote view-model getter) | current score | `ScoreSink` |
| queue | (remote view-model getter) | queue list | `ReservationQueue.snapshot()` |
| tempo map | (remote view-model getter) | tempo segments | playback state |

Client→host actions (SongHub remote `sendAction`): `search`, `reserve`, `play_now`, `reserve_first`, `play`, `pause`, `next`, `previous`, `stop`, `volume`, `key`, `melody`, `tempo`, `record`, `bgv`.

**Security requirement (not in SongHub):** NSD discovery is unauthenticated. KANTA's Smart Link host must require an **explicit pairing step** (short code shown on the host, entered on the phone), issue a per-session token, reject unpaired frames, enforce replay protection (bounded, connection-scoped like the existing iOS LAN cache) and support revocation — mirroring the rules `AGENTS.md` §4 already states for companion commands.

### 4.4 Optional content endpoint (Beta 2)

`GET https://<owner-host>/kanta-content/manifest.json` — the same shape SongHub used (`files[]` with `folder`, `fileName`, `url`; `appUpdate{}` block). KANTA must **not** point at `raw.githubusercontent.com/jpdcs/SongHub-Android`; the owner hosts the manifest under a KANTA-branded path. KANTA's app self-update stays owned by the GitHub release pipeline — the `appUpdate` block is **not** implemented.

---

## 5. The parts that bite

- **Undocumented binary formats (critical path).** `kantidx` (1.8 MB) and `kantablob` (607 MB, magic `MKMD`) are SongHub's proprietary "Meg" formats. Nothing in the public repos describes them. The plan assumes a **read-only parser** written against observed bytes, developed as a framework-free Java class so it can be exercised by the repo's JVM `coreRegression` task. Until the parser is validated against real files, the songbook feature cannot be claimed.
- **600 MB random access on a phone.** Never read the blob whole. Use `RandomAccessFile` seeks with a small LRU of open handles per volume; bound concurrent reads; do the parsing on the serial IO executor, never on the main thread.
- **Index rebuild cost.** A full scan of the index must not block launch: scan in the background, publish an immutable snapshot when done, and fence stale results by a generation counter.
- **Audio thread priority.** TinySoundFont rendering plus `AudioTrack` must run on a dedicated high-priority thread with a bounded buffer; a UI jank must never drop the audible clock (`RenderedPlaybackClock`).
- **Scoring honesty.** SongHub's `libMewtwo` is unavailable; KANTA's own `MelodyScoreSession`/`DryPitchDetector` is the scorer. Mic monitoring stays **opt-in** and off by default (repo rule). Do not present Mewtwo-grade accuracy.
- **Reservation idempotency.** Every reserve request carries a UUID request id; `ReservationQueue.reserve(requestId, …)` is the only mutation path. Duplicate callbacks, retries and lifecycle restoration must not create a second reservation or a second completion credit (repo rule §4).
- **One playback clock.** Background video, lyrics, countdown and score all read the same audible clock; the BGV player never becomes a second timeline.
- **Background media decode.** Media3 must decode on its own surface/thread, be muted by default (it is decoration, not audio), and release the decoder when the background changes.
- **TV / D-pad focus.** SongHub's focus handling (`setupFocusAnimation`, `dispatchKeyEvent`) is the defect-prone part of the original; KANTA already had a dead-tap class of bug on iPad. Beta 1 focuses handset; ATV work is Beta 2 with explicit focus tests.
- **Offline-first.** No runtime network dependency. Every feature except optional prebuilt downloads must work in airplane mode (SongHub's headline property).
- **Content licensing.** The bundled soundbanks (Yamaha/Korg/Ketron/Dream, "custom made by jc") and the BGV clips are third-party. They must be verified as distributable before any public KANTA release; the app ships with an in-app licence notice like SongHub's `prebuilt_option_songs_note`. KANTA's own `kanta-tone.sf2` remains the default bank.
- **Rebrand enforcement.** A CI check greps the KANTA Android sources, resources and manifest for `songhub`, `shub`, `_songhub._tcp`, `songhub_settings`, `songhub_prefs`, `com.songhub.*` and fails the build on a hit (excluding `replica/` documentation, which is allowed to mention the original).
- **Room identity in the companion app.** If the phone remote ships, the Room database identity changes (`songs(com.songhub.smartlinkapp.Song)` → new identity) and requires a destructive migration or a schema export — it must not silently reuse the old identity.
- **Package and store identity.** `applicationId` stays `ph.kanta.reborn`; the visible label becomes `KANTA Karaoke`. Changing the application id would break installs and the verified-download pipeline — it is explicitly **not** in scope.

---

## 6. Build order

### Milestone 0 — Rebrand foundation (no behaviour change)
- Apply recon §10 to the plan: content root `KANTA/`, index files `kantidx` / `kantablob`, prefs `kanta_settings` / `kanta_prefs`, NSD `_kanta._tcp`, lock names, log tags, label `KANTA Karaoke`.
- Add the CI naming-sweep check.
- Screens: none. Tables: none. Routes: none.
- **Exit:** `rg -i 'songhub|shub'` over `apps/android` returns only `replica/` docs; CI green.

### Milestone 1 — Vertical slice (Beta 1 core, ugly but end to end)
- `ContentRoot` resolution + `ContentScanner` (read `KANTA/`, legacy `SongHub/` read-only fallback with notice).
- `core/songbook` Meg index parser + SQLite cache + FTS4 search.
- Search → reserve → **single** queue projection → TinySoundFont playback of the selected song → lyric highlight → countdown → score result.
- Background: solid/photo only (no video yet).
- Screens: **S01, S02, S03, S07** (handset). Tables: `volumes`, `songs`, `artists`, `song_search`, `index_meta`. Contracts: `ContentRoot`, `SongCatalog`, `StagePresenter` (existing owner).
- **Exit:** on a device with the owner's content pack, a song can be searched, reserved, played with lyrics, counted in, scored and queued — with the queue visible in KANTA's existing UI.

### Milestone 2 — Must-haves, Beta 1 remainder
- BGV video backgrounds (Media3), background photos, camera background via existing `CameraStage`.
- Adjustment overlay: key / melody / tempo / volume, applied as intents to `SessionController`, with clock re-anchoring.
- Settings groups: lyric, audio, mic, score, behavior, bgv (`kanta_settings`).
- Score result overlay + score settings (difficulty/mode from SongHub's `ScoreSettings`).
- Smart Link **host** side (NSD + beacon + WebSocket) with explicit pairing, replay protection and revocation.
- Screens: **S05 (artists), S12, S15, S16, S17**. Tables: unchanged. Contracts: `SettingsStore`, `ScoreSink`, Smart Link frames.

### Milestone 3 — Should-haves (Beta 2)
- Media library and players (S09–S11) with Media3; new songs (S04); rankings (S06); ATV player + focus handling (S08); prebuilt content downloader against the KANTA manifest (F08); intro card, interlude, wavy time bar.
- **Exit:** full screen parity for S04–S11 except diagnostics.

### Milestone 4 — Could-haves (Beta 3)
- Smart Link **phone app** (`ph.kanta.reborn.companion`) with Room cache and the ported binary protocol.
- Voice search, spectrum analyser, score celebration (stars/confetti/balloons), custom fonts, diagnostics mode (S14), help/about (S13), app self-update — the last one only if the owner wants it; the repo's release pipeline stays authoritative.

### Milestone 5 — Fixes from `/replica-entrepreneur`
- Public review mining (App Store/Play/Reddit) applied to KANTA Karaoke positioning and fixes.

### Verification per milestone
- JVM `coreRegression` task for the framework-free `core/songbook` parser (fixture-based, no device needed).
- Hosted CI Android build + unit tests (`reconstruction-validation.yml`).
- Device acceptance is **not** implied by a green build (repo rule): Beta 1 exit requires a real device run with the owner's content pack.

---

## 7. Risk register

| # | risk | impact | mitigation |
| --- | --- | --- | --- |
| 1 | Legacy container payloads stay protected | a legacy songbook can be listed but not played from the bundled container | metadata-only interoperability (mirrors iOS); playback uses the user's own plain MIDI/KAR/MP3/WAV files; the proprietary native engine is out of bounds |
| 2 | Scoring quality drops vs Mewtwo | users see worse scores | label the scorer honestly; tune `DryPitchDetector`; keep difficulty settings |
| 3 | Two companion transports double the attack surface | security/QA cost | one shared pairing/token module; shared command allow-list; unpaired frames rejected |
| 4 | BGV video decode stalls the UI | jank during singing | dedicated Media3 instance, muted, released on change, no surface thrash |
| 5 | Third-party soundbank/clip licences block release | public release blocked | KANTA ships none of the legacy media by default; any owner-authorised set is licence-checked and recorded in `apps/android/THIRD_PARTY_NOTICES.md`; KANTA's own bank stays the default |
| 6 | Rebrand drift (a stray "SongHub") | inconsistent branding | CI naming sweep check |
| 7 | 4.16.67 feature drift unnoticed | parity claim wrong | re-recon the delta before Beta 2 parity claims |

---

## 8. Summary

- **Stack in one line:** existing native Android app (`ph.kanta.reborn`, Java 17 + Views + NDK TinySoundFont) + SQLite songbook index + AtomicFile app state + Media3 backgrounds + two companions (existing encrypted browser companion, new ported Smart Link host/phone app) — **no backend, no accounts, offline-first**.
- **Tables:** 5 SQLite tables (+1 FTS4 shadow) + the existing `library-v1.json` state file.
- **Routes:** 17 screens / 12 flows mapped onto KANTA's existing owners; 4 new internal contracts; 2 companion protocols; 1 optional content manifest endpoint.
- **Three riskiest parts:** (1) the undocumented Meg content format, (2) the scoring engine replacement (no Mewtwo), (3) running two companion transports safely.
- **Next step:** `/replica-design` — design system for the KANTA Karaoke stage overlays (lyric typography, countdown, score result, background treatment, keypad) in KANTA's existing visual language, then `/replica-build` starting at Milestone 0.
