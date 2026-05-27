# Open Questions

Design decisions that were resolved during the V1 design phase. All entries are DECIDED ✅ - this doc is preserved as the decision log. Note: V1 used abstract category names (Metallic/Mineral/Gem/Aquatic/Arcane); v1.0 renamed them to species names (Bog/Cave/Geode/Tide/Void; Infernal unchanged) per [species_as_category_redesign.md](./species_as_category_redesign.md). The tables below have been updated to the v1.0 species names; the underlying decisions are unchanged.

## 1. Tier order - DECIDED ✅

**Decision:** progression bias `Cave → Geode → Infernal → Void` (Bog and Tide are unordered, accessible early-to-mid).

| Tier | Species | Primer (canonical) | Equipment gate |
|---|---|---|---|
| Early | Bog | any Bog variant primer (bone, etc.) | Stone pickaxe - accessible from swamp surface |
| T1 | Cave | Iron Ingot | Stone pickaxe → iron ore |
| T2 | Cave | Diamond | Iron pickaxe → diamond ore |
| T3 | Geode | Emerald | Iron pickaxe → mountain biome |
| T4 | Tide | Prismarine Shard | Iron gear + water breathing + ocean monument |
| T5 | Infernal | Magma Cream | Diamond pickaxe → obsidian → Nether |
| T6 | Void | Ender Pearl | Enderman kills + End access |

**Rationale:** Reflects actual vanilla equipment-cost progression. Cave is the workhorse species containing iron, copper, gold, redstone, lapis, coal, diamond - all reachable on iron-pickaxe gear. T4 (Tide) is a soft gate behind ocean monument access. T5 (Infernal) is a hard gate behind obsidian → Nether. T6 (Void) is endgame via the End.

---

## 2. Slime sourcing - DECIDED ✅ (with v1.0 modifications)

**Original decision:** Two paths, both run on the vanilla slime split event:

- **Random discovery path** - when a PF parent species is killed, each split offspring has a configurable chance to be a category-only Resource Slime.
- **Infusion path** - right-click a PF parent slime with a variant's `primer_item` → slime is immediately transformed into the matching variant Resource Slime.

**v1.0 modifications (from species_as_category_redesign.md):**
- **Vanilla `minecraft:slime` and `minecraft:magma_cube` are NOT acceptable infusion targets** (Q1=A baked in). Only the six PF parent species can be infused. Vanilla mobs split into vanilla offspring with no discovery roll.
- **Infusion is species-locked.** Cave Slime + iron ingot → Iron Slime ✓. Cave Slime + ender pearl → rejected (Ender is a Void variant).
- **Re-infusion is hard-rejected** (Q3). An Iron Slime cannot be re-infused into a Copper Slime.

**No breeding, no wild Resource Slime spawns, no Slime Nursery (V2).**

### 2a. Tiny slime (size 1) infusion behavior - DECIDED ✅

**Decision:** **(A) Transform in place.** Feeding a primer-tagged item to a size-1 vanilla slime converts that exact slime into a tiny Resource Slime of the matching type on the spot. The item is consumed; the slime is ready to feed to a frog immediately.

**Rationale:** Avoids a dead-end UX where tiny slimes can't be used. Keeps the mechanic consistent at any slime size. Cost is symmetric - 1 ingot produces 1 Resource Slime regardless of parent size, just without the multiplier you get from infusing a larger slime that then splits.

### 2b. Default random-discovery chance - DECIDED ✅

**Decision:** **5%** per offspring (configurable per parent-entity).

**Rationale:** Discovery is the "treat" path; infusion is the workhorse. At 5%, killing a big vanilla slime (which spawns ~3 medium offspring) gives a Resource Slime about 1 in 7 kills on average - frequent enough to feel rewarding, rare enough that serious production still requires infusion. The value is exposed as mod config so it can be tuned per-pack without requiring a release.

### 2c. Discovery coverage for non-vanilla species - DECIDED ✅ (v1.0: ALL species custom)

**v1.0 decision:** Vanilla `minecraft:slime` and `minecraft:magma_cube` are NOT part of the production system. Every species is a custom PF entity with its own themed spawn rules.

| Species | Parent slime entity | Spawn niche |
|---|---|---|
| Bog | `productivefrogs:bog_slime` | Swamps, mangrove swamps |
| Cave | `productivefrogs:cave_slime` | Dripstone caves, lush caves |
| Geode | `productivefrogs:geode_slime` | Mountain peaks (stony / jagged / frozen) |
| Tide | `productivefrogs:tide_slime` | Deep / lukewarm / warm ocean biomes |
| Infernal | `productivefrogs:infernal_slime` | Nether wastes, basalt deltas, soul sand valley |
| Void | `productivefrogs:void_slime` | Outer End islands |

