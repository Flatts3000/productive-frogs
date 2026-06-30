package com.flatts.productivefrogs.content.entity.ai;

import com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity;
import com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.multiblock.TerrariumManager;
import com.flatts.productivefrogs.registry.PFBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * Brain behavior that lays a Primed Frog Egg block of the parent's category
 * on a water tile near the frog.
 *
 * <p>Where vanilla {@code TryLaySpawnOnWaterNearLand} scans the horizontal
 * neighbours of {@code frog.blockPosition().below()} - geometry that assumes the
 * frog stands at a whole-block height - this behavior does a footing-aware
 * <em>contact lay</em> onto the surface of a nearby water source. That makes
 * laying strictly more reliable than vanilla: it works when the frog stands on
 * mud, a top/bottom slab, or snow layers (sub-full collision tops that sink the
 * frog and shift {@code blockPosition()} down a whole block - the cause of
 * issue #270), when the frog is submerged (it lays at the column surface above
 * itself), and on the true surface of a deep pool (it climbs to the topmost
 * source with air above instead of placing one level too low).
 *
 * <p>Two PF-specific differences from vanilla remain:
 * <ul>
 *   <li>The placed block is {@code PFBlocks.primedEgg(category)} (or the Midas
 *       egg, #253) for whatever the {@link ResourceFrog} carries, not the
 *       hard-coded {@code minecraft:frogspawn}.</li>
 *   <li>It bails out (returns false, defers to the next behavior) if the entity
 *       is not a {@link ResourceFrog} - defensive; the brain is only added to
 *       Resource Frogs.</li>
 * </ul>
 *
 * <p>Registered at priority 2 of the LAY_SPAWN activity (vanilla's
 * {@code TryLaySpawnOnWaterNearLand} runs at priority 3). On success this
 * behavior erases {@code IS_PREGNANT}, which causes vanilla's priority-3
 * behavior to skip - preventing a second frogspawn placement.
 */
public final class LayCategoryFrogspawn {

    /**
     * Vertical reach, in blocks below the frog's footing, that the surface
     * search looks for a water column. One block of slack covers a frog standing
     * a step above the pool rim (e.g. on a block bordering a sunken pool) while
     * staying local enough that the frog never lays into some distant pool it
     * merely happens to be above.
     */
    private static final int DOWN_REACH = 1;

    /**
     * Vertical reach above the frog. Lets a submerged frog climb its own column
     * to the surface source (vanilla just bails on {@code isInWater()}); two
     * blocks covers the typical breeding pool while keeping the lay local.
     */
    private static final int UP_REACH = 2;

    private LayCategoryFrogspawn() {
        // utility class, behavior factory only
    }

    /**
     * Redirect a bred frog's lay into the nearest Incubator with room when the
     * frog is inside a formed Terrarium. Carries pending-offspring stats straight
     * into the Incubator (so bred lineage flows frog -&gt; Incubator BE -&gt; matured
     * frog, no stat-bearing item needed); a non-bred lay seeds baseline. Returns
     * false when there is no Terrarium or no Incubator with room.
     */
    private static boolean tryLayIntoIncubator(ServerLevel level, ResourceFrog frog) {
        TerrariumManager.FormedTerrarium terrarium = TerrariumManager.containing(level, frog.position());
        if (terrarium == null) {
            return false;
        }
        for (BlockPos incubatorPos : terrarium.incubators()) {
            if (level.getBlockEntity(incubatorPos) instanceof IncubatorBlockEntity incubator && incubator.hasRoom()) {
                // Midas (#253) carries its marker into the Incubator so a bred Midas
                // matures into a Midas (not a VOID frog). A bred pair always has
                // pending stats, so the baseline path never carries Midas - fine.
                boolean seeded = frog.hasPendingOffspring()
                    ? incubator.seedFromBreeding(frog.getCategory(),
                        frog.getPendingOffspringAppetite(), frog.getPendingOffspringBounty(),
                        frog.getPendingOffspringReach(), frog.isMidas())
                    : incubator.seedBaseline(frog.getCategory());
                if (seeded) {
                    frog.clearPendingOffspring();
                    level.playSound(null, incubatorPos, SoundEvents.FROG_LAY_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
                    return true;
                }
            }
        }
        return false;
    }

    public static BehaviorControl<Frog> create() {
        return BehaviorBuilder.create(
            instance -> instance.group(
                instance.absent(MemoryModuleType.ATTACK_TARGET),
                instance.present(MemoryModuleType.WALK_TARGET),
                instance.present(MemoryModuleType.IS_PREGNANT)
            ).apply(
                instance,
                (attackTarget, walkTarget, isPregnant) -> (level, frog, time) -> {
                    if (!(frog instanceof ResourceFrog resourceFrog)) {
                        return false;
                    }
                    // Terrarium redirect (#185): inside a formed Terrarium, lay into
                    // the nearest Incubator with room instead of seeking water - no
                    // loose frogspawn in the cavity, and bred stats flow straight
                    // into the Incubator. Falls through to the water-lay below when
                    // there's no Incubator with room (or no Terrarium). Midas carries
                    // its marker through (#253), so a bred Midas matures Midas.
                    if (tryLayIntoIncubator(level, resourceFrog)) {
                        isPregnant.erase();
                        return true;
                    }
                    if (tryLayOnWaterSurface(level, resourceFrog)) {
                        isPregnant.erase();
                        return true;
                    }
                    return false;
                }
            )
        );
    }

    /**
     * Place the frog's egg on the surface of a water source it is in contact
     * with. Returns false (keep looking) when the frog is mid-air or no valid
     * surface source sits within reach.
     */
    private static boolean tryLayOnWaterSurface(ServerLevel level, ResourceFrog frog) {
        // Lay while planted on the bank or while standing in the water (a
        // submerged frog lays at the surface above it); never mid-jump. The
        // surface search is the real gate - it only succeeds beside/over water.
        if (!frog.onGround() && !frog.isInWater()) {
            return false;
        }

        BlockPos placePos = findLaySurface(level, frog);
        if (placePos == null) {
            return false;
        }

        // Midas (#253) lays its own egg block (named "Midas Egg", hatches Midas);
        // the six species lay their category egg.
        BlockState placed = (frog.isMidas()
            ? PFBlocks.MIDAS_FROG_EGG.get()
            : PFBlocks.primedEgg(frog.getCategory()))
            .defaultBlockState();
        level.setBlock(placePos, placed, 3);
        level.gameEvent(GameEvent.BLOCK_PLACE, placePos, GameEvent.Context.of(frog, placed));
        level.playSound(null, frog, SoundEvents.FROG_LAY_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);

        // Hand the offspring stats computed at conception
        // (ResourceFrog#spawnChildFromBreeding) off to the egg's BlockEntity so
        // they survive the frogspawn intermediary and reach the hatched tadpoles.
        // A non-bred lay (no pending roll) leaves the egg statless, and the
        // hatchlings mature into baseline (1/1/1) frogs. See docs/frog_breeding.md.
        // (The Midas egg block stamps its own midas marker in onPlace, so a bred
        // Midas egg hatches Midas - #253.)
        if (frog.hasPendingOffspring()
                && level.getBlockEntity(placePos) instanceof PrimedFrogEggBlockEntity eggBe) {
            eggBe.setPendingStats(
                frog.getPendingOffspringAppetite(),
                frog.getPendingOffspringBounty(),
                frog.getPendingOffspringReach()
            );
            frog.clearPendingOffspring();
        }
        return true;
    }

    /**
     * Find the air position where the egg should be placed: the block directly
     * above the surface water source of a column the frog is in contact with.
     * Tolerant of sub-full footing (mud/slabs/snow), submersion, deep pools, and
     * pool edges. Returns {@code null} when no eligible surface sits within reach.
     *
     * <p>Search order is the four horizontal neighbours first (the vanilla
     * "lay from the bank" feel), then the frog's own column (the submerged case).
     *
     * <p>Exposed (the {@code PFGameTests} lay-geometry seam for issue #270 lives
     * in another package): driving the full brain in-world is fragile, so the lay
     * geometry is asserted directly.
     */
    @Nullable
    public static BlockPos findLaySurface(ServerLevel level, Frog frog) {
        BlockPos frogPos = frog.blockPosition();
        // Footing block, mud/slab/snow-aware (getBlockPosBelowThatAffectsMyMovement
        // accounts for sub-full collision tops, unlike blockPosition().below()).
        int footY = frog.getBlockPosBelowThatAffectsMyMovement().getY();
        // Search band: from a little above the frog (so a submerged frog reaches
        // the surface above it) down to just below its footing (so sub-full
        // footing like mud still finds the source at the frog's own level).
        int top = frogPos.getY() + UP_REACH;
        int bottom = footY - DOWN_REACH;

        List<BlockPos> columns = new ArrayList<>(5);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            columns.add(frogPos.relative(direction));
        }
        columns.add(frogPos);

        for (BlockPos column : columns) {
            BlockPos surface = surfaceLayTarget(level, column, top, bottom);
            if (surface != null) {
                return surface;
            }
        }
        return null;
    }

    /**
     * For one column, find the topmost WATER source within {@code [bottom, top]}
     * and return the air block directly above it (the egg's spot) when that block
     * is air - i.e. the column's open surface. Returns {@code null} when the
     * column has no source in range, or its topmost in-range source is capped by
     * a non-air block (a covered column - bail gracefully rather than burying the
     * egg). Source-only ({@code fluid.isSource()}) so the placed egg survives the
     * next shape update, matching {@code PrimedFrogEggBlock.canSurvive}.
     */
    @Nullable
    private static BlockPos surfaceLayTarget(ServerLevel level, BlockPos column, int top, int bottom) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(column.getX(), top, column.getZ());
        for (int y = top; y >= bottom; y--) {
            cursor.setY(y);
            FluidState fluid = level.getFluidState(cursor);
            if (fluid.is(Fluids.WATER) && fluid.isSource()) {
                BlockPos above = cursor.above();
                // The topmost source's cover: air -> the open surface (place here);
                // anything else -> this column is roofed, so don't bury the egg.
                return level.getBlockState(above).isAir() ? above.immutable() : null;
            }
        }
        return null;
    }
}
