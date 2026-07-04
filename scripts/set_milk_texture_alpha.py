#!/usr/bin/env python3
"""Set the shared milk fluid texture alpha (maintainer ruling 2026-07-03:
slurry and milk slightly more opaque).

The greyscale ``slime_milk_still`` / ``slime_milk_flow`` set is the render
texture for EVERY milk-family fluid - Slime Milk (all variants), Mimic Milk,
and Mob Slurry - in-world sources, the Crucible fill, and the Basin contents
surface. The set shipped at a uniform alpha 180 (~70% opaque), baked into the
generator's source art; this script is the durable record of the approved
override: a uniform ALPHA below.

Run from the repo root after regenerating the base set:
    python scripts/set_milk_texture_alpha.py
"""

import sys
from pathlib import Path

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

REPO = Path(__file__).resolve().parent.parent
BLOCK = REPO / "src/main/resources/assets/productivefrogs/textures/block"

# ~80% opaque: visibly thicker than the old 180 without going flat.
ALPHA = 205

TEXTURES = ["slime_milk_still.png", "slime_milk_flow.png"]


def main() -> None:
    for name in TEXTURES:
        path = BLOCK / name
        img = Image.open(path).convert("RGBA")
        px = img.load()
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = px[x, y]
                if a > 0:
                    px[x, y] = (r, g, b, ALPHA)
        img.save(path)
        print(f"{name}: alpha -> {ALPHA}")


if __name__ == "__main__":
    main()
