package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/**
 * Cave Slime — the MINERAL parent species. Vanilla-flavoured Slime subclass
 * that exists as a thin marker class so {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}
 * can tell parent species apart via {@code instanceof} when picking the
 * discovery pool's default category.
 *
 * <p>No gameplay overrides on the vanilla {@link Slime} base — same split
 * mechanic, same movement, same sounds. What changes: the default category
 * MINERAL the discovery handler picks for its split offspring, the texture
 * (client-side via {@code CaveSlimeRenderer}), and the splash-particle
 * colour ({@link #getParticleType} returns a MINERAL-tinted
 * {@link DustParticleOptions} in place of vanilla's hardcoded green
 * {@code ITEM_SLIME}).
 *
 * <p>Per design Q2c ({@code docs/open_questions.md}), each non-vanilla
 * category gets its own parent species so all six categories have a passive
 * discovery path. CaveSlime covers MINERAL; the other three land in
 * subsequent PRs (Geode/Tide/Void).
 *
 * <p>Natural spawn rules are deferred to a polish PR — currently CaveSlime
 * is reachable via {@code /summon} and via the spawn egg item.
 */
public class CaveSlime extends Slime {

    public CaveSlime(EntityType<? extends CaveSlime> type, Level level) {
        super(type, level);
    }

    /**
     * Tint the splash particle with the MINERAL category colour instead of
     * vanilla's hardcoded green ITEM_SLIME. Matches the ResourceSlime fix:
     * each parent species emits its own colour so the splash carries a
     * visual signal about which slime line a player is looking at.
     */
    @Override
    protected ParticleOptions getParticleType() {
        return new DustParticleOptions(Category.MINERAL.tintRgb(), 1.0F);
    }
}
