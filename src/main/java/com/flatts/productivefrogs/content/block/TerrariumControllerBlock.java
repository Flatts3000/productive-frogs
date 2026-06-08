package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity;
import com.flatts.productivefrogs.content.multiblock.TerrariumValidationResult;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Terrarium Controller - the multiblock anchor and the single milk input
 * (milk intake + distribution land in phase 2). Phase 1 wires the validation
 * loop: a server ticker on the {@link TerrariumControllerBlockEntity} re-validates
 * the 7x7x7 shell around its 5x5x5 cavity on a throttled cadence and lights
 * {@link #FORMED}; right-clicking forces a validate and reports the first
 * structural problem in chat.
 *
 * <p>{@link #FACING} is 6-way (machines may sit on any shell face) and points
 * OUTWARD (front-to-player, like the other appliances), so the inward face -
 * which must abut the cavity - is {@code FACING.getOpposite()}.
 */
public class TerrariumControllerBlock extends Block implements EntityBlock {

    /** Outward-pointing face (front to the player who placed it from outside). */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** True while the structure validates - drives the glow (see PFBlocks lightLevel). */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public TerrariumControllerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, net.minecraft.core.Direction.NORTH)
            .setValue(FORMED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Front (FACING) faces the player, so the opposite face abuts the cavity
        // the player is looking into when building from outside.
        return this.defaultBlockState()
            .setValue(FACING, context.getNearestLookingDirection().getOpposite())
            .setValue(FORMED, Boolean.FALSE);
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
        return new TerrariumControllerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.TERRARIUM_CONTROLLER.get(),
            TerrariumControllerBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // Hand-feed: a per-variant Slime Milk bucket pushes a charge into the funnel.
        if (stack.getItem() instanceof SlimeMilkBucketItem
                && level.getBlockEntity(pos) instanceof TerrariumControllerBlockEntity be) {
            if (!level.isClientSide() && be.pushChargeFromBucket(stack)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                    ItemStack empty = new ItemStack(Items.BUCKET);
                    if (!player.getInventory().add(empty)) {
                        player.drop(empty, false);
                    }
                }
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof TerrariumControllerBlockEntity be
                && level instanceof ServerLevel serverLevel) {
            TerrariumValidationResult result = be.forceValidate(serverLevel, pos);
            player.displayClientMessage(describe(result), false);
        }
        return InteractionResult.SUCCESS;
    }

    private static Component describe(TerrariumValidationResult result) {
        if (result.formed()) {
            return Component.translatable("message.productivefrogs.terrarium.formed")
                .withStyle(ChatFormatting.GREEN);
        }
        TerrariumValidationResult.Problem problem = result.firstProblem();
        String key = problem == null ? "not_solid" : problem.messageKey();
        Component reason = Component.translatable("message.productivefrogs.terrarium." + key);
        Component message = problem != null && problem.at() != null
            ? Component.translatable("message.productivefrogs.terrarium.problem_at",
                reason, problem.at().getX(), problem.at().getY(), problem.at().getZ())
            : reason;
        return message.copy().withStyle(ChatFormatting.RED);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof TerrariumControllerBlockEntity be) {
            be.onBroken(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> serverType,
            BlockEntityType<E> clientType,
            BlockEntityTicker<? super E> ticker) {
        return serverType == clientType ? (BlockEntityTicker<A>) ticker : null;
    }
}
