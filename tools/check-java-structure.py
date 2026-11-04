#!/usr/bin/env python3
"""Fail closed when a Java method is declared inside another method.

The Android build cannot run on this device (no working JVM), so structural
Java errors like a method body nested inside a sibling method's body would
only surface in CI. This linter tracks brace contexts per line (string/char/
comment aware) and flags method-signature-shaped lines that open inside a
method context. Inner class members and anonymous-class bodies are handled by
classifying each '{' as METHOD, CLASS or PLAIN from the opening line.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

METHOD_RE = re.compile(
    r'^\s*(?:public|private|protected|static|final|synchronized|abstract|native|'
    r'default|strictfp|\bvoid\b|\bint\b|\blong\b|\bfloat\b|\bdouble\b|\bboolean\b|'
    r'\bchar\b|\bbyte\b|\bshort\b|\bString\b|\bList\b|\bMap\b|\bSet\b|\bFile\b|'
    r'[A-Z][A-Za-z0-9_<>?, \[\]]*)\s+[A-Za-z_][A-Za-z0-9_]*\s*\([^;]*\)\s*\{'
    r'|^\s*(?:public|private|protected|static|final|synchronized|abstract|native|'
    r'default|strictfp|\bvoid\b|\bint\b|\blong\b|\bfloat\b|\bdouble\b|\bboolean\b|'
    r'\bchar\b|\bbyte\b|\bshort\b|\bString\b|[A-Z][A-Za-z0-9_<>?, \[\]]*)\s+'
    r'[A-Za-z_][A-Za-z0-9_]*\s*\([^;]*\)\s*th\b'
)

CLASS_RE = re.compile(r'^\s*(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+)*(?:class|interface|enum|record)\b.*\{')


class JavaStructure:
    def __init__(self, path: str) -> None:
        self.path = path
        self.violations: list[str] = []

    def run(self) -> list[str]:
        lines = Path(self.path).read_text(encoding="utf-8").splitlines()
        contexts: list[str] = []  # stack of 'method' | 'class' | 'plain'
        in_string = in_char = in_block = False
        esc = False
        for lineno, line in enumerate(lines, 1):
            stripped = line.strip()
            if not stripped or stripped.startswith("//") or stripped.startswith("*"):
                continue
            prev_top = contexts[-1] if contexts else None
            i = 0
            opens = 0
            # Classify each '{' on this line using the line's text shape.
            is_method_line = bool(METHOD_RE.match(line)) and not stripped.startswith("@")
            is_class_line = bool(CLASS_RE.match(line))
            while i < len(line):
                ch = line[i]
                nxt = line[i + 1] if i + 1 < len(line) else ""
                if in_block:
                    if ch == "*" and nxt == "/":
                        in_block = False
                        i += 2
                        continue
                    i += 1
                    continue
                if ch == "/" and nxt == "/":
                    break
                if ch == "/" and nxt == "*":
                    in_block = True
                    i += 2
                    continue
                if in_string:
                    if esc:
                        esc = False
                    elif ch == "\\":
                        esc = True
                    elif ch == '"':
                        in_string = False
                    i += 1
                    continue
                if in_char:
                    if esc:
                        esc = False
                    elif ch == "\\":
                        esc = True
                    elif ch == "'":
                        in_char = False
                    i += 1
                    continue
                if ch == '"':
                    in_string = True
                    i += 1
                    continue
                if ch == "'":
                    in_char = True
                    i += 1
                    continue
                if ch == "{":
                    opens += 1
                    if opens == 1:
                        ctx = "class" if is_class_line else ("method" if is_method_line else "plain")
                        contexts.append(ctx)
                    else:
                        contexts.append("plain")
                elif ch == "}":
                    if contexts:
                        contexts.pop()
                i += 1
            # A method signature that opens inside an enclosing method body is invalid
            # Java. prev_top is the brace context BEFORE this line's own '{', so a
            # genuine signature is never self-flagged; anonymous-class bodies open as
            # 'plain' and allow legal overrides inside.
            if is_method_line and opens and prev_top == "method":
                self.violations.append(f"{self.path}:{lineno}: method declared inside a method body")
        return self.violations


def main(argv: list[str]) -> int:
    roots = argv[1:] or ["apps/android/app/src/main/java", "apps/android/app/src/test/java"]
    files: list[str] = []
    for root in roots:
        files.extend(str(p) for p in Path(root).rglob("*.java"))
    bad = 0
    for f in sorted(files):
        for violation in JavaStructure(f).run():
            print(violation)
            bad += 1
    if bad:
        print(f"Java structure check: {bad} violation(s)")
        return 1
    print(f"Java structure check: {len(files)} files, no nested method declarations")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))