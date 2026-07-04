#!/usr/bin/env python3
"""Bake the Liquid Experience bucket texture (#281 Phase 2).

Composites the shared bucket base (``slime_milk_bucket.png``) with the milk
contents overlay (``slime_milk_bucket_milk.png``) tinted vanilla XP-orb green
(0x80FF20 - the same constant ``PFClientEvents.LIQUID_EXPERIENCE_GREEN`` uses
for the fluid render), producing a single static ``liquid_experience_bucket.png``.
The colour never varies, so unlike the milk bucket there is no runtime tint
layer - the green is baked in and the item model is single-layer.

Run from the repo root:  python scripts/generate_liquid_experience_textures.py
"""

import sys
from pathlib import Path

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

REPO = Path(__file__).resolve().parent.parent
ITEM_TEX = REPO / "src/main/resources/assets/productivefrogs/textures/item"

XP_GREEN = (0x80, 0xFF, 0x20)


def tint(img: Image.Image, rgb: tuple[int, int, int]) -> Image.Image:
    """Multiply-tint an RGBA image by rgb, preserving alpha."""
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r * rgb[0] // 255, g * rgb[1] // 255, b * rgb[2] // 255, a)
    return out


def main() -> None:
    base = Image.open(ITEM_TEX / "slime_milk_bucket.png").convert("RGBA")
    contents = Image.open(ITEM_TEX / "slime_milk_bucket_milk.png").convert("RGBA")

    out = base.copy()
    out.alpha_composite(tint(contents, XP_GREEN))

    dest = ITEM_TEX / "liquid_experience_bucket.png"
    out.save(dest)
    print(f"wrote {dest.relative_to(REPO)}")


if __name__ == "__main__":
    main()
