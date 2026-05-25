# Architecture

How the mod is structured internally to support data-driven slime variants, cross-mod compat, and modpack extensibility.

## Guiding Principles

1. **Slime variants are fully data — addable by datapack alone.** A variant is a `slime_variant` JSON, not a Java class, and needs no Java edit, no per-variant assets, and no lang (display names fall back to a title-cased id). The spawn egg, Configurable Froglight, Slime Bucket, and Slime Milk are all single component-driven items/blocks whose per-variant stacks the creative tab + JEI enumerate from the registry. Slime Milk's fluid/block/bucket collapsed to one of each (variant on the `SLIME_VARIANT` component + source BlockEntity), removing the last per-variant Java edit — see [refactor_data_driven_variants.md](./refactor_data_driven_variants.md).
2. **Cross-mod support is conditional JSON.** No Java code calls Mekanism's classes; we reference `c:ingots/osmium` tags. Mod-specific JSON entries are wrapped in `neoforge:conditions → mod_loaded`.
3. **Category membership is tag-based.** Frog categories, slime categories, and primer tags all live in NeoForge's tag system. Lookups are O(1) tag membership checks.
4. **Drops are vanilla loot tables.** No bespoke drop logic — leverage vanilla's `LootTable` system for full flexibility (random ranges, fortune scaling, enchantment effects all work for free).

## Frogspawn Bottling Hook

`minecraft:glass_bottle` right-clicked on `minecraft:frogspawn` produces a `productivefrogs:frog_egg` item:

- Register a NeoForge `PlayerInteractEvent.RightClickBlock` listener.
- If the held item is `minecraft:glass_bottle` AND the targeted block is `minecraft:frogspawn`:
  - Shrink the held stack by 1 (consume one bottle).
  - Set the block at the target position to air (consume the frogspawn).
  - Give the player 1 `productivefrogs:frog_egg` item (or drop at the block position if inventory is full).
  - Play a "bottle pop" sound (vanilla `entity.item.pickup` or `block.brewing_stand.brew`).
  - Set the event result to `CONSUME` so vanilla doesn't double-process.
- If either condition fails, the event is left alone (no interference with vanilla bottle behavior).

No custom item, no new recipe — entirely event-driven.

## Slime Sourcing Hooks

The slime sourcing mechanic (see [slime_sourcing.md](./slime_sourcing.md)) lives in two hook points:

1. **Right-click interaction on a vanilla slime/magma cube** while holding a primer-tagged item:
   - Despawn the original entity.
   - Spawn a new `ResourceSlime` at the same position with the same size, HP, and velocity. Variant is set to the matching primer's category-mapped variant.
   - Consume one of the held item.
   - This is "infusion = immediate transformation" — there is no NBT-tracked infusion state.

2. **Vanilla slime split event** (when a vanilla parent slime is killed and would spawn smaller offspring):
   - Roll the configured discovery chance per offspring (default 5%).
   - On hit: look up the parent entity's default category, pick a random `SlimeVariant` from that category pool (weighted by each variant's optional `weight` field), and spawn as `ResourceSlime` at the child size.
   - On miss: spawn vanilla as normal.
   - Resource Slimes that split simply split into smaller Resource Slimes of the same variant — no roll needed.

The "parent entity → default category" mapping is itself data-driven via a small datapack registry (`data/<ns>/slime_split_pool/<entity_id>.json`), so modpack authors and mod compat can register new parent slime species without code.

## Slime Milker + Milk Fluid

The V1 farming keystone (see [farming.md](./farming.md)) introduces two architectural pieces:

### Slime Milker (block)

- Block entity that hosts the right-click handler. No persistent storage state in V1 (operation is instant).
- On right-click with a `Slime Bucket` item: read the bucket's stored variant, consume one bucket count, give the player a `Bucket of <Variant> Slime Milk` (replacing the slime bucket they were holding), and emit press animation + sound.

### Slime Milk fluid (single, component-driven)

