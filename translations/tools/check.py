#!/usr/bin/env python3
"""Validate RU translation data: coverage, XML, placeholders, plurals, arrays."""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path
from xml.etree import ElementTree

from common import (
    KIND_ARRAY,
    KIND_PLURALS,
    KIND_STRING,
    REPO_ROOT,
    iter_data_files,
    load_config,
    load_data_file,
    missing_entries,
    parse_entries,
    source_name_for_data,
)

PLACEHOLDER_RE = re.compile(r"(?<!\d)%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[douxXeEfFgGaAcsbBShH%]")
XLIFF_RE = re.compile(r"<xliff:g\b([^>]*)>")
TAG_RE = re.compile(r"</?([a-zA-Z][\w:.-]*)")


def placeholders(text: str) -> Counter:
    # Protect literal percent pairs before scanning for format specifiers.
    return Counter(PLACEHOLDER_RE.findall(text.replace("%%", "\x00")))


def xliff_ids(text: str) -> list[str]:
    ids = []
    for match in XLIFF_RE.finditer(text):
        id_match = re.search(r'id="([^"]*)"', match.group(1))
        ids.append(id_match.group(1) if id_match else "")
    return ids


def tags(text: str) -> Counter:
    return Counter(t for t in TAG_RE.findall(text) if t != "xliff:g")


def check_string(source: str, translation: str, where: str, errors: list[str], warnings: list[str], formatted: bool = True):
    if not translation.strip():
        errors.append(f"{where}: empty translation")
        return
    if formatted:
        src_ph = placeholders(source)
        dst_ph = placeholders(translation)
        if src_ph != dst_ph:
            errors.append(f"{where}: placeholder mismatch {dict(src_ph)} -> {dict(dst_ph)}")
    src_ids = xliff_ids(source)
    dst_ids = xliff_ids(translation)
    if src_ids != dst_ids:
        errors.append(f"{where}: xliff:g ids mismatch {src_ids} -> {dst_ids}")
    if tags(source) != tags(translation):
        warnings.append(
            f"{where}: inline tag mismatch {dict(tags(source))} -> {dict(tags(translation))}"
        )
    if source != source.strip() and translation == translation.strip():
        warnings.append(f"{where}: source has surrounding spaces, translation does not")


