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
    /** Lily pad VoxelShape height (1.5/16): the frog rests on the pad's top surface. */
    private static final double PAD_TOP = 0.09375;
    /** "On the pad" horizontal radius (squared): within ~1.5 blocks of the pad centre. */
    private static final double ON_PAD_RADIUS_SQ = 2.25;
    /** "On the pad" vertical band: within 3 blocks of the pad - catches a frog sunk into the water below it. */
    private static final double ON_PAD_VERTICAL = 3.0;
    /** Vertical half-extent of the claim scan box (frogs perch at water level; no need for a full cube). */
    private static final int PERCH_VERTICAL = 6;

    /**
     * The frog this pad holds. Persisted (NBT "Claimant") so a server/world reload
     * re-pins the same frog on the first tick via {@link #resolveClaimant} - an O(1)
     * UUID lookup that tolerates re-adopting its own frog - instead of waiting on the
     * throttled rescan. Without persistence the pad couldn't re-adopt its frog while
     * that frog's still-valid {@link ResourceFrog#getActivePerch()} made the scan skip
     * it, so the claim lapsed and the frog visibly wandered off before being walked
     * back. The frog persists the matching link ({@link ResourceFrog} "PerchPad").
     */
    @Nullable
    private UUID claimant;

    public SweetslimedLilyPadBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SWEETSLIMED_LILY_PAD.get(), pos, state);
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (claimant != null) {
            tag.putUUID("Claimant", claimant);
        }
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        claimant = tag.hasUUID("Claimant") ? tag.getUUID("Claimant") : null;
    }

    /** Set the claimed frog (or null), marking the BE dirty so the claim persists across a reload. */
    private void setClaimant(@Nullable UUID id) {
        if (!java.util.Objects.equals(this.claimant, id)) {
            this.claimant = id;
            setChanged();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SweetslimedLilyPadBlockEntity be) {
        if (!(level instanceof ServerLevel server) || !PFConfig.lilyPadPerchEnabled()) {
            return;
        }
        int range = PFConfig.lilyPadPerchRange();
        ResourceFrog frog = be.resolveClaimant(server, pos, range);
        // The only non-trivial cost is the unclaimed-pad entity scan. A claimed pad
        // (the normal case) never reaches it - resolveClaimant is an O(1) UUID lookup.
        // Stagger the scan by position so a field of idle pads doesn't all scan the
        // same tick, and only scan when this pad has no frog.
        if (frog == null && (server.getGameTime() + Math.floorMod(pos.asLong(), SCAN_INTERVAL)) % SCAN_INTERVAL == 0L) {
            frog = findUnclaimedFrog(server, pos, range);
            if (frog != null) {
                be.setClaimant(frog.getUUID());
                PFDebug.log(PFDebug.Area.LIFECYCLE, () -> "perch @" + pos + ": claimed a frog");
            }
        }
        if (frog == null) {
            return;
        }
        // Re-assert the claim and hold the frog on the pad - every tick.
        frog.setPerch(pos, server.getGameTime() + CLAIM_TTL);
        holdOnPad(pos, frog);
    }

    /** The cached claimant if it is still a live, in-range frog claimed by this pad (or claimable); else null (and the cache is cleared). */
    @Nullable
    private ResourceFrog resolveClaimant(ServerLevel level, BlockPos pos, int range) {
        if (claimant == null) {
            return null;
        }
        Entity entity = level.getEntity(claimant);
        if (!(entity instanceof ResourceFrog frog) || !frog.isAlive()) {
            setClaimant(null);
            return null;
        }
        BlockPos perch = frog.getActivePerch();
        if (perch != null && !perch.equals(pos)) {
            setClaimant(null); // another pad took it
            return null;
        }
        if (frog.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > (double) range * range) {
            setClaimant(null); // wandered out of range
            return null;
        }
        return frog;
    }

    /** Nearest live, in-range Resource Frog with no active perch (one frog per pad), or null. */
    @Nullable
    private static ResourceFrog findUnclaimedFrog(ServerLevel level, BlockPos pos, int range) {
        // Frogs perch at water level, so keep the scan box flat vertically - a full
        // 2*range cube would scan several times the volume for no benefit.
        AABB box = new AABB(pos).inflate(range, Math.min(range, PERCH_VERTICAL), range);
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

    /**
     * Approach, then teleport-and-pin. While the frog is away from the pad it gets a
     * walk target toward it; the instant its bounding box intersects the pad it is
     * teleported so its centre sits on the pad's centre, on top, and is held there
     * each tick - navigation stopped, velocity zeroed, long jump suppressed - so it
     * reliably stays put and never hops off. The brain keeps ticking, so the frog's
     * tongue still eats slimes that come within range of the pad.
     */
    private static void holdOnPad(BlockPos pos, ResourceFrog frog) {
        Brain<?> brain = frog.getBrain();
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        double dx = cx - frog.getX();
        double dz = cz - frog.getZ();
        double horizSq = dx * dx + dz * dz;
        double dy = Math.abs((pos.getY() + 0.5) - frog.getY());
        // "On the pad" = horizontally over it and within a vertical band. Crucially NOT
        // a strict bbox-intersect with the pad block: a frog approaching over open water
        // sinks through the non-solid pad into the water below (bbox below the pad), and
        // must still count so the teleport LIFTS it up onto the pad rather than missing.
        boolean onPad = horizSq <= ON_PAD_RADIUS_SQ && dy <= ON_PAD_VERTICAL;
        if (!onPad) {
            // Still approaching - walk onto the pad. Don't override an active hunt;
            // let it reach an in-range slime first, it returns to the pad after.
            if (!brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    && !brain.hasMemoryValue(MemoryModuleType.NEAREST_ATTACKABLE)) {
                brain.setMemoryWithExpiry(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, PERCH_SPEED, 0), NUDGE_EXPIRY);
            }
            return;
        }
        // On the pad: pin the frog's centre to the pad centre, on top, and freeze it.
        double py = pos.getY() + PAD_TOP;
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        // Keep the long jump on cooldown so it never launches off the pad.
        brain.setMemoryWithExpiry(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, NUDGE_EXPIRY, NUDGE_EXPIRY);
        frog.getNavigation().stop();
        frog.setDeltaMovement(0.0, 0.0, 0.0);
        // moveTo (not setPos) so the prev-position resets too - a clean instant
        // teleport with no client-side glide. Keep its yaw so it can still face prey.
        frog.moveTo(cx, py, cz, frog.getYRot(), frog.getXRot());
        frog.setOnGround(true);
    }
}
