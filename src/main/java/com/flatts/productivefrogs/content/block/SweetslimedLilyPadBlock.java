package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.SweetslimedLilyPadBlockEntity;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A Sweetslimed Lily Pad - a perch that pins a working Resource Frog over a spot
 * (#214, docs/lily_pad_perch.md). Made by right-clicking a vanilla lily pad with a
 * Sweetslime ({@link com.flatts.productivefrogs.event.LilyPadPerchHandler}).
 *
 * <p>It is a vanilla {@link LilyPadBlock} (sits on water, same placement/break,
 * boats still snap it) plus a {@link SweetslimedLilyPadBlockEntity} ticker that does
 * the perching: claim the nearest frog and hold it near the pad. All the perch logic
 * lives in the BlockEntity; the block just wires the server ticker.
 */
public class SweetslimedLilyPadBlock extends LilyPadBlock implements EntityBlock {

    public SweetslimedLilyPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SweetslimedLilyPadBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.SWEETSLIMED_LILY_PAD.get(), SweetslimedLilyPadBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> serverType, BlockEntityType<E> clientType, BlockEntityTicker<? super E> ticker) {
        return serverType == clientType ? (BlockEntityTicker<A>) ticker : null;
    }
}
