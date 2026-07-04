"""
Generate CANDIDATE textures for the predation Phase 3 blocks/items (#281)
into the review pipeline: gen/<slug>-<timestamp>/<idx>.png batches, compared
side-by-side (against vanilla refs + the currently shipped placeholders) by
gen/scripts/build_comparison_page.ps1. Nothing here touches assets/ - approved
winners are installed in a separate step.

Procedural Pillow art in the generate_terrarium_textures.py house style
(explicit palettes + motifs, no hue-shifted recolors). Texture follows the
recipe (maintainer convention):

    slurry_press      iron ingots + obsidian + ender pearl
                      -> obsidian machine body, iron press plate, pearl core
    mob_slurry_basin  obsidian + ender pearl
                      -> obsidian basin, teal pearl studs, purple slurry floor
    slime_milk_basin  packed mud + slime ball
                      -> packed-mud basin, moss rim, slime-green floor
    ender_net         string net + ender pearl (the Frog Net's sibling)
                      -> per-variant net recolor + a pearl bead at the hub
    mob_slurry_bucket -> bucket + per-variant slurry contents

Two design variants everywhere the identity is contestable:
    variant 0 = ender TEAL (the pearl's own colour)
    variant 1 = ender PURPLE (the enderman-eye colour)

Run from the repo root:  python scripts/generate_phase3_texture_candidates.py
Then rebuild the review page:  powershell gen/scripts/build_comparison_page.ps1
"""

import os
import sys
from PIL import Image, ImageDraw

sys.stdout.reconfigure(encoding="utf-8")

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(REPO, "gen")
ITEM = os.path.join(REPO, "src", "main", "resources", "assets", "productivefrogs", "textures", "item")
TS = "20260703-220000"

S = 16
T = (0, 0, 0, 0)

# Obsidian family (vanilla obsidian's violet-black facets)
OBS = (21, 14, 32, 255)
OBS_FACET = (44, 32, 62, 255)
OBS_HI = (74, 56, 96, 255)
OBS_DK = (11, 7, 18, 255)
# Iron press plate
IRON = (198, 198, 202, 255)
IRON_HI = (228, 228, 232, 255)
IRON_DK = (136, 136, 142, 255)
IRON_SHADOW = (96, 96, 102, 255)
# Ender accents - the two contested identities
TEAL = (52, 178, 138, 255)
TEAL_HI = (150, 236, 202, 255)
TEAL_DK = (18, 92, 70, 255)
PURPLE = (156, 107, 199, 255)
PURPLE_HI = (216, 178, 240, 255)
PURPLE_DK = (84, 44, 122, 255)
# Packed mud family
MUD = (142, 106, 79, 255)
MUD_HI = (163, 125, 94, 255)
MUD_DK = (116, 84, 61, 255)
MUD_LINE = (100, 72, 52, 255)
# Moss / slime
MOSS = (89, 125, 53, 255)
MOSS_HI = (118, 158, 74, 255)
SLIME = (112, 190, 82, 255)
SLIME_HI = (168, 230, 138, 255)
SLIME_DK = (70, 132, 50, 255)


def accents(variant):
    """(main, hi, dk) accent triple for variant 0=teal, 1=purple."""
    return (TEAL, TEAL_HI, TEAL_DK) if variant == 0 else (PURPLE, PURPLE_HI, PURPLE_DK)


def obsidian_body(seed=0):
    """A 16x16 obsidian tile: violet-black with angular lighter facets."""
    img = Image.new("RGBA", (S, S), OBS)
    d = ImageDraw.Draw(img)
    facets = [
        [(2, 1), (5, 1), (3, 4)], [(9, 2), (13, 3), (10, 6)],
        [(1, 8), (4, 10), (1, 12)], [(12, 9), (14, 12), (10, 13)],
        [(6, 11), (8, 14), (5, 14)],
    ]
    for i, tri in enumerate(facets):
        d.polygon([((x + seed) % S, y) for x, y in tri], fill=OBS_FACET)
    for i, (x, y) in enumerate([(4, 2), (10, 3), (2, 9), (13, 10), (7, 12)]):
        img.putpixel(((x + seed) % S, y), OBS_HI)
    # dark seams
    for x, y in [(0, 6), (1, 6), (7, 0), (7, 1), (15, 8), (14, 8), (8, 15), (9, 15)]:
        img.putpixel((x, y), OBS_DK)
    return img


