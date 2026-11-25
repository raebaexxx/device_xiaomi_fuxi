#!/usr/bin/env python3
"""Shared helpers for the Lunaris RU translation tooling."""

from __future__ import annotations

import json
import re
from collections import OrderedDict
from pathlib import Path

TRANSLATIONS_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = TRANSLATIONS_DIR.parents[3]

# Resource files that never contain user-facing translatable text.
SKIP_FILE_RE = re.compile(
    r"^(donottranslate|attrs|colors|dimens|styles|themes|bools|integers|ids|paths|public|symbols|config)",
    re.IGNORECASE,
)

ENTRY_RE = re.compile(
    r"<(?P<tag>string|plurals|string-array|array|item)\b(?P<attrs>[^>]*)>"
    r"(?P<body>.*?)</(?P=tag)>",
    re.DOTALL,
)
ITEM_RE = re.compile(r"<item\b(?P<attrs>[^>]*)>(?P<body>.*?)</item>", re.DOTALL)
ATTR_RE = re.compile(r'([\w:.-]+)\s*=\s*"([^"]*)"')
COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)
LETTER_RE = re.compile(r"[^\W\d_]", re.UNICODE)

KIND_STRING = "string"
KIND_PLURALS = "plurals"
KIND_ARRAY = "string-array"


def load_config(path: Path | None = None) -> dict:
    cfg_path = path or TRANSLATIONS_DIR / "config.json"
    with open(cfg_path, encoding="utf-8") as fh:
        return json.load(fh)


def parse_attrs(raw: str) -> dict:
    return {m.group(1): m.group(2) for m in ATTR_RE.finditer(raw)}


def strip_comments(text: str) -> str:
    return COMMENT_RE.sub("", text)


def parse_entries(path: Path) -> list[dict]:
    """Parse a values XML file into an ordered list of resource entries."""
    text = strip_comments(path.read_text(encoding="utf-8", errors="replace"))
    entries: list[dict] = []
    for match in ENTRY_RE.finditer(text):
        tag = match.group("tag")
        attrs = parse_attrs(match.group("attrs"))
        body = match.group("body")
        name = attrs.get("name")
        if not name:
            continue
        if tag == "item":
            if attrs.get("type") != "string":
                continue
            tag = "string"
        if tag == "string":
            entries.append(
                {
                    "kind": KIND_STRING,
                    "tag": "string",
                    "name": name,
                    "attrs": attrs,
                    "text": body,
                }
            )
        elif tag == "plurals":
            items = OrderedDict()
            for item in ITEM_RE.finditer(body):
                iattrs = parse_attrs(item.group("attrs"))
                quantity = iattrs.get("quantity")
                if quantity:
                    items[quantity] = item.group("body")
            entries.append(
                {
                    "kind": KIND_PLURALS,
                    "tag": "plurals",
                    "name": name,
                    "attrs": attrs,
                    "items": items,
                }
            )
        elif tag in ("string-array", "array"):
            items = [m.group("body") for m in ITEM_RE.finditer(body)]
            entries.append(
                {
                    "kind": KIND_ARRAY,
                    "tag": tag,
                    "name": name,
                    "attrs": attrs,
                    "items": items,
                }
            )
    return entries


def has_letters(value: str) -> bool:
    plain = re.sub(r"<[^>]+>", "", value)
    return bool(LETTER_RE.search(plain))


def is_translatable(entry: dict) -> bool:
    if entry["attrs"].get("translatable", "true").lower() == "false":
        return False
    if entry["kind"] == KIND_STRING:
        text = entry["text"].strip()
        if not text:
            return False
        if text.startswith("@") or text.startswith("?"):
            return False
        return has_letters(text)
    if entry["kind"] == KIND_PLURALS:
        return any(has_letters(v) for v in entry["items"].values())
    if entry["kind"] == KIND_ARRAY:
        if not entry["items"]:
            return False
        # Arrays of references / numbers are configuration, not UI text.
        if all(
            it.strip().startswith("@") or it.strip().startswith("?") or not has_letters(it)
            for it in entry["items"]
        ):
            return False
        return True
    return False


def values_files(resdir: Path) -> list[Path]:
    values = resdir / "values"
    if not values.is_dir():
        return []
    return sorted(
        p
        for p in values.glob("*.xml")
        if p.is_file() and not SKIP_FILE_RE.match(p.name)
    )


def ru_dirs(resdir: Path) -> list[Path]:
    return sorted(
        p
        for p in resdir.glob("values*")
        if p.is_dir() and re.search(r"(^|-)ru($|-)|b\+ru", p.name)
    )


def package_entries(package: dict, include_translated: bool = True) -> list[dict]:
    """Return all source entries of a package with path metadata."""
    result = []
    source = REPO_ROOT / package["source"]
    for resdir_name in package["resource_dirs"]:
        resdir = source / resdir_name
        for path in values_files(resdir):
            for entry in parse_entries(path):
                if not is_translatable(entry):
                    continue
                result.append(
                    {
                        "package": package["id"],
                        "source": package["source"],
                        "resdir": resdir_name,
                        "file": path.name,
                        "entry": entry,
                    }
                )
    return result


def existing_ru_keys(package: dict) -> set[tuple[str, str]]:
    """Collect (kind, name) pairs already present in any values-ru* dir."""
    keys: set[tuple[str, str]] = set()
    source = REPO_ROOT / package["source"]
    for resdir_name in package["resource_dirs"]:
        resdir = source / resdir_name
        for ru_dir in ru_dirs(resdir):
            for path in sorted(ru_dir.glob("*.xml")):
                for entry in parse_entries(path):
                    keys.add((entry["kind"], entry["name"]))
    return keys


def missing_entries(package: dict) -> list[dict]:
    existing = existing_ru_keys(package)
    return [
        item
        for item in package_entries(package)
        if (item["entry"]["kind"], item["entry"]["name"]) not in existing
    ]


def data_file_path(data_dir: Path, package: dict, resdir: str, source_file: str) -> Path:
    slug = resdir.replace("/", "__")
    return data_dir / package["id"] / slug / (Path(source_file).stem + ".json")


def overlay_file_path(overlay_dir: Path, package: dict, resdir: str, source_file: str) -> Path:
    return overlay_dir / package["source"] / resdir / "values-ru" / source_file


def load_data_file(path: Path) -> dict:
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    for key in ("strings", "plurals", "arrays"):
        data.setdefault(key, {})
    return data


def iter_data_files(data_dir: Path):
    if not data_dir.is_dir():
        return
    for pkg_dir in sorted(data_dir.iterdir()):
        if not pkg_dir.is_dir():
            continue
        for resdir_dir in sorted(pkg_dir.iterdir()):
            if not resdir_dir.is_dir():
                continue
            for path in sorted(resdir_dir.glob("*.json")):
                yield path
