# Frog Stat Breeding

> **Status: IMPLEMENTED.** This is the design of record for the frog stat-upgrade
> system. Captured 2026-05-26; built 2026-05-27 on `feat/frog-stat-breeding`.
> Decisions are recorded in the Decisions section. The two former open questions
> are resolved (see Open questions, below). Cosmetic tiers are the one deferred
> piece - the stats already sync client-side, so a later render-layer PR can read
> them with no data change.
>
> **Config master (#202):** the entire layer can be turned off with `frog_stats.enabled = false`. When off, behavior reads `effectiveAppetite()`/`effectiveBounty()`/`effectiveReach()` on `ResourceFrog` - the stored stat when on, a fixed baseline (stat 1) when off - so every frog acts identically with no variance; `isFood` stops accepting Sweetslime (no breeding), the Sweetslime recipe is gated off (`config_enabled: frog_stats`) and it is hidden from JEI + the creative tab, conception stamps baseline, and the Jade stat lines (all three providers route through `appendStatLines`) are suppressed. The stored stats are **frozen, not deleted** - the getters and NBT keep them - so flipping the flag back on restores each frog's bred behavior. See [modpack_integration.md](./modpack_integration.md).

## Summary

Resource Frogs gain three persistent stats - **Appetite**, **Bounty**, and
**Reach** - each on a 1-10 scale. A frog's stats govern how productive it is in the
core loop (how fast it eats slimes, how many Froglights it drops, how far it spots
prey). You improve a frog by **breeding** it: feed two same-species frogs a
**Sweetslime** treat, run the stock vanilla frog breeding process, and the offspring
inherits a blend of its parents' stats with a chance to come out better than either.
Keep the winners, cull the duds, re-breed, and ladder a species' line toward a maxed
10/10/10 frog. The grind to max a frog is the long-term goal, in the spirit of
Productive Bees' gene-maxing.

This is a frog-only system: slimes and the production appliances are unchanged.

## Stats

Three stats, each an integer in `[1, 10]`. `10/10/10` is the cap ("maxed").

| Stat | Player-facing meaning | Behavioural effect | Hook |
|---|---|---|---|
| **Appetite** | How hungry/fast the frog is | Shortens the eat cooldown (more slimes eaten per minute) | frog eat/tongue cadence |
| **Bounty** | How much it yields | Froglights dropped per slime eaten | `FrogTongueDropHandler` |
| **Reach** | How far it hunts | Targeting scan radius (one frog services a larger pen) | `ResourceFrogAttackablesSensor` |

Illustrative default curves (all config-tunable; linear interpolation between the
stat-1 and stat-10 anchors unless noted):

| Stat | At 1 | At 10 |
|---|---|---|
| Appetite | baseline eat cooldown (`APPETITE_COOLDOWN_MAX`, e.g. 100 ticks) | fast (`APPETITE_COOLDOWN_MIN`, e.g. 30 ticks) |
| Bounty | 1 Froglight per slime | 3 Froglights per slime (equal-band step curve: at the defaults, 1 at 1-4, 2 at 5-8, 3 at 9-10) |
| Reach | baseline scan radius (`REACH_RADIUS_MIN`, e.g. 8) | extended (`REACH_RADIUS_MAX`, e.g. 16) |

The variant-aware drop (a `ConfigurableFroglightItem` stamped with the
`SLIME_VARIANT`) and the category fallback drop both scale by Bounty; the stat
multiplies the existing drop, it does not change which item drops.

## Stat storage, sync, and acquisition

- **Storage:** three `SynchedEntityData` int accessors on `ResourceFrog`
  (`DATA_APPETITE`, `DATA_BOUNTY`, `DATA_REACH`), persisted in NBT
  (`addAdditionalSaveData` / `readAdditionalSaveData`) and synced to clients for the
  Jade tooltip and the cosmetic render tier. Values are clamped to `[1, 10]` on read
  and write (a tampered/migrated save cannot inject out-of-range stats).
- **Baseline stats (non-bred frogs):** a Resource Frog that was not bred (matured
  from a Primed Frog Egg, the Spawnery, or any other first-acquisition path) starts
  at **baseline** - every stat at `STAT_MIN` (`1/1/1`). There is no random starter
  roll; **breeding is the only way to raise a stat.** (Earlier builds rolled a random
  starter band `[1, 3]`; that was removed - see docs/known_issues.md - so every
  crafted/Spawnery frog is a clean, predictable `1/1/1` and all variance comes from
  the breeding line.)

## Breeding mechanic

Breeding is **stock vanilla frog breeding** (love mode -> the pregnant frog lays
frogspawn in water -> a tadpole hatches -> the tadpole matures into a frog), with
exactly two additions:

1. **Same-species gate.** A frog only accepts a partner of its own species/category
   (a Cave frog breeds only with another Cave frog). This keeps each species' stat
   line clean and is the only deviation from vanilla, where any frog breeds any frog.
2. **Stat inheritance on the offspring** (below).

The breeding trigger is the **Sweetslime** item (not the vanilla slime ball - see D5).
Both parents must be fed a Sweetslime to enter love mode, exactly as vanilla frogs
consume one breeding item per parent.

### Inheritance and the climb

When breeding produces an offspring, each of the three stats is rolled
**independently** from the two parents. Let `hi = max(parentA, parentB)` and
`lo = min(parentA, parentB)`:

- with probability `IMPROVEMENT_CHANCE` (default 0.20): offspring = `min(10, hi + 1)`
- else with probability `REGRESSION_CHANCE` (default 0.30): offspring = `round((hi + lo) / 2)`
- else: offspring = `hi`

So most offspring match the better parent, ~1 in 5 improve a stat by one, and ~30%
regress toward the average. The player keeps improvers, culls regressors, and
re-breeds the best pair - the ladder to 10/10/10. When both parents are already equal
on a stat, regression is a no-op (average == hi), so a maxed-leaning pair only ever
holds or ticks up, which is the intended late-game grind (breeding two 9s for the
occasional 10). All three probabilities are config-tunable; raising
`IMPROVEMENT_CHANCE` makes the climb brisker.

**Carrying stats through the frogspawn intermediary** is the one non-trivial bit of
plumbing: vanilla breeding lays a frogspawn *block*, which spawns tadpoles on its own
schedule, and a plain frogspawn block holds no entity data. The implementation
captures the computed offspring stats at the moment breeding completes and stamps
them onto the resulting Resource Tadpole(s), which carry them through maturation into
the Resource Frog. See Implementation Outline.

## The Sweetslime item

- **Id:** `productivefrogs:sweetslime`. A frog-breeding treat.
- **Recipe:** shapeless - 1 `minecraft:slime_ball` + 1 `minecraft:sugar` ->
  **2** Sweetslime (yields two so a single craft feeds one breeding pair; both
  ingredients are cheap).
- **Behaviour:** the breeding food for Resource Frogs. Right-click an adult Resource
  Frog to feed it (vanilla animal-feeding interaction); two fed same-species frogs
  enter love mode. It is *only* a breeding trigger - it does not heal, tame, or buff.
- **Why dedicated, not the slime ball:** slime balls are ubiquitous in a slime-farming
  setup; if they triggered breeding, frogs would breed constantly and accidentally,
  wrecking a deliberate breeding program (D5). A dedicated item makes breeding an
  intentional act.

## Reading a frog's stats

Via the **Jade** look-at tooltip (the mod already ships `ProductiveFrogsJadePlugin`).
Looking at a Resource Frog shows its three stats (e.g. `Appetite 7/10`,
`Bounty 4/10`, `Reach 9/10`). No separate analyzer item - Jade is the readout. New
lang keys under `productivefrogs.jade.*`.

## Persistence (prize protection)

A bred-up frog is valuable and must not be lost between crosses, so **Resource Frogs
are persistent** (`setPersistenceRequired(true)` - they never despawn). They can still
die to damage; the V2 Frog Terrarium (separate feature) will later offer fully safe
housing, but this system does not depend on it.

## Cosmetic tiers

Visual payoff for the grind, keyed off the stat total (sum of the three, range 3-30):

| Total | Tier | Look |
|---|---|---|
| 3-14 | common | no overlay |
| 15-23 | improved | subtle accent (faint sheen) |
| 24-29 | elite | stronger accent (metallic trim) |
| 30 | maxed | distinct "prize" look (gold trim + sparkle) |

Rendered as an additive layer on `ResourceFrogRenderer` reading the synced stat total.
The maxed look is the headline reward; the intermediate tiers are nice-to-have and can
ship in a follow-up if art lags. Exact visuals are an art-pass decision.

## Config (`PFConfig`, COMMON)

| Key | Default | Meaning |
|---|---|---|
| `breeding.sameSpeciesOnly` | true | enforce the same-species gate |
| `breeding.improvementChance` | 0.20 | per-stat chance offspring rolls `hi + 1` |
| `breeding.regressionChance` | 0.30 | per-stat chance offspring rolls the average |
| `breeding.statCap` | 10 | maximum per-stat value |
| `stats.appetiteCooldownMin` / `Max` | 30 / 100 | eat cooldown (ticks) at stat 10 / 1 |
| `stats.bountyMaxDrops` | 3 | Froglights per slime at stat 10 |
| `stats.reachRadiusMin` / `Max` | 8 / 16 | scan radius at stat 1 / 10 |
| `frogs.persistent` | true | Resource Frogs do not despawn |
| `lifecycle.primedFrogspawnHatchTicks` | 3600 | fixed hatch delay for primed frogspawn (deterministic) |
| `lifecycle.tadpoleGrowthTicks` | 24000 | tadpole -> frog maturation time (<= vanilla 24000; lower = faster) |
| `lifecycle.breedingCooldownTicks` | 6000 | deterministic post-breed re-breed cooldown |

> The `breeding.starterStatMin` / `starterStatMax` keys were **removed** - non-bred
> frogs now start at baseline `1/1/1` (see "Baseline stats" above), so there is no
> starter roll to configure.

## Decisions

- **D1 - Frogs only.** The frog is the persistent producer (the Productive Bees
  "bee" analog). Slimes are disposable inputs and the appliances are unchanged; the
  stat system lives entirely on the frog entity.
- **D2 - Three stats, no fourth.** Appetite / Bounty / Reach. A "Discovery" fourth
  axis was considered and dropped.
- **D3 - Mechanism is breeding, not consumables or an apparatus.** A consumable
  stat-bump tonic and an AgriCraft-style crop-stick apparatus were both considered and
  rejected in favour of plain vanilla breeding + stat inheritance.
- **D4 - Same-species, stats-only.** Breeding climbs a species' stat line. It never
  produces a new species or variant; new types come exclusively from priming. (Type
  discovery via breeding would fight the priming system.)
- **D5 - Dedicated breeding item (Sweetslime), not the slime ball** - to prevent
  accidental breeding from loose slime balls in a farming pen.
- **D6 - Sweetslime recipe:** shapeless slime ball + sugar, yields 2.
- **D7 - Inheritance:** hi-biased with an improvement chance and a regression-to-mean
  chance (the climb plus the culling), all config-tunable.
- **D8 - Non-bred frogs start at baseline** (`1/1/1`); breeding is the only way to
  climb. (Superseded the original "starter roll `[1, 3]`" - see docs/known_issues.md.)
- **D9 - Stat readout via Jade**, no new analyzer item.
- **D10 - Resource Frogs are persistent** so a bred line is not lost to despawn.
- **D11 - Cosmetic tiers** keyed off stat total; the maxed look is the headline reward.
- **D12 - No Terrarium dependency.** Breeding works in an open pen near water; the
  system ships standalone.

## Implementation outline

Pure additive feature; no existing data shapes change. Likely touch points:

- **`content/entity/ResourceFrog`** - add the three `SynchedEntityData` int
  accessors + NBT save/load + clamped getters/setters + a `statTotal()` helper. Apply
  baseline stats (`STAT_MIN` across the board) in `finalizeSpawn` when the frog was
  not bred.
- **Stat effects:**
  - *Appetite* -> the frog's eat cadence. Locate where the eat/tongue cooldown is
    governed (vanilla `Frog` brain `EAT`/`TONGUE` behaviours) and scale it by Appetite.
  - *Bounty* -> `FrogTongueDropHandler`: multiply the emitted Froglight count by the
    Bounty curve.
  - *Reach* -> `ResourceFrogAttackablesSensor`: scale the scan radius by Reach.
- **Breeding:**
  - Same-species gate: override the mate/partner check (`canMate` / the breed goal)
    to require a same-`Category` partner and Sweetslime as the breeding food
    (`isFood`).
  - Inheritance: at breeding completion compute the offspring stats from the two
    parents (the inheritance roll) and stamp them onto the resulting Resource
    Tadpole(s); carry through maturation to the Resource Frog. Resolve the
    frogspawn-block-holds-no-data gap here (capture-at-breeding -> stamp-on-tadpole).
- **`registry/PFItems`** - register `SWEETSLIME`; add the shapeless recipe JSON +
  5-prefix lang (item name) and the creative-tab entry.
- **`client/jade/ProductiveFrogsJadePlugin`** - add a Resource Frog component
  provider showing the three stats; new `config.jade.plugin_...` and tooltip lang keys
  (mind the Jade config-translation gotcha - a missing key crashes the client reload).
- **`client/renderer/ResourceFrogRenderer`** - additive cosmetic layer keyed off
  stat total.
- **`PFConfig`** - the config block above.
- **`setPersistenceRequired(true)`** for Resource Frogs.

## Testing

- **Unit (JUnit):** the inheritance roll with a seeded RNG (improvement / regression /
  hold math, the `min(10, hi+1)` cap, equal-parent no-op); stat clamping to `[1, 10]`;
  the Sweetslime recipe shape; the stat-to-effect curves (Appetite cooldown, Bounty
  drop count, Reach radius) at 1 and 10; config defaults.
- **GameTest (`PFGameTests`):** two same-species frogs + Sweetslime breed and produce
  an offspring whose stats fall in the inheritance range; cross-species frogs fed
  Sweetslime do **not** breed; a high-Bounty frog drops more Froglights than a
  low-Bounty one on a kill; a Resource Frog does not despawn.
- **runClient (manual; GameTest is blind to visuals):** Jade tooltip shows the three
  stats; the cosmetic tier renders on a high-total / maxed frog.

## Out of scope (future)

- **Breeding tree / deep genetics** (Forestry-style multi-trait inheritance, mutation
  to discover trait combinations) - the next depth layer on top of this; feeds the
  same three stats.
- **Frog Terrarium integration** (V2) - safe housing and automated breeding; optional,
  this system does not require it.
- **Slime or appliance stats** - explicitly not in scope (D1).

## Open questions

- ~~The exact vanilla hook for the frog eat cadence (which brain behaviour/cooldown to
  scale for Appetite).~~ **Resolved:** vanilla frogs have no eat cooldown, but
  `FrogAttackablesSensor` already refuses to surface prey while the
  `HAS_HUNTING_COOLDOWN` memory is present. That memory is absent from
  `Frog.MEMORY_TYPES`, so `ResourceFrog#brainProvider` registers it, and
  `ResourceFrog#startEatCooldown` sets it (expiry = `FrogStats.appetiteCooldownTicks`)
  after every eat. The expiry auto-decrements in `Brain#tick`; no countdown behaviour.
- ~~The frogspawn -> tadpole stat-carry plumbing.~~ **Resolved:** stats are computed at
  conception (`ResourceFrog#spawnChildFromBreeding`), stamped onto the
  `PrimedFrogEggBlockEntity` when the egg is laid (`LayCategoryFrogspawn`), read back at
  hatch onto each `ResourceTadpole`, and applied to the matured frog in
  `ResourceTadpole#ageUp` (after `finalizeSpawn`, overriding the baseline stats). The egg
  BE persists the stats so a chunk unload between lay and hatch doesn't lose them.
- Cosmetic tier visuals (the art pass) - the maxed "gold trim + sparkle" look in
  particular. **Still open / deferred** to a follow-up PR; the synced stat total is
  ready for a render layer to consume.

## Related

- Runway: [../ROADMAP.md](../ROADMAP.md)
- Scope split: [versioning.md](./versioning.md)
- Sibling appliance/feature specs: [spawnery.md](./spawnery.md), [froglight_crucible.md](./froglight_crucible.md)
- Entity / category architecture: `CLAUDE.md`
- Productive Bees comparison: [productive_bees_analysis.md](./productive_bees_analysis.md)