def mud_body():
    """A 16x16 packed-mud tile: warm brown with soft clod lines."""
    img = Image.new("RGBA", (S, S), MUD)
    d = ImageDraw.Draw(img)
    for y0, x0, x1 in [(4, 0, 6), (4, 9, 15), (9, 3, 12), (13, 0, 8)]:
        d.line([(x0, y0), (x1, y0)], fill=MUD_LINE)
    for x, y in [(2, 2), (11, 2), (6, 6), (13, 7), (4, 11), (10, 11), (14, 14)]:
        img.putpixel((x, y), MUD_HI)
    for x, y in [(8, 3), (3, 7), (12, 12), (6, 14)]:
        img.putpixel((x, y), MUD_DK)
    return img


def iron_band(img, y0, y1):
    """Rivetted iron plate rows [y0, y1] across the tile."""
    d = ImageDraw.Draw(img)
    d.rectangle([0, y0, 15, y1], fill=IRON)
    d.line([(0, y0), (15, y0)], fill=IRON_HI)
    d.line([(0, y1), (15, y1)], fill=IRON_SHADOW)
    for x in (2, 13):
        img.putpixel((x, (y0 + y1) // 2), IRON_DK)
    return img


def pearl_core(img, cx, cy, variant, lit=False):
    """A round ender-pearl core at (cx, cy): 5px orb with highlight + glow."""
    main, hi, dk = accents(variant)
    d = ImageDraw.Draw(img)
    d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=dk)
    d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=main)
    img.putpixel((cx - 1, cy - 1), hi)
    if lit:
        for gx, gy in [(cx - 3, cy), (cx + 3, cy), (cx, cy - 3), (cx, cy + 3)]:
            if 0 <= gx < S and 0 <= gy < S:
                img.putpixel((gx, gy), hi)
    return img


# ---------------------------------------------------------------- press ----

def press_front(variant, working):
    img = obsidian_body()
    iron_band(img, 0, 2)
    d = ImageDraw.Draw(img)
    # the press mouth: recessed dark square with the pearl core behind it
    d.rectangle([4, 5, 11, 12], fill=OBS_DK)
    d.rectangle([4, 5, 11, 12], outline=OBS_HI)
    pearl_core(img, 8, 9, variant, lit=working)
    if working:
        main, hi, _ = accents(variant)
        # slurry drip under the mouth
        img.putpixel((8, 13), main)
        img.putpixel((8, 14), main)
        img.putpixel((7, 14), hi)
    return img


def press_side(variant, working):
    img = obsidian_body(seed=5)
    iron_band(img, 0, 2)
    main, hi, dk = accents(variant)
    d = ImageDraw.Draw(img)
    # two vent slits that glow while pressing
    for y in (7, 10):
        d.line([(5, y), (10, y)], fill=(hi if working else dk))
    return img


def press_top(variant, working):
    img = Image.new("RGBA", (S, S), IRON)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=IRON_SHADOW)
    d.rectangle([1, 1, 14, 14], outline=IRON_HI)
    # obsidian hatch in the middle - the mob goes in here
    d.rectangle([5, 5, 10, 10], fill=OBS)
    d.rectangle([5, 5, 10, 10], outline=OBS_HI)
    if working:
        main, hi, _ = accents(variant)
        d.rectangle([7, 7, 8, 8], fill=main)
        img.putpixel((7, 7), hi)
    for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        img.putpixel((x, y), IRON_DK)  # corner rivets
    return img


def press_bottom():
    img = obsidian_body(seed=9)
    ImageDraw.Draw(img).rectangle([0, 0, 15, 15], outline=OBS_DK)
    return img


# --------------------------------------------------------------- basins ----

def basin_side(body_img, rim_color, rim_hi, stud, variant=None):
    """Half-crucible mapping: the wall element (y 2..8) samples texture rows
    8..14 and the floor edge (y 0..2) rows 14..16 - so the art lives in the
    bottom half of the tile; the top half repeats the body for inner faces."""
    img = body_img.copy()
    d = ImageDraw.Draw(img)
    # rim band at the TOP OF THE WALL = texture rows 8..9
    d.rectangle([0, 8, 15, 9], fill=rim_color)
    d.line([(0, 8), (15, 8)], fill=rim_hi)
    # footing rows 14..15 darker
    for y in (14, 15):
        for x in range(16):
            r, g, b, a = img.getpixel((x, y))
            img.putpixel((x, y), (max(0, r - 30), max(0, g - 30), max(0, b - 30), a))
    if stud and variant is not None:
        main, hi, _ = accents(variant)
        for x in (3, 12):
            img.putpixel((x, 11), main)
            img.putpixel((x, 10), hi)
    return img


