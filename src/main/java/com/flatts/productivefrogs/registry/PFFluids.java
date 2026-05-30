package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Fluid {@link DeferredRegister}. As of v1.8 there is no single generic
 * {@code slime_milk} fluid: every variant gets its own source + flowing fluid,
 * minted dynamically at mod-init by {@link PFVariantMilk} (registered into this
 * same register). A variant's milk being a distinct {@code Fluid} is what lets
 * tank/pipe mods preserve the variant through automation - they key on the Fluid
 * registry object, never our data component (see
 * {@code docs/refactor_data_driven_variants.md} and {@code docs/automated_milk_variants.md}).
 *
 * <p>Per-variant fluids can only exist for variants known before this register
 * freezes at end of mod construction, so they cover PF's shipped variants plus any
 * declared in {@code config/productivefrogs/variants/}. Flow tuning lives in
 * {@link PFVariantMilk} (lava-style 4-block reach, matching the old single fluid).
 */
public final class PFFluids {

    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(BuiltInRegistries.FLUID, ProductiveFrogs.MOD_ID);

    private PFFluids() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }
}
