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

Re-run after adding a variant or changing an `inner_block`. Requires Pillow (PIL)
and the vanilla assets jar under build/moddev/artifacts (run any gradle task on
THIS checkout first).

Idempotent in the strict sense: a run whose output matches the committed art
writes nothing at all, so it never produces a diff you have to squint at. Pass
`--check` to report what a bake WOULD change and exit non-zero without writing -
suitable as a CI gate for "someone changed an inner_block and forgot to re-bake".

The vanilla jar is chosen by the MC version inside its own `version.json`, not by
its filename, and the extraction cache records which jar it came from and is
rebuilt when that changes. Both matter: the two MC lines share a temp directory
and their build dirs can hold each other's jars, so name matching and a
reused-on-sight cache each let one line silently bake from the other's art.
"""

import json
import os
import shutil
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
ARTIFACTS = os.path.join(REPO, "build", "moddev", "artifacts")
# Present in every jar that carries vanilla assets. NOT a way to tell an assets
# jar from a sources jar - the sources jar ships assets too - so it only filters
# out jars with no assets at all. Choosing between the qualifying ones is
# jar_rank's job.
SENTINEL = "assets/minecraft/textures/block/stone.png"
# Several jars in one artifacts dir can carry the same MC version's assets (on
# 26.1: merged 45MB, plain 35MB, sources 20MB). Without an explicit order the
# winner falls out of alphabetical sort, so a future moddev artifact name could
# silently change which jar gets extracted. Rank by role, then smallest first -
# the resources-only jar is both the right one and the cheapest to unpack, and
# the sources jar is a last resort since it is mostly Java.
def jar_rank(jar):
    name = os.path.basename(jar)
    if "client-extra" in name:
        role = 0
    elif "-sources" in name:
        role = 2
    else:
        role = 1
    return (role, os.path.getsize(jar))
# The extraction directory is a SHARED CONTRACT: generate_molten_textures.py,
# generate_virtual_terrarium_gui.py and four .ps1 generators all read this exact
# path and tell the user to run this script to populate it. So the path stays put
# and the staleness is fixed with a marker instead: the jar it came from is
# recorded, and a directory from a different jar is re-extracted rather than
# reused. That is what stops the two MC lines - which share a temp dir - from
# deciding what the other one bakes from.
MC_EXTRACT = os.path.join(tempfile.gettempdir(), "mc-extra")
# Written last, so an extraction killed part-way leaves no marker and is redone
# rather than cached forever. Matters more since the 26.1 jar is 36k files.
EXTRACT_MARKER = os.path.join(MC_EXTRACT, ".pf-source-jar")

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


def minecraft_version():
    """The MC version this checkout targets, from gradle.properties."""
    with open(os.path.join(REPO, "gradle.properties"), encoding="utf-8") as fh:
        for line in fh:
            if line.startswith("minecraft_version="):
                return line.split("=", 1)[1].strip()
    return None


def jar_minecraft_version(jar):
    """The MC version a vanilla jar actually contains, or None.

    Read from the jar's own `version.json` rather than inferred from its name.
    The NeoForge resources jar is named for the NEOFORGE version
    (`neoforge-21.1.230-client-extra-...`), which shares no digits with the MC
    version it carries (1.21.1), so name matching silently fails on that line.
    """
    try:
        with zipfile.ZipFile(jar) as z:
            if SENTINEL not in z.namelist():
                return None
            return json.loads(z.read("version.json")).get("id")
    except (zipfile.BadZipFile, KeyError, ValueError):
        return None


def find_assets_jar():
    """The vanilla-assets jar matching this checkout's MC version.

    The old code globbed `neoforge-*-client-extra-*.jar` and took the first hit.
    On the 26.1 line the only jar matching that name is the leftover
    neoforge-21.1.230 one from the 1.21.1 era, so the baker read 1.21.1 art -
    silently, because every other step still succeeds. Refusing to guess is the
    point: baking from the wrong version is not something a contributor can spot
    in the output.
    """
    want = minecraft_version()
    found = {}
    for jar in sorted(glob.glob(os.path.join(ARTIFACTS, "*.jar"))):
        version = jar_minecraft_version(jar)
        if version:
            found.setdefault(version, []).append(jar)
    if not found:
        sys.exit("No vanilla assets jar under build/moddev/artifacts. Run a gradle task first.")
    if want in found:
        return sorted(found[want], key=jar_rank)[0]
    available = ", ".join(
        f"{v} ({os.path.basename(js[0])})" for v, js in sorted(found.items()))
    sys.exit("\n".join([
        f"No vanilla assets jar for minecraft_version={want}. Found: {available}.",
        "Run a gradle task on THIS checkout first; baking from another version's",
        "art is silent and wrong.",
    ]))


def ensure_assets():
    """Extract the chosen jar into the shared cache and return its block dir.

    Re-extracts whenever the cache came from a different jar, or from an
    extraction that did not finish. The old code reused the directory on nothing
    more than its existence, which is how one MC line ended up baking from the
    other's art and how a torn extraction would have been cached indefinitely.
    """
    jar = find_assets_jar()
    name = os.path.basename(jar)
    block_dir = os.path.join(MC_EXTRACT, "assets", "minecraft", "textures", "block")

    current = None
    if os.path.isfile(EXTRACT_MARKER):
        with open(EXTRACT_MARKER, encoding="utf-8") as fh:
            current = fh.read().strip()
    if current == name and os.path.isdir(block_dir):
        print(f"vanilla assets: {name} (cached)")
        return block_dir

    if os.path.isdir(MC_EXTRACT):
        why = "incomplete" if current is None else f"came from {current}"
        print(f"discarding {MC_EXTRACT} ({why})")
        shutil.rmtree(MC_EXTRACT)
    os.makedirs(MC_EXTRACT, exist_ok=True)
    print(f"extracting {name} -> {MC_EXTRACT}")
    with zipfile.ZipFile(jar) as z:
        z.extractall(MC_EXTRACT)
    # Written LAST: a run killed mid-extract leaves no marker, so the next run
    # discards the partial tree instead of baking from it.
    with open(EXTRACT_MARKER, "w", encoding="utf-8") as fh:
        fh.write(name)
    return block_dir


def find_block_texture(block_dir, block_id):
    """block_id like 'minecraft:iron_block' -> a 16x16 RGBA tile, or None."""
    path = block_id.split(":", 1)[-1]
    candidates = []
    if path in TEXTURE_OVERRIDE:
        candidates.append(TEXTURE_OVERRIDE[path] + ".png")
    for suf in SUFFIXES:
        candidates.append(path + suf + ".png")
    for c in candidates:
        full = os.path.join(block_dir, c)
        if os.path.isfile(full):
            img = Image.open(full).convert("RGBA")
            # Animated textures (e.g. magma, sea_lantern) are a vertical strip;
            # take the top 16x16 frame.
            if img.height > img.width:
                img = img.crop((0, 0, img.width, img.width))
            return img.resize((FACE, FACE), Image.LANCZOS), c
    return None, None


def save_if_changed(img, path, check_only=False):
    """Write only when the PIXELS differ. Returns True if the file was written.

    Re-encoding an identical image does not produce identical BYTES - the
    committed textures were written by a different Pillow than whatever runs
    today, so an unconditional save rewrote every file with byte-different,
    pixel-identical PNGs. That made a documented manual step emit a wall of
    meaningless diffs, which is how a genuinely wrong bake would hide: nobody
    reads a diff that is noisy on every single run. Comparing pixels makes the
    script idempotent regardless of which Pillow is installed.
    """
    if os.path.isfile(path):
        with Image.open(path) as existing:
            if list(existing.convert("RGBA").getdata()) == list(img.convert("RGBA").getdata()):
                return False
    if not check_only:
        img.save(path)
    return True


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


# Variants whose slime texture is baked by a DEDICATED script instead of from an
# `inner_block`. They deliberately ship no `inner_block`, so this baker skips them -
# without this list they show up under MISSING, and "fixing" that by adding an
# `inner_block` would silently overwrite their bespoke art on the next run.
BESPOKE = {"rainbow": "generate_rainbow_slime_texture.py"}


def main():
    # --check reports what a bake WOULD change and writes nothing, exiting
    # non-zero if anything is stale. Now that a run is a no-op when the committed
    # art is current, that is a usable CI gate: it catches a variant whose
    # inner_block changed without the texture being re-baked, which is otherwise
    # invisible until someone looks at the slime in-game.
    args = sys.argv[1:]
    unknown = [a for a in args if a != "--check"]
    if unknown:
        # Silently ignoring these meant a typo'd CI invocation ("--dry-run", "-n")
        # performed a real write over 38 committed textures and exited 0 - the
        # exact opposite of what the caller asked for.
        sys.exit(f"unknown argument(s): {' '.join(unknown)} (only --check is supported)")
    check_only = "--check" in args
    block_dir = ensure_assets()
    template = load_outer_template()
    made = 0
    unchanged = 0
    missing = []
    unresolved = []
    bespoke = []
    for jf in sorted(glob.glob(os.path.join(VARIANT_JSON_DIR, "*.json"))):
        variant = os.path.splitext(os.path.basename(jf))[0]
        data = json.load(open(jf, encoding="utf-8"))
        inner = data.get("inner_block")
        if not inner:
            if variant in BESPOKE:
                bespoke.append(f"{variant} <- {BESPOKE[variant]}")
            else:
                missing.append(f"{variant} (no inner_block)")
            continue
        tile, used = find_block_texture(block_dir, inner)
        if tile is None:
            unresolved.append(f"{variant} -> {inner}")
            continue
        out = template.copy()
        for ox, oy in FACE_ORIGINS:
            out.paste(tile, (ox, oy))
        if save_if_changed(out, os.path.join(SLIME_TEX_DIR, f"{variant}_resource_slime.png"),
                           check_only):
            made += 1
            print(f"{variant:20s} <- {used}")
        else:
            unchanged += 1
    verb = "would rewrite" if check_only else "wrote"
    print()
    print(f"{verb} {made} per-variant slime textures ({unchanged} already current)")
    if bespoke:
        print("SKIPPED (bespoke texture, baked by its own script - do NOT add an inner_block):")
        for b in bespoke:
            print(f"  - {b}")
    if missing:
        print("MISSING (left to category fallback):")
        for m in missing:
            print(f"  - {m}")
    if unresolved:
        # Distinct from MISSING above, and always an error: the variant DID name an
        # inner_block and no texture could be found for it, so any previously baked
        # PNG is now stale and still committed. Lumping these in with the legitimate
        # no-inner_block fallbacks is how a mistyped block id stays green forever.
        print("UNRESOLVED inner_block (no vanilla texture found - the committed "
              "texture is now stale):")
        for u in unresolved:
            print(f"  - {u}")
    # An unresolved inner_block is wrong in either mode. Staleness is only an
    # error under --check, since a normal run is what fixes it.
    return 1 if (unresolved or (check_only and made)) else 0


if __name__ == "__main__":
    sys.exit(main())
