# Architecture

How the mod is structured internally to support data-driven slime variants, cross-mod compat, and modpack extensibility.

## Guiding Principles

1. **Slime variants are mostly data.** A variant is a `slime_variant` JSON, not a Java class. Two one-line Java edits still register its spawn egg and Slime Milk fluid (item/fluid registration runs at mod-init, before datapack registries load); see `docs/code_review_2026_05_24.md` CR-9 for the path to removing the spawn-egg one.
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

### Slime Milk fluid (one variant per slime variant)

- Each slime variant has a corresponding fluid registered: `productivefrogs:<variant>_slime_milk`.
- Implemented as a custom `Fluid` + `FluidType` per variant (NeoForge fluid API). Source block and flowing block per variant.
- Flow behavior matches lava (slower than water, 4-block overworld flow distance).
- Spawn behavior: each source block has a random-tick handler. On each random tick (subject to vanilla random tick speed), with low probability, attempts a spawn:
  - Check the position above is air or replaceable AND no entity is blocking spawn.
  - Spawn a `ResourceSlime` (or vanilla slime / magma cube for the vanilla/magma milk variants) at size 1 of the matching variant.
  - Decrement the source block's depletion counter (stored in block state via integer property `spawns_remaining`, or in block entity if more granular range is needed). When counter hits zero, replace the source block with air.
- Default spawn interval is implemented as: average ~20s wall-clock between spawn ticks per source block, ± uniform jitter [-10s, +10s]. Random tick speed tuning derives from this target.
- Default depletion: 16 spawns per source block before drying up. Configurable (mod config); can be disabled entirely (effectively ∞ counter).

### Variant explosion concerns

Registering one fluid per variant creates many registrations. Bounded by the number of `SlimeVariant` entries — base + Mekanism + Create + Thermal + Mythic Metals = ~30-50 fluids. This is manageable but worth designing for: fluid type generators run at registration time, reading the variant registry. Cross-mod variants conditionally register their fluid alongside.

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

`data/<namespace>/slime_variant/<name>.json`:

```json
{
  "neoforge:conditions": [
    { "type": "neoforge:mod_loaded", "modid": "mekanism" }
  ],
  "display_name": { "translate": "entity.productivefrogs.osmium_slime" },
  "category": "productivefrogs:metallic",
  "color_rgb": [127, 178, 197],
  "texture": "productivefrogs:textures/entity/slime/osmium.png",
  "loot_table": "productivefrogs:entities/slime/osmium",
  "spawn": {
    "biomes": "#minecraft:is_overworld",
    "weight": 5,
    "min_count": 1,
    "max_count": 2,
    "y_min": 0,
    "y_max": 64
  }
}
```

The `neoforge:conditions` block ensures the variant is only loaded when Mekanism is present. Without it, the slime simply doesn't exist in the registry — no errors, no broken references.

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

v1.0 deleted the `productivefrogs:primer/<category>` tag system. Variant → primer resolution now goes through the `SlimeVariant` datapack registry directly via `SlimeVariant.findByPrimerItem(registry, itemId)`. The variant JSON's `primer_item` field is the exact 1:1 match required for both slime infusion and Frog Egg priming (see `species_as_category_redesign.md` §Slime infusion).

- **"Can this primer prime a Frog Egg block?"** `SlimeVariant.findByPrimerItem(registry, held.itemId)` — if a variant matches, its `category()` selects the Primed Frog Egg block.
- **"Will this frog eat this slime?"** `frog.getCategory() == slime.getCategory()` — direct enum equality. Both sides carry a `Category` reference.

### Cross-mod variant additions

Cross-mod variants ship as JSON `SlimeVariant` entries with `neoforge:conditions → mod_loaded` wrappers. Example for a Mekanism osmium variant (`data/productivefrogs/productivefrogs/slime_variant/osmium.json`):

```json
{
  "neoforge:conditions": [
    { "type": "neoforge:mod_loaded", "modid": "mekanism" }
  ],
  "category": "cave",
  "primer_item": "mekanism:ingot_osmium",
  "primary_color": 8027317,
  "secondary_color": 5526612
}
```

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
  - NeoForge data registries for `SlimeVariant` and `slime_split_pool`
  - Custom `Fluid` + `FluidType` for Slime Milk variants
- Target audience: ATM10 / NeoForge ecosystem players. Fabric audience is explicitly out of scope.

## Testing Strategy (sketch — to be expanded)

- **Unit tests** for codec round-trips on SlimeVariant JSON.
- **Game-test framework** scenarios for: priming an egg, hatching, frog eating a slime, drop appearing.
- **Manual integration smoke test** with Mekanism + Create installed: place Frog Egg, prime with osmium, hatch, feed bronze slime, verify drop.
