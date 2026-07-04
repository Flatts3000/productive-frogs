package com.flatts.productivefrogs.client.color;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A constant ARGB block tint. Used for the six Primed Frog Egg blocks (one per
 * species colour) and the Midas Frog Egg (gold). The colour is expected to be
 * already opaque (alpha {@code 0xFF}).
 *
 * @param argb the fixed ARGB tint returned for every state at this tint index.
 */
public record ConstantBlockTint(int argb) implements BlockTintSource {

    @Override
    public int color(BlockState state) {
        return argb;
    }
}
