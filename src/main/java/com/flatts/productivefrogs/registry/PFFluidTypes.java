package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Slime Milk {@link FluidType} registration. As of v1.8 each variant gets its own
 * FluidType, minted dynamically by {@link PFVariantMilk} (so a variant's milk is a
 * distinct {@code Fluid} that tank/pipe mods can preserve through automation - see
 * {@code docs/automated_milk_variants.md}). This class owns the shared
 * {@link DeferredRegister} and the {@link #milkProperties() properties} every
 * per-variant type shares.
 *
 * <p>Per-variant colour is applied at render time: each per-variant FluidType's
 * client extension knows its own variant and reads the colour from the
 * {@code slime_variant} registry (see
 * {@link com.flatts.productivefrogs.client.PFClientEvents}), so one greyscale
 * texture set serves every variant.
 *
 * <p>Properties give milk a "slower than water, doesn't flow forever" feel:
 * density 1500 (sinks slowly), viscosity 2000 (slower than water), swimmable but
 * <b>not drownable</b>, not a light source. Milk is poured into shallow
 * production pools that frogs work over, so it must be safe to stand in - frogs
 * (and players) take no air-loss / drowning damage in it (docs/known_issues.md).
 */
public final class PFFluidTypes {

    public static final DeferredRegister<FluidType> TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ProductiveFrogs.MOD_ID);

    static FluidType.Properties milkProperties() {
        return FluidType.Properties.create()
            .density(1500)
            .viscosity(2000)
            .lightLevel(0)
            .canSwim(true)
            .canDrown(false);
    }

    private PFFluidTypes() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
