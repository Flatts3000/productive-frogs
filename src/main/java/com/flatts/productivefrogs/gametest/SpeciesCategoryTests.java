package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.event.SlimeInfusionHandler;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;

/**
 * V1.5 species-as-category regression pins: vanilla slimes are NOT parent species,
 * all six PF species resolve to their {@link Category}, and an already-infused
 * {@link ResourceSlime} hard-rejects re-resolution.
 */
final class SpeciesCategoryTests {

    private SpeciesCategoryTests() {
    }

    static void register() {
        PFGameTests.test("vanilla_slime_is_not_a_parent_species", 20, SpeciesCategoryTests::vanillaSlimeIsNotAParentSpecies);
        PFGameTests.test("vanilla_magma_cube_is_not_a_parent_species", 20, SpeciesCategoryTests::vanillaMagmaCubeIsNotAParentSpecies);
        PFGameTests.test("all_pf_parent_species_resolve_to_their_category", 20, SpeciesCategoryTests::allPfParentSpeciesResolveToTheirCategory);
        PFGameTests.test("resource_slime_resolves_to_null_parent_species", 20, SpeciesCategoryTests::resourceSlimeResolvesToNullParentSpecies);
    }

    /** Q1=A: vanilla {@code minecraft:slime} resolves to null (cannot be infused). */
    private static void vanillaSlimeIsNotAParentSpecies(GameTestHelper helper) {
        Slime vanilla = helper.spawn(EntityType.SLIME, new BlockPos(2, 2, 2));
        Category resolved = SlimeInfusionHandler.resolveParentSpecies(vanilla);
        if (resolved != null) {
            helper.fail("vanilla Slime must resolve to null parent species (Q1=A), got " + resolved);
        }
        helper.succeed();
    }

    /** Q1=A: vanilla {@code minecraft:magma_cube} resolves to null. */
    private static void vanillaMagmaCubeIsNotAParentSpecies(GameTestHelper helper) {
        MagmaCube vanilla = helper.spawn(EntityType.MAGMA_CUBE, new BlockPos(2, 2, 2));
        Category resolved = SlimeInfusionHandler.resolveParentSpecies(vanilla);
        if (resolved != null) {
            helper.fail("vanilla MagmaCube must resolve to null parent species (Q1=A), got " + resolved);
        }
        helper.succeed();
    }

    /** Q1=A positive pin: all six PF parent species resolve to their matching category. */
    private static void allPfParentSpeciesResolveToTheirCategory(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        PFGameTestHelpers.assertResolves(helper, helper.spawn(PFEntities.BOG_SLIME.get(), pos), Category.BOG);
        PFGameTestHelpers.assertResolves(helper, helper.spawn(PFEntities.CAVE_SLIME.get(), pos), Category.CAVE);
        PFGameTestHelpers.assertResolves(helper, helper.spawn(PFEntities.GEODE_SLIME.get(), pos), Category.GEODE);
        PFGameTestHelpers.assertResolves(helper, helper.spawn(PFEntities.TIDE_SLIME.get(), pos), Category.TIDE);
        PFGameTestHelpers.assertResolves(helper, helper.spawn(PFEntities.INFERNAL_SLIME.get(), pos), Category.INFERNAL);
        PFGameTestHelpers.assertResolves(helper, helper.spawn(PFEntities.VOID_SLIME.get(), pos), Category.VOID);
        helper.succeed();
    }

    /** Q3 hard-reject: an already-infused {@link ResourceSlime} resolves to null (no variant swapping). */
    private static void resourceSlimeResolvesToNullParentSpecies(GameTestHelper helper) {
        ResourceSlime resource = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(2, 2, 2));
        resource.setCategory(Category.CAVE);
        Category resolved = SlimeInfusionHandler.resolveParentSpecies(resource);
        if (resolved != null) {
            helper.fail("ResourceSlime must NOT resolve to a parent species (Q3 hard-reject), got " + resolved);
        }
        helper.succeed();
    }
}
