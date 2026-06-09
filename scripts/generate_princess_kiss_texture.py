"""Generate the Princess's Kiss item texture (#216).

Procedural 16x16 pixel art (no PixelLab): a pink lip-print kiss mark with a small
sparkle. Run from the repo root:
  python scripts/generate_princess_kiss_texture.py

Writes:
  src/main/resources/assets/productivefrogs/textures/item/princess_kiss.png
"""

from pathlib import Path

from PIL import Image

OUT = Path("src/main/resources/assets/productivefrogs/textures/item")

CLEAR = (0, 0, 0, 0)
LIP = (214, 70, 110, 255)
LIP_HI = (240, 120, 156, 255)
LIP_SH = (170, 44, 82, 255)
SPARK = (255, 240, 250, 255)


def put(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = c


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.load()

    # Upper lip: two humps. Lower lip: a single curve. Classic lipstick print.
    upper = [
        (4, 6), (5, 5), (6, 6), (7, 7), (8, 6), (9, 5), (10, 6),
        (5, 6), (9, 6),
    ]
    middle = [(4, 7), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7)]
    lower = [
        (4, 8), (5, 9), (6, 9), (7, 10), (8, 9), (9, 9), (10, 8),
        (5, 8), (6, 8), (7, 8), (8, 8), (9, 8),
    ]

    for (x, y) in upper:
        put(px, x, y, LIP)
    for (x, y) in middle:
        put(px, x, y, LIP_SH)
    for (x, y) in lower:
        put(px, x, y, LIP)
    # Highlights on the lips.
    for (x, y) in [(5, 5), (9, 5), (6, 9), (8, 9)]:
        put(px, x, y, LIP_HI)
    for (x, y) in [(4, 8), (10, 8), (7, 10)]:
        put(px, x, y, LIP_SH)

    # Sparkle, upper-right.
    put(px, 12, 3, SPARK)
    put(px, 12, 2, SPARK)
    put(px, 12, 4, SPARK)
    put(px, 11, 3, SPARK)
    put(px, 13, 3, SPARK)

    img.save(OUT / "princess_kiss.png")
    print(f"wrote {OUT / 'princess_kiss.png'}")


if __name__ == "__main__":
    main()
