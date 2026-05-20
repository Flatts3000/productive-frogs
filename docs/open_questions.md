# Open Questions

Design decisions that are still outstanding. Each has a current "lean" I'd recommend, but they need explicit confirmation before scaffolding begins.

## 1. Tier order — DECIDED ✅

**Decision:** `Metallic → Mineral → Gem → Infernal → Arcane`

| Tier | Category | Primer | Equipment gate |
|---|---|---|---|
| T1 | Metallic | Iron Ingot | Stone pickaxe |
| T2 | Mineral | Redstone Dust | Iron pickaxe |
| T3 | Gem | Diamond | Iron pickaxe |
| T4 | Infernal | Magma Cream | Diamond pickaxe → Nether |
| T5 | Arcane | Ender Pearl | Enderman kills, End for full content |

**Rationale:** Reflects actual vanilla equipment-cost progression. T1–T3 are reachable in parallel on iron-pickaxe gear; T4 is the hard Nether gate (requires diamond pick for obsidian); T5 functions as endgame via End access.

---

## 2. Slime sourcing — CORE MECHANIC DECIDED ✅ (sub-questions pending)

**Decision:** Two paths, both run on the vanilla slime split event:

- **Random discovery path** — when a vanilla slime/magma cube is killed, each split offspring has a configurable chance to be a Resource Slime from the parent species's default category pool (green slime → metallic pool, magma cube → infernal pool).
- **Infusion override path** — right-click slime with a primer-tagged item → 100% of offspring on next kill are Resource Slimes of that type. Overrides the default category, allowing cross-category production.

**No breeding, no wild Resource Slime spawns, no Slime Nursery (V2).** Full details: [slime_sourcing.md](./slime_sourcing.md).

### 2a. Tiny slime (size 1) infusion behavior — DECIDED ✅

**Decision:** **(A) Transform in place.** Feeding a primer-tagged item to a size-1 vanilla slime converts that exact slime into a tiny Resource Slime of the matching type on the spot. The item is consumed; the slime is ready to feed to a frog immediately.

**Rationale:** Avoids a dead-end UX where tiny slimes can't be used. Keeps the mechanic consistent at any slime size. Cost is symmetric — 1 ingot produces 1 Resource Slime regardless of parent size, just without the multiplier you get from infusing a larger slime that then splits.

### 2b. Default random-discovery chance — DECIDED ✅

**Decision:** **5%** per offspring (configurable per parent-entity).

**Rationale:** Discovery is the "treat" path; infusion is the workhorse. At 5%, killing a big vanilla slime (which spawns ~3 medium offspring) gives a Resource Slime about 1 in 7 kills on average — frequent enough to feel rewarding, rare enough that serious production still requires infusion. The value is exposed as mod config so it can be tuned per-pack without requiring a release.

### 2c. Discovery coverage for non-vanilla categories — DECIDED ✅

**Question:** Vanilla only provides parent slimes for metallic (`minecraft:slime`) and infernal (`minecraft:magma_cube`). How do players get discovery-path access to mineral, gem, aquatic, and arcane?

**Decision:** **(A) Add new parent slime entities, one per missing category.** Each is a vanilla-flavored slime variant (same split mechanic, themed texture and spawn rule).

| Category | Parent slime species | Spawn niche |
|---|---|---|
| Mineral | `productivefrogs:cave_slime` | Dripstone caves, deep dark, deep slime chunks |
| Gem | `productivefrogs:geode_slime` | Amethyst geodes, mountain biomes |
| Aquatic | `productivefrogs:tide_slime` | Deep / lukewarm / warm ocean biomes |
| Arcane | `productivefrogs:void_slime` | Outer End islands |

**Rationale:** Symmetric design across all categories — every category has both a discovery path (its themed parent) and an infusion path (any primer-tagged item on any slime). New entity scope is bounded — each new parent is a re-skin of green slime in behavior, requiring texture + spawn config + 1 JSON registration.

---

## 3. Vanilla variant mapping — DECIDED ✅

**Decision:** **(A) Untouched.** Vanilla `minecraft:frog` (warm/temperate/cold) entity and its froglight-from-magma-cube mechanic are left exactly as in vanilla. They remain a parallel, decorative path for the three vanilla froglight colors.

