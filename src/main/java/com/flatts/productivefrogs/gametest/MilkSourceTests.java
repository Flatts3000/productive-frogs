package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * In-world regression pins for the per-variant Slime Milk source block: the
 * variant round-trips through the source BlockEntity and the milk bucket, the
 * spawn picker chooses a sane landing position, the depletion counter decrements
 * and drains correctly, the source survives foreign-fluid floods, and the Slime
 * Milk catalysts (Count / Speed / Quantity / Infinite) apply and round-trip.
 *
 * <p>Ported from the 1.21.1 {@code PFGameTests} monolith. The spawn-economy /
 * catalyst APIs on {@code SlimeMilkSourceBlockEntity} are unchanged; the only
 * 26.1 deltas inside these bodies are the {@code EntityType.create} spawn-reason
 * argument and {@code moveTo -> snapTo} in the crowding test.
 */
final class MilkSourceTests {

    private MilkSourceTests() {
    }

    static void register() {
        PFGameTests.test("slime_milk_bucket_round_trips_variant_through_source_block", 100,
            MilkSourceTests::slimeMilkBucketRoundTripsVariantThroughSourceBlock);
        PFGameTests.test("slime_milk_bucket_round_trip_preserves_spawns_remaining", 100,
            MilkSourceTests::slimeMilkBucketRoundTripPreservesSpawnsRemaining);
        PFGameTests.test("slime_milk_source_spawns_iron_resource_slime_on_solid_neighbour", 100,
            MilkSourceTests::slimeMilkSourceSpawnsIronResourceSlimeOnSolidNeighbour);
        PFGameTests.test("slime_milk_source_falls_back_to_liquid_when_no_solid_neighbour", 100,
            MilkSourceTests::slimeMilkSourceFallsBackToLiquidWhenNoSolidNeighbour);
        PFGameTests.test("slime_milk_source_picks_solid_neighbour_below_when_no_horizontal_neighbour", 100,
            MilkSourceTests::slimeMilkSourcePicksSolidNeighbourBelowWhenNoHorizontalNeighbour);
        PFGameTests.test("slime_milk_source_pauses_when_area_is_crowded", 100,
            MilkSourceTests::slimeMilkSourcePausesWhenAreaIsCrowded);
        PFGameTests.test("slime_milk_source_decrements_spawns_remaining_each_spawn", 100,
            MilkSourceTests::slimeMilkSourceDecrementsSpawnsRemainingEachSpawn);
        PFGameTests.test("slime_milk_source_drains_when_spawns_remaining_reaches_zero", 100,
            MilkSourceTests::slimeMilkSourceDrainsWhenSpawnsRemainingReachesZero);
        PFGameTests.test("slime_milk_source_drains_same_tick_as_final_spawn", 100,
            MilkSourceTests::slimeMilkSourceDrainsSameTickAsFinalSpawn);
        PFGameTests.test("slime_milk_source_seeds_default_spawn_count_on_placement", 100,
            MilkSourceTests::slimeMilkSourceSeedsDefaultSpawnCountOnPlacement);
        PFGameTests.test("slime_milk_source_keeps_variant_across_depletion_ticks", 100,
            MilkSourceTests::slimeMilkSourceKeepsVariantAcrossDepletionTicks);
        PFGameTests.test("slime_milk_source_survives_water_flood", 60,
            MilkSourceTests::slimeMilkSourceSurvivesWaterFlood);
        PFGameTests.test("slime_milk_source_survives_lava_flood", 60,
            MilkSourceTests::slimeMilkSourceSurvivesLavaFlood);
        PFGameTests.test("catalyst_dropped_in_pool_is_consumed", 60,
            MilkSourceTests::catalystDroppedInPoolIsConsumed);
        PFGameTests.test("catalyst_infinite_source_never_drains", 100,
            MilkSourceTests::catalystInfiniteSourceNeverDrains);
        PFGameTests.test("catalyst_at_cap_is_not_consumed", 60,
            MilkSourceTests::catalystAtCapIsNotConsumed);
        PFGameTests.test("catalyst_upgrades_survive_bucket_round_trip", 100,
            MilkSourceTests::catalystUpgradesSurviveBucketRoundTrip);
    }

