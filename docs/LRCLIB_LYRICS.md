# LRCLIB — optional Android lyric lookup (local candidate)

## API verification

Official contract: <https://lrclib.net/docs.md> (checked 2026-11-12).
The public API needs client identification. The owner supplied a **public,
self-asserted application ID**, not a secret. Every request sends `Lrclib-App-Id`
and a `User-Agent` identifying KANTA, the installed version and project URL.
The supplied `GET /api/search?q=hello` setup check returned **HTTP 200 / 20 records**.
This proves network/header setup only, not Android runtime integration.

## User flow and data boundary

Now Playing → **Find lyrics on LRCLIB** (also overflow) is enabled only for a song
without timed lyrics. The dialog lets the user review/edit the title and optional
artist before pressing **Search**. Only those strings and ordinary connection/app
identification are sent over HTTPS to `lrclib.net`. No audio, microphone data,
singer names, filesystem paths, queue or library scan is sent.

`GET /api/search?track_name=…&artist_name=…` returns up to 20 records. KANTA shows
title, artist, album, duration and timing availability, and requires the user to
choose a version. It does not guess a semantic match or replace embedded lyrics.
Search result matching/timing accuracy is not guaranteed. Album/duration are
shown to help distinguish recordings; they are not fabricated from missing metadata.

A selected synchronized LRC is parsed into the existing controller-owned lyric cue
list for **this playback only**. There is no disk cache or automatic lookup on
startup/song change, and no upload/publish endpoint is used. Line timestamps remain
line timestamps; word timing is not invented. Untimed lyrics are a selectable-text
preview, not a falsely synchronized karaoke track. Instrumental, missing, malformed
or oversized results leave the song unchanged.

Network work is serial, off-main and gated to one in-flight request plus a one-second
minimum manual-search interval. Requests have connect/read/response-size/time bounds,
redirects disabled, no automatic retries and connection cleanup. Playback identity,
lookup generation and foreground checks discard stale results on replacement,
stop or backgrounding. Result choices are generation-fenced again before use.
Neither queue, transport, melody targets nor scoring owns this optional adapter.

## Format and verification

`LrcLyrics` handles minute/second timestamps with 1–3 fractional digits, repeated
timestamps, sorted lines, blank instrumental cues and bounded positive/negative
offsets. Plain text receives no fake time. Timed source is capped at 128 KiB, 1,000 source lines/cues and 4,096 characters
per line. Parsing runs on the network worker before any UI selection.
Malformed input degrades to a no-timing result; permissions/network/server errors
are surfaced without logging private metadata or response bodies.

- Host `LrcLyricsRegressionTests` is registered in `coreRegression`.
- Offline device JSON contract checks are registered in `RuntimeChecks` alongside
  font decoder checks. Neither test runner contacts LRCLIB.
- Python guards verify explicit user initiation, identification headers, HTTPS bounds,
  existing-lyric preservation, generation fences and runner registration.
- Java/Android execution is unavailable locally (`getcpu(2)` JVM failure); no APK,
  emulator, device rendering, timing accuracy or exact-SHA CI acceptance is claimed.

LRCLIB is a third-party service. Public API access is not a blanket license to
redistribute lyrics. KANTA does not bundle, publish or claim ownership of returned
lyrics; users remain responsible for permitted use. Website/iOS privacy copies
identify this as an Android-only **unreleased local candidate**, not an iOS feature.

No push, merge, signing, publishing or deployment has been authorized.
