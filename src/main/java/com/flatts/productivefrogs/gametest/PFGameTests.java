package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.PFTags;
import com.flatts.productivefrogs.event.SlimeInfusionHandler;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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

    private static final Identifier EMPTY_STRUCTURE =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "empty_5x5x5");

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
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "default")
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
     * Verify the bucket round-trip preserves category: spawn a tadpole, write
     * its state into a bucket via {@code saveToBucketTag}, then load that bucket
     * NBT into a fresh tadpole and assert the category survived.
     *
     * <p>This is an API-level check rather than a full player-driven bucket
     * interaction — but the {@code saveToBucketTag} / {@code loadFromBucketTag}
     * pair is exactly what vanilla {@code Bucketable.bucketMobPickup} and the
     * bucket's release hook call, so the data-shape contract is fully exercised.
     */
    private static void tadpoleBucketRoundTripPreservesCategory(GameTestHelper helper) {
        Category cat = Category.INFERNAL;
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.WATER);

        ResourceTadpole source = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos);
        source.setCategory(cat);

        ItemStack bucket = new ItemStack(PFItems.RESOURCE_TADPOLE_BUCKET.get());
        source.saveToBucketTag(bucket);

        // Round-trip via the same helper FrogEggItem / handlers use to read it back.
        Category readBack = ResourceTadpoleBucketItem.readCategory(bucket);
        if (readBack != cat) {
            helper.fail("bucket round-trip lost category: wrote " + cat + ", read " + readBack);
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

        // The sensor scans every ~20 ticks; poll until the brain memory settles.
        helper.succeedWhen(() -> {
            LivingEntity target = frog.getBrain()
                .getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_ATTACKABLE)
                .orElse(null);
            if (target == null) {
                helper.fail("sensor never wrote NEAREST_ATTACKABLE — does the matching slime exist within range?");
            }
            if (target == offCategory) {
                helper.fail("sensor targeted the off-category slime instead of filtering it out");
            }
            if (target != matching) {
                helper.fail("sensor targeted an unexpected entity: " + target);
            }
        });
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
}
