# Backlog

Work that's been **deferred from a PR**, **planned in design docs but not yet shipped**, or **flagged as polish**. Grouped by milestone with the design source linked where one exists.

The numbered checklist in [versioning.md](./versioning.md) is the **canonical V1 scope**; this doc is the implementation backlog tracking which V1 items are done vs outstanding, plus the polish/post-V1 work picked up along the way.

## ~~V1 - remaining work~~ - COMPLETE

All V1 scope ("playable foundation + appliances") has shipped. The entries below are retained as a record of what landed and where; the only outstanding follow-up is cross-mod crush recipes (see Known Issues + the V2 section). New work starts from the V1.2+ sections.

### Slime sourcing - non-vanilla categories
- ~~**Cave Slime entity**~~ - shipped in PR I1 (CAVE).
- ~~**Geode Slime entity**~~ - shipped in PR I2 (GEODE).
- ~~**Tide Slime entity**~~ - shipped in PR I3 (TIDE).
- ~~**Void Slime entity**~~ - shipped in PR I4 (VOID).
- ~~**Natural spawn rules for parent species**~~ - shipped. `SpawnPlacements` registered via `RegisterSpawnPlacementsEvent` in `PFModBusEvents`; biome-conditioned spawning gated by four `neoforge:add_spawns` BiomeModifier JSONs at `data/productivefrogs/neoforge/biome_modifier/`. Cave→dripstone_caves+deep_dark, Geode→stony/jagged/frozen peaks, Tide→deep/lukewarm/warm oceans (OCEAN_FLOOR heightmap so they land on the sea floor like Drowned), Void→small_end_islands. Spawn check is a Mob-typed mirror of `Monster.checkMonsterSpawnRules` (peaceful=no, dark enough, vanilla mob position checks).

### ~~SlimeVariant data registry (per-resource sub-categorization)~~ - shipped
Datapack registry at `PFRegistries.SLIME_VARIANT` (created via `DataPackRegistryEvent.NewRegistry` in `PFDataPackRegistryEvents`). Entries live at `data/<ns>/productivefrogs/slime_variant/<name>.json`, codec-decoded by `SlimeVariant`. 12 variants ship (iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, magma_cream, ender_pearl). Lookups via `SlimeVariant.findByPrimer` (infusion, item-or-tag; `findByPrimerItem` is the legacy exact-only path) and `SlimeVariant.pickWeighted` (split discovery). `ResourceSlime` carries the variant as a SynchedEntityData id and resyncs category from the registry on load.

### ~~Configurable Froglight item~~ - shipped
`PFItems.CONFIGURABLE_FROGLIGHT` is registered with the `slime_variant` data component. Tint resolves via an `ItemColor` lambda registered in `RegisterColorHandlersEvent.Item` (see `client/PFClientEvents.java`, the 1.21.1 form; the JSON `ItemTintSource` pipeline does not exist on 1.21.1). `FrogTongueDropHandler` emits the configurable Froglight stamped with the variant when the consumed slime carries a SlimeVariant, falling back to the category Froglight otherwise. Covered by the `variant_slime_kill_drops_configurable_froglight` GameTest.

### ~~Configurable Froglight placeable block (3D variant Froglight)~~ - shipped
Promoted `configurable_froglight` from a plain Item to a `BlockItem` backed by `ConfigurableFroglightBlock` (`RotatedPillarBlock` + `EntityBlock`) and `ConfigurableFroglightBlockEntity`. The BE stores the `SLIME_VARIANT` Identifier (written by `ConfigurableFroglightItem#updateCustomBlockEntityTag` on placement; exposed via `collectImplicitComponents` for pick-block and the `copy_components(source=block_entity)` loot function). Block break + lighting mechanics match vanilla froglight identically - `strength(0.3F)`, `lightLevel(state -> 15)`, `sound(SoundType.FROGLIGHT)`, `survives_explosion` loot, default `NORMAL` push reaction. Per-variant tint resolves at render time via a `BlockColor` reading the BE → `SlimeVariant#primaryColor` from the datapack registry. Round-trip pinned by the `variant_froglight_round_trip_preserves_variant_through_place_and_break` GameTest.

