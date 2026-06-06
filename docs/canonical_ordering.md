# Canonical Species Ordering

> **Status:** convention. Defines the one canonical order of the six species across Productive Frogs, so that object IDs, the `Category` enum, registration order, creative-tab layout, and JEI / recipe-book display all present in the intended **player-progression** sequence.

## The order

Productive Frogs' canonical species order is the progression order of the flagship pack it is built around (**Sky Frogs**) - also a sensible general difficulty/theme ramp:

| # | Species | Theme |
|---|---------|-------|
| 1 | **CAVE** | Ores & metals - iron, copper, gold, coal, lapis, redstone, ... |
| 2 | **GEODE** | Gems & crystals - emerald, diamond, amethyst, tuff, calcite, certus quartz, fluix, ... |
| 3 | **BOG** | Organic & swamp - clay, mud, moss, dirt, mycelium, lily pad, leather, pink slime, ... |
| 4 | **TIDE** | Aquatic - sponge, prismarine, sea pickle, nautilus shell, ink sac, ... |
| 5 | **INFERNAL** | Nether - blaze, nether quartz, soul sand, netherrack, glowstone, obsidian, netherite scrap |
| 6 | **VOID** | End & endgame - ender pearl, chorus fruit, shulker shell, echo shard, sculk, end stone, ... |

Each species is a gated tier; players complete one to unlock the next. Presenting the mod's objects in this order makes creative tabs, JEI, and the recipe book read in journey order.

> **Done (v1.2.x):** `data.Category` is declared in this canonical order. It was previously `BOG, CAVE, GEODE, TIDE, INFERNAL, VOID` (roughly alphabetical). The reorder was confirmed save-safe (persistence is by name; `ordinal()` is transient network-sync only).

## Where the order must be baked in

1. **`data/Category.java` enum** - declare the constants in canonical order: `CAVE, GEODE, BOG, TIDE, INFERNAL, VOID`.
   - **Save-safety:** `Category` serializes via `StringRepresentable` (by name - `"cave"`, `"bog"`, ...), so reordering the constants does **not** change saved worlds or datapack data. Before reordering, audit for any dependence on `Category.ordinal()`, `Category.values()[i]`, or `EnumMap`/`EnumSet` iteration order, and convert those to explicit ordering if present.
2. **Per-species block/item registration** - the `DeferredRegister` insertion order for the per-species objects (primed frog-egg blocks, frog-egg bottle variants, slime spawn eggs, resource frog/slime presentation) should follow canonical order. Insertion order drives the default creative-tab order, so this is where most of the user-visible ordering comes from.
3. **Creative tab(s)** - group entries by species in canonical order; within a species, order by resource/variant.
4. **Slime-variant grouping** - whenever variants are grouped/iterated by category (JEI category pages, generated docs, tab sections), iterate categories in canonical order.
5. **Docs** - per-species tables/lists in `docs/` use this order.

## Within-species (variant) ordering

Within a species, order resource variants vanilla-first in a stable sub-order, then conditional/modded variants. (Exact sub-order convention is TBD - pick one and keep it consistent across the enum, tabs, and docs.)

## Cross-references

- The driving pack: the **Sky Frogs** modpack (its `docs/progression.md` defines the gated journey this order serves).
- Species model: [`species_as_category_redesign.md`](./species_as_category_redesign.md). The historical pre-v1.0 abstract-category tier ladder is in [`categories_and_tiers.md`](./categories_and_tiers.md) (superseded).
