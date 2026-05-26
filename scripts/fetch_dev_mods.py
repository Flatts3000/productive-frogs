#!/usr/bin/env python3
"""Fetch the crusher mods needed for the v1.3 crush-recipe smoke test into run/mods/.

The crush recipes (data/productivefrogs/recipe/<modid>/) are mod_loaded-gated, so
they are inert - and untestable - unless an actual crusher mod is present. CI can't
install them (heavy, version-churning, and it would cut against the no-hard-mod-
dependency rule), so the smoke test is a manual runClient pass. This script just
drops the mods into the dev run's gitignored mods folder; it does NOT make the
build depend on them (same posture as the Jade drop-in in docs/dev_setup.md).

It queries Modrinth live for each mod's latest 1.21.1 / NeoForge build (no fragile
hardcoded file ids), verifies the SHA-1, and skips files already present. Re-runnable.

    python scripts/fetch_dev_mods.py            # fetch into <repo>/run/mods
    python scripts/fetch_dev_mods.py --dir PATH  # fetch into a different mods folder
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

API = "https://api.modrinth.com/v2"
# Modrinth requires a descriptive, contactable User-Agent.
UA = "Flatts3000/productive-frogs devtools (crush smoke-test mod fetcher)"
GAME_VERSION = "1.21.1"
LOADER = "neoforge"

# Modrinth slugs for the crushers the v1.3 recipes target, plus the ATO dust layer.
TARGETS = {
    "mekanism": "Mekanism (Enrichment Chamber)",
    "immersiveengineering": "Immersive Engineering (Crusher)",
    "enderio": "EnderIO (SAG Mill)",
    "all-the-ores": "AllTheOres (dust + smelt-back fallback)",
}

ROOT = Path(__file__).resolve().parent.parent


def _get_json(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def latest_version(project: str) -> dict | None:
    """Newest version of a project (by slug or id) for our game version + loader."""
    query = urllib.parse.urlencode({
        "loaders": json.dumps([LOADER]),
        "game_versions": json.dumps([GAME_VERSION]),
    })
    versions = _get_json(f"{API}/project/{project}/version?{query}")
    if not versions:
        return None
    # The list is normally newest-first, but sort explicitly so we never depend on it.
    versions.sort(key=lambda v: v.get("date_published", ""), reverse=True)
    return versions[0]


def primary_file(version: dict) -> dict:
    files = [f for f in version["files"] if f.get("primary")] or version["files"]
    return files[0]


def download(file_meta: dict, dest_dir: Path) -> str:
    dest = dest_dir / file_meta["filename"]
    sha1 = file_meta.get("hashes", {}).get("sha1")
    if dest.exists():
        if sha1 and hashlib.sha1(dest.read_bytes()).hexdigest() != sha1:
            print(f"      present but SHA-1 mismatch, re-downloading: {dest.name}")
        else:
            return "skip (already present)"
    req = urllib.request.Request(file_meta["url"], headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=180) as resp:
        data = resp.read()
    if sha1 and hashlib.sha1(data).hexdigest() != sha1:
        raise RuntimeError(f"SHA-1 mismatch downloading {file_meta['filename']}")
    dest.write_bytes(data)
    return f"downloaded ({len(data) // 1024} KB)"


def fetch(project: str, label: str, dest_dir: Path, seen: set[str]) -> None:
    if project in seen:
        return
    seen.add(project)
    version = latest_version(project)
    if version is None:
        print(f"  {label}: NO {GAME_VERSION}/{LOADER} build on Modrinth - download manually.")
        return
    fmeta = primary_file(version)
    status = download(fmeta, dest_dir)
    print(f"  {label}: {version['version_number']} -> {fmeta['filename']}  [{status}]")
    # Pull any required dependencies (none of the four report any today, but a
    # version bump could introduce one - resolve transitively so the run still loads).
    for dep in version.get("dependencies", []):
        if dep.get("dependency_type") == "required" and dep.get("project_id"):
            fetch(dep["project_id"], f"  (dep of {project})", dest_dir, seen)


def main() -> int:
    parser = argparse.ArgumentParser(description="Fetch crush-recipe smoke-test mods into run/mods/.")
    parser.add_argument("--dir", type=Path, default=ROOT / "run" / "mods",
                        help="mods folder to populate (default: <repo>/run/mods)")
    args = parser.parse_args()

    dest_dir = args.dir
    dest_dir.mkdir(parents=True, exist_ok=True)
    print(f"Fetching {GAME_VERSION}/{LOADER} crush-test mods into {dest_dir}\n")

    seen: set[str] = set()
    failures = 0
    for slug, label in TARGETS.items():
        try:
            fetch(slug, label, dest_dir, seen)
        except Exception as exc:  # noqa: BLE001 - report and continue to the next mod
            failures += 1
            print(f"  {label}: FAILED - {exc}")

    print(f"\nDone. {dest_dir} now holds the crusher mods; run `.\\gradlew runClient` to smoke-test.")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
