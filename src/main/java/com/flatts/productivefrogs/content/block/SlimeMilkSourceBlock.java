package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.jspecify.annotations.Nullable;

/**
 * Slime Milk's placeable form. Subclasses {@link LiquidBlock} to retain
 * vanilla flow behavior while adding the J4 spawn loop: each source block
 * periodically rolls a tick that produces one size-1 slime matching the
 * fluid's variant.
 *
 * <p>The block-tick this class uses (scheduled via
 * {@link LevelAccessor#scheduleTick(BlockPos, net.minecraft.world.level.block.Block, int)})
 * is independent of the fluid-tick the underlying {@code FlowingFluid} uses
 * for spread/decay — they're stored under different scheduled-tick keys,
 * so this override does not interfere with flow.
 *
 * <p>Spawn cadence (per {@code docs/farming.md} §Slime spawning from milk):
 * uniform random in {@code [200, 600]} ticks (10–30s) on a successful spawn,
 * uniform random in {@code [40, 80]} ticks (2–4s) when the slot is blocked.
 * The short retry is to recover quickly once a transient blocker (player or
 * frog briefly stepping on the spawn slot) clears; the long cadence keeps
 * the steady-state production rate honest.
 *
 * <p>Spawn position selection: prefer the top of a solid horizontal
 * neighbour so the slime lands on a block rather than splashing back into
 * the pool. Only when no such neighbour exists does the slime spawn in the
 * column above the source itself. Existing entities in the slot don't
 * block the spawn — overlapping is fine; the new slime simply appears at
 * the same coordinates and the physics engine separates them within a
 * tick or two.
 *
 * <p>Depletion is J5; this class spawns indefinitely.
 */
public class SlimeMilkSourceBlock extends LiquidBlock {

    private static final int SUCCESS_MIN_TICKS = 200;
    private static final int SUCCESS_MAX_TICKS = 600;
    private static final int RETRY_MIN_TICKS = 40;
    private static final int RETRY_MAX_TICKS = 80;

    private final String variant;

    public SlimeMilkSourceBlock(FlowingFluid fluid, String variant, Properties properties) {
        super(fluid, properties);
        this.variant = variant;
    }

    public String variant() {
        return variant;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // Only source blocks spawn. Flowing blocks share the same Block
        // class but have a non-source FluidState, so the FluidState check is
        // the real gate. Don't schedule on the client — block-tick scheduling
        // is server-only.
        if (level instanceof ServerLevel serverLevel && level.getFluidState(pos).isSource()) {
            scheduleNextSpawnTick(serverLevel, pos, level.getRandom(), true);
        }
    }

    /**
     * Widened to public so {@code PFGameTests} can invoke the spawn loop
     * directly on the concrete reference, the same shortcut
     * {@code PrimedFrogEggBlock} uses to bypass its 3600–12000-tick hatch
     * schedule. Don't change visibility — the in-world tests depend on it.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        // If the block was knocked out or down-converted to flowing (e.g.
        // a neighbour pulled the source dry), stop ticking — no need to
        // reschedule; if the source is restored later, onPlace will start
        // the loop again on placement.
        if (!level.getFluidState(pos).isSource()) {
            return;
        }
        boolean spawned = attemptSpawn(level, pos, random);
        scheduleNextSpawnTick(level, pos, random, spawned);
    }

    private static void scheduleNextSpawnTick(ServerLevel level, BlockPos pos, RandomSource random, boolean spawned) {
        int min = spawned ? SUCCESS_MIN_TICKS : RETRY_MIN_TICKS;
        int max = spawned ? SUCCESS_MAX_TICKS : RETRY_MAX_TICKS;
        int delay = min + random.nextInt(max - min + 1);
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), delay);
    }

    /**
     * Returns true if a slime spawned; false if no eligible position was
     * found. Caller uses the result to decide whether to apply the long
     * (success) or short (retry) reschedule cadence.
     */
    private boolean attemptSpawn(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos spawnPos = chooseSpawnPos(level, pos);
        if (spawnPos == null) {
            return false;
        }
        Slime slime = createSlimeForVariant(level);
        if (slime == null) {
            return false;
        }
        slime.setSize(1, true);
        // Center on the block's XZ; sit on top of whatever's at spawnPos.below()
        // so the entity's feet line up with the block face rather than clipping.
        slime.snapTo(spawnPos.getX() + 0.5,
                     spawnPos.getY(),
                     spawnPos.getZ() + 0.5,
                     random.nextFloat() * 360F,
                     0F);
        return level.addFreshEntity(slime);
    }

    @Nullable
    private Slime createSlimeForVariant(ServerLevel level) {
        if (variant.equals("vanilla")) {
            return EntityType.SLIME.create(level, EntitySpawnReason.TRIGGERED);
        }
        if (variant.equals("magma")) {
            return EntityType.MAGMA_CUBE.create(level, EntitySpawnReason.TRIGGERED);
        }
        ResourceSlime resource = PFEntities.RESOURCE_SLIME.get().create(level, EntitySpawnReason.TRIGGERED);
        if (resource == null) {
            return null;
        }
        // setVariant syncs category from the SlimeVariant registry. The
        // variant id is constructed from this block's variant name — the
        // contract that every entry in PFFluidTypes.VARIANTS (minus
        // "vanilla" / "magma") has a matching SlimeVariant is enforced by
        // SlimeMilkerBlockTest's variant-drift sanity check.
        resource.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant));
        return resource;
    }

    /**
     * Pick the spawn slot. Prefer the top of a solid horizontal neighbour
     * so the slime lands on the rim of the pool. Fall back to the column
     * above the source itself (which is typically more milk, but slimes
     * survive in liquid so this is a sane default).
     *
     * <p>Returns null only when every candidate position contains a
     * motion-blocking block (terrain, doors, etc.) — entities already at
     * the position are NOT a blocker; the new slime just spawns on top of
     * them. In the null case the caller skips the spawn and reschedules
     * with the short-retry cadence.
     */
    @Nullable
    private static BlockPos chooseSpawnPos(ServerLevel level, BlockPos source) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos rim = source.relative(dir).above();
            BlockPos support = rim.below();
            if (level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
                && !level.getBlockState(rim).blocksMotion()) {
                return rim;
            }
        }
        BlockPos column = source.above();
        return level.getBlockState(column).blocksMotion() ? null : column;
    }
}
