#!/usr/bin/env python3
"""Bake the predation Phase 3 textures (#281): the Ender Net, the Slurry
Press, the two Basins, the Mob Slurry bucket, and the Slurry Press GUI.

All derived procedurally from existing art so the new blocks sit in the
appliance family:
  - ender_net(_filled): the frog_net art hue-shifted to the ender palette
    (string -> purple, keeping luminance).
  - slurry_press_*: the slime_churn set darkened + purple-shifted (obsidian
    press with ender accents), idle + working.
  - mob_slurry_basin_* / slime_milk_basin_*: procedural stone basin walls;
    the inside floor carries the contents hue (ender purple / slime green).
  - mob_slurry_bucket: the shared bucket base + contents overlay tinted the
    slurry purple (PFClientEvents.MOB_SLURRY_PURPLE).
  - gui/container/slurry_press.png: the slime_churn GUI (identical layout -
    same slot geometry, same arrow inset) with the panel hue nudged purple.

Run from the repo root:  python scripts/generate_phase3_textures.py
"""

import colorsys
import sys
from pathlib import Path

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "src/main/resources/assets/productivefrogs/textures"
ITEM = ASSETS / "item"
BLOCK = ASSETS / "block"
GUI = ASSETS / "gui/container"

SLURRY_PURPLE_HUE = 270 / 360.0
SLIME_GREEN_HUE = 100 / 360.0


def hue_shift(img: Image.Image, hue: float, sat: float = 0.45, value_mul: float = 1.0) -> Image.Image:
    """Re-hue every visible pixel to `hue`, keeping per-pixel luminance."""
    out = img.convert("RGBA").copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            nr, ng, nb = colorsys.hsv_to_rgb(hue, sat, min(1.0, v * value_mul))
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return out


def basin_texture_set(name: str, inside_rgb: tuple[int, int, int]) -> None:
    """Procedural stone basin: noisy grey walls, darker bottom, rim top, tinted inside."""
    import random
    rng = random.Random(name)  # deterministic per block

    def stone(base: int, spread: int) -> Image.Image:
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y in range(16):
            for x in range(16):
                v = base + rng.randint(-spread, spread)
                px[x, y] = (v, v, min(255, v + 4), 255)
        return img

    side = stone(96, 10)
    # a darker footing band + a rim highlight row so the wall reads as a basin
    spx = side.load()
    for x in range(16):
        for y in (0, 1):
            r, g, b, a = spx[x, y]
            spx[x, y] = (min(255, r + 24), min(255, g + 24), min(255, b + 24), a)
        for y in (14, 15):
            r, g, b, a = spx[x, y]
            spx[x, y] = (max(0, r - 28), max(0, g - 28), max(0, b - 28), a)
    bottom = stone(72, 8)
    top = stone(108, 8)
    inside = stone(84, 6)
    ipx = inside.load()
    ir, ig, ib = inside_rgb
    for y in range(16):
        for x in range(16):
            r, g, b, a = ipx[x, y]
            # multiply toward the contents hue so the floor hints what it holds
            ipx[x, y] = (r * ir // 255, g * ig // 255, b * ib // 255, a)
    side.save(BLOCK / f"{name}_side.png")
    bottom.save(BLOCK / f"{name}_bottom.png")
    top.save(BLOCK / f"{name}_top.png")
    inside.save(BLOCK / f"{name}_inside.png")
    print(f"wrote {name} block set")


def main() -> None:
    # Ender Net: frog net re-hued to ender purple.
    for src, dest in [("frog_net.png", "ender_net.png"), ("frog_net_filled.png", "ender_net_filled.png")]:
        img = Image.open(ITEM / src)
        hue_shift(img, SLURRY_PURPLE_HUE, sat=0.55).save(ITEM / dest)
        print(f"wrote {dest}")

    # Slurry Press: the churn set darkened + purple-shifted (idle + working).
    for suffix in ["top", "bottom", "side", "front", "top_working", "side_working", "front_working"]:
        src = BLOCK / f"slime_churn_{suffix}.png"
        img = Image.open(src)
        hue_shift(img, SLURRY_PURPLE_HUE, sat=0.40, value_mul=0.62).save(BLOCK / f"slurry_press_{suffix}.png")
        print(f"wrote slurry_press_{suffix}.png")

    # Basins.
    basin_texture_set("mob_slurry_basin", (0x9C, 0x6B, 0xC7))
    basin_texture_set("slime_milk_basin", (0x5D, 0xDE, 0x36))

    # Mob Slurry bucket: bucket base + purple contents (the LE bucket recipe).
    base = Image.open(ITEM / "slime_milk_bucket.png").convert("RGBA")
    contents = Image.open(ITEM / "slime_milk_bucket_milk.png").convert("RGBA")
    tinted = contents.copy()
    px = tinted.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            px[x, y] = (r * 0x9C // 255, g * 0x6B // 255, b * 0xC7 // 255, a)
    out = base.copy()
    out.alpha_composite(tinted)
    out.save(ITEM / "mob_slurry_bucket.png")
    print("wrote mob_slurry_bucket.png")

    # Slurry Press GUI: the churn GUI (identical layout) nudged purple.
    gui = Image.open(GUI / "slime_churn.png").convert("RGBA")
    px = gui.load()
    for y in range(gui.height):
        for x in range(gui.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            # gentle purple cast: lift blue+red slightly, drop green
            px[x, y] = (min(255, r + 6), max(0, g - 6), min(255, b + 12), a)
    gui.save(GUI / "slurry_press.png")
    print("wrote gui slurry_press.png")


if __name__ == "__main__":
    main()
