# Species-as-Category Redesign

## Status

**Shipped 2026-05-24 in v1.0.0.** Implementation merged via PR #81 (renamed Category enum, added Bog + Infernal entities, rewrote SlimeInfusionHandler + EggPrimerHandler for species-lock, deleted `primer/<category>` tag files, deleted species Froglight blocks, added JEI Information pages). All Q1–Q4 decisions baked into shipping code. This doc is preserved as the authoritative v1.0 design spec — the Open Questions section at the bottom records the decisions that were taken; bracketed [RESOLVED] notes mark which questions were closed by the v1.0 ship.

Originally drafted 2026-05-23 after the MC 1.21.1 port playtest surfaced the parent-vs-frog naming mismatch. Originally scoped as V1.5 (between the V1.0.x port and V1.1 vanilla expansion); collapsed into the v1.0 ship after the design locked.

## The problem this fixes

Today the mod has three nested layers of slime-naming and one layer of frog-naming:

1. **Parent slime species** (Cave Slime, Geode Slime, Tide Slime, Void Slime, vanilla Slime, vanilla Magma Cube)
2. **Abstract category** (METALLIC, MINERAL, GEM, AQUATIC, INFERNAL, ARCANE) — an internal Java `enum`
3. **Resource variant** (Iron Slime, Copper Slime, ...) — datapack registry entry, scoped to a category
4. **Frog** (Metallic Frog, Mineral Frog, ...) — one per category

Players have to mentally walk this chain every time they encounter a slime: *"Cave Slime → that's MINERAL → infuse it with iron ore → I get an Iron Slime (still MINERAL) → eaten by Mineral Frog → drops Mineral Froglight."*

The parent species names share no theme with the category or the frog. Cave Slime produces Mineral Slimes; Tide Slime produces Aquatic Slimes; the abstract category labels feel arbitrary because they don't match anything visible in the world.

## Core insight: species IS the category

Collapse the abstraction. One species = one biome theme = one frog. The category enum stays under the hood as the join key (so the existing datapack registry pipeline keeps working) but **disappears from player-facing strings**.

After the redesign:

> "Cave Slimes spawn in caves. Infuse a Cave Slime with iron ore to get an Iron Slime (a Cave Slime variant). Cave Frogs eat Cave Slimes and drop Cave Froglights."

The chain reads itself. No `Mineral` jargon anywhere.

## The six species

| Theme | Species | Frog | Tadpole | Internal category |
|---|---|---|---|---|
| Swamp / generic | **Bog Slime** | Bog Frog | Bog Tadpole | `bog` |
| Caves | **Cave Slime** | Cave Frog | Cave Tadpole | `cave` |
| Mountains / geodes | **Geode Slime** | Geode Frog | Geode Tadpole | `geode` |
| Oceans | **Tide Slime** | Tide Frog | Tide Tadpole | `tide` |
| Nether | **Infernal Slime** | Infernal Frog | Infernal Tadpole | `infernal` |
| End | **Void Slime** | Void Frog | Void Tadpole | `void` |

User-locked names (2026-05-23): the four PF species **Bog, Cave, Geode, Tide, Void** stay, **Infernal** is the nether species (not "Ember" or "Magma" — explicit user pref).

Internal category renames:
- `METALLIC` → `BOG`
- `MINERAL` → `CAVE`
- `GEM` → `GEODE`
- `AQUATIC` → `TIDE`
- `ARCANE` → `VOID`
- `INFERNAL` stays `INFERNAL`

(Java enum constants in `data.Category`. Tag paths under `productivefrogs:primer/<category>/*` and JSON registry keys all flip with it.)

## Spawn rules (comprehensive)

Each species gets its own `neoforge:add_spawns` biome modifier. Light level and Y-level filtering happens via the spawn condition predicate in `PFModBusEvents.checkParentSlimeSpawnRules`.

### Bog Slime — swamp surface

| Property | Value | Why |
|---|---|---|
| Biomes | `minecraft:swamp`, `minecraft:mangrove_swamp` | Mirrors vanilla slime swamp affinity but as a distinct species |
| Y-level | 50 ≤ y ≤ 80 | Surface-level swamp; avoid overlap with Cave Slime spawn band |
| Light level | ≤ 7 (dark) | Mirrors vanilla slime swamp spawn rule |
| Weight | 8 | Common in target biomes; slightly higher than the cave/geode/tide weight because swamps are small |
| Count | 1–3 | Slimes split, so even 1 spawn → multiple slimes |
| Spawn heightmap | `MOTION_BLOCKING_NO_LEAVES` | Standard surface heightmap |

Coexistence with vanilla slime: vanilla slimes still spawn in swamps too. The infusion handler accepts both as conversion targets, so vanilla slime → primer → Bog-category Resource Slime still works. Bog Slime is the *PF parent species* that does NOT depend on vanilla mob being present.

### Cave Slime — deep caves

| Property | Value | Why |
|---|---|---|
| Biomes | `minecraft:dripstone_caves`, `minecraft:deep_dark`, `minecraft:lush_caves` | All three are explicitly cave biomes; deep_dark adds Ancient-City flavour |
| Y-level | -64 ≤ y ≤ 30 | Deep-cave band — overlaps copper/iron/redstone vanilla ore distribution |
| Light level | ≤ 7 (dark) | Caves are dark by default; same predicate as vanilla cave mobs |
| Weight | 5 | Current value; player encounter rate already calibrated |
| Count | 1–2 | Standard slime spawn count |
| Spawn heightmap | `MOTION_BLOCKING_NO_LEAVES` | Standard |

### Geode Slime — mountain peaks

| Property | Value | Why |
|---|---|---|
| Biomes | `minecraft:stony_peaks`, `minecraft:jagged_peaks`, `minecraft:frozen_peaks`, `minecraft:snowy_slopes` | All peak biomes; snowy_slopes added for emerald-tier vanilla ore overlap |
| Y-level | y ≥ 90 | Peak band only — keeps spawning visible above tree line / cloud line |
| Light level | ≤ 7 (dark) | Standard night spawn; peaks at night are reachable |
| Weight | 5 | Same as current |
| Count | 1–2 | Standard |
| Spawn heightmap | `MOTION_BLOCKING_NO_LEAVES` | Standard |

