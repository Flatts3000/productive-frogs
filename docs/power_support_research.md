# Power Support (research notes)

> **Status: RESEARCH, not a build spec.** This is a deep-dive into what it would
> take to make Productive Frogs machines accept power from the NeoForge tech-mod
> ecosystem (Mekanism, Industrial Foregoing, Powah, etc.). Power is **V2 scope**
> (`docs/versioning.md`: "power/pipes/multiblocks" is the V1/V2 line), and the
> **Froglight Juicer** (`docs/froglight_juicer.md`) is the designated lightest-possible
> v2 block that opens the energy-capability pattern. Nothing here is committed;
> treat it as the reference brief to promote into a real spec when a power-consuming
> block is greenlit. Captured 2026-05-29.

## Bottom line up front

**One capability covers the entire ecosystem.** If a PF machine exposes NeoForge's
built-in **`Capabilities.EnergyStorage.BLOCK`** as a *receiver* of Forge Energy
(FE), every power mod that has a 1.21.1 NeoForge release will feed it with **zero
hard dependency and zero per-mod code** - the same "compat is shipped-in-NeoForge,
not a `compileOnly` mod dep" principle the variant system and the v1.3 crush
recipes already follow. FE / `IEnergyStorage` ships in NeoForge core; it is not a
mod we depend on, exactly like the `c:` common tags are not a mod we depend on.

The single exception is **base Create** (rotational Stress Units, not FE), which
needs a converter addon. AE2 is a power *sink*, not a source, so it is irrelevant
as a feeder. **Thermal Series still has no 1.21.1 release** (confirms prior
research in `cross_mod_compat.md`).

This makes a power-consuming machine *low-risk on the compat axis*. The real cost
is the V2 mechanics it introduces (an internal energy buffer + the design question
of what happens in a pack with no power mod), not interop breadth.

---

## 1. The interop layer: FE / `IEnergyStorage` is universal

Forge Energy (FE, the Redstone-Flux-derived standard) exposed through NeoForge's
`IEnergyStorage` block capability **is** the lingua franca. Mods with their own
internal energy unit still expose an FE-compatible capability on their cables and
machines and convert at the boundary:

| Mod | Internal unit | Speaks FE at the block boundary? | Conversion | 1.21.1 release |
|---|---|---|---|---|
| Immersive Engineering | FE natively | Yes (native) | 1:1 | Yes (12.4.x) |
| Powah | FE natively | Yes (native) | 1:1 | Yes (6.2.x, Technici4n fork) |
| Flux Networks | FE natively | Yes (type-agnostic transport) | 1:1 | Yes (8.0.0) |
| Industrial Foregoing | internal unit | Yes (accepts/converts FE) | 1:1 | Yes (3.6.x) |
| Mystical Agriculture / Agradditions | FE | Yes | 1:1 | Yes (8.0.x) |
| Mekanism | Joules (J) | Yes (FE cap on cables/machines) | **1 FE = 2.5 J** | Yes (10.7.x, dev-labeled alpha) |
| EnderIO | Microinfinity (uI) | Yes (native, uI == FE) | 1:1 | Yes (8.2.x beta) |
| AE2 | AE | **Sink only** (Energy Acceptor draws FE) | 2 FE = 1 AE | Yes (19.2.x) |
| Create | Stress Units (rotation) | **No** | n/a, needs addon | Yes (6.0.x) base; see below |
| Thermal Series | RF/FE | **No 1.21.1 release** | n/a | **No** (stalled at 1.20.1) |

Takeaways:

- **Mekanism** is the only ratio worth remembering (2.5 J per FE), and it is handled
  transparently by Mekanism's own cables, not by us. A Universal Cable pushing into
  an FE-receiving block converts on the way out.
- **AE2 never feeds a machine.** Its Energy Acceptor *consumes* FE to charge an ME
  network (2 FE -> 1 AE). It is only relevant if we ever wanted a PF block to
  *output* FE into an AE2 network, which is not on the table.
- **Create** is the lone true outlier (section 3).

---

## 2. The NeoForge 1.21.1 energy API (exact symbols)

Verified against the NeoForge `1.21.1` branch source (the line our `21.1.230`
target lives on). **Version-drift warning:** later branches replaced this with
`EnergyHandler` / `SimpleEnergyHandler` and `ValueOutput`/`ValueInput`
serialization, and deprecated `IEnergyStorage`/`EnergyStorage` as of 1.21.9. **Do
not copy newer-MC examples.** The forms below are correct for 1.21.1, and mirror
the same capability-API generation as our existing `Capabilities.ItemHandler.BLOCK`
usage (not the newer `Capabilities.Item.BLOCK` / `ResourceHandler<ItemResource>`).

