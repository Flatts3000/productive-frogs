package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumBlockEntity;
import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumInventory;
import com.flatts.productivefrogs.content.entity.FrogStats;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.item.EntityNetItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * In-world coverage for the Virtual Terrarium Processor (slices 1-5): the
 * Resource / Midas / Predator production paths, the Smelter upgrade (smelted
 * output and its RF hard-stall), the Overclock upgrade's interval cut, and the
 * Dome-above formation gate.
 *
 * <p>Each test drives {@link VirtualTerrariumBlockEntity#serverTick} directly in a
 * tight loop rather than waiting on the world ticker, so a full production cycle
 * (the 200-tick initial interval) resolves inside the test body deterministically.
 */
public final class VirtualTerrariumTests {

    private VirtualTerrariumTests() {
    }

    // Enough manual ticks to clear the 200-tick initial interval and reschedule once.
    private static final int CYCLE_TICKS = 205;

    private static final BlockPos PROCESSOR = new BlockPos(2, 2, 2);

    static void register() {
        PFGameTests.test("vt_resource_match_produces_variant_froglight", 40,
            VirtualTerrariumTests::resourceMatchProducesVariantFroglight);
        PFGameTests.test("vt_resource_mismatch_produces_nothing", 40,
            VirtualTerrariumTests::resourceMismatchProducesNothing);
        PFGameTests.test("vt_midas_produces_prismatic_froglight", 40,
            VirtualTerrariumTests::midasProducesPrismaticFroglight);
        PFGameTests.test("vt_predator_produces_loot_and_liquid_xp", 40,
            VirtualTerrariumTests::predatorProducesLootAndLiquidXp);
        PFGameTests.test("vt_smelter_yields_smelted_output", 40,
            VirtualTerrariumTests::smelterYieldsSmeltedOutput);
        PFGameTests.test("vt_smelter_hard_stalls_without_rf", 40,
            VirtualTerrariumTests::smelterHardStallsWithoutRf);
        PFGameTests.test("vt_overclock_shortens_interval", 40,
            VirtualTerrariumTests::overclockShortensInterval);
        PFGameTests.test("vt_no_dome_does_not_run", 40,
            VirtualTerrariumTests::noDomeDoesNotRun);
        // Catalyst-driven feedstock economy (replaces the retired Capacity/Everflow upgrades).
        PFGameTests.test("vt_infinite_catalyst_no_drain", 40,
            VirtualTerrariumTests::infiniteCatalystNoDrain);
        PFGameTests.test("vt_capacity_catalyst_slows_drain", 40,
            VirtualTerrariumTests::capacityCatalystSlowsDrain);
        // Melter upgrade (the Smelter's molten-fluid sibling).
        PFGameTests.test("vt_melter_yields_molten_fluid", 40,
            VirtualTerrariumTests::melterYieldsMoltenFluid);
        PFGameTests.test("vt_melter_hard_stalls_without_rf", 40,
            VirtualTerrariumTests::melterHardStallsWithoutRf);
        PFGameTests.test("vt_melter_not_charged_on_predator", 40,
            VirtualTerrariumTests::melterNotChargedOnPredator);
        // Stat-stacking upgrades.
        PFGameTests.test("vt_bounty_upgrade_raises_output_count", 40,
            VirtualTerrariumTests::bountyUpgradeRaisesOutputCount);
        PFGameTests.test("vt_appetite_upgrade_shortens_interval", 40,
            VirtualTerrariumTests::appetiteUpgradeShortensInterval);
        PFGameTests.test("vt_overclock_stacks_capped", 40,
            VirtualTerrariumTests::overclockStacksCapped);
        // Backpressure + fluid refund.
        PFGameTests.test("vt_output_full_stalls_production", 40,
            VirtualTerrariumTests::outputFullStallsProduction);
        PFGameTests.test("vt_drop_fluids_refunds_buckets", 40,
            VirtualTerrariumTests::dropFluidsRefundsBuckets);
        // Jade status / idle-reason readout.
        PFGameTests.test("vt_jade_status_reasons", 40,
            VirtualTerrariumTests::jadeStatusReasons);
        // Upgrade-slot validation (tag gating + Smelter/Melter exclusion) - needs the
        // loaded datapack tag, so it lives here rather than in the bare JUnit test.
        PFGameTests.test("vt_upgrade_slot_validation", 40,
            VirtualTerrariumTests::upgradeSlotValidation);
        // Hand bucket fill: exactly one 1000 mB charge, variant preserved, bucket returned.
        PFGameTests.test("vt_bucket_fills_tank", 40,
            VirtualTerrariumTests::bucketFillsTankExactlyOneCharge);
    }

    // -- the eight cases --

    /** Resource frog + matching-category Slime Milk -> a variant-stamped Froglight. */
    private static void resourceMatchProducesVariantFroglight(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        Identifier copper = pf("copper");
        be.getFeedstock().setFluid(slimeMilk(copper, 0));

        runCycle(helper, be);

        ItemStack out = firstOutput(be);
        helper.assertFalse(out.isEmpty(), "the Processor produced no output for a matched Resource frog");
        helper.assertTrue(out.is(PFItems.CONFIGURABLE_FROGLIGHT.get()),
            "expected a Configurable Froglight, got " + out);
        Identifier stamped = out.get(PFDataComponents.SLIME_VARIANT.get());
        helper.assertTrue(copper.equals(stamped),
            "the Froglight must carry the fed variant (copper), got " + stamped);
        helper.succeed();
    }

    /** Resource frog whose category does not match the fed variant -> nothing runs. */
    private static void resourceMismatchProducesNothing(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.TIDE)); // frog wants Tide; fed Cave milk
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));

        runCycle(helper, be);

        helper.assertTrue(firstOutput(be).isEmpty(),
            "a category mismatch must produce no Froglight");
        helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
            "a mismatch must not consume feedstock");
        helper.succeed();
    }

    /** Midas frog + Mimic Milk (equivalence forced on) -> a synthesized Prismatic Froglight. */
    private static void midasProducesPrismaticFroglight(GameTestHelper helper) {
        PFConfig.equivalenceEnabledOverride = Boolean.TRUE;
        try {
            VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
            loadFrog(be, FrogKind.MIDAS);
            Identifier diamond = BuiltInRegistries.ITEM.getKey(Items.DIAMOND);
            be.getFeedstock().setFluid(mimicMilk(diamond));

            runCycle(helper, be);

            ItemStack out = firstOutput(be);
            helper.assertFalse(out.isEmpty(), "the Processor produced no output for a Midas frog");
            helper.assertTrue(out.is(PFItems.CONFIGURABLE_FROGLIGHT.get()),
                "expected a Prismatic (synthesized) Froglight, got " + out);
            Identifier synthesized = out.get(PFDataComponents.SYNTHESIZED_ITEM.get());
            helper.assertTrue(diamond.equals(synthesized),
                "the Prismatic Froglight must carry the synthesized item id (diamond), got " + synthesized);
            helper.succeed();
        } finally {
            PFConfig.equivalenceEnabledOverride = null;
        }
    }

    /** Predator frog + matching Mob Slurry -> the mob's loot roll plus Liquid Experience. */
    private static void predatorProducesLootAndLiquidXp(GameTestHelper helper) {
        PFConfig.predatorsEnabledOverride = Boolean.TRUE;
        try {
            VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
            loadFrog(be, FrogKind.Predator.PROWLER);
            be.getFeedstock().setFluid(mobSlurry(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOMBIE)));

            runCycle(helper, be);

            helper.assertTrue(be.getFeedstock().getFluidAmount() < VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
                "the predator cycle must consume feedstock");
            helper.assertTrue(be.getXpTank().getFluidAmount() > 0,
                "a devoured mob must deposit Liquid Experience");
            helper.succeed();
        } finally {
            PFConfig.predatorsEnabledOverride = null;
        }
    }

    /** Smelter upgrade + RF: the raw Copper Froglight is auto-smelted to a copper ingot. */
    private static void smelterYieldsSmeltedOutput(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        be.getInventory().setStackInSlot(VirtualTerrariumInventory.UPGRADE_START,
            new ItemStack(PFItems.VT_UPGRADE_SMELTER.get()));
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));
        fillEnergy(be);

        runCycle(helper, be);

        ItemStack out = firstOutput(be);
        helper.assertFalse(out.isEmpty(), "the Smelter Processor produced no output");
        helper.assertTrue(out.is(Items.COPPER_INGOT),
            "the Smelter must convert the Copper Froglight to a copper ingot, got " + out);
        helper.succeed();
    }

    /** Smelter upgrade with an empty RF buffer: a hard stall, no output at all. */
    private static void smelterHardStallsWithoutRf(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        be.getInventory().setStackInSlot(VirtualTerrariumInventory.UPGRADE_START,
            new ItemStack(PFItems.VT_UPGRADE_SMELTER.get()));
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));
        // deliberately no fillEnergy: the Smelter's RF cost cannot be paid

        runCycle(helper, be);

        helper.assertTrue(firstOutput(be).isEmpty(),
            "an unpowered Smelter must hard-stall (no output)");
        helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
            "a hard-stalled cycle must not consume feedstock");
        helper.succeed();
    }

    /** One Overclock upgrade halves the rescheduled interval versus an un-upgraded Processor. */
    private static void overclockShortensInterval(GameTestHelper helper) {
        // A high Speed charge collapses the random spawn interval to the deterministic
        // floor, so the two rescheduled intervals differ only by the Overclock factor.
        VirtualTerrariumBlockEntity plain = placeProcessorAt(helper, new BlockPos(1, 2, 1), true);
        loadFrog(plain, FrogKind.resource(Category.CAVE));
        plain.getFeedstock().setFluid(slimeMilk(pf("copper"), 100));

        VirtualTerrariumBlockEntity fast = placeProcessorAt(helper, new BlockPos(3, 2, 3), true);
        loadFrog(fast, FrogKind.resource(Category.CAVE));
        fast.getInventory().setStackInSlot(VirtualTerrariumInventory.UPGRADE_START,
            new ItemStack(PFItems.VT_UPGRADE_OVERCLOCK.get()));
        fast.getFeedstock().setFluid(slimeMilk(pf("copper"), 100));
        fillEnergy(fast); // Overclock draws RF per cycle

        runCycleAt(helper, plain, new BlockPos(1, 2, 1));
        runCycleAt(helper, fast, new BlockPos(3, 2, 3));

        int base = plain.getDataAccess().get(VirtualTerrariumBlockEntity.DATA_INTERVAL);
        int overclocked = fast.getDataAccess().get(VirtualTerrariumBlockEntity.DATA_INTERVAL);
        helper.assertTrue(base > 0 && overclocked > 0,
            "both Processors must have rescheduled an interval (base=" + base + ", oc=" + overclocked + ")");
        helper.assertTrue(overclocked < base,
            "Overclock must shorten the interval (base=" + base + ", overclocked=" + overclocked + ")");
        helper.succeed();
    }

    /** No Dome above the Processor: it never runs, regardless of a loaded frog + feedstock. */
    private static void noDomeDoesNotRun(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, false); // no Dome
        loadFrog(be, FrogKind.resource(Category.CAVE));
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));

        runCycle(helper, be);

        helper.assertTrue(firstOutput(be).isEmpty(),
            "the Processor must not run without a Dome directly above it");
        helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
            "a domeless Processor must not consume feedstock");
        helper.succeed();
    }

    // -- catalyst-driven feedstock economy --

    /** Infinite (Endless) catalyst on the feedstock: the block still produces but never drains. */
    private static void infiniteCatalystNoDrain(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        be.getFeedstock().setFluid(infiniteMilk(pf("copper")));

        runCycle(helper, be);

        helper.assertFalse(firstOutput(be).isEmpty(), "an Infinite-catalyst source must still produce");
        helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
            "the Infinite catalyst must stop depletion, left " + be.getFeedstock().getFluidAmount());
        helper.succeed();
    }

    /** A high Count/Bountiful capacity drains proportionally LESS feedstock per cycle than plain milk. */
    private static void capacityCatalystSlowsDrain(GameTestHelper helper) {
        VirtualTerrariumBlockEntity plain = placeProcessorAt(helper, new BlockPos(1, 2, 1), true);
        loadFrog(plain, FrogKind.resource(Category.CAVE));
        plain.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));

        VirtualTerrariumBlockEntity big = placeProcessorAt(helper, new BlockPos(3, 2, 3), true);
        loadFrog(big, FrogKind.resource(Category.CAVE));
        big.getFeedstock().setFluid(capacityMilk(pf("copper"), 1_000));

        runCycleAt(helper, plain, new BlockPos(1, 2, 1));
        runCycleAt(helper, big, new BlockPos(3, 2, 3));

        helper.assertFalse(firstOutput(plain).isEmpty(), "plain source must produce");
        helper.assertFalse(firstOutput(big).isEmpty(), "high-capacity source must produce");
        int plainLeft = plain.getFeedstock().getFluidAmount();
        int bigLeft = big.getFeedstock().getFluidAmount();
        helper.assertTrue(plainLeft < VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY, "plain source must drain feedstock");
        helper.assertTrue(bigLeft > plainLeft,
            "a Count/Bountiful capacity must drain less per cycle (big=" + bigLeft + " vs plain=" + plainLeft + ")");
        helper.succeed();
    }

    // -- Melter upgrade --

    /** Melter + RF: the Copper Froglight is routed into the molten tank, not the item output. */
    private static void melterYieldsMoltenFluid(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        installUpgrade(be, 0, PFItems.VT_UPGRADE_MELTER.get());
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));
        fillEnergy(be);

        runCycle(helper, be);

        helper.assertTrue(be.getMoltenTank().getFluidAmount() > 0,
            "the Melter must route the Copper Froglight into molten fluid");
        helper.assertTrue(firstOutput(be).isEmpty(),
            "a melted Froglight must not also land in the item output");
        helper.succeed();
    }

    /** Melter with an empty RF buffer: hard stall - no molten, no output, no feedstock drain. */
    private static void melterHardStallsWithoutRf(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        installUpgrade(be, 0, PFItems.VT_UPGRADE_MELTER.get());
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));
        // deliberately no fillEnergy

        runCycle(helper, be);

        helper.assertTrue(be.getMoltenTank().getFluidAmount() == 0, "an unpowered Melter must produce no molten");
        helper.assertTrue(firstOutput(be).isEmpty(), "an unpowered Melter must produce no output");
        helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
            "a hard-stalled Melter cycle must not consume feedstock");
        helper.succeed();
    }

    /** A Melter only melts Froglights - it must cost no RF on the predator path (empty buffer still produces). */
    private static void melterNotChargedOnPredator(GameTestHelper helper) {
        PFConfig.predatorsEnabledOverride = Boolean.TRUE;
        try {
            VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
            loadFrog(be, FrogKind.Predator.PROWLER);
            installUpgrade(be, 0, PFItems.VT_UPGRADE_MELTER.get());
            be.getFeedstock().setFluid(mobSlurry(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOMBIE)));
            // no fillEnergy: if the Melter wrongly charged RF here, this would hard-stall

            runCycle(helper, be);

            helper.assertTrue(be.getXpTank().getFluidAmount() > 0,
                "the Melter must not charge RF on the predator path (empty buffer still produced)");
            helper.succeed();
        } finally {
            PFConfig.predatorsEnabledOverride = null;
        }
    }

    // -- stat-stacking upgrades --

    /** Bounty upgrades raise the Froglight count versus an un-upgraded Processor. */
    private static void bountyUpgradeRaisesOutputCount(GameTestHelper helper) {
        VirtualTerrariumBlockEntity plain = placeProcessorAt(helper, new BlockPos(1, 2, 1), true);
        loadFrog(plain, FrogKind.resource(Category.CAVE));
        plain.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));

        VirtualTerrariumBlockEntity rich = placeProcessorAt(helper, new BlockPos(3, 2, 3), true);
        loadFrog(rich, FrogKind.resource(Category.CAVE));
        fillUpgrades(rich, PFItems.VT_UPGRADE_BOUNTY.get());
        rich.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));

        runCycleAt(helper, plain, new BlockPos(1, 2, 1));
        runCycleAt(helper, rich, new BlockPos(3, 2, 3));

        int plainCount = totalOutput(plain);
        int richCount = totalOutput(rich);
        helper.assertTrue(plainCount >= 1, "plain source produced nothing");
        helper.assertTrue(richCount > plainCount,
            "Bounty upgrades must raise the Froglight count (rich=" + richCount + " vs plain=" + plainCount + ")");
        helper.succeed();
    }

    /** Appetite upgrades shorten the rescheduled interval versus an un-upgraded Processor. */
    private static void appetiteUpgradeShortensInterval(GameTestHelper helper) {
        VirtualTerrariumBlockEntity plain = placeProcessorAt(helper, new BlockPos(1, 2, 1), true);
        loadFrog(plain, FrogKind.resource(Category.CAVE));
        plain.getFeedstock().setFluid(slimeMilk(pf("copper"), 100));

        VirtualTerrariumBlockEntity fast = placeProcessorAt(helper, new BlockPos(3, 2, 3), true);
        loadFrog(fast, FrogKind.resource(Category.CAVE));
        fillUpgrades(fast, PFItems.VT_UPGRADE_APPETITE.get());
        fast.getFeedstock().setFluid(slimeMilk(pf("copper"), 100));

        runCycleAt(helper, plain, new BlockPos(1, 2, 1));
        runCycleAt(helper, fast, new BlockPos(3, 2, 3));

        int base = plain.getDataAccess().get(VirtualTerrariumBlockEntity.DATA_INTERVAL);
        int fastInterval = fast.getDataAccess().get(VirtualTerrariumBlockEntity.DATA_INTERVAL);
        helper.assertTrue(base > 0 && fastInterval > 0,
            "both Processors must have rescheduled an interval (base=" + base + ", fast=" + fastInterval + ")");
        helper.assertTrue(fastInterval < base,
            "Appetite upgrades must shorten the interval (base=" + base + ", fast=" + fastInterval + ")");
        helper.succeed();
    }

    /** Overclock stacking is capped: 3 and 4 stacks reschedule to the same interval. */
    private static void overclockStacksCapped(GameTestHelper helper) {
        VirtualTerrariumBlockEntity three = placeProcessorAt(helper, new BlockPos(1, 2, 1), true);
        loadFrog(three, FrogKind.resource(Category.CAVE));
        for (int i = 0; i < 3; i++) {
            installUpgrade(three, i, PFItems.VT_UPGRADE_OVERCLOCK.get());
        }
        three.getFeedstock().setFluid(slimeMilk(pf("copper"), 100));
        fillEnergy(three);

        VirtualTerrariumBlockEntity four = placeProcessorAt(helper, new BlockPos(3, 2, 3), true);
        loadFrog(four, FrogKind.resource(Category.CAVE));
        fillUpgrades(four, PFItems.VT_UPGRADE_OVERCLOCK.get()); // all four slots
        four.getFeedstock().setFluid(slimeMilk(pf("copper"), 100));
        fillEnergy(four);

        runCycleAt(helper, three, new BlockPos(1, 2, 1));
        runCycleAt(helper, four, new BlockPos(3, 2, 3));

        int i3 = three.getDataAccess().get(VirtualTerrariumBlockEntity.DATA_INTERVAL);
        int i4 = four.getDataAccess().get(VirtualTerrariumBlockEntity.DATA_INTERVAL);
        helper.assertTrue(i3 > 0 && i4 > 0, "both must reschedule (i3=" + i3 + ", i4=" + i4 + ")");
        helper.assertTrue(i3 == i4,
            "Overclock must cap at MAX_OVERCLOCK - 3 and 4 stacks give the same interval (i3=" + i3 + ", i4=" + i4 + ")");
        helper.succeed();
    }

    // -- backpressure + fluid refund --

    /** Every output slot jammed with a non-mergeable stack: production stalls, no feedstock consumed. */
    private static void outputFullStallsProduction(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));
        for (int i = 0; i < VirtualTerrariumInventory.OUTPUT_COUNT; i++) {
            be.getInventory().setStackInSlot(VirtualTerrariumInventory.OUTPUT_START + i,
                new ItemStack(Items.COBBLESTONE, 64));
        }

        runCycle(helper, be);

        helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
            "a full output must stall production (no feedstock consumed)");
        helper.succeed();
    }

    /** On break, dropFluids refunds the buffered feedstock and Liquid Experience as buckets - nothing voided. */
    private static void dropFluidsRefundsBuckets(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));                     // 1000 mB -> 1 bucket
        be.getXpTank().setFluid(new FluidStack(PFFluids.LIQUID_EXPERIENCE.get(), 3_000)); // -> 3 buckets

        BlockPos abs = helper.absolutePos(PROCESSOR);
        be.dropFluids(be.getLevel(), abs);

        List<ItemEntity> items = be.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3.0));
        long milk = items.stream().filter(e -> e.getItem().getItem()
            instanceof com.flatts.productivefrogs.content.item.SlimeMilkBucketItem).count();
        long xp = items.stream().filter(e -> e.getItem().is(PFItems.LIQUID_EXPERIENCE_BUCKET.get())).count();
        helper.assertTrue(milk == 1, "expected 1 Slime Milk bucket refunded, got " + milk);
        helper.assertTrue(xp == 3, "expected 3 Liquid Experience buckets refunded, got " + xp);
        helper.succeed();
    }

    // -- Jade status --

    /** The Jade status() readout returns the right idle reason for each state, in priority order. */
    private static void jadeStatusReasons(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, false); // no Dome
        helper.assertTrue(be.status() == VirtualTerrariumBlockEntity.Status.NO_DOME,
            "no dome -> NO_DOME, got " + be.status());

        helper.setBlock(PROCESSOR.above(), PFBlocks.VIRTUAL_TERRARIUM_DOME.get());
        helper.assertTrue(be.status() == VirtualTerrariumBlockEntity.Status.NO_FROG,
            "dome + no frog -> NO_FROG, got " + be.status());

        loadFrog(be, FrogKind.resource(Category.CAVE));
        helper.assertTrue(be.status() == VirtualTerrariumBlockEntity.Status.NO_FEEDSTOCK,
            "no feedstock -> NO_FEEDSTOCK, got " + be.status());

        // Wrong fluid for a Resource frog (Mimic Milk) -> a kind/feedstock mismatch.
        be.getFeedstock().setFluid(mimicMilk(BuiltInRegistries.ITEM.getKey(Items.DIAMOND)));
        helper.assertTrue(be.status() == VirtualTerrariumBlockEntity.Status.MISMATCH,
            "wrong feedstock -> MISMATCH, got " + be.status());

        // Matching feedstock, no powered upgrade -> producing.
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));
        helper.assertTrue(be.status() == VirtualTerrariumBlockEntity.Status.PRODUCING,
            "matched feedstock -> PRODUCING, got " + be.status());

        // A Smelter with an empty RF buffer -> needs power.
        installUpgrade(be, 0, PFItems.VT_UPGRADE_SMELTER.get());
        helper.assertTrue(be.status() == VirtualTerrariumBlockEntity.Status.NEEDS_POWER,
            "powered upgrade + empty buffer -> NEEDS_POWER, got " + be.status());
        helper.succeed();
    }

    /** Upgrade-slot tag gating and the Smelter/Melter mutual exclusion (both via isItemValid). */
    private static void upgradeSlotValidation(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        VirtualTerrariumInventory inv = be.getInventory();
        int u0 = VirtualTerrariumInventory.UPGRADE_START;
        int u1 = u0 + 1;

        helper.assertTrue(inv.isItemValid(u0, new ItemStack(PFItems.VT_UPGRADE_BOUNTY.get())),
            "an upgrade slot must accept a tagged upgrade");
        helper.assertFalse(inv.isItemValid(u0, new ItemStack(Items.COBBLESTONE)),
            "an upgrade slot must reject a non-upgrade");

        inv.setStackInSlot(u0, new ItemStack(PFItems.VT_UPGRADE_SMELTER.get()));
        helper.assertFalse(inv.isItemValid(u1, new ItemStack(PFItems.VT_UPGRADE_MELTER.get())),
            "a Melter must not go in beside a Smelter");

        inv.setStackInSlot(u0, new ItemStack(PFItems.VT_UPGRADE_MELTER.get()));
        helper.assertFalse(inv.isItemValid(u1, new ItemStack(PFItems.VT_UPGRADE_SMELTER.get())),
            "a Smelter must not go in beside a Melter");

        helper.assertFalse(inv.isItemValid(VirtualTerrariumInventory.FROG_SLOT,
            new ItemStack(PFItems.FROG_NET.get())), "an empty Frog Net is not a loaded frog");
        helper.succeed();
    }

    /** A hand milk bucket fills the tank to exactly one 1000 mB charge (variant kept), returns the empty bucket, and a second is rejected. */
    private static void bucketFillsTankExactlyOneCharge(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        ItemStack milkBucket = com.flatts.productivefrogs.content.item.SlimeMilkBucketItem.forVariant(pf("copper"));

        ItemStack empty = be.fillFromBucket(milkBucket);

        helper.assertFalse(empty.isEmpty(), "filling from a milk bucket must return an empty bucket");
        helper.assertTrue(empty.is(Items.BUCKET), "the returned item must be an empty bucket, got " + empty);
        helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
            "the tank must hold exactly one 1000 mB charge, got " + be.getFeedstock().getFluidAmount());
        helper.assertTrue(be.getFeedstock().getFluid().is(PFFluids.SLIME_MILK.get()), "the tank must hold Slime Milk");
        Identifier variant = be.getFeedstock().getFluid().get(PFDataComponents.SLIME_VARIANT.get());
        helper.assertTrue(pf("copper").equals(variant), "the fill must preserve the milk's variant, got " + variant);

        ItemStack second = be.fillFromBucket(
            com.flatts.productivefrogs.content.item.SlimeMilkBucketItem.forVariant(pf("copper")));
        helper.assertTrue(second.isEmpty(), "a full tank (exactly one charge) must reject a second bucket");
        helper.succeed();
    }

    // -- setup helpers --

    private static VirtualTerrariumBlockEntity placeProcessor(GameTestHelper helper, boolean withDome) {
        return placeProcessorAt(helper, PROCESSOR, withDome);
    }

    private static VirtualTerrariumBlockEntity placeProcessorAt(GameTestHelper helper, BlockPos pos, boolean withDome) {
        helper.setBlock(pos, PFBlocks.VIRTUAL_TERRARIUM.get());
        if (withDome) {
            helper.setBlock(pos.above(), PFBlocks.VIRTUAL_TERRARIUM_DOME.get());
        }
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof VirtualTerrariumBlockEntity be)) {
            helper.fail("expected a VirtualTerrariumBlockEntity at " + pos);
            throw new IllegalStateException("unreachable"); // helper.fail throws
        }
        return be;
    }

    /** Install a filled Frog Net carrying the given kind (min stats) into the frog slot. */
    private static void loadFrog(VirtualTerrariumBlockEntity be, FrogKind kind) {
        ServerLevel level = (ServerLevel) be.getLevel();
        ResourceFrog frog = PFEntities.RESOURCE_FROG.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (frog == null) {
            throw new IllegalStateException("could not create a ResourceFrog for the frog slot");
        }
        frog.setKind(kind);
        frog.setStats(FrogStats.STAT_MIN, FrogStats.STAT_MIN, FrogStats.STAT_MIN);
        ItemStack net = new ItemStack(PFItems.FROG_NET.get());
        EntityNetItem.captureEntity(frog, net);
        frog.discard();
        be.getInventory().setStackInSlot(VirtualTerrariumInventory.FROG_SLOT, net);
    }

    private static FluidStack slimeMilk(Identifier variant, int speed) {
        FluidStack milk = new FluidStack(PFFluids.SLIME_MILK.get(), VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY);
        milk.set(PFDataComponents.SLIME_VARIANT.get(), variant);
        if (speed > 0) {
            milk.set(PFDataComponents.MILK_SPEED.get(), speed);
        }
        return milk;
    }

    /** Slime Milk carrying the Infinite (Endless) catalyst - never depletes. */
    private static FluidStack infiniteMilk(Identifier variant) {
        FluidStack milk = slimeMilk(variant, 0);
        milk.set(PFDataComponents.MILK_INFINITE.get(), Boolean.TRUE);
        return milk;
    }

    /** Slime Milk carrying a high Count/Bountiful capacity - drains proportionally slower. */
    private static FluidStack capacityMilk(Identifier variant, int capacity) {
        FluidStack milk = slimeMilk(variant, 0);
        milk.set(PFDataComponents.MILK_CAPACITY.get(), capacity);
        return milk;
    }

    private static void installUpgrade(VirtualTerrariumBlockEntity be, int slot, net.minecraft.world.item.Item upgrade) {
        be.getInventory().setStackInSlot(VirtualTerrariumInventory.UPGRADE_START + slot, new ItemStack(upgrade));
    }

    /** Fill every upgrade slot with the given upgrade item (max stack of the effect). */
    private static void fillUpgrades(VirtualTerrariumBlockEntity be, net.minecraft.world.item.Item upgrade) {
        for (int i = 0; i < VirtualTerrariumInventory.UPGRADE_COUNT; i++) {
            installUpgrade(be, i, upgrade);
        }
    }

    /** Total item count across every output slot. */
    private static int totalOutput(VirtualTerrariumBlockEntity be) {
        int total = 0;
        for (int i = 0; i < VirtualTerrariumInventory.OUTPUT_COUNT; i++) {
            total += be.getInventory().getStackInSlot(VirtualTerrariumInventory.OUTPUT_START + i).getCount();
        }
        return total;
    }

    private static FluidStack mimicMilk(Identifier synthesized) {
        FluidStack milk = new FluidStack(PFFluids.MIMIC_MILK.get(), VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY);
        milk.set(PFDataComponents.SYNTHESIZED_ITEM.get(), synthesized);
        return milk;
    }

    private static FluidStack mobSlurry(Identifier mob) {
        FluidStack slurry = new FluidStack(PFFluids.MOB_SLURRY.get(), VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY);
        slurry.set(PFDataComponents.SLURRIED_ENTITY.get(), mob);
        return slurry;
    }

    private static void fillEnergy(VirtualTerrariumBlockEntity be) {
        for (int i = 0; i < 200; i++) {
            be.energyStorage().receiveEnergy(Integer.MAX_VALUE, false);
        }
    }

    private static void runCycle(GameTestHelper helper, VirtualTerrariumBlockEntity be) {
        runCycleAt(helper, be, PROCESSOR);
    }

    private static void runCycleAt(GameTestHelper helper, VirtualTerrariumBlockEntity be, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        for (int i = 0; i < CYCLE_TICKS; i++) {
            BlockState state = level.getBlockState(abs);
            VirtualTerrariumBlockEntity.serverTick(level, abs, state, be);
        }
    }

    private static ItemStack firstOutput(VirtualTerrariumBlockEntity be) {
        return be.getInventory().getStackInSlot(VirtualTerrariumInventory.OUTPUT_START);
    }

    private static Identifier pf(String path) {
        return Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, path);
    }
}
