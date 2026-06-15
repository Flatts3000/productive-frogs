package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.EndDragonAltarHatchBlockEntity;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * End Dragon Altar Hatch (#249) - the altar's output. Same player-facing function
 * as the Terrarium Hatch (open like a chest, or pipe items out) but a distinct,
 * <b>non-directional</b> block with its own recipe, decoupled from the Terrarium.
 * The summon deposits the dragon's drops here; pipes pull from any face. Contents
 * drop on break.
 */
public class EndDragonAltarHatchBlock extends Block implements EntityBlock {

    public EndDragonAltarHatchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EndDragonAltarHatchBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null; // the plinth-frog reconciliation is server-side
        }
        return createTickerHelper(type, PFBlockEntities.END_DRAGON_ALTAR_HATCH.get(),
            EndDragonAltarHatchBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> serverType, BlockEntityType<E> clientType, BlockEntityTicker<? super E> ticker) {
        return serverType == clientType ? (BlockEntityTicker<A>) ticker : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof EndDragonAltarHatchBlockEntity be
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(be);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof EndDragonAltarHatchBlockEntity be) {
            Containers.dropContents(level, pos, be);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