### Tide Slime — oceans

| Property | Value | Why |
|---|---|---|
| Biomes | `minecraft:deep_ocean`, `minecraft:deep_cold_ocean`, `minecraft:deep_lukewarm_ocean`, `minecraft:deep_frozen_ocean`, `minecraft:lukewarm_ocean`, `minecraft:warm_ocean` | Deep + warm; cold variants added for diving-helmet flavour |
| Y-level | 30 ≤ y ≤ 62 | Under sea level (sea level = 63 in vanilla) |
| Light level | any | Underwater light propagation is already low; no explicit predicate needed |
| Weight | 5 | Same as current |
| Count | 1–2 | Standard |
| Spawn heightmap | `OCEAN_FLOOR` | Top of solid blocks **ignoring water** — actual sea floor (current behaviour) |

### Infernal Slime — nether

| Property | Value | Why |
|---|---|---|
| Biomes | `minecraft:nether_wastes`, `minecraft:basalt_deltas`, `minecraft:soul_sand_valley` | Three main nether biomes; soul_sand_valley adds variety |
| Y-level | any | Standard for nether mobs |
| Light level | any | Nether mobs ignore light (matches vanilla magma cube) |
| Weight | 10 | Higher than overworld species because nether biomes are large and mob-dense |
| Count | 1–3 | Mirrors vanilla magma cube count |
| Spawn heightmap | `MOTION_BLOCKING_NO_LEAVES` | Standard (no real heightmap matters in nether) |

Coexistence with vanilla magma cube: same pattern as Bog Slime / vanilla slime — magma cubes still spawn vanilla-style, and infusion accepts them too. Infernal Slime is the new canonical PF parent.

### Void Slime — end

| Property | Value | Why |
|---|---|---|
| Biomes | `minecraft:small_end_islands`, `minecraft:end_midlands`, `minecraft:end_highlands` | Outer end islands; mainland end uses midlands/highlands |
| Y-level | y ≥ 40 | End islands are typically at 60-80 |
| Light level | any | End mobs ignore light (matches vanilla enderman) |
| Weight | 5 | Same as current; end is sparse so low weight keeps it interesting |
| Count | 1–2 | Standard |
| Spawn heightmap | `MOTION_BLOCKING_NO_LEAVES` | Standard |

## Variant → species remapping

Every existing Resource Slime variant moves from `category: <abstract>` to `category: <species>`. New variants (V1.1 scope) get assigned to a species at the same time. The principle: *the variant goes under the species whose biome you'd realistically find that resource in vanilla.*

### Already-shipped variants (12 from V1)

| Variant | Old category | New species | Why |
|---|---|---|---|
| iron | METALLIC | Cave | iron ore distribution is cave-band |
| copper | METALLIC | Cave | copper-y prefers dripstone caves |
| gold | METALLIC | Cave | overworld gold is cave-band; nether gold is bonus, doesn't move primary |
| redstone | MINERAL | Cave | deep cave ore |
| lapis | MINERAL | Cave | deep cave ore |
| coal | MINERAL | Cave | broadest cave ore |
| diamond | GEM | Cave | deep cave ore (could argue Geode, but vanilla diamond is mining-not-geological) |
| emerald | GEM | Geode | mountain-biome-only ore — perfect Geode fit |
| prismarine | AQUATIC | Tide | ocean monuments |
| sponge | AQUATIC | Tide | ocean monuments |
| magma_cream | INFERNAL | Infernal | unchanged |
| ender_pearl | ARCANE | Void | unchanged (rename only) |

Big mover: most of the old METALLIC + MINERAL + GEM variants collapse into **Cave**. That's because in vanilla you mine all of them from the cave-floor band. The metallurgy / ore-type abstraction wasn't load-bearing — players reach for these resources at the same Y band, with the same iron pickaxe.

### V1.1 vanilla coverage — full variant table

Under the species-as-category model, V1.1's scope expands to also absorb the 5 mob drops previously deferred to V1.2 (no new category is needed — they fit under Bog Slime per Decision 2026-05-23 in the Bog Slime variant pool section above). The original V1.1 doc (`v1_1_scope.md`) categorised 16 variants under the old enum names; this section is the authoritative re-categorisation under the 6 species.

**Total V1 + V1.1: ~34 variants** (12 V1 already shipped + 22 V1.1 additions, including the 6 V1.2-pull-forward items).

#### Bog Slime — swamps, mob drops, generic overworld (8 new V1.1 variants, 0 V1)

| Variant | Primer item | Smelt result | Source in vanilla |
|---|---|---|---|
| `bone` | `minecraft:bone` | `minecraft:bone` | skeletons, fossil structures |
| `gunpowder` | `minecraft:gunpowder` | `minecraft:gunpowder` | creepers (broad overworld) |
| `clay_ball` | `minecraft:clay_ball` | `minecraft:brick` (vanilla smelt chain) | clay blocks in swamps + shallow water |
| `rotten_flesh` | `minecraft:rotten_flesh` | `minecraft:rotten_flesh` | zombies (broad overworld) |
| `string` | `minecraft:string` | `minecraft:string` | spiders + cobwebs |
| `leather` | `minecraft:leather` | `minecraft:leather` | cows, horses |
| `feather` | `minecraft:feather` | `minecraft:feather` | chickens |
| `slime_ball` | `minecraft:slime_ball` | `minecraft:slime_ball` | self-reference — see Open Question on `slime_ball` |

Cross-validation: gunpowder and clay_ball were originally MINERAL in `v1_1_scope.md`. The new species design moves them to Bog because they're not mined — gunpowder is a mob drop, clay_ball spawns in swamps.

#### Cave Slime — mining (3 new V1.1 variants, 7 V1)

