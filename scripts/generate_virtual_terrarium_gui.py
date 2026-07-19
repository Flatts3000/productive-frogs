"""
Generate the Virtual Terrarium GUI background (256x256 sheet, 176x180 panel).

The Virtual Terrarium has more UI than any furnace-shaped appliance, so it needs a
taller panel than the stock 176x166. We build the vanilla container chrome by a
vertical 9-slice of vanilla furnace.png (perfect border + player-inventory slots),
then paint the machine-area wells / gauges ourselves in the vanilla style.

Layout (content top-left coords, matching VirtualTerrariumMenu + VirtualTerrariumScreen):
    RF meter        (8, 18)  14x54          feedstock fluid slot (26, 18)
    frog slot       (26, 40)                progress arrow       (46, 31) 24x16
    output grid 3x2 (74,20)(92,20)(110,20)/(.,38)
    output tank     (130, 18) 16x54         upgrade column       (150, 18/36/54/72)
    player inv      (8, 98)                 hotbar               (8, 156)
The lit progress arrow sprite is inlined at (176, 14) for the screen's blit.

Requires the vanilla client-extra jar extracted to %TEMP%/mc-extra (the comparison
harness / gui scripts populate it; run gen/scripts/build_comparison_page.ps1 once if
missing). Output:
    src/main/resources/assets/productivefrogs/textures/gui/container/virtual_terrarium.png
"""

import os
import tempfile
from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MC = os.path.join(tempfile.gettempdir(), "mc-extra", "assets", "minecraft", "textures", "gui")
FURNACE = os.path.join(MC, "container", "furnace.png")
BURN = os.path.join(MC, "sprites", "container", "furnace", "burn_progress.png")
OUT = os.path.join(REPO, "src", "main", "resources", "assets", "productivefrogs",
                   "textures", "gui", "container", "virtual_terrarium.png")

PANEL = (198, 198, 198, 255)
SLOT_BG = (139, 139, 139, 255)
SLOT_UP = (120, 100, 138, 255)   # upgrade wells: faint purpur tint
DARK = (55, 55, 55, 255)         # recess: top/left inset
LIGHT = (255, 255, 255, 255)     # bevel: bottom/right
TANK = (48, 40, 58, 255)         # empty fluid recess (void-dark)
ENERGY = (40, 28, 24, 255)       # empty energy recess (dark)
ARROW = (120, 120, 120, 255)     # empty arrow track

PW, PH = 176, 180                # panel size


def _recess(d, x, y, w, h, inner):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=inner)
    d.line([(x - 1, y - 1), (x + w - 1, y - 1)], fill=DARK)       # top
    d.line([(x - 1, y - 1), (x - 1, y + h - 1)], fill=DARK)       # left
    d.line([(x - 1, y + h), (x + w, y + h)], fill=LIGHT)          # bottom
    d.line([(x + w, y - 1), (x + w, y + h)], fill=LIGHT)          # right


def slot(d, px, py, bg=SLOT_BG):
    """Vanilla-style recessed 16x16 slot."""
    _recess(d, px, py, 16, 16, bg)


def gauge(d, gx, gy, w, h, inner=TANK):
    """Recessed fluid / energy meter frame (fill drawn live by the screen)."""
    _recess(d, gx, gy, w, h, inner)


def arrow_track(d, ax, ay):
    """A right-pointing empty arrow track (the lit arrow blits over it)."""
    d.rectangle([ax, ay + 5, ax + 15, ay + 10], fill=ARROW)
    d.polygon([(ax + 15, ay + 2), (ax + 23, ay + 8), (ax + 15, ay + 13)], fill=ARROW)


def main():
    if not os.path.exists(FURNACE):
        raise SystemExit(f"missing {FURNACE} - run gen/scripts/build_comparison_page.ps1 once to populate mc-extra")
    fur = Image.open(FURNACE).convert("RGBA")

    out = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    # Vertical 9-slice: top chrome (0..82), stretched panel gap (14px), inventory block.
    out.paste(fur.crop((0, 0, PW, 83)), (0, 0))
    out.paste(fur.crop((0, 82, PW, 83)).resize((PW, 14), Image.NEAREST), (0, 83))
    out.paste(fur.crop((0, 83, PW, 166)), (0, 97))

    d = ImageDraw.Draw(out)
    # Clear the furnace machine-area slots (input / fuel / output / arrow), keep borders.
    d.rectangle([7, 16, 168, 90], fill=PANEL)

    gauge(d, 8, 18, 14, 54, inner=ENERGY)     # RF meter
    slot(d, 26, 18)                           # feedstock fluid slot
    slot(d, 26, 40)                           # frog
    arrow_track(d, 46, 31)                    # progress arrow track
    for i in range(6):                        # 3x2 output grid
        slot(d, 74 + (i % 3) * 18, 20 + (i // 3) * 18)
    gauge(d, 130, 18, 16, 54)                 # output product tank
    for i in range(4):                        # vertical upgrade column
        slot(d, 150, 18 + i * 18, bg=SLOT_UP)

    if os.path.exists(BURN):
        out.paste(Image.open(BURN).convert("RGBA"), (176, 14))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
