# Froglight Crucible (design notes)

> **Status: PARKED for v2.** These are design notes from a scoping conversation,
> not a build spec. The shape below is decided; the Open Questions are not. Do not
> implement from this doc until those are resolved and it is promoted to a real
> spec (cf. `docs/spawnery.md`). Captured 2026-05-26.
>
> **2026-06-06 decisions:** (1) Renamed from "Froglight Juicer" - "Juicer" sat too
> close to the Slime Milker's squeeze-out-a-fluid identity, and two near-synonym
> extractors confuse the "which block takes my item" choice. "Crucible" is the
> established skyblock term for block-in-fluid-out (Ex Nihilo), so it teaches
> itself to the target audience. (2) **Heat, not FE power** - the Crucible runs on
> heat rather than NeoForge energy. This removes the energy capability from the
> block entirely and reopens the v1.x-vs-v2 placement question (see "Why this is
> v2" below).

## Concept

A processing block that converts a **Froglight into a fluid** - a third cash-out
lane for a Froglight, alongside the two that ship today:

- smelt -> 1x solid resource
- crush (with a crusher mod) -> 2x dust -> smelt -> 2x
- **Froglight Crucible -> a fluid, at Tinkers-style ore-doubling yield** (decided
  2026-06-06): the Froglight is the ore-equivalent, so melting a metal Froglight
  yields **2 ingots' worth** of molten metal - parity with the crush lane, paid in
  fluid + casting infrastructure instead of a crusher mod.

The Froglight is already the "ore-equivalent" the frog drops; this just adds a
fluid output route. The Froglight carries everything needed to pick the output
(the Configurable Froglight's `SLIME_VARIANT` component; category Froglights map
to their resource), exactly like the v1.3 crush recipes match a Froglight via
`neoforge:components`.

## First iteration: water and lava

The v1 of this feature ships exactly two fluids, both vanilla, so the mod mints no
new fluids:

- a **lava slime** -> (eaten by its frog) -> **lava Froglight** -> (Crucible) -> **lava**
- a **water slime** -> **water Froglight** -> (Crucible) -> **water**

This makes water and lava themselves the farmable resource. The payoff is renewable
water and lava through the frog loop - exactly what skyblock and nether-locked packs
lack (lava for fuel / obsidian gen; water that does not evaporate). It pairs with the
Spawnery's skyblock framing.

**Second wave: molten metals (intent locked 2026-06-06).** Metal Froglights should
melt into their molten-fluid versions - an Iron Froglight melts to molten iron, and
so on through the variant roster - at **ore-doubling yield, the way the same ore
would melt in Tinkers' Construct** (2 ingots' worth of mB per Froglight). The
recipe-driven mapping below is designed for exactly this; what's unresolved is
where the molten fluids come from and what consumes them (Open Question 6).

## Decided shape

- **Name:** Froglight Crucible.
- **Input:** a (variant) Froglight. Recipe-driven mapping, see below.
- **Output:** an **internal fluid tank**. Drain it by right-clicking with an empty
  bucket, or by pipe via `Capabilities.FluidHandler.BLOCK`. (NOT a bucket-output
  slot like the Milker.)
- **Heat, not power** (decided 2026-06-06): the block requires **heat**, not FE
  energy. No `EnergyStorage` capability, no power-mod expectation - a vanilla-only
  or skyblock pack can run it from day one, and it matches the crucible identity
  (a heated vessel, not a machine).
- **Heat comes from the block below** (decided 2026-06-06): Ex Nihilo-style
  passive heat - place fire / lava / magma block (exact source list + tier
  multipliers in the numbers question) under the Crucible and it melts; hotter
  source = faster melt. Chosen deliberately because it is *different from most
  metal melters* (no fuel slot, no power cable) and it is the heat model the
  skyblock audience already knows. Consequence: the Crucible is likely
  **GUI-less** - right-click to insert a Froglight, look-at (Jade/JEI) to read
  progress and tank contents, bucket or pipe to drain. No Menu/Screen pair; it
  departs from the Milker/Spawnery appliance shape and instead follows the
  composter/cauldron interaction model, which also sidesteps the 1.21.1
  renderTooltip gotcha entirely.
- **Recipe-driven Froglight -> fluid mapping** (datapack), not hardcoded. The block
  reads "this variant Froglight produces this fluid (this many mB)." v1 ships two
  recipes (lava Froglight -> lava, water Froglight -> water); later fluids and
  cross-mod molten metals are pure JSON gated by `neoforge:conditions` ->
  `mod_loaded`, no new Java. Same ethos as the variant system and the crush recipes.
- Reuses the appliance pattern (Block + BlockEntity + Menu + Screen + ContainerData
  + static serverTick) documented in `CLAUDE.md`, extended with the energy and
  fluid-tank capabilities.

## Why this is v2, not a v1.x appliance (REOPENED 2026-06-06)

The original v2 rationale was that the block introduced two automation mechanics:
an energy requirement and a pipe-able internal fluid tank. The heat decision
removes the energy mechanic entirely, which guts half that argument:

