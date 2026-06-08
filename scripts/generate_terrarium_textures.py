"""
Generate the Terrarium block textures (16x16 PNG), per face.

Procedural Pillow art in the same shape as generate_catalyst_textures.py - no
base-PNG compositing. The four blocks share an Infernal nether-brick body so they
read as one machine family; each face carries a distinct motif:

    terrarium_controller  front = green "brain" core + quartz frame + blaze rivets
                          back  = quartz milk-feed vents
                          side  = framed machine side (green accent)
    sprinkler             front/back/side = quartz-banded body
                          bottom = the dripper: hopper funnel + cream milk drip
    incubator             front = amber glowstone window + green frog blob
                          back  = dark release port (frog exits into the cavity)
                          side  = framed machine side (amber accent)
    hatch                 front = iron chest mouth + froglight glint + hopper notch
                          back  = iron grille
                          side  = framed machine side (iron accent)

Each block also gets a <name>.png (= its front) as a cube_all fallback so the
current models stay valid until the oriented models are wired.

Re-run after editing the palette/motifs:
    python scripts/generate_terrarium_textures.py
Then rebuild the review page (it scans the shipped textures + candidate batches):
    pwsh gen/scripts/build_comparison_page.ps1

Requires Pillow. Outputs PNGs into
src/main/resources/assets/productivefrogs/textures/block/.
"""

import os
from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(REPO, "src", "main", "resources", "assets", "productivefrogs", "textures", "block")

S = 16
T = (0, 0, 0, 0)
BODY = (58, 28, 31, 255)        # #3A1C1F nether-brick maroon
MORTAR = (40, 18, 21, 255)
BODY_HI = (80, 42, 46, 255)
DARK = (18, 10, 12, 255)
PORT = (30, 18, 20, 255)
QUARTZ = (232, 224, 216, 255)
QUARTZ_DK = (188, 178, 168, 255)
AMBER = (232, 184, 56, 255)
AMBER_HI = (252, 220, 130, 255)
GREEN = (143, 203, 90, 255)
GREEN_HI = (190, 240, 150, 255)
CREAM = (240, 237, 227, 255)
IRON = (200, 200, 200, 255)
IRON_DK = (128, 128, 132, 255)
BLAZE = (228, 140, 40, 255)


def base_body():
    """A 16x16 nether-brick maroon body with mortar lines and a soft bevel."""
    img = Image.new("RGBA", (S, S), BODY)
    d = ImageDraw.Draw(img)
    for y in range(0, S, 4):
        d.line([(0, y), (S - 1, y)], fill=MORTAR)
    for row, y in enumerate(range(0, S, 4)):
        offset = 0 if row % 2 == 0 else 4
        for x in range(offset, S, 8):
            d.line([(x, y), (x, min(S - 1, y + 3))], fill=MORTAR)
    d.line([(0, 0), (S - 2, 0)], fill=BODY_HI)
    d.line([(0, 0), (0, S - 2)], fill=BODY_HI)
    d.line([(0, S - 1), (S - 1, S - 1)], fill=DARK)
    d.line([(S - 1, 0), (S - 1, S - 1)], fill=DARK)
    return img, d


def _corner_frame(d, color):
    for (x, y) in [(1, 1), (2, 1), (1, 2), (13, 1), (14, 1), (14, 2),
                   (1, 13), (1, 14), (2, 14), (14, 13), (13, 14), (14, 14)]:
        d.point((x, y), fill=color)


def _machine_side(accent):
    """Shared neutral side: framed body with quartz edge rails + a small accent."""
    img, d = base_body()
    d.line([(1, 1), (1, 14)], fill=QUARTZ_DK)
    d.line([(14, 1), (14, 14)], fill=QUARTZ_DK)
    d.rectangle([7, 7, 8, 8], fill=accent)
    return img


# ---- Controller ----------------------------------------------------------

def controller_front():
    img, d = base_body()
    _corner_frame(d, QUARTZ)
    for (x, y) in [(8, 1), (1, 8), (14, 8), (8, 14)]:
        d.point((x, y), fill=BLAZE)
    d.rectangle([5, 5, 10, 10], fill=GREEN)
    d.rectangle([6, 6, 9, 9], fill=GREEN_HI)
    d.rectangle([7, 7, 8, 8], fill=(228, 255, 205, 255))
    return img


