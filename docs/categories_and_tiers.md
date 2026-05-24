# Categories and Tiers

> **⚠️ SUPERSEDED by v1.0.** This doc describes the original pre-v1.0 abstract-category design (Metallic / Mineral / Gem / Aquatic / Arcane). The shipping v1.0 model is **species-as-category**: BOG / CAVE / GEODE / TIDE / INFERNAL / VOID, with the `productivefrogs:primer/<category>` tag system deleted in favour of exact `SlimeVariant.primer_item` field matching. See [species_as_category_redesign.md](./species_as_category_redesign.md) for the authoritative v1.0 design.
>
> Old → new name mapping: METALLIC→BOG (semantically — Bog became the overworld/swamp/mob-drop catch-all, while the metallurgy variants like iron moved to Cave), MINERAL→CAVE, GEM→GEODE, AQUATIC→TIDE, ARCANE→VOID, INFERNAL unchanged. The tier ladder below is reorganized in `species_as_category_redesign.md` and `open_questions.md` §1.

## The Six Categories (HISTORICAL — pre-v1.0)

| Category | Frog name (working) | Theme |
|---|---|---|
| Metallic | Coppergreen Frog | Smelted metals: iron, copper, gold, osmium, bronze, tin, lead, etc. |
| Mineral | Stoneback Frog | Powders and dust ores: redstone, lapis, coal |
| Gem | Crystalline Frog | Faceted/crystal materials: diamond, emerald, quartz, amethyst, fluorite, ruby, sapphire |
| Aquatic | Tidefoot Frog | Ocean materials: sponge, prismarine, coral, kelp, ink, nautilus |
| Infernal | Ashbelly Frog | Nether and fire materials: magma cream, blaze, ghast, wither |
| Arcane | Voidshade Frog | End and aether materials: ender pearl, echo shard, end stone |

Frog names are placeholders — open to renaming.

## Tier Ladder (locked)

Order driven by **vanilla equipment-cost progression** — what gear the player needs to reach each primer:

| Tier | Category | Primer (canonical gateway) | Equipment gate |
|---|---|---|---|
| T1 | Metallic | Iron Ingot | Stone pickaxe → iron ore |
| T2 | Mineral | Redstone Dust | Iron pickaxe → redstone/lapis/coal |
| T3 | Gem | Diamond | Iron pickaxe → diamond ore |
| T4 | Aquatic | Prismarine Shard | Iron gear + water breathing + ocean monument |
| T5 | Infernal | Magma Cream | Diamond pickaxe → obsidian → Nether |
| T6 | Arcane | Ender Pearl | Enderman kills + (effective) End access |

Note: T1–T3 are technically reachable on the same equipment tier (iron pickaxe), so a player can pursue them in parallel. T4 (Aquatic) is a soft gate behind ocean monument access (water breathing potion + decent gear). T5 (Infernal) is a hard gate behind obsidian → Nether. T6 (Arcane) is a soft gate via overworld enderman drops, but real Arcane slimes come from the End, so it functions as endgame.

## Primer System

Each category has a **primer tag** — a list of items that can be used to prime a base Frog Egg into that category. Any item in the tag works; choice of material is flavor only (and inventory management). All produce the same primed egg item.

```
productivefrogs:primer/metallic
productivefrogs:primer/mineral
productivefrogs:primer/gem
productivefrogs:primer/aquatic
productivefrogs:primer/infernal
productivefrogs:primer/arcane
```

Tags are populated by:
- **Base entries** (always loaded) — vanilla items only.
- **Conditional entries** (loaded only if the named mod is present) — modded items added via `neoforge:conditions → mod_loaded`.

### Primer Tag Contents — Base + Common Mod Extensions

| Tag | Vanilla / always | + Mekanism | + Create | + Thermal | + Mythic Metals |
|---|---|---|---|---|---|
| `primer/metallic` | iron ingot, copper ingot, gold ingot | osmium, tin, lead, uranium | zinc, brass, bronze | tin, lead, silver, nickel | many |
| `primer/mineral` | redstone dust, lapis lazuli, coal | — | — | — | — |
| `primer/gem` | diamond, emerald, quartz, amethyst shard | fluorite | — | — | ruby, sapphire, topaz |
| `primer/aquatic` | prismarine shard, prismarine crystals, sponge, nautilus shell | — | — | — | — |
| `primer/infernal` | magma cream, blaze powder, ghast tear, wither rose | — | — | — | — |
| `primer/arcane` | ender pearl, echo shard, end stone | — | — | — | — |

### Disjointness Rule

**Every material must belong to exactly one primer tag.** Ambiguous materials (could fit multiple) must be assigned to one category at design time. Current assignments for the obvious clashes:

| Material | Category | Rationale |
|---|---|---|
| Blaze powder | Infernal | Nether-sourced, fire theme dominant |
| Amethyst shard | Gem | Crystal physical form dominant |
| Magma cream | Infernal | Nether mob drop |
| Coal | Mineral | Powder/dust shape, not gem |
| Quartz (Nether) | Gem | Crystal form, not infernal |

## Slime Category Tags

Parallel tags on the slime side describe which slime species are in which category. Each slime variant is registered as a JSON entry in `data/productivefrogs/slime_variant/<name>.json` and is added to the matching tag.

```
productivefrogs:slime_category/metallic
productivefrogs:slime_category/mineral
productivefrogs:slime_category/gem
productivefrogs:slime_category/aquatic
productivefrogs:slime_category/infernal
productivefrogs:slime_category/arcane
```

The "is this slime tasty to this frog" check at runtime is:

```
frog.category_tag == slime.variant.category_tag
```

## Why Tags (and not Java enums)

- **Modpack authors can add new slime variants** without recompiling.
- **Cross-mod compat is one JSON file** per modded resource.
- **Categories can be rebalanced** by datapack overrides.
- **Forge/NeoForge tag system already does the heavy lifting** of resolving cross-namespace IDs.

See [architecture.md](./architecture.md) for the data-driven registration pattern.