**Rationale:** Symmetric design across all six species - every species has both a discovery path (its themed parent) and an infusion path. Players seeking PF slimes go to PF biomes; vanilla slime farms don't bootstrap PF production.

---

## 3. Vanilla variant mapping - DECIDED ✅

**Decision:** **(A) Untouched.** Vanilla `minecraft:frog` (warm/temperate/cold) entity and its froglight-from-magma-cube mechanic are left exactly as in vanilla. They remain a parallel, decorative path for the three vanilla froglight colors.

**Our Resource Frogs are a separate entity type** (`productivefrogs:resource_frog`) born from primed Frog Eggs via the glass-bottle pipeline. Vanilla frogs serve our mod only as the source of frogspawn (which an empty glass bottle captures into Frog Eggs).

**Rationale:**

- Cleanest mental model: vanilla content stays vanilla, our content is distinct and recognizable.
- No compat risk with other mods that extend the vanilla frog entity.
- 3 vanilla variants vs 6 categories would create asymmetry if mapped.
- Players' existing vanilla frog farms (for ochre/pearlescent/verdant froglights) keep working unchanged.

---

## 4. Org / author slug for Java package - DECIDED ✅

**Decision:** `com.flatts.productivefrogs`

- Mod ID: `productivefrogs`
- Java package: `com.flatts.productivefrogs`
- Sub-packages follow the layout in [architecture.md](./architecture.md): `.registry`, `.content.item`, `.content.block`, `.content.entity`, `.content.fluid`, `.data`, `.datagen`, `.compat`

---

## 5. Frog Net - superseded by vanilla glass bottle ✅

**Decision:** No custom "Frog Net" item is added. Players use a **vanilla empty glass bottle** to bottle frogspawn into a `Frog Egg` item. One fewer item to design, model, and texture.

**Original question** (preserved for history): Should the Frog Net double as a pickup tool for adult Resource Frogs?

**Original answer:** (B) No - bottling tool is frogspawn-only.

**Current answer:** Question is moot. There IS no Frog Net. Adult Resource Frogs are moved via:

- **Leads** - the canonical method. Vanilla leads work on frogs, and Resource Frogs inherit this behavior.
- **Slimeball lure** - frogs follow players holding slimeballs.
- **Boats** - frogs can ride boats.

**Glass bottle implementation:**

- Right-click handler on `minecraft:glass_bottle` checks for a `minecraft:frogspawn` block target.
- Consumes 1 bottle from the stack + the frogspawn block.
- Gives 1 `productivefrogs:frog_egg` item.
- Empty bottles stack to 64 in vanilla, so players can carry many.

---

## 6. Loader scope - DECIDED ✅

**Decision:** **(C) NeoForge-only, forever.** No Fabric port planned, in V1 or any future version.

**What this means:**

- No Architectury abstraction layer. All code uses native NeoForge APIs directly.
- Single Gradle module, single platform module - simpler project layout than the sibling `flatts-chem-lib`.
- Smaller maintenance surface; less ongoing dual-platform testing.
- Target audience: ATM10 / NeoForge ecosystem players. Fabric audience explicitly out of scope.

