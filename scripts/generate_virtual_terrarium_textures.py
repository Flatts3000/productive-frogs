"""
Generate the Virtual Terrarium UPGRADE ITEM textures (16x16 PNG), procedural
Pillow in the same shape as generate_terrarium_textures.py - no base-PNG
compositing.

Scope note: this script owns ONLY the 5 upgrade item icons. The processor block
faces (front / front_working / side / top / bottom) were picked from OpenAI
gpt-image-1 candidates and promoted via gen/scripts/finalize_texture.py (raws in
gen/virtual_terrarium_*-<ts>/); the Display Dome uses the vanilla glass texture
(minecraft:block/glass) directly. Do NOT re-add those faces here - regenerating
them would silently clobber the promoted art.

    vt_upgrade_*   5 framed void-crystal item icons, one glyph per lever
                   (bounty / appetite / smelter / melter / overclock)

Re-run after editing:
    python scripts/generate_virtual_terrarium_textures.py
Then rebuild the review page:
    pwsh gen/scripts/build_comparison_page.ps1

Outputs item PNGs into .../textures/item/.
"""

import os
from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(REPO, "src", "main", "resources", "assets", "productivefrogs", "textures", "item")

T = (0, 0, 0, 0)
OBS = (20, 16, 30, 255)        # obsidian body
PURPUR_DK = (110, 74, 117, 255)
CORE = (154, 77, 234, 255)     # active glow
CORE_HI = (206, 150, 255, 255)
CRYSTAL = (201, 184, 232, 255)
ENDST = (222, 214, 150, 255)   # end-stone / gold accent
FLAME = (236, 150, 46, 255)
FLAME_HI = (252, 214, 120, 255)
MOLTEN = (232, 92, 40, 255)
BOLT = (250, 226, 96, 255)
WHITE = (236, 232, 244, 255)


def _upgrade_base():
    """A small framed void-crystal tile shared by every upgrade for family cohesion."""
    img = Image.new("RGBA", (16, 16), T)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([2, 2, 13, 13], radius=2, fill=OBS, outline=PURPUR_DK)
    d.point((3, 3), fill=CRYSTAL)
    d.point((12, 3), fill=CRYSTAL)
    return img, d


def upgrade_bounty():
    img, d = _upgrade_base()
    d.polygon([(8, 4), (9, 7), (12, 8), (9, 9), (8, 12), (7, 9), (4, 8), (7, 7)], fill=ENDST)
    d.point((8, 8), fill=WHITE)
    return img


def upgrade_appetite():
    img, d = _upgrade_base()
    d.polygon([(4, 5), (8, 8), (4, 11)], fill=CORE_HI)     # fast-forward >>
    d.polygon([(8, 5), (12, 8), (8, 11)], fill=CORE)
    return img


def upgrade_smelter():
    img, d = _upgrade_base()
    d.polygon([(8, 4), (11, 9), (10, 12), (6, 12), (5, 9)], fill=FLAME)  # flame
    d.polygon([(8, 7), (9, 10), (7, 10)], fill=FLAME_HI)
    return img


def upgrade_melter():
    img, d = _upgrade_base()
    d.polygon([(8, 4), (11, 10), (8, 12), (5, 10)], fill=MOLTEN)  # molten drop
    d.ellipse([7, 8, 9, 11], fill=FLAME_HI)
    return img


def upgrade_overclock():
    img, d = _upgrade_base()
    d.polygon([(9, 3), (5, 9), (8, 9), (7, 13), (11, 7), (8, 7)], fill=BOLT)  # bolt
    return img


ITEMS = {
    "vt_upgrade_bounty": upgrade_bounty,
    "vt_upgrade_appetite": upgrade_appetite,
    "vt_upgrade_smelter": upgrade_smelter,
    "vt_upgrade_melter": upgrade_melter,
    "vt_upgrade_overclock": upgrade_overclock,
}


def main():
    os.makedirs(ITEM_DIR, exist_ok=True)
    for name, fn in ITEMS.items():
        path = os.path.join(ITEM_DIR, f"{name}.png")
        fn().save(path)
        print("wrote", path)
    print("done - rebuild the review page: pwsh gen/scripts/build_comparison_page.ps1")


if __name__ == "__main__":
    main()
