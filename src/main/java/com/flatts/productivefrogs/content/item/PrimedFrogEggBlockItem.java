package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.data.Category;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockItem that places the {@link PrimedFrogEggBlock} pre-set to a specific
 * {@link Category}. We register one of these per category so the creative
 * tab / JEI shows a distinct item per category, but they all place the same
 * underlying block (varying only by its state property).
 */
public final class PrimedFrogEggBlockItem extends BlockItem {

    private final Category category;

    public PrimedFrogEggBlockItem(Block block, Properties properties, Category category) {
        super(block, properties);
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }

    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState base = super.getPlacementState(context);
        return base == null ? null : base.setValue(PrimedFrogEggBlock.CATEGORY, this.category);
    }
}
