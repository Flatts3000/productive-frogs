# Slime Milk catalysts

> **Status: SHIPPED v1.7.0 (2026-05-29).** The early-game stopgap toward
> lower-friction production, ahead of the V2 Frog Habitat that will close the
> loop properly. Still firmly V1: a hand-applied buff to a single block, no
> power / pipes / multiblocks.

## Why

The renewable loop has hand-only steps no V1 automation can bridge (bucketing a
live slime, placing a Slime Milk source, re-placing a depleted one). The Frog
Habitat (V2) is the real fix; catalysts are the cheap interim win: they make a
*single placed source* go further and faster, so a player tends it far less
often. See the V1/V2 framing in [versioning.md](./versioning.md) and the parked
Habitat in [../ROADMAP.md](../ROADMAP.md).

## The four catalysts

Each is a plain crafted item dropped into a placed Slime Milk source pool. Drop
it in (toss with Q, or pipe it in with a dropper) and the source consumes it and
applies the upgrade. All four buffs are stored on the source's BlockEntity and
ride along when the source is re-bucketed.

| Display name (id) | Effect | Bound | Craft |
|---|---|---|---|
| **Bountiful** (`count_catalyst`) | +`countPerCatalyst` (16) to remaining spawns | **uncapped** | 2 Sweetslime + bone meal (shapeless) |
| **Rapid** (`speed_catalyst`) | +1 speed level (shorter spawn interval) | `maxSpeedLevel` (4) | 2 Sweetslime + sugar (shapeless) |
| **Teeming** (`quantity_catalyst`) | +1 slime per spawn event | `maxQuantityLevel` (3) | 2 Sweetslime + glowstone dust (shapeless) |
| **Endless** (`infinite_catalyst`) | source never depletes | one-shot | 8 Bountiful ringing 1 diamond (shaped) |

