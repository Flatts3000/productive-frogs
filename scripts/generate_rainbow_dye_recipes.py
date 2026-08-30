#!/usr/bin/env python3
"""Generate the 16 Rainbow Froglight -> dye shaped crafting recipes.

A Rainbow Froglight is one item stack: every one of them is
`productivefrogs:configurable_froglight` stamped with the `rainbow` slime
variant. So the *arrangement* in the grid is what picks the colour - sixteen
shaped recipes over the same ingredient, one per vanilla dye. This is the only
way one stack reaches sixteen outputs; a shapeless recipe would see sixteen
identical inputs and only ever resolve the first.

Yield is a flat DYE_PER_FROGLIGHT for every recipe, so no colour is a better
deal than another - a one-Froglight pattern just mints a smaller batch than a
three-Froglight one. Tune that one constant to rebalance the whole set.

Patterns must stay distinct under the same rule `RecipeConflictTest` applies:
the trimmed grid, folded to the min of itself and its HORIZONTAL mirror (vanilla
matches a shaped recipe or its mirror, so a pattern and its mirror are the same
recipe; a vertical flip is not). This script re-implements that signature and
refuses to write if any two patterns collide, so a hand-edit that breaks the set
fails here rather than in the build.

Idempotent. Writes UTF-8, LF, no BOM.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RECIPE_DIR = os.path.join(ROOT, "src/main/resources/data/productivefrogs/recipe")

VARIANT = "productivefrogs:rainbow"
DYE_PER_FROGLIGHT = 8

# colour -> pattern rows. Grouped by cost: the blank white is one Froglight, the
# pale/mixed colours two, the deep saturated colours three.
PATTERNS = {
    # 1 Froglight
    "white":      ["X"],
    # 2 Froglights
    "light_gray": ["XX"],
    "gray":       ["X X"],
    "pink":       ["X",
                   "X"],
    "light_blue": ["X",
                   " ",
                   "X"],
    "lime":       ["X ",
                   " X"],
    "yellow":     ["X  ",
                   "  X"],
    "orange":     ["X ",
                   "  ",
                   " X"],
    "magenta":    ["X  ",
                   "   ",
                   "  X"],
    # 3 Froglights
    "red":        ["XXX"],
    "green":      ["X",
                   "X",
                   "X"],
    "blue":       ["XX",
                   "X "],
    "cyan":       ["X ",
                   "XX"],
    "purple":     ["X X",
                   " X "],
    "brown":      [" X ",
                   "X X"],
    "black":      ["X  ",
                   " X ",
                   "  X"],
}


def _trim(grid):
    """Drop fully-empty leading/trailing rows and columns (interior gaps stay)."""
    rows = [r for r in grid]
    while rows and not any(rows[0]):
        rows.pop(0)
    while rows and not any(rows[-1]):
        rows.pop()
    if not rows:
        return []
    width = len(rows[0])
    left = 0
    while left < width and not any(r[left] for r in rows):
        left += 1
    right = width
    while right > left and not any(r[right - 1] for r in rows):
        right -= 1
    return [r[left:right] for r in rows]


def signature(pattern):
    """The signature RecipeConflictTest computes: trimmed grid, folded to its mirror."""
    width = max(len(r) for r in pattern)
    grid = [[c != " " for c in row.ljust(width)] for row in pattern]
    mirror = [list(reversed(row)) for row in grid]
    render = lambda g: "/".join("".join("X" if c else "_" for c in r) for r in _trim(g))
    return min(render(grid), render(mirror))


def main() -> None:
    seen = {}
    for colour, pattern in PATTERNS.items():
        if len(set(len(r) for r in pattern)) != 1:
            raise SystemExit(f"{colour}: pattern rows must all be the same length: {pattern}")
        sig = signature(pattern)
        if sig in seen:
            raise SystemExit(
                f"{colour} and {seen[sig]} are the same recipe to the crafting grid "
                f"(signature {sig!r}) - one of them would be uncraftable. Change a pattern.")
        seen[sig] = colour

    if len(PATTERNS) != 16:
        raise SystemExit(f"expected 16 dye colours, got {len(PATTERNS)}")

    for colour, pattern in PATTERNS.items():
        count = sum(row.count("X") for row in pattern) * DYE_PER_FROGLIGHT
        recipe = {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "group": "productivefrogs_rainbow_dye",
            "pattern": pattern,
            "key": {
                "X": {
                    "neoforge:ingredient_type": "neoforge:components",
                    "items": ["productivefrogs:configurable_froglight"],
                    "components": {"productivefrogs:slime_variant": VARIANT},
                }
            },
            "result": {"id": f"minecraft:{colour}_dye", "count": count},
        }
        path = os.path.join(RECIPE_DIR, f"rainbow_froglight_to_{colour}_dye.json")
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(recipe, fh, indent=2)
            fh.write("\n")

    print(f"wrote {len(PATTERNS)} rainbow dye recipes to {RECIPE_DIR}")
    print("all patterns distinct under trim + horizontal mirror")


if __name__ == "__main__":
    main()
