#!/usr/bin/env python3
"""Bake the APPROVED predation Phase 3 textures (#281) into assets.

The maintainer reviewed candidates on the gen/ comparison page
(scripts/generate_phase3_texture_candidates.py builds them) and approved,
2026-07-03:

    ender_net / ender_net_filled   candidate 1  (green ring, darker purple
                                   mesh; filled = shaded bulge, no eyes)
    slurry_press (7 faces)         candidate 0  (obsidian + iron plate, TEAL
                                   pearl core + vents)
    mob_slurry_basin (4 faces)     candidate 0  (obsidian basin, TEAL studs
                                   + pool floor)
    slime_milk_basin (4 faces)     the single design (packed mud, moss rim,
                                   slime-green pool)
    mob_slurry_bucket              settled by ruling: bucket base + greyscale
                                   contents layer, tinted per-mob at runtime
                                   (SlurriedEntityTint) - baked separately in
                                   this script.
    slurry_press GUI               the recolored churn layout (same slot
                                   geometry, purple cast).

This script re-derives that exact set from the candidate generator's drawing
functions, so the shipped art is reproducible from source. Re-run after
editing the generator:  python scripts/generate_phase3_textures.py
"""

import importlib.util
import sys
from pathlib import Path

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "src/main/resources/assets/productivefrogs/textures"
ITEM = ASSETS / "item"
BLOCK = ASSETS / "block"
GUI = ASSETS / "gui/container"

spec = importlib.util.spec_from_file_location(
    "phase3_candidates", REPO / "scripts/generate_phase3_texture_candidates.py")
cand = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cand)

TEAL = 0  # approved accent variant for press + slurry basin


def main() -> None:
    # Ender Net: approved candidate 1 (darker mesh / plain shaded bulge).
    cand.ender_net(1, False).save(ITEM / "ender_net.png")
    cand.ender_net(1, True).save(ITEM / "ender_net_filled.png")
    print("wrote ender_net + ender_net_filled")

    # Slurry Press: approved candidate 0 (teal accents), all seven faces.
    cand.press_front(TEAL, False).save(BLOCK / "slurry_press_front.png")
    cand.press_front(TEAL, True).save(BLOCK / "slurry_press_front_working.png")
    cand.press_side(TEAL, False).save(BLOCK / "slurry_press_side.png")
    cand.press_side(TEAL, True).save(BLOCK / "slurry_press_side_working.png")
    cand.press_top(TEAL, False).save(BLOCK / "slurry_press_top.png")
    cand.press_top(TEAL, True).save(BLOCK / "slurry_press_top_working.png")
    cand.press_bottom().save(BLOCK / "slurry_press_bottom.png")
    print("wrote slurry_press set (teal)")

    # Basins: slurry = approved candidate 0 (teal); milk = the single design.
    slurry = cand.slurry_basin_set(TEAL)
    milk = cand.milk_basin_set()
    for face in ("side", "top", "inside", "bottom"):
        slurry[face].save(BLOCK / f"mob_slurry_basin_{face}.png")
        milk[face].save(BLOCK / f"slime_milk_basin_{face}.png")
    print("wrote both basin sets")

    # Mob Slurry bucket: base bucket + normalized-greyscale contents (tinted
    # per-mob at runtime by SlurriedEntityTint).
    base = Image.open(ITEM / "slime_milk_bucket.png").convert("RGBA")
    base.save(ITEM / "mob_slurry_bucket.png")
    contents = Image.open(ITEM / "slime_milk_bucket_milk.png").convert("RGBA").copy()
    px = contents.load()
    lums = [0.299 * r + 0.587 * g + 0.114 * b
            for y in range(16) for x in range(16)
            for r, g, b, a in [px[x, y]] if a > 0]
    scale = 255.0 / max(lums) if lums else 1.0
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            if a > 0:
                v = min(255, round((0.299 * r + 0.587 * g + 0.114 * b) * scale))
                px[x, y] = (v, v, v, a)
    contents.save(ITEM / "mob_slurry_bucket_contents.png")
    print("wrote mob_slurry_bucket base + contents")

    # Slurry Press GUI: the churn GUI layout (identical slot geometry) with a
    # gentle purple cast.
    gui = Image.open(GUI / "slime_churn.png").convert("RGBA")
    px = gui.load()
    for y in range(gui.height):
        for x in range(gui.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            px[x, y] = (min(255, r + 6), max(0, g - 6), min(255, b + 12), a)
    gui.save(GUI / "slurry_press.png")
    print("wrote gui slurry_press.png")


if __name__ == "__main__":
    main()