### Slime Milker block + Slime Milk fluid (V1 production keystone)
[docs/farming.md](./farming.md) specifies the full design. Sub-PR plan (J-series):

- **J1** ✅ - fluid framework (PFFluidTypes / PFFluids / BaseFlowingFluid plumbing) + iron_slime_milk variant + iron_slime_milk_bucket. Placeable fluid; no milker block or spawning yet. Placeholder iron-grey textures (real animated textures backlogged under polish).
- **J2** ✅ - expanded to all 14 variants (iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, magma_cream, ender_pearl, vanilla, magma). Refactored PFFluidTypes/PFFluids/PFBlocks/PFItems to `Map<String, ...>` keyed by variant name; `PFFluidTypes.VARIANTS` is the single source of truth list. Tests parameterized so adding future variants auto-extends coverage.
- **J3** ✅ - `SlimeMilkerBlock`: single appliance, no power, right-click with a Slime Bucket → consume slime, output typed milk bucket matching slime's variant. Fails closed (CONSUME, player keeps bucket) when the bucket has no Variant tag or the variant isn't in `PFFluidTypes.VARIANTS`. Covered by `SlimeMilkerBlockTest` (parser edge cases) + `slime_milker_converts_iron_slime_bucket_into_iron_milk_bucket` GameTest (in-world variant-pipeline + block placement).
- **J4** ✅ - Source-block slime spawning. `SlimeMilkSourceBlock extends LiquidBlock` overrides `onPlace` + `tick` to drive a self-scheduling block-tick loop independent of the fluid's flow-tick. Cadence uniform [200, 600] ticks (10-30s) per spawn. Spawn-position picker scans all 26 blocks in the 3×3×3 cube around the source (rim cardinals first, then diagonals, then below plane, then above) and lands the slime on top of the first sturdy neighbour. If no sturdy neighbour exists, falls back to spawning inside the source (the milk fluid is non-collision). Entity overlap is allowed; spawns never fail. Variant mapping: `"vanilla"` → vanilla Slime, `"magma"` → vanilla MagmaCube, other 12 → `ResourceSlime` with the matching SlimeVariant. Covered by 5 GameTests (iron rim spawn, copper no-solid-anywhere fallback, gold solid-floor-below pick, vanilla-slime mapping, magma-cube mapping).
- **J5** ✅ - Depletion counter + mod config wiring. `SlimeMilkSourceBlock` carries a `spawns_remaining` IntegerProperty (range [0, 16]); decrements on each successful spawn, drains to air on zero. New `PFConfig` (COMMON `ModConfigSpec`) exposes `depletionEnabled`, `depletionCount`, `minSpawnIntervalTicks`, `maxSpawnIntervalTicks`, `discoveryChancePerOffspring`. `SlimeSplitDiscoveryHandler.discoveryChancePerOffspring` promoted from public-static hack to a config read with a test-only override field (`testOverride`) that GameTests set in try/finally. Covered by 3 GameTests (decrement, drain on zero, default-state-is-max).

