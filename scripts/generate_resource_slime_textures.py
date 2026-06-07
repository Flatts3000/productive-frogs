"""Generate per-variant Resource Slime entity textures.

Design (post inner-block-render simplification, 2026-05-25)
-----------------------------------------------------------
The v1.0.1 "live block rendered inside the slime" approach was removed: an
opaque block drawn in a separate render pass is depth-culled by the slime's
translucent shell, so it never showed (the per-category coloured inner cube was
what players actually saw). The reliable surface is the slime model's own inner
body cube, which renders as part of the translucent entity.

So we bake the resource's look straight into the texture:
  * OUTER cube region (y < 16): neutral grey jelly. `ResourceSlimeOuterLayer`
    multiplies this by the variant's primary_color, so the exterior is tinted
    per-variant at render time. Leave it grey here.
  * INNER cube region (y >= 16): a downscaled copy of the variant's vanilla
    `inner_block` texture, tiled onto all six 6x6 inner-cube faces. This is the
    "downscaled vanilla resource block as the interior" look.

`ResourceSlimeRenderer.getTextureLocation` returns the per-variant texture
(`<variant>_resource_slime.png`) when the slime carries a variant, falling back
to the per-category `<category>_resource_slime.png` for variant-less slimes.

Inner-cube face rectangles (standard 6x6x6 box UV at texOffs(0,16) in the 64x32
slime layout), each 6x6:
  y[16,22): (6,16) down, (12,16) up
  y[22,28): (0,22) east, (6,22) north, (12,22) west, (18,22) south

Re-run after adding a variant or changing an `inner_block`. Idempotent.
Requires Pillow (PIL) and the extracted vanilla assets (run the mod once / any
gradle task that populates build/moddev/artifacts, same as the milk generator).
"""

import json
import os
import sys
import tempfile
import zipfile
import glob
from PIL import Image

REPO = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
VARIANT_JSON_DIR = os.path.join(
    REPO, "src", "main", "resources", "data", "productivefrogs",
    "productivefrogs", "slime_variant",
)
SLIME_TEX_DIR = os.path.join(
    REPO, "src", "main", "resources", "assets", "productivefrogs",
    "textures", "entity", "slime",
)
MC_EXTRACT = os.path.join(tempfile.gettempdir(), "mc-extra")
BLOCK_TEX_DIR = os.path.join(MC_EXTRACT, "assets", "minecraft", "textures", "block")
ARTIFACTS = os.path.join(REPO, "build", "moddev", "artifacts")

# Six inner-cube face rectangles (x0, y0) top-left, each 6x6.
FACE_ORIGINS = [(6, 16), (12, 16), (0, 22), (6, 22), (12, 22), (18, 22)]
FACE = 6

# inner_block ids whose primary texture file is not "<path>.png".
# (Most storage blocks are <path>.png; these are the exceptions.)
TEXTURE_OVERRIDE = {
    "magma_block": "magma",
    # snow_block's vanilla texture file is block/snow.png (no snow_block.png),
    # so the snow variant baked to the category fallback until this was added.
    "snow_block": "snow",
}
# Suffixes to try when "<path>.png" is absent (top/side blocks).
SUFFIXES = ["", "_side", "_top", "_front", "_0"]


def ensure_assets():
    if os.path.isdir(BLOCK_TEX_DIR):
        return
    jars = sorted(
        glob.glob(os.path.join(ARTIFACTS, "neoforge-*-client-extra-aka-minecraft-resources.jar")),
        key=os.path.getmtime, reverse=True,
    )
    if not jars:
        sys.exit("No minecraft-resources jar under build/moddev/artifacts. Run a gradle task first.")
    os.makedirs(MC_EXTRACT, exist_ok=True)
    print(f"extracting {os.path.basename(jars[0])} -> {MC_EXTRACT}")
    with zipfile.ZipFile(jars[0]) as z:
        z.extractall(MC_EXTRACT)


def find_block_texture(block_id):
    """block_id like 'minecraft:iron_block' -> a 16x16 RGBA tile, or None."""
    path = block_id.split(":", 1)[-1]
    candidates = []
    if path in TEXTURE_OVERRIDE:
        candidates.append(TEXTURE_OVERRIDE[path] + ".png")
    for suf in SUFFIXES:
        candidates.append(path + suf + ".png")
    for c in candidates:
        full = os.path.join(BLOCK_TEX_DIR, c)
        if os.path.isfile(full):
            img = Image.open(full).convert("RGBA")
            # Animated textures (e.g. magma, sea_lantern) are a vertical strip;
            # take the top 16x16 frame.
            if img.height > img.width:
                img = img.crop((0, 0, img.width, img.width))
            return img.resize((FACE, FACE), Image.LANCZOS), c
    return None, None


def load_outer_template():
    """A cleared category texture supplies the grey tintable outer shell."""
    for name in ("cave_resource_slime.png", "bog_resource_slime.png"):
        p = os.path.join(SLIME_TEX_DIR, name)
        if os.path.isfile(p):
            tpl = Image.open(p).convert("RGBA")
            # Wipe the inner-cube band so only the grey outer shell remains.
            px = tpl.load()
            for y in range(16, tpl.height):
                for x in range(tpl.width):
                    px[x, y] = (0, 0, 0, 0)
            return tpl
    sys.exit("No category slime texture to use as outer-shell template.")


def main():
    ensure_assets()
    template = load_outer_template()
    made = 0
    missing = []
    for jf in sorted(glob.glob(os.path.join(VARIANT_JSON_DIR, "*.json"))):
        variant = os.path.splitext(os.path.basename(jf))[0]
        data = json.load(open(jf, encoding="utf-8"))
        inner = data.get("inner_block")
        if not inner:
            missing.append(f"{variant} (no inner_block)")
            continue
        tile, used = find_block_texture(inner)
        if tile is None:
            missing.append(f"{variant} -> {inner} (texture not found)")
            continue
        out = template.copy()
        for ox, oy in FACE_ORIGINS:
            out.paste(tile, (ox, oy))
        out.save(os.path.join(SLIME_TEX_DIR, f"{variant}_resource_slime.png"))
        made += 1
        print(f"{variant:20s} <- {used}")
    print(f"\nwrote {made} per-variant slime textures")
    if missing:
        print("MISSING (left to category fallback):")
        for m in missing:
            print(f"  - {m}")


if __name__ == "__main__":
    main()