**Our Resource Frogs are a separate entity type** (`productivefrogs:resource_frog`) born from primed Frog Eggs via the glass-bottle pipeline. Vanilla frogs serve our mod only as the source of frogspawn (which an empty glass bottle captures into Frog Eggs).

**Rationale:**

- Cleanest mental model: vanilla content stays vanilla, our content is distinct and recognizable.
- No compat risk with other mods that extend the vanilla frog entity.
- 3 vanilla variants vs 6 categories would create asymmetry if mapped.
- Players' existing vanilla frog farms (for ochre/pearlescent/verdant froglights) keep working unchanged.

---

## 4. Org / author slug for Java package — DECIDED ✅

**Decision:** `com.flatts.productivefrogs`

- Mod ID: `productivefrogs`
- Java package: `com.flatts.productivefrogs`
- Sub-packages follow the layout in [architecture.md](./architecture.md): `.registry`, `.content.item`, `.content.block`, `.content.entity`, `.content.fluid`, `.data`, `.datagen`, `.compat`

---

## 5. Frog Net — superseded by vanilla glass bottle ✅

**Decision:** No custom "Frog Net" item is added. Players use a **vanilla empty glass bottle** to bottle frogspawn into a `Frog Egg` item. One fewer item to design, model, and texture.

**Original question** (preserved for history): Should the Frog Net double as a pickup tool for adult Resource Frogs?

**Original answer:** (B) No — bottling tool is frogspawn-only.

**Current answer:** Question is moot. There IS no Frog Net. Adult Resource Frogs are moved via:

- **Leads** — the canonical method. Vanilla leads work on frogs, and Resource Frogs inherit this behavior.
- **Slimeball lure** — frogs follow players holding slimeballs.
- **Boats** — frogs can ride boats.

**Glass bottle implementation:**

- Right-click handler on `minecraft:glass_bottle` checks for a `minecraft:frogspawn` block target.
- Consumes 1 bottle from the stack + the frogspawn block.
- Gives 1 `productivefrogs:frog_egg` item.
- Empty bottles stack to 64 in vanilla, so players can carry many.

---

## 6. Loader scope — DECIDED ✅

**Decision:** **(C) NeoForge-only, forever.** No Fabric port planned, in V1 or any future version.

**What this means:**

- No Architectury abstraction layer. All code uses native NeoForge APIs directly.
- Single Gradle module, single platform module — simpler project layout than the sibling `flatts-chem-lib`.
- Smaller maintenance surface; less ongoing dual-platform testing.
- Target audience: ATM10 / NeoForge ecosystem players. Fabric audience explicitly out of scope.

