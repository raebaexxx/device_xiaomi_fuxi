#!/usr/bin/env python3
"""Print a TSV worklist for one source file (already translated entries excluded)."""

from __future__ import annotations

import argparse
import json
import sys

from common import (
    REPO_ROOT,
    iter_data_files,
    load_config,
    missing_entries,
    source_name_for_data,
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", required=True)
    parser.add_argument("--resdir", required=True)
    parser.add_argument("--file", required=True)
    parser.add_argument("--start", type=int, default=1, help="1-based first row")
    parser.add_argument("--end", type=int, help="1-based last row (inclusive)")
    parser.add_argument("--count-only", action="store_true")
    args = parser.parse_args()

    config = load_config()
    package = next((p for p in config["packages"] if p["id"] == args.package), None)
    if not package:
        print(f"Unknown package: {args.package}", file=sys.stderr)
        return 2

    done: set[str] = set()
    data_dir = REPO_ROOT / config["data_dir"]
    for path in iter_data_files(data_dir):
        rel = path.relative_to(data_dir)
        if len(rel.parts) != 3:
            continue
        pkg_id, resdir_slug, filename = rel.parts
        if pkg_id != args.package or resdir_slug.replace("__", "/") != args.resdir:
            continue
        if source_name_for_data(filename) != args.file:
            continue
        data = json.load(open(path, encoding="utf-8"))
        for section in ("strings", "plurals", "arrays"):
            done.update(data.get(section, {}).keys())

    rows = [
        item
        for item in missing_entries(package)
        if item["resdir"] == args.resdir
        and item["file"] == args.file
        and item["entry"]["name"] not in done
    ]

    start = max(args.start, 1)
    end = args.end if args.end is not None else len(rows)
    selected = rows[start - 1 : end]

    if args.count_only:
        print(len(rows))
        return 0

    print(f"# {args.package} {args.resdir}/{args.file}: {len(selected)} of {len(rows)} untranslated rows")
    for idx, item in enumerate(selected, start=start):
        entry = item["entry"]
        text = entry["text"] if entry["kind"] == "string" else json.dumps(entry["items"], ensure_ascii=False)
        text = text.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "").replace("\n", "\\n")
        print(f"{idx}\t{entry['kind']}\t{entry['name']}\t{text}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())