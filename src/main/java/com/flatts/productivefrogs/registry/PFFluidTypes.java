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
 * <p>{@link #VARIANTS} mirrors the shipped resource variants in the
 * {@code slime_variant} datapack registry (11 from v1.0 + 22 from v1.1) plus
 * two specials: {@code vanilla}
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
        // v1.0 variants
        "iron", "copper", "gold",
        "redstone", "lapis", "coal",
        "diamond", "emerald",
        "prismarine", "sponge",
        "ender_pearl",
        // v1.1 variants (vanilla resource coverage across the six species)
        "bone", "gunpowder", "clay_ball", "rotten_flesh", "string",
        "leather", "feather",
        "glow_ink_sac", "obsidian", "echo_shard",
        "amethyst",
        "ink_sac", "prismarine_crystals",
        "netherite_scrap", "glowstone_dust", "soul_sand", "soul_soil",
        "netherrack", "blaze", "quartz",
        "chorus_fruit", "shulker_shell",
        // specials (not in the slime_variant registry; produced by milking
        // vanilla green slimes / magma cubes directly)
        "vanilla", "magma"
    );

    /** FluidType holders keyed by variant name. */
    public static final Map<String, DeferredHolder<FluidType, FluidType>> BY_VARIANT = buildTypes();

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
