#!/usr/bin/env python3
"""Generate per-variant Slime Milk client assets.

Slime Milk was refactored from ONE fluid to PER-VARIANT fluids/blocks/buckets.
For each variant <v> listed in variants_index.json this writes:
  - assets/productivefrogs/blockstates/<v>_slime_milk.json
  - assets/productivefrogs/models/item/<v>_slime_milk_bucket.json

Textures stay SHARED (one greyscale set, tinted at render time): the blockstate
points at the shared block/slime_milk_fluid model, and the item model references
the shared item/slime_milk_bucket + item/slime_milk_bucket_milk layer textures.
No per-variant block models or textures are created.

Idempotent: re-running overwrites the same files with identical content.

CRITICAL (project memory): write JSON without a BOM and with LF newlines, or the
Windows toolchain breaks. We use open(..., encoding='utf-8', newline='\n').
"""

import json
import os

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO_ROOT, "src", "main", "resources", "assets", "productivefrogs")
VARIANTS_INDEX = os.path.join(
    REPO_ROOT, "src", "main", "resources", "productivefrogs", "variants_index.json"
)

BLOCKSTATES_DIR = os.path.join(ASSETS, "blockstates")
ITEM_MODELS_DIR = os.path.join(ASSETS, "models", "item")

# Shape copied verbatim from the single-fluid assets.
BLOCKSTATE = {
    "variants": {
        "": {"model": "productivefrogs:block/slime_milk_fluid"}
    }
}

ITEM_MODEL = {
    "parent": "minecraft:item/generated",
    "textures": {
        "layer0": "productivefrogs:item/slime_milk_bucket",
        "layer1": "productivefrogs:item/slime_milk_bucket_milk",
    },
}


def write_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    with open(VARIANTS_INDEX, "r", encoding="utf-8") as f:
        index = json.load(f)
    variants = index["variants"]

    os.makedirs(BLOCKSTATES_DIR, exist_ok=True)
    os.makedirs(ITEM_MODELS_DIR, exist_ok=True)

    blockstates_written = 0
    item_models_written = 0

    for v in variants:
        write_json(os.path.join(BLOCKSTATES_DIR, f"{v}_slime_milk.json"), BLOCKSTATE)
        blockstates_written += 1
        write_json(os.path.join(ITEM_MODELS_DIR, f"{v}_slime_milk_bucket.json"), ITEM_MODEL)
        item_models_written += 1

    total = blockstates_written + item_models_written
    print(f"variants: {len(variants)}")
    print(f"blockstates written: {blockstates_written}")
    print(f"item models written: {item_models_written}")
    print(f"total files written: {total}")


if __name__ == "__main__":
    main()
