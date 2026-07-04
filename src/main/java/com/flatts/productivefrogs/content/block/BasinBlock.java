package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.AbstractBasinBlockEntity;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
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
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The Basin block (#281, predation Phase 3) - one class, two registrations:
 * the <b>Mob Slurry Basin</b> and the <b>Slime Milk Basin</b>, differing only
 * in their {@link AbstractBasinBlockEntity} flavour. A waterloggable container
 * that holds one bucket's worth of spawning fluid INSIDE the block and runs
 * the milk spawn economy - see the BE for the engine.
 *
 * <p><b>Waterloggable, works wet or dry</b> (maintainer ruling): the held
 * fluid never becomes a world fluid, so a waterlogged Basin coexists with the
 * pool it sits in - no mixing, no washing away - and aquatic mobs spawn
 * straight into the surrounding water.
 *
 * <p>Hand interactions (no GUI - it's a container block, not an appliance):
 * <ul>
 *   <li>Matching charged bucket on an empty Basin -&gt; pour in (empty bucket back).</li>
 *   <li>Empty bucket on a charged Basin -&gt; drain back out, budget intact.</li>
 *   <li>Milk catalyst on a charged Basin -&gt; apply (right-click parity with
 *       dropping one into a source pool).</li>
 * </ul>
 * Pipes fill it via {@code Capabilities.Fluid.BLOCK} (fill-only, exactly
 * 1000 mB, components carried - wired in {@code PFModBusEvents}).
 */
public class BasinBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /**
     * A half-block basin - a crucible cut in half (maintainer rulings): the
     * four SIDES are inset 1px so flush walls can't z-fight the water rendered
     * in a waterlogged cell, but the BOTTOM sits against the block's bottom
     * (y=0) - a basin rests on the ground, and the flush down face culls
     * normally against the block below.
     */
    private static final VoxelShape SHAPE =
        Shapes.box(1.0 / 16.0, 0.0, 1.0 / 16.0, 15.0 / 16.0, 0.5, 15.0 / 16.0);

    private final BiFunction<BlockPos, BlockState, AbstractBasinBlockEntity> beFactory;
    private final Supplier<BlockEntityType<? extends AbstractBasinBlockEntity>> beType;

    public BasinBlock(Properties properties,
            BiFunction<BlockPos, BlockState, AbstractBasinBlockEntity> beFactory,
            Supplier<BlockEntityType<? extends AbstractBasinBlockEntity>> beType) {
        super(properties);
        this.beFactory = beFactory;
        this.beType = beType;
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
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return beFactory.apply(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, beType.get(), AbstractBasinBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        if (!(level.getBlockEntity(pos) instanceof AbstractBasinBlockEntity basin)) {
            return InteractionResult.PASS;
        }

        // Pour a matching charged bucket into an empty Basin.
        Identifier key = basin.keyFromBucket(stack);
        if (key != null && !basin.isCharged()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (!(level instanceof ServerLevel serverLevel) || !basin.acceptsKey(serverLevel, key)) {
                // Refused content (boss milk/slurry): tell the player by sound, keep the bucket.
                level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 1.0F);
                return InteractionResult.SUCCESS;
            }
            basin.chargeFrom(key, stack);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }

        // Drain a charged Basin back into an empty bucket (budget intact).
        if (stack.is(Items.BUCKET) && basin.isCharged()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            ItemStack drained = basin.drainToBucket();
            if (!drained.isEmpty()) {
                stack.shrink(1);
                if (!player.getInventory().add(drained)) {
                    player.drop(drained, false);
                }
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        // Apply a catalyst by right-click (the hand path; drop-in below is the
        // automation path - both feed the same shared consumption core).
        if (MilkCatalyst.fromStack(stack) != null && basin.isCharged()) {
            if (!level.isClientSide()) {
                tryConsumeCatalyst(level, pos, basin, stack);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /**
     * Consume catalyst items dropped INTO the bowl - full parity with dropping
     * one into a milk source pool (maintainer ruling: Basins allow catalysts),
     * and the automation path (a dropper can feed catalysts in). An item
     * resting in the open bowl overlaps this block's cell, so vanilla calls
     * {@code entityInside} for it every tick.
     *
     * <p>Same gates as {@code SlimeMilkSourceBlock.entityInside}: server-side,
     * catalysts globally enabled, a real charged Basin, the per-catalyst flag
     * on, and Count/Infinite only while depletion matters. A refused catalyst
     * is left floating for the player rather than silently eaten.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.Entity entity,
            net.minecraft.world.entity.InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
        if (level.isClientSide() || !(entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof AbstractBasinBlockEntity basin) || !basin.isCharged()) {
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
     * catalyst to the charged Basin, shrink the stack by one, and play the
     * source pool's feedback. Returns false - stack untouched - when the
     * catalyst is config-disabled, meaningless (Count/Infinite with depletion
     * off), or already maxed on this Basin.
     */
    private static boolean tryConsumeCatalyst(Level level, BlockPos pos,
            AbstractBasinBlockEntity basin, ItemStack stack) {
        MilkCatalyst catalyst = MilkCatalyst.fromStack(stack);
        if (catalyst == null) {
            return false;
        }
        if (!com.flatts.productivefrogs.PFConfig.milkCatalystsEnabled() || !catalyst.isEnabled()) {
            return false;
        }
        if ((catalyst == MilkCatalyst.COUNT || catalyst == MilkCatalyst.INFINITE)
                && !AbstractBasinBlockEntity.depletionEnabled()) {
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
