package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The dragon altar's display frog (#249) - "Dragonsbane". A display-only
 * {@link AltarDisplayFrog} perched on the bedrock plinth; the ender aura
 * (converging void particles) reads as otherworldly.
 */
public class DragonsbaneFrog extends AltarDisplayFrog {

    public DragonsbaneFrog(EntityType<? extends DragonsbaneFrog> type, Level level) {
        super(type, level);
    }

    @Override
    protected ParticleOptions ambientParticle() {
        return ParticleTypes.REVERSE_PORTAL;
    }

    /** The altar uses this type for spawn/lifecycle reconciliation. */
    public static EntityType<DragonsbaneFrog> type() {
        return PFEntities.DRAGONSBANE.get();
    }
}
