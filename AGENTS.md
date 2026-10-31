# Astra repository operating standard

## 1. Scope and instruction precedence

Apply this standard to existing and new code across the native iOS and Android projects.

When instructions conflict, use this precedence:

1. Current explicit owner instructions
2. Root AGENTS.md
3. The nearest platform- or directory-specific AGENTS.md
4. docs/ARCHITECTURE.md and docs/RECONSTRUCTION.md
5. Current testing, publishing, playback and handoff policies
6. Recovered historical instructions

Historical release claims and recovered workflow instructions are evidence only. They do not establish the current reconstruction, test, signing or release state.

Before implementation, read:

- docs/ARCHITECTURE.md
- docs/RECONSTRUCTION.md
- apps/ios/AGENTS.md
- docs/TESTING.md
- docs/GITHUB_PUBLISHING_POLICY.md
- docs/YOUTUBE_PLAYBACK_PLAN.md

Before a substantive refactor, follow docs/CODING_CONTEXT_GUIDE.md and regenerate docs/CODEBASE_CONTEXT_PACKET.md from the actual repository source.

A substantive refactor includes changes to state ownership, module boundaries, public interfaces, persistence formats, dependencies, navigation, lifecycle behavior, build systems, signing, CI, release configuration or cross-platform behavioral contracts.

## 2. Repository acquisition and evidence

Gather existing files directly from the online repository. Do not ask the owner for local settings or duplicate copies when the repository already contains the required material.

Do not invent missing repository state, installed dependencies, performance measurements, test results or release evidence. Do not treat suggested libraries as installed until verified from manifests, project files or resolved dependencies.

Distinguish these states explicitly:

- Recovered: an original artifact was located and its provenance recorded.
- Reconstructed: behavior was implemented from available evidence.
- Compiled: the identified target built successfully at the reported commit.
- Tested: the identified test suite executed and results were recorded.
- App-built: an installable application artifact was produced.
- Device-accepted: required behavior was verified on the named simulator or device.
- Signed: signing completed under the applicable release gate.
- Released: an approved artifact was published through the release process.

Never infer a later state from an earlier one. In particular, a green compile is not test execution, UI acceptance, a signed IPA/APK, a verified download or a public release.

## 3. Native platform boundaries

Keep native iOS and Android implementation boundaries independent:

- source trees
- dependencies
- package management
- build systems
- signing
- release configuration

Shared product behavior may be documented, but one platform must not become a build-time or runtime dependency of the other.

Preserve the product invariants in apps/ios/AGENTS.md within the iOS project unless a higher-priority current instruction explicitly changes them.

## 4. Playback, queue and scoring invariants

Each native application must have exactly one authoritative owner for playback and FIFO queue state.

All queue mutations and playback-state transitions must flow through that owner. Views, controllers, services and callbacks may observe or request changes, but must not maintain competing authoritative queue state.

Every queue reservation must receive a stable, distinct reservation identity. Repeated reservations of the same song are separate reservations and must not be deduplicated by media identity alone.

Every playback attempt must have a playback identity suitable for idempotent completion processing.

Natural-completion scoring must occur at most once per playback identity. Duplicate callbacks, observer replay, lifecycle restoration and retry paths must not award duplicate completion credit.

Preserve scoring and camera-preview capabilities. Preserve microphone monitoring and experimental DSP as explicit opt-in behavior. Do not silently enable either capability.

## 5. Recovery and provenance

Do not activate historical workflows during recovery import.

Do not overlay historical snapshots wholesale. Reconcile overlapping implementations through source-level comparison, preserve the intended current architecture and record provenance for recovered or reconstructed behavior.

Files designated as preserved recovery artifacts must remain byte-for-byte unchanged. If repair, normalization or adaptation is necessary, create a separate derivative and document:

- original source
- transformation performed
- reason for the change
- resulting location

Preserve required licenses, copyrights, provenance and existing authorship records. Do not rewrite history or metadata to imply different authorship.

## 6. Privacy, credentials and publication safety

Never commit, publish, upload or include in fixtures, logs, screenshots, artifacts or handoff documentation:

- user music catalogs
- credentials
- access tokens
- private keys
- certificates
- provisioning profiles
- signing materials
- secret-bearing environment values
- unapproved personal data

Use synthetic or explicitly approved test fixtures.

Follow docs/GITHUB_PUBLISHING_POLICY.md before publication. Review the complete bounded diff and generated artifacts for secrets, private data, media-library contents, signing assets and unintended historical material.

Merging, releasing, deploying and running CI are authorized engineering actions (§9, §11). Signing is not gated by policy here; it is simply unavailable in this environment because no Apple credentials or macOS toolchain exist. Record that as a capability limit, never as an approval requirement.

## 7. YouTube integration boundary

Keep the queued official YouTube integration described in docs/YOUTUBE_PLAYBACK_PLAN.md behind the current reconstruction work.

Do not implement ad suppression, unsupported playback behavior or unsupported parity claims.

Do not describe planned functionality as implemented, tested or released.

## 8. Testing and failure repair

