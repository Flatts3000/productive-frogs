package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Primed Frog Egg block — vanilla frogspawn that has been primed with a
 * category-tagged material. One subclass instance per {@link Category} (six
 * total), each registered as a distinct block ID. Matches vanilla's
 * coral/sapling/wool pattern of "N visual variants → N block IDs".
 *
 * <p>Behavior mirrors {@code minecraft:frogspawn}: must sit on a water source,
 * frogspawn voxel shape, no collision, instant break, frogspawn sound type.
 * Does not yet hatch — that lands alongside the Resource Tadpole entity in a
 * future PR.
 */
public final class PrimedFrogEggBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.5, 16.0);

    private final Category category;

    public PrimedFrogEggBlock(Category category, Properties properties) {
        super(properties);
        this.category = category;
    }

    public Category getCategory() {
        return category;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level, pos.below());
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                  BlockPos pos, Direction direction, BlockPos neighborPos,
                                  BlockState neighborState, RandomSource random) {
        if (!canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    private static boolean mayPlaceOn(LevelReader level, BlockPos below) {
        var fluid = level.getFluidState(below);
        return fluid.is(Fluids.WATER) && fluid.isSource();
    }
}
