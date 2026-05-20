package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.frog.Tadpole;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for our entity-type registry wiring. Runs in moddev's
 * unitTest environment, so {@code BuiltInRegistries.ENTITY_TYPE} is populated
 * with our types by the time the test executes.
 *
 * <p>Catches: the registration didn't run, the entity ID was typoed, the
 * builder used the wrong mob category or hitbox, or our DeferredHolder
 * doesn't actually resolve to the registered type.
 */
class PFEntitiesTest {

    @Test
    void resourceTadpoleIsRegistered() {
        Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "resource_tadpole");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        assertNotNull(type, id + " must be registered");
        assertSame(PFEntities.RESOURCE_TADPOLE.get(), type, "DeferredHolder must resolve to the registered type");
    }

    @Test
    void resourceFrogIsRegistered() {
        Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "resource_frog");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        assertNotNull(type, id + " must be registered");
        assertSame(PFEntities.RESOURCE_FROG.get(), type, "DeferredHolder must resolve to the registered type");
    }

    @Test
    void resourceTadpoleMobCategoryAndHitboxMatchVanilla() {
        EntityType<ResourceTadpole> type = PFEntities.RESOURCE_TADPOLE.get();
        assertEquals(MobCategory.WATER_CREATURE, type.getCategory(),
            "tadpoles spawn as water creatures (matches vanilla Tadpole)");
        // Vanilla hitbox constants — reusing them keeps vanilla renderer geometry valid.
        assertEquals(Tadpole.HITBOX_WIDTH, type.getWidth(), 1e-6f);
        assertEquals(Tadpole.HITBOX_HEIGHT, type.getHeight(), 1e-6f);
    }

    @Test
    void resourceFrogIsCreatureCategory() {
        EntityType<ResourceFrog> type = PFEntities.RESOURCE_FROG.get();
        assertEquals(MobCategory.CREATURE, type.getCategory(),
            "frogs spawn as land creatures (matches vanilla Frog)");
    }

    @Test
    void resourceTadpoleExtendsVanillaTadpole() {
        // The mod relies on this inheritance — vanilla brain, AI, bucket tag,
        // slimeball-acceleration, and water-survival timer all come for free via
        // the Tadpole superclass. If this assertion ever fails we've drifted off
        // the "stay close to vanilla" rail.
        assertTrue(Tadpole.class.isAssignableFrom(ResourceTadpole.class),
            "ResourceTadpole must extend vanilla Tadpole to inherit its behavior");
    }
}
