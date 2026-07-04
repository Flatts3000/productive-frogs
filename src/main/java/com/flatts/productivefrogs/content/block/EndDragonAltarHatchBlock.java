package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.EndDragonAltarHatchBlockEntity;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
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

    /**
     * Apex install (#281 Phase 4, maintainer ruling): shift-right-click with a
     * net holding THIS altar's Apex frog installs it - the whole net NBT moves
     * onto the hatch's dock (the display frog renders while installed; breaking
     * the hatch respawns the real frog) and the net comes back empty. The wrong
     * frog (or any other kind) is refused with a no-thanks sound, nothing
     * consumed. A plain right-click still opens the chest.
     */
    @Override
    protected InteractionResult useItemOn(
        net.minecraft.world.item.ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        net.minecraft.world.InteractionHand hand,
        BlockHitResult hit
    ) {
        if (player.isShiftKeyDown()
                && stack.getItem() instanceof com.flatts.productivefrogs.content.item.EntityNetItem
                && com.flatts.productivefrogs.content.item.EntityNetItem.isFilled(stack)
                && level.getBlockEntity(pos) instanceof com.flatts.productivefrogs.content.block.entity.EndDragonAltarHatchBlockEntity hatch) {
            if (!level.isClientSide()) {
                if (hatch.dock().tryInstall(stack)) {
                    stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
                } else {
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.VILLAGER_NO,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.0F);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
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

    // 26.1 port: contents drop on break is now handled automatically by
    // BaseContainerBlockEntity#preRemoveSideEffects (the BE implements Container), which runs
    // before the BE is removed - so the old onRemove override is gone.
}
