#!/usr/bin/env python3
"""Fail closed when implementation changes are not reflected in their owner docs."""
from __future__ import annotations

import subprocess
import sys
from pathlib import PurePosixPath

ROOT_DOCS = {
    "apps/web": {"README.md", "apps/web/README.md", "docs/ARCHITECTURE.md", "docs/TESTING.md"},
    "apps/android": {"README.md", "apps/android/README.md", "docs/ARCHITECTURE.md", "docs/TESTING.md", "docs/RECONSTRUCTION.md"},
    "apps/ios": {"README.md", "apps/ios/AGENTS.md", "docs/ARCHITECTURE.md", "docs/TESTING.md", "docs/RECONSTRUCTION.md"},
}
WORKFLOW_DOCS = {"AGENTS.md", "docs/ARCHITECTURE.md", "docs/TESTING.md"}
TOOL_DOCS = {"docs/TESTING.md"}
PRIVACY_DOCS = {
    "apps/web": {"apps/web/privacy.html"},
    "apps/android": {"apps/android/app/src/main/assets/privacy.txt"},
    "apps/ios": {"apps/ios/PRIVACY.md", "apps/ios/PublicSite/privacy.html"},
}


def required_docs(changed_paths: set[str]) -> set[str]:
    required: set[str] = set()
    for path in changed_paths:
        if path.startswith("apps/web/"):
            required.update(ROOT_DOCS["apps/web"])
        elif path.startswith("apps/android/"):
            required.update(ROOT_DOCS["apps/android"])
        elif path.startswith("apps/ios/"):
            required.update(ROOT_DOCS["apps/ios"])
        elif path.startswith(".github/workflows/"):
            required.update(WORKFLOW_DOCS)
        elif path.startswith("tools/"):
            required.update(TOOL_DOCS)

        if "privacy" in path.lower() or path.endswith("PrivacyInfo.xcprivacy"):
            for platform, docs in PRIVACY_DOCS.items():
                if path.startswith(platform + "/"):
                    required.update(docs)
    return required


def changed_paths(base: str, head: str) -> set[str]:
    result = subprocess.run(
        ["git", "diff", "--name-only", "--no-renames", f"{base}...{head}"],
        check=True, capture_output=True, text=True,
    )
    return {line.strip() for line in result.stdout.splitlines() if line.strip()}


def validate(changed: set[str]) -> tuple[set[str], set[str]]:
    required = required_docs(changed)
    updated_docs = {path for path in changed if path in required or
                    path == "README.md" or path.startswith(("docs/", "apps/")) and
                    (path.endswith(".md") or path.endswith(".html") or path.endswith(".txt"))}
    missing = required - updated_docs
    return required, missing


def main() -> int:
    if len(sys.argv) != 3:
        print("Usage: check-documentation-sync.py BASE_SHA HEAD_SHA", file=sys.stderr)
        return 2
    try:
        changed = changed_paths(sys.argv[1], sys.argv[2])
    except subprocess.CalledProcessError as exc:
        print(exc.stderr or "Unable to compare the requested Git revisions.", file=sys.stderr)
        return 2

    required, missing = validate(changed)
    if not required:
        print("Documentation sync: not required; no implementation/workflow paths changed.")
        return 0
    if missing:
        print("Documentation sync: FAILED. Update these documents in the same change:")
        for path in sorted(missing):
            print(f"  - {path}")
        print("\nRequired by changed paths:")
        for path in sorted(changed):
            mapped = required_docs({path})
            if mapped:
                print(f"  {path}: {', '.join(sorted(mapped))}")
        return 1
    print("Documentation sync: PASS. Required owner documents changed with this implementation.")
    for path in sorted(required):
        print(f"  - {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
