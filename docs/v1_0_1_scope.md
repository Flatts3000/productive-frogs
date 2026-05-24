# v1.0.1 Scope - Native-resolution Resource Slime inner-cube textures

## Status

**Spec; not yet implemented.** Patch release on top of v1.0.0 (shipped 2026-05-24). Visual polish only - no behavior changes, no data migration, no API surface change.

## Motivation

In v1.0 the Resource Slime inner cube renders the variant's vanilla block texture **downsampled from 16x16 to 6x6** nearest-neighbor and stamped into a custom 64x32 atlas. Vanilla `SlimeModel`'s inner cube is a 6x6x6 box, and each face only gets 6 pixels of UV resolution.

At small slime sizes (1-2) the difference is invisible (the box renders at ~16-20 screen pixels either way). At slime size 4 the inner cube renders at 60+ pixels and the 6x6 source visibly blurs. v1.0.1 promotes the inner cube to native 16x16 per face so the iron-block texture inside an Iron Slime IS the iron-block texture, byte-identical to vanilla.

Cosmetic polish only. Production loop, drops, AI, tints, infusion semantics, JEI subtypes - all unchanged.

## Approach

**Two-pass entity rendering.** Split the slime model render into two `RenderType` bindings:

1. **Pass 1 (outer shell + eyes + mouth):** rendered against the existing per-category atlas (`<category>_resource_slime.png`, 64x32, unchanged from v1.0).
2. **Pass 2 (inner cube only):** rendered against the variant's vanilla resource block texture directly, bound by `ResourceLocation` (e.g., `minecraft:textures/block/iron_block.png`). The inner cube's UVs span `(0, 0)..(16, 16)` of the bound texture, so each face displays the full vanilla block at native 16x16.

This works because Minecraft's `EntityRenderer` doesn't require a single texture per model. `ModelPart#render(PoseStack, VertexConsumer, ...)` takes a single buffer; the renderer obtains one buffer per `RenderType` from the `MultiBufferSource`, and the same model can be split across multiple sub-renders by calling individual `ModelPart#render` on the parts you want grouped under each texture. Vanilla uses this pattern for paintings (per-art texture), armor stands (per-armor-layer), banner patterns, and others.

**Why this beats the original "subclassed model + grow the atlas to 64x64" approach:**

| Aspect | Original Path 1 (16x16x16 inner cube + atlas grow to 64x64) | Two-pass rendering (this spec) |
|---|---|---|
| Per-variant atlas PNG | 12 regenerated to new 64x64 layout | All 12 **deleted**; per-category template absorbs the role |
| Generator script | Rewritten to stamp 16x16 tiles into a 64x64 atlas | **Deleted** (or trimmed to handle parent species only) |
| Adding a v1.1 variant | 4 files (JSON + texture + recipe + lang) | 3 files (JSON + recipe + lang); the JSON's `inner_texture` field points at a vanilla block PNG, no per-variant atlas to author |
| Animated vanilla sources (sea lantern, redstone block glow, magma) | Static tile only - the downsample collapsed animation frames | **Works for free** - the vanilla animation system runs on the directly-bound texture |
| Modpack-custom variant migration | Required: regen against new 64x64 layout | None: third-party variants ship a `SlimeVariant` JSON with `inner_texture` pointing at any vanilla or modded block PNG |

Two-pass adds slightly more renderer code (~50 lines for the split-render method) and one extra draw call per slime (negligible). The premium buys away the atlas regeneration burden, removes 12 PNGs from the jar, and unlocks animated inner textures.

## File changes

### New code

| Path | Purpose |
|---|---|
| `client/model/ResourceSlimeModel.java` | Subclass vanilla `SlimeModel` (or a parallel `HierarchicalModel<SlimeRenderState>`) that exposes `getInnerCubePart()` and `getOuterPart()` (plus eyes/mouth) as public ModelParts so the renderer can render them independently. Vanilla SlimeModel keeps the inner cube private. |
| `client/PFModelLayers.java` | Holds the `RESOURCE_SLIME` `ModelLayerLocation` for the new model. (May already exist; if so, extend.) |

### Modified code

