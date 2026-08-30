#!/usr/bin/env python3
"""Bake the Rainbow Slime's entity texture.

Every other variant gets its inner cube from a vanilla block texture via
`generate_resource_slime_textures.py`, driven by the variant's `inner_block`.
"Rainbow" has no vanilla block to point at, so the `rainbow` variant ships no
`inner_block` and this script paints the inner cube instead: a hue sweep across
all six 6x6 inner faces.

BOTH cubes are painted, and the variant sets `untinted_shell: true` so the renderer
skips the shell tint entirely. That matters: the shell is normally multiplied by
`primary_color`, a multiply can only ever produce one flat hue, and the inner
cube is SEEN THROUGH the translucent shell - so a tinted shell would wash both
toward a single colour no matter what is baked here.

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
INNER_FACES = [(6, 16), (12, 16), (0, 22), (6, 22), (12, 22), (18, 22)]
INNER_SIZE = 6

# Outer-shell faces: the 8x8x8 box at texOffs(0,0). Sides sit in y[8,16), the two
# caps in y[0,8) - confirmed by scanning which pixels of the source are opaque.
OUTER_SIDES = [(0, 8), (8, 8), (16, 8), (24, 8)]
OUTER_CAPS = [(8, 0), (16, 0)]
OUTER_SIZE = 8

# The source shell is a flat grey at one alpha - no shading to preserve - so the
# bake only has to choose a hue per pixel and keep the jelly's value and alpha.
# Saturation stays moderate: the shell is translucent and sits in front of the
# inner cube, and a fully saturated shell would bury it.
SHELL_SATURATION = 0.65


# The inner cube is FULLY TRANSPARENT in the base texture - the parent species has
# no inner block - so it has to be drawn from nothing rather than recoloured, at a
# fixed opaque value. Recolouring in place would leave it black.
INNER_SATURATION = 0.85
INNER_VALUE = 0.95


def recolour(img, faces, size, hue_at, saturation):
    """Recolour existing pixels in place, keeping each one's value and alpha."""
    for fx, fy in faces:
        for dy in range(size):
            for dx in range(size):
                r, g, b, a = img.getpixel((fx + dx, fy + dy))
                if a == 0:
                    continue
                _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                nr, ng, nb = colorsys.hsv_to_rgb(hue_at(dx, dy, size), saturation, v)
                img.putpixel((fx + dx, fy + dy),
                             (int(nr * 255), int(ng * 255), int(nb * 255), a))


def draw(img, faces, size, hue_at, saturation, value):
    """Draw opaque pixels over a region that may be empty in the source."""
    for fx, fy in faces:
        for dy in range(size):
            for dx in range(size):
                nr, ng, nb = colorsys.hsv_to_rgb(hue_at(dx, dy, size), saturation, value)
                img.putpixel((fx + dx, fy + dy),
                             (int(nr * 255), int(ng * 255), int(nb * 255), 255))


def banded(dx, dy, size):
    """Hue by ROW. A diagonal puts too many hue steps across a small face and
    reads as coloured noise at entity scale; bands read as a rainbow. Confirmed
    in-client. Used for the inner cube and the shell's four sides, so the two
    sweep the same direction and reinforce rather than clash."""
    return (dy / size) % 1.0


def diagonal(dx, dy, size):
    """Caps have no up/down to sweep along, so they sweep corner to corner."""
    return ((dx + dy) / (2 * size - 1)) % 1.0


def main() -> None:
    img = Image.open(BASE).convert("RGBA")
    # Inner cube: drawn, not recoloured - the source region is empty.
    draw(img, INNER_FACES, INNER_SIZE, banded, INNER_SATURATION, INNER_VALUE)
    # Outer shell: recoloured in place, keeping the jelly's value and alpha.
    recolour(img, OUTER_SIDES, OUTER_SIZE, banded, SHELL_SATURATION)
    recolour(img, OUTER_CAPS, OUTER_SIZE, diagonal, SHELL_SATURATION)
    img.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
