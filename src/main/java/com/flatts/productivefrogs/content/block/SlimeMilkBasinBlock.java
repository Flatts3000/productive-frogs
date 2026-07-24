package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkBasinBlockEntity;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Slime Milk Basin - a waterloggable container that holds one bucket of
 * Slime Milk inside the block and spawns that variant's Resource Slimes around
 * it. The engine lives on {@link SlimeMilkBasinBlockEntity}.
 *
 * <p><b>Additive:</b> the placeable {@link SlimeMilkSourceBlock} is unchanged and
 * stays. The Basin is the automation-friendly form - it persists when it empties
 * (a spent source drains to air), takes a pipe fill in place, and can be dropped
 * into a pool without the milk mixing with the water.
 *
 * <p><b>Works wet or dry.</b> The held milk never becomes a world fluid, so a
 * waterlogged Basin coexists with the pool it sits in - no mixing, no washing
 * away - and slimes spawn straight into the surrounding water.
 *
 * <p>Hand interactions (no GUI - it is a container, not an appliance):
 * <ul>
 *   <li>Slime Milk bucket on an empty Basin: pour in, empty bucket back.</li>
 *   <li>Empty bucket on a charged Basin: drain back out, budget and catalysts intact.</li>
 *   <li>Milk catalyst on a charged Basin: apply it (right-click parity with
 *       dropping one into a source pool; dropping still works too).</li>
 * </ul>
 * Pipes fill it through {@code Capabilities.FluidHandler.BLOCK} (fill-only,
 * exactly 1000 mB) - wired in {@code PFModBusEvents}.
 */
public class SlimeMilkBasinBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /**
     * A half-block basin, a crucible cut in half: the four sides are inset 1px so
     * flush walls can't z-fight water rendered in a waterlogged cell, but the
     * bottom sits on the block floor (y=0) - a basin rests on the ground, and the
     * flush down face culls normally against the block below.
     */
    private static final VoxelShape SHAPE =
        Shapes.box(1.0 / 16.0, 0.0, 1.0 / 16.0, 15.0 / 16.0, 0.5, 15.0 / 16.0);

    public SlimeMilkBasinBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluid = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState().setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SlimeMilkBasinBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.SLIME_MILK_BASIN.get(),
            SlimeMilkBasinBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SlimeMilkBasinBlockEntity basin)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Pour a Slime Milk bucket into an empty Basin.
        ResourceLocation variant = SlimeMilkBasinBlockEntity.variantFromBucket(stack);
        if (variant != null && !basin.isCharged()) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            if (!basin.acceptsVariant(level, variant)) {
                // Boss (altar-gated) milk: refuse audibly and keep the bucket.
                level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 1.0F);
                return ItemInteractionResult.SUCCESS;
            }
            basin.chargeFromBucket(variant, stack);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }

        // Drain a charged Basin back into an empty bucket, budget intact.
        if (stack.is(Items.BUCKET) && basin.isCharged()) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            ItemStack drained = basin.drainToBucket();
            if (!drained.isEmpty()) {
                stack.shrink(1);
                if (!player.getInventory().add(drained)) {
                    player.drop(drained, false);
                }
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        // Apply a catalyst by hand. The drop-in path below is the automation
        // twin; both funnel through the same consumption core.
        if (MilkCatalyst.fromStack(stack) != null && basin.isCharged()) {
            if (!level.isClientSide()) {
                tryConsumeCatalyst(level, pos, basin, stack);
            }
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Consume catalyst items dropped INTO the bowl - full parity with dropping one
     * into a milk source pool, and the automation path (a dropper can feed them
     * in). An item resting in the open bowl overlaps this block's cell, so vanilla
     * calls {@code entityInside} for it every tick. A refused catalyst is left
     * floating for the player rather than silently eaten.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide() || !(entity instanceof ItemEntity itemEntity)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof SlimeMilkBasinBlockEntity basin) || !basin.isCharged()) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (MilkCatalyst.fromStack(stack) == null) {
            return;
        }
        if (tryConsumeCatalyst(level, pos, basin, stack)) {
            if (stack.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(stack);
            }
        }
    }

    /**
     * The shared catalyst-consumption core (right-click + drop-in): apply one
     * catalyst to a charged Basin, shrink the stack by one, and play the source
     * pool's feedback. Returns false - stack untouched - when the catalyst is
     * config-disabled, meaningless (Count/Infinite with depletion off), or already
     * maxed on this Basin.
     */
    private static boolean tryConsumeCatalyst(Level level, BlockPos pos,
            SlimeMilkBasinBlockEntity basin, ItemStack stack) {
        MilkCatalyst catalyst = MilkCatalyst.fromStack(stack);
        if (catalyst == null) {
            return false;
        }
        if (!PFConfig.milkCatalystsEnabled() || !catalyst.isEnabled()) {
            return false;
        }
        if ((catalyst == MilkCatalyst.COUNT || catalyst == MilkCatalyst.INFINITE)
                && !SlimeMilkBasinBlockEntity.depletionEnabled()) {
            return false;
        }
        if (!basin.applyCatalyst(catalyst)) {
            return false;
        }
        stack.shrink(1);
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS,
            0.7F, 1.3F + level.getRandom().nextFloat() * 0.2F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.0);
        }
        return true;
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
