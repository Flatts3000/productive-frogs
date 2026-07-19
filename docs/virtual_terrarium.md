# Virtual Terrarium (spec + acceptance criteria)

> **Status: SPEC / not built.** A design contract for a **two-block Virtual Terrarium**
> that virtualizes one frog's eat loop with no spawned entities. The physical multiblock
> Terrarium (`docs/terrarium.md`) is untouched; this is an additive sibling.

## Why

Players expected the Terrarium to be a *virtual* block - the way Productive Bees processes
bees internally with upgrades, instead of a sealed box full of live entities. So: **keep the
multiblock**, and add a compact, expensive, sleek unit that runs the same loop headlessly -
one frog, fed its feedstock, dropping product, in something you tuck into a wall and pipe.

## Locked decisions (maintainer, 2026-07-18)

1. **Two blocks.** A **Processor** (bottom - the machine: GUI, tanks, upgrades, logic) and a
   **Display Dome** (top - a glass terrarium dome that renders the frog, the slime, and
   animations). Both crafted independently; **both required**; they **form** when the Dome
   sits directly on the Processor and **unform** if either is broken. This is a trivial
   two-block adjacency, not a validated multiblock like the physical Terrarium.
2. **No power for the core loop; only the Overclock draws RF.** The frog-eating virtualization
   runs passively - no power. Smelter and Melter also run for free; **only the Overclock**
   (a flat +50% speed) draws RF (maintainer, 2026-07-18). The Processor gains a receive-only
   energy buffer (`Capabilities.Energy.BLOCK`, like the EE lane's Alembic/Distiller), used
   *only* while an Overclock is installed. No Overclock -> no power needed at all.
3. **One frog at the normal per-frog rate.** An un-upgraded frog on plain feedstock matches a
   single physical-Terrarium frog; a maxed physical Terrarium (up to 8 frogs) still out-produces
   one Virtual Terrarium, so both stay worth building. Upgrades and catalyst-buffed feedstock
   compound from there.
4. **A universal frog virtualizer** (one frog per unit), for the three *eating* kinds:
   - **Resource** frog + **Slime Milk** -> Froglights.
   - **Midas** frog + **Mimic Milk** -> Prismatic Froglights (gated by `equivalenceEnabled`).
   - **Predator** frog + **Mob Slurry** -> the mob's player-credited loot + Liquid Experience.
   - **Apex frogs stay out** - they run boss altars, not an eat loop.
5. **Void tier.** Recipe gated at the top species (Void / End-tier). Very expensive, late-game.
   Exact recipe is a balance-pass TODO.
6. **Dedicated upgrade items** (Productive Bees style), installed in a vertical column of slots,
   not consumed per cycle. See Upgrades.
7. **Strong ergonomics:** two block textures (idle vs active), a GUI duration bar, the Dome
   showing the live frog + slime + animations, and Jade for the details.

## Structure

- **Processor block** (bottom): the machine. Holds the frog slot, the feedstock tank, the
  upgrade column, the outputs, and the eat-emulation `serverTick`. Void-tier textures, with a
  distinct **inactive vs active** look.
- **Display Dome** (top): a glass terrarium dome - the block uses the **vanilla glass texture**
  (`minecraft:block/glass`, `cutout`) directly, no bespoke PF texture. A `BlockEntityRenderer` draws the **loaded
  frog** (kind-tinted, the altar display-frog approach), a **slime** tinted to the feedstock's
  variant, and idle/eat **animations**, with terrarium ambiance (plants, water shimmer). It is
  cosmetic but **load-bearing**: the Processor only runs when a Dome is directly above it.
- **Formation:** place the Processor, place the Dome on top -> formed. Breaking either unforms
  it (production stops; contents stay in the Processor). No structure scan - a single
  `getBlockState(pos.above())` check on the Processor, revalidated on neighbor change.

## How it works

### Inputs

- **One frog, from a Frog Net.** Insert a filled `FrogNetItem`; the Processor reads the stored
  frog's `FrogKind` + stats straight off the net stack's `DataComponents.CUSTOM_DATA`
  (`FrogKind.readFromTag`; `Appetite/Bounty/Reach` keys) - **no entity is rebuilt or spawned.**
  Extractable; breaking the Processor returns the filled net (stats intact, the #210 rule).
  Only `Resource`, `Midas`, and `Predator` kinds are accepted; Apex is refused on insert.
- **A feedstock tank.** Fill-only, `Capabilities.Fluid.BLOCK`, one variant/kind at a time
  (refuses a second until it drains, like the Terrarium Controller). The fluid must match the
  frog: **Slime Milk** (`SLIME_VARIANT`) for Resource, **Mimic Milk** (`SYNTHESIZED_ITEM`) for
  Midas, **Mob Slurry** (`SLURRIED_ENTITY`) for Predator.
- **Upgrade column** - see Upgrades.

### The virtual eat (emulated - there is no live slime, mob, or frog)

No headless production path exists; the block emulates the eat per kind, reusing entity-free
seams. Each **eat cycle** (timed below), when productive (see Rules):

- **Resource:** produce `batchQuantity(teemingLevel) x bountyDropCount(effectiveBounty)`
  Froglights via `FrogTongueDropHandler.buildFroglight(variantId, null)`.
- **Midas:** same, via `MidasTongueDropHandler.buildPrismaticFroglight(itemId)` (gated by
  `equivalenceEnabled`).
- **Predator:** roll the slurried mob's loot the **boss-altar way** -
  `BossAltarHatchBlockEntity.rollLoot`-style: a transient `EntityType.create()` **phantom**
  (never added to the world, then discarded) as `THIS_ENTITY`, `getDefaultLootTable()` rolled in
  `LootContextParamSets.ENTITY` with `DAMAGE_SOURCE = playerAttack(fakePlayer)` +
  `LAST_DAMAGE_PLAYER = fakePlayer`, the fake player holding a **Looting-N sword** where
  `N = FrogStats.bountyLootingLevel(effectiveBounty, statCap)`. Plus **Liquid Experience** for
  the mob's XP (see the caveat below).

Then apply the installed processing upgrade (Smelter/Melter) and route to the output. Spend one
**spawn** of the feedstock's budget per cycle (the liquid itself is untouched until the budget runs out).

### Yield and timing (rate = spawn rate + catalysts + upgrades)

The rate is built from the **feedstock's own spawn rate** (the base) **plus its catalysts plus
the installed upgrades** - catalysts and upgrades are **both** in the calc, not one or the other -
with the frog's stats layered in. All of these combine into the formula:

- **Cycle time** = a base `MilkSpawnEconomy.intervalTicks(rapidLevel, ...)` (the feedstock's
  spawn cadence, 200-600 ticks, shortened by the **Rapid** catalyst), then further shortened by
  the frog's **Appetite stat**, then a **flat -15% per Appetite upgrade** (decoupled from the
  stat, capped at 3), and, when powered, the **Overclock** (+50% speed, stacking to a cap).
- **Count** = `batchQuantity(teemingLevel) x bountyDropCount(effectiveBounty)`, where
  `teemingLevel` = the feedstock's **Teeming** catalyst and `effectiveBounty` = frog Bounty
  ; each **Bounty upgrade** then adds a **flat +1 output** (NOT the frog-stat band mechanic),
  capped at 3, so a base-1 frog with 3 Bounty upgrades makes 4. For predators the frog's Bounty
  sets base Looting (`bountyLootingLevel`) and each Bounty upgrade adds +1 Looting.
- **Budget is spawns, not liquid.** Each eat spends **one spawn** of the loaded milk's budget
  (exactly like a placed Slime Milk source - nothing in the pack consumes milk any sooner). The
  1000mB **liquid never drains per eat**; the tank only empties once the spawn budget hits zero.
  A **Count/Bountiful** catalyst raises that budget (more eats per bucket); the **Endless/Infinite**
  catalyst makes it never deplete. The GUI fluid slot fills by spawns-remaining; the tooltip +
  Jade show "spawns left: N/cap". These are read straight off the feedstock's `MilkCharge` -
  the feedstock's own catalysts govern longevity, exactly like a placed source. There is **no
  Capacity or Everflow upgrade** (the catalysts already in the milk do that job).

Mob Slurry reuses the **same milk catalysts** (the "slurry catalyst" = `MilkCatalyst`
COUNT/SPEED/QUANTITY/INFINITE, applied exactly as `AbstractBasinBlockEntity` already does), so
there is no separate slurry-catalyst item.

### Rules it follows

- **Match gate.** Resource: the milk's category must equal the frog's `getCategory()`. Midas:
  the feedstock must be Mimic Milk. Predator: the slurried mob must be valid prey for the loaded
  predator (`PredatorPrey.predatorFor(registry, type) == frog.getKind()`). Mismatch -> idle.
- **Bounty** scales count (and predator Looting); **Appetite** scales cycle time; **Reach does
  nothing** in a single block (no Reach upgrade).
- **Feedstock depletes** unless Endless.
- **Backpressure:** a full output pauses production; nothing voided.
- **Midas** gated by `equivalenceEnabled`; **Predator** gated by `predatorsEnabled` - a loaded
  frog of a disabled kind idles.
- **Brewed Froglights are NOT produced** (the effect is captured from a live slime's active
  effects; the virtual loop has none). Documented limitation.
- **Predator loot is loot-table only** (see caveat) - it does not reproduce hardcoded
  `dropCustomDeathLoot` drops the open predator eat gets from the real death pipeline.

### Upgrades

A **vertical column** of slots accepts a **new family of dedicated upgrade items** (Productive
Bees style), installed persistently, returned on break. Upgrades are **single-tier** (no I/II/III
crafts) - install several of a lever across the slots to stack its effect, up to a cap. Three
groups:

**Stat upgrades** stack on the frog's own stats in the formula (a good frog + good upgrades
compound; each type stacks up to a cap):

