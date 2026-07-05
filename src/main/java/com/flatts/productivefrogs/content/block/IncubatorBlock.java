package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Incubator - a shell-face machine that grows frogspawn/tadpoles into stat-
 * preserving frogs released into the cavity. Phase 1 is an inert shell: it
 * places, breaks, and counts as a sealed shell-face cell for
 * {@link com.flatts.productivefrogs.content.multiblock.TerrariumValidator}
 * (a Terrarium needs at least one, on a face, facing inward).
 *
 * <p>{@link #FACING} is 6-way and points OUTWARD (front-to-player); validation
 * requires the inward neighbour to be inside the cavity. The stat relay + GUI
 * land in phase 4.
 */
public class IncubatorBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public IncubatorBlock(Properties properties) {
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
        return new IncubatorBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.INCUBATOR.get(), IncubatorBlockEntity::serverTick);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        // Empty-hand right-click opens the status screen (growth progress / state).
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof IncubatorBlockEntity be
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(be, buf -> buf.writeBlockPos(pos));
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // Sweetslime: feed an actively-incubating Incubator to shave time off (10%
        // of the full lifecycle per slime), like hurrying a tadpole along. Gated on
        // the frog-stat layer (#202) so "stats off" means Sweetslime does nothing
        // anywhere (it is also uncraftable then) rather than leaving a dead-end use.
        if (stack.is(PFItems.SWEETSLIME.get()) && PFConfig.frogStatsEnabled()
                && level.getBlockEntity(pos) instanceof IncubatorBlockEntity be && be.isIncubating()) {
            if (!level.isClientSide() && be.accelerateWithSweetslime()) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SLIME_SQUISH,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.4F);
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.0);
                }
            }
            return InteractionResult.SUCCESS;
        }
        // Seed with a Frog Egg bottle: read its species and start an incubation
        // (baseline stats - a bottled egg carries none; bred stats arrive via the
        // LayCategoryFrogspawn redirect).
        Category category = stack.get(PFDataComponents.CONTAINED_CATEGORY.get());
        if (category != null && level.getBlockEntity(pos) instanceof IncubatorBlockEntity be && be.hasRoom()) {
            if (!level.isClientSide() && be.seedBaseline(category)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FROG_LAY_SPAWN,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
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
