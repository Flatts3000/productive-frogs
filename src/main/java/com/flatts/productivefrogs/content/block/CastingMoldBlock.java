package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * The Casting Mold - solidifies molten metal back into ingots (v1.12 wave 2
 * part B, {@code docs/froglight_crucible.md}). Iron-and-bricks identity like
 * the Crucible; sits on TOP of a Crucible to pull molten directly (the
 * three-block tower: heat / Crucible / Mold), or runs free-standing fed by
 * pipes and buckets.
 *
 * <p>Interactions: a fluid-capable item (molten bucket, should one exist)
 * interacts with the buffer; everything else opens the GUI - the mod's third
 * Menu/Screen pair, where the fluid gauge, cast progress, and output slot
 * live. Breaking drops the output stack.
 */
public class CastingMoldBlock extends Block implements EntityBlock {

    public CastingMoldBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CastingMoldBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.CASTING_MOLD.get(), CastingMoldBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // FluidUtil resolves the held item's fluid handler internally and returns false for a
        // non-fluid item, so it doubles as the "is this fluid-capable" guard.
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection())) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CastingMoldBlockEntity mold
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(mold, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // 26.1 port: drop-on-break now lives in CastingMoldBlockEntity#preRemoveSideEffects (the BE
    // still exists there, whereas it is gone by affectNeighborsAfterRemoval). This block has no
    // BE-independent removal side effect, so no affectNeighborsAfterRemoval override is needed.

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> serverType,
            BlockEntityType<E> clientType,
            BlockEntityTicker<? super E> ticker) {
        return serverType == clientType ? (BlockEntityTicker<A>) ticker : null;
    }
}
