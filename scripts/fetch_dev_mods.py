#!/usr/bin/env python3
"""Fetch the cross-mod smoke-test mods into run/mods/.

PF's cross-mod content (crush recipes, mod_loaded-gated slime variants and their
milk fluids) is inert - and untestable - unless the provider mod is present. CI
can't install them (heavy, version-churning, and it would cut against the
no-hard-mod-dependency rule), so the smoke test is a manual runClient /
runGameTestServer pass. This script just drops the mods into the dev run's
gitignored mods folder; it does NOT make the build depend on them (same posture
as the Jade drop-in in docs/dev_setup.md). The environment-driven GameTest
(crossModVariantPresenceMatchesModLoadedConditions) asserts that exactly the
variants whose providers are present actually load.

Modrinth targets are queried live for the latest 1.21.1 / NeoForge build (no
fragile hardcoded file ids) with SHA-1 verification; CurseForge-only targets go
through the anonymous website API (no hash on that endpoint). Skips files
already present. Re-runnable.

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

# Sources for the mod_loaded-gated content that needs an in-dev smoke test: the
# v1.3 crusher mods (+ the ATO dust layer), and any cross-mod variant provider
# whose variant shipped without a Gradle runtimeOnly dep (JEI and Refined
# Storage come from build.gradle instead - do NOT add them here, that would
# double-load and trip NeoForge's duplicate-modid check).
#
# GOTCHA - verify the modid before adding a Modrinth slug: Modrinth hosts
# third-party repacks of some CurseForge-only mods whose modid is rebranded
# with an `mr_` prefix. The "all-the-ores" Modrinth jar ships modid
# `mr_all_theores`, NOT `alltheores` - it loaded fine but never satisfied a
# single `mod_loaded: alltheores` condition, so the ATO-gated variants and
# crush recipes were silently untested in dev (caught 2026-06-06). Mods like
# that go in CURSEFORGE_TARGETS instead.
TARGETS = {
    "mekanism": "Mekanism (Enrichment Chamber)",
    "immersiveengineering": "Immersive Engineering (Crusher)",
    "enderio": "EnderIO (SAG Mill)",
    "powah": "Powah (crystal variants; uraninite #146)",
}

# CurseForge-only providers (no genuine Modrinth distribution). Fetched via the
# anonymous website API (www.curseforge.com/api/v1) - the official Core API
# needs an API key, but the website file list + download redirect do not.
# Values: (project id, label). Newest 1.21.1 NeoForge file wins.
CURSEFORGE_TARGETS = {
    405593: "AllTheOres (dust + smelt-back fallback; real modid `alltheores`)",
    248020: "Flux Networks (flux_dust variant, #145)",
}
CF_API = "https://www.curseforge.com/api/v1"
# The CF website API rejects non-browser User-Agents at the load balancer.
CF_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"

ROOT = Path(__file__).resolve().parent.parent


def _get_json(url: str) -> list | dict:
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
    # SHA-1 here is a download-integrity check, not a security primitive
    # (usedforsecurity=False keeps it quiet under FIPS builds). Modrinth always
    # ships the hash today; if it ever omits it we accept an existing file as-is.
    sha1 = file_meta.get("hashes", {}).get("sha1")
    if dest.exists():
        if sha1 and hashlib.sha1(dest.read_bytes(), usedforsecurity=False).hexdigest() != sha1:
            print(f"      present but SHA-1 mismatch, re-downloading: {dest.name}")
        else:
            return "skip (already present)"
    req = urllib.request.Request(file_meta["url"], headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=180) as resp:
        data = resp.read()
    if sha1 and hashlib.sha1(data, usedforsecurity=False).hexdigest() != sha1:
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


def _cf_get_json(url: str) -> dict:
    req = urllib.request.Request(url, headers={"User-Agent": CF_UA})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def fetch_curseforge(project_id: int, label: str, dest_dir: Path) -> None:
    """Newest 1.21.1 NeoForge file of a CurseForge project, via the anonymous
    website API. No SHA available on this endpoint, so an existing file with the
    same name is trusted as-is (delete it to force a re-download)."""
    listing = _cf_get_json(
        f"{CF_API}/mods/{project_id}/files?pageIndex=0&pageSize=50&sort=dateCreated&sortDescending=true")
    candidates = [f for f in listing.get("data", [])
                  if GAME_VERSION in f.get("gameVersions", [])
                  and "NeoForge" in f.get("gameVersions", [])]
    if not candidates:
        print(f"  {label}: NO {GAME_VERSION}/NeoForge file on CurseForge - download manually.")
        return
    fmeta = max(candidates, key=lambda f: f["id"])
    dest = dest_dir / fmeta["fileName"]
    if dest.exists():
        print(f"  {label}: {fmeta['fileName']}  [skip (already present)]")
        return
    req = urllib.request.Request(
        f"{CF_API}/mods/{project_id}/files/{fmeta['id']}/download", headers={"User-Agent": CF_UA})
    with urllib.request.urlopen(req, timeout=180) as resp:
        data = resp.read()
    dest.write_bytes(data)
    print(f"  {label}: {fmeta['fileName']}  [downloaded ({len(data) // 1024} KB)]")


def main() -> int:
    parser = argparse.ArgumentParser(description="Fetch cross-mod smoke-test mods into run/mods/.")
    parser.add_argument("--dir", type=Path, default=ROOT / "run" / "mods",
                        help="mods folder to populate (default: <repo>/run/mods)")
    args = parser.parse_args()

    dest_dir = args.dir
    dest_dir.mkdir(parents=True, exist_ok=True)
    print(f"Fetching {GAME_VERSION}/{LOADER} smoke-test mods into {dest_dir}\n")

    seen: set[str] = set()
    failures = 0
    for slug, label in TARGETS.items():
        try:
            fetch(slug, label, dest_dir, seen)
        except Exception as exc:  # noqa: BLE001 - report and continue to the next mod
            failures += 1
            print(f"  {label}: FAILED - {exc}")
    for project_id, label in CURSEFORGE_TARGETS.items():
        try:
            fetch_curseforge(project_id, label, dest_dir)
        except Exception as exc:  # noqa: BLE001 - report and continue to the next mod
            failures += 1
            print(f"  {label}: FAILED - {exc}")

    print(f"\nDone. {dest_dir} now holds the smoke-test mods; run `.\\gradlew runClient` to smoke-test.")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
