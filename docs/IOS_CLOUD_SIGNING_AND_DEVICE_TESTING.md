# iOS cloud build and personal-device testing

## Owner constraints

The current test target is one physical iPhone, a free Apple Account, Safari installation, and no locally owned Mac or PC. All repository work and builds must therefore remain cloud-hosted.

## What is already available

- `.github/workflows/ios-validation.yml` validates the iOS source and unsigned simulator build.
- `.github/workflows/ios-runtime-artifacts.yml` creates a device-targeted **unsigned** IPA input. It is not installable as-is.
- `.github/workflows/ios-visual-walkthrough.yml` runs the iPhone and iPad simulator walkthroughs.
- `.github/workflows/verified-downloads.yml` packages accepted exact-SHA artifacts and records their provenance.

The unsigned IPA is useful as a build artifact and signing input. It is not a signed app, and it must never be labeled as Safari-installable.

## Free Apple Account: actual boundary

A free Personal Team is a development-signing path, not an Ad Hoc or enterprise distribution identity.

- Personal Team development provisioning is short-lived (typically seven days) and is intended for development installation.
- The normal supported flow uses Xcode with the device registered to the Personal Team. It is not a Safari OTA distribution entitlement.
- An unsigned IPA cannot be installed by opening it in Safari.
- The GeloTech PEM/CA certificate previously examined is not an Apple-issued iOS Development or Apple Distribution identity and must not be used to sign KANTA.
- Do not attempt to create an enterprise certificate, reuse another developer's certificate, spoof a Team ID, or install a profile from an untrusted signing service.

With the current constraints (free account, no Mac/PC, physical iPhone, Safari-only installation), there is no supported Apple route that can produce a directly installable native IPA. A cloud macOS runner can compile KANTA, but it does not gain access to the owner's Apple Personal Team or the iPhone merely because it runs Xcode.

## Supported paths

| Goal | Supported route | What changes |
| --- | --- | --- |
| Test native code without owning a computer | Keep using hosted iPhone/iPad simulators | No Apple membership needed; this is not physical-device acceptance |
| Install on the physical iPhone with a free account | Use Xcode Personal Team development signing on a Mac that can pair with the iPhone | Requires temporary access to a Mac and a device connection; reinstall after provisioning expires |
| Install directly from Safari | Apple Ad Hoc distribution with a registered device, signed IPA and OTA manifest | Requires paid Apple Developer Program membership and a device-registered Ad Hoc profile |
| Distribute to invited testers | TestFlight | Requires paid Apple Developer Program membership and App Store Connect setup |

A cloud Mac alone does not solve device pairing: the iPhone must be reachable to the signing/install workflow, for example through a supported remote-device connection. Do not promise that a generic GitHub-hosted runner can see a phone attached to the owner's iPhone cable.

## Future Safari OTA release gate

Only enable a Safari installer after all of the following are verified for the exact source SHA:

1. Active paid Apple Developer Program membership and the correct Team ID.
2. Apple-issued Apple Distribution certificate with its matching private key.
3. Ad Hoc provisioning profile for `com.gelo.karaoke` that includes the iPhone UDID and matching Team ID.
4. Xcode archive and export succeed with the profile and certificate.
5. The exported IPA's embedded profile, entitlements, bundle ID, signing identity and device allowlist are inspected and match.
6. The IPA and `manifest.plist` are hosted over HTTPS with a working `itms-services://?action=download-manifest&url=...` link.
7. A real Safari installation and first launch are completed on the target iPhone.
8. The website download manifest records the exact source SHA, workflow run, artifact identity and signed state.

The existing `apps/ios/.github/workflows/manual-ad-hoc-ipa.yml` is stored below the repository workflow root, so GitHub Actions will not discover it at that location. It must not be presented as an active workflow. Before enabling this path, move/reconcile it under `.github/workflows/`, pin every third-party action to a reviewed full commit SHA, validate the export contract and keep signing secrets out of source and logs.

## Credential handling

Never paste Apple passwords, app-specific passwords, private keys, P12 files, provisioning profiles, recovery codes or API keys into ChatGPT, issues, commits or workflow logs. Add signing material only through an approved encrypted secret store after the account and distribution route are confirmed. Do not create repository secrets until the owner has the appropriate Apple membership and matching credentials.

## Current decision

Continue cloud compilation, tests, simulator walkthroughs and exact-SHA unsigned artifact production. Do not publish the unsigned IPA as an installable iPhone download. Revisit Safari OTA only after the Apple distribution prerequisites above exist, or change the goal to a supported development-install route with temporary Mac/device access.
