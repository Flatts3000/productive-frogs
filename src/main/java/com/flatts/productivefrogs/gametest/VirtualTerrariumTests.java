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
        PFGameTests.test("vt_smelter_hard_stalls_without_rf", 40,
            VirtualTerrariumTests::smelterHardStallsWithoutRf);
        PFGameTests.test("vt_overclock_shortens_interval", 40,
            VirtualTerrariumTests::overclockShortensInterval);
        PFGameTests.test("vt_no_dome_does_not_run", 40,
            VirtualTerrariumTests::noDomeDoesNotRun);
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
        helper.assertTrue(be.getFeedstock().getFluidAmount() == 8_000,
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

            helper.assertTrue(be.getFeedstock().getFluidAmount() < 8_000,
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
        helper.assertTrue(be.getFeedstock().getFluidAmount() == 8_000,
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
        helper.assertTrue(be.getFeedstock().getFluidAmount() == 8_000,
            "a domeless Processor must not consume feedstock");
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
        FluidStack milk = new FluidStack(PFFluids.SLIME_MILK.get(), 8_000);
        milk.set(PFDataComponents.SLIME_VARIANT.get(), variant);
        if (speed > 0) {
            milk.set(PFDataComponents.MILK_SPEED.get(), speed);
        }
        return milk;
    }

    private static FluidStack mimicMilk(Identifier synthesized) {
        FluidStack milk = new FluidStack(PFFluids.MIMIC_MILK.get(), 8_000);
        milk.set(PFDataComponents.SYNTHESIZED_ITEM.get(), synthesized);
        return milk;
    }

    private static FluidStack mobSlurry(Identifier mob) {
        FluidStack slurry = new FluidStack(PFFluids.MOB_SLURRY.get(), 8_000);
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