When reconciling or changing source, follow docs/TESTING.md.

Required validation includes:

1. Run the layout preflight.
2. Confirm the complete test suite remains wired into its intended targets.
3. Compile all package test targets before test execution.
4. Execute the applicable tests.
5. Preserve compiler and test failure diagnostics.
6. Build the affected application targets when the environment permits.
7. Perform simulator or device acceptance separately when required.

Fix production behavior or demonstrably invalid fixtures. Never delete assertions, disable suites, remove target wiring, suppress lint or weaken checks to conceal a regression.

Report these independently:

- compiler result
- test result
- application build result
- simulator acceptance
- physical-device acceptance
- signing status
- release status

For a failing CI job:

1. Inspect the failing step and complete available logs.
2. Classify the failure as deterministic, transient, environmental, policy-related or externally blocked.
3. Make the smallest architectural repair.
4. Add or update regression coverage.
5. Update the failure/repair ledger.
6. Validate against the actual resulting commit.
7. Rerun only the necessary workflows.

Do not rerun an unchanged commit for a deterministic failure. An unchanged rerun is allowed only when evidence supports a transient or infrastructure failure.

Avoid duplicate workflow dispatches. Respect provider quotas, concurrency controls and policy denials.

## Documentation synchronization is a release requirement

Every implementation or workflow change must update its owner documentation in the same PR. the `website-validation.yml` documentation-sync job runs the lightweight diff-based guard for every PR; a missing required README, architecture, testing, reconstruction, engineering-instructions or privacy document fails validation. The required paths are maintained in `tools/check-documentation-sync.py` and tested in `tools/tests/test_documentation_sync.py`. Web changes require the root and website README plus architecture/testing; Android changes require the root and Android README plus architecture/testing/reconstruction; iOS changes require the root README, iOS engineering instructions plus architecture/testing/reconstruction; workflow edits require the root README, this file, architecture and testing; tooling changes require testing documentation. Any privacy disclosure change requires all website, Android, iOS app and iOS public-site privacy copies to be updated together. Do not weaken the matrix to make a PR pass; update the relevant documents with the accepted behavior, changed architecture, test evidence and remaining limitations. Existing main-branch platform/release/deploy workflows execute the same guard inside their existing job before building or publishing. Documentation-only changes must not start platform build runners.

## 9. CI and build authorization

Owner authorization (updated 2026-10-23) grants Astra unrestricted use of CI for this repository:
any workflow, any branch, any trigger, workflow dispatch, reruns, labels and PR-triggered runs.
No per-run approval, label, actor or run-count gate applies.

The root workflow triggers are intentionally scoped by ownership and source paths:
- `android-validation.yml`: Android source, explicitly listed Android-owned tools/tests, exact-SHA packaging inputs and relevant release-workflow changes. Never use a broad `tools/**` filter; website-only verifier and contract-test changes must not start Android CI.
- `ios-validation.yml`: iOS build inputs, resources and tests on pull requests and main pushes. Exclude documentation-only `PRIVACY.md`, `PublicSite/**`, `Docs/**`, README and AGENTS paths; feature branches use the pull-request event only.
- `ios-smoke.yml`: focused iOS native/UI smoke on relevant iOS pull requests and manual dispatch.
- `ios-runtime-artifacts.yml`: unsigned iPhoneOS packaging on iOS-source pull requests and native iOS/Android source plus exact-SHA release-packaging inputs on main; Android-only pull requests do not allocate an iOS archive. Website-only tools and packaging-tool PRs are excluded.
- `ios-visual-walkthrough.yml`: separate iPhone and iPad simulator acceptance on iOS pull requests; Android source and release-packaging tools trigger it on main after merge for exact-SHA release provenance. The Android companion-interface guard can trigger a PR walkthrough. Weekly schedule, tags and manual dispatch remain supported. Website-only verifier/test edits and workflow-only edits are excluded.
- `website-validation.yml`: website source/tooling plus iOS PublicSite and native privacy-copy contract checks; documentation changes use this lightweight job, not native builds.
- `vercel-deploy.yml`: production deployment on main pushes or explicit dispatch; no pull-request validation trigger.
- `verified-downloads.yml`: exact-SHA release-contract tests and workflow-ownership tests on packaging/workflow changes; actual packaging is gated by successful same-SHA main-branch release runs or an explicit main-branch dispatch.

The verified-download packager binds to the exact workflow names in the root workflow files. Its Android artifact source is `KANTA Android validation`, not a separately named reconstruction workflow. Keep workflow names, packager constants and contract tests synchronized. Owner-approval labels and the retired `kanta-gelo` actor gate have been removed; they blocked work without adding safety.

Only genuinely external limits remain: hosted-runner quotas, platform policy, and the absence of
Apple signing credentials in this environment.

Batch coherent changes and inspect failure evidence before rerunning.

## 10. Continuous execution and task selection

During an active engineering session, completion of one task transitions to the highest-priority unblocked task.

Select work from:

