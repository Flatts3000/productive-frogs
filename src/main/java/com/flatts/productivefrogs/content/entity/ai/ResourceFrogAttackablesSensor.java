package com.flatts.productivefrogs.content.entity.ai;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.FrogStats;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.FrogAttackablesSensor;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.animal.frog.Frog;

/**
 * Sensor that augments vanilla {@link FrogAttackablesSensor} with a Category
 * filter and a Reach-scaled detection radius — used by {@link ResourceFrog} so a
 * Cave frog only considers Cave Resource Slimes as valid tongue targets, an
 * Infernal frog only Infernal slimes, etc., and so a higher-Reach frog hunts
 * across a larger pen.
 *
 * <p>Per design Q8 ("Frog AI specifics"): vanilla AI preserved completely
 * except for the prey eligibility filter. This sensor is the implementation
 * — everything else (jump pacing, water-proximity preference, breeding,
 * persistence) flows through the same vanilla brain tasks unchanged.
 *
 * <p>Non-{@link ResourceSlime} prey (vanilla slimes, magma cubes) is rejected
 * by design: the player must infuse those first to convert them into a
 * category-locked Resource Slime that frogs can eat. The category lives on the
 * entity instance (synced data), so the filter is a direct comparison rather
 * than a tag check.
 *
 * <p><b>Reach (docs/frog_breeding.md):</b> vanilla {@code FrogAttackablesSensor}
 * hard-codes a 10-block detection distance inside its own
 * {@code isMatchingEntity}, so we cannot widen it by delegating to super.
 * Instead this faithfully re-runs vanilla's four guards
 * ({@code HAS_HUNTING_COOLDOWN} absent, {@link Sensor#isEntityAttackable},
 * {@link Frog#canEat}, not an unreachable target) and swaps the constant for the
 * frog's {@link FrogStats#reachRadius} value. The candidate pool is gathered at
 * the {@code FOLLOW_RANGE} attribute (default 32), so any Reach radius up to 32
 * is already in-pool with no attribute change; a pack configuring a larger
 * radius would also need to raise follow range.
 */
public class ResourceFrogAttackablesSensor extends FrogAttackablesSensor {

    @Override
    protected boolean isMatchingEntity(LivingEntity attacker, LivingEntity target) {
        if (!(attacker instanceof ResourceFrog frog)) {
            // Defensive: this sensor is only attached to ResourceFrog brains, so
            // a non-ResourceFrog attacker would mean misconfigured wiring. Defer
            // to vanilla rather than silently dropping prey.
            return super.isMatchingEntity(attacker, target);
        }
        // Honour the Appetite eat cooldown exactly as vanilla does.
        if (frog.getBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN)) {
            return false;
        }
        // Terrarium backpressure (#185): a frog inside a formed Terrarium whose
        // Hatch is full refuses all prey - nothing it ate would have anywhere to
        // go, so it stops eating rather than wasting slimes (layer 1; the drop
        // handler is the safety net).
        if (isOwningHatchFull(frog)) {
            return false;
        }
        if (!Sensor.isEntityAttackable(frog, target) || !Frog.canEat(target) || isUnreachable(frog, target)) {
            return false;
        }
        // Midas (Equivalence lane, #253) eats ONLY Mimic Slimes - never the six
        // species' Resource Slimes - and uses the same reach gate. MimicSlime is a
        // sibling of ResourceSlime (extends vanilla Slime), so the species path
        // below already excludes it; this is the symmetric inclusion for Midas.
        if (frog.isMidas()) {
            return target instanceof com.flatts.productivefrogs.content.entity.MimicSlime
                && target.closerThan(frog, reachRadius(frog));
        }
        // Only Resource Slimes of the matching category are eligible prey.
        // Vanilla slimes/magma cubes get filtered out — they must be infused
        // (right-click with a primer-tagged item) before any frog will eat them.
        if (!(target instanceof ResourceSlime slime) || frog.getCategory() != slime.getCategory()) {
            if (PFDebug.on(PFDebug.Area.SENSOR) && target instanceof ResourceSlime slime) {
                PFDebug.logOnce(PFDebug.Area.SENSOR,
                    "sensor#" + frog.getId() + "/" + slime.getId() + "/false",
                    () -> String.format("frog id=%d category=%s vs slime id=%d category=%s -> filtered",
                        frog.getId(), frog.getCategory(), slime.getId(), slime.getCategory()));
            }
            return false;
        }
        int radius = reachRadius(frog);
        boolean inReach = target.closerThan(frog, radius);
        if (PFDebug.on(PFDebug.Area.SENSOR)) {
            PFDebug.logOnce(PFDebug.Area.SENSOR,
                "sensor#" + frog.getId() + "/" + slime.getId() + "/" + inReach,
                () -> String.format("frog id=%d category=%s reach=%d(r=%d) vs slime id=%d category=%s -> %s",
                    frog.getId(), frog.getCategory(), frog.effectiveReach(), radius,
                    slime.getId(), slime.getCategory(), inReach ? "ATTACKABLE" : "out-of-reach"));
        }
        return inReach;
    }

    /** True when the frog sits in a formed Terrarium whose Hatch is full (backpressure). */
    private static boolean isOwningHatchFull(ResourceFrog frog) {
        com.flatts.productivefrogs.content.multiblock.TerrariumManager.FormedTerrarium terrarium =
            com.flatts.productivefrogs.content.multiblock.TerrariumManager.containing(frog.level(), frog.position());
        return terrarium != null
            && frog.level().getBlockEntity(terrarium.hatchPos())
                instanceof com.flatts.productivefrogs.content.block.entity.HatchBlockEntity hatch
            && hatch.isFull();
    }

    /** The frog's Reach-scaled prey-scan radius (config-tunable; defaults 8..16). */
    private static int reachRadius(ResourceFrog frog) {
        return FrogStats.reachRadius(
            frog.effectiveReach(), PFConfig.reachRadiusMin(), PFConfig.reachRadiusMax(), PFConfig.statCap());
    }

    /**
     * Replicates vanilla {@code FrogAttackablesSensor#isUnreachableAttackTarget}
     * (private there): a target is skipped if it is in the frog's
     * {@code UNREACHABLE_TONGUE_TARGETS} memory (set when pathing to it fails).
     */
    private static boolean isUnreachable(LivingEntity frog, LivingEntity target) {
        return frog.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS)
            .map(list -> list.contains(target.getUUID()))
            .orElse(false);
    }
}