- **One** `productivefrogs:slime_milk` fluid (Source + Flowing), one `slime_milk` source block, one `slime_milk_bucket` item — not one per variant. The variant rides on the bucket's `SLIME_VARIANT` data component and the source block's `SlimeMilkSourceBlockEntity`; placing the bucket writes the variant to the BE, re-bucketing reads it back. Only a source with a variant spawns slimes; spread/flowing milk is inert.
- Per-variant colour is applied at render time: the fluid's `getTintColor(state, level, pos)` reads the source BE → `SlimeVariant.primaryColor`; the bucket's item color reads the component. One greyscale texture set serves every variant. This collapse is what lets a datapack variant get milk with no Java edit (fluids register at mod-init, before world datapacks load, so a per-variant fluid could never be datapack-added).
- Flow behavior matches lava (slower than water, 4-block overworld flow distance).
- Spawn behavior: each source block has a random-tick handler. On each random tick (subject to vanilla random tick speed), with low probability, attempts a spawn:
  - Check the position above is air or replaceable AND no entity is blocking spawn.
  - Spawn a `ResourceSlime` (or vanilla slime / magma cube for the vanilla/magma milk variants) at size 1 of the matching variant.
  - Decrement the source block's depletion counter (stored in block state via integer property `spawns_remaining`, or in block entity if more granular range is needed). When counter hits zero, replace the source block with air.
- Default spawn interval is implemented as: average ~20s wall-clock between spawn ticks per source block, ± uniform jitter [-10s, +10s]. Random tick speed tuning derives from this target.
- Default depletion: 16 spawns per source block before drying up. Configurable (mod config); can be disabled entirely (effectively ∞ counter).

### No variant explosion (collapsed design)

An earlier design registered one fluid/block/bucket per variant, which scaled
with the variant count and could never be datapack-added (fluids register at
mod-init, before world datapacks load). That was collapsed: there is now exactly
**one** `slime_milk` fluid + source block + bucket, with the variant riding on
the `SLIME_VARIANT` data component and the source `BlockEntity` (see the Slime
Milk fluid section above and [refactor_data_driven_variants.md](./refactor_data_driven_variants.md)).
A datapack variant therefore gets milk, a spawn egg, a Slime Bucket, and a
Configurable Froglight with zero Java edits and zero new registrations.

## Slime Variant Pattern

Modeled on vanilla 1.21's cat/wolf/frog variant system (data-driven entity variants registered as a datapack registry).

### One Entity Class, N Variants

```
ResourceSlime extends Slime {
    private SlimeVariant variant;  // points into the data registry
    // size, drops, texture all derived from variant
}
```

### Variant JSON Schema

`data/<namespace>/productivefrogs/slime_variant/<name>.json` (the doubled
`productivefrogs/` segment is NeoForge datapack-registry path convention, not a
typo). A first-party variant (`iron.json`):

```json
{
  "primer_item": "minecraft:iron_ingot",
  "category": "cave",
  "primary_color": 14211288,
  "secondary_color": 12632256,
  "inner_block": "minecraft:iron_block"
}
```

Fields, decoded by `SlimeVariant.CODEC`:

| Field | Required | Meaning |
|---|---|---|
| `primer_item` | one of `primer_item`/`primer_tag` | exact item id that primes this variant (vanilla / first-party items) |
| `primer_tag` | one of `primer_item`/`primer_tag` | item tag whose members each prime this variant (cross-mod, e.g. `c:ingots/tin`) |
| `category` | yes | parent category: `bog` / `cave` / `geode` / `tide` / `infernal` / `void` |
| `primary_color` | yes | outer-shell tint, 24-bit RGB int in `[0, 0xFFFFFF]` |
| `secondary_color` | yes | secondary tint, same range |
| `weight` | no (default 1) | relative weight in the random discovery pool |
| `inner_block` | no | vanilla block id whose texture is baked inside the translucent slime |
| `spawn_entity` | no | EntityType a Slime Milk source spawns instead of the default `ResourceSlime` |