| Variant | Primer item | Smelt result | Source in vanilla |
|---|---|---|---|
| `glow_ink_sac` | `minecraft:glow_ink_sac` | `minecraft:glow_ink_sac` | glow squids spawn in lush caves + deep dark |
| `obsidian` | `minecraft:obsidian` | `minecraft:obsidian` | lava-meets-water — most often encountered at cave lava-lake level |
| `echo_shard` | `minecraft:echo_shard` | `minecraft:echo_shard` | Ancient City structures in deep dark biome |

Cross-validation: glow_ink_sac was originally AQUATIC, obsidian INFERNAL, echo_shard ARCANE in `v1_1_scope.md`. All three move to Cave because biome-source > form-source in the new model — deep dark is a Cave biome, lava lakes are predominantly cave-encountered, glow squids spawn in lush caves.

V1 carryovers (already shipped, Cave species): iron, copper, gold, redstone, lapis, coal, diamond.

#### Geode Slime — mountains + amethyst geodes (1 new V1.1 variant, 1 V1)

| Variant | Primer item | Smelt result | Source in vanilla |
|---|---|---|---|
| `amethyst` | `minecraft:amethyst_shard` | `minecraft:amethyst_shard` | amethyst geodes in mountain biomes |

V1 carryover (already shipped, Geode species): emerald.

#### Tide Slime — oceans (1 new V1.1 variant, 2 V1)

| Variant | Primer item | Smelt result | Source in vanilla |
|---|---|---|---|
| `ink_sac` | `minecraft:ink_sac` | `minecraft:ink_sac` | squids in oceans (and rivers) |

V1 carryovers (already shipped, Tide species): prismarine, sponge.

#### Infernal Slime — nether (7 new V1.1 variants, 1 V1)

| Variant | Primer item | Smelt result | Source in vanilla |
|---|---|---|---|
| `netherite_scrap` | `minecraft:netherite_scrap` | `minecraft:netherite_scrap` | ancient debris in nether |
| `glowstone_dust` | `minecraft:glowstone_dust` | `minecraft:glowstone_dust` | glowstone in nether ceiling |
| `soul_sand` | `minecraft:soul_sand` | `minecraft:soul_sand` | soul sand valley |
| `soul_soil` | `minecraft:soul_soil` | `minecraft:soul_soil` | soul sand valley |
| `netherrack` | `minecraft:netherrack` | `minecraft:netherrack` | nether terrain bulk |
| `blaze` | `minecraft:blaze_powder` | `minecraft:blaze_powder` | blaze drops in nether fortresses |
| `quartz` | `minecraft:quartz` | `minecraft:quartz` | nether quartz ore |

Cross-validation: netherite_scrap was originally METALLIC, quartz was originally GEM in `v1_1_scope.md`. Both move to Infernal because their only vanilla source is the nether. The "form vs source" tradeoff resolved in favour of source for the species-as-category redesign.

V1 carryover (already shipped, Infernal species): magma_cream.

#### Void Slime — end (2 new V1.1 variants, 1 V1)

| Variant | Primer item | Smelt result | Source in vanilla |
|---|---|---|---|
| `chorus_fruit` | `minecraft:chorus_fruit` | `minecraft:popped_chorus_fruit` (vanilla smelt chain) | end outer islands |
| `shulker_shell` | `minecraft:shulker_shell` | `minecraft:shulker_shell` | shulkers in end cities |

V1 carryover (already shipped, Void species): ender_pearl.

#### Tier B items — still open

The V1.1 doc flagged 5 items as Tier B (decided per default or deferred). Re-evaluating under the species model:

| Variant | Default (V1.1 doc) | Species under redesign | Notes |
|---|---|---|---|
| `prismarine_crystals` | Ship alongside `prismarine` | Tide | Additive; same shape as iron/copper/gold being separate variants |
| `nautilus_shell` | Defer | Tide if shipped | Production-loop framing weak — drowned drop only |
| `ghast_tear` | Defer | Infernal if shipped | Single-mob-drop rarity break |
| `wither_rose` | Skip variant (primer-tag-only) | **N/A** — primer tags are deleted in V1.5, so wither_rose either ships as a full variant or is dropped entirely. Recommend: drop. | |
| `end_stone` | Skip variant (primer-tag-only) | **N/A** — same as wither_rose. Recommend: drop. | |

The species redesign forces the wither_rose / end_stone decision earlier — without the primer-tag fallback, "primer-tag-only" is no longer a meaningful state. Both either become full variants or get dropped from scope.

#### Per-species count after V1 + V1.1

| Species | V1 | V1.1 | Total |
|---|---|---|---|
| Bog | 0 | 8 | 8 |
| Cave | 7 | 3 | 10 |
| Geode | 1 | 1 | 2 |
| Tide | 2 | 1 | 3 |
| Infernal | 1 | 7 | 8 |
| Void | 1 | 2 | 3 |
| **Total** | **12** | **22** | **34** |

Geode is the smallest pool by a wide margin (2 variants). This is a known split-discovery edge case noted in Open Question #6 — when the pool is size-1 or size-2, the variety of "split discovery converts to a Cave-Iron Slime" loses meaning. Likely fine because Geode Slimes spawn in mountain peaks (a sparse player-time biome anyway), but worth monitoring in playtest.

### Cross-mod variants (V1.1 scope, gated by `mod_loaded`)

These move with the same per-resource logic. Examples:

- Mekanism tin / osmium / lead / uranium → **Cave** (all mined ores)
- Create zinc / brass → **Cave** (zinc is a mined ore in Create)
- Thermal silver / nickel → **Cave**
- Mythic Metals adamantite / aquarium / starrite / etc. → **Cave** for ores; one-off thematic placement for the exotics

## Naming changes (lang)

Every player-facing string with a category label flips. Approximate touch count: ~50 lang keys.

Pattern (showing one representative key per family):

