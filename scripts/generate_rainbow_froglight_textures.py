#!/usr/bin/env python3
"""Bake the Rainbow Froglight's block textures.

Every other Configurable Froglight variant renders vanilla's ochre froglight
textures multiplied by the variant's `primary_color` - one texture, N colors.
That mechanism can only ever produce a FLAT color, so it cannot express
"rainbow": the whole point of the variant is more than one hue in one block.

So the rainbow variant gets real textures instead, and its models carry no
`tintindex` at all - what is baked here is what renders.

Method: take vanilla's ochre froglight side/top sprites and keep their VALUE
channel exactly (that is where the froglight's cell-and-glow structure lives),
then replace HUE by pixel position and lift saturation to a floor so pale cells
still take the color. The result reads as the same material as every other
Froglight in the mod, just spectrum-swept rather than single-hue.

  * side  - hue sweeps top-to-bottom, so a placed block reads as a vertical
            rainbow band and a stacked column repeats it cleanly.
  * top   - hue sweeps along the diagonal, matching the Rainbow Slime's inner
            cube (`generate_rainbow_slime_texture.py`) so the two read as one
            material.

Also writes a zoomed side-by-side preview to `gen/` for eyeballing without
launching the client.

Idempotent. Requires Pillow. Reads vanilla assets out of the Minecraft jar under
`build/moddev/artifacts` (run any gradle task first), preferring the jar that
matches `minecraft_version` in gradle.properties so a stale jar from the other
MC line cannot silently supply the source art.
"""
import colorsys
import glob
import os
import sys
import zipfile

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ARTIFACTS = os.path.join(REPO, "build", "moddev", "artifacts")
BLOCK_TEX = os.path.join(REPO, "src/main/resources/assets/productivefrogs/textures/block")
GEN = os.path.join(REPO, "gen")

SIDE_SRC = "assets/minecraft/textures/block/ochre_froglight_side.png"
TOP_SRC = "assets/minecraft/textures/block/ochre_froglight_top.png"

# The froglight's cell-and-glow structure lives almost entirely in SATURATION,
# not value (measured on vanilla's sprites: S sd 0.156 / V sd 0.051 on the side).
# So saturation is SHIFTED and scaled rather than floored - a flat floor pins
# every pale pixel to the same value and erases the material, leaving a plain
# gradient that no longer reads as a Froglight. Base lifts the near-white core
# far enough to take a hue; gain keeps the original variance visible.
SAT_BASE = 0.35
SAT_GAIN = 1.15


def minecraft_version():
    props = os.path.join(REPO, "gradle.properties")
    with open(props, encoding="utf-8") as fh:
        for line in fh:
            if line.startswith("minecraft_version="):
                return line.split("=", 1)[1].strip()
    return None


def find_assets_jar():
    """A jar containing the vanilla froglight sprites, preferring this line's version."""
    version = minecraft_version()
    candidates = sorted(glob.glob(os.path.join(ARTIFACTS, "*.jar")))
    # A stale jar from the other MC line also carries these sprites, so rank by
    # version match rather than taking whatever sorts first.
    candidates.sort(key=lambda p: (version or "") not in os.path.basename(p))
    for jar in candidates:
        try:
            with zipfile.ZipFile(jar) as z:
                if SIDE_SRC in z.namelist():
                    return jar
        except zipfile.BadZipFile:
            continue
    sys.exit("No jar with the vanilla froglight sprites under build/moddev/artifacts. "
             "Run any gradle task first (it populates that directory).")


def sweep(img, hue_at):
    """Keep each pixel's value, replace its hue by position, shift its saturation."""
    out = img.convert("RGBA").copy()
    width, height = out.size
    for y in range(height):
        for x in range(width):
            r, g, b, a = out.getpixel((x, y))
            if a == 0:
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            sat = min(1.0, SAT_BASE + s * SAT_GAIN)
            nr, ng, nb = colorsys.hsv_to_rgb(hue_at(x, y, width, height), sat, v)
            out.putpixel((x, y), (int(nr * 255), int(ng * 255), int(nb * 255), a))
    return out


def side_hue(x, y, width, height):
    """Top-to-bottom sweep: a placed block is a vertical rainbow band."""
    return (y / height) % 1.0


def top_hue(x, y, width, height):
    """Diagonal sweep, matching the Rainbow Slime's inner cube."""
    return ((x + y) / (width + height - 2)) % 1.0


def preview(pairs):
    """Zoomed before/after strip in gen/ so the bake can be judged without a client."""
    os.makedirs(GEN, exist_ok=True)
    scale, pad = 12, 8
    tiles = [im for pair in pairs for im in pair]
    w = sum(im.width for im in tiles) * scale + pad * (len(tiles) + 1)
    h = max(im.height for im in tiles) * scale + pad * 2
    sheet = Image.new("RGBA", (w, h), (32, 32, 36, 255))
    x = pad
    for im in tiles:
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        sheet.paste(big, (x, pad), big)
        x += big.width + pad
    path = os.path.join(GEN, "_rainbow_froglight_preview.png")
    sheet.save(path)
    print(f"preview -> {path}")


def main() -> None:
    jar = find_assets_jar()
    print(f"vanilla assets: {os.path.basename(jar)}")
    os.makedirs(BLOCK_TEX, exist_ok=True)

    with zipfile.ZipFile(jar) as z:
        with z.open(SIDE_SRC) as fh:
            side_src = Image.open(fh).convert("RGBA").copy()
        with z.open(TOP_SRC) as fh:
            top_src = Image.open(fh).convert("RGBA").copy()

    side = sweep(side_src, side_hue)
    top = sweep(top_src, top_hue)

    side.save(os.path.join(BLOCK_TEX, "rainbow_froglight_side.png"))
    top.save(os.path.join(BLOCK_TEX, "rainbow_froglight_top.png"))
    print(f"wrote rainbow_froglight_side.png ({side.width}x{side.height})")
    print(f"wrote rainbow_froglight_top.png ({top.width}x{top.height})")

    preview([(side_src, side), (top_src, top)])


if __name__ == "__main__":
    main()