    /**
     * Round-trips the variant through the single component-driven Slime Milk
     * plumbing: a stamped milk bucket's placement hook ({@code checkExtraContent})
     * writes the variant to the source block's BlockEntity, and re-bucketing
     * ({@code pickupBlock}) reads it back onto the bucket. This is the path that
     * lets a datapack variant get milk with no per-variant registration.
     */
    private static void slimeMilkBucketRoundTripsVariantThroughSourceBlock(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        Identifier iron = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

        // 26.1 R-1: the single source block is inert until stamped. Placing an
        // iron-stamped bucket over it runs checkExtraContent, which reads the bucket's
        // SLIME_VARIANT component and writes iron onto the source's BE.
        helper.setBlock(pos, PFBlocks.SLIME_MILK_SOURCE.get().defaultBlockState());
        ItemStack bucket = milkBucket("iron");
        ((com.flatts.productivefrogs.content.item.SlimeMilkBucketItem) PFItems.SLIME_MILK_BUCKET.get())
            .checkExtraContent(null, level, bucket, abs);
        if (!(level.getBlockEntity(abs)
                instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity be)
            || !iron.equals(be.getVariantId())) {
            helper.fail("the iron source block should make the BE report variant iron");
            return;
        }

        // Re-bucket: pickupBlock returns the iron-variant filled bucket.
        ItemStack picked = PFBlocks.SLIME_MILK_SOURCE.get()
            .pickupBlock(null, level, abs, level.getBlockState(abs));
        if (!isMilkBucket(picked, "iron")) {
            helper.fail("pickupBlock should return an iron-stamped slime_milk_bucket, got " + picked);
            return;
        }
        helper.succeed();
    }

    /**
     * Re-bucketing a partially-depleted source preserves its spawns-remaining
     * counter through the world -> bucket -> world round-trip, so it can't be
     * refilled to full by re-bucketing (docs/known_issues.md). Place a variant
     * source at SPAWNS_REMAINING=5, re-bucket it (the bucket should carry 5),
     * then re-place via {@code checkExtraContent} and assert the restored source
     * reads 5 - not the default MAX.
     */
    private static void slimeMilkBucketRoundTripPreservesSpawnsRemaining(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        Identifier iron = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        var block = PFBlocks.SLIME_MILK_SOURCE.get();

        // Partially-depleted iron source: 5 spawns left (counter lives on the BE).
        helper.setBlock(pos, block.defaultBlockState());
        stampMilkVariant(helper, pos, "iron");
        setMilkSpawns(helper, pos, 5);

        // Re-bucket: the filled bucket must carry the remaining count.
        ItemStack picked = block.pickupBlock(null, level, abs, level.getBlockState(abs));
        Integer carried = picked.get(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get());
        if (carried == null || carried != 5) {
            helper.fail("re-bucketing a source with 5 spawns left should stamp SPAWNS_REMAINING=5 on the bucket, got "
                + carried);
            return;
        }

        // Re-place a fresh (default) source, then run the placement hook with
        // the carried bucket: it must restore the count to 5, not leave it full.
        helper.setBlock(pos, block.defaultBlockState());
        ((com.flatts.productivefrogs.content.item.SlimeMilkBucketItem) PFItems.SLIME_MILK_BUCKET.get())
            .checkExtraContent(null, level, picked, abs);
        int restored = getMilkSpawns(helper, pos);
        if (restored != 5) {
            helper.fail("re-placing the carried bucket should restore the count to 5, got " + restored);
            return;
        }
        helper.succeed();
    }

