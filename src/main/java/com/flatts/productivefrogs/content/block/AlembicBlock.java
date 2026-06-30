package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 * The Alembic (#253) - the Equivalence lane's RF-powered synthesizer. An empty
 * bucket + an off-roster item + RF produces a Mimic Slime Bucket carrying that
 * item. Breaking drops all held stacks.
 */
public class AlembicBlock extends Block implements EntityBlock {

    public AlembicBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlembicBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.ALEMBIC.get(), AlembicBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AlembicBlockEntity alembic
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(alembic, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // NOTE (26.1 port): the BlockEntity is removed before affectNeighborsAfterRemoval runs, so the
    // inventory drop below can no longer read the BE here. The drop must move to
    // AlembicBlockEntity#preRemoveSideEffects (BlockEntity-owned). Kept as a (currently no-op)
    // guard so the intent stays visible until that relocation lands.
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof AlembicBlockEntity alembic) {
            for (int slot = 0; slot < alembic.items().getSlots(); slot++) {
                ItemStack held = alembic.items().getStackInSlot(slot);
                if (!held.isEmpty()) {
                    Block.popResource(level, pos, held);
                }
            }
        }
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
