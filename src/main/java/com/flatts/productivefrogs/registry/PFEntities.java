package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity registry for Productive Frogs.
 *
 * <p>{@link ResourceTadpole} and {@link ResourceFrog} are sized to match
 * their vanilla counterparts so vanilla renderers can be reused without
 * tuning hitboxes per-mod. Mob category matches vanilla too:
 * {@code WATER_CREATURE} for tadpoles, {@code CREATURE} for frogs.
 */
public final class PFEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, ProductiveFrogs.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ResourceTadpole>> RESOURCE_TADPOLE =
        ENTITIES.register(
            "resource_tadpole",
            () -> EntityType.Builder.<ResourceTadpole>of(ResourceTadpole::new, MobCategory.WATER_CREATURE)
                .sized(Tadpole.HITBOX_WIDTH, Tadpole.HITBOX_HEIGHT)
                .eyeHeight(Tadpole.HITBOX_HEIGHT * 0.5F)
                .build(net.minecraft.resources.ResourceKey.create(
                    Registries.ENTITY_TYPE,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "resource_tadpole")
                ))
        );

    public static final DeferredHolder<EntityType<?>, EntityType<ResourceFrog>> RESOURCE_FROG =
        ENTITIES.register(
            "resource_frog",
            () -> EntityType.Builder.<ResourceFrog>of(ResourceFrog::new, MobCategory.CREATURE)
                .sized(0.5F, 0.5F)
                .eyeHeight(0.4F)
                .build(net.minecraft.resources.ResourceKey.create(
                    Registries.ENTITY_TYPE,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "resource_frog")
                ))
        );

    private PFEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
