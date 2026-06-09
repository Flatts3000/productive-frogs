# Automatable Slime Milk (per-variant fluids)

Shipped in v1.8.0. This doc covers why Slime Milk is per-variant, how the
registration works, and the one current limitation for pack authors.

## The problem it solves

Slime Milk used to be a single `slime_milk` fluid with the variant carried on the
bucket's data component and the source block's BlockEntity. Tank/pipe automation
mods identify a fluid by its **registry object**, never by our component:

- Just Dire Things Fluid Collector reads `FluidResource.of(liquidBlock.fluid)` then
  deletes the block - the BlockEntity (and its variant) is never read.
- Just Dire Things Fluid Placer places `fluid.defaultFluidState().createLegacyBlock()`
  via a raw `setBlock` - the bucket placement hook that stamps the variant is never
  called.
- NeoForge's own `FluidUtil.tryPlaceFluid` does the same through `BlockWrapper`.

So with one fluid, every variant looked identical to a pipe, and automated milk came
out variant-less and inert. The only fix is for the variant to **be** a distinct
`Fluid`. See `docs/refactor_data_driven_variants.md` for the history of the earlier
single-fluid collapse and why this reverses part of it.

## How it works

`PFVariantMilk` mints, per variant, a `FluidType` + source/flowing `Fluid` + source
`LiquidBlock` + `BucketItem`, named `<variant>_slime_milk` / `_flowing` / `_bucket`.
Each bucket wraps its own variant fluid, so vanilla `FluidBucketWrapper` round-trips
it through any `IFluidHandler` with no custom code.

These are registered at **mod construction** (`ProductiveFrogs` constructor calls
`PFVariantMilk.bootstrap(VariantFluidDiscovery.discover())`), because
`BuiltInRegistries.FLUID` freezes right after mod load. A fluid can only exist for a
variant **known at that point** - which is why the variant ids come from a bundled
classpath index (`productivefrogs/variants_index.json`), kept in sync with the
`slime_variant` registry by `scripts/generate_variants_index.py` and enforced by
`VariantIndexTest`.

The source block carries its variant baked in at registration (authoritative over
the BlockEntity mirror), so a source placed by a tank mod's raw `setBlock` still
spawns and tints the right variant. The BlockEntity still holds the spawn economy
and catalyst upgrades; `onPlace` seeds it from the block's variant.

Cross-mod variants gated by `neoforge:mod_loaded` are skipped at discovery when their
source mod is absent, so an unused variant does not mint a dead fluid.

## Acceptance (what now works)

A Just Dire Things Fluid Collector (or any `IFluidHandler` setup) can pick up a
variant Slime Milk source, move it through pipes/tanks, and a Fluid Placer can place
it back, with the placed source spawning that variant's slimes. The variant survives
the whole loop because it is the fluid's identity.

## Limitations

- **No milk for unknown variants.** A variant that reaches the `slime_variant`
  content registry without being in the mod-init index (for example a variant added
  to a live world's datapack) gets no per-variant fluid and therefore no milk - the
  Slime Milker fails closed for it. This is the deliberate single-path design.
- **Catalyst buffs are not preserved through a tank.** Buffs ride the bucket through
  a re-bucketing (`pickupBlock` stamps them, `checkExtraContent` restores them), but a
  source placed from a tank gets the default budget. Routing through a tank trades
  buffs for automation.
- **Hard world break.** The old `slime_milk` fluid/block/bucket ids were removed;
  pre-1.8 placed milk and inventory buckets orphan. No migration.
- **A config-disabled variant (#203) still keeps its milk fluid registered.** The
  per-variant fluids are minted at mod-init, before the COMMON config loads, so the
  `variants.disabledVariants` / `disabledCategories` / `bossVariantsEnabled` toggles
  cannot suppress the fluid object itself. The fluid stays inert and unobtainable -
  the disabled variant is unprimable, undiscoverable, and hidden from JEI + the
  creative tab, so no Milker/Churn recipe or milk bucket for it is reachable. This
  mirrors the unknown-variant rule above (the fluid registry is a mod-init artifact,
  the disable toggle is a world-reload one). See `docs/modpack_integration.md`.

## Adding your own automatable variant (pack authors)

The variants the mod ships are all automatable out of the box. Adding a brand-new
**pack-authored** automatable variant (one not shipped by the mod) is a planned
follow-up: it needs the variant declared somewhere readable at mod-init so its fluid
can be minted before the registry freezes. The intended home is a PF-owned config
folder (`config/productivefrogs/variants/`) that the mod reads at mod-init for fluid
names AND registers as a datapack so the same file feeds the `slime_variant` content
registry. That datapack side needs a custom `PackResources` to expose the flat config
files under the `slime_variant` path; until it lands, `VariantFluidDiscovery` reads
only the bundled index, so the config folder is not yet scanned (it would otherwise
mint a fluid with no content). Pack-added variants still get everything except
pipe-automatable milk in the meantime.
