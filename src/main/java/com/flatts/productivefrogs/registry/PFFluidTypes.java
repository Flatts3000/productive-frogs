package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The single Slime Milk {@link FluidType}. Collapsed from the former
 * one-FluidType-per-variant model: variant identity now rides on the Slime Milk
 * bucket's {@code SLIME_VARIANT} component and on the source block's
 * {@link com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity},
 * not on a distinct fluid registration. This is what lets a datapack-added
 * variant get milk with no Java edit - fluids register at mod construction,
 * before any datapack loads, so a per-variant fluid could never be added by a
 * datapack (see {@code docs/refactor_data_driven_variants.md} > Decision).
 *
 * <p>Per-variant colour is applied at render time via the position-aware
 * {@code getTintColor} in {@link com.flatts.productivefrogs.client.PFClientEvents}
 * (reads the source BE's variant), so one greyscale texture serves every variant.
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

    /** The one Slime Milk FluidType. */
    public static final DeferredHolder<FluidType, FluidType> SLIME_MILK =
        TYPES.register("slime_milk", () -> new FluidType(milkProperties()));

    private static FluidType.Properties milkProperties() {
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
