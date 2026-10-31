# KANTA Privacy Policy

**Effective:** October 10, 2026. KANTA is preparing for internal testing.

KANTA is designed for karaoke with songs you add yourself. This policy explains what the app and website use, what stays on your device, and what you can choose. The customer-facing version is at https://kanta-public.vercel.app/privacy.

## Your information at a glance

- You do not need a KANTA account to play your local karaoke files.
- The current app has no advertising or user-tracking software. KANTA does not sell your app data.
- Your music is not uploaded to KANTA. You decide which folders, exports, backups, and sharing destinations to use.
- Microphone scoring, live vocal effects, voice search, companion remote, and iCloud are optional.

## Included demo songs and saved removal choices

KANTA bundles three short original KAR demonstrations and a generated instrument tone. It installs private managed copies of the demo files. Removing an included song saves a local removal preference and deletes only that managed demo copy; Restore copies the original from the app bundle offline. It does **not** remove imported songs, selected SoundFonts, user folders, or cloud-provider content. The bundled demos are not a KANTA-hosted account, tracking feature, or user library upload.

## Your library and saved songs

KANTA reads music from folders you choose through Apple's Files app. To remember your library, it can save file names and details, folder access, song titles, codes, known artists, your preferences, Favorites, playlists and singer labels on the device. It may make temporary local playback copies. Removing a library entry does not delete the original folder. Some recovery records and temporary files may remain until cleaned up by the app or operating system.

If you export a Songbook CSV or a saved setlist, it contains song information rather than audio files. You decide where to send it.

## Microphone, scores and voice search

Optional scoring and live vocal effects process microphone sound on the device. KANTA does not store recordings of singing or upload that audio to a KANTA server. Vocal monitoring starts off and is intended for compatible wired/USB audio equipment.

The optional Filipino/Taglish voice pack is downloaded from an external provider only on request; the provider can see normal download request information. A temporary, short recording is used on the device to turn speech into search text, then deleted. You can remove the downloaded pack. The separate English voice option uses Apple's speech service and may send speech to Apple for recognition when on-device recognition is unavailable. Voice search does not automatically reserve or play a song. Real-device language accuracy is still under evaluation.

## Private website Songbook

The public page does not hold your song list. You explicitly select an encrypted Songbook file and type its code in your own browser; KANTA does not upload the file, code or searches to its servers. The reader does not save your song list in cookies or browser storage. Anyone with both the file and code may open it, and the code does not expire automatically. Exported CSV and print/PDF copies are not encrypted. Browser or device downloads and print history may persist.

## Companion Remote and iCloud

A paired Remote sends song metadata, searches, singer names, queue state and commands over a trusted local network. A PIN is required, but **the connection is not encrypted**. Do not use it on public or untrusted networks.

Optional iCloud Save prepares selected settings and files for your Apple iCloud account. Apple and third-party Files providers manage transfers, backup, logs and storage under their terms. Uploads and deletions may take time. Deleting KANTA's iCloud backup does not delete your original library files, unrelated device backups or provider-managed copies.

## Website hosting and beta feedback

The website is served by Vercel, which may receive IP, browser and request information to operate and secure the website. The public website (not either native app) offers Google Analytics (G-L0QV01MWR4) and Google AdSense (publisher ca-pub-6290349358884298) only after a visitor accepts optional website technologies. Rejecting prevents both from loading. If accepted, Google may receive IP address, browser/device identifiers, page and interaction events, cookie or similar-technology data and ad-request information for measurement and advertising. Google processes this information under its own privacy terms. The private website Songbook excludes both integrations. The iOS app does not bundle or run these website scripts; this website disclosure does not describe app tracking. Website access records are distinct from the app's local songs and microphone use.

If you submit feedback through TestFlight, Apple may share your comments and related testing information with the developer. Please avoid sharing private songs, codes or passwords.

## Your choices, rights and updates

You can turn off scoring and voice search, disconnect the Remote, remove downloaded speech models and local saved lists, remove library entries, and change microphone, speech or local-network permissions in iOS Settings. For copies you exported to other services or devices, use those services' controls.

Find help and privacy information at https://kanta-public.vercel.app/support. Public contact details are being finalized before wider distribution. Applicable privacy and consumer rights remain in effect. We will update this policy and the effective date if how data is handled changes.

For content ownership and copyright permissions, see https://kanta-public.vercel.app/legal.

**Release review:** This is plain-language product copy. The detailed engineering/data audit remains in `Docs/PRIVACY_REVIEW.md`; Apple privacy disclosures, publisher identity, and a verified public contact remain release-review requirements.

## Experimental microphone DSP source update (PR #46)

The SoundpipeAudioKit `PitchCorrect` path works locally on the existing
optional microphone monitor engine. No singing audio is saved or
transmitted to KANTA servers by the tuner. The processed mic sound may
be sent to the user-connected wired/USB/line-out amplifier or headphones;
dry scoring remains upstream. The user controls microphone permission,
monitoring, tuner backend and disable/stop. This branch is pending
real-device testing and is not a claim that all audio hardware works.

## Optional local support diagnostics — October 10, 2026 (draft)

KANTA offers an opt-in local event log in Settings > Support & Diagnostic
Reports. It is **off by default**. When enabled, KANTA records only fixed
technical codes for app sessions, Host/scoring start and stop, microphone
permission/status, safety-gated audio output, and experimental DSP backend
selection. It stores **no microphone audio**, lyrics, song titles, folder
paths, singers, PINs, passwords, IP addresses, device IDs or raw error
messages. History is limited to **200 events or seven days**, stored
within app-private, backup-excluded, device-protected files.

The user may prepare a diagnostic report with app/build version, broad
device type (iPhone/iPad), iOS version, audio sample rate/buffer and
nonidentifying microphone/DSP configuration. KANTA **never automatically
uploads** this report. It leaves the device **only if the user reviews
and chooses to share** via iOS Share Sheet; recipients may keep copies.
Turning off diagnostics or choosing Erase removes local history and the
prepared local export. Previously shared copies cannot be revoked.
An interrupted app-session marker is not a confirmed crash. Apple
system crash reports, which may contain more private data, require a
separate intentional user share. No crash analytics SDK is included.

The legal signing and privacy checklist is recorded in
`Docs/BETA_SIGNING_AND_SUPPORT_DIAGNOSTICS.md`. If future support
inboxes retain submissions, their provider, access rights, retention,
user deletion process and applicable App Store privacy disclosures
must be finalized separately. This is not a claim of legal immunity.


## Live camera BGV (October 11, 2026 source update)

Camera BGV is off at every fresh launch and requires explicit camera permission.
The front camera is a mirrored live preview behind the lyrics on the device.
KANTA creates no camera recording, photo, video file, upload or backup and does
not attach a microphone input to the camera session. Stop Camera restores the
previous selected visual; entering the background, opening More/CONTROL, or
leaving the stage stops capture. Revoking permission is supported through iOS
Settings. Extended TV output retains its selected scenic background; ordinary
system screen mirroring may display the camera preview. Camera rotation,
interruptions and resource use require physical-device acceptance before release.
