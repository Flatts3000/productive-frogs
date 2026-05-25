# Code Review - 2026-05-24

Comprehensive review of the Productive Frogs source tree (63 main files, ~9,500 LOC) at
v1.0.1 (`92d920e`). Conducted with six parallel specialist reviewers (architecture,
registry/events/data, content, client rendering, dead-code/duplication, security), then
every CRITICAL and HIGH claim was verified against the actual code before inclusion here.

The codebase is in good shape: clean package layering with no dependency cycles, an
idiomatic datapack-registry spine, correct registry/lifecycle ordering with the
load-bearing constraints documented inline, and disciplined V1/V2 scope adherence. The
findings below are mostly hardening, dead-code cleanup, documentation drift, and
duplication - not correctness bugs.

## Verification note - one reviewer false positive (rejected)

A reviewer flagged a CRITICAL: `PFModBusEvents` and `PFDataPackRegistryEvents` use
`@EventBusSubscriber(modid = ...)` without `bus = Bus.MOD`, claiming the mod-bus events
(`EntityAttributeCreationEvent`, `RegisterCapabilitiesEvent`,
`DataPackRegistryEvent.NewRegistry`) never fire and "the mod is totally broken."

This is FALSE and was rejected after verification:

- Decompiling `net.neoforged.fml.common.EventBusSubscriber` from FancyModLoader 10.0.36
  (the loader bundled with NeoForge 21.1.230) shows the annotation has only `value()` and
  `modid()` - there is no `bus()` parameter and no `Bus` enum in this version. NeoForge
  21.1.x auto-routes each `@SubscribeEvent` handler by inspecting whether its event type
  implements `IModBusEvent`. The reviewer applied outdated Forge / older-NeoForge knowledge
  (where `bus()` defaulted to `GAME`).
- Empirically: 50 GameTests spawn these entities in-world and pass; the mod ships and runs.
  If the attribute event never fired, every custom entity would crash on spawn.

A second rejected finding: "ResourceSlime.remove() double-splits." Verified NOT a bug -
`remove()` calls `setSize(1, false)` before `super.remove()`, so vanilla's `size > 1`
split guard fails and only the custom category-split runs. The split-discovery GameTests
assert child count and category inheritance and pass.

---

## HIGH

### CR-1: Category.STREAM_CODEC indexes `values()` with no bounds check
**File:** `src/main/java/com/flatts/productivefrogs/data/Category.java:31-32`
**Trust boundary:** network (item data component sync)
**Status:** verified real.

```java
ByteBufCodecs.idMapper(ordinal -> values()[ordinal], Category::ordinal)
```

`ByteBufCodecs.idMapper(IntFunction, ToIntFunction)` does no range validation before calling
the decode lambda, so a wire ordinal outside `[0, 5]` (or negative) throws
`ArrayIndexOutOfBoundsException`. This codec backs the `contained_category` data component
(`PFDataComponents`, `networkSynchronized`). A modified creative client can send an item
with an out-of-range `contained_category` ordinal via `ServerboundSetCreativeModeSlotPacket`;
the server decodes it and an uncaught AIOOBE on the netty thread is a remotely triggerable
crash vector. Low-frequency (creative + modified client) but trivially fixable.

**Fix:** bounds-check in the decode lambda and throw `io.netty.handler.codec.DecoderException`
(which the pipeline catches and turns into a clean disconnect) on out-of-range input.
Alternatively encode by name via `Category.CODEC` + `ByteBufCodecs.STRING_UTF8` (also robust
to enum reordering).

---

## MEDIUM

### CR-2: Dead code - delete on sight
**Status:** verified (each symbol appears only as its definition; zero call sites in `src/`).

- `PFItems.spawnEggProperties(EntityType, Category)` - `registry/PFItems.java:319-327`. Superseded
  by `applySpawnEggProps`; never called. (~9 lines)
- `PFItems.tadpoleBucketCategory(ItemStack)` - `registry/PFItems.java:396-399`. Javadoc claims it
  is the client-tint helper, but client code calls `ResourceTadpoleBucketItem.readCategory`
  directly. (~4 lines)
