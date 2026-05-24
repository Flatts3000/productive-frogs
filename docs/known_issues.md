# Known Issues

Living tracker of playtest bugs, limitations, and workarounds for Productive Frogs. Player-facing concerns only — developer-facing test flakiness, code hygiene debt, and refactor ideas live in [backlog.md](./backlog.md) §Polish.

## Status legend

| Symbol | Meaning |
|---|---|
| 🔴 | Open. Fix pending. |
| 🟡 | Open. Workaround available — see entry. |
| 🟠 | Reopened. Was 🟢 resolved but design intent changed; entry kept so the original "ship it" decision and the revert rationale stay searchable. |
| 🔵 | Limitation by design for V1. May revisit in V2 (see [versioning.md](./versioning.md)). |
| 🟢 | Resolved. Listed here so playtest reports stay searchable. |

---

## Open issues

### 🟡 MC 1.21.1 port: per-category and per-variant tints not rendering on items — fixes in flight
**Branch:** `port/mc-1.21.1` (commit `441c786` and earlier).
**Symptom (from creative-tab screenshot on 2026-05-23):** Most data-component-driven items render plain white/grey instead of category-tinted. Affected:
- **Frog Egg bottle** (`productivefrogs:frog_egg`) — all 6 primed bottles show grey blob shape; only the unprimed bottle reads as blue.
- **Primed Frog Egg blocks** (all 6 categories) — render as flat grey discs instead of category-tinted frogspawn.
- **Slime Bucket** — plain bucket, no slime silhouette tint inside.
- **Resource Tadpole Bucket** — plain bucket, no tadpole silhouette tint inside.
- **All 28 spawn eggs** (4 parent slime species + 12 variant slimes + 6 frogs + 6 tadpoles) — plain grey/white instead of category/variant two-tone overlay.

**Root causes (multiple, untangled during the playtest):**
1. **Frog Egg item model layer order is wrong.** `frog_egg.json` uses vanilla `potion`/`potion_overlay` layers but the color handler tints `tintIndex == 1` (the bottle glass), not `tintIndex == 0` (the liquid). Need to swap — vanilla potion tints the *overlay* layer.
2. **PFClientEvents only registers item-color handlers for 12 variant slime spawn eggs**, not the 4 parent species or 12 category-keyed frog/tadpole eggs. The `for (Category cat : Category.values()) { ... break; }` block (lines 190-212 of `PFClientEvents.java`) is a vestigial outer loop — the inner loop runs once and only covers `RESOURCE_SLIME_SPAWN_EGGS`.
3. **Vanilla SpawnEggItem auto-color appears not to fire for our subclasses** under NeoForge 21.1.230. Even though `ItemColors.createDefault` should pick up `SpawnEggItem` subclasses via `BuiltInRegistries.ITEM.stream()`, none of the 4 parent slime spawn eggs (which have no custom handler) show tint. Fix: register an explicit `RegisterColorHandlersEvent.Item` lambda for every spawn egg, returning `getColor(layer)` on the SpawnEggItem.
4. **Primed Frog Egg block tint may not be reaching the in-hand model.** `primed_frog_egg.json` block model has `tintindex: 0` on both faces, and we register a BlockColor handler for each `PFBlocks.primedEgg(cat)`. But the BlockItem in inventory uses the same model and should auto-derive from BlockColor — needs investigation; possibly the `productivefrogs:block/primed_frog_egg` parent isn't being resolved correctly in item context.
5. **Slime / Tadpole bucket silhouette rendering is plain bucket with no overlay.** `slime_bucket.json` and `resource_tadpole_bucket.json` reference `productivefrogs:item/slime_silhouette` and `productivefrogs:item/tadpole_silhouette` as `layer1`. Textures exist at `textures/item/slime_silhouette.png` and `tadpole_silhouette.png`. The color handler tints `tintIndex == 1` correctly. So either (a) the texture isn't loading, (b) the model is being overridden somewhere, or (c) the silhouette texture is fully transparent and needs the body painted in a base color before tint.

**Severity:** Blocks merging the port — every primary item in the creative tab is visually broken.

