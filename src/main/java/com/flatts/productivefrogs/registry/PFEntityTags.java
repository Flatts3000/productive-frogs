package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.frog.Frog;

/**
 * Entity {@link TagKey}s owned by Productive Frogs, plus the shared "is this a
 * frog?" predicate.
 *
 * <p>{@link #FROGS} ({@code productivefrogs:frogs}) is the single source of truth
 * for which entities the mod treats as frogs - the vanilla frog and the Resource
 * Frog by default, extensible by a pack for a modded frog that does not subclass
 * vanilla {@link Frog}. Both the Frog Net (catch target) and the frog-leg drop
 * (#194) key off {@link #isFrog} so they agree on what counts.
 */
public final class PFEntityTags {

    public static final TagKey<EntityType<?>> FROGS = TagKey.create(
        Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "frogs"));

    /**
     * Mobs the Ender Net refuses to catch (#281 Phase 3, maintainer ruling):
     * ships containing {@code #c:bosses} (wither, ender dragon, modded bosses),
     * datapack-extensible for anything else a pack wants un-nettable.
     */
    public static final TagKey<EntityType<?>> ENDER_NET_DENYLIST = TagKey.create(
        Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "ender_net_denylist"));

    /**
     * Mobs that must never become Mob Slurry (#281 Phase 3): {@code #c:bosses}
     * plus every Productive Frogs mob - the mod's own creatures have their own
     * economies (milk on the slime side, breeding on the frog side), and
     * slurrying a Resource Slime would both bypass the milk lane and respawn
     * variant-less husks. Checked by the Slurry Press (produce) AND the Mob
     * Slurry Basin (accept), so even tampered NBT can't route around it.
     */
    public static final TagKey<EntityType<?>> SLURRY_DENYLIST = TagKey.create(
        Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slurry_denylist"));

    private PFEntityTags() {
        // constants holder
    }

    /**
     * Whether an entity is a frog - any vanilla {@link Frog} (covers the vanilla
     * frog, the Resource Frog, and frog mobs that subclass it) or any entity type a
     * pack has added to the {@link #FROGS} tag. Resource Tadpoles are not frogs.
     */
    public static boolean isFrog(Entity entity) {
        return entity instanceof Frog || entity.getType().builtInRegistryHolder().is(FROGS);
    }
}
