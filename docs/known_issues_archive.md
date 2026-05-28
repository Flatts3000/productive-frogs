# Known Issues Archive

Resolved issues lifted out of [known_issues.md](./known_issues.md). Kept here so playtest reports referencing an issue already fixed in a newer build still find searchable context (what shipped, when, and why).

**Convention:** when an open issue in `known_issues.md` is marked 🟢, move the whole entry here. The active doc stays focused on truly open concerns plus by-design V1 limitations; this doc absorbs everything that shipped.

Newest-first within each section. Section ordering loosely tracks the area of the mod the fix touched.

---

## Resolved issues

> **Batch fix `fix/known-issues-batch` (2026-05-28).** Seven Sky-Frogs-reported issues fixed together: four in the Slime Milk fluid/block/bucket subsystem and three in the frog lifecycle. All land with `build` + `runGameTestServer` green (73 GameTests, 3 new). Items marked **(runClient pending)** are code-complete and pass the automated gates, but their behavior is a client-render / fluid-flow / time-and-AI surface that GameTest cannot observe (per docs/testing.md), so they want an in-world smoke pass before the release is cut.

### 🟢 Flowing Slime Milk displaced frogspawn / water sources / other milk sources - resolved (2026-05-28) (runClient pending)
Flowing Slime Milk washed away frogspawn / Primed Frog Eggs, water source blocks, and neighboring Slime Milk source blocks the way flowing water sweeps off a plant. Fix: `slime_milk` now uses custom `SlimeMilkFluid.Source` / `Flowing` subclasses (`content/fluid/SlimeMilkFluid`) that override `FlowingFluid#canSpreadTo` - the choke point vanilla checks before `spreadTo` replaces (destroys) the block at a target - to refuse to spread into frogspawn, `PrimedFrogEggBlock`, or any fluid SOURCE (`FluidState#isSource()`, covering water + other milk sources). Flowing-into-flowing, air, and the normal slope/level math are unchanged. Wired in `PFFluids` (holder types updated to the subclasses). Flow behavior is GameTest-unfriendly - confirm in-world.

### 🟢 Frogs drowned in Slime Milk - resolved (2026-05-28) (runClient pending)
Resource Frogs (and players) took air-loss / drowning damage standing in Slime Milk, which is poured into the shallow pools frogs work over. Fix: `PFFluidTypes` Slime Milk `FluidType` now sets `.canDrown(false)` (was `true`); `FluidType#canDrownIn` returns false so no mob drains air or drowns in milk. Still swimmable. Air-supply behavior isn't covered by GameTest - confirm in-world.

### 🟢 Bucketing + replacing Slime Milk reset its spawns-remaining counter - resolved (2026-05-28)
Re-bucketing a partially-depleted source and re-placing it refilled the `SPAWNS_REMAINING` depletion counter to full, defeating depletion. Fix: a new `productivefrogs:spawns_remaining` int data component (`PFDataComponents`); `SlimeMilkSourceBlock#pickupBlock` stamps the source's current count onto the filled bucket (only for a variant-carrying source), and `SlimeMilkBucketItem#checkExtraContent` restores it onto the re-placed source (after `onPlace` seeds the default). A freshly-milked bucket carries no such component and still makes a full source. Verified by the new `slimeMilkBucketRoundTripPreservesSpawnsRemaining` GameTest.

### 🟢 Jade "Slime spawns left" readout stuck at full count - resolved (2026-05-28) (runClient pending)
The Slime Milk source's Jade tooltip stayed pinned at `cap / cap` and never reflected the live remaining count (the server-side counter decremented correctly - proven by `slimeMilkSourceDecrementsSpawnsRemainingEachSpawn` - so this was a client display/sync gap). Fix: the spawns-left readout moved from a plain client-blockstate read to a dedicated `MilkSourceProvider` that is both `IServerDataProvider` and `IBlockComponentProvider` (the same live-server-fetch shape as the egg hatch countdown), registered in both `register` and `registerClient` with the new `milk_source` UID and its required `config.jade.plugin_productivefrogs.milk_source` lang key. It now re-reads the authoritative server count on Jade's interval, so it counts down live. Client-only - confirm in-world.

### 🟢 Primed frogspawn hatch delay made deterministic + config-exposed - resolved (2026-05-28)
Primed Frog Eggs inherited vanilla's random `[3600, 12000)`-tick hatch window. Fix: `PrimedFrogEggBlock` now schedules a fixed delay from the new `lifecycle.primedFrogspawnHatchTicks` config (default 3600 / 3 min); vanilla frogspawn keeps its random window. Verified by the new `primedEggSchedulesDeterministicHatchDelay` GameTest.

