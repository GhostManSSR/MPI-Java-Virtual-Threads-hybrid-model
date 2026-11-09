#!/usr/bin/env python3
import csv
import statistics
from pathlib import Path


def summarize_csv(csv_path: str):
    with open(csv_path, newline='') as fh:
        reader = csv.DictReader(fh)
        rows = list(reader)

    if not rows:
        return {"rows": 0, "summary": {}}

    metrics = {}
    for key in rows[0].keys():
        values = [float(r[key]) for r in rows if r.get(key) not in (None, '')]
        if values:
            metrics[key] = {
                "min": min(values),
                "max": max(values),
                "mean": statistics.mean(values),
                "median": statistics.median(values),
            }

    return {"rows": len(rows), "summary": metrics}


def main():
    root = Path(__file__).resolve().parent.parent
    results_dir = root / 'experiments' / 'results'
    candidates = sorted(results_dir.glob('*.csv'))

    if not candidates:
        print('No CSV results present in experiments/results')
        return

    for csv_file in candidates:
        payload = summarize_csv(str(csv_file))
        print(f'[{csv_file.name}] {payload["rows"]} rows')
        for name, values in payload['summary'].items():
            print(f'  {name}: min={values["min"]:.3f}, mean={values["mean"]:.3f}, median={values["median"]:.3f}, max={values["max"]:.3f}')


if __name__ == '__main__':
    main()
