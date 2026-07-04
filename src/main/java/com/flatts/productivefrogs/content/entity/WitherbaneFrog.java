package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The Wither Altar's display frog (#247) - "Witherbane". A display-only
 * {@link AltarDisplayFrog} pinned at one end of the arena, facing the ritual;
 * the rising soul particles read as soul-devouring.
 */
public class WitherbaneFrog extends AltarDisplayFrog {

    public WitherbaneFrog(EntityType<? extends WitherbaneFrog> type, Level level) {
        super(type, level);
    }

    @Override
    protected ParticleOptions ambientParticle() {
        return ParticleTypes.SOUL;
    }

    /** The altar uses this type for spawn/lifecycle reconciliation. */
    public static EntityType<WitherbaneFrog> type() {
        return PFEntities.WITHERBANE.get();
    }
}
