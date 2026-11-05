#!/usr/bin/env python3
"""Run the local guards a change actually needs, from one command.

The repository has many gates, and which ones apply depends on the paths a change
touches. Remembering that mapping is friction, and forgetting it is what produces a
red pull request after a long wait. This tool derives the applicable local guards
from the changed paths, reuses tools/check-documentation-sync.py for the required
owner documents (so the mapping cannot drift), and prints clearly what it could not
run here.

A passing run is NOT a build, an APK/IPA, a simulator walkthrough, or device
acceptance. Cloud-only gates are listed as CI-only and never reported as passing.

Usage:
    python3 tools/preflight.py                     # plan + run against origin/main
    python3 tools/preflight.py --base main
    python3 tools/preflight.py --list              # print the plan, run nothing
    python3 tools/preflight.py --base origin/main --head HEAD
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path
from runpy import run_path

REPO_ROOT = Path(__file__).resolve().parents[1]
SYNC = run_path(str(REPO_ROOT / "tools/check-documentation-sync.py"))
required_docs = SYNC["required_docs"]

PY = sys.executable or "python3"

# Local guards, keyed by the change class that requires them. Every command is a
# list of argv tokens; {"base"} and {"head"} are substituted with the revisions.
GUARDS: dict[str, list[list[str]]] = {
    "any": [
        ["git", "diff", "--check"],
    ],
    "android": [
        [PY, "tools/check-java-structure.py"],
        [PY, "tools/check-android-workspace-contract.py"],
        [PY, "tools/verify-android-companion-interface.py"],
    ],
    "ios": [
        [PY, "tools/check-ios-presentation-contract.py"],
    ],
    "web": [
        [PY, "tools/verify-website.py"],
    ],
    "branding": [
        [PY, "tools/check-product-branding.py"],
    ],
    "tooling": [
        [PY, "-m", "unittest", "discover", "-s", "tools/tests", "-p", "test_*.py"],
    ],
    "workflows": [
        [PY, "-m", "unittest", "tools.tests.test_workflow_path_ownership"],
        [PY, "-m", "unittest", "tools.tests.test_platform_download_publishing"],
        [PY, "-m", "unittest", "tools.tests.test_vercel_deploy_workflow"],
    ],
}

# Gates that exist in CI and cannot run in the authoring environment. They are
# printed so nobody misreads a local pass as one of them.
CI_ONLY = [
    "Android: Gradle compile, unit tests, lint, APK packaging (android-validation.yml)",
    "Android: runtime/emulator acceptance and APK verifier (needs ANDROID_HOME)",
    "iOS: Xcode build, unsigned IPA, simulator walkthrough (ios-*.yml, macOS runners)",
    "Web: node --test suites and the Vercel build (node does not execute here)",
    "Release: durable release asset, downloads.json/updates.json publication, Vercel deploy",
]


def classify(paths: set[str]) -> set[str]:
    """Return the change classes present in ``paths``."""
    classes = {"any"}
    for path in paths:
        if path.startswith("apps/android/") and SYNC["is_implementation_path"](path):
            classes.add("android")
        elif path.startswith("apps/ios/") and SYNC["is_implementation_path"](path):
            classes.add("ios")
        elif path.startswith("apps/web/") and SYNC["is_implementation_path"](path):
            classes.add("web")
        elif path.startswith(".github/workflows/"):
            classes.add("workflows")
        elif path.startswith("tools/"):
            classes.add("tooling")
    if any(p.startswith(("apps/", "assets/")) for p in paths):
        classes.add("branding")
    return classes


def plan(classes: set[str], base: str, head: str) -> list[list[str]]:
    """Ordered guard commands for the given change classes."""
    commands: list[list[str]] = []
    for name in ("any", "android", "ios", "web", "branding", "tooling", "workflows"):
        if name in classes:
            commands.extend(GUARDS[name])
    commands.append([PY, "tools/check-documentation-sync.py", base, head])
    seen, ordered = set(), []
    for command in commands:
        key = tuple(command)
        if key not in seen:
            seen.add(key)
            ordered.append(command)
    return ordered


def committed_range(base: str, head: str) -> bool:
    """True when base...head contains at least one committed change.

    ``check-documentation-sync.py`` compares revisions, so it says "not required" for
    work that is still uncommitted. The matrix printed above already covers that case
    from the working tree, and a missing document still fails the run.
    """
    result = subprocess.run(
        ["git", "rev-list", "--count", f"{base}..{head}"],
        cwd=REPO_ROOT, capture_output=True, text=True,
    )
    return result.returncode == 0 and result.stdout.strip() not in ("", "0")


def resolve_base(explicit: str | None) -> str:
    """Default to the upstream default branch so a change is judged as a pull request is."""
    if explicit:
        return explicit
    for candidate in ("origin/main", "main", "HEAD~1"):
        result = subprocess.run(
            ["git", "rev-parse", "--verify", "--quiet", f"{candidate}^{{commit}}"],
            cwd=REPO_ROOT, capture_output=True, text=True,
        )
        if result.returncode == 0:
            return candidate
    raise SystemExit("preflight: cannot resolve a base revision; pass --base REV")


def changed_paths(base: str, head: str) -> set[str]:
    """Paths changed between ``base`` and ``head``, plus anything still uncommitted.

    A preflight run usually happens before the commit exists, so the working tree and
    the untracked files are part of the change as far as the author is concerned.
    """
    def git(*args: str) -> set[str]:
        result = subprocess.run(["git", *args], cwd=REPO_ROOT, capture_output=True, text=True)
        if result.returncode != 0:
            raise SystemExit(f"preflight: git {' '.join(args)} failed\n{result.stderr.strip()}")
        return {line.strip() for line in result.stdout.splitlines() if line.strip()}

    paths = git("diff", "--name-only", "--no-renames", "--ignore-all-space",
                "--ignore-blank-lines", f"{base}...{head}")
    paths |= git("diff", "--name-only", "--no-renames", base)
    paths |= git("ls-files", "--others", "--exclude-standard")
    return paths


def run(commands: list[list[str]]) -> tuple[int, list[tuple[str, int, str]]]:
    """Run each guard; return (failures, results)."""
    failures, results = 0, []
    for command in commands:
        display = " ".join(command)
        print(f"\n=== {display}")
        result = subprocess.run(command, cwd=REPO_ROOT, capture_output=True, text=True)
        output = (result.stdout or "") + (result.stderr or "")
        tail = [line for line in output.splitlines() if line.strip()]
        summary = tail[-1][:160] if tail else "(no output)"
        status = result.returncode
        if status:
            failures += 1
            print(output[-4000:] if output else "(no output)")
        print(f"--- {'PASS' if status == 0 else 'FAIL'} ({summary})")
        results.append((display, status, summary))
    return failures, results


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", help="base revision (default: origin/main, main, or HEAD~1)")
    parser.add_argument("--head", default="HEAD", help="head revision (default: HEAD)")
    parser.add_argument("--list", action="store_true", help="print the plan and run nothing")
    args = parser.parse_args()

    base = resolve_base(args.base)
    paths = changed_paths(base, args.head)
    print(f"preflight: {base}...{args.head} — {len(paths)} changed path(s)")
    for path in sorted(paths):
        print(f"  - {path}")
    if not paths:
        print("preflight: nothing changed; nothing to run.")
        return 0

    classes = classify(paths)
    commands = plan(classes, base, args.head)
    print(f"\nchange classes: {', '.join(sorted(classes))}")

    docs = required_docs(paths)
    missing = docs - paths
    if docs:
        print("owner documents required by these paths:")
        for path in sorted(docs):
            print(f"  {'missing' if path in missing else 'updated'}: {path}")

    print("guards to run locally:")
    for command in commands:
        print(f"  - {' '.join(command)}")
    print("CI-only gates (never proven by this run):")
    for line in CI_ONLY:
        print(f"  - {line}")
    if not committed_range(base, args.head):
        print(f"note: {base}..{args.head} has no committed change, so check-documentation-sync.py "
              "reports \"not required\"; the owner-document list above covers the uncommitted work.")

    if args.list:
        return 0
    if missing:
        print("\npreflight: FAIL — owner documents are missing (see above); run the guards anyway below.")

    failures, _ = run(commands)
    print("\npreflight summary:")
    for display, status, summary in _:
        print(f"  {'PASS' if status == 0 else 'FAIL'}  {summary}")
    if failures or missing:
        print(f"\npreflight: FAIL ({failures} guard(s) failed, {len(missing)} document(s) missing)")
        return 1
    print("\npreflight: PASS (local guards only — not a build, packaging, simulator or release result)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
