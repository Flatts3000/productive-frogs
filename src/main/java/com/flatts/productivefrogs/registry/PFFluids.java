package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.fluid.MimicMilkFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
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

    /**
     * The single Mimic Milk fluid (Equivalence lane, #253) - source + flowing. The
     * flowing form is registered for completeness but never manifests
     * ({@link MimicMilkFluid} refuses all spread), so Mimic Milk is source-only and
     * every block is a BE-backed source. NOT per-variant: the synthesized item
     * rides on the source block's BE, not the fluid object.
     */
    public static final DeferredHolder<Fluid, MimicMilkFluid.Source> MIMIC_MILK =
        FLUIDS.register("mimic_slime_milk", () -> new MimicMilkFluid.Source(mimicMilkProps()));
    public static final DeferredHolder<Fluid, MimicMilkFluid.Flowing> MIMIC_MILK_FLOWING =
        FLUIDS.register("mimic_slime_milk_flowing", () -> new MimicMilkFluid.Flowing(mimicMilkProps()));

    private static BaseFlowingFluid.Properties mimicMilkProps;

    /** Shared properties for both Mimic Milk forms (built once, lazily). */
    private static BaseFlowingFluid.Properties mimicMilkProps() {
        if (mimicMilkProps == null) {
            mimicMilkProps = new BaseFlowingFluid.Properties(
                PFFluidTypes.MIMIC_MILK_TYPE, MIMIC_MILK, MIMIC_MILK_FLOWING)
                .bucket(PFItems.MIMIC_MILK_BUCKET)
                .block(() -> (LiquidBlock) PFBlocks.MIMIC_MILK.get());
        }
        return mimicMilkProps;
    }

    private PFFluids() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }
}
