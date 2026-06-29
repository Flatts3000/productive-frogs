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
- **Free-flowing milk spread across the world** has no BE and no per-pos component -> can't resolve its variant at the flowing pos. Options:
  1. **Recommended:** reconsider whether milk needs to be a freely-spreading world fluid at all. Its roles are bucket-transport, piped-automation, and placed-source-spawning - none require vanilla-style spreading pools. Modeling milk as a `FluidResource` (automation) + the custom source block (spawning), with minimal/no world spread, **removes the wrinkle entirely**. (Gameplay decision: does milk still pool? Flag for maintainer.)
  2. Accept base-hue flowing milk (minor cosmetic regression; flowing milk is transient).
  3. Client walk-back from the flowing pos to the nearest source BE (the pre-v1.8 approach) - works, but reintroduces the walk-back per-variant fluids removed.

### 2.0 vs 2.x note

For **2.0 (standalone, no cross-mod fluid mods in scope)** the collapse is unambiguously correct - only PF's own machines touch milk, and they read the component/BE. When **integrations return in 2.x**, third-party fluid mods *on 26.1* use the same `ResourceHandler<FluidResource>` API and preserve the component, so the single-fluid design holds for them too (any mod still on a pre-transfer-rework API is the exception, not the rule, on 26.1). The v1.8 reason to expand no longer applies on the modern line.

---

## R-2..R-n: queued reconsiderations (not yet investigated)

Each gets the same treatment (assumption -> verified 26.1 capability -> decision) as its phase is reached:

- **R-2 Item tints** - legacy `RegisterColorHandlersEvent.Item` -> declarative JSON `ItemTintSource` (+ datagen + completeness gate). Dynamic component-read confirmed first-class.
- **R-3 Capability / inventory model** - `Capabilities.Item.BLOCK` is `ResourceHandler<ItemResource>` (verified). Adopt the transactional model natively; question whether `ItemResource` (item+components) simplifies how variant-stamped items sit in appliance slots.
- **R-4 GUI** - delete the `PFContainerScreen` `renderTooltip` workaround (new flow handles tooltips); adopt `RenderPipelines` + two-phase `GuiRenderState`.
- **R-5 Slime interior render** - 1.21.1 baked the inner cube as a texture because the translucent shell depth-culled a live block render (a recurring manual-baker pain point). **Re-test whether 26.1's render pipeline makes a live inner-block render viable** - if so, the baker script dies.
- **R-6 GameTest** - registry `GameTestInstance`/`TestData` + self-documenting JSON `test_instance`, not an annotation-mimicking shim.
- **R-7 BE save/load** - `ValueInput`/`ValueOutput` typed/codec I/O, native.
- **R-8 Registration** - `registerItem`/`registerBlock` + `setId`, native.

---

## Evidence index

All API claims verified against the locally decompiled `neoforge-26.1.2.76-sources.jar` and the joined NeoForM runtime sources (gradle caches), not the upstream-primer research (which mispredicted the `ResourceLocation`->`Identifier` rename and the entity package moves - the real 26.1 NeoForge mappings retain `ResourceLocation` and PF's entity imports already match). When an assumption is questioned, cite the actual 26.1 source file, as R-1 does.
