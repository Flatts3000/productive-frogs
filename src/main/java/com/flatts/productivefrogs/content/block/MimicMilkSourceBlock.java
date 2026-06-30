package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.MimicMilkSourceBlockEntity;
import com.flatts.productivefrogs.content.entity.MimicSlime;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
 * <p>Leaner than {@link SlimeMilkSourceBlock} only in that the Mimic fluid never
 * spreads (every block is a source), so there is no inert-spread case. It accepts
 * the same Slime Milk catalysts (Count / Speed / Quantity / Endless) dropped into
 * the pool via {@link #entityInside}, at parity with a species source.
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
            scheduleNextSpawnTick(serverLevel, pos, level.getRandom(), 0);
        }
    }

    /**
     * Consume a Slime Milk catalyst dropped into the pool, applying its upgrade to
     * the BE - parity with {@link SlimeMilkSourceBlock#entityInside}. Gated: server
     * only; catalysts enabled; an enabled catalyst {@link ItemEntity} over a real
     * source with an assigned item; Count/Endless only when depletion is on (else
     * a no-op). An already-maxed upgrade is left unconsumed for the player.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
        if (level.isClientSide() || !PFConfig.milkCatalystsEnabled()) {
            return;
        }
        if (!(entity instanceof ItemEntity itemEntity) || !state.getFluidState().isSource()) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        MilkCatalyst catalyst = MilkCatalyst.fromStack(stack);
        if (catalyst == null || !catalyst.isEnabled()) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof MimicMilkSourceBlockEntity be) || be.getSynthesizedItem() == null) {
            return;
        }
        boolean depleting = PFConfig.SPEC.isLoaded() && PFConfig.DEPLETION_ENABLED.get();
        if ((catalyst == MilkCatalyst.COUNT || catalyst == MilkCatalyst.INFINITE) && !depleting) {
            return;
        }
        if (!be.applyCatalyst(catalyst)) {
            return;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(stack);
        }
        if (catalyst == MilkCatalyst.INFINITE) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS,
            0.7F, 1.3F + level.getRandom().nextFloat() * 0.2F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.0);
        }
        PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
            "mimic milk @%s: consumed %s catalyst (speed=%d quantity=%d infinite=%s remaining=%d)",
            pos, catalyst, be.getSpeedLevel(), be.getQuantityLevel(), be.isInfinite(), be.getSpawnsRemaining()));
    }

    /** Reject foreign fluids so water/lava can't wash the source away (mirrors SlimeMilkSourceBlock). */
    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos,
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
        // Whole-lane gate (#253): a Mimic Milk source spawns nothing when the EE lane
        // is disabled. Pause without rescheduling - a block update (or re-enable +
        // reload) revives it; nothing drains in the meantime.
        if (!com.flatts.productivefrogs.PFConfig.equivalenceEnabled()) {
            return;
        }
        if (!level.getFluidState(pos).isSource()
                || !(level.getBlockEntity(pos) instanceof MimicMilkSourceBlockEntity be)) {
            return;
        }
        Identifier itemId = be.getSynthesizedItem();
        if (itemId == null) {
            // Placed without an item (e.g. /setblock): inert, no reschedule.
            return;
        }
        // Endless (Infinite catalyst) never depletes, so it never drains.
        boolean depleting = PFConfig.SPEC.isLoaded() && PFConfig.DEPLETION_ENABLED.get() && !be.isInfinite();
        if (depleting && be.getSpawnsRemaining() <= 0) {
            drainToAir(level, pos);
            return;
        }
        boolean spawned = spawn(level, pos, random, itemId, be);
        if (!spawned) {
            scheduleNextSpawnTick(level, pos, random, be.getSpeedLevel());
            return;
        }
        if (depleting) {
            be.decrementSpawns();
            if (be.getSpawnsRemaining() <= 0) {
                drainToAir(level, pos);
                return;
            }
        }
        scheduleNextSpawnTick(level, pos, random, be.getSpeedLevel());
    }

    private static void drainToAir(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format("mimic milk @%s: depleted, drained", pos));
    }

    private static void scheduleNextSpawnTick(ServerLevel level, BlockPos pos, RandomSource random, int speedLevel) {
        int delay = MilkSpawnEconomy.intervalTicks(speedLevel, random);
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), delay);
    }

    /**
     * Spawn {@code 1 + quantityLevel} Mimic Slimes (Quantity catalyst), each carrying
     * the source's item. The budget is still spent once per event by the caller, so
     * Quantity is strictly additive. Returns true if at least one slime spawned.
     */
    private boolean spawn(ServerLevel level, BlockPos pos, RandomSource random, Identifier itemId,
                          MimicMilkSourceBlockEntity be) {
        int batch = MilkSpawnEconomy.batchQuantity(be.getQuantityLevel());
        int spawned = 0;
        for (int i = 0; i < batch; i++) {
            BlockPos spawnPos = chooseSpawnPos(level, pos);
            MimicSlime slime = PFEntities.MIMIC_SLIME.get().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (slime == null) {
                continue;
            }
            slime.setSize(1, true);
            slime.setSynthesizedItem(itemId);
            slime.snapTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                random.nextFloat() * 360F, 0F);
            level.addFreshEntity(slime);
            spawned++;
            PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                "mimic milk @%s: spawned Mimic Slime (%s) at %s", pos, itemId, spawnPos));
        }
        return spawned > 0;
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
    public ItemStack pickupBlock(@Nullable LivingEntity player, LevelAccessor level, BlockPos pos, BlockState state) {
        // Read the item + the full budget/catalyst set BEFORE super removes the block,
        // and stamp them onto the filled bucket so a buffed source survives the
        // world -> bucket round-trip (mirrors SlimeMilkSourceBlock.pickupBlock). Without
        // this, re-bucketing reset the source to the default budget and dropped Endless.
        MimicMilkSourceBlockEntity be = level.getBlockEntity(pos) instanceof MimicMilkSourceBlockEntity b ? b : null;
        Identifier itemId = be != null ? be.getSynthesizedItem() : null;
        int remaining = be != null ? be.getSpawnsRemaining() : 0;
        int capacity = be != null ? be.getSpawnsCapacity() : 0;
        int speed = be != null ? be.getSpeedLevel() : 0;
        int quantity = be != null ? be.getQuantityLevel() : 0;
        boolean infinite = be != null && be.isInfinite();
        ItemStack bucket = super.pickupBlock(player, level, pos, state);
        if (itemId != null && !bucket.isEmpty()) {
            bucket.set(com.flatts.productivefrogs.registry.PFDataComponents.SYNTHESIZED_ITEM.get(), itemId);
            bucket.set(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get(), remaining);
            bucket.set(com.flatts.productivefrogs.registry.PFDataComponents.MILK_CAPACITY.get(), capacity);
            if (speed > 0) {
                bucket.set(com.flatts.productivefrogs.registry.PFDataComponents.MILK_SPEED.get(), speed);
            }
            if (quantity > 0) {
                bucket.set(com.flatts.productivefrogs.registry.PFDataComponents.MILK_QUANTITY.get(), quantity);
            }
            if (infinite) {
                bucket.set(com.flatts.productivefrogs.registry.PFDataComponents.MILK_INFINITE.get(), true);
            }
        }
        return bucket;
    }
}
