package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hatch block entity. Inert in phase 1 (placeable; counts as a shell-face cell
 * for {@link com.flatts.productivefrogs.content.multiblock.TerrariumValidator}).
 *
 * <p>Phase 3 (frog output) gives it the output inventory: inside a formed
 * Terrarium the frog-eats-slime drop is redirected straight into this inventory
 * (no item entity), with backpressure - a full Hatch makes frogs stop eating.
 * The outward face exposes the inventory for piping.
 */
public class HatchBlockEntity extends BlockEntity {

    public HatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.HATCH.get(), pos, state);
    }
}
