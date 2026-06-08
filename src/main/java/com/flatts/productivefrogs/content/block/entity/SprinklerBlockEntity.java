package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sprinkler block entity. Inert in phase 1 (the block is placeable and counts
 * as a ceiling cell for {@link com.flatts.productivefrogs.content.multiblock.TerrariumValidator}).
 *
 * <p>Phase 2 (milk path) gives it the placed-Slime-Milk-source behavior: a
 * {@code MilkBudget} (spawn budget + catalysts, shared with
 * {@link SlimeMilkSourceBlockEntity} via the factored spawn loop, NOT forked),
 * a cadence countdown ticker spawning its variant's slimes down into the cavity,
 * and an empty-bucket drain back to the per-variant milk bucket.
 */
public class SprinklerBlockEntity extends BlockEntity {

    public SprinklerBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SPRINKLER.get(), pos, state);
    }
}