### Smelting + crush recipes
- ✅ Froglight → base-resource smelt recipes. Shipped for every category Froglight (`<category>_froglight` → that category's canonical resource) plus every variant `configurable_froglight` (matched via NeoForge's `neoforge:components` ingredient on the `slime_variant` data component). Covered by GameTests for category lookup, variant lookup, and the unstamped-froglight negative case.
- ☐ Crush recipes (Mekanism / Immersive Engineering / EnderIO, + AllTheOres dust layer) - NOT yet shipped; the current next-up task (v1.3). Per-variant `neoforge:components` recipes (no `crushable` tag - all variants are one item distinguished by component). Each outputs 2x the crusher's own dust; the mod's own (or ATO's tag-driven) dust→ingot smelt closes the loop, so PF ships no smelt-back. Create + Actually Additions deferred (no native dust); Just Dire Things (no crusher) and Thermal (no 1.21.1) ruled out. Build spec: [v1_3_crush_recipes.md](./v1_3_crush_recipes.md); cross-mod rationale: [cross_mod_compat.md](./cross_mod_compat.md).

### ~~Player direct-feeding (Q9)~~ - shipped
[docs/open_questions.md#9](./open_questions.md). `ResourceFrog.mobInteract` checks for a Slime Bucket, reads the bucket's stored Category + Variant via `ResourceTadpoleBucketItem.readCategory` / `readVariant`, and if the category matches the frog's, calls `FrogTongueDropHandler.dropFroglightAtFrog` to emit the matching Froglight (variant-stamped configurable_froglight when the bucket carried a Variant, broad-strokes category Froglight otherwise) and replaces the held bucket with vanilla `Items.BUCKET`. Mismatch falls through to `super.mobInteract` (vanilla `Frog.mobInteract` → `Animal.mobInteract` - slimeballs / name-tag still work). Covered by 3 GameTests (matching no-variant, matching iron-variant, mismatched category).

### ~~Mod config wiring~~ - landed alongside J5
- ✅ `discoveryChancePerOffspring` promoted to `PFConfig.DISCOVERY_CHANCE_PER_OFFSPRING` (GameTests override via `SlimeSplitDiscoveryHandler.testOverride`).
- ✅ Milk source depletion count: `PFConfig.DEPLETION_COUNT` + `PFConfig.DEPLETION_ENABLED`.
- ✅ Milk spawn interval: `PFConfig.MIN_SPAWN_INTERVAL_TICKS` + `PFConfig.MAX_SPAWN_INTERVAL_TICKS`.
- ~~Per-parent-species default categories (config or datapack)~~ - shipped. New `productivefrogs:parent_species` datapack registry; entries at `data/<ns>/productivefrogs/parent_species/<name>.json` with shape `{ "entity_type": "...", "category": "..." }`. Six defaults ship (vanilla `Slime`/`MagmaCube` + the four PF parent species). `SlimeSplitDiscoveryHandler.categoryForParent` now does an EntityType-id lookup against the registry instead of an `instanceof` chain - drops the subclass-ordering footgun and lets modpacks wire modded slime mobs into the discovery loop. Pinned by `ParentSpeciesEntryTest` (codec round-trips + error paths) and the new `parent_species_datapack_registry_loads_six_defaults` GameTest.

## ~~V1.0.x - port to MC 1.21.1 / NeoForge 21.1.230~~ - DONE

Productive Frogs was rebuilt from 1.21.11 down onto **MC 1.21.1 / NeoForge 21.1.230** to match Sky Frogs (PR #80 + #81). `gradle.properties` now pins 1.21.1 / 21.1.230, and v1.0.0 through v1.2.0 all shipped on the ported codebase. Full design is preserved in [port_mc_1_21_1.md](./port_mc_1_21_1.md).

The **V1.5 species-as-category redesign** ([species_as_category_redesign.md](./species_as_category_redesign.md)) rode in on the same branch: the `Category` enum is now CAVE / GEODE / BOG / TIDE / INFERNAL / VOID (named for the parent species, not abstract categories). The 1.21.x singular datapack dirs (`recipe/`, `loot_table/`, `structure/`, `tags/item/`) were already correct for 1.21.1, so the planned plural-rename phase was a no-op.

## ~~Data-driven variant architecture refactor~~ - DONE

Removed per-variant Java hardcoding so a modpack adds a SlimeVariant by datapack
JSON only (no Java, no recompile, no per-variant assets/lang). Full design +
decision history in [refactor_data_driven_variants.md](./refactor_data_driven_variants.md).

- ☑ **Spawn eggs collapse + lang derivation** - shipped as CR-9 (PR #100): single
  `resource_slime_spawn_egg` carrying the variant in the `SLIME_VARIANT` component.
- ☑ **Slime Milk collapse (Approach B)** - shipped: one `slime_milk`
  fluid/source-block/bucket, variant on the bucket component + source BlockEntity;
  one neutral texture set tinted per-variant at render. Replaced the
  ~35-per-variant model. (Approach A - mod-init JSON scan - was rejected: it
  can't make milk addable by a *world* datapack, since fluids register before
  datapacks load.)
- ☑ **Title-cased lang fallback** - Slime Milk bucket / Configurable Froglight /
  spawn egg derive a readable name from the variant id when no lang key exists,
  so a datapack variant needs no client lang.
- ☑ **Slime texture fallback** - `ResourceSlimeRenderer` uses the category
  texture when a variant ships no `<variant>_resource_slime.png`.

## ~~V1.1 - vanilla resource coverage~~ - SHIPPED (v1.1.0)

Adds every vanilla item fitting cleanly into one of the existing 6 species. JSON-only authoring (plus the one Slime Milk `VARIANTS` Java edit). Full design in [v1_1_scope.md](./v1_1_scope.md). 22 new variants → 33 total post-V1.1 (the v1.0 `magma_cream` variant was also removed as redundant).

Per-variant work for each = a `slime_variant` JSON (with `primer_item` exact-match + thematic `inner_block`) + smelting recipe + milk blockstate + milk bucket model + lang entries + one `VARIANTS` entry. The first four JSON files are emitted by `scripts/generate_v1_1_variants.ps1`; the downscaled-block inner-cube texture by `scripts/generate_resource_slime_textures.py`; milk PNGs by `scripts/generate_slime_milk_textures.ps1`. Existing GameTests auto-cover.

### Shipped scope (22 variants)

**Bog (+7)** - swamp + mob drops, under Bog species (no new category needed)
- ☑ `bone` (skeleton)
- ☑ `gunpowder` (creeper, broad overworld)
- ☑ `clay_ball` (swamp clay) *(non-1:1 smelt - Froglight smelts to `brick`)*
- ☑ `rotten_flesh` (zombie)
- ☑ `string` (spider)
- ☑ `leather` (cow / horse)
- ☑ `feather` (chicken)
- ✗ `slime_ball` - cut as redundant (a slime made of slimeballs)

**Cave (+3)**
- ☑ `glow_ink_sac` (lush caves)
- ☑ `obsidian` (lava-lake at cave level)
- ☑ `echo_shard` (deep dark ancient cities)

**Geode (+1)**
- ☑ `amethyst` (mountain geodes)

**Tide (+2)**
- ☑ `ink_sac` (squids in oceans)
- ☑ `prismarine_crystals` (ocean monuments) *(Tier B, promoted into scope)*

**Infernal (+7)**
- ☑ `netherite_scrap`
- ☑ `glowstone_dust`
- ☑ `soul_sand`
- ☑ `soul_soil`
- ☑ `netherrack`
- ☑ `blaze`
- ☑ `quartz`

**Void (+2)**
- ☑ `chorus_fruit` *(originally a non-1:1 smelt to `popped_chorus_fruit`; made 1:1 to `chorus_fruit` on 2026-06-05 - see known_issues_archive.md)*
- ☑ `shulker_shell`

### Tier B - remaining
- ☑ `prismarine_crystals` (Tide) - shipped in v1.1 (promoted; inner_block `sea_lantern`).
- ☐ `nautilus_shell` (Tide) - deferred to V1.2+ (production-loop framing weak - drowned/fishing only in vanilla).
- ☐ `ghast_tear` (Infernal) - deferred to V1.2+ (single rare mob drop, rarity-break concern).
- ✗ `wither_rose` - dropped (the primer-tag-only fallback this depended on is gone in v1.0).
- ✗ `end_stone` - dropped (same as wither_rose).

## ~~V1.2 - cross-mod variant pools~~ - SHIPPED (v1.2.0)

Cross-mod variant entries for the popular ATM10 mods, shipped in PR #109. Design of record: [cross_mod_compat.md](./cross_mod_compat.md). The `SlimeVariant` codec gained a `primer_tag` field (item-or-tag primer); cross-mod entries are gated by `neoforge:conditions → mod_loaded` and key off the NeoForge `c:` common tags (`c:ingots/tin`, etc.) so one entry covers every providing mod (incl. AllTheOres, a parallel ore set). Each variant's smelt-back result is encoded in its own generated recipe JSON (also `mod_loaded`-gated for cross-mod variants, e.g. `configurable_froglight_tin.json` outputs `alltheores:tin_ingot`), not on the variant. ~24 cross-mod variants ship: metals (aluminum, tin, lead, nickel, osmium, silver, zinc, brass, uranium, mythril, orichalcum), AE2 (certus_quartz, fluix, fluorite, silicon), Mystical Agriculture (inferium, supremium), Industrial Foregoing (pink_slime), Powah (niotic, spirited, nitro), Mekanism (refined_obsidian), and more. The observability framework (PFDebug) also rode this release.

The original V1.2 plan (add a new "biological mob drops" 7th category) is **obsolete**: under the species model those 5 vanilla items (bone, rotten_flesh, string, leather, feather) fit cleanly under Bog Slime in V1.1, no new species required.

## V2 - automation

Out of scope until V1 ships. [docs/versioning.md#v2--automation](./versioning.md) is canonical.

- Buffered / auto-upgrading Slime Milker (basic hopper I/O already shipped in V1 via `Capabilities.ItemHandler.BLOCK`; V2 adds internal buffering + auto-cycling)
- Frog Terrarium / Habitat block (placeable frog housing with I/O inventory)
- Auto-feeders (hopper-fed slime delivery to frogs)
- Capacity / efficiency upgrades for habitat blocks
- Pipe/hopper-aware fluid handling for Slime Milk
- FE / NeoForge Energy compat (optional)

> **Not planned:** a native crusher block (an in-house double-yield crush path). The 2x crush yield is delegated to external crusher mods (Mekanism / Immersive Engineering / EnderIO) via the optional `mod_loaded` recipes shipped in v1.3.0; the mod will not ship its own crusher. Matches the "Explicitly NOT planned" lists in [ROADMAP.md](../ROADMAP.md) and [versioning.md](./versioning.md). Cross-mod crush rationale: [cross_mod_compat.md](./cross_mod_compat.md).

## Polish / debt - non-blocking, do when convenient

Items noted in commit messages or PR descriptions as known issues but not blocking.

### Code hygiene
- ~~**`ResourceFrog#brainProvider` duplicates vanilla constants.**~~ - resolved. Two ATs added to `META-INF/accesstransformer.cfg` (`Frog.SENSOR_TYPES` and `Frog.MEMORY_TYPES`); `brainProvider` is now a one-line stream substitution that maps the vanilla list, replacing `FROG_ATTACKABLES` with our category-filtered variant. New sensors Mojang adds to the frog brain are inherited automatically - no maintenance burden tracking vanilla churn.
- ~~**`SlimeSplitDiscoveryHandler.discoveryChancePerOffspring` is `public static`.**~~ - resolved in J5. Now backed by `PFConfig.DISCOVERY_CHANCE_PER_OFFSPRING` with a separate `testOverride` field for GameTest force-conversion.

### Tests
- ~~**GameTest for frog kill via actual tongue path.**~~ - resolved. New `frog_tongue_ai_path_drops_category_froglight` lets the frog's AI itself drive target selection, tongue extend, and damage dispatch (no `hurtServer` shortcut), so the test fails closed if any link breaks: sensor wiring, vanilla `FrogEat` behavior, the `Attributes.ATTACK_DAMAGE` value, or the `LivingDeathEvent` drop emitter. The original manual-damage test (`matching_frog_kill_drops_category_froglight`) is kept as a fast sanity-check of the death-event handler in isolation. 5/5 green on local repeat runs at the registered 400-tick timeout.
- ~~**GameTest for `loadFromBucketTag` from a fresh-spawned tadpole.**~~ - resolved. `tadpole_bucket_round_trip_preserves_category` now exercises the full save→read→`loadFromBucketTag`→assert path, matching `slime_bucket_round_trip_preserves_variant`'s shape from PR #22. A fresh ResourceTadpole spawned with CAVE is released via `loadFromBucketTag` from an INFERNAL-stamped bucket and must end up INFERNAL - silent no-ops would fail loudly.
- ~~**Visual rendering verification.**~~ - resolved. `docs/testing.md` now carries a "What GameTest does NOT cover - visuals are blind" subsection enumerating the client-side surfaces GameTest can't see (tints, render types, textures, UV/transforms, particles, GUI, lang fallbacks), with the PR #27 outer-shell-gray bug as the canonical example. The bottom "manual playtesting" section also expands into an explicit minimum-checklist for visual-touching PRs (inventory + dropped + in-world tint check, render-type sanity, lang-fallback check, JEI search). Authors of `client/` / `assets/` / `Category.tintArgb` / model-JSON / lang changes are pointed at the playtest path with screenshot capture for the PR description.
- ~~**`frog_tongue_targets_only_matching_category_slime` is flaky.**~~ - resolved. The original fixed-tick assertion raced against the two-sensor chain (`NEAREST_LIVING_ENTITIES` → `RESOURCE_FROG_ATTACKABLES`), each with a random `[0, scanRate)` first-scan offset. Switched to a polling pattern in `onEachTick`: succeed once `NEAREST_ATTACKABLE` holds the matching slime for a stability window of consecutive ticks, fail the moment the off-category slime appears, with a delayed-fallback assertion near the test timeout that fails with the last observed memory state if the sensor never settles. 5/5 green on local repeat runs.

### Visuals / assets
- **Real (non-placeholder) textures** for every item/block. Status by surface:
  - ✅ **Slime Milk buckets** (14 variants) - generated by `scripts/generate_slime_milk_textures.ps1`, which tints vanilla milk_bucket pixels per variant `primary_color`. Bucket metal stays gray; only the milk surface gets the hue swap. Re-run the script after any variant primary_color change.
  - ✅ **Slime Milk still + flow** (14 variants × 2) - same script; tints the top frame of vanilla water_still/water_flow per variant. Single-frame for V1; animated multi-frame strip + .mcmeta deferred to polish.
  - ✅ **Slime Milker block faces** - shipped (top / side / bottom / front + working variants for top/side/front). Generated by `scripts/generate_slime_milker_faces.ps1`.
  - ✅ **Slime Milker GUI** (`textures/gui/container/slime_milker.png`) - shipped. Vanilla furnace composite with fuel slot painted out, input slot relocated to vertically centre with the output (PR #78), and the burn_progress arrow re-inlined at (176, 14) so the partial-blit animation works (PR #74).
  - ✅ **Parent slime entities** (Cave / Geode / Tide / Void) - inner-cube textures shipped + per-species outer-shell tint shipped in PR #77 (stone grey / diamond cyan / water blue / end-portal purple).
  - ✅ **`tadpole_silhouette.png` / `slime_silhouette.png`** - shipped as small head sprites peeking from the bucket mouth (PR #76).
- ~~**Spawn eggs for ResourceFrog / ResourceSlime / ResourceTadpole.**~~ - shipped. 6 frog + 6 tadpole + 12 variant slime + 4 parent-species (Cave / Geode / Tide / Void) spawn eggs are registered in `PFItems` and exposed via `PFCreativeTabs.PRODUCTIVE_FROGS_TAB`. Variant slime eggs carry the `slime_variant` data component so each renders its resource colour rather than the broader category tint; pinned by `PFRegistryTest#variantSlimeSpawnEggCarriesSlimeVariantComponent` (12 parameterized cases).
- ~~**Per-category SlimeOuterLayer tweaks.**~~ - shipped. `Category.shellTintArgb()` returns a desaturated tinted gray (70% light gray + 30% category colour); `ResourceSlimeRenderer.extractRenderState` writes that value into `ResourceSlimeRenderState.outerTint` for variant-less Resource Slimes. TIDE slimes now read cooler-gray, INFERNAL warmer-gray, etc., without going full red/orange/cyan. Variant-locked slimes still use the full-saturation `variant.primary_color` (the more specific match). Pinned by `CategoryTest#shellTintArgb*` (opacity, math, per-category distinctness).

### Tooling
- ✅ **Jade.** Installed manually per [docs/dev_setup.md](./dev_setup.md); good enough for V1 dev. If Jade ever publishes to a maven we use cleanly, swap to a `runtimeOnly` dep like JEI - kept as a tracked nice-to-have, not a blocker.
- ✅ **Debug observability framework (all layers).** Shipped in PR #106; designed in [observability.md](./observability.md). One cross-cutting `PFDebug` helper (per-layer areas, gated by `-Dproductivefrogs.debug=<areas>` system property + a `/pf debug <area> on|off` command), off by default, logs each layer's resolution decisions to `latest.log` with a greppable `[PF/<area>]` prefix. All 12 areas instrumented (render, tint, infusion, split, tongue, egg, sensor, milker, milk_source, registry, config, lifecycle); `PFDebugTest` pins the gate behaviour. Replaces the add-then-delete ad-hoc logging. **Lesson that motivated it:** client-render bugs (tints, inner-block resolution, render types) are invisible to GameTest; the 2026-05-25 inner-block investigation burned ~25 round-trips re-reading correct code while the running build diverged, and a one-line render-thread log ended it in one launch. When a visual bug can't be reproduced by reading correct code, suspect a stale dev build first.

### Spawnery (v1.4) follow-ups
Deferred while building the Spawnery ([docs/spawnery.md](./spawnery.md)); none blocking.
- **Dynamic JEI primer display.** The Spawnery JEI info lists primers statically. Read the live `spawnery_primer/<species>` tag contents and render them per species, so a pack's overrides surface automatically and players can discover the pack's gating.
- ~~**`cross_mod_compat.md` cross-ref.**~~ Done - superseded by the consolidated [modpack_integration.md](./modpack_integration.md) (the pack-author entry point covering config, variants, Spawnery primers, crush yields); `cross_mod_compat.md` now points to it.
- **Durable dev-enable.** `spawnery.enabled` is off by default; testing from IntelliJ needs `run/config/productivefrogs-common.toml` flipped to `true`, which a `clean` resets. A small Gradle task wired into `prepareClientRun` could seed it so it survives (hand-flipped during dev for now).
- **Shared container-screen base.** `SpawneryScreen` and `SlimeMilkerScreen` both carry the same `render` -> `renderTooltip` override (the 1.21.1 `AbstractContainerScreen` gap). If a third GUI lands, extract a `PFContainerScreen<T>` base rather than duplicating it.

## Architecture lessons captured

From the Productive Bees survey ([productive_bees_analysis.md](./productive_bees_analysis.md)) and our PR-review history:

- **Use Codec/MapCodec records from day one** for any JSON-driven data. Productive Bees' hand-rolled JSON→CompoundTag conversion is their biggest tech debt.
- **Keep gameplay-critical logic in Java**, JSON for cosmetics and variant data only. Productive Bees' attempt to make produce-recipes fully data-driven ended in hardcoded `if/else` chains in `BeeHelper`.
- **Don't load-bear the entity class on soft deps.** Their GeckoLib-based class swap creates saves that can't load without the optional mod installed.
- **Stick with our `Category` enum** instead of a `ResourceLocation` string for the entity's type field - the enum prevents the "self-destruct unconfigured entity" band-aid Productive Bees needs.
- **Texture alpha is part of the data, not just the visual.** PR #27's outer-shell-gray bug was source-alpha 255 where vanilla uses 180. The render type respects source alpha; the file is the source of truth.
- **Pin display-name lang entries early.** PR #25 added them after the fact when playtest pain forced it; should have been part of every item-registration PR from day one.

## How to use this doc

When a PR adds a TODO or notes a deferred item, add a one-line entry here under the right section. When a PR closes one, delete the entry (git history is the trail; the doc tracks the *future*, not the past).

For larger units of work (e.g., "Slime Milker"), the entry here is a stub; the design lives in `docs/farming.md` or wherever and the implementation breakdown lives in the PR description when work starts.