1. reproducible regressions
2. failing required checks
3. architecture violations
4. iPhone and iPad runtime acceptance gaps
5. cross-platform product parity gaps
6. missing tests or release evidence
7. documented backlog and release gates

Prioritize by user impact, regression risk, architectural dependency and reproducibility.

Do not wait idly for CI when independent implementation, testing, documentation or analysis can proceed safely.

Do not broaden a task merely because adjacent code could be improved. Prefer the smallest coherent change that restores the required invariant.

## 11. Repository and publication authority

Astra has full engineering authority in this repository: inspect and modify source, create and
delete branches, commit, push, merge green pull requests into `main`, close superseded work,
edit workflows, dispatch and rerun CI, build artifacts, and update documentation.

Remaining limits, kept for safety rather than process:

- Do not commit or publish credentials, signing material, user music libraries or personal data (§6).
- Do not force-push or rewrite already-published `main` history; land corrections forward.
- Do not present an artifact as signed, released or device-accepted unless that is literally true (§2).
- Do not accept third-party licence terms on the owner's behalf; record the requirement instead.

Dependency changes, workflow changes and destructive migrations still require coherent commits,
justification in the handoff and validation through the applicable checks.

## 12. Documentation and handoff

Update architecture and handoff documentation whenever behavior, ownership, module boundaries, platform boundaries, test requirements or release gates change.

Keep architecture, reconstruction status and task inventories synchronized with implementation. A placeholder, mock, disabled path or unvalidated scaffold is not a completed feature.

Maintain a durable handoff containing:

- completed work
- affected commit IDs
- pull request and CI run links when available
- source provenance
- observed failures
- root causes
- repairs applied
- regression coverage
- validation evidence
- current blockers
- exact next executable task
- any unavailable gates and their precise reason

## 13. Source comments

Use concise, professional engineering comments.

Comments should explain:

- intent
- architectural constraints
- invariants
- provenance requirements
- non-obvious edge cases

Do not add conversational progress notes, implementation narration, speculative commentary or references to agents, tools or models.

## 14. Pause conditions

Pause only when:

- required repository access is unavailable
- an external dependency blocks all remaining executable work
- the hosting platform denies a workflow (quota, policy) and no alternative path exists
- a genuinely destructive, irreversible act would destroy information that cannot be reconstructed
- governing instructions conflict and precedence does not resolve them
- the environment lacks the toolchain the task needs (for example macOS/Xcode here)
- all currently executable work is exhausted
- the active engineering session ends

Approval or permission is not a pause condition: this repository grants standing authority for
builds, CI, merges and documentation changes. When a capability is missing, say plainly which
capability is missing and continue with the work that does not need it.

When one gate is blocked, record the precise blocker and immediately advance an independent task if one exists.

Never claim background or autonomous execution after the active session ends. Future monitoring requires an explicitly configured scheduled task.

## 15. PR #36 architecture and validation supplements

Before each substantive change, also read `docs/ENGINEERING_ARCHITECTURE_CONTRACT.md` and follow its ownership, lifecycle, parity, and documentation review checklist. Treat planned seams as unimplemented until code and tests prove otherwise.

For each CI failure, update `docs/CI_FAILURE_REPAIR_LEDGER.md` with the fingerprint, run/SHA, confirmed cause versus hypothesis, repair commit, and regression coverage. Append green validation evidence in a separate documentation follow-up after the actual fixed head passes; do not erase failures or infer simulator acceptance from compilation.


## Exact-SHA download publication automation

A successful KANTA iOS unsigned artifacts run on main is the release-pipeline completion signal. The Verified download packaging workflow then resolves the Android validation artifact, unsigned iPhoneOS IPA and both-device simulator walkthrough from the same exact main-branch SHA. It waits for in-flight exact-SHA gates instead of accepting an older successful artifact.

Only after all three gates pass does packaging create up-to-30-day, provenance-bearing Android and unsigned iOS bundles. The publication helper verifies that both builds share the same full source SHA, validates the expected debug/unsigned signing states, and writes apps/web/downloads.json plus the two platform download sections. The workflow commits those generated website files with its narrowly scoped GitHub Actions token and dispatches the existing Vercel production deployment workflow.

The iOS download remains an unsigned personal-signing input; this automation does not create Apple signing identities, provisioning profiles, Safari OTA installation or physical-device acceptance. A failed or missing exact-SHA gate fails closed and leaves the previous website downloads untouched. A rejected non-fast-forward publication push also fails safely rather than overwriting newer main-branch work.

**Current CI implementation note (2026-10-31):** the `website-validation.yml` documentation-sync job is the single lightweight pull-request-wide documentation gate. It uses the PR base/head diff, not only the latest commit, and runs on Ubuntu with a five-minute timeout. The same checker is embedded as a step in existing main-branch Android/iOS/website/release/deployment jobs; it must not create additional platform jobs. iOS simulator walkthrough checks the documentation contract in both device jobs. A workflow path change must update this operating standard, `docs/ARCHITECTURE.md` and `docs/TESTING.md` in the same PR. The workflow ownership regression test must keep this topology and the path boundaries covered.
