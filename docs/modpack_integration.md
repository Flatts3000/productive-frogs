# Modpack Integration Guide

The single entry point for **modpack and datapack authors**. Productive Frogs is built to be tuned from data: every cross-mod hook, resource, primer, and balance knob is a config value, a datapack JSON, or an item tag - **no hard dependencies, no per-mod Java, and nothing breaks when an optional mod is absent.** Drop the jar into any 1.21.1 / NeoForge 21.1.230 pack and the relevant features light up on their own.

This doc is the consolidated surface; each section links to the detailed design doc.

## Quick reference - what you can tune and where

| Want to... | Surface | Where |
|---|---|---|
| Turn the Spawnery on (skyblock) | config `spawnery.enabled` | `config/productivefrogs-common.toml` |
| Change which item primes a frog species | item tag | `tags/item/spawnery_primer/<species>` |
| Add a new farmable resource (vanilla or modded) | datapack registry | `data/<ns>/productivefrogs/slime_variant/<name>.json` |
| Map a (modded) slime entity to a species | datapack registry | `data/<ns>/productivefrogs/parent_species/<name>.json` |
| Tune milk output / spawn cadence / discovery rate | config | `config/productivefrogs-common.toml` |
| Disable specific resources, whole species, or the boss tier | config `[variants]` | `config/productivefrogs-common.toml` |
| Add or change cross-mod crush yields | recipe JSON | `data/<ns>/productivefrogs/recipe/<modid>/<name>.json` |

`<ns>` is your pack's namespace for new content, or `productivefrogs` to override the mod's own files.

## 1. Config (`config/productivefrogs-common.toml`)

A COMMON config, generated on first launch. Toggling a value that gates a recipe (the Spawnery) requires a world reload.

| Section | Key | Default | Meaning |
|---|---|---|---|
| `[spawnery]` | `enabled` | `false` | Enable the Spawnery (skyblock frogspawn bootstrap). Off by default; a normal world has swamps. When off it is uncraftable + hidden from JEI/creative. |
| `[spawnery]` | `productionTicks` | `200` | Ticks per bottled egg (one slime ball of burn). |
| `[slime_milk]` | `depletionEnabled` | `true` | Whether Slime Milk source blocks deplete after a fixed number of spawns. Set `false` for infinite (low-friction) production. |
| `[slime_milk]` | `depletionCount` | `16` | Spawns before a fresh source drains. (Count catalysts raise a placed source past this.) |
| `[slime_milk_spawning]` | `minSpawnIntervalTicks` | `200` | Min delay between slime spawns from a source. |
| `[slime_milk_spawning]` | `maxSpawnIntervalTicks` | `600` | Max delay (uniform random in [min, max]). |
| `[slime_milk_catalysts]` | `enabled` | `true` | Slime Milk catalysts (Count / Speed / Quantity / Infinite, dropped into a source to buff it). Off = uncraftable + hidden from JEI; placed sources and existing upgrades still work. See [slime_milk_catalysts.md](./slime_milk_catalysts.md). |
| `[slime_milk_catalysts]` | `countPerCatalyst` | `16` | Spawns added per Count catalyst. |
| `[slime_milk_catalysts]` | `maxSpeedLevel` / `maxQuantityLevel` | `4` / `3` | Caps on the Speed / Quantity upgrades. |
| `[slime_milk_catalysts]` | `speedReductionPerLevel` / `minIntervalFloorTicks` | `0.20` / `20` | Speed scaling and the spawn-interval floor. |
| `[discovery]` | `discoveryChancePerOffspring` | `0.05` | Per-offspring chance a parent-species split converts to a Resource Slime. |
| `[variants]` | `disabledVariants` | `[]` | Variant ids to force-off (e.g. `["productivefrogs:iron", "productivefrogs:tin"]`). A disabled variant is unprimable, never discovered, and hidden from JEI + the creative tab. |
| `[variants]` | `disabledCategories` | `[]` | Whole species to force-off, by lowercase name: `cave`, `geode`, `bog`, `tide`, `infernal`, `void`. Disables every variant in the species. |
| `[variants]` | `bossVariantsEnabled` | `true` | The boss tier's prime-only variants (wither skull, nether star, dragon egg, dragon breath) in one switch. `false` makes them unprimable + hidden. Narrower than `[boss] enabled` - drops just the variants, keeps the altar blocks craftable. |
| `[boss]` | `enabled` | `true` | Master switch for the whole boss tier. `false` suppresses the four boss variants (like `bossVariantsEnabled=false`, so no toxic milk / altar gating), makes the four catalyst-altar blocks uncraftable + hidden, and drops the boss Froglight smelt-backs - the standard loop with no boss farming in one toggle. Recipe gating needs a world reload. |