**Fix attempts landed (await playtest verification):**
- **Alpha-bit normalization:** every `ItemColor` / `BlockColor` handler in `PFClientEvents` now wraps its non-`-1` return through `opaque(rgb) = 0xFF000000 | rgb`. The 24-bit RGB values from `Category.tintRgb()` / `SlimeVariant.primaryColor()` were being interpreted as ARGB with `alpha == 0`, making tinted layers fully transparent. Vanilla auto-applies this for its SpawnEggItem handler; modded handlers must do it themselves.
- **Frog Egg layer:** moved tint from `tintIndex == 1` (bottle glass) to `tintIndex == 0` (potion_overlay liquid).
- **All 28 spawn eggs:** explicit `RegisterColorHandlersEvent.Item` registrations (4 parent slimes via `Consumer<SpawnEggItem>`, 12 variant slimes via registry-then-ctor fallback, 12 frog/tadpole via `Consumer`).
- **Primed Frog Egg + Resource Froglight in-hand BlockItems:** added explicit per-item color handlers (BlockColor doesn't auto-propagate to BlockItem in 1.21.1).
- **Slime/Tadpole bucket silhouettes:** regenerated 16×16 RGBA textures with full-bucket blob shapes (~8×6 slime body in lower half + face features; ~4×4 tadpole head with curved tail). Old textures were eye-dot-only specks.

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
Three distinct base PNGs now ship at `textures/item/{frog,tadpole,slime}_spawn_egg.png`, generated from source silhouettes and tone-mapped through `scripts/process_silhouette.ps1` so they render cleanly under the spawn-egg tint pipelines: body pixels brighten to (220,220,220) so the runtime `ItemTintSource` multiplication renders the category color, dark accent pixels under 64 preserved as-is so eyes stay visible across all category tints. The relevant tint sources are `productivefrogs:contained_category` (frog + tadpole eggs), `productivefrogs:slime_variant` (12 variant slime eggs), and `minecraft:constant` (4 parent-species slime eggs — Cave / Geode / Tide / Void use a fixed RGB rather than a runtime data component). `productivefrogs:bucketed_category` is the bucket-item tint and is NOT used here. Three new model JSONs at `models/item/{frog,tadpole,slime}_spawn_egg.json` route each shape to its own texture, and all 28 spawn-egg item JSONs were updated to point at the right shape model — frogs at frog, tadpoles at tadpole, all 12 variant + 4 parent-species + 6 category slime eggs at slime. The old shared `category_spawn_egg.png` / model JSON were deleted.

### 🟢 Slime Milk fluids don't animate (static surface) — resolved
`scripts/generate_slime_milk_textures.ps1` now tints the **full vanilla water_still / water_flow vertical strips** (32 frames each) instead of just the top frame. Output: `<variant>_slime_milk_still.png` is now 16×512 (32 frames), `<variant>_slime_milk_flow.png` is 32×1024 (32 frames). Each PNG ships a sibling `.mcmeta` with `{"animation": {"frametime": 2}}` matching vanilla water cadence. Source-block fluid surface now moves like vanilla water in the variant's tinted hue.

### 🟢 Resource Froglights appear twice in the creative tab — resolved
Dropped the `for (var entry : PFItems.RESOURCE_FROGLIGHT_ITEMS.values())` loop from `PFCreativeTabs.PRODUCTIVE_FROGS_TAB.displayItems`. The 6 broad-strokes category Froglight BlockItems stay registered (existing worlds load fine; `FrogTongueDropHandler` still emits them as the no-variant fallback drop) — only their creative-tab listing was removed. The variant-stamped `configurable_froglight` stacks remain in the tab and read with their canonical resource names, so no Froglight appears twice. Creative testers wanting a specific category block can use `/give productivefrogs:metallic_froglight` etc.

### 🟠 Slime Milk bucket textures should show slime eyes in the liquid — REOPENED (design pivot, since reverted)
PR #67 shipped this with two dark pixel dots at (6,3) and (9,3) overlaid on each variant's milk surface. **Playtest revealed the design intent was wrong**: Slime Milk is the extracted fluid, the slime itself isn't in the bucket anymore — eyes don't belong there. Eyes belong on the **Slime Bucket** (the bucketed live-slime entity), which already carries them via the `slime_silhouette.png` from PR #66. The revert has since landed — see the 🟢 "Slime Milk buckets have eyes — they shouldn't; Slime Buckets should — resolved" entry above. Keeping this entry visible with the design-pivot note so the original "ship it" decision isn't silently invalidated.

### 🟢 Slime splash particle uses vanilla green regardless of slime variant — resolved
Vanilla's `Slime#tick` reads `getParticleType()` once per particle in its jump-landing splash loop, so overriding that single method swaps the colour without touching the spawn cadence or count. Each PF slime now returns `new DustParticleOptions(<tint>, 1.0F)` in place of `ParticleTypes.ITEM_SLIME`:

- `ResourceSlime#getParticleType` reads the variant's `primary_color` when present, falls back to `Category.tintRgb()` otherwise — so iron Resource Slimes spray silver, redstone slimes spray red, etc.
- `CaveSlime`, `GeodeSlime`, `TideSlime`, `VoidSlime` each pin their respective category colour (MINERAL / GEM / AQUATIC / ARCANE).
- The vanilla magma cube keeps its vanilla orange splash since we don't subclass it.

The particle shape changes from `ITEM_SLIME` (slimeball icon) to `DUST` (colored speck) — slightly different from vanilla but each variant now carries its own visual signal in-world.

Follow-up (deferred): the vanilla magma cube also inherits a hardcoded particle that doesn't match the magma_cream Resource Slime variant. To fix it without subclassing MagmaCube would require a custom registered particle type with a runtime tint argument plus a swap at the call site. Left open since the per-variant signal is delivered for the primary surface (Resource Slimes + 4 parent species), and players rarely confuse magma cubes with their resource analogue.

### 🟢 Per-variant + per-category items — tints + JEI subtypes shipped

**Tint pipeline.** Every variant/category surface resolves its colour from the data-component chain:

- **Variant Slime Spawn Eggs** (12 items) carry `SLIME_VARIANT` on default properties; JSON tint source is `productivefrogs:slime_variant` (layer = `primary`). Each variant renders its resource colour (iron-silver, copper-orange, gold-yellow, …). Pinned by 12 `PFRegistryTest#variantSlimeSpawnEggCarriesSlimeVariantComponent` cases.
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

The creative tab in `PFCreativeTabs.PRODUCTIVE_FROGS_TAB.displayItems` emits one stamped stack per variant/category for each of these (12 variant slime buckets, 6 category tadpole buckets, 6 primed frog egg bottles, 12 configurable froglight stacks), so JEI surfaces them as distinct entries. JEI is `compileOnly` at build time + `runtimeOnly` at play time — the plugin class is dead code if a player runs without JEI.

**Follow-up — display names shipped.** Slime Bucket now resolves via `SlimeBucketItem` (subclasses `MobBucketItem`, `getName(ItemStack)` reads Variant > Category > base from `BUCKET_ENTITY_DATA`). Resource Tadpole Bucket already had category-aware names since `ResourceTadpoleBucketItem`; Frog Egg bottle has category-aware names via `FrogEggItem.getName`. So JEI text-search for `iron` now finds the iron Slime Bucket in addition to the iron Slime Spawn Egg. Lang file ships all 6 category + 12 variant keys per surface; missing translations surface as the raw key (Minecraft's standard fallback).

### 🟢 Slime Milker furnace-style GUI + hopper I/O shipped

**Shipped — GUI**: the right-click-to-instant-convert appliance is replaced with a `SlimeMilkerBlockEntity`-backed furnace-shaped block:

- GUI with one **input slot** (accepts a Slime Bucket only), one **output slot** (filled with the matching variant-typed Slime Milk bucket on cook completion), and a **progress bar**.
- **Cook time: 100 ticks (5 s)** per conversion. No fuel — the slime IS the input.
- Variant lookup unchanged: `SlimeMilkerBlock.readBucketVariant` → `PFFluidTypes.VARIANTS` → `PFItems.MILK_BUCKETS`. Fail-closed semantic: if the input bucket has no `Variant` component, cook progress stays at zero and no output appears. Covered by GameTests.
- Inventory drops on break via `playerWillDestroy` so a broken milker doesn't swallow buckets.
- Placeholder GUI background ships at `assets/productivefrogs/textures/gui/container/slime_milker.png` — basic grey container with slot wells highlighted. Polish texture (full Productive Frogs theming) tracked separately.

**Shipped — hopper I/O.** The Milker's storage is now a `SlimeMilkerInventory extends ItemStacksResourceHandler` (NeoForge 1.21.11's replacement for the legacy `ItemStackHandler`). `Capabilities.Item.BLOCK` is registered with a side-aware provider in `PFModBusEvents` that mirrors the vanilla furnace convention: side `DOWN` returns an extract-only view of the OUTPUT slot, every other face (top, horizontal, and `null` for non-sided access) returns an insert-only view of the INPUT slot restricted to `productivefrogs:slime_bucket`. Pinned by 3 new GameTests: a synthetic capability-routing check, a real `HopperBlockEntity` pushing Slime Buckets in from above, and another pulling milk buckets out from below.

### 🟢 Slime Milk integrates with tank mods — confirmed working

**Verification**: cross-referenced with [Productive Bees' honey fluid](https://github.com/JDKDigital/productive-bees/blob/dev-1.21.0/src/main/java/cy/jdkdigital/productivebees/common/fluid/HoneyFluid.java). PB ships **no** custom `Capabilities.Fluid.BLOCK` registration for their honey LiquidBlock — they rely entirely on the same `BaseFlowingFluid` + vanilla `LiquidBlock` pipeline we use, and downstream tank / pipe mods integrate via:

1. **Bucket item capability** — NeoForge's `CapabilityHooks.registerVanillaProviders` auto-registers `Capabilities.Fluid.ITEM` on every `BucketItem` subclass. Our Slime Milk buckets inherit from vanilla `BucketItem`, so the cap is live without any code from us. Pinned by the `milk_bucket_exposes_fluid_capability_for_tank_mods` GameTest.
2. **Source block pickup** — tank mods that pump from a fluid source block use vanilla `LiquidBlock` bucket-pickup mechanics. Our `SlimeMilkSourceBlock extends LiquidBlock` inherits that behavior unchanged.

If a specific tank mod ever turns out to need an explicit `Capabilities.Fluid.BLOCK` handler (e.g. one that doesn't go through vanilla bucket scoop), file a follow-up issue with the specific mod + version — we can register a `ResourceHandler<FluidResource>` wrapper at that point, similar to how Productive Bees adds fluid handlers to their machine block entities (centrifuge, etc.) but NOT to the raw honey fluid block.

### 🟢 Resource Slime per-variant texture — shipped

**Framework + assets both shipped.** `SlimeVariant` carries an optional `texture` field (`Optional<Identifier>`); all 12 shipped variant JSONs declare one pointing at the matching `<variant>_resource_slime.png`. The renderer resolves in order:

1. Per-variant `texture` from the variant's datapack entry, when set.
2. Per-category fallback PNG (`<category>_resource_slime.png`) for variants without a custom texture and for category-only slimes.

PNGs are generated by `scripts/generate_variant_slime_textures.ps1`, which composites each variant's canonical vanilla block texture (`iron_block.png`, `copper_block.png`, `gold_block.png`, …) into the SlimeModel inner-cube UV layout on the matching per-category template. The outer translucent shell is tinted from the variant's `primary_color` via `ResourceSlimeRenderState.outerTint` → `ResourceSlimeOuterLayer.submit`, so each variant reads as a distinct resource block inside a tinted shell.

Schema + renderer round-trip covered by `SlimeVariantTest` (codec accepts JSONs with and without the field). End-to-end load + texture-field decoding pinned by the `slime_variant_datapack_registry_loads_initial_variants` GameTest's iron spot-check.

To regenerate the PNGs after a vanilla block texture changes upstream, or when adding a new variant:

```
.\scripts\generate_variant_slime_textures.ps1
```

The script auto-discovers the NeoForge minecraft-resources jar via glob under `build/moddev/artifacts/neoforge-*-client-extra-aka-minecraft-resources.jar` (so it survives NeoForge version bumps) and extracts it to a cache dir using the .NET `ZipFile` API (no `jar.exe` / JAVA_HOME dependency). Outputs land in `src/main/resources/assets/productivefrogs/textures/entity/slime/`. Windows-only — System.Drawing is in-box on Windows but needs libgdiplus on Linux/macOS.

---

## V1 limitations (by design)

These are intentional V1 scope cuts. Each is on the V2 roadmap unless noted otherwise.

### 🟢 Automated Slime Milker — shipped (GUI + hopper I/O)
The Milker is a furnace-shaped GUI block with input + output slots, a 100-tick cook progress, and fail-closed variant-bucket validation. Hopper compat is wired via `Capabilities.Item.BLOCK` with side-aware routing — top + horizontal faces accept SLIME_BUCKET pushes, the bottom face yields finished milk buckets. See the resolved entry above for the implementation details.

### 🔵 No Frog Terrarium / Habitat block
Frogs in V1 live where you place them, near water. A placeable housing block with I/O inventory is V2.

### 🔵 Slime Milk only in buckets (UI surface)
Bucket-only is the shipped UI in V1 — no jugs, tanks, or custom fluid containers. The underlying fluid IS accessible to any tank-mod ecosystem (Mekanism, Thermal, Create, Fluid Tanks): the bucket item exposes `Capabilities.Fluid.ITEM` via NeoForge's automatic `BucketItem` registration, and the source block uses vanilla `LiquidBlock` bucket-pickup mechanics. See the resolved entry above for verification details.

### 🔵 No visual depletion countdown on milk source blocks
Source blocks deplete after `depletionCount` spawns (default 16) and drain to air. The texture does NOT desaturate as the counter approaches zero — the counter lives in blockstate but has no client-side visual cue. Specced in `farming.md`; deferred to polish so J5 could ship without a custom fluid renderer.

### 🔵 No native crusher / pestle
V1 ships no in-house crushing block. The 2× metallic yield is unlocked by installing Create, Mekanism, or Thermal (compat recipes still pending — see Cross-Mod section below).

### 🔵 No drop-collection block
Use vanilla hoppers under the frog pen to collect Froglight item entities. A custom collection block is V2.

---

## Cross-Mod Compat caveats

### 🔵 Crush recipes (Create / Mekanism / Thermal) not yet shipped
Design ([farming.md §Cross-Mod](./farming.md)) calls for conditional `mod_loaded` JSON recipes converting 1 metallic Froglight → 2 dust / crushed material. Not in V1 — will ship as a follow-up once we have a test environment that can validate the cross-mod recipe shapes. Players can still smelt directly for 1× yield in the meantime.

The `productivefrogs:crushable/metallic` item tag is reserved for this purpose and will be populated alongside the recipes.

### 🔵 No `compat/` Java package — deliberate
Cross-mod integration ships exclusively as JSON datapacks gated by `neoforge:conditions → mod_loaded`. Variants for modded resources (e.g. Mythic Metals) similarly ship as JSON `SlimeVariant` entries with `mod_loaded` conditions. See `docs/architecture.md` for the schema.

---

## Recently resolved

Listed for searchability — useful when a playtest report references an issue that's already fixed in a newer dev build.

### 🟢 Slime hitboxes 4× too large at every size — fixed in PR #43
**Symptom**: Cave / Geode / Tide / Void / Resource Slimes had bounding boxes far larger than their rendered sprites. Attacking them required clicking visibly outside the body; they'd push the player from blocks away.
**Cause**: All five custom slime `EntityType.Builder` registrations used the pre-1.21 `sized(2.04F, 2.04F)` base. Vanilla 1.21.11 `Slime#getDefaultDimensions` now scales the base directly by `getSize()` (no internal 0.255 multiplier). At size 1 the hitbox came out at 2.04 instead of 0.52 — 4× too large.
**Fix**: matched vanilla's current builder — `sized(0.52F, 0.52F)`, `eyeHeight(0.325F)`, `spawnDimensionsScale(4.0F)`. Regression-pinned by `custom_slimes_size_1_hitbox_matches_vanilla_slime` GameTest.

### 🟢 Confusing Froglight display names — fixed in PR #43
**Symptom**: in-game furnace / inventory tooltip showed `Metallic Froglight` / `Mineral Froglight` / etc. — internal category names that don't appear anywhere else in the player-facing UX.
**Fix**: renamed the six broad-strokes Froglight display strings to the canonical resource each one smelts to (Iron, Redstone, Diamond, Prismarine, Magma Cream, Ender Pearl). Registry IDs stayed `metallic_froglight` etc. so existing saves and tag entries aren't disturbed.

---

## How to report a new issue

1. Try to reproduce in the latest `main` build (`./gradlew build` → `runClient`).
2. If it still happens, file a GitHub issue with:
   - MC / NeoForge / Productive Frogs versions
   - Minimal repro steps
   - Expected vs observed
   - `latest.log` snippet around the failure if it's a crash / log warning
3. Tag the issue `bug` or `limitation` so it sorts cleanly against this doc.

---

*Last updated: 2026-05-22 (per-variant Slime texture framework + outer-shell tint shipped; PNG assets pending as a separate asset PR)*
