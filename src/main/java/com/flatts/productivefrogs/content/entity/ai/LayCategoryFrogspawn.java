package com.flatts.productivefrogs.content.entity.ai;

import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
                        boolean isWater = level.getFluidState(waterPos).is(Fluids.WATER);
                        if (!waterFaceOpen || !isWater) {
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
                        isPregnant.erase();
                        return true;
                    }
                    return false;
                }
            )
        );
    }
}
