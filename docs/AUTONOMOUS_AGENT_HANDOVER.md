# Autonomous Agent Handover — KANTA Reborn

**Prepared:** 2026-10-26  
**Canonical repository:** [`usermalik20-del/Kanta-reborn`](https://github.com/usermalik20-del/Kanta-reborn)  
**Current work area:** PR [#97 — Compact persistent KANTA workspace foundation](https://github.com/usermalik20-del/Kanta-reborn/pull/97)  
**Related work:** PR [#96 — Implement compact persistent KANTA workspace](https://github.com/usermalik20-del/Kanta-reborn/pull/96)

This document is the operational handover for the next autonomous engineering agent. It records the known state, failure history, safety rails, and required order of work. It is not a claim that the current feature branch is stable or accepted.

## 1. First rule: re-establish reality before changing code

Do not trust this document as a substitute for live repository state. At takeover:

1. Read root `AGENTS.md`, this document, `docs/ARCHITECTURE.md`, `docs/RECONSTRUCTION.md`, `docs/TESTING.md`, `docs/GITHUB_PUBLISHING_POLICY.md`, `docs/CI_FAILURE_REPAIR_LEDGER.md`, and `docs/CODEBASE_CONTEXT_PACKET.md`. Read `apps/ios/AGENTS.md` before native iOS work and the nearest Android instructions before Android work.
2. Query GitHub for the current default branch SHA, open PRs, each PR's head SHA, review state, mergeability, and all workflow runs/checks. Never infer current state from this handover or an earlier assistant message.
3. Determine whether PR #96 and #97 overlap, whether either has merged or been superseded, and which exact commit is the intended candidate. Avoid parallel edits to the same Host/player surfaces.
4. Record a concise baseline in the PR/engineering log: branch + SHA, dirty/uncommitted state if the environment exposes it, open PRs, latest run IDs and conclusions, currently in-progress jobs, and failed/skipped/cancelled jobs with reasons.
5. Follow the current workflow run to completion where possible before pushing another change. Do not launch another duplicate run merely to make activity visible.

## 2. Current known facts (must be re-verified)

- PR #97 describes the compact persistent workspace foundation: persistent Search / Camera / Display / More toolbar, contextual display-status popover, reduced Host landing clutter, and source-contract coverage.
- PR #96 also describes a compact persistent workspace and overlaps the same product area. Decide which change is authoritative by comparing actual diffs and branch ancestry; do not merge both blindly.
- A candidate commit previously had a missing closing brace after the `KantaMainPlayerView` initializer in `apps/ios/Sources/Features/Host/HostView.swift`. This produced cascading Swift scope/brace compilation errors. A subsequent commit restored the brace. Verify the exact current file and compile it; do not assume the fix is green.
- An earlier iOS source-contract guard expected the More action in its old location after the button moved to the workspace toolbar. The guard and tests were adjusted to reflect the new owner/location while preserving the `stage.more` accessibility identifier.
- The Display status popover was moved to the toolbar/player owner to keep presentation anchored to the Display control. Confirm there is one owner, one presentation path, correct dismissal, and no duplicate state.
- The unsigned iOS artifact workflow run #221 (run ID `40352256137`) reported success on the candidate at the time checked.
- Reconstruction validation run #358 (`40352256378`), focused iOS smoke run #148 (`40352256425`), and visual walkthrough run #125 (`40352256434`) were observed running/queued at the time of the last inspection. Their current results are unknown; inspect them live.
- Earlier runs on a previous candidate included cancelled jobs and iOS compile failures. A cancelled or skipped workflow is not a pass. A successful unsigned archive is not proof of simulator UI acceptance or full product correctness.
- The prior work session ended without a verified complete green result for the compact workspace feature. Do not describe PR #97 as fully validated, merged, deployed, or production-ready unless fresh evidence proves it.

## 3. Immediate priority: stabilize, do not expand

Do not add more UI features until the current candidate has been inspected and the baseline compiles.

1. Inspect the latest CI run/job details and full logs for every failure. Distinguish source/test failures from runner setup, concurrency cancellation, skipped-by-condition, and queued capacity.
2. Check the exact failing SHA, not just the PR's current head. For every red run, retrieve the job and step logs; identify the first meaningful error, not cascading errors.
3. Reproduce deterministically in the repository's supported hosted workflow. Make the smallest root-cause fix that preserves intended behavior. Do not weaken assertions, remove tests, skip jobs, disable checks, or change required checks just to get green.
4. After the fix, push one focused commit. Verify the new workflow run explicitly references that exact SHA. Wait for all required checks to finish and inspect outcomes.
5. If the run fails again, repair the new root cause before starting another broad test batch. Avoid committing unrelated UI changes while a compile blocker remains.
6. If CI capacity or workflow dependencies cause queues/cancellations, inspect workflow `concurrency`, triggers, job dependencies, and conditional expressions. Reduce duplicate work only when correctness coverage is preserved; do not cancel required validation or hide checks.
7. Update `docs/CI_FAILURE_REPAIR_LEDGER.md` with the fingerprint, exact failing run/job/step, cause, fix commit, regression test, and first green run on the fixed SHA. Keep repairs marked candidate/pending until verified.

## 4. Required validation and merge gate

Treat each check separately; do not combine partial success into a blanket “green.”

- **Repository reconstruction / general validation:** Android and iOS jobs must complete successfully.
- **iOS focused simulator smoke:** build app and UI-test bundle, execute focused lifecycle/Host tests, and inspect failure logs if red.
- **Unsigned iOS artifacts:** build/archive succeeds on the same candidate SHA; this does not replace simulator acceptance.
- **iPhone walkthrough:** compile and run the UI walkthrough on the configured iPhone simulator.
- **iPad walkthrough:** compile and run the UI walkthrough on the configured iPad simulator.
- **Source/presentation contracts:** all relevant source guards and Python tests pass without weakening behavior assertions.
- **Architecture and docs:** update architecture, handover, context packet, and CI ledger when the actual architecture or known failure state changes.

A check counts as passed only when its conclusion is `success` on the exact intended candidate SHA. A skipped, cancelled, queued, in-progress, neutral, or success-on-another-SHA result is not acceptance. If a workflow intentionally skips a job, explain the condition and identify the separate check that covers its contract.

Only merge when required checks are green, branch protection/review requirements are met, the PR diff has been reviewed for unrelated changes, and the PR is not superseded. Do not merge automatically just because the user requested autonomous work; autonomy does not waive repository policy or safety gates. After merge, verify the merge SHA and post-merge checks, then inspect the deployed environment only if deployment is part of the workflow.

## 5. Architecture and implementation invariants

- Preserve the existing playback/session owner, queue owner, Host view model, presentation gate, camera background owner, and external-stage coordinator. Do not introduce a second player, queue, session store, or competing playback controls.
- Preserve the established transport contract: Play/Pause + Next; do not reintroduce Previous or a progress bar where architecture/product docs prohibit them.
- Search, Queue, More, Settings, Display, Camera, Stage Visuals, and Remote surfaces must route through their existing authoritative owners. Keep a single source of truth for presentation state and predictable dismissal.
- Stable accessibility identifiers are test contracts. When moving an element, preserve its identifier unless there is an explicitly justified migration, and update tests/guards to assert behavior and owner—not merely the presence of a string.
- Keep iPhone and iPad behavior responsive and accessible: hit areas, VoiceOver labels/order, popover/sheet adaptation, focus, dismissal, and layout must be tested.
- Do not remove existing permanent behavior without checking architecture, reconstruction evidence, and the full feature path. Avoid visual-only placeholder actions.
- Never add secrets, signing certificates/private keys, unlicensed media, copyrighted song packs, or unverified third-party assets to the repository.

## 6. Workflow hygiene and safe autonomous execution

- Work online through GitHub-hosted repository tools and CI/CD; do not require the owner to make local machine changes.
- Follow root `AGENTS.md` precedence and platform-specific instructions. Check current branch protection, publishing policy, pinned actions, and workflow conventions before editing workflows.
- Prefer a single focused commit per root-cause fix, followed by targeted and required broad checks. Do not batch three unrelated UI changes while the app does not compile.
- Before pushing, inspect the full diff against the PR base and run all available local/hosted static checks. Avoid repeated pushes that each trigger the entire workflow matrix.
- Never force-push, rewrite shared history, close a competing PR, merge, change required checks, or alter production deployment settings without confirming that the action is permitted by repository policy. If superseding a PR, document why and preserve relevant commits.
- Do not state that background work is continuing unless a real automation/agent session exists. Report only actions actually performed and results actually observed.
- Every progress update must distinguish: **completed and verified**, **changed but not yet verified**, **currently running**, and **blocked/failed**. Include run links and candidate SHA when useful.
- Do not end with status-only reporting when an unblocked, safe implementation or repair can be performed. Conversely, do not continue feature work past an unresolved blocker just to appear productive.

## 7. Suggested next execution sequence

1. Fetch current repository/PR state and reconcile #96 vs #97.
2. Inspect live statuses and logs for runs `40352256378`, `40352256425`, and `40352256434` (these IDs are historical pointers; current state may have changed).
3. Verify current branch Swift syntax around `HostView.embeddedStage`, the toolbar, and the player-owned Display popover.
4. Obtain a clean compile and focused smoke result on one candidate SHA.
5. Run the required reconstruction, unsigned archive, iPhone, and iPad checks without unnecessary duplicate dispatch.
6. Fix all new failures and update the CI ledger with the first green run on the fixed SHA.
7. Review the complete diff and test results; update the PR description so it matches actual behavior and evidence.
8. Merge only if the policy gates are satisfied. Verify post-merge checks and deployment if applicable.
9. Only then continue compact Search/Queue panels, the Stage Visuals inspector, and compact Settings/Remote panels—each as small, independently validated slices with architecture updates as needed.

## 8. Handover completion checklist

- [ ] Current main/head SHA and open PR states rechecked
- [ ] #96/#97 overlap resolved by evidence
- [ ] Current CI run states inspected (including skipped/cancelled reasons)
- [ ] First meaningful compile/test failure repaired
- [ ] Required checks green on one exact candidate SHA
- [ ] Regression tests cover behavior and accessibility contracts
- [ ] CI repair ledger records fix and first green run
- [ ] Architecture/context packet updated if architecture changed
- [ ] PR diff reviewed; no unrelated or unsafe changes
- [ ] Merge/post-merge/deployment verified where permitted
- [ ] Next prioritized task selected only after stabilization

**Definition of done for this handover:** the next agent has independently rechecked live state and knows the exact validated baseline. This document alone is not proof of completion.
