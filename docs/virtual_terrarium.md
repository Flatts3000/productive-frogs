# Virtual Terrarium (mc-1.21.1 backport)

Backported from the 2.x line (#341) at parity with what this line has. A two-block
machine that runs one frog's eat loop headlessly: a **Processor** (the machine) and
a glass **Display Dome** on top. Break either and it stops.

The canonical design contract is `docs/virtual_terrarium.md` **on `main`** (the 2.x
spec). This file records only where the 1.21.1 build differs from it.

## Scope on this line: Resource + Midas, no Predator

The 2.x Virtual Terrarium feeds three frog kinds. The **Predator** path depends on
predation - Mob Slurry, `PredatorPrey`, the entity-free mob-loot roll, and Liquid
Experience for its XP tank - none of which exists on 1.21.1. Porting it would mean
porting predation, which is the whole reason the 2.x line is a separate mod. So the
predator path and its XP tank are **out**; everything else is in:

- **Resource** frog + **Slime Milk** of its own species -> that variant's Froglight.
- **Midas** frog + **Mimic Milk** -> a Prismatic Froglight (gated on `equivalenceEnabled`).
- All five upgrades: Bounty, Appetite, Smelter, Melter, Overclock.
- Item output (DOWN) and the molten product tank (with a Melter).
- The Display Dome frog renderer, the GUI, Jade, JEI, and the guide entry.

This is the same "parity with what the line has" bar the Slime Milk Basin shipped
under (#342).

## The 1.21.1 deltas (where the port diverges from the 2.x code)

| Area | 2.x (26.1) | Here (1.21.1) |
|---|---|---|
| Milk identity | one `slime_milk` fluid + `SLIME_VARIANT` component | **per-variant fluids** (v1.8): the loaded variant comes from the fluid identity via `PFVariantMilk.variantOf`, not a component. Mimic Milk is still one fluid + `SYNTHESIZED_ITEM` |
| Fluid caps | `Capabilities.Fluid.BLOCK` over `ResourceHandler<FluidResource>` | `IFluidHandler` (fill-only feedstock, extract-only molten), the Crucible's shape |
| Item caps | `RestrictedItemResourceHandler` | `IItemHandler` side views (the Milker's `SidedView`) |
| Energy | `EnergyHandler` + `ReceiveOnlyEnergyHandler` | `EnergyStorage` receive-only subclass (the Distiller's shape); RF syncs as one full-int `ContainerData` slot |
| Frog kind | `FrogKind` off `CUSTOM_DATA` | the netted frog's saved NBT `"Category"` string + `"Midas"` flag - the pre-FrogKind model |
| Save/load | `ValueInput` / `ValueOutput` | `CompoundTag`; `FluidTank.writeToNBT` / `readFromNBT` |
| Screen | 26.1 `GuiGraphicsExtractor` pipeline | `AbstractContainerScreen` (`renderBg` / `render` + tooltip; `PFContainerScreen` base) |
| Dome render | 26.1 extract/submit render state | direct `EntityRenderDispatcher.render` (the WitherAltarHatch display pattern) |
| Guide | Modonomicon | Patchouli, prose inline in the entry JSON |

**Added seam:** `MilkSpawnEconomy.intervalTicksDeterministic(speed)` - the midpoint of
the spawn interval, so the eat cycle is predictable and the Appetite / Overclock
levers read as visible speed changes rather than random noise. The physical Basin and
source keep using the random `intervalTicks`.

## Decisions carried over from `main` (do not re-litigate)

- **No power for the core loop.** Only the Overclock draws RF; Smelter and Melter run
  free. An Overclock installed with an empty buffer **hard-stalls** at zero progress.
- **Budget is spawns, not liquid.** Each eat spends one spawn of the milk's budget; the
  1000 mB never drains per eat, only when the budget hits zero. Endless never depletes.
- **Per-slot upgrade caps:** 8 Bounty, 8 Appetite, 3 Overclock, and exactly one of
  Smelter **or** Melter (mutually exclusive, enforced at the slot).
- **Break returns items, not fluids.** The Processor drops the frog (filled net) and the
  upgrades; the tank fluid is not minted as a bucket (bucket econ). Drain it first.
- Void tier, expensive, late. No config flag.

## Tests

`PFGameTests` covers: the two-block form gate; the Resource and Midas paths; the
mismatch idle; the Bounty upgrade raising output; Smelter (smelted item) and Melter
(molten tank) outputs plus their mutual exclusion; Endless non-depletion; break
returning items-not-fluids; the Overclock power stall; and pipe fill + DOWN extract.
Client visuals (the Processor textures, the Dome frog render, the GUI, Jade, JEI, the
Patchouli entry) are verified by a manual `runClient` pass - GameTest is render-blind.
