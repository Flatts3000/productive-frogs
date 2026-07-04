package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The Warden Altar's display frog (#279) - "Wardenbane". A display-only
 * {@link AltarDisplayFrog} perched on the Hatch at the pit floor; the drifting
 * sculk-soul particles read as an echo-devouring deep-dark apex frog.
 */
public class WardenbaneFrog extends AltarDisplayFrog {

    public WardenbaneFrog(EntityType<? extends WardenbaneFrog> type, Level level) {
        super(type, level);
    }

    @Override
    protected ParticleOptions ambientParticle() {
        return ParticleTypes.SCULK_SOUL;
    }

    /** The altar uses this type for spawn/lifecycle reconciliation. */
    public static EntityType<WardenbaneFrog> type() {
        return PFEntities.WARDENBANE.get();
    }
}