The codec rejects a variant declaring neither `primer_item` nor `primer_tag` at
datapack load (it could never be primed, yet would still enter the discovery
pool). When both kinds of variant could match one item, an exact `primer_item`
match wins over a `primer_tag` match, deterministically. There is no
`display_name` / `texture` / `loot_table` / `spawn` block: names fall back to a
title-cased id, the shell is a tinted shared texture, drops are the Configurable
Froglight stamped with the variant, and discovery is driven off `parent_species`
+ `weight` rather than per-variant spawn rules.

Optional `neoforge:conditions` gate whether the variant loads at all; cross-mod
variants use them (see below).

### Variant Registration Flow

```
On server start / datapack reload:
  1. Scan data/*/slime_variant/*.json
  2. For each file, evaluate neoforge:conditions
     - If conditions fail, skip silently
     - If conditions pass, parse via Codec
  3. Insert into the SlimeVariant datapack registry
  4. Tags update accordingly (slime_category/<X> resolves to live variant set)
```

## Variant resolution at runtime

v1.0 deleted the `productivefrogs:primer/<category>` tag system. Variant → primer resolution now goes through the `SlimeVariant` datapack registry directly via `SlimeVariant.findByPrimer(registry, heldStack)`, which matches a held item against each variant's `primer_item` (exact item id) **or** `primer_tag` (tag membership, resolved at runtime where tags are loaded). Both slime infusion and Frog Egg priming share this resolver (see `species_as_category_redesign.md` §Slime infusion). An exact `primer_item` match always wins over a `primer_tag` match, deterministically regardless of registry order.

- **"Can this primer prime a Frog Egg block?"** `SlimeVariant.findByPrimer(registry, held)` — if a variant matches (by item id or tag), its `category()` selects the Primed Frog Egg block.
- **"Will this frog eat this slime?"** `frog.getCategory() == slime.getCategory()` — direct enum equality. Both sides carry a `Category` reference.

### Cross-mod variant additions

