package com.flatts.productivefrogs.content.entity.ai;

import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.FrogAttackablesSensor;

/**
 * Sensor that augments vanilla {@link FrogAttackablesSensor} with a Category
 * filter — used by {@link ResourceFrog} so a Metallic frog only considers
 * Metallic Resource Slimes as valid tongue targets, an Infernal frog only
 * Infernal slimes, etc.
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
 */
public class ResourceFrogAttackablesSensor extends FrogAttackablesSensor {

    @Override
    protected boolean isMatchingEntity(LivingEntity attacker, LivingEntity target) {
        if (!super.isMatchingEntity(attacker, target)) {
            return false;
        }
        if (!(attacker instanceof ResourceFrog frog)) {
            // Defensive: this sensor is only attached to ResourceFrog brains, so
            // a non-ResourceFrog attacker would mean misconfigured wiring. Fall
            // through to vanilla acceptance rather than silently dropping prey.
            return true;
        }
        // Only Resource Slimes of the matching category are eligible prey.
        // Vanilla slimes/magma cubes get filtered out — they must be infused
        // (right-click with a primer-tagged item) before any frog will eat them.
        if (target instanceof ResourceSlime slime) {
            boolean match = frog.getCategory() == slime.getCategory();
            if (PFDebug.on(PFDebug.Area.SENSOR)) {
                PFDebug.logOnce(PFDebug.Area.SENSOR,
                    "sensor#" + frog.getId() + "/" + slime.getId() + "/" + match,
                    () -> String.format("frog id=%d category=%s vs slime id=%d category=%s -> %s",
                        frog.getId(), frog.getCategory(), slime.getId(), slime.getCategory(),
                        match ? "ATTACKABLE" : "filtered"));
            }
            return match;
        }
        return false;
    }
}
