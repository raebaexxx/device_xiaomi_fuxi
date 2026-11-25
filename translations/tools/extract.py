#!/usr/bin/env python3
"""Extract translatable entries that have no Russian translation yet."""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict

from common import (
    REPO_ROOT,
    TRANSLATIONS_DIR,
    load_config,
    missing_entries,
    package_entries,
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", help="only process this package id")
    parser.add_argument("--phase", type=int, help="only process packages of this phase")
    parser.add_argument(
        "--output",
        type=str,
        default=str(TRANSLATIONS_DIR / "worklist.json"),
        help="write the full worklist as JSON here",
    )
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()

    config = load_config()
    packages = config["packages"]
    if args.package:
        packages = [p for p in packages if p["id"] == args.package]
    if args.phase:
        packages = [p for p in packages if p.get("phase") == args.phase]
    if not packages:
        print("No packages matched the filter", file=sys.stderr)
        return 2

    worklist = []
    grand_total = 0
    per_package = []
    per_file: Counter = Counter()
    kinds: Counter = Counter()

    for package in packages:
        source = REPO_ROOT / package["source"]
        all_entries = package_entries(package)
        missing = missing_entries(package)
        grand_total += len(missing)
        by_file: Counter = Counter()
        for item in missing:
            entry = item["entry"]
            by_file[f'{item["resdir"]}/{item["file"]}'] += 1
            kinds[entry["kind"]] += 1
            worklist.append(
                {
                    "package": item["package"],
                    "source": item["source"],
                    "resdir": item["resdir"],
                    "file": item["file"],
                    "kind": entry["kind"],
                    "name": entry["name"],
                    "attrs": {k: v for k, v in entry["attrs"].items() if k != "name"},
                    "text": entry.get("text"),
                    "items": entry.get("items"),
                }
            )
        per_file.update(by_file)
        per_package.append(
            {
                "id": package["id"],
                "phase": package.get("phase"),
                "total": len(all_entries),
                "missing": len(missing),
                "files": len(by_file),
            }
        )

    with open(args.output, "w", encoding="utf-8") as fh:
        json.dump(
            {
                "packages": per_package,
                "total_missing": grand_total,
                "kinds": dict(kinds),
                "by_file": dict(sorted(per_file.items())),
                "entries": worklist,
            },
            fh,
            ensure_ascii=False,
            indent=2,
        )

    if not args.quiet:
        print(f"{'Package':24} {'Phase':>5} {'Total':>7} {'Missing':>8}")
        print("-" * 48)
        for row in per_package:
            print(
                f"{row['id']:24} {str(row['phase'] or '-'):>5} {row['total']:>7} {row['missing']:>8}"
            )
        print("-" * 48)
        print(f"{'TOTAL':24} {'':>5} {sum(r['total'] for r in per_package):>7} {grand_total:>8}")
        print()
        print("Missing by kind:", dict(kinds))
        print(f"Worklist: {args.output}")

        print("\nLargest files:")
        for name, count in per_file.most_common(15):
            print(f"  {count:5}  {name}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
