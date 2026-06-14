package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for a {@link com.flatts.productivefrogs.content.block.SweetslimedLilyPadBlock}
 * (#214, docs/lily_pad_perch.md). It is the perch's driver - and deliberately
 * <b>stateless</b>: the claim ("this frog is pinned to this pad") lives on the frog
 * ({@link ResourceFrog#getActivePerch()}) as a short-lived, expiring link, not here.
 *
 * <p>On a throttled server tick the pad:
 * <ul>
 *   <li>finds its frog - the incumbent already claiming this pad (kept), else the
 *       nearest unclaimed Resource Frog in range (newly claimed);</li>
 *   <li>re-asserts the claim with a fresh short TTL, so it persists while the pad
 *       stands and lapses on its own once the pad is broken (the pad stops
 *       re-asserting) - that is the whole release mechanism, no break hook needed;</li>
 *   <li>nudges the frog back toward the pad (via the vanilla {@code WALK_TARGET}
 *       memory) when it has strayed and is <b>not</b> hunting - so a pinned frog
 *       holds position but still chases and eats slimes in range.</li>
 * </ul>
 *
 * <p>One frog per pad falls out for free: a frog already claimed by another pad
 * (its {@code getActivePerch()} points elsewhere) is skipped, and only the one frog
 * the pad re-asserts each scan keeps a live claim.
 */
public class SweetslimedLilyPadBlockEntity extends BlockEntity {

    /** Ticks between claim/assign scans (cheap entity scan, so a modest cadence). */
    private static final int SCAN_INTERVAL = 20;
    /** Claim lifetime stamped on the frog each scan; must exceed {@link #SCAN_INTERVAL} so it never lapses while the pad stands. */
    private static final long CLAIM_TTL = 60L;
    /** Pull the frog back only once it has strayed beyond this many blocks (squared) on the horizontal. */
    private static final double STRAY_NUDGE_SQ = 4.0;
    private static final float PERCH_SPEED = 1.0F;
    /** Expiry on the nudge WALK_TARGET so a stale pull clears itself if the scan stops. */
    private static final int NUDGE_EXPIRY = 40;

    public SweetslimedLilyPadBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SWEETSLIMED_LILY_PAD.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SweetslimedLilyPadBlockEntity be) {
        if (!(level instanceof ServerLevel server) || !PFConfig.lilyPadPerchEnabled()) {
            return;
        }
        if (server.getGameTime() % SCAN_INTERVAL != 0L) {
            return;
        }
        int range = PFConfig.lilyPadPerchRange();
        ResourceFrog frog = findClaim(server, pos, range);
        if (frog == null) {
            return;
        }
        frog.setPerch(pos, server.getGameTime() + CLAIM_TTL);
        nudge(pos, frog);
        PFDebug.log(PFDebug.Area.LIFECYCLE, () -> String.format(
            "perch @%s: holding frog %s (category=%s)", pos, frog.getUUID(), frog.getCategory()));
    }

    /**
     * The frog this pad holds: the incumbent already claiming this pad (kept, so we
     * don't thrash claims), else the nearest unclaimed Resource Frog within range.
     * A frog claimed by a different pad is skipped (one frog per pad). Null if none.
     */
    @Nullable
    private static ResourceFrog findClaim(ServerLevel level, BlockPos pos, int range) {
        AABB box = new AABB(pos).inflate(range);
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double rangeSq = (double) range * range;
        List<ResourceFrog> frogs = level.getEntitiesOfClass(ResourceFrog.class, box, ResourceFrog::isAlive);
        ResourceFrog best = null;
        double bestSq = Double.MAX_VALUE;
        for (ResourceFrog frog : frogs) {
            BlockPos claimed = frog.getActivePerch();
            if (claimed != null && !claimed.equals(pos)) {
                continue; // claimed by another pad
            }
            double distSq = frog.distanceToSqr(cx, cy, cz);
            if (distSq > rangeSq) {
                continue; // out of range (an incumbent that wandered off lapses here)
            }
            if (claimed != null) {
                return frog; // our incumbent, still in range - keep it
            }
            if (distSq < bestSq) {
                bestSq = distSq;
                best = frog;
            }
        }
        return best;
    }

    private static void nudge(BlockPos pos, ResourceFrog frog) {
        Brain<?> brain = frog.getBrain();
        // Don't fight hunting: a frog tracking prey keeps its own walk target so it
        // can reach and eat the slime; the perch re-pulls afterwards.
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || brain.hasMemoryValue(MemoryModuleType.NEAREST_ATTACKABLE)) {
            return;
        }
        double dx = (pos.getX() + 0.5) - frog.getX();
        double dz = (pos.getZ() + 0.5) - frog.getZ();
        if (dx * dx + dz * dz <= STRAY_NUDGE_SQ) {
            return; // close enough - let it idle on the pad
        }
        brain.setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, PERCH_SPEED, 1), NUDGE_EXPIRY);
    }
}
