package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/**
 * Void Slime — the VOID parent species. Vanilla-flavoured Slime subclass that
 * exists as a distinct {@link EntityType} so the parent species can be keyed in
 * the {@code parent_species} datapack registry, which
 * {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler} and
 * {@link com.flatts.productivefrogs.event.SlimeInfusionHandler} consult to map a
 * parent slime to its category.
 *
 * <p>No gameplay overrides on the vanilla {@link Slime} base — same split
 * mechanic, same movement, same sounds. What changes: the default category
 * VOID the discovery handler picks for its split offspring, the texture
 * (client-side via {@code VoidSlimeRenderer}), and the splash-particle
 * colour ({@link #getParticleType} returns a VOID-tinted dust particle in
 * place of vanilla green {@code ITEM_SLIME}).
 *
 * <p>Per design Q2c ({@code docs/open_questions.md}), each non-vanilla
 * category gets its own parent species so all six categories have a passive
 * discovery path. VoidSlime closes the set — with Bog, Cave, Geode, Tide, and
 * Infernal, all six categories now have a native parent species and a
 * natural-discovery path.
 *
 * <p>Natural spawn rules are deferred to a polish PR — currently VoidSlime
 * is reachable via {@code /summon} and via the spawn egg item.
 */
public class VoidSlime extends Slime {

    public VoidSlime(EntityType<? extends VoidSlime> type, Level level) {
        super(type, level);
    }

    /** VOID-tinted splash particle in place of vanilla green. See CaveSlime. */
    @Override
    protected ParticleOptions getParticleType() {
        return Category.VOID.tintParticle();
    }
}