**Rationale:** Avoids the Architectury cost at every API call. Productive Frogs leans heavily on NeoForge-specific APIs (datapack registries with `neoforge:conditions`, custom Fluid + FluidType pattern, NeoForge's IItemExtension for slime interactions). Porting would mean rewriting most of the interesting hooks. Better to do one platform well.

---

## 7. Frog breeding mechanic — DECIDED ✅

**Decision:** Two Resource Frogs of the **same category** can be bred via the vanilla animal love-mode pattern, producing a Primed Frog Egg block of that category placed on water.

- **Breed item:** slimeball (1 per parent, consumed). Vanilla frog breeding mechanic.
- **Same-category only.** Two Metallic Frogs produce a Metallic offspring. Cross-category pairing (e.g. Metallic × Gem) is not supported.
- **Offspring path:** the breeding pair places a Primed Frog Egg block of their shared category on a nearby water tile — same block as hand-primed eggs. Hatches into a Resource Tadpole of that category, or nettable into a Primed Frog Egg item.
- **No primer cost on offspring.** Once a player has two same-category Resource Frogs, the population is self-sustaining. The primer pipeline (glass bottle → Frog Egg → primer item) is the one-time bootstrap per category.
- **Slimes do NOT breed.** Slime breeding was earlier discussed but rejected; the breeding mechanic is exclusively on the frog side.

**Subsumes Q12** (which asked the same question separately).

Full spec: [items_and_blocks.md](./items_and_blocks.md#frog-breeding).

## 8. Frog AI specifics — DECIDED ✅

**Decision:** Resource Frogs inherit vanilla `minecraft:frog` AI completely. Only behavioral override is a category-match filter on prey eligibility — a Metallic Frog only considers slimes in the metallic category tag as valid tongue targets.

**Vanilla behaviors preserved:**

- Tongue range and cooldown
- Movement (mostly stationary, hops around water)
- Water proximity preference
- Breeding behavior (slimeballs, but see Q12 below for whether Resource Frogs produce Resource Frogspawn or vanilla frogspawn)
- Persistence (bred/hatched frogs don't despawn)

**Why:** Avoids tuning a separate satiation/cooldown system; lets the throughput economy sit on top of vanilla pacing (frog tongue rate vs. player's milk source count).

## 9. Player direct-feeding — DECIDED ✅

**Decision:** **(A) Yes.** Right-click a Resource Frog while holding a matching-category Slime Bucket → frog instantly tongues the bucketed slime, the bucket transforms back to empty, and the frog drops the appropriate Froglight at its position.

- The category-match check still applies — feeding a Gem Slime to a Metallic Frog does nothing (frog ignores it). The bucket is not consumed on mismatch.
- Uses the same drop logic as the in-world feed (loot table per slime variant).
- Tongue cooldown still applies — repeat direct-feeds happen at vanilla tongue rate.

**Rationale:** Single right-click handler. Real QoL win. Doesn't break the economy — just shortcuts the milk-fountain step for players who already have a slime in hand. Useful for testing and low-tech players.

## 10. Resource Slime direct-kill drops — DECIDED ✅

**Decision:** **(A) Slimeballs only.** Killing a Resource Slime directly (sword, fall damage, lava, etc.) yields only slimeballs — same as vanilla slime parity. The resource conversion happens exclusively via the frog tongue interaction.

**Rationale:** Keeps the frog the canonical conversion path. Doesn't punish accidents — slimeballs are useful (re-priming, breeding frogs). Players who skip the frog still get a baseline return, but no resource bonus.

## 11. Primed Frog Egg hatching conditions — DECIDED ✅

**Decision:** **Same rules as vanilla frogspawn.** Must sit on (or adjacent to) a water source block. No biome / temperature requirement. Hatch time matches the vanilla frogspawn timer.

**Rationale:** No surprise gating, no biome-discovery friction. The primer cost (paid at egg-priming time) IS the gate. Players who place a primed egg in the wrong spot just need to fix the water adjacency — same as they would for vanilla frogspawn.

## 12. Resource Frog breeding — DECIDED ✅ (subsumed by Q7)

**Decision:** Subsumed by Q7 above. Resource Frogs breed same-category and produce a Primed Frog Egg block of their shared category. Primer pipeline is the one-time bootstrap; breeding sustains the population after.

## Decision Status

Once all questions are answered, the design is frozen and scaffolding begins:

- [x] 1. Tier order confirmed: Metallic → Mineral → Gem → Infernal → Arcane
- [x] 2. Slime sourcing core mechanic chosen: random-discovery + infusion override
  - [x] 2a. Tiny slime infusion: transform in place
  - [x] 2b. Default discovery chance: 5% per offspring (configurable)
  - [x] 2c. Non-vanilla category discovery: new parent slime entities (Cave / Geode / Tide / Void)
- [x] 3. Vanilla variant mapping decided: Option A (vanilla frogs untouched, Resource Frogs are separate entity)
- [x] 4. Org slug chosen: com.flatts.productivefrogs
- [x] 5. Frogspawn capture: vanilla glass bottle (no custom tool); adult frogs move via leads
- [x] 6. Loader scope confirmed: NeoForge-only, forever (no Fabric port)
- [x] 7. Frog breeding: same-category → Primed Frog Egg of that category (slimes do NOT breed)
- [x] 8. Frog AI: vanilla unchanged except category-match prey filter
- [x] 9. Player direct-feeding: yes (right-click frog with bucketed slime, category-match required)
- [x] 10. Resource Slime direct-kill drops: slimeballs only (vanilla parity)
- [x] 11. Primed Frog Egg hatching conditions: vanilla frogspawn rules (water-adjacent, vanilla timer)
- [x] 12. Subsumed by Q7 (Resource Frog breeding produces category-matching offspring)
