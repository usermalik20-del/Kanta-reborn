# Android private v34

This local candidate is based on the retained final v33 workspace, not a fresh checkout of main. It does not replace a public download.

## Ownership and acceptance

- The embedded and fullscreen Stage controls project the same session state; the Play/Pause control owns transport while song result actions still apply to one selected song. Menu owns the Party Pads destination; Stage Controls opens the pads without leaving the Stage. Settings routes do not own Stage & Visual, Intermission or Voice & Scoring.
- Browser Remote still uses the existing local authenticated transport. Pairing bootstrap data is for the selected host, not for a public or cloud endpoint. Wi-Fi reconnect does not create an extended session, and host Stop still revokes access. Validate actual camera scans and subsequent Wi-Fi loss/reconnect on a trusted two-device LAN; do not skip a hostname or certificate warning to test scanning.
- `AutomaticDiagnostics` is independent of the manual report. The private build's `AUTOMATIC_DIAGNOSTICS` flag is false. An approval and privacy review must precede enabling any build or provisioning the service. See [web collection protocol](../apps/web/DIAGNOSTICS.md) for the launch dependencies. The Android client uses one bounded crash marker and a once-per-day foreground signal; it never serializes the local support report. The manual copy/share route is unchanged.

## Validation plan

- Run workspace/Java/companion contract checks, Python host tests, Java core/TLS regression, Android private lint and instrumentation compilation, and the web diagnostics and website tests. Verify Android package ID, version, signer continuity and 16 KB alignment on the exact local APK.
- Separately review an Android Menu capture against the iOS glyph reference. Check the 48 dp controls at default and enlarged text, embedded and fullscreen, in portrait and landscape. Play/Pause should not duplicate standalone Play; the Pad shortcut should open the same sounds as Menu.
- Separately validate an HTTPS browser run, real camera decode, reconnect after a transient network loss, and Stop/expiry on a paired host. Production diagnostics activation additionally requires a private staging database, a retention-job check and authorization review. Instrumentation compiled is not instrumentation run.