| Concern | Exact symbol |
|---|---|
| Interface | `net.neoforged.neoforge.energy.IEnergyStorage` |
| Helper impl | `net.neoforged.neoforge.energy.EnergyStorage` (implements `INBTSerializable<Tag>`, serializes to a bare `IntTag`) |
| Block cap object | `net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK` (type `BlockCapability<IEnergyStorage, @Nullable Direction>`, resource id `neoforge:energy`) |
| Registration | `RegisterCapabilitiesEvent#registerBlockEntity(BlockCapability, BlockEntityType, ICapabilityProvider)` |

### The interface

```java
int  receiveEnergy(int toReceive, boolean simulate); // returns amount accepted
int  extractEnergy(int toExtract, boolean simulate);  // returns amount removed
int  getEnergyStored();
int  getMaxEnergyStored();
boolean canExtract();   // false => extractEnergy always returns 0
boolean canReceive();   // false => receiveEnergy always returns 0
```

`simulate == true` is a dry run; transport mods probe with it before committing.

### Registration (mirrors `PFModBusEvents.onRegisterCapabilities`)

```java
event.registerBlockEntity(
    Capabilities.EnergyStorage.BLOCK,
    PFBlockEntities.FROGLIGHT_JUICER.get(),
    (be, side) -> be.getEnergyStorage());     // ignore side => all faces accept
```

Refuse specific faces by returning `null` for them, exactly like the Milker's
side-aware item routing:

```java
(be, side) -> (side == null || side == Direction.DOWN) ? be.getEnergyStorage() : null;
```

### The helper

`net.neoforged.neoforge.energy.EnergyStorage` is ready-made. Constructors:

```java
new EnergyStorage(capacity)
new EnergyStorage(capacity, maxTransfer)
new EnergyStorage(capacity, maxReceive, maxExtract)
new EnergyStorage(capacity, maxReceive, maxExtract, energy) // initial energy
```

`canReceive()` is `maxReceive > 0`; `canExtract()` is `maxExtract > 0`.

**NBT (the load-bearing 1.21.1 difference):** it serializes to a bare `IntTag`,
and both methods take a `HolderLookup.Provider`:

```java
@Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.put("Energy", energy.serializeNBT(registries));   // returns IntTag
}
@Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
}
```

The stock class does **not** call `setChanged()` on mutation - we do that ourselves
(below).

### Consume-per-operation pattern (fits the appliance `serverTick`)

A pure consumer keeps a receive-only buffer and spends it in the static ticker,
slotting straight into the furnace-style loop documented in `CLAUDE.md`:

```java
private final EnergyStorage energy = new EnergyStorage(CAPACITY, MAX_RECEIVE, 0) {
    @Override public int receiveEnergy(int toReceive, boolean simulate) {
        int r = super.receiveEnergy(toReceive, simulate);
        if (r > 0 && !simulate) setChanged();   // + sync; see below
        return r;
    }
    void consume(int n) { this.energy -= n; }    // internal drain, bypasses maxExtract=0
};

public static void serverTick(Level level, BlockPos pos, BlockState state, FroglightJuicerBlockEntity be) {
    if (!be.hasWork()) { setWorking(level, pos, state, false); return; }
    if (be.energy.getEnergyStored() < FE_PER_TICK) { setWorking(level, pos, state, false); return; } // buffer empty -> pause
    be.energy.consume(FE_PER_TICK);
    be.cookProgress++;
    be.setChanged();
    if (be.cookProgress >= be.cookTotal) be.complete();
    else setWorking(level, pos, state, true);
}
```

**Gotcha worth pinning:** if `maxExtract == 0` (the recommended setting for a pure
consumer, so neighbors can't pull power back out), then `extractEnergy(...)` returns
0 and won't drain our own buffer. Drain an internal counter directly (the `consume`
helper above) rather than going through `extractEnergy`. This is the standard
consumer-machine idiom.

### Push, not pull

Forge Energy is direction-agnostic at the interface, but every FE cable/conduit mod
(Mekanism, EnderIO, Flux Networks, IE, Powah, Pipez, ...) is **push-based**: the
network finds adjacent `EnergyStorage.BLOCK` providers and calls `receiveEnergy` on
them. A PF consumer machine therefore does **nothing active** - it exposes a
receive-capable buffer and energy flows in. We only ever write pull-side logic if PF
were to ship a *generator*, which it is not.

### Client sync of an energy bar (the `ContainerData` short-truncation trap)

