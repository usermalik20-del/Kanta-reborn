#!/usr/bin/env python3
"""Fail closed when implementation changes are not reflected in their owner docs."""
from __future__ import annotations

import subprocess
import sys

ROOT_DOCS = {
    "apps/web": {"README.md", "apps/web/README.md", "docs/ARCHITECTURE.md", "docs/TESTING.md"},
    "apps/android": {"README.md", "apps/android/README.md", "docs/ARCHITECTURE.md", "docs/TESTING.md", "docs/RECONSTRUCTION.md"},
    "apps/ios": {"README.md", "apps/ios/AGENTS.md", "docs/ARCHITECTURE.md", "docs/TESTING.md", "docs/RECONSTRUCTION.md"},
}
WORKFLOW_DOCS = {"README.md", "AGENTS.md", "docs/ARCHITECTURE.md", "docs/TESTING.md"}
TOOL_DOCS = {"docs/TESTING.md"}
WEBSITE_PATHS = (
    "apps/web/", "apps/ios/PRIVACY.md", "apps/ios/PublicSite/",
    "apps/android/app/src/main/assets/privacy.txt", "tools/verify-website.py",
    "tools/tests/test_vercel_deploy_workflow.py",
    "tools/tests/test_website_security_contract.py",
    "tools/tests/test_site_page_handler.test.js",
)
PRIVACY_DOCS = {
    "apps/web/privacy.html",
    "apps/android/app/src/main/assets/privacy.txt",
    "apps/ios/PRIVACY.md",
    "apps/ios/PublicSite/privacy.html",
}
DOC_ONLY_PATHS = {
    "apps/web/README.md", "apps/web/privacy.html",
    "apps/android/README.md", "apps/android/app/src/main/assets/privacy.txt",
    "apps/ios/PRIVACY.md", "apps/ios/README.md", "apps/ios/AGENTS.md",
}


def is_implementation_path(path: str) -> bool:
    if path in DOC_ONLY_PATHS or path.startswith(("apps/ios/Docs/", "apps/ios/PublicSite/")):
        return False
    return path.startswith(("apps/web/", "apps/android/", "apps/ios/"))


def required_docs(changed_paths: set[str]) -> set[str]:
    required: set[str] = set()
    for path in changed_paths:
        if is_implementation_path(path) and path.startswith("apps/web/"):
            required.update(ROOT_DOCS["apps/web"])
        elif is_implementation_path(path) and path.startswith("apps/android/"):
            required.update(ROOT_DOCS["apps/android"])
        elif is_implementation_path(path) and path.startswith("apps/ios/"):
            required.update(ROOT_DOCS["apps/ios"])
        elif path.startswith(".github/workflows/"):
            required.update(WORKFLOW_DOCS)
        elif path.startswith("tools/"):
            required.update(TOOL_DOCS)

        if "privacy" in path.lower() or path.endswith("PrivacyInfo.xcprivacy"):
            required.update(PRIVACY_DOCS)
    return required


def changed_paths(base: str, head: str) -> set[str]:
    result = subprocess.run(
        ["git", "diff", "--name-only", "--no-renames", "--ignore-all-space", "--ignore-blank-lines", f"{base}...{head}"],
        check=True, capture_output=True, text=True,
    )
    return {line.strip() for line in result.stdout.splitlines() if line.strip()}


def is_website_change(path: str) -> bool:
    return any(path == prefix or path.startswith(prefix) for prefix in WEBSITE_PATHS)


def validate(changed: set[str]) -> tuple[set[str], set[str]]:
    required = required_docs(changed)
    updated_docs = changed & required
    missing = required - updated_docs
    return required, missing


def main() -> int:
    if len(sys.argv) not in (3, 4):
        print("Usage: check-documentation-sync.py BASE_SHA HEAD_SHA [GITHUB_OUTPUT]", file=sys.stderr)
        return 2
    try:
        changed = changed_paths(sys.argv[1], sys.argv[2])
    except subprocess.CalledProcessError as exc:
        print(exc.stderr or "Unable to compare the requested Git revisions.", file=sys.stderr)
        return 2

    website_changed = any(is_website_change(path) for path in changed)
    print(f"Website validation required: {str(website_changed).lower()}")
    print("Changed paths:")
    for path in sorted(changed):
        print(f"  - {path}")
    if len(sys.argv) == 4:
        with open(sys.argv[3], "a", encoding="utf-8") as output:
            output.write(f"website_changed={str(website_changed).lower()}\n")

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
