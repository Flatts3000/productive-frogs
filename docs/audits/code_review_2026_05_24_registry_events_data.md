# Code Review Audit - Registry / Events / Data (2026-05-24)

Specialist: code reviewer (Sonnet). Scope: `registry/*`, `ProductiveFrogs.java`, `PFConfig.java`,
`event/*`, `data/*`. Raw findings preserved for posterity. See `docs/code_review_2026_05_24.md`
for the synthesis. IDs here (R-N) are this reviewer's own numbering.

> VERIFICATION CAVEAT: This reviewer's CRITICAL finding R-1 (wrong event bus) was VERIFIED FALSE
> and REJECTED. In NeoForge 21.1.230 (FancyModLoader 10.0.36) the `@EventBusSubscriber` annotation
> has no `bus()` parameter at all; NeoForge auto-routes `@SubscribeEvent` handlers by whether the
> event implements `IModBusEvent`. The bare `@EventBusSubscriber(modid = ...)` is correct, which is
> why the mod runs and 50 in-world GameTests pass. R-1 is preserved below only as a record. Treat
> R-5 (stream codec) as the genuine high-value finding from this audit.

## CRITICAL (REJECTED - see caveat)

### R-1: Wrong event bus on PFModBusEvents and PFDataPackRegistryEvents [FALSE POSITIVE]
Files: `event/PFModBusEvents.java:32`, `event/PFDataPackRegistryEvents.java:22`. The reviewer
claimed `@EventBusSubscriber(modid = ...)` defaults to `Bus.GAME`, so mod-bus registration events
never fire and the mod is "totally broken." This is based on outdated Forge/older-NeoForge
knowledge. In this version the annotation has no `bus()` parameter; routing is automatic by event
type. Rejected.

## HIGH

### R-2: SlimeVariant.findByPrimerItem / pickWeighted return null without @Nullable
File: `data/SlimeVariant.java:102,123`. Both return `Map.Entry<...>` and can return null; callers
null-check correctly today, but the missing annotation hides the contract from static analysis and
future callers. Add `@Nullable` (JetBrains, per project convention).

### R-3: PFConfig has no min<=max cross-field constraint on spawn interval
File: `PFConfig.java:59-72`. `MIN`/`MAX_SPAWN_INTERVAL_TICKS` are validated individually but an
inverted config (min>max) silently falls back to using min as a fixed delay, ignoring max. Add a
startup warn in `FMLCommonSetupEvent` (where config is loaded).

### R-4: Dead private method spawnEggProperties
File: `registry/PFItems.java:319-327`. Never called; all setup goes through `applySpawnEggProps` /
`applyVariantSpawnEggProps`. Delete (~9 lines).

### R-5: Category.STREAM_CODEC uses ordinal with no bounds check
File: `data/Category.java:32`. `ByteBufCodecs.idMapper(ordinal -> values()[ordinal], ...)` does no
range validation; an out-of-range or negative wire ordinal throws AIOOBE during packet decode.
Used for client sync of the data component. Add a bounds check throwing on unknown ordinal (or
encode by string name). [VERIFIED REAL - this is CR-1 in the synthesis.]

## MEDIUM

### R-6: ParentSpeciesEntry javadoc references deleted enum constants
File: `data/ParentSpeciesEntry.java:14-19`. Lists `METALLIC/MINERAL/GEM/AQUATIC/ARCANE` which no
longer exist (now BOG/CAVE/GEODE/TIDE/INFERNAL/VOID). Broken `@link` targets. Also sweep
`SlimeSplitDiscoveryHandler.java` and `SlimeVariant.java` for the same references.

### R-7: Fully-qualified class names in method bodies instead of imports
Files: `registry/PFItems.java:238-248` (LinkedHashMap, BucketItem, Items.BUCKET),
`registry/PFBlocks.java:94-111` (LinkedHashMap, Collections). Hoist to top-of-file imports.

### R-8: FrogspawnBottlingHandler cancels before the side guard [NOT AN ISSUE]
File: `event/FrogspawnBottlingHandler.java:68-72`. The early cancel is the intentional vanilla-mirror
pattern (documented in the architecture doc). Downgraded by the reviewer to non-issue.

### R-9: testOverride is a public mutable static
File: `event/SlimeSplitDiscoveryHandler.java:60`. `public static volatile Float testOverride`.
Make package-private with `setTestOverride`/`clearTestOverride` accessors to enforce the
try/finally contract.

### R-10: buildSlimeVariantSpawnEggs hardcodes the variant->category mapping
File: `registry/PFItems.java:336-366`. Duplicates the JSON data; can drift silently (missing JSON =
no egg; changed category = wrong tint). Mitigate with a runData-time check or convert to the
single-item + component model. [Same root as architecture A-1 / synthesis CR-9.]

### R-11: DEPLETION_COUNT range comment couples implicitly to a blockstate property
File: `PFConfig.java:47-53`. The "hard ceiling 16" must stay in sync with
`SlimeMilkSourceBlock.SPAWNS_REMAINING.max`; add a comment naming the property constant.

## LOW

### R-12: Undocumented ordering dependency in buildMilkBlocks
File: `registry/PFBlocks.java:98`. `PFFluids.BY_VARIANT.get(variant).source().get()` is safe because
the constructor registers FluidTypes->Fluids->Blocks in order, but the dependency is undocumented
here (it is documented in `PFFluids.buildFluids`). Add an analogous comment.

### R-13: PFEntities javadoc references removed instanceof dispatch
File: `registry/PFEntities.java:88-89` (and GEODE/TIDE/VOID blocks). Describes
`categoryForParent` using `instanceof CaveSlime`, which was replaced by the registry lookup. Also
references old category names.

### R-14: BuiltInRegistries / ResourceLocation used as FQN despite no import
File: `registry/PFItems.java:299,321,380` and 369. Add proper imports per the no-wildcard,
one-block convention.

### R-15: PFConfig COMMON-vs-SERVER comment argues from the wrong direction
File: `PFConfig.java:12-18`. The values are server-only; COMMON is acceptable but the comment could
be tightened to say SERVER would be more precise. No code change.

## Summary
| Severity | Count | Issues |
|----------|-------|--------|
| CRITICAL | 1 (rejected) | R-1 false positive |
| HIGH | 4 | R-2 @Nullable; R-3 config cross-field; R-4 dead method; R-5 stream codec OOB |
| MEDIUM | 5 | R-6 stale javadoc; R-7 FQN; R-9 public test field; R-10 variant table drift; R-11 config comment |
| LOW | 4 | R-12 ordering dep; R-13 stale instanceof; R-14 FQN imports; R-15 config doc |