### 🟢 Tadpole growth + frog breeding times made config-exposed - resolved (2026-05-28) (runClient pending)
Both inherited vanilla pacing with no pack-facing knob. Fix: new `lifecycle.tadpoleGrowthTicks` (default 24000) - `ResourceTadpole#aiStep` adds extra age per tick to mature in the configured window (accelerate-only: values at/above vanilla's 24000-tick ceiling keep stock pace; slime-ball feeding still composes additively; vanilla tadpoles untouched; `Tadpole.age` exposed via AT). And `lifecycle.breedingCooldownTicks` (default 6000) - `ResourceFrog#spawnChildFromBreeding` re-applies the configured re-breed cooldown after `super`. Defaults are no-ops vs vanilla. The time/AI behavior isn't GameTest-friendly - confirm faster maturation + cooldown in-world.

### 🟢 Clay Ball Froglight smelted into a brick instead of clay balls - resolved (2026-05-28)
The `clay_ball` variant Froglight smelted to `minecraft:brick`, the only variant that didn't smelt back to the resource it represents (its primer item) - bone -> bone, coal -> coal, copper -> copper ingot, etc. It applied vanilla's clay-ball -> brick furnace transformation instead of returning the clay ball it was primed with. Fix: changed the smelt result to `minecraft:clay_ball` in both the generator source of truth (`scripts/generate_v1_1_variants.ps1`, the `clay_ball` row's `Smelt`) and the generated recipe (renamed `configurable_froglight_clay_ball_to_brick.json` -> `configurable_froglight_clay_ball_to_clay_ball.json`, matching the `_to_<resource>` naming of the others). Datapack loads cleanly; all 73 GameTests pass.

### 🟢 Frogs from crafted frogspawn now start at baseline stats - resolved (2026-05-28)
A frog matured from a crafted / Spawnery (non-bred) frogspawn rolled a random `[1, 3]` starter stat band (reported as a `2/1/3` frog). Decision (confirmed 2026-05-28): non-bred frogs start at **baseline** `1/1/1` (`FrogStats.STAT_MIN`); breeding is the only path above baseline. Fix: `ResourceFrog#finalizeSpawn` calls `applyBaselineStats()` instead of a starter roll; the `breeding.starterStatMin` / `starterStatMax` config keys and the roll helpers were removed; `docs/frog_breeding.md` updated. Bred frogs (stats applied in `ResourceTadpole#ageUp` after `finalizeSpawn`) and disk-loaded frogs (`statsInitialized`) are unaffected. Verified by the new `nonBredFrogMaturesToBaselineStats` GameTest.

### 🟢 Lapis slime miscategorized as Cave (should be Geode) - resolved (v1.5.1, 2026-05-27)
The `lapis` slime variant (`data/productivefrogs/productivefrogs/slime_variant/lapis.json`) shipped as `category: cave`, but lapis lazuli is a gemstone and belongs with the Geode gems (amethyst, diamond, emerald, fluix, ...). It was therefore farmed by the Cave frog, and `SlimeInfusionHandler`'s species lock meant lapis lazuli primed a Cave Slime rather than the thematically correct Geode Slime. Flagged by the Sky Frogs pack, which places lapis in its Geode tier. Fix: flipped the one field to `"category": "geode"`. The lapis slime / froglight / milk colour is unchanged (it tints from the variant's own `primary_color`, not the category). Corrected the player-facing JEI parent-species blurbs in the same pass: lapis (and diamond, which had the identical pre-existing error) were listed as Cave primer examples, but with species-locked infusion they only work on a Geode Slime, so both moved to the Geode blurb - which was also refreshed from its stale "emerald (post-V1.1)" text to the real gem roster. The legacy pre-v1.0 docs (`categories_and_tiers.md`, `slime_sourcing.md`, `progression.md`) still group lapis under the old MINERAL tier, but they carry SUPERSEDED banners and use the defunct abstract-category model wholesale, so they were left for a separate legacy-doc rewrite rather than half-corrected here.

### 🟢 Slime Milker had no crafting recipe - resolved (Spawnery PR #114, 2026-05-26)
The Slime Milker shipped with a loot table but no crafting recipe, so survival players couldn't obtain it (creative / `/give` only - or breaking an already-placed one). Added `data/productivefrogs/recipe/slime_milker.json`: shaped, planks-on-top, 5 cobblestone + 3 planks + 1 slime ball (centered), not config-gated. Shares the Spawnery's frame (centre ingredient distinguishes them). Pinned by `SpawneryRecipeTest`.

### 🟢 Slime Milk buckets collapsed to one entry in JEI - resolved (Spawnery PR #114, 2026-05-26)
JEI listed only one Slime Milk Bucket while the creative tab showed every variant, because `ProductiveFrogsJeiPlugin.registerItemSubtypes` had no `SLIME_VARIANT` subtype interpreter for `slime_milk_bucket` (it had one for the Froglight + spawn egg). Registered the existing `slimeVariantInterp` for it, so each variant is now a distinct JEI ingredient with its own info page. Pre-existing bug surfaced during Spawnery testing; confirmed in-client.

### 🟢 No item tooltips in the mod's container GUIs - resolved (Spawnery PR #114, 2026-05-26)
Neither the Slime Milker nor the Spawnery GUI rendered slot tooltips. Root cause (confirmed from `neoforge-21.1.230-sources.jar`): NeoForge 1.21.1's `AbstractContainerScreen.render` draws background / slots / labels / dragging item but never calls `renderTooltip`, so a screen overriding only `renderBg` (both of ours) shows none. Both `SpawneryScreen` and `SlimeMilkerScreen` now override `render` to call `super.render(...)` then `renderTooltip(...)`. Restored tooltips on the shipped Milker too; confirmed in-client.

### 🟢 Spawnery primer-required decision - resolved (Spawnery PR #114, 2026-05-26)
Design question on the (new) Spawnery: should an empty primer produce vanilla frogspawn? Decision: **a primer is required** - an empty or unrecognised primer produces nothing (no ignite, no fuel burned). A slime ball primes plain vanilla frogspawn; a `spawnery_primer/<species>` item primes that species. Implemented via `SpawneryBlockEntity.serverTick`'s `canProduce` gate + `SpawneryInventory.isValidPrimer`; `complete()` always consumes the primer.

### 🟢 JEI info text said "Configurable Froglight" instead of "Froglight" - resolved
**Original symptom**: two JEI description strings called the block by its registry-flavored id rather than its display name - `productivefrogs.jei.variant_slime.info` ("...drops a Configurable Froglight stamped with this variant...") and `productivefrogs.jei.frog.info` ("...drops a Configurable Froglight stamped with that variant..."). Everywhere else the block reads "Froglight" (its `block.productivefrogs.configurable_froglight` display name and every per-variant name), so this was an internal naming leak, not a deliberate term.

**Resolution**: edited both strings in `en_us.json` to "...drops a Froglight stamped with [this/that] variant..." (pure lang edit, no code). Added a copy-lint guard to `LangCompletenessTest` (`noPlayerFacingValueSaysConfigurableFroglight`) that fails the build if any lang *value* contains "Configurable Froglight", so the registry id can't leak back into player-facing copy.

### 🟢 Cross-mod variant slimes showed a raw lang key in the Froglight tooltip - resolved
**Original symptom**: the Configurable Froglight JEI info page read "Dropped when a Cave Frog eats a **entity.productivefrogs.resource_slime.osmium**. Smelts in a furnace to the resource it represents." - the slime name rendered as its untranslated entity translation key instead of a readable name. Affected every cross-mod variant (osmium, tin, lead, and the rest of the `c:`-tag pool); hand-authored base / v1.1 variants rendered fine.

**Cause**: two gaps. (1) `ProductiveFrogsJeiPlugin` built the interpolated slime name with raw `Component.translatable("entity.productivefrogs.resource_slime." + variant)` - the one variant display path that lacked the title-case fallback the Froglight / Slime Milk bucket / spawn-egg display names already used. (2) `en_us.json` shipped per-variant keys only for the 33 base + v1.1 variants; the 24 cross-mod variants we ship JSONs for had no explicit entry in any of the five per-variant key families.

**Resolution**:
- Applied the shared `translatableWithFallback` + `VariantNames.titleCase` fallback to the JEI slime-name interpolation in `ProductiveFrogsJeiPlugin`, matching the other four variant surfaces. The shared `variantSlimeName` feeds the variant_slime / variant_froglight / slime_milk info pages, so the single change fixed all three.
- Added explicit `en_us.json` keys for all 57 shipped variants across all five per-variant families (`entity.productivefrogs.resource_slime.*`, `item.productivefrogs.slime_bucket.*`, `item.productivefrogs.resource_slime_spawn_egg.*`, `block.productivefrogs.configurable_froglight.*`, `item.productivefrogs.slime_milk_bucket.*`) - 153 new keys. The `slime_milk_bucket` family had previously shipped zero per-variant keys (it relied entirely on the fallback). Names follow the existing convention (full resource name + " Slime", e.g. "Osmium Slime"); Powah crystals carry "Crystal" ("Blazing Crystal Slime"), and `pink_slime` collapses the redundant suffix ("Pink Slime", not "Pink Slime Slime").
- The title-case fallback stays as the safety net for downstream datapack-only variants we don't ship (a pack-added variant cannot ship its own lang, since lang is a client asset and datapacks are server data).
- New `scripts/audit_lang_keys.py` enumerates every shipped variant against the five families and reports/fills gaps; re-run it after adding a variant JSON to catch a missing-key regression.

### 🟢 Resource Slime captured with an empty bucket, not a water bucket - resolved (v1.2.x)
**Original symptom**: right-clicking a size-1 Resource Slime with an empty bucket did nothing; a water bucket was what captured it into a Slime Bucket. Inherited vanilla `Bucketable` behaviour (fish/axolotl/tadpole capture with a water bucket), but it read wrong for a non-aquatic slime - a player reaches for an empty bucket and nothing happens.

**Resolution**: `ResourceSlime.mobInteract` no longer bridges to vanilla `Bucketable.bucketMobPickup` (which hardcodes `WATER_BUCKET`). A new `tryEmptyBucketCapture` is a minimal re-implementation keyed on `Items.BUCKET` - same pickup sound, `saveToBucketTag` NBT write, `ItemUtils.createFilledResult` stack handling, and FILLED_BUCKET advancement trigger (guarded `instanceof ServerPlayer`), but matching the empty bucket. Pinned by the `emptyBucketCapturesSlimeWaterBucketDoesNot` GameTest: an empty bucket captures and discards the slime; a water bucket leaves both the slime and the bucket untouched.

### 🟢 Object order didn't follow the canonical species progression - resolved (v1.2.x)
**Original symptom**: creative tabs, JEI, and the recipe book presented the six species in the `Category` enum's declared (roughly alphabetical) order - `BOG, CAVE, GEODE, TIDE, INFERNAL, VOID` - instead of the intended player-progression order `CAVE -> GEODE -> BOG -> TIDE -> INFERNAL -> VOID` (see [canonical_ordering.md](./canonical_ordering.md)).

**Resolution**: reordered the `data.Category` enum constants to the canonical sequence. Every user-visible surface iterates `Category.values()` (per-species `DeferredRegister` insertion in `PFItems` / `PFBlocks`, the creative tab in `PFCreativeTabs`, JEI category pages, `PFClientEvents`), so they all follow the new order automatically - no per-surface change. Confirmed save-safe by the audit the spec required: persistence is by name (entity NBT writes `getCategory().name()` and reads `Category.valueOf`; the `contained_category` data component uses `.persistent(Category.CODEC)`, which is `StringRepresentable`-by-name). The only `ordinal()` uses are transient network sync (synced data + `STREAM_CODEC`), regenerated each session from the name-based persisted value, so nothing on disk remaps.

### 🟢 MC 1.21.1 port: per-category and per-variant tints not rendering on items - resolved (v1.0)
Shipped as part of the 1.21.1 port (PR #81). The five root causes that surfaced from the 2026-05-23 playtest screenshot (frog egg layer order, missing spawn-egg color handler registrations, vanilla `SpawnEggItem` auto-color not firing for our subclasses, BlockColor not propagating to BlockItem, bucket silhouette textures rendering as empty overlays) all landed before v1.0.0 cut.

Fixes applied:
- Alpha-bit normalization: every `ItemColor` / `BlockColor` handler in `PFClientEvents` now wraps its non-`-1` return through `opaque(rgb) = 0xFF000000 | rgb` so 24-bit RGB values from `Category.tintRgb()` / `SlimeVariant.primaryColor()` don't get interpreted as ARGB with `alpha == 0`.
- Frog Egg item model: moved tint from `tintIndex == 1` (bottle glass) to `tintIndex == 0` (potion_overlay liquid).
- All spawn eggs: explicit `RegisterColorHandlersEvent.Item` registrations covering parent species, variant slimes, and the per-category frog/tadpole sets.
- Primed Frog Egg + Resource Froglight in-hand BlockItems: explicit per-item color handlers since BlockColor doesn't auto-propagate to BlockItem in 1.21.1.
- Slime/Tadpole bucket silhouettes: regenerated 16x16 RGBA textures with body+face features so the runtime tint multiply actually has body pixels to color.

### 🟢 Coal Resource Slime's eye dots are nearly invisible (dark on dark) — resolved
Took option 2 from the fix-path discussion: bumped `data/.../slime_variant/coal.json` `primary_color` from `0x2A2A2A` (near-black) to `0x585858` (charcoal grey), and `secondary_color` from `0x101010` to `0x202020`. Body tint now multiplies silhouette body pixels to a charcoal value while the preserved eye dots stay near-black — visible contrast restored. Coal variant still reads as "coal" but no longer flattens against its own eye dots.

### 🟢 World-spawned parent slime species render with vanilla green — resolved
**Original symptom**: spawning a Cave / Geode / Tide / Void Slime via spawn egg or `/summon` showed a generic green slime cube. The per-species inner-cube PNGs were correctly themed (Cave terracotta-brown, Geode amethyst-purple, Tide blue-cyan, Void dark-purple) but the **outer translucent shell** still pulled from the hardcoded `SlimeRenderer.SLIME_LOCATION` (vanilla green), and that green dominated the visual.

**Resolution**: added a generic `TintedSlimeOuterLayer` (drop-in replacement for vanilla `SlimeOuterLayer`, parameterised by a constant ARGB tint) and updated each parent renderer to swap it in via `this.layers.removeIf(l -> l instanceof SlimeOuterLayer); this.addLayer(new TintedSlimeOuterLayer(...))`. The layer routes texture lookups through the parent renderer's `getTextureLocation`, so both the inner cube and the outer shell read from the same per-species PNG. Per-species tints:
- Cave Slime → `0xFF8A8A8A` stone grey
- Geode Slime → `0xFF6CDCD7` diamond cyan (matches the `diamond` variant's `primary_color`)
- Tide Slime → `0xFF3F76E4` water blue (vanilla water tint)
- Void Slime → `0xFF5E3782` end-portal-frame purple

Modelled directly on `ResourceSlimeRenderer` + `ResourceSlimeOuterLayer`, but with a constant tint (every Cave Slime is grey, every Geode Slime is cyan, etc.) rather than per-state variant lookup — that shape fits the parent species exactly. The outer-shell-tint-is-known-limitation comment that previously lived on each parent renderer is gone since the limitation is gone.

### 🟢 Bucket of Slime + Bucket of Tadpole silhouettes positioned outside the bucket — resolved
**Original symptom (PR #66 era)**: silhouettes were invisible — buckets rendered as plain iron buckets. PR #73 wrote the silhouette PNGs and they became visible, but at the wrong position: the body-shaped silhouettes painted onto the bucket body (rows 5-10 in the 16×16 frame) rather than peeking from the bucket's mouth opening (rows 1-5, cols 5-10), so creatures read as "stuck on the front of the bucket" instead of "contained inside it."

**Resolution**: Redrew `slime_silhouette.png` and `tadpole_silhouette.png` as small head sprites positioned in the bucket's natural mouth interior (cols 5-10, rows 1-5). Slime is a 6×4 cube with 2 eyes at row 3 (cols 6, 9) whose crown rises one row above the rim back. Tadpole is a 4-6 wide rounded head with 2 eyes at row 4 (cols 6, 9) sitting fully inside the bucket. Body pixels at (220,220,220) so `BucketedCategoryTint` multiplies cleanly to category colour; eye pixels at (32,32,32) preserved dark across all 12 variants. Bucket rim sides at cols 3-4 + 11-13 and front rim at row 6 stay intact (silhouette pixels only occupy cols 5-10 of the rim region), framing the head naturally.

### 🟢 Slime Milker progress bar / arrow doesn't animate — resolved (PR #74)
**Original symptom**: arrow between input and output slots stayed grey throughout the cook with no visual progress feedback.

**Root cause**: MC 1.21.x moved vanilla's furnace arrow into a sprite-atlas entry (`gui/sprites/container/furnace/burn_progress.png`) instead of inlining it in `furnace.png` at (176, 14). Our screen's blit code pulled from `(176, 14)` of the GUI background — an empty region — so the partial-width blit had no source pixels and rendered nothing visible.

**Resolution (PR #74)**: `generate_slime_milker_gui.ps1` now composites the vanilla `burn_progress.png` sprite (24×16) into our GUI background at `(176, 14)` so the existing `SlimeMilkerScreen.renderBg` partial-width blit pulls from a populated region. End-to-end data flow already worked: `SlimeMilkerBlockEntity.serverTick` increments `cookProgress` per tick (pinned by `slimeMilkerBeCompletesCookAndOutputsVariantMilkBucket` GameTest), `SlimeMilkerMenu.addDataSlots(dataAccess)` syncs the value via vanilla's standard ContainerData mechanism, and the screen reads `menu.getCookProgress() / menu.getCookTotal()` to compute the filled width. Only the missing sprite at the blit's source coordinates was broken.

### 🟢 Slime Milker input slot is not vertically centered relative to the output — resolved
**Original symptom**: input slot sat at vanilla furnace's y=17 (top of the stacked input/fuel column) while the output sat at y=35 (vanilla result-slot row). With no fuel slot in the milker, the asymmetry looked awkward and the arrow ran diagonally instead of horizontally between the two.

**Resolution**: bumped `SlimeMilkerMenu.INPUT_SLOT_Y` from 17 to 35 so both item positions sit on the same horizontal line as the existing arrow at y=34. Updated `scripts/generate_slime_milker_gui.ps1` to copy the vanilla furnace's 20×20 input slot bevel from (54, 15) and re-paste it at (54, 33), then erase the original input slot well + the obsolete fuel-system column underneath (single 20×55 panel-grey fill from y=15 to y=70). The input slot frame now sits at rows 34-50 and aligns visually with the output result-slot at rows 30-54 (both centred at y=42). Regenerated `slime_milker.png` ships in the same commit.

### 🟢 Slime Milker output slot doesn't center its item — resolved
Bumped `SlimeMilkerMenu.OUTPUT_SLOT_X` from 112 to 116 and `OUTPUT_SLOT_Y` from 30 to 35 so the menu uses vanilla furnace's actual result-slot coordinates (the previous (112, 30) values were a mis-quote of vanilla — real furnace result slot is at (116, 35)). The output bucket now centres against the result-slot frame drawn by the inherited GUI texture.

### 🟢 Slime Milk buckets have eyes — they shouldn't; Slime Buckets should — resolved
Reverted the eye-overlay portion of PR #67. `scripts/generate_slime_milk_textures.ps1`'s `Build-BucketTexture` no longer writes the two dark pixels at (6,3) and (9,3); replaced with a comment block explaining the design pivot. Re-ran the script to regenerate the 14 variant milk-bucket PNGs without eyes. The animated-fluid changes from the same PR stay (still correct). The Slime Bucket (`slime_silhouette.png` layer0) keeps its eyes, which was always the right surface.

### 🟢 Spawn egg textures don't read as eggs — they look like dead 2D creatures — resolved
**Original symptom**: the 3 base textures from PR #69 rendered the full creature silhouette tinted per variant — read as "dead frogs / tadpoles / slime blobs" rather than "spawn eggs." Per playtest, the egg ITEM should be egg-shaped with minor accompaniments suggesting the animal's silhouette (matching vanilla 1.21.x style), not a full creature portrait and not a generic ovoid either.

**Resolution**: regenerated all three at 16×16 from source PNGs, side-by-side reviewed against the shipped versions and vanilla references, then tone-mapped to the 4-tone greyscale palette below. Selected:
- `frog_spawn_egg.png` — vanilla 1.21.x frog spawn egg shape tone-mapped to 4-tone greyscale (egg body + visible hind-leg bumps at row 14). Lifts vanilla's design directly since it already matches the "egg + leg accompaniments" pattern.
- `slime_spawn_egg.png` — 1024×1024 source PNG downscaled (nearest-neighbour) and tone-mapped: rounded blob-cube egg silhouette suggesting the slime cube shape.
- `tadpole_spawn_egg.png` — 1024×1024 source PNG similarly downscaled + tone-mapped: rounded head + tapering tail-flick at the back.

Each PNG uses a 4-tone palette (highlight 220, mid body 150, shadow 100, dark outline 50) so the existing `productivefrogs:contained_category` / `productivefrogs:slime_variant` runtime tint multiplies cleanly to give each variant a distinct coloured egg with preserved shading depth. No model JSON / item JSON changes needed — the same single-layer tint pipeline still applies. No faces, no eyes — silhouettes only.

### 🟢 Frog tongue kill on Resource Slime emits both Froglight AND slimeballs — resolved
Took option 2 from the fix-path discussion: added a new entity-type tag `productivefrogs:frogs` listing both `minecraft:frog` and `productivefrogs:resource_frog`, then added an `inverted` `entity_properties` condition on the Resource Slime loot table (`data/productivefrogs/loot_table/entities/resource_slime.json`) that gates the slimeball drop on `attacker != #productivefrogs:frogs`. Frog tongue kills now drop the variant Froglight only; non-frog kills (sword, fall damage, environmental) still drop slimeballs per vanilla parity.

The single-Froglight-per-kill design is preserved by `FrogTongueDropHandler.dropFroglightAtFrog`: each invocation creates exactly one `ItemStack` (default count 1) and spawns it as one `ItemEntity`, so a single tongue-hit cannot multiply the drop.

### 🟢 Variant-stamped Froglights render as 2D items instead of 3D placeable blocks — resolved
Took option 1 from the fix-path discussion. `configurable_froglight` is now a `BlockItem` backed by `ConfigurableFroglightBlock` (a `RotatedPillarBlock` + `EntityBlock`) with a single block ID and one `ConfigurableFroglightBlockEntity` per placement. The BE stores the `SLIME_VARIANT` identifier, which drives a per-variant tint via a `BlockColor` lambda reading the BE through `BlockEntity#getLevel().registryAccess()` (the `BlockAndTintGetter` parameter is often a `RenderChunkRegion` and doesn't expose `registryAccess`).

Break + lighting mechanics match vanilla froglight exactly — same `RotatedPillarBlock` base, `strength(0.3F)`, `lightLevel(state -> 15)`, `sound(SoundType.FROGLIGHT)`, default `NORMAL` push reaction, `survives_explosion` loot condition. The only behavioral addition is `minecraft:copy_components` (source `block_entity`, include `productivefrogs:slime_variant`) on the loot table so the variant survives the place → break round-trip. The BE additionally implements `collectImplicitComponents` so creative pick-block stamps the variant onto the picked item without extra code.

Translation keys migrated from `item.productivefrogs.configurable_froglight.*` to `block.productivefrogs.configurable_froglight.*` to match the `useBlockDescriptionPrefix()` setting on the item properties. New GameTest `variant_froglight_round_trip_preserves_variant_through_place_and_break` pins the BE write → loot table copy → item drop chain so a future loot-table or BE refactor that drops the variant fails closed.

### 🟢 Frog / Slime / Tadpole spawn eggs share one silhouette — resolved
Three distinct base PNGs now ship at `textures/item/{frog,tadpole,slime}_spawn_egg.png`, generated from source silhouettes and tone-mapped through `scripts/process_silhouette.ps1` so they render cleanly under the spawn-egg tint pipelines: body pixels brighten to (220,220,220) so the runtime `ItemTintSource` multiplication renders the category color, dark accent pixels under 64 preserved as-is so eyes stay visible across all category tints. The relevant tint sources are `productivefrogs:contained_category` (frog + tadpole eggs), `productivefrogs:slime_variant` (variant slime eggs), and `minecraft:constant` (parent-species slime eggs - Bog / Cave / Geode / Tide / Infernal / Void use a fixed RGB rather than a runtime data component). `productivefrogs:bucketed_category` is the bucket-item tint and is NOT used here. Three new model JSONs at `models/item/{frog,tadpole,slime}_spawn_egg.json` route each shape to its own texture, and every spawn-egg item JSON points at the right shape model - frogs at frog, tadpoles at tadpole, all variant + parent-species + category slime eggs at slime. The old shared `category_spawn_egg.png` / model JSON were deleted.

### 🟢 Slime Milk fluids don't animate (static surface) — resolved
`scripts/generate_slime_milk_textures.ps1` now tints the **full vanilla water_still / water_flow vertical strips** (32 frames each) instead of just the top frame. Output: `<variant>_slime_milk_still.png` is now 16×512 (32 frames), `<variant>_slime_milk_flow.png` is 32×1024 (32 frames). Each PNG ships a sibling `.mcmeta` with `{"animation": {"frametime": 2}}` matching vanilla water cadence. Source-block fluid surface now moves like vanilla water in the variant's tinted hue.

### 🟢 Resource Froglights appear twice in the creative tab — resolved
Dropped the `for (var entry : PFItems.RESOURCE_FROGLIGHT_ITEMS.values())` loop from `PFCreativeTabs.PRODUCTIVE_FROGS_TAB.displayItems`. The 6 broad-strokes category Froglight BlockItems stay registered (existing worlds load fine; `FrogTongueDropHandler` still emits them as the no-variant fallback drop) — only their creative-tab listing was removed. The variant-stamped `configurable_froglight` stacks remain in the tab and read with their canonical resource names, so no Froglight appears twice. Creative testers wanting a specific category block can use `/give productivefrogs:metallic_froglight` etc.

### 🟢 Slime splash particle uses vanilla green regardless of slime variant — resolved
Vanilla's `Slime#tick` reads `getParticleType()` once per particle in its jump-landing splash loop, so overriding that single method swaps the colour without touching the spawn cadence or count. Each PF slime now returns `new DustParticleOptions(<tint>, 1.0F)` in place of `ParticleTypes.ITEM_SLIME`:

- `ResourceSlime#getParticleType` reads the variant's `primary_color` when present, falls back to `Category.tintRgb()` otherwise — so iron Resource Slimes spray silver, redstone slimes spray red, etc.
- `BogSlime`, `CaveSlime`, `GeodeSlime`, `TideSlime`, `InfernalSlime`, `VoidSlime` each pin their respective species colour.
- The vanilla magma cube keeps its vanilla orange splash since we don't subclass it.

The particle shape changes from `ITEM_SLIME` (slimeball icon) to `DUST` (colored speck) — slightly different from vanilla but each variant now carries its own visual signal in-world.

Follow-up (deferred): the vanilla magma cube also inherits a hardcoded particle that doesn't match the magma_cream Resource Slime variant. To fix it without subclassing MagmaCube would require a custom registered particle type with a runtime tint argument plus a swap at the call site. Left open since the per-variant signal is delivered for the primary surface (Resource Slimes + parent species), and players rarely confuse magma cubes with their resource analogue.

### 🟢 Per-variant + per-category items — tints + JEI subtypes shipped

**Tint pipeline.** Every variant/category surface resolves its colour from the data-component chain:

- **Variant Slime Spawn Eggs** carry `SLIME_VARIANT` on default properties; JSON tint source is `productivefrogs:slime_variant` (layer = `primary`). Each variant renders its resource colour (iron-silver, copper-orange, gold-yellow, …). Pinned by `PFRegistryTest#variantSlimeSpawnEggCarriesSlimeVariantComponent` cases.
- **Slime Bucket** uses `BucketedCategoryTint` (variant-first lookup via `ResourceTadpoleBucketItem.readVariant` → `PFRegistries.SLIME_VARIANT`, broader category fallback). `ResourceSlime.saveToBucketTag` writes both `Category` and `Variant` to `BUCKET_ENTITY_DATA`; pinned by `slime_bucket_round_trip_preserves_variant` GameTest.
- **Configurable Froglight** uses `SlimeVariantTint` reading `SLIME_VARIANT` directly.
- **Frog / Tadpole spawn eggs**, **Primed Frog Egg blocks**, **Frog Egg bottle**, **Resource Tadpole Bucket** — all use `ContainedCategoryTint` (or per-category `BlockColor`).
- **Broad-strokes Froglight blocks** (`<category>_froglight`) — per-category `BlockColor` in `PFClientEvents.onRegisterBlockColors`.

Variant primary colours come from each `data/productivefrogs/productivefrogs/slime_variant/<name>.json`'s `primary_color`. Category colours come from `Category.tintArgb()`.

**JEI subtype plugin.** `ProductiveFrogsJeiPlugin` (under `client/jei/`, auto-discovered via `@JeiPlugin`) calls `registerFromDataComponentTypes` for the four items that share a single registry id but vary by component:

| Item | Subtype key |
|---|---|
| Slime Bucket | `BUCKET_ENTITY_DATA` (Variant + Category NBT) |
| Resource Tadpole Bucket | `BUCKET_ENTITY_DATA` (Category NBT) |
| Configurable Froglight | `SLIME_VARIANT` |
| Frog Egg bottle | `CONTAINED_CATEGORY` |

The creative tab in `PFCreativeTabs.PRODUCTIVE_FROGS_TAB.displayItems` emits one stamped stack per variant/category for each of these (variant slime buckets, category tadpole buckets, primed frog egg bottles, configurable froglight stacks), so JEI surfaces them as distinct entries. JEI is `compileOnly` at build time + `runtimeOnly` at play time — the plugin class is dead code if a player runs without JEI.

**Follow-up — display names shipped.** Slime Bucket now resolves via `SlimeBucketItem` (subclasses `MobBucketItem`, `getName(ItemStack)` reads Variant > Category > base from `BUCKET_ENTITY_DATA`). Resource Tadpole Bucket already had category-aware names since `ResourceTadpoleBucketItem`; Frog Egg bottle has category-aware names via `FrogEggItem.getName`. So JEI text-search for `iron` now finds the iron Slime Bucket in addition to the iron Slime Spawn Egg. Lang file ships all category + variant keys per surface; missing translations surface as the raw key (Minecraft's standard fallback).

### 🟢 Slime Milker furnace-style GUI + hopper I/O shipped

**Shipped — GUI**: the right-click-to-instant-convert appliance is replaced with a `SlimeMilkerBlockEntity`-backed furnace-shaped block:

- GUI with one **input slot** (accepts a Slime Bucket only), one **output slot** (filled with the matching variant-typed Slime Milk bucket on cook completion), and a **progress bar**.
- **Cook time: 100 ticks (5 s)** per conversion. No fuel — the slime IS the input.
- Variant lookup unchanged: `SlimeMilkerBlock.readBucketVariant` → `PFFluidTypes.VARIANTS` → `PFItems.MILK_BUCKETS`. Fail-closed semantic: if the input bucket has no `Variant` component, cook progress stays at zero and no output appears. Covered by GameTests.
- Inventory drops on break via `playerWillDestroy` so a broken milker doesn't swallow buckets.
- Placeholder GUI background ships at `assets/productivefrogs/textures/gui/container/slime_milker.png` — basic grey container with slot wells highlighted. Polish texture (full Productive Frogs theming) tracked separately.

**Shipped — hopper I/O.** The Milker's storage is now a `SlimeMilkerInventory extends ItemStackHandler` (the standard NeoForge 1.21.1 item-handler API). `Capabilities.ItemHandler.BLOCK` is registered with a side-aware provider in `PFModBusEvents` that mirrors the vanilla furnace convention: side `DOWN` returns an extract-only view of the OUTPUT slot, every other face (top, horizontal, and `null` for non-sided access) returns an insert-only view of the INPUT slot restricted to `productivefrogs:slime_bucket`. Pinned by 3 new GameTests: a synthetic capability-routing check, a real `HopperBlockEntity` pushing Slime Buckets in from above, and another pulling milk buckets out from below.

### 🟢 Slime Milk integrates with tank mods — confirmed working

**Verification**: cross-referenced with [Productive Bees' honey fluid](https://github.com/JDKDigital/productive-bees/blob/dev-1.21.0/src/main/java/cy/jdkdigital/productivebees/common/fluid/HoneyFluid.java). PB ships **no** custom `Capabilities.Fluid.BLOCK` registration for their honey LiquidBlock — they rely entirely on the same `BaseFlowingFluid` + vanilla `LiquidBlock` pipeline we use, and downstream tank / pipe mods integrate via:

1. **Bucket item capability** — NeoForge's `CapabilityHooks.registerVanillaProviders` auto-registers `Capabilities.Fluid.ITEM` on every `BucketItem` subclass. Our Slime Milk buckets inherit from vanilla `BucketItem`, so the cap is live without any code from us. Pinned by the `milk_bucket_exposes_fluid_capability_for_tank_mods` GameTest.
2. **Source block pickup** — tank mods that pump from a fluid source block use vanilla `LiquidBlock` bucket-pickup mechanics. Our `SlimeMilkSourceBlock extends LiquidBlock` inherits that behavior unchanged.

If a specific tank mod ever turns out to need an explicit `Capabilities.Fluid.BLOCK` handler (e.g. one that doesn't go through vanilla bucket scoop), file a follow-up issue with the specific mod + version — we can register a `ResourceHandler<FluidResource>` wrapper at that point, similar to how Productive Bees adds fluid handlers to their machine block entities (centrifuge, etc.) but NOT to the raw honey fluid block.

### 🟢 Resource Slime per-variant texture — shipped

**Framework + assets both shipped.** `SlimeVariant` carries an optional `texture` field (`Optional<Identifier>`); all shipped variant JSONs declare one pointing at the matching `<variant>_resource_slime.png`. The renderer resolves in order:

1. Per-variant `texture` from the variant's datapack entry, when set.
2. Per-category fallback PNG (`<category>_resource_slime.png`) for variants without a custom texture and for category-only slimes.

PNGs are generated by `scripts/generate_variant_slime_textures.ps1`, which composites each variant's canonical vanilla block texture (`iron_block.png`, `copper_block.png`, `gold_block.png`, …) into the SlimeModel inner-cube UV layout on the matching per-category template. The outer translucent shell is tinted from the variant's `primary_color` via `ResourceSlimeRenderState.outerTint` → `ResourceSlimeOuterLayer.submit`, so each variant reads as a distinct resource block inside a tinted shell.

Schema + renderer round-trip covered by `SlimeVariantTest` (codec accepts JSONs with and without the field). End-to-end load + texture-field decoding pinned by the `slime_variant_datapack_registry_loads_initial_variants` GameTest's iron spot-check.

To regenerate the PNGs after a vanilla block texture changes upstream, or when adding a new variant:

```
.\scripts\generate_variant_slime_textures.ps1
```

The script auto-discovers the NeoForge minecraft-resources jar via glob under `build/moddev/artifacts/neoforge-*-client-extra-aka-minecraft-resources.jar` (so it survives NeoForge version bumps) and extracts it to a cache dir using the .NET `ZipFile` API (no `jar.exe` / JAVA_HOME dependency). Outputs land in `src/main/resources/assets/productivefrogs/textures/entity/slime/`. Windows-only — System.Drawing is in-box on Windows but needs libgdiplus on Linux/macOS.

### 🟢 Automated Slime Milker — shipped (GUI + hopper I/O)
The Milker is a furnace-shaped GUI block with input + output slots, a 100-tick cook progress, and fail-closed variant-bucket validation. Hopper compat is wired via `Capabilities.ItemHandler.BLOCK` with side-aware routing — top + horizontal faces accept SLIME_BUCKET pushes, the bottom face yields finished milk buckets. See the related entry above for the implementation details.

### 🟢 Slime hitboxes 4× too large at every size — fixed in PR #43
**Symptom**: Cave / Geode / Tide / Void / Resource Slimes had bounding boxes far larger than their rendered sprites. Attacking them required clicking visibly outside the body; they'd push the player from blocks away.
**Cause**: All five custom slime `EntityType.Builder` registrations used the pre-1.21 `sized(2.04F, 2.04F)` base. Vanilla `Slime#getDefaultDimensions` now scales the base directly by `getSize()` (no internal 0.255 multiplier). At size 1 the hitbox came out at 2.04 instead of 0.52 — 4× too large.
**Fix**: matched vanilla's current builder — `sized(0.52F, 0.52F)`, `eyeHeight(0.325F)`, `spawnDimensionsScale(4.0F)`. Regression-pinned by `custom_slimes_size_1_hitbox_matches_vanilla_slime` GameTest.

### 🟢 Confusing Froglight display names — fixed in PR #43
**Symptom**: in-game furnace / inventory tooltip showed `Metallic Froglight` / `Mineral Froglight` / etc. — internal category names that don't appear anywhere else in the player-facing UX.
**Fix**: renamed the six broad-strokes Froglight display strings to the canonical resource each one smelts to (Iron, Redstone, Diamond, Prismarine, Magma Cream, Ender Pearl). Registry IDs stayed `metallic_froglight` etc. so existing saves and tag entries aren't disturbed.

---

## Reopened / since-reverted history

Entries that were originally marked resolved, then reopened after design intent shifted, and have since been re-resolved (usually by reverting). Kept for the searchable rationale chain.

### 🟠 Slime Milk bucket textures should show slime eyes in the liquid — REOPENED (design pivot, since reverted)
PR #67 shipped this with two dark pixel dots at (6,3) and (9,3) overlaid on each variant's milk surface. **Playtest revealed the design intent was wrong**: Slime Milk is the extracted fluid, the slime itself isn't in the bucket anymore — eyes don't belong there. Eyes belong on the **Slime Bucket** (the bucketed live-slime entity), which already carries them via the `slime_silhouette.png` from PR #66. The revert has since landed — see the 🟢 "Slime Milk buckets have eyes — they shouldn't; Slime Buckets should — resolved" entry in the Resolved section above. Keeping this entry visible with the design-pivot note so the original "ship it" decision isn't silently invalidated.
