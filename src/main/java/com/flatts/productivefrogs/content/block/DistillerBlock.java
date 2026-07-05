package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.DistillerBlockEntity;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
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
 * The Distiller (#253) - the Equivalence lane's extractor. A Prismatic
 * Froglight (a {@code configurable_froglight} carrying a synthesized item) plus
 * RF renders the carried item back out. PF's first RF-powered machine; power
 * cables fill its buffer, the GUI shows the energy bar + distill progress + the
 * input/output slots. Breaking drops both held stacks.
 */
public class DistillerBlock extends Block implements EntityBlock {

    public DistillerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistillerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.DISTILLER.get(), DistillerBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DistillerBlockEntity distiller
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(distiller, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // 26.1 port: drop-on-break now lives in DistillerBlockEntity#preRemoveSideEffects (the BE still
    // exists there, whereas it is gone by affectNeighborsAfterRemoval). This block has no
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
