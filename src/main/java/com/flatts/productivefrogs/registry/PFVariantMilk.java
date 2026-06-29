package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.fluid.SlimeMilkFluid;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

/**
 * Dynamic per-variant Slime Milk registration. For each variant id discovered at
 * mod-init by {@link com.flatts.productivefrogs.setup.VariantFluidDiscovery}, this
 * mints a full fluid stack - {@code FluidType} + source/flowing {@code Fluid} +
 * source {@link SlimeMilkSourceBlock} + {@link BucketItem} - so the variant *is* a
 * distinct {@code Fluid} and tank/pipe mods (JDT, Mekanism, Pipez) preserve it
 * through automation (they key on the Fluid registry object, never our component).
 *
 * <p>{@link #bootstrap} must run inside the {@code ProductiveFrogs} constructor,
 * before the {@code DeferredRegister}s fire on the mod bus. Entries are added to
 * the existing {@link PFFluidTypes#TYPES} / {@link PFFluids#FLUIDS} /
 * {@link PFBlocks#BLOCKS} / {@link PFItems#ITEMS} registers, so they ship through
 * the same registration pass as the static content. Registry names are
 * {@code <variant>_slime_milk} (fluid + block), {@code <variant>_slime_milk_flowing},
 * and {@code <variant>_slime_milk_bucket}.
 *
 * <p>The per-variant bucket's {@code content} is its own source fluid, so vanilla
 * {@code FluidBucketWrapper} drain/fill round-trips it correctly with no custom
 * {@code IFluidHandlerItem} - the pre-v1.1 model, which was vanilla-clean.
 */
public final class PFVariantMilk {

    private static final Map<Identifier, DeferredHolder<FluidType, FluidType>> TYPES = new LinkedHashMap<>();
    private static final Map<Identifier, DeferredHolder<Fluid, SlimeMilkFluid.Source>> SOURCES = new LinkedHashMap<>();
    private static final Map<Identifier, DeferredHolder<Fluid, SlimeMilkFluid.Flowing>> FLOWINGS = new LinkedHashMap<>();
    private static final Map<Identifier, DeferredBlock<SlimeMilkSourceBlock>> BLOCKS = new LinkedHashMap<>();
    private static final Map<Identifier, DeferredItem<SlimeMilkBucketItem>> BUCKETS = new LinkedHashMap<>();

    private static boolean bootstrapped = false;

    private PFVariantMilk() {
        // utility class
    }

    /**
     * Register a per-variant fluid stack for every discovered id. Idempotent: a
     * second call is a no-op (guards against double-registration if the constructor
     * is somehow re-entered).
     */
    public static void bootstrap(Set<Identifier> variantIds) {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        for (Identifier id : variantIds) {
            registerVariant(id);
        }
        PFDebug.log(PFDebug.Area.REGISTRY, () -> "PFVariantMilk: registered " + SOURCES.size() + " per-variant milk fluids");
    }

    private static void registerVariant(Identifier vid) {
        String base = vid.getPath() + "_slime_milk";

        DeferredHolder<FluidType, FluidType> type =
            PFFluidTypes.TYPES.register(base, () -> new FluidType(PFFluidTypes.milkProperties()));
        TYPES.put(vid, type);

        // Forward refs resolve lazily through the maps (populated below before any
        // .get() runs at use time), mirroring PFFluids' single-fluid pattern.
        BaseFlowingFluid.Properties props = new BaseFlowingFluid.Properties(
            type,
            () -> SOURCES.get(vid).get(),
            () -> FLOWINGS.get(vid).get())
            .bucket(() -> BUCKETS.get(vid).get())
            .block(() -> (LiquidBlock) BLOCKS.get(vid).get())
            .slopeFindDistance(4)
            .levelDecreasePerBlock(2);

        SOURCES.put(vid, PFFluids.FLUIDS.register(base, () -> new SlimeMilkFluid.Source(props)));
        FLOWINGS.put(vid, PFFluids.FLUIDS.register(base + "_flowing", () -> new SlimeMilkFluid.Flowing(props)));

        BLOCKS.put(vid, PFBlocks.BLOCKS.registerBlock(
            base,
            p -> new SlimeMilkSourceBlock(SOURCES.get(vid).get(), vid, p),
            milkBlockProperties()));

        BUCKETS.put(vid, PFItems.ITEMS.registerItem(
            base + "_bucket",
            p -> new SlimeMilkBucketItem(SOURCES.get(vid).get(), vid, p.stacksTo(1).craftRemainder(Items.BUCKET))));
    }

    /** Mirror of the single source block's properties (see {@link PFBlocks}). */
    static BlockBehaviour.Properties milkBlockProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .replaceable()
            .noCollission()
            .strength(100.0F)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY);
    }

    // ---- accessors (null when the variant has no registered milk fluid) ----

    public static boolean isRegistered(Identifier variantId) {
        return SOURCES.containsKey(variantId);
    }

    public static Set<Identifier> registeredVariants() {
        return Collections.unmodifiableSet(SOURCES.keySet());
    }

    public static SlimeMilkFluid.Source sourceFluid(Identifier variantId) {
        DeferredHolder<Fluid, SlimeMilkFluid.Source> h = SOURCES.get(variantId);
        return h == null ? null : h.get();
    }

    /**
     * Reverse lookup: the variant id whose source fluid is {@code fluid}, or null.
     * Used by the Terrarium Controller's fluid intake to map a piped milk
     * {@code FluidStack} back to its variant (the variant rides the fluid identity,
     * so it survives even a non-PF pipe that strips catalyst components).
     */
    @Nullable
    public static Identifier variantOf(Fluid fluid) {
        for (Map.Entry<Identifier, DeferredHolder<Fluid, SlimeMilkFluid.Source>> e : SOURCES.entrySet()) {
            if (e.getValue().get() == fluid) {
                return e.getKey();
            }
        }
        return null;
    }

    public static FluidType fluidType(Identifier variantId) {
        DeferredHolder<FluidType, FluidType> h = TYPES.get(variantId);
        return h == null ? null : h.get();
    }

    public static SlimeMilkSourceBlock block(Identifier variantId) {
        DeferredBlock<SlimeMilkSourceBlock> h = BLOCKS.get(variantId);
        return h == null ? null : h.get();
    }

    /**
     * All per-variant source blocks, for the shared {@code BlockEntityType.Builder}
     * valid-blocks set (one BE type backs every per-variant source block). Resolved
     * lazily at BE-registration time, after {@link #bootstrap} has run.
     */
    public static net.minecraft.world.level.block.Block[] allBlocksArray() {
        return BLOCKS.values().stream().map(DeferredBlock::get)
            .toArray(net.minecraft.world.level.block.Block[]::new);
    }

    /** The per-variant Slime Milk bucket item, or null if the variant has no milk fluid. */
    public static Item bucket(Identifier variantId) {
        DeferredItem<SlimeMilkBucketItem> h = BUCKETS.get(variantId);
        return h == null ? null : h.get();
    }
}