| Old key value | New key value |
|---|---|
| `Bottle of Metallic Frog Eggs` | `Bottle of Bog Frog Eggs` |
| `Bucket of Mineral Tadpole` | `Bucket of Cave Tadpole` |
| `Bucket of Gem Slime` | `Bucket of Geode Slime` |
| `Gem Frog Spawn Egg` | `Geode Frog Spawn Egg` |
| `Aquatic Tadpole` (entity) | `Tide Tadpole` (entity) |
| `Arcane Frog` (entity) | `Void Frog` (entity) |
| `Metallic Frog` (entity) | `Bog Frog` (entity) |
| `Iron Slime` (entity) | unchanged — variants keep their resource-name |
| `Iron Froglight` (block) | unchanged — variants keep their resource-name |

The big rule: **the species name replaces the abstract category in every player-facing string**. Resource variant names (Iron Slime, Copper Slime, etc.) don't change because they were never category-named to begin with.

## Slime infusion — species-locked

The current infusion system accepts any vanilla Slime or Magma Cube and turns it into any Resource Slime, category-mapped via the held primer item. The redesign tightens this: **only PF parent species can be infused, and only into variants within their own species.**

### The new rule, one sentence

*A Cave Slime infused with iron ore becomes an Iron Slime. A Cave Slime infused with an ender pearl does nothing — Void variants only come from Void Slimes.*

### Acceptance matrix

| Held primer item | Targeted entity | Outcome |
|---|---|---|
| `iron_ingot` (Cave variant primer) | Cave Slime | ✓ becomes Iron Slime |
| `iron_ingot` | Void Slime | ✗ rejected (Iron is Cave-only) |
| `ender_pearl` (Void variant primer) | Cave Slime | ✗ rejected (Ender Pearl is Void-only) |
| `iron_ingot` | vanilla `minecraft:slime` | ✗ rejected (vanilla mobs are not parents) |
| `magma_cream` | vanilla `minecraft:magma_cube` | ✗ rejected (vanilla mobs are not parents) |
| any | already-infused Resource Slime | ✗ rejected (single-shot infusion; no swapping) |
| stone (in `primer/cave` tag, no variant primer_item) | Cave Slime | ✗ rejected (loose-fit primers removed) |
| `iron_ingot` | Iron Slime | ✗ rejected (already a variant) |

### Q1–Q4 decisions baked in

- **Q1 = A.** Vanilla Slime and Magma Cube are NOT parent species. The infusion handler explicitly rejects them. Player must find a PF parent slime (Bog / Cave / Geode / Tide / Infernal / Void) to start the loop.
- **Q2 = sound + particle.** On rejection, play `SoundEvents.NOTE_BLOCK_BASS` (the "denied" click) at low volume + a `ParticleTypes.SMOKE` puff at the slime's head position. No actionbar / chat text — keep the feedback subtle and discoverable through play.
- **Q3 = hard-reject.** Already-infused Resource Slimes cannot be re-infused into a different variant, even within the same species. Once a Cave Slime becomes an Iron Slime, it's locked.
- **Q4 = Path A only.** The `primer/<category>` item tag system is deprecated for infusion. Every infusion requires an exact 1:1 match with a `SlimeVariant.primer_item` field. The tags are kept *only* for the Frog Egg priming flow (see "Frog Egg priming" subsection below).

### Implementation: `SlimeInfusionHandler` changes

Current logic (simplified):
```
1. target = right-clicked entity
2. if !instanceof Slime: return
3. held = player's held item
4. variantEntry = findByPrimerItem(registry, held)   // global scan
5. category = variantEntry?.category ?? matchPrimerTag(held)
6. if category == null: return
7. resource = transformInPlace(target, category)
8. resource.setVariant(variantEntry?.id)
```

New logic:
```
1. target = right-clicked entity
2. species = resolveParentSpecies(target)            // NEW: returns Category or null
3. if species == null: return                        // ← vanilla slime + magma cube fall out here
4. if target instanceof ResourceSlime: return        // ← re-infusion guard (unchanged)
5. held = player's held item
6. variantEntry = findByPrimerItem(registry, held)
7. if variantEntry == null: rejectFeedback(target); return    // no exact primer match
8. if variantEntry.category != species: rejectFeedback(target); return    // wrong species
9. resource = transformInPlace(target, species)
10. resource.setVariant(variantEntry.id)
```

`resolveParentSpecies(entity)` reads the `parent_species` datapack registry:
- If entity is a PF parent species (`com.flatts.productivefrogs.content.entity.{Bog,Cave,Geode,Tide,Infernal,Void}Slime`) → return its category from the registry.
- If entity is vanilla `Slime` or `MagmaCube` (and is NOT one of the above PF subclasses) → return `null`. The datapack entries for `minecraft:slime` and `minecraft:magma_cube` get **removed** in V1.5 so the registry lookup naturally returns null for them.

`rejectFeedback(target)` is a helper that plays the denied sound + smoke puff. Does not consume the primer item.

### Frog Egg priming — biome-agnostic, variant-primer-driven

Decision (2026-05-23): **Any vanilla frogspawn block can be converted to a species Primed Frog Egg by right-clicking with any variant's primer item.** No species-lock on the target — only vanilla frogspawn matters as the trigger.

| Held primer | Target | Result |
|---|---|---|
| `iron_ingot` | vanilla frogspawn | Cave Primed Frog Egg (Iron is a Cave variant → Cave species) |
| `diamond` | vanilla frogspawn | Cave Primed Frog Egg (Diamond is a Cave variant → Cave species) |
| `emerald` | vanilla frogspawn | Geode Primed Frog Egg (Emerald is a Geode variant → Geode species) |
| `ender_pearl` | vanilla frogspawn | Void Primed Frog Egg |
| any item not in any variant's `primer_item` | vanilla frogspawn | no effect (held item not consumed) |

