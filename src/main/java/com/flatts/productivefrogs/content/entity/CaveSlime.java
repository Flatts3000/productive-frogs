package com.flatts.productivefrogs.content.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;

/**
 * Cave Slime — the MINERAL parent species. Vanilla-flavoured Slime subclass
 * that exists as a thin marker class so {@link com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler}
 * can tell parent species apart via {@code instanceof} when picking the
 * discovery pool's default category.
 *
 * <p>No behavioural overrides on the vanilla {@link Slime} base — same split
 * mechanic, same movement, same sounds. The only thing that changes is the
 * default category MINERAL the discovery handler picks for its split
 * offspring, plus the texture (handled client-side by
 * {@code CaveSlimeRenderer}).
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
}
