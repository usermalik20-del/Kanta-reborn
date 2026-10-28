#!/usr/bin/env python3
"""WCAG contrast checker for design tokens."""

import json
import sys

def hex_to_rgb(hex_color):
    hex_color = hex_color.lstrip('#')
    if len(hex_color) == 8:
        hex_color = hex_color[2:]
    elif len(hex_color) == 4:
        hex_color = hex_color[1:]
    elif len(hex_color) == 3:
        hex_color = ''.join(c*2 for c in hex_color)
    r = int(hex_color[0:2], 16)
    g = int(hex_color[2:4], 16)
    b = int(hex_color[4:6], 16)
    return r, g, b

def relative_luminance(r, g, b):
    def channel(c):
        c = c / 255.0
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)

def contrast_ratio(hex1, hex2):
    r1, g1, b1 = hex_to_rgb(hex1)
    r2, g2, b2 = hex_to_rgb(hex2)
    l1 = relative_luminance(r1, g1, b1)
    l2 = relative_luminance(r2, g2, b2)
    lighter = max(l1, l2)
    darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)

def check_pairs(tokens):
    colors = tokens['colors']
    failures = []
    warnings = []
    
    for pair in tokens['pairs']:
        text_color = colors.get(pair['text'])
        bg_color = colors.get(pair['background'])
        
        if not text_color or not bg_color:
            warnings.append(f"Missing color for pair: {pair['text']} / {pair['background']}")
            continue
        
        ratio = contrast_ratio(text_color, bg_color)
        desc = pair.get('description', f"{pair['text']} on {pair['background']}")
        
        if ratio < 3.0:
            failures.append(f"FAIL: {desc} — ratio {ratio:.2f}:1 (needs 3:1 for large text, 4.5:1 for body)")
        elif ratio < 4.5:
            warnings.append(f"WARN: {desc} — ratio {ratio:.2f}:1 (meets 3:1 for large text, below 4.5:1 for body)")
        else:
            print(f"  PASS: {desc} — ratio {ratio:.2f}:1")
    
    return failures, warnings

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 contrast.py tokens.json")
        sys.exit(1)
    
    with open(sys.argv[1]) as f:
        tokens = json.load(f)
    
    print(f"Checking {len(tokens['pairs'])} color pairs for WCAG AA compliance...\n")
    
    failures, warnings = check_pairs(tokens)
    
    print()
    if warnings:
        print("Warnings:")
        for w in warnings:
            print(f"  {w}")
        print()
    
    if failures:
        print("FAILURES:")
        for f in failures:
            print(f"  {f}")
        print(f"\nTotal: {len(failures)} failures, {len(warnings)} warnings")
        sys.exit(1)
    else:
        print(f"All {len(tokens['pairs'])} pairs pass WCAG AA!")
        if warnings:
            print(f"({len(warnings)} warnings for large-text-only pairs)")
        sys.exit(0)

if __name__ == '__main__':
    main()