Cross-mod variants ship as JSON `SlimeVariant` entries, primed off a `c:` common tag where one exists (so any providing mod's item primes them) and gated `neoforge:conditions → mod_loaded` on the canonical provider whose item the smelt-back recipe emits. The full strategy, the curated list, and the gating reasoning (NeoForge forbids `tag_empty` conditions on datapack-registry entries, so the load gate is `mod_loaded`, not the tag) live in [cross_mod_compat.md](./cross_mod_compat.md). Example, the shipped tin variant (`slime_variant/tin.json`):

```json
{
  "neoforge:conditions": [
    { "type": "neoforge:mod_loaded", "modid": "alltheores" }
  ],
  "primer_tag": "c:ingots/tin",
  "category": "cave",
  "primary_color": 13161170,
  "secondary_color": 10463920
}
```

The `mod_loaded` gate keeps the variant out of the registry when its provider is absent — no errors, no broken references. The `primer_tag` then lets any mod's tin ingot prime it at infusion time. A paired `minecraft:smelting` recipe (gated the same way) smelts the resulting Configurable Froglight back to the provider's ingot. Bespoke variants with no clean common tag (e.g. Powah crystals) use `primer_item` against the mod's exact id instead.

No tag files involved. The variant either exists in the registry (mod present) or it doesn't (mod absent); no broken-reference state. Cross-mod loot tables / recipes can still wrap entire JSON files in `neoforge:conditions` when needed.

## Project Layout (planned)

```
productive-frogs/
├── README.md
├── docs/                               # design docs (this directory)
├── gradle/                             # gradle wrapper
├── build.gradle
├── settings.gradle
├── gradle.properties
├── src/
│   └── main/
│       ├── java/com/flatts/productivefrogs/
│       │   ├── ProductiveFrogs.java            # mod entry, modid: "productivefrogs"
│       │   ├── registry/                       # block/item/entity/fluid registries
│       │   │   ├── PFBlocks.java
│       │   │   ├── PFItems.java
│       │   │   ├── PFEntities.java
│       │   │   ├── PFFluids.java
│       │   │   └── PFDataComponents.java
│       │   ├── content/
│       │   │   ├── item/
│       │   │   │   ├── FrogEggItem.java
│       │   │   │   ├── SlimeBucketItem.java
│       │   │   │   └── SlimeMilkBucketItem.java
│       │   │   ├── block/
│       │   │   │   ├── FrogEggBlock.java
│       │   │   │   ├── PrimedFrogEggBlock.java
│       │   │   │   └── SlimeMilkerBlock.java
│       │   │   ├── entity/
│       │   │   │   ├── ResourceSlime.java
│       │   │   │   ├── ResourceTadpole.java
│       │   │   │   ├── ResourceFrog.java
│       │   │   │   ├── CaveSlime.java
│       │   │   │   ├── GeodeSlime.java
│       │   │   │   ├── TideSlime.java
│       │   │   │   └── VoidSlime.java
│       │   │   └── fluid/
│       │   │       ├── SlimeMilkFluid.java     # base custom Fluid
│       │   │       └── SlimeMilkFluidType.java # base FluidType
│       │   ├── data/
│       │   │   ├── SlimeVariant.java           # record + Codec
│       │   │   ├── PFDataRegistries.java       # registers slime_variant datapack registry
│       │   │   └── PFTags.java                 # tag key constants
│       │   ├── datagen/                        # data generation entry point
│       │   │   ├── PFDataGenerator.java
│       │   │   ├── PFTagsProvider.java
│       │   │   ├── PFLootTableProvider.java
│       │   │   └── PFRecipeProvider.java
│       │   └── compat/
│       │       └── (currently empty — compat is purely data, not code)
│       └── resources/
│           ├── META-INF/neoforge.mods.toml
│           ├── pack.mcmeta
│           ├── assets/productivefrogs/
│           │   ├── lang/en_us.json
│           │   ├── textures/
│           │   ├── models/
│           │   └── ...
│           └── data/productivefrogs/
│               ├── productivefrogs/slime_variant/
│               │   ├── iron.json
│               │   ├── copper.json
│               │   ├── gold.json
│               │   ├── redstone.json
│               │   ├── osmium.json           # mod_loaded: mekanism
│               │   ├── bronze.json           # mod_loaded: create
│               │   └── ...
│               ├── productivefrogs/parent_species/
│               │   ├── bog_slime.json
│               │   ├── cave_slime.json
│               │   ├── geode_slime.json
│               │   ├── tide_slime.json
│               │   ├── infernal_slime.json
│               │   └── void_slime.json
│               ├── loot_tables/entities/slime/
│               │   ├── iron.json
│               │   ├── copper.json
│               │   └── ...
│               └── recipes/
│                   └── (no custom recipes for frogspawn capture — vanilla glass bottle handles it via event hook)
└── .gitignore
```

## Build Configuration

- **Loader**: NeoForge 21.1.230 for MC 1.21.1
- **Java**: 21
- **Gradle**: NeoGradle plugin (latest)
- **Mappings**: official (Mojang)

## NeoForge-Only, Forever

Productive Frogs is **NeoForge-only**. No Fabric port is planned in V1 or any future version.

- No Architectury abstraction layer. All code uses native NeoForge APIs directly.
- Single Gradle module, single platform — simpler than the sibling `flatts-chem-lib` multi-loader layout.
- Mod APIs we depend on that are NeoForge-specific:
  - `IItemExtension.interactLivingEntity` (for slime infusion right-click)
  - `neoforge:conditions → mod_loaded` for cross-mod compat
  - NeoForge datapack registries for `slime_variant` and `parent_species`
  - Custom `Fluid` + `FluidType` for the single Slime Milk fluid
- Target audience: ATM10 / NeoForge ecosystem players. Fabric audience is explicitly out of scope.

## Testing Strategy (sketch — to be expanded)

- **Unit tests** for codec round-trips on SlimeVariant JSON (including the no-primer rejection and the `primer_tag` decode).
- **Game-test framework** scenarios for: priming an egg, hatching, a frog eating a same-category slime, the drop appearing, and the cross-mod resolution paths (condition gating, `primer_tag` membership, exact-item-over-tag precedence).
- **Manual integration smoke test** with AllTheOres installed: prime a Cave Slime with a tin ingot, confirm it becomes a tin Resource Slime, feed it to a Cave Frog, and verify the tin Configurable Froglight drops and smelts back to a tin ingot.
