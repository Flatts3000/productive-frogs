package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.CaveSlime;
import com.flatts.productivefrogs.content.entity.GeodeSlime;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.content.entity.TideSlime;
import com.flatts.productivefrogs.content.entity.VoidSlime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.levelgen.Heightmap;
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
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "resource_tadpole");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        assertNotNull(type, id + " must be registered");
        assertSame(PFEntities.RESOURCE_TADPOLE.get(), type, "DeferredHolder must resolve to the registered type");
    }

    @Test
    void resourceFrogIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "resource_frog");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
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

    @Test
    void resourceSlimeIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "resource_slime");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        assertNotNull(type, id + " must be registered");
        assertSame(PFEntities.RESOURCE_SLIME.get(), type, "DeferredHolder must resolve to the registered type");
    }

    @Test
    void resourceSlimeIsMonsterCategory() {
        EntityType<ResourceSlime> type = PFEntities.RESOURCE_SLIME.get();
        assertEquals(MobCategory.MONSTER, type.getCategory(),
            "ResourceSlime spawns as a monster (matches vanilla Slime mob category)");
    }

    @Test
    void resourceSlimeExtendsVanillaSlime() {
        // Same load-bearing inheritance argument as ResourceTadpole — we get
        // vanilla split mechanics, movement, sounds, spawn-rule eligibility,
        // and Bucketable behavior (for the future Slime Bucket) for free.
        assertTrue(Slime.class.isAssignableFrom(ResourceSlime.class),
            "ResourceSlime must extend vanilla Slime to inherit its behavior");
    }

    @Test
    void caveSlimeIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "cave_slime");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        assertNotNull(type, id + " must be registered");
        assertSame(PFEntities.CAVE_SLIME.get(), type, "DeferredHolder must resolve to the registered type");
    }

    @Test
    void caveSlimeIsMonsterCategory() {
        EntityType<CaveSlime> type = PFEntities.CAVE_SLIME.get();
        assertEquals(MobCategory.MONSTER, type.getCategory(),
            "CaveSlime spawns as a monster (matches vanilla Slime)");
    }

    @Test
    void caveSlimeExtendsVanillaSlime() {
        // Load-bearing for SlimeSplitDiscoveryHandler#categoryForParent — the
        // instanceof CaveSlime check assumes Slime split mechanics still fire.
        assertTrue(Slime.class.isAssignableFrom(CaveSlime.class),
            "CaveSlime must extend vanilla Slime so it splits like a slime");
    }

    @Test
    void geodeSlimeIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "geode_slime");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        assertNotNull(type, id + " must be registered");
        assertSame(PFEntities.GEODE_SLIME.get(), type, "DeferredHolder must resolve to the registered type");
    }

    @Test
    void geodeSlimeIsMonsterCategory() {
        EntityType<GeodeSlime> type = PFEntities.GEODE_SLIME.get();
        assertEquals(MobCategory.MONSTER, type.getCategory(),
            "GeodeSlime spawns as a monster (matches vanilla Slime)");
    }

    @Test
    void geodeSlimeExtendsVanillaSlime() {
        assertTrue(Slime.class.isAssignableFrom(GeodeSlime.class),
            "GeodeSlime must extend vanilla Slime so it splits like a slime");
    }

    @Test
    void tideSlimeIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "tide_slime");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        assertNotNull(type, id + " must be registered");
        assertSame(PFEntities.TIDE_SLIME.get(), type, "DeferredHolder must resolve to the registered type");
    }

    @Test
    void tideSlimeIsMonsterCategory() {
        EntityType<TideSlime> type = PFEntities.TIDE_SLIME.get();
        assertEquals(MobCategory.MONSTER, type.getCategory(),
            "TideSlime spawns as a monster (matches vanilla Slime)");
    }

    @Test
    void tideSlimeExtendsVanillaSlime() {
        assertTrue(Slime.class.isAssignableFrom(TideSlime.class),
            "TideSlime must extend vanilla Slime so it splits like a slime");
    }

    @Test
    void voidSlimeIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "void_slime");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        assertNotNull(type, id + " must be registered");
        assertSame(PFEntities.VOID_SLIME.get(), type, "DeferredHolder must resolve to the registered type");
    }

    @Test
    void voidSlimeIsMonsterCategory() {
        EntityType<VoidSlime> type = PFEntities.VOID_SLIME.get();
        assertEquals(MobCategory.MONSTER, type.getCategory(),
            "VoidSlime spawns as a monster (matches vanilla Slime)");
    }

    @Test
    void voidSlimeExtendsVanillaSlime() {
        assertTrue(Slime.class.isAssignableFrom(VoidSlime.class),
            "VoidSlime must extend vanilla Slime so it splits like a slime");
    }

    @Test
    void caveSlimeSpawnPlacementIsRegistered() {
        assertParentSpawnPlacement(PFEntities.CAVE_SLIME.get(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
    }

    @Test
    void geodeSlimeSpawnPlacementIsRegistered() {
        assertParentSpawnPlacement(PFEntities.GEODE_SLIME.get(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
    }

    @Test
    void tideSlimeSpawnPlacementIsRegistered() {
        // Tide is the odd one — OCEAN_FLOOR heightmap so the spawn algorithm
        // picks the topmost solid block ignoring water (i.e., actual sea floor),
        // matching how vanilla Drowned spawns.
        assertParentSpawnPlacement(PFEntities.TIDE_SLIME.get(), Heightmap.Types.OCEAN_FLOOR);
    }

    @Test
    void voidSlimeSpawnPlacementIsRegistered() {
        assertParentSpawnPlacement(PFEntities.VOID_SLIME.get(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
    }

    private static void assertParentSpawnPlacement(EntityType<?> type, Heightmap.Types expectedHeightmap) {
        SpawnPlacementType placement = SpawnPlacements.getPlacementType(type);
        assertNotNull(placement, type + " must have a registered SpawnPlacementType after the mod-bus event fires");
        assertSame(SpawnPlacementTypes.ON_GROUND, placement,
            "parent species spawn on solid ground (vanilla Slime placement)");
        Heightmap.Types heightmap = SpawnPlacements.getHeightmapType(type);
        assertNotNull(heightmap, type + " must have a registered Heightmap");
        assertSame(expectedHeightmap, heightmap);
    }
}