| Upgrade | Effect |
|---------|--------|
| **Bounty** | adds to the frog's Bounty -> more product per cycle (and higher predator Looting) |
| **Appetite** | adds to the frog's Appetite -> shorter cycle (the "speed" lever) |

**Processing upgrades** transform the item output, and are **mutually exclusive** (the block
refuses both):

| Upgrade | Effect |
|---------|--------|
| **Smelter** | auto-smelts each Froglight/drop the instant it is made - the output holds the **smelted result** (iron Froglight -> iron ingot), via the item's vanilla smelting recipe |
| **Melter** | auto-melts each Froglight (Crucible logic) into its **molten fluid**; the item output routes to a **molten tank** (see Output) |

Items with no smelting/melting result **pass through unprocessed**. Smelter and Melter draw
**no RF** - they run for free (the only upgrade that needs power is the Overclock, below).

**Feedstock economy is not an upgrade** - it rides the feedstock's own catalysts (the milk
model): a **Count/Bountiful** catalyst raises the milk's budget so a tankful lasts longer, and an
**Endless/Infinite** catalyst stops depletion. Feed catalyzed milk to change longevity; the block
has no Capacity or Everflow upgrade.

**Overclock upgrade (RF-powered):** while the energy buffer has power, the **whole cycle runs
50% faster** - a flat block-wide boost stacking on top of the frog's Appetite and any Rapid
catalyst. It is the **only** upgrade that draws RF - and the only reason the Processor carries an
RF buffer. Unpowered with an Overclock installed, the block **hard-stalls at zero progress** (Jade
+ the GUI say "needs power"); pull the Overclock or supply RF to run. Multiple Overclocks **stack
additively up to a cap** (each +50% while powered, to a ceiling set at the balance pass).

