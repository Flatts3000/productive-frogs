"""Generate the Hopping mob-effect icon (#215).

Procedural 18x18 pixel art (no PixelLab): a green upward chevron / leap arc, the
HUD icon for the Hopping effect. Run from the repo root:
  python scripts/generate_hopping_effect_icon.py

Writes:
  src/main/resources/assets/productivefrogs/textures/mob_effect/hopping.png
"""

from pathlib import Path

from PIL import Image

OUT = Path("src/main/resources/assets/productivefrogs/textures/mob_effect")

CLEAR = (0, 0, 0, 0)
GREEN = (120, 200, 90, 255)
GREEN_HI = (160, 230, 120, 255)
GREEN_SH = (84, 150, 64, 255)


def put(px, x, y, c):
    if 0 <= x < 18 and 0 <= y < 18:
        px[x, y] = c


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGBA", (18, 18), CLEAR)
    px = img.load()

    # An upward chevron (>>> rotated to point up) suggesting a forward leap arc.
    # Two stacked chevrons.
    for cy in (10, 6):
        for i in range(6):
            put(px, 3 + i, cy - i if i <= 3 else cy - (6 - i), GREEN)
            put(px, 14 - i, cy - i if i <= 3 else cy - (6 - i), GREEN)
    # Center spine + highlight tip.
    for y in range(2, 14):
        put(px, 8, y, GREEN)
        put(px, 9, y, GREEN_SH)
    put(px, 8, 2, GREEN_HI)
    put(px, 9, 2, GREEN_HI)

    img.save(OUT / "hopping.png")
    print(f"wrote {OUT / 'hopping.png'}")


if __name__ == "__main__":
    main()