**Rationale:** Avoids the Architectury cost at every API call. Productive Frogs leans heavily on NeoForge-specific APIs (datapack registries with `neoforge:conditions`, custom Fluid + FluidType pattern, NeoForge's IItemExtension for slime interactions). Porting would mean rewriting most of the interesting hooks. Better to do one platform well.

---

## 7. Frog breeding mechanic - DECIDED ✅

**Decision:** Two Resource Frogs of the **same species** can be bred via the vanilla animal love-mode pattern, producing a Primed Frog Egg block of that species placed on water.

- **Breed item:** slimeball (1 per parent, consumed). Vanilla frog breeding mechanic.
- **Same-species only.** Two Cave Frogs produce a Cave offspring. Cross-species pairing (e.g. Cave × Geode) is not supported.
- **Offspring path:** the breeding pair places a Primed Frog Egg block of their shared species on a nearby water tile - same block as hand-primed eggs. Hatches into a Resource Tadpole of that species, or nettable into a Primed Frog Egg item.
- **No primer cost on offspring.** Once a player has two same-species Resource Frogs, the population is self-sustaining. The primer pipeline (glass bottle → Frog Egg → primer item) is the one-time bootstrap per species.
- **Slimes do NOT breed.** Slime breeding was earlier discussed but rejected; the breeding mechanic is exclusively on the frog side.

**Subsumes Q12** (which asked the same question separately).

Full spec: [items_and_blocks.md](./items_and_blocks.md#frog-breeding).

## 8. Frog AI specifics - DECIDED ✅

**Decision:** Resource Frogs inherit vanilla `minecraft:frog` AI completely. Only behavioral override is a species-match filter on prey eligibility - a Cave Frog only considers Cave-species Resource Slimes as valid tongue targets.

**Vanilla behaviors preserved:**

- Tongue range and cooldown
- Movement (mostly stationary, hops around water)
- Water proximity preference
- Breeding behavior (slimeballs, but see Q12 below for whether Resource Frogs produce Resource Frogspawn or vanilla frogspawn)
- Persistence (bred/hatched frogs don't despawn)

**Why:** Avoids tuning a separate satiation/cooldown system; lets the throughput economy sit on top of vanilla pacing (frog tongue rate vs. player's milk source count).

## 9. Player direct-feeding - DECIDED ✅

**Decision:** **(A) Yes.** Right-click a Resource Frog while holding a matching-species Slime Bucket → frog instantly tongues the bucketed slime, the bucket transforms back to empty, and the frog drops the appropriate Froglight at its position.

- The species-match check still applies - feeding a Geode Slime to a Cave Frog does nothing (frog ignores it). The bucket is not consumed on mismatch.
- Uses the same drop logic as the in-world feed (loot table per slime variant).
- Tongue cooldown still applies - repeat direct-feeds happen at vanilla tongue rate.

**Rationale:** Single right-click handler. Real QoL win. Doesn't break the economy - just shortcuts the milk-fountain step for players who already have a slime in hand. Useful for testing and low-tech players.

## 10. Resource Slime direct-kill drops - DECIDED ✅

**Decision:** **(A) Slimeballs only.** Killing a Resource Slime directly (sword, fall damage, lava, etc.) yields only slimeballs - same as vanilla slime parity. The resource conversion happens exclusively via the frog tongue interaction.

**Rationale:** Keeps the frog the canonical conversion path. Doesn't punish accidents - slimeballs are useful (re-priming, breeding frogs). Players who skip the frog still get a baseline return, but no resource bonus.

## 11. Primed Frog Egg hatching conditions - DECIDED ✅

**Decision:** **Same rules as vanilla frogspawn.** Must sit on (or adjacent to) a water source block. No biome / temperature requirement. Hatch time matches the vanilla frogspawn timer.

**Rationale:** No surprise gating, no biome-discovery friction. The primer cost (paid at egg-priming time) IS the gate. Players who place a primed egg in the wrong spot just need to fix the water adjacency - same as they would for vanilla frogspawn.

## 12. Resource Frog breeding - DECIDED ✅ (subsumed by Q7)

**Decision:** Subsumed by Q7 above. Resource Frogs breed same-category and produce a Primed Frog Egg block of their shared category. Primer pipeline is the one-time bootstrap; breeding sustains the population after.

## Decision Status

All questions resolved; v1.0 design is frozen and shipped (2026-05-24):

- [x] 1. Tier order confirmed: Cave → Geode → Tide → Infernal → Void (Bog accessible early)
- [x] 2. Slime sourcing core mechanic chosen: PF-parent random-discovery + species-locked infusion
  - [x] 2a. Tiny slime infusion: transform in place
  - [x] 2b. Default discovery chance: 5% per offspring (configurable, vanilla slimes excluded)
  - [x] 2c. Species discovery: six custom PF parent entities (Bog / Cave / Geode / Tide / Infernal / Void); vanilla mobs hard-rejected
- [x] 3. Vanilla variant mapping decided: Option A (vanilla frogs untouched, Resource Frogs are separate entity)
- [x] 4. Org slug chosen: com.flatts.productivefrogs
- [x] 5. Frogspawn capture: vanilla glass bottle (no custom tool); adult frogs move via leads
- [x] 6. Loader scope confirmed: NeoForge-only, forever (no Fabric port)
- [x] 7. Frog breeding: same-species → Primed Frog Egg of that species (slimes do NOT breed)
- [x] 8. Frog AI: vanilla unchanged except species-match prey filter
- [x] 9. Player direct-feeding: yes (right-click frog with bucketed slime, species-match required)
- [x] 10. Resource Slime direct-kill drops: slimeballs only (vanilla parity)
- [x] 11. Primed Frog Egg hatching conditions: vanilla frogspawn rules (water-adjacent, vanilla timer)
- [x] 12. Subsumed by Q7 (Resource Frog breeding produces species-matching offspring)
