#!/usr/bin/env python3
"""Fail on new lint findings; retain exact, visible existing debt, not suppressions."""
import argparse
from collections import Counter
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BUDGET = ROOT / 'tools/android-lint-debt.json'

def normalized_file(raw):
    raw = raw.replace('\\', '/')
    for marker in ('/apps/android/', '/transformed/'):
        if marker in raw:
            return raw.split(marker, 1)[1]
    return raw

def identity(issue):
    locations = issue.findall('location')
    if not locations:
        raise ValueError('Lint finding has no location')
    return (issue.attrib['id'], normalized_file(locations[0].attrib['file']),
            issue.attrib['message'], issue.get('errorLine1', '').strip())

def findings(path):
    root = ET.parse(path).getroot()
    if root.tag != 'issues':
        raise ValueError('Expected Android lint XML issues report')
    counts = Counter()
    for issue in root.findall('issue'):
        if issue.get('severity') in ('Error', 'Fatal'):
            raise ValueError('Lint error: ' + issue.attrib['id'])
        if issue.get('severity') in ('Warning', 'Information'):
            counts[identity(issue)] += 1
    return counts

def check(report, budget):
    expected = Counter()
    for entry in budget['findings']:
        key = tuple(entry['identity'])
        if key in expected or entry['count'] < 1:
            raise ValueError('Invalid or duplicate debt entry')
        expected[key] = entry['count']
    actual = findings(report)
    new = actual - expected
    for key, count in sorted(new.items()):
        print(f'NEW lint finding ({count}): {key}', file=sys.stderr)
    if new:
        return False
    print(f'{report}: {sum(actual.values())} existing findings; no new lint debt')
    return True

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reports', type=Path, nargs='+')
    parser.add_argument('--budget', type=Path, default=DEFAULT_BUDGET)
    args = parser.parse_args()
    try:
        budget = json.loads(args.budget.read_text())
        results = [check(report, budget) for report in args.reports]
        return 0 if all(results) else 1
    except (OSError, ET.ParseError, ValueError, KeyError, TypeError) as error:
        print(f'Lint gate failed: {error}', file=sys.stderr)
        return 1

if __name__ == '__main__':
    sys.exit(main())
