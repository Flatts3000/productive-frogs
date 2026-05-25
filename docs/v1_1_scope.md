# V1.1 Scope — Vanilla Resource Coverage

## Status

**Not yet shipped.** v1.0 (2026-05-24) shipped 12 Resource Slime variants under the six PF species (Bog, Cave, Geode, Tide, Infernal, Void). v1.1 is the next planned content release. Authoritative species-as-category model lives in [species_as_category_redesign.md](./species_as_category_redesign.md).

## Theme

v1.1's single thrust: **complete vanilla resource coverage across the six species** by adding every vanilla item that fits cleanly into one of them. No new mechanics, no new species, no schema changes — pure JSON additions flowing through the data-driven variant architecture.

Adding a variant is mostly data: a `slime_variant` JSON (with an optional `inner_block` block id rendered inside the slime, v1.0.1+) + a smelting recipe + a lang entry. One one-line Java edit remains — a Slime Milk `VARIANTS` entry in `PFFluidTypes` — because fluid registration runs at mod-init, before datapack registries load. The spawn egg is a single component-driven item enumerated from the registry (CR-9), so it needs no Java edit. See the per-variant checklist below.

## Locked scope — 22 new variants

After v1.1: **34 total variants** (12 v1.0 + 22 v1.1). Includes 5 mob-drop variants that were previously deferred to V1.2 under the old category model — under the species model they fit cleanly under Bog Slime (the swamp/generic species) with no new species needed.

### Bog Slime — swamps + mob drops (+8)

| Variant | Primer item | Smelt result | Texture source |
|---|---|---|---|
| `bone` | `minecraft:bone` | `minecraft:bone` | skeleton drop / sand-fossil structure |
| `gunpowder` | `minecraft:gunpowder` | `minecraft:gunpowder` | composite (no native block) |
| `clay_ball` | `minecraft:clay_ball` | `minecraft:brick` (vanilla chain) | `block/clay.png` |
| `rotten_flesh` | `minecraft:rotten_flesh` | `minecraft:rotten_flesh` | composite |
| `string` | `minecraft:string` | `minecraft:string` | composite |
| `leather` | `minecraft:leather` | `minecraft:leather` | composite |
| `feather` | `minecraft:feather` | `minecraft:feather` | composite |
| `slime_ball` | `minecraft:slime_ball` | `minecraft:slime_ball` | `block/slime_block.png` |

### Cave Slime — mining (+3)

| Variant | Primer item | Smelt result | Texture source |
|---|---|---|---|
| `glow_ink_sac` | `minecraft:glow_ink_sac` | `minecraft:glow_ink_sac` | composite (glow squids spawn in lush caves) |
| `obsidian` | `minecraft:obsidian` | `minecraft:obsidian` | `block/obsidian.png` |
| `echo_shard` | `minecraft:echo_shard` | `minecraft:echo_shard` | derived from `block/sculk.png` |

### Geode Slime — mountains + amethyst geodes (+1)

| Variant | Primer item | Smelt result | Texture source |
|---|---|---|---|
| `amethyst` | `minecraft:amethyst_shard` | `minecraft:amethyst_shard` | `block/amethyst_block.png` |

### Tide Slime — oceans (+1)

| Variant | Primer item | Smelt result | Texture source |
|---|---|---|---|
| `ink_sac` | `minecraft:ink_sac` | `minecraft:ink_sac` | composite (squids in oceans + rivers) |

### Infernal Slime — nether (+7)

| Variant | Primer item | Smelt result | Texture source |
|---|---|---|---|
| `netherite_scrap` | `minecraft:netherite_scrap` | `minecraft:netherite_scrap` | `block/netherite_block.png` |
| `glowstone_dust` | `minecraft:glowstone_dust` | `minecraft:glowstone_dust` | `block/glowstone.png` |
| `soul_sand` | `minecraft:soul_sand` | `minecraft:soul_sand` | `block/soul_sand.png` |
| `soul_soil` | `minecraft:soul_soil` | `minecraft:soul_soil` | `block/soul_soil.png` |
| `netherrack` | `minecraft:netherrack` | `minecraft:netherrack` | `block/netherrack.png` |
| `blaze` | `minecraft:blaze_powder` | `minecraft:blaze_powder` | composite (blaze rod orange-yellow) |
| `quartz` | `minecraft:quartz` | `minecraft:quartz` | `block/quartz_block.png` |

### Void Slime — end (+2)

| Variant | Primer item | Smelt result | Texture source |
|---|---|---|---|
| `chorus_fruit` | `minecraft:chorus_fruit` | `minecraft:popped_chorus_fruit` (vanilla chain) | `block/chorus_plant.png` |
| `shulker_shell` | `minecraft:shulker_shell` | `minecraft:shulker_shell` | derived from `block/purpur_block.png` |

