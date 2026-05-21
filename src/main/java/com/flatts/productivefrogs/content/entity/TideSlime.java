package com.flatts.productivefrogs.content.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/**
 * Tide Slime — the AQUATIC parent species. Vanilla-flavoured Slime subclass
 * that exists as a thin marker class so {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}
 * can tell parent species apart via {@code instanceof} when picking the
 * discovery pool's default category.
 *
 * <p>No behavioural overrides on the vanilla {@link Slime} base — same split
 * mechanic, same movement, same sounds. The only thing that changes is the
 * default category AQUATIC the discovery handler picks for its split
 * offspring, plus the texture (handled client-side by
 * {@code TideSlimeRenderer}).
 *
 * <p>Per design Q2c ({@code docs/open_questions.md}), each non-vanilla
 * category gets its own parent species so all six categories have a passive
 * discovery path. TideSlime covers AQUATIC; Void covers ARCANE in the
 * subsequent PR.
 *
 * <p>Natural spawn rules are deferred to a polish PR — currently TideSlime
 * is reachable via {@code /summon} and via the spawn egg item.
 */
public class TideSlime extends Slime {

    public TideSlime(EntityType<? extends TideSlime> type, Level level) {
        super(type, level);
    }
}