def basin_top(body_img, rim_hi):
    """The rim ring as seen from above: material with an inner-edge highlight."""
    img = body_img.copy()
    d = ImageDraw.Draw(img)
    d.rectangle([1, 1, 14, 14], outline=rim_hi)
    d.rectangle([0, 0, 15, 15], outline=(0, 0, 0, 70))
    return img


def basin_inside(body_img, pool, pool_hi, pool_dk):
    """The floor the charge sits on: material edges, liquid pool centre."""
    img = body_img.copy()
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 13, 13], fill=pool_dk)
    d.rectangle([3, 3, 12, 12], fill=pool)
    for x, y in [(5, 5), (9, 7), (6, 10)]:
        img.putpixel((x, y), pool_hi)
    return img


def slurry_basin_set(variant):
    main, hi, dk = accents(variant)
    body = obsidian_body(seed=3)
    return {
        "side": basin_side(body, OBS_FACET, OBS_HI, stud=True, variant=variant),
        "top": basin_top(obsidian_body(seed=7), OBS_HI),
        "inside": basin_inside(obsidian_body(seed=11), main, hi, dk),
        "bottom": press_bottom(),
    }


def milk_basin_set():
    body = mud_body()
    return {
        "side": basin_side(body, MOSS, MOSS_HI, stud=False),
        "top": basin_top(mud_body(), MOSS_HI),
        "inside": basin_inside(mud_body(), SLIME, SLIME_HI, SLIME_DK),
        "bottom": mud_body(),
    }


# ---------------------------------------------------------------- items ----

# Ender-pearl greens (the pearl's own palette) - maintainer ruling: the net is
# MOSTLY green, purple only as hint pixels. Recipe-faithful too: the net is
# crafted from a Frog Net + an ender pearl.
PEARL = (58, 158, 126, 255)
PEARL_HI = (142, 226, 184, 255)
PEARL_DK = (16, 84, 66, 255)


