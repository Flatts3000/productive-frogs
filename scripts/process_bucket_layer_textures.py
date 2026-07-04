#!/usr/bin/env python3
"""Bake the two-layer tadpole / slime bucket textures from the hand-split
vanilla 26.2 mob-in-bucket art (maintainer-separated, 2026-07-03).

Inputs (checked in next to this script - the durable copies of the split):
  - tadpole_bucket_part.png / tadpole_creature_part.png  (vanilla tadpole_bucket)
  - slime_bucket_part.png   / slime_creature_part.png    (vanilla sulfur_cube_bucket)
The two parts of each pair recomposite pixel-perfectly to the vanilla art
(verified against the 26.2 jar); they never overlap.

Outputs (assets/productivefrogs/textures/item/):
  - tadpole_bucket_base.png / slime_bucket_base.png - the bucket part VERBATIM.
    This is the untinted layer0, so an empty bucket renders exactly the vanilla
    bucket-and-water art with a hole where the creature sits.
  - tadpole_silhouette.png / slime_silhouette.png - the creature part converted
    to NORMALIZED GREYSCALE (luminance, scaled so the brightest pixel is pure
    white). This is the tinted layer1: the item tint multiplies the texture, so
    a luminance base keeps every category/variant hue true (a blue tint over
    the vanilla brown tadpole would go muddy; over its greyscale it reads blue
    with the vanilla shading intact). The tint sources' unstamped defaults
    (tadpole brown / slime green) reproduce a roughly-vanilla look.

The slime silhouette is shared by the Mimic Slime Bucket (over the plain
vanilla bucket, tinted by the synthesized item's colour).

Run from the repo root:  python scripts/process_bucket_layer_textures.py
"""

import sys
from pathlib import Path

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "scripts"
ITEM_TEX = REPO / "src/main/resources/assets/productivefrogs/textures/item"

PAIRS = [
    ("tadpole_bucket_part.png", "tadpole_creature_part.png",
     "tadpole_bucket_base.png", "tadpole_silhouette.png"),
    ("slime_bucket_part.png", "slime_creature_part.png",
     "slime_bucket_base.png", "slime_silhouette.png"),
]


def normalized_greyscale(img: Image.Image) -> Image.Image:
    """Luminance greyscale, scaled so the brightest opaque pixel is white."""
    out = img.copy()
    px = out.load()
    lums = []
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a > 0:
                lums.append(0.299 * r + 0.587 * g + 0.114 * b)
    peak = max(lums) if lums else 255.0
    scale = 255.0 / peak if peak > 0 else 1.0
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a > 0:
                v = min(255, round((0.299 * r + 0.587 * g + 0.114 * b) * scale))
                px[x, y] = (v, v, v, a)
    return out


def main() -> None:
    for bucket_in, creature_in, base_out, silhouette_out in PAIRS:
        bucket = Image.open(SRC / bucket_in).convert("RGBA")
        creature = Image.open(SRC / creature_in).convert("RGBA")

        bucket.save(ITEM_TEX / base_out)
        normalized_greyscale(creature).save(ITEM_TEX / silhouette_out)
        print(f"wrote {base_out} + {silhouette_out}")


if __name__ == "__main__":
    main()
