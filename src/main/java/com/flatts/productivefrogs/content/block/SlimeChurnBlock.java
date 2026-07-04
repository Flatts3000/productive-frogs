package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Slime Churn (#187) - the inverse of the Slime Milker: a furnace-style
 * appliance that consumes a per-variant Slime Milk bucket plus empty buckets
 * and produces captured variant Slime Buckets, running the placed-source
 * spawn economy ({@link MilkSpawnEconomy}). Replaces the manual
 * place-source / wait / catch / re-bucket loop with one block.
 *
 * <p>Two inputs (milk bucket, empty buckets), two outputs (slime buckets,
 * spent empty containers) - see {@code SlimeChurnInventory}. Right-click
 * opens the GUI; hopper I/O is wired via a side-aware
 * {@code Capabilities.ItemHandler.BLOCK} provider in {@code PFModBusEvents}
 * (top + horizontal faces = input view, bottom = output view).
 *
 * <p>Structure mirrors {@link SlimeMilkerBlock} deliberately - one shape for
 * all appliances (CLAUDE.md).
 */
public class SlimeChurnBlock extends Block implements EntityBlock {

    /** Horizontal facing; the {@code front} texture renders on this face. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * True while the churn is counting down toward a spawn event or emitting
     * a batch. Drives the idle/working texture swap via the blockstate JSON.
     */
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public SlimeChurnBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WORKING, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WORKING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(WORKING, Boolean.FALSE);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SlimeChurnBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.SLIME_CHURN.get(), SlimeChurnBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return openChurnMenu(level, pos, player);
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        InteractionResult openResult = openChurnMenu(level, pos, player);
        return openResult == InteractionResult.SUCCESS
            ? net.minecraft.world.ItemInteractionResult.SUCCESS
            : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private InteractionResult openChurnMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MenuProvider provider) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SlimeChurnBlockEntity churn) {
                com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory inv = churn.getInventory();
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        net.minecraft.world.Containers.dropItemStack(
                            level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Type-safe ticker cast - same private reimplementation as
     * {@link SlimeMilkerBlock} (vanilla's helper is protected on
     * BaseEntityBlock and invisible from a plain Block subclass).
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> serverType,
            BlockEntityType<E> clientType,
            BlockEntityTicker<? super E> ticker) {
        return serverType == clientType ? (BlockEntityTicker<A>) ticker : null;
    }
}
