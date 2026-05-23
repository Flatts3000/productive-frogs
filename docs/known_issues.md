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

### 🟢 Coal Resource Slime's eye dots are nearly invisible (dark on dark) — resolved
Took option 2 from the fix-path discussion: bumped `data/.../slime_variant/coal.json` `primary_color` from `0x2A2A2A` (near-black) to `0x585858` (charcoal grey), and `secondary_color` from `0x101010` to `0x202020`. Body tint now multiplies silhouette body pixels to a charcoal value while the preserved eye dots stay near-black — visible contrast restored. Coal variant still reads as "coal" but no longer flattens against its own eye dots.

### 🔴 Bucket of Slime + Bucket of Tadpole render as plain empty iron buckets
Per the screenshot, the rows of Resource Tadpole Buckets and Slime Buckets across all 6 categories + 12 variants render as **plain unadorned iron buckets** — the silhouette layer that PR #66 (`tadpole_silhouette.png` / `slime_silhouette.png`) was supposed to provide isn't visible. The earlier resolved entry for "Bucket of Tadpole + Bucket of Slime render as empty buckets" claimed this was fixed; playtest shows the fix didn't actually land in the live render. The Slime Milk buckets (variant rim colour visible) are rendering correctly, so the tint pipeline works on at least one surface.

**Symptom**: Open the creative tab → every Resource Tadpole Bucket and every Slime Bucket (across 6 categories + 12 variants) shows as a vanilla iron bucket with empty interior. The tadpole / slime silhouette inside is missing entirely.

