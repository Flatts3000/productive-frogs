package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.DistillerBlockEntity;
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

    // NOTE (26.1 port): the BlockEntity is removed before affectNeighborsAfterRemoval runs, so the
    // inventory drop below can no longer read the BE here. The drop must move to
    // DistillerBlockEntity#preRemoveSideEffects (BlockEntity-owned). Kept as a (currently no-op)
    // guard so the intent stays visible until that relocation lands.
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof DistillerBlockEntity distiller) {
            ItemStack in = distiller.items().getStackInSlot(DistillerBlockEntity.INPUT_SLOT);
            if (!in.isEmpty()) {
                Block.popResource(level, pos, in);
            }
            ItemStack out = distiller.items().getStackInSlot(DistillerBlockEntity.OUTPUT_SLOT);
            if (!out.isEmpty()) {
                Block.popResource(level, pos, out);
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
