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

### ~~Configurable Froglight placeable block (3D variant Froglight)~~ — shipped
Promoted `configurable_froglight` from a plain Item to a `BlockItem` backed by `ConfigurableFroglightBlock` (`RotatedPillarBlock` + `EntityBlock`) and `ConfigurableFroglightBlockEntity`. The BE stores the `SLIME_VARIANT` Identifier (written by `ConfigurableFroglightItem#updateCustomBlockEntityTag` on placement; exposed via `collectImplicitComponents` for pick-block and the `copy_components(source=block_entity)` loot function). Block break + lighting mechanics match vanilla froglight identically — `strength(0.3F)`, `lightLevel(state -> 15)`, `sound(SoundType.FROGLIGHT)`, `survives_explosion` loot, default `NORMAL` push reaction. Per-variant tint resolves at render time via a `BlockColor` reading the BE → `SlimeVariant#primaryColor` from the datapack registry. Round-trip pinned by the `variant_froglight_round_trip_preserves_variant_through_place_and_break` GameTest.

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

## V1.0.x — port to MC 1.21.1 / NeoForge 21.1.230 (blocks everything else)

Sky Frogs is locked to 1.21.1; PF must rebuild on 1.21.1 to ship in the pack. This is a port, not a version bump — ~10 minor versions of API changes to revert. Full design in [port_mc_1_21_1.md](./port_mc_1_21_1.md). Estimated 3-4 weeks across 11 phases on a long-lived branch:

- ☐ **Phase 0** — Branch + gradle setup (gradle.properties, neoforge.mods.toml version ranges, moddev plugin version check).
- ☐ **Phase 1** — `Identifier` → `ResourceLocation` mechanical sweep (33 files).
- ☐ **Phase 2** — BE save/load: revert `ValueInput`/`ValueOutput` → `CompoundTag` (5 BlockEntities).
- ☐ **Phase 3** — NeoForge resource handler API revert: `ItemStacksResourceHandler` → `IItemHandler` / `ItemStackHandler` (3 files).
- ☐ **Phase 4** — GameTest annotation revert: `@GameTestHolder` + `@GameTest`, remove the `DeferredRegister<Consumer<GameTestHelper>>` scaffolding.
- ☐ **Phase 5** — Tint pipeline revert: delete `assets/.../items/*.json` (~70 files), delete 3 `ItemTintSource` classes, register `ItemColor` lambdas via `RegisterColorHandlersEvent.Item`.
- ☐ **Phase 6** — Data path renames: `tags/item/`→`tags/items/`, `tags/entity_type/`→`tags/entity_types/`, `structure/`→`structures/`, `loot_table/`→`loot_tables/`, `recipe/`→`recipes/`; bump `pack.mcmeta` `pack_format` to 48.
- ☐ **Phase 7** — Item / Block registration shape revert (`registerItem(name, Function, props)` → `register(name, () -> new Item(props))`).
- ☐ **Phase 8** — `DataPackRegistryEvent.NewRegistry` + codec adjustments for SlimeVariant / ParentSpecies registries.
- ☐ **Phase 9** — Biome modifier JSON adjustments (4 files at `data/.../neoforge/biome_modifier/`).
- ☐ **Phase 10** — Final compile sweep + full manual playtest (creative-tab, milker pipeline, all 12 variants).
- ☐ **Phase 11** — Merge to main; update `CLAUDE.md` + `docs/dev_setup.md` + `docs/architecture.md` to reflect new versions.

## V1.0.x — data-driven variant architecture refactor (V1.1 prerequisite, post-port)

Removes per-variant Java hardcoding so modpacks can add a SlimeVariant by JSON only. Full design in [refactor_data_driven_variants.md](./refactor_data_driven_variants.md). Ship in 4 PRs:

- ☐ **Phase 1 — Spawn eggs collapse + lang derivation**: single `resource_slime_spawn_egg` with `SLIME_VARIANT` component; template lang entries with title-cased fallback. Deprecated aliases for old per-variant spawn egg items (migration safety).
- ☐ **Phase 2 — Milk fluid auto-registration**: scan loaded mods for `slime_variant/*.json` at mod init, register fluid pipeline dynamically. Removes `PFFluidTypes.VARIANTS` hardcoded list. Carries the most risk (mod-resource scanning at mod init is non-standard).
- ☐ **Phase 3 — Asset auto-generation from variant JSONs**: extend `generate_variant_slime_textures.ps1` + `generate_slime_milk_textures.ps1` to scan variant JSONs and use a new optional `texture_source_block` field. Document the modpack workflow in `docs/modpack_adding_variants.md`.
- ☐ **Phase 4 — Validation with a test external variant**: ship a brand-new variant via JSON-only and verify the full production loop end-to-end.

