# Android OTA upgrade acceptance checklist

**Status: UNVERIFIED.** This checklist is required evidence, not a claim that acceptance has occurred. Run only with an owner-approved persistent compatible signing key; never generate a replacement key for this test.

- [ ] Confirm the CI signing key is the historical production/preview key and record only its public certificate SHA-256 (never the key).
- [ ] Install the prior published APK on a physical supported Android device; record package, versionCode, versionName and signer certificate.
- [ ] Publish a candidate only after the immutable release-asset and signer-continuity gates pass.
- [ ] Fetch the HTTPS feed on-device and confirm the candidate has a strictly higher versionCode and the pinned APK SHA-256 matches.
- [ ] Exercise download interruption/resume and confirm no partial APK is offered for installation.
- [ ] Install through the existing user-consent flow; confirm package identity, signer continuity and app data/queue/library persistence.
- [ ] Restart the device and confirm the upgraded app opens and retains persisted state.
- [ ] Verify an older/equal feed, changed digest, changed signer and unrelated source are refused.
- [ ] Preserve device model, OS version, source SHA, APK SHA-256, versionCode, certificate digest and timestamp as review evidence.

A missing compatible keystore, missing pinned historical APK, or failed certificate comparison is a publication blocker. Do not weaken the gate or silently bootstrap a new signer.
