package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The pre-release config suite (#200 / #202 / #203 / #204): disabling variants,
 * categories, the boss tier, integrations, the boss master switch, and frog
 * stat breeding each suppresses exactly the intended resolution seam while
 * leaving everything else intact. Each test mutates the live COMMON config in a
 * try/finally so the global state is always reset.
 */
final class ConfigSuiteTests {

    private ConfigSuiteTests() {
    }

    static void register() {
        PFGameTests.test("disabled_variant_config_suppresses_resolution", 100, ConfigSuiteTests::disabledVariantConfigSuppressesResolution);
        PFGameTests.test("disabled_category_config_suppresses_pool", 100, ConfigSuiteTests::disabledCategoryConfigSuppressesPool);
        PFGameTests.test("boss_variants_disabled_makes_them_unprimable", 100, ConfigSuiteTests::bossVariantsDisabledMakesThemUnprimable);
        PFGameTests.test("disabled_integration_suppresses_variants", 100, ConfigSuiteTests::disabledIntegrationSuppressesVariants);
        PFGameTests.test("boss_master_disables_boss_variants", 100, ConfigSuiteTests::bossMasterDisablesBossVariants);
        PFGameTests.test("frog_stats_disabled_forces_baseline", 100, ConfigSuiteTests::frogStatsDisabledForcesBaseline);
    }

    private static void disabledVariantConfigSuppressesResolution(GameTestHelper helper) {
        if (!com.flatts.productivefrogs.PFConfig.SPEC.isLoaded()) {
            helper.fail("COMMON config must be loaded for the variant-disable tests to be meaningful");
            return;
        }
        net.minecraft.core.HolderLookup.RegistryLookup<SlimeVariant> registry =
            PFRegistries.variants(helper.getLevel().registryAccess());
        Identifier iron = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        Identifier ironIngot = Identifier.fromNamespaceAndPath("minecraft", "iron_ingot");
        net.minecraft.util.RandomSource random = helper.getLevel().getRandom();

        // Baseline: iron resolves before we disable it.
        if (SlimeVariant.findByPrimerItem(registry, ironIngot) == null) {
            helper.fail("baseline: iron should resolve by its primer item");
            return;
        }
        try {
            com.flatts.productivefrogs.PFConfig.DISABLED_VARIANTS.set(java.util.List.of(iron.toString()));
            if (SlimeVariant.findByPrimerItem(registry, ironIngot) != null) {
                helper.fail("disabled iron should not resolve via findByPrimerItem");
                return;
            }
            if (SlimeVariant.findByPrimer(registry, new ItemStack(Items.IRON_INGOT)) != null) {
                helper.fail("disabled iron should not resolve via findByPrimer (held stack)");
                return;
            }
            // The rest of iron's CAVE pool is unaffected: discovery still produces
            // non-iron variants and never the disabled one.
            boolean sawCave = false;
            for (int i = 0; i < 300; i++) {
                java.util.Map.Entry<Identifier, SlimeVariant> pick =
                    SlimeVariant.pickWeighted(registry, Category.CAVE, random);
                if (pick != null) {
                    sawCave = true;
                    if (pick.getKey().equals(iron)) {
                        helper.fail("disabled iron should never come out of split-discovery");
                        return;
                    }
                }
            }
            if (!sawCave) {
                helper.fail("CAVE pool should still produce non-iron variants while only iron is disabled");
                return;
            }
        } finally {
            com.flatts.productivefrogs.PFConfig.DISABLED_VARIANTS.set(java.util.List.of());
        }
        // Restored: iron primes again after re-enabling.
        if (SlimeVariant.findByPrimerItem(registry, ironIngot) == null) {
            helper.fail("iron should resolve again after re-enabling");
            return;
        }
        helper.succeed();
    }

    private static void disabledCategoryConfigSuppressesPool(GameTestHelper helper) {
        if (!com.flatts.productivefrogs.PFConfig.SPEC.isLoaded()) {
            helper.fail("COMMON config must be loaded for the variant-disable tests to be meaningful");
            return;
        }
        net.minecraft.core.HolderLookup.RegistryLookup<SlimeVariant> registry =
            PFRegistries.variants(helper.getLevel().registryAccess());
        net.minecraft.util.RandomSource random = helper.getLevel().getRandom();

        if (SlimeVariant.pickWeighted(registry, Category.CAVE, random) == null) {
            helper.fail("baseline: the CAVE pool should be non-empty");
            return;
        }
        try {
            com.flatts.productivefrogs.PFConfig.DISABLED_CATEGORIES.set(java.util.List.of(Category.CAVE.id()));
            for (int i = 0; i < 100; i++) {
                if (SlimeVariant.pickWeighted(registry, Category.CAVE, random) != null) {
                    helper.fail("a disabled category should yield an empty discovery pool");
                    return;
                }
            }
            // iron is CAVE: disabling the category makes it unprimable too.
            if (SlimeVariant.findByPrimer(registry, new ItemStack(Items.IRON_INGOT)) != null) {
                helper.fail("a CAVE-category variant should be unprimable when CAVE is disabled");
                return;
            }
            // A different category still produces picks.
            if (SlimeVariant.pickWeighted(registry, Category.BOG, random) == null) {
                helper.fail("BOG should still pick while only CAVE is disabled");
                return;
            }
        } finally {
            com.flatts.productivefrogs.PFConfig.DISABLED_CATEGORIES.set(java.util.List.of());
        }
        helper.succeed();
    }

    private static void bossVariantsDisabledMakesThemUnprimable(GameTestHelper helper) {
        if (!com.flatts.productivefrogs.PFConfig.SPEC.isLoaded()) {
            helper.fail("COMMON config must be loaded for the variant-disable tests to be meaningful");
            return;
        }
        net.minecraft.core.HolderLookup.RegistryLookup<SlimeVariant> registry =
            PFRegistries.variants(helper.getLevel().registryAccess());
        Identifier netherStar = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "nether_star");
        Identifier netherStarItem = Identifier.fromNamespaceAndPath("minecraft", "nether_star");

        java.util.Map.Entry<Identifier, SlimeVariant> baseline =
            SlimeVariant.findByPrimerItem(registry, netherStarItem);
        if (baseline == null || !baseline.getKey().equals(netherStar)) {
            helper.fail("baseline: nether_star should prime (prime-only, not unreachable)");
            return;
        }
        try {
            com.flatts.productivefrogs.PFConfig.BOSS_VARIANTS_ENABLED.set(false);
            if (SlimeVariant.findByPrimerItem(registry, netherStarItem) != null) {
                helper.fail("with the boss tier off, nether_star should be unprimable");
                return;
            }
        } finally {
            com.flatts.productivefrogs.PFConfig.BOSS_VARIANTS_ENABLED.set(true);
        }
        if (SlimeVariant.findByPrimerItem(registry, netherStarItem) == null) {
            helper.fail("nether_star should prime again after re-enabling the boss tier");
            return;
        }
        helper.succeed();
    }

    private static void disabledIntegrationSuppressesVariants(GameTestHelper helper) {
        if (!com.flatts.productivefrogs.PFConfig.SPEC.isLoaded()) {
            helper.fail("COMMON config must be loaded for the variant-disable tests to be meaningful");
            return;
        }
        Identifier tin = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "tin");
        Identifier silicon = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "silicon");
        Identifier iron = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

        // (1) mapping derived from bundled JSON conditions.
        if (!com.flatts.productivefrogs.setup.VariantIntegrations.providersOf(tin).equals(java.util.Set.of("alltheores"))) {
            helper.fail("tin should map to provider {alltheores}, got "
                + com.flatts.productivefrogs.setup.VariantIntegrations.providersOf(tin));
            return;
        }
        if (!com.flatts.productivefrogs.setup.VariantIntegrations.providersOf(silicon).equals(java.util.Set.of("ae2", "refinedstorage"))) {
            helper.fail("silicon should map to {ae2, refinedstorage}, got "
                + com.flatts.productivefrogs.setup.VariantIntegrations.providersOf(silicon));
            return;
        }
        if (!com.flatts.productivefrogs.setup.VariantIntegrations.providersOf(iron).isEmpty()) {
            helper.fail("first-party iron should have no provider integration");
            return;
        }

        // (2) the isEnabled integration clause. The dummy's own fields don't matter -
        // the integration gate keys off the id argument's bundled mapping.
        SlimeVariant dummy = new SlimeVariant(java.util.Optional.empty(),
            java.util.Optional.empty(), Category.CAVE, 0xFFFFFF, 0xFFFFFF, 1,
            java.util.Optional.empty(), java.util.Optional.empty());
        try {
            com.flatts.productivefrogs.PFConfig.DISABLED_INTEGRATIONS.set(java.util.List.of("alltheores"));
            if (dummy.isEnabled(tin)) {
                helper.fail("tin should be disabled when its only provider (alltheores) is disabled");
                return;
            }
            if (!dummy.isEnabled(silicon)) {
                helper.fail("silicon should stay enabled when only one of its two providers is disabled");
                return;
            }
            if (!dummy.isEnabled(iron)) {
                helper.fail("first-party iron should be unaffected by disabledIntegrations");
                return;
            }
            // All of silicon's providers listed -> now disabled.
            com.flatts.productivefrogs.PFConfig.DISABLED_INTEGRATIONS.set(java.util.List.of("ae2", "refinedstorage"));
            if (dummy.isEnabled(silicon)) {
                helper.fail("silicon should be disabled when ALL its providers are listed");
                return;
            }
            if (!dummy.isEnabled(tin)) {
                helper.fail("tin should be re-enabled when alltheores is no longer listed");
                return;
            }
        } finally {
            com.flatts.productivefrogs.PFConfig.DISABLED_INTEGRATIONS.set(java.util.List.of());
        }
        if (!dummy.isEnabled(tin) || !dummy.isEnabled(silicon)) {
            helper.fail("all variants should be enabled again after clearing disabledIntegrations");
            return;
        }
        helper.succeed();
    }

    private static void bossMasterDisablesBossVariants(GameTestHelper helper) {
        if (!com.flatts.productivefrogs.PFConfig.SPEC.isLoaded()) {
            helper.fail("COMMON config must be loaded for the variant-disable tests to be meaningful");
            return;
        }
        net.minecraft.core.HolderLookup.RegistryLookup<SlimeVariant> registry =
            PFRegistries.variants(helper.getLevel().registryAccess());
        Identifier netherStarItem = Identifier.fromNamespaceAndPath("minecraft", "nether_star");
        Identifier ironItem = Identifier.fromNamespaceAndPath("minecraft", "iron_ingot");

        if (SlimeVariant.findByPrimerItem(registry, netherStarItem) == null
                || !com.flatts.productivefrogs.PFConfig.bossEnabled()) {
            helper.fail("baseline: nether_star should prime and boss should be enabled");
            return;
        }
        try {
            com.flatts.productivefrogs.PFConfig.BOSS_ENABLED.set(false);
            if (com.flatts.productivefrogs.PFConfig.bossEnabled()) {
                helper.fail("bossEnabled() should be false when the master is off");
                return;
            }
            if (SlimeVariant.findByPrimerItem(registry, netherStarItem) != null) {
                helper.fail("with the boss master off, nether_star should be unprimable");
                return;
            }
            // A normal (non-boss) variant is untouched by the boss master.
            if (SlimeVariant.findByPrimerItem(registry, ironItem) == null) {
                helper.fail("iron should still prime when only the boss master is off");
                return;
            }
        } finally {
            com.flatts.productivefrogs.PFConfig.BOSS_ENABLED.set(true);
        }
        if (SlimeVariant.findByPrimerItem(registry, netherStarItem) == null) {
            helper.fail("nether_star should prime again after re-enabling the boss master");
            return;
        }
        helper.succeed();
    }

    private static void frogStatsDisabledForcesBaseline(GameTestHelper helper) {
        if (!com.flatts.productivefrogs.PFConfig.SPEC.isLoaded()) {
            helper.fail("COMMON config must be loaded for the variant-disable tests to be meaningful");
            return;
        }
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        frog.setCategory(Category.CAVE);
        frog.setStats(8, 8, 8);
        ItemStack sweetslime = new ItemStack(PFItems.SWEETSLIME.get());

        // Baseline (layer on): effective stats track the stored stats; Sweetslime breeds.
        if (frog.effectiveAppetite() != 8 || frog.effectiveBounty() != 8 || frog.effectiveReach() != 8
                || !frog.isFood(sweetslime)) {
            helper.fail("baseline: effective stats should equal the stored 8s and Sweetslime should be food");
            return;
        }
        try {
            com.flatts.productivefrogs.PFConfig.FROG_STATS_ENABLED.set(false);
            // Effective stats collapse to baseline; Sweetslime is no longer food.
            if (frog.effectiveAppetite() != com.flatts.productivefrogs.content.entity.FrogStats.STAT_MIN
                    || frog.effectiveBounty() != com.flatts.productivefrogs.content.entity.FrogStats.STAT_MIN
                    || frog.effectiveReach() != com.flatts.productivefrogs.content.entity.FrogStats.STAT_MIN) {
                helper.fail("layer off: effective stats should be baseline (1)");
                return;
            }
            if (frog.isFood(sweetslime)) {
                helper.fail("layer off: Sweetslime should not be a breeding food");
                return;
            }
            // Freeze, not delete: the stored stats are untouched.
            if (frog.getAppetite() != 8 || frog.getBounty() != 8 || frog.getReach() != 8) {
                helper.fail("layer off: stored stats must be frozen (still 8), not deleted");
                return;
            }
        } finally {
            com.flatts.productivefrogs.PFConfig.FROG_STATS_ENABLED.set(true);
        }
        // Restored: effective stats track the stored stats again.
        if (frog.effectiveAppetite() != 8 || frog.effectiveBounty() != 8 || frog.effectiveReach() != 8
                || !frog.isFood(sweetslime)) {
            helper.fail("re-enabled: effective stats and Sweetslime breeding should be back");
            return;
        }
        helper.succeed();
    }
}
