package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.fluid.SlimeMilkFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The single Source + Flowing Slime Milk fluid pair. Collapsed from the former
 * one-pair-per-variant model (see {@link PFFluidTypes} and
 * {@code docs/refactor_data_driven_variants.md}). Variant identity lives on the
 * bucket component / source BlockEntity, not on the fluid.
 *
 * <p>Flow tuning: {@code slopeFindDistance 4} + {@code levelDecreasePerBlock 2}
 * = lava-style 4-block reach instead of water's 8, keeping farm footprints small.
 */
public final class PFFluids {

    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(BuiltInRegistries.FLUID, ProductiveFrogs.MOD_ID);

    // Non-final: assigned once in the static initializer below. They can't be
    // final because the Properties lambdas capture them before assignment (a
    // blank-final read in a lambda is a definite-assignment error); the lambdas
    // are lazy, so they read the assigned value at fluid-build time.
    public static DeferredHolder<Fluid, SlimeMilkFluid.Source> SLIME_MILK_SOURCE;
    public static DeferredHolder<Fluid, SlimeMilkFluid.Flowing> SLIME_MILK_FLOWING;

    static {
        // The Properties needs Supplier<Fluid> for both Source and Flowing, but
        // neither holder exists yet here. The lambdas are lazy (invoked at
        // fluid-build time, after both fields are assigned below), so the
        // forward reference resolves. Same pattern the per-variant code used.
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
            PFFluidTypes.SLIME_MILK,
            () -> SLIME_MILK_SOURCE.get(),
            () -> SLIME_MILK_FLOWING.get()
        )
            .bucket(() -> PFItems.SLIME_MILK_BUCKET.get())
            .block(() -> (LiquidBlock) PFBlocks.SLIME_MILK_SOURCE.get())
            .slopeFindDistance(4)
            .levelDecreasePerBlock(2);

        // Custom subclasses (not the bare BaseFlowingFluid forms) so flowing milk
        // refuses to wash away frogspawn / Primed Frog Eggs and never displaces a
        // fluid source block - see SlimeMilkFluid and docs/known_issues.md.
        SLIME_MILK_SOURCE = FLUIDS.register("slime_milk", () -> new SlimeMilkFluid.Source(props));
        SLIME_MILK_FLOWING = FLUIDS.register("slime_milk_flowing", () -> new SlimeMilkFluid.Flowing(props));
    }

    private PFFluids() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }
}