## Per-variant count after v1.0 + v1.1

| Species | v1.0 | v1.1 | Total |
|---|---|---|---|
| Bog | 0 | 8 | 8 |
| Cave | 7 | 3 | 10 |
| Geode | 1 | 1 | 2 |
| Tide | 2 | 1 | 3 |
| Infernal | 1 | 7 | 8 |
| Void | 1 | 2 | 3 |
| **Total** | **12** | **22** | **34** |

Geode stays the smallest pool. v2+ may add new variants but the species roster stays at six.

## Per-variant file checklist

Each variant needs JSON plus one one-line Java edit:

1. `data/productivefrogs/productivefrogs/slime_variant/<name>.json` — `primer_item`, `category`, `primary_color`, `secondary_color`, optional `weight`, optional `inner_block` (a vanilla block id rendered inside the slime; v1.0.1+ replaced the old per-variant inner-cube texture with live block rendering).
2. Smelting recipe at `data/productivefrogs/recipe/smelting/configurable_froglight_<name>_smelting.json` — match the existing variant-recipe shape (`neoforge:components` ingredient on the `slime_variant` data component).
3. Lang entry for the slime / bucket display name + the spawn-egg name key `item.productivefrogs.resource_slime_spawn_egg.<variant>`.
4. **Java (one one-line edit):** a `VARIANTS` entry in `PFFluidTypes` (registers its Slime Milk fluid/block/bucket). Fluids must register at mod-init, before datapack registries load, so this can't be datapack-driven. The spawn egg, Slime Bucket, and Configurable Froglight are all single component-driven items whose per-variant stacks the creative tab + JEI enumerate from the `slime_variant` registry, so they need no Java edit (CR-9).

Existing GameTests (`SlimeVariantTest`, the datapack-load spot-check, `PFRegistryTest#variantSlimeSpawnEggCarriesSlimeVariantComponent`) auto-extend coverage as soon as the JSON + `VARIANTS` entry exist.

## Tier B candidates — open

| Variant | Species | Default if undecided |
|---|---|---|
| `prismarine_crystals` | Tide | Ship alongside `prismarine` (additive — same shape as iron/copper/gold being separate variants) |
| `nautilus_shell` | Tide | Defer (drowned drop only — production-loop framing weak) |
| `ghast_tear` | Infernal | Defer (single mob drop, rarity-break concern) |
| `wither_rose` | — | Drop (the `primer/<category>` tag system is deleted in v1.0; "primer-tag-only" is no longer a meaningful state) |
| `end_stone` | — | Drop (same as wither_rose) |

## Out of v1.1 scope

- **New species beyond the six** — V2/V3 (e.g. "Brew Slime / Alchemical" parked in [versioning.md](./versioning.md))
- **Cross-mod variant entries** (Mekanism osmium, Mythic Metals ruby, etc.) — own milestone, see [cross_mod_compat.md](./cross_mod_compat.md)
- **Re-theming existing v1.0 variants** (colours / `inner_block`) — only new variants get new data
- **Any V2 automation items** — auto-milker, terrarium, crusher block, FE compat stay deferred

## Implementation notes

- Adding a variant is mostly JSON (variant definition + smelting recipe + lang); only the Slime Milk fluid needs a one-line Java edit (the `VARIANTS` entry) — see the per-variant checklist above.
- `inner_block` is optional: set it to the variant's canonical vanilla block (e.g. `minecraft:iron_block`) to render that block inside the slime; omit it to leave the interior empty. The pre-v1.0.1 per-variant inner-cube texture pipeline (and its generator script) was removed in v1.0.1.
- Each variant's `primary_color` should be sampled from the variant's canonical vanilla block so the spawn-egg tint, outer-shell tint, and Froglight tint all read as the resource colour.
- Smelting recipe shape: copy `recipe/smelting/configurable_froglight_iron_smelting.json` as the template; change the variant name in the `neoforge:components` ingredient and the result item ID. For clay_ball → brick and chorus_fruit → popped_chorus_fruit, the result item differs from the primer.

## Definition of done

- 22 variant JSONs (each with an `inner_block`) + 22 smelting recipes + 22 `VARIANTS` entries shipped.
- `./gradlew build` green; `./gradlew runGameTestServer` green (existing GameTests auto-cover the new variants).
- Creative tab shows 22 new variant slime spawn eggs, 22 new variant-stamped configurable_froglight stacks, 22 new variant Slime Buckets.
- All 34 variants smelt to their respective resources via vanilla furnace (with the two non-1:1 chains documented: clay_ball → brick, chorus_fruit → popped_chorus_fruit).
- Tier B items either decided per the defaults above or have explicit alternate decisions documented here.
- `docs/versioning.md` v1.1 section reflects shipped state.
- `docs/backlog.md` v1.1 checklist reflects shipped state.
