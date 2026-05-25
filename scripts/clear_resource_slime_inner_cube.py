"""Clear the inner-cube region of the per-category Resource Slime entity textures.

Why this exists
---------------
v1.0.1 renders each Resource Slime's variant block live inside the slime
(`ResourceSlimeInnerBlockLayer` -> `BlockRenderDispatcher.renderSingleBlock`).
The vanilla `SlimeRenderer` still draws the model's inner body cube underneath,
using the per-category texture `<category>_resource_slime.png`. Those textures
(authored in V1.5) carry an OPAQUE, solidly-coloured inner-cube body
(e.g. cave = red, void = purple). Because the slime renders in the translucent
entity pass, that opaque coloured cube ends up obscuring the live resource
block - so every Cave variant looked like a red (redstone) cube, every Void
variant like a purple cube, etc., regardless of the actual variant.

The slime texture is a standard 64x32 layout: the outer 8x8x8 jelly shell
occupies y < 16; the inner 6x6x6 body cube occupies y >= 16. Clearing the
inner-cube region (alpha 0) removes the obscuring coloured cube so the live
per-variant block is the visible interior, seen through the translucent shell.

These Resource Slime textures have no eyes in the inner region (the eyes live on
the parent-species textures, which are left untouched here), so clearing the
whole y >= 16 band is safe.

Re-run after re-authoring any `*_resource_slime.png`. Idempotent.

Requires Pillow (PIL). Platform-independent.
"""

import os
from PIL import Image

SLIME_DIR = os.path.join(
    os.path.dirname(__file__), "..",
    "src", "main", "resources", "assets", "productivefrogs",
    "textures", "entity", "slime",
)

# Only the Resource Slime textures - parent-species *_slime.png keep their
# inner cube (some carry eyes, and their inner block is a separate concern).
TARGETS = [
    "bog_resource_slime.png",
    "cave_resource_slime.png",
    "geode_resource_slime.png",
    "infernal_resource_slime.png",
    "tide_resource_slime.png",
    "void_resource_slime.png",
]

INNER_CUBE_TOP_Y = 16  # rows >= this belong to the 6x6x6 inner body cube


def clear_inner_cube(path):
    img = Image.open(path).convert("RGBA")
    w, h = img.size
    px = img.load()
    cleared = 0
    for y in range(INNER_CUBE_TOP_Y, h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a != 0:
                px[x, y] = (0, 0, 0, 0)
                cleared += 1
    img.save(path)
    return w, h, cleared


def main():
    for name in TARGETS:
        path = os.path.join(SLIME_DIR, name)
        if not os.path.exists(path):
            print(f"skip (missing): {name}")
            continue
        w, h, cleared = clear_inner_cube(path)
        print(f"{name}: {w}x{h}, cleared {cleared} inner-cube px -> transparent")
    print("done")


if __name__ == "__main__":
    main()
