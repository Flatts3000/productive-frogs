package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * In-world GameTests for the egg -> tadpole -> frog pipeline: Primed Frog Egg
 * survival on water, the hatch into matching-category tadpoles, tadpole
 * maturation into a same-category Resource Frog, the tadpole-bucket round-trip
 * (category and bred/pending stats), the deterministic hatch delay, and
 * baseline-stat maturation for non-bred frogspawn.
 *
 * <p>Migrated from the MC 1.21.1 annotation-based {@code PFGameTests} holder to
 * the 26.1 registration shape: each test is wired in {@link #register()} via
 * {@link PFGameTests#test(String, int, java.util.function.Consumer)} and the
 * body runs as a {@code private static} method taking one {@link GameTestHelper}.
 */
final class EggTadpoleFrogTests {

    private EggTadpoleFrogTests() {
    }

    static void register() {
        PFGameTests.test("primed_egg_breaks_when_water_removed", 100, EggTadpoleFrogTests::primedEggBreaksWhenWaterRemoved);
        PFGameTests.test("primed_egg_hatches_into_matching_category_tadpoles", 100, EggTadpoleFrogTests::primedEggHatchesIntoMatchingCategoryTadpoles);
        PFGameTests.test("tadpole_ages_up_into_resource_frog_of_same_category", 100, EggTadpoleFrogTests::tadpoleAgesUpIntoResourceFrogOfSameCategory);
        PFGameTests.test("tadpole_bucket_round_trip_preserves_category", 100, EggTadpoleFrogTests::tadpoleBucketRoundTripPreservesCategory);
        PFGameTests.test("tadpole_bucket_round_trip_preserves_pending_stats", 100, EggTadpoleFrogTests::tadpoleBucketRoundTripPreservesPendingStats);
        PFGameTests.test("primed_egg_schedules_deterministic_hatch_delay", 20, EggTadpoleFrogTests::primedEggSchedulesDeterministicHatchDelay);
        PFGameTests.test("non_bred_frog_matures_to_baseline_stats", 100, EggTadpoleFrogTests::nonBredFrogMaturesToBaselineStats);
    }

    /**
     * Place a Primed Frog Egg on a water source, then remove the water. The
     * block's {@code updateShape} runs {@code canSurvive}, sees no water below,
     * and replaces itself with air. Verifies the survive-on-water rule.
     */
    private static void primedEggBreaksWhenWaterRemoved(GameTestHelper helper) {
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);
        helper.setBlock(eggPos, PFBlocks.primedEgg(Category.BOG));
        helper.assertBlockPresent(PFBlocks.primedEgg(Category.BOG), eggPos);

        // Knock out the water — the egg should detect via neighbor update
        // (PrimedFrogEggBlock.updateShape → canSurvive false → air) and disappear.
        helper.setBlock(eggPos.below(), Blocks.AIR);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(PFBlocks.primedEgg(Category.BOG), eggPos));
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
        Category cat = Category.GEODE;
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
        Category cat = Category.VOID;
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
        //    BOG as the starting state so the assertion fails loudly if
        //    loadFromBucketTag silently no-ops.
        ResourceTadpole released = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos.east());
        released.setCategory(Category.BOG);
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
     * Bucketing a bred tadpole must preserve its inherited (pending) stats, the
     * same as a world save does - otherwise scooping a 10/10/10 tadpole and
     * re-placing it drops the stats and it matures into a baseline 1/1/1 frog
     * (the bug OldManLeroy reported). Mirrors the category round-trip above: write
     * a stat-stamped tadpole into a bucket, load it onto a fresh tadpole, and
     * assert the pending stats came back.
     */
    private static void tadpoleBucketRoundTripPreservesPendingStats(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.WATER);

        ResourceTadpole source = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos);
        source.setCategory(Category.GEODE);
        source.setPendingStats(10, 10, 10);

        ItemStack bucket = new ItemStack(PFItems.RESOURCE_TADPOLE_BUCKET.get());
        source.saveToBucketTag(bucket);

        ResourceTadpole released = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos.east());
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("bucket's BUCKET_ENTITY_DATA is unexpectedly null after saveToBucketTag");
            return;
        }
        released.loadFromBucketTag(data.copyTag());

        if (!released.hasPendingStats()
                || released.getPendingAppetite() != 10
                || released.getPendingBounty() != 10
                || released.getPendingReach() != 10) {
            helper.fail("released tadpole lost its bred stats through the bucket: hasPending="
                + released.hasPendingStats() + " stats=" + released.getPendingAppetite()
                + "/" + released.getPendingBounty() + "/" + released.getPendingReach()
                + " (expected 10/10/10)");
            return;
        }
        helper.succeed();
    }

    /**
     * A Primed Frog Egg schedules a <b>deterministic</b> hatch delay equal to the
     * config-exposed {@link com.flatts.productivefrogs.PFConfig#hatchTicks()}, not
     * vanilla's random {@code [3600, 12000)} window (docs/known_issues.md).
     * {@code onPlace} stamps the absolute hatch time on the BE, so we read it back
     * and assert the delay equals the fixed config value.
     */
    private static void primedEggSchedulesDeterministicHatchDelay(GameTestHelper helper) {
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);
        helper.setBlock(eggPos, PFBlocks.primedEgg(Category.CAVE));

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(eggPos);
        if (!(level.getBlockEntity(abs)
                instanceof com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity egg)) {
            helper.fail("primed egg should have a PrimedFrogEggBlockEntity after placement");
            return;
        }
        long delay = egg.getHatchGameTime() - level.getGameTime();
        int expected = com.flatts.productivefrogs.PFConfig.hatchTicks();
        if (delay != expected) {
            helper.fail("hatch delay should be the fixed config value " + expected + " ticks, got " + delay);
            return;
        }
        helper.succeed();
    }

    /**
     * A frog matured from a non-bred (crafted / Spawnery) frogspawn starts at
     * <b>baseline</b> stats - all {@code FrogStats.STAT_MIN} (1/1/1) - rather than
     * a random starter roll. Breeding is the only path above baseline
     * (docs/known_issues.md). A tadpole with no pending stats ages up through
     * {@code finalizeSpawn -> applyBaselineStats}.
     */
    private static void nonBredFrogMaturesToBaselineStats(GameTestHelper helper) {
        Category cat = Category.TIDE;
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.WATER);

        ResourceTadpole tadpole = helper.spawn(PFEntities.RESOURCE_TADPOLE.get(), pos);
        tadpole.setCategory(cat);
        // No setPendingStats() call -> non-bred. ageUp() runs finalizeSpawn.
        tadpole.ageUp();

        helper.succeedWhen(() -> {
            List<ResourceFrog> frogs = helper.getEntities(PFEntities.RESOURCE_FROG.get());
            if (frogs.size() != 1) {
                helper.fail("expected 1 Resource Frog after maturation, got " + frogs.size());
                return;
            }
            ResourceFrog frog = frogs.get(0);
            int min = com.flatts.productivefrogs.content.entity.FrogStats.STAT_MIN;
            if (frog.getAppetite() != min || frog.getBounty() != min || frog.getReach() != min) {
                helper.fail("non-bred frog should be baseline " + min + "/" + min + "/" + min + ", got "
                    + frog.getAppetite() + "/" + frog.getBounty() + "/" + frog.getReach());
            }
        });
    }
}
