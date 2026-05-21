package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Source + Flowing fluid registrations for Slime Milk variants. Uses
 * {@link BaseFlowingFluid} (NeoForge helper) which handles the standard
 * FlowingFluid boilerplate — we only need to supply the FluidType + the
 * Source / Flowing / LiquidBlock / Bucket DeferredHolders.
 *
 * <p>Flow tuning picked for V1: {@code slopeFindDistance 4} +
 * {@code levelDecreasePerBlock 2} = lava-style 4-block reach instead of
 * water's 8. Keeps farm footprints small without making the fluid feel
 * unusable. Per design doc {@code docs/farming.md} §Slime Milk.
 *
 * <p>This PR (J1) ships only the iron variant to validate the registration
 * stack end-to-end. J2 expands to all 14 variants (vanilla + magma +
 * the 12 resource variants).
 */
public final class PFFluids {

    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(BuiltInRegistries.FLUID, ProductiveFrogs.MOD_ID);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> IRON_SLIME_MILK_SOURCE;
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> IRON_SLIME_MILK_FLOWING;

    static {
        // BaseFlowingFluid.Properties needs forward references to the Source +
        // Flowing holders that don't exist yet, plus the bucket + block. The
        // Supplier indirection means everything resolves lazily at registry-
        // build time, after all DeferredRegisters have run. Same forward-ref
        // dance the bucket items use for EntityType lookups (see PFItems).
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
            PFFluidTypes.IRON_SLIME_MILK,
            () -> PFFluids.IRON_SLIME_MILK_SOURCE.get(),
            () -> PFFluids.IRON_SLIME_MILK_FLOWING.get()
        )
            .bucket(() -> PFItems.IRON_SLIME_MILK_BUCKET.get())
            .block(() -> (LiquidBlock) PFBlocks.IRON_SLIME_MILK.get())
            .slopeFindDistance(4)
            .levelDecreasePerBlock(2);

        IRON_SLIME_MILK_SOURCE = FLUIDS.register(
            "iron_slime_milk",
            () -> new BaseFlowingFluid.Source(props)
        );
        IRON_SLIME_MILK_FLOWING = FLUIDS.register(
            "iron_slime_milk_flowing",
            () -> new BaseFlowingFluid.Flowing(props)
        );
    }

    private PFFluids() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }
}