The upgrade item family (names, textures, recipes, tiers) is an open sub-decision.

### Output (adaptive)

The Processor routes each product to the right sink:

- **Item output** - Froglights / Prismatic Froglights / mob loot, or their **smelted** form with
  a Smelter. Extractable on the **DOWN** face (`Capabilities.Item.BLOCK`).
- **Molten tank** - present when a **Melter** is installed; the Froglights' molten metal.
  Extractable as fluid on DOWN (`Capabilities.Fluid.BLOCK`).
- **Liquid Experience tank** - present whenever a **Predator** runs; the mob's XP as fluid
  (`LiquidExperienceFluid`, 20 mB/point). Extractable as fluid.

So the block has one feedstock in-tank plus, by role, an item output and up to two product tanks.
Backpressure applies to whichever sink is in use. The GUI shows a **duration bar** (fills over
the cycle, resets on yield), the frog slot, the feedstock gauge, the upgrade column, and the
active output form (item grid / molten gauge / XP gauge).

### Visual feedback

- **Two Processor textures - inactive and active (required).** A clearly different look idle vs
  producing, readable at a glance. Sleek, **Void-tier materials** (End / void-themed - dark,
  glassy, purple).
- **The Dome renders the live loop (required):** a `BlockEntityRenderer` on the Dome draws the
  loaded frog (kind-tinted), a slime tinted to the feedstock variant, idle/eat animations, and
  terrarium ambiance. Empty -> just the dome.
