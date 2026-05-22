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

### 🔴 Per-variant items missing their resource colour and JEI presence

Each Resource Slime variant (`iron`, `copper`, `gold`, `redstone`, `lapis`, `coal`, `diamond`, `emerald`, `prismarine`, `sponge`, `magma_cream`, `ender_pearl`) should expose three visually-coloured items, each with a distinct JEI entry:

1. The variant's **Resource Slime** (the entity) — already coloured by `SlimeVariantTint`. Verify the spawn egg + JEI hover label match.
2. The variant's **Slime Bucket** — `productivefrogs:slime_bucket` with the `slime_variant` data component. Currently tints by category fallback; should resolve per-variant once the bucket carries the variant id.
3. The variant's **Configurable Froglight** — `productivefrogs:configurable_froglight` stamped with the `slime_variant` component. Drop is correct (`FrogTongueDropHandler`); tint needs to render the variant's colour (copper-coloured for copper, gold-coloured for gold, etc.) in inventory AND in JEI.

**Expected colours** (canonical resources):

| Variant | Colour intent |
|---|---|
| iron | light grey-silver |
| copper | orange-pink |
| gold | yellow |
| redstone | bright red |
| lapis | deep blue |
| coal | near-black |
| diamond | cyan |
| emerald | bright green |
| prismarine | sea green |
| sponge | yellow-tan |
| magma_cream | dark red-orange |
| ender_pearl | teal-purple |

**JEI**: every variant should appear as its own line in JEI search (e.g. searching `iron` returns `Iron Resource Slime`, `Iron Slime Bucket`, `Iron Froglight`, separately — not all collapsed under a generic `Configurable Froglight` entry). Component-aware JEI subtypes may need to be registered.

### 🔴 Per-category items missing their category colour and JEI presence

The six-category roster (`METALLIC`, `MINERAL`, `GEM`, `AQUATIC`, `INFERNAL`, `ARCANE`) should expose category-tinted items with distinct JEI entries:

- **Resource Frog** + spawn egg
- **Resource Tadpole** + spawn egg
- **Resource Tadpole Bucket** (already wired)
- **Primed Frog Egg** (block + block item — already wired)
- **Frog Egg** bottle (item — already wired)
- **Broad-strokes Froglight** (the 6 `<category>_froglight` blocks — display names updated in PR #43, tint still needs verification)

**Expected colours**: see `Category.tintArgb()` in `data/Category.java` — the single source of truth.

| Category | Tint |
|---|---|
| METALLIC | iron-grey |
| MINERAL | red-orange |
| GEM | bright cyan |
| AQUATIC | sea-blue |
| INFERNAL | hot-orange |
| ARCANE | purple |

**JEI**: each of the six categories should appear as its own line per item family (e.g. six `Metallic / Mineral / Gem / Aquatic / Infernal / Arcane Frog Spawn Egg` entries, not one combined `Resource Frog Spawn Egg`).

### 🟡 Slime Milker furnace-style GUI shipped — hopper I/O pending

**Shipped**: the right-click-to-instant-convert appliance is replaced with a `SlimeMilkerBlockEntity`-backed furnace-shaped block:

- GUI with one **input slot** (accepts a Slime Bucket only), one **output slot** (filled with the matching variant-typed Slime Milk bucket on cook completion), and a **progress bar**.
- **Cook time: 100 ticks (5 s)** per conversion. No fuel — the slime IS the input.
- Variant lookup unchanged: `SlimeMilkerBlock.readBucketVariant` → `PFFluidTypes.VARIANTS` → `PFItems.MILK_BUCKETS`. Fail-closed semantic: if the input bucket has no `Variant` component, cook progress stays at zero and no output appears. Covered by 2 new GameTests.
- Inventory drops on break via `playerWillDestroy` so a broken milker doesn't swallow buckets.
- Placeholder GUI background ships at `assets/productivefrogs/textures/gui/container/slime_milker.png` — basic grey container with slot wells highlighted. Polish texture (full Productive Frogs theming) tracked separately.

**Pending — hopper I/O.** NeoForge 1.21.11 deprecated the legacy `Capabilities.ItemHandler.BLOCK` (returning `IItemHandler`) in favour of `Capabilities.Item.BLOCK` (returning `ResourceHandler<ItemResource>`). Per [NeoForge's transfer-rework notes](https://neoforged.net/news/21.9-transfer-rework/), no adapter wraps the old handler as the new one — the BE's storage needs to migrate to a new `ResourceStacksResourceHandler<ItemResource>` (or equivalent) before hopper compatibility lands. Substantial enough to ship as its own PR; tracked here so it's not forgotten.

### 🟢 Slime Milk integrates with tank mods — confirmed working

**Verification**: cross-referenced with [Productive Bees' honey fluid](https://github.com/JDKDigital/productive-bees/blob/dev-1.21.0/src/main/java/cy/jdkdigital/productivebees/common/fluid/HoneyFluid.java). PB ships **no** custom `Capabilities.Fluid.BLOCK` registration for their honey LiquidBlock — they rely entirely on the same `BaseFlowingFluid` + vanilla `LiquidBlock` pipeline we use, and downstream tank / pipe mods integrate via:

1. **Bucket item capability** — NeoForge's `CapabilityHooks.registerVanillaProviders` auto-registers `Capabilities.Fluid.ITEM` on every `BucketItem` subclass. Our Slime Milk buckets inherit from vanilla `BucketItem`, so the cap is live without any code from us. Pinned by the `milk_bucket_exposes_fluid_capability_for_tank_mods` GameTest.
2. **Source block pickup** — tank mods that pump from a fluid source block use vanilla `LiquidBlock` bucket-pickup mechanics. Our `SlimeMilkSourceBlock extends LiquidBlock` inherits that behavior unchanged.

If a specific tank mod ever turns out to need an explicit `Capabilities.Fluid.BLOCK` handler (e.g. one that doesn't go through vanilla bucket scoop), file a follow-up issue with the specific mod + version — we can register a `ResourceHandler<FluidResource>` wrapper at that point, similar to how Productive Bees adds fluid handlers to their machine block entities (centrifuge, etc.) but NOT to the raw honey fluid block.

### 🔴 Resource Slime inner texture should be the resource's block texture

Replace the current flat-tint inner sprite on each Resource Slime with the **vanilla block texture of the variant's canonical resource** — iron block for iron variant, copper block for copper, diamond block for diamond, etc. The **outer translucent shell** keeps the vanilla slime jelly look (same model + translucent layer as today) but tints to a translucent version of the inner texture so the visual reads as "you can see the resource inside the slime".

Likely implementation:

- Extend the `SlimeVariant` codec to declare an inner texture path (e.g. `inner_texture: "minecraft:block/iron_block"`).
- Resolve that path in `ResourceSlimeRenderer` (or its `RenderState`) and bind it as the inner layer's texture.
- Outer shell uses the existing translucent overlay and reads its tint from the variant's primary colour for cohesion.

Substantial visual upgrade — needs the schema extension AND a renderer rework. Track as one larger task; will not slip into V1 ship without its own PR.

---

## V1 limitations (by design)

These are intentional V1 scope cuts. Each is on the V2 roadmap unless noted otherwise.

### 🟢 Automated Slime Milker — shipped (GUI), hopper I/O pending
The Milker is now a furnace-shaped GUI block with input + output slots, a 100-tick cook progress, and fail-closed variant-bucket validation. Hopper compat is still pending pending the NeoForge `ResourceHandler<ItemResource>` migration — see the entry above for details.

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

*Last updated: 2026-05-21 (PR #43 in flight)*
