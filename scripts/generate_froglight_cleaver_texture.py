"""Generate the Froglight Cleaver item texture (#212).

Procedural 16x16 pixel art (no PixelLab). The cleaver is crafted from its
materials, and the texture reflects them: a pale glowing blade (Nether Star
froglight - 3 of them, the dominant material), a dark purple hilt/guard (Dragon
Egg froglight), and a faint purple wisp (Dragon's Breath). Diagonal like a
vanilla sword. Run from the repo root:
  python scripts/generate_froglight_cleaver_texture.py

Writes:
  src/main/resources/assets/productivefrogs/textures/item/froglight_cleaver.png
"""

from pathlib import Path

from PIL import Image

OUT = Path("src/main/resources/assets/productivefrogs/textures/item")

CLEAR = (0, 0, 0, 0)
# Nether Star froglight: pale white-cyan glow (the blade).
BLADE = (214, 240, 236, 255)
BLADE_HI = (240, 252, 250, 255)
BLADE_EDGE = (150, 196, 196, 255)
# Dragon Egg froglight: dark purple-black (the hilt + guard).
HILT = (44, 30, 56, 255)
HILT_HI = (74, 52, 92, 255)
GUARD = (96, 64, 120, 255)
# Dragon's Breath: faint magenta wisp accent.
BREATH = (206, 130, 224, 200)


def put(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = c


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()

    # Blade: diagonal from the guard (lower-left) to the tip (upper-right).
    for i in range(9):
        x = 5 + i
        y = 10 - i
        put(px, x - 1, y, BLADE_EDGE)
        put(px, x, y, BLADE)
        put(px, x, y - 1, BLADE_HI)
    put(px, 13, 2, BLADE_HI)
    put(px, 14, 1, BLADE_HI)

    # Dragon's Breath wisp drifting off the blade.
    put(px, 12, 1, BREATH)
    put(px, 15, 3, BREATH)
    put(px, 11, 0, BREATH)

    # Guard (dragon-egg purple) across the base of the blade.
    for (x, y) in [(4, 11), (5, 11), (5, 10), (6, 12), (3, 10)]:
        put(px, x, y, GUARD)

    # Hilt: short dark-purple handle down to the bottom-left.
    for (x, y) in [(4, 12), (3, 13), (2, 14)]:
        put(px, x, y, HILT)
    put(px, 3, 12, HILT_HI)
    put(px, 2, 13, HILT_HI)

    img.save(OUT / "froglight_cleaver.png")
    print(f"wrote {OUT / 'froglight_cleaver.png'}")


if __name__ == "__main__":
    main()
