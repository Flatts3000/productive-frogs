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
the only in-game listing today. That matters more here than for the rest of the
mod: every other PF recipe has a shape a player can reason about, whereas these
sixteen are arbitrary by construction. Change a pattern's Froglight count
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

**The slime** (`generate_rainbow_slime_texture.py`) has **both** of its cubes
painted: the translucent outer shell in eight hue bands, and the inner cube in
six. The variant therefore ships **no `inner_block`** - rainbow has no vanilla
block for the generic baker (`generate_resource_slime_textures.py`) to downscale,
which is why that baker lists it under SKIPPED rather than MISSING.

It also sets **`untinted: true`**, and that flag is load-bearing. The shell is
normally multiplied by `primary_color`; a multiply can only ever produce one flat
hue, and because the inner cube is *seen through* the translucent shell, a tinted
shell washes the whole slime toward that hue no matter what is baked. `untinted`
makes `ResourceSlimeRenderer#resolveShellTint` return `-1`, the no-tint sentinel
the outer layer passes straight through, so the art renders as painted.

The flag is data-driven and defaults to false, so any future variant shipping
full-colour entity art opts in with one JSON key and no Java.

Two things about the bake were got wrong first and corrected against a running
client, so don't re-derive them from the code:

1. **A diagonal sweep reads as noise.** The first bake swept hue along the face
   diagonal, which puts eleven hue steps across six pixels. At entity scale that
   is coloured static, not a rainbow. Horizontal bands read cleanly, and the shell
   and inner cube sweep the same direction so they reinforce rather than clash.
   The two caps still sweep diagonally, having no up-down to run along.
2. **The inner cube has to be DRAWN, not recoloured.** The base texture
   (`bog_resource_slime.png`) is fully transparent in that region, so a
   recolour-in-place leaves it black. The shell is the opposite case: it is a flat
   grey at alpha 180 with no shading, and its alpha must be preserved or the slime
   stops being translucent.

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

### Where it renders

Both surfaces render the baked art, by two different mechanisms, because items
and blocks pick models in completely different ways.

**The item** selects its model on the `slime_variant` component
(`assets/productivefrogs/items/configurable_froglight.json`, a `minecraft:select`
on `minecraft:component`), so it is rainbow in the inventory, in hand, in JEI, as
a dropped item, and in an item frame.

**The placed block** cannot do that: a blockstate file maps only blockstate
properties, and the variant lives in the block entity. It goes through
`VariantBlockStateModel` instead, a `CustomUnbakedBlockStateModel` registered in
`RegisterBlockStateModels` and referenced from
`blockstates/configurable_froglight.json`. The block entity publishes its variant
as NeoForge model data; the model reads that on the meshing thread and forwards
to the matching child model. The other 39 variants fall through to the shared
tinted model, unchanged.

The variant-to-model map lives in the blockstate JSON, **not** in Java, so a
future variant shipping its own art is one JSON entry and no code.

### Three things that are silent when wrong

All three were found by driving a running client; none is reachable by GameTest,
because a dedicated server never loads models.

1. **`createGeometryKey` must be overridden, and must not return null.** The
   default null means "not implemented" and the renderer reuses one position's
   quads at another. The key also has to include the model instance, not just the
   variant: the three axis entries are separate baked models whose children carry
   different rotations, so keying on the variant alone would make a vertical
   Froglight render a neighbour's horizontal quads.
2. **Refreshing model data does not re-render anything.**
   `requestModelDataUpdate()` only queues the position; the data is picked up
   lazily when a section is next meshed. A block-entity data packet does not dirty
   the section on its own, so without an explicit dirty the block keeps the
   geometry it was last meshed with.
3. **`setBlocksDirty(pos, state, state)` is a no-op.** `ModelManager.requiresRender`
   returns false immediately when the two states are identical, so the obvious call
   does nothing at all. Observed directly: a `/data merge block` setting Variant to
   rainbow left the block rendering as lapis until an unrelated neighbouring block
   was placed. `ConfigurableFroglightBlockEntity#refreshModel` passes `AIR` as the
   old state to force the comparison, which is why that argument looks wrong and
   is not.

### What `primary_color` is for now

Neither the placed Froglight nor the slime shell consults it any more - the
Froglight renders its own model and the slime opts out with `untinted` - so it is
free to be vivid again, and is back to `0xC354CD`.

It still drives every surface that has no bespoke art: the Slime Milk bucket
(`VariantColorTint`), the slime bucket and spawn egg, the Sprinkler, the Crucible
and Basin renderers, the Terrarium readout, and the slime's dust particles. A
saturated value is what those want. Verified in-client that a Rainbow Slime Milk
bucket is now plainly distinct from the cream `0xF0F0E0` that `VariantColorTint`
returns when a variant **cannot be resolved** - an earlier near-white value sat
close enough to that fallback to be mistaken for a broken bucket.