### Scoping content with `[variants]`

Use this to ship a smaller mod surface without datapack surgery - say, only the Cave and Bog lines (`disabledCategories = ["geode", "tide", "infernal", "void"]`), or to drop a single noisy resource (`disabledVariants = ["productivefrogs:redstone"]`). The toggles are a **soft hide**: the registry entry stays, so a disabled variant's already-placed slimes, buckets, and froglights keep working, and re-enabling restores everything (no save surgery). One caveat - a disabled variant may still have its per-variant Slime Milk *fluid* registered (that is minted at mod-init, before this config loads), but it stays unobtainable since every way to reach the variant is gated. Changes apply on world reload.

## 2. Adding a farmable resource (the `slime_variant` registry)

A "variant" is one resource a frog can farm. Adding one is **a single JSON, no Java**. Path (the double `productivefrogs` is NeoForge datapack-registry convention, not a typo):

```
data/<ns>/productivefrogs/slime_variant/<name>.json
```

Fields: `primer_item` (exact item id) and/or `primer_tag` (item tag - one entry covers every mod that provides that tag), `category` (one of `cave`/`geode`/`bog`/`tide`/`infernal`/`void`), `primary_color`, `secondary_color`, `weight`, optional `inner_block` (the block shown inside the slime) and `spawn_entity`. A variant must declare at least one of `primer_item` / `primer_tag`. The smelt-back resource lives in the variant's generated smelting recipe, not on the variant.

Gate a modded variant so it only loads when its mod is present:

```json
{
  "neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "mekanism" }],
  "primer_tag": "c:ingots/osmium",
  "category": "cave",
  "primary_color": "...", "secondary_color": "...", "weight": 5
}
```

Prefer NeoForge `c:` common tags (`c:ingots/tin`, `c:gems/ruby`, ...) so one entry covers every mod that registers that tag. Full strategy + the shipped cross-mod shortlist: [cross_mod_compat.md](./cross_mod_compat.md). Schema details: [architecture.md](./architecture.md).

**Lang note:** a datapack can't ship client lang (lang is a client asset, datapacks are server data), so a pack-added variant's display name falls back to a title-cased version of its id ("Osmium" -> "Osmium Slime"). That fallback is intentional; the mod ships explicit lang only for its own variants.

## 3. Mapping a modded slime to a species (the `parent_species` registry)

To make another mod's slime act as a PF parent species (so its splits can discover Resource Slimes), drop:

```
data/<ns>/productivefrogs/parent_species/<name>.json
```

```json
{ "entity_type": "somemod:crystal_slime", "category": "geode" }
```

Vanilla `minecraft:slime` and `minecraft:magma_cube` are deliberately **not** parent species. See [species_as_category_redesign.md](./species_as_category_redesign.md).

## 4. Retuning Spawnery primers (the `spawnery_primer/<species>` tags)

The Spawnery turns glass bottles into bottled frogspawn; the **primer** decides the species. A primer is required - a slime ball primes plain vanilla frogspawn, and one item per species primes that species. The defaults are normal-world resources; a pack overrides any species by editing one item tag.

| Species | Default primer | Tag |
|---|---|---|
| Cave | iron ingot | `productivefrogs:spawnery_primer/cave` |
| Geode | amethyst shard | `productivefrogs:spawnery_primer/geode` |
| Bog | bone | `productivefrogs:spawnery_primer/bog` |
| Tide | prismarine shard | `productivefrogs:spawnery_primer/tide` |
| Infernal | blaze powder | `productivefrogs:spawnery_primer/infernal` |
| Void | ender pearl | `productivefrogs:spawnery_primer/void` |