For a furnace-style screen, reuse the `ContainerData` path the Milker/Spawnery
already use for cook progress. **But `ContainerData` syncs each index as a `short`**
(`ClientboundContainerSetDataPacket` writes `(short) value`), so any energy value
above 32767 corrupts. Split the int across two indices:

```java
case 0 -> energy.getEnergyStored() & 0xFFFF;          // low 16 bits
case 1 -> (energy.getEnergyStored() >>> 16) & 0xFFFF; // high 16 bits
// client: int e = (hi << 16) | (lo & 0xFFFF);
```

This is the conventional fix and avoids a custom packet. The cook/burn progress
fields the appliances already sync stay below 32767, so they were never exposed to
this; an energy capacity in the tens-of-thousands FE is, so it must be split.

---

## 3. Create is the one outlier

Base **Create uses Stress Units / rotation and implements no `IEnergyStorage`** -
a Create-only pack cannot power an FE machine. The bridge is the addon **Create:
Crafts & Additions** (`createaddition`, verified 1.21.1 NeoForge build): its
**Alternator** converts rotation -> FE (~75% efficiency), so a Create user feeds a
PF block by building an Alternator off their kinetic network. We document this for
pack authors; we do not (and cannot reasonably) support raw rotation.

---

## 4. Per-mod feeding behavior + quirks (for pack-author docs)

Whatever block ships first should carry a short "how to power it" note per mod:

- **Immersive Engineering** - attach an **LV/MV/HV connector** to a face; the wire
  carries FE between connectors. IE's voltage tiers are 256 / 1024 / 4096 RF/t. IE's
  "too much voltage explodes the machine" rule applies to **IE's own** machines; a
  generic FE sink isn't governed by it, but a pack author should still match
  connector tier to the block's accept rate so nothing on the IE side overloads.
- **Mekanism** - any Universal/Ultimate cable auto-connects and pushes FE
  (converted from Joules at 2.5 J/FE). Tiered throughput (Basic..Ultimate).
- **Powah** - energy cable, push-based, auto-connects. FE native.
- **EnderIO** - energy conduit, push-based, uI == FE. Conduits support per-face I/O
  modes (the 1.21.1 conduit rewrite reworked this); default pushes into FE sinks.
- **Flux Networks** - wireless: a **Flux Point** on a face pushes network energy in.
  Type-agnostic.
- **Industrial Foregoing** - machines/transport accept FE and convert 1:1. Note the
  "Pity/Simple/Advanced/Supreme" names are machine-frame/upgrade tiers, **not** an
  energy protocol, so they don't affect interop.

(The FE-receive interop above is verified; exact in-game I/O-config UI per the
specific 1.21.1 builds of EnderIO and Mekanism is inferred from established mod
design, not a build-specific changelog line.)

---

## 5. How this lands in the PF architecture

The good news is that an energy capability is **architecturally cheap** and fits
existing patterns:

1. **No hard dependency, no `compat/` package.** FE/`IEnergyStorage` is NeoForge
   core, so a power-consuming block is pure first-party code with no `compileOnly`
   mod deps - fully consistent with `architecture.md` guiding principle 2 and the
   no-`compat/`-Java rule. This is *more* self-contained than the JEI/Jade
   integrations, which at least need `compileOnly` APIs.
2. **Reuses the appliance shape.** Block + BlockEntity (`MenuProvider`) +
   `ContainerData` + static `serverTick` + Menu + Screen, plus one extra capability
   registration line in `PFModBusEvents`. The energy buffer is just another field
   alongside the inventory, serialized in `saveAdditional`/`loadAdditional`.
3. **The Froglight Juicer is the intended opener** (`docs/froglight_juicer.md`):
   the lightest possible v2 block (single block, no multiblock) whose entire reason
   for being v2 is that it introduces the energy-capability + fluid-tank patterns
   every later v2 block (Frog Terrarium, buffered Slime Milker) reuses. Building
   power *as* the Juicer establishes the pattern once.

### The one real design decision: "no power mod present"

This is open question #2 in `froglight_juicer.md`, and it is the crux of bringing
power into the mod at all. A power-required block is dead weight in a pack with no
power mod (and Sky Frogs, the anchor pack, may not ship one). Options:

- **(a) Tech-pack-only block.** Accept that the powered block simply isn't usable
  without a power mod. Cleanest code, narrowest audience.
- **(b) Config toggle to run powerless.** A `power.required` flag (defaulting per
  pack) that, when off, skips the energy gate. Mirrors the Spawnery's
  `config_enabled` gating philosophy - PF already has the `ConfigEnabledCondition`
  machinery and a "fail-open / fail-closed" convention to model this on.