- J1 backward-compat aliases, all zero-caller (the doc `refactor_data_driven_variants.md` already
  marks them for removal): `PFFluids.IRON_SLIME_MILK_SOURCE` / `IRON_SLIME_MILK_FLOWING`
  (`registry/PFFluids.java:41-44`), `PFFluidTypes.IRON_SLIME_MILK` (`registry/PFFluidTypes.java:71`),
  `PFBlocks.IRON_SLIME_MILK` (`registry/PFBlocks.java:91`), `PFItems.IRON_SLIME_MILK_BUCKET`
  (`registry/PFItems.java:223-224`).
- Unused import `java.util.Optional` - `content/item/ResourceTadpoleBucketItem.java:4`.

### CR-3: Stale javadoc - renamed Category constants and removed `instanceof` mechanism
**Status:** verified across 11 files.

The V1.5 species-as-category rename and the version port left a trail of misleading comments.
The code is correct; the docs lie, which actively misleads a maintainer.

- Broken `{@link Category#METALLIC/#MINERAL/#GEM/#AQUATIC/#ARCANE}` links - these constants no
  longer exist (now `BOG/CAVE/GEODE/TIDE/INFERNAL/VOID`). Worst offender:
  `data/ParentSpeciesEntry.java:14-23` (broken `@link` targets + lists the two vanilla parents
  that V1.5 removed).
- "Fall back to METALLIC (tier 1)" comments where the code actually returns `Category.BOG`:
  `content/entity/ResourceFrog.java:69`, `ResourceTadpole.java:61`, `ResourceSlime.java:81`.
- "`instanceof CaveSlime` check" / "instanceof ... branch" descriptions where
  `SlimeSplitDiscoveryHandler` now uses the `PARENT_SPECIES` registry lookup:
  `registry/PFEntities.java:87-89,122,138-139`, plus the six parent entity class javadocs.
- Stale `METALLIC`/`AQUATIC` in `data/SlimeVariant.java:29,37`, `ResourceSlime.java:33-38`,
  `VoidSlime.java`, `GeodeSlime.java`, and pre-V1.5-history prose in `gametest/PFGameTests.java`
  (lower priority - those describe history).
- Broken `{@link #matchingFrogKillDropsCategoryFroglight}` (should be `...Configurable...`) -
  `gametest/PFGameTests.java:804`.

**Fix:** one mechanical `docs:` sweep. No behavior change.

### CR-4: Infusion and split-discovery resolve "parent species -> category" two different ways
**Files:** `event/SlimeInfusionHandler.java:135-141` (`instanceof` chain) vs
`event/SlimeSplitDiscoveryHandler.java` (`PARENT_SPECIES` registry lookup).
**Status:** verified (`SlimeInfusionHandler.java:136` is `if (slime instanceof CaveSlime) return Category.CAVE;`).

A modpack that adds a modded parent slime via a `parent_species` JSON (the documented extension
path) works for split-discovery but silently does nothing for infusion - the `instanceof` chain
returns null for any non-PF subclass. The split handler's own javadoc calls the old `instanceof`
approach a "footgun" that was replaced, yet infusion still uses it.

**Fix:** route `resolveParentSpecies` through the same registry lookup (extract a shared
`categoryForEntityType` helper). The 6-entry scan on a right-click path is negligible.

### CR-5: Per-frame registry scan in the inner-block render path
**File:** `client/renderer/ResourceSlimeInnerBlockLayer.java:131-143` (`parentSpeciesBlock`).
**Status:** verified.

For every visible parent-species slime, every frame, the layer does a linear scan of the
`PARENT_SPECIES` registry plus an entity-type-key fetch - to recompute a `BlockState` that is
fixed for the world session. Cheap at 6 entries today, but it is per-frame churn for a constant
value.

**Fix:** resolve the `BlockState` once. For parent species each renderer maps to exactly one
species, so pass the resolved `BlockState` (or its supplier) into the layer at construction time,
paralleling how `outerTexture` is now a constructor argument. For the variant path, a small
`Map<ResourceLocation, BlockState>` cache cleared on resource reload.

### CR-6: Resource Frog can place vanilla frogspawn when the category-lay behavior fails
**Files:** `content/entity/ai/LayCategoryFrogspawn.java:98` (returns false without erasing
`IS_PREGNANT`), `content/entity/ResourceFrog.java` (`makeBrain` keeps vanilla's
`TryLaySpawnOnWaterNearLand` at priority 3).
**Status:** plausible edge case - confirm against vanilla's water check.

