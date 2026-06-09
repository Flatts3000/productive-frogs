"""Generate the Frog Net item textures (#205).

Procedural 16x16 pixel art (no PixelLab - the project bans it): a handled net,
plus a "filled" variant with a green frog peeking out of the hoop. Run from the
repo root:  python scripts/generate_frog_net_textures.py

Writes:
  src/main/resources/assets/productivefrogs/textures/item/frog_net.png
  src/main/resources/assets/productivefrogs/textures/item/frog_net_filled.png
"""

from pathlib import Path

from PIL import Image

OUT = Path("src/main/resources/assets/productivefrogs/textures/item")

# Palette (RGBA).
CLEAR = (0, 0, 0, 0)
HANDLE = (120, 81, 45, 255)        # wooden stick
HANDLE_HI = (150, 105, 60, 255)    # stick highlight
RING = (210, 214, 220, 255)        # net hoop (light grey)
RING_SH = (150, 156, 166, 255)     # hoop shadow
MESH = (235, 238, 242, 200)        # net mesh (faint)
FROG = (96, 168, 84, 255)          # frog body (Cave-ish green; generic)
FROG_SH = (66, 124, 58, 255)       # frog shadow
EYE = (28, 36, 28, 255)            # frog eye


def blank():
    return Image.new("RGBA", (16, 16), CLEAR)


def put(px, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = color


# Net hoop: a ring of cells approximating a circle centred at (9.5, 5.5), r~4.
RING_CELLS = [
    (8, 1), (9, 1), (10, 1),
    (6, 2), (7, 2), (11, 2), (12, 2),
    (5, 3), (13, 3),
    (5, 4), (13, 4),
    (5, 5), (13, 5),
    (5, 6), (13, 6),
    (6, 7), (7, 7), (11, 7), (12, 7),
    (8, 8), (9, 8), (10, 8),
]

# Interior cells of the hoop (where mesh / frog sit).
INNER_CELLS = [
    (8, 2), (9, 2), (10, 2),
    (7, 3), (8, 3), (9, 3), (10, 3), (11, 3), (12, 3),
    (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (11, 4), (12, 4),
    (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5), (12, 5),
    (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 6), (12, 6),
    (7, 7), (8, 7), (9, 7), (10, 7),
]

# Handle: diagonal stick from the hoop's lower-left down to bottom-left.
HANDLE_CELLS = [
    (7, 8), (6, 9), (6, 10), (5, 11), (5, 12), (4, 13), (4, 14),
]


def draw_net(filled):
    img = blank()
    px = img.load()

    if filled:
        # Frog body fills most of the hoop interior; a couple of shadow cells
        # at the bottom give it volume, two eyes near the top.
        for (x, y) in INNER_CELLS:
            put(px, x, y, FROG)
        for (x, y) in [(7, 6), (8, 6), (9, 6), (10, 6), (11, 6)]:
            put(px, x, y, FROG_SH)
        put(px, x=8, y=3, color=EYE)
        put(px, x=11, y=3, color=EYE)
    else:
        # Empty: a faint crosshatch mesh so the hoop reads as a net.
        for (x, y) in [(8, 3), (10, 4), (7, 5), (11, 5), (9, 6), (8, 5), (10, 6)]:
            put(px, x, y, MESH)

    # Hoop ring (drawn last on the top arc so it frames the contents).
    for (x, y) in RING_CELLS:
        # Lower arc gets the shadow tone for a bit of depth.
        put(px, x, y, RING_SH if y >= 6 else RING)

    # Handle.
    for i, (x, y) in enumerate(HANDLE_CELLS):
        put(px, x, y, HANDLE_HI if i % 2 == 0 else HANDLE)

    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    draw_net(filled=False).save(OUT / "frog_net.png")
    draw_net(filled=True).save(OUT / "frog_net_filled.png")
    print(f"wrote {OUT / 'frog_net.png'}")
    print(f"wrote {OUT / 'frog_net_filled.png'}")


if __name__ == "__main__":
    main()