## V1.1 — vanilla resource coverage

After V1.0.x refactor lands: add every vanilla item fitting cleanly into one of the existing 6 categories. JSON-only authoring post-refactor. Full design in [v1_1_scope.md](./v1_1_scope.md). 16 new variants → 28 total post-V1.1.

Per-variant work for each = 4 files: variant JSON + (optional) hand-authored inner-cube PNG OR `texture_source_block` field + primer tag entry + smelting recipe. Existing GameTests auto-cover.

### Locked scope (16 variants)

**METALLIC (+1)**
- ☐ `netherite_scrap`

**MINERAL (+2)**
- ☐ `gunpowder`
- ☐ `clay_ball` *(non-1:1 smelt — Froglight smelts to `brick`)*

**GEM (+2)**
- ☐ `quartz`
- ☐ `amethyst`

**AQUATIC (+2)**
- ☐ `ink_sac`
- ☐ `glow_ink_sac`

**INFERNAL (+6)**
- ☐ `blaze`
- ☐ `glowstone_dust` *(moved from MINERAL — Nether-source classification)*
- ☐ `soul_sand`
- ☐ `soul_soil`
- ☐ `obsidian`
- ☐ `netherrack`

**ARCANE (+3)**
- ☐ `echo_shard`
- ☐ `chorus_fruit` *(non-1:1 smelt — Froglight smelts to `popped_chorus_fruit`)*
- ☐ `shulker_shell`

### Tier B — design open (5 candidates)
- ☐ `prismarine_crystals` (AQUATIC) — default if undecided: ship alongside existing `prismarine` (additive, no rename).
- ☐ `nautilus_shell` (AQUATIC) — default if undecided: defer to V1.2+ (production-loop framing weak — drowned/fishing only in vanilla).
- ☐ `ghast_tear` (INFERNAL) — default if undecided: defer to V1.2+ (single rare mob drop, rarity-break concern).
- ☐ `wither_rose` (INFERNAL) — default if undecided: skip slime variant, primer tag entry only (flower, not a resource).
- ☐ `end_stone` (ARCANE) — default if undecided: skip slime variant, primer tag entry only (bulk block).

## V1.2 — new category for biological mob drops

Adds a 7th category covering vanilla items harvested from living/undead mobs. Requires a Java edit (new `Category` enum constant + tint + tags) — that's why it's V1.2, not V1.1. Full design at V1.2 kickoff time.

### Variants (5)
- ☐ `bone` (skeleton)
- ☐ `rotten_flesh` (zombie)
- ☐ `string` (spider)
- ☐ `leather` (cow / horse)
- ☐ `feather` (chicken)