`LayCategoryFrogspawn` erases `IS_PREGNANT` only on success. On failure (no adjacent water-SOURCE
tile) it returns false, and vanilla's priority-3 lay behavior then runs. Vanilla's behavior keys
on `FluidTags.WATER` (includes flowing), while the custom one requires `isSource()`. In a
flowing-water-only spot the custom behavior fails, vanilla succeeds, and the frog lays a vanilla
`minecraft:frogspawn` block instead of a Primed Frog Egg.

**Fix:** either remove vanilla's `TryLaySpawnOnWaterNearLand` from the `ResourceFrog` brain, or
have `LayCategoryFrogspawn` erase `IS_PREGNANT` and return true on terminal failure so vanilla
never places a fallback. Add a GameTest for the flowing-water case.

### CR-7: SlimeMilkerBlockEntity has no client sync tag
**File:** `content/block/entity/SlimeMilkerBlockEntity.java`.
**Status:** verified (no `getUpdateTag`/`getUpdatePacket`; `ConfigurableFroglightBlockEntity` has both).

Disk persistence (`saveAdditional`/`loadAdditional`) is correct, and the GUI syncs inventory while
open + the `WORKING` blockstate drives the animation, so there is no player-visible bug. But
info-HUD mods (Jade/WTHIT) reading the BE from the client see stale/empty inventory when the menu
is closed.

**Fix:** add `getUpdateTag`/`getUpdatePacket` following the Froglight BE pattern.

### CR-8: Duplication - 6 parent renderers, particle conversion x7, getCategory x3
**Status:** verified.

- Six parent-species renderers (`client/renderer/{Bog,Cave,Geode,Infernal,Tide,Void}SlimeRenderer.java`)
  are byte-identical except a `TEXTURE` and `OUTER_TINT_ARGB` constant. Collapse to one
  `ParentSlimeRenderer(ctx, texture, tintArgb)` constructed with per-species args in `PFClientEvents`.
  (~170 lines, -5 files)
- `getParticleType()` RGB-to-`DustParticleOptions` conversion is copy-pasted in all six parent
  entities plus `ResourceSlime` (7 sites), with inconsistent `org.joml.Vector3f` import style.
  Extract a `Category` helper. (~20 lines)
- `getCategory()` ordinal-bounds-check is identical in `ResourceFrog`/`ResourceTadpole`/`ResourceSlime`.
  Extract `Category.fromOrdinalOrDefault(int)`. (~15 lines)

Keep the six parent ENTITY classes - distinct EntityType ids are required for biome-spawn rules,
spawn eggs, and the `parent_species` registry keys.

### CR-9: "Add a variant = one JSON" is not yet true - blocks the V1.1 plan
**Files:** `registry/PFItems.java:336-366` (`buildSlimeVariantSpawnEggs` hardcodes a
variant->category table duplicating the JSON), `registry/PFFluidTypes.java:54-61` (`VARIANTS`
list of 14 must be hand-edited per milkable variant).
**Status:** ADDRESSED (spawn-egg half) on branch `feat/v1.1-data-driven-spawn-eggs`. The 12
per-variant spawn-egg item IDs were collapsed into one component-driven
`resource_slime_spawn_egg`; the creative tab + JEI + tint now enumerate variants from the
`slime_variant` registry. Adding a variant no longer needs a spawn-egg Java edit. The Slime
Milk `VARIANTS` edit remains (fluids must register at mod-init) - that is the documented,
inherent one-line touch, not a defect.

`architecture.md` and `versioning.md` promise V1.1 is "JSON-only, no Java edits" for 22 new
variants. Today each variant needs a `VariantSpec` row (with a category copy that can drift from
the JSON) to get a spawn egg, and a `VARIANTS` entry to get a Slime Milk fluid. Item/fluid
registration happens at mod-init, before the datapack registry loads, so this is a real
constraint, not an oversight.

**Fix (highest strategic leverage before V1.1):** convert per-variant spawn eggs to a single
`resource_slime_spawn_egg` item carrying the variant in a data component - exactly the pattern
`configurable_froglight` (and JEI subtyping) already use. That deletes the `VariantSpec[]` and its
category duplication. For milk fluids (which genuinely must register at mod-init), keep the static
list but add a startup validation that every shipped `slime_variant` JSON has a matching `VARIANTS`
entry, and correct the V1.1 doc to say "JSON + one fluid-list edit per milkable variant."

