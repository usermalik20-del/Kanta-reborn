#!/usr/bin/env python3
"""Reject orphaned Swift tests and missing local test modules before cloud compilation."""

import json
import re
from pathlib import Path


def audit(root: Path) -> dict:
    ios = root / "apps/ios"
    manifest = (ios / "Package.swift").read_text()
    targets = dict(re.findall(
        r'\.(?:testTarget|target|executableTarget)\(\s*name:\s*"([^"]+)"[\s\S]*?path:\s*"([^"]+)"',
        manifest,
    ))
    test_targets = dict(re.findall(
        r'\.testTarget\(\s*name:\s*"([^"]+)"[\s\S]*?path:\s*"([^"]+)"',
        manifest,
    ))
    errors = []
    for name, path in targets.items():
        if not (ios / path).is_dir():
            errors.append(f"Missing target directory: {name}: {path}")

    suites = {}
    covered = set()
    for name, path in test_targets.items():
        files = sorted((ios / path).rglob("*.swift"))
        if not files:
            errors.append(f"Empty test target: {name}")
        suites[name] = len(files)
        covered.update(files)
        for file in files:
            for module in re.findall(r'@testable\s+import\s+(\w+)', file.read_text()):
                if module not in targets:
                    errors.append(f"Unknown local test module: {file.name}: {module}")

    project = (ios / "project.yml").read_text()
    for name in ("KantaUITests", "KantaNativeTests"):
        path = f"Tests/{name}"
        if f"path: {path}" in project and f"- {name}" in project:
            files = set((ios / path).rglob("*.swift"))
            if not files:
                errors.append(f"Empty Xcode test target: {name}")
            covered.update(files)
            suites[f"{name} (Xcode)"] = len(files)
    for file in sorted((ios / "Tests").rglob("*.swift")):
        if file not in covered:
            errors.append(f"Orphaned test file: {file.relative_to(ios)}")
    if errors:
        raise ValueError("\n".join(errors))
    return {"swift_test_files": len(covered), "suites": suites}


if __name__ == "__main__":
    print(json.dumps(audit(Path(__file__).resolve().parents[1]), indent=2))
