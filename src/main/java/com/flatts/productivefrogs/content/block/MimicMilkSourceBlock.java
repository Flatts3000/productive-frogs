package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.MimicMilkSourceBlockEntity;
import com.flatts.productivefrogs.content.entity.MimicSlime;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * The placeable form of Mimic Milk (#253). A single block (not per-variant): the
 * synthesized item it spawns rides on its {@link MimicMilkSourceBlockEntity}.
 * Subclasses {@link LiquidBlock} for the fluid render and is an {@link EntityBlock}
 * for the BE. On a scheduled tick it spawns a {@link MimicSlime} carrying the
 * source's item, on the shared {@code MilkSpawnEconomy} cadence, until its budget
 * drains - the EE lane's 1->N duplication step.
 *
 * <p>Leaner than {@link SlimeMilkSourceBlock}: the Mimic fluid never spreads
 * (every block is a source), so there is no inert-spread case; and there are no
 * catalysts / density cap / infinite flag (the lane's throttle is RF, upstream).
 */
public class MimicMilkSourceBlock extends LiquidBlock implements EntityBlock, LiquidBlockContainer {

    private static final int[][] NEIGHBOUR_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
        {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
        {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1}, {0, -1, 0},
        {0, 1, 0},
    };

    public MimicMilkSourceBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MimicMilkSourceBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level instanceof ServerLevel serverLevel && level.getFluidState(pos).isSource()) {
            // The item is written by the bucket's checkExtraContent (runs right
            // after onPlace, well before this delayed tick fires).
            scheduleNextSpawnTick(serverLevel, pos, level.getRandom());
        }
    }

    /** Reject foreign fluids so water/lava can't wash the source away (mirrors SlimeMilkSourceBlock). */
    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos,
                                  BlockState state, Fluid fluid) {
        return fluid.getFluidType() == this.fluid.getFluidType();
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        // The Mimic fluid never spreads, so this is only reached defensively; never
        // overwrite a real source cell (it owns the spawn-economy BE).
        return false;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getFluidState(pos).isSource()
                || !(level.getBlockEntity(pos) instanceof MimicMilkSourceBlockEntity be)) {
            return;
        }
        ResourceLocation itemId = be.getSynthesizedItem();
        if (itemId == null) {
            // Placed without an item (e.g. /setblock): inert, no reschedule.
            return;
        }
        boolean depleting = PFConfig.SPEC.isLoaded() && PFConfig.DEPLETION_ENABLED.get();
        if (depleting && be.getSpawnsRemaining() <= 0) {
            drainToAir(level, pos);
            return;
        }
        boolean spawned = spawn(level, pos, random, itemId);
        if (!spawned) {
            scheduleNextSpawnTick(level, pos, random);
            return;
        }
        if (depleting) {
            be.decrementSpawns();
            if (be.getSpawnsRemaining() <= 0) {
                drainToAir(level, pos);
                return;
            }
        }
        scheduleNextSpawnTick(level, pos, random);
    }

    private static void drainToAir(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format("mimic milk @%s: depleted, drained", pos));
    }

    private static void scheduleNextSpawnTick(ServerLevel level, BlockPos pos, RandomSource random) {
        int delay = com.flatts.productivefrogs.content.block.MilkSpawnEconomy.intervalTicks(0, random);
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), delay);
    }

    private boolean spawn(ServerLevel level, BlockPos pos, RandomSource random, ResourceLocation itemId) {
        BlockPos spawnPos = chooseSpawnPos(level, pos);
        MimicSlime slime = PFEntities.MIMIC_SLIME.get().create(level);
        if (slime == null) {
            return false;
        }
        slime.setSize(1, true);
        slime.setSynthesizedItem(itemId);
        slime.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
            random.nextFloat() * 360F, 0F);
        level.addFreshEntity(slime);
        PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
            "mimic milk @%s: spawned Mimic Slime (%s) at %s", pos, itemId, spawnPos));
        return true;
    }

    private static BlockPos chooseSpawnPos(ServerLevel level, BlockPos source) {
        for (int[] off : NEIGHBOUR_OFFSETS) {
            BlockPos neighbour = source.offset(off[0], off[1], off[2]);
            BlockPos above = neighbour.above();
            if (level.getBlockState(neighbour).isFaceSturdy(level, neighbour, Direction.UP)
                    && !level.getBlockState(above).blocksMotion()) {
                return above;
            }
        }
        return source;
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        ResourceLocation itemId = level.getBlockEntity(pos) instanceof MimicMilkSourceBlockEntity be
            ? be.getSynthesizedItem() : null;
        ItemStack bucket = super.pickupBlock(player, level, pos, state);
        if (itemId != null && !bucket.isEmpty()) {
            bucket.set(com.flatts.productivefrogs.registry.PFDataComponents.SYNTHESIZED_ITEM.get(), itemId);
        }
        return bucket;
    }
}
