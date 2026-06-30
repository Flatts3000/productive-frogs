package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Dynamic molten-metal fluids for the Crucible's wave-2 melt lane
 * ({@code docs/froglight_crucible.md}), registered at mod-init like the v1.8
 * per-variant Slime Milk ({@link PFVariantMilk}) but deliberately leaner: a
 * {@code FluidType} + source/flowing {@code Fluid} only - <b>no source block
 * (not placeable) and no bucket</b>; molten metal exists for the
 * tank -> pipe -> Casting Mold loop.
 *
 * <p><b>ATM interop is the design center.</b> AllTheOres 3.x mints its own
 * molten metals ({@code alltheores:molten_iron} etc., tagged
 * {@code c:molten_<metal>}), so PF defers to those wherever they exist:
 * <ul>
 *   <li>Metals whose variants are themselves ATO-gated (tin, lead, osmium,
 *       nickel, silver, zinc, aluminum, uranium) NEVER get a PF fluid - the
 *       variant only exists when ATO does, and the melt recipe outputs ATO's
 *       molten directly.</li>
 *   <li>Metals that exist without ATO (vanilla iron/copper/gold, Create's
 *       brass, Mekanism's steel) get a PF fluid ONLY when ATO is absent; with
 *       ATO present the melt recipes output ATO's molten and the PF fluid is
 *       not registered (no duplicate registry entries, no duplicate JEI rows).</li>
 *   <li>Metals ATO doesn't cover (refined obsidian, mythril, orichalcum) get a
 *       PF fluid whenever their provider mod is loaded.</li>
 * </ul>
 * Every PF molten fluid is tagged into the same {@code c:molten_<metal>} tags
 * ATO uses ({@code required: false} entries, shipped in our datapack), so
 * downstream machinery that keys on the common tags accepts either source -
 * and the Casting Mold's solidify recipes take the TAG, accepting ATO molten
 * too. The melt recipes pick the concrete output fluid with
 * {@code mod_loaded} / {@code not(mod_loaded)} conditions that mirror the
 * minting rules here, so recipe and registry can never disagree.
 *
 * <p>Like milk, the per-metal colour is applied at render time from the
 * variant's {@code primary_color} (one greyscale molten texture set, see
 * {@code PFClientEvents}); the metal id doubles as the variant id.
 */
public final class PFMoltenFluids {

    /**
     * The melt roster's PF-minted candidates: metal id (== variant id path) ->
     * minting rule. {@code atoCovered} metals skip minting when AllTheOres is
     * present; {@code providerModid} (nullable = vanilla) must be loaded for
     * the variant to exist at all.
     */
    private record Spec(String metal, boolean atoCovered, @Nullable String providerModid) {
    }

    private static final Spec[] SPECS = {
        new Spec("iron", true, null),
        new Spec("copper", true, null),
        new Spec("gold", true, null),
        new Spec("brass", true, "create"),
        new Spec("steel", true, "mekanism"),
        new Spec("refined_obsidian", false, "mekanism"),
        new Spec("mythril", false, "mythicmetals"),
        new Spec("orichalcum", false, "mythicmetals"),
    };

    private static final String ATO_MODID = "alltheores";

    private static final Map<Identifier, DeferredHolder<FluidType, FluidType>> TYPES = new LinkedHashMap<>();
    private static final Map<Identifier, DeferredHolder<Fluid, BaseFlowingFluid.Source>> SOURCES = new LinkedHashMap<>();
    private static final Map<Identifier, DeferredHolder<Fluid, BaseFlowingFluid.Flowing>> FLOWINGS = new LinkedHashMap<>();

    private static boolean bootstrapped = false;

    private PFMoltenFluids() {
        // utility class
    }

    /**
     * Mint the PF-side molten fluids this launch needs (see class doc for the
     * rules). Must run in the {@code ProductiveFrogs} constructor before the
     * FluidType/Fluid DeferredRegisters fire. Idempotent.
     */
    public static void bootstrap() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        boolean atoLoaded = isLoaded(ATO_MODID);
        for (Spec spec : SPECS) {
            if (spec.atoCovered() && atoLoaded) {
                continue;
            }
            if (spec.providerModid() != null && !isLoaded(spec.providerModid())) {
                continue;
            }
            registerMetal(spec.metal());
        }
        PFDebug.log(PFDebug.Area.REGISTRY, () -> "PFMoltenFluids: minted " + SOURCES.size()
            + " molten fluids (alltheores loaded: " + atoLoaded + ") " + SOURCES.keySet());
    }

    private static void registerMetal(String metal) {
        Identifier vid = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, metal);
        String base = "molten_" + metal;

        DeferredHolder<FluidType, FluidType> type =
            PFFluidTypes.TYPES.register(base, () -> new FluidType(PFFluidTypes.moltenProperties()));
        TYPES.put(vid, type);

        // No .block() (not placeable in v1.12) and no .bucket() - the fluid
        // moves by pipe and by the Casting Mold's pull, never by hand.
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
            type,
            () -> SOURCES.get(vid).get(),
            () -> FLOWINGS.get(vid).get());

        SOURCES.put(vid, PFFluids.FLUIDS.register(base, () -> new BaseFlowingFluid.Source(props)));
        FLOWINGS.put(vid, PFFluids.FLUIDS.register(base + "_flowing", () -> new BaseFlowingFluid.Flowing(props)));
    }

    private static boolean isLoaded(String modid) {
        try {
            return ModList.get().isLoaded(modid);
        } catch (Exception e) {
            // ModList not ready (bare unit-test JVM) - mint nothing extra.
            return false;
        }
    }

    // ---- accessors (variant-id keyed, like PFVariantMilk) ----

    /** Variant ids that received a PF molten fluid this launch. */
    public static Set<Identifier> registeredMetals() {
        return Collections.unmodifiableSet(SOURCES.keySet());
    }

    @Nullable
    public static Fluid sourceFluid(Identifier variantId) {
        DeferredHolder<Fluid, BaseFlowingFluid.Source> h = SOURCES.get(variantId);
        return h == null ? null : h.get();
    }

    /** The metal's flowing fluid (pairs with {@link #sourceFluid}); used by the client FluidModel registration. */
    @Nullable
    public static Fluid flowingFluid(Identifier variantId) {
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> h = FLOWINGS.get(variantId);
        return h == null ? null : h.get();
    }

    @Nullable
    public static FluidType fluidType(Identifier variantId) {
        DeferredHolder<FluidType, FluidType> h = TYPES.get(variantId);
        return h == null ? null : h.get();
    }
}
