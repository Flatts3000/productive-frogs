package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.BossAltarHatchBlockEntity;
import java.util.function.BiFunction;
import java.util.function.Supplier;
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

    // NOTE: the Apex net-install gesture (shift-right-click with a filled net)
    // lives in EntityNetItem#useOn, NOT here - sneaking with an item in hand
    // skips block interaction entirely (isSecondaryUseActive), so a block-side
    // branch can never fire for that gesture. A plain right-click opens the chest.

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
