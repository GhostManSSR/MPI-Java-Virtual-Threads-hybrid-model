#!/usr/bin/env python3
import csv
import statistics
from pathlib import Path


def read_rows(path: Path):
    with path.open(newline='') as fh:
        return list(csv.DictReader(fh))


def main():
    root = Path(__file__).resolve().parent.parent
    csv_path = root / 'experiments' / 'results' / 'benchmark-results.csv'
    if not csv_path.exists():
        print(f'No benchmark file at {csv_path}')
        return

    rows = read_rows(csv_path)
    print(f'rows={len(rows)}')
    if rows:
        durations = [float(r['durationMs']) for r in rows]
        print(f'durationMs mean={statistics.mean(durations):.3f}; median={statistics.median(durations):.3f}')


if __name__ == '__main__':
    main()
