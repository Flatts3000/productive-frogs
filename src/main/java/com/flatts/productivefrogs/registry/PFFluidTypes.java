package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Slime Milk {@link FluidType} registration. As of the 26.1 re-implementation (R-1)
 * there is a SINGLE {@code slime_milk} FluidType (plus the Mimic Milk type): the
 * variant rides the {@code SLIME_VARIANT} component on the {@code FluidResource} /
 * bucket, which the 26.1 transfer API preserves through automation, so a distinct
 * {@code Fluid}/type per variant (v1.8) is no longer needed. This class owns the
 * shared {@link DeferredRegister} and the {@link #milkProperties() properties} both
 * milk types share. See {@code docs/port_mc_26_1_reimplementation.md} (R-1).
 *
 * <p>Colour is applied at render time: the placed source's per-instance colour is
 * resolved from its BE variant (see
 * {@link com.flatts.productivefrogs.client.PFClientEvents}), so one greyscale texture
 * set serves every variant.
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

    /**
     * The single Slime Milk fluid type (26.1 R-1). There is ONE type; the variant
     * rides as the {@code SLIME_VARIANT} component on the {@code FluidResource} /
     * bucket, and the placed source's per-instance colour is resolved at render
     * time from its BE (see {@code PFClientEvents}). Replaces the v1.8 per-variant
     * types (the 26.1 transfer API preserves the component through automation, so a
     * distinct {@code Fluid} per variant is no longer needed).
     */
    public static final DeferredHolder<FluidType, FluidType> SLIME_MILK_TYPE =
        TYPES.register("slime_milk", () -> new FluidType(milkProperties()));

    /**
     * The single Mimic Milk fluid type (Equivalence lane, #253). Like Slime Milk,
     * there is ONE; its per-instance colour is resolved at render time from the
     * source block's BE (see {@code PFClientEvents}). Shares the milk feel
     * ({@link #milkProperties()}).
     */
    public static final DeferredHolder<FluidType, FluidType> MIMIC_MILK_TYPE =
        TYPES.register("mimic_slime_milk", () -> new FluidType(milkProperties()));

    /**
     * Liquid Experience (#281 Phase 2) - the {@code c:experience} XP fluid. One
     * plain type: XP is fungible, so unlike milk there is no per-instance colour
     * or component to resolve (the render tint is a constant XP green, see
     * {@code PFClientEvents}). Glows faintly like the orbs it bottles.
     */
    public static final DeferredHolder<FluidType, FluidType> LIQUID_EXPERIENCE_TYPE =
        TYPES.register("liquid_experience", () -> new FluidType(experienceProperties()));

    /**
     * Mob Slurry (#281 Phase 3) - the mob-side twin of Slime Milk on the R-1
     * model: ONE type; the mob rides the {@code SLURRIED_ENTITY} component.
     * Shares the milk feel; like Liquid Experience it has no block form, so
     * the in-world properties are academic.
     */
    public static final DeferredHolder<FluidType, FluidType> MOB_SLURRY_TYPE =
        TYPES.register("mob_slurry", () -> new FluidType(milkProperties()));

    static FluidType.Properties milkProperties() {
        return FluidType.Properties.create()
            .density(1500)
            .viscosity(2000)
            .lightLevel(0)
            .canSwim(true)
            .canDrown(false);
    }

    /**
     * Liquid Experience: water-ish weight with a slight syrup drag, glowing like
     * the orbs it bottles. Not placeable (no {@code .block()} on the fluid), so
     * the swim/drown flags never matter in-world; set safe values anyway for any
     * mod that inspects the type.
     */
    static FluidType.Properties experienceProperties() {
        return FluidType.Properties.create()
            .density(1200)
            .viscosity(1500)
            .lightLevel(10)
            .canSwim(true)
            .canDrown(false);
    }

    /**
     * Molten metal (v1.12 Crucible melt lane): lava-like - dense, slow,
     * glowing. Not placeable, so the swim/drown flags never matter in-world;
     * set lava-ish anyway for any mod that inspects the type.
     */
    static FluidType.Properties moltenProperties() {
        return FluidType.Properties.create()
            .density(3000)
            .viscosity(6000)
            .temperature(1300)
            .lightLevel(10)
            .canSwim(false)
            .canDrown(false);
    }

    private PFFluidTypes() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
