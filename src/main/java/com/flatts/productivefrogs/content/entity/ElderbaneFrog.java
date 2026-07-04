package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The Elder Guardian Altar's display frog (#280) - "Elderbane". A display-only
 * {@link AltarDisplayFrog} pinned swimming in the Monument Well's water cavity;
 * the bubble trail reads right at home in the flooded tank.
 */
public class ElderbaneFrog extends AltarDisplayFrog {

    public ElderbaneFrog(EntityType<? extends ElderbaneFrog> type, Level level) {
        super(type, level);
    }

    @Override
    protected ParticleOptions ambientParticle() {
        return ParticleTypes.BUBBLE;
    }

    /** The altar uses this type for spawn/lifecycle reconciliation. */
    public static EntityType<ElderbaneFrog> type() {
        return PFEntities.ELDERBANE.get();
    }
}
