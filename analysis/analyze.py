
#!/usr/bin/env python3
import csv
import statistics
from pathlib import Path


def summarize_csv(csv_path: str):
    with open(csv_path, newline='', encoding='utf-8-sig') as fh:
        reader = csv.DictReader(fh)
        rows = list(reader)

    if not rows:
        return {"rows": 0, "summary": {}, "categorical": {}}

    metrics = {}
    categorical = {}

    for key in rows[0].keys():
        if not key:
            continue

        values = []
        categories = []

        for row in rows:
            raw = row.get(key)

            if raw is None or raw.strip() == '':
                continue

            try:
                value = float(raw)
                values.append(value)
            except (ValueError, TypeError):
                categories.append(raw.strip())

        print("DEBUG statistics:", statistics)
        print("DEBUG statistics type:", type(statistics))
        print("DEBUG statistics file:", getattr(statistics, "__file__", None))
        print("DEBUG statistics.mean:", getattr(statistics, "mean", "MISSING"))
        
        if values:
            metrics[key] = {
                "count": len(values),
                "min": min(values),
                "max": max(values),
                "mean": sum(values) / len(values),
                "median": sorted(values)[len(values) // 2]
                    if len(values) % 2
                    else (
                        sorted(values)[len(values) // 2 - 1]
                        + sorted(values)[len(values) // 2]
                    ) / 2,
                "std": (
                    sum((x - sum(values) / len(values)) ** 2 for x in values)
                    / (len(values) - 1)
                ) ** 0.5 if len(values) > 1 else 0.0,
            }

        if categories:
            categorical[key] = {
                "count": len(categories),
                "unique": sorted(set(categories)),
            }

    return {
        "rows": len(rows),
        "summary": metrics,
        "categorical": categorical,
    }


def main():
    root = Path(__file__).resolve().parent.parent
    results_dir = root / 'experiments' / 'results'
    candidates = sorted(results_dir.glob('*.csv'))

    if not candidates:
        print('No CSV results present in experiments/results')
        return

    for csv_file in candidates:
        payload = summarize_csv(str(csv_file))

        print('\n' + '=' * 75)
        print(f'FILE: {csv_file.name}')
        print(f'ROWS: {payload["rows"]}')
        print('=' * 75)

        print('\nNUMERIC METRICS')

        for name, values in payload['summary'].items():
            print(f'\n{name}:')
            print(f'  Count:  {values["count"]}')
            print(f'  Min:    {values["min"]:.3f}')
            print(f'  Max:    {values["max"]:.3f}')
            print(f'  Mean:   {values["mean"]:.3f}')
            print(f'  Median: {values["median"]:.3f}')
            print(f'  Std:    {values["std"]:.3f}')

        if payload['categorical']:
            print('\nCATEGORICAL METRICS')

            for name, values in payload['categorical'].items():
                print(f'\n{name}:')
                print(f'  Count: {values["count"]}')
                print(f'  Unique values: {values["unique"]}')


if __name__ == '__main__':
    main()
