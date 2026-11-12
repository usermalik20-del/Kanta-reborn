## User-facing wording and README scope

Follow [the plain-language writing guide](docs/USER_FACING_LANGUAGE.md) for app labels,
messages, public website copy, download descriptions and the root README. Keep jargon,
implementation notes, commit/run IDs and detailed test evidence in developer docs or
diagnostics. Never hide installation, data-loss, privacy or unverified-device limitations.
Keep the root README a short user introduction (aim for at most 100 lines); do not prepend
engineering updates. Required README sync should change the affected user explanation or
contributor link, with technical evidence in the other required owner docs. The previous
README is preserved in `docs/README_HISTORY.md` as historical, not current, guidance.

## Android standby video lifecycle and truthful runtime evidence (2026-11-10)

Follow-up to the ordering repair in PR #192: `MainActivity.onStart()` now registers
its session listener (which synchronously creates the primary stage) before activating
visuals. Previously the first-launch activation saw a null presenter and left the newly
created `BackgroundStage` inactive, so it never prepared standby video. Existing
stop/start and fullscreen visibility ownership remain unchanged.

Standby live-frame evidence now advances only from `TextureView.onSurfaceTextureUpdated`
in the embedded and fullscreen renderers, not from playback or UI refresh timers.
The embedded renderer gates notification on active BGV media; the fullscreen renderer
requires an active foreground prepared player. Frozen/absent video cannot pass merely
because a timer runs. Playback, queue and media selection stay controller-owned.

The Android emulator runtime step is now blocking: instrumentation failure fails the
job. Diagnostic collection and artifact upload retain `if: always()` so failures still
preserve logs and available artifacts. This supersedes the historical non-blocking
runtime-step description below; no signing, merge or publication authority is changed.

Three source-contract regression guards failed before this repair and pass afterward:
first-launch activation order, actual-texture frame evidence, and CI failure propagation.
Local static/Python checks are not emulator acceptance; exact-head CI must prove the
standby assertion and all nineteen UI captures before claiming runtime verification.

## CI: Fix Trusted Main Branch Check (2026-11-09)

Fixed Android validation workflow to properly fetch full main branch history before ancestor check:
- Added explicit `git fetch origin main` and `git fetch origin main --tags` before the trusted_main check
- Fixes "ref outside main history" error on push to main branch
- Ensures git merge-base --is-ancestor check has access to full main branch history
## CI: Fix Trusted Main Branch Check (2026-11-09)

Fixed Android validation workflow to properly fetch main branch before ancestor check:
- Added explicit `git fetch origin main --depth=1` and `git fetch origin main --tags --depth=1` before the trusted_main check
- Fixes "ref outside main history" error on push to main branch
- Ensures git merge-base --is-ancestor check has access to up-to-date main branch history
# KANTA Reborn — Agent Configuration

## Model Groups

| Group | Purpose | Models |
|-------|---------|--------|
| Tier 1 · Free Main Coder | Primary coding agent | DeepSeek V4.1 Flash, DeepSeek V3.2, Qwen3-Coder, Gemini 3.6 Flash, Grok 4.7, Mistral Medium 3.5 |
| Tier 1 · Free Grunt Workers | Fast parallel sub-agents | DeepSeek V4.1 Flash, Qwen3-Coder, Gemini 3.5 Flash |
| Tier 1 · Free Overflow | Spare quota | DeepSeek V3.2, Grok 4.7, Mistral Medium 3.5 |
| Tier 2 · GPT Plus | GPT subscription models | GPT-6.1 Sol/Terra, GPT-6 Luna/Sol/Astra, GPT-5.6 Luna/Sol/Terra |
| Tier 3 · Paid Escalation & Reviewer | Highest capability | GPT-6.x, Claude Sonnet 5, GPT-5.6 |

## Agent Assignments

| Agent | Model Group | Focus |
|-------|-------------|-------|
| General Sub Agent | Tier 1 · Free Grunt Workers | Exploration, research, bulk processing |
| Migration & Architecture Agent | Tier 3 · Paid Escalation & Reviewer | Architecture, migrations, refactors |
| UI/UX & Theme Design Agent | Tier 1 · Free Main Coder | Visual design, UX, accessibility |
| Offline-First Data Agent | Tier 1 · Free Main Coder | Data persistence, sync, offline |
| QA & Build Integration Agent | Tier 1 · Free Grunt Workers | Testing, CI, build validation |

## BLA Hardening

- **Web**: `vercel-deploy.yml` runs `npx --yes bla-agents@3.1.2 harden ./apps/web --verify` before Vercel build. The CLI writes `.bla-hardened` output, but the Vercel route handler still serves the original source files; no Vite plugin or replacement step consumes the candidate.
- **Android**: the CLI runs after `:app:assembleRelease` against DEX intermediates and writes `.bla-hardened` candidates. The output is not repackaged into the APK; this step is non-blocking and is not a release security gate.
- **Credential isolation is mandatory**: third-party BLA code must not receive `VERCEL_TOKEN`, Android signing secrets, or a persisted GitHub checkout credential. Keep `actions/checkout` set to `persist-credentials: false`; scope the Vercel token to only Vercel API/CLI steps; scope signing secrets to the single release Gradle step; delete the temporary keystore with an `EXIT` trap; never write signing secrets to `GITHUB_ENV`.
- **Manual Android refs**: a main-branch release run may use signing secrets only when the checked-out commit is in `origin/main` history. Reject a non-main ref selected from a main-branch dispatch; feature-branch dispatches remain unsigned.
- **Production Vercel authority**: `vercel-deploy.yml` production deploy and `configure-is-a-dev-domain.yml` domain mutation jobs run only from `refs/heads/main`. Their validation/dispatch inputs must not grant production secrets to feature-branch runs.
- **Verification claims**: `--verify` verifies the CLI candidate, not the final deployed artifact. Vendor benchmark numbers and runtime-equivalence claims are not KANTA-verified.
- **iOS**: BLA is not integrated into iOS/IPA builds.
- **Repository controls**: do not claim branch protection, CODEOWNERS, CodeQL, Dependabot or secret scanning are enabled without current evidence. As of 2026-11-09, GitHub reports `main` as unprotected and the repository tree contains no CODEOWNERS file or CodeQL workflow; other security settings require a settings-level check.

Documentation sync: any change to BLA invocation, output integration, or release-secret scope must update `README.md`, `AGENTS.md`, `docs/ARCHITECTURE.md`, and `docs/TESTING.md` in the same commit.
During an active engineering session, completion of one task transitions to the highest-priority unblocked task.

## CI fix (2026-11-12)

Fixed trusted_main check in Android validation workflow by updating tracking branch before ancestor check.
