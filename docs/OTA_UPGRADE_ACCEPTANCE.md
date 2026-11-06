# Android OTA upgrade acceptance checklist

**Status: UNVERIFIED.** The owner approved a new persistent signing identity because the historical private key is unavailable. This deliberately breaks in-place updates from previously published APKs. Do not claim continuity from old-signer installs; begin acceptance with a clean install of the first APK signed by the pinned new certificate, then test subsequent updates using that same key.

- [ ] Confirm the CI signing key matches the new pinned certificate SHA-256 `04488bab3deaf6c9702605e27b7e5469e5f1b6bcf2d440b0bd2b507202d385fc` (record only the public fingerprint, never the key).
- [ ] Clean-install the first new-signer APK on a supported physical Android device; record package, versionCode, versionName and signer certificate. Do not attempt an in-place update from old-signer APKs.
- [ ] Publish a candidate only after the immutable release-asset and signer-continuity gates pass.
- [ ] Fetch the HTTPS feed on-device and confirm the candidate has a strictly higher versionCode and the pinned APK SHA-256 matches.
- [ ] Exercise download interruption/resume and confirm no partial APK is offered for installation.
- [ ] Install through the existing user-consent flow; confirm package identity and data/queue/library persistence from this new-signer baseline.
- [ ] Restart the device and confirm the upgraded app opens and retains persisted state.
- [ ] Verify an older/equal feed, changed digest, changed signer and unrelated source are refused.
- [ ] Preserve device model, OS version, source SHA, APK SHA-256, versionCode, certificate digest and timestamp as review evidence.

The first new-signer publication remains blocked until the matching owner-controlled keystore/password secrets are configured and the pinned prior APK digest verifies. A candidate certificate differing from the pinned new fingerprint is always refused. Old installations require reinstall; updates among new-signer releases must preserve signer continuity.
