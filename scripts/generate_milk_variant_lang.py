#!/usr/bin/env python3
"""Generate per-variant Slime Milk lang keys (v1.8 per-variant fluids).

Each shipped variant now has its own milk fluid/block/bucket, so the lang shifts
from the single-fluid suffix shape (item.productivefrogs.slime_milk_bucket.<v>)
to per-variant prefix keys:
  item.productivefrogs.<v>_slime_milk_bucket   = "Bucket of <Name> Slime Milk"
  block.productivefrogs.<v>_slime_milk         = "<Name> Slime Milk"
  fluid_type.productivefrogs.<v>_slime_milk    = "<Name> Slime Milk"

Curated names are carried over from the old per-variant bucket values where
present, so no naming is lost. Removes the obsolete single-fluid keys.

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

    # Drop obsolete single-fluid keys (the suffix-style per-variant bucket names,
    # the base bucket, and the singular block / fluid_type).
    obsolete = {"item.productivefrogs.slime_milk_bucket",
                "block.productivefrogs.slime_milk",
                "fluid_type.productivefrogs.slime_milk"}
    for v in variants:
        obsolete.add(f"item.productivefrogs.slime_milk_bucket.{v}")
    # Capture curated bucket names before deleting them.
    old_bucket = {v: lang.get(f"item.productivefrogs.slime_milk_bucket.{v}") for v in variants}
    for k in obsolete:
        lang.pop(k, None)

    added = 0
    for v in variants:
        bucket_name = old_bucket[v] or f"Bucket of {title_case(v)} Slime Milk"
        # "<Name> Slime Milk" = bucket name minus the "Bucket of " prefix.
        milk_name = bucket_name[len("Bucket of "):] if bucket_name.startswith("Bucket of ") \
            else f"{title_case(v)} Slime Milk"
        for key, val in (
            (f"item.productivefrogs.{v}_slime_milk_bucket", bucket_name),
            (f"block.productivefrogs.{v}_slime_milk", milk_name),
            (f"fluid_type.productivefrogs.{v}_slime_milk", milk_name),
        ):
            if key not in lang:
                added += 1
            lang[key] = val

    with open(LANG, "w", encoding="utf-8", newline="\n") as f:
        json.dump(lang, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"per-variant milk lang: {len(variants)} variants, {added} keys added/updated, "
          f"{len(obsolete)} obsolete keys removed")


if __name__ == "__main__":
    main()
