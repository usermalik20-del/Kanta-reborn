# Verified download publication

The download pipeline is deliberately fail-closed and exact-SHA pinned.

1. A main-branch change to tools/prepare-verified-downloads.py starts Android validation, the iPhone/iPad simulator walkthrough and the unsigned iPhoneOS archive.
2. Only a successful KANTA iOS unsigned artifacts run on main starts Verified download packaging.
3. The packager waits for and resolves all three successful runs by the same full commit SHA. It rejects missing, failed, expired, ambiguous or cross-revision artifacts.
4. It copies the accepted APK and unsigned IPA bytes into up-to-30-day GitHub Actions artifacts and writes checksums and provenance.
5. The publication helper checks both source SHAs and signing states, refreshes apps/web/downloads.json and the platform download sections, and links to the exact packaged artifact IDs.
6. The workflow pushes the generated website changes with a fast-forward-only push and explicitly dispatches KANTA Vercel deploy. A failed gate or rejected push leaves the existing production download page unchanged.

The workflow uses only the GitHub Actions token for repository publication and dispatch. It does not use Apple credentials or signing secrets. The IPA remains unsigned and is not directly installable through Safari, TestFlight or the App Store. Simulator acceptance is not physical-device acceptance.
