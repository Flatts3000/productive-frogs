package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * The Froglight Crucible - a GUI-less heated basin that melts a (variant)
 * Froglight into a fluid. v1.12 wave 1; full design in
 * {@code docs/froglight_crucible.md}.
 *
 * <p>Interaction follows the cauldron/composter model, NOT the Milker/Spawnery
 * Menu+Screen shape (which also sidesteps the 1.21.1 renderTooltip gotcha):
 * <ul>
 *   <li>Right-click with a Froglight: insert it (one at a time; rejected while
 *       a melt is in progress, when no heat is below, when the Froglight has
 *       no melt recipe, or when its output fluid doesn't match/fit the tank).</li>
 *   <li>Right-click with an empty bucket (or any fluid container): drain the
 *       internal tank via the standard {@link FluidUtil} interaction.</li>
 * </ul>
 *
 * <p>Heat comes from the block BELOW (Ex Deorum-parity values via the
 * {@code productivefrogs:crucible_heat} data map); {@link #LIT} mirrors
 * "actively melting over heat" for the glow. Pipes interact through the
 * extract-only {@code Capabilities.FluidHandler.BLOCK} wired in
 * {@code PFModBusEvents}.
 */
public class CrucibleBlock extends Block implements EntityBlock {

    /** True while a Froglight is actively melting over a live heat source. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /**
     * Vanilla {@code AbstractCauldronBlock}'s shape, copied verbatim: a full
     * cube minus the interior bowl and the gaps between the legs. Gives the
     * basin a real outline for targeting/collision instead of the default
     * full-cube shape (which both felt wrong to walk on and paired badly with
     * the gappy cauldron geometry).
     */
    private static final net.minecraft.world.phys.shapes.VoxelShape INSIDE =
        box(2.0, 4.0, 2.0, 14.0, 16.0, 14.0);
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
        net.minecraft.world.phys.shapes.Shapes.join(
            net.minecraft.world.phys.shapes.Shapes.block(),
            net.minecraft.world.phys.shapes.Shapes.or(
                box(0.0, 0.0, 3.0, 16.0, 3.0, 13.0),
                box(3.0, 0.0, 0.0, 13.0, 3.0, 16.0),
                box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
                INSIDE),
            net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST);

    public CrucibleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getInteractionShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrucibleBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.CRUCIBLE.get(), CrucibleBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Anything fluid-capable (buckets) -> direct tank interaction. The
        // handler is extract-only, so this fills empty buckets and no-ops on
        // full ones.
        if (stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM) != null) {
            return FluidUtil.interactWithFluidHandler(player, hand, crucible.fluidHandler())
                ? ItemInteractionResult.sidedSuccess(level.isClientSide())
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Glass bottle pulls a water bottle when the tank holds water
        // (Ex Deorum parity).
        if (stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE) && crucible.canFillBottle()) {
            if (!level.isClientSide()) {
                crucible.drainBottle();
                stack.consume(1, player);
                ItemStack bottle = net.minecraft.world.item.alchemy.PotionContents.createItemStack(
                    net.minecraft.world.item.Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER);
                if (!player.getInventory().add(bottle)) {
                    player.drop(bottle, false);
                }
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // Froglight (or anything else with a melt recipe) -> queue as solids.
        // Click semantics mirror Ex Deorum: OK consumes one (free in
        // creative), FULL still consumes the CLICK (no awkward pass-through
        // while holding a valid input), REJECT passes through.
        if (stack.is(PFItems.CONFIGURABLE_FROGLIGHT.get())) {
            switch (crucible.insertCheck(stack)) {
                case OK -> {
                    if (!level.isClientSide()) {
                        ItemStack one = stack.copyWithCount(1);
                        if (crucible.acceptFroglight(one) && !player.getAbilities().instabuild) {
                            stack.shrink(1);
                        }
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
                case FULL -> {
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
                case REJECT -> {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
