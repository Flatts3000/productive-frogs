package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Source + Flowing fluid registrations for Slime Milk variants. One
 * {@link BaseFlowingFluid.Source} + one {@link BaseFlowingFluid.Flowing} per
 * variant in {@link PFFluidTypes#VARIANTS}, totalling
 * {@code VARIANTS.size() * 2} fluid IDs.
 *
 * <p>Flow tuning picked for V1: {@code slopeFindDistance 4} +
 * {@code levelDecreasePerBlock 2} = lava-style 4-block reach instead of
 * water's 8. Keeps farm footprints small. Per design doc
 * {@code docs/farming.md} §Slime Milk.
 */
public final class PFFluids {

    public static final DeferredRegister<Fluid> FLUIDS =
        DeferredRegister.create(BuiltInRegistries.FLUID, ProductiveFrogs.MOD_ID);

    /** Per-variant Source + Flowing fluid pair. */
    public record Pair(
        DeferredHolder<Fluid, BaseFlowingFluid.Source> source,
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing
    ) {}

    /** Fluid pairs keyed by variant name. Iteration order matches {@link PFFluidTypes#VARIANTS}. */
    public static final Map<String, Pair> BY_VARIANT = buildFluids();

    /** Backwards-compatible aliases for J1 callers. New code should use {@link #BY_VARIANT}. */
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> IRON_SLIME_MILK_SOURCE =
        BY_VARIANT.get("iron").source();
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> IRON_SLIME_MILK_FLOWING =
        BY_VARIANT.get("iron").flowing();

    private static Map<String, Pair> buildFluids() {
        LinkedHashMap<String, Pair> map = new LinkedHashMap<>();
        for (String variant : PFFluidTypes.VARIANTS) {
            // Forward references: the Properties object needs Supplier<Fluid>
            // for both Source and Flowing, but neither holder exists yet at this
            // point in the loop. We materialize the holders inside the lambda
            // (via map.get) — by the time the lambda runs at registry-build
            // time, both holders have been added to the map.
            BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
                PFFluidTypes.BY_VARIANT.get(variant),
                () -> BY_VARIANT.get(variant).source().get(),
                () -> BY_VARIANT.get(variant).flowing().get()
            )
                .bucket(() -> PFItems.MILK_BUCKETS.get(variant).get())
                .block(() -> (LiquidBlock) PFBlocks.MILK_BLOCKS.get(variant).get())
                .slopeFindDistance(4)
                .levelDecreasePerBlock(2);

            DeferredHolder<Fluid, BaseFlowingFluid.Source> source = FLUIDS.register(
                variant + "_slime_milk",
                () -> new BaseFlowingFluid.Source(props)
            );
            DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing = FLUIDS.register(
                variant + "_slime_milk_flowing",
                () -> new BaseFlowingFluid.Flowing(props)
            );
            map.put(variant, new Pair(source, flowing));
        }
        return Collections.unmodifiableMap(map);
    }

    private PFFluids() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        FLUIDS.register(modEventBus);
    }
}
