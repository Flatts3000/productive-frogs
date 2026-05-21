# Productive Bees Architecture Analysis

Reference analysis of [JDKDigital/productive-bees](https://github.com/JDKDigital/productive-bees) (`dev-1.21.0`), the mod that directly inspired Productive Frogs. Clone of their source lives locally at `F:\minecraft-repos\productive-bees-reference\` for cross-referencing during V1/V2 work on the slime side.

This is a survey, not a copy-paste catalog — every section ends with **how it maps to our design** and whether we should mirror, diverge, or skip.

## 1. Bee type structure — data-driven from one entity class

**Their approach.** One `ConfigurableBee` Java class handles ~50+ bee variants. Type is stored on the entity as a `SynchedEntityData<String>` holding a full `ResourceLocation`:

```java
public static final EntityDataAccessor<String> TYPE =
    SynchedEntityData.defineId(ConfigurableBee.class, EntityDataSerializers.STRING);
```

A `BeeReloadListener` (subclass of `SimpleJsonResourceReloadListener`) scans `data/<ns>/productivebees/**.json` on every datapack reload and produces a `Map<ResourceLocation, CompoundTag> BEE_DATA`. Every entity getter does a map lookup by `TYPE`. Solitary bees with divergent behavior (`MasonBee`, `BumbleBee`) get their own Java class; the configurable path covers everything else.

**Example bee JSON** (`data/productivebees/productivebees/coal.json`):
```json
{
  "primaryColor": "#222525",
  "secondaryColor": "#804f40",
  "particleColor": "#222525",
  "flowerTag": "c:storage_blocks/coal",
  "size": 0.5
}
```

The renderer keys textures from the JSON's optional `renderer` field (~10 base models — `default`, `default_crystal`, `default_shell`, etc.) and tints them via `primaryColor`/`secondaryColor`/`tertiaryColor`. Most bees are recolors of the same base PNGs; only flagship bees get custom textures.

**Maps to us.** We already use this pattern with `Category` ordinal instead of a `ResourceLocation` string. Cleaner: enum prevents the "self-destruct unconfigured entity after 5 ticks" band-aid they need at `ConfigurableBee.tick():139`. When we ship the per-resource `SlimeVariant` data registry (iron_slime vs copper_slime within METALLIC), follow their `SimpleJsonResourceReloadListener` shape — but use a `Codec`/`MapCodec` record from day one (see §5).

## 2. Comb structure — single item, data-component-keyed

**Their approach.** One `Honeycomb` item registered as `CONFIGURABLE_HONEYCOMB`. Bee type stored on the stack via a custom data component:

```java
public static final Supplier<DataComponentType<ResourceLocation>> BEE_TYPE =
    ProductiveBees.DATA_COMPONENTS.register("bee_type", () ->
        DataComponentType.<ResourceLocation>builder()
            .persistent(ResourceLocation.CODEC)
            .networkSynchronized(ResourceLocation.STREAM_CODEC).build());
```

`Honeycomb.getColor(stack, tintIndex)` and `Honeycomb.getName(stack)` pull the type off the stack and re-resolve the bee NBT through `BeeReloadListener.INSTANCE.getData(type)`. Same pattern for the block form (`CONFIGURABLE_COMB_BLOCK` + `CombBlockItem`).

Legacy hand-written `HONEYCOMB_GHOSTLY` / `HONEYCOMB_MILKY` / `HONEYCOMB_POWDERY` items still exist — kept for backwards compat, not the active pattern.

**Maps to us.** Today we ship 6 hand-registered `ResourceFroglight` blocks (one per Category) — that's the right call for the Category-keyed world. When per-resource variants land, add **one `configurable_froglight` item** with a `slime_variant` data component alongside the 6 category blocks. Don't refactor the 6 away; they're the broad-strokes look (METALLIC vs INFERNAL), the configurable one is the fine-grained per-resource variant.

Our existing `productivefrogs:contained_category` data component on `FrogEggItem` already follows this exact pattern.

## 3. "Bee produces comb" — custom recipe type, not loot

**Their approach.** Subclassed beehive (`AdvancedBeehiveBlockEntity`) overrides `beeReleasePostAction`. When a bee delivers honey, it calls `BeeHelper.getBeeProduce(level, bee, hasCombBlockUpgrade, modifier)`, which:

1. Reads the bee's type via `((ConfigurableBee) bee).getBeeType().toString()`
2. Looks up a matching `AdvancedBeehiveRecipe` (custom recipe type, JSONs at `data/<ns>/recipe/bee_produce/<bee>_bee.json`)
3. Rolls the chanced outputs
4. Critically: if the output item is `CONFIGURABLE_HONEYCOMB`, `getRecipeOutputs()` calls `BeeCreator.setType(beeType, stack)` to stamp the `bee_type` data component before returning

Recipe JSON:
```json
{
  "type": "productivebees:advanced_beehive",
  "ingredient": "productivebees:bacon",
  "results": [{ "item": { "type": "productivebees:component",
    "components": { "productivebees:bee_type": "productivebees:bacon" },
    "items": "productivebees:configurable_honeycomb" } }]
}
```

Vanilla beehives still drop vanilla honeycomb — Productive Bees produce only fires inside their own hive block.

**Maps to us.** Our `FrogTongueDropHandler` is Java-only — fine for "one Froglight per Category" since the mapping is small and fixed. If we want modpack authors to add their own slime-variant → Froglight-variant mappings (when per-variant Froglights land), a custom recipe type is the right abstraction. **Defer until the use case actually appears** — adding a recipe type now without a real consumer is over-engineering.

## 4. Cross-mod compat — JSON-only, modid subdirectories

**Their approach.** Each modded variant lives in `data/<ns>/productivebees/<modid>/<resource>.json` with a single `mod_loaded` condition:

```json
"conditions": [{ "modid": "ae2", "type": "neoforge:mod_loaded" }]
```

`BeeReloadListener.apply()` decodes via `ICondition.LIST_CODEC` and silently skips disabled entries. Same pattern for the produce recipes. **Zero Java compat code for bees themselves** — only ancillary integrations (JEI plugin, Patchouli) live in `compat/`. 28+ mods supported.

**Maps to us.** Already aligned with `docs/architecture.md`. Adopt this exact subdirectory layout when we ship modded slime variants.

## 5. Things they did poorly — avoid in our work

### 5a. Hand-rolled JSON→CompoundTag conversion

`BeeCreator.java` literally has `// MapCodec CODEC = ...` commented out at line 24 — they meant to use a codec, gave up, now every getter re-parses the CompoundTag (`ConfigurableBee.getNBTData()` is called on every getter — fireproof, withered, blinding, etc.). **Use a `Codec`/`MapCodec` record from day one for `SlimeVariant`.** Cache the decoded record on the entity, not the raw CompoundTag.

### 5b. Self-destruct unconfigured entity

`ConfigurableBee.tick():139` kills any entity whose `TYPE` is still empty after 5 ticks ("kill unconfigured bees"). Band-aid for a design where the constructor can't enforce its own invariant. Our `Category` ordinal prevents the equivalent — keep the enum, don't switch to a `ResourceLocation` string.

### 5c. Hardcoded special-case types

`BeeHelper.getBeeProduce()` lines 342–380 have `else if (type.equals("lumber_bee"))` / `quarry_bee` / `dye_bee` / `wanna` chains baked into Java. The "data-driven" claim breaks down for any behavior that isn't "produce a comb of color X." **Decide upfront what's truly data-driven** (cosmetics, drops, breed item) **vs always-Java** (frog-eats-slime → Froglight-drops should stay Java even after variants land). Resist the urge to make everything pluggable.

### 5d. Soft-dep class-swap at registry time

`ModEntities.java:62` picks `GeckoBee::new` vs `ConfigurableBee::new` based on whether GeckoLib is loaded. Result: a saved world built with GeckoLib installed **cannot load** without it. Don't load-bear our entity class on a soft dep. If we want optional GeckoLib-driven animations, gate them at render time, not at entity construction.

### 5e. Easter-egg names inside core init

`ConfigurableBee.setDefaultAttributes()` lines 295–345 have 50 lines of `if type.equals("productivebees:ghostly")` for cosmetic easter eggs. Cute, but living inside a generic init method makes the core logic harder to follow. Move easter eggs to a separate decorator class if we adopt this pattern.

## 6. Key file paths (for cross-referencing later)

```
F:\minecraft-repos\productive-bees-reference\src\main\java\cy\jdkdigital\productivebees\
├── common\entity\bee\ConfigurableBee.java         — single-entity + JSON-driven
├── common\item\Honeycomb.java                     — single-item + data-component
├── common\recipe\AdvancedBeehiveRecipe.java       — recipe-driven production
├── setup\BeeReloadListener.java                   — datapack registry pattern
├── util\BeeCreator.java                           — JSON parser (the anti-pattern)
├── util\BeeHelper.java                            — getBeeProduce (line 305)
├── init\ModDataComponents.java                    — bee_type component shape
└── client\render\entity\ProductiveBeeRenderer.java — tint-by-JSON renderer

F:\minecraft-repos\productive-bees-reference\pb_datapack\data\productivebees\
├── productivebees\example.json                    — annotated bee JSON template
├── productivebees\coal.json                       — simple bee example
├── productivebees\ae2\fluix.json                  — mod_loaded compat example
└── recipe\bee_produce\bacon_bee.json              — produce recipe example
```

## 7. 1.21.x API renames to translate

Their `dev-1.21.0` branch uses old API names:
- `noCollission()` (double-l, French spelling) → `noCollision()` in 1.21.11. 4 hits in `ModBlocks.java`.
- `ResourceLocation.fromNamespaceAndPath(ns, path)` is still around in 1.21.11 but we use `Identifier.fromNamespaceAndPath(...)` (the Bedrock-parity rename).

See `~/.claude/projects/D--minecraft-repos/memory/project_mc1211_api_renames.md` for the full list of renames we've hit.

## Net takeaway

Our V1 Category-keyed approach is the right shape — Productive Bees' architecture validates "one entity class, type as data, JSON for cosmetics/variants." When per-resource variants land:

1. Ship a `SlimeVariant` data registry as a `SimpleJsonResourceReloadListener` — but with a `MapCodec` record, not hand-rolled CompoundTag conversion
2. Add ONE `configurable_froglight` item with a `slime_variant` data component — keep the 6 Category Froglights alongside as the broad look
3. Compat JSONs go in `data/productivefrogs/<modid>/<resource>.json` with `mod_loaded` conditions
4. Keep the frog-eats-slime → Froglight logic in Java (don't make it pluggable until a real modpack author asks)

See [`backlog.md`](./backlog.md) for the concrete items derived from this analysis.
