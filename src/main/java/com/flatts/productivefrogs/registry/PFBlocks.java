package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.data.Category;
import java.util.EnumMap;
import java.util.Map;
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
 */
public final class PFBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(ProductiveFrogs.MOD_ID);

    public static final Map<Category, DeferredBlock<PrimedFrogEggBlock>> PRIMED_FROG_EGGS = buildPrimedEggs();

    private static Map<Category, DeferredBlock<PrimedFrogEggBlock>> buildPrimedEggs() {
        EnumMap<Category, DeferredBlock<PrimedFrogEggBlock>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, BLOCKS.register(
                cat.primedEggItemName(),
                () -> new PrimedFrogEggBlock(cat, primedEggProperties(cat))
            ));
        }
        return map;
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
}
