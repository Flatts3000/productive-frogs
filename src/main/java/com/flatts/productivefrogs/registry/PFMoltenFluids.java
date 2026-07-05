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
 * ({@code docs/froglight_crucible.md}), registered at mod-init like the former v1.8
 * per-variant Slime Milk ({@code PFVariantMilk}, since collapsed in R-1) but
 * deliberately leaner: a
 * {@code FluidType} + source/flowing {@code Fluid} only - <b>no source block
 * (not placeable) and no bucket</b>; molten metal exists for the
 * tank -> pipe -> Casting Mold loop.
 *
 * <p><b>PF mints every molten fluid itself.</b> The 1.21.1 line deferred to
 * AllTheOres' molten metals wherever ATO was loaded, but ATO 4.x (26.1)
 * dropped its fluid system entirely - no molten fluids, no buckets, no
 * {@code c:molten_*} tags (verified against alltheores-4.0.4.jar, 2026-07-04) -
 * so the defer would leave a pack with ATO installed and NO molten iron at
 * all (the ATO-gated recipe referenced a fluid that no longer exists while
 * the PF fallback was conditioned off). On the 2.0 line the rule is simply:
 * a metal's PF fluid is minted whenever its variant can exist -
 * unconditionally for the vanilla metals (iron/copper/gold), gated on the
 * provider mod for the wave-2 metals whose providers have no 26.1 port yet.
 *
 * <p>Every PF molten fluid is tagged {@code c:molten_<metal>} in our datapack
 * ({@code required: false} entries), and the Casting Mold's solidify recipes
 * take the TAG - so if a partner mod ever reintroduces its own molten
 * (Productive Metalworks mints tag-compatible molten on 26.1), packs can
 * accept either source without touching PF recipes.
 *
 * <p>Like milk, the per-metal colour is applied at render time from the
 * variant's {@code primary_color} (one greyscale molten texture set, see
 * {@code PFClientEvents}); the metal id doubles as the variant id.
 */
public final class PFMoltenFluids {

    /**
     * The melt roster's PF-minted candidates: metal id (== variant id path) ->
     * minting rule. {@code providerModid} (nullable = vanilla) must be loaded
     * for the variant to exist at all.
     */
    private record Spec(String metal, @Nullable String providerModid) {
    }

    private static final Spec[] SPECS = {
        new Spec("iron", null),
        new Spec("copper", null),
        new Spec("gold", null),
        // wave 2: providers with no 26.1 port yet - these mint the day the
        // partner mod ports (and its variant returns), no PF code change needed.
        new Spec("brass", "create"),
        new Spec("steel", "mekanism"),
        new Spec("refined_obsidian", "mekanism"),
        new Spec("mythril", "mythicmetals"),
        new Spec("orichalcum", "mythicmetals"),
    };

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
        for (Spec spec : SPECS) {
            if (spec.providerModid() != null && !isLoaded(spec.providerModid())) {
                continue;
            }
            registerMetal(spec.metal());
        }
        PFDebug.log(PFDebug.Area.REGISTRY, () -> "PFMoltenFluids: minted " + SOURCES.size()
            + " molten fluids " + SOURCES.keySet());
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
