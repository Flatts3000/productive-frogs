# Froglight Juicer (design notes)

> **Status: PARKED for v2.** These are design notes from a scoping conversation,
> not a build spec. The shape below is decided; the Open Questions are not. Do not
> implement from this doc until those are resolved and it is promoted to a real
> spec (cf. `docs/spawnery.md`). Captured 2026-05-26.

## Concept

A processing block that converts a **Froglight into a fluid** - a third cash-out
lane for a Froglight, alongside the two that ship today:

- smelt -> 1x solid resource
- crush (with a crusher mod) -> 2x dust -> smelt -> 2x
- **Froglight Juicer -> a fluid**

The Froglight is already the "ore-equivalent" the frog drops; this just adds a
fluid output route. The Froglight carries everything needed to pick the output
(the Configurable Froglight's `SLIME_VARIANT` component; category Froglights map
to their resource), exactly like the v1.3 crush recipes match a Froglight via
`neoforge:components`.

## First iteration: water and lava

The v1 of this feature ships exactly two fluids, both vanilla, so the mod mints no
new fluids:

- a **lava slime** -> (eaten by its frog) -> **lava Froglight** -> (Juicer) -> **lava**
- a **water slime** -> **water Froglight** -> (Juicer) -> **water**

This makes water and lava themselves the farmable resource. The payoff is renewable
water and lava through the frog loop - exactly what skyblock and nether-locked packs
lack (lava for fuel / obsidian gen; water that does not evaporate). It pairs with the
Spawnery's skyblock framing.

## Decided shape

- **Name:** Froglight Juicer.
- **Input:** a (variant) Froglight. Recipe-driven mapping, see below.
- **Output:** an **internal fluid tank**. Drain it by right-clicking with an empty
  bucket, or by pipe via `Capabilities.FluidHandler.BLOCK`. (NOT a bucket-output
  slot like the Milker.)
- **Energy:** the block **requires power**. Use NeoForge's built-in energy
  (`IEnergyStorage` + `Capabilities.EnergyStorage.BLOCK`) - the FE-equivalent every
  power mod (Mekanism, IE, Powah, EnderIO, Create addons) bridges to. No hard
  dependency. Internal energy buffer + consume-per-operation.
- **Recipe-driven Froglight -> fluid mapping** (datapack), not hardcoded. The block
  reads "this variant Froglight produces this fluid (this many mB)." v1 ships two
  recipes (lava Froglight -> lava, water Froglight -> water); later fluids and
  cross-mod molten metals are pure JSON gated by `neoforge:conditions` ->
  `mod_loaded`, no new Java. Same ethos as the variant system and the crush recipes.
- Reuses the appliance pattern (Block + BlockEntity + Menu + Screen + ContainerData
  + static serverTick) documented in `CLAUDE.md`, extended with the energy and
  fluid-tank capabilities.

## Why this is v2, not a v1.x appliance

The two mechanics it introduces - an energy requirement and a pipe-able internal
fluid tank - are exactly the automation features v2 is defined by (see
`docs/versioning.md`; energy is the v2 "FE power compat" line). It is, however, the
**lightest possible v2 block**: a single block, no multiblock. That makes it a good
candidate to open v2 with, because it forces us to build the **energy-capability**
and **fluid-tank-capability** patterns that every later v2 block (Frog Terrarium,
buffered Slime Milker) will reuse.

## New content this needs

New `SlimeVariant` entries (datapack JSON, no Java):

- **lava slime** - thematic home: Infernal species. Primer: TBD (see Open Questions).
  `inner_block` / colors per the lava theme.
- **water slime** - thematic home: Tide species. Primer: TBD.

Their Froglights flow through the existing Configurable Froglight pipeline unchanged.

## Open questions (resolve before building)

1. **Primers for the water and lava slimes.** A lava/water bucket as the primer is
   net-neutral (spend a bucket to eventually get a bucket back), so we likely want a
   cheaper thematic primer. Undecided.
2. **"No power mod" handling.** With energy required, the Juicer is dead weight in a
   pack with no power mod. Options: (a) accept it as a tech-pack-only block;
   (b) a config toggle to run powerless; (c) a fuel-burn fallback like the Spawnery.
   Undecided.
3. **Single tank, fluid switching.** If you juice a lava Froglight then a water
   Froglight into one tank, that mixes fluids. Lean: one tank that only accepts a
   Froglight whose fluid matches the current contents (must drain to switch). Confirm.
4. **Numbers:** energy cost per juice, juice time (ticks), tank capacity (mB), and
   yield (mB of fluid per Froglight).
5. **Sequencing:** is the Juicer the first v2 block built, or does it follow the
   Frog Terrarium?

## Related

- Scope split / why v2: [versioning.md](./versioning.md)
- Runway: [../ROADMAP.md](../ROADMAP.md)
- Appliance pattern + capability registration: `CLAUDE.md`
- Sibling appliance specs: [spawnery.md](./spawnery.md), [farming.md](./farming.md)