### Open design questions
- ☐ Category name: BESTIAL / MORTAL / VISCERAL / FAUNA / CARNAL — or split into UNDEAD + BESTIAL?
- ☐ Category ARGB tint (`Category.tintArgb()`).
- ☐ New parent slime species for the category (parallel to Cave/Geode/Tide/Void Slime).
- ☐ Biome modifier JSONs for natural spawn rules (parallel to existing `data/productivefrogs/neoforge/biome_modifier/`).
- ☐ Outer-shell tint for the new parent species (per the PR #77 `TintedSlimeOuterLayer` pattern).

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
- ~~**GameTest for frog kill via actual tongue path.**~~ — resolved. New `frog_tongue_ai_path_drops_category_froglight` lets the frog's AI itself drive target selection, tongue extend, and damage dispatch (no `hurtServer` shortcut), so the test fails closed if any link breaks: sensor wiring, vanilla `FrogEat` behavior, the `Attributes.ATTACK_DAMAGE` value, or the `LivingDeathEvent` drop emitter. The original manual-damage test (`matching_frog_kill_drops_category_froglight`) is kept as a fast sanity-check of the death-event handler in isolation. 5/5 green on local repeat runs at the registered 400-tick timeout.
- ~~**GameTest for `loadFromBucketTag` from a fresh-spawned tadpole.**~~ — resolved. `tadpole_bucket_round_trip_preserves_category` now exercises the full save→read→`loadFromBucketTag`→assert path, matching `slime_bucket_round_trip_preserves_variant`'s shape from PR #22. A fresh ResourceTadpole spawned with METALLIC is released via `loadFromBucketTag` from an INFERNAL-stamped bucket and must end up INFERNAL — silent no-ops would fail loudly.
- ~~**Visual rendering verification.**~~ — resolved. `docs/testing.md` now carries a "What GameTest does NOT cover — visuals are blind" subsection enumerating the client-side surfaces GameTest can't see (tints, render types, textures, UV/transforms, particles, GUI, lang fallbacks), with the PR #27 outer-shell-gray bug as the canonical example. The bottom "manual playtesting" section also expands into an explicit minimum-checklist for visual-touching PRs (inventory + dropped + in-world tint check, render-type sanity, lang-fallback check, JEI search). Authors of `client/` / `assets/` / `Category.tintArgb` / model-JSON / lang changes are pointed at the playtest path with screenshot capture for the PR description.
- ~~**`frog_tongue_targets_only_matching_category_slime` is flaky.**~~ — resolved. The original fixed-tick assertion raced against the two-sensor chain (`NEAREST_LIVING_ENTITIES` → `RESOURCE_FROG_ATTACKABLES`), each with a random `[0, scanRate)` first-scan offset. Switched to a polling pattern in `onEachTick`: succeed once `NEAREST_ATTACKABLE` holds the matching slime for a stability window of consecutive ticks, fail the moment the off-category slime appears, with a delayed-fallback assertion near the test timeout that fails with the last observed memory state if the sensor never settles. 5/5 green on local repeat runs.

### Visuals / assets
- **Real (non-placeholder) textures** for every item/block. Status by surface:
  - ✅ **Slime Milk buckets** (14 variants) — generated by `scripts/generate_slime_milk_textures.ps1`, which tints vanilla milk_bucket pixels per variant `primary_color`. Bucket metal stays gray; only the milk surface gets the hue swap. Re-run the script after any variant primary_color change.
  - ✅ **Slime Milk still + flow** (14 variants × 2) — same script; tints the top frame of vanilla water_still/water_flow per variant. Single-frame for V1; animated multi-frame strip + .mcmeta deferred to polish.
  - ✅ **Slime Milker block faces** — shipped (top / side / bottom / front + working variants for top/side/front). Generated by `scripts/generate_slime_milker_faces.ps1`.
  - ✅ **Slime Milker GUI** (`textures/gui/container/slime_milker.png`) — shipped. Vanilla furnace composite with fuel slot painted out, input slot relocated to vertically centre with the output (PR #78), and the burn_progress arrow re-inlined at (176, 14) so the partial-blit animation works (PR #74).
  - ✅ **Parent slime entities** (Cave / Geode / Tide / Void) — inner-cube textures shipped + per-species outer-shell tint shipped in PR #77 (stone grey / diamond cyan / water blue / end-portal purple).
  - ✅ **`tadpole_silhouette.png` / `slime_silhouette.png`** — shipped as small head sprites peeking from the bucket mouth (PR #76).
- ~~**Spawn eggs for ResourceFrog / ResourceSlime / ResourceTadpole.**~~ — shipped. 6 frog + 6 tadpole + 12 variant slime + 4 parent-species (Cave / Geode / Tide / Void) spawn eggs are registered in `PFItems` and exposed via `PFCreativeTabs.PRODUCTIVE_FROGS_TAB`. Variant slime eggs carry the `slime_variant` data component so each renders its resource colour rather than the broader category tint; pinned by `PFRegistryTest#variantSlimeSpawnEggCarriesSlimeVariantComponent` (12 parameterized cases).
- ~~**Per-category SlimeOuterLayer tweaks.**~~ — shipped. `Category.shellTintArgb()` returns a desaturated tinted gray (70% light gray + 30% category colour); `ResourceSlimeRenderer.extractRenderState` writes that value into `ResourceSlimeRenderState.outerTint` for variant-less Resource Slimes. AQUATIC slimes now read cooler-gray, INFERNAL warmer-gray, etc., without going full red/orange/cyan. Variant-locked slimes still use the full-saturation `variant.primary_color` (the more specific match). Pinned by `CategoryTest#shellTintArgb*` (opacity, math, per-category distinctness).

### Tooling
- ✅ **Jade.** Installed manually per [docs/dev_setup.md](./dev_setup.md); good enough for V1 dev. If Jade ever publishes to a maven we use cleanly, swap to a `runtimeOnly` dep like JEI — kept as a tracked nice-to-have, not a blocker.

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