A skyblock pack (iron gated, cobblestone infinite) swaps the Cave primer - datapack:

```json
// data/productivefrogs/tags/item/spawnery_primer/cave.json
{ "replace": true, "values": ["minecraft:cobblestone"] }
```

KubeJS: `ServerEvents.tags('item', e => { e.remove('productivefrogs:spawnery_primer/cave', 'minecraft:iron_ingot'); e.add('productivefrogs:spawnery_primer/cave', 'minecraft:cobblestone') })`

CraftTweaker: `<tag:items:productivefrogs:spawnery_primer/cave>.remove(<item:minecraft:iron_ingot>); <tag:items:productivefrogs:spawnery_primer/cave>.add(<item:minecraft:cobblestone>);`

A modded item that may be absent: list it soft - `{ "id": "othermod:thing", "required": false }`. The Spawnery gates a **species** (the whole frog), not a single resource. Full design: [spawnery.md](./spawnery.md).

## 5. Cross-mod crush yields

With Mekanism, Immersive Engineering, or EnderIO installed, a metal Froglight crushes to 2 dust (2 ingots) instead of the 1 from a direct smelt. These ship as optional `mod_loaded`-gated recipes under `data/productivefrogs/recipe/<modid>/<metal>.json`; AllTheOres broadens the metal coverage. To add or retune them for your pack, drop recipes following the same shape. See [cross_mod_compat.md](./cross_mod_compat.md) (Crushing compat) and [v1_3_crush_recipes.md](./v1_3_crush_recipes.md).

## 6. Tank / hopper / pipe integration

- **Slime Milk in tanks:** the Slime Milk bucket exposes `Capabilities.Fluid.ITEM` (vanilla `BucketItem` auto-registration), and the source block uses vanilla `LiquidBlock` pickup, so any tank-mod ecosystem (Mekanism, Thermal, Create, Fluid Tanks) can pull it.
- **Hopper automation:** the Slime Milker and Spawnery expose a side-aware `Capabilities.ItemHandler.BLOCK` - insert from the top/sides (routed to the right slot), extract finished output from the bottom.
- **Crucible automation (v1.12):** hoppers insert Froglights (recipe-validated, bounced if the queue is full or the fluid type mismatches); pipes and buckets extract the melted fluid through `Capabilities.FluidHandler.BLOCK` (extract-only - the input is items).
- **Casting Mold automation (v1.12):** pipes and buckets fill molten in through `Capabilities.FluidHandler.BLOCK` (fill-only and recipe-validated; committed molten leaves as a cast item, never as fluid), and hoppers pull cast ingots from the output. Stacked directly on a Crucible, the Mold drinks from it with no pipes at all.

**v1.8 - variant Slime Milk is pipe-automatable.** Each shipped variant's Slime Milk is now its own fluid (rather than one fluid keyed by component), so a fluid-automation mod can collect a variant's milk from a source, move it through pipes/tanks, and place it back as the same variant - a chosen variant then farms fully hands-off. Caveat: catalyst buffs survive a hand bucket re-pickup but are not preserved when milk goes through a tank. **Authoring NEW automatable variants beyond the shipped set is a planned follow-up:** today a pack-added (world-datapack) variant gets full slime/milk/bucket content but not yet its own pipe-routable fluid, because those fluids register at mod init from a bundled index of shipped variants. Design and the scoping rationale: [automated_milk_variants.md](./automated_milk_variants.md).

## 7. The drop-in guarantee

Every cross-mod hook is gated by `neoforge:conditions -> mod_loaded` and keys off `c:` common tags. There is no hard mod dependency and no `compat/` Java package: a variant or recipe for an absent mod silently does not load. The same jar is safe to drop into any pack; nothing crashes when a referenced mod is missing.

## See also

- [cross_mod_compat.md](./cross_mod_compat.md) - cross-mod variant strategy + the curated resource shortlist + crush compat
- [spawnery.md](./spawnery.md) - the Spawnery, in full (primers, config gating, override examples)
- [architecture.md](./architecture.md) - the datapack-registry schema and the mod's internal model
- [versioning.md](./versioning.md) / [ROADMAP.md](../ROADMAP.md) - what's V1 vs V2
