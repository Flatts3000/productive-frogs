package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.content.entity.CaveSlime;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.PFTags;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.event.SlimeInfusionHandler;
import com.flatts.productivefrogs.event.SlimeSplitDiscoveryHandler;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFFluidTypes;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFItems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world GameTests for Productive Frogs. Each test is a headless scenario
 * that runs inside a real Minecraft server via {@code ./gradlew runGameTestServer};
 * each is given a small plot via the referenced structure NBT and asserts
 * behavior via {@link GameTestHelper}.
 *
 * <p>Registration in MC 1.21.11 uses the registry-based system (the older
 * {@code @GameTestHolder}/{@code @GameTest} annotations were removed):
 * <ol>
 *   <li><b>Test functions</b> register via a standard {@link DeferredRegister}
 *       on {@link BuiltInRegistries#TEST_FUNCTION}. NeoForge unfreezes that
 *       registry after vanilla bootstrap and fires {@code RegisterEvent} for
 *       it on the mod bus, identical lifecycle to Blocks/Items.</li>
 *   <li><b>Test instances</b> are registered through {@link RegisterGameTestsEvent},
 *       which pairs a function holder with a structure + timing metadata.
 *       That event fires only on game-test-enabled boots, and per
 *       NeoForge issue patterns it can fire twice — guard via
 *       {@link #testInstancesRegistered}.</li>
 *   <li><b>Structure NBT</b> lives at {@code data/<modid>/structure/<name>.nbt}
 *       — singular {@code structure/}, same as 1.21.x tag dirs went singular.</li>
 * </ol>
 *
 * <p>Pattern lifted from the working reference at
 * <a href="https://github.com/ksoichiro/JustBlockShapes/blob/main/neoforge/1.21.11/src/main/java/com/justblockshapes/neoforge/gametest/JustBlockShapesGameTestNeoForge.java">ksoichiro/JustBlockShapes</a>.
 */
public final class PFGameTests {

    private static final ResourceLocation EMPTY_STRUCTURE =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "empty_5x5x5");

    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, ProductiveFrogs.MOD_ID);

    private static final List<RegisteredTest> REGISTERED_TESTS = new ArrayList<>();

    /** {@link RegisterGameTestsEvent} can fire more than once per JVM. */
    private static boolean testInstancesRegistered = false;

    private record RegisteredTest(
        DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> holder,
        int timeoutTicks
    ) {}

    static {
        registerTest("primed_egg_breaks_when_water_removed",
            PFGameTests::primedEggBreaksWhenWaterRemoved, 100);
        registerTest("primed_egg_hatches_into_matching_category_tadpoles",
            PFGameTests::primedEggHatchesIntoMatchingCategoryTadpoles, 100);
        registerTest("tadpole_ages_up_into_resource_frog_of_same_category",
            PFGameTests::tadpoleAgesUpIntoResourceFrogOfSameCategory, 100);
        registerTest("tadpole_bucket_round_trip_preserves_category",
            PFGameTests::tadpoleBucketRoundTripPreservesCategory, 100);
        registerTest("primer_tags_contain_expected_items",
            PFGameTests::primerTagsContainExpectedItems, 100);
        registerTest("slime_infusion_transforms_vanilla_into_resource_slime",
            PFGameTests::slimeInfusionTransformsVanillaIntoResourceSlime, 100);
        registerTest("resource_slime_split_preserves_category",
            PFGameTests::resourceSlimeSplitPreservesCategory, 100);
        registerTest("frog_tongue_targets_only_matching_category_slime",
            PFGameTests::frogTongueTargetsOnlyMatchingCategorySlime, 200);
        registerTest("matching_frog_kill_drops_category_froglight",
            PFGameTests::matchingFrogKillDropsCategoryFroglight, 100);
        registerTest("mismatched_frog_kill_drops_no_froglight",
            PFGameTests::mismatchedFrogKillDropsNoFroglight, 100);
        registerTest("frog_tongue_ai_path_drops_category_froglight",
            PFGameTests::frogTongueAiPathDropsCategoryFroglight, 400);
        registerTest("slime_bucket_round_trip_preserves_category",
            PFGameTests::slimeBucketRoundTripPreservesCategory, 100);
        registerTest("slime_bucket_round_trip_preserves_variant",
            PFGameTests::slimeBucketRoundTripPreservesVariant, 100);
        registerTest("vanilla_slime_split_discovery_converts_to_metallic_resource_slime",
            PFGameTests::vanillaSlimeSplitDiscoveryConvertsToMetallicResourceSlime, 100);
        registerTest("vanilla_magma_cube_split_discovery_converts_to_infernal_resource_slime",
            PFGameTests::vanillaMagmaCubeSplitDiscoveryConvertsToInfernalResourceSlime, 100);
        registerTest("slime_variant_datapack_registry_loads_initial_variants",
            PFGameTests::slimeVariantDatapackRegistryLoadsInitialVariants, 100);
        registerTest("parent_species_datapack_registry_loads_six_defaults",
            PFGameTests::parentSpeciesDatapackRegistryLoadsSixDefaults, 100);
        registerTest("variant_slime_kill_drops_configurable_froglight",
            PFGameTests::variantSlimeKillDropsConfigurableFroglight, 100);
        registerTest("infusion_with_variant_primer_sets_specific_variant",
            PFGameTests::infusionWithVariantPrimerSetsSpecificVariant, 100);
        registerTest("split_discovery_picks_variant_from_pool",
            PFGameTests::splitDiscoveryPicksVariantFromPool, 100);
        registerTest("cave_slime_split_discovery_converts_to_mineral_resource_slime",
            PFGameTests::caveSlimeSplitDiscoveryConvertsToMineralResourceSlime, 100);
        registerTest("geode_slime_split_discovery_converts_to_gem_resource_slime",
            PFGameTests::geodeSlimeSplitDiscoveryConvertsToGemResourceSlime, 100);
        registerTest("tide_slime_split_discovery_converts_to_aquatic_resource_slime",
            PFGameTests::tideSlimeSplitDiscoveryConvertsToAquaticResourceSlime, 100);
        registerTest("void_slime_split_discovery_converts_to_arcane_resource_slime",
            PFGameTests::voidSlimeSplitDiscoveryConvertsToArcaneResourceSlime, 100);
        registerTest("slime_milker_converts_iron_slime_bucket_into_iron_milk_bucket",
            PFGameTests::slimeMilkerConvertsIronSlimeBucketIntoIronMilkBucket, 100);
        registerTest("slime_milk_source_spawns_iron_resource_slime_on_solid_neighbour",
            PFGameTests::slimeMilkSourceSpawnsIronResourceSlimeOnSolidNeighbour, 100);
        registerTest("slime_milk_source_falls_back_to_liquid_when_no_solid_neighbour",
            PFGameTests::slimeMilkSourceFallsBackToLiquidWhenNoSolidNeighbour, 100);
        registerTest("vanilla_slime_milk_source_spawns_vanilla_slime",
            PFGameTests::vanillaSlimeMilkSourceSpawnsVanillaSlime, 100);
        registerTest("magma_slime_milk_source_spawns_magma_cube",
            PFGameTests::magmaSlimeMilkSourceSpawnsMagmaCube, 100);
        registerTest("slime_milk_source_picks_solid_neighbour_below_when_no_horizontal_neighbour",
            PFGameTests::slimeMilkSourcePicksSolidNeighbourBelowWhenNoHorizontalNeighbour, 100);
        registerTest("slime_milk_source_decrements_spawns_remaining_each_spawn",
            PFGameTests::slimeMilkSourceDecrementsSpawnsRemainingEachSpawn, 100);
        registerTest("slime_milk_source_drains_when_spawns_remaining_reaches_zero",
            PFGameTests::slimeMilkSourceDrainsWhenSpawnsRemainingReachesZero, 100);
        registerTest("slime_milk_source_default_state_has_max_spawns_remaining",
            PFGameTests::slimeMilkSourceDefaultStateHasMaxSpawnsRemaining, 100);
        registerTest("custom_slimes_size_1_hitbox_matches_vanilla_slime",
            PFGameTests::customSlimesSize1HitboxMatchesVanillaSlime, 100);
        registerTest("category_froglight_smelt_recipes_resolve_to_canonical_resource",
            PFGameTests::categoryFroglightSmeltRecipesResolveToCanonicalResource, 100);
        registerTest("variant_configurable_froglight_smelt_recipes_resolve_per_variant",
            PFGameTests::variantConfigurableFroglightSmeltRecipesResolvePerVariant, 100);
        registerTest("configurable_froglight_without_variant_does_not_smelt",
            PFGameTests::configurableFroglightWithoutVariantDoesNotSmelt, 100);
        registerTest("variant_froglight_round_trip_preserves_variant_through_place_and_break",
            PFGameTests::variantFroglightRoundTripPreservesVariantThroughPlaceAndBreak, 100);
        registerTest("direct_feed_matching_category_drops_froglight_and_empties_bucket",
            PFGameTests::directFeedMatchingCategoryDropsFroglightAndEmptiesBucket, 100);
        registerTest("direct_feed_variant_slime_drops_configurable_froglight",
            PFGameTests::directFeedVariantSlimeDropsConfigurableFroglight, 100);
        registerTest("direct_feed_mismatched_category_is_a_no_op",
            PFGameTests::directFeedMismatchedCategoryIsANoOp, 100);
        registerTest("milk_bucket_exposes_fluid_capability_for_tank_mods",
            PFGameTests::milkBucketExposesFluidCapabilityForTankMods, 100);
        registerTest("slime_milker_be_cooks_iron_bucket_to_iron_milk_after_100_ticks",
            PFGameTests::slimeMilkerBeCooksIronBucketToIronMilkAfter100Ticks, 200);
        registerTest("slime_milker_be_resets_progress_when_input_lacks_variant",
            PFGameTests::slimeMilkerBeResetsProgressWhenInputLacksVariant, 100);
        registerTest("slime_milker_capability_routes_input_view_to_top_and_output_view_to_bottom",
            PFGameTests::slimeMilkerCapabilityRoutesInputViewToTopAndOutputViewToBottom, 100);
        registerTest("hopper_above_slime_milker_pushes_slime_bucket_into_input_slot",
            PFGameTests::hopperAboveSlimeMilkerPushesSlimeBucketIntoInputSlot, 100);
        registerTest("hopper_below_slime_milker_pulls_milk_bucket_from_output_slot",
            PFGameTests::hopperBelowSlimeMilkerPullsMilkBucketFromOutputSlot, 100);
    }

    private PFGameTests() {
        // static-only
    }

    private static void registerTest(String name, Consumer<GameTestHelper> test, int timeoutTicks) {
        DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> holder =
            TEST_FUNCTIONS.register(name, () -> test);
        REGISTERED_TESTS.add(new RegisteredTest(holder, timeoutTicks));
    }

    /** Wire up via the mod event bus from {@code ProductiveFrogs} constructor. */
    public static void register(IEventBus modEventBus) {
        TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(PFGameTests::onRegisterGameTests);
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        if (testInstancesRegistered) {
            return;
        }
        testInstancesRegistered = true;

        Holder<TestEnvironmentDefinition> defaultEnv = event.registerEnvironment(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "default")
        );

        for (RegisteredTest test : REGISTERED_TESTS) {
            // Fail fast — if a holder didn't bind it means the DeferredRegister
            // pipeline broke somewhere, and silently skipping would let CI go
            // green with zero of our tests actually running.
            if (!test.holder().isBound()) {
                throw new IllegalStateException(
                    "Test function holder " + test.holder().getId() + " is unbound at "
                    + "RegisterGameTestsEvent time — DeferredRegister pipeline is broken"
                );
            }
            TestData<Holder<TestEnvironmentDefinition>> testData = new TestData<>(
                defaultEnv,
                EMPTY_STRUCTURE,
                test.timeoutTicks(),
                0,      // setupTicks
                true    // required
            );
            event.registerTest(
                test.holder().getId(),
                new FunctionGameTestInstance(test.holder().getKey(), testData)
            );
        }
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    /**
     * Place a Primed Frog Egg on a water source, then remove the water. The
     * block's {@code updateShape} runs {@code canSurvive}, sees no water below,
     * and replaces itself with air. Verifies the survive-on-water rule.
     */
    private static void primedEggBreaksWhenWaterRemoved(GameTestHelper helper) {
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);
        helper.setBlock(eggPos, PFBlocks.primedEgg(Category.METALLIC));
        helper.assertBlockPresent(PFBlocks.primedEgg(Category.METALLIC), eggPos);

        // Knock out the water — the egg should detect via neighbor update
        // (PrimedFrogEggBlock.updateShape → canSurvive false → air) and disappear.
        helper.setBlock(eggPos.below(), Blocks.AIR);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(PFBlocks.primedEgg(Category.METALLIC), eggPos));
    }

    /**
     * Place a category-primed egg, force its scheduled tick to fire, and assert
     * the hatch produced between 1 and 3 Resource Tadpoles all carrying the
     * matching category. This is the headline egg→tadpole pipeline behavior.
     *
     * <p>Vanilla schedules the hatch at random(3600..12000) ticks via
     * {@code onPlace} → {@code scheduleTick}. We can't wait that long in a test,
     * so we invoke {@link PrimedFrogEggBlock#tick} directly — exercising the
     * exact same code path the schedule would have triggered.
     */
    private static void primedEggHatchesIntoMatchingCategoryTadpoles(GameTestHelper helper) {
        Category cat = Category.GEM;
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);

        PrimedFrogEggBlock eggBlock = PFBlocks.primedEgg(cat);
        helper.setBlock(eggPos, eggBlock);

        ServerLevel level = helper.getLevel();
        BlockPos absEggPos = helper.absolutePos(eggPos);

        // Invoke hatch directly via the block's tick() — PrimedFrogEggBlock
        // widens the override to public, so we can call it on the concrete
        // reference and bypass the random 3600..12000-tick schedule that
        // onPlace queues up. Exercises the exact code path the schedule
        // would have reached.
        eggBlock.tick(level.getBlockState(absEggPos), level, absEggPos, level.getRandom());

        helper.assertBlockNotPresent(eggBlock, eggPos);

        List<ResourceTadpole> tadpoles = helper.getEntities(PFEntities.RESOURCE_TADPOLE.get());
        if (tadpoles.isEmpty()) {
            helper.fail("expected 1-3 Resource Tadpoles after hatch, got 0");
        }
        if (tadpoles.size() > 3) {
            helper.fail("expected 1-3 Resource Tadpoles after hatch, got " + tadpoles.size());
        }
        for (ResourceTadpole tadpole : tadpoles) {
            if (tadpole.getCategory() != cat) {
                helper.fail("hatched tadpole has category " + tadpole.getCategory() + ", expected " + cat);
            }
        }
        helper.succeed();
    }

    /**
     * Spawn a category-locked Resource Tadpole, force its maturation via the
     * access-transformer-exposed {@code ageUp()}, and assert exactly one
     * Resource Frog of the same category exists afterward. Tadpole entity
     * itself should be gone (converted, not duplicated).
     */
    private static void tadpoleAgesUpIntoResourceFrogOfSameCategory(GameTestHelper helper) {
        Category cat = Category.ARCANE;
        BlockPos spawnPos = new BlockPos(2, 2, 2);
        helper.setBlock(spawnPos.below(), Blocks.WATER);

        ResourceTadpole tadpole = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), spawnPos);
        tadpole.setCategory(cat);
        tadpole.ageUp();

        helper.succeedWhen(() -> {
            List<ResourceFrog> frogs = helper.getEntities(PFEntities.RESOURCE_FROG.get());
            if (frogs.size() != 1) {
                helper.fail("expected 1 Resource Frog after maturation, got " + frogs.size());
            }
            if (frogs.get(0).getCategory() != cat) {
                helper.fail("matured frog has category " + frogs.get(0).getCategory() + ", expected " + cat);
            }
            if (!helper.getEntities(PFEntities.RESOURCE_TADPOLE.get()).isEmpty()) {
                helper.fail("tadpole entity must be removed during ageUp conversion");
            }
        });
    }

    /**
     * Verify the full bucket round-trip preserves category: spawn a tadpole,
     * write its state into a bucket via {@code saveToBucketTag}, read the
     * category back from the NBT, then spawn a fresh tadpole of a different
     * default category and call {@code loadFromBucketTag} on it. The fresh
     * tadpole must end up with the source's category — that's the path
     * vanilla {@code Bucketable.bucketMobPickup} → release hook takes.
     *
     * <p>The pre-PR-#60 version of this test only verified the save→read half
     * (the bucket NBT contains the category). Extending to cover
     * {@code loadFromBucketTag} closes the gap flagged in backlog.md against
     * PR #22's slime-bucket strengthening — the tadpole bucket now has the
     * same coverage shape.
     */
    private static void tadpoleBucketRoundTripPreservesCategory(GameTestHelper helper) {
        Category cat = Category.INFERNAL;
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.WATER);

        ResourceTadpole source = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos);
        source.setCategory(cat);

        ItemStack bucket = new ItemStack(PFItems.RESOURCE_TADPOLE_BUCKET.get());
        source.saveToBucketTag(bucket);

        // 1. Save → read half: bucket NBT carries the category.
        Category readBack = ResourceTadpoleBucketItem.readCategory(bucket);
        if (readBack != cat) {
            helper.fail("bucket NBT lost category: wrote " + cat + ", read " + readBack);
            return;
        }

        // 2. loadFromBucketTag half: a fresh tadpole of a DIFFERENT category
        //    has its category overwritten when the bucket is released. Picking
        //    METALLIC as the starting state so the assertion fails loudly if
        //    loadFromBucketTag silently no-ops.
        ResourceTadpole released = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos.east());
        released.setCategory(Category.METALLIC);
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("bucket's BUCKET_ENTITY_DATA is unexpectedly null after saveToBucketTag");
            return;
        }
        released.loadFromBucketTag(data.copyTag());

        if (released.getCategory() != cat) {
            helper.fail("released tadpole category was " + released.getCategory()
                + ", expected " + cat + " (loadFromBucketTag did not restore the bucket's category)");
            return;
        }
        helper.succeed();
    }

    /**
     * Verify that primer item tags actually loaded — exercises the data-load
     * pipeline. The {@code tags/items/} → {@code tags/item/} singularization in
     * MC 1.21.x silently dropped our tag files until we renamed; this test
     * would have flagged that within a CI run instead of from a manual playtest
     * that "nothing happens when I right-click frogspawn with iron".
     */
    private static void primerTagsContainExpectedItems(GameTestHelper helper) {
        // Spot-check one canonical entry per category. We don't enumerate every
        // entry here — that's the tag JSON's job. We just verify the tags
        // themselves resolve in the live tag manager.
        assertItemInPrimerTag(helper, Items.IRON_INGOT, Category.METALLIC);
        assertItemInPrimerTag(helper, Items.REDSTONE, Category.MINERAL);
        assertItemInPrimerTag(helper, Items.DIAMOND, Category.GEM);
        assertItemInPrimerTag(helper, Items.PRISMARINE_SHARD, Category.AQUATIC);
        assertItemInPrimerTag(helper, Items.MAGMA_CREAM, Category.INFERNAL);
        assertItemInPrimerTag(helper, Items.ENDER_PEARL, Category.ARCANE);
        helper.succeed();
    }

    private static void assertItemInPrimerTag(GameTestHelper helper, net.minecraft.world.item.Item item, Category cat) {
        ItemStack stack = new ItemStack(item);
        if (!stack.is(PFTags.PRIMER_BY_CATEGORY.get(cat))) {
            helper.fail(BuiltInRegistries.ITEM.getKey(item)
                + " must be in primer/" + cat.id() + " tag — check the JSON and the directory path");
        }
    }

    /**
     * Verify {@link SlimeVariant#findByPrimerItem} resolves item ids to the
     * correct variants — covers the path the slime infusion handler walks
     * before calling {@code setVariant} on the transformed slime.
     */
    private static void infusionWithVariantPrimerSetsSpecificVariant(GameTestHelper helper) {
        net.minecraft.core.Registry<SlimeVariant> registry =
            helper.getLevel().registryAccess().lookupOrThrow(PFRegistries.SLIME_VARIANT);

        java.util.Map.Entry<ResourceLocation, SlimeVariant> ironEntry = SlimeVariant.findByPrimerItem(
            registry, ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"));
        if (ironEntry == null) {
            helper.fail("iron_ingot should resolve to a variant (productivefrogs:iron)");
            return;
        }
        if (!ironEntry.getKey().equals(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"))) {
            helper.fail("expected productivefrogs:iron, got " + ironEntry.getKey());
        }

        // A primer-tag item that ISN'T in the variant registry (e.g.,
        // blaze_powder is in primer/infernal but not a variant primer) should
        // miss the variant lookup so the handler falls back to category-only.
        if (SlimeVariant.findByPrimerItem(registry,
                ResourceLocation.fromNamespaceAndPath("minecraft", "blaze_powder")) != null) {
            helper.fail("blaze_powder is not a variant primer in V1 — lookup should miss");
        }

        // Stick is in NO primer tag — must miss too.
        if (SlimeVariant.findByPrimerItem(registry,
                ResourceLocation.fromNamespaceAndPath("minecraft", "stick")) != null) {
            helper.fail("stick is not a primer for any variant — lookup should miss");
        }
        helper.succeed();
    }

    /**
     * Force the discovery chance to 100% and split a vanilla green slime;
     * assert every offspring is a Resource Slime carrying a METALLIC-pool
     * variant (iron / copper / gold). Verifies the
     * {@link SlimeVariant#pickWeighted} integration in
     * {@code SlimeSplitDiscoveryHandler}.
     */
    private static void splitDiscoveryPicksVariantFromPool(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        // Capture-and-restore in case another harness mid-run has set its own
        // override — blindly clearing to null would clobber it. Unconditional
        // null was correct for V1 (only this test family writes the field),
        // but the restore pattern is cheap insurance against future nesting.
        Float originalOverride = SlimeSplitDiscoveryHandler.testOverride;
        SlimeSplitDiscoveryHandler.testOverride = 1.0f;
        try {
            net.minecraft.world.entity.monster.Slime parent =
                helper.spawn(net.minecraft.world.entity.EntityType.SLIME, pos);
            parent.setSize(3, true);
            parent.setHealth(0.0F);
            parent.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);

            helper.succeedWhen(() -> {
                List<ResourceSlime> resources = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
                if (resources.isEmpty()) {
                    helper.fail("expected at least one Resource Slime after forced discovery, got 0");
                }
                // At 100% chance every child must convert — no vanilla slimes
                // should remain. Mirrors runSplitDiscoveryTest's check.
                List<? extends net.minecraft.world.entity.monster.Slime> vanillaRemaining =
                    helper.getEntities(net.minecraft.world.entity.EntityType.SLIME);
                if (!vanillaRemaining.isEmpty()) {
                    helper.fail("expected zero vanilla slime children at 100% discovery, got "
                        + vanillaRemaining.size());
                }
                for (ResourceSlime s : resources) {
                    ResourceLocation variantId = s.getVariantId();
                    if (variantId == null) {
                        helper.fail("split-discovered slime should carry a variant (METALLIC pool is non-empty)");
                        return;
                    }
                    if (s.getCategory() != Category.METALLIC) {
                        helper.fail("variant sync should leave category METALLIC, got " + s.getCategory());
                    }
                    String path = variantId.getPath();
                    if (!path.equals("iron") && !path.equals("copper") && !path.equals("gold")) {
                        helper.fail("variant " + path + " is not in the METALLIC pool (iron/copper/gold)");
                    }
                }
            });
        } finally {
            SlimeSplitDiscoveryHandler.testOverride = originalOverride;
        }
    }

    /**
     * Mirror of {@code matching_frog_kill_drops_category_froglight} for the
     * variant-aware drop path: spawn a METALLIC frog, an IRON-variant slime,
     * deal damage from the frog → assert a {@code configurable_froglight}
     * item entity drops carrying the {@code productivefrogs:iron}
     * SLIME_VARIANT component. The original category-Froglight test still
     * covers the fallback path (slime without a variant).
     */
    private static void variantSlimeKillDropsConfigurableFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.METALLIC);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        ResourceLocation ironVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        slime.setVariant(ironVariant);
        // setVariant syncs category from the registry, but assert just in case.
        if (slime.getCategory() != Category.METALLIC) {
            helper.fail("setVariant(iron) should have synced category to METALLIC, got " + slime.getCategory());
        }

        slime.hurtServer(helper.getLevel(),
            helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        helper.succeedWhen(() -> {
            net.minecraft.world.item.Item expected = PFItems.CONFIGURABLE_FROGLIGHT.get();
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> {
                    ItemStack stack = itemEntity.getItem();
                    if (!stack.is(expected)) return false;
                    ResourceLocation variant = stack.get(
                        com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get());
                    return ironVariant.equals(variant);
                });
            if (!found) {
                helper.fail("expected configurable_froglight stamped with iron variant to drop at frog");
            }
        });
    }

    /**
     * Verify that the {@code productivefrogs:slime_variant} datapack registry
     * is populated by server boot with our 12 shipped variants. Confirms three
     * things end-to-end: (a) the {@code DataPackRegistryEvent.NewRegistry}
     * listener actually fires and binds the codec, (b) NeoForge loads JSONs
     * from the conventional {@code data/<ns>/productivefrogs/slime_variant/}
     * path, and (c) the codec decodes them without throwing.
     */
    private static void slimeVariantDatapackRegistryLoadsInitialVariants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.core.Registry<SlimeVariant> registry =
            level.registryAccess().lookupOrThrow(PFRegistries.SLIME_VARIANT);

        String[] expected = {
            "iron", "copper", "gold",
            "redstone", "lapis", "coal",
            "diamond", "emerald",
            "prismarine", "sponge",
            "magma_cream",
            "ender_pearl"
        };
        for (String name : expected) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, name);
            SlimeVariant variant = registry.get(id);
            if (variant == null) {
                helper.fail("expected variant " + id + " to be loaded from datapack registry");
            }
        }

        // Spot-check a specific variant's decoded fields so a codec regression
        // (e.g., a field name typo) fails the test, not just "registry empty."
        SlimeVariant iron = registry.get(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        if (iron == null) {
            helper.fail("iron variant must be loaded");
            return;
        }
        if (iron.category() != Category.METALLIC) {
            helper.fail("iron variant should be METALLIC, got " + iron.category());
        }
        if (!iron.primerItem().equals(ResourceLocation.fromNamespaceAndPath("minecraft", "iron_ingot"))) {
            helper.fail("iron variant primer should be minecraft:iron_ingot, got " + iron.primerItem());
        }
        // PR-F wires per-variant inner-cube PNGs: every shipped variant JSON
        // declares a `texture` pointing at its generated PNG. Spot-check that
        // the codec round-tripped the field rather than silently dropping it.
        if (iron.texture().isEmpty()) {
            helper.fail("iron variant must declare a `texture` field after the per-variant PNG ship");
            return;
        }
        ResourceLocation expectedTexture = ResourceLocation.fromNamespaceAndPath(
            ProductiveFrogs.MOD_ID, "textures/entity/slime/iron_resource_slime.png");
        if (!expectedTexture.equals(iron.texture().get())) {
            helper.fail("iron texture path should be " + expectedTexture
                + ", got " + iron.texture().get());
            return;
        }
        helper.succeed();
    }

    /**
     * Verify that the {@code productivefrogs:parent_species} datapack registry
     * is populated by server boot with the 6 default entries (vanilla slime +
     * magma_cube + 4 PF parent species). Confirms the {@link
     * com.flatts.productivefrogs.data.ParentSpeciesEntry} codec decodes the
     * shipped JSONs without throwing AND that each entry maps the expected
     * entity type to the expected category. Regression-pins
     * {@code SlimeSplitDiscoveryHandler.categoryForParent}'s registry lookup —
     * if any default JSON is dropped, renamed, or has a category typo, the
     * existing 6 split-discovery GameTests would also fail, but this one
     * surfaces the registry-load failure directly without the indirection.
     */
    private static void parentSpeciesDatapackRegistryLoadsSixDefaults(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.core.Registry<com.flatts.productivefrogs.data.ParentSpeciesEntry> registry =
            level.registryAccess().lookupOrThrow(
                com.flatts.productivefrogs.registry.PFRegistries.PARENT_SPECIES);

        // entity_type id -> expected category
        java.util.Map<ResourceLocation, Category> expected = new java.util.LinkedHashMap<>();
        expected.put(ResourceLocation.parse("minecraft:slime"),              Category.METALLIC);
        expected.put(ResourceLocation.parse("minecraft:magma_cube"),         Category.INFERNAL);
        expected.put(ResourceLocation.parse("productivefrogs:cave_slime"),   Category.MINERAL);
        expected.put(ResourceLocation.parse("productivefrogs:geode_slime"),  Category.GEM);
        expected.put(ResourceLocation.parse("productivefrogs:tide_slime"),   Category.AQUATIC);
        expected.put(ResourceLocation.parse("productivefrogs:void_slime"),   Category.ARCANE);

        java.util.Map<ResourceLocation, Category> actual = new java.util.HashMap<>();
        for (com.flatts.productivefrogs.data.ParentSpeciesEntry entry : registry) {
            actual.put(entry.entityType(), entry.category());
        }

        for (var e : expected.entrySet()) {
            Category got = actual.get(e.getKey());
            if (got == null) {
                helper.fail("parent_species registry is missing " + e.getKey());
                return;
            }
            if (got != e.getValue()) {
                helper.fail("parent_species " + e.getKey() + " maps to " + got
                    + ", expected " + e.getValue());
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Spawn a vanilla Slime, run it through the infusion helper, and assert the
     * source is gone and a ResourceSlime of the matching category sits at the
     * same place with the same size. Exercises the data shape of the
     * transformation independent of the player-interaction event.
     */
    private static void slimeInfusionTransformsVanillaIntoResourceSlime(GameTestHelper helper) {
        Category cat = Category.METALLIC;
        BlockPos spawnPos = new BlockPos(2, 2, 2);

        net.minecraft.world.entity.monster.Slime vanilla =
            helper.spawn(net.minecraft.world.entity.EntityType.SLIME, spawnPos);
        vanilla.setSize(2, true);
        int originalSize = vanilla.getSize();

        ResourceSlime resource = SlimeInfusionHandler.transformInPlace(vanilla, cat);
        if (resource == null) {
            helper.fail("transformInPlace returned null");
            return;
        }
        if (resource.getCategory() != cat) {
            helper.fail("expected category " + cat + ", got " + resource.getCategory());
        }
        if (resource.getSize() != originalSize) {
            helper.fail("expected size " + originalSize + ", got " + resource.getSize());
        }
        if (vanilla.isAlive()) {
            helper.fail("source vanilla slime should be discarded after infusion");
        }
        if (helper.getEntities(net.minecraft.world.entity.EntityType.SLIME).size() != 0) {
            helper.fail("no vanilla slimes should remain in the test plot after infusion");
        }
        helper.succeed();
    }

    /**
     * Spawn a METALLIC ResourceFrog with both a METALLIC and an INFERNAL slime
     * within tongue range. The category-filtered sensor should write only the
     * METALLIC slime into {@code NEAREST_ATTACKABLE}; the INFERNAL one must be
     * filtered out. Verifies {@link
     * com.flatts.productivefrogs.content.entity.ai.ResourceFrogAttackablesSensor}
     * is wired into ResourceFrog's brain provider and the category check
     * actually fires.
     */
    private static void frogTongueTargetsOnlyMatchingCategorySlime(GameTestHelper helper) {
        Category cat = Category.METALLIC;
        BlockPos frogPos = new BlockPos(2, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(cat);

        // Same-category prey at one offset, off-category prey at the other.
        // Both within the 10-block sensor radius.
        ResourceSlime matching = helper.spawn(PFEntities.RESOURCE_SLIME.get(), frogPos.east());
        matching.setSize(1, true);
        matching.setCategory(cat);

        ResourceSlime offCategory = helper.spawn(PFEntities.RESOURCE_SLIME.get(), frogPos.west());
        offCategory.setSize(1, true);
        offCategory.setCategory(Category.INFERNAL);

        // Two sensors chain to populate NEAREST_ATTACKABLE:
        //   1. NEAREST_LIVING_ENTITIES   → writes NEAREST_VISIBLE_LIVING_ENTITIES
        //   2. RESOURCE_FROG_ATTACKABLES → reads NEAREST_VISIBLE_LIVING_ENTITIES,
        //                                  filters by category, writes NEAREST_ATTACKABLE
        //
        // Each Sensor's first scan is offset by a random tick in [0, scanRate)
        // chosen at construction, and the chain only settles once both sensors
        // have fired in the right order. A fixed-tick assertion (the original
        // runAfterDelay(60L, ...)) raced against worst-case timing and went
        // flaky on PR #32.
        //
        // Polling pattern below:
        //   - Fail immediately if NEAREST_ATTACKABLE ever points at the
        //     off-category slime (the category filter is broken).
        //   - Require a stability window of STABLE_TICKS consecutive ticks of
        //     NEAREST_ATTACKABLE == matching before succeeding. A single
        //     correct tick isn't enough -- the memory could oscillate after
        //     and we'd miss it.
        //   - Delayed-fallback assertion at tick 180 reports a specific
        //     last-observed-state failure if the chain never settles, instead
        //     of falling through to the generic 200-tick timeout.
        final int STABLE_TICKS = 10;
        java.util.concurrent.atomic.AtomicInteger consecutiveMatches = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<LivingEntity> lastTarget = new java.util.concurrent.atomic.AtomicReference<>();
        helper.onEachTick(() -> {
            LivingEntity target = frog.getBrain()
                .getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_ATTACKABLE)
                .orElse(null);
            lastTarget.set(target);
            if (target == offCategory) {
                helper.fail("sensor targeted the off-category slime — category filter is broken");
                return;
            }
            if (target == matching) {
                if (consecutiveMatches.incrementAndGet() >= STABLE_TICKS) {
                    helper.succeed();
                }
            } else {
                consecutiveMatches.set(0);
            }
        });
        helper.runAfterDelay(180L, () -> {
            LivingEntity target = lastTarget.get();
            String label = target == null
                ? "null"
                : target == matching ? "matching (but stability window never reached)"
                : target == offCategory ? "offCategory (would have already failed above)"
                : target.toString();
            helper.fail("NEAREST_ATTACKABLE never settled on matching for "
                + STABLE_TICKS + " consecutive ticks within 180-tick window; last observed target=" + label);
        });
    }

    /**
     * Place a matching-category frog and slime; deal lethal damage to the
     * slime sourced from the frog (simulating the result of the tongue eat);
     * assert a Froglight item entity of the correct category drops at the
     * frog's position. Verifies the {@code LivingDeathEvent} handler runs.
     */
    private static void matchingFrogKillDropsCategoryFroglight(GameTestHelper helper) {
        Category cat = Category.AQUATIC;
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(cat);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        slime.setCategory(cat);

        // Damage from the frog — drives the LivingDeathEvent handler's
        // source check.
        slime.hurtServer(helper.getLevel(),
            helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        helper.succeedWhen(() -> {
            net.minecraft.world.item.Item expected = PFBlocks.resourceFroglight(cat).asItem();
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> itemEntity.getItem().is(expected));
            if (!found) {
                helper.fail("expected " + expected + " to drop at frog position after kill");
            }
        });
    }

    /**
     * Same setup but with mismatched categories — the slime dies but the
     * handler must skip its drop because the frog/slime categories disagree.
     * Asserts no Froglight item entities appear.
     */
    private static void mismatchedFrogKillDropsNoFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.METALLIC);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        slime.setSize(1, true);
        slime.setCategory(Category.INFERNAL);

        slime.hurtServer(helper.getLevel(),
            helper.getLevel().damageSources().mobAttack(frog), 999.0F);

        // Wait a small window then assert: no Froglight items dropped from any
        // category. The death event has already fired by the next tick, so 20
        // ticks is generous headroom.
        helper.runAfterDelay(20L, () -> {
            for (Category cat : Category.values()) {
                net.minecraft.world.item.Item froglight = PFBlocks.resourceFroglight(cat).asItem();
                boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                    .anyMatch(itemEntity -> itemEntity.getItem().is(froglight));
                if (found) {
                    helper.fail("category mismatch should not drop Froglight, but found " + froglight);
                }
            }
            helper.succeed();
        });
    }

    /**
     * End-to-end tongue-kill test: spawn a matching frog and slime, let the
     * frog's AI itself drive the tongue strike, and verify the Froglight drops.
     * The pre-PR-#61 manual-damage test ({@link #matchingFrogKillDropsCategoryFroglight})
     * uses {@code hurtServer(level, source, 999.0F)} which bypasses the frog's
     * {@code ATTACK_DAMAGE} attribute and the entire vanilla tongue task chain
     * — PR #27 caught a damage=0 regression that test couldn't see. This one
     * fails closed if any link in the chain breaks:
     *
     * <ul>
     *   <li>sensor wiring writes {@code NEAREST_ATTACKABLE},</li>
     *   <li>vanilla {@code FrogEat} behavior extends the tongue and reaches the prey,</li>
     *   <li>the damage value derived from {@code Attributes.ATTACK_DAMAGE} is non-zero,</li>
     *   <li>the slime dies and the {@code LivingDeathEvent} handler still emits the drop.</li>
     * </ul>
     *
     * <p>Generous 400-tick timeout: the frog's tongue task can pace through
     * targeting → approach → strike across ~30–60 ticks; the test plot tick
     * boundary adds variance. Polling via {@code succeedWhen} succeeds the
     * moment the drop appears, so green runs finish well under that ceiling.
     */
    private static void frogTongueAiPathDropsCategoryFroglight(GameTestHelper helper) {
        Category cat = Category.METALLIC;
        BlockPos frogPos = new BlockPos(2, 2, 2);

        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(cat);

        ResourceSlime slime = helper.spawn(PFEntities.RESOURCE_SLIME.get(), frogPos.east());
        slime.setSize(1, true);
        slime.setCategory(cat);

        // No manual hurtServer call — the frog's brain runs the full target →
        // tongue-extend → damage path on its own.
        helper.succeedWhen(() -> {
            net.minecraft.world.item.Item expected = PFBlocks.resourceFroglight(cat).asItem();
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> itemEntity.getItem().is(expected));
            if (!found) {
                helper.fail("expected " + expected + " to drop after frog's AI tongues the slime");
            }
        });
    }

    /**
     * Exercise the full bucket pickup→release contract: spawn a MINERAL slime,
     * write its state into a bucket via {@code saveToBucketTag}, then load
     * that bucket NBT into a fresh ResourceSlime via {@code loadFromBucketTag}.
     * Verifies (1) the bucket carries the category, (2) the released slime
     * decodes it correctly, and (3) the released slime is flagged
     * {@code fromBucket} so it doesn't despawn on chunk reload.
     *
     * <p>Cross-bucket detail: ResourceTadpoleBucketItem.readCategory works for
     * the slime bucket too because both bucket types write the same
     * {@code BUCKET_ENTITY_DATA → "Category" string} shape — same reader is
     * referenced by both bucket item models via the renamed
     * {@code BucketedCategoryTint} ItemTintSource.
     */
    private static void slimeBucketRoundTripPreservesCategory(GameTestHelper helper) {
        Category cat = Category.MINERAL;
        BlockPos pos = new BlockPos(2, 2, 2);

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        source.setSize(1, true);
        source.setCategory(cat);

        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);

        // Step 1: NBT round-trip via the tint-source reader.
        Category readBack = ResourceTadpoleBucketItem.readCategory(bucket);
        if (readBack != cat) {
            helper.fail("slime bucket round-trip lost category: wrote " + cat + ", read " + readBack);
        }

        // Step 2: spawn a fresh slime and exercise loadFromBucketTag — same
        // path vanilla MobBucketItem walks on release.
        ResourceSlime released = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos.east());
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("bucket's BUCKET_ENTITY_DATA component is unexpectedly null after saveToBucketTag");
            return;
        }
        released.loadFromBucketTag(data.copyTag());

        if (released.getCategory() != cat) {
            helper.fail("released slime has category " + released.getCategory() + ", expected " + cat);
        }
        if (!released.fromBucket()) {
            helper.fail("released slime must be flagged fromBucket so it survives chunk reload");
        }
        helper.succeed();
    }

    /**
     * Bucket a variant-stamped Resource Slime, then assert the
     * {@code BUCKET_ENTITY_DATA} payload carries the {@code Variant} NBT
     * (read via {@code ResourceTadpoleBucketItem.readVariant}) AND that
     * releasing the bucket restores the variant on the spawned slime.
     *
     * <p>This pins the precondition for the variant-aware Slime Bucket tint:
     * if the bucket doesn't carry the Variant id after capture, the
     * {@code BucketedCategoryTint} resolution-order would skip the
     * variant lookup and fall back to the broader category colour.
     */
    private static void slimeBucketRoundTripPreservesVariant(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ResourceLocation variantId =
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper");

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        source.setSize(1, true);
        source.setVariant(variantId);

        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);

        ResourceLocation readBack =
            com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem.readVariant(bucket);
        if (readBack == null || !readBack.equals(variantId)) {
            helper.fail("slime bucket round-trip lost variant: wrote " + variantId
                + ", read " + readBack);
            return;
        }

        ResourceSlime released = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos.east());
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("bucket's BUCKET_ENTITY_DATA is unexpectedly null after saveToBucketTag");
            return;
        }
        released.loadFromBucketTag(data.copyTag());

        if (!variantId.equals(released.getVariantId())) {
            helper.fail("released slime variant was " + released.getVariantId()
                + ", expected " + variantId);
            return;
        }
        helper.succeed();
    }

    /**
     * Force the discovery chance to 100% and split a vanilla green slime —
     * assert every child becomes a METALLIC {@link ResourceSlime} via
     * {@link SlimeSplitDiscoveryHandler}'s {@link MobSplitEvent} hook.
     */
    private static void vanillaSlimeSplitDiscoveryConvertsToMetallicResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, net.minecraft.world.entity.EntityType.SLIME, Category.METALLIC);
    }

    /**
     * Same shape as the slime test but for magma cubes — vanilla magma cube
     * splits map to INFERNAL Resource Slimes.
     */
    private static void vanillaMagmaCubeSplitDiscoveryConvertsToInfernalResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, net.minecraft.world.entity.EntityType.MAGMA_CUBE, Category.INFERNAL);
    }

    /**
     * Cave Slime is the parent species for the MINERAL category — splitting one
     * with 100% discovery should give MINERAL ResourceSlimes. Mirrors the
     * vanilla-slime / magma-cube tests, but exercises the {@code instanceof
     * CaveSlime} branch in {@code SlimeSplitDiscoveryHandler#categoryForParent}
     * which must be checked BEFORE the {@code getClass() == Slime.class} check
     * (CaveSlime extends Slime, so the strict-equality check would miss it).
     */
    private static void caveSlimeSplitDiscoveryConvertsToMineralResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.CAVE_SLIME.get(), Category.MINERAL);
    }

    /**
     * Geode Slime is the parent species for GEM — splitting one with 100%
     * discovery should give GEM ResourceSlimes. Same shape as the Cave Slime
     * test, just hitting the {@code instanceof GeodeSlime} branch.
     */
    private static void geodeSlimeSplitDiscoveryConvertsToGemResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.GEODE_SLIME.get(), Category.GEM);
    }

    /**
     * Tide Slime is the parent species for AQUATIC — splitting one with 100%
     * discovery should give AQUATIC ResourceSlimes. Same shape as the other
     * parent-species tests.
     */
    private static void tideSlimeSplitDiscoveryConvertsToAquaticResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.TIDE_SLIME.get(), Category.AQUATIC);
    }

    /**
     * Void Slime is the parent species for ARCANE — splitting one with 100%
     * discovery should give ARCANE ResourceSlimes. Closes the parent-species
     * test set (one per non-vanilla category).
     */
    private static void voidSlimeSplitDiscoveryConvertsToArcaneResourceSlime(GameTestHelper helper) {
        runSplitDiscoveryTest(helper, PFEntities.VOID_SLIME.get(), Category.ARCANE);
    }

    /**
     * Shared body for the discovery tests — force chance=1.0, split a size-3
     * parent, assert all children are ResourceSlimes of the expected category.
     * Restores chance via try/finally so subsequent tests aren't affected.
     */
    private static <T extends net.minecraft.world.entity.monster.Slime> void runSplitDiscoveryTest(
            GameTestHelper helper,
            net.minecraft.world.entity.EntityType<T> parentType,
            Category expectedCategory) {
        BlockPos pos = new BlockPos(2, 2, 2);
        Float originalOverride = SlimeSplitDiscoveryHandler.testOverride;
        SlimeSplitDiscoveryHandler.testOverride = 1.0f;
        try {
            T parent = helper.spawn(parentType, pos);
            parent.setSize(3, true);
            // Force death + split. setHealth(0) flips isDeadOrDying, then
            // remove(KILLED) runs vanilla's split which fires MobSplitEvent.
            parent.setHealth(0.0F);
            parent.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);

            helper.succeedWhen(() -> {
                List<ResourceSlime> resources = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
                if (resources.isEmpty()) {
                    helper.fail("expected at least one ResourceSlime from forced 100% discovery, got 0");
                }
                List<? extends net.minecraft.world.entity.monster.Slime> vanillaRemaining =
                    helper.getEntities(parentType);
                if (!vanillaRemaining.isEmpty()) {
                    helper.fail("expected zero vanilla " + parentType + " children after 100% conversion, "
                        + "got " + vanillaRemaining.size());
                }
                for (ResourceSlime slime : resources) {
                    if (slime.getCategory() != expectedCategory) {
                        helper.fail("discovered slime has category " + slime.getCategory()
                            + ", expected " + expectedCategory);
                    }
                }
            });
        } finally {
            SlimeSplitDiscoveryHandler.testOverride = originalOverride;
        }
    }

    /**
     * Spawn a size-3 ResourceSlime of one category, kill it, and assert the
     * split children are also ResourceSlimes of the same category. Verifies
     * the {@code Slime#remove} override propagates category through the
     * convertTo lambda.
     */
    private static void resourceSlimeSplitPreservesCategory(GameTestHelper helper) {
        Category cat = Category.INFERNAL;
        BlockPos spawnPos = new BlockPos(2, 2, 2);

        ResourceSlime parent = helper.spawn(PFEntities.RESOURCE_SLIME.get(), spawnPos);
        parent.setSize(3, true);
        parent.setCategory(cat);

        // Trigger death + split. Setting health to 0 makes isDeadOrDying() true,
        // and remove(KILLED) runs our override's split logic before delegating
        // to super.
        parent.setHealth(0.0F);
        parent.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);

        helper.succeedWhen(() -> {
            List<ResourceSlime> children = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            // The parent has been removed; remaining entities are split children.
            if (children.isEmpty()) {
                helper.fail("expected 2-4 ResourceSlime split children, got 0");
            }
            for (ResourceSlime child : children) {
                if (child.getCategory() != cat) {
                    helper.fail("split child has category " + child.getCategory() + ", expected " + cat);
                }
                if (child.getSize() != 1) {
                    helper.fail("split child has size " + child.getSize() + ", expected 1 (half of parent size 3 floor-divided)");
                }
            }
        });
    }

    /**
     * In-world integration check for the Slime Milker's variant pipeline.
     * Capture an iron-variant Resource Slime to a slime bucket, place the
     * milker, and verify the chain the block's {@code useItemOn} walks:
     * the bucket's {@code BUCKET_ENTITY_DATA} carries the variant identifier,
     * {@link PFFluidTypes#VARIANTS} recognizes the parsed path, and
     * {@link PFItems#MILK_BUCKETS} resolves it to the matching milk bucket
     * item. Parsing-edge cases (empty bucket, missing Variant tag, malformed
     * id) are covered by {@code SlimeMilkerBlockTest}; this test pins the
     * server-side data flow that the JUnit test can't reach.
     *
     * <p>The Slime Milker block is also placed and asserted present, which
     * confirms the block is registered and its default state is valid for
     * world placement. Client-side asset resolution (blockstates, models,
     * textures) is NOT exercised — the dedicated GameTest server doesn't
     * load client resource packs, so missing or malformed asset JSON has
     * to be caught by running {@code ./gradlew runClient} manually.
     */
    private static void slimeMilkerConvertsIronSlimeBucketIntoIronMilkBucket(GameTestHelper helper) {
        BlockPos milkerPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        // Capture an iron-variant slime — saveToBucketTag is the same write
        // path mobInteract walks when a player right-clicks the slime with
        // an empty slime bucket. Mirrors the slime_bucket_round_trip test's
        // setup but with a registered Variant so the milker has work to do.
        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        source.setSize(1, true);
        ResourceLocation ironVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        source.setVariant(ironVariant);
        if (source.getCategory() != Category.METALLIC) {
            helper.fail("setVariant(iron) should sync category to METALLIC, got " + source.getCategory());
            return;
        }

        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);

        // Pin the wire format the milker depends on: Variant is written as
        // the full ResourceLocation string. If saveToBucketTag is ever refactored
        // to write the bare path (or some struct shape), the milker silently
        // stops resolving variants — this assertion is the canary.
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("slime bucket BUCKET_ENTITY_DATA is null after saveToBucketTag");
            return;
        }
        String storedVariant = data.copyTag().getString("Variant").orElse(null);
        if (!ironVariant.toString().equals(storedVariant)) {
            helper.fail("expected Variant=" + ironVariant + " in bucket NBT, got " + storedVariant);
            return;
        }

        // Variant-resolution chain — the exact lookups SlimeMilkerBlock.useItemOn
        // performs after readBucketVariant returns. Stays in lockstep with
        // PFFluidTypes.VARIANTS + PFItems.MILK_BUCKETS so a drift between
        // either of those data structures and the milker's expectations
        // surfaces here.
        String variantPath = ironVariant.getPath();
        if (!PFFluidTypes.VARIANTS.contains(variantPath)) {
            helper.fail("PFFluidTypes.VARIANTS missing iron — milker would no-op on iron slimes");
            return;
        }
        if (PFItems.MILK_BUCKETS.get(variantPath) == null
            || PFItems.MILK_BUCKETS.get(variantPath).get() == null) {
            helper.fail("PFItems.MILK_BUCKETS.iron unbound — milker would NPE on iron slimes");
            return;
        }
        net.minecraft.world.item.Item ironMilkBucket = PFItems.MILK_BUCKETS.get(variantPath).get();
        ResourceLocation expected = BuiltInRegistries.ITEM.getKey(ironMilkBucket);
        ResourceLocation actual = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk_bucket");
        if (!expected.equals(actual)) {
            helper.fail("expected MILK_BUCKETS.iron to resolve to productivefrogs:iron_slime_milk_bucket, got " + expected);
            return;
        }

        // Place the milker block in-world. Catches any registry boot failure
        // (e.g. the block somehow not being registered) and exercises the
        // setBlock → BlockState resolution path. We do NOT invoke useItemOn
        // here — that requires a Player, and the JUnit test covers the
        // parser surface that drives useItemOn's branching.
        helper.setBlock(milkerPos, PFBlocks.SLIME_MILKER.get());
        helper.assertBlockPresent(PFBlocks.SLIME_MILKER.get(), milkerPos);

        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // J4 — Slime Milk source-block spawning
    // ---------------------------------------------------------------------

    /**
     * Happy path: an iron_slime_milk source block with a solid neighbour
     * directly east should spawn one size-1 iron ResourceSlime on top of
     * that neighbour when its tick fires.
     *
     * <p>Tick is invoked directly on the concrete block reference (same
     * pattern as {@code primedEggHatchesIntoMatchingCategoryTadpoles}) so we
     * don't sit through the 200–600-tick scheduled delay. The block widens
     * {@code tick} to public specifically to enable this.
     */
    private static void slimeMilkSourceSpawnsIronResourceSlimeOnSolidNeighbour(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        BlockPos neighbourPos = sourcePos.east();
        BlockPos expectedSpawnPos = neighbourPos.above();

        // Stone east of the source provides the solid landing pad. Other
        // neighbours stay air so the candidate iteration short-circuits on
        // the first hit and the spawn lands deterministically east-up.
        helper.setBlock(neighbourPos, Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            (com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock) PFBlocks.MILK_BLOCKS.get("iron").get();
        helper.setBlock(sourcePos, block);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);
        block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

        helper.succeedWhen(() -> {
            List<ResourceSlime> slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            if (slimes.size() != 1) {
                helper.fail("expected exactly 1 ResourceSlime, got " + slimes.size());
                return;
            }
            ResourceSlime slime = slimes.get(0);
            if (slime.getSize() != 1) {
                helper.fail("spawned slime has size " + slime.getSize() + ", expected 1");
            }
            ResourceLocation expectedVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
            if (!expectedVariant.equals(slime.getVariantId())) {
                helper.fail("spawned slime has variant " + slime.getVariantId() + ", expected " + expectedVariant);
            }
            // Position check: the slime should land on top of the stone
            // neighbour. Floor X and Z only — we deliberately skip the Y
            // assertion because gravity + bobbing physics move the slime
            // down within the polling window, which would make Y flaky.
            BlockPos absExpected = helper.absolutePos(expectedSpawnPos);
            int sx = net.minecraft.util.Mth.floor(slime.getX());
            int sz = net.minecraft.util.Mth.floor(slime.getZ());
            if (sx != absExpected.getX() || sz != absExpected.getZ()) {
                helper.fail("spawned slime at (" + sx + ", " + sz + "), expected ("
                    + absExpected.getX() + ", " + absExpected.getZ() + ")");
            }
        });
    }

    /**
     * No solid neighbour anywhere in the 3×3×3 cube around the source —
     * spawn picker falls back to the source's own position so the slime
     * appears inside the milk fluid. The milk block has noCollision, so
     * spawning the entity there always succeeds.
     *
     * <p>This pins the fallback branch in {@code chooseSpawnPos}: every
     * candidate in {@code NEIGHBOUR_OFFSETS} fails the sturdy check, the
     * loop exits, and the source position is returned.
     */
    private static void slimeMilkSourceFallsBackToLiquidWhenNoSolidNeighbour(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);

        // No neighbour blocks set — the empty 5x5x5 test plot is pure air
        // everywhere except the source itself. Every NEIGHBOUR_OFFSETS entry
        // points to an air block (not sturdy), so the picker exhausts the
        // list and returns source.
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            (com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock) PFBlocks.MILK_BLOCKS.get("copper").get();
        helper.setBlock(sourcePos, block);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);
        block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

        helper.succeedWhen(() -> {
            List<ResourceSlime> slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            if (slimes.size() != 1) {
                helper.fail("expected exactly 1 ResourceSlime from liquid fallback, got " + slimes.size());
                return;
            }
            ResourceSlime slime = slimes.get(0);
            ResourceLocation expectedVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper");
            if (!expectedVariant.equals(slime.getVariantId())) {
                helper.fail("spawned slime has variant " + slime.getVariantId() + ", expected " + expectedVariant);
            }
            // Position assertion: the fallback puts the slime AT the source
            // position. Floor X and Z only — Y is deliberately not checked
            // because gravity may settle the slime within the polling
            // window before the assertion runs.
            int sx = net.minecraft.util.Mth.floor(slime.getX());
            int sz = net.minecraft.util.Mth.floor(slime.getZ());
            if (sx != absSourcePos.getX() || sz != absSourcePos.getZ()) {
                helper.fail("liquid-fallback spawn at (" + sx + ", " + sz + "), expected ("
                    + absSourcePos.getX() + ", " + absSourcePos.getZ() + ")");
            }
        });
    }

    /**
     * vanilla_slime_milk maps to a vanilla {@code Slime} (no ResourceSlime
     * wrapper, no SlimeVariant). The variant-name switch in
     * {@code createSlimeForVariant} is what diverges here — if a refactor
     * accidentally routes "vanilla" through the ResourceSlime path, this
     * test catches it.
     */
    private static void vanillaSlimeMilkSourceSpawnsVanillaSlime(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            (com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock) PFBlocks.MILK_BLOCKS.get("vanilla").get();
        helper.setBlock(sourcePos, block);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);
        block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

        helper.succeedWhen(() -> {
            if (!helper.getEntities(PFEntities.RESOURCE_SLIME.get()).isEmpty()) {
                helper.fail("vanilla milk must NOT produce a ResourceSlime — the spawner should route through EntityType.SLIME");
                return;
            }
            // getEntities for vanilla Slime catches our ResourceSlime too
            // because ResourceSlime extends Slime — filter strictly by class
            // so we're asserting a vanilla green slime specifically.
            long vanillaCount = helper.getEntities(net.minecraft.world.entity.EntityType.SLIME).stream()
                .filter(s -> s.getClass() == net.minecraft.world.entity.monster.Slime.class)
                .count();
            if (vanillaCount != 1) {
                helper.fail("expected exactly 1 vanilla Slime, got " + vanillaCount);
            }
        });
    }

    /** magma_slime_milk → vanilla MagmaCube. Mirror of the vanilla-milk test. */
    private static void magmaSlimeMilkSourceSpawnsMagmaCube(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            (com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock) PFBlocks.MILK_BLOCKS.get("magma").get();
        helper.setBlock(sourcePos, block);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);
        block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

        helper.succeedWhen(() -> {
            List<net.minecraft.world.entity.monster.MagmaCube> cubes =
                helper.getEntities(net.minecraft.world.entity.EntityType.MAGMA_CUBE);
            if (cubes.size() != 1) {
                helper.fail("expected exactly 1 MagmaCube, got " + cubes.size());
            }
        });
    }

    /**
     * Block every horizontal neighbour at y=0 with non-sturdy blocks (no
     * rim candidate) but put a solid block directly below the source. The
     * picker should iterate past the y=0 plane and pick the y=-1 center
     * neighbour, returning {@code source} as the spawn pos (i.e. the slime
     * spawns at the source's coordinates, inside the milk).
     *
     * <p>This exercises the deeper iteration order — same-y plane fails
     * first, then y=-1 plane gets considered. Pins the algorithm's
     * "rim-first" priority without conflating it with the no-solid-anywhere
     * fallback (which is the previous test).
     */
    private static void slimeMilkSourcePicksSolidNeighbourBelowWhenNoHorizontalNeighbour(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);

        // Solid floor directly beneath the source. The y=-1 center neighbour
        // is sturdy; its .above() = source itself is non-blocking (milk has
        // noCollision). Picker returns source.
        helper.setBlock(sourcePos.below(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            (com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock) PFBlocks.MILK_BLOCKS.get("gold").get();
        helper.setBlock(sourcePos, block);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);
        block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

        helper.succeedWhen(() -> {
            List<ResourceSlime> slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            if (slimes.size() != 1) {
                helper.fail("expected exactly 1 ResourceSlime from below-floor pick, got " + slimes.size());
                return;
            }
            // Slime should land at source XZ (because the picked neighbour
            // is directly below the source).
            ResourceSlime slime = slimes.get(0);
            int sx = net.minecraft.util.Mth.floor(slime.getX());
            int sz = net.minecraft.util.Mth.floor(slime.getZ());
            if (sx != absSourcePos.getX() || sz != absSourcePos.getZ()) {
                helper.fail("expected spawn at source XZ (" + absSourcePos.getX() + ", " + absSourcePos.getZ()
                    + "), got (" + sx + ", " + sz + ")");
            }
        });
    }

    // ---------------------------------------------------------------------
    // J5 — Depletion counter
    // ---------------------------------------------------------------------

    /**
     * Place an iron milk source with the depletion counter explicitly set to
     * 5 (mid-life). Tick once and assert the resulting state's counter is
     * 4 — confirms the decrement edge of the tick loop runs exactly once
     * per successful spawn.
     *
     * <p>Forces {@code depletionEnabledOverride=true} so the test result is
     * stable regardless of whether the developer has flipped
     * {@code depletionEnabled} off in their local
     * {@code productivefrogs-common.toml}.
     */
    private static void slimeMilkSourceDecrementsSpawnsRemainingEachSpawn(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            (com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock) PFBlocks.MILK_BLOCKS.get("iron").get();
        BlockState stateWithFive = block.defaultBlockState().setValue(
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.SPAWNS_REMAINING, 5);
        helper.setBlock(sourcePos, stateWithFive);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);

        Boolean originalOverride =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

            BlockState after = level.getBlockState(absSourcePos);
            int afterCount = after.getValue(
                com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.SPAWNS_REMAINING);
            if (afterCount != 4) {
                helper.fail("expected SPAWNS_REMAINING=4 after one tick (started at 5), got " + afterCount);
                return;
            }
            // Sanity: the spawn itself still happened.
            if (helper.getEntities(PFEntities.RESOURCE_SLIME.get()).size() != 1) {
                helper.fail("expected 1 ResourceSlime to spawn during the decrementing tick");
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = originalOverride;
        }
    }

    /**
     * Place an iron milk source with SPAWNS_REMAINING=0 (counter exhausted)
     * and tick it. The tick must replace the block with air rather than
     * spawning a slime — this is the drain path. Also asserts no slime
     * appears, since the drain branch returns before {@code spawn()}.
     *
     * <p>Forces {@code depletionEnabledOverride=true} so a developer who
     * has flipped {@code depletionEnabled} off in their local config can
     * still run this suite.
     */
    private static void slimeMilkSourceDrainsWhenSpawnsRemainingReachesZero(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            (com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock) PFBlocks.MILK_BLOCKS.get("iron").get();
        BlockState drainedState = block.defaultBlockState().setValue(
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.SPAWNS_REMAINING, 0);
        helper.setBlock(sourcePos, drainedState);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);

        Boolean originalOverride =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

            // Block should be gone — the drain branch calls
            // level.setBlock(pos, AIR) explicitly (not removeBlock, which
            // would round-trip the fluid back to a default-state source).
            BlockState after = level.getBlockState(absSourcePos);
            if (!after.isAir()) {
                helper.fail("expected drained source pos to be air, got " + after);
                return;
            }
            // Drain branch returns early — no slime should have spawned.
            if (!helper.getEntities(PFEntities.RESOURCE_SLIME.get()).isEmpty()) {
                helper.fail("drain tick must NOT produce a slime, but one appeared");
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = originalOverride;
        }
    }

    /**
     * Sanity check: the block's default state has SPAWNS_REMAINING set to
     * MAX_SPAWNS_REMAINING (16). Pins the {@code registerDefaultState}
     * call in the constructor — if a refactor drops it, fresh placements
     * via {@code setBlock(pos, block)} would default to 0 and drain
     * immediately on first tick.
     */
    private static void slimeMilkSourceDefaultStateHasMaxSpawnsRemaining(GameTestHelper helper) {
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            (com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock) PFBlocks.MILK_BLOCKS.get("iron").get();
        int defaultCount = block.defaultBlockState().getValue(
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.SPAWNS_REMAINING);
        if (defaultCount != com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.MAX_SPAWNS_REMAINING) {
            helper.fail("default state SPAWNS_REMAINING=" + defaultCount
                + ", expected " + com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.MAX_SPAWNS_REMAINING);
            return;
        }
        helper.succeed();
    }

    /**
     * Regression pin for the 1.21.11 slime sizing migration. Vanilla
     * {@code EntityType.SLIME} uses {@code sized(0.52F, 0.52F)} +
     * {@code Slime#getDefaultDimensions(...).scale(getSize())} — the older
     * {@code sized(2.04F, 2.04F)} base with the historical {@code 0.255*size}
     * internal multiplier no longer holds, so reusing the old base value
     * produces hitboxes ~4× too large at every size. Caught in playtest;
     * this test stops it from happening again.
     *
     * <p>Spawn one of every custom slime species at size 1, snapshot
     * {@code getBbWidth()}, and assert it matches vanilla {@code Slime} at
     * size 1 within a tight tolerance (floating-point arithmetic on the
     * scale chain). If any custom slime diverges, the size of the base
     * dimensions in {@code PFEntities} drifted from vanilla.
     */
    private static void customSlimesSize1HitboxMatchesVanillaSlime(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);

        net.minecraft.world.entity.monster.Slime vanilla =
            helper.spawn(net.minecraft.world.entity.EntityType.SLIME, pos);
        vanilla.setSize(1, true);
        float expectedWidth = vanilla.getBbWidth();
        float expectedHeight = vanilla.getBbHeight();

        assertSize1HitboxMatches(helper, PFEntities.RESOURCE_SLIME.get(), "ResourceSlime", expectedWidth, expectedHeight);
        assertSize1HitboxMatches(helper, PFEntities.CAVE_SLIME.get(), "CaveSlime", expectedWidth, expectedHeight);
        assertSize1HitboxMatches(helper, PFEntities.GEODE_SLIME.get(), "GeodeSlime", expectedWidth, expectedHeight);
        assertSize1HitboxMatches(helper, PFEntities.TIDE_SLIME.get(), "TideSlime", expectedWidth, expectedHeight);
        assertSize1HitboxMatches(helper, PFEntities.VOID_SLIME.get(), "VoidSlime", expectedWidth, expectedHeight);

        helper.succeed();
    }

    private static <T extends net.minecraft.world.entity.monster.Slime> void assertSize1HitboxMatches(
            GameTestHelper helper,
            net.minecraft.world.entity.EntityType<T> type,
            String name,
            float expectedWidth,
            float expectedHeight) {
        BlockPos pos = new BlockPos(3, 2, 3);
        T slime = helper.spawn(type, pos);
        slime.setSize(1, true);
        float w = slime.getBbWidth();
        float h = slime.getBbHeight();
        // Tolerance handles the float-scale chain (base × size). 0.001 is
        // tight enough to catch any 4× regression while staying ahead of
        // legitimate float rounding.
        if (Math.abs(w - expectedWidth) > 0.001f) {
            helper.fail(name + " size-1 width " + w + " != vanilla " + expectedWidth);
        }
        if (Math.abs(h - expectedHeight) > 0.001f) {
            helper.fail(name + " size-1 height " + h + " != vanilla " + expectedHeight);
        }
    }

    // ---------------------------------------------------------------------
    // Smelting recipes
    // ---------------------------------------------------------------------

    /**
     * Every broad-strokes (category) Froglight should smelt to its canonical
     * resource. Pins the 6 plain-item smelting recipes — if a recipe JSON
     * is renamed, deleted, or has a wrong output, this catches it before
     * playtest.
     */
    private static void categoryFroglightSmeltRecipesResolveToCanonicalResource(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.item.crafting.RecipeManager rm = level.getServer().getRecipeManager();

        assertSmelts(helper, rm, level, PFBlocks.resourceFroglight(Category.METALLIC).asItem(), net.minecraft.world.item.Items.IRON_INGOT);
        assertSmelts(helper, rm, level, PFBlocks.resourceFroglight(Category.MINERAL).asItem(), net.minecraft.world.item.Items.REDSTONE);
        assertSmelts(helper, rm, level, PFBlocks.resourceFroglight(Category.GEM).asItem(), net.minecraft.world.item.Items.DIAMOND);
        assertSmelts(helper, rm, level, PFBlocks.resourceFroglight(Category.AQUATIC).asItem(), net.minecraft.world.item.Items.PRISMARINE_SHARD);
        assertSmelts(helper, rm, level, PFBlocks.resourceFroglight(Category.INFERNAL).asItem(), net.minecraft.world.item.Items.MAGMA_CREAM);
        assertSmelts(helper, rm, level, PFBlocks.resourceFroglight(Category.ARCANE).asItem(), net.minecraft.world.item.Items.ENDER_PEARL);

        helper.succeed();
    }

    /**
     * configurable_froglight stamped with a {@code slime_variant} component
     * should smelt to the variant's canonical resource. Pins the 12
     * component-ingredient smelting recipes against the
     * {@code neoforge:components} ingredient codec — if NeoForge ever
     * renames the discriminator (e.g. {@code neoforge:data_components}),
     * the recipes silently stop matching and this test catches it.
     */
    private static void variantConfigurableFroglightSmeltRecipesResolvePerVariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.item.crafting.RecipeManager rm = level.getServer().getRecipeManager();

        // Full coverage — all 12 variants. The component-ingredient pipeline
        // is the load-bearing piece here (one bad codec name and every
        // variant silently stops matching), so it's cheap insurance to
        // exercise the whole roster rather than rely on a sample.
        assertVariantSmelts(helper, rm, level, "iron", net.minecraft.world.item.Items.IRON_INGOT);
        assertVariantSmelts(helper, rm, level, "copper", net.minecraft.world.item.Items.COPPER_INGOT);
        assertVariantSmelts(helper, rm, level, "gold", net.minecraft.world.item.Items.GOLD_INGOT);
        assertVariantSmelts(helper, rm, level, "redstone", net.minecraft.world.item.Items.REDSTONE);
        assertVariantSmelts(helper, rm, level, "lapis", net.minecraft.world.item.Items.LAPIS_LAZULI);
        assertVariantSmelts(helper, rm, level, "coal", net.minecraft.world.item.Items.COAL);
        assertVariantSmelts(helper, rm, level, "diamond", net.minecraft.world.item.Items.DIAMOND);
        assertVariantSmelts(helper, rm, level, "emerald", net.minecraft.world.item.Items.EMERALD);
        assertVariantSmelts(helper, rm, level, "prismarine", net.minecraft.world.item.Items.PRISMARINE_SHARD);
        assertVariantSmelts(helper, rm, level, "sponge", net.minecraft.world.item.Items.SPONGE);
        assertVariantSmelts(helper, rm, level, "magma_cream", net.minecraft.world.item.Items.MAGMA_CREAM);
        assertVariantSmelts(helper, rm, level, "ender_pearl", net.minecraft.world.item.Items.ENDER_PEARL);

        helper.succeed();
    }

    /**
     * Negative: a configurable_froglight stack with no slime_variant
     * component should not match any of the 12 variant smelting recipes —
     * each recipe's ingredient is strict on the component. This pins the
     * "fail closed" semantic: no variant → no recipe → no spurious smelt
     * output. Without this, a refactor that loosens the ingredient's
     * strict flag would silently let bare configurable_froglights smelt
     * to whatever variant happens to sort first.
     */
    private static void configurableFroglightWithoutVariantDoesNotSmelt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.item.crafting.RecipeManager rm = level.getServer().getRecipeManager();

        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        net.minecraft.world.item.crafting.SingleRecipeInput input =
            new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe>> match =
            rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, level);
        if (match.isPresent()) {
            helper.fail("configurable_froglight without slime_variant component must NOT match any smelt recipe, but matched "
                + match.get().id() + " → " + match.get().value().assemble(input, level.registryAccess()));
            return;
        }
        helper.succeed();
    }

    /**
     * Round-trip: a variant-stamped configurable_froglight ItemStack placed
     * as a block, then broken, must produce a dropped ItemStack carrying the
     * same {@code SLIME_VARIANT} component. Pins three load-bearing pieces
     * of the placement/break loop together:
     *
     * <ol>
     *   <li>{@link com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity#setVariantId}
     *       persists the variant onto the BE.</li>
     *   <li>{@link com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity#collectImplicitComponents}
     *       exposes that variant as an implicit data component on the BE.</li>
     *   <li>The loot table at
     *       {@code data/productivefrogs/loot_table/blocks/configurable_froglight.json}
     *       uses {@code minecraft:copy_components} with source
     *       {@code block_entity} to copy that component onto the dropped item.</li>
     * </ol>
     *
     * <p>If any of those three pieces silently breaks, a variant-stamped
     * Iron Froglight would survive the placement but the broken-block drop
     * would lose the iron variant — i.e. the player would pick up an
     * unstamped configurable_froglight, lose smelt-recipe matching, and
     * regress to exactly the bug this PR fixes. This test fails closed in
     * that scenario.
     *
     * <p>The test exercises the post-placement path directly (set block +
     * call BE setter) rather than the BlockItem.useOn path. The two are
     * equivalent for our purposes — {@code ConfigurableFroglightItem.updateCustomBlockEntityTag}
     * is a thin wrapper that resolves the BE and calls the same setter —
     * and avoiding the UseOnContext / mock-player ceremony keeps the test
     * focused on the placement→drop invariant.
     */
    private static void variantFroglightRoundTripPreservesVariantThroughPlaceAndBreak(GameTestHelper helper) {
        BlockPos blockPos = new BlockPos(2, 2, 2);
        ResourceLocation ironVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(blockPos);

        // Mirror the placement code path: set the block, then write the
        // variant onto the BE (this is what ConfigurableFroglightItem's
        // updateCustomBlockEntityTag override does after vanilla seats the BE).
        level.setBlock(absPos,
            PFBlocks.CONFIGURABLE_FROGLIGHT.get().defaultBlockState(), 3);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity froglightBe)) {
            helper.fail("expected ConfigurableFroglightBlockEntity at " + blockPos
                + ", got " + (be == null ? "null" : be.getClass().getSimpleName()));
            return;
        }
        froglightBe.setVariantId(ironVariant);

        // Run the loot table. The 4-arg dropResources overload passes the
        // BE through LootContextParams.BLOCK_ENTITY, which copy_components
        // (source=block_entity) reads via BlockEntity.collectComponents().
        BlockState state = level.getBlockState(absPos);
        net.minecraft.world.level.block.Block.dropResources(state, level, absPos, froglightBe);

        helper.succeedWhen(() -> {
            net.minecraft.world.item.Item expected = PFItems.CONFIGURABLE_FROGLIGHT.get();
            net.minecraft.world.entity.item.ItemEntity match =
                helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                    .filter(e -> e.getItem().is(expected))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                helper.fail("expected configurable_froglight to drop after break");
                return;
            }
            ResourceLocation droppedVariant = match.getItem().get(
                com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get());
            if (!ironVariant.equals(droppedVariant)) {
                helper.fail("dropped variant=" + droppedVariant + ", expected " + ironVariant);
            }
        });
    }

    private static void assertSmelts(
            GameTestHelper helper,
            net.minecraft.world.item.crafting.RecipeManager rm,
            ServerLevel level,
            net.minecraft.world.item.Item input,
            net.minecraft.world.item.Item expectedOutput) {
        ItemStack stack = new ItemStack(input);
        net.minecraft.world.item.crafting.SingleRecipeInput recipeInput =
            new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe>> match =
            rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, recipeInput, level);
        if (match.isEmpty()) {
            helper.fail("no smelting recipe matches " + BuiltInRegistries.ITEM.getKey(input));
            return;
        }
        ItemStack output = match.get().value().assemble(recipeInput, level.registryAccess());
        if (!output.is(expectedOutput)) {
            helper.fail("smelt(" + BuiltInRegistries.ITEM.getKey(input) + ") = "
                + BuiltInRegistries.ITEM.getKey(output.getItem())
                + ", expected " + BuiltInRegistries.ITEM.getKey(expectedOutput));
        }
    }

    private static void assertVariantSmelts(
            GameTestHelper helper,
            net.minecraft.world.item.crafting.RecipeManager rm,
            ServerLevel level,
            String variant,
            net.minecraft.world.item.Item expectedOutput) {
        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        stack.set(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get(),
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant));
        net.minecraft.world.item.crafting.SingleRecipeInput recipeInput =
            new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe>> match =
            rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, recipeInput, level);
        if (match.isEmpty()) {
            helper.fail("no smelting recipe matches configurable_froglight[variant=" + variant + "]");
            return;
        }
        ItemStack output = match.get().value().assemble(recipeInput, level.registryAccess());
        if (!output.is(expectedOutput)) {
            helper.fail("smelt(configurable_froglight[variant=" + variant + "]) = "
                + BuiltInRegistries.ITEM.getKey(output.getItem())
                + ", expected " + BuiltInRegistries.ITEM.getKey(expectedOutput));
        }
    }

    // ---------------------------------------------------------------------
    // Q9 — Player direct-feeding
    // ---------------------------------------------------------------------

    /**
     * Happy path: matching-category Slime Bucket fed to the right Resource
     * Frog. Captures a METALLIC ResourceSlime (no variant) into a Slime
     * Bucket, spawns a METALLIC ResourceFrog, then simulates a right-click
     * by calling {@code frog.mobInteract(player, MAIN_HAND)} with the
     * bucket in the player's hand.
     *
     * <p>Asserts: SUCCESS result, broad-strokes metallic_froglight item
     * dropped at frog position, player's main hand now holds an empty
     * vanilla bucket (Slime Bucket consumed).
     */
    private static void directFeedMatchingCategoryDropsFroglightAndEmptiesBucket(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);

        // Build the bucket the same way the slime mob-interact path does —
        // saveToBucketTag on a sized-1 slime writes the canonical NBT shape.
        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        source.setCategory(Category.METALLIC);
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);
        source.discard();

        com.flatts.productivefrogs.content.entity.ResourceFrog frog =
            helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.METALLIC);

        net.minecraft.world.entity.player.Player player =
            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bucket);

        net.minecraft.world.InteractionResult result =
            frog.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (result != net.minecraft.world.InteractionResult.SUCCESS) {
            helper.fail("expected SUCCESS interaction, got " + result);
            return;
        }

        helper.succeedWhen(() -> {
            // Hand should now hold an empty vanilla bucket.
            ItemStack heldNow = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (!heldNow.is(net.minecraft.world.item.Items.BUCKET)) {
                helper.fail("expected hand to hold an empty bucket after direct-feed, got "
                    + BuiltInRegistries.ITEM.getKey(heldNow.getItem()));
                return;
            }
            // A broad-strokes metallic_froglight should have dropped at the
            // frog's position.
            net.minecraft.world.item.Item expected = PFBlocks.resourceFroglight(Category.METALLIC).asItem();
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> itemEntity.getItem().is(expected));
            if (!found) {
                helper.fail("expected " + BuiltInRegistries.ITEM.getKey(expected)
                    + " to drop at frog position after direct-feed");
            }
        });
    }

    /**
     * Variant path: an iron-variant Slime Bucket fed to a METALLIC Resource
     * Frog drops a {@code configurable_froglight} stamped with the iron
     * variant id (NOT the broad-strokes {@code metallic_froglight}). Mirrors
     * the variant_slime_kill_drops_configurable_froglight test but for the
     * player-driven path instead of the tongue-kill path.
     */
    private static void directFeedVariantSlimeDropsConfigurableFroglight(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        ResourceLocation ironVariant = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        source.setVariant(ironVariant);
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);
        source.discard();

        com.flatts.productivefrogs.content.entity.ResourceFrog frog =
            helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.METALLIC);

        net.minecraft.world.entity.player.Player player =
            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bucket);

        net.minecraft.world.InteractionResult result =
            frog.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (result != net.minecraft.world.InteractionResult.SUCCESS) {
            helper.fail("expected SUCCESS interaction for variant direct-feed, got " + result);
            return;
        }

        helper.succeedWhen(() -> {
            net.minecraft.world.item.Item expected = PFItems.CONFIGURABLE_FROGLIGHT.get();
            boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> {
                    ItemStack stack = itemEntity.getItem();
                    if (!stack.is(expected)) return false;
                    ResourceLocation variant = stack.get(
                        com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get());
                    return ironVariant.equals(variant);
                });
            if (!found) {
                helper.fail("expected configurable_froglight stamped with iron variant to drop");
            }
        });
    }

    /**
     * Mismatch path: a METALLIC slime bucket fed to an AQUATIC Resource
     * Frog must be a no-op. The bucket is NOT consumed, no froglight drops,
     * and the result returns PASS (so vanilla Animal#mobInteract continues
     * — slimeballs and name-tag still work as before).
     */
    private static void directFeedMismatchedCategoryIsANoOp(GameTestHelper helper) {
        BlockPos frogPos = new BlockPos(2, 2, 2);

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        source.setCategory(Category.METALLIC);
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);
        source.discard();

        com.flatts.productivefrogs.content.entity.ResourceFrog frog =
            helper.spawn(PFEntities.RESOURCE_FROG.get(), frogPos);
        frog.setCategory(Category.AQUATIC);

        net.minecraft.world.entity.player.Player player =
            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, bucket);

        net.minecraft.world.InteractionResult mismatchResult =
            frog.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);

        // Pin the mismatch contract: result must NOT be SUCCESS, otherwise
        // a refactor could silently swallow the interaction (treating it as
        // handled) without actually consuming the bucket or producing a
        // drop. The fall-through to super.mobInteract → Animal.mobInteract
        // with a non-breeding item returns PASS in vanilla 1.21.x, but
        // accept TRY_WITH_EMPTY_HAND too — both encode "not handled here".
        if (mismatchResult == net.minecraft.world.InteractionResult.SUCCESS
            || mismatchResult == net.minecraft.world.InteractionResult.CONSUME) {
            helper.fail("mismatched direct-feed returned " + mismatchResult
                + " — expected PASS or TRY_WITH_EMPTY_HAND from the super.mobInteract fallthrough");
            return;
        }

        // Allow a short window for any erroneous spawns to appear, then
        // assert nothing happened.
        helper.runAfterDelay(5L, () -> {
            // Bucket should still be a Slime Bucket — not consumed.
            ItemStack heldNow = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (!heldNow.is(PFItems.SLIME_BUCKET.get())) {
                helper.fail("mismatched direct-feed consumed the bucket — expected SLIME_BUCKET retained, got "
                    + BuiltInRegistries.ITEM.getKey(heldNow.getItem()));
                return;
            }
            // No Froglight items should have dropped.
            for (Category cat : Category.values()) {
                net.minecraft.world.item.Item froglight = PFBlocks.resourceFroglight(cat).asItem();
                boolean found = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                    .anyMatch(itemEntity -> itemEntity.getItem().is(froglight));
                if (found) {
                    helper.fail("mismatched direct-feed dropped a " + cat.id() + " Froglight — should have been a no-op");
                    return;
                }
            }
            boolean configurableDropped = helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                .anyMatch(itemEntity -> itemEntity.getItem().is(PFItems.CONFIGURABLE_FROGLIGHT.get()));
            if (configurableDropped) {
                helper.fail("mismatched direct-feed dropped a configurable_froglight — should have been a no-op");
                return;
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------------
    // Tank-mod compatibility (IFluidHandler via NeoForge capability)
    // ---------------------------------------------------------------------

    /**
     * NeoForge auto-registers {@link
     * net.neoforged.neoforge.capabilities.Capabilities.Fluid#ITEM} on every
     * vanilla {@link net.minecraft.world.item.BucketItem} subclass (see
     * {@code CapabilityHooks.registerVanillaProviders}). Our Slime Milk
     * buckets are vanilla BucketItems with our source fluid attached, so
     * tank-mod compatibility for the bucket form should work out of the box.
     *
     * <p>This test verifies it: spot-check three representative variants
     * (iron, magma_cream, vanilla) and assert each exposes a non-null
     * {@code ResourceHandler<FluidResource>} whose contents match the
     * variant's source fluid. If NeoForge ever drops the auto-registration
     * or our buckets stop being recognized as BucketItem subclasses, this
     * test catches it before a downstream modpack ships broken.
     *
     * <p>The block-level fluid capability ({@code Capabilities.Fluid.BLOCK})
     * is NOT registered by us — Productive Bees ships the same way for its
     * honey LiquidBlock. Tank mods that want to pump from a milk source
     * block use vanilla bucket-pickup mechanics on {@link
     * net.minecraft.world.level.block.LiquidBlock}, which our
     * {@link com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock}
     * inherits unchanged.
     */
    private static void milkBucketExposesFluidCapabilityForTankMods(GameTestHelper helper) {
        assertBucketExposesFluid(helper, "iron");
        assertBucketExposesFluid(helper, "magma_cream");
        assertBucketExposesFluid(helper, "vanilla");
        helper.succeed();
    }

    private static void assertBucketExposesFluid(GameTestHelper helper, String variant) {
        net.minecraft.world.item.BucketItem bucketItem = PFItems.MILK_BUCKETS.get(variant).get();
        ItemStack stack = new ItemStack(bucketItem);
        net.neoforged.neoforge.transfer.access.ItemAccess access =
            net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack);
        net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> handler =
            access.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM);
        if (handler == null) {
            helper.fail(variant + "_slime_milk_bucket exposes no Fluid.ITEM capability — "
                + "NeoForge's auto-registration on BucketItem broke");
            return;
        }
        if (handler.size() < 1) {
            helper.fail(variant + " bucket handler reports size " + handler.size() + ", expected >= 1");
            return;
        }
        net.neoforged.neoforge.transfer.fluid.FluidResource resource = handler.getResource(0);
        net.minecraft.world.level.material.Fluid expectedFluid =
            PFFluids.BY_VARIANT.get(variant).source().get();
        if (resource.getFluid() != expectedFluid) {
            helper.fail(variant + " bucket handler reports fluid "
                + BuiltInRegistries.FLUID.getKey(resource.getFluid())
                + ", expected " + BuiltInRegistries.FLUID.getKey(expectedFluid));
        }
    }

    // ---------------------------------------------------------------------
    // Slime Milker furnace-style BE (Q9b — known_issues.md redesign)
    // ---------------------------------------------------------------------

    /**
     * Happy path: place a Slime Milker, drop an iron-variant Slime Bucket
     * into its input slot, drive the server-tick by hand 100 times, and
     * assert the output slot holds an iron Slime Milk bucket and the
     * input slot is empty.
     */
    private static void slimeMilkerBeCooksIronBucketToIronMilkAfter100Ticks(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SLIME_MILKER.get());

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity milker)) {
            helper.fail("expected SlimeMilkerBlockEntity at " + absPos + ", got "
                + (be == null ? "null" : be.getClass().getSimpleName()));
            return;
        }

        // Build the iron-variant Slime Bucket via the real saveToBucketTag
        // path so the test pins the same NBT shape the in-world capture
        // flow produces.
        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        source.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);
        source.discard();

        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT, bucket);

        // Drive the cook loop. serverTick is a no-op on client; on server
        // it advances cookProgress by one per call. After exactly
        // COOK_TIME_TOTAL invocations the input should be consumed and
        // the output should be set.
        int total = com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.COOK_TIME_TOTAL;
        for (int i = 0; i < total; i++) {
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.serverTick(
                level, absPos, level.getBlockState(absPos), milker);
        }

        ItemStack input = milker.getInventory().getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT);
        ItemStack output = milker.getInventory().getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT);
        if (!input.isEmpty()) {
            helper.fail("expected input slot empty after cook, got "
                + BuiltInRegistries.ITEM.getKey(input.getItem()));
            return;
        }
        if (!output.is(PFItems.MILK_BUCKETS.get("iron").get())) {
            helper.fail("expected output to be iron_slime_milk_bucket, got "
                + (output.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(output.getItem())));
            return;
        }
        helper.succeed();
    }

    /**
     * Negative case: an empty Slime Bucket (no Variant component) sitting
     * in the input slot should NOT advance cook progress. Pins the
     * "fail-closed" semantic — a category-only or vanilla bucket can sit
     * in the input forever without producing a default-milk output.
     */
    private static void slimeMilkerBeResetsProgressWhenInputLacksVariant(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SLIME_MILKER.get());

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity milker)) {
            helper.fail("expected SlimeMilkerBlockEntity at " + absPos);
            return;
        }

        // Empty Slime Bucket — no BUCKET_ENTITY_DATA at all.
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT,
            new ItemStack(PFItems.SLIME_BUCKET.get()));

        // Tick 20 times — should never advance progress.
        for (int i = 0; i < 20; i++) {
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.serverTick(
                level, absPos, level.getBlockState(absPos), milker);
        }

        if (milker.getCookProgress() != 0) {
            helper.fail("expected cookProgress=0 with empty bucket input, got " + milker.getCookProgress());
            return;
        }
        ItemStack output = milker.getInventory().getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT);
        if (!output.isEmpty()) {
            helper.fail("output slot must remain empty when input has no variant, got "
                + BuiltInRegistries.ITEM.getKey(output.getItem()));
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Slime Milker hopper compat (Capabilities.Item.BLOCK migration)
    // ---------------------------------------------------------------------

    /**
     * Sanity-check the side-aware {@code Capabilities.Item.BLOCK} provider:
     * querying with {@link net.minecraft.core.Direction#DOWN} returns the
     * output view (sees OUTPUT_SLOT contents, refuses inserts); querying
     * with any other side returns the input view (sees INPUT_SLOT contents,
     * accepts only SLIME_BUCKET inserts). Pins the routing in
     * {@code PFModBusEvents#onRegisterCapabilities} without spinning up a
     * real hopper.
     */
    private static void slimeMilkerCapabilityRoutesInputViewToTopAndOutputViewToBottom(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SLIME_MILKER.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity milker)) {
            helper.fail("expected SlimeMilkerBlockEntity at " + absPos);
            return;
        }

        // Seed: a primed Slime Bucket in INPUT, a finished iron milk bucket in OUTPUT.
        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        source.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        ItemStack primedBucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(primedBucket);
        source.discard();
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT, primedBucket);
        ItemStack ironMilk = new ItemStack(PFItems.MILK_BUCKETS.get("iron").get());
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT, ironMilk);

        net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> downView =
            level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, absPos, net.minecraft.core.Direction.DOWN);
        net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> upView =
            level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, absPos, net.minecraft.core.Direction.UP);
        if (downView == null || upView == null) {
            helper.fail("capability not registered for SLIME_MILKER (downView="
                + downView + ", upView=" + upView + ")");
            return;
        }
        if (downView.size() != 1 || upView.size() != 1) {
            helper.fail("expected single-slot views, got downView.size=" + downView.size()
                + ", upView.size=" + upView.size());
            return;
        }
        // DOWN view sees the OUTPUT slot's iron milk bucket.
        if (!downView.getResource(0).is(PFItems.MILK_BUCKETS.get("iron").get())) {
            helper.fail("down view should see OUTPUT slot's iron milk bucket, got "
                + downView.getResource(0));
            return;
        }
        // UP view sees the INPUT slot's primed Slime Bucket.
        if (!upView.getResource(0).is(PFItems.SLIME_BUCKET.get())) {
            helper.fail("up view should see INPUT slot's slime bucket, got "
                + upView.getResource(0));
            return;
        }
        // DOWN view refuses insert (it's extract-only).
        if (downView.isValid(0, net.neoforged.neoforge.transfer.item.ItemResource.of(PFItems.SLIME_BUCKET.get()))) {
            helper.fail("down view must reject inserts (extract-only)");
            return;
        }
        // UP view accepts SLIME_BUCKET insert.
        if (!upView.isValid(0, net.neoforged.neoforge.transfer.item.ItemResource.of(PFItems.SLIME_BUCKET.get()))) {
            helper.fail("up view must accept SLIME_BUCKET inserts");
            return;
        }
        // UP view refuses unrelated items even though the underlying slot would accept SLIME_BUCKET.
        if (upView.isValid(0, net.neoforged.neoforge.transfer.item.ItemResource.of(Items.IRON_INGOT))) {
            helper.fail("up view must reject non-SLIME_BUCKET items");
            return;
        }
        helper.succeed();
    }

    /**
     * Hopper directly above the milker pushes an iron-variant Slime Bucket
     * into INPUT_SLOT via the capability. Verifies the side=UP routing in
     * {@code PFModBusEvents} actually works against a real vanilla
     * {@code HopperBlockEntity} — not just a synthetic capability query.
     */
    private static void hopperAboveSlimeMilkerPushesSlimeBucketIntoInputSlot(GameTestHelper helper) {
        BlockPos milkerPos = new BlockPos(2, 2, 2);
        BlockPos hopperPos = new BlockPos(2, 3, 2);
        helper.setBlock(milkerPos, PFBlocks.SLIME_MILKER.get());
        // Default hopper state faces DOWN — the orientation we want, since
        // the hopper at (2,3,2) needs to push down into the milker below.
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState());

        ServerLevel level = helper.getLevel();
        BlockPos absHopper = helper.absolutePos(hopperPos);
        net.minecraft.world.level.block.entity.BlockEntity hopperBe = level.getBlockEntity(absHopper);
        if (!(hopperBe instanceof net.minecraft.world.level.block.entity.HopperBlockEntity hopper)) {
            helper.fail("expected HopperBlockEntity at " + absHopper);
            return;
        }

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 2, 4));
        source.setSize(1, true);
        source.setVariant(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);
        source.discard();
        hopper.setItem(0, bucket);

        // Hopper transfer cooldown is 8 ticks by default; 30 ticks is
        // safely past one transfer cycle. succeedWhen retries every tick.
        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.BlockEntity be =
                level.getBlockEntity(helper.absolutePos(milkerPos));
            if (!(be instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity milker)) {
                helper.fail("milker BE went missing at " + helper.absolutePos(milkerPos));
                return;
            }
            ItemStack input = milker.getInventory().getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT);
            if (input.isEmpty() || !input.is(PFItems.SLIME_BUCKET.get())) {
                helper.fail("hopper has not yet pushed Slime Bucket into INPUT_SLOT (input="
                    + (input.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(input.getItem())) + ")");
                return;
            }
            // Re-resolve the hopper BE (it can be unloaded/reloaded
            // mid-test in some test rigs; safe to re-fetch each retry).
            if (level.getBlockEntity(absHopper) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity h
                && !h.getItem(0).isEmpty()) {
                helper.fail("hopper slot 0 should be drained after the push, still has "
                    + BuiltInRegistries.ITEM.getKey(h.getItem(0).getItem()));
            }
        });
    }

    /**
     * Hopper directly below the milker pulls the finished iron milk bucket
     * out of OUTPUT_SLOT via the side=DOWN capability route. Pre-populates
     * the OUTPUT slot directly (the full cook is covered by the existing
     * BE tick test); this scenario isolates the extract path.
     */
    private static void hopperBelowSlimeMilkerPullsMilkBucketFromOutputSlot(GameTestHelper helper) {
        BlockPos milkerPos = new BlockPos(2, 3, 2);
        BlockPos hopperPos = new BlockPos(2, 2, 2);
        helper.setBlock(milkerPos, PFBlocks.SLIME_MILKER.get());
        // Hopper below the milker; default-facing DOWN means it'll try to
        // push into air below it — fine, the relevant behavior is the pull
        // from the milker above, which happens regardless of facing.
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState());

        ServerLevel level = helper.getLevel();
        BlockPos absMilker = helper.absolutePos(milkerPos);
        BlockPos absHopper = helper.absolutePos(hopperPos);
        net.minecraft.world.level.block.entity.BlockEntity milkerBe = level.getBlockEntity(absMilker);
        if (!(milkerBe instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity milker)) {
            helper.fail("expected SlimeMilkerBlockEntity at " + absMilker);
            return;
        }
        ItemStack ironMilk = new ItemStack(PFItems.MILK_BUCKETS.get("iron").get());
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT, ironMilk);

        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.BlockEntity hopperBe = level.getBlockEntity(absHopper);
            if (!(hopperBe instanceof net.minecraft.world.level.block.entity.HopperBlockEntity hopper)) {
                helper.fail("hopper BE went missing at " + absHopper);
                return;
            }
            ItemStack pulled = hopper.getItem(0);
            if (pulled.isEmpty() || !pulled.is(PFItems.MILK_BUCKETS.get("iron").get())) {
                helper.fail("hopper has not yet pulled iron milk bucket (slot0="
                    + (pulled.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(pulled.getItem())) + ")");
                return;
            }
            // Milker OUTPUT must have been drained by the same transfer.
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absMilker);
            if (be instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity m) {
                ItemStack output = m.getInventory().getStackInSlot(
                    com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT);
                if (!output.isEmpty()) {
                    helper.fail("milker OUTPUT should be drained after pull, still has "
                        + BuiltInRegistries.ITEM.getKey(output.getItem()));
                }
            }
        });
    }
}