def shade_filled_bulge(img, px, interior, eyes):
    """Deliberate shading for the captured-mob bulge (the flat recolor read as
    a blob): edge pixels dark, a top-left highlight region, mid body elsewhere,
    and optionally two bright 'ender eyes' so it reads as something IN the net."""
    if not interior:
        return
    xs = [x for x, y in interior]
    ys = [y for x, y in interior]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    inset = set(interior)
    for x, y in interior:
        edge = any((x + dx, y + dy) not in inset for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            px[x, y] = PURPLE_DK
        else:
            # diagonal light: top-left brightest
            t = ((x - x0) / max(1, x1 - x0) + (y - y0) / max(1, y1 - y0)) / 2
            px[x, y] = PURPLE_HI if t < 0.30 else (PURPLE if t < 0.72 else PURPLE_DK)
    if eyes:
        ex = x0 + (x1 - x0) // 2
        ey = y0 + (y1 - y0) // 2
        for cx in (ex - 1, ex + 1):
            if (cx, ey) in inset:
                px[cx, ey] = (246, 240, 255, 255)


def ender_net(mesh_shade, filled):
    """Green RING with purple NET inside (maintainer ruling). The frog net's
    head pixels are classified geometrically: a visible non-wood pixel with a
    transparent 4-neighbour is the hoop RING (ender-pearl green ramp); interior
    pixels are the MESH (purple ramp). Wood handle kept.
    mesh_shade: 0 = mid purple mesh, 1 = darker mesh + a bright purple hub."""
    src = "frog_net_filled.png" if filled else "frog_net.png"
    img = Image.open(os.path.join(ITEM, src)).convert("RGBA").copy()
    px = img.load()

    def visible(x, y):
        return 0 <= x < S and 0 <= y < S and px[x, y][3] != 0

    def is_wood(x, y):
        r, g, b, a = px[x, y]
        mx, mn = max(r, g, b), min(r, g, b)
        sat = 0 if mx == 0 else (mx - mn) / mx
        return sat > 0.25 and r > g > b

    head = [(x, y) for y in range(S) for x in range(S) if visible(x, y) and not is_wood(x, y)]
    mesh_main = PURPLE if mesh_shade == 0 else PURPLE_DK
    mesh_hi = PURPLE_HI
    # The RING is the hoop itself: the outer annulus of the head disc. Thin
    # mesh strands also touch transparency, so adjacency can't tell them from
    # the hoop - distance from the head centroid can.
    cx = sum(x for x, y in head) / len(head)
    cy = sum(y for x, y in head) / len(head)
    maxr = max(((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 for x, y in head)
    interior = []
    for x, y in head:
        r, g, b, a = px[x, y]
        lum = (r * 299 + g * 587 + b * 114) // 1000
        ring = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 > maxr - 1.6
        if ring:
            px[x, y] = PEARL_HI if lum > 150 else (PEARL if lum > 80 else PEARL_DK)
        else:
            px[x, y] = mesh_hi if lum > 170 else (mesh_main if lum > 90 else PURPLE_DK)
            interior.append((x, y))
    if filled:
        # mesh_shade repurposed for the filled art: 0 = shaded bulge with ender
        # eyes, 1 = shaded bulge only.
        shade_filled_bulge(img, px, interior, eyes=(mesh_shade == 0))
    elif mesh_shade == 1 and interior:
        hx, hy = min(interior, key=lambda p: (p[0] - 8) ** 2 + (p[1] - 6) ** 2)
        px[hx, hy] = PURPLE_HI
    return img


def slurry_bucket(variant):
    base = Image.open(os.path.join(ITEM, "slime_milk_bucket.png")).convert("RGBA").copy()
    contents = Image.open(os.path.join(ITEM, "slime_milk_bucket_milk.png")).convert("RGBA")
    main, hi, dk = accents(variant)
    tinted = contents.copy()
    px = tinted.load()
    for y in range(S):
        for x in range(S):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (r * 299 + g * 587 + b * 114) // 1000
            px[x, y] = hi if lum > 200 else (main if lum > 110 else dk)
    base.alpha_composite(tinted)
    return base


# ----------------------------------------------------------------- main ----

def emit(slug, images):
    """Write one candidate batch dir gen/<slug>-<TS>/<idx>.png."""
    batch = os.path.join(GEN, f"{slug}-{TS}")
    os.makedirs(batch, exist_ok=True)
    for idx, img in enumerate(images):
        img.save(os.path.join(batch, f"{idx}.png"))
    print(f"{slug}: {len(images)} candidate(s)")


def main():
    # Press faces: candidate 0 = teal accents, 1 = purple accents.
    emit("slurry_press_front", [press_front(v, False) for v in (0, 1)])
    emit("slurry_press_front_working", [press_front(v, True) for v in (0, 1)])
    emit("slurry_press_side", [press_side(v, False) for v in (0, 1)])
    emit("slurry_press_side_working", [press_side(v, True) for v in (0, 1)])
    emit("slurry_press_top", [press_top(v, False) for v in (0, 1)])
    emit("slurry_press_top_working", [press_top(v, True) for v in (0, 1)])
    emit("slurry_press_bottom", [press_bottom()])

    # Mob Slurry Basin: teal-stud vs purple-stud walls, matching floors.
    for face in ("side", "top", "inside", "bottom"):
        emit(f"mob_slurry_basin_{face}", [slurry_basin_set(v)[face] for v in (0, 1)])
    # Slime Milk Basin: one design (mud + moss + slime - nothing contested).
    for face in ("side", "top", "inside", "bottom"):
        emit(f"slime_milk_basin_{face}", [milk_basin_set()[face]])

    # Items. Net candidates are the three purple-hint intensities (all
    # pearl-green dominant, per the maintainer ruling).
    emit("ender_net", [ender_net(v, False) for v in (0, 1)])
    emit("ender_net_filled", [ender_net(v, True) for v in (0, 1)])
    # mob_slurry_bucket: settled by ruling as base + greyscale contents layer
    # tinted per-mob at runtime (SlurriedEntityTint) - no colour candidates.

    print("done - rebuild the review page with: powershell gen/scripts/build_comparison_page.ps1")


if __name__ == "__main__":
    main()
