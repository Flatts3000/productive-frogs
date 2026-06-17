# The Equivalence Lane (#253)

The **Equivalence (EE) lane** is a post-capstone, RF-powered transmutation lane
that lets players synthesize **off-roster** items - anything the `slime_variant`
registry never reached (arbitrary vanilla/modded resources). It is the mod's
Equivalent-Exchange "you've won, transmute freely" sandbox, gated so it never
competes with the authored slime chains.

It is **purely additive**: the `slime_variant` registry, the discovery chains,
the per-variant milk, the six species frogs, and the appliances are unchanged.
The lane is new entities/blocks/handlers plus isolated added branches in shared
code that never alter an existing path.

## The loop

```
Alembic (empty bucket + OFF-ROSTER item + RF)
  -> Mimic Slime Bucket
  -> Milker -> Mimic Milk (source-only fluid)
  -> place -> spawns Mimic Slimes (1 -> N, the milk spawn economy)
  -> Midas (Kiss-primed frog, eats ONLY Mimic Slimes)
  -> Prismatic Froglight (Bounty-scaled drop)
  -> Distiller (+ RF)
  -> the original item
```

The **Churn** runs the loop in reverse (Mimic Milk -> Mimic Slime Buckets). The
**Froglight Cleaver** also drops a Prismatic Froglight when it kills a Mimic Slime.

## The join key: `SYNTHESIZED_ITEM`, not a variant

Every synthesized surface carries the `productivefrogs:synthesized_item` data
component - an arbitrary item id, mutually exclusive with `SLIME_VARIANT`. This
sidesteps the v1.8 per-variant-fluid wall entirely: nothing is minted at
mod-init; the item id rides on the stack / entity / BE.

