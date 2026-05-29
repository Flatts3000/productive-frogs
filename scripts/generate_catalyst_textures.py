"""
Generate the four Slime Milk catalyst item textures (16x16 PNG).

Source of truth for the catalyst icons (docs/slime_milk_catalysts.md). Each
catalyst is a tinted slime orb (matching the mod's slime aesthetic) carrying a
distinct white glyph:

    count     - green   - "+"        (adds spawns)
    speed     - yellow  - ">>"       (faster cadence)
    quantity  - orange  - 2x2 pips   (more slimes per spawn)
    infinite  - purple  - figure-8   (never depletes; built from count catalysts)

Re-run after editing the palette/glyphs:
    python scripts/generate_catalyst_textures.py

Requires Pillow (same dependency as generate_resource_slime_textures.py).
Outputs into src/main/resources/assets/productivefrogs/textures/item/.
"""

import os
from PIL import Image, ImageDraw

OUT_DIR = os.path.join(
    os.path.dirname(__file__), "..",
    "src", "main", "resources", "assets", "productivefrogs", "textures", "item",
)

TRANSPARENT = (0, 0, 0, 0)
WHITE = (245, 245, 245, 255)


def _shade(rgb, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in rgb)


def _orb(base):
    """A 16x16 tinted slime orb: rounded body with a darker rim for depth.

    No lighter highlight blob - on a light base colour (yellow especially) the
    highlight read as a stray bright patch behind the glyph. The rim alone gives
    enough roundness, and the white glyph reads cleaner on a flat body.
    """
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    d = ImageDraw.Draw(img)
    body = base + (255,)
    rim = _shade(base, 0.6) + (255,)
    d.ellipse([2, 3, 13, 14], fill=body, outline=rim)
    d.ellipse([3, 4, 12, 13], fill=body)
    return img, d


def _plus(d):
    d.rectangle([7, 5, 8, 11], fill=WHITE)   # vertical
    d.rectangle([5, 7, 10, 8], fill=WHITE)   # horizontal


def _chevrons(d):
    for ox in (4, 7):
        d.line([ox, 5, ox + 3, 8], fill=WHITE, width=1)
        d.line([ox + 3, 8, ox, 11], fill=WHITE, width=1)


def _pips(d):
    for px in (5, 9):
        for py in (5, 9):
            d.rectangle([px, py, px + 1, py + 1], fill=WHITE)


def _infinity(d):
    d.ellipse([4, 6, 8, 10], outline=WHITE, width=1)
    d.ellipse([8, 6, 12, 10], outline=WHITE, width=1)


CATALYSTS = {
    "count_catalyst": ((111, 191, 63), _plus),
    "speed_catalyst": ((242, 197, 61), _chevrons),
    "quantity_catalyst": ((224, 122, 43), _pips),
    "infinite_catalyst": ((155, 79, 214), _infinity),
}


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, (base, glyph) in CATALYSTS.items():
        img, d = _orb(base)
        glyph(d)
        path = os.path.join(OUT_DIR, name + ".png")
        img.save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
