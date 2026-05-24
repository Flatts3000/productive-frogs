# v1.0.1 Scope - Native-resolution Resource Slime inner-cube textures

## Status

**Spec; not yet implemented.** Patch release on top of v1.0.0 (shipped 2026-05-24). Visual polish only - no behavior changes, no data migration, no API surface change.

## Motivation

In v1.0 the Resource Slime inner-cube renders the variant's vanilla block texture downsampled from 16x16 to 6x6 nearest-neighbor. Vanilla `SlimeModel`'s inner cube is a 6x6x6 box on a 64x32 texture, so each face only has 6 pixels of resolution.

At small slime sizes (1-2) the difference is invisible (the box renders at ~16-20 screen pixels either way). At slime size 4 the inner cube renders at 60+ pixels and the 6x6 source visibly blurs. v1.0.1 promotes the inner cube to native 16x16 per face so the iron-block texture inside an Iron Slime IS the iron-block texture, not a downsampled approximation.

Cosmetic polish only. Production loop, drops, AI, tints, infusion semantics, JEI subtypes - all unchanged.

## Approach

**Path 1** (subclass SlimeModel, scale a 16x16x16 inner cube down to vanilla's visual size). Chosen over Path 2 (render an actual vanilla block via `BlockRenderDispatcher` inside the slime) because:

- Smaller blast radius. No bridging variant ID -> Block lookup; the existing texture pipeline already maps variant -> texture path correctly.
- No per-frame block-render cost.
- Animated vanilla textures (sea lantern, redstone block glow) wouldn't apply anyway since the existing variant set is mostly static blocks. If a future variant wants animated source, revisit Path 2 at that point.
- Preserves the existing `ResourceSlimeOuterLayer` / `TintedSlimeOuterLayer` tint pipeline unchanged.

**Geometry trick.** The 3D box dimensions and the texture pixel mapping are independent in `CubeListBuilder`. By defining the inner cube as a 16x16x16 box at the model level and applying a `0.375f` (= 6/16) scale in `setupAnim` (via `ModelPart.xScale/yScale/zScale`), the cube renders at the same visual size as vanilla's 6x6x6 inner cube but its faces UV-map to 16x16 pixel regions on the texture. No per-face UV override needed.

## File changes

### New code

| Path | Purpose |
|---|---|
| `client/model/ResourceSlimeModel.java` | Subclass `SlimeModel` (or a parallel `HierarchicalModel<SlimeRenderState>`). `createBodyLayer()` builds the outer cube as vanilla does (8x8x8 at `texOffs(0, 0)`) but the inner cube as 16x16x16 at `texOffs(0, 16)`. `setupAnim` applies the 0.375 scale to the inner cube ModelPart. |
| `client/PFModelLayers.java` (or extend an existing layer-IDs file) | Registers `RESOURCE_SLIME` `ModelLayerLocation` so the renderer can `bakeLayer(...)` it. |

### Modified code

| Path | Change |
|---|---|
| `event/PFModBusEvents.java` (or wherever `EntityRenderersEvent.RegisterLayerDefinitions` is wired) | Register `ResourceSlimeModel.createBodyLayer()` against the new `ModelLayerLocation`. |
| `client/renderer/ResourceSlimeRenderer.java` | Construct with the new model in place of vanilla `SlimeModel`. The existing layer (outer-shell tint, eyes/mouth) keeps working since the outer-cube UV layout is unchanged. |
| `client/renderer/{Bog,Cave,Geode,Tide,Infernal,Void}SlimeRenderer.java` | Same swap. Each parent species also gets the upgraded inner-cube resolution. |

### Modified scripts

| Path | Change |
|---|---|
| `scripts/generate_variant_slime_textures.ps1` | Rewrite to (a) use the new 64x64 texture canvas, (b) stamp the vanilla 16x16 block tile onto each inner-cube face *without* downsampling, (c) preserve the outer-cube + eyes/mouth regions from the existing per-category template. |
| `scripts/generate_parent_slime_textures.ps1` (if it exists; otherwise create) | Same treatment for the 6 parent-species PNGs. |

### Regenerated assets

All 18 PNGs in `src/main/resources/assets/productivefrogs/textures/entity/slime/`:

- 12 `<variant>_resource_slime.png` (iron, copper, gold, redstone, lapis, coal, diamond, emerald, prismarine, sponge, magma_cream, ender_pearl)
- 6 parent species PNGs (`bog_slime`, `cave_slime`, `geode_slime`, `tide_slime`, `infernal_slime`, `void_slime`)
- 6 per-category template PNGs (`metallic_resource_slime`, `mineral_resource_slime`, etc. - the templates that the variant generator overlays onto)

Total: 24 PNGs regenerated. All bumped from 64x32 to 64x64 (the smallest power-of-2 atlas that fits 6 inner-cube faces of 16x16 each plus the existing outer-cube + eyes/mouth regions).

## Texture layout

Proposed 64x64 atlas:

```
       0      8      16     24     32     40     48     56     64
   0  +------+------+------+------+------+------+------+------+
      | outer cube faces: top, bottom, west, front, east, back
      | + eyes/mouth (vanilla SlimeModel positions, unchanged)
   16 +------+------+------+------+------+------+------+------+
      | inner cube top    | inner cube bottom |
   32 +-------------------+-------------------+
      | inner cube west   | inner cube front  |
   48 +-------------------+-------------------+
      | inner cube east   | inner cube back   |
   64 +-------------------+-------------------+
```

Inner cube faces are 16x16 each. Six faces in a 2x3 grid below the outer-cube band. The top 16 rows preserve vanilla `SlimeModel`'s outer-cube + eyes/mouth UV regions unchanged so the existing tint / outer-shell pipeline (`ResourceSlimeOuterLayer`, `TintedSlimeOuterLayer`, eyes color) doesn't need touching.

(Final layout TBD during implementation; the key constraint is "outer cube UV regions match vanilla so we don't have to re-touch the outer-shell render path.")

## Model definition

```java
public class ResourceSlimeModel<S extends SlimeRenderState> extends HierarchicalModel<S> {
    private static final float INNER_SCALE = 6f / 16f;  // 0.375

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Outer cube: unchanged from vanilla
        root.addOrReplaceChild("cube",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-4f, 16f, -4f, 8f, 8f, 8f),
            PartPose.ZERO);

        // Inner cube: 16x16x16 in model space, scaled down in setupAnim
        root.addOrReplaceChild("inner_cube",
            CubeListBuilder.create()
                .texOffs(0, 16)
                .addBox(-8f, 8f, -8f, 16f, 16f, 16f),
            PartPose.ZERO);

        // Eyes + mouth: unchanged from vanilla (positions on the outer cube)
        // ...

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(S state) {
        ModelPart inner = root().getChild("inner_cube");
        inner.xScale = INNER_SCALE;
        inner.yScale = INNER_SCALE;
        inner.zScale = INNER_SCALE;
        // ... vanilla squish animation copied from SlimeModel
    }
}
```

Exact box positions / pose are TBD during implementation - the snippet above is illustrative.

## Backward compatibility

- **Worlds**: no data migration. Slime entities don't store rendering data; they re-render with the new model on next chunk load. Old `<variant>_resource_slime.png` files in shipped jars are silently superseded by the new 64x64 versions.
- **Modpacks shipping custom variant textures**: their existing 64x32 PNGs would render with the *new* model, which means the inner-cube face regions would land in the wrong place (current PNGs put inner-cube content at `(0, 16)..(24, 28)`; new model expects 16x16 faces in the 16-64 row band). Modpacks adding variants between v1.0 and v1.0.1 would need to regenerate their textures against the new layout.
  - Mitigation: bundle a migration note in the CHANGELOG entry. Most modpacks adding variants haven't shipped yet (mod is < 1 month old at v1.0.1 cut).

## Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Subtle UV alignment bug puts wrong texture region on a face | Medium | Visual regression diff between v1.0 and v1.0.1 renders side by side |
| Inner-cube scale-down via `ModelPart.xScale/yScale/zScale` causes lighting / normal recalc to shift visibly | Low | Test in playtest at sizes 1, 2, 4 |
| Existing `ResourceSlimeOuterLayer` rendering breaks if I accidentally touch the outer-cube UV | Low | Keep outer-cube definition byte-identical to vanilla `SlimeModel.createBodyLayer` |
| The `bakeLayer(...)` lookup in Renderer breaks because layer ID isn't registered | Medium | Add the layer registration in the SAME PR that adds the model, with a runClient smoke-test before merge |
| GameTests fail because they pixel-compare rendered slimes | Very low | PF's tests are world-state tests; none assert on rendered pixels |

## Test plan

- [ ] `./gradlew build` green
- [ ] `./gradlew runGameTestServer` green (no regression - tests are world-state, not pixel)
- [ ] Smoke-test in `runClient`:
  - Spawn each of the 12 variant Resource Slimes at size 1, 2, 4 via `/summon` + `/data merge entity`
  - Confirm inner-cube faces render the native 16x16 vanilla block texture
  - Confirm outer-shell tint still applies correctly
  - Confirm eyes / mouth still render
  - Confirm Slime Bucket pickup + release preserves variant
  - Spawn each of the 6 parent species at size 1, 2, 4
  - Confirm parent species inner cube also renders correctly per its per-species texture
- [ ] Visual diff: side-by-side screenshot of v1.0 vs v1.0.1 at slime size 4 (where the upgrade is most visible)
- [ ] JEI subtype: confirm variant slime spawn eggs still surface as distinct entries in JEI

## Ship checklist

- [ ] All implementation in one PR (model + renderer wiring + generator script + regenerated PNGs)
- [ ] Bump `mod_version` to `1.0.1` in `gradle.properties`
- [ ] Write CHANGELOG.md `## v1.0.1` entry: visual-polish framing, "no behavior change", note modpack-custom-texture migration if relevant
- [ ] Tag `v1.0.1` on the merge commit
- [ ] `gh release create v1.0.1` with the CHANGELOG section as release notes
- [ ] `.\scripts\ship.ps1 -PublishCurseForge` ships build + GH attach + CF publish in one command
- [ ] Update `ROADMAP.md` if the v1.0 section needs a v1.0.1 footnote (likely not - patches are CHANGELOG-tracked, ROADMAP is per-minor)

## Out of v1.0.1 scope

- New variants (those land in v1.1)
- Cross-mod variants (v1.2)
- Path 2 (`BlockRenderDispatcher` inside the slime) - parked unless an animated-texture variant ships and needs it
- Frog model upgrades (frogs don't have a "resource block inside" semantic; their texture is just per-species)

## Open questions

- **Texture canvas size**: 64x64 is the proposal. If outer + eyes + 6x 16x16 inner faces don't fit, bump to 64x96 or 128x32. Decide during implementation by laying out the actual UVs.
- **Should parent species also get this?** Yes by default - they're slimes too and their per-species inner cube benefits from the same upgrade. Spec assumes yes; flag if not wanted.
- **Migration note in CHANGELOG**: include modpack-custom-texture warning, or assume the surface area is small enough (no shipped third-party variants known) to skip?
