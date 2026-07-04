package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.BossAltarHatchBlockEntity;
import com.flatts.productivefrogs.content.item.EntityNetItem;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import org.jetbrains.annotations.Nullable;

/**
 * The boss-altar Hatch block, shared by all four altars (#247/#249/#279/#280) -
 * each altar registers its own block instance wired to its own
 * {@link BossAltarHatchBlockEntity} subclass (distinct id, recipe, and texture;
 * one behavior). Open like a chest or pipe items out; the summon state machine
 * lives in the BE; contents drop on break (via the BE's Container spill).
 *
 * <p>The BE type is passed as a {@link Supplier} because blocks register before
 * BE types (the load-bearing PFBlocks-before-PFBlockEntities ordering) - it is
 * only resolved at ticker time, well after both registries exist.
 */
public class BossAltarHatchBlock extends Block implements EntityBlock {

    private final Supplier<? extends BlockEntityType<? extends BossAltarHatchBlockEntity>> type;
    private final BiFunction<BlockPos, BlockState, ? extends BossAltarHatchBlockEntity> factory;

    public BossAltarHatchBlock(Properties properties,
            Supplier<? extends BlockEntityType<? extends BossAltarHatchBlockEntity>> type,
            BiFunction<BlockPos, BlockState, ? extends BossAltarHatchBlockEntity> factory) {
        super(properties);
        this.type = type;
        this.factory = factory;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> t) {
        if (level.isClientSide() || t != this.type.get()) {
            return null; // display-frog reconciliation + the summon are server-side
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<? extends BossAltarHatchBlockEntity>) BossAltarHatchBlockEntity::serverTick;
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
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()
                && stack.getItem() instanceof EntityNetItem
                && EntityNetItem.isFilled(stack)
                && level.getBlockEntity(pos) instanceof BossAltarHatchBlockEntity hatch) {
            if (!level.isClientSide()) {
                if (hatch.dock().tryInstall(stack)) {
                    stack.remove(DataComponents.CUSTOM_DATA);
                    hatch.syncToClient(); // the Jade warning reads the installed state client-side
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F);
                } else {
                    level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 1.0F);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof BossAltarHatchBlockEntity be
                && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(be);
        }
        return InteractionResult.SUCCESS;
    }
}
