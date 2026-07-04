"""Bake the reinforced-froglight block textures (#279/#280 re-key).

The reinforced family shares one 16x16 layout: an obsidian frame (pixels
identical across every reinforced texture) around a themed froglight fill.
This baker keeps the frame from the shipped reinforced_soul_sand_froglight
template and remaps the fill pixels' luminance onto a per-theme color ramp,
so every reinforced block keeps the same pixel structure and only the theme
color changes (the same one-base-many-tints instinct as the Category tint
pipeline, applied at bake time).

The frame mask (scripts/reinforced_frame_mask.png, white = frame) was derived
once by diffing the four pre-re-key reinforced textures: any pixel identical
across all four is frame. Regenerate it with --remask <png...> if the base
art ever changes.

Run from the repo root:  python scripts/generate_reinforced_froglight_textures.py
"""
import os
import sys

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src/main/resources/assets/productivefrogs/textures/block")
TEMPLATE = os.path.join(BLOCK_TEX, "reinforced_soul_sand_froglight.png")
MASK = os.path.join(os.path.dirname(os.path.abspath(__file__)), "reinforced_frame_mask.png")

# theme -> 5-stop ramp, dark -> light (RGB)
RAMPS = {
    "reinforced_glowstone_froglight": [
        (94, 60, 26), (150, 100, 44), (198, 152, 62), (232, 196, 96), (252, 236, 160)],
    "reinforced_end_stone_froglight": [
        (126, 124, 90), (170, 168, 128), (204, 202, 162), (226, 224, 186), (242, 240, 208)],
    "reinforced_obsidian_froglight": [
        (12, 10, 22), (26, 20, 44), (46, 34, 74), (70, 54, 108), (100, 80, 148)],
    "reinforced_sculk_froglight": [
        (6, 26, 34), (10, 42, 52), (16, 60, 72), (24, 84, 98), (44, 138, 150)],
    "reinforced_echo_shard_froglight": [
        (14, 20, 38), (24, 34, 62), (38, 54, 94), (60, 82, 134), (96, 126, 178)],
    "reinforced_prismarine_froglight": [
        (36, 92, 88), (56, 122, 114), (80, 152, 140), (110, 182, 166), (144, 208, 192)],
    "reinforced_sponge_froglight": [
        (142, 122, 50), (182, 160, 68), (210, 188, 88), (232, 212, 112), (246, 234, 152)],
}


def build_mask(paths):
    imgs = [Image.open(p).convert("RGBA") for p in paths]
    w, h = imgs[0].size
    mask = Image.new("L", (w, h), 0)
    for x in range(w):
        for y in range(h):
            px = imgs[0].getpixel((x, y))
            if all(im.getpixel((x, y)) == px for im in imgs[1:]):
                mask.putpixel((x, y), 255)
    mask.save(MASK)
    frame = sum(1 for x in range(w) for y in range(h) if mask.getpixel((x, y)) == 255)
    print(f"mask saved: {frame}/{w * h} frame pixels")


def lum(px):
    return 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]


def ramp_color(ramp, t):
    t = max(0.0, min(1.0, t)) * (len(ramp) - 1)
    i = int(t)
    if i >= len(ramp) - 1:
        return ramp[-1]
    f = t - i
    a, b = ramp[i], ramp[i + 1]
    return tuple(round(a[c] + (b[c] - a[c]) * f) for c in range(3))


def bake():
    template = Image.open(TEMPLATE).convert("RGBA")
    mask = Image.open(MASK).convert("L")
    w, h = template.size
    fills = [(x, y) for x in range(w) for y in range(h) if mask.getpixel((x, y)) != 255]
    lums = [lum(template.getpixel(p)) for p in fills]
    lo, hi = min(lums), max(lums)
    span = (hi - lo) or 1.0
    for name, ramp in RAMPS.items():
        out = template.copy()
        for (x, y), l in zip(fills, lums):
            a = template.getpixel((x, y))[3]
            r, g, b = ramp_color(ramp, (l - lo) / span)
            out.putpixel((x, y), (r, g, b, a))
        path = os.path.join(BLOCK_TEX, f"{name}.png")
        out.save(path)
        print(f"baked {name}.png")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--remask":
        build_mask(sys.argv[2:])
    else:
        if not os.path.exists(MASK):
            sys.exit("frame mask missing - run with --remask <the four reinforced pngs>")
        bake()
