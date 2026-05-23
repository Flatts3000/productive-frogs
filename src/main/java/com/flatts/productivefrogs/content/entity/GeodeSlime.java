package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/**
 * Geode Slime — the GEM parent species. Vanilla-flavoured Slime subclass that
 * exists as a thin marker class so {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}
 * can tell parent species apart via {@code instanceof} when picking the
 * discovery pool's default category.
 *
 * <p>No gameplay overrides on the vanilla {@link Slime} base — same split
 * mechanic, same movement, same sounds. What changes: the default category
 * GEM the discovery handler picks for its split offspring, the texture
 * (client-side via {@code GeodeSlimeRenderer}), and the splash-particle
 * colour ({@link #getParticleType} returns a GEM-tinted
 * {@link DustParticleOptions} in place of vanilla green {@code ITEM_SLIME}).
 *
 * <p>Per design Q2c ({@code docs/open_questions.md}), each non-vanilla
 * category gets its own parent species so all six categories have a passive
 * discovery path. GeodeSlime covers GEM; Tide/Void cover AQUATIC/ARCANE in
 * subsequent PRs.
 *
 * <p>Natural spawn rules are deferred to a polish PR — currently GeodeSlime
 * is reachable via {@code /summon} and via the spawn egg item.
 */
public class GeodeSlime extends Slime {

    public GeodeSlime(EntityType<? extends GeodeSlime> type, Level level) {
        super(type, level);
    }

    /** GEM-tinted splash particle in place of vanilla green. See CaveSlime. */
    @Override
    protected ParticleOptions getParticleType() {
        return new DustParticleOptions(Category.GEM.tintRgb(), 1.0F);
    }
}
