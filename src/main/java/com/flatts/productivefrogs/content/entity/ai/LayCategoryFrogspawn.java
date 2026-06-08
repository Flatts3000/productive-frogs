package com.flatts.productivefrogs.content.entity.ai;

import com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity;
import com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.multiblock.TerrariumManager;
import com.flatts.productivefrogs.registry.PFBlocks;
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
import net.minecraft.world.level.material.Fluids;

/**
 * Brain behavior that lays a Primed Frog Egg block of the parent's category
 * on a water tile near the frog. Mirrors the vanilla
 * {@code TryLaySpawnOnWaterNearLand} behavior structure exactly, with two
 * differences:
 *
 * <ul>
 *   <li>The placed block is {@code PFBlocks.primedEgg(category)} for whatever
 *       category the {@link ResourceFrog} carries, instead of the hard-coded
 *       {@code minecraft:frogspawn}.</li>
 *   <li>It bails out (returns false, defers to the next behavior) if the
 *       entity is not a {@link ResourceFrog}. Defensive — the brain is only
 *       added to Resource Frogs, so this is just belt-and-suspenders.</li>
 * </ul>
 *
 * <p>Registered at priority 2 of the LAY_SPAWN activity (vanilla's
 * {@code TryLaySpawnOnWaterNearLand} runs at priority 3). On success this
 * behavior erases {@code IS_PREGNANT}, which causes vanilla's priority-3
 * behavior to skip — preventing a second frogspawn placement.
 */
public final class LayCategoryFrogspawn {

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
                boolean seeded = frog.hasPendingOffspring()
                    ? incubator.seedFromBreeding(frog.getCategory(),
                        frog.getPendingOffspringAppetite(), frog.getPendingOffspringBounty(),
                        frog.getPendingOffspringReach())
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
                    // there's no Incubator with room (or no Terrarium).
                    if (tryLayIntoIncubator(level, resourceFrog)) {
                        isPregnant.erase();
                        return true;
                    }
                    if (frog.isInWater() || !frog.onGround()) {
                        return false;
                    }

                    BlockPos below = frog.blockPosition().below();
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos waterPos = below.relative(direction);
                        boolean waterFaceOpen = level.getBlockState(waterPos)
                            .getCollisionShape(level, waterPos)
                            .getFaceShape(Direction.UP)
                            .isEmpty();
                        // Source-water required — matches PrimedFrogEggBlock.canSurvive,
                        // otherwise the placed block breaks on the next shape update.
                        var fluid = level.getFluidState(waterPos);
                        boolean isWaterSource = fluid.is(Fluids.WATER) && fluid.isSource();
                        if (!waterFaceOpen || !isWaterSource) {
                            continue;
                        }

                        BlockPos placePos = waterPos.above();
                        if (!level.getBlockState(placePos).isAir()) {
                            continue;
                        }

                        BlockState placed = PFBlocks.primedEgg(resourceFrog.getCategory())
                            .defaultBlockState();
                        level.setBlock(placePos, placed, 3);
                        level.gameEvent(
                            GameEvent.BLOCK_PLACE,
                            placePos,
                            GameEvent.Context.of(frog, placed)
                        );
                        level.playSound(
                            null,
                            frog,
                            SoundEvents.FROG_LAY_SPAWN,
                            SoundSource.BLOCKS,
                            1.0F,
                            1.0F
                        );
                        // Hand the offspring stats computed at conception
                        // (ResourceFrog#spawnChildFromBreeding) off to the egg's
                        // BlockEntity so they survive the frogspawn intermediary
                        // and reach the hatched tadpoles. A non-bred lay (no
                        // pending roll) leaves the egg statless, and the hatchlings
                        // mature into baseline (1/1/1) frogs.
                        // See docs/frog_breeding.md.
                        if (resourceFrog.hasPendingOffspring()
                            && level.getBlockEntity(placePos) instanceof PrimedFrogEggBlockEntity eggBe) {
                            eggBe.setPendingStats(
                                resourceFrog.getPendingOffspringAppetite(),
                                resourceFrog.getPendingOffspringBounty(),
                                resourceFrog.getPendingOffspringReach()
                            );
                            resourceFrog.clearPendingOffspring();
                        }
                        isPregnant.erase();
                        return true;
                    }
                    return false;
                }
            )
        );
    }
}
