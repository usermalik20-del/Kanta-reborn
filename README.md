## Search-results dock overlaps Stage (2026-11-24)

The in-app search-results panel is docked as a persistent overlay on the Stage (rather than below it), which the runtime acceptance now accounts for.

## Compact landscape Home: no duplicate queue card (2026-11-24)

A compact landscape Home now hides the Up next queue card from construction (previously only after the first measurement), so it never duplicates the Stage's Up next ticker in any state, including the first frame.

## Compact landscape Home: no duplicate queue card (2026-11-24)

A compact landscape Home now hides the Up next queue card from construction (previously only after the first measurement), so it never duplicates the Stage's Up next ticker in any state, including the first frame.

## Compact keypad backspace icon (2026-11-24)

In compact landscape row heights the Delete key now shows the ⌫ glyph instead of the DEL text (too short to stay legible); normal rows keep DEL. The spoken label ("Delete one character") is unchanged. The runtime acceptance check for this behavior first executed on the CI emulator when the consolidated tree reached main.

## Android CI AAPT2 portability fix (2026-11-24)

## Emulator acceptance: Stage Controls pad diagnostics (2026-11-25)

The embedded Stage Controls pad-visibility check is failing in the emulator walk while the construction code is statically consistent (pads built once from a `static final` list, tags verified). The check now dumps the panel's full tag tree to logcat (tag `KANTA_RUNTIME`) on failure so the run artifact shows the exact runtime state.

## Emulator acceptance: Stage Controls bounds polled after layout (2026-11-25)

The below-stage bounds assertion ran synchronously right after opening the panel, before the layout pass that follows `setVisibility(VISIBLE)`, so it read stale geometry and failed. The check is now polled via `await` (`stageControlsBelowStage`, 50 ms / 15 s) between the open and the content/collapse verification; all assertions are unchanged.

## Emulator acceptance: Stage Controls below-stage geometry (2026-11-24)

The shipped portrait layout places the Stage Controls panel as a sibling below the Stage pane (`main.addView(embeddedControls)`), never layered over the Stage surface and never inside the keyboard scroll. The emulator walk's `checkStagePartyPads` now verifies that geometry (panel top at or below the Stage bottom) instead of the pre-v34 overlay expectation (FrameLayout parent inside Stage bounds), which the current product shape deliberately does not use. The pad/count/translucency assertions are unchanged.

## Emulator runtime acceptance: settings volume spec (2026-11-24)

The music volume slider `kanta.settings.music-volume` lives on the Voice & Scoring page (advanced options), not the Settings page. `hasSettingsWithoutDuplicateControls` therefore only asserts the absence of duplicate key/tempo/voice/intermission/background controls on Settings; the slider accessibility + persistence check runs on Voice & Scoring.

A machine-specific AAPT2 override (`android.aapt2FromMavenOverride=/opt/kanta-local/aapt2`) had been committed in `apps/android/gradle.properties`; the path exists only on one build machine, so GitHub runners failed AAR resource compilation with "Specified AAPT2 executable does not exist". The override is removed: CI uses the AAPT2 that AGP resolves from Maven, and local builds that pin an AAPT2 pass `-Pandroid.aapt2FromMavenOverride=...` on the command line instead of a tracked file.

## Android legacy import progress and file order (2026-11-21)

A full 48,000-record legacy pair import takes minutes on a phone, and the pair
picker gave no hint about which file is which. The import now reports
"Importing legacy songs: done/total…" live, the two bounded copy steps identify
their role (and detect the swap by the BLOB's MKMD prefix, with a specific
"select the IDX first / BLOB second" message), and a step-1 dialog tells the
user the smaller IDX file is requested first. Host verification imported all
47,997 decodable records of a real pair in 318 s; device acceptance is tracked
with the private build.
## Android private-variant launcher label (2026-11-20)

The opt-in **KANTA Private** Android build now gets its launcher label from the
variant resource source set (`apps/android/app/src/privateLegacy/res`) instead of a
manifest placeholder. Public builds keep the same `app_name` string, ID, and
behavior; the change resolves an unused-resource lint finding in the private
variant and keeps the manifest reference statically visible to lint.

## Mobile-first responsive enhancements (2026-11-16)

- **Header**: Sticky with blur backdrop (rgba(8,11,20,0.92) + 16px blur)
- **Buttons**: Full-width on mobile (actions, download cards), auto-width on desktop
- **Feature cards**: Single column mobile, 2 columns tablet (768px), 3 columns desktop (900px), 4 columns large (1200px)
- **Footer**: Stacks on mobile, 2-col tablet, 5-col desktop
- **Sections**: Improved mobile spacing (40px/56px margins), hero padding 32px/48px
- **Split banner**: Stacks on mobile with centered content
- **Navigation**: Preserved original responsive pattern (site-nav with overflow-x: auto on mobile)
- **Accessibility**: Added mobile nav drawer HTML/JS as progressive enhancement

---
## Landscape mode and camera rotation (2026-11-16)

