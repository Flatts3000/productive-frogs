# Virtual Terrarium (spec + acceptance criteria)

> **Status: SPEC / not built.** A design contract for a new single-block "Virtual
> Terrarium" that virtualizes one frog's slime-eating loop, with no spawned
> entities. The physical multiblock Terrarium (`docs/terrarium.md`) stays exactly
> as it is; this is an additive sibling, not a replacement.

## Why

Players expected the Terrarium to be a *virtual* single block - the way Productive
Bees lets a hive process bees internally with upgrades, instead of a big box full
of live entities. The physical Terrarium (build a sealed 7x6x7 shell, live frogs
and slimes ticking and rendering inside) is the opposite of that. So: **keep the
multiblock**, and add a compact, expensive single block that runs the same loop
headlessly. One frog, fed milk, dropping Froglights, in a block you can tuck into a
wall and pipe.

## Locked decisions (maintainer, 2026-07-18)

1. **A single block.** Not a multiblock. No structure to validate.
2. **No power.** It runs passively off the Slime Milk you feed it. (Considered RF,
   ruled out.)
3. **Runs one frog at the normal per-frog rate.** A maxed physical Terrarium (up to
   8 frogs) still out-produces a single Virtual Terrarium roughly 8:1, so both
   blocks stay worth building - the Virtual trades throughput for zero build cost,
   zero footprint, and no entity load.
4. **Resource frogs and Midas frogs.** The six species run Slime Milk -> Froglights;
   a Midas frog runs Mimic Milk -> Prismatic Froglights (gated by
   `equivalenceEnabled`). Predators (eat mobs) and Apexes (run altars) are **out** -
   they are not slime-eaters.
5. **Void tier.** The recipe is gated at the top species (Void / End-tier) - very
   expensive, a late-game convenience block. Exact recipe is a balance-pass TODO.
6. **It accepts dedicated upgrade items** (Productive Bees style) - a new family of
   upgrade items installed in upgrade slots, not consumed per batch. See Upgrades.
7. **It shows its contents from the outside** - Jade required, and the loaded frog
   renders in the block (see Visual feedback).
8. **Full GUI** - a screen with the frog slot, milk gauge, upgrade slots, and output
   grid (the Milker/Churn shape).
9. **The loaded frog renders in the block** - a block-entity renderer draws the actual
   frog, kind-tinted, plus a milk-color tint on the block.

## How it works

### Inputs

- **One frog, from a Frog Net.** Insert a filled Frog Net (`FrogNetItem`); the block
  reads the stored frog's `FrogKind` and stats straight off the net stack's
  `DataComponents.CUSTOM_DATA` (`FrogKind.readFromTag`, and `Appetite/Bounty/Reach`
  keys) - **no entity is rebuilt or spawned.** The frog is extractable; breaking the
  block returns the filled net. Only `Resource` and `Midas` kinds are accepted; any
  other kind is refused on insert.
- **An internal milk tank.** Fill-only, via `Capabilities.Fluid.BLOCK` (bucket by
  hand or pipe). Holds **one variant at a time** and refuses a second until it drains
  (mirrors `TerrariumControllerBlockEntity`'s single-variant FIFO). A Resource frog
  wants **Slime Milk** (`SLIME_VARIANT`); a Midas frog wants **Mimic Milk**
  (`SYNTHESIZED_ITEM`).
- **Upgrade slots** - see Upgrades.

### The virtual eat (emulated - there is no live slime or frog)

There is **no existing headless production path** - every froglight route needs a
live `ResourceFrog`. So the block emulates the eat, reusing the entity-free seams:

Each **eat event** (paced below), if the block is *productive* (see rules):
1. Resolve the milk's variant + category (`variantId -> PFRegistries.variant(...).category()`).
2. Produce `MilkSpawnEconomy.batchQuantity(quantityLevel)` slimes'-worth, and for each,
   `FrogStats.bountyDropCount(storedBounty, bountyMaxDrops, statCap)` Froglights - i.e.
   **froglights per event = batchQuantity x bountyDropCount**, exactly the physical
   economy's two multipliers (Teeming = more slimes, Bounty = more drops per slime).
3. Emit each via `FrogTongueDropHandler.buildFroglight(variantId, null)` (Resource) or
   `MidasTongueDropHandler.buildPrismaticFroglight(itemId)` (Midas) into the output.
4. Spend **one** unit of the milk's budget for the event (Quantity is free throughput,
   matching the source: `decrementSpawns()` fires once per event, not per slime).

**Pace** = the frog's eat cadence, `FrogStats.appetiteCooldownTicks(storedAppetite, ...)`,
shortened by the installed Rapid upgrade level (`MilkSpawnEconomy` speed reduction). This
is what makes it "one frog at the normal rate" - the binding constraint on a real frog is
how fast it eats.

