# Frog Net

The Frog Net (issue #205) is a reusable tool that catches a Resource Frog into
the item and releases it elsewhere, stats intact - so a bred-up frog can be
relocated, or a Terrarium restocked, without leashing, water-pushing, or killing
it.

## Behavior

- **Catch:** right-click a Resource Frog with an empty net. The frog is removed
  from the world and stored in the net; the net renders "filled" and its name
  reads `Frog Net (Cave Frog)`.
- **Release:** right-click a block face with a loaded net. The frog spawns in the
  cell adjacent to that face and the net returns to empty (reusable - it is not
  consumed).
- **Scope:** catches **only** Productive Frogs Resource Frogs. Vanilla frogs,
  Resource Tadpoles (that is the Resource Tadpole Bucket's job), and slimes are
  rejected.
- **Preserved:** the whole entity is serialized via `Entity.saveWithoutId`, so
  species/category, the bred Appetite/Bounty/Reach stats, persistence, health,
  and a custom name all survive the round trip. The stored UUID is dropped so a
  released frog gets a fresh identity (no duplicate-UUID risk if a loaded net is
  creative-copied).
- **Tooltip:** a loaded net shows the caught frog's stats and a release hint.

## Design

Modelled directly on Productive Bees' Bee Cage (the reusable `SturdyBeeCage`
variant). Like the cage:

- `FrogNetItem` is a plain `Item` (not a bucket - no water needed to release).
- The captured entity lives in the vanilla `minecraft:custom_data` component as a
  `CompoundTag` (`entity` type id + `name` + the `saveWithoutId` blob), so no
  custom data component is registered.
- Catch is `interactLivingEntity`; release is `useOn` (clicked-face-relative
  cell).
- The empty/loaded model swaps via a `productivefrogs:filled` item-model
  property override (registered in `PFClientEvents` on client setup), exactly like
  the bee cage's `filled` property.

This deliberately mirrors the bee cage rather than the Resource Tadpole Bucket:
the bucket reuses vanilla's `Bucketable`/`MobBucketItem` water mechanic, which is
right for an aquatic juvenile but wrong for relocating an adult frog on dry land.

## Config

`frog_net.enabled` (default `true`). When off, the net is uncraftable, hidden
from JEI + the creative tab, and inert if obtained another way (it neither
catches nor releases). Recipe-gated via the shared `ConfigEnabledCondition`
machinery (`Key.FROG_NET`), like the appliances; toggling the recipe needs a
world reload. Part of the #196 config-coverage line.

## Recipe

A string-and-stick net:

```
S S S
S   S
  I
```

`S` = `minecraft:string`, `I` = `minecraft:stick`.

## Assets

`scripts/generate_frog_net_textures.py` bakes the two 16x16 item textures
(`frog_net.png` empty, `frog_net_filled.png` with a frog in the hoop). Procedural
Pillow art, run manually when the look changes - not build-validated.

## Tests

- **JUnit** `FrogNetRecipeTest` - recipe shape + the `frog_net` config gate.
- **JUnit** `ConfigEnabledConditionTest` - the `frog_net` condition key
  serializes + codec-round-trips.
- **GameTest** `frogNetPreservesSpeciesAndStats` - catch a stat-stamped frog,
  rebuild it from the net, assert species + Appetite/Bounty/Reach survive.

GameTest is blind to the model swap / textures / item name; verify those with a
manual `runClient` pass.

## Related

- Productive Bees comparison: [productive_bees_analysis.md](./productive_bees_analysis.md)
- The juvenile-stage analogue: the Resource Tadpole Bucket
- Config-gating precedent: [spawnery.md](./spawnery.md), [slime_milk_catalysts.md](./slime_milk_catalysts.md)
