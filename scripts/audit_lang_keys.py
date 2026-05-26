#!/usr/bin/env python3
"""Audit per-variant lang-key coverage for Productive Frogs.

For every shipped SlimeVariant JSON, the mod's item/entity getName paths build
five per-variant translation keys (each backed by a title-case fallback in
Java). The fallback keeps datapack-only variants we don't ship readable, but
variants we DO ship should carry an explicit en_us.json entry so the display
name is ours to control (e.g. "Clay Slime", not the raw-id "Clay Ball Slime").

This script reports which of those explicit keys are present vs missing, grouped
by source mod, then emits paste-ready JSON blocks (one per family) to fill every
gap, plus a flat additions file.

Run:  python scripts/audit_lang_keys.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent
LANG = ROOT / "src/main/resources/assets/productivefrogs/lang/en_us.json"
VARIANT_DIR = ROOT / "src/main/resources/data/productivefrogs/productivefrogs/slime_variant"

BUCKET = "item.productivefrogs.slime_bucket."
ENTITY = "entity.productivefrogs.resource_slime."
EGG = "item.productivefrogs.resource_slime_spawn_egg."
FROG = "block.productivefrogs.configurable_froglight."
MILK = "item.productivefrogs.slime_milk_bucket."
FAMILY_ORDER = [ENTITY, BUCKET, EGG, FROG, MILK]

# Resource display name for variant ids where the existing bucket key can't
# supply it (cross-mod) AND plain title-case reads wrong. Title-case is correct
# for every metal (c:ingots/*), gem (c:gems/*), silicon, and the mysticalag
# essences, so they need no entry here.
NAME_OVERRIDES = {
    "blazing": "Blazing Crystal",   # powah:crystal_blazing
    "niotic": "Niotic Crystal",     # powah:crystal_niotic
    "nitro": "Nitro Crystal",       # powah:crystal_nitro
    "spirited": "Spirited Crystal",  # powah:crystal_spirited
    "pink_slime": "Pink Slime",     # industrialforegoing:pink_slime (resource IS "Pink Slime")
}


def title_case(variant_id: str) -> str:
    return " ".join(w.capitalize() for w in variant_id.split("_"))


def resource_name(variant_id: str, lang: dict[str, str]) -> str:
    """The resource's display name, minus any 'Bucket of'/'Slime' decoration.

    Overrides win first (so 'pink_slime' -> 'Pink Slime', not the over-stripped
    'Pink'); otherwise a base variant reuses its already-shipped bucket key so
    the name matches exactly (clay_ball -> 'Clay'); failing that, title-case.
    """
    if variant_id in NAME_OVERRIDES:
        return NAME_OVERRIDES[variant_id]
    existing = lang.get(BUCKET + variant_id)
    if existing:  # "Bucket of <Name> Slime" -> "<Name>"
        if not (existing.startswith("Bucket of ") and existing.endswith(" Slime")):
            raise ValueError(
                f"bucket key {BUCKET + variant_id!r} has an unexpected value "
                f"{existing!r}; expected 'Bucket of <Name> Slime'")
        return existing.removeprefix("Bucket of ").removesuffix(" Slime")
    return title_case(variant_id)


def values_for(name: str) -> dict[str, str]:
    """Build the five family values from a resource name, collapsing the
    redundant ' Slime Slime' when the resource name already ends in 'Slime'."""
    slime = name if name.endswith("Slime") else f"{name} Slime"
    return {
        ENTITY: slime,
        BUCKET: f"Bucket of {slime}",
        EGG: f"{name} Spawn Egg" if name.endswith("Slime") else f"{name} Slime Spawn Egg",
        FROG: f"{name} Froglight",
        MILK: f"Bucket of {name} Milk" if name.endswith("Slime") else f"Bucket of {name} Slime Milk",
    }


def variant_source(data: dict) -> str | None:
    for cond in data.get("neoforge:conditions", []):
        if cond.get("type") == "neoforge:mod_loaded":
            return cond.get("modid")
    return None


def main() -> int:
    if not VARIANT_DIR.is_dir():
        print(f"ERROR: variant dir not found: {VARIANT_DIR}", file=sys.stderr)
        return 1
    if not LANG.is_file():
        print(f"ERROR: lang file not found: {LANG}", file=sys.stderr)
        return 1

    lang = json.loads(LANG.read_text(encoding="utf-8"))
    keys = set(lang)

    variants: dict[str, str | None] = {}
    for path in sorted(VARIANT_DIR.glob("*.json")):
        variants[path.stem] = variant_source(json.loads(path.read_text(encoding="utf-8")))

    base = [v for v, s in variants.items() if s is None]
    xmod = [v for v, s in variants.items() if s is not None]
    print(f"Shipped variants: {len(variants)}  (base/vanilla: {len(base)}, cross-mod: {len(xmod)})\n")

    print("Per-family coverage:")
    for prefix in FAMILY_ORDER:
        missing = [v for v in variants if prefix + v not in keys]
        print(f"  {prefix}*  present {len(variants) - len(missing):>3}/{len(variants)}  missing {len(missing):>3}")

    missing_by_mod: dict[str, set[str]] = {}
    for v, src in variants.items():
        if any(prefix + v not in keys for prefix in FAMILY_ORDER):
            missing_by_mod.setdefault(src or "(base/vanilla)", set()).add(v)
    print("\nVariants with >=1 missing key, by source:")
    for src in sorted(missing_by_mod):
        print(f"  {src} ({len(missing_by_mod[src])}): {', '.join(sorted(missing_by_mod[src]))}")

    # Build additions. Order new keys alphabetically by variant id within family.
    additions: dict[str, str] = {}
    blocks: dict[str, list[tuple[str, str]]] = {p: [] for p in FAMILY_ORDER}
    for v in sorted(variants):
        vals = values_for(resource_name(v, lang))
        for prefix in FAMILY_ORDER:
            full = prefix + v
            if full not in keys:
                additions[full] = vals[prefix]
                blocks[prefix].append((full, vals[prefix]))

    print(f"\nTotal missing keys: {len(additions)}")
    flat = ROOT / "build" / "lang_audit_additions.json"
    flat.parent.mkdir(parents=True, exist_ok=True)
    flat.write_text(
        json.dumps(dict(sorted(additions.items())), indent=2, ensure_ascii=False),
        encoding="utf-8", newline="\n")

    # Paste-ready blocks: each line is `  "key": "value",` (2-space indent).
    paste = ROOT / "build" / "lang_audit_blocks.txt"
    with paste.open("w", encoding="utf-8", newline="\n") as fh:
        for prefix in FAMILY_ORDER:
            fh.write(f"# ---- {prefix}* ({len(blocks[prefix])} keys) ----\n")
            for full, val in blocks[prefix]:
                fh.write(f'  {json.dumps(full)}: {json.dumps(val, ensure_ascii=False)},\n')
            fh.write("\n")
    print(f"Flat additions: {flat.relative_to(ROOT)}")
    print(f"Paste-ready blocks: {paste.relative_to(ROOT)}")

    if "--write" in sys.argv:
        write_merged(lang, blocks)
        print(f"\nMerged {len(additions)} keys into {LANG.relative_to(ROOT)}")
    return 0


# Insertion point for a family that has no per-variant keys yet (only MILK,
# before its first --write): drop the new keys right after the base milk key.
FALLBACK_ANCHOR = {
    MILK: "item.productivefrogs.slime_milk_bucket",
}


def last_key_with_prefix(lang: dict[str, str], prefix: str) -> str | None:
    """The last key (in file order) under a family prefix, else None. New keys
    insert after it so the diff stays a pure insertion and the keys group with
    their siblings - computed live so it stays correct as the family grows
    across successive --write runs (a static anchor would strand later batches
    mid-block)."""
    last = None
    for key in lang:
        if key.startswith(prefix):
            last = key
    return last


def write_merged(lang: dict[str, str], blocks: dict[str, list[tuple[str, str]]]) -> None:
    anchor_families: dict[str, list[str]] = {}
    for prefix in FAMILY_ORDER:
        anchor = last_key_with_prefix(lang, prefix) or FALLBACK_ANCHOR.get(prefix)
        if anchor is None:
            raise ValueError(f"no insertion anchor for family {prefix!r}")
        anchor_families.setdefault(anchor, []).append(prefix)

    merged: dict[str, str] = {}
    for key, value in lang.items():
        merged[key] = value
        for prefix in anchor_families.get(key, []):
            for full, val in blocks[prefix]:
                merged[full] = val
    text = json.dumps(merged, indent=2, ensure_ascii=False) + "\n"
    with LANG.open("w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


if __name__ == "__main__":
    raise SystemExit(main())
