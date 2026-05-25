# Data-Driven Variant Architecture Refactor

> Prerequisite for V1.1. Until this lands, modpacks adding a new Resource Slime variant must edit Java source and recompile. V1.1 (which adds 16 new variants) would lock that "hardcoded variants" debt deeper if we ship it first — refactor must come before V1.1.

## Decision (2026-05-25): Approach B, full component-driven milk

This doc predates v1.1 and originally recommended **Approach A** (auto-register per-variant fluids by scanning JSONs at mod init). That recommendation is **superseded.** v1.1 shipped (33 variants) while milk stayed per-variant Java, and the goal has firmed to: **a variant must be addable by datapack alone, including its Slime Milk.** Approach A cannot satisfy that — fluids register at mod construction, before any *world* datapack loads, so a player-added datapack variant could never get a per-variant fluid. Only **Approach B** (one generic `slime_milk` fluid + variant on a component / BlockEntity) makes milk truly datapack-addable.

Status of the four original phases:
- **Phase 1 (spawn eggs collapse + lang derivation)** — DONE (shipped as CR-9, PR #100): single `resource_slime_spawn_egg`, template lang with title-cased fallback.
- **Phases 2-4** — superseded by the Approach B plan below.

The per-position fluid-render risk the original Approach B write-up flagged is resolved: NeoForge's `IClientFluidTypeExtensions` exposes position-aware `getTintColor(FluidState, BlockAndTintGetter, BlockPos)` and `getStillTexture(FluidState, BlockAndTintGetter, BlockPos)`, so a single greyscale milk texture tinted per-position from the source block's BlockEntity works without mixins.

### Approach B implementation plan (the work)

Collapse the ~35 per-variant Slime Milk registrations into one of each:

- **1 `FluidType` `slime_milk`** + **1 source + 1 flowing `SlimeMilkFluid`** (replaces the `VARIANTS`-driven maps in `PFFluidTypes`/`PFFluids`).
- **1 `SlimeMilkSourceBlock`** that is an `EntityBlock`; its **`SlimeMilkSourceBlockEntity`** stores the variant `ResourceLocation`. Only BE-backed source blocks spawn slimes (of their stored variant); flowing/spread milk is generic and spawns nothing.
- **1 `slime_milk_bucket` item** carrying the variant in the `SLIME_VARIANT` data component (mirrors `configurable_froglight` / slime bucket). A custom bucket item: placing writes the variant to the new source block's BE; bucketing a source reads the BE back onto the stack.
- **`SlimeMilkerBlockEntity`** outputs that single bucket stamped with the input slime bucket's variant, instead of a per-variant `MILK_BUCKETS.get(variant)` item.
- **Render:** one greyscale `slime_milk_still`/`_flow` texture; per-position tint via `getTintColor(state, level, pos)` reading the BE → `SlimeVariant.primaryColor`. Source-block `BlockColor` + bucket item color read the same. Removes the ~70 per-variant milk PNGs/.mcmeta and the per-variant blockstate/model JSONs.
- **In-slime texture (the other config-only blocker):** `ResourceSlimeRenderer` falls back to the category texture when no `<variant>_resource_slime.png` is shipped (keyed to membership in the built-in set), so a datapack variant with no texture renders the category cube + its `primary_color` shell instead of a missing texture.
- **Lang:** template keys with title-cased fallback for the milk bucket + block + fluid (the 5 the original Phase 2 deferred), so a datapack variant needs no lang.

**Backwards compatibility: hard break, no world migration.** The per-variant milk fluid/block/item IDs are removed; placed milk blocks in pre-existing worlds become orphaned refs. Consistent with the project norm (v1.0.0 "no migration path … regenerate worlds"; v1.1 shipped breaking removals). Documented in the CHANGELOG.

**Validation:** ship a throwaway test-datapack variant (no Java, no assets) and confirm the full loop — infuse → slime renders (category fallback) → bucket → milker → milk bucket → place source → source spawns the variant slime → frog kill drops the froglight → smelt (test recipe) — works end to end.

The original (superseded) Approach A/B analysis is retained below for the design history.

## Goal

A modpack adds a new Resource Slime variant by **JSON only**:

```
data/<modpack_ns>/productivefrogs/slime_variant/<name>.json
```

After that JSON file is dropped into the modpack's datapack folder, the full pipeline auto-extends:

- Resource Slime entity renders with the variant's tint and inner-cube texture
- Frog tongue kill drops a variant-stamped `configurable_froglight` (already works ✓)
- The variant smelts to its resource via the variant's smelting recipe (recipe is a separate JSON, also data-driven ✓)
- A spawn egg appears in the creative tab for the variant
- A Slime Bucket can capture a variant slime (already works ✓)
- The Slime Milker can convert that bucket to a variant Slime Milk bucket
- The Slime Milk bucket can be placed in the world as a fluid source block
- The Slime Milk source block spawns the variant slime via the source-block-spawn cadence

**Zero Java edits required from modpack authors.**

## Current architecture — what's hardcoded per variant

After auditing the source, these are every surface where adding a variant today requires touching Java, lang, or hand-authored assets:

### Java surfaces (hardcoded per variant)

| File | What's hardcoded | Per-variant cost |
|---|---|---|
| `PFFluidTypes.VARIANTS` | `List.of("iron", "copper", "gold", …)` — 14 entries | 1 line in the list, drives 4 derived registry maps |
| `PFFluids.BY_VARIANT` | Auto-built from `PFFluidTypes.VARIANTS` — Source + Flowing Fluid per variant | (derived) |
| `PFBlocks.MILK_BLOCKS` | Auto-built — LiquidBlock per variant | (derived) |
| `PFItems.MILK_BUCKETS` | Auto-built — BucketItem per variant | (derived) |
| `PFItems.buildSlimeVariantSpawnEggs()` | Hardcoded `VariantSpec[]` of 12 entries | 1 line in array, registers one SpawnEggItem per |
| `PFCreativeTabs.PRODUCTIVE_FROGS_TAB.displayItems` | Iterates `PFFluidTypes.VARIANTS` for milk + slime buckets (already data-driven from the list) | (derived from VARIANTS list) |
| `PFGameTests` | Many tests reference specific variants like `iron`, `magma_cream` | Test-only; refactor adds 1 "for-each-variant" test scaffold |

**Critical files calling per-variant maps:**

- `SlimeMilkerBlock`, `SlimeMilkerBlockEntity`, `SlimeMilkSourceBlock` — all do `PFFluidTypes.VARIANTS.contains(variant)` + `PFItems.MILK_BUCKETS.get(variant)`. These work today because VARIANTS is a Java list known at compile time.
- `PFClientEvents` — registers per-variant tints. Currently driven by the hardcoded VARIANTS list (via the SLIME_VARIANT tint source already being data-driven).

### Lang surfaces (per-variant strings)

Each variant needs **7 lang entries** in `en_us.json`:

```
item.productivefrogs.slime_bucket.<variant>            (e.g. "Bucket of Iron Slime")
item.productivefrogs.<variant>_slime_milk_bucket       (e.g. "Bucket of Iron Slime Milk")
block.productivefrogs.<variant>_slime_milk             (e.g. "Iron Slime Milk")
fluid_type.productivefrogs.<variant>_slime_milk        (e.g. "Iron Slime Milk")
block.productivefrogs.configurable_froglight.<variant> (e.g. "Iron Froglight")
item.productivefrogs.<variant>_slime_spawn_egg         (e.g. "Iron Slime Spawn Egg")
entity.productivefrogs.resource_slime.<variant>        (e.g. "Iron Slime")
```

Modpacks would have to provide their own `en_us.json` overrides for every modded variant — workable but tedious. Worse, the keys reference variant names as path components, so the modpack's lang files need to know the exact PF key shape.

### Per-variant asset files

Each variant needs **9 files** auto-generated by scripts today:

```
assets/.../blockstates/<variant>_slime_milk.json
assets/.../items/<variant>_slime_milk_bucket.json
assets/.../items/<variant>_slime_spawn_egg.json
assets/.../models/item/<variant>_slime_milk_bucket.json
assets/.../textures/block/<variant>_slime_milk_still.png + .mcmeta
assets/.../textures/block/<variant>_slime_milk_flow.png + .mcmeta
assets/.../textures/item/<variant>_slime_milk_bucket.png
assets/.../textures/entity/slime/<variant>_resource_slime.png
```

The texture generation scripts (`generate_variant_slime_textures.ps1`, `generate_slime_milk_textures.ps1`) have hardcoded variant maps inside them. Modpacks shipping new variants currently have to either run their own copies of these scripts or hand-author all 9 files.

### Already data-driven (no work needed here)

- `SlimeVariant` datapack registry — the JSON-defined variant content (primer_item, category, primary_color, secondary_color, texture path).
- `configurable_froglight` Item — single item, variant via `SLIME_VARIANT` component. Adding a variant doesn't touch this item's registration.
- `slime_bucket` Item — single item, variant via `BUCKET_ENTITY_DATA` component.
- `resource_tadpole_bucket` Item — single item, category via component.
- `parent_species` datapack registry — modpacks add modded parent slimes via JSON (PR L9).

## Refactor target architecture

For each hardcoded surface above, the refactor target:

### Spawn eggs — single item with `SLIME_VARIANT` component

Replace 12 per-variant `SpawnEggItem` registrations with ONE `resource_slime_spawn_egg` item. Variant carried via `SLIME_VARIANT` data component. Same pattern proven by `configurable_froglight`.

- The item's `getName(ItemStack)` reads the component and emits a translatable name like `Component.translatable("item.productivefrogs.resource_slime_spawn_egg.named", variantName)` where `variantName` derives from the `SlimeVariant.name` field.
- Inventory tint already reads via the existing `productivefrogs:slime_variant` `ItemTintSource` — keeps working unchanged.
- Creative tab iterates `SLIME_VARIANT` registry entries (already does for `configurable_froglight`) and emits a stamped stack per variant.
- **Backwards compat**: existing world saves with old per-variant spawn egg items (`iron_slime_spawn_egg`) need a migration path. Options:
  - Keep the old per-variant items registered as deprecated aliases that auto-convert to the new component-stamped item on first inventory access.
  - Hard break — old items become invalid item refs in existing worlds, players lose them.
  - Recommended: deprecated aliases for one minor version, hard remove later.

### Slime milk fluid — load-bearing design decision

This is the hardest surface. Two viable approaches:

#### Approach A: Auto-register fluids at mod init from JSON discovery

At mod construction time (before datapacks load), scan all loaded mods' resource trees for `data/<ns>/productivefrogs/slime_variant/*.json`. For each filename discovered, register the matching fluid + LiquidBlock + BucketItem + FluidType via DeferredRegister. The actual variant CONTENT (color, category, texture path) still loads from the datapack registry at server startup — mod init only needs the NAME (filename) for registration.

- **Pro**: minimal change to existing code. The per-variant fluid model stays; the registration loop becomes dynamic. `PFFluidTypes.VARIANTS` is removed; the auto-discovery output is the new source of truth.
- **Pro**: per-variant texture paths still work (`<variant>_slime_milk_still.png`) — no fluid-rendering refactor.
- **Con**: scanning mod resources at mod init is non-standard. NeoForge's `ModList` exposes `IModFile.findResource(...)` which can list files in mod jars; need to verify this works pre-datapack-load.
- **Con**: assets must still be present per variant — modpacks ship the 9 asset files per variant. Auto-generation scripts need to also be data-driven (scan SlimeVariant JSONs and emit assets) so modpacks can reuse the PF scripts on their own datapacks. The asset surface is still tedious; only the Java surface goes away.

#### Approach B: Single generic `slime_milk` fluid + variant components

Collapse all 14 per-variant fluids into ONE `productivefrogs:slime_milk` Fluid + ONE LiquidBlock + ONE BucketItem.

- Variant carried on the BucketItem via `SLIME_VARIANT` data component (matches the `configurable_froglight` pattern).
- LiquidBlock subclassed with a BlockEntity (`SlimeMilkSourceBlockEntity`) that holds the variant Identifier. Source block placement reads the bucket's component and writes the variant to the BE.
- Fluid rendering: the `FluidType.getRenderProperties` returns a custom `IClientFluidTypeExtensions` that delegates `getStillTexture(FluidState, BlockAndTintGetter, BlockPos)` to look up the BE at `pos` and return the variant-specific texture path. **This is the hard part** — need to verify NeoForge's fluid rendering supports per-position texture lookup. Vanilla doesn't.
- Bucket-pickup flow: when the player picks up a source block with an empty bucket, the BE's variant is read and stamped onto the resulting Slime Milk Bucket.

- **Pro**: truly minimal asset surface — one set of textures (parameterised by tint or per-variant atlas entries) instead of 14 sets.
- **Pro**: modpacks add a variant by JSON only — no auto-generated assets per variant, no script extension needed.
- **Con**: massive refactor of the fluid pipeline. The per-position fluid texture lookup may not be supported by vanilla rendering — may need ASM/mixin hacks or custom render code.
- **Con**: backwards compat is harder — existing worlds have `iron_slime_milk:source` blocks in them; need a `WorldUpgrader` or block-state migration.
- **Con**: the SlimeMilker's "input slime bucket → output milk bucket" loop changes meaningfully — output is now a `slime_milk_bucket` stack with a `SLIME_VARIANT` component, not an `iron_slime_milk_bucket` distinct item.

**Recommendation (SUPERSEDED 2026-05-25 — see the Decision section at the top):** originally **Approach A**, on smaller-blast-radius grounds. Reversed because Approach A cannot make milk addable by a *world* datapack (fluids register before world datapacks load), and the per-position fluid-render risk that pushed against B turned out to be a non-issue (NeoForge exposes position-aware `getTintColor`). Approach B is the chosen path.

### Lang derivation — translation key fallback pattern

Instead of requiring per-variant lang entries, derive display names from the variant id with a translation-key fallback:

```
Component.translatable("variant.productivefrogs." + variantId.getPath(),
                       variantId.getPath().replace('_', ' ').titlecased())
```

The `titlecased(variantId.getPath())` fallback ensures unknown variants (modded) render readable names without their own lang file:

- `iron` → "Iron"
- `magma_cream` → "Magma Cream"
- `mythicmetals:adamantite` → "Adamantite"

Items that need composite names (e.g. "Iron Slime Spawn Egg") use a template translation:

```
item.productivefrogs.resource_slime_spawn_egg.named = "%s Slime Spawn Egg"
```

Where `%s` is the variant name from the pattern above. Three template lang entries replace the 7-entries-per-variant overhead.

PF's 14 shipped variants STILL get explicit lang entries to override the auto-cased default (so `iron` reads as "Iron" rather than the auto-derived "Iron" — same output, but explicit). Modpacks omit their lang entries and accept the auto-cased fallback, or ship overrides as desired.

### Asset auto-generation — scripts scan SlimeVariant JSONs

`generate_variant_slime_textures.ps1` and `generate_slime_milk_textures.ps1` are extended to:

1. Scan `data/<ns>/productivefrogs/slime_variant/*.json` across all directories the script is given (default: just PF's own).
2. For each variant, read the JSON to find the source-block reference (new optional field: `texture_source_block: "minecraft:iron_block"`).
3. Generate the 9 per-variant assets into the namespace's `assets/<ns>/` tree.

Modpacks can `git clone productive-frogs` and run the scripts against their own datapack folder to generate matching assets. Optional new field in `SlimeVariant`:

```json
{
  "primer_item": "minecraft:iron_ingot",
  "category": "metallic",
  "primary_color": 14211288,
  "secondary_color": 12632256,
  "texture": "productivefrogs:textures/entity/slime/iron_resource_slime.png",
  "texture_source_block": "minecraft:iron_block"
}
```

`texture_source_block` is optional; modpacks that hand-author textures don't need it. Scripts that auto-generate use it.

## Phase breakdown — PR sizing

### Phase 1 — Spawn eggs collapse + lang derivation (1 PR)

- Refactor `PFItems.buildSlimeVariantSpawnEggs()` to register ONE `resource_slime_spawn_egg`.
- Update creative tab to iterate `SLIME_VARIANT` registry for spawn egg display.
- Add `item.productivefrogs.resource_slime_spawn_egg.named = "%s Slime Spawn Egg"` template lang entry.
- Add variant-name derivation utility (`SlimeVariants.displayName(Identifier)`).
- Implement deprecated-alias migration for old per-variant spawn egg items (auto-convert on inventory access).
- Migrate existing 7-entries-per-variant lang to template form for slime_bucket, resource_slime entity display name (the simpler 2 of the 7). Keep the 5 fluid/milk-related entries for now (deferred to Phase 2).
- Regression test: spawn eggs in creative tab still work for all 12 V1 variants.

**Estimated**: ~2-3 days. Self-contained refactor; no fluid surface touched.

### Phase 2 — Milk fluid auto-registration (Approach A) (1 PR)

- Build `PFFluidTypes.discoverVariants()` that scans loaded mod jars at mod init for `data/<ns>/productivefrogs/slime_variant/*.json` filenames.
- Replace `PFFluidTypes.VARIANTS` hardcoded list with the discovery output.
- `PFFluids`, `PFBlocks.MILK_BLOCKS`, `PFItems.MILK_BUCKETS` already derive from VARIANTS — no change to their structure, just the source.
- Add the 5 remaining template lang entries (milk bucket, milk block, milk fluid).
- Regression test: all 14 V1 milk fluids/buckets/blocks still register correctly.
- Smoke test: drop a NEW SlimeVariant JSON into a test datapack folder, boot the dev client, verify the variant's slime/milk/bucket all appear without Java edits.

**Estimated**: ~1 week. Mod-resource scanning at mod init is non-standard; verification + testing carries the cost.

### Phase 3 — Asset auto-generation from variant JSONs (1 PR)

- Add optional `texture_source_block` field to `SlimeVariant` codec.
- Extend `generate_variant_slime_textures.ps1` to scan slime_variant JSONs in the repo (or a passed datapack dir) and use `texture_source_block` to drive the inner-cube generation.
- Extend `generate_slime_milk_textures.ps1` similarly.
- Document the modpack workflow in a new `docs/modpack_adding_variants.md`.
- Regression test: scripts produce identical output for V1's 12 variants after the refactor.

**Estimated**: ~2 days. Scripts are straightforward to extend.

### Phase 4 — Validation with a test "external" variant (1 PR)

- Add a brand-new variant via the JSON-only flow (e.g. `productivefrogs_test:bismuth`) shipped under a test datapack folder.
- Verify the full loop end-to-end: slime spawns from creative egg → can be captured in bucket → milker converts → milk source places → milk source spawns slime → kill drops Froglight → Froglight smelts to bismuth_ingot.
- This is the smoke test that "fully config based" actually works.
- Remove the test variant before merging (or ship it under a disabled-by-default flag).

**Estimated**: ~2-3 days. The bulk is end-to-end verification.

### Total estimated effort

≈ **2-3 weeks** of focused work across 4 PRs. Once shipped, V1.1 becomes a JSON-only release that can be authored and reviewed in a single day.

## Backwards compatibility

- **Existing world saves**: the 14 V1 fluids stay registered at their existing IDs (Approach A preserves them). Existing milk blocks in worlds load unchanged. No data migration needed for milk.
- **Spawn eggs in inventories**: Phase 1 keeps old per-variant spawn egg items registered as deprecated aliases that auto-convert. Players don't lose items.
- **Lang fallback**: Old lang keys remain working for one minor version (`item.productivefrogs.iron_slime_spawn_egg` still resolves) alongside the new template-based keys.
- **Modpack datapacks shipped against V1**: unchanged — they just continue to work since they only added SlimeVariant JSONs which are already datapack-driven.

## Testing strategy

- **Phase 1**: regression test all 12 spawn eggs render + spawn correctly post-refactor. New GameTest: spawn egg from a brand-new variant JSON (test-only datapack) spawns the right entity with the right tint.
- **Phase 2**: regression test all 14 milk fluids place + render + are pickup-able. New GameTest: drop a test variant JSON and verify the auto-registered fluid pipeline matches the V1-shipped pipeline for an existing variant.
- **Phase 3**: byte-compare regenerated V1 textures to the shipped ones (should match exactly).
- **Phase 4**: end-to-end test of a brand-new variant through the entire production loop.

## Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Mod-jar resource scanning at mod init is unsupported / unreliable in NeoForge | High | Spike Phase 2 first — verify the discovery approach works before committing the full refactor. If unsupported, fall back to a config-file-driven variants list (still better than hardcoded Java, but two-source). |
| Spawn egg migration loses existing player inventory items | Medium | Deprecated-alias pattern auto-converts on inventory access. Cover with GameTest. |
| Existing GameTests in `PFGameTests` reference per-variant items directly (e.g. `iron_slime_spawn_egg`) | Low | Refactor those tests to use the new component-based stack constructor; the test scaffold is straightforward. |
| Fluid rendering breaks subtly post-refactor (Approach A keeps per-variant fluids but the registration loop changes) | Low | Approach A only changes WHEN fluids register, not what they look like. Regression tests cover. |
| Modpack authors don't run the texture generation scripts, ship variants without assets | N/A | Document expected modpack workflow. Missing textures render as Minecraft's purple-black missing-texture indicator — visible and self-diagnostic. |

## Open design questions

- **Approach A vs B for milk fluids** — recommendation is A, but if Approach A's mod-init scanning turns out to be too fragile in practice, B becomes the default. Spike Phase 2 first.
- **Test variant in Phase 4 — keep or remove before merging?** Recommendation: keep it under a `productivefrogs:test_variants_enabled` config flag, default off. Lets us regression-test the data-driven flow on every CI build without polluting the player's creative tab.
- **What happens to PFFluidTypes.IRON_SLIME_MILK alias?** It's documented as "backwards-compatible alias for tests + J1 code." Should be removed in the refactor; nothing should reach for specific variants by name in Java post-refactor.
- **`texture_source_block` field — required or optional?** Recommendation: optional. Variants without it must hand-author the inner-cube texture. Most modpack authors will use it; some may want bespoke art.

## Success criteria

- [ ] All 12 V1 SlimeVariants still work identically after the refactor (regression tests green).
- [ ] A new SlimeVariant added via JSON-only (no Java changes, no lang edits) gets:
  - Spawn egg in creative tab with correct tint + name
  - Functioning Slime Bucket capture + release
  - Functioning Slime Milker conversion to milk bucket
  - Functioning Slime Milk source block that spawns the variant
  - Functioning kill drop (variant configurable_froglight)
  - Functioning smelt chain (if the modpack ships the smelting recipe JSON too)
- [ ] Asset generation scripts run unchanged against PF's own variants and produce identical output.
- [ ] Asset generation scripts can be pointed at an external datapack folder and produce assets for that pack's variants.
- [ ] `docs/modpack_adding_variants.md` documents the modpack workflow end-to-end.
- [ ] V1.1 implementation (the 16 new variants) becomes pure JSON authoring — fitting the original V1.1 "pure JSON" framing.
