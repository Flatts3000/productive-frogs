"""Generate the Frog Legs Soup item texture (#217).

Procedural 16x16 pixel art (no PixelLab): a wooden bowl of greenish soup with a
frog leg poking out. Run from the repo root:
  python scripts/generate_frog_legs_soup_texture.py

Writes:
  src/main/resources/assets/productivefrogs/textures/item/frog_legs_soup.png
"""

from pathlib import Path

from PIL import Image

OUT = Path("src/main/resources/assets/productivefrogs/textures/item")

CLEAR = (0, 0, 0, 0)
BOWL = (150, 102, 60, 255)
BOWL_SH = (112, 74, 42, 255)
BOWL_HI = (178, 128, 80, 255)
SOUP = (120, 150, 78, 255)        # greenish swamp soup
SOUP_HI = (150, 178, 104, 255)
LEG = (170, 104, 58, 255)         # a cooked leg poking out
BONE = (235, 230, 214, 255)


def put(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = c


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()

    # Soup surface (an ellipse band near the top of the bowl).
    for x in range(4, 12):
        put(px, x, 8, SOUP)
    for x in range(3, 13):
        put(px, x, 9, SOUP)
    for (x, y) in [(5, 8), (7, 8), (9, 8), (6, 9), (10, 9)]:
        put(px, x, y, SOUP_HI)

    # A little leg + bone poking out of the soup.
    put(px, 10, 6, LEG)
    put(px, 10, 7, LEG)
    put(px, 11, 6, LEG)
    put(px, 12, 5, BONE)

    # Bowl body (rounded), rows 9-13.
    bowl_rows = {
        9: range(3, 13),
        10: range(3, 13),
        11: range(4, 12),
        12: range(5, 11),
        13: range(6, 10),
    }
    for y, xs in bowl_rows.items():
        for x in xs:
            if y == 9:
                continue  # soup band already drawn on row 9 interior
            put(px, x, y, BOWL)
    # Rim highlights + shading.
    for x in range(3, 13):
        put(px, x, 9, px[x, 9] if px[x, 9][3] else BOWL)
    put(px, 3, 9, BOWL_HI)
    put(px, 12, 9, BOWL_SH)
    for (x, y) in [(6, 13), (7, 13), (8, 13), (9, 13), (5, 12), (10, 12)]:
        put(px, x, y, BOWL_SH)
    for (x, y) in [(4, 10), (4, 11)]:
        put(px, x, y, BOWL_HI)

    img.save(OUT / "frog_legs_soup.png")
    print(f"wrote {OUT / 'frog_legs_soup.png'}")


if __name__ == "__main__":
    main()
