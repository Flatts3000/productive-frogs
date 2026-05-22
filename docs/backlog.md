# Backlog

Work that's been **deferred from a PR**, **planned in design docs but not yet shipped**, or **flagged as polish**. Grouped by milestone with the design source linked where one exists.

The numbered checklist in [versioning.md](./versioning.md) is the **canonical V1 scope**; this doc is the implementation backlog tracking which V1 items are done vs outstanding, plus the polish/post-V1 work picked up along the way.

## V1 — remaining work

What's left to land before "playable foundation + appliances" is complete. Each item is a PR-sized slice.

### Slime sourcing — non-vanilla categories
- ~~**Cave Slime entity**~~ — shipped in PR I1 (MINERAL).
- ~~**Geode Slime entity**~~ — shipped in PR I2 (GEM).
- ~~**Tide Slime entity**~~ — shipped in PR I3 (AQUATIC).
- ~~**Void Slime entity**~~ — shipped in PR I4 (ARCANE).
- ~~**Natural spawn rules for parent species**~~ — shipped. `SpawnPlacements` registered via `RegisterSpawnPlacementsEvent` in `PFModBusEvents`; biome-conditioned spawning gated by four `neoforge:add_spawns` BiomeModifier JSONs at `data/productivefrogs/neoforge/biome_modifier/`. Cave→dripstone_caves+deep_dark, Geode→stony/jagged/frozen peaks, Tide→deep/lukewarm/warm oceans (OCEAN_FLOOR heightmap so they land on the sea floor like Drowned), Void→small_end_islands. Spawn check is a Mob-typed mirror of `Monster.checkMonsterSpawnRules` (peaceful=no, dark enough, vanilla mob position checks).

### ~~SlimeVariant data registry (per-resource sub-categorization)~~ — shipped
Datapack registry at `PFRegistries.SLIME_VARIANT` (created via `DataPackRegistryEvent.NewRegistry` in `PFDataPackRegistryEvents`). Entries live at `data/<ns>/productivefrogs/slime_variant/<name>.json`, codec-decoded by `SlimeVariant`. 12 variants ship (iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, magma_cream, ender_pearl). Lookups via `SlimeVariant.findByPrimerItem` (infusion) and `SlimeVariant.pickWeighted` (split discovery). `ResourceSlime` carries the variant as a SynchedEntityData id and resyncs category from the registry on load.

### ~~Configurable Froglight item~~ — shipped
`PFItems.CONFIGURABLE_FROGLIGHT` is registered with the `slime_variant` data component. Tint resolves via the `SlimeVariantTint` `ItemTintSource`. `FrogTongueDropHandler` emits the configurable Froglight stamped with the variant when the consumed slime carries a SlimeVariant, falling back to the category Froglight otherwise. Covered by the `variant_slime_kill_drops_configurable_froglight` GameTest.

### Slime Milker block + Slime Milk fluid (V1 production keystone)
[docs/farming.md](./farming.md) specifies the full design. Sub-PR plan (J-series):

- **J1** ✅ — fluid framework (PFFluidTypes / PFFluids / BaseFlowingFluid plumbing) + iron_slime_milk variant + iron_slime_milk_bucket. Placeable fluid; no milker block or spawning yet. Placeholder iron-grey textures (real animated textures backlogged under polish).
- **J2** ✅ — expanded to all 14 variants (iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, magma_cream, ender_pearl, vanilla, magma). Refactored PFFluidTypes/PFFluids/PFBlocks/PFItems to `Map<String, ...>` keyed by variant name; `PFFluidTypes.VARIANTS` is the single source of truth list. Tests parameterized so adding future variants auto-extends coverage.
- **J3** ✅ — `SlimeMilkerBlock`: single appliance, no power, right-click with a Slime Bucket → consume slime, output typed milk bucket matching slime's variant. Fails closed (CONSUME, player keeps bucket) when the bucket has no Variant tag or the variant isn't in `PFFluidTypes.VARIANTS`. Covered by `SlimeMilkerBlockTest` (parser edge cases) + `slime_milker_converts_iron_slime_bucket_into_iron_milk_bucket` GameTest (in-world variant-pipeline + block placement).
- **J4** ✅ — Source-block slime spawning. `SlimeMilkSourceBlock extends LiquidBlock` overrides `onPlace` + `tick` to drive a self-scheduling block-tick loop independent of the fluid's flow-tick. Cadence uniform [200, 600] ticks (10–30s) per spawn. Spawn-position picker scans all 26 blocks in the 3×3×3 cube around the source (rim cardinals first, then diagonals, then below plane, then above) and lands the slime on top of the first sturdy neighbour. If no sturdy neighbour exists, falls back to spawning inside the source (the milk fluid is non-collision). Entity overlap is allowed; spawns never fail. Variant mapping: `"vanilla"` → vanilla Slime, `"magma"` → vanilla MagmaCube, other 12 → `ResourceSlime` with the matching SlimeVariant. Covered by 5 GameTests (iron rim spawn, copper no-solid-anywhere fallback, gold solid-floor-below pick, vanilla-slime mapping, magma-cube mapping).
- **J5** ✅ — Depletion counter + mod config wiring. `SlimeMilkSourceBlock` carries a `spawns_remaining` IntegerProperty (range [0, 16]); decrements on each successful spawn, drains to air on zero. New `PFConfig` (COMMON `ModConfigSpec`) exposes `depletionEnabled`, `depletionCount`, `minSpawnIntervalTicks`, `maxSpawnIntervalTicks`, `discoveryChancePerOffspring`. `SlimeSplitDiscoveryHandler.discoveryChancePerOffspring` promoted from public-static hack to a config read with a test-only override field (`testOverride`) that GameTests set in try/finally. Covered by 3 GameTests (decrement, drain on zero, default-state-is-max).