def xml_ok(text: str) -> bool:
    wrapped = f'<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">{text}</resources>'
    try:
        ElementTree.fromstring(wrapped)
        return True
    except ElementTree.ParseError:
        return False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--strict", action="store_true", help="treat warnings as errors")
    parser.add_argument("--package", help="only check this package id")
    parser.add_argument("--only", help="only check data files whose path contains this substring")
    args = parser.parse_args()

    config = load_config()
    packages = {p["id"]: p for p in config["packages"]}
    data_dir = REPO_ROOT / config["data_dir"]

    errors: list[str] = []
    warnings: list[str] = []
    stats: dict[str, dict] = defaultdict(lambda: {"strings": 0, "plurals": 0, "arrays": 0})
    translated_names: dict[str, set] = defaultdict(set)

    data_files = list(iter_data_files(data_dir))
    if not data_files:
        print("No translation data files found.")
        return 0

    for data_path in data_files:
        if args.only and args.only not in str(data_path):
            continue
        rel = data_path.relative_to(data_dir)
        if len(rel.parts) != 3:
            errors.append(f"{data_path}: unexpected data path")
            continue
        pkg_id, resdir_slug, filename = rel.parts
        if args.package and pkg_id != args.package:
            continue
        package = packages.get(pkg_id)
        if not package:
            errors.append(f"{data_path}: unknown package id")
            continue
        resdir = resdir_slug.replace("__", "/")
        source_file = REPO_ROOT / package["source"] / resdir / "values" / source_name_for_data(filename)
        if not source_file.exists():
            errors.append(f"{data_path}: source file not found: {source_file}")
            continue

        data = load_data_file(data_path)
        source_entries = {e["name"]: e for e in parse_entries(source_file)}

        for name, translation in data["strings"].items():
            entry = source_entries.get(name)
            if not entry:
                errors.append(f"{data_path}: string '{name}' not found in source")
            elif entry["kind"] != KIND_STRING:
                errors.append(f"{data_path}: '{name}' is {entry['kind']} in source, not string")
            else:
                check_string(
                    entry["text"],
                    translation,
                    f"{pkg_id}/{resdir}/{filename}:{name}",
                    errors,
                    warnings,
                    formatted=entry["attrs"].get("formatted") != "false",
                )
                stats[pkg_id]["strings"] += 1
                translated_names[pkg_id].add((KIND_STRING, name))

        for name, quantities in data["plurals"].items():
            entry = source_entries.get(name)
            if not entry:
                errors.append(f"{data_path}: plurals '{name}' not found in source")
                continue
            if entry["kind"] != KIND_PLURALS:
                errors.append(f"{data_path}: '{name}' is {entry['kind']} in source, not plurals")
                continue
            if not isinstance(quantities, dict):
                errors.append(f"{data_path}: plurals '{name}' must be an object")
                continue
            missing_q = {"one", "few", "many", "other"} - set(quantities)
            if missing_q:
                errors.append(f"{data_path}: plurals '{name}' missing quantities {sorted(missing_q)}")
            source_other = entry["items"].get("other", entry["items"].get("one", ""))
            for quantity, text in quantities.items():
                check_string(
                    source_other,
                    text,
                    f"{pkg_id}/{resdir}/{filename}:{name}[{quantity}]",
                    errors,
                    warnings,
                    formatted=entry["attrs"].get("formatted") != "false",
                )
            stats[pkg_id]["plurals"] += 1
            translated_names[pkg_id].add((KIND_PLURALS, name))

        for name, items in data["arrays"].items():
            entry = source_entries.get(name)
            if not entry:
                errors.append(f"{data_path}: array '{name}' not found in source")
                continue
            if entry["kind"] != KIND_ARRAY:
                errors.append(f"{data_path}: '{name}' is {entry['kind']} in source, not array")
                continue
            if not isinstance(items, list):
                errors.append(f"{data_path}: array '{name}' must be a list")
                continue
            if len(items) != len(entry["items"]):
                errors.append(
                    f"{data_path}: array '{name}' has {len(items)} item(s), source has {len(entry['items'])}"
                )
            for idx, text in enumerate(items):
                src = entry["items"][idx] if idx < len(entry["items"]) else ""
                check_string(
                    src,
                    text,
                    f"{pkg_id}/{resdir}/{filename}:{name}[{idx}]",
                    errors,
                    warnings,
                )
            stats[pkg_id]["arrays"] += 1
            translated_names[pkg_id].add((KIND_ARRAY, name))

        for section in ("strings", "plurals", "arrays"):
            for name, value in data[section].items():
                values = (
                    value
                    if isinstance(value, list)
                    else list(value.values())
                    if isinstance(value, dict)
                    else [value]
                )
                for text in values:
                    if not xml_ok(text):
                        errors.append(
                            f"{data_path}:{name}: value is not well-formed XML: {text!r}"
                        )

    print(f"{'Package':24} {'Missing':>8} {'Done':>7} {'Left':>7}")
    print("-" * 50)
    total_missing = total_done = 0
    for pkg_id, package in packages.items():
        if args.package and pkg_id != args.package:
            continue
        missing = missing_entries(package)
        if not missing:
            continue
        remaining = [
            m
            for m in missing
            if (m["entry"]["kind"], m["entry"]["name"]) not in translated_names[pkg_id]
        ]
        done = len(translated_names[pkg_id])
        left = len(remaining)
        total_missing += len(remaining)
        total_done += done
        print(f"{pkg_id:24} {len(remaining):>8} {done:>7} {left:>7}")
    print("-" * 50)
    print(f"{'TOTAL':24} {total_missing:>8} {total_done:>7} {max(total_missing - total_done, 0):>7}")
    print()
    print("Translated entries:", {k: dict(v) for k, v in sorted(stats.items())})

    if warnings:
        print(f"\nWarnings ({len(warnings)}):")
        for w in warnings[:80]:
            print(f"  ! {w}")
        if len(warnings) > 80:
            print(f"  ... and {len(warnings) - 80} more")

    if errors:
        print(f"\nErrors ({len(errors)}):", file=sys.stderr)
        for error in errors[:120]:
            print(f"  ✗ {error}", file=sys.stderr)
        if len(errors) > 120:
            print(f"  ... and {len(errors) - 120} more", file=sys.stderr)
        return 1

    if args.strict and warnings:
        return 1

    print("\nOK: no errors.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
