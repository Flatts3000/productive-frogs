# Slime Churn (#187)

> **Status: SHIPPED in v1.15.0.** The Milker's
> inverse: a V1 appliance that turns Slime Milk back into captured slimes,
> in buckets, without ever spawning an entity.

## The pitch

The manual slime-harvest loop - place a milk source, wait for a spawn, catch
the slime with a bucket, re-bucket the source, move it - is busywork. The
**Slime Churn** is one block that does the whole loop in place: load a
per-variant Slime Milk bucket plus empty buckets, and it produces captured
variant **Slime Buckets** on the **same spawn rules as placed milk** - same
cadence, same per-bucket budget, same catalyst behavior. No entities, no
chasing, no density cap.

## Slots: two in, two out

| Slot | Accepts | Role |
|---|---|---|
| Milk input | a per-variant Slime Milk bucket | the variant + spawn budget + catalysts (read from the bucket's components) |
| Bucket input | empty `minecraft:bucket`s (up to 16) | capture containers, one consumed per slime |
| Slime output | - | the produced variant Slime Buckets |
| Container output | - | the spent empty milk container when a milk bucket depletes |

The spent container gets its **own** output so a hopper draining slime buckets
never picks up empties, and the returned empty can be looped straight back
into the bucket input for a self-feeding line. Hopper routing: bottom face =
extract-only over both outputs; every other face = insert-only over both
inputs, routed by per-slot validity.

## The economy: a placed source in a box

The Churn runs `MilkSpawnEconomy` - the **same math** the placed
`SlimeMilkSourceBlock` uses, extracted so the two can't drift:

- **Cadence**: each production event waits a randomized source interval
  (`milk.minSpawnIntervalTicks..maxSpawnIntervalTicks`), shortened by the
  **Speed** catalyst with the same floor.
- **Budget**: the milk bucket's own `SPAWNS_REMAINING` component decrements
  one per event - the bucket drains visibly in its tooltip, and a half-spent
  bucket can be pulled out and placed in-world to finish there (or vice
  versa). Components seed on first processing exactly like a placed source.
  **Count** raises the budget; **Infinite** (or depletion config-off) never
  depletes.
- **Quantity**: one event emits `1 + level` slime buckets. The budget is paid
  once per event (Quantity is additive throughput, never extra cost). Slime
  Buckets stack to 1, so a multi-bucket batch drains through the output slot
  one per tick (`pendingBatch`, persisted in NBT - a paid-for batch is never
  voided).
- **Pause-without-waste, furnace stall semantics**: no empty buckets, output
  occupied, or container-output full all stall the churn with the budget
  untouched - and the **progress arrow does not advance while blocked**
  (vanilla-furnace full-output behavior, same as the Milker). Progress is
  held, never reset; it resumes where it paused once the blockage clears.

**Yield equivalence is the design contract:** a catalyzed milk bucket spent in
the Churn yields exactly as many slimes as hand-placing it - they just arrive
pre-bucketed. (One niche difference, by construction: the in-world density cap
never applies because no entities exist.)

## Output fidelity

Produced buckets are minted by `SlimeBucketItem.forVariant(category, variant)`,
which writes the same `Category` + `Variant` NBT shape a real capture
(`ResourceSlime.saveToBucketTag`) stamps. Release runs the normal
`loadFromBucketTag` path - size forced to 1, marked bucket-originated - so a
churned bucket and a hand-captured bucket are interchangeable everywhere
(including as Milker input, closing the Milk -> Slime -> Milk round trip).

## Recipe and identity (Bog)

Crafted as a **plank barrel over a mud base** (maintainer-specified pattern;
planks are the `#minecraft:planks` tag - recipes never hardcode a wood
species):

```
planks      moss block  planks
planks      slime ball  planks
packed mud  packed mud  packed mud
```

The texture follows the crafting materials (the rule, per the Milker's
wood-over-cobble following its planks-over-cobblestone recipe): an oak barrel
churn with iron-banded planks, moss tufts, a slime-green churn window, and a
packed-mud base. Sound/feel: wood.

## Implementation map

- `content/block/SlimeChurnBlock` - FACING + WORKING, GUI open, drops on break.
- `content/block/entity/SlimeChurnBlockEntity` - the economy loop
  (`serverTick`), pendingBatch, component seeding/decrement, retire-on-deplete.
- `content/block/entity/SlimeChurnInventory` - 4 slots, validity-routed
  `inputView()`/`outputView()` (the Spawnery's MultiSlotView shape).
- `content/menu/SlimeChurnMenu` + `client/screen/SlimeChurnScreen` (extends
  `PFContainerScreen`); GUI background composed by
  `scripts/generate_slime_churn_gui.ps1` (furnace-derived, 2x2 wells).
- `content/block/MilkSpawnEconomy` - the shared interval/batch math, also
  called by `SlimeMilkSourceBlock` (extracted in this PR).
- `SlimeBucketItem.forVariant` - entity-less variant bucket minting.
- `SlimeMilkBucketItem.variantId()` - the reverse lookup accessor.
- Debug area: `-Dproductivefrogs.debug=churn` / `/pf debug churn on`.
- `client/jei/SlimeChurnRecipeCategory` - one JEI recipe per variant (the
  Milker category inverted: milk bucket + empty in, slime bucket + returned
  container out), plus the item info page.

## GameTests (PFGameTests, 7)

`slimeChurnProducesVariantSlimeBucketFromMilk`,
`slimeChurnDepletionReturnsEmptyContainerToSecondOutput`,
`slimeChurnPausesWithoutEmptyBuckets`, `slimeChurnPausesWhenOutputFull`,
`slimeChurnInfiniteMilkNeverDepletes`, `slimeChurnQuantityBatchPaysOneBudget`,
`slimeChurnSpeedCatalystShortensInterval` - all drive `serverTick` by hand
(the Milker test pattern); the speed test asserts the started interval sits
inside the Speed-modulated bounds rather than racing randomness.

## Deliberately out of scope (follow-ups if wanted)

- A Jade cook-progress provider (the Milker has one; the Churn's interval is
  randomized, so the readout needs a "next slime in ~Ns" framing).
- Any auto-pull of milk from an adjacent tank (fluid handling is the V2
  Terrarium's lane; the Churn is deliberately bucket-item-based).

## Decision log (2026-06-07, issue #187)

1. **Full source yield** (chosen over 1:1-converter and conjured-bucket
   variants): the Churn is an in-place source, not a converter - empty
   buckets in, the full depletion budget out, catalysts included.
2. **Two inputs / two outputs** - spent milk containers exit a separate slot
   from the slime buckets.
3. **Bog recipe**, and **textures follow the crafting materials** (mud/moss
   identity, not a generic wooden barrel).
4. **Name: Slime Churn** (over Curdler / Decanter / Condenser / Coagulator) -
   the dairy companion to the Slime Milker.
