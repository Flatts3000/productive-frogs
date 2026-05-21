package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.block.SlimeMilkerBlock;
import com.flatts.productivefrogs.data.Category;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry. Six separate Primed Frog Egg blocks (one per {@link
 * Category}) — matches vanilla's "N visual variants → N block IDs" pattern for
 * coral, saplings, and wool. Each block pairs cleanly with one BlockItem,
 * so vanilla's Block↔Item bijection (used by pick-block, drops, getCloneItemStack)
 * works without overrides.
 *
 * <p>Uses {@code registerBlock(name, factory, properties)} (not the older
 * {@code register(name, Supplier)}) because MC 1.21.x now requires the
 * {@code ResourceKey} to be set on the Properties before the block's
 * constructor runs. The factory form lets DeferredRegister inject the ID
 * into the Properties and then hand them to our constructor.
 */
public final class PFBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(ProductiveFrogs.MOD_ID);

    public static final Map<Category, DeferredBlock<PrimedFrogEggBlock>> PRIMED_FROG_EGGS = buildPrimedEggs();

    /**
     * Per-category Resource Froglight blocks. {@link RotatedPillarBlock} so they
     * inherit vanilla {@code OCHRE_FROGLIGHT}'s "axis" rotation property and
     * placement behavior. Light level 15, sound type FROGLIGHT — same vanilla
     * properties — only the map color and (client-side) tint differ per category.
     */
    public static final Map<Category, DeferredBlock<RotatedPillarBlock>> RESOURCE_FROGLIGHTS = buildResourceFroglights();

    /**
     * Slime Milk LiquidBlocks keyed by variant name. One block per variant in
     * {@link PFFluidTypes#VARIANTS}; each wraps its source fluid from
     * {@link PFFluids#BY_VARIANT}. The concrete type is {@link
     * SlimeMilkSourceBlock} (a {@link LiquidBlock} subclass) — adds the J4
     * periodic-spawn loop that turns each source block into a slime fountain
     * for the matching variant.
     */
    public static final Map<String, DeferredBlock<LiquidBlock>> MILK_BLOCKS = buildMilkBlocks();

    /**
     * The Slime Milker — V1 production keystone block. Single appliance,
     * no power, no GUI; right-click with a Slime Bucket → milk bucket out.
     * See {@link SlimeMilkerBlock} for interaction details.
     *
     * <p>Strength chosen to match the brewing stand / composter feel —
     * cheap to break with hand, sturdy enough to feel "built". Sound type
     * METAL since the design is a "mechanical press" (themed asset still
     * TODO; placeholder block model + texture for now).
     */
    public static final DeferredBlock<SlimeMilkerBlock> SLIME_MILKER = BLOCKS.registerBlock(
        "slime_milker",
        SlimeMilkerBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(0.5F)
            .sound(SoundType.METAL)
    );

    /** Backwards-compatible alias for J1 callers. New code should use {@link #MILK_BLOCKS}. */
    public static final DeferredBlock<LiquidBlock> IRON_SLIME_MILK = MILK_BLOCKS.get("iron");

    private static Map<String, DeferredBlock<LiquidBlock>> buildMilkBlocks() {
        java.util.LinkedHashMap<String, DeferredBlock<LiquidBlock>> map = new java.util.LinkedHashMap<>();
        for (String variant : PFFluidTypes.VARIANTS) {
            map.put(variant, BLOCKS.registerBlock(
                variant + "_slime_milk",
                props -> new SlimeMilkSourceBlock(PFFluids.BY_VARIANT.get(variant).source().get(), variant, props),
                BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .replaceable()
                    .noCollision()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY)
            ));
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    private static Map<Category, DeferredBlock<PrimedFrogEggBlock>> buildPrimedEggs() {
        EnumMap<Category, DeferredBlock<PrimedFrogEggBlock>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, BLOCKS.registerBlock(
                cat.primedEggItemName(),
                props -> new PrimedFrogEggBlock(cat, props),
                primedEggProperties(cat)
            ));
        }
        return map;
    }

    private static Map<Category, DeferredBlock<RotatedPillarBlock>> buildResourceFroglights() {
        EnumMap<Category, DeferredBlock<RotatedPillarBlock>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, BLOCKS.registerBlock(
                cat.id() + "_froglight",
                RotatedPillarBlock::new,
                resourceFroglightProperties(cat)
            ));
        }
        return map;
    }

    private static BlockBehaviour.Properties resourceFroglightProperties(Category cat) {
        return BlockBehaviour.Properties.of()
            .mapColor(mapColorFor(cat))
            .strength(0.3F)
            .lightLevel(state -> 15)
            .sound(SoundType.FROGLIGHT);
    }

    private static BlockBehaviour.Properties primedEggProperties(Category cat) {
        return BlockBehaviour.Properties.of()
            .mapColor(mapColorFor(cat))
            .replaceable()
            .noCollision()
            .instabreak()
            .sound(SoundType.FROGSPAWN)
            .noOcclusion()
            .pushReaction(PushReaction.DESTROY);
    }

    /**
     * Rough map-color picks per category — used in inventory icons and on
     * cartography-table maps. Visual textures use full RGB tints; this map
     * color is just the closest vanilla-bucket approximation.
     */
    private static MapColor mapColorFor(Category cat) {
        return switch (cat) {
            case METALLIC -> MapColor.METAL;
            case MINERAL  -> MapColor.COLOR_RED;
            case GEM      -> MapColor.DIAMOND;
            case AQUATIC  -> MapColor.WATER;
            case INFERNAL -> MapColor.FIRE;
            case ARCANE   -> MapColor.COLOR_PURPLE;
        };
    }

    private PFBlocks() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    /** Convenience: get the primed egg block for a given category. */
    public static PrimedFrogEggBlock primedEgg(Category category) {
        return PRIMED_FROG_EGGS.get(category).get();
    }

    /** Convenience: get the Resource Froglight block for a given category. */
    public static Block resourceFroglight(Category category) {
        return RESOURCE_FROGLIGHTS.get(category).get();
    }
}