### Rules it follows (the "it follows all the rules" requirement)

- **Category match.** A species frog only produces when the milk's category equals the
  frog's `getCategory()` (`FrogKind.fallbackCategory()`); a Midas frog only on Mimic Milk.
  Mismatch (e.g. Cave frog + Bog milk) -> idle, nothing produced.
- **Bounty** scales the Froglight count per the shipped curve.
- **Appetite** scales the cadence.
- **Reach is ignored** - there is no targeting radius in a single virtual block. (Not
  repurposed; a frog's Reach simply doesn't matter here. Noted so it isn't a surprise.)
- **Milk depletes** (`spawnsRemaining`) unless the milk or an installed upgrade is Endless.
- **Backpressure**: a full output inventory pauses production; nothing is voided
  (mirrors the Hatch-full stop).
- **Brewed Froglights are NOT produced.** The brewed effect is captured from a live
  slime's active potion effects; the virtual loop has no slime to carry one, so a Virtual
  Terrarium never makes Brewed Froglights. Documented limitation, not a bug. (A player who
  wants Brewed Froglights uses the physical loop.)
- **Midas** production is gated by `equivalenceEnabled`; off -> a loaded Midas frog idles.

### Upgrades

Upgrade slots (proposed **4**) accept a **new family of dedicated upgrade items** built
for the Virtual Terrarium (Productive Bees style) - installed persistently, not consumed
per batch, and returned when the block is broken or the upgrade pulled out. Each drives one
lever of the eat loop through `MilkSpawnEconomy`/`FrogStats`; installing several of the same
type stacks up to a cap:

| Upgrade | Effect | Cap |
|---------|--------|-----|
| Speed | shortens the eat cadence | speed level cap (default 4) |
| Yield | more Froglights per event | quantity cap (default 3) |
| Capacity | raises the milk-budget the tank holds | (new lever) |
| Everflow | the tank never depletes | the Endless/infinite behavior |

These are **distinct from the milk catalysts** (which tune placed milk sources): a Virtual
Terrarium is tuned by its *installed upgrades*, and the fed milk provides only the variant
and its base budget (any catalyst components on the milk are ignored for the block's own
tuning). New items mean new textures + recipes to design and balance; tiering them (as
Productive Bees does) is optional and deferred. Names/textures/recipes/tiers are an open
sub-decision.

### Output

An internal output inventory (proposed 9-18 slots) of Froglights (or Prismatic
Froglights). Extractable/pipeable on the **DOWN** face via `Capabilities.Item.BLOCK`
(PF's furnace-style side convention); other faces are milk intake.

### Visual feedback

- **Jade (required):** loaded frog kind + stats, milk variant + remaining budget, output
  fullness, and whether it is currently producing (and if idle, why - no frog / no milk /
  category mismatch / output full). Server-data provider, the pattern we already use.
- **Live model (required for v1):** a `BlockEntityRenderer` draws the **actual loaded
  frog**, kind-tinted, in/on the block - the approach the altar display frogs
  (Dragonsbane/Witherbane/...) already use - and the block/tank **tints to the milk's
  variant color** (`Category.tintArgb()` / the variant color). Empty block shows no frog.
  This is a real render task; budget for it in the build.

## Reuse map (build on these, do not reinvent)

| Need | Reuse |
|------|-------|
| Construct a Froglight from a variant | `FrogTongueDropHandler.buildFroglight(Identifier, StoredEffect)` |
| Construct a Prismatic Froglight | `MidasTongueDropHandler.buildPrismaticFroglight(Identifier)` |
| Drop count from Bounty | `FrogStats.bountyDropCount(bounty, maxDrops, cap)` |
| Eat cadence from Appetite | `FrogStats.appetiteCooldownTicks(appetite, ...)` |
| Interval/batch + catalyst math | `MilkSpawnEconomy.intervalTicks` / `batchQuantity` |
| Milk stats (variant, catalyst levels, budget) | `MilkCharge.fromBucket/fromFluid` |
| Frog kind/stats off a net stack | `EntityNetItem.isFilled`, `CUSTOM_DATA`, `FrogKind.readFromTag` |
| Variant -> category | `PFRegistries.variant(...).category()` |
| Block shape + capability wiring | Slime Milker (`SlimeMilkerBlock`/BE/Inventory/Menu/Screen); `PFModBusEvents.registerCapabilities`; `content/transfer/` adapters (`FluidTankResourceHandler`, `RestrictedItemResourceHandler`) |
| Single-variant fill-only intake | `TerrariumControllerBlockEntity`'s hand-rolled fill-only `ResourceHandler` + `SnapshotJournal` |

## Acceptance criteria

1. **Load a frog.** Right-clicking (or GUI-inserting) a filled Frog Net holding a
   Resource frog loads it; the block reports loaded; a non-Resource/non-Midas net is
   refused with no loss.
2. **Produce on match.** With a Resource frog loaded and matching-category Slime Milk in
   the tank, Froglights of the milk's variant appear in the output on the eat cadence, no
   power supplied.
3. **Idle on mismatch.** A frog whose category differs from the milk variant (Cave frog +
   Bog milk) produces nothing and Jade says "category mismatch."
4. **Bounty scales count.** Froglights per event follow `FrogStats.bountyDropCount`
   (default 1 at Bounty 1-4, 2 at 5-8, 3 at 9-10), times the quantity multiplier.
5. **Appetite scales rate.** A higher-Appetite frog produces faster; a lower one slower.
6. **Upgrades apply.** Installing a Speed upgrade shortens the cadence; Yield raises
   per-event count; Capacity raises tank budget; Everflow stops depletion. Removing an
   upgrade reverts the effect, and upgrades are returned on break.
7. **Milk depletes.** Without Endless, the tank's budget falls with production and, when
   empty, the block idles ("no milk"); with Endless it never empties.
8. **Backpressure.** A full output inventory pauses production with nothing voided;
   clearing it resumes.
9. **Midas path.** A Midas frog + Mimic Milk yields Prismatic Froglights when
   `equivalenceEnabled`; with equivalence off, a loaded Midas frog idles.
10. **No power.** The block never asks for or accepts RF; it runs on milk alone.
11. **Automation I/O.** The DOWN face outputs Froglights to a hopper/pipe; a fluid pipe
    fills the tank; other faces do not output items.
12. **Jade + render.** Jade shows frog kind, milk variant + remaining, output fullness,
    and the produce/idle reason. The loaded frog renders in the block (kind-tinted) and the
    block tints to the milk's variant color; an empty block shows no frog.
13. **No loss on break.** Breaking the block drops the loaded frog as a filled Frog Net
    (stats intact - the #210 whole-entity rule) and the remaining milk as bucket(s) or
    voids nothing it can return; installed upgrades drop too.
14. **Throughput parity.** One Virtual Terrarium's output rate is within tolerance of one
    physical-Terrarium frog at equal stats + equal upgrades (the "normal per-frog rate"
    ruling), and below a maxed multiblock's total.
15. **GameTest lock.** A registry GameTest asserts: variant match -> correct-variant
    Froglight; mismatch -> nothing; Bounty count curve; Endless non-depletion; output-full
    backpressure. (Client visuals - tint, model, Jade - verified by a manual `runClient`
    pass; GameTest is render-blind.)

## Registration / wiring checklist (for the build PR)

- `content/block/VirtualTerrariumBlock` (LIT/loaded-style state, wires the BE ticker) +
  `content/block/entity/VirtualTerrariumBlockEntity` (`MenuProvider`; frog slot + fluid
  tank + upgrade slots + output; `static serverTick` eat-emulation loop) +
  `VirtualTerrariumInventory` + `content/menu/VirtualTerrariumMenu` +
  `client/screen/VirtualTerrariumScreen`.
- `PFBlocks` / `PFItems` (BlockItem + the new upgrade items) / `PFBlockEntities` /
  `PFMenuTypes` / `PFCreativeTabs` (after the appliances).
- The **upgrade item family** (Speed / Yield / Capacity / Everflow) - items, models,
  textures, recipes, and a `virtual_terrarium_upgrade` item tag for the slot filter.
- A **`BlockEntityRenderer`** drawing the loaded frog (kind-tinted; reuse the altar
  display-frog rendering) + the milk-color tint; registered client-side.
- Capabilities in `PFModBusEvents.registerCapabilities`: `Fluid.BLOCK` (fill-only tank),
  `Item.BLOCK` (DOWN = output).
- Blockstate + model + textures (gen/ pipeline); loot table (returns frog + milk +
  upgrades); `mineable/pickaxe` tag; lang (name + tooltip + Jade + the idle reasons).
- Void-tier crafting recipe (balance pass) + a `config_enabled` gate if we add
  `virtualTerrarium.enabled`.
- Jade provider (server-data), split per the Jade 26.1 single-interface rule.
- Guide: an entry under the Terrarium or Appliances chapter (Modonomicon).
- GameTests per AC 15.

## Decisions to finalize (before build)

1. **Upgrade item family** - names, textures, recipes, and whether they are tiered. The
   four levers are set (Speed, Yield, Capacity, Everflow); only the item design is open.
2. **Cadence basis** - Appetite-cooldown (lead) vs milk spawn interval vs a blend.
3. **Slot counts** - upgrade slots (lead 4), output slots (lead 9).
4. **Config gate** - ship a `virtualTerrarium.enabled` flag? (Lead: yes, default on.)
5. **Void-tier recipe** - the actual ingredient list for the block (balance pass).
