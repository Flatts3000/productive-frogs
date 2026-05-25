# Code Review Audit - Client Rendering (2026-05-24)

Specialist: code reviewer (Sonnet). Scope: `client/PFClientEvents.java`, `client/jei/*`,
`client/screen/*`, `client/renderer/*` (16 files). Raw findings preserved for posterity. See
`docs/code_review_2026_05_24.md` for the synthesis. IDs here (CL-N) are this reviewer's own numbering.

CLAUDE.md decisions confirmed against the actual code before being set aside: RenderType-batching
draw order, vanilla model subclassing, and `RegisterColorHandlersEvent.Item` being the correct
1.21.1 API (the code uses it; CLAUDE.md's ItemTintSource text describes a later MC version).

## CRITICAL
None found.

## HIGH

### CL-1: parentSpeciesBlock does an O(N) registry scan every frame per visible slime
File: `client/renderer/ResourceSlimeInnerBlockLayer.java:131-143`. Called every frame for every
parent-species slime; linearly scans `PARENT_SPECIES` and fetches the entity-type key to recompute
a `BlockState` that is fixed for the world session. Resolve once: pass the `BlockState` into the
layer at construction time (parent species map 1:1 to renderer classes), paralleling how
`outerTexture` is now a constructor argument; for the variant path, a small cache cleared on
resource reload. [Synthesis CR-5.]

### CL-2: ResourceSlimeRenderer.getTextureLocation fallback + null-map risk
File: `client/renderer/ResourceSlimeRenderer.java:78`. Fallback for a non-ResourceSlime is
`Category.BOG` (silent wrong-texture render). Also `TEXTURES.get(cat)` can return null if the
enum ever gains a value with no shipped PNG, NPE-ing at render. Use `getOrDefault` and/or a
`MissingTextureAtlasSprite` sentinel; null-guard the map lookup.

### CL-3: Verify ResourceTadpole.getCategory() has the ordinal-bounds guard
Files: `client/renderer/ResourceFrogRenderer.java:29-31`, `ResourceTadpoleRenderer.java:21-23`.
`ResourceFrog.getCategory()` is guarded (never null), so its tint lambda is safe. The same lambda
pattern calls `ResourceTadpole.getCategory()`; confirm it has the same defensive fallback (it does,
per the content audit) - a client NPE crashes the game. Verification task.

## MEDIUM

### CL-4: Minecraft.getInstance() per frame in outer layers [vanilla parity]
Files: `client/renderer/ResourceSlimeOuterLayer.java:46`, `TintedSlimeOuterLayer.java:57`. Matches
vanilla `SlimeOuterLayer`; a static-field read, not an allocation. No change; documents the cost.

### CL-5: parentSpeciesBlock reads the datapack registry via entity.level() on the render thread
File: `client/renderer/ResourceSlimeInnerBlockLayer.java:133`. Correct and safe in 1.21.1 (client
registry manager is synced at login); the null path during pre-sync join is handled. Documents the
dependency; the per-frame cost is the CL-1 concern.

### CL-6: SlimeMilkerScreen has no label coords / renderLabels override
File: `client/screen/SlimeMilkerScreen.java`. Inherits vanilla label defaults (title 8,6; inventory
8,imageHeight-94), probably fine for a 176x166 furnace layout but needs an in-client eyeball to
confirm labels do not render over slots. [Synthesis CR-16.]

### CL-7: JEI getLegacyStringSubtypeInfo casts getSubtypeData result to String
File: `client/jei/ProductiveFrogsJeiPlugin.java:95`. Safe today (all interpreters return String),
but a future change returning a non-String throws only at runtime in the JEI sidebar. Make
`getSubtypeData` return `String` directly, or comment the cast assumption.

### CL-8: ResourceSlimeInnerBlockLayer transform constants are unverified
File: `client/renderer/ResourceSlimeInnerBlockLayer.java:63-64`. `BLOCK_EDGE` / `CENTER_Y` carry a
"TUNE IN-CLIENT" comment (honest, not a defect). Replace with a tracked issue reference and confirm
values before a release build. [Synthesis CR-19.]

## LOW

### CL-9: Six parent-species renderers are structurally identical
Files: `client/renderer/{Bog,Cave,Geode,Infernal,Tide,Void}SlimeRenderer.java`. Differ only in
`TEXTURE` + `OUTER_TINT_ARGB`; constructor body identical. Collapse to one `ParentSlimeRenderer`
constructed with per-species args in `PFClientEvents`. [Synthesis CR-8 / dead-code D-11.]

### CL-10: TintedSlimeOuterLayer refactor verified clean
File: `client/renderer/TintedSlimeOuterLayer.java:66`. The refactor correctly removed only the
texture-lookup indirection, not the model-copy path (`getParentModel()` is RenderLayer's own). No bug.

### CL-11 / CL-12: Fully-qualified class names in lambda/method bodies
File: `client/PFClientEvents.java:98` (ConfigurableFroglightBlockEntity inline FQN),
`:249-252` (SpawnEggItem, java.util.function.Consumer inline FQN). Add imports; optionally extract a
`registerSpawnEggCtorColors` helper. [Synthesis CR-17.]

### CL-13: getCategory() fallback named BOG but entity docs say METALLIC
File: `client/renderer/ResourceSlimeRenderer.java:78` and the entity getCategory() comments. Doc
drift from the rename. [Synthesis CR-3.]

## Summary
| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH | 3 (CL-1, CL-2, CL-3) |
| MEDIUM | 5 |
| LOW | 5 |
