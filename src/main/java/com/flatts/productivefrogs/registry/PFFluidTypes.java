package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * {@link FluidType} registrations for the Slime Milk fluid family.
 *
 * <p>NeoForge separates a fluid's "kind" (FluidType, registered here) from its
 * Source / Flowing instances (in {@link PFFluids}). FluidType carries the
 * physics-like properties (density, viscosity, light) and is what
 * {@link net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties} references
 * when wiring up the Source + Flowing pair.
 *
 * <p>Client-side properties (still texture, flowing texture, tint color) are
 * registered separately via {@code RegisterClientExtensionsEvent} in
 * {@link com.flatts.productivefrogs.client.PFClientEvents}, because they
 * depend on classes that aren't present on a dedicated server. In NF
 * 21.11.x the {@code initializeClient} method that previous versions used
 * was removed in favor of this event-based pattern.
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
 */
public final class PFFluidTypes {

    public static final DeferredRegister<FluidType> TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ProductiveFrogs.MOD_ID);

    public static final DeferredHolder<FluidType, FluidType> IRON_SLIME_MILK = TYPES.register(
        "iron_slime_milk",
        () -> new FluidType(milkProperties())
    );

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
