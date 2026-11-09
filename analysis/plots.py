#!/usr/bin/env python3
"""Minimal plotting helper for the benchmark results CSV."""

import csv
from pathlib import Path


def main():
    root = Path(__file__).resolve().parent.parent
    csv_path = root / 'experiments' / 'results' / 'benchmark-results.csv'
    if not csv_path.exists():
        print('No CSV results available yet.')
        return

    with csv_path.open(newline='') as fh:
        rows = list(csv.DictReader(fh))

    print('Architecture,Scenario,Throughput')
    for row in rows:
        print(f"{row['architecture']},{row['benchmark']},{row['throughput']}")


if __name__ == '__main__':
    main()
