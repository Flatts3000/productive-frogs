# Froglight Crucible + Casting Mold (build spec)

> **Status: PROMOTED to build spec - target v1.12.0.** Originally captured
> 2026-05-26 as parked-for-v2 design notes ("Froglight Juicer"); promoted
> 2026-06-06 after a design session resolved every open question. The decision
> log below records how the shape evolved; everything else in this doc is
> buildable as written.
>
> **Decision log (2026-06-06):**
> 1. **Renamed** Froglight Juicer -> **Froglight Crucible**. "Juicer" sat too
>    close to the Slime Milker's squeeze-out-a-fluid identity; "Crucible" is the
>    established skyblock term for block-in-fluid-out (Ex Nihilo) and
>    self-teaches to the target audience.
> 2. **Heat, not FE power** - no `EnergyStorage` capability, no power-mod
>    expectation. Works in vanilla-only and skyblock packs from day one.
> 3. **Heat comes from the block below** (Ex Nihilo-style passive heat) -
>    chosen deliberately because it is *different from most metal melters*
>    (no fuel slot, no power cable).
> 4. **Metal Froglights melt to molten fluids at Tinkers-style ore-doubling
>    yield** (2 ingots' worth per Froglight).
> 5. **PF ships the Casting Mold** - first-party molten-to-ingot
>    solidification, so the melt-and-cast lane works in any pack.
> 6. **v1.x, not v2.** With the energy mechanic gone, the only never-shipped
>    piece is the fluid tank, and exposing `FluidHandler.BLOCK` is the fluid
>    analog of the `ItemHandler` hopper courtesy the v1 appliances already
>    ship. Ships as v1.12.0.
> 7. **The "no native crusher" ROADMAP entry is retired** - it was a guideline,
>    not a rule. The crush lane stays delegated to crusher mods; the
>    first-party 2x now comes from melt-and-cast, uncapped and not
>    config-gated.
> 8. **The tower**: heat source / Crucible / Casting Mold stack vertically; a
>    Mold directly on top of a Crucible pulls molten without pipes. Loose
>    composition (hopper-under-furnace model), NOT a formed multiblock.
> 9. **The Crucible is GUI-less; the Casting Mold has a GUI** (fluid gauge,
>    progress, output slots). The Mold is the mod's third GUI - extract the
>    shared `PFContainerScreen` base while building it.
> 10. **Heat sources copied verbatim from Ex Deorum** (torch 1 / soul + campfire
>     2 / lava 3 / fire 5) - Ex Deorum's crucibles are in Sky Frogs' Tier 0, so
>     heat intuitions transfer 1:1. Delivered as a NeoForge block data map.

## Concept

A heated stone basin that melts a **Froglight into a fluid** - the third cash-out
lane for a Froglight:

- smelt -> 1x solid resource
- crush (with a crusher mod) -> 2x dust -> smelt -> 2x
- **melt (Crucible) -> fluid at 2x** - for metals, 2 ingots' worth of molten
  metal, cast back to ingots in the Casting Mold

The Froglight is already the "ore-equivalent" the frog drops; this adds a fluid
output route. The Froglight carries everything needed to pick the output (the
Configurable Froglight's `SLIME_VARIANT` component; category Froglights map to
their resource), exactly like the v1.3 crush recipes match a Froglight via
`neoforge:components`.

## What ships in v1.12.0

**Wave 1 - water and lava (vanilla fluids, no new fluids minted):**

- a **lava slime** -> (eaten by its frog) -> **lava Froglight** -> (Crucible) -> **lava**
- a **water slime** -> **water Froglight** -> (Crucible) -> **water**

Renewable water and lava through the frog loop - exactly what skyblock and
nether-locked packs lack (lava for fuel / obsidian gen; water that does not
evaporate). Pairs with the Spawnery's skyblock framing.

**Wave 2 - molten metals + the Casting Mold:**

Every metal Froglight melts into its **molten fluid** at ore-doubling yield, and
the **Casting Mold** solidifies molten fluid back into ingots. Both waves ship
in v1.12.0; build wave 1 first and split the release only if wave 2 balloons.

## The Froglight Crucible

- **Interaction: GUI-less.** Right-click with a Froglight to insert (one at a
  time, like the composter); look-at (Jade) shows melt progress, tank contents,
  and current heat tier; drain with an empty bucket or by pipe. No Menu/Screen
  pair - this follows the cauldron/composter interaction model, not the
  Milker/Spawnery appliance shape (and sidesteps the 1.21.1 renderTooltip gotcha
  entirely).
- **Heat from the block below.** No heat source = no melting. The source list
  and values are **copied verbatim from Ex Deorum** (decided 2026-06-06) -
  Ex Deorum ships in Sky Frogs (its crucibles are part of the Tier 0 substrate),
  so the same block that heats an Ex Deorum crucible heats the PF Crucible at
  the same relative strength. Players' heat intuitions transfer 1:1.

  | Block below | Heat value |
  |---|---|
  | torch, wall torch, lantern | 1 |
  | soul torch, soul wall torch, soul lantern, lit campfire, lit soul campfire | 2 |
  | lava | 3 |
  | fire, soul fire | 5 |

  **Melt speed = heat value:** melt time is `400 / heat` ticks per Froglight
  (torch 400, soul/campfire 200, lava ~133, fire 80). Yes, fire out-heats lava -
  that is Ex Deorum's balance, copied deliberately; fire-on-netherrack is the
  endgame heat plate, lava is the set-and-forget mid-tier.
  - **Mechanism:** a NeoForge **data map** (`data/productivefrogs/data_maps/
    block/crucible_heat.json`, block id -> heat int) rather than tier tags - a
    data map carries the per-block value, ships as plain JSON, and a pack
    overrides or extends it the same way it overrides the Spawnery primer tags.
    (Ex Deorum models this as `crucible_heat_source` recipes with block-state
    predicates; the data map is the lighter equivalent since PF needs no state
    matching - campfires' `lit` check is handled in code.)
  - **Skyblock bootstrap check (passes trivially):** a torch is a day-one heat
    source; the ladder up through campfire -> lava -> fire mirrors the pack's
    existing Ex Deorum crucible progression exactly.
- **Internal tank: 4,000 mB**, single fluid. The tank only accepts a Froglight
  whose output fluid matches the current contents; drain to switch. Exposed via
  `Capabilities.FluidHandler.BLOCK` (all faces; extract-only from pipes - input
  is items, not fluid).
- **One Froglight melts at a time**; a second right-click while melting is
  rejected (composter model). Melt time per the heat table above
  (`400 / heat` ticks).
- **Recipe-driven Froglight -> fluid mapping** (datapack), not hardcoded:
  "this variant Froglight produces this fluid (this many mB)." Later fluids and
  cross-mod outputs are pure JSON gated by `neoforge:conditions`, no new Java -
  same ethos as the variant system and the crush recipes.
- **Block + BlockEntity + static serverTick** per the appliance pattern, minus
  Menu/Screen (GUI-less). `LIT`-style blockstate when actively melting over
  heat (glow + particles sell the "it's working" read without a GUI).

## Yields (decided)

| Input Froglight | Output | Amount |
|---|---|---|
| Water Froglight | `minecraft:water` | **1,000 mB** (one full bucket per Froglight) |
| Lava Froglight | `minecraft:lava` | **1,000 mB** |
| Metal/resource Froglight (wave 2) | `productivefrogs:molten_<variant>` | **180 mB** (90 mB/ingot x 2 - Tinkers convention, ore-doubling) |

Non-metal, non-fluid variants (e.g. bone, string) simply ship no melt recipe -
the Crucible rejects a Froglight with no mapping, same as the crush generator's
curated metal list.

## Molten fluids (wave 2)

**PF mints its own `molten_<variant>` fluids** through the same dynamic
per-variant registration the v1.8 Slime Milk pipeline uses (`PFVariantMilk`-style
bootstrap off `variants_index.json`): one greyscale molten still/flow texture
set, tinted per variant `primary_color`. Pre-build check: a quick v1.3-style
provider scan to confirm no 1.21.1 NeoForge mod ships molten metals worth
deferring to (none known - Tinkers does not exist on NeoForge 1.21.1); if one
surfaces, key outputs on its `c:` fluid tags behind `mod_loaded` instead of
minting duplicates. Cross-mod interop (tagging PF molten fluids `c:molten_iron`
etc. so other mods' machinery accepts them) ships as pure data.

Molten fluids are **not placeable** as world blocks in v1.12 (no source-block
behavior to design or test); they exist for the tank -> pipe -> Mold loop. They
mint only for variants with a melt recipe.

## The Casting Mold

- **Input:** molten fluid only. Three routes: **stacked on a Crucible** (pulls
  directly from the Crucible's tank below it - see "The tower"), piped in via
  `Capabilities.FluidHandler.BLOCK`, or poured from a bucket. Internal buffer:
  1,000 mB, single fluid.
- **Solidify: 90 mB -> 1 ingot, 60 ticks** per ingot, automatic while fluid is
  buffered. No coolant requirement (kept v1-light; a water-adjacency speed bonus
  is a v2-flavored idea, parked).
- **Has a GUI** (decided 2026-06-06) - unlike the Crucible. A furnace-shaped
  screen with a fluid gauge, solidify-progress arrow, and an output slot stack:
  the Mold accumulates ingots and the player manages them like furnace output.
  `Menu` + `Screen` per the appliance pattern, **including the 1.21.1
  renderTooltip override**. This is the mod's **third** GUI - per the backlog's
  standing note, extract the shared `PFContainerScreen<T>` base (the duplicated
  `render` -> `renderTooltip` override in `SlimeMilkerScreen`/`SpawneryScreen`)
  as part of this build rather than copy-pasting it a third time.
- **Output:** GUI slot + hoppers extract from below (`ItemHandler.BLOCK`,
  output-only) - the standard v1 appliance courtesy.
- **Ingot-only this update.** Nugget/block casts (Tinkers precedent) are
  deferred; the recipe-driven solidify mapping leaves room for them as data.
- The ingot produced is the variant's existing smelt-result item (the same item
  the Froglight smelts to), so cross-mod variants cast to their verified
  provider ingots with no new resolution logic.

## The tower (stack composition, not a formed multiblock)

The intended in-world layout is a vertical stack (decided 2026-06-06):

```
[ Casting Mold ]   <- GUI, ingots out (hopper below... see note)
[ Crucible      ]   <- GUI-less, Froglights in by right-click
[ heat source   ]   <- vanilla block: magma block / fire / lava
```

When a Casting Mold sits **directly on top of a Crucible**, it pulls molten
fluid straight from the Crucible's tank - no pipes, no buckets. That makes the
full metal-doubling loop work in a vanilla-only pack: build the three-block
tower, right-click Froglights into the middle, take ingots out of the top.

This is **composition, not a formed multiblock**: each block is a standalone BE
that works alone (a free-standing Mold still accepts pipes and buckets; a
Crucible without a Mold is still the water/lava farm), and stacking them just
wires the pull - the same model as vanilla's hopper-under-furnace. No
controller, no formed-structure validation, so the "multiblocks are V2" line
stays uncrossed.

Note on hopper output: the Mold's down-face is the Crucible when stacked, so
automated ingot extraction from a tower uses a hopper feeding from the Mold's
**side** faces (side = output too) or a hopper minecart track above; the
down-face hopper courtesy applies to free-standing Molds.

## New content this needs

New `SlimeVariant` entries (datapack JSON, no Java):

- **lava slime** - Infernal species. **Primer: `minecraft:magma_block`**
  (cheap, thematic, reachable pre-lava via the Infernal chain).
  `inner_block: minecraft:magma_block`; colors per the lava theme.
- **water slime** - Tide species. **Primer: `minecraft:kelp`** (renewable,
  water-themed, dirt-cheap - correct for a fluid the player will want early).
  `inner_block: minecraft:blue_ice` or water-still texture per the texture
  generator's constraints; colors per the water theme.

Both ship the usual five lang keys (LangCompletenessTest) and baked inner-block
textures. **Neither ships a furnace smelt recipe** - their resource IS the
fluid, so the Crucible is their only cash-out lane (a first: every prior
variant smelts back to its primer resource; note it in the JEI info page).

## Pre-build checks (small, do during implementation)

1. The molten-fluid provider scan (above) - confirm PF-minted is right.
2. ~~Final name for the casting block~~ - RESOLVED 2026-06-06: **Casting Mold**
   (user call). "Casting Basin" read too close to the Crucible's basin shape;
   "Ingot Mold" was rejected as shape-locked since nugget/block/gem casts may
   land later as data; "Casting Table" (Tinkers borrow) and "Castery" (Spawnery
   coinage) lost to the plainer name. Shape-neutral: the mold casts whatever the
   solidify recipe says.
3. Confirm the texture generator can bake a water-themed inner cube (water_still
   is animated; may need a single-frame crop like the milk textures).

## Related

- Scope: ships as **v1.12.0** (see decision log) - [versioning.md](./versioning.md)
- Runway: [../ROADMAP.md](../ROADMAP.md)
- Appliance pattern + capability registration: `CLAUDE.md`
- Per-variant dynamic fluid registration (the pipeline molten fluids reuse):
  [automated_milk_variants.md](./automated_milk_variants.md)
- Sibling appliance specs: [spawnery.md](./spawnery.md), [farming.md](./farming.md)