- **Jade (required):** frog kind + stats, feedstock kind/variant + remaining, output fullness,
  and the produce/idle reason (no dome / no frog / no feedstock / mismatch / output full).

## Reuse map (build on these, do not reinvent)

| Need | Reuse |
|------|-------|
| Froglight from a variant | `FrogTongueDropHandler.buildFroglight(Identifier, StoredEffect)` |
| Prismatic Froglight | `MidasTongueDropHandler.buildPrismaticFroglight(Identifier)` |
| **Entity-free mob-loot roll** | `BossAltarHatchBlockEntity.rollLoot(server, pos, phantomType, tableKey, keep)` - throwaway `EntityType.create()` phantom + `LootContextParamSets.ENTITY`; resolve table via `EntityType.getDefaultLootTable()` (Optional) |
| Looting from Bounty | `FrogStats.bountyLootingLevel(bounty, cap)` (0..III) |
| Drop count from Bounty | `FrogStats.bountyDropCount(bounty, maxDrops, cap)` |
| Cycle time from Appetite | `FrogStats.appetiteCooldownTicks(appetite, ...)` |
| Interval/batch + catalyst math | `MilkSpawnEconomy.intervalTicks` / `batchQuantity` |
| Feedstock stats (variant/mob, catalysts, budget) | `MilkCharge.fromBucket/fromFluid`; `MobSlurryBucketItem.entityOf` / `SLURRIED_ENTITY` |
| Prey eligibility | `PredatorPrey.predatorFor(registry, EntityType)` |
| XP -> Liquid Experience | `LiquidExperienceFluid.pointsToMb(points)` (20 mB/point) |
| Frog kind/stats off a net stack | `EntityNetItem.isFilled`, `CUSTOM_DATA`, `FrogKind.readFromTag` |
| Slurry catalysts = milk catalysts | `MilkCatalyst` + `AbstractBasinBlockEntity.applyCatalyst` |
| Block + capability wiring | Slime Milker (`SlimeMilkerBlock`/BE/Inventory/Menu/Screen); `PFModBusEvents.registerCapabilities`; `content/transfer/` adapters; the Terrarium Controller's fill-only tank + `SnapshotJournal` |
| RF buffer for the Overclock | `ReceiveOnlyEnergyHandler` + `Capabilities.Energy.BLOCK` (EE lane Alembic/Distiller) |
| Mob's real XP | `Entity.getExperienceReward` (per-entity) off the loot phantom, then `LiquidExperienceFluid.pointsToMb` |
| Display-frog rendering | the altar display frogs (Dragonsbane/... BER) |

