package com.flatts.productivefrogs.content.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Virtual Terrarium Display Dome - the top, cosmetic block of the two-block
 * Virtual Terrarium (see {@code docs/virtual_terrarium.md}). A glass dome that sits
 * on the Processor; the frog/slime render is drawn by the Processor's block-entity
 * renderer up into this dome, so the Dome itself needs no block entity. The Processor
 * only runs while a Dome sits directly above it.
 */
public class VirtualTerrariumDomeBlock extends Block {

    public VirtualTerrariumDomeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, net.minecraft.core.Direction direction) {
        // Glass-style: cull faces against neighbouring domes.
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
