"""Author warden_altar.nbt + elder_altar.nbt structure fixtures (#279/#280) from
the same layouts the validators encode (5x5x5, hatch at structure (2,1,2)).

The layout tables below MIRROR WardenAltarValidator / ElderAltarValidator - if
either side changes without the other, the warden_altar_* / elder_altar_*
GameTests fail (that is the lock working). Rerun after any layout change:

    python scripts/generate_altar_structures.py
"""
import gzip
import struct
import sys

sys.stdout.reconfigure(encoding="utf-8")

import os
STRUCT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                          "src/main/resources/data/productivefrogs/structure")

# ---- extract DataVersion from the shipped wither_altar.nbt ----
with gzip.open(f"{STRUCT_DIR}/wither_altar.nbt", "rb") as f:
    raw = f.read()
i = raw.find(b"DataVersion")
assert i >= 0, "DataVersion not found"
DATA_VERSION = struct.unpack(">i", raw[i + 11:i + 15])[0]
print("DataVersion:", DATA_VERSION)

# ---- minimal NBT writer ----
def tag_name(name):
    b = name.encode()
    return struct.pack(">H", len(b)) + b

def t_int(name, v):
    return b"\x03" + tag_name(name) + struct.pack(">i", v)

def t_string(name, v):
    b = v.encode()
    return b"\x08" + tag_name(name) + struct.pack(">H", len(b)) + b

def t_list_int(name, vals):
    return b"\x09" + tag_name(name) + b"\x03" + struct.pack(">i", len(vals)) + b"".join(
        struct.pack(">i", v) for v in vals)

def compound_payload(children):
    return b"".join(children) + b"\x00"

def t_compound(name, children):
    return b"\x0a" + tag_name(name) + compound_payload(children)

def t_list_compound(name, payloads):
    return b"\x09" + tag_name(name) + b"\x0a" + struct.pack(">i", len(payloads)) + b"".join(payloads)

def t_list_empty(name):
    return b"\x09" + tag_name(name) + b"\x00" + struct.pack(">i", 0)

def build_structure(blocks, palette):
    """blocks: list of ((x,y,z), palette_idx); palette: list of block id strings."""
    block_payloads = [
        compound_payload([t_list_int("pos", list(pos)), t_int("state", idx)])
        for pos, idx in blocks
    ]
    palette_payloads = [compound_payload([t_string("Name", name)]) for name in palette]
    root_children = [
        t_list_int("size", [5, 5, 5]),
        t_list_empty("entities"),
        t_list_compound("blocks", block_payloads),
        t_list_compound("palette", palette_payloads),
        t_int("DataVersion", DATA_VERSION),
    ]
    return b"\x0a" + tag_name("") + compound_payload(root_children)

def write(path, blocks, palette):
    data = build_structure(blocks, palette)
    with gzip.open(path, "wb", compresslevel=6) as f:
        f.write(data)
    print(f"wrote {path} ({len(blocks)} blocks, {len(palette)} palette entries)")

PF = "productivefrogs:"
# structure coords: the Hatch sits IN THE WALL at (2,1,0) (canonical frame:
# interior toward +Z); validator offset (dx,dy,dz) -> (2+dx, 1+dy, dz)
def S(dx, dy, dz):
    return (2 + dx, 1 + dy, dz)

# ---- Warden Altar (the Shrieker Pit) ----
palette = [PF + "warden_altar_hatch", PF + "echoing_catalyst", PF + "reinforced_sculk_froglight",
           PF + "reinforced_echo_shard_froglight", PF + "shrieker_receptacle", "minecraft:air"]
HATCH, CAPSTONE, SCULK, ECHO, RECEPT, AIR = range(6)
blocks = [(S(0, 0, 0), HATCH), (S(0, -1, 2), CAPSTONE)]
for dx in range(-2, 3):
    for dz in range(0, 5):
        ring = max(abs(dx), abs(dz - 2)) == 2
        if not (dx == 0 and dz == 2):
            blocks.append((S(dx, -1, dz), SCULK))                 # floor (capstone at center)
        if ring:
            for dy in range(0, 3):
                if not (dx == 0 and dy == 0 and dz == 0):         # the Hatch cell
                    blocks.append((S(dx, dy, dz), SCULK))         # lining
            cardinal = (dx == 0 and dz in (0, 4)) or (abs(dx) == 2 and dz == 2)
            blocks.append((S(dx, 3, dz), RECEPT if cardinal else ECHO))  # rim
for dx in range(-1, 2):
    for dz in range(1, 4):
        for dy in range(0, 4):
            blocks.append((S(dx, dy, dz), AIR))                   # open shaft
write(f"{STRUCT_DIR}/warden_altar.nbt", blocks, palette)

# ---- Elder Guardian Altar (the Monument Well) ----
palette = [PF + "elder_altar_hatch", PF + "monument_core", PF + "reinforced_sponge_froglight",
           PF + "reinforced_prismarine_froglight", PF + "tide_offering_receptacle", "minecraft:water",
           PF + "reinforced_light_blue_stained_glass"]
HATCH, CORE, SPONGE, PRISM, RECEPT, WATER, GLASS = range(7)
blocks = [(S(0, 0, 0), HATCH), (S(0, 3, 2), CORE)]
for dx in range(-2, 3):
    for dz in range(0, 5):
        blocks.append((S(dx, -1, dz), SPONGE))                    # floor
        ring = max(abs(dx), abs(dz - 2)) == 2
        if ring:
            for dy in range(0, 3):
                if not (dx == 0 and dy == 0 and dz == 0):         # the Hatch cell
                    blocks.append((S(dx, dy, dz), GLASS))         # glass walls
        corner = abs(dx) == 2 and dz in (0, 4)
        center = dx == 0 and dz == 2
        if corner:
            blocks.append((S(dx, 3, dz), RECEPT))                 # roof corners
        elif not center:
            blocks.append((S(dx, 3, dz), PRISM))                  # roof plate
for dx in range(-1, 2):
    for dz in range(1, 4):
        for dy in range(0, 3):
            blocks.append((S(dx, dy, dz), WATER))                 # flooded interior
write(f"{STRUCT_DIR}/elder_altar.nbt", blocks, palette)