### Predator-path caveats (documented, not bugs)

- **Loot-table only (ACCEPTED).** `rollLoot` rolls the mob's loot table; it **misses code-side
  `dropCustomDeathLoot`** drops (some mobs add drops in code, not the table). The open predator
  eat gets these free via the real death pipeline; the virtual kill will not. Maintainer ruling:
  accept it, document the gap in the guide.
- **XP is the mob's real reward (DECIDED).** Not a fixed value - derive each mob's actual XP
  (vanilla exposes it per-entity via `getExperienceReward`; read it off the phantom or an
  equivalent per-type lookup) and pay it as Liquid Experience via `LiquidExperienceFluid.pointsToMb`.
- **Phantom sees default state.** Loot conditions reading live entity flags (on fire, in water)
  see a freshly-created phantom, so a few state-keyed drops may differ from a real kill.
- **No default table** -> that mob produces no loot (handle the empty `Optional`).

## Acceptance criteria

1. **Forms from two blocks.** A Processor with a Dome directly above forms the Virtual Terrarium;
   without the Dome (or after it breaks) the Processor does not run and Jade says "no dome". Both
   blocks are separately craftable.
2. **Load a frog.** Inserting a filled Frog Net holding a Resource/Midas/Predator frog loads it;
   an Apex net is refused with no loss.
3. **Resource path.** Resource frog + matching-category Slime Milk -> variant Froglights in the
   item output, no power, on the cycle cadence.
4. **Midas path.** Midas frog + Mimic Milk -> Prismatic Froglights when `equivalenceEnabled`; off
   -> idle.
5. **Predator path.** Predator frog + Mob Slurry of a valid prey mob -> that mob's loot in the
   item output (Looting scaled by Bounty) **plus** Liquid Experience in the XP tank. Invalid prey
   for that predator -> idle.
6. **Match gate.** Category mismatch (Cave frog + Bog milk), wrong feedstock for the kind, or a
   mob the predator can't eat -> no production, Jade names the reason.
7. **Bounty scales.** Product count follows `bountyDropCount`; predator Looting follows
   `bountyLootingLevel` (0..III).
