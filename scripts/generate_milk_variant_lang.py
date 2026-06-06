#!/usr/bin/env python3
"""Ensure per-variant Slime Milk lang keys exist (v1.8 per-variant fluids).

Each shipped variant has its own milk fluid/block/bucket with prefix-style keys:
  item.productivefrogs.<v>_slime_milk_bucket   = "Bucket of <Name> Slime Milk"
  block.productivefrogs.<v>_slime_milk         = "<Name> Slime Milk"
  fluid_type.productivefrogs.<v>_slime_milk    = "<Name> Slime Milk"

This script fills the keys for any variant that lacks them and NEVER overwrites
an existing value, so hand-curated names ("Blazing Crystal Slime Milk") survive
re-runs. New variants get a title-cased fallback; curate by hand afterwards if
the resource name isn't plain title-case (the same convention as
audit_lang_keys.py's NAME_OVERRIDES for the non-milk families).

History: this began as the one-time v1.8 suffix->prefix migration script. The
migration machinery (deleting `item.productivefrogs.slime_milk_bucket.<v>` keys
and sourcing names from them) was removed 2026-06-06 - those keys are long gone,
and re-running the migration form regenerated every name from the variant id,
clobbering curated names ("Blazing Crystal" -> "Blazing", "Pink Slime" ->
"Pink Slime Slime").

Idempotent. Writes UTF-8, LF, no BOM (Windows PS 5.1 BOM gotcha).
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, "src/main/resources/assets/productivefrogs/lang/en_us.json")
INDEX = os.path.join(ROOT, "src/main/resources/productivefrogs/variants_index.json")


def title_case(name: str) -> str:
    return " ".join(w.capitalize() for w in name.split("_"))


def main() -> None:
    with open(INDEX, encoding="utf-8") as f:
        variants = json.load(f)["variants"]
    with open(LANG, encoding="utf-8") as f:
        lang = json.load(f)

    added = 0
    for v in variants:
        bucket_key = f"item.productivefrogs.{v}_slime_milk_bucket"
        bucket_name = lang.get(bucket_key) or f"Bucket of {title_case(v)} Slime Milk"
        # "<Name> Slime Milk" = bucket name minus the "Bucket of " prefix.
        milk_name = bucket_name[len("Bucket of "):] if bucket_name.startswith("Bucket of ") \
            else f"{title_case(v)} Slime Milk"
        for key, val in (
            (bucket_key, bucket_name),
            (f"block.productivefrogs.{v}_slime_milk", milk_name),
            (f"fluid_type.productivefrogs.{v}_slime_milk", milk_name),
        ):
            if key not in lang:
                lang[key] = val
                added += 1

    if added:
        with open(LANG, "w", encoding="utf-8", newline="\n") as f:
            json.dump(lang, f, ensure_ascii=False, indent=2)
            f.write("\n")
    print(f"per-variant milk lang: {len(variants)} variants, {added} missing keys added")


if __name__ == "__main__":
    main()
