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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

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
        PFGameTests.test("vt_smelter_needs_no_rf", 40,
            VirtualTerrariumTests::smelterNeedsNoRf);
        PFGameTests.test("vt_overclock_shortens_interval", 40,
            VirtualTerrariumTests::overclockShortensInterval);
        PFGameTests.test("vt_no_dome_does_not_run", 40,
            VirtualTerrariumTests::noDomeDoesNotRun);
        // Spawn-budget feedstock economy (milk consumed only when its spawns run out).
        PFGameTests.test("vt_eat_spends_one_spawn", 40,
            VirtualTerrariumTests::eatSpendsOneSpawnNotLiquid);
        PFGameTests.test("vt_exhausting_spawns_empties_tank", 40,
            VirtualTerrariumTests::exhaustingSpawnsEmptiesTank);
        PFGameTests.test("vt_infinite_catalyst_never_depletes", 40,
            VirtualTerrariumTests::infiniteCatalystNeverDepletes);
        PFGameTests.test("vt_capacity_catalyst_raises_budget", 40,
            VirtualTerrariumTests::capacityCatalystRaisesBudget);
        PFGameTests.test("vt_bucket_drains_tank", 40,
            VirtualTerrariumTests::bucketDrainsTank);
        // Melter upgrade (the Smelter's molten-fluid sibling).
        PFGameTests.test("vt_melter_yields_molten_fluid", 40,
            VirtualTerrariumTests::melterYieldsMoltenFluid);
        PFGameTests.test("vt_melter_needs_no_rf", 40,
            VirtualTerrariumTests::melterNeedsNoRf);
        PFGameTests.test("vt_melter_not_charged_on_predator", 40,
            VirtualTerrariumTests::melterNotChargedOnPredator);
        // Stat-stacking upgrades.
        PFGameTests.test("vt_bounty_upgrade_adds_flat_output", 40,
            VirtualTerrariumTests::bountyUpgradeAddsFlatOutput);
        PFGameTests.test("vt_appetite_upgrade_shortens_interval", 40,
            VirtualTerrariumTests::appetiteUpgradeShortensInterval);
        PFGameTests.test("vt_overclock_stacks_capped", 40,
            VirtualTerrariumTests::overclockStacksCapped);
        PFGameTests.test("vt_overclock_no_power_holds_progress", 40,
            VirtualTerrariumTests::overclockNoPowerHoldsProgress);
        // Backpressure.
        PFGameTests.test("vt_output_full_stalls_production", 40,
            VirtualTerrariumTests::outputFullStallsProduction);
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

            // Spawn budget is spent, not the liquid (which stays at 1000mB until exhausted).
            helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
                "the predator cycle must not drain the liquid");
            helper.assertTrue(be.feedstockSpawnsRemaining() < be.feedstockSpawnsCapacity(),
                "the predator cycle must spend one spawn");
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

    /** The Smelter draws no RF: it smelts with an empty energy buffer. */
    private static void smelterNeedsNoRf(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        installUpgrade(be, 0, PFItems.VT_UPGRADE_SMELTER.get());
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));
        // deliberately no fillEnergy: only Overclock costs RF, so the Smelter still runs

        runCycle(helper, be);

        ItemStack out = firstOutput(be);
        helper.assertFalse(out.isEmpty(), "the Smelter must run without RF");
        helper.assertTrue(out.is(Items.COPPER_INGOT),
            "the Smelter must smelt the Copper Froglight to a copper ingot with no power, got " + out);
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

    // -- spawn-budget feedstock economy (milk consumed only when its spawns run out) --

    /** One eat spends one spawn and leaves the 1000mB liquid untouched. */
    private static void eatSpendsOneSpawnNotLiquid(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));
        int cap = be.feedstockSpawnsCapacity();

        runCycle(helper, be);

        helper.assertFalse(firstOutput(be).isEmpty(), "a matched frog must produce");
        helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
            "the liquid must stay at 1000mB - only the spawn budget is spent");
        helper.assertTrue(be.feedstockSpawnsRemaining() == cap - 1,
            "exactly one spawn must be spent (cap=" + cap + ", left=" + be.feedstockSpawnsRemaining() + ")");
        helper.succeed();
    }

    /** When the last spawn is spent, the milk is finally consumed - the tank empties. */
    private static void exhaustingSpawnsEmptiesTank(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        // Speed 100 collapses the reschedule interval to its floor so each runCycle
        // completes at least one eat (a speed-0 interval can exceed CYCLE_TICKS).
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 100));
        int cap = be.feedstockSpawnsCapacity();

        for (int i = 0; i < cap; i++) {
            runCycle(helper, be);
        }

        helper.assertTrue(be.getFeedstock().getFluid().isEmpty(),
            "the tank must empty once every spawn is spent (cap=" + cap + ")");
        helper.succeed();
    }

    /** Infinite (Endless) catalyst: the block produces forever and never spends a spawn. */
    private static void infiniteCatalystNeverDepletes(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        be.getFeedstock().setFluid(infiniteMilk(pf("copper")));
        int cap = be.feedstockSpawnsCapacity();

        for (int i = 0; i < 3; i++) {
            runCycle(helper, be);
        }

        helper.assertFalse(firstOutput(be).isEmpty(), "an Infinite-catalyst source must still produce");
        helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
            "Infinite feedstock must never empty");
        helper.assertTrue(be.feedstockSpawnsRemaining() == cap,
            "Infinite feedstock must never spend a spawn");
        helper.succeed();
    }

    /** A Count/Bountiful capacity gives a LARGER spawn budget than plain milk (more eats per bucket). */
    private static void capacityCatalystRaisesBudget(GameTestHelper helper) {
        VirtualTerrariumBlockEntity plain = placeProcessorAt(helper, new BlockPos(1, 2, 1), true);
        loadFrog(plain, FrogKind.resource(Category.CAVE));
        plain.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));

        VirtualTerrariumBlockEntity big = placeProcessorAt(helper, new BlockPos(3, 2, 3), true);
        loadFrog(big, FrogKind.resource(Category.CAVE));
        big.getFeedstock().setFluid(capacityMilk(pf("copper"), 1_000));

        helper.assertTrue(big.feedstockSpawnsCapacity() > plain.feedstockSpawnsCapacity(),
            "a Count/Bountiful capacity must raise the spawn budget (big=" + big.feedstockSpawnsCapacity()
                + " vs plain=" + plain.feedstockSpawnsCapacity() + ")");
        helper.succeed();
    }

    /** An empty bucket drains the loaded milk back out - variant + remaining spawns preserved, tank emptied. */
    private static void bucketDrainsTank(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));

        ItemStack filled = be.drainToBucket();

        helper.assertFalse(filled.isEmpty(), "draining must return a filled bucket");
        helper.assertTrue(filled.getItem() instanceof com.flatts.productivefrogs.content.item.SlimeMilkBucketItem,
            "the drained bucket must be a Slime Milk bucket, got " + filled);
        Identifier variant = filled.get(PFDataComponents.SLIME_VARIANT.get());
        helper.assertTrue(pf("copper").equals(variant), "the drained bucket must keep the variant, got " + variant);
        helper.assertTrue(be.getFeedstock().getFluid().isEmpty(), "draining must empty the tank");
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

    /** The Melter draws no RF: it melts with an empty energy buffer. */
    private static void melterNeedsNoRf(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        installUpgrade(be, 0, PFItems.VT_UPGRADE_MELTER.get());
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));
        // deliberately no fillEnergy: only Overclock costs RF, so the Melter still runs

        runCycle(helper, be);

        helper.assertTrue(be.getMoltenTank().getFluidAmount() > 0,
            "the Melter must melt the Copper Froglight without RF");
        helper.assertTrue(firstOutput(be).isEmpty(), "a melted Froglight must not land in the item output");
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

    /** Each Bounty upgrade adds a flat +1 Froglight; capped at 3 (a base 1 -> 4x1 output). */
    private static void bountyUpgradeAddsFlatOutput(GameTestHelper helper) {
        VirtualTerrariumBlockEntity plain = placeProcessorAt(helper, new BlockPos(1, 2, 1), true);
        loadFrog(plain, FrogKind.resource(Category.CAVE)); // min stats -> base 1 Froglight/eat
        plain.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));

        VirtualTerrariumBlockEntity rich = placeProcessorAt(helper, new BlockPos(3, 2, 3), true);
        loadFrog(rich, FrogKind.resource(Category.CAVE));
        // 4 Bounty upgrades -> +4 (a base 1 -> 5 Froglights, which still fits the 6 output slots).
        fillUpgrades(rich, PFItems.VT_UPGRADE_BOUNTY.get());
        rich.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));

        runCycleAt(helper, plain, new BlockPos(1, 2, 1));
        runCycleAt(helper, rich, new BlockPos(3, 2, 3));

        int plainCount = totalOutput(plain);
        int richCount = totalOutput(rich);
        helper.assertTrue(plainCount == 1, "a min-Bounty frog produces 1 Froglight/eat, got " + plainCount);
        helper.assertTrue(richCount == plainCount + 4,
            "each Bounty upgrade adds +1 output (expected " + (plainCount + 4) + ", got " + richCount + ")");
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

    /** An unpaid Overclock holds progress at zero (not frozen at full) and produces nothing. */
    private static void overclockNoPowerHoldsProgress(GameTestHelper helper) {
        VirtualTerrariumBlockEntity be = placeProcessor(helper, true);
        loadFrog(be, FrogKind.resource(Category.CAVE));
        installUpgrade(be, 0, PFItems.VT_UPGRADE_OVERCLOCK.get());
        be.getFeedstock().setFluid(slimeMilk(pf("copper"), 0));
        // no fillEnergy: the Overclock cannot be paid

        runCycle(helper, be);

        helper.assertTrue(firstOutput(be).isEmpty(), "an unpaid Overclock must not produce");
        helper.assertTrue(be.getDataAccess().get(VirtualTerrariumBlockEntity.DATA_PROGRESS) == 0,
            "an unpaid Overclock must hold progress at zero, not freeze at full");
        helper.assertTrue(be.getFeedstock().getFluidAmount() == VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY,
            "a power-stalled cycle must not consume feedstock");
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

        // An Overclock with an empty RF buffer -> needs power (the only upgrade that draws RF).
        installUpgrade(be, 0, PFItems.VT_UPGRADE_OVERCLOCK.get());
        helper.assertTrue(be.status() == VirtualTerrariumBlockEntity.Status.NEEDS_POWER,
            "Overclock + empty buffer -> NEEDS_POWER, got " + be.status());
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

        // Per-upgrade caps: a full Melter (cap 1) rejects a second; a full Bounty (cap 8) rejects a 9th.
        helper.assertTrue(inv.upgradeCap(PFItems.VT_UPGRADE_MELTER.get()) == 1, "Melter cap is 1");
        helper.assertFalse(inv.isItemValid(u1, new ItemStack(PFItems.VT_UPGRADE_MELTER.get())),
            "a second Melter must be rejected (cap 1)");
        inv.setStackInSlot(u0, new ItemStack(PFItems.VT_UPGRADE_BOUNTY.get(), 8));
        helper.assertTrue(inv.upgradeCap(PFItems.VT_UPGRADE_BOUNTY.get()) == 8, "Bounty cap is 8");
        helper.assertFalse(inv.isItemValid(u1, new ItemStack(PFItems.VT_UPGRADE_BOUNTY.get())),
            "a 9th Bounty must be rejected (cap 8)");
        inv.setStackInSlot(u0, new ItemStack(PFItems.VT_UPGRADE_BOUNTY.get(), 7));
        helper.assertTrue(inv.isItemValid(u1, new ItemStack(PFItems.VT_UPGRADE_BOUNTY.get())),
            "an 8th Bounty (under the cap) is accepted");

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
