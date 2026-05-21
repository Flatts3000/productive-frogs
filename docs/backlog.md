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

### SlimeVariant data registry (per-resource sub-categorization)
Currently we're Category-keyed only (one METALLIC slime, one INFERNAL slime, etc). The full design has per-resource variants within each category (iron_slime, copper_slime, gold_slime within METALLIC). [docs/architecture.md#slime-variant-pattern](./architecture.md)

- New `SlimeVariant` data class with a `MapCodec` (NOT hand-rolled JSON→CompoundTag — see [productive_bees_analysis.md §5a](./productive_bees_analysis.md))
- `SimpleJsonResourceReloadListener` scanning `data/<ns>/slime_variant/*.json`
- Cache decoded records on `ResourceSlime` entities — don't re-parse on every getter
- Schema includes: category, display name, color, parent species pool, weight, optional `neoforge:conditions`
- Cross-mod variants ship via JSON with `mod_loaded` conditions; no Java compat code

### Configurable Froglight item
Single `configurable_froglight` item with a `slime_variant` data component (mirroring [productive_bees_analysis.md §2](./productive_bees_analysis.md)). Keep the 6 hand-registered category Froglights alongside as the broad-strokes look.

- `slime_variant` data component on the item
- Tint + display name resolve at runtime from the SlimeVariant registry
- Frog tongue picks the configurable variant when the slime carries a SlimeVariant, falls back to the category Froglight otherwise

### Slime Milker block + Slime Milk fluid (V1 production keystone)
[docs/farming.md](./farming.md) specifies the full design. Sub-PR plan (J-series):

- **J1** ✅ — fluid framework (PFFluidTypes / PFFluids / BaseFlowingFluid plumbing) + iron_slime_milk variant + iron_slime_milk_bucket. Placeable fluid; no milker block or spawning yet. Placeholder iron-grey textures (real animated textures backlogged under polish).
- **J2** ✅ — expanded to all 14 variants (iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, magma_cream, ender_pearl, vanilla, magma). Refactored PFFluidTypes/PFFluids/PFBlocks/PFItems to `Map<String, ...>` keyed by variant name; `PFFluidTypes.VARIANTS` is the single source of truth list. Tests parameterized so adding future variants auto-extends coverage.
- **J3** ✅ — `SlimeMilkerBlock`: single appliance, no power, right-click with a Slime Bucket → consume slime, output typed milk bucket matching slime's variant. Fails closed (CONSUME, player keeps bucket) when the bucket has no Variant tag or the variant isn't in `PFFluidTypes.VARIANTS`. Covered by `SlimeMilkerBlockTest` (parser edge cases) + `slime_milker_converts_iron_slime_bucket_into_iron_milk_bucket` GameTest (in-world variant-pipeline + block placement).
- **J4** — Source-block slime spawning (~20s interval ± 10s jitter, vanilla mob-cap check).
- **J5** — Depletion counter (16 spawns default) + mod config wiring (depletion ON/OFF, interval, count, plus `discoveryChancePerOffspring` promotion from public-static hack).

### Smelting + crush recipes
- Froglight → base resource smelt recipes (6 recipes, one per category for V1)
- Cross-mod crush 2× recipes for metallic Froglights via Create / Mekanism / Thermal compat
- Crush tag: `productivefrogs:crushable/metallic`

### Player direct-feeding (Q9)
[docs/open_questions.md#9](./open_questions.md) — right-click a Resource Frog while holding a matching-category Slime Bucket. Bucket transforms back to empty, frog drops Froglight at its position. Category-match check applies; mismatch is a no-op.

### Mod config wiring
- `discoveryChancePerOffspring` is currently a `public static float` for GameTest access — promote to a proper mod config (`net.neoforged.neoforge.common.ModConfigSpec`)
- Per-parent-species default categories (config or datapack)
- Milk source depletion count
- Milk spawn interval

## V2 — automation

Out of scope until V1 ships. [docs/versioning.md#v2--automation](./versioning.md) is canonical.

- Auto-fed Slime Milker (hopper-integrated)
- Frog Terrarium / Habitat block (placeable frog housing with I/O inventory)
- Auto-feeders (hopper-fed slime delivery)
- Capacity / efficiency upgrades for habitat blocks
- Native crusher block (in-house 2× path so we don't depend on Create/Mekanism/Thermal)
- Pipe/hopper-aware fluid handling for Slime Milk
- FE / NeoForge Energy compat (optional)

## Polish / debt — non-blocking, do when convenient

Items noted in commit messages or PR descriptions as known issues but not blocking.

### Code hygiene
- **Rename `TadpoleBucketCategoryTint` → `BucketedCategoryTint`.** Originally tadpole-specific; now serves the Slime Bucket too (PR #22). The class name is misleading. Touch the registration site + 2 client-item-info JSONs.
- **`ResourceFrog#brainProvider` duplicates vanilla constants.** We rebuild the sensor list inline because `Frog.SENSOR_TYPES` is `protected static`. If we add a few more sensors a cleaner approach is access-transforming `Frog.SENSOR_TYPES` and `Frog.MEMORY_TYPES` to public so we can reference them directly.
- **`SlimeSplitDiscoveryHandler.discoveryChancePerOffspring` is `public static`.** Necessary for the GameTest to force 1.0; needs to move to mod config (see V1 §Mod config wiring above).

### Tests
- **GameTest for frog kill via actual tongue path.** `matching_frog_kill_drops_category_froglight` uses `hurtServer(level, source, 999.0F)` which bypasses the frog's `ATTACK_DAMAGE` attribute. PR #27 caught a damage=0 regression that this test couldn't see. A more realistic test would let the tongue actually drive the kill.
- **GameTest for `loadFromBucketTag` from a fresh-spawned slime.** Currently we only call save→read on the bucket. PR #22's strengthening covered the slime bucket; verify the tadpole bucket has equivalent coverage.
- **Visual rendering verification.** GameTest is headless — can't catch broken tints / missing textures / wrong UV alpha. The slime outer-shell solid-gray bug (PR #27) is the canonical example. Playtest is the only test layer for visuals; document this clearly in `docs/testing.md`.
- **`frog_tongue_targets_only_matching_category_slime` is flaky.** Observed during PR #32 — failed once with "sensor never wrote NEAREST_ATTACKABLE on tick 60" then passed on retry. The 60-tick window I picked for the assertion is right at the edge of vanilla's `NearestVisibleLivingEntitySensor` scan cadence. Either bump the window to ~100 ticks or pre-warm the sensor by manually invoking it (`sensor.tick(level, frog)`) before polling.

### Visuals / assets
- **Real (non-placeholder) textures** for every item/block. Currently using vanilla textures + per-category tints + placeholder PNGs (tadpole_silhouette, slime_silhouette).
- **Spawn eggs for ResourceFrog / ResourceSlime / ResourceTadpole.** Currently accessible via `/summon` only — fine for dev but blocks survival creative-tab use.
- **Per-category SlimeOuterLayer tweaks.** PR #26 + #27 fixed the basic shell rendering. Could tighten the gray tone per-category (cooler gray for AQUATIC, warmer for INFERNAL) for visual variety. Low priority.

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
