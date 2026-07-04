package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/**
 * Bog Slime — the swamp parent species. Vanilla-flavoured {@link Slime}
 * subclass that exists as a thin marker class so
 * {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}
 * can tell parent species apart via {@code instanceof} when picking the
 * discovery pool's default category.
 *
 * <p>Per the V1.5 species-as-category redesign, Bog Slime replaces vanilla
 * {@code minecraft:slime} as the canonical BOG parent species. Vanilla
 * slimes still spawn naturally in swamps but no longer participate in the
 * Productive Frogs production system — only Bog Slime + variants do.
 *
 * <p>No gameplay overrides on the {@link Slime} base — same split mechanic,
 * movement, sounds. What changes: the default category BOG the discovery
 * handler picks for its split offspring, the texture (client-side via the
 * matching renderer), and the splash-particle colour.
 *
 * <p>Natural spawn: swamp + mangrove_swamp biomes, light level ≤ 7 (via
 * the shared parent-slime spawn predicate), weight 8, count 1-3 per spawn.
 * See {@code data/productivefrogs/neoforge/biome_modifier/add_bog_slime_spawn.json}.
 * Y-band restriction is handled implicitly by the biome's natural surface
 * topology; if hard Y-range filtering becomes necessary, add a dedicated
 * predicate in {@code PFModBusEvents}.
 */
public class BogSlime extends Slime {

    public BogSlime(EntityType<? extends BogSlime> type, Level level) {
        super(type, level);
    }

    /**
     * Tint the splash particle with the BOG category colour (swamp green)
     * instead of vanilla's hardcoded green ITEM_SLIME. Each parent species
     * emits its own colour so the splash carries a visual signal about
     * which slime line a player is looking at.
     */
    @Override
    protected ParticleOptions getParticleType() {
        int rgb = Category.BOG.tintRgb();
        org.joml.Vector3f color = new org.joml.Vector3f(
            ((rgb >> 16) & 0xFF) / 255.0F,
            ((rgb >> 8) & 0xFF) / 255.0F,
            (rgb & 0xFF) / 255.0F);
        return new DustParticleOptions(color, 1.0F);
    }
}
