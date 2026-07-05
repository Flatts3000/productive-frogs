package com.flatts.productivefrogs.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Runtime mob -&gt; representative colour resolver (#281 Phase 3, maintainer
 * ruling: Mob Slurry is coloured for the mob it is for). Resolves through the
 * mob's SPAWN EGG item sprite via {@link SynthesizedTint#colorFor} - the same
 * sprite-average machinery the Equivalence lane uses for arbitrary items, with
 * its cache and its no-cache-on-failure retry semantics.
 *
 * <p>Client-only (reads the texture atlas). Returns {@code -1} when the mob
 * can't be resolved to a colour (unknown id, no spawn egg, atlas not ready) -
 * callers pick their own fallback.
 */
public final class MobColors {

    private MobColors() {
        // static resolver
    }

    /** Opaque ARGB for {@code entityTypeId}'s mob, or {@code -1} when unresolvable. */
    public static int colorFor(Identifier entityTypeId) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId).orElse(null);
        if (type == null) {
            return -1;
        }
        // 26.1: byId returns Optional<Holder<Item>>.
        Item egg = SpawnEggItem.byId(type).map(net.minecraft.core.Holder::value).orElse(null);
        if (egg == null) {
            return -1;
        }
        int argb = SynthesizedTint.colorFor(egg);
        // SynthesizedTint's opaque-white fallback means "couldn't sample yet";
        // report unresolvable so the caller's fallback shows instead of white.
        return argb == 0xFFFFFFFF ? -1 : argb;
    }
}
