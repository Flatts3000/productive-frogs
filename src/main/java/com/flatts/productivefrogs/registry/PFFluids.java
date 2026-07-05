package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.fluid.LiquidExperienceFluid;
import com.flatts.productivefrogs.content.fluid.MimicMilkFluid;
import com.flatts.productivefrogs.content.fluid.MobSlurryFluid;
import com.flatts.productivefrogs.content.fluid.SlimeMilkFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Fluid {@link DeferredRegister}. As of the 26.1 re-implementation (R-1) there is a
 * <b>single</b> {@code slime_milk} fluid (source + flowing): the variant it carries
 * rides as the {@code SLIME_VARIANT} data component on the {@code FluidResource} /
 * bucket, which the 26.1 transfer API ({@code ResourceHandler<FluidResource>})
 * preserves through tanks/pipes. This collapses the v1.8 per-variant fluids
 * ({@code PFVariantMilk}, deleted) and brings Slime Milk to the same shape as the
 * Mimic Milk (EE) lane. See {@code docs/port_mc_26_1_reimplementation.md} (R-1).
 *
 * <p>Both milk fluids are <b>source-only</b> ({@code Flowing} never manifests): the
 * fluid classes refuse all spread, so every milk block is a BE-backed source whose
 * tint + spawn variant read straight off the BE.
 */
public final class PFFluids {

    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(BuiltInRegistries.FLUID, ProductiveFrogs.MOD_ID);

    /**
     * The single Slime Milk fluid (26.1 R-1) - source + flowing. The flowing form
     * is registered for completeness but never manifests ({@link SlimeMilkFluid}
     * refuses all spread), so Slime Milk is source-only and every block is a
     * BE-backed source. NOT per-variant: the variant rides the {@code SLIME_VARIANT}
     * component on the bucket / {@code FluidResource}, not the fluid object.
     */
    public static final DeferredHolder<Fluid, SlimeMilkFluid.Source> SLIME_MILK =
        FLUIDS.register("slime_milk", () -> new SlimeMilkFluid.Source(slimeMilkProps()));
    public static final DeferredHolder<Fluid, SlimeMilkFluid.Flowing> SLIME_MILK_FLOWING =
        FLUIDS.register("slime_milk_flowing", () -> new SlimeMilkFluid.Flowing(slimeMilkProps()));

    private static BaseFlowingFluid.Properties slimeMilkProps;

    /** Shared properties for both Slime Milk forms (built once, lazily). */
    private static BaseFlowingFluid.Properties slimeMilkProps() {
        if (slimeMilkProps == null) {
            slimeMilkProps = new BaseFlowingFluid.Properties(
                PFFluidTypes.SLIME_MILK_TYPE, SLIME_MILK, SLIME_MILK_FLOWING)
                .bucket(PFItems.SLIME_MILK_BUCKET)
                .block(() -> (LiquidBlock) PFBlocks.SLIME_MILK_SOURCE.get());
        }
        return slimeMilkProps;
    }

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

    /**
     * Liquid Experience (#281 Phase 2) - source + flowing, {@code c:experience}
     * at 20 mB/point ({@link LiquidExperienceFluid#MB_PER_POINT}). The simplest
     * fluid in the mod: no components (XP is fungible), no source block (never
     * placeable - it lives in tanks/pipes/buckets), no BE. The flowing form is
     * registered for completeness but never manifests (refuses all spread).
     */
    public static final DeferredHolder<Fluid, LiquidExperienceFluid.Source> LIQUID_EXPERIENCE =
        FLUIDS.register("liquid_experience", () -> new LiquidExperienceFluid.Source(liquidExperienceProps()));
    public static final DeferredHolder<Fluid, LiquidExperienceFluid.Flowing> LIQUID_EXPERIENCE_FLOWING =
        FLUIDS.register("liquid_experience_flowing", () -> new LiquidExperienceFluid.Flowing(liquidExperienceProps()));

    private static BaseFlowingFluid.Properties liquidExperienceProps;

    /** Shared properties for both Liquid Experience forms (built once, lazily). No {@code .block()} - not placeable. */
    private static BaseFlowingFluid.Properties liquidExperienceProps() {
        if (liquidExperienceProps == null) {
            liquidExperienceProps = new BaseFlowingFluid.Properties(
                PFFluidTypes.LIQUID_EXPERIENCE_TYPE, LIQUID_EXPERIENCE, LIQUID_EXPERIENCE_FLOWING)
                .bucket(PFItems.LIQUID_EXPERIENCE_BUCKET);
        }
        return liquidExperienceProps;
    }

    /**
     * Mob Slurry (#281 Phase 3) - source + flowing, both refusing all spread,
     * with NO block form ("the slurry never becomes a world fluid"): it lives
     * in buckets, tanks/pipes, and inside the Basin. The mob rides the
     * {@code SLURRIED_ENTITY} component on the bucket / {@code FluidResource}.
     */
    public static final DeferredHolder<Fluid, MobSlurryFluid.Source> MOB_SLURRY =
        FLUIDS.register("mob_slurry", () -> new MobSlurryFluid.Source(mobSlurryProps()));
    public static final DeferredHolder<Fluid, MobSlurryFluid.Flowing> MOB_SLURRY_FLOWING =
        FLUIDS.register("mob_slurry_flowing", () -> new MobSlurryFluid.Flowing(mobSlurryProps()));

    private static BaseFlowingFluid.Properties mobSlurryProps;

    /** Shared properties for both Mob Slurry forms (built once, lazily). No {@code .block()} - not placeable. */
    private static BaseFlowingFluid.Properties mobSlurryProps() {
        if (mobSlurryProps == null) {
            mobSlurryProps = new BaseFlowingFluid.Properties(
                PFFluidTypes.MOB_SLURRY_TYPE, MOB_SLURRY, MOB_SLURRY_FLOWING)
                .bucket(PFItems.MOB_SLURRY_BUCKET);
        }
        return mobSlurryProps;
    }

    private PFFluids() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }
}
