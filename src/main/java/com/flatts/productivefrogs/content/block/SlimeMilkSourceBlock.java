package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

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
 * uniform random in {@code [PFConfig.MIN_SPAWN_INTERVAL_TICKS, PFConfig.MAX_SPAWN_INTERVAL_TICKS]}
 * (defaults 200–600 ticks = 10–30s) per spawn. No retry cadence — every
 * tick produces a slime; see the spawn-position picker below for why
 * this always succeeds.
 *
 * <p>Depletion (J5): when {@link PFConfig#DEPLETION_ENABLED} is true (the
 * default), each source block carries a {@link #SPAWNS_REMAINING}
 * blockstate that starts at {@link PFConfig#DEPLETION_COUNT} on placement
 * and decrements by one per spawn. When it reaches zero the next tick
 * removes the block (drains the pool). When depletion is disabled the
 * counter is ignored entirely and the block spawns indefinitely.
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
 */
public class SlimeMilkSourceBlock extends LiquidBlock {

    /**
     * Max value of the depletion counter blockstate property. Capped at 16
     * because the property's range is fixed at compile time — see the
     * comment on {@link PFConfig#DEPLETION_COUNT} for the trade-off.
     */
    public static final int MAX_SPAWNS_REMAINING = 16;

    /**
     * Depletion counter persisted into the blockstate. {@code N} means
     * "{@code N} more spawns until this source drains". On a fresh
     * placement the block's default state is {@code MAX_SPAWNS_REMAINING};
     * each successful spawn decrements by one; reaching zero removes the
     * block on the next tick. Read only when {@link PFConfig#DEPLETION_ENABLED}
     * is true; when disabled the counter is left untouched on the state
     * but ignored by the tick logic.
     */
    public static final IntegerProperty SPAWNS_REMAINING =
        IntegerProperty.create("spawns_remaining", 0, MAX_SPAWNS_REMAINING);

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

    /**
     * Test-only override for {@link PFConfig#DEPLETION_ENABLED}. When
     * non-null, takes precedence over the config — GameTests set this so
     * their assertions don't depend on whatever the developer has in
     * {@code productivefrogs-common.toml}. Always null in production.
     * Volatile so the test thread's write is visible to the server thread
     * that runs the block tick. Mirrors the {@code testOverride} pattern
     * on {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}.
     */
    @Nullable
    public static volatile Boolean depletionEnabledOverride = null;

    private final String variant;

    public SlimeMilkSourceBlock(FlowingFluid fluid, String variant, Properties properties) {
        super(fluid, properties);
        this.variant = variant;
        // Default state starts at the max counter — placements via item
        // (bucket use), /setblock without explicit properties, fluid placer,
        // etc. all use this default. Config-driven start values lower than
        // MAX are applied in onPlace by overwriting the property before
        // scheduling the first tick.
        registerDefaultState(defaultBlockState().setValue(SPAWNS_REMAINING, MAX_SPAWNS_REMAINING));
    }

    public String variant() {
        return variant;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SPAWNS_REMAINING);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // Only source blocks spawn. Flowing blocks share the same Block
        // class but have a non-source FluidState, so the FluidState check is
        // the real gate. Don't schedule on the client — block-tick scheduling
        // is server-only.
        if (!(level instanceof ServerLevel serverLevel) || !level.getFluidState(pos).isSource()) {
            return;
        }
        // Honour the config's depletionCount even when it's lower than
        // MAX_SPAWNS_REMAINING (the property's max). Placement through item
        // use or /setblock-without-properties leaves the state at the
        // default (MAX); overwrite it here. If the existing state already
        // carries a lower counter (e.g. /setblock with explicit
        // spawns_remaining=5, or block-load from a saved chunk), keep it.
        int configured = PFConfig.DEPLETION_COUNT.get();
        if (oldState.getBlock() != state.getBlock()
            && state.getValue(SPAWNS_REMAINING) == MAX_SPAWNS_REMAINING
            && configured < MAX_SPAWNS_REMAINING) {
            serverLevel.setBlock(pos, state.setValue(SPAWNS_REMAINING, configured), Block.UPDATE_CLIENTS);
        }
        scheduleNextSpawnTick(serverLevel, pos, level.getRandom());
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

        if (depletionEnabled()) {
            int remaining = state.getValue(SPAWNS_REMAINING);
            if (remaining <= 0) {
                // Counter exhausted — drain the pool. Set air explicitly
                // rather than calling level.removeBlock: vanilla's
                // removeBlock for a fluid block routes through
                // fluidState.createLegacyBlock(), which puts the source
                // block right back at its default state (counter resets
                // to MAX). We need a true air swap to drain.
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                    "source @%s: depleted, drained to air (variant=%s)", pos, variant));
                return;
            }
            spawn(level, pos, random);
            level.setBlock(pos, state.setValue(SPAWNS_REMAINING, remaining - 1), Block.UPDATE_CLIENTS);
        } else {
            spawn(level, pos, random);
        }
        scheduleNextSpawnTick(level, pos, random);
    }

    /** Effective depletionEnabled value — {@link #depletionEnabledOverride} wins when set, else config. */
    private static boolean depletionEnabled() {
        Boolean override = depletionEnabledOverride;
        return override != null ? override : PFConfig.DEPLETION_ENABLED.get();
    }

    private static void scheduleNextSpawnTick(ServerLevel level, BlockPos pos, RandomSource random) {
        int min = PFConfig.MIN_SPAWN_INTERVAL_TICKS.get();
        int max = PFConfig.MAX_SPAWN_INTERVAL_TICKS.get();
        // Defensive: if an operator inverted min/max in the config, fall back
        // to a single deterministic delay rather than throwing on the
        // negative-range nextInt call.
        int delay = max <= min ? min : min + random.nextInt(max - min + 1);
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), delay);
    }

    private void spawn(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos spawnPos = chooseSpawnPos(level, pos);
        Slime slime = createSlimeForVariant(level);
        if (slime == null) {
            // Defensive — EntityType.create returns null only when the
            // entity-type registry is in a broken state. Skip the spawn but
            // don't fail loud; reschedule normally so the next tick retries.
            PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                "source @%s: slime create failed for variant=%s (skip)", pos, variant));
            return;
        }
        slime.setSize(1, true);
        // Center on the block's XZ; sit on top of whatever's at spawnPos.below()
        // so the entity's feet line up with the block face rather than clipping.
        slime.moveTo(spawnPos.getX() + 0.5,
                     spawnPos.getY(),
                     spawnPos.getZ() + 0.5,
                     random.nextFloat() * 360F,
                     0F);
        level.addFreshEntity(slime);
        PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
            "source @%s: spawned %s slime at %s (%s)", pos, variant, spawnPos,
            spawnPos.equals(pos) ? "inside-fluid fallback" : "on neighbour"));
    }

    @Nullable
    private Slime createSlimeForVariant(ServerLevel level) {
        if (variant.equals("vanilla")) {
            return EntityType.SLIME.create(level);
        }
        if (variant.equals("magma")) {
            return EntityType.MAGMA_CUBE.create(level);
        }
        ResourceSlime resource = PFEntities.RESOURCE_SLIME.get().create(level);
        if (resource == null) {
            return null;
        }
        // setVariant syncs category from the SlimeVariant registry. The
        // variant id is constructed from this block's variant name — the
        // contract that every entry in PFFluidTypes.VARIANTS (minus
        // "vanilla" / "magma") has a matching SlimeVariant is enforced by
        // SlimeMilkerBlockTest's variant-drift sanity check.
        resource.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant));
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
