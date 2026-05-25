package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/**
 * Tide Slime — the TIDE parent species. Vanilla-flavoured Slime subclass that
 * exists as a distinct {@link EntityType} so the parent species can be keyed in
 * the {@code parent_species} datapack registry, which
 * {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler} and
 * {@link com.flatts.productivefrogs.event.SlimeInfusionHandler} consult to map a
 * parent slime to its category.
 *
 * <p>No gameplay overrides on the vanilla {@link Slime} base — same split
 * mechanic, same movement, same sounds. What changes: the default category
 * TIDE the discovery handler picks for its split offspring, the texture
 * (client-side via {@code TideSlimeRenderer}), and the splash-particle
 * colour ({@link #getParticleType} returns a TIDE-tinted dust particle in
 * place of vanilla green {@code ITEM_SLIME}).
 *
 * <p>Per design Q2c ({@code docs/open_questions.md}), each non-vanilla
 * category gets its own parent species so all six categories have a passive
 * discovery path. TideSlime covers TIDE; Void covers VOID in the
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
        return Category.TIDE.tintParticle();
    }
}
