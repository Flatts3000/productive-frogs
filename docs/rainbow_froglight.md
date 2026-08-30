# Rainbow Froglight - one Froglight, sixteen dyes

## What it is

A **Rainbow Slime** is a Bog-species Resource Slime primed by **any dye**. A Bog
Frog eats it and drops a **Rainbow Froglight**, which shapes into any of the
sixteen vanilla dyes at a crafting table.

```
any dye + slime  ->  Rainbow Slime  ->  Bog Frog eats it  ->  Rainbow Froglight
                                                                    |
                                                    shaped recipe picks the color
                                                                    v
                                                        8 / 16 / 24 of that dye
```

Inspired by Productive Bees' Dye Bee, which is also a single bee covering all
sixteen colors. PB reads the color off the flower the bee pollinated
(`BeeHelper#getBeeProduce`, a hardcoded Java special case); here the color comes
from the grid instead, so the whole lane is datapack JSON with no Java at all.

## Why the recipes are shaped, not shapeless

Every Rainbow Froglight is the same item stack - `configurable_froglight`
stamped with the `rainbow` slime variant. Sixteen **shapeless** recipes over
that one ingredient would be byte-identical to the recipe manager: it would
resolve the same one every time and the other fifteen dyes would be
uncraftable, invisibly. (That is the exact failure `RecipeConflictTest` was
added to catch, after both altar hatches shipped as obsidian + chest on the
1.21.1 line.)

**Shaped** recipes match on the arrangement, so the pattern is what picks the
color. The sixteen patterns must stay distinct under the rule vanilla actually
applies - the trimmed grid, folded against its **horizontal mirror**. A pattern
and its mirror are the same recipe; a vertical flip is not, which is why
`XX`/`X.` (Blue) and `X.`/`XX` (Cyan) can coexist.

## The sixteen patterns

`X` is a Rainbow Froglight, `.` is an empty slot.

| Dye | Pattern | Froglights | Yield |
|---|---|---|---|
| White | `X` | 1 | 8 |
| Orange | `X.` / `..` / `.X` | 2 | 16 |
| Magenta | `X..` / `...` / `..X` | 2 | 16 |
| Light Blue | `X` / `.` / `X` | 2 | 16 |
| Yellow | `X..` / `..X` | 2 | 16 |
| Lime | `X.` / `.X` | 2 | 16 |
| Pink | `X` / `X` | 2 | 16 |
| Gray | `X.X` | 2 | 16 |
| Light Gray | `XX` | 2 | 16 |
| Cyan | `X.` / `XX` | 3 | 24 |
| Purple | `X.X` / `.X.` | 3 | 24 |
| Blue | `XX` / `X.` | 3 | 24 |
| Brown | `.X.` / `X.X` | 3 | 24 |
| Green | `X` / `X` / `X` | 3 | 24 |
| Red | `XXX` | 3 | 24 |
| Black | `X..` / `.X.` / `..X` | 3 | 24 |

## Balance rule: every color pays the same rate

Yield is a flat **8 dye per Froglight consumed**, so no color is a better deal
than another - a one-Froglight pattern just mints a smaller batch than a
three-Froglight one. Cost tiers were assigned by how "deep" the color reads:
white is the blank single, the pale and mixed colors are two, the saturated
colors are three.

`RainbowDyeRecipeTest` pins both halves of that: all sixteen colors present,
and `count == froglights * 8` in every file.

**Discoverability caveat.** PF ships no recipe-unlock advancements anywhere, so
**none** of these shapes appears in the vanilla recipe book - it only ever lists
recipes a `minecraft:recipe` advancement reward has unlocked. JEI is therefore
the only in-game listing today. Change a pattern's Froglight count
without changing its yield and the build fails.

## Files

| What | Where |
|---|---|
| Variant | `data/productivefrogs/productivefrogs/slime_variant/rainbow.json` |
| Recipes | `data/productivefrogs/recipe/rainbow_froglight_to_<color>_dye.json` (x16) |
| Generator | `scripts/generate_rainbow_dye_recipes.py` |
| Entity texture | `scripts/generate_rainbow_slime_texture.py` |
| Tests | `RainbowDyeRecipeTest` (set + balance), `RecipeConflictTest` (distinctness) |

Regenerate the recipes with the script rather than hand-editing - it re-implements
the conflict signature and refuses to write a colliding set, so a bad pattern
fails at your terminal instead of in CI.

## Art note

The Rainbow Slime's inner cube is a baked hue sweep of **six horizontal bands**
(`generate_rainbow_slime_texture.py`), not a vanilla block texture, so the
`rainbow` variant deliberately ships **no `inner_block`** - the generic baker
`generate_resource_slime_textures.py` has nothing to point it at.

Two things about that were got wrong first and corrected against a running 26.1
client (the render path is the same on both lines), so don't re-derive them from
the code:

1. **A diagonal sweep reads as noise.** Eleven hue steps across six pixels is
   colored static at entity scale. Horizontal bands read cleanly.
2. **The baked hues do NOT come through true.** Only the outer shell is
   multiplied by `primary_color`, which is easy to misread as "the inner cube is
   untinted, so it renders as authored". It is untinted, but it is *seen through*
   the translucent shell, so the shell's color multiplies it optically anyway.
   Behind the shipped magenta the bands are legible but muted.

The **Froglight** itself is still the shared greyscale texture tinted flat by
`primary_color` (magenta), because a placed Froglight's color comes from a
block tint and a block model cannot vary per-face from a component. Bespoke
rainbow Froglight art is a follow-up, not a blocker.
