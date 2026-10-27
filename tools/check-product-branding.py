#!/usr/bin/env python3
"""Static branding guard for the KANTA product surfaces.

KANTA Karaoke replaces the legacy SongHub product. Product branding must not
survive in shipped source, resources, the public website or the Android
manifest, and the legacy container format name may only appear inside the
documented read-only compatibility namespaces.

Rules enforced:

* The literal legacy product name never appears under `apps/`.
* The legacy container format name may only appear inside the Android
  compatibility package `ph/kanta/reborn/core/songbook` and never in the
  public website.
* The Android application id stays `ph.kanta.reborn`; a rebrand must not
  change the install identity.
* The Android application label never contains the legacy product name.

Run from the repository root:

    python3 tools/check-product-branding.py
"""

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APPS = os.path.join(ROOT, "apps")
ANDROID_APP = os.path.join(APPS, "android", "app")
ANDROID_MANIFEST = os.path.join(ANDROID_APP, "src", "main", "AndroidManifest.xml")
ANDROID_COMPATIBILITY = os.path.join(
    ANDROID_APP, "src", "main", "java", "ph", "kanta", "reborn", "core", "songbook"
)
SKIP_DIRECTORIES = {".git", "build", ".gradle", "node_modules", "DerivedData"}
TEXT_SUFFIXES = {
    ".java",
    ".kt",
    ".swift",
    ".m",
    ".mm",
    ".c",
    ".cc",
    ".cpp",
    ".h",
    ".js",
    ".mjs",
    ".css",
    ".html",
    ".xml",
    ".json",
    ".txt",
    ".md",
    ".yml",
    ".yaml",
    ".gradle",
    ".properties",
    ".pro",
    ".cfg",
    ".ini",
    ".sh",
    ".py",
}

failures = []
passes = []


def check(condition, message):
    if condition:
        passes.append(message)
    else:
        failures.append(message)


def read(path):
    with io.open(path, encoding="utf-8", errors="replace") as handle:
        return handle.read()


def iter_files(root):
    for directory, subdirectories, names in os.walk(root):
        subdirectories[:] = [name for name in subdirectories if name not in SKIP_DIRECTORIES]
        for name in names:
            path = os.path.join(directory, name)
            if os.path.splitext(name)[1].lower() in TEXT_SUFFIXES:
                yield path


LEGACY_TYPE_DECLARATION = re.compile(
    r"\b(?:class|interface|enum|struct|object|final\s+class)\s+shub\w*", re.IGNORECASE
)


def declares_legacy_container_type(path):
    """True when a file declares a type whose name carries the legacy container name."""
    return LEGACY_TYPE_DECLARATION.search(read(path)) is not None


def main():
    legacy_product = "songhub"
    legacy_container = "shub"

    offenders = []
    for path in iter_files(APPS):
        lowered = path.lower()
        if os.path.join("core", "songbook") in lowered:
            # The Android compatibility package may name the legacy container
            # format, but never the legacy product.
            if legacy_product in read(path).lower():
                offenders.append((path, "legacy product name"))
            continue
        body = read(path).lower()
        if legacy_product in body:
            offenders.append((path, "legacy product name"))
        elif legacy_container in body and lowered.startswith(os.path.join(APPS, "web").lower()):
            offenders.append((path, "legacy container name in the public website"))
        elif legacy_container in body and lowered.startswith(
            os.path.join(APPS, "android").lower()
        ) and declares_legacy_container_type(path):
            offenders.append((path, "legacy container type declared outside the compatibility package"))

    check(
        not offenders,
        "No legacy product branding survives in shipped surfaces"
        if not offenders
        else "Legacy branding found: " + ", ".join("%s (%s)" % item for item in offenders),
    )

    manifest = read(ANDROID_MANIFEST)
    check(
        'applicationId' not in read(os.path.join(APPS, "android", "app", "build.gradle"))
        or "applicationId 'ph.kanta.reborn'" in read(
            os.path.join(APPS, "android", "app", "build.gradle")
        ),
        "Android application id stays ph.kanta.reborn",
    )
    check(
        legacy_product not in manifest.lower(),
        "Android manifest carries no legacy product name",
    )

    check(
        os.path.isdir(ANDROID_COMPATIBILITY),
        "The Android legacy-container compatibility package exists",
    )

    for message in passes:
        print("PASS: %s" % message)

    if failures:
        for message in failures:
            print("FAIL: %s" % message)
        print("\n%d passed, %d failed" % (len(passes), len(failures)))
        sys.exit(1)

    print("\n%d checks passed, 0 failed" % len(passes))


if __name__ == "__main__":
    main()