- **Case for v1.x now:** vanilla has single-block heated/fluid appliances (furnace,
  cauldron), and the V1 rule of thumb is "if vanilla has a single-block appliance
  equivalent, it's V1." The fuel-burn loop is the shipped Slime Milker pattern, and
  exposing `Capabilities.FluidHandler.BLOCK` on the tank is the fluid analog of the
  `ItemHandler` hopper courtesy the v1 appliances already ship.
- **Case for staying v2:** the internal fluid tank is still a mechanic the mod has
  never shipped, and "pipes" sits on the v2 side of the versioning line.

Either way it remains the **lightest possible opener** for whichever bucket it
lands in: single block, no multiblock, and it establishes the fluid-tank pattern
later blocks (Frog Terrarium, buffered Slime Milker) will reuse. Placement is
folded into Open Question 5.

## Companion block: casting molten metal back to ingots (decided 2026-06-06)

The molten lane needs a first-party sink: **PF ships a casting block** (working
name: **Casting Basin**) that accepts molten fluid - piped in via
`Capabilities.FluidHandler.BLOCK` or poured from a bucket - and solidifies it
into ingots. With both ends first-party, the doubled melt lane works in any pack,
including vanilla-only, and the strong lean on Open Question 6's *source* side
becomes PF-minted `molten_<variant>` fluids (the v1.8 `PFVariantMilk`-style
dynamic registration; one greyscale molten texture set + per-variant tint).
Cross-mod interop (accepting/emitting `c:` molten fluid tags so other mods'
casters and crucibles play along) layers on later as pure data.

**Design tension to resolve explicitly:** the ROADMAP's "Explicitly NOT planned"
list rejects a native crusher because the 2x crush payoff is *deliberately
delegated* to external crusher mods. A first-party Crucible + Casting Basin pair
gives every pack an in-house 2x metal lane, which reverses that stance in
economic effect (if not in mechanism - melting/casting is a new fluid mechanic,
not a re-implemented crusher). Acknowledge and re-litigate that entry in the
ROADMAP when this doc is promoted to a spec; options if 2x-everywhere is too
generous: melt yield config-gated (Spawnery precedent), doubled yield only for
some variants, or 1.5x melt vs 2x crush so crushers stay the ceiling.

## New content this needs

New `SlimeVariant` entries (datapack JSON, no Java):

- **lava slime** - thematic home: Infernal species. Primer: TBD (see Open Questions).
  `inner_block` / colors per the lava theme.
- **water slime** - thematic home: Tide species. Primer: TBD.

Their Froglights flow through the existing Configurable Froglight pipeline unchanged.

## Open questions (resolve before building)

1. **Primers for the water and lava slimes.** A lava/water bucket as the primer is
   net-neutral (spend a bucket to eventually get a bucket back), so we likely want a
   cheaper thematic primer. Candidates: magma block (lava), kelp or wet sponge
   (water). Undecided.
2. **Heat source list + bootstrap check.** Below-block heat is decided (see
   Decided shape); still open: the exact source-block list and tier ladder
   (e.g. torch < fire/soul fire < magma block < lava source?), whether the list
   is a block tag packs can extend (`productivefrogs:crucible_heat_sources`,
   pack-overridable like the Spawnery primer tags), and the bootstrap check -
   a skyblock pack must be able to reach its *first* heat source before having
   lava (magma block via the Infernal chain? fire on netherrack?). Also confirm
   GUI-less interaction (right-click insert, Jade readout) vs a minimal screen.
3. **Single tank, fluid switching.** If you melt a lava Froglight then a water
   Froglight into one tank, that mixes fluids. Lean: one tank that only accepts a
   Froglight whose fluid matches the current contents (must drain to switch). Confirm.
4. **Numbers:** melt time (ticks), tank capacity (mB), per-ingot mB convention for
   molten metals (Tinkers uses 90 mB/ingot; 2 ingots = 180 mB per metal Froglight
   at the decided doubling yield), water/lava yield per Froglight, and heat-tier
   speed multipliers if heat is passive (Q2b).
5. **Sequencing AND scope bucket.** Reopened by the heat decision: with no energy
   mechanic, is this a v1.x appliance (furnace+cauldron analogy, Milker capability
   precedent) or still the v2 opener (first internal fluid tank)? And does it
   precede or follow the Frog Terrarium?
6. **Molten metal fluids - remaining unknowns.** The sink is decided (PF ships
   the Casting Basin, above) and the source leans PF-minted `molten_<variant>`
   fluids. Still open:
   - Confirm PF-minted over mod-provided (is there any 1.21.1 NeoForge mod that
     registers molten metals worth deferring to? Needs the v1.3-style
     verified-provider check before committing either way).
   - Casting shapes: ingot-only, or nugget/block casts too (Tinkers precedent)?
   - Does casting need a coolant/time mechanic (water adjacency, ticks) or is it
     instant on fill?
   - The 2x-everywhere balance question and the "no native crusher" tension
     (see the Casting Basin section) - config-gate, partial roster, or reduced
     melt yield.
   - Naming: "Casting Basin" vs "Casting Table" vs a frog-flavored coinage
     (the Spawnery precedent).

## Related

- Scope split / why v2: [versioning.md](./versioning.md)
- Runway: [../ROADMAP.md](../ROADMAP.md)
- Appliance pattern + capability registration: `CLAUDE.md`
- Sibling appliance specs: [spawnery.md](./spawnery.md), [farming.md](./farming.md)
