package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Filled Sprinkler milk-surface tint. The top milk surface (tint index 1) reads
 * the held variant off the block entity and tints to its {@code primary_color},
 * so a filled Sprinkler shows what milk is inside it from above. The base faces
 * (index 0) and an empty Sprinkler are untinted. Bound at tint index 1 in the
 * block model (index 0 is filled by {@link NoTint}).
 */
public final class SprinklerBlockTint implements BlockTintSource {

    @Override
    public int color(BlockState state) {
        return -1;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (level == null || pos == null) {
            return -1;
        }
        if (!(level.getBlockEntity(pos) instanceof SprinklerBlockEntity sprinkler) || sprinkler.isEmpty()) {
            return -1;
        }
        Identifier variantId = sprinkler.getVariantId();
        if (variantId == null) {
            return -1;
        }
        // The Sprinkler BE's level is a server-or-client Level, not necessarily a
        // ClientLevel; resolve the variant colour via the running client level.
        int color = Tints.variantColor(null, variantId);
        return color != -1 ? color : Tints.opaque(0xF0F0E0);
    }
}
