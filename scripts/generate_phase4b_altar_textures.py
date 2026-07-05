"""Bake the Phase 4b altar block textures (#279 Warden / #280 Elder Guardian).

Same technique as generate_reinforced_froglight_textures.py: keep the shipped
wither-altar art's pixel structure (hatch / receptacle / capstone templates) and
remap each pixel's luminance onto a per-theme color ramp, so the new altars'
blocks read as siblings of the shipped ones with the deep-dark / monument
palette. APPROVED by the maintainer 2026-07-04 - these are the final textures;
re-run only to reproduce them, not to redesign.

Run from the repo root:  python scripts/generate_phase4b_altar_textures.py
"""
import os
import sys

from PIL import Image

sys.stdout.reconfigure(encoding="utf-8")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src/main/resources/assets/productivefrogs/textures/block")

# deep-dark: near-black blues up to sculk cyan glow
WARDEN_RAMP = [(4, 14, 20), (8, 28, 38), (14, 48, 60), (24, 78, 92), (54, 150, 160)]
# monument: dark prismarine up to pale sea-lantern teal
ELDER_RAMP = [(24, 60, 56), (44, 96, 88), (72, 136, 122), (110, 178, 160), (168, 224, 208)]

# output -> (template, ramp)
BAKES = {
    "warden_altar_hatch": ("wither_altar_hatch", WARDEN_RAMP),
    "shrieker_receptacle": ("soul_sand_receptacle", WARDEN_RAMP),
    "echoing_catalyst": ("withered_star", WARDEN_RAMP),
    "elder_altar_hatch": ("wither_altar_hatch", ELDER_RAMP),
    "tide_offering_receptacle": ("soul_sand_receptacle", ELDER_RAMP),
    "monument_core": ("withered_star", ELDER_RAMP),
}


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


def bake(name, template_name, ramp):
    template = Image.open(os.path.join(BLOCK_TEX, f"{template_name}.png")).convert("RGBA")
    w, h = template.size
    pixels = [(x, y) for x in range(w) for y in range(h)]
    lums = [lum(template.getpixel(p)) for p in pixels]
    lo, hi = min(lums), max(lums)
    span = (hi - lo) or 1.0
    out = template.copy()
    for (x, y), l in zip(pixels, lums):
        a = template.getpixel((x, y))[3]
        r, g, b = ramp_color(ramp, (l - lo) / span)
        out.putpixel((x, y), (r, g, b, a))
    out.save(os.path.join(BLOCK_TEX, f"{name}.png"))
    print(f"baked {name}.png (from {template_name})")


if __name__ == "__main__":
    for name, (template_name, ramp) in BAKES.items():
        bake(name, template_name, ramp)
