# Platform-specific verified download publication

The active download pipeline is fail-closed, exact-SHA pinned and platform-owned. Android and iOS are separate release paths; one platform's build, artifact or walkthrough must not gate or rewrite the peer platform.

## Active release paths

1. A successful **KANTA Android validation** completion on `main` starts Android publication. The packager resolves the Android validation artifact for the triggering run's exact full source SHA, verifies its identity and contents, and extracts the debug APK. It does not wait for or require any iOS workflow.
2. A successful **KANTA iOS unsigned artifacts** completion on `main` starts iOS publication. The packager resolves the unsigned iPhoneOS archive and the iPhone/iPad simulator walkthrough for that same full source SHA, verifies both, and extracts the unsigned IPA. It does not wait for or require Android validation.
3. The `workflow_run` trigger is restricted to `branches: [main]` so feature-branch pull-request completions do not create publisher runs. The job retains success, branch and workflow-name guards as defense in depth.
4. Each platform package is uploaded as its own GitHub Actions artifact with 1–30-day retention. The publisher reads the package artifact ID, digest, creation time and expiry from GitHub and refuses missing, malformed, expired or out-of-range provenance.
5. `publish-platform-download.py` updates only the selected platform's top-level and nested manifest entry and its owned page. It preserves the peer platform's source SHA, artifact IDs, digest, signing state and retention metadata.
6. `tools/verify-website.py` and `tools.tests.test_website_security_contract` run before the website files are committed. The publisher stages only `apps/web/downloads.json` and the page owned by the selected platform, pushes the generated website change to `main` with the narrowly scoped GitHub Actions token, then dispatches the existing Vercel production deployment.
7. A failed source gate, missing or ambiguous artifact, digest mismatch, website contract failure or rejected push leaves the previous public download page unchanged.

## Contract-only workflow and legacy helpers

`verified-downloads.yml` is a contract-only workflow. It must not package, upload or publish either platform. It still runs unit-contract tests for the older `prepare-verified-downloads.py` and `publish-verified-downloads.py` helpers, whose paired same-SHA behavior is retained as legacy compatibility coverage. Those legacy assumptions are not the active release policy and must not be used to re-couple the independent platform publishers.

## Signing and acceptance boundaries

- Android output is a debug-signed preview APK, not a Google Play release.
- iOS output is unsigned, requires separate trusted personal signing, has no iCloud Save, and is not directly installable through Safari, TestFlight or the App Store.
- iPhone/iPad simulator walkthroughs are required for iOS publication on the same iOS source SHA; they do not establish physical-device acceptance.
- The pipeline uses the GitHub Actions token for repository publication and dispatch. It does not use Apple credentials or signing secrets.
- A green build is not a public release: the manifest, platform page and production deployment must all be verified before claiming publication.

See [workflow ownership and validation](TESTING.md), [architecture](ARCHITECTURE.md), and [unfinished release work](UNFINISHED_WORK.md) for the current gates and owner-controlled release blockers.
