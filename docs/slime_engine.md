# Slime Engine (design spec, deferred)

> Status: SPEC ONLY, not implemented. This captures the agreed design so a future
> session can build it. The Slime Engine is the mod's first FE (NeoForge Energy)
> generator, which makes it the V2 "automation / power" opener the
> [power support research](power_support_research.md) doc set up (power is V2 in
> [ROADMAP.md](../ROADMAP.md) and [versioning.md](versioning.md)). FE ships in
> NeoForge with no extra dependency, and the codebase currently has zero energy
> code, so this is greenfield. Targets the existing NeoForge 21.1.230 / MC 1.21.1 /
> Java 21 stack.

## Concept

A placed block with 8 internal fuel slots that each hold a Slime Bucket. Each filled
slot generates a trickle of FE; a set of crafted upgrades placed in the engine raises
the output. The engine pushes its FE into adjacent cables / machines (Mekanism,
Industrial Foregoing, Pipez, any `IEnergyStorage` consumer).

## Locked design decisions

1. **Fuel = Slime Bucket** (the captured-slime item `PFItems.SLIME_BUCKET`, which
   carries its variant in `BUCKET_ENTITY_DATA` NBT and stacks to 1). Power is
   variant-agnostic: any captured slime counts the same.
2. **Fuel is permanent while present.** A filled slot generates while the bucket sits
   in it; the bucket is NOT consumed. Remove it to stop that slot. No burn timer, no
   empty-bucket return.
3. **Per-slot output.** Each filled fuel slot makes a base 1 FE/tick, so 8 full slots
   = 8 FE/t base. Upgrades multiply on top.
4. **GUI upgrade slots.** Dedicated upgrade slots in the engine's screen; insert
   crafted Power Upgrade items to raise FE/t. This is new infrastructure: the mod has
   no upgrade-slot pattern yet (Slime Milk catalysts are drop-in, not slotted).

## Behaviour

- Right-click opens a furnace-style GUI: 8 fuel slots (accept only `SLIME_BUCKET`),
  4 upgrade slots (accept Power Upgrade items), and an energy bar showing the buffer.
- Each server tick:
  `output = filledFuelSlots(0..8) * baseFePerSlot * multiplier`,
  where `multiplier = 1 + sum(bonus of each Power Upgrade in the 4 upgrade slots)`.
  Add `output` to the internal buffer (capped), then push to adjacent receivers.
