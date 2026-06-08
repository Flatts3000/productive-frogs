package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Incubator block entity. Inert in phase 1 (placeable; counts as a shell-face
 * cell for {@link com.flatts.productivefrogs.content.multiblock.TerrariumValidator}).
 *
 * <p>Phase 4 (incubator) gives it the v1.5 stat-relay role: insert frogspawn /
 * a captured tadpole, grow it, and release a {@code ResourceFrog} into the
 * cavity with stats applied post-{@code finalizeSpawn} (mirroring
 * {@link PrimedFrogEggBlockEntity}'s {@code hasStats/appetite/bounty/reach}
 * fields). At the frog cap it holds matured frogs; it is also the catch-basin
 * for in-cavity breeding (the {@code LayCategoryFrogspawn} redirect).
 */
public class IncubatorBlockEntity extends BlockEntity {

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.INCUBATOR.get(), pos, state);
    }
}
