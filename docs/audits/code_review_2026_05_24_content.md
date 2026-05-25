# Code Review Audit - Content (2026-05-24)

Specialist: code reviewer (Sonnet). Scope: `content/entity/*`, `content/entity/ai/*`,
`content/block/*`, `content/block/entity/*`, `content/menu/*`, `content/item/*`. Raw findings
preserved for posterity. See `docs/code_review_2026_05_24.md` for the synthesis. IDs here (C-N)
are this reviewer's own numbering.

> VERIFICATION CAVEAT: This reviewer's top finding C-1 (ResourceSlime double-split) was VERIFIED
> NOT A BUG. `remove()` calls `setSize(1, false)` before `super.remove()`, so vanilla's `size > 1`
> guard fails and only the custom category-split runs; the split-discovery GameTests assert child
> count + category inheritance and pass. The reviewer also self-withdrew C-2, C-4, and C-6 mid-audit.
> Genuine findings from this audit: C-8 (vanilla frogspawn leak), C-7 (BE client sync), C-5 (stale
> comments), C-3 (finalizeSpawn).

## CRITICAL
None found.

## HIGH

### C-1: ResourceSlime.remove() split mechanism [NOT A BUG - see caveat]
File: `content/entity/ResourceSlime.java:251`. The reviewer raised concern about a redundant
size-sync packet and a theoretical double-split, but conceded "the mechanism does work."
Verified correct: no double-split. At most a micro-optimization (skip the redundant
`setSize(1, false)` data broadcast on a dying entity). Rejected as a defect.

### C-3: SlimeMilkSourceBlock.spawn() does not call finalizeSpawn()
File: `content/block/SlimeMilkSourceBlock.java:225`. `createSlimeForVariant` sets size but never
calls `finalizeSpawn` before `addFreshEntity`. For the vanilla `Slime`/`MagmaCube` variants this
skips vanilla spawn-init side effects. The `MobSpawnType` import is currently dead. Fix: call
`slime.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.SPAWNER, null)`
before adding. [Synthesis CR-13 - LOW.]

### C-5: getCategory() comments say "Fall back to METALLIC" but code returns BOG
Files: `content/entity/ResourceSlime.java:81,86`, `ResourceFrog.java:70,74`, `ResourceTadpole.java:62,66`.
Stale from the V1.5 rename. Code is self-consistent (all default to BOG); comments mislead. Doc-only.
[Synthesis CR-3.]

## MEDIUM

### C-8: ResourceFrog can place vanilla frogspawn when category-lay fails
Files: `content/entity/ResourceFrog.java:194` (makeBrain keeps vanilla `TryLaySpawnOnWaterNearLand`
at priority 3), `content/entity/ai/LayCategoryFrogspawn.java` (erases `IS_PREGNANT` only on success).
When `LayCategoryFrogspawn` finds no water-SOURCE position it returns false without erasing
`IS_PREGNANT`; vanilla's less-strict (flowing-water-accepting) behavior then runs and places
`minecraft:frogspawn`. Fix: remove vanilla's behavior from the ResourceFrog brain, or erase
`IS_PREGNANT` and return true on terminal failure. Add a flowing-water GameTest. [Synthesis CR-6.]

### C-7: SlimeMilkerBlockEntity missing getUpdateTag/getUpdatePacket
File: `content/block/entity/SlimeMilkerBlockEntity.java`. Disk persistence is correct and the GUI
syncs inventory while open + the WORKING blockstate drives animation, so no player-visible bug.
But info-HUD mods (Jade/WTHIT) reading the closed BE see stale/empty inventory. Add the two
overrides following the `ConfigurableFroglightBlockEntity` pattern. [Synthesis CR-7.]

## LOW

### C-9: ResourceTadpole.ageUp() calls setPersistenceRequired unconditionally
File: `content/entity/ResourceTadpole.java:166`. Matches vanilla Tadpole behavior, but bucket-released
tadpoles far from a player will never despawn, allowing frog accumulation near milk fountains.
Flag for awareness; matches vanilla intentionally.

### C-10: SlimeMilkSourceBlock has unused imports MagmaCube and MobSpawnType
File: `content/block/SlimeMilkSourceBlock.java:12,14`. `MagmaCube` is never referenced by name;
`MobSpawnType` is dead until C-3 is fixed. Convention violation (no dead imports).

### C-11: PrimedFrogEggBlock.hatch() yaw uses nextInt(1,361) instead of a continuous float
File: `content/block/PrimedFrogEggBlock.java:113`. Range `[1,360]` excludes 0, includes 360 (=0),
and is discrete. Cosmetic; vanilla uses `random.nextFloat() * 360.0F`.

### C-12 / C-13: loadFromBucketTag one-liner + missing @Nullable
`content/entity/ResourceSlime.java:359-363` packs if/try-catch onto single lines (audit hazard;
duplicates `readAdditionalSaveData` in a compressed style). `data/SlimeVariant.java:102` missing
`@Nullable` on the null-returning finders. [Synthesis CR-20 / CR-14.]

### C-14: ParentSpeciesEntry javadoc stale category names
File: `data/ParentSpeciesEntry.java:14`. `Category#METALLIC/MINERAL/GEM/AQUATIC/ARCANE` no longer
exist; broken `@link`. [Synthesis CR-3.]

### C-15: Category.STREAM_CODEC ordinal fragile to enum reordering
File: `data/Category.java:32`. Ordinal encoding corrupts across reordering; the disk/JSON CODEC
uses string names and is resilient. [Overlaps the security audit S-1 / synthesis CR-1.]

### C-16: getVariantId() parses ResourceLocation on every access
File: `content/entity/ResourceSlime.java:101`. Called from `getParticleType()` (hot path) and
others; allocates a `ResourceLocation` per call though the id is stable. Cache the parsed value.

### C-17: FrogEggItem.use() plays FROGSPAWN_HATCH on placement
File: `content/item/FrogEggItem.java:106`. A hatch sound when placing is misleading; prefer
`BOTTLE_EMPTY` or a place sound. [Synthesis CR-18.]

## Withdrawn mid-audit
- C-2 (SlimeMilkSourceBlock.tick double-schedule) - withdrawn after analysis; no bug.
- C-4 (ResourceFrog.mobInteract client/server order) - withdrawn; behavior correct and intentional.
- C-6 (SlimeMilkerMenu quickMoveStack off-by-one) - withdrawn; `hotbarEnd = SLOT_COUNT + 36 = 38`
  is correct because `moveItemStackTo` uses an exclusive end.

## Summary
| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH | 3 (C-1 rejected; C-3, C-5 valid) |
| MEDIUM | 2 (C-7, C-8) |
| LOW | 8 |
