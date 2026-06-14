package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
 * (#214, docs/lily_pad_perch.md) - the perch's driver.
 *
 * <p>It caches the claimed frog's UUID (transient - re-claimed on load), and splits
 * work by cost:
 * <ul>
 *   <li><b>Claim scan</b> (throttled, every {@link #SCAN_INTERVAL} ticks): an entity
 *       scan to find the nearest unclaimed Resource Frog when this pad has none. One
 *       frog per pad - a frog already claimed by another pad is skipped.</li>
 *   <li><b>Nudge</b> (<b>every tick</b>, cheap UUID lookup): re-assert the claim and
 *       re-set the frog's {@code WALK_TARGET} onto the pad with {@code closeEnough = 0}
 *       so it walks fully <i>onto</i> the pad and holds at the centre. Re-issuing every
 *       tick leaves the idle stroll no gap to fire, so the frog settles instead of
 *       drifting. Skipped while the frog is hunting, so it can still chase + eat slimes
 *       in range and resume the perch afterwards.</li>
 * </ul>
 *
 * <p>The claim lives on the frog as a short expiring link
 * ({@link ResourceFrog#getActivePerch()}); re-asserted while the pad stands, it lapses
 * on its own once the pad is broken (the pad stops ticking) - that is the release.
 */
public class SweetslimedLilyPadBlockEntity extends BlockEntity {

    /** Ticks between claim scans (the expensive entity scan) when this pad has no frog. */
    private static final int SCAN_INTERVAL = 20;
    /** Claim lifetime stamped on the frog; re-asserted every tick, so any few-tick value is safe. */
    private static final long CLAIM_TTL = 40L;
    /** WALK_TARGET expiry; re-issued every tick, long enough that a chunk unload clears it cleanly. */
    private static final int NUDGE_EXPIRY = 40;
    private static final float PERCH_SPEED = 1.0F;

    /** The frog this pad holds (transient; re-claimed after a reload). */
    @Nullable
    private UUID claimant;

    public SweetslimedLilyPadBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SWEETSLIMED_LILY_PAD.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SweetslimedLilyPadBlockEntity be) {
        if (!(level instanceof ServerLevel server) || !PFConfig.lilyPadPerchEnabled()) {
            return;
        }
        int range = PFConfig.lilyPadPerchRange();
        ResourceFrog frog = be.resolveClaimant(server, pos, range);
        if (frog == null && server.getGameTime() % SCAN_INTERVAL == 0L) {
            frog = findUnclaimedFrog(server, pos, range);
            if (frog != null) {
                be.claimant = frog.getUUID();
                PFDebug.log(PFDebug.Area.LIFECYCLE, () -> "perch @" + pos + ": claimed a frog");
            }
        }
        if (frog == null) {
            return;
        }
        // Re-assert the claim and pull the frog onto the pad - every tick, so the
        // stroll never gets a gap and the frog settles at the centre.
        frog.setPerch(pos, server.getGameTime() + CLAIM_TTL);
        nudge(pos, frog);
    }

    /** The cached claimant if it is still a live, in-range frog claimed by this pad (or claimable); else null (and the cache is cleared). */
    @Nullable
    private ResourceFrog resolveClaimant(ServerLevel level, BlockPos pos, int range) {
        if (claimant == null) {
            return null;
        }
        Entity entity = level.getEntity(claimant);
        if (!(entity instanceof ResourceFrog frog) || !frog.isAlive()) {
            claimant = null;
            return null;
        }
        BlockPos perch = frog.getActivePerch();
        if (perch != null && !perch.equals(pos)) {
            claimant = null; // another pad took it
            return null;
        }
        if (frog.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > (double) range * range) {
            claimant = null; // wandered out of range
            return null;
        }
        return frog;
    }

    /** Nearest live, in-range Resource Frog with no active perch (one frog per pad), or null. */
    @Nullable
    private static ResourceFrog findUnclaimedFrog(ServerLevel level, BlockPos pos, int range) {
        AABB box = new AABB(pos).inflate(range);
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        double rangeSq = (double) range * range;
        List<ResourceFrog> frogs = level.getEntitiesOfClass(ResourceFrog.class, box, ResourceFrog::isAlive);
        ResourceFrog best = null;
        double bestSq = Double.MAX_VALUE;
        for (ResourceFrog frog : frogs) {
            if (frog.getActivePerch() != null) {
                continue; // already claimed by some pad
            }
            double distSq = frog.distanceToSqr(cx, cy, cz);
            if (distSq <= rangeSq && distSq < bestSq) {
                bestSq = distSq;
                best = frog;
            }
        }
        return best;
    }

    private static void nudge(BlockPos pos, ResourceFrog frog) {
        Brain<?> brain = frog.getBrain();
        // Don't fight hunting: while the frog has (or is approaching) prey, leave its
        // walk target alone so it can reach and eat the slime; the perch re-pulls after.
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || brain.hasMemoryValue(MemoryModuleType.NEAREST_ATTACKABLE)) {
            return;
        }
        // closeEnough = 0: walk all the way onto the pad and hold the centre. Re-set
        // every tick by serverTick, so once arrived the frog stays put (no stroll gap).
        brain.setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, PERCH_SPEED, 0), NUDGE_EXPIRY);
    }
}