- Android reflows between portrait and landscape without restarting the karaoke session.
- A primary-stage camera preview rebinds to the replacement primary stage. Fullscreen or
  external-stage previews stay on their selected stage during primary-screen rotation.
- Physical-device rotation and camera acceptance remain outstanding; emulator checks are not
  a substitute for testing on the target phone/tablet.

---
# KANTA Reborn

**Karaoke with your own songs, on Android, iPhone and iPad.**

Import your music, follow the lyrics when available, and line up songs for your next singing session. Personal backgrounds and optional voice tools help you make it your own. Features differ between Android and iPhone/iPad.

## Try KANTA

- **[Android download and installation](https://kanta-public.vercel.app/android)**
- **[iPhone / iPad download and installation](https://kanta-public.vercel.app/ios-download)**
- **[How to use KANTA](https://kanta-public.vercel.app/guide)**
- **[Help](https://kanta-public.vercel.app/support)**

**These are test versions, not Google Play or App Store releases.** Check the download page for what is included and any installation requirements.

The iPhone/iPad download cannot be installed directly: it needs a separate, trusted tool to prepare it for installation using your own Apple account. KANTA's website does not ask for your Apple password.

## Keypad sounds, interlude once, BGV access, SoundFont progress (2026-11-14)

- **Keypad sounds**: Fixed `SoundPool.OnLoadCompleteListener` to use anonymous class for D8 desugaring compatibility; keypad tones now load on IO thread for faster readiness.
- **Standby interlude**: Bundled welcome track now plays once on startup (not looping); custom intermission tracks still loop via `PartyAudio.loop()`.
- **Background Video (BGV)**: Added prominent entry in More ▸ Background Video for managing stage background videos.
- **SoundFont downloads**: Added progress dialog with live percentage for FluidR3 GM, MuseScore General, and SGM-v2.01 downloads via `SessionController.DownloadProgressListener`.

---
## What you can do

- **Bring your own songs:** import karaoke files (MIDI/KAR) or audio files (MP3/WAV). Lyrics depend on what the file contains. Android can also import KANTA song libraries (an IDX + BLOB pair): the shipped library unlocks automatically, and for any other library you are asked for the unlock details supplied with it, which KANTA does not save. Malformed or truncated MKMD data in either direct decoding or paired legacy import is rejected promptly; a damaged legacy record is skipped without stalling the import. Party sounds are bundled recordings. Legacy-library compatibility is experimental and may not work with every library or play every song.
- **Plan your session:** search your library, line up songs and keep saved song sets. The Android preview now shows reserved songs above the keypad, accepts exact song codes with RES, and displays an Up next hint on stage. Playback controls have more room.
- **Personalize the screen:** choose backgrounds or use your own photos and videos. On Android, open Menu → Stage & Visual to shuffle photos or videos. You can also choose a lyric font from a folder you grant read access to.
- **Try optional voice tools:** microphone input, voice effects and scoring are available for testing, with different support on each platform. Android includes separate Small Room, Room, Hall and Concert Hall effects.

For music files that need an instrument sound library, KANTA uses files called **SoundFonts**. See the guide for setup.

<!-- party-sounds-assets-committed -->
## Keypad press sounds & standby interlude (2026-11-14)

The on-screen keypad now plays a short tone for each key press (0–9, Delete, Reserve). Tones are silent while a song is playing on stage. A bundled standby welcome (backing track + branding line) plays between songs on the empty stage; the owner can override with a custom track or disable it in Party Mode.

## Now Playing divider removed & scroll fade (2026-11-13)

The stray `|` divider between Key and Tempo columns in the Now Playing header was removed. A vertical fading edge (24 dp) was added to the main scroll view so content fades softly into the floating bottom dock instead of a hard cutoff.

## Before you install

- **Back up your music and saved work.** Switching Android test versions may require uninstalling the old app, which can erase its saved data.
- **Download access varies by platform and build.** Each download page shows whether a GitHub account is required and any expiry limits; always use the current page rather than an old build link.
- **Real-device testing is not complete.** Voice quality, microphone delay, camera use and external displays still need testing on physical devices. Passing automated tests does not guarantee they work on your device.
- **Remote control is experimental.** Compatibility and setup differ between platforms; use a trusted private network and share pairing codes only with people you trust. Closing the host screen or leaving the app disconnects the session; commands are not replayed after reconnect.

- **Remote control is experimental.** The legacy iOS Bonjour remote is plaintext and intended only for a trusted local network; its new connection cap and pairing timeout limit idle sessions but do not encrypt traffic or rate-limit PIN guesses. Browser Remote and the encrypted Android companion are separate paths, not upgrades to that legacy channel. Share pairing codes only with people you trust.
- **YouTube playback is not available.**

## Privacy

Camera and microphone features are optional. The website asks before enabling optional analytics and advertising; those website scripts and ads are not included in the native apps.

Read the [privacy information](https://kanta-public.vercel.app/privacy) before using KANTA.

## For contributors

Build instructions, test results and engineering details live in the developer documentation—not on this front page. A daily automated check re-verifies the website, download contracts, and Android/iOS builds every day; see [testing and release requirements](docs/TESTING.md).

- [Android development](apps/android/README.md) · [iOS development](apps/ios/AGENTS.md) · [Website development](apps/web/README.md)
- [Architecture](docs/ARCHITECTURE.md) · [Engineering rules](docs/ENGINEERING_ARCHITECTURE_CONTRACT.md)
- [Testing and release requirements](docs/TESTING.md) · [Download verification](docs/VERIFIED_DOWNLOADS.md)
- [Outstanding work](docs/UNFINISHED_WORK.md) · [Android engineering records and testing limits](docs/ANDROID_LOCAL_B1_B2_D5_D8.md) · [Private Android v34](docs/ANDROID_PRIVATE_V34.md)
- [Plain-language writing guide](docs/USER_FACING_LANGUAGE.md)
- [Android lint validation and release evidence](docs/ANDROID_LINT_HARDENING.md) · [Android layout and stage search](apps/android/README.md#android-responsive-stage) · [Android SoundFont downloads](apps/android/README.md#android-soundfont-download-compatibility) · [Bundled legacy key library](apps/android/README.md#android-bundled-legacy-key-library)
- [Archived engineering notes from the previous README](docs/README_HISTORY.md) — historical records, not current download or feature guidance.
## AdSense policy truthfulness pass (2026-11-17)

Fixed public-site trust and content-quality issues ahead of AdSense re-review:
- Removed inflated reading times: all 15 blog articles now show estimates matching their
  actual word counts and the `blog.js` values (previously 8–18 min claims on 2–7 min pages).
- Added self-referential `rel="canonical"` links to every main page and blog article
  (category pages already had them).
- Removed the unsupported "ultimate karaoke app" superlative from the home description.
- Added `/about` (factual site-identity page: what KANTA is, what is tested vs open,
  how the site is maintained, GitHub issue tracker as the contact path); registered in
  `vercel.json` routes, `api/page.js` PAGES, `sitemap.xml` and `tools/verify-website.py`
  PUBLIC_ROUTES; linked from every footer.
- Corrected blog claims that contradicted the verified feature copy: party pad list and
  intermission behaviors (party-effects article), companion pairing/session/security facts
  (companion article, matches support.html), external-display settings path and
  dual-screen status, microphone setup flow (no calibration routine exists), SPICE
  accuracy wording and mic-capture phrasing, `.kanta-book` scope in two articles
  (metadata-only catalog; browser reader; no in-app import/restore flow).

Validation: `python3 tools/verify-website.py` passes; website security, Vercel workflow,
documentation-sync, customer-legal-copy and site-page-handler suites pass locally.
Node `--check` runs are unreliable in this iSH environment (intermittent hangs), so the
Node steps in `website-validation.yml` remain the authoritative gate for those files.
No ad code, consent gating, or ads.txt changes; no claims of AdSense approval.

**Production 404 fix (same pass):** all 15 blog articles, `/blog/all` and the six blog
category pages returned 404 in production: `vercel.json` dispatches those routes to
`/api/page`, but `PAGES` in `api/page.js` contained no blog entries (pre-existing since
the blog routes were added). Added the 22 missing `PAGES` entries, exported `PAGES`, and
added two regression tests: every `/api/page` route in `vercel.json` must resolve to a
`PAGES` entry, and every blog entry must point at an existing file.

**Function bundle fix (follow-up):** after adding blog `PAGES` entries, production
returned 500 on those routes because `functions["api/page.js"].includeFiles` was `*.html`,
which bundles only root-level HTML — the function never received `blog/*.html`. Changed
to `**/*.html` and updated both guards (`tools/verify-website.py`,
`tools/tests/test_website_security_contract.py`).

## Website validation fixes (2026-11-12)

Fixed verify-website.py entry-point and link/sitemap/CSP validation for blog routes. No user-facing changes.

## CI fix (2026-11-12)

Fixed trusted_main check in Android validation workflow by updating tracking branch before ancestor check.
<!-- keypad-sounds+interlude: 2026-11-14 -->

**Publisher tag-race fix (2026-11-18):** `platform-download-publishing.yml` now passes
`--target ${SOURCE_SHA}` to `gh release create`. Without it, the tag defaults to the
runner's checkout head, which can be a descendant of the claimed source SHA when main
advances between build and publish — making the tag object disagree with its
`-preview-<sha>` suffix and tripping the release-notes guard.


## Private Songbook CSV safety

The browser Songbook keeps its AES-GCM envelope, key derivation and decrypt routine
byte-identical to the archived reader reference. After authenticated decryption, the
four catalog fields are parsed and re-serialized for export; formula-leading cells
(including values with leading whitespace/control characters) are forced to text.
Exports do not reuse the raw decrypted CSV string, and the parser enforces the song
count limit even when the final row has no trailing newline.

## Private Songbook interaction safety

The browser Songbook keeps unlock/lock, page navigation and keyboard controls in its
core reader script. Decorative effects must not reveal the reader before decryption
succeeds or replace the core page-navigation state. The existing CSS flip-sheet
animation is driven by the core page state; decorative JavaScript only owns effects.
