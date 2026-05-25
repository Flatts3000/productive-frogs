package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/**
 * Infernal Slime — the nether parent species. Vanilla-flavoured {@link Slime}
 * subclass that exists as a distinct {@link EntityType} so the parent species
 * can be keyed in the {@code parent_species} datapack registry, which
 * {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler} and
 * {@link com.flatts.productivefrogs.event.SlimeInfusionHandler} consult to map a
 * parent slime to its category.
 *
 * <p>Per the V1.5 species-as-category redesign, Infernal Slime replaces
 * vanilla {@code minecraft:magma_cube} as the canonical INFERNAL parent
 * species. Vanilla magma cubes still spawn naturally in nether wastes but no
 * longer participate in the Productive Frogs production system — only
 * Infernal Slime + variants do.
 *
 * <p>No gameplay overrides on the {@link Slime} base — same split mechanic,
 * movement, sounds. What changes: the default category INFERNAL the
 * discovery handler picks for its split offspring, the texture (client-side
 * via the matching renderer), and the splash-particle colour.
 *
 * <p>Natural spawn: nether_wastes + basalt_deltas + soul_sand_valley with
 * any light level (via {@code PFModBusEvents.checkInfernalSlimeSpawnRules}
 * which drops the darkness gate vanilla magma cubes also skip), weight 10,
 * count 1-3 per spawn. See
 * {@code data/productivefrogs/neoforge/biome_modifier/add_infernal_slime_spawn.json}.
 */
public class InfernalSlime extends Slime {

    public InfernalSlime(EntityType<? extends InfernalSlime> type, Level level) {
        super(type, level);
    }

    /**
     * Tint the splash particle with the INFERNAL category colour (lava red)
     * instead of vanilla's hardcoded green ITEM_SLIME.
     */
    @Override
    protected ParticleOptions getParticleType() {
        return Category.INFERNAL.tintParticle();
    }
}
