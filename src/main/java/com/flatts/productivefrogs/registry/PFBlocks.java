package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry. Single Primed Frog Egg block with a {@link
 * com.flatts.productivefrogs.data.Category} state property covering all six
 * categories — one registration, six visual variants via blockstate JSON.
 */
public final class PFBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(ProductiveFrogs.MOD_ID);

    /**
     * The Primed Frog Egg block — vanilla-frogspawn-shaped, lives on water,
     * no hatching yet. Category is a state property; see
     * {@link PrimedFrogEggBlock#CATEGORY}.
     */
    public static final DeferredBlock<PrimedFrogEggBlock> PRIMED_FROG_EGG = BLOCKS.register(
        "primed_frog_egg",
        () -> new PrimedFrogEggBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN)
            .replaceable()
            .noCollision()
            .instabreak()
            .sound(SoundType.FROGSPAWN)
            .noOcclusion()
            .pushReaction(PushReaction.DESTROY))
    );

    private PFBlocks() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