- **Name + tint are computed at runtime** from the item id via
  `client/SynthesizedTint` (`colorFor(Item)`): average the opaque pixels of the
  item's sprite, with a **block map-colour fallback** for block items (whose
  block-atlas particle sprite has no readable original image) and a no-cache
  policy on failure (so an early-frame miss isn't cached as white). This is the
  runtime counterpart to a variant's `primary_color`, feeding the Prismatic
  Froglight (item + block + Jade), the Mimic Slime shell, the Mimic Milk fluid +
  bucket.

## Components

- **Alembic** (`content/block/AlembicBlock`, RF machine): empty bucket +
  off-roster item + RF -> a Mimic Slime Bucket carrying the item. The player
  supplies the bucket, so buckets are conserved through the lane. PF's RF-machine
  pattern (receive-only `EnergyStorage`, transactional consume+emit) - the
  Distiller shares it.
- **Distiller** (`content/block/DistillerBlock`, RF machine): a Prismatic
  Froglight + RF -> the carried item, read from the component in code (a machine,
  NOT a dynamic smelting recipe).
- **Mimic Slime** (`content/entity/MimicSlime`): a sibling of `ResourceSlime`
  (extends vanilla `Slime`), NOT a subclass - so `instanceof ResourceSlime` in
  the species sensor/drop excludes it by construction. Always size 1, never
  splits. Greyscale body + eyes; the translucent shell tints to the item.
- **Mimic Milk** (`content/fluid/MimicMilkFluid`): ONE fluid, **source-only /
  non-flowing** (`canSpreadTo` returns false), so every block is a BE-backed
  source whose tint + spawn item read off the BE. `MimicMilkSourceBlock` reuses
  the static `MilkSpawnEconomy` to spawn Mimic Slimes; leaner than the variant
  source (no catalysts/density-cap).
- **Midas** (the frog): a `ResourceFrog` with a `midas` marker flag (NOT a 7th
  `Category` - that would spray phantom Midas content through every
  `Category.values()` surface). Kiss-primed, breeds true, eats only Mimic Slimes,
  drops Bounty-scaled Prismatic Froglights. See below.
- **Prismatic Froglight**: the `configurable_froglight` carrying
  `SYNTHESIZED_ITEM` instead of `SLIME_VARIANT`. Name "`<item>` Froglight"; tint
  from the resolver. JEI-invisible by construction (the subtype interpreter keys
  on `SLIME_VARIANT`).

## Midas - a flag, not a category

Midas rides the existing egg -> tadpole -> frog + stat-breeding pipeline via a
`midas` boolean threaded exactly like the bred stats:

- **Kiss priming**: right-clicking vanilla frogspawn with a Princess's Kiss makes
  a Midas egg (a dedicated case in `EggPrimerHandler`, ahead of the variant
  lookup - the Kiss isn't a variant). The egg reuses the VOID block as its
  carrier + the midas flag, so it hatches Midas tadpoles. The Kiss's existing
  frog->villager use (a live-entity interaction) is untouched.
- **Breeds true**: `canMate` gates Midas x Midas only (never with a species frog,
  even one sharing the VOID sentinel category). `LayCategoryFrogspawn` stamps the
  laid egg as Midas and skips the Incubator path.
- **Diet**: `ResourceFrogAttackablesSensor` gains a midas branch - eats only
  `MimicSlime` (the species path's `instanceof ResourceSlime` excludes Mimic
  Slimes).
- **Drop**: `MidasTongueDropHandler` (a new handler) - a Midas eating a Mimic
  Slime drops a Bounty-scaled Prismatic Froglight with the eaten slime's item.
  The species `FrogTongueDropHandler` ignores Mimic Slimes, so the two never
  overlap.
- Renders gold; names "Midas".

The egg carrier is VOID purely as a sentinel; the midas flag drives all
divergent behaviour.

## Dupe-safety (the Alembic input filter)

Allow-by-default (ProjectE posture), with hard exclusions - see
`AlembicBlockEntity.canSynthesize`:

1. The pack-overridable `productivefrogs:alembic/denied` item tag
   (command/structure blocks, bedrock, spawn eggs, boss resources, ...).
2. **Component-bearing items are refused** (`getComponentsPatch().isEmpty()`): a
   type-only lane that strips components would emit a meaningless or invalid item
   (a Patchouli guide book with no book id reads "Invalid book"). So only *plain*
   items synthesize - this also blocks container laundering and component-stripping
   generally.
3. Our own pipeline items (Froglight + every milk/slime bucket) - no recursion.
4. The roster gate: `SlimeVariant.findByPrimer != null` -> refuse (it has an
   authored lane; covers the weight-0 boss primers too).
5. Transactional: input + RF are consumed on the tick the output is produced.

The Alembic screen draws a **red X over the arrow** whenever the item slot holds
something `canSynthesize` refuses.

## Gating

`equivalence.enabled` (**default FALSE - opt-in**) is the master toggle, via
`ConfigEnabledCondition.Key.EQUIVALENCE` (recipes) and `PFConfig.equivalenceEnabled()`
(behaviour). The gate is **whole-lane**, not just visibility: the Alembic + Distiller
recipes carry the `config_enabled` condition (uncraftable + JEI-hidden when off), the
creative-tab entries hide, AND every behavioural surface is inert when off - the
Alembic/Distiller `serverTick` no-op, the Princess's Kiss won't prime a Midas egg
(`EggPrimerHandler`), Midas frogs drop nothing (`MidasTongueDropHandler`), and Mimic
Milk sources + Terrarium Sprinklers spawn no Mimic Slimes. (This is stricter than the
Spawnery, whose placed block keeps working when its flag is off.) The EE GameTests set
`PFConfig.equivalenceEnabledOverride = true` to drive the machines under the off
default. Access is further gated by Midas (the Kiss drops from the Dragon Altar) and
throttled by RF.

Recipe materials: **copper + glass + a nether star** (the texture follows the
recipe per PF convention - copper-framed glass apparatus with a glowing
nether-star core), which also makes the machines Wither-gated to craft.

## Documented caveat (not a regression)

Mimic Milk is one component-carrying fluid, so it automates fine through PF's own
Milker / Churn (which read the item off the stack/BE), but piping it through a
third-party fluid tank may strip the item id - the same reason v1.8 went
per-variant. It is a property of this *new* fluid; the per-variant milks are
unaffected.

## Status / deferred

Shipped: the full hand-built + standalone-appliance loop (Alembic, Distiller,
Mimic Slime + bucket, Mimic Milk source + bucket, Midas, Milker + Churn
branches), the config gate, and the textures.

Deferred: **Terrarium automation** of the lane (the Sprinkler + Terrarium
Controller + `MilkCharge` carrying the synthesized item, and a Midas laying into
a Terrarium Incubator) - an intricate thread through core multiblock classes,
held back to protect the existing Terrarium. GameTests + the Patchouli guide
entry are also pending.
