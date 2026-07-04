package com.flatts.productivefrogs.client.color;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A no-op block tint source (always {@code -1}). Used as the index-0 filler when
 * a block is only tinted at a higher tint index (e.g. the Sprinkler tints its
 * milk surface at index 1, with no tint on the base faces at index 0). The list
 * position passed to {@code RegisterColorHandlersEvent.BlockTintSources#register}
 * is the tint index, so index 0 must be occupied.
 */
public final class NoTint implements BlockTintSource {

    public static final NoTint INSTANCE = new NoTint();

    private NoTint() {
    }

    @Override
    public int color(BlockState state) {
        return -1;
    }
}
