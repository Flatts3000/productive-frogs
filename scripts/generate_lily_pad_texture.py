"""Generate the Sweetslimed Lily Pad block texture (#214).

A vanilla-style lily pad (green disc with the characteristic slit) given a slime
sheen: a glossy lighter-green highlight and a couple of gloss dots, so it reads as
"a lily pad coated in sweetslime". Procedural (Pillow) per the project's no-PixelLab
rule. Deterministic - re-running produces the same 16x16 PNG.

Run: python scripts/generate_lily_pad_texture.py
"""
import math
import os

from PIL import Image

S = 16
CX, CY = 8.0, 8.0
R = 7.2

BASE = (78, 130, 60, 255)    # lily-pad green
EDGE = (52, 94, 44, 255)     # darker rim
SHEEN = (156, 214, 122, 255) # slime gloss highlight
GLOSS = (188, 236, 150, 255) # bright gloss dots
NOTCH = math.radians(45.0)   # the lily-pad slit, toward the lower-right
NOTCH_WIDTH = 0.34


def build():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            dx = x + 0.5 - CX
            dy = y + 0.5 - CY
            dist = math.hypot(dx, dy)
            if dist > R:
                continue
            ang = math.atan2(dy, dx)
            # The slit: a thin transparent wedge from just off-centre outward.
            if dist > 1.3 and abs(ang - NOTCH) < NOTCH_WIDTH:
                continue
            img.putpixel((x, y), EDGE if dist > R - 1.3 else BASE)

    # Soft sheen highlight in the upper-left of the pad.
    for y in range(S):
        for x in range(S):
            if img.getpixel((x, y))[3] == 0:
                continue
            if math.hypot(x + 0.5 - (CX - 2.4), y + 0.5 - (CY - 2.4)) < 2.9:
                img.putpixel((x, y), SHEEN)

    # A few bright gloss specks for the slimy wet look.
    for px, py in [(6, 5), (10, 6), (7, 9), (11, 10)]:
        if img.getpixel((px, py))[3] > 0:
            img.putpixel((px, py), GLOSS)

    return img


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(
        here, "..", "src", "main", "resources", "assets", "productivefrogs",
        "textures", "block", "sweetslimed_lily_pad.png",
    )
    out = os.path.normpath(out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    build().save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
