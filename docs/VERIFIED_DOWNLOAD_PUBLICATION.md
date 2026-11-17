# Platform-specific verified download publication

The active download pipeline is fail-closed, exact-SHA pinned and platform-owned. Android and iOS are separate release paths; one platform's build, artifact or walkthrough must not gate or rewrite the peer platform.

## Active release paths

1. A successful **KANTA Android validation** completion on `main` starts Android publication. The packager resolves the Android validation artifact for the triggering run's exact full source SHA, verifies its identity and contents, and extracts the debug APK. It does not wait for or require any iOS workflow.
2. A successful **KANTA iOS unsigned artifacts** completion on `main` starts iOS publication. The packager resolves the unsigned iPhoneOS archive and the iPhone/iPad simulator walkthrough for that same full source SHA, verifies both, and extracts the unsigned IPA. It does not wait for or require Android validation.
3. The `workflow_run` trigger is restricted to `branches: [main]` so feature-branch pull-request completions do not create publisher runs. The job retains success, branch and workflow-name guards as defense in depth.
4. Each platform package is uploaded as its own GitHub Actions provenance artifact with 1–30-day retention. The publisher reads its artifact ID, digest, creation time and expiry from GitHub and refuses missing, malformed, expired or out-of-range provenance. The Android `downloadUrl` is a durable public GitHub Release asset; the retained Actions artifact remains its provenance source. The iOS `downloadUrl` remains the exact retained Actions artifact because the workflow does not upload the unsigned IPA to a Release. Do not publish a public Release URL for iOS until that asset is actually created and verified.
5. `publish-platform-download.py` updates only the selected platform's top-level and nested manifest entry and its owned page. It preserves the peer platform's source SHA, artifact IDs, digest, signing state and retention metadata. Each platform entry carries its own `signInRequired` flag: Android's public Release asset is false; iOS's retained Actions-artifact link is true. There is no shared sign-in flag because the two platforms can be on different publication generations. Before writing, the publisher verifies Git ancestry against that platform's current published source: the same SHA is allowed only for a source workflow run ID at least as new as the published run; a descendant is allowed, while older ancestors, older same-SHA retries and unrelated history are rejected. This is required because concurrency serializes publisher jobs but does not guarantee completion order.
6. `tools/verify-website.py` and `tools.tests.test_website_security_contract` run before the website files are committed. The publisher stages only `apps/web/downloads.json` and the page owned by the selected platform, pushes the generated website change to `main` with the narrowly scoped GitHub Actions token, then dispatches the existing Vercel production deployment.
7. A failed source gate, missing or ambiguous artifact, digest mismatch, website contract failure or rejected push leaves the previous public download page unchanged.

## Contract-only workflow and legacy helpers

`verified-downloads.yml` is a contract-only workflow. It must not package, upload or publish either platform. It still runs unit-contract tests for the older `prepare-verified-downloads.py` and `publish-verified-downloads.py` helpers, whose paired same-SHA behavior is retained as legacy compatibility coverage. Those legacy assumptions are not the active release policy and must not be used to re-couple the independent platform publishers.

## Signing and acceptance boundaries

- Android validation provides a release-signed candidate for publication and a separate debug APK for emulator acceptance; neither is a Google Play release.
- iOS output is unsigned, requires separate trusted personal signing, has no iCloud Save, and is not directly installable through Safari, TestFlight or the App Store.
- iPhone/iPad simulator walkthroughs are required for iOS publication on the same iOS source SHA; they do not establish physical-device acceptance.
- The pipeline uses the GitHub Actions token for repository publication and dispatch. It does not use Apple credentials or signing secrets.
- A green build is not a public release: the manifest, platform page and production deployment must all be verified before claiming publication.

See [workflow ownership and validation](TESTING.md), [architecture](ARCHITECTURE.md), and [unfinished release work](UNFINISHED_WORK.md) for the current gates and owner-controlled release blockers.


## Website download-status validation

The status bar validates the selected platform's own manifest entry and the exact page link. It accepts either a public Release asset whose tag suffix and filename match that entry's source SHA, or a retained Actions artifact whose run and artifact IDs match its provenance. The access message is derived from the platform entry, not a shared manifest flag. This supports the current split where Android has a public Release asset while iOS links to a sign-in-protected Actions artifact. The status bar reports publication metadata and checksums; it does not download or independently re-hash the file.
