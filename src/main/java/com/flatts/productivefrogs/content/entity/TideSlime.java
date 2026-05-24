package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * Tide Slime — the TIDE parent species. Vanilla-flavoured Slime subclass
 * that exists as a thin marker class so {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}
 * can tell parent species apart via {@code instanceof} when picking the
 * discovery pool's default category.
 *
 * <p>No gameplay overrides on the vanilla {@link Slime} base — same split
 * mechanic, same movement, same sounds. What changes: the default category
 * TIDE the discovery handler picks for its split offspring, the texture
 * (client-side via {@code TideSlimeRenderer}), and the splash-particle
 * colour ({@link #getParticleType} returns an TIDE-tinted
 * {@link DustParticleOptions} in place of vanilla green {@code ITEM_SLIME}).
 *
 * <p>Per design Q2c ({@code docs/open_questions.md}), each non-vanilla
 * category gets its own parent species so all six categories have a passive
 * discovery path. TideSlime covers TIDE; Void covers ARCANE in the
 * subsequent PR.
 *
 * <p>Natural spawn rules are deferred to a polish PR — currently TideSlime
 * is reachable via {@code /summon} and via the spawn egg item.
 */
public class TideSlime extends Slime {

    public TideSlime(EntityType<? extends TideSlime> type, Level level) {
        super(type, level);
    }

    /** TIDE-tinted splash particle in place of vanilla green. See CaveSlime. */
    @Override
    protected ParticleOptions getParticleType() {
        int rgb = Category.TIDE.tintRgb();
        Vector3f color = new Vector3f(
            ((rgb >> 16) & 0xFF) / 255.0F,
            ((rgb >>  8) & 0xFF) / 255.0F,
            (rgb         & 0xFF) / 255.0F);
        return new DustParticleOptions(color, 1.0F);
    }
}
