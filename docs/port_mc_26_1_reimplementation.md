# MC 26.1 Re-implementation Decisions

> **Purpose.** The 26.1 port is a re-implementation for the new era, not a 1:1 migration. Maintainer directive (2026-06-29): *"all assumptions from the current version about implementation need to be questioned."* This doc records, per subsystem, the load-bearing 1.21.1 implementation assumption, what the real 26.1 API enables (verified against the decompiled NeoForge 26.1.2.76 sources, not the research predictions), and the decision. Companion: [`port_mc_26_1.md`](./port_mc_26_1.md) (phase plan), [`port_mc_26_1_research.md`](./port_mc_26_1_research.md) (API/ecosystem research).
>
> **Guiding principle:** the new transfer API's unit of currency is a **resource = (registry object + data components)** - `ItemResource` is item+components, `FluidResource` is fluid+components, both immutable and component-carrying. In 1.21.1 pipes/tanks/inventories keyed on the bare registry object and could not see components; that single limitation is the root of most workarounds in this codebase. The new era removes it. Question every workaround that existed because "X can't carry a component."

---

## R-1: Slime Milk - collapse per-variant fluids to one component-carrying fluid

**Status: DECIDED - collapse. High-leverage (~300 registry objects -> ~5 + a component).**

### The 1.21.1 assumption

