"""Generate the Frog Legs item textures (#194).

Procedural 16x16 pixel art (no PixelLab - the project bans it): a drumstick-style
leg (a bone with a meat blob), raw (pink) and cooked (browned). Run from the repo
root:  python scripts/generate_frog_legs_textures.py

Writes:
  src/main/resources/assets/productivefrogs/textures/item/raw_frog_legs.png
  src/main/resources/assets/productivefrogs/textures/item/cooked_frog_legs.png
"""

from pathlib import Path

from PIL import Image

OUT = Path("src/main/resources/assets/productivefrogs/textures/item")

CLEAR = (0, 0, 0, 0)
BONE = (235, 230, 214, 255)
BONE_SH = (200, 194, 176, 255)
OUTLINE = (60, 45, 45, 255)

RAW = (224, 132, 140, 255)       # pink frog-meat
RAW_SH = (190, 96, 110, 255)
RAW_HI = (240, 170, 176, 255)

COOKED = (170, 104, 58, 255)     # browned
COOKED_SH = (130, 74, 40, 255)
COOKED_HI = (200, 140, 86, 255)


def blank():
    return Image.new("RGBA", (16, 16), CLEAR)


def put(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = c


# Meat blob: an oval mass in the lower-left/middle. Cells as (x, y).
MEAT = [
    (3, 6), (4, 6), (5, 6),
    (2, 7), (3, 7), (4, 7), (5, 7), (6, 7),
    (2, 8), (3, 8), (4, 8), (5, 8), (6, 8), (7, 8),
    (2, 9), (3, 9), (4, 9), (5, 9), (6, 9), (7, 9),
    (3, 10), (4, 10), (5, 10), (6, 10), (7, 10),
    (4, 11), (5, 11), (6, 11),
]
MEAT_SHADE = [(2, 9), (3, 10), (4, 11), (5, 11), (6, 11), (6, 10), (7, 9)]
MEAT_LIGHT = [(3, 7), (4, 6), (3, 8)]

# Bone: a thin diagonal handle running up-right out of the meat, with a knob.
BONE_CELLS = [(7, 7), (8, 6), (9, 5), (10, 4), (11, 3)]
BONE_KNOB = [(11, 2), (12, 2), (11, 3), (12, 3)]


def draw_leg(meat, meat_sh, meat_hi):
    img = blank()
    px = img.load()

    # Simple outline ring around the meat for readability.
    for (x, y) in MEAT:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            nx, ny = x + dx, y + dy
            if (nx, ny) not in MEAT:
                put(px, nx, ny, OUTLINE)

    for (x, y) in MEAT:
        put(px, x, y, meat)
    for (x, y) in MEAT_SHADE:
        put(px, x, y, meat_sh)
    for (x, y) in MEAT_LIGHT:
        put(px, x, y, meat_hi)

    for i, (x, y) in enumerate(BONE_CELLS):
        put(px, x, y, BONE if i % 2 else BONE_SH)
    for (x, y) in BONE_KNOB:
        put(px, x, y, BONE)

    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    draw_leg(RAW, RAW_SH, RAW_HI).save(OUT / "raw_frog_legs.png")
    draw_leg(COOKED, COOKED_SH, COOKED_HI).save(OUT / "cooked_frog_legs.png")
    print(f"wrote {OUT / 'raw_frog_legs.png'}")
    print(f"wrote {OUT / 'cooked_frog_legs.png'}")


if __name__ == "__main__":
    main()