Logic:
1. Target must be `Blocks.FROGSPAWN` (vanilla frogspawn block).
2. Held item resolves via `SlimeVariant.findByPrimerItem(registry, heldItemId)`.
3. If no match → no effect, no feedback (this is a no-op interaction, not a rejection — players right-clicking frogspawn with random items shouldn't see denial sounds).
4. If match → replace block with `PFBlocks.primedEgg(variant.category())`. Consume one item from held stack (creative skips).
5. The primed egg later hatches into species-keyed tadpoles (Cave Tadpole, Geode Tadpole, etc.) — variant identity is NOT preserved on tadpoles/frogs.

### Why the asymmetry with slime infusion

This is intentional. Slime infusion is **species-locked** (Q1 = A: must find a PF parent slime first). Frog Egg priming is **biome-agnostic** (vanilla frogspawn anywhere works). Two different gate functions because the gameplay roles differ:

- **Slimes are the resource.** They're what your frogs eat to produce ingots/gems/etc. Locking slime acquisition behind species discovery gives each parent species a unique reason to seek out its biome and protects the production loop from being trivially bootstrapped from vanilla mob farms.
- **Frogs are the consumer.** They're the conversion engine that turns slimes → froglights. Locking *both* sides behind species discovery would create a chicken-and-egg problem — you'd need a Cave Slime to start the slime loop AND a separate biome traversal to start the frog loop. By keeping frog egg priming accessible (any vanilla frogspawn + any variant primer), the player can pre-build their frog farm anywhere, then go biome-hunting for the slime side at their pace.

The asymmetry also means **skyblock viability is unblocked on the frog side**: if a pack provides any vanilla frogspawn (or a way to bottle frogspawn), the player can prime every species' frog using whatever resource items they have. Skyblock pain remains on the slime side (need parent slime spawn eggs in starter pack) but that's narrower than the alternative.

### Implementation: `EggPrimerHandler` changes

Current logic (simplified):
```
1. target = right-clicked block
2. if target != Blocks.FROGSPAWN: return
3. held = player's held item
4. for cat in Category.values():
     if held.is(PFTags.PRIMER_BY_CATEGORY.get(cat)):
       category = cat; break
5. if category == null: return
6. setBlock(pos, PFBlocks.primedEgg(category))
7. consume held item (1)
```

New logic:
```
1. target = right-clicked block
2. if target != Blocks.FROGSPAWN: return
3. held = player's held item
4. variantEntry = SlimeVariant.findByPrimerItem(registry, held.itemId)
5. if variantEntry == null: return  // no feedback — this is a no-op
6. category = variantEntry.category()
7. setBlock(pos, PFBlocks.primedEgg(category))
8. consume held item (1)
```

Net code change: ~5 lines replaced. The `primer/<category>` tag iteration goes away.

### Tag deletion in V1.5

Both slime infusion (Q4 = A) and frog egg priming (this section) route through `SlimeVariant.primer_item` exclusively. The `productivefrogs:primer/<category>` item tags become dead data and the JSON files are deleted in V1.5:

```
data/productivefrogs/tags/items/primer/bog.json       -- DELETE
data/productivefrogs/tags/items/primer/cave.json      -- DELETE  (was primer/mineral)
data/productivefrogs/tags/items/primer/geode.json     -- DELETE  (was primer/gem)
data/productivefrogs/tags/items/primer/tide.json      -- DELETE  (was primer/aquatic)
data/productivefrogs/tags/items/primer/infernal.json  -- DELETE
data/productivefrogs/tags/items/primer/void.json      -- DELETE  (was primer/arcane)
```

`PFTags.PRIMER_BY_CATEGORY` constant and its uses in code also removed.

### Sourcing impact — downstream concerns

- **Skyblock / restricted-biome packs.** Vanilla slime chunks are the easiest infinite slime source in skyblock; locking infusion to PF parents means Sky Frogs must ship Bog Slime + Infernal Slime as either craftable, quest-rewarded, or guaranteed via biome dimension. **Modpack note: include a "starter pack" path that grants 1 Bog Slime spawn egg early** so the player can bootstrap the Milker loop.
- **Discovery loop.** The "first Cave Slime" becomes a high-value find. Players will base-camp near a known spawn until the Slime Milker source-block loop is sustaining itself. Could matter for tutorial / advancement design — `advancements/first_parent_slime.json` becomes a meaningful milestone per species.
- **No vanilla-mob shortcut.** Players who farmed slime chunks before for cheap iron now need to find a Cave Slime first. Existing world saves with vanilla-slime farms still WORK as slime sources (drop slime balls etc.), they just don't gateway into PF anymore.

### Slime Milk source — spawns the slime that made the milk

Decision (2026-05-23): **A Slime Milk source block spawns the same slime type that was used to make the milk.** The milk preserves the source slime's identity end-to-end:

- An **Iron Slime** infused into the Milker → **Iron Slime Milk** → source block spawns **Iron Slimes**.
- A **Diamond Slime** infused into the Milker → **Diamond Slime Milk** → source block spawns **Diamond Slimes**.
- A category-only **Cave Slime** (no variant) infused into the Milker → **Cave Slime Milk** → source block spawns **Cave Slimes** (parent species, no variant).
- A **parent species** (Bog, Cave, Geode, Tide, Infernal, Void) milked directly → species-name milk → source spawns the same parent species.

This preserves the closed production loop: one variant slime in, an infinite stream of the same variant slime out. No per-cycle primer cost.

**Production loop, full chain:**

```
Iron Slime → Milker → Iron Slime Milk → source spawns Iron Slimes
                                      → feed to Cave Frog
                                      → Iron Froglight drops
                                      → smelt → Iron Ingot
```

Self-sustaining the moment the first Iron Slime is acquired (via Cave Slime + iron ingot infusion). The iron-ore investment is *one-time-per-variant* — the player pays the primer cost once to seed each variant's milk loop, then the loop runs forever.

This restores the variant identity as a meaningful first-class concept on the milk side. Variant-specific milks are mechanically distinct (each spawns its own variant), so the existing N-milks-per-species shape stays — no collapse to one-milk-per-species.

### Split discovery — parent-species only

Decision (2026-05-23): **Split discovery applies only to PF parent species. Vanilla `minecraft:slime` and `minecraft:magma_cube` no longer have any chance to split into a Resource Slime.**

When a PF parent species splits (Cave Slime → smaller Cave Slimes), each offspring rolls against the split-discovery probability. On hit, the offspring is converted to a category-only Resource Slime of the parent's species (no variant). This is the "passive discovery" production path — players who farm parent species naturally accumulate category-only Resource Slimes over time, which a matching frog will still eat (for a category-default Froglight drop).

When a vanilla slime / magma cube splits, every offspring stays vanilla. No conversion, no roll.

**Implementation note:** `SlimeSplitDiscoveryHandler.categoryForParent()` already does the EntityType-id lookup against the `parent_species` datapack registry. With vanilla `minecraft:slime` and `minecraft:magma_cube` entries **removed** from the registry (per the slime infusion section above), this lookup returns null for vanilla mobs and the handler's existing early-return guard handles it. No new code; the registry edit drives the behaviour.

### Bog Slime variant pool — confirmed for V1.5

Decision (2026-05-23): **Mob-drop variants (bone, leather, feather, gunpowder, string, rotten_flesh, clay_ball) stay under Bog Slime for V1.5.** The "Bog as catch-all" pattern noted in Open Question #2 is accepted as-is for the initial ship; a dedicated Fauna species is deferred to V2 or later if the variant pool gets large enough to warrant the split.

Updated Bog variant pool:

| Variant | Source in vanilla |
|---|---|
| bone | skeletons + sand structure fossils — broad overworld |
| gunpowder | creepers — broad overworld |
| clay_ball | clay blocks in swamps, shallow water |
| rotten_flesh | zombies — broad overworld |
| string | spiders + cobwebs — broad overworld |
| leather | cows — broad overworld |
| feather | chickens — broad overworld |
| slime_ball | a Bog Slime variant is itself a "Bog Slime carrying slime balls" — self-referential but harmless (a Bog Slime that drops slime balls when killed by a frog, separate from vanilla slime-ball drop on death). |

(Self-reference flag on `slime_ball` — see Open Question #3 in the original list. Still open; not breaking V1.5.)

### Definition of done (Slime Infusion section)

- [ ] `SlimeInfusionHandler.resolveParentSpecies()` returns null for `minecraft:slime` and `minecraft:magma_cube` instances.
- [ ] Right-clicking a vanilla slime / magma cube with any PF primer produces the rejection feedback (sound + smoke) and consumes nothing.
- [ ] Right-clicking a Cave Slime with an ender pearl produces the rejection feedback.
- [ ] Right-clicking a Cave Slime with an iron ingot produces an Iron Slime (existing happy-path behaviour, just within the new gate).
- [ ] Right-clicking an already-stamped Iron Slime with a copper ingot is silently ignored (current behaviour preserved).
- [ ] `primer/<category>` tag files removed from `data/productivefrogs/tags/items/primer/*`.
- [ ] `EggPrimerHandler` updated to route through `SlimeVariant.findByPrimerItem` exclusively.
- [ ] GameTest `slime_infusion_transforms_vanilla_into_resource_slime` updated to target a Cave Slime (not vanilla slime) and asserts the rejection feedback fires when targeting a vanilla slime.
- [ ] `docs/slime_sourcing.md` updated with the new rule.

## JEI information pages

Adding `IRecipeRegistration.addIngredientInfo()` entries so every item exposes an "Information" tab in JEI (accessible via **U** / **R** on the item). The goal: a new player who picks up an Iron Slime spawn egg can immediately see *"Hunted by Cave Frog. When killed by a Cave Frog, drops Iron Froglight."* without leaving the inventory screen.

### Coverage

| Item / Item class | Info template |
|---|---|
| Resource Slime spawn egg (each variant) | "Hunted by **\<Species\> Frog**. When killed by a \<Species\> Frog, drops **\<Resource\> Froglight**." |
| Slime Bucket (each variant) | Same as spawn egg — points at the matching frog + drop. |
| Category-only Slime Bucket (no variant) | "A \<Species\> Slime with no specific resource. Hunted by **\<Species\> Frog**." |
| Configurable Froglight (each variant) | "Dropped when a **\<Species\> Frog** kills an **\<Resource\> Slime**. Smelts to **\<Resource\>**." |
| Category Froglight block (each species) | "Dropped when a \<Species\> Frog kills a category-only \<Species\> Slime. Smelts to the species's default resource." |
| Resource Frog spawn egg (each species) | "Eats \<Species\> Slimes. Drops **\<Species\> Froglight** (or a variant-specific Froglight if the slime carried a resource)." |
| Resource Tadpole spawn egg (each species) | "Matures into **\<Species\> Frog** in water." |
| Bottle of \<Species\> Frog Eggs (each species) | "Place on water to hatch into **\<Species\> Tadpoles**." |
| Primed Frog Egg block (each species) | "Hatches into 1-3 **\<Species\> Tadpoles** after a delay." |
| Slime Milk bucket (each variant) | "Place in world to spawn **\<Resource\> Slimes** periodically. Output of the **Slime Milker** when fed an \<Resource\> Slime Bucket." |
| Slime Milker block | "Converts a Slime Bucket into the matching Slime Milk bucket over 100 ticks. Hand-operated; no power needed." |
| Parent slime species spawn egg (each species) | "Naturally spawns in \<biome list\>. Splits to produce **\<Species\> Slimes**. Infuse with a primer item to produce a specific variant." |

### Dynamic vs static

**Decision: dynamic.** Walk the `SlimeVariant` and `ParentSpeciesEntry` datapack registries at JEI plugin init, format short lang templates with substitutions. Reasons:

1. **Scale.** Post-redesign + V1.1 we'll ship ~30 variants; cross-mod variants (Mekanism osmium, Create zinc, etc.) appear via `mod_loaded` JSON conditions and would never get manual lang entries. Dynamic auto-scales.
2. **Lang surface stays small.** Three lang keys (variant slime, frog, parent species) substitute their way to ~60 surfaced info pages.
3. **Single source of truth.** When a variant's `category` changes (or a new species is added), the JEI info follows automatically — no parallel lang-file edit required.

### Lang keys (final count: 3)

| Key | Template | Substitutions |
|---|---|---|
| `productivefrogs.jei.variant_slime.info` | `Hunted by %1$s. When killed by a %1$s, drops %2$s Froglight.` | %1$s = frog display name, %2$s = resource display name |
| `productivefrogs.jei.frog.info` | `Eats %1$s Slimes. Drops %1$s Froglight (or a variant-specific Froglight if the slime carried a resource).` | %1$s = species display name |
| `productivefrogs.jei.parent_species.info` | `Spawns in %1$s. Splits to produce %2$s Slimes; infuse with a primer item to choose a specific resource variant.` | %1$s = biome list, %2$s = species display name |

(Plus 4-5 fixed-string keys for Slime Milker, Frog Egg Bottle, Primed Egg block, Slime Milk bucket — these don't need substitution.)

### Implementation skeleton

In `ProductiveFrogsJeiPlugin`, override `registerRecipes`. The plugin already runs after datapack load (`registerRecipes` fires after `registerItemSubtypes`), so the variant/parent-species registries are populated.

```java
@Override
public void registerRecipes(IRecipeRegistration reg) {
    Level level = Minecraft.getInstance().level;
    if (level == null) return; // JEI re-fires after world load
    Registry<SlimeVariant> variants = level.registryAccess()
        .registryOrThrow(PFRegistries.SLIME_VARIANT);

    for (Map.Entry<ResourceKey<SlimeVariant>, SlimeVariant> e : variants.entrySet()) {
        SlimeVariant v = e.getValue();
        String variantName = e.getKey().location().getPath(); // "iron"
        Component frogName = Component.translatable(
            "entity.productivefrogs.resource_frog." + v.category().id());
        Component resourceName = Component.translatable(
            "item.productivefrogs.resource_slime." + variantName);
        Component info = Component.translatable(
            "productivefrogs.jei.variant_slime.info", frogName, resourceName);

        // Slime spawn egg
        reg.addIngredientInfo(
            new ItemStack(PFItems.RESOURCE_SLIME_SPAWN_EGGS.get(variantName).get()),
            VanillaTypes.ITEM_STACK, info);
        // Configurable Froglight stamped with this variant
        ItemStack froglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        froglight.set(PFDataComponents.SLIME_VARIANT.get(), e.getKey().location());
        reg.addIngredientInfo(froglight, VanillaTypes.ITEM_STACK, info);
        // Slime bucket stamped with this variant — TODO: build per-variant bucket stack
    }
    // ... frog + parent-species loops similar
}
```

### Definition of done (JEI section)

- [ ] All ~60 info-surfaceable items have a JEI Information tab populated.
- [ ] Hovering an Iron Slime spawn egg in JEI's right sidebar and pressing **U** shows a readable info page mentioning Cave Frog + Iron Froglight.
- [ ] No hard-coded variant names in the JEI plugin code — everything routes through the SlimeVariant registry.
- [ ] When a modpack adds a new variant via JSON datapack, its JEI info page appears automatically (no PF code change needed). Verify via a throwaway "test_variant.json" in `data/productivefrogs/productivefrogs/slime_variant/` during dev.

## Implementation impact

### Code changes

1. **`data.Category` enum** — rename constants. Migration notes:
   - JSON data files: every existing `data/<ns>/productivefrogs/slime_variant/*.json` has a `"category": "<old>"` field. Mechanical sed for the rename.
   - Tag paths: `productivefrogs:primer/metallic/*` directories rename to `productivefrogs:primer/bog/*` (and are then deleted entirely per the Frog Egg priming section above).
   - **Save-data migration: NOT required.** Pre-V1.5 worlds will have stale category names baked into entity NBT and item data components; these will fail to deserialise cleanly on V1.5 load. Decision (2026-05-23): downstream consumers carry their own world-reset responsibility for this upgrade. No datafixer ships.
2. **`PFEntities`** — register two new entities: `bog_slime` and `infernal_slime`. Each gets its own class (CaveSlime / GeodeSlime / TideSlime / VoidSlime are the existing references).
3. **`PFItems`** — register spawn eggs for the two new species. Update `PFItems.RESOURCE_FROG_SPAWN_EGGS` / `RESOURCE_TADPOLE_SPAWN_EGGS` map keys to new category names.
4. **`PFBlocks`** — Primed Frog Egg blocks need to rename (per-category block IDs). Same for Resource Froglight blocks.
5. **`PFFluidTypes` / `PFFluids` / `PFBlocks`** — Slime Milk fluid variants are keyed off variant names (iron, copper, ...) not category names — these don't change.
6. **`PFTags`** — primer tag paths rename. Tag files relocate from `tags/item/primer/<old>.json` to `tags/item/primer/<new>.json`.
7. **`SlimeInfusionHandler` / `SlimeSplitDiscoveryHandler` / `EggPrimerHandler` / `FrogTongueDropHandler`** — switch from `Category.METALLIC` etc. to new enum constants. Logic is unchanged; only constant names.
8. **`ResourceFrogAttackablesSensor`** — uses `frog.getCategory() == slime.getCategory()` — works unchanged once enum constants are renamed.
9. **`PFModBusEvents.onRegisterSpawnPlacements`** — register placements for the two new species.

### Data changes

1. **Parent-species datapack registry entries** — 2 new JSON files for `bog_slime.json` + `infernal_slime.json`; existing 4 stay but with category field renamed. Vanilla `slime.json` and `magma_cube.json` entries can be **removed** (since Bog/Infernal Slime are now the canonical PF parents) OR kept as a courtesy so vanilla mobs still classify into the new categories on infusion. Open Question.
2. **Biome modifier JSONs** — 6 files total (4 existing get updated weights/Y bands; 2 new for Bog + Infernal).
3. **Slime variant JSONs** — all 12 V1 variants get `"category": "<new>"`.
4. **Tag JSONs** — `tags/item/primer/<old>.json` → `tags/item/primer/<new>.json` (just file moves; contents unchanged).
5. **Loot tables, recipes, spawn predicate JSONs** that reference category names — sweep for `metallic`/`mineral`/`gem`/`aquatic`/`arcane` literal strings.

### Asset changes

1. **Two new entity texture sets** for Bog Slime + Infernal Slime (inner cube + outer translucent layer, matching the existing 4 PF parent species).
2. **Two new spawn-egg model files** (Bog Slime, Infernal Slime).
3. **Lang file** — ~50 key renames per the table above.
4. **All Primed Frog Egg / Resource Froglight block JSONs** rename their model paths (the file paths embed the category — `metallic_frog_egg.json` → `bog_frog_egg.json` etc.).
5. **All spawn egg models** — file renames (`metallic_frog_spawn_egg.json` → `bog_frog_spawn_egg.json` etc.). Generated from a script — already proven workable in the port.

## Backwards compatibility

This is a **breaking change** for datapacks and modpack overlays. **World save migration is explicitly out of scope** — V1.5 ships clean; pre-V1.5 worlds need to be regenerated by downstream consumers.

Concrete breakages:

- **Cross-mod datapack overlays**: any modpack maintainer who shipped custom slime variant JSONs has to update their `category` field values to the new species names.
- **Tag overrides**: modpack tag overrides like `data/<modpack>/productivefrogs/tags/items/primer/metallic.json` were already being deprecated for the slime infusion / egg priming flows (Q4 = A) — these are deleted entirely in V1.5.
- **Recipe overrides**: any custom froglight smelting recipe override that references the old category names.
- **Pre-V1.5 world saves**: undefined behaviour. Entities and ItemStacks with stale category NBT will fail to deserialise cleanly. No datafixer ships.

The doc `docs/cross_mod_compat.md` needs a migration appendix listing the category renames once V1.5 lands.

## Open questions

These need user input before the spec locks. Each is non-trivial and changes the implementation surface.

1. **Are vanilla slime + magma cube still acceptable infusion targets?** If yes, the player can right-click a swamp-spawned vanilla slime with iron ore and get a Cave-Iron Slime. If no, the player has to find a Bog Slime spawn first. Argument for yes: skyblock / early game accessibility. Argument for no: the new PF species feel meaningless if vanilla mobs are interchangeable substitutes.
2. **Should mob-drop variants (bone, leather, feather, gunpowder, etc.) go under Bog, or get their own species?** Bog is currently doing double duty — both "swamp slimes" and "mob drops" — which feels strained. An alternative is to add a 7th species ("Fauna Slime"? "Carnal Slime"?) for mob drops. That would also un-default Bog from being the catch-all and let Bog focus on swamp-thematic resources (clay, bones from drowned, etc.).
3. **What about the "Magma" + "Slime" mob-drop pair?** Magma cream and slime ball were category-specific in V1 (Infernal + METALLIC). In the new scheme, slime ball is a Bog variant (the parent slime is the source). Magma cream is an Infernal variant. Both are self-referencing — a Bog Slime droppped from a Bog-Slime-Ball is weird. Maybe these need a separate handling path (e.g., the parent species drops its own ball directly, no variant required).
4. **Do `Cave Slime` variants get a Y-level requirement on spawn?** Or only on the biome-modifier level? Players in shallow lush_caves might spawn-camp at y=40 and miss the Y < 30 deep-cave band where copper/iron are densest.
5. **Geode Slime above tree line — texture readability.** At y=120+ in snowy_slopes the slime is rendering against snow. The existing Geode Slime texture is amethyst-cyan; on snow it might wash out. Worth a render-pass test.
6. **Should categories with no current variants (post-rename) get a fallback?** E.g., post-rename, the new "Infernal" category currently has only `magma_cream` from V1 — until V1.1 lands the other 5 infernal variants, infernal Resource Slime split-discovery would have an almost-empty pool. Same for Geode (only emerald). Workaround: if pool is size-1, always pick that one; if size-0, fall back to category-only (no variant). Already current behaviour — just want to confirm.
7. **What is the V1.5 release shape?** Two options:
   - **Single big PR / release**: rename everything atomically. Datafixer ships in the same release. Modpack maintainers update once.
   - **Two-phase release**: phase A adds the new species + new category constants alongside the old ones (deprecated); phase B drops the old constants. Gives modpack maintainers a deprecation window.
   The cost of phase B = months of two-name handling code in every Java surface. Cost of single PR = forced atomic upgrade for downstream. Open Question whether the modpack ecosystem is small enough to absorb the single-PR cost.

## Definition of done (V1.5)

This list assumes single-PR shape (Open Question #7). Tick when each is verified by playtest:

- [ ] Internal `Category` enum renamed in code; all references compile clean.
- [ ] Two new entities (`bog_slime`, `infernal_slime`) render correctly in-world with category tint.
- [ ] All six species have a working biome modifier; verify spawn in-world by flying around target biomes for ~10 minutes in each.
- [ ] All Resource Slime variants resolve to their new species correctly. Verify via JEI subtype lookup.
- [ ] All six frog spawn eggs resolve to the right entity + tint.
- [ ] Lang file has no stray "METALLIC"/"Metallic"/"Mineral" etc. in any user-facing string.
- [ ] The GameTest suite (47 tests) updates its category constant references and re-passes 47/47.
- [ ] `docs/categories_and_tiers.md`, `docs/slime_sourcing.md`, `docs/architecture.md`, `docs/farming.md` all updated to the new naming.
- [ ] Modpack migration note added to `docs/cross_mod_compat.md`.
- [ ] CHANGELOG entry tagged BREAKING with a one-line migration summary.
- [ ] JEI Information pages populated for all surfaceable items (see JEI section's Definition of done).

## Changelog of this document

- **2026-05-23**: Initial draft after the MC 1.21.1 port surfaced the parent/frog naming mismatch. User locked the six species names: Bog, Cave, Geode, Tide, Infernal, Void. Variant remapping table is a strawman pending playtest validation. Open Questions are real and need iteration.
