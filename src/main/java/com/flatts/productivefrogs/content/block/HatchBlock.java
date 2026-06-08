package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.HatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Hatch - the Terrarium's froglight output. Phase 1 is an inert shell: it
 * places, breaks, and counts as a sealed shell-face cell for
 * {@link com.flatts.productivefrogs.content.multiblock.TerrariumValidator}
 * (a Terrarium needs exactly one, on a face, facing inward).
 *
 * <p>{@link #FACING} is 6-way and points OUTWARD (front-to-player); validation
 * requires the inward neighbour to be inside the cavity. The output inventory +
 * direct-to-Hatch froglight override + backpressure land in phase 3.
 */
public class HatchBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public HatchBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HatchBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
            BlockPos pos, Player player, net.minecraft.world.phys.BlockHitResult hit) {
        // Hand-collect the stored froglights (a proper GUI lands in the ship phase).
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof HatchBlockEntity be) {
            be.collectInto(player);
        }
        return InteractionResult.SUCCESS;
    }
}
