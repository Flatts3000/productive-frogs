# Code Review Audit - Dead Code and Duplication (2026-05-24)

Specialist: refactor-cleaner (Sonnet). Scope: whole `src/main/java` tree + `src/test/java`. Each
"dead" item was confirmed with Grep (zero call sites) before listing. Raw findings preserved for
posterity. See `docs/code_review_2026_05_24.md` for the synthesis. IDs here (D-N) are this
reviewer's own numbering. Independently re-verified via Grep on 2026-05-24.

## DEAD CODE

### D-1: spawnEggProperties - private method, zero callers [HIGH]
File: `registry/PFItems.java:319-327`. Superseded by `applySpawnEggProps`. Grep confirms only the
definition. Delete (~9 lines).

### D-2: tadpoleBucketCategory - public wrapper, zero callers [HIGH]
File: `registry/PFItems.java:396-399`. Delegates to `ResourceTadpoleBucketItem.readCategory`, which
all real callers use directly. Grep confirms zero call sites. Delete (~4 lines).

### D-3: onCommonSetup only logs a line [LOW / needless indirection]
File: `ProductiveFrogs.java:66-68`. The `FMLCommonSetupEvent` listener does nothing but log
"common setup complete"; the constructor already logs init. Delete method + listener registration +
the `FMLCommonSetupEvent` import (~5 lines + 1 import). NOTE: keep this if the synthesis adds a
config-validation warn (CR-12) to common setup - then the listener earns its keep.

### D-4: unused import java.util.Optional [LOW / unused import]
File: `content/item/ResourceTadpoleBucketItem.java:4`. Grep confirms the body never uses Optional.
Delete line 4.

### D-5: IRON_SLIME_MILK_SOURCE / IRON_SLIME_MILK_FLOWING - J1 aliases, zero callers [MEDIUM]
File: `registry/PFFluids.java:41-44`. Explicitly labelled "backwards-compatible aliases for J1
callers"; `refactor_data_driven_variants.md:258` marks them for removal. Grep confirms zero `src/`
references. Delete (~4 lines).

### D-6: IRON_SLIME_MILK aliases in PFFluidTypes and PFBlocks [MEDIUM]
Files: `registry/PFFluidTypes.java:71`, `registry/PFBlocks.java:91`. Same J1 alias pattern; zero
callers. Delete (~2 lines).

### D-7: IRON_SLIME_MILK_BUCKET alias [MEDIUM]
File: `registry/PFItems.java:223-224`. Same; tests use `PFItems.MILK_BUCKETS.get("iron")`. Delete
(~2 lines).

> Note on D-5/6/7: these are declared `public final`; a modpack's reflection or a future cross-mod
> API could theoretically reference them. Risk is low and the docs already call for removal.

### D-8: ParentSpeciesEntry javadoc cites removed enum names [LOW / stale javadoc]
File: `data/ParentSpeciesEntry.java:14-20`. `Category#METALLIC/MINERAL/GEM/AQUATIC/ARCANE` no longer
exist; also lists the two vanilla parents removed in V1.5. Broken `@link` targets. Rewrite to BOG/
CAVE/GEODE/TIDE/INFERNAL/VOID.

### D-9: broken @link #matchingFrogKillDropsCategoryFroglight [LOW / stale javadoc]
File: `gametest/PFGameTests.java:804`. No such method; should be `...Configurable...`. One-line fix.

### D-10: six parent-entity javadocs describe instanceof dispatch [LOW / stale javadoc]
Files: `BogSlime/CaveSlime/GeodeSlime/InfernalSlime/TideSlime/VoidSlime.java:14`. Each says the
class exists so `SlimeSplitDiscoveryHandler` can use `instanceof`; that handler now uses the
registry lookup. (The classes are NOT dead - they need distinct EntityType ids; only the
explanation is wrong. `SlimeInfusionHandler` still uses instanceof - see architecture A-2.)

## DUPLICATION