**Possible causes** (need diagnosis):
1. The shipped PNG paths in `assets/productivefrogs/textures/item/{tadpole,slime}_silhouette.png` aren't matching what the item-model JSONs reference — quick grep to verify.
2. The two-layer model has `layer0` (silhouette) and `layer1` (iron bucket) but the silhouette is being completely covered by the bucket layer — either layer-ordering wrong, or the silhouette pixels are landing where the bucket interior is solid metal rather than transparent (so they're hidden behind the bucket).
3. The `BucketedCategoryTint` runtime tint is multiplying the silhouette layer to a colour that matches the bucket interior, making it invisible even though it's there.
4. The silhouettes shipped from PR #66 are actually transparent / near-empty (the tone-mapping wrote out too few pixels).

**Fix path**: Diagnose with `/data` on a held bucket, inspect the actual PNG file pixels, and view the item with F3+H (advanced tooltips) to confirm which model JSON is being applied. The resolved 🟢 entry should be flipped back to 🟠 REOPENED once the cause is identified.

### 🔴 Slime Milker progress bar / arrow doesn't animate
The Slime Milker GUI shows the static furnace-style arrow between input and output slots, but it doesn't animate during the milking operation. Vanilla furnace UIs draw a progress-filled arrow (the recipe-cook progress bar) that fills left-to-right as the operation advances. The Slime Milker arrow is just static art.

**Symptom**: Place a Slime Bucket in the input slot, wait for the milking to complete → the arrow stays grey and unchanging throughout. No visual indication that anything is happening. Output appears suddenly with no progress feedback.

**Fix path**: `SlimeMilkerMenu` should expose a `progress` ContainerData int (current cook ticks) and a `totalProgress` int (max ticks for the recipe), the same shape as vanilla `FurnaceMenu`. `SlimeMilkerScreen.render` reads them and blits the progress-arrow texture at the appropriate width based on the ratio. `SlimeMilkerBlockEntity` already drives `cookProgress` per server-tick (see CLAUDE.md notes); wire it through to the menu via ContainerData sync.

### 🔴 Slime Milker input slot is not vertically centered relative to the output
The input slot sits in the top-left of the GUI's recipe area while the output slot (and the arrow) are vertically centered lower down. The vanilla furnace pattern has input above + fuel below the input column with the output centered to the right; the Slime Milker doesn't use a fuel slot (it's hand-operated), so the input should be vertically centered next to the output rather than top-aligned.

**Symptom**: Open the Slime Milker GUI → the input slot is visibly higher than the output slot. The arrow points from somewhere below the input to the output, creating an awkward visual hierarchy.

**Fix path**: Bump `SlimeMilkerMenu.INPUT_SLOT` Y position from its current value (56,17 per CLAUDE.md) to align vertically with `OUTPUT_SLOT` at y=30 — set input Y to 30 (or whatever centers it against the output). Also bump the input slot frame in `textures/gui/container/slime_milker.png` so the visual slot box matches the new Y. The GUI was modelled on vanilla furnace (which has 2 input slots stacked + 1 output centred); since we only need 1 input + 1 output, recentering is the right move.

### 🟢 Slime Milker output slot doesn't center its item — resolved
Bumped `SlimeMilkerMenu.OUTPUT_SLOT_X` from 112 to 116 and `OUTPUT_SLOT_Y` from 30 to 35 so the menu uses vanilla furnace's actual result-slot coordinates (the previous (112, 30) values were a mis-quote of vanilla — real furnace result slot is at (116, 35)). The output bucket now centres against the result-slot frame drawn by the inherited GUI texture.

### 🟢 Slime Milk buckets have eyes — they shouldn't; Slime Buckets should — resolved
Reverted the eye-overlay portion of PR #67. `scripts/generate_slime_milk_textures.ps1`'s `Build-BucketTexture` no longer writes the two dark pixels at (6,3) and (9,3); replaced with a comment block explaining the design pivot. Re-ran the script to regenerate the 14 variant milk-bucket PNGs without eyes. The animated-fluid changes from the same PR stay (still correct). The Slime Bucket (`slime_silhouette.png` layer0) keeps its eyes, which was always the right surface.

### 🔴 Spawn egg textures don't read as eggs — they look like dead 2D creatures
The 3 spawn-egg base textures shipped in PR #69 (`frog_spawn_egg.png`, `tadpole_spawn_egg.png`, `slime_spawn_egg.png`) render the full creature silhouette tinted per variant. In context next to the **vanilla Minecraft spawn-egg style** they don't read as "eggs you spawn a creature from" — they read as flat 2D dead frogs / tadpoles / slime blobs. Vanilla spawn eggs are an **oval / ovoid egg shape with a two-tone speckle pattern** (primary + secondary colour) and a **small creature-face overlay** on top to distinguish species; PF's spawn eggs throw away the egg shape entirely and just paint the creature.

**Symptom**: Open the Productive Frogs creative tab → bottom rows of spawn eggs read as a wall of dark silhouettes ("dead frogs sitting on the grass," "wet tadpoles with tails," "ghost slimes"). Compared to the vanilla spawn-egg tab (oval eggs with clean two-tone fills + a creature face), PF's eggs look out of place and amateurish.

**Fix path**: Replace the three base PNGs with the vanilla spawn-egg pattern:
1. Each base PNG is a 16×16 **egg shape** matching vanilla's `template_spawn_egg.png` (two-tone: primary fill body + secondary speckle highlight).
2. A small **creature-face overlay** in the top portion distinguishes frog vs tadpole vs slime (just the face/silhouette, not the whole body).
3. Per-variant runtime tint via the existing `ItemTintSource` pipeline still drives the colour — but now it's the EGG that gets tinted, not the whole creature body.

The simplest implementation copies vanilla's two-layer spawn-egg approach: `layer0` is the egg base (tintable primary), `layer1` is the speckle overlay (tintable secondary, or constant grey for now). Updating `models/item/{frog,tadpole,slime}_spawn_egg.json` to a two-layer template plus regenerating the three PNGs in the vanilla style closes this.

### 🟢 Frog tongue kill on Resource Slime emits both Froglight AND slimeballs — resolved
Took option 2 from the fix-path discussion: added a new entity-type tag `productivefrogs:frogs` listing both `minecraft:frog` and `productivefrogs:resource_frog`, then added an `inverted` `entity_properties` condition on the Resource Slime loot table (`data/productivefrogs/loot_table/entities/resource_slime.json`) that gates the slimeball drop on `attacker != #productivefrogs:frogs`. Frog tongue kills now drop the variant Froglight only; non-frog kills (sword, fall damage, environmental) still drop slimeballs per vanilla parity.

The single-Froglight-per-kill design is preserved by `FrogTongueDropHandler.dropFroglightAtFrog`: each invocation creates exactly one `ItemStack` (default count 1) and spawns it as one `ItemEntity`, so a single tongue-hit cannot multiply the drop.

### 🟢 Variant-stamped Froglights render as 2D items instead of 3D placeable blocks — resolved
Took option 1 from the fix-path discussion. `configurable_froglight` is now a `BlockItem` backed by `ConfigurableFroglightBlock` (a `RotatedPillarBlock` + `EntityBlock`) with a single block ID and one `ConfigurableFroglightBlockEntity` per placement. The BE stores the `SLIME_VARIANT` identifier, which drives a per-variant tint via a `BlockColor` lambda reading the BE through `BlockEntity#getLevel().registryAccess()` (the `BlockAndTintGetter` parameter is often a `RenderChunkRegion` and doesn't expose `registryAccess`).

Break + lighting mechanics match vanilla froglight exactly — same `RotatedPillarBlock` base, `strength(0.3F)`, `lightLevel(state -> 15)`, `sound(SoundType.FROGLIGHT)`, default `NORMAL` push reaction, `survives_explosion` loot condition. The only behavioral addition is `minecraft:copy_components` (source `block_entity`, include `productivefrogs:slime_variant`) on the loot table so the variant survives the place → break round-trip. The BE additionally implements `collectImplicitComponents` so creative pick-block stamps the variant onto the picked item without extra code.

Translation keys migrated from `item.productivefrogs.configurable_froglight.*` to `block.productivefrogs.configurable_froglight.*` to match the `useBlockDescriptionPrefix()` setting on the item properties. New GameTest `variant_froglight_round_trip_preserves_variant_through_place_and_break` pins the BE write → loot table copy → item drop chain so a future loot-table or BE refactor that drops the variant fails closed.

### 🟢 Frog / Slime / Tadpole spawn eggs share one silhouette — resolved
Three distinct base PNGs now ship at `textures/item/{frog,tadpole,slime}_spawn_egg.png`, generated via PixelLab MCP (`create_map_object`) and tone-mapped through `scripts/process_silhouette.ps1` so they render cleanly under the spawn-egg tint pipelines: body pixels brighten to (220,220,220) so the runtime `ItemTintSource` multiplication renders the category color, dark accent pixels under 64 preserved as-is so eyes stay visible across all category tints. The relevant tint sources are `productivefrogs:contained_category` (frog + tadpole eggs), `productivefrogs:slime_variant` (12 variant slime eggs), and `minecraft:constant` (4 parent-species slime eggs — Cave / Geode / Tide / Void use a fixed RGB rather than a runtime data component). `productivefrogs:bucketed_category` is the bucket-item tint and is NOT used here. Three new model JSONs at `models/item/{frog,tadpole,slime}_spawn_egg.json` route each shape to its own texture, and all 28 spawn-egg item JSONs were updated to point at the right shape model — frogs at frog, tadpoles at tadpole, all 12 variant + 4 parent-species + 6 category slime eggs at slime. The old shared `category_spawn_egg.png` / model JSON were deleted.

### 🟢 Slime Milk fluids don't animate (static surface) — resolved
`scripts/generate_slime_milk_textures.ps1` now tints the **full vanilla water_still / water_flow vertical strips** (32 frames each) instead of just the top frame. Output: `<variant>_slime_milk_still.png` is now 16×512 (32 frames), `<variant>_slime_milk_flow.png` is 32×1024 (32 frames). Each PNG ships a sibling `.mcmeta` with `{"animation": {"frametime": 2}}` matching vanilla water cadence. Source-block fluid surface now moves like vanilla water in the variant's tinted hue.

### 🟢 Resource Froglights appear twice in the creative tab — resolved
Dropped the `for (var entry : PFItems.RESOURCE_FROGLIGHT_ITEMS.values())` loop from `PFCreativeTabs.PRODUCTIVE_FROGS_TAB.displayItems`. The 6 broad-strokes category Froglight BlockItems stay registered (existing worlds load fine; `FrogTongueDropHandler` still emits them as the no-variant fallback drop) — only their creative-tab listing was removed. The variant-stamped `configurable_froglight` stacks remain in the tab and read with their canonical resource names, so no Froglight appears twice. Creative testers wanting a specific category block can use `/give productivefrogs:metallic_froglight` etc.

### 🟢 Bucket of Tadpole + Bucket of Slime render as empty buckets — resolved
The two silhouette PNGs (`textures/item/tadpole_silhouette.png` and `textures/item/slime_silhouette.png`) were blank/transparent placeholders, so the layered bucket items rendered as iron exteriors with empty contents. Resolved by generating both via PixelLab MCP (`create_map_object`, transparent BG), then tone-mapping with `scripts/process_silhouette.ps1`: non-transparent body pixels brighten to near-white (220,220,220) so the runtime `BucketedCategoryTint` multiplication renders the category color, while dark accent pixels (eyes) stay below the 64 threshold and are preserved as-is so they remain visible at every variant tint.

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