Recipes are built on **Sweetslime** (the mod's breeding treat) + a vanilla
"flavor" item borrowing potion logic: **sugar** = swiftness (Rapid), **glowstone
dust** = amplifier (Teeming), **bone meal** = growth (Bountiful). The item ids
stay literal (`count/speed/quantity/infinite_catalyst`) for clear recipes/code;
the display names are Bountiful / Rapid / Teeming / Endless.

Count is uncapped by design; Infinite is the premium **final tier of the count
line**, built *from* Count catalysts (a deliberate progression, decided with the
user). Speed and Quantity have sane caps so a single source can't trivialise the
whole game.

- **Count** decrements once per spawn *event*; **Quantity** widens each event
  (`1 + quantityLevel` slimes). So Quantity multiplies yield without burning
  extra Count - they stack cleanly.
- **Speed** scales the base `[minSpawnIntervalTicks, maxSpawnIntervalTicks]`
  window down by `speedReductionPerLevel` (20%/level), clamped to
  `minIntervalFloorTicks` (20t) so stacking can't drive the cadence to zero.
- A catalyst that would no-op (a Speed/Quantity at its cap, or a second Infinite)
  is **left unconsumed** - the item floats for the player to grab back rather
  than being silently eaten.

## Where the state lives (the load-bearing change)

The depletion counter used to be a blockstate `IntegerProperty` capped at 16.
Uncapped Count needs an unbounded counter, which a finite-range blockstate
property can't hold, so the **whole spawn economy moved onto
`SlimeMilkSourceBlockEntity`**: `spawnsRemaining`, `speedLevel`, `quantityLevel`,
`infinite`. This also shrinks the block's state-combination count.

- **Persistence:** the four fields are saved in `saveAdditional`/`loadAdditional`
  and exposed as implicit components.
- **Bucket round-trip:** `SlimeMilkSourceBlock#pickupBlock` stamps all four onto
  the filled bucket (`SPAWNS_REMAINING` + new `MILK_SPEED` / `MILK_QUANTITY` /
  `MILK_INFINITE` components); `SlimeMilkBucketItem#checkExtraContent` restores
  them onto the re-placed source via `restoreUpgrades(...)`. A freshly-milked
  bucket carries none and places a default source. This is the same component
  round-trip the variant + spawns-remaining already used.
- **Seeding:** `setVariantId` seeds `spawnsRemaining` to the configured
  `depletionCount` the first time a real variant is attached, so a fresh source
  starts with a full budget without a carried component.

**Save-compat note:** existing worlds' sources carried `spawns_remaining` in their
blockstate, which NeoForge drops when the property is removed. Those in-progress
sources reset to a full budget once on load - player-favorable and harmless.

## Application hook

`SlimeMilkSourceBlock#entityInside` consumes catalysts. It is event-driven (fires
only while an entity overlaps the block), so idle sources cost nothing - far
cheaper than scanning every milk source each tick. Verified by GameTest that the
hook reliably fires for an item resting in the no-collision fluid
(`catalystDroppedInPoolIsConsumed`), so no per-tick AABB-scan fallback is needed.

Gates, in order: server-side only; catalysts globally enabled; entity is a
catalyst `ItemEntity`; this is an actual source (not spread milk) with a variant;
and Count/Infinite only apply when depletion is globally on (else they'd be
no-ops). The catalyst-type resolution lives in
`com.flatts.productivefrogs.content.item.MilkCatalyst` so neither the block nor
the items hard-code each other.

## Presentation

- **Discoverability:** drop-in is the only application method (so a dropper aimed
  into the pool auto-feeds catalysts), but each catalyst is a `MilkCatalystItem`
  carrying two hover tooltip lines - its effect, plus "Drop into a Slime Milk
  source to apply" - so the interaction is learnable without JEI.
- **Apply feedback:** a slime-plop sound + a small green particle burst when a
  catalyst is consumed.
- **In-world tell:** an **Endless** (infinite) source emits a faint slow-rising
  `END_ROD` glint (`SlimeMilkSourceBlock#animateTick`) so "never runs dry" reads
  at a glance; plain sources and the other upgrade tiers look unchanged. The
  infinite flag is synced to the client via the BE update tag for this.
- **Icons:** four tinted slime orbs with a white glyph each (green `+`, yellow
  chevrons, orange pips, purple figure-8), from
  `scripts/generate_catalyst_textures.py`.

## Config (`slime_milk_catalysts`)

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master toggle. Off = uncraftable + hidden from JEI (placed sources still work; applied upgrades still honoured). Recipe gate via the `config_enabled`/`milk_catalysts` condition. |
| `countPerCatalyst` | 16 | Spawns added per Count catalyst. |
| `maxSpeedLevel` | 4 | Speed cap. |
| `maxQuantityLevel` | 3 | Quantity cap. |
| `speedReductionPerLevel` | 0.20 | Interval reduction per Speed level. |
| `minIntervalFloorTicks` | 20 | Floor the Speed-reduced interval can't drop below. |

The recipe gate reuses the Spawnery's `ConfigEnabledCondition` machinery (new
`Key.MILK_CATALYSTS`); toggling requires a world reload to re-evaluate the recipe
condition, exactly like the Spawnery.

## Jade

Looking at a buffed source shows its Speed and Quantity levels (`lvl/max`)
alongside the existing spawns-left readout (or "unlimited" when infinite or
depletion is config-off). Read server-side from the BE.

## Tests

- **JUnit** `SlimeMilkSourceCatalystTest` - seed, apply, caps, idempotent
  infinite, decrement floor, restore clamping.
- **JUnit** `CatalystRecipeTest` - all four recipes config-gated; Infinite built
  from Count.
- **GameTest** - `catalystDroppedInPoolIsConsumed` (the `entityInside` de-risk),
  `catalystInfiniteSourceNeverDrains`, `catalystAtCapIsNotConsumed`,
  `catalystUpgradesSurviveBucketRoundTrip`, plus the existing milk-source
  depletion tests retargeted onto the BE.

## Related

- Why this is the interim and not the fix: [versioning.md](./versioning.md), [../ROADMAP.md](../ROADMAP.md)
- The source block + fluid: [farming.md](./farming.md), [architecture.md](./architecture.md)
- Config-gating precedent: [spawnery.md](./spawnery.md)
- Pack-facing flags: [modpack_integration.md](./modpack_integration.md)