- An `ACTIVE` / `LIT` blockstate drives a glow and light level while `output > 0` and
  the buffer is not full (mirrors the Spawnery's `LIT`).

## Energy model

- Internal `EnergyStorage` buffer, capacity ~200,000 FE (config). Generator semantics:
  `canReceive = false`, `canExtract = true` so pull-based cables work. Generation adds
  to the buffer directly (not via `receiveEnergy`, which is disabled) through a small
  `EnergyStorage` subclass exposing a `generate(int)` that bumps `energy` up to
  capacity and calls the block entity's `setChanged` (stock `EnergyStorage` does not).
- Active push: each tick, iterate the 6 faces, query `Capabilities.EnergyStorage.BLOCK`
  on the neighbour, `receiveEnergy(...)`, and drain the buffer by the accepted amount,
  so it also works with push-only setups. Pull-based cables work via the exposed cap.
- Expose with
  `event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PFBlockEntities.SLIME_ENGINE.get(), (be, side) -> be.getEnergy())`
  in `PFModBusEvents.onRegisterCapabilities`.
- NBT (1.21.1 shape): `tag.put("Energy", energy.serializeNBT(registries))` (an IntTag)
  and `energy.deserializeNBT(registries, tag.get("Energy"))`. Both take a
  `HolderLookup.Provider`.
- GUI sync: split the `int` buffer across two `ContainerData` indices (low / high 16
  bits) to avoid the short-truncation trap; an optional third index carries current FE/t.

## Upgrade model

- 4 GUI upgrade slots, not consumed (removable, like tech-mod upgrades).
- A tiered set of Power Upgrade items (for example I / II / III), new plain `Item`s in
  `PFItems`. Each carries a bonus added to the output multiplier sum. Default bonuses
  are config-tunable, for example +1 / +2 / +4. Example: 8 fuel slots plus one Tier III
  (+4) gives `8 * 1 * (1 + 4) = 40` FE/t; four Tier III gives `8 * (1 + 16) = 136` FE/t.
- Recipes mirror the Slime Milk catalyst recipes (Sweetslime plus a tech or vanilla
  material), gated by the config condition below.

## Config and gating

- New `PFConfig` `slime_engine` section: `enabled` (default true), `baseFePerSlot`
  (default 1), `bufferCapacity` (default 200000), `maxTransferPerTick`, and the upgrade
  tier bonuses. Add accessors with pre-config-load fallbacks (mirror the catalyst
  accessors).
- Gate the engine and upgrade recipes via a new
  `ConfigEnabledCondition.Key.SLIME_ENGINE("slime_engine")` reading
  `PFConfig.SLIME_ENGINE_ENABLED`, exactly like `MILK_CATALYSTS` and `SPAWNERY`.
  Default ON: it is core V2 content and is harmless without a power mod (the buffer
  just fills and idles).

## Files (mirror the Slime Milker / Spawnery appliance pattern)

New:
- `content/block/SlimeEngineBlock.java` (Block + EntityBlock; FACING + ACTIVE/LIT;
  `getTicker`; opens the menu; drops the inventory on break).
- `content/block/entity/SlimeEngineBlockEntity.java` (MenuProvider; the `EnergyStorage`
  buffer + generate; a `static serverTick` doing output + push; ContainerData for the
  energy bar; save/load energy).
- `content/block/entity/SlimeEngineInventory.java` (`ItemStackHandler`, 12 slots = 8
  fuel + 4 upgrade; `isItemValid`: fuel slots accept `SLIME_BUCKET`, upgrade slots
  accept Power Upgrade items; side-aware view so hoppers insert fuel from any face; no
  item output view, since energy is the output).
- `content/menu/SlimeEngineMenu.java` (8 + 4 container slots plus player inventory;
  `quickMoveStack`; `addDataSlots`).
- `client/screen/SlimeEngineScreen.java` (`render` override that calls `renderTooltip`,
  the 1.21.1 gotcha; `renderBg` draws the background plus the energy-bar fill).
- `content/item/PowerUpgradeItem.java` (plus a `PowerUpgrade` enum/tier if tiered),
  with tooltip lines.
- Recipe JSONs (config_enabled gated): the engine and each upgrade tier. Blockstate +
  block model + item models + textures (a procedural generator script under `scripts/`,
  mirroring `generate_catalyst_textures.py`).

Modify (registration, mirroring the `SLIME_MILKER` wiring):
- `registry/PFBlocks.java` (register `SLIME_ENGINE` with a lit light level),
  `registry/PFBlockEntities.java`, `registry/PFMenuTypes.java`,
  `registry/PFItems.java` (block item + upgrade items),
  `client/PFClientEvents.java` (bind the screen),
  `event/PFModBusEvents.java` (register `Capabilities.EnergyStorage.BLOCK` plus a
  `Capabilities.ItemHandler.BLOCK` fuel-insert view),
  `PFConfig.java`, `data/condition/ConfigEnabledCondition.java`,
  `registry/PFCreativeTabs.java` (add the engine + upgrades, gated),
  and `en_us.json` (block, engine, upgrade, and JEI info keys).

## Tests

- GameTest: place the engine, fill N fuel slots, tick, and assert the buffer gained
  `N * baseFePerSlot * multiplier`; add an upgrade and assert the rate rises; place an
  adjacent `IEnergyStorage` and assert the push drains the buffer.
- JUnit: registration (block / BE / menu / items), config accessors, `isItemValid`
  per slot.
- Manual `runClient`: the GUI renders, the energy bar fills, and a power mod's cable
  or machine pulls FE; LangCompleteness for the new keys.

## Open calls (resolve at build / release time)

- Version: this opens V2; recommend cutting it as v2.0.0 (the automation / power
  opener) rather than a v1.x. Confirm at release.
- Exact upgrade tier count, bonus numbers, and buffer / transfer sizes are
  config-tunable; the defaults above are a starting point to balance in playtest.
- Whether rarer slime variants should yield more FE (a variant-weighted fuel axis) is
  deferred; the first version of the engine is variant-agnostic.