### Smelting + crush recipes
- ✅ Froglight → base resource smelt recipes. 18 recipes shipped: 6 broad-strokes category Froglights (each → canonical resource: iron, redstone, diamond, prismarine_shard, magma_cream, ender_pearl) plus 12 variant configurable_froglight recipes that match via NeoForge's `neoforge:components` ingredient on the `slime_variant` data component. Covered by 3 GameTests (category lookup × 6, variant lookup × 12, negative case for unstamped configurable_froglight).
- ✅ Crush tag `productivefrogs:crushable/metallic` ships with `metallic_froglight` as its only entry. Tag is reserved for V2; cross-mod crush recipes themselves are V2 scope (see V2 section).

### ~~Player direct-feeding (Q9)~~ — shipped
[docs/open_questions.md#9](./open_questions.md). `ResourceFrog.mobInteract` checks for a Slime Bucket, reads the bucket's stored Category + Variant via `ResourceTadpoleBucketItem.readCategory` / `readVariant`, and if the category matches the frog's, calls `FrogTongueDropHandler.dropFroglightAtFrog` to emit the matching Froglight (variant-stamped configurable_froglight when the bucket carried a Variant, broad-strokes category Froglight otherwise) and replaces the held bucket with vanilla `Items.BUCKET`. Mismatch falls through to `super.mobInteract` (vanilla `Frog.mobInteract` → `Animal.mobInteract` — slimeballs / name-tag still work). Covered by 3 GameTests (matching no-variant, matching iron-variant, mismatched category).

### ~~Mod config wiring~~ — landed alongside J5
- ✅ `discoveryChancePerOffspring` promoted to `PFConfig.DISCOVERY_CHANCE_PER_OFFSPRING` (GameTests override via `SlimeSplitDiscoveryHandler.testOverride`).
- ✅ Milk source depletion count: `PFConfig.DEPLETION_COUNT` + `PFConfig.DEPLETION_ENABLED`.
- ✅ Milk spawn interval: `PFConfig.MIN_SPAWN_INTERVAL_TICKS` + `PFConfig.MAX_SPAWN_INTERVAL_TICKS`.
- ~~Per-parent-species default categories (config or datapack)~~ — shipped. New `productivefrogs:parent_species` datapack registry; entries at `data/<ns>/productivefrogs/parent_species/<name>.json` with shape `{ "entity_type": "...", "category": "..." }`. Six defaults ship (vanilla `Slime`/`MagmaCube` + the four PF parent species). `SlimeSplitDiscoveryHandler.categoryForParent` now does an EntityType-id lookup against the registry instead of an `instanceof` chain — drops the subclass-ordering footgun and lets modpacks wire modded slime mobs into the discovery loop. Pinned by `ParentSpeciesEntryTest` (codec round-trips + error paths) and the new `parent_species_datapack_registry_loads_six_defaults` GameTest.

## V2 — automation

Out of scope until V1 ships. [docs/versioning.md#v2--automation](./versioning.md) is canonical.

- Auto-fed Slime Milker (hopper-integrated)
- Frog Terrarium / Habitat block (placeable frog housing with I/O inventory)
- Auto-feeders (hopper-fed slime delivery)
- Capacity / efficiency upgrades for habitat blocks
- Native crusher block (in-house 2× path so we don't depend on Create/Mekanism/Thermal)
- Cross-mod crush 2× recipes for metallic Froglights via Create / Mekanism / Thermal — conditional `mod_loaded` JSON recipes. The `productivefrogs:crushable/metallic` tag is already reserved in V1; the recipes wait on a multi-mod test environment.
- Pipe/hopper-aware fluid handling for Slime Milk
- FE / NeoForge Energy compat (optional)

## Polish / debt — non-blocking, do when convenient

Items noted in commit messages or PR descriptions as known issues but not blocking.

### Code hygiene
- ~~**`ResourceFrog#brainProvider` duplicates vanilla constants.**~~ — resolved. Two ATs added to `META-INF/accesstransformer.cfg` (`Frog.SENSOR_TYPES` and `Frog.MEMORY_TYPES`); `brainProvider` is now a one-line stream substitution that maps the vanilla list, replacing `FROG_ATTACKABLES` with our category-filtered variant. New sensors Mojang adds to the frog brain are inherited automatically — no maintenance burden tracking vanilla churn.
- ~~**`SlimeSplitDiscoveryHandler.discoveryChancePerOffspring` is `public static`.**~~ — resolved in J5. Now backed by `PFConfig.DISCOVERY_CHANCE_PER_OFFSPRING` with a separate `testOverride` field for GameTest force-conversion.

### Tests
- **GameTest for frog kill via actual tongue path.** `matching_frog_kill_drops_category_froglight` uses `hurtServer(level, source, 999.0F)` which bypasses the frog's `ATTACK_DAMAGE` attribute. PR #27 caught a damage=0 regression that this test couldn't see. A more realistic test would let the tongue actually drive the kill.
- **GameTest for `loadFromBucketTag` from a fresh-spawned slime.** Currently we only call save→read on the bucket. PR #22's strengthening covered the slime bucket; verify the tadpole bucket has equivalent coverage.
- ~~**Visual rendering verification.**~~ — resolved. `docs/testing.md` now carries a "What GameTest does NOT cover — visuals are blind" subsection enumerating the client-side surfaces GameTest can't see (tints, render types, textures, UV/transforms, particles, GUI, lang fallbacks), with the PR #27 outer-shell-gray bug as the canonical example. The bottom "manual playtesting" section also expands into an explicit minimum-checklist for visual-touching PRs (inventory + dropped + in-world tint check, render-type sanity, lang-fallback check, JEI search). Authors of `client/` / `assets/` / `Category.tintArgb` / model-JSON / lang changes are pointed at the playtest path with screenshot capture for the PR description.
- ~~**`frog_tongue_targets_only_matching_category_slime` is flaky.**~~ — resolved. The original fixed-tick assertion raced against the two-sensor chain (`NEAREST_LIVING_ENTITIES` → `RESOURCE_FROG_ATTACKABLES`), each with a random `[0, scanRate)` first-scan offset. Switched to a polling pattern in `onEachTick`: succeed once `NEAREST_ATTACKABLE` holds the matching slime for a stability window of consecutive ticks, fail the moment the off-category slime appears, with a delayed-fallback assertion near the test timeout that fails with the last observed memory state if the sensor never settles. 5/5 green on local repeat runs.

### Visuals / assets
- **Real (non-placeholder) textures** for every item/block. Currently using vanilla textures + per-category tints + placeholder PNGs (tadpole_silhouette, slime_silhouette).
- ~~**Spawn eggs for ResourceFrog / ResourceSlime / ResourceTadpole.**~~ — shipped. 6 frog + 6 tadpole + 12 variant slime + 4 parent-species (Cave / Geode / Tide / Void) spawn eggs are registered in `PFItems` and exposed via `PFCreativeTabs.PRODUCTIVE_FROGS_TAB`. Variant slime eggs carry the `slime_variant` data component so each renders its resource colour rather than the broader category tint; pinned by `PFRegistryTest#variantSlimeSpawnEggCarriesSlimeVariantComponent` (12 parameterized cases).
- ~~**Per-category SlimeOuterLayer tweaks.**~~ — shipped. `Category.shellTintArgb()` returns a desaturated tinted gray (70% light gray + 30% category colour); `ResourceSlimeRenderer.extractRenderState` writes that value into `ResourceSlimeRenderState.outerTint` for variant-less Resource Slimes. AQUATIC slimes now read cooler-gray, INFERNAL warmer-gray, etc., without going full red/orange/cyan. Variant-locked slimes still use the full-saturation `variant.primary_color` (the more specific match). Pinned by `CategoryTest#shellTintArgb*` (opacity, math, per-category distinctness).

### Tooling
- **Jade auto-install.** Currently a manual drop into `run/mods/` per [docs/dev_setup.md](./dev_setup.md). If Jade ever publishes to a maven we use cleanly, swap to a `runtimeOnly` dep like JEI.

## Architecture lessons captured

From the Productive Bees survey ([productive_bees_analysis.md](./productive_bees_analysis.md)) and our PR-review history:

- **Use Codec/MapCodec records from day one** for any JSON-driven data. Productive Bees' hand-rolled JSON→CompoundTag conversion is their biggest tech debt.
- **Keep gameplay-critical logic in Java**, JSON for cosmetics and variant data only. Productive Bees' attempt to make produce-recipes fully data-driven ended in hardcoded `if/else` chains in `BeeHelper`.
- **Don't load-bear the entity class on soft deps.** Their GeckoLib-based class swap creates saves that can't load without the optional mod installed.
- **Stick with our `Category` enum** instead of a `ResourceLocation` string for the entity's type field — the enum prevents the "self-destruct unconfigured entity" band-aid Productive Bees needs.
- **Texture alpha is part of the data, not just the visual.** PR #27's outer-shell-gray bug was source-alpha 255 where vanilla uses 180. The render type respects source alpha; the file is the source of truth.
- **Pin display-name lang entries early.** PR #25 added them after the fact when playtest pain forced it; should have been part of every item-registration PR from day one.

## How to use this doc

When a PR adds a TODO or notes a deferred item, add a one-line entry here under the right section. When a PR closes one, delete the entry (git history is the trail; the doc tracks the *future*, not the past).

For larger units of work (e.g., "Slime Milker"), the entry here is a stub; the design lives in `docs/farming.md` or wherever and the implementation breakdown lives in the PR description when work starts.
