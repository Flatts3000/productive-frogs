# V1.1 Scope — Vanilla Resource Coverage

## Status

**Implemented, pending release.** v1.0 (2026-05-24) shipped 12 Resource Slime variants under the six PF species (Bog, Cave, Geode, Tide, Infernal, Void). v1.1 adds **22 new variants** and removes the v1.0 `magma_cream` variant (**33 total**) on branch `feat/v1.1-resource-variants`; the version bump + CurseForge publish have not happened yet. Authoritative species-as-category model lives in [species_as_category_redesign.md](./species_as_category_redesign.md).

## Theme

v1.1's single thrust: **complete vanilla resource coverage across the six species** by adding every vanilla item that fits cleanly into one of them. No new mechanics, no new species, no schema changes — pure JSON additions flowing through the data-driven variant architecture.

Adding a variant is mostly data: a `slime_variant` JSON (with an optional `inner_block` block id rendered inside the slime, v1.0.1+) + a smelting recipe + a lang entry. One one-line Java edit remains — a Slime Milk `VARIANTS` entry in `PFFluidTypes` — because fluid registration runs at mod-init, before datapack registries load. The spawn egg is a single component-driven item enumerated from the registry (CR-9), so it needs no Java edit. See the per-variant checklist below.

## Shipped scope — 22 new variants

After v1.1: **33 total variants** (11 v1.0 + 22 v1.1). Includes 4 mob-drop variants that were previously deferred to V1.2 under the old category model — under the species model they fit cleanly under Bog Slime (the swamp/generic species) with no new species needed. `prismarine_crystals` (Tier B) was promoted into scope to ship alongside `prismarine`. Two variants were dropped as redundant: `slime_ball` (a slime made of slimeballs) was cut from this scope, and the v1.0 `magma_cream` variant was removed (see [CHANGELOG](../CHANGELOG.md)).

### Bog Slime — swamps + mob drops (+7)

| Variant | Primer item | Smelt result | Texture source |
|---|---|---|---|
| `bone` | `minecraft:bone` | `minecraft:bone` | skeleton drop / sand-fossil structure |
| `gunpowder` | `minecraft:gunpowder` | `minecraft:gunpowder` | composite (no native block) |
| `clay_ball` | `minecraft:clay_ball` | `minecraft:brick` (vanilla chain) | `block/clay.png` |
| `rotten_flesh` | `minecraft:rotten_flesh` | `minecraft:rotten_flesh` | composite |
| `string` | `minecraft:string` | `minecraft:string` | composite |
| `leather` | `minecraft:leather` | `minecraft:leather` | composite |
| `feather` | `minecraft:feather` | `minecraft:feather` | composite |

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

### Tide Slime — oceans (+2)

| Variant | Primer item | Smelt result | Texture source |
|---|---|---|---|
| `ink_sac` | `minecraft:ink_sac` | `minecraft:ink_sac` | composite (squids in oceans + rivers) |
| `prismarine_crystals` | `minecraft:prismarine_crystals` | `minecraft:prismarine_crystals` | `block/sea_lantern.png` (crafts sea lantern) |

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
| Bog | 0 | 7 | 7 |
| Cave | 7 | 3 | 10 |
| Geode | 1 | 1 | 2 |
| Tide | 2 | 2 | 4 |
| Infernal | 0 | 7 | 7 |
| Void | 1 | 2 | 3 |
| **Total** | **11** | **22** | **33** |

(`magma_cream` was the lone v1.0 Infernal variant; removed in v1.1 as redundant, so Infernal v1.0 drops to 0.)

Geode stays the smallest pool. v2+ may add new variants but the species roster stays at six.

## Per-variant file checklist

Each variant needs JSON plus one one-line Java edit:

1. `data/productivefrogs/productivefrogs/slime_variant/<name>.json` — `primer_item`, `category`, `primary_color`, `secondary_color`, optional `weight`, optional `inner_block` (a vanilla block id; `scripts/generate_resource_slime_textures.py` bakes a downscaled copy of this block's texture onto the slime's inner-cube faces — the visible interior. The v1.0.1 live block-model render layer was removed; see [CHANGELOG](../CHANGELOG.md)).
2. Smelting recipe at `data/productivefrogs/recipe/configurable_froglight_<name>_to_<result>.json` — match the existing variant-recipe shape (`neoforge:components` ingredient on the `slime_variant` data component). The `scripts/generate_v1_1_variants.ps1` data-table generator emits items 1-2 plus the milk blockstate + bucket model.
3. Lang entry for the slime / bucket display name + the spawn-egg name key `item.productivefrogs.resource_slime_spawn_egg.<variant>`.
4. **Java (one one-line edit):** a `VARIANTS` entry in `PFFluidTypes` (registers its Slime Milk fluid/block/bucket). Fluids must register at mod-init, before datapack registries load, so this can't be datapack-driven. The spawn egg, Slime Bucket, and Configurable Froglight are all single component-driven items whose per-variant stacks the creative tab + JEI enumerate from the `slime_variant` registry, so they need no Java edit (CR-9).

Existing GameTests (`SlimeVariantTest`, the datapack-load spot-check, `PFRegistryTest#variantSlimeSpawnEggCarriesSlimeVariantComponent`) auto-extend coverage as soon as the JSON + `VARIANTS` entry exist.

## Tier B candidates — open

| Variant | Species | Default if undecided |
|---|---|---|
| `prismarine_crystals` | Tide | **Shipped in v1.1** (promoted into scope; inner_block `sea_lantern`) |
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

- [x] 22 variant JSONs (each with a thematic `inner_block`) + 22 smelting recipes + 22 `VARIANTS` entries shipped.
- [x] `./gradlew build` green; `./gradlew runGameTestServer` green (all 49 required GameTests pass).
- [x] Creative tab shows the new variant slime spawn eggs, variant-stamped configurable_froglight stacks, and variant Slime Buckets (enumerated from the registry, no per-variant Java).
- [x] All 33 variants smelt to their respective resources via vanilla furnace (with the two non-1:1 chains documented: clay_ball -> brick, chorus_fruit -> popped_chorus_fruit).
- [x] Tier B `prismarine_crystals` promoted into scope; remaining Tier B items deferred per the defaults above.
- [x] `docs/versioning.md` v1.1 section reflects shipped state.
- [x] `docs/backlog.md` v1.1 checklist reflects shipped state.

### inner_block decisions for composite variants

Mob/composite drops with no canonical full-cube block still ship a thematic
best-fit cube (decision: every v1.1 variant renders an interior block): gunpowder
-> `tnt`, rotten_flesh -> `mud`, string -> `cobweb`, leather -> `brown_terracotta`,
feather -> `white_wool`, glow_ink_sac -> `verdant_froglight`, ink_sac ->
`black_concrete`, blaze -> `shroomlight`, shulker_shell -> `purpur_pillar`. The
rest use their obvious block (bone -> `bone_block`, obsidian -> `obsidian`, etc.).