Slime Milk is **per-variant fluids** (v1.8, issue #129). `PFVariantMilk.bootstrap` mints, for **each of the 60 variants**, five registry objects: a `FluidType`, a source `Fluid`, a flowing `Fluid`, a `SlimeMilkSourceBlock`, and a `SlimeMilkBucketItem` - plus the per-variant blockstate/model/texture/lang. The stated rationale (CLAUDE.md, `PFVariantMilk` javadoc):

> "the variant *is* a distinct `Fluid` so tank/pipe mods (JDT, Mekanism, Pipez) preserve it through automation (they key on the Fluid registry object, never our component)."

CLAUDE.md is emphatic this was deliberate and warns *"don't re-collapse to one fluid."* That warning was correct **under 1.21.1**, where fluid automation could not carry a component.

### What 26.1 actually enables (verified from source)

From `neoforge-26.1.2.76-sources.jar`:

- `FluidResource implements DataComponentHolderResource<Fluid>` (`transfer/fluid/FluidResource.java`). A `FluidResource` **is** `(Fluid + data components)`, immutable.
- `DataComponentHolderResource` exposes `.with(DataComponentType<D>, D value)`, `.without(...)`, `.withMergedPatch(DataComponentPatch)`, `.getComponentsPatch()` - you can stamp any component onto a fluid resource.
- `FluidResource`'s `STREAM_CODEC` includes `DataComponentPatch.STREAM_CODEC` - the component patch is **network-synced**.
- The whole fluid transfer/automation layer is `ResourceHandler<FluidResource>` (`Capabilities.Fluid.BLOCK`), which moves `FluidResource`s **with their components intact**.

So the exact thing per-variant fluids worked around - "automation can't preserve a component on milk" - is **solved natively**: one `slime_milk` fluid + a `slime_variant` (and `catalysts`) component on the `FluidResource` survives any tank/pipe/bucket that uses the modern transfer API.

### Decision

**One `slime_milk` fluid** (source + flowing), **one `FluidType`**, **one `SlimeMilkSourceBlock` + BE**, **one `SlimeMilkBucketItem`**. The variant (and catalyst buffs) ride as **data components**:

- **Bucket / tank / pipe / GUI:** the variant component lives on the `FluidResource` / bucket `ItemStack`. The new transfer API preserves it through automation - no per-variant `Fluid` needed. This also fixes the v1.8 documented caveat that *catalyst buffs are lost through a tank* (catalysts become components on the resource too, so they survive).
- **Placed source block:** already BE-driven today (`SlimeMilkSourceBlockEntity` carries the variant) - unchanged in spirit.
- **`PFVariantMilk.variantOf(Fluid)` reverse lookup** (Terrarium Controller intake): replaced by reading the `slime_variant` component off the incoming `FluidResource` - simpler and exact.

This deletes `PFVariantMilk` (the 300-object minting loop), the per-variant blockstates/models/textures/lang, and `VariantFluidDiscovery`'s milk-minting role. The Mimic Milk (EE lane) was *already* a single source-only fluid keyed on a component - this brings Slime Milk to the same, now-idiomatic, shape.

### The one wrinkle: in-world flowing-milk tint

Per-variant fluids gave **flowing milk** (vanilla fluid spread, no BE at the flowing pos) its per-variant tint via `FluidType.getTintColor` reading the variant. With one fluid:

- **Bucket / tank / GUI milk** tints correctly: the `FluidStack`/`FluidResource` overload of `getTintColor` reads the variant component -> color. Clean.
- **Placed source block** tints via its BE (BER or block tint). Clean.
- **Free-flowing milk spread across the world** has no BE and no per-pos component -> can't resolve its variant at the flowing pos.

**DECIDED (maintainer, 2026-06-29): milk does NOT spread as a world fluid in 2.0.** Milk exists only as a `FluidResource` (buckets, tanks, pipes) and the placed `SlimeMilkSourceBlock` + BE (which spawns slimes and carries the variant). No vanilla-style spreading pools. This removes the flowing-tint wrinkle entirely and is the cleanest fit for the single-fluid design - milk's real roles (transport, automation, source-spawning) never needed spreading. Implementation: the `slime_milk` fluid still exists as the bucket/tank content type, but its `Flowing` form is effectively unused (or the source block is not a `LiquidBlock` at all - decide during R-1 implementation whether to keep a non-spreading `FlowingFluid` or model the source purely as a custom block whose only fluid face is the bucket/pipe capability).

### 2.0 vs 2.x note

For **2.0 (standalone, no cross-mod fluid mods in scope)** the collapse is unambiguously correct - only PF's own machines touch milk, and they read the component/BE. When **integrations return in 2.x**, third-party fluid mods *on 26.1* use the same `ResourceHandler<FluidResource>` API and preserve the component, so the single-fluid design holds for them too (any mod still on a pre-transfer-rework API is the exception, not the rule, on 26.1). The v1.8 reason to expand no longer applies on the modern line.

---

## R-3: Capability / inventory model - adopt the transactional transfer API natively

**Status: DECIDED - native adoption. ~25 files, clean analogues (not a redesign).**

### The 1.21.1 assumption

Appliances back a slot-bounded `ItemStackHandler`, register `Capabilities.ItemHandler.BLOCK` (returns `IItemHandler`), expose side-aware `inputView()` / `outputView()` slices, and mutate via `extractItem` / `setStackInSlot`. Menus use `SlotItemHandler`. The EE machines use a receive-only `EnergyStorage(cap, maxReceive, 0)` + `Capabilities.EnergyStorage.BLOCK`. CLAUDE.md pins the **1.21.1** capability ids explicitly ("not the newer `Capabilities.Item.BLOCK`").

### What 26.1 actually enables (verified from source)

The old `Capabilities.ItemHandler`/`EnergyStorage` holders are **gone**; `Capabilities.Item.BLOCK` is `BlockCapability<ResourceHandler<ItemResource>, Direction>` and `Capabilities.Energy.BLOCK` is `EnergyHandler`. But the new transfer API ships **direct, idiomatic analogues** for every PF pattern:

| 1.21.1 | 26.1 (verified source) |
|---|---|
| `ItemStackHandler(size)` | `ItemStacksResourceHandler(size)` (extends `StacksResourceHandler<ItemStack, ItemResource>`; override `onContentsChanged` -> `setChanged`) |
| side-aware `inputView()`/`outputView()` | `RangedResourceHandler.of(handler, start, end)` / `.ofSingleIndex(handler, i)` |
| `SlotItemHandler` (menu) | `ResourceHandlerSlot(handler, modifier, slot, x, y)` (extends `StackCopySlot`) |
| `Capabilities.ItemHandler.BLOCK` -> `IItemHandler` | `Capabilities.Item.BLOCK` -> `ResourceHandler<ItemResource>` |
| `extractItem` / `setStackInSlot` | `insert(i, res, n, tx)` / `extract(i, res, n, tx)` inside `try (var tx = Transaction.openRoot()) { ...; tx.commit(); }` |
| `ItemStack` <-> handler | `ItemResource.of(stack)` / `resource.toStack(n)`; `ItemResource` is item+components |
| `EnergyStorage(cap, maxReceive, 0)` | `SimpleEnergyHandler(cap, maxInsert, maxExtract, energy)` (also `implements ValueIOSerializable` -> plugs into R-7's BE I/O; `onEnergyChanged` hook) |
| `Capabilities.EnergyStorage.BLOCK` | `Capabilities.Energy.BLOCK` -> `EnergyHandler` |

### Decision

Adopt the transactional API natively across the ~25 sites (appliance inventories + blocks + menus: Milker / Churn / Spawnery / Casting Mold / Crucible / Hatch; EE Alembic / Distiller; the altar receptacles; `PFModBusEvents` capability registration). Two real wins, not just compile-fixing:

1. **Transactions give the EE machines' airtight "insert output, then consume inputs, all-or-nothing" for free** - the manual transaction guard in the Alembic/Distiller becomes `Transaction.openRoot()` + `commit()`. Fire `onContentsChanged -> setChanged` on commit, not mid-transaction.
2. **`ItemResource` (item+components) makes the slot model component-aware by construction** - variant-stamped Slime Buckets / Froglights are first-class resources, not ItemStacks PF has to component-check by hand. Consistent with R-1's component-carrying milk.

Do **not** lean on the legacy `IItemHandler.of(...)` adapter shims (still present but a backport crutch) - the directive is to build the new era, and the native handlers are a clean fit. The side-aware routing shape (DOWN -> output slice, others -> input slice) is unchanged; only the slice type changes (`RangedResourceHandler`).

---

## R-2, R-4..R-n: queued reconsiderations (not yet investigated)

Each gets the same treatment (assumption -> verified 26.1 capability -> decision) as its phase is reached:

- **R-2 Item tints** - legacy `RegisterColorHandlersEvent.Item` -> declarative JSON `ItemTintSource` (+ datagen + completeness gate). Dynamic component-read confirmed first-class.
- **R-4 GUI** - delete the `PFContainerScreen` `renderTooltip` workaround (new flow handles tooltips); adopt `RenderPipelines` + two-phase `GuiRenderState`.
- **R-5 Slime interior render** - 1.21.1 baked the inner cube as a texture because the translucent shell depth-culled a live block render (a recurring manual-baker pain point). **Re-test whether 26.1's render pipeline makes a live inner-block render viable** - if so, the baker script dies.
- **R-6 GameTest** - registry `GameTestInstance`/`TestData` + self-documenting JSON `test_instance`, not an annotation-mimicking shim.
- **R-7 BE save/load** - `ValueInput`/`ValueOutput` typed/codec I/O, native.
- **R-8 Registration** - `registerItem`/`registerBlock` + `setId`, native.

---

## Evidence index + a correction

**The authoritative sources, in order:** (1) the **compiler** (`./gradlew compileJava` against the real classpath) - final word; (2) **NeoForge's own source** (`neoforge-26.1.2.76-sources.jar`) for NeoForge's classes (`Capabilities`, `FluidResource`, `ResourceHandler`, `SimpleEnergyHandler`) - reliable; these back R-1 and R-3.

**Do NOT trust the NFRT joined/intermediate jar (`sourcesAndCompiledWithNeoForge_*.jar`) for vanilla class names.** It is a pre-final-mapping pipeline stage and gave **false signals** (2026-06-29): it showed `ResourceLocation.java` and `BlockEntity.saveAdditional(CompoundTag)`, leading to a wrong "ResourceLocation retained / entity imports match / port is small" conclusion. The compiler then showed **3,676 errors** including ~1,270 `ResourceLocation` "cannot find symbol" - and NeoForge's own `Capabilities.java` imports `net.minecraft.resources.Identifier`. **So `ResourceLocation` -> `Identifier` IS real**, the GameTest registry rewrite IS real (938 errors in `PFGameTests`), and the upstream-primer research was substantially correct about the breakage. When questioning an assumption, verify against the compiler and NeoForge's own source - not the intermediate decompiled vanilla jar.

The R-1 (milk) and R-3 (capability) decisions are unaffected: both rest on NeoForge's own source and are forced regardless (the old `Capabilities.ItemHandler`/`EnergyStorage` holders are gone, confirmed by the compiler's `ItemHandler`/`FluidHandler` errors).