def controller_back():
    img, d = base_body()
    # Three quartz vents - milk feeding toward the Sprinklers.
    for vy in (4, 8, 11):
        d.rectangle([4, vy, 11, vy + 1], fill=QUARTZ_DK)
        d.rectangle([5, vy, 10, vy], fill=QUARTZ)
    d.point((7, 2), fill=GREEN)
    d.point((8, 2), fill=GREEN)
    return img


# ---- Sprinkler -----------------------------------------------------------

def sprinkler_side():
    img, d = base_body()
    # Quartz band around the middle (the nozzle collar).
    d.rectangle([0, 6, 15, 9], fill=QUARTZ)
    d.rectangle([0, 6, 15, 6], fill=QUARTZ_DK)
    d.rectangle([0, 9, 15, 9], fill=QUARTZ_DK)
    return img


def sprinkler_bottom():
    img, d = base_body()
    # Quartz rim, then a grey hopper funnel converging to a centered milk drip.
    d.rectangle([2, 2, 13, 13], outline=QUARTZ)
    d.polygon([(3, 3), (12, 3), (9, 10), (6, 10)], fill=IRON_DK)
    d.rectangle([6, 10, 9, 12], fill=IRON_DK)
    d.rectangle([7, 7, 8, 13], fill=CREAM)
    d.point((7, 6), fill=QUARTZ)
    return img


# ---- Incubator -----------------------------------------------------------

def incubator_front():
    img, d = base_body()
    d.rectangle([3, 3, 12, 12], outline=QUARTZ)
    d.rectangle([4, 4, 11, 11], fill=AMBER)
    d.rectangle([5, 5, 10, 7], fill=AMBER_HI)
    d.ellipse([6, 7, 10, 11], fill=GREEN)
    d.point((7, 8), fill=GREEN_HI)
    d.point((9, 8), fill=GREEN_HI)
    return img


def incubator_back():
    img, d = base_body()
    # Dark release port: where the matured frog hops into the cavity.
    d.rectangle([4, 5, 11, 12], fill=PORT)
    d.rectangle([5, 6, 10, 11], fill=DARK)
    d.line([(4, 5), (11, 5)], fill=QUARTZ_DK)
    d.point((7, 9), fill=GREEN)
    d.point((8, 9), fill=GREEN)
    return img


# ---- Hatch ---------------------------------------------------------------

def hatch_front():
    img, d = base_body()
    d.rectangle([0, 0, 15, 15], outline=IRON)
    d.rectangle([1, 1, 14, 14], outline=IRON_DK)
    d.rectangle([3, 4, 12, 10], fill=DARK)
    d.rectangle([3, 6, 12, 6], fill=(34, 22, 24, 255))
    d.point((5, 8), fill=AMBER)
    d.point((8, 8), fill=GREEN)
    d.point((10, 8), fill=CREAM)
    d.rectangle([6, 13, 9, 14], fill=IRON_DK)
    d.rectangle([7, 14, 8, 15], fill=DARK)
    return img


def hatch_back():
    img, d = base_body()
    d.rectangle([0, 0, 15, 15], outline=IRON_DK)
    # Iron grille: a few horizontal output slots.
    for gy in (5, 8, 11):
        d.rectangle([4, gy, 11, gy], fill=IRON)
        d.rectangle([4, gy + 1, 11, gy + 1], fill=DARK)
    return img


BLOCKS = {
    "terrarium_controller": {
        "front": controller_front,
        "back": controller_back,
        "side": lambda: _machine_side(GREEN),
    },
    "sprinkler": {
        "front": sprinkler_side,
        "back": sprinkler_side,
        "side": sprinkler_side,
        "bottom": sprinkler_bottom,
    },
    "incubator": {
        "front": incubator_front,
        "back": incubator_back,
        "side": lambda: _machine_side(AMBER),
    },
    "hatch": {
        "front": hatch_front,
        "back": hatch_back,
        "side": lambda: _machine_side(IRON),
    },
}


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, faces in BLOCKS.items():
        for face, fn in faces.items():
            path = os.path.join(OUT_DIR, f"{name}_{face}.png")
            fn().save(path)
            print("wrote", path)
        # cube_all fallback (= front) so current models stay valid until the
        # oriented models are wired.
        fallback = os.path.join(OUT_DIR, f"{name}.png")
        faces["front"]().save(fallback)
        print("wrote", fallback)
    print("done - rebuild the review page with: pwsh gen/scripts/build_comparison_page.ps1")


if __name__ == "__main__":
    main()
