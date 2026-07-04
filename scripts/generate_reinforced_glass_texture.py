"""Bake reinforced_light_blue_stained_glass.png (#280 Elder altar walls).

Procedural: a vanilla-style light blue stained glass pane (translucent pale
blue fill + lighter streak highlights) framed by opaque obsidian corner
brackets so it reads as the reinforced family. Provisional pending the
comparison-page approval pass.

Run from the repo root:  python scripts/generate_reinforced_glass_texture.py
"""
import os
import sys

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/productivefrogs/textures/block",
                   "reinforced_light_blue_stained_glass.png")

FILL = (140, 190, 235, 150)        # pale light blue, translucent
STREAK = (196, 226, 250, 190)      # lighter diagonal streaks
EDGE = (170, 210, 245, 210)        # glass border line
FRAME_DARK = (18, 14, 34, 255)     # obsidian bracket, dark
FRAME_LIGHT = (66, 50, 102, 255)   # obsidian bracket highlight

img = Image.new("RGBA", (16, 16), FILL)

# vanilla-style diagonal streaks
for i in range(16):
    for (sx, sy) in [(2, 11), (3, 10), (4, 9), (10, 4), (11, 3), (12, 2)]:
        pass
for d in range(-2, 3):
    for t in range(16):
        x, y = t, t + d * 5
        if 0 <= y < 16 and d in (-1, 1):
            img.putpixel((x, y), STREAK)

# glass border
for i in range(16):
    for (x, y) in [(i, 0), (i, 15), (0, i), (15, i)]:
        img.putpixel((x, y), EDGE)

# obsidian corner brackets (3px arms) - the "reinforced" read
def bracket(cx, cy, dx, dy):
    for a in range(4):
        img.putpixel((cx + a * dx, cy), FRAME_DARK)
        img.putpixel((cx, cy + a * dy), FRAME_DARK)
    img.putpixel((cx + dx, cy + dy), FRAME_LIGHT)

bracket(0, 0, 1, 1)
bracket(15, 0, -1, 1)
bracket(0, 15, 1, -1)
bracket(15, 15, -1, -1)

img.save(OUT)
print("baked reinforced_light_blue_stained_glass.png")
