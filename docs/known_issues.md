# Known Issues

Living tracker of playtest bugs, limitations, and workarounds for Productive Frogs. Player-facing concerns only — developer-facing test flakiness, code hygiene debt, and refactor ideas live in [backlog.md](./backlog.md) §Polish.

## Status legend

| Symbol | Meaning |
|---|---|
| 🔴 | Open. Fix pending. |
| 🟡 | Open. Workaround available — see entry. |
| 🔵 | Limitation by design for V1. May revisit in V2 (see [versioning.md](./versioning.md)). |
| 🟢 | Resolved. Listed here so playtest reports stay searchable. |

---

## Open issues

### 🔴 Frog / Slime / Tadpole spawn eggs share one silhouette and can't be told apart
All Productive Frogs spawn eggs (6 Resource Frog + 6 Resource Tadpole + 12 variant Resource Slime + 4 parent-species) currently share **one base texture** (`textures/item/category_spawn_egg.png` — the vanilla egg-shape silhouette) and are differentiated only by per-item tint via the `SLIME_VARIANT` / `CONTAINED_CATEGORY` data components. Result: at-rest the eggs read as "tinted vanilla spawn eggs" — the iron Slime egg and the iron Frog egg are visually identical apart from being on different inventory slots.

**Symptom**: Open creative tab → see ~28 spawn eggs that look like color-tinted variants of the same shape. Can't tell a Frog egg from a Tadpole egg from a Slime egg at a glance; the name tooltip is the only signal.

**Fix path**: Generate three distinct base PNGs (grayscale, tintable at runtime via the existing `slime_variant` / `contained_category` `ItemTintSource`):
- `frog_spawn_egg_base.png` — frog-silhouette shape with two visible eye dots
- `tadpole_spawn_egg_base.png` — egg shape with a small tadpole tail wisp
- `slime_spawn_egg_base.png` — egg shape with a small blob / squish detail

Each grayscale; tinting stays the per-variant runtime path. Then update the three item-model JSONs to reference their distinct base instead of the shared `category_spawn_egg.png`. PixelLab `create_map_object` is the right pipeline for these.

### 🟢 Slime Milk fluids don't animate (static surface) — resolved
`scripts/generate_slime_milk_textures.ps1` now tints the **full vanilla water_still / water_flow vertical strips** (32 frames each) instead of just the top frame. Output: `<variant>_slime_milk_still.png` is now 16×512 (32 frames), `<variant>_slime_milk_flow.png` is 32×1024 (32 frames). Each PNG ships a sibling `.mcmeta` with `{"animation": {"frametime": 2}}` matching vanilla water cadence. Source-block fluid surface now moves like vanilla water in the variant's tinted hue.

### 🟢 Resource Froglights appear twice in the creative tab — resolved
Resolved via the V1 path (option 1 in the prior fix-path discussion): the loop that listed `PFItems.RESOURCE_FROGLIGHT_ITEMS.values()` in `PFCreativeTabs.PRODUCTIVE_FROGS_TAB.displayItems` is gone. The 6 broad-strokes category Froglight BlockItems stay registered (existing worlds load fine; `FrogTongueDropHandler` still emits them as the no-variant fallback drop) — only their creative-tab listing was dropped. The variant-stamped `configurable_froglight` stacks remain in the tab and read with their canonical resource names. Creative testers wanting a specific category block can use `/give productivefrogs:metallic_froglight` etc.

### 🟢 Bucket of Tadpole + Bucket of Slime render as empty buckets — resolved
The two silhouette PNGs (`textures/item/tadpole_silhouette.png` and `textures/item/slime_silhouette.png`) were blank/transparent placeholders, so the layered bucket items rendered as iron exteriors with empty contents. Resolved by generating both via PixelLab MCP (`create_map_object`, transparent BG), then tone-mapping with `scripts/process_silhouette.ps1`: non-transparent body pixels brighten to near-white (220,220,220) so the runtime `BucketedCategoryTint` multiplication renders the category color, while dark accent pixels (eyes) stay below the 64 threshold and are preserved as-is so they remain visible at every variant tint.

### 🟢 Slime Milk bucket textures should show slime eyes in the liquid — resolved
`scripts/generate_slime_milk_textures.ps1` now overlays two dark pixel dots at (6,3) and (9,3) on the milk surface — eye positions chosen against the vanilla `milk_bucket.png` milk region (y=3 is the top of the widest milk band; x=6 and x=9 give vanilla-slime-style 2-pixel eye spacing). The eyes are written **after** the tint loop has finished, so they bypass `Apply-Tint` entirely and ship at their literal (28,28,28) value on every variant. Held / dropped slime milk buckets now read as "slime peering out of an iron bucket" instead of "tinted dye."

### 🔴 Slime splash particle uses vanilla green regardless of slime variant
The slime splash animation (the particle burst that fires when a slime jumps / lands) renders with vanilla green coloring on **every** slime type — Resource Slimes (all 12 variants + 6 category fallbacks), Cave / Geode / Tide / Void parent species, and the magma cube variant.

**Symptom**: An iron Resource Slime, a Void Slime, or any non-green slime jumping in-world emits the same green-tinted slime splash particles as a vanilla Minecraft slime. Breaks the visual identity of the per-category / per-variant pipeline.

**Root cause** (suspected): vanilla `Slime#jumpFromGround` / `Slime#updateSquishing` (or whichever fires the particle) uses the hardcoded `ParticleTypes.ITEM_SLIME` particle, which carries the vanilla-slimeball color baked into its texture. Our `ResourceSlime` doesn't override that path, so it inherits the green burst.

**Fix path**: either (a) override the particle emission in `ResourceSlime` to use a category/variant-tinted particle, OR (b) register a custom particle type with a runtime tint argument and swap the call site. Option (b) is the cleaner story since it generalises to magma cubes (which also currently inherit vanilla magma-cream particles even when they're our typed variant).

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
