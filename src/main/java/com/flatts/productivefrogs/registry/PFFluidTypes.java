package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * {@link FluidType} registrations for the Slime Milk fluid family. One
 * FluidType per shipped variant — same physics properties, distinct texture
 * (the texture binds to the FluidType via the client extensions event in
 * {@link com.flatts.productivefrogs.client.PFClientEvents}).
 *
 * <p>NeoForge separates a fluid's "kind" (FluidType, registered here) from its
 * Source / Flowing instances (in {@link PFFluids}). FluidType carries the
 * physics-like properties (density, viscosity, light) and is what
 * {@link net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties} references
 * when wiring up the Source + Flowing pair.
 *
 * <p>Properties picked to give milk a "slower than water, doesn't flow forever"
 * feel without making it act like lava:
 * <ul>
 *   <li>{@code density 1500} — heavier than water (1000), lighter than lava
 *       (3000). Players sink slowly.</li>
 *   <li>{@code viscosity 2000} — slower flow rate than water (1000), faster
 *       than lava (6000).</li>
 *   <li>{@code canSwim true} + {@code canDrown true} — players can swim in it
 *       but air supply still depletes, same as water.</li>
 *   <li>{@code lightLevel 0} — milk isn't a light source.</li>
 * </ul>
 *
 * <p>{@link #VARIANTS} mirrors the 12 resource variants in
 * {@link PFItems#RESOURCE_SLIME_SPAWN_EGGS} plus two specials: {@code vanilla}
 * (from milking a vanilla green slime) and {@code magma} (from magma cubes).
 * Reordering or renaming entries here cascades to {@link PFFluids},
 * {@link PFBlocks}, {@link PFItems}, and the lang file — keep them in sync.
 */
public final class PFFluidTypes {

    public static final DeferredRegister<FluidType> TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ProductiveFrogs.MOD_ID);

    /**
     * Canonical variant order. Used by every J-series registry that needs to
     * iterate the milk family. Iteration order is preserved for stable creative
     * tab + JEI grouping.
     */
    public static final List<String> VARIANTS = List.of(
        "iron", "copper", "gold",
        "redstone", "lapis", "coal",
        "diamond", "emerald",
        "prismarine", "sponge",
        "magma_cream", "ender_pearl",
        "vanilla", "magma"
    );

    /** FluidType holders keyed by variant name. */
    public static final Map<String, DeferredHolder<FluidType, FluidType>> BY_VARIANT = buildTypes();

    /**
     * Backwards-compatible alias for tests + J1 code that referenced the iron
     * variant directly. New code should prefer {@link #BY_VARIANT} to support
     * iteration across the whole family.
     */
    public static final DeferredHolder<FluidType, FluidType> IRON_SLIME_MILK = BY_VARIANT.get("iron");

    private static Map<String, DeferredHolder<FluidType, FluidType>> buildTypes() {
        LinkedHashMap<String, DeferredHolder<FluidType, FluidType>> map = new LinkedHashMap<>();
        for (String variant : VARIANTS) {
            map.put(variant, TYPES.register(
                variant + "_slime_milk",
                () -> new FluidType(milkProperties())
            ));
        }
        return Collections.unmodifiableMap(map);
    }

    private static FluidType.Properties milkProperties() {
        return FluidType.Properties.create()
            .density(1500)
            .viscosity(2000)
            .lightLevel(0)
            .canSwim(true)
            .canDrown(true);
    }

    private PFFluidTypes() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
