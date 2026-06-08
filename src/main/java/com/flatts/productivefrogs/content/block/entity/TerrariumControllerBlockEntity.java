package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.TerrariumControllerBlock;
import com.flatts.productivefrogs.content.multiblock.TerrariumManager;
import com.flatts.productivefrogs.content.multiblock.TerrariumValidationResult;
import com.flatts.productivefrogs.content.multiblock.TerrariumValidator;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Terrarium Controller's block entity - the multiblock anchor. In phase 1
 * (structure + validation) it does one job: on a throttled tick it re-runs
 * {@link TerrariumValidator}, registers/deregisters the result in
 * {@link TerrariumManager}, and flips the block's {@link TerrariumControllerBlock#FORMED}
 * state (which drives the glow). Right-clicking the block forces a validate and
 * reports the first structural problem - the "why won't it form" story.
 *
 * <p>Phase 2 (milk path) adds the charge buffer here - a FIFO of {@code MilkCharge}
 * (one variant at a time, reject-until-empty) plus the bucket-slot / fluid-handler
 * intake and the round-robin Sprinkler distribution. Those fields are intentionally
 * absent until that phase so the skeleton stays minimal; the validation tick and
 * the {@code FormedTerrarium} (which already carries the sprinkler list) are the
 * hooks they will build on.
 */
public class TerrariumControllerBlockEntity extends BlockEntity {

    private int tickCounter;
    private boolean formed;
    @Nullable
    private TerrariumValidationResult lastResult;

    public TerrariumControllerBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.TERRARIUM_CONTROLLER.get(), pos, state);
    }

    /** Throttled validation tick (cadence = {@code terrarium.validationIntervalTicks}). */
    public static void serverTick(Level level, BlockPos pos, BlockState state, TerrariumControllerBlockEntity be) {
        int interval = Math.max(1, PFConfig.terrariumValidationIntervalTicks());
        if (++be.tickCounter < interval) {
            return;
        }
        be.tickCounter = 0;
        be.runValidation((ServerLevel) level, pos, state);
    }

    /**
     * Validate now, sync the {@link TerrariumManager} registry and the
     * {@link TerrariumControllerBlock#FORMED} state to the result, and cache it.
     */
    public TerrariumValidationResult runValidation(ServerLevel level, BlockPos pos, BlockState state) {
        TerrariumValidationResult result = TerrariumValidator.validate(level, pos, state);
        this.lastResult = result;
        if (result.formed()) {
            TerrariumManager.register(level, result);
        } else {
            TerrariumManager.deregister(level, pos);
        }
        if (result.formed() != this.formed) {
            this.formed = result.formed();
            level.setBlock(pos, state.setValue(TerrariumControllerBlock.FORMED, this.formed), Block.UPDATE_ALL);
        }
        return result;
    }

    /** Force a validate against the live state - the right-click entry and the test seam. */
    public TerrariumValidationResult forceValidate(ServerLevel level, BlockPos pos) {
        return runValidation(level, pos, level.getBlockState(pos));
    }

    /** Whether the structure was formed as of the last validation. */
    public boolean isFormed() {
        return formed;
    }

    @Nullable
    public TerrariumValidationResult lastResult() {
        return lastResult;
    }

    /** Deregister on break / chunk-unload so a stale entry never lingers. */
    public void onBroken(ServerLevel level, BlockPos pos) {
        TerrariumManager.deregister(level, pos);
    }
}
