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
and `count == froglights * 8` in every file. Change a pattern's Froglight count
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

Both of the Rainbow variant's looks are **baked procedurally**, because the
mod's normal mechanism cannot express them. Every other variant is one shared
sprite multiplied by `primary_color`, and a multiply can only ever produce a
flat color - which is exactly the one thing "rainbow" must not be.

**The slime** (`generate_rainbow_slime_texture.py`) gets a hue sweep painted
across the six inner-cube faces, as **six horizontal bands**. The variant
therefore ships **no `inner_block`**: rainbow has no vanilla block for the
generic baker (`generate_resource_slime_textures.py`) to downscale.

Two things about that were got wrong first and corrected against a running
client, so don't re-derive them from the code:

1. **A diagonal sweep reads as noise.** The first bake swept hue along the face
   diagonal, which puts eleven hue steps across six pixels. At entity scale that
   is coloured static, not a rainbow. Six horizontal bands read cleanly and
   match the vertical sweep on the Froglight's side texture.
2. **The baked hues do NOT come through true.** Only the outer shell is
   multiplied by `primary_color`, which is easy to misread as "the inner cube is
   untinted, so it renders as authored". It is untinted, but it is *seen
   through* the translucent shell, so the shell's color multiplies it optically
   anyway. Behind the magenta shell the bands are barely legible; behind a
   near-white shell they are unmistakable.

**The Froglight** (`generate_rainbow_froglight_textures.py`) gets its own
`rainbow_froglight_side` / `_top` sprites, derived from vanilla's ochre
froglight art: each pixel keeps its **value**, takes a hue from its position,
and has its **saturation shifted rather than floored**. That last detail
matters - the froglight's cell-and-glow structure lives almost entirely in
saturation (measured on vanilla's sprites: S sd 0.156 against V sd 0.051), so
clamping saturation to a floor erases the material and leaves a plain gradient.
Shifting keeps the variance, and the result reads as the same material as every
other Froglight, just spectrum-swept.

Because those hues are baked, the rainbow models carry **no `tintindex`** at
all. `RainbowFroglightAssetTest` pins that, along with the model paths and the
existence of every sprite they name - none of which GameTest can see, since a
dedicated server never loads models.

### Where it renders, and the one place it does not

The item form selects its model on the `slime_variant` component
(`assets/productivefrogs/items/configurable_froglight.json`, a
`minecraft:select` on `minecraft:component`), so the Rainbow Froglight is
genuinely rainbow in the inventory, in hand, in JEI and the recipe book, as a
dropped item, and in an item frame. The other 39 variants fall through to the
tinted model unchanged.

**The placed block is still flat-tinted.** A blockstate maps only *blockstate
properties* to models, and the variant lives in the block entity, so the
blockstate cannot pick the rainbow model. Fixing it needs a dynamic model:
NeoForge 26.1 exposes the hook (`BlockStateModelExtension.collectParts` with a
level and pos, reading `IBlockGetterExtension#getModelData`), so the shape is a
`ModelProperty` on `ConfigurableFroglightBlockEntity` plus a custom
`BlockStateModel` that swaps parts on it. That is a rendering-architecture
change to a block shared by every variant, so it is deliberately left as a
separate decision rather than bolted on here.

A blockstate boolean would be the cheap alternative and is **not** recommended:
it only works while rainbow is the sole special case, and it is the ad-hoc flag
pattern this codebase has already refactored away from once (see `FrogKind`).

### Why `primary_color` is currently a compromise

The placed Froglight and the slime shell read the same `primary_color`, and
rainbow wants opposite things from it:

| `primary_color` | Slime | Placed Froglight |
|---|---|---|
| magenta (shipped) | magenta, bands barely legible | distinctly magenta |
| near-white | unmistakable rainbow bands | indistinguishable from a vanilla ochre Froglight |

Both were confirmed in a running client. The shipped value is magenta, because a
Froglight that looks exactly like vanilla's is the worse of the two failures.
Solving the placed block removes the conflict entirely - once the block renders
its own baked texture it stops consulting `primary_color`, which frees that
field to go pale for the slime. That is the main argument for doing the dynamic
model rather than living with the compromise.
