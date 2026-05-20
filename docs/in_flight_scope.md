# In-Flight Scope — Egg/Tadpole/Frog Visual + Behavioral Gaps

This doc captures the requirements and implementation status for the active PR
closing the egg/tadpole/frog gaps. Updated as items land; deleted (or moved to
historical notes) once the PR merges and the gaps are gone.

## Goal

After this PR, the egg/tadpole/frog content side is **truly complete** for V1:

- Category info propagates through every container transition
  (block ↔ bottle ↔ entity ↔ bucket) without ever being silently lost.
- Every category variant is visually distinguishable in-game.
- The remaining V1 work (Resource Slimes, Slime Milker, Froglight drops,
  Froglight processing) is the only thing left between us and a playable mod.

## Gaps being closed

### Gap 1 — NBT-tagged Frog Egg item

**Why it exists**: bottling a Primed Frog Egg block currently does nothing —
the `FrogspawnBottlingHandler` only recognizes vanilla `minecraft:frogspawn`.
Players who primed an egg in the wrong spot can't relocate it.

**Requirements**:

- New data component `productivefrogs:contained_category` typed `Category`,
  registered via `DeferredRegister<DataComponentType<?>>`.
- `FrogspawnBottlingHandler` recognizes Primed Frog Egg blocks too. When the
  player right-clicks one with an empty glass bottle, the block is consumed
  and the bottle becomes a Frog Egg with the matching category set on the
  `contained_category` component.
- `FrogEggItem.useOn` branches on the data component: absent → place vanilla
  frogspawn; present → place the matching Primed Frog Egg block.
- `FrogEggItem.getName(stack)` overridden to return a per-category translation
  key when the component is present.
- Lang adds 6 keys: `item.productivefrogs.frog_egg.metallic` through `arcane`.

### Gap 2 — Resource Tadpole Bucket

**Why it exists**: bucketing a Resource Tadpole currently inherits vanilla
`Tadpole.getBucketItemStack()` which returns `Items.TADPOLE_BUCKET` — a vanilla
tadpole bucket that has no category information. Release loses the category.

**Requirements**:

- New item `productivefrogs:resource_tadpole_bucket`, registered as a subclass
  of `MobBucketItem` (or vanilla `MobBucketItem` directly with a custom
  display-name override).
- `ResourceTadpole` overrides:
  - `getBucketItemStack()` → returns our bucket
  - `saveToBucketTag(stack)` → calls super, then writes `Category` string to
    the bucket's `bucket_entity_data` component
  - `loadFromBucketTag(tag)` → calls super, then reads `Category` and sets it
- Custom display name via `getName(ItemStack)`: "Bucket of Metallic Tadpoles",
  etc. — same per-category translation key pattern as the Frog Egg.
- Lang adds 6 keys: `item.productivefrogs.resource_tadpole_bucket.metallic`
  through `arcane` + a default.

## V1 visual decisions (locked)

Already locked in conversation; documented here for permanence:

| Question | Decision |
|---|---|
| Bespoke per-category textures or tint-based? | **Tint-based.** Single base texture (or vanilla texture) + `Category.tintArgb()`. |
| Frog Egg bottle visual? | **Two-layer item model.** Layer 0 = static glass bottle exterior. Layer 1 = gelatinous contents, tinted via `ItemColor`. Mirrors vanilla potion-bottle pattern exactly. |
| Resource Tadpole bucket visual? | Same two-layer pattern with iron-bucket exterior + tadpole-silhouette content tinted by `ItemColor`. |
| Resource Tadpole / Resource Frog entity visual? | **Custom renderer + RenderState subclass** with `getModelTint(state)` override returning `category.tintArgb()`. Vanilla textures reused as base. Will read crisp once grayscale-base textures land; readable but muddy with current vanilla green base. |
| Do primed eggs emit light? | **No.** Dropped Froglights (the eat-result) will be the emissive resource block, not the egg. |

## Implementation checklist

Tracked here so re-entering the work doesn't lose state.

- [x] `Category.tintArgb()` + `tintRgb()` + `Codec` + `StreamCodec`
- [x] `PFDataComponents.CONTAINED_CATEGORY` registration
- [x] `ProductiveFrogs.java` wires `PFDataComponents.register`
- [x] `FrogEggItem.useOn` branches on data component
- [x] `FrogEggItem.getName` dynamic name override
- [ ] `FrogspawnBottlingHandler` recognizes Primed Frog Egg blocks
- [ ] Lang: 6 per-category Frog Egg names
- [ ] Frog Egg two-layer item model (`bottle.png` exterior + `contents.png` tintable)
- [ ] `BlockColor` for Primed Frog Eggs (client)
- [ ] `ItemColor` for Frog Egg + Primed Frog Egg BlockItems + Resource Tadpole Bucket (client)
- [ ] Primed Frog Egg block models: unify to a single shared model with `tintindex: 0`
- [ ] Primed Frog Egg blockstate JSONs: point at the shared model
- [ ] `ResourceTadpoleRenderState` + `ResourceTadpoleRenderer` (entity tint)
- [ ] `ResourceFrogRenderState` + `ResourceFrogRenderer` (entity tint)
- [ ] Update `PFClientEvents` to register custom renderers (replace vanilla)
- [ ] Resource Tadpole Bucket item registration
- [ ] `ResourceTadpole.getBucketItemStack` override
- [ ] `ResourceTadpole.saveToBucketTag` / `loadFromBucketTag` overrides (carry Category)
- [ ] Resource Tadpole Bucket item model (two-layer)
- [ ] Lang: 6 per-category Tadpole Bucket names
- [ ] Build green
- [ ] Commit + push + open PR
- [ ] Resolve Copilot comments
- [ ] Merge

## Out of scope for this PR

- **Frog breeding** (Q7 was answered "same-category breeding → Primed Frog
  Egg block placed on nearby water") — separate follow-up PR.
- **Real per-category textures** (vs. tint applied to vanilla / single base) —
  picked up with the texture batch.
- **Resource Frog tongue/prey filtering** — needs Resource Slimes to exist.
- **Resource Slimes themselves** — entirely separate PR series.