| Path | Change |
|---|---|
| `event/PFModBusEvents.java` (or wherever `EntityRenderersEvent.RegisterLayerDefinitions` is wired) | Register `ResourceSlimeModel.createBodyLayer()` against the new `ModelLayerLocation`. |
| `client/renderer/ResourceSlimeRenderer.java` | Override `render(...)` (or `extractRenderState` -> custom submit chain in 1.21.1 idiom) to do two-pass: bind the per-category atlas and render outer + eyes + mouth, then bind `state.innerTexture` and render the inner cube. The existing outer-shell `ResourceSlimeOuterLayer` keeps working since it operates on the outer cube only. |
| `client/renderer/{Bog,Cave,Geode,Tide,Infernal,Void}SlimeRenderer.java` | Same two-pass treatment. Each parent species gets the upgraded inner-cube resolution rendering its species' canonical resource block. |
| `data/SlimeVariant.java` | Add `inner_texture: ResourceLocation` field to the codec. Required field for new variants; default fallback to `minecraft:textures/block/<derived>` for legacy entries during migration. |
| `data/ParentSpeciesEntry.java` | Add same `inner_texture` field to the parent-species codec. Each of the 6 shipped parent species gets a default texture in its datapack JSON. |
| `client/renderer/state/ResourceSlimeRenderState.java` (if exists; otherwise extend the equivalent) | Carry the resolved `innerTexture: ResourceLocation` from the slime entity's variant to the renderer. |

### Deleted assets

| Path | Why |
|---|---|
| `assets/productivefrogs/textures/entity/slime/iron_resource_slime.png` and 11 siblings | The per-category template now covers the outer-shell render. Inner cube binds to the vanilla block PNG directly. |

### Deleted / trimmed scripts

| Path | Change |
|---|---|
| `scripts/generate_variant_slime_textures.ps1` | **Delete.** The 12 outputs it produced are no longer needed. |

### Modified data files

| Path | Change |
|---|---|
| `data/productivefrogs/productivefrogs/slime_variant/iron.json` and 11 siblings | Add `"inner_texture": "minecraft:block/iron_block"` (or per-variant equivalent). The existing `texture` field stays for the outer-shell atlas lookup (now equals the per-category template path). |
| `data/productivefrogs/productivefrogs/parent_species/cave_slime.json` and 5 siblings | Add same `inner_texture` field. Each parent species points at a thematically-appropriate vanilla block (e.g., Cave -> `minecraft:block/stone`, Geode -> `minecraft:block/amethyst_block`, Tide -> `minecraft:block/prismarine`, Infernal -> `minecraft:block/netherrack`, Void -> `minecraft:block/end_stone`, Bog -> `minecraft:block/moss_block` or similar). |

## Variant -> vanilla block mapping

Twelve shipped variants. Source-block-name guesses for the `inner_texture` field:

| Variant | Inner texture |
|---|---|
| iron | `minecraft:block/iron_block` |
| copper | `minecraft:block/copper_block` |
| gold | `minecraft:block/gold_block` |
| redstone | `minecraft:block/redstone_block` |
| lapis | `minecraft:block/lapis_block` |
| coal | `minecraft:block/coal_block` |
| diamond | `minecraft:block/diamond_block` |
| emerald | `minecraft:block/emerald_block` |
| prismarine | `minecraft:block/prismarine` |
| sponge | `minecraft:block/sponge` |
| magma_cream | `minecraft:block/magma` |
| ender_pearl | `minecraft:block/end_stone` |

Six parent species (resolved):

| Species | Inner texture |
|---|---|
| Bog | `minecraft:block/moss_block` |
| Cave | `minecraft:block/stone` |
| Geode | `minecraft:block/amethyst_block` |
| Tide | `minecraft:block/prismarine` |
| Infernal | `minecraft:block/netherrack` |
| Void | `minecraft:block/end_stone` |

## Backward compatibility

- **Worlds**: no data migration. Slime entities don't store rendering data; they re-render with the new model on next chunk load.
- **Variant JSON without an `inner_texture` field** (typo, modded block from an absent mod, third-party variant not yet updated): codec accepts the missing / unresolved field; the renderer falls back to the vanilla missing-texture sprite (`MissingTextureAtlasSprite.getLocation()`, the purple/black checker). Visually loud, doesn't crash, easy to spot in playtest.
- **Existing v1.0 variant JSONs**: this PR ships the `inner_texture` field populated for all 12 shipped variants and the 6 parent species, so the in-tree state has zero degradation.

## Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Two-pass render order: translucent outer shell rendered before opaque inner cube causes Z-fighting or wrong alpha sorting | Medium | Render inner cube FIRST (opaque pass), outer shell SECOND (translucent pass). Vanilla `RenderType.entityCutout` for inner, `RenderType.entityTranslucent` for outer. Matches vanilla slime render order. |
| Vanilla `SlimeModel.innerCube` is private; can't get the part directly | High | Subclass `SlimeModel` (or rebuild the model from scratch with the same `LayerDefinition` and our own public-field structure). The model API was redesigned in 1.21.x; verify the actual field visibility during implementation. |
| Inner-cube vertex normals are off because we render it with a different RenderType than the outer cube | Low | Use the same lighting setup; the RenderType change is only about which texture is bound. |
| `inner_texture` points at a missing file (typo, modded block from an absent mod) | Medium | Renderer falls back to `MissingTextureAtlasSprite.getLocation()` (vanilla missing-texture purple/black checker) at render time. Visually loud, doesn't crash. See Backward compatibility section. |
| Animated source textures (e.g., a future variant pointing at `minecraft:block/sea_lantern`) interact weirdly with the slime's squish animation | Low | The animation is a texture-level UV cycle; our render doesn't override it. Should just work; verify in playtest if any animated variant ships. |
| Removing the 12 variant atlas PNGs breaks anything still referencing them | Low | Grep the codebase before delete. Likely references: `SlimeVariant` codec (existing `texture` field), renderer (resolved via state). Both can be redirected. |
| GameTests fail because they pixel-compare rendered slimes | Very low | PF tests are world-state tests, not pixel tests. |

## Test plan

- [ ] `./gradlew build` green
- [ ] `./gradlew runGameTestServer` green (no regression - tests are world-state, not pixel)
- [ ] Smoke-test in `runClient`:
  - Spawn each of the 12 variant Resource Slimes at size 1, 2, 4 via `/summon` + `/data merge entity`
  - Confirm inner-cube faces render the **native 16x16** vanilla block texture (visually crisp at size 4)
  - Confirm outer-shell tint still applies correctly (variant primary color)
  - Confirm eyes / mouth still render
  - Confirm Slime Bucket pickup + release preserves variant
  - Spawn each of the 6 parent species at size 1, 2, 4
  - Confirm parent species inner cube renders the species' configured vanilla block at native 16x16
  - Verify a variant with an animated source (manually point one variant's `inner_texture` at `minecraft:block/sea_lantern` for the test) animates correctly
- [ ] Visual diff: side-by-side screenshot of v1.0 vs v1.0.1 at slime size 4
- [ ] JEI subtype: confirm variant slime spawn eggs still surface as distinct entries in JEI

## Ship checklist

- [ ] All implementation in one PR (new model + renderer wiring + 7 renderer touches + codec field + 18 JSON updates + 12 PNG deletions + script deletion)
- [ ] Bump `mod_version` to `1.0.1` in `gradle.properties`
- [ ] Write CHANGELOG.md `## v1.0.1` entry: visual-polish framing, no behavior change, note the asset cleanup (12 fewer PNGs in the jar)
- [ ] Tag `v1.0.1` on the merge commit
- [ ] `gh release create v1.0.1` with the CHANGELOG section as release notes
- [ ] `.\scripts\ship.ps1 -PublishCurseForge` ships build + GH attach + CF publish in one command
- [ ] Update `ROADMAP.md` if needed (likely not; patches are CHANGELOG-tracked, ROADMAP is per-minor)

## Out of v1.0.1 scope

- New variants (those land in v1.1; v1.1 variant JSONs gain the same `inner_texture` field)
- Cross-mod variants (v1.2)
- Frog model upgrades (frogs don't have a "resource block inside" semantic)
- Refactoring the per-category template PNGs (still useful as the outer-shell atlas for each species)

## Resolved decisions

- **Missing-texture fallback behavior**: render the vanilla missing-texture sprite (purple/black checker) at render time. No load-time error, no derive-from-variant-id heuristic. Visually loud, doesn't crash.
- **Parent-species inner textures**: approved per the table above (Bog -> moss_block, Cave -> stone, Geode -> amethyst_block, Tide -> prismarine, Infernal -> netherrack, Void -> end_stone).
- **CHANGELOG migration note**: skip. No third-party variants are known to exist between v1.0 and v1.0.1.