8. **Appetite scales.** Higher Appetite -> shorter cycle.
9. **Upgrades apply.** Bounty/Appetite upgrades stack on the frog's stats; removing one reverts
   it; all returned on break. (Feedstock budget/depletion is not an upgrade - it comes from the
   milk's own Count/Endless catalysts, step in "Rates".)
10. **Smelter.** With a Smelter, the item output holds smelted results, never raw Froglights;
    unsmeltable products pass through.
11. **Melter.** With a Melter, molten output routes to a fluid tank (metal -> molten metal,
    Water/Lava -> water/lava); unmeltable products pass through as items. Smelter + Melter cannot
    both be installed.
12. **Catalysts factor.** Rapid/Teeming/Bountiful/Endless on the fed milk *or slurry* speed up /
    add count / extend budget / stop depletion, stacking with the stat upgrades.
13. **No power for the core loop.** The base frog-eating loop needs no power. Only the
    the Overclock upgrade draws RF (receive-only; Smelter/Melter are free); with one installed and the buffer
    empty, the block stalls until powered.
14. **Automation I/O.** DOWN outputs items (and the fluid tanks); pipes fill the feedstock tank
    and drain the product tanks; other faces don't output items.
15. **Ergonomics.** Distinct inactive vs active Processor texture; the Dome renders the loaded
    frog + a feedstock-tinted slime + animations; the GUI duration bar tracks the cycle; Jade
    shows contents + reason.
16. **Break returns items, NOT fluids.** Breaking the Processor returns the frog (filled net,
    stats intact) and the upgrades; the Dome drops as itself. Tank fluids (feedstock / XP / molten)
    are **not** dropped as buckets - that would mint free buckets (bucket econ). Drain the feedstock
    back into a bucket first (empty bucket -> the tank) if you want to keep it.
17. **Throughput parity.** One un-upgraded unit on plain feedstock is within tolerance of one
    physical-Terrarium frog, and below a maxed multiblock's total.
18. **GameTest lock.** Registry GameTests assert: forms only with the Dome; each path's match ->
    correct product; mismatch -> nothing; Bounty count + Looting; Smelter/Melter output form +
    mutual exclusion; Endless non-depletion; predator loot-table roll + Liquid Experience payout;
    output-full backpressure. (Client visuals - textures, Dome render, Jade - verified by a manual
    `runClient` pass; GameTest is render-blind.)

## Registration / wiring checklist (for the build PR)

- **Processor:** `content/block/VirtualTerrariumProcessorBlock` (inactive/active state, formed
  state, wires the BE ticker) + `.../entity/VirtualTerrariumBlockEntity` (`MenuProvider`; frog
  slot + feedstock tank + upgrade column + item output + molten/XP tanks; `static serverTick`
  eat-emulation + Dome-above check) + `VirtualTerrariumInventory` + `content/menu/...Menu` +
  `client/screen/...Screen` (with the duration bar + adaptive output).
- **Display Dome:** `content/block/VirtualTerrariumDomeBlock` (+ a light BE if the renderer needs
  per-instance state) + the `BlockEntityRenderer` (loaded frog, feedstock-tinted slime,
  animations; reuse the altar display-frog render).
- **Shared helper:** extract/reuse `rollLoot` as an entity-free `(EntityType, tableKey, looting,
  fakePlayer) -> drops` helper; plus a mob-XP -> points helper for the Liquid Experience payout.
- `PFBlocks` / `PFItems` (two BlockItems + the upgrade items) / `PFBlockEntities` / `PFMenuTypes`
  / `PFCreativeTabs`; capabilities in `PFModBusEvents` (Fluid.BLOCK feedstock fill-only + product
  tanks; Item.BLOCK DOWN output; **Energy.BLOCK receive-only** for the powered upgrades, advertised
  only when a Smelter/Melter/Overclock is installed).
- The **upgrade item family** (Bounty / Appetite / Smelter / Melter / Overclock) - items,
  models, textures, recipes, and a `virtual_terrarium_upgrade` item tag for the slot filter.
  (Feedstock economy is not an upgrade - it rides the milk's Count/Endless catalysts.)
- Blockstates + models + textures (gen/ pipeline; the void-tier inactive/active Processor + the
  glass Dome); loot tables (Processor returns frog + upgrades - NOT tank fluids, bucket econ; Dome
  returns itself); `mineable/pickaxe`; lang (names + tooltips + Jade + idle reasons).
- Void-tier crafting recipes (balance pass). No config flag - the feature is always available.
- Jade provider (server-data, single-interface split per the Jade 26.1 rule).
- Guide entry (Modonomicon), under the Terrarium or Appliances chapter.
- GameTests per AC 18.

## Decisions to finalize (before build)

1. **Upgrade item family** - names, textures, recipes. The levers are set (Bounty, Appetite,
   Smelter, Melter, Overclock); only the item design is open. (Feedstock economy is catalyst-
   driven, not an upgrade.)
2. **Slot counts** - upgrade slots (lead 4-6), item-output slots (lead 9).
3. **Void-tier recipes** - the ingredient lists for both blocks + the upgrade items.
4. **Balance figures** - RF/tick for each powered upgrade, the Overclock stacking cap, and the
   base rate numbers.

*Settled:* Resource + Midas + Predator (Apex out); predator XP = the mob's real reward;
loot-table-only gap accepted; Smelter/Melter + Overclock draw RF (empty buffer = hard stall);
base cadence = feedstock spawn interval; Overclock stacks to a cap; upgrades single-tier; no
config flag (always available).