- **(c) Fuel-burn fallback** like the Spawnery (slime balls as fuel). Keeps the
  block universally usable; power just becomes the faster/cleaner lane. Most work,
  best player experience, and most on-brand with "stay close to vanilla" (a
  furnace burns fuel; a powered furnace upgrade is the tech-pack bonus).

**Lean:** option (c) or (b). A block that's useless half the time contradicts the
"every V1 capability stays usable" compatibility promise if the powered block is the
*only* way to do something. If power is purely a *speed/convenience* upgrade over a
fuel or hand-operated baseline, it sidesteps the problem entirely - which argues for
making any powered machine an *accelerated variant* of an existing hand path rather
than a new gated capability.

---

## 6. Open questions (resolve before building)

1. **Powerless fallback** (section 5): (a) tech-only, (b) config toggle, or (c)
   fuel-burn. This is the gating decision.
2. **Is power a new capability or an accelerator?** If powered machines only ever
   *speed up* something the hand-operated V1 loop already does, the "no power mod"
   problem largely dissolves and the compatibility promise stays intact. Decide the
   philosophy before the first block.
3. **Numbers:** FE capacity, FE/tick or FE/operation cost, accept rate
   (`maxReceive`). Pick values that read sensibly next to the target mods' generator
   outputs (a basic Powah/Mekanism generator is hundreds of FE/t).
4. **Energy-bar UI:** confirm the two-index `ContainerData` split (section 2) in a
   dev run against a real value > 32767.
5. **Does the buffered Slime Milker (a named v2 item) also take power, or stay
   fuelless?** If the Juicer is the only powered block, the pattern is contained;
   if the Milker upgrade also draws FE, settle that it shares the same buffer
   helper.
6. **Pack-author doc:** ship a short per-mod "how to power it" table (section 4),
   including the Create -> Crafts & Additions Alternator caveat.

---

## Related

- Designated first powered block: [froglight_juicer.md](./froglight_juicer.md)
- Scope split / why power is V2: [versioning.md](./versioning.md)
- No-hard-dep / conditional-compat philosophy: [cross_mod_compat.md](./cross_mod_compat.md), [architecture.md](./architecture.md)
- Appliance pattern + capability registration: `CLAUDE.md`, `PFModBusEvents.onRegisterCapabilities`
- Config-gating precedent (`config_enabled` condition): [spawnery.md](./spawnery.md)
- Runway: [../ROADMAP.md](../ROADMAP.md)

## Sources

NeoForge 1.21.1 energy API (verified against the `1.21.1` branch source):

- `net/neoforged/neoforge/energy/IEnergyStorage.java`, `EnergyStorage.java`
- `net/neoforged/neoforge/capabilities/Capabilities.java`, `RegisterCapabilitiesEvent.java`, `BlockCapability.java`
- NeoForged capabilities docs: https://docs.neoforged.net/docs/1.21.8/inventories/capabilities/ (`registerBlockEntity` shape unchanged from 1.21.1)

Power-mod interop (verified per-mod where noted):

- Mekanism Joules<->FE (1 FE = 2.5 J): https://wiki.aidancbrady.com/wiki/Energy_Unit_Conversion
- AE2 energy (2 FE = 1 AE, Energy Acceptor): https://guide.appliedenergistics.org/development/ae2-mechanics/energy
- Immersive Engineering (FE native, LV/MV/HV 256/1024/4096 RF/t): https://github.com/BluSunrize/ImmersiveEngineering/blob/1.21.1/changelog.md
- Powah (FE, 1.21.1 Technici4n fork 6.2.x): https://github.com/Technici4n/Powah
- EnderIO (1.21.1 conduits): https://github.com/Team-EnderIO/EnderIO/releases
- Flux Networks (FE, type-agnostic, 1.21.1-8.0.0): https://www.curseforge.com/minecraft/mc-mods/flux-networks/files
- Industrial Foregoing (FE 1:1, 1.21.1 3.6.x): https://www.curseforge.com/minecraft/mc-mods/industrial-foregoing/files/all
- Create Stress Units (no FE): https://github.com/Creators-of-Create/Create/wiki/Stress-Units,-Capacity-and-Impact
- Create: Crafts & Additions (Alternator rotation->FE, 1.21.1): https://modrinth.com/mod/createaddition
- Thermal Series (no 1.21.1, tops at 1.20.1): https://www.curseforge.com/minecraft/mc-mods/thermal-expansion/files/all
- Mystical Agriculture / Agradditions (1.21.1 8.0.x): https://www.curseforge.com/minecraft/mc-mods/mystical-agriculture/files/all