---

## LOW (defensive / polish)

- **CR-10** `SlimeMilkerBlockEntity.loadAdditional` reads `CookProgress` without clamping
  (`:200-201`); a tampered save with a negative value stalls the block permanently. `Math.max(0, ...)`. (disk-NBT)
- **CR-11** `SlimeVariant` codec does not range-constrain `primary_color`/`secondary_color`
  (`data/SlimeVariant.java:85-86`); out-of-range values produce garbage tints, not crashes.
  `Codec.intRange(0, 0xFFFFFF)`. (datapack)
- **CR-12** `PFConfig` validates min/max spawn-interval individually but not `min <= max`
  (`PFConfig.java:59-72`); an inverted config silently ignores max. Add a startup warn in common setup.
- **CR-13** `SlimeMilkSourceBlock.spawn` never calls `finalizeSpawn` before `addFreshEntity`
  (`:225`); the vanilla `Slime`/`MagmaCube` variants skip vanilla spawn init. Also dead imports
  `MagmaCube` and `MobSpawnType`. (Note: `MobSpawnType` becomes used if the fix lands.)
- **CR-14** Missing `@Nullable` on `SlimeVariant.findByPrimerItem` / `pickWeighted`
  (`data/SlimeVariant.java:102,123`) - both return null; current callers null-check, but the
  contract is invisible to static analysis.
- **CR-15** `SlimeSplitDiscoveryHandler.testOverride` is a `public` mutable static
  (`event/SlimeSplitDiscoveryHandler.java:60`); make it package-private with set/clear accessors.
- **CR-16** `SlimeMilkerScreen` does not set label coords or override `renderLabels`; defaults are
  probably fine for a 176x166 furnace layout but need an in-client eyeball.
- **CR-17** Fully-qualified class names in method bodies instead of imports (style; the project bans
  this implicitly): `PFItems.java` (LinkedHashMap, BucketItem, BuiltInRegistries, ResourceLocation),
  `PFBlocks.java` (LinkedHashMap, Collections), `PFClientEvents.java:98,249-252`
  (ConfigurableFroglightBlockEntity, SpawnEggItem, Consumer).
- **CR-18** `FrogEggItem.use` plays `SoundEvents.FROGSPAWN_HATCH` on placement
  (`content/item/FrogEggItem.java:106`); a hatch sound when placing is misleading - prefer
  `BOTTLE_EMPTY` or a place sound.
- **CR-19** `ResourceSlimeInnerBlockLayer` render constants (`BLOCK_EDGE`, `CENTER_Y`) carry a
  "TUNE IN-CLIENT" comment (`:63-64`); replace with a tracked issue reference and confirm visually.
- **CR-20** `ResourceSlime` carries both a synced `Category` and a variant id with an implicit
  "set category before variant" ordering contract repeated at four call sites; centralize the
  invariant in a single private `applyVariant(id)` (informational - the denormalization is a
  defensible perf fast-path).

---

## Remediation tranches (suggested)

1. **Tranche A - docs + dead code (zero behavior risk):** CR-2, CR-3, the dead imports in CR-13.
   One `chore:`/`docs:` PR. Build stays green by construction.
2. **Tranche B - hardening (small, targeted):** CR-1 (stream codec bounds check), CR-10
   (CookProgress clamp), CR-11 (color range), CR-12 (config validation), CR-14 (@Nullable),
   CR-15 (testOverride visibility). One `fix:` PR with a regression test per item.
3. **Tranche C - correctness:** CR-6 (vanilla frogspawn leak; needs a GameTest), CR-7 (BE client
   sync), CR-13 (finalizeSpawn). One `fix:` PR.
4. **Tranche D - duplication / perf refactor:** CR-5 (per-frame scan), CR-8 (collapse renderers +
   extract helpers), CR-4 (unify parent resolution). One `refactor:` PR, guarded by the existing
   GameTests.
5. **Tranche E - V1.1 enabler (do before the 22-variant batch):** CR-9 (single spawn-egg item +
   component; milk-list validation). Its own PR with a clear migration note.

CR-16, CR-17, CR-18, CR-19, CR-20 are opportunistic - fold into whichever tranche touches the file.
