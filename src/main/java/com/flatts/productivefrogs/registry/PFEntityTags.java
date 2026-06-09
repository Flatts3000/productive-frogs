package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
        Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "frogs"));

    private PFEntityTags() {
        // constants holder
    }

    /**
     * Whether an entity is a frog - any vanilla {@link Frog} (covers the vanilla
     * frog, the Resource Frog, and frog mobs that subclass it) or any entity type a
     * pack has added to the {@link #FROGS} tag. Resource Tadpoles are not frogs.
     */
    public static boolean isFrog(Entity entity) {
        return entity instanceof Frog || entity.getType().is(FROGS);
    }
}