### D-11: six parent-species renderer classes, constructor bodies 100% identical [HIGH]
Files: `client/renderer/{Bog,Cave,Geode,Infernal,Tide,Void}SlimeRenderer.java`. ~38-40 lines each;
differ only in `TEXTURE` + `OUTER_TINT_ARGB`. Collapse to one `ParentSlimeRenderer(ctx, texture,
tintArgb)`; six registrations in `PFClientEvents` become one-line lambdas. Est. ~170 lines removed,
-5 files. [Synthesis CR-8.]

### D-12: getParticleType RGB-to-DustParticle conversion duplicated x7 [MEDIUM]
Files: `BogSlime:49-53`, `InfernalSlime:47-51`, `CaveSlime:48-52`, `GeodeSlime:41-45`,
`TideSlime:41-45`, `VoidSlime:41-45`, `ResourceSlime:180-184`. Identical bit-shift + `/255f` +
`Vector3f` logic; inconsistent `org.joml.Vector3f` import style (3 use inline FQN). Extract a
package-private `categoryTintToParticle(int rgb)` helper. Est. ~20 lines. [Synthesis CR-8.]

### D-13: getCategory ordinal-bounds-check duplicated x3 [MEDIUM]
Files: `ResourceFrog.java:68-77`, `ResourceTadpole.java:59-68`, `ResourceSlime.java:80-89`. Identical
8-line body (read DATA_CATEGORY, bounds-check, fall back to BOG). The three classes have different
vanilla superclasses so inheritance cannot share it; extract `Category.fromOrdinalOrDefault(int)`.
Est. ~15 lines. [Synthesis CR-8.]

### D-14: spawn-egg ENTITY_DATA NBT construction partial overlap [LOW]
File: `registry/PFItems.java:297-305` and `368-389`. Both build a CompoundTag with `id` + `Category`
then a `CustomData.of`. Extract a `spawnEggNbt(EntityType, Category)` helper. Est. ~4 lines. (Both
methods are live; only `spawnEggProperties` from D-1 is dead.)

## NOT dead code (considered and confirmed intentional)
`@SubscribeEvent` methods (bus-invoked), DeferredRegister fields, CODEC/STREAM_CODEC constants,
`testOverride` (GameTest hook), the two-layer category enforcement, GameTest functions
(harness-invoked), the six parent entity classes (distinct EntityType ids), and all
`BY_VARIANT`/`MILK_BLOCKS`/`MILK_BUCKETS` map entries (actively iterated).

## Summary table
| ID | Title | Category | Severity | Est. lines |
|----|-------|----------|----------|-----------|
| D-1 | spawnEggProperties dead | DEAD | HIGH | 9 |
| D-2 | tadpoleBucketCategory dead | DEAD | HIGH | 4 |
| D-3 | onCommonSetup only logs | DEAD/indirection | LOW | 6 |
| D-4 | unused Optional import | UNUSED IMPORT | LOW | 1 |
| D-5 | IRON_SLIME_MILK_SOURCE/FLOWING | DEAD | MEDIUM | 4 |
| D-6 | IRON_SLIME_MILK (FluidTypes/Blocks) | DEAD | MEDIUM | 2 |
| D-7 | IRON_SLIME_MILK_BUCKET | DEAD | MEDIUM | 2 |
| D-8 | ParentSpeciesEntry stale enum names | STALE JAVADOC | LOW | doc |
| D-9 | broken @link in PFGameTests | STALE JAVADOC | LOW | 1 |
| D-10 | six entity javadocs say instanceof | STALE JAVADOC | LOW | doc |
| D-11 | six identical renderers | DUPLICATION | HIGH | ~170 |
| D-12 | getParticleType x7 | DUPLICATION | MEDIUM | ~20 |
| D-13 | getCategory x3 | DUPLICATION | MEDIUM | ~15 |
| D-14 | spawn-egg NBT overlap | DUPLICATION | LOW | ~4 |
