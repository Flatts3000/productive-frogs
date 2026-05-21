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
 * uniform random in {@code [200, 600]} ticks (10–30s) per spawn. No retry
 * cadence — every tick produces a slime; see the spawn-position picker
 * below for why this always succeeds.
 *
 * <p>Spawn position selection: scan all 26 blocks in the 3×3×3 cube
 * surrounding the source. The first one whose top face is sturdy AND whose
 * block-above is non-motion-blocking becomes the landing slot; the slime
 * appears on top of that solid neighbour. If no eligible solid neighbour
 * exists (source floating in pure air), the slime spawns at the source's
 * own position — the milk fluid itself is non-collision, so this fallback
 * always works. Entity overlap is never a blocker.
 *
 * <p>Iteration order biases toward natural "rim" spawns: same-y plane
 * first (cardinals then diagonals), then the below plane (so a typical
 * milk pool on solid ground spills slimes horizontally adjacent to the
 * source instead of two blocks up), then the above plane.
 *
 * <p>Depletion is J5; this class spawns indefinitely.
 */
public class SlimeMilkSourceBlock extends LiquidBlock {

    private static final int MIN_DELAY_TICKS = 200;
    private static final int MAX_DELAY_TICKS = 600;

    /**
     * Offsets to the 26 neighbours of the source block, ordered so the
     * picker prefers natural-looking spawn positions: same-y plane first
     * (cardinals → diagonals), then the below plane, then above. A typical
     * milk-pool-on-solid-ground placement matches a same-y cardinal first
     * (rim spawn), so the deeper iteration rarely runs.
     */
    private static final int[][] NEIGHBOUR_OFFSETS = {
        // y=0 cardinals — rim of the pool
        {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
        // y=0 diagonals — corners of the rim
        {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
        // y=-1 cardinals — floor of the pool; their .above() is the source's horizontal neighbour
        {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
        // y=-1 center — directly below the source; .above() is the source itself
        {0, -1, 0},
        // y=-1 diagonals
        {1, -1, 1}, {1, -1, -1}, {-1, -1, 1}, {-1, -1, -1},
        // y=+1 cardinals — above the rim
        {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
        // y=+1 center — directly above the source
        {0, 1, 0},
        // y=+1 diagonals
        {1, 1, 1}, {1, 1, -1}, {-1, 1, 1}, {-1, 1, -1},
    };

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
            scheduleNextSpawnTick(serverLevel, pos, level.getRandom());
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
        spawn(level, pos, random);
        scheduleNextSpawnTick(level, pos, random);
    }

    private static void scheduleNextSpawnTick(ServerLevel level, BlockPos pos, RandomSource random) {
        int delay = MIN_DELAY_TICKS + random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), delay);
    }

    private void spawn(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos spawnPos = chooseSpawnPos(level, pos);
        Slime slime = createSlimeForVariant(level);
        if (slime == null) {
            // Defensive — EntityType.create returns null only when the
            // entity-type registry is in a broken state. Skip the spawn but
            // don't fail loud; reschedule normally so the next tick retries.
            return;
        }
        slime.setSize(1, true);
        // Center on the block's XZ; sit on top of whatever's at spawnPos.below()
        // so the entity's feet line up with the block face rather than clipping.
        slime.snapTo(spawnPos.getX() + 0.5,
                     spawnPos.getY(),
                     spawnPos.getZ() + 0.5,
                     random.nextFloat() * 360F,
                     0F);
        level.addFreshEntity(slime);
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
     * Pick the spawn slot by scanning the 3×3×3 cube around the source.
     * For each of the 26 neighbours, if that neighbour's top face is
     * sturdy AND the position directly above it is non-motion-blocking,
     * the spawn lands on top of that neighbour.
     *
     * <p>If no solid neighbour exists (source floating in air, or buried
     * in liquid with no solid edges), falls back to the source's own
     * position — the milk fluid block has noCollision, so spawning the
     * slime inside it always succeeds. Entity overlap is never a blocker.
     */
    private static BlockPos chooseSpawnPos(ServerLevel level, BlockPos source) {
        for (int[] off : NEIGHBOUR_OFFSETS) {
            BlockPos neighbour = source.offset(off[0], off[1], off[2]);
            BlockPos above = neighbour.above();
            if (level.getBlockState(neighbour).isFaceSturdy(level, neighbour, Direction.UP)
                && !level.getBlockState(above).blocksMotion()) {
                return above;
            }
        }
        // No eligible solid neighbour — spawn inside the liquid itself.
        return source;
    }
}
