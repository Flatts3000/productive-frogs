#!/usr/bin/env python3
"""Generate the shared greyscale molten still/flow textures (v1.12).

Desaturates vanilla lava_still/lava_flow (preserving their animation strips +
.mcmeta timing) into productivefrogs:block/molten_still / molten_flow. One
greyscale set serves every PF molten metal - the per-metal colour is a render
-time tint from the variant's primary_color, exactly like the Slime Milk
texture model (see PFClientEvents).

Source: the extracted vanilla assets at %TEMP%/mc-extra (populated by
generate_resource_slime_textures.py / build_comparison_page.ps1; delete the
folder to force re-extraction from the moddev artifacts jar).

Run:  python scripts/generate_molten_textures.py
"""
import os
import shutil
import sys
import tempfile

from PIL import Image

REPO = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
SRC = os.path.join(tempfile.gettempdir(), "mc-extra", "assets", "minecraft", "textures", "block")
DEST = os.path.join(REPO, "src", "main", "resources", "assets", "productivefrogs", "textures", "block")

PAIRS = [
    ("lava_still.png", "molten_still.png"),
    ("lava_flow.png", "molten_flow.png"),
]


def main() -> int:
    if not os.path.isdir(SRC):
        sys.exit(f"vanilla extract not found at {SRC} - run generate_resource_slime_textures.py first")
    for src_name, dest_name in PAIRS:
        src = os.path.join(SRC, src_name)
        img = Image.open(src).convert("RGBA")
        # Luminance-greyscale, brightened toward the milk-texture range so the
        # multiplicative tint lands near the variant colour instead of muddy.
        grey = img.convert("LA").convert("RGBA")
        px = grey.load()
        for y in range(grey.height):
            for x in range(grey.width):
                r, g, b, a = px[x, y]
                v = min(255, int(r * 1.35) + 30)
                px[x, y] = (v, v, v, a)
        dest = os.path.join(DEST, dest_name)
        grey.save(dest)
        print(f"{src_name} -> {dest_name} ({grey.width}x{grey.height})")
        # Animation timing rides along verbatim.
        meta_src = src + ".mcmeta"
        if os.path.isfile(meta_src):
            shutil.copyfile(meta_src, os.path.join(DEST, dest_name + ".mcmeta"))
            print(f"  + {dest_name}.mcmeta")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
