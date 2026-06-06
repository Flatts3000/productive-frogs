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

    public CrucibleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
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
        // Froglight in hand -> try to insert it as the melt input.
        if (stack.is(PFItems.CONFIGURABLE_FROGLIGHT.get())) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            if (crucible.tryInsertFroglight(stack, player)) {
                return ItemInteractionResult.CONSUME;
            }
            return ItemInteractionResult.FAIL;
        }
        // Anything bucket-shaped -> standard fluid-handler interaction (drains
        // the tank into an empty bucket; the tank capability is extract-only,
        // so pouring INTO the crucible no-ops).
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
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