    /**
     * Iron milk source with a solid stone neighbour directly east should spawn
     * one size-1 iron ResourceSlime on top of that neighbour when its tick fires.
     *
     * <p>Tick is invoked directly on the concrete block reference so we don't sit
     * through the 200-600-tick scheduled delay. The block widens {@code tick} to
     * public specifically to enable this.
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
            placeMilkSource(helper, sourcePos, "iron");
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
            Identifier expectedVariant = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
            if (!expectedVariant.equals(slime.getVariantId())) {
                helper.fail("spawned slime has variant " + slime.getVariantId() + ", expected " + expectedVariant);
            }
            // Position check: the slime should land on top of the stone
            // neighbour. Floor X and Z only - we deliberately skip the Y
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
     * No solid neighbour anywhere in the 3x3x3 cube around the source -
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

        // No neighbour blocks set - the empty 5x5x5 test plot is pure air
        // everywhere except the source itself. Every NEIGHBOUR_OFFSETS entry
        // points to an air block (not sturdy), so the picker exhausts the
        // list and returns source.
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            placeMilkSource(helper, sourcePos, "copper");
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
            Identifier expectedVariant = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper");
            if (!expectedVariant.equals(slime.getVariantId())) {
                helper.fail("spawned slime has variant " + slime.getVariantId() + ", expected " + expectedVariant);
            }
            // Position assertion: the fallback puts the slime AT the source
            // position. Floor X and Z only - Y is deliberately not checked
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
     * Block every horizontal neighbour at y=0 with non-sturdy blocks (no
     * rim candidate) but put a solid block directly below the source. The
     * picker should iterate past the y=0 plane and pick the y=-1 center
     * neighbour, returning {@code source} as the spawn pos (i.e. the slime
     * spawns at the source's coordinates, inside the milk).
     *
     * <p>This exercises the deeper iteration order - same-y plane fails
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
            placeMilkSource(helper, sourcePos, "gold");
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

    /**
     * Density cap (v1.8): a source pauses spawning when its own species already
     * crowds the area, and crucially does NOT spend its remaining-spawn budget
     * while paused. Uses {@code spawnCapOverride=2} so the test needs only 2 slimes
     * instead of the default 30.
     */
    private static void slimeMilkSourcePausesWhenAreaIsCrowded(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);
        var block = PFBlocks.SLIME_MILK_SOURCE.get();
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 5);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);

        Boolean depOrig = com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        Integer capOrig = com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.spawnCapOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.spawnCapOverride = 2;
        try {
            // Seed the area with 2 iron slimes (the overridden cap) so the next tick is over-cap.
            for (int i = 0; i < 2; i++) {
                var slime = PFEntities.RESOURCE_SLIME.get()
                    .create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                if (slime == null) {
                    helper.fail("could not create ResourceSlime for the cap test");
                    return;
                }
                slime.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
                slime.setSize(1, true);
                slime.snapTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0F, 0F);
                level.addFreshEntity(slime);
            }
            int before = getMilkSpawns(helper, sourcePos);
            block.tick(level.getBlockState(abs), level, abs, level.getRandom());

            int after = getMilkSpawns(helper, sourcePos);
            if (after != before) {
                helper.fail("capped source must NOT spend its budget while paused; before="
                    + before + " after=" + after);
                return;
            }
            int count = helper.getEntities(PFEntities.RESOURCE_SLIME.get()).size();
            if (count != 2) {
                helper.fail("capped source must not spawn beyond the cap; expected 2 slimes, got " + count);
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = depOrig;
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.spawnCapOverride = capOrig;
        }
    }

    private static void slimeMilkSourceDecrementsSpawnsRemainingEachSpawn(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFBlocks.SLIME_MILK_SOURCE.get();
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 5);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);

        Boolean originalOverride =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

            int afterCount = getMilkSpawns(helper, sourcePos);
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
     * spawning a slime - this is the drain path. Also asserts no slime
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
            PFBlocks.SLIME_MILK_SOURCE.get();
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 0);
        ServerLevel level = helper.getLevel();
        BlockPos absSourcePos = helper.absolutePos(sourcePos);

        Boolean originalOverride =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            block.tick(level.getBlockState(absSourcePos), level, absSourcePos, level.getRandom());

            // Block should be gone - the drain branch calls
            // level.setBlock(pos, AIR) explicitly (not removeBlock, which
            // would round-trip the fluid back to a default-state source).
            BlockState after = level.getBlockState(absSourcePos);
            if (!after.isAir()) {
                helper.fail("expected drained source pos to be air, got " + after);
                return;
            }
            // Drain branch returns early - no slime should have spawned.
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
     * Regression: the final spawn and the drain happen in the SAME tick, so the
     * source never lingers at {@code SPAWNS_REMAINING=0}. Set the counter to 1 and
     * tick once: the source must spawn its one slime AND drain to air in that tick
     * (not reschedule and drain a full interval later). Before the fix the counter
     * hit 0 while the block stayed standing for one more spawn interval, which read
     * as an off-by-one (Jade said 0 yet the source was still there about to drain).
     */
    private static void slimeMilkSourceDrainsSameTickAsFinalSpawn(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);

        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFBlocks.SLIME_MILK_SOURCE.get();
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 1);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);

        Boolean originalOverride =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            block.tick(level.getBlockState(abs), level, abs, level.getRandom());

            // One slime spawned this tick.
            if (helper.getEntities(PFEntities.RESOURCE_SLIME.get()).size() != 1) {
                helper.fail("expected exactly 1 ResourceSlime from the final spawn");
                return;
            }
            // ...and the source drained in the SAME tick, not a later one.
            BlockState after = level.getBlockState(abs);
            if (!after.isAir()) {
                helper.fail("source with 1 spawn left must drain in the same tick as its "
                    + "final spawn, but the block still stands: " + after);
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = originalOverride;
        }
    }

    /**
     * Sanity check: a freshly-placed source, once its variant is set, seeds the
     * BlockEntity's remaining-spawn counter to the configured default
     * ({@code DEPLETION_COUNT}, default 16). The counter moved from a blockstate
     * property to the BE in v1.7 (so Count catalysts can raise it without bound);
     * this pins the {@code setVariantId -> seedIfUnset} path so a fresh placement
     * starts with a full budget rather than 0 (which would drain on first tick).
     */
    private static void slimeMilkSourceSeedsDefaultSpawnCountOnPlacement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFBlocks.SLIME_MILK_SOURCE.get();
        helper.setBlock(pos, block);
        stampMilkVariant(helper, pos, "iron");
        int expected = com.flatts.productivefrogs.PFConfig.SPEC.isLoaded()
            ? com.flatts.productivefrogs.PFConfig.DEPLETION_COUNT.get()
            : com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.MAX_SPAWNS_REMAINING;
        int seeded = getMilkSpawns(helper, pos);
        if (seeded != expected) {
            helper.fail("a fresh variant-stamped source should seed " + expected + " spawns, got " + seeded);
            return;
        }
        helper.succeed();
    }

    /**
     * Pin the "BlockEntity survives a same-block state change" invariant across
     * multiple depletion ticks. The decrement path uses {@code setBlock} with the
     * same block + a lower SPAWNS_REMAINING, which must keep the BE (and its
     * variant) alive. Set the counter to 2, tick three times, and assert the
     * source spawned exactly 2 iron slimes (variant intact each tick) then
     * drained to air. A regression that dropped the BE on state change would
     * make the 2nd spawn lose its variant (or fall back to a vanilla slime).
     */
    private static void slimeMilkSourceKeepsVariantAcrossDepletionTicks(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFBlocks.SLIME_MILK_SOURCE.get();
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 2);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);
        Identifier iron = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

        Boolean original =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            // Ticks are invoked manually (scheduled reschedules do not auto-fire
            // here): counter 2 -> 1 (tick 1 spawns), then 1 -> 0 (tick 2 spawns
            // AND drains in the same tick), so the block is air by tick 3. Exactly
            // 2 spawns either way; the 3rd tick is a no-op on the drained air block.
            block.tick(level.getBlockState(abs), level, abs, level.getRandom());
            block.tick(level.getBlockState(abs), level, abs, level.getRandom());
            block.tick(level.getBlockState(abs), level, abs, level.getRandom());

            if (!level.getBlockState(abs).isAir()) {
                helper.fail("source should drain to air after its 2 spawns, got " + level.getBlockState(abs));
                return;
            }
            List<ResourceSlime> slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            if (slimes.size() != 2) {
                helper.fail("expected 2 ResourceSlimes from 2 depletion ticks, got " + slimes.size());
                return;
            }
            for (ResourceSlime s : slimes) {
                if (!iron.equals(s.getVariantId())) {
                    helper.fail("a depletion-spawned slime lost its variant (BE not preserved): "
                        + s.getVariantId());
                    return;
                }
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = original;
        }
    }

    /**
     * Water poured onto a Slime Milk source must not wash it away (#235). A milk source
     * is a no-collision {@link com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock}
     * ({@code LiquidBlock}), so without the {@code LiquidBlockContainer} guard vanilla's
     * {@code canHoldFluid} lets a falling water source flow straight in and overwrite the
     * production pool. Places a water source directly above and lets it tick; the milk
     * source must still be a milk source afterwards (the water just sits on top).
     */
    private static void slimeMilkSourceSurvivesWaterFlood(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        placeMilkSource(helper, sourcePos, "iron");
        helper.setBlock(sourcePos.above(), Blocks.WATER);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);
        helper.runAfterDelay(20, () -> {
            BlockState state = level.getBlockState(abs);
            if (!(state.getBlock() instanceof com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock)) {
                helper.fail("water displaced the Slime Milk source -> " + state);
                return;
            }
            if (!level.getFluidState(abs).isSource()) {
                helper.fail("Slime Milk source is no longer a source fluid -> " + level.getFluidState(abs).getType());
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Lava (the "other fluids" half of #235) must not wash away a Slime Milk source
     * either - the same {@code LiquidBlockContainer} guard rejects every foreign fluid,
     * not just water. Mirror of {@link #slimeMilkSourceSurvivesWaterFlood} with lava.
     */
    private static void slimeMilkSourceSurvivesLavaFlood(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        placeMilkSource(helper, sourcePos, "iron");
        helper.setBlock(sourcePos.above(), Blocks.LAVA);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);
        helper.runAfterDelay(30, () -> {
            BlockState state = level.getBlockState(abs);
            if (!(state.getBlock() instanceof com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock)) {
                helper.fail("lava displaced the Slime Milk source -> " + state);
                return;
            }
            if (!level.getFluidState(abs).isSource()) {
                helper.fail("Slime Milk source is no longer a source fluid -> " + level.getFluidState(abs).getType());
                return;
            }
            helper.succeed();
        });
    }

    /**
     * De-risk the {@code entityInside} hook: a Count catalyst dropped INTO a real
     * milk source pool is consumed and raises the source's remaining-spawn count,
     * with no manual call - the item ticks naturally while overlapping the fluid
     * block. If {@code entityInside} does not fire for a pooled item this test
     * fails, flagging the need for the per-tick AABB-scan fallback.
     */
    private static void catalystDroppedInPoolIsConsumed(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFBlocks.SLIME_MILK_SOURCE.get();
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        setMilkSpawns(helper, sourcePos, 4);
        int baseline = getMilkSpawns(helper, sourcePos);

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
            level, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5,
            new ItemStack(PFItems.COUNT_CATALYST.get()));
        item.setNoGravity(true);
        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level.addFreshEntity(item);

        helper.succeedWhen(() -> helper.assertTrue(getMilkSpawns(helper, sourcePos) > baseline,
            "a Count catalyst in the pool should raise remaining spawns (entityInside must fire for pooled items)"));
    }

    /**
     * An infinite source (Infinite Count catalyst) never drains: even with the
     * remaining counter set to 1 and depletion forced on, repeated ticks keep
     * spawning without draining the block to air.
     */
    private static void catalystInfiniteSourceNeverDrains(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        helper.setBlock(sourcePos.east(), Blocks.STONE);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFBlocks.SLIME_MILK_SOURCE.get();
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        var be = milkBE(helper, sourcePos);
        if (be == null) {
            helper.fail("source BE missing after placement");
            return;
        }
        be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.INFINITE);
        be.setSpawnsRemaining(1);

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);
        Boolean original =
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride;
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = true;
        try {
            for (int i = 0; i < 5; i++) {
                block.tick(level.getBlockState(abs), level, abs, level.getRandom());
            }
            if (level.getBlockState(abs).isAir()) {
                helper.fail("an infinite source must not drain to air");
                return;
            }
            var beAfter = milkBE(helper, sourcePos);
            if (beAfter == null || beAfter.getSpawnsRemaining() != 1) {
                helper.fail("an infinite source must not decrement its counter, got "
                    + (beAfter == null ? "no BE" : beAfter.getSpawnsRemaining()));
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = original;
        }
    }

    /**
     * A catalyst that would no-op (here: a Speed catalyst on an already-maxed
     * source) is left unconsumed - the item floats for the player to retrieve
     * rather than being silently eaten. Pins the {@code applyCatalyst -> false ->
     * don't shrink} contract through the real {@code entityInside} path.
     */
    private static void catalystAtCapIsNotConsumed(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(2, 2, 2);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFBlocks.SLIME_MILK_SOURCE.get();
        helper.setBlock(sourcePos, block);
        stampMilkVariant(helper, sourcePos, "iron");
        var be = milkBE(helper, sourcePos);
        if (be == null) {
            helper.fail("source BE missing after placement");
            return;
        }
        // Max out speed so a further Speed catalyst is a no-op.
        while (be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.SPEED)) {
            // saturate
        }
        int maxedSpeed = be.getSpeedLevel();

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(sourcePos);
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
            level, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5,
            new ItemStack(PFItems.SPEED_CATALYST.get()));
        item.setNoGravity(true);
        item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level.addFreshEntity(item);

        helper.runAfterDelay(20L, () -> {
            if (item.isRemoved() || item.getItem().getCount() != 1) {
                helper.fail("a maxed-out Speed catalyst must not be consumed");
                return;
            }
            var beNow = milkBE(helper, sourcePos);
            if (beNow == null || beNow.getSpeedLevel() != maxedSpeed) {
                helper.fail("speed level should stay at the cap");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The full upgrade set (speed + quantity + infinite + remaining count) survives
     * the world -> bucket -> world round-trip: re-bucketing a buffed source stamps
     * the components, and re-placing the bucket restores them onto the new source.
     */
    private static void catalystUpgradesSurviveBucketRoundTrip(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            PFBlocks.SLIME_MILK_SOURCE.get();
        helper.setBlock(pos, block);
        stampMilkVariant(helper, pos, "iron");
        var be = milkBE(helper, pos);
        if (be == null) {
            helper.fail("source BE missing after placement");
            return;
        }
        be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.SPEED);
        be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.QUANTITY);
        be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.QUANTITY);
        be.applyCatalyst(com.flatts.productivefrogs.content.item.MilkCatalyst.INFINITE);
        int speed = be.getSpeedLevel();
        int quantity = be.getQuantityLevel();

        ItemStack picked = block.pickupBlock(null, level, abs, level.getBlockState(abs));
        Integer cSpeed = picked.get(com.flatts.productivefrogs.registry.PFDataComponents.MILK_SPEED.get());
        Integer cQuantity = picked.get(com.flatts.productivefrogs.registry.PFDataComponents.MILK_QUANTITY.get());
        Boolean cInfinite = picked.get(com.flatts.productivefrogs.registry.PFDataComponents.MILK_INFINITE.get());
        if (cSpeed == null || cSpeed != speed || cQuantity == null || cQuantity != quantity
                || !Boolean.TRUE.equals(cInfinite)) {
            helper.fail("re-bucketing must stamp speed=" + speed + " quantity=" + quantity
                + " infinite=true, got speed=" + cSpeed + " quantity=" + cQuantity + " infinite=" + cInfinite);
            return;
        }

        // Re-place a fresh source, run the placement hook, and confirm restore.
        helper.setBlock(pos, block);
        ((com.flatts.productivefrogs.content.item.SlimeMilkBucketItem)
                PFItems.SLIME_MILK_BUCKET.get())
            .checkExtraContent(null, level, picked, abs);
        var be2 = milkBE(helper, pos);
        if (be2 == null || be2.getSpeedLevel() != speed || be2.getQuantityLevel() != quantity || !be2.isInfinite()) {
            helper.fail("re-placing the bucket must restore the upgrades, got "
                + (be2 == null ? "no BE"
                    : "speed=" + be2.getSpeedLevel() + " quantity=" + be2.getQuantityLevel()
                        + " infinite=" + be2.isInfinite()));
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Helpers for the per-variant Slime Milk source model
    // ---------------------------------------------------------------------

    /** The per-variant Slime Milk bucket for productivefrogs:&lt;variantPath&gt;. */
    private static ItemStack milkBucket(String variantPath) {
        return PFItems.slimeMilkBucket(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantPath));
    }

    /** True if {@code stack} is the Slime Milk bucket stamped for productivefrogs:&lt;variantPath&gt;. */
    private static boolean isMilkBucket(ItemStack stack, String variantPath) {
        return stack.is(PFItems.SLIME_MILK_BUCKET.get())
            && Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantPath)
                .equals(SlimeMilkBucketItem.variantOf(stack));
    }

    /**
     * Place the Slime Milk source block at {@code pos} and stamp its
     * BlockEntity with productivefrogs:&lt;variantPath&gt; (what bucket placement
     * does in-world). Returns the block so tests can drive {@code tick} directly.
     */
    private static com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock placeMilkSource(
            GameTestHelper helper, BlockPos pos, String variantPath) {
        var block = PFBlocks.SLIME_MILK_SOURCE.get();
        // 26.1 R-1: the single source block has no baked variant; placing it is inert
        // until the BE is stamped (in-world the placing bucket's checkExtraContent does
        // this from its SLIME_VARIANT component). stampMilkVariant is that seed here.
        helper.setBlock(pos, block.defaultBlockState());
        stampMilkVariant(helper, pos, variantPath);
        return block;
    }

    /** Stamp an already-placed Slime Milk source block's BE with the variant. */
    private static void stampMilkVariant(GameTestHelper helper, BlockPos pos, String variantPath) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity be) {
            be.setVariantId(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantPath));
        }
    }

    /**
     * The source's spawn economy lives on its BlockEntity (v1.7; it used to be a
     * blockstate property). These three helpers read/write the remaining-spawn
     * counter for the depletion tests.
     */
    @org.jetbrains.annotations.Nullable
    private static com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity milkBE(
            GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity be ? be : null;
    }

    private static void setMilkSpawns(GameTestHelper helper, BlockPos pos, int remaining) {
        var be = milkBE(helper, pos);
        if (be != null) {
            be.setSpawnsRemaining(remaining);
        }
    }

    private static int getMilkSpawns(GameTestHelper helper, BlockPos pos) {
        var be = milkBE(helper, pos);
        return be != null ? be.getSpawnsRemaining() : -1;
    }
}
