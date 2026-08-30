#!/usr/bin/env python3
"""Bake the Rainbow Slime's entity texture.

Every other variant gets its inner cube from a vanilla block texture via
`generate_resource_slime_textures.py`, driven by the variant's `inner_block`.
"Rainbow" has no vanilla block to point at, so the `rainbow` variant ships no
`inner_block` and this script paints the inner cube instead: a hue sweep across
all six 6x6 inner faces.

Only the OUTER shell is multiplied by the variant's `primary_color` at render
time (`ResourceSlimeOuterLayer`); the inner cube renders as authored, so the
baked hues come through true rather than tinted flat.

Base is `bog_resource_slime.png` - the parent species' texture, which is the
grey shell with an empty (transparent) inner cube.

Idempotent. Requires Pillow.
"""
import colorsys
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SLIME_DIR = os.path.join(ROOT, "src/main/resources/assets/productivefrogs/textures/entity/slime")
BASE = os.path.join(SLIME_DIR, "bog_resource_slime.png")
OUT = os.path.join(SLIME_DIR, "rainbow_resource_slime.png")

# Inner-cube face rectangles in the 64x32 slime layout (6x6 each), same as
# generate_resource_slime_textures.py.
FACES = [(6, 16), (12, 16), (0, 22), (6, 22), (12, 22), (18, 22)]
SIZE = 6


def main() -> None:
    img = Image.open(BASE).convert("RGBA")
    for fx, fy in FACES:
        for dy in range(SIZE):
            for dx in range(SIZE):
                # Sweep hue along the face diagonal so every face reads as a
                # full rainbow rather than a flat band.
                hue = ((dx + dy) / (2 * (SIZE - 1))) % 1.0
                r, g, b = colorsys.hsv_to_rgb(hue, 0.85, 0.95)
                img.putpixel((fx + dx, fy + dy),
                             (int(r * 255), int(g * 255), int(b * 255), 255))
    img.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
