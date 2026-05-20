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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Primed Frog Egg block — a single block ID with a {@link Category} state
 * property covering all six categories.
 *
 * <p>Refactor of the original 6-blocks-per-category design: one registration,
 * one block class, six blockstate variants. Matches the "1 block, N variants
 * via state" pattern (compact registry, single class to maintain). Each
 * variant has its own model file referenced from the single blockstate JSON.
 *
 * <p>Behavior mirrors vanilla {@code minecraft:frogspawn}: lives on a water
 * source, frogspawn voxel shape, no collision, instant break, frogspawn sound
 * type. Does not yet hatch — that lands alongside the Resource Tadpole
 * entity in a future PR.
 */
public final class PrimedFrogEggBlock extends Block {

    public static final EnumProperty<Category> CATEGORY = EnumProperty.create("category", Category.class);

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.5, 16.0);

    public PrimedFrogEggBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CATEGORY, Category.METALLIC));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CATEGORY);
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
