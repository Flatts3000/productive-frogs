package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.SpawneryBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * In-world GameTests for the hand-operated appliance blocks: the Slime Milker,
 * the Slime Churn, the Spawnery, the Froglight furnace-fuel path, and the
 * milk-bucket capability surfaces (fluid + hopper item I/O).
 *
 * <p>Ported from the MC 1.21.1 {@code PFGameTests} annotation scaffold to the
 * 26.1 {@code DeferredRegister}-style registration that {@link PFGameTests#test}
 * drives. Each test is registered in {@link #register()} and is a plain
 * {@code private static void(GameTestHelper)}; structure NBT, helper methods, and
 * GameTestHelper usage are unchanged from 1.21.1.
 */
final class ApplianceTests {

    private ApplianceTests() {
    }

    static void register() {
        PFGameTests.test("slime_milker_converts_iron_slime_bucket_into_iron_milk_bucket", 100, ApplianceTests::slimeMilkerConvertsIronSlimeBucketIntoIronMilkBucket);
        PFGameTests.test("coal_froglight_fuels_furnace", 300, ApplianceTests::coalFroglightFuelsFurnace);
        PFGameTests.test("lava_froglight_burns_like_lava_bucket", 100, ApplianceTests::lavaFroglightBurnsLikeLavaBucket);
        PFGameTests.test("milk_bucket_exposes_fluid_capability_for_tank_mods", 100, ApplianceTests::milkBucketExposesFluidCapabilityForTankMods);
        PFGameTests.test("slime_milker_be_cooks_iron_bucket_to_iron_milk_after_100_ticks", 200, ApplianceTests::slimeMilkerBeCooksIronBucketToIronMilkAfter100Ticks);
        PFGameTests.test("slime_milker_be_resets_progress_when_input_lacks_variant", 100, ApplianceTests::slimeMilkerBeResetsProgressWhenInputLacksVariant);
        PFGameTests.test("slime_churn_produces_variant_slime_bucket_from_milk", 200, ApplianceTests::slimeChurnProducesVariantSlimeBucketFromMilk);
        PFGameTests.test("slime_churn_depletion_returns_empty_container_to_second_output", 200, ApplianceTests::slimeChurnDepletionReturnsEmptyContainerToSecondOutput);
        PFGameTests.test("slime_churn_pauses_without_empty_buckets", 200, ApplianceTests::slimeChurnPausesWithoutEmptyBuckets);
        PFGameTests.test("slime_churn_pauses_when_output_full", 200, ApplianceTests::slimeChurnPausesWhenOutputFull);
        PFGameTests.test("slime_churn_infinite_milk_never_depletes", 200, ApplianceTests::slimeChurnInfiniteMilkNeverDepletes);
        PFGameTests.test("slime_churn_quantity_batch_pays_one_budget", 200, ApplianceTests::slimeChurnQuantityBatchPaysOneBudget);
        PFGameTests.test("slime_churn_speed_catalyst_shortens_interval", 200, ApplianceTests::slimeChurnSpeedCatalystShortensInterval);
        PFGameTests.test("spawnery_slime_ball_primer_produces_vanilla_frogspawn", 100, ApplianceTests::spawnerySlimeBallPrimerProducesVanillaFrogspawn);
        PFGameTests.test("spawnery_iron_primer_produces_cave_egg", 100, ApplianceTests::spawneryIronPrimerProducesCaveEgg);
        PFGameTests.test("spawnery_without_fuel_does_not_produce", 100, ApplianceTests::spawneryWithoutFuelDoesNotProduce);
        PFGameTests.test("spawnery_without_bottle_does_not_consume_fuel", 100, ApplianceTests::spawneryWithoutBottleDoesNotConsumeFuel);
        PFGameTests.test("spawnery_without_primer_does_not_produce", 100, ApplianceTests::spawneryWithoutPrimerDoesNotProduce);
        PFGameTests.test("slime_milker_capability_routes_input_view_to_top_and_output_view_to_bottom", 100, ApplianceTests::slimeMilkerCapabilityRoutesInputViewToTopAndOutputViewToBottom);
        PFGameTests.test("hopper_above_slime_milker_pushes_slime_bucket_into_input_slot", 100, ApplianceTests::hopperAboveSlimeMilkerPushesSlimeBucketIntoInputSlot);
        PFGameTests.test("hopper_below_slime_milker_pulls_milk_bucket_from_output_slot", 100, ApplianceTests::hopperBelowSlimeMilkerPullsMilkBucketFromOutputSlot);
    }

    // ---------------------------------------------------------------------
    // Slime Milker
    // ---------------------------------------------------------------------

    private static void slimeMilkerConvertsIronSlimeBucketIntoIronMilkBucket(GameTestHelper helper) {
        BlockPos milkerPos = new BlockPos(2, 2, 2);
        BlockPos slimePos = new BlockPos(3, 2, 2);

        // Capture an iron-variant slime - saveToBucketTag is the same write
        // path mobInteract walks when a player right-clicks the slime with
        // an empty slime bucket. Mirrors the slime_bucket_round_trip test's
        // setup but with a registered Variant so the milker has work to do.
        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), slimePos);
        source.setSize(1, true);
        Identifier ironVariant = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        source.setVariant(ironVariant);
        if (source.getCategory() != Category.CAVE) {
            helper.fail("setVariant(iron) should sync category to CAVE (V1.5), got " + source.getCategory());
            return;
        }

        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);

        // Pin the wire format the milker depends on: Variant is written as
        // the full Identifier string. If saveToBucketTag is ever refactored
        // to write the bare path (or some struct shape), the milker silently
        // stops resolving variants - this assertion is the canary.
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("slime bucket BUCKET_ENTITY_DATA is null after saveToBucketTag");
            return;
        }
        String storedVariant = data.copyTag().getStringOr("Variant", "");
        if (!ironVariant.toString().equals(storedVariant)) {
            helper.fail("expected Variant=" + ironVariant + " in bucket NBT, got " + storedVariant);
            return;
        }

        // The milker now stamps the input bucket's variant onto the single
        // slime_milk_bucket. Pin the lookup it walks: readBucketVariantId must
        // return the full variant id parsed from the bucket NBT.
        Identifier parsed =
            com.flatts.productivefrogs.content.block.SlimeMilkerBlock.readBucketVariantId(bucket);
        if (!ironVariant.equals(parsed)) {
            helper.fail("expected readBucketVariantId=" + ironVariant + ", got " + parsed);
            return;
        }

        // Place the milker block in-world. Catches any registry boot failure
        // (e.g. the block somehow not being registered) and exercises the
        // setBlock -> BlockState resolution path. We do NOT invoke useItemOn
        // here - that requires a Player, and the JUnit test covers the
        // parser surface that drives useItemOn's branching.
        helper.setBlock(milkerPos, PFBlocks.SLIME_MILKER.get());
        helper.assertBlockPresent(PFBlocks.SLIME_MILKER.get(), milkerPos);

        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Froglight furnace fuel
    // ---------------------------------------------------------------------

    private static void coalFroglightFuelsFurnace(GameTestHelper helper) {
        BlockPos furnacePos = new BlockPos(2, 2, 2);
        helper.setBlock(furnacePos, Blocks.FURNACE);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(furnacePos))
                instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace)) {
            helper.fail("furnace BE missing");
            return;
        }
        ItemStack coalFroglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        coalFroglight.set(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get(),
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "coal"));
        furnace.setItem(0, new ItemStack(Items.COBBLESTONE)); // input
        furnace.setItem(1, coalFroglight);                   // fuel

        helper.succeedWhen(() -> helper.assertTrue(furnace.getItem(2).is(Items.STONE),
            "furnace should smelt cobblestone to stone using the Coal Froglight as fuel, got "
                + furnace.getItem(2)));
    }

    private static void lavaFroglightBurnsLikeLavaBucket(GameTestHelper helper) {
        ItemStack lavaFroglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        lavaFroglight.set(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get(),
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "lava"));
        // MIGRATION-FLAG (26.1): ItemStack#getBurnTime changed (now takes a
        // FuelValues arg). Left as the 1.21.1 single-arg form - needs the
        // 26.1 signature applied at compile (see migration notes).
        int froglightBurn = lavaFroglight.getBurnTime(null, helper.getLevel().fuelValues());
        int bucketBurn = new ItemStack(Items.LAVA_BUCKET).getBurnTime(null, helper.getLevel().fuelValues());
        if (froglightBurn != 20000 || froglightBurn != bucketBurn) {
            helper.fail("lava Froglight should burn 20000t (one lava bucket = " + bucketBurn
                + "), got " + froglightBurn);
            return;
        }
        ItemStack ironFroglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        ironFroglight.set(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get(),
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        if (ironFroglight.getBurnTime(null, helper.getLevel().fuelValues()) > 0) {
            helper.fail("a non-fuel variant (iron) Froglight must not be furnace fuel, got "
                + ironFroglight.getBurnTime(null, helper.getLevel().fuelValues()));
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Milk bucket fluid capability
    // ---------------------------------------------------------------------

    private static void milkBucketExposesFluidCapabilityForTankMods(GameTestHelper helper) {
        assertBucketExposesFluid(helper, "iron");
        assertBucketExposesFluid(helper, "blaze");
        helper.succeed();
    }

    // Ported to 26.1: the item-level fluid capability is Capabilities.Fluid.ITEM
    // over ResourceHandler<FluidResource> (was Capabilities.FluidHandler.ITEM /
    // IFluidHandlerItem on 1.21.1).
    private static void assertBucketExposesFluid(GameTestHelper helper, String variant) {
        ItemStack stack = milkBucket(variant);
        net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> handler =
            stack.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM,
                net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack));
        if (handler == null) {
            helper.fail(variant + "_slime_milk_bucket exposes no Fluid.ITEM capability - "
                + "NeoForge's auto-registration on BucketItem broke");
            return;
        }
        if (handler.size() < 1) {
            helper.fail(variant + " bucket handler reports " + handler.size() + " tanks, expected >= 1");
            return;
        }
        net.neoforged.neoforge.transfer.fluid.FluidResource contents = handler.getResource(0);
        // 26.1 R-1: one shared slime_milk fluid for every variant; the variant rides
        // the SLIME_VARIANT component on the FluidResource, not the fluid identity.
        net.minecraft.world.level.material.Fluid expectedFluid = PFFluids.SLIME_MILK.get();
        if (contents.getFluid() != expectedFluid) {
            helper.fail(variant + " bucket handler reports fluid "
                + BuiltInRegistries.FLUID.getKey(contents.getFluid())
                + ", expected " + BuiltInRegistries.FLUID.getKey(expectedFluid));
        }
        // The variant must survive the bucket -> FluidResource drain via the
        // SLIME_VARIANT copy in MilkBucketFluidResourceHandler - otherwise the
        // Terrarium Controller reads a null variant and rejects all piped milk.
        Identifier expectedVariant = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant);
        Identifier gotVariant = contents.get(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get());
        if (!expectedVariant.equals(gotVariant)) {
            helper.fail(variant + " bucket handler FluidResource lost SLIME_VARIANT: got " + gotVariant
                + ", expected " + expectedVariant);
        }
    }

    // ---------------------------------------------------------------------
    // Slime Milker furnace-style BE
    // ---------------------------------------------------------------------

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
        source.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
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
        if (!isMilkBucket(output, "iron")) {
            helper.fail("expected output to be iron-stamped slime_milk_bucket, got "
                + (output.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(output.getItem())));
            return;
        }
        helper.succeed();
    }

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

        // Empty Slime Bucket - no BUCKET_ENTITY_DATA at all.
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT,
            new ItemStack(PFItems.SLIME_BUCKET.get()));

        // Tick 20 times - should never advance progress.
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
    // Slime Churn (the Milker's inverse, placed-source spawn economy)
    // ---------------------------------------------------------------------

    /** Place a Slime Churn and return its BlockEntity, failing the test if absent. */
    @org.jetbrains.annotations.Nullable
    private static com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity placeChurn(
            GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, PFBlocks.SLIME_CHURN.get());
        net.minecraft.world.level.block.entity.BlockEntity be =
            helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(be instanceof com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity churn)) {
            helper.fail("expected SlimeChurnBlockEntity at " + helper.absolutePos(pos) + ", got "
                + (be == null ? "null" : be.getClass().getSimpleName()));
            return null;
        }
        return churn;
    }

    /** Fresh iron Slime Milk bucket (variant stamped, no budget components - the churn seeds them). */
    private static ItemStack ironMilkBucket(GameTestHelper helper) {
        return SlimeMilkBucketItem.forVariant(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
    }

    /** Drive the churn's serverTick {@code times} times, stopping early when the slime output fills. */
    private static int driveChurnUntilOutput(GameTestHelper helper,
            com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity churn,
            BlockPos pos, int times) {
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        for (int i = 0; i < times; i++) {
            com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity.serverTick(
                level, absPos, level.getBlockState(absPos), churn);
            if (!churn.getInventory().getStackInSlot(
                    com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.SLIME_OUTPUT_SLOT).isEmpty()) {
                return i + 1;
            }
        }
        return -1;
    }

    private static void slimeChurnProducesVariantSlimeBucketFromMilk(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        var churn = placeChurn(helper, pos);
        if (churn == null) {
            return;
        }
        var inv = churn.getInventory();
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT,
            ironMilkBucket(helper));
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.BUCKET_SLOT,
            new ItemStack(Items.BUCKET, 16));

        int maxTicks = com.flatts.productivefrogs.PFConfig.MAX_SPAWN_INTERVAL_TICKS.get() + 5;
        int fired = driveChurnUntilOutput(helper, churn, pos, maxTicks);
        if (fired < 0) {
            helper.fail("churn produced nothing within " + maxTicks + " ticks");
            return;
        }
        ItemStack out = inv.getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.SLIME_OUTPUT_SLOT);
        if (!out.is(PFItems.SLIME_BUCKET.get())) {
            helper.fail("expected slime_bucket output, got " + BuiltInRegistries.ITEM.getKey(out.getItem()));
            return;
        }
        Identifier outVariant =
            com.flatts.productivefrogs.content.block.SlimeMilkerBlock.readBucketVariantId(out);
        Identifier iron = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        if (!iron.equals(outVariant)) {
            helper.fail("expected output Variant " + iron + ", got " + outVariant);
            return;
        }
        ItemStack milk = inv.getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT);
        Integer remaining = milk.get(
            com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get());
        int expected = com.flatts.productivefrogs.PFConfig.DEPLETION_COUNT.get() - 1;
        if (remaining == null || remaining != expected) {
            helper.fail("expected milk SPAWNS_REMAINING=" + expected + ", got " + remaining);
            return;
        }
        ItemStack buckets = inv.getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.BUCKET_SLOT);
        if (buckets.getCount() != 15) {
            helper.fail("expected 15 empty buckets left, got " + buckets.getCount());
            return;
        }
        helper.succeed();
    }

    private static void slimeChurnDepletionReturnsEmptyContainerToSecondOutput(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        var churn = placeChurn(helper, pos);
        if (churn == null) {
            return;
        }
        var inv = churn.getInventory();
        ItemStack milk = ironMilkBucket(helper);
        milk.set(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get(), 1);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT, milk);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.BUCKET_SLOT,
            new ItemStack(Items.BUCKET, 4));

        int maxTicks = com.flatts.productivefrogs.PFConfig.MAX_SPAWN_INTERVAL_TICKS.get() + 5;
        if (driveChurnUntilOutput(helper, churn, pos, maxTicks) < 0) {
            helper.fail("churn produced nothing within " + maxTicks + " ticks");
            return;
        }
        if (!inv.getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT).isEmpty()) {
            helper.fail("expected milk slot cleared after depletion");
            return;
        }
        ItemStack spent = inv.getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.EMPTY_OUTPUT_SLOT);
        if (!spent.is(Items.BUCKET) || spent.getCount() != 1) {
            helper.fail("expected 1 empty bucket in the spent-container output, got "
                + (spent.isEmpty() ? "EMPTY" : spent.getCount() + "x" + BuiltInRegistries.ITEM.getKey(spent.getItem())));
            return;
        }
        helper.succeed();
    }

    private static void slimeChurnPausesWithoutEmptyBuckets(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        var churn = placeChurn(helper, pos);
        if (churn == null) {
            return;
        }
        var inv = churn.getInventory();
        ItemStack milk = ironMilkBucket(helper);
        milk.set(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get(), 5);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT, milk);

        int ticks = com.flatts.productivefrogs.PFConfig.MAX_SPAWN_INTERVAL_TICKS.get() + 50;
        if (driveChurnUntilOutput(helper, churn, pos, ticks) >= 0) {
            helper.fail("churn produced with no empty buckets available");
            return;
        }
        Integer remaining = inv.getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT)
            .get(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get());
        if (remaining == null || remaining != 5) {
            helper.fail("expected budget untouched at 5, got " + remaining);
            return;
        }
        // Furnace stall semantics: a blocked churn never advances progress -
        // with no empty buckets present the interval must not even start.
        if (churn.getIntervalTotal() != 0) {
            helper.fail("expected NO interval progress with no empty buckets, got total="
                + churn.getIntervalTotal());
            return;
        }
        helper.succeed();
    }

    private static void slimeChurnPausesWhenOutputFull(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        var churn = placeChurn(helper, pos);
        if (churn == null) {
            return;
        }
        var inv = churn.getInventory();
        ItemStack milk = ironMilkBucket(helper);
        milk.set(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get(), 5);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT, milk);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.BUCKET_SLOT,
            new ItemStack(Items.BUCKET, 16));
        // Pre-occupy the slime output.
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.SLIME_OUTPUT_SLOT,
            com.flatts.productivefrogs.content.item.SlimeBucketItem.forVariant(
                Category.CAVE, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper")));

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        int ticks = com.flatts.productivefrogs.PFConfig.MAX_SPAWN_INTERVAL_TICKS.get() + 50;
        for (int i = 0; i < ticks; i++) {
            com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity.serverTick(
                level, absPos, level.getBlockState(absPos), churn);
        }
        Integer remaining = inv.getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT)
            .get(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get());
        if (remaining == null || remaining != 5) {
            helper.fail("expected budget untouched at 5 with output full, got " + remaining);
            return;
        }
        if (inv.getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.BUCKET_SLOT).getCount() != 16) {
            helper.fail("expected no empty buckets consumed with output full");
            return;
        }
        // Furnace stall semantics: the progress arrow must not run while the
        // output is full - the interval never starts.
        if (churn.getIntervalTotal() != 0) {
            helper.fail("expected NO interval progress with output full, got total="
                + churn.getIntervalTotal());
            return;
        }
        helper.succeed();
    }

    private static void slimeChurnInfiniteMilkNeverDepletes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        var churn = placeChurn(helper, pos);
        if (churn == null) {
            return;
        }
        var inv = churn.getInventory();
        ItemStack milk = ironMilkBucket(helper);
        milk.set(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get(), 7);
        milk.set(com.flatts.productivefrogs.registry.PFDataComponents.MILK_INFINITE.get(), true);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT, milk);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.BUCKET_SLOT,
            new ItemStack(Items.BUCKET, 16));

        int maxTicks = com.flatts.productivefrogs.PFConfig.MAX_SPAWN_INTERVAL_TICKS.get() + 5;
        for (int production = 0; production < 2; production++) {
            if (driveChurnUntilOutput(helper, churn, pos, maxTicks) < 0) {
                helper.fail("infinite milk stopped producing on production #" + (production + 1));
                return;
            }
            inv.setStackInSlot(
                com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.SLIME_OUTPUT_SLOT,
                ItemStack.EMPTY);
        }
        ItemStack after = inv.getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT);
        Integer remaining = after.get(
            com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get());
        if (after.isEmpty() || remaining == null || remaining != 7) {
            helper.fail("expected infinite milk untouched at 7, got "
                + (after.isEmpty() ? "EMPTY SLOT" : String.valueOf(remaining)));
            return;
        }
        helper.succeed();
    }

    private static void slimeChurnQuantityBatchPaysOneBudget(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        var churn = placeChurn(helper, pos);
        if (churn == null) {
            return;
        }
        var inv = churn.getInventory();
        ItemStack milk = ironMilkBucket(helper);
        milk.set(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get(), 5);
        milk.set(com.flatts.productivefrogs.registry.PFDataComponents.MILK_QUANTITY.get(), 2);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT, milk);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.BUCKET_SLOT,
            new ItemStack(Items.BUCKET, 16));

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        int maxTicks = com.flatts.productivefrogs.PFConfig.MAX_SPAWN_INTERVAL_TICKS.get() + 5;
        if (driveChurnUntilOutput(helper, churn, pos, maxTicks) < 0) {
            helper.fail("churn produced nothing within " + maxTicks + " ticks");
            return;
        }
        // Batch should be 3 (1 + quantity 2): first emitted at fire, two pending.
        int produced = 1;
        while (churn.getPendingBatch() > 0 && produced < 10) {
            inv.setStackInSlot(
                com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.SLIME_OUTPUT_SLOT,
                ItemStack.EMPTY);
            com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity.serverTick(
                level, absPos, level.getBlockState(absPos), churn);
            if (!inv.getStackInSlot(
                    com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.SLIME_OUTPUT_SLOT).isEmpty()) {
                produced++;
            }
        }
        if (produced != 3) {
            helper.fail("expected a batch of 3 slime buckets (quantity 2), got " + produced);
            return;
        }
        Integer remaining = inv.getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT)
            .get(com.flatts.productivefrogs.registry.PFDataComponents.SPAWNS_REMAINING.get());
        if (remaining == null || remaining != 4) {
            helper.fail("expected ONE budget paid for the batch (5 -> 4), got " + remaining);
            return;
        }
        helper.succeed();
    }

    private static void slimeChurnSpeedCatalystShortensInterval(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        var churn = placeChurn(helper, pos);
        if (churn == null) {
            return;
        }
        var inv = churn.getInventory();
        int speedLevel = com.flatts.productivefrogs.PFConfig.catalystMaxSpeedLevel();
        ItemStack milk = ironMilkBucket(helper);
        milk.set(com.flatts.productivefrogs.registry.PFDataComponents.MILK_SPEED.get(), speedLevel);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.MILK_SLOT, milk);
        inv.setStackInSlot(com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory.BUCKET_SLOT,
            new ItemStack(Items.BUCKET, 16));

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity.serverTick(
            level, absPos, level.getBlockState(absPos), churn);

        // Recompute the modulated bounds with the same formula the economy uses.
        int baseMin = com.flatts.productivefrogs.PFConfig.MIN_SPAWN_INTERVAL_TICKS.get();
        int baseMax = com.flatts.productivefrogs.PFConfig.MAX_SPAWN_INTERVAL_TICKS.get();
        double factor = Math.max(0.0,
            1.0 - speedLevel * com.flatts.productivefrogs.PFConfig.catalystSpeedReductionPerLevel());
        int floor = com.flatts.productivefrogs.PFConfig.catalystMinIntervalFloorTicks();
        int modMin = Math.max(floor, (int) Math.round(baseMin * factor));
        int modMax = Math.max(floor, (int) Math.round(baseMax * factor));

        int total = churn.getIntervalTotal();
        if (total < modMin || total > modMax) {
            helper.fail("expected interval in [" + modMin + ", " + modMax + "], got " + total);
            return;
        }
        if (modMax < baseMin && total >= baseMin) {
            helper.fail("speed catalyst did not shorten the interval below base min " + baseMin);
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------
    // Spawnery (skyblock bootstrap appliance)
    // ---------------------------------------------------------------------

    private static void spawnerySlimeBallPrimerProducesVanillaFrogspawn(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SPAWNERY.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof SpawneryBlockEntity spawnery)) {
            helper.fail("expected SpawneryBlockEntity at " + absPos + ", got "
                + (be == null ? "null" : be.getClass().getSimpleName()));
            return;
        }
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.FUEL_SLOT, new ItemStack(Items.SLIME_BALL));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.PRIMER_SLOT, new ItemStack(Items.SLIME_BALL));

        driveSpawnery(level, absPos, spawnery);

        ItemStack output = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.OUTPUT_SLOT);
        if (!output.is(PFItems.FROG_EGG.get())) {
            helper.fail("expected a frog egg output, got "
                + (output.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(output.getItem())));
            return;
        }
        if (output.get(com.flatts.productivefrogs.registry.PFDataComponents.CONTAINED_CATEGORY.get()) != null) {
            helper.fail("a slime-ball primer must yield plain vanilla frogspawn (no contained_category)");
            return;
        }
        if (!spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.BOTTLE_SLOT).isEmpty()) {
            helper.fail("the glass bottle should have been consumed");
            return;
        }
        if (!spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.FUEL_SLOT).isEmpty()) {
            helper.fail("the fuel slime ball should have been consumed");
            return;
        }
        if (!spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.PRIMER_SLOT).isEmpty()) {
            helper.fail("the primer slime ball should have been consumed");
            return;
        }
        helper.succeed();
    }

    private static void spawneryIronPrimerProducesCaveEgg(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SPAWNERY.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof SpawneryBlockEntity spawnery)) {
            helper.fail("expected SpawneryBlockEntity at " + absPos);
            return;
        }
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.FUEL_SLOT, new ItemStack(Items.SLIME_BALL));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.PRIMER_SLOT, new ItemStack(Items.IRON_INGOT));

        driveSpawnery(level, absPos, spawnery);

        ItemStack output = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.OUTPUT_SLOT);
        if (!output.is(PFItems.FROG_EGG.get())) {
            helper.fail("expected a frog egg output, got "
                + (output.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(output.getItem())));
            return;
        }
        Category stamped = output.get(com.flatts.productivefrogs.registry.PFDataComponents.CONTAINED_CATEGORY.get());
        if (stamped != Category.CAVE) {
            helper.fail("iron ingot primer must stamp CAVE, got " + stamped);
            return;
        }
        if (!spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.PRIMER_SLOT).isEmpty()) {
            helper.fail("the iron ingot primer should have been consumed");
            return;
        }
        helper.succeed();
    }

    private static void spawneryWithoutFuelDoesNotProduce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SPAWNERY.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof SpawneryBlockEntity spawnery)) {
            helper.fail("expected SpawneryBlockEntity at " + absPos);
            return;
        }
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.PRIMER_SLOT, new ItemStack(Items.IRON_INGOT));

        driveSpawnery(level, absPos, spawnery);

        if (spawnery.getCookProgress() != 0) {
            helper.fail("cook progress must stay 0 without fuel, got " + spawnery.getCookProgress());
            return;
        }
        ItemStack output = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.OUTPUT_SLOT);
        if (!output.isEmpty()) {
            helper.fail("no egg should be produced without fuel, got "
                + BuiltInRegistries.ITEM.getKey(output.getItem()));
            return;
        }
        helper.succeed();
    }

    private static void spawneryWithoutBottleDoesNotConsumeFuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SPAWNERY.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof SpawneryBlockEntity spawnery)) {
            helper.fail("expected SpawneryBlockEntity at " + absPos);
            return;
        }
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.FUEL_SLOT, new ItemStack(Items.SLIME_BALL));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.PRIMER_SLOT, new ItemStack(Items.IRON_INGOT));

        driveSpawnery(level, absPos, spawnery);

        ItemStack fuel = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.FUEL_SLOT);
        if (fuel.isEmpty() || !fuel.is(Items.SLIME_BALL)) {
            helper.fail("the slime ball must NOT be consumed without a bottle to fill");
            return;
        }
        ItemStack output = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.OUTPUT_SLOT);
        if (!output.isEmpty()) {
            helper.fail("no egg should be produced without a bottle, got "
                + BuiltInRegistries.ITEM.getKey(output.getItem()));
            return;
        }
        helper.succeed();
    }

    private static void spawneryWithoutPrimerDoesNotProduce(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SPAWNERY.get());
        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof SpawneryBlockEntity spawnery)) {
            helper.fail("expected SpawneryBlockEntity at " + absPos);
            return;
        }
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.BOTTLE_SLOT, new ItemStack(Items.GLASS_BOTTLE));
        spawnery.getInventory().setStackInSlot(SpawneryBlockEntity.FUEL_SLOT, new ItemStack(Items.SLIME_BALL));

        driveSpawnery(level, absPos, spawnery);

        if (spawnery.getCookProgress() != 0) {
            helper.fail("cook progress must stay 0 with no primer, got " + spawnery.getCookProgress());
            return;
        }
        ItemStack fuel = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.FUEL_SLOT);
        if (fuel.isEmpty() || !fuel.is(Items.SLIME_BALL)) {
            helper.fail("fuel must NOT be consumed without a primer (it never ignites)");
            return;
        }
        ItemStack output = spawnery.getInventory().getStackInSlot(SpawneryBlockEntity.OUTPUT_SLOT);
        if (!output.isEmpty()) {
            helper.fail("no egg should be produced without a primer, got "
                + BuiltInRegistries.ITEM.getKey(output.getItem()));
            return;
        }
        helper.succeed();
    }

    /**
     * Drive the Spawnery's serverTick by hand for one full production cycle plus
     * a margin (mirrors the Slime Milker cook-loop test). serverTick advances
     * burn + cook by one per call; after the configured production ticks the cycle
     * completes once and then stalls on the stacksTo(1) output.
     */
    private static void driveSpawnery(ServerLevel level, BlockPos absPos, SpawneryBlockEntity spawnery) {
        int total = Math.max(1, com.flatts.productivefrogs.PFConfig.SPAWNERY_PRODUCTION_TICKS.get());
        for (int i = 0; i < total + 5; i++) {
            SpawneryBlockEntity.serverTick(level, absPos, level.getBlockState(absPos), spawnery);
        }
    }

    // ---------------------------------------------------------------------
    // Slime Milker hopper compat (item capability)
    // ---------------------------------------------------------------------

    // Ported to 26.1: the block item capability is Capabilities.Item.BLOCK over
    // ResourceHandler<ItemResource> (was Capabilities.ItemHandler.BLOCK /
    // IItemHandler on 1.21.1). getSlots()/getStackInSlot(int)/isItemValid(int,
    // ItemStack) map to size()/getResource(int)/isValid(int, ItemResource.of(...)).
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
        source.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        ItemStack primedBucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(primedBucket);
        source.discard();
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.INPUT_SLOT, primedBucket);
        ItemStack ironMilk = milkBucket("iron");
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT, ironMilk);

        // 26.1: Capabilities.Item.BLOCK returns ResourceHandler<ItemResource>.
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
            helper.fail("expected single-slot views, got downView.slots=" + downView.size()
                + ", upView.slots=" + upView.size());
            return;
        }
        // DOWN view sees the OUTPUT slot's iron milk bucket.
        if (!isMilkBucket(downView.getResource(0).toStack(1), "iron")) {
            helper.fail("down view should see OUTPUT slot's iron milk bucket, got "
                + downView.getResource(0));
            return;
        }
        // UP view sees the INPUT slot's primed Slime Bucket.
        if (upView.getResource(0).getItem() != PFItems.SLIME_BUCKET.get()) {
            helper.fail("up view should see INPUT slot's slime bucket, got "
                + upView.getResource(0));
            return;
        }
        // Predicate checks via isValid - the slot may be full (we pre-seeded
        // both INPUT and OUTPUT), so an insert would conflate "slot has no room"
        // with "slot rejects this item". isValid is the pure-predicate check.
        ItemStack probe = new ItemStack(PFItems.SLIME_BUCKET.get());
        if (downView.isValid(0, net.neoforged.neoforge.transfer.item.ItemResource.of(probe))) {
            helper.fail("down view must reject inserts (extract-only)");
            return;
        }
        if (!upView.isValid(0, net.neoforged.neoforge.transfer.item.ItemResource.of(probe))) {
            helper.fail("up view must accept SLIME_BUCKET inserts");
            return;
        }
        // UP view's underlying validator restricts to SLIME_BUCKET only.
        if (upView.isValid(0, net.neoforged.neoforge.transfer.item.ItemResource.of(new ItemStack(Items.IRON_INGOT)))) {
            helper.fail("up view must reject non-SLIME_BUCKET items");
            return;
        }
        helper.succeed();
    }

    private static void hopperAboveSlimeMilkerPushesSlimeBucketIntoInputSlot(GameTestHelper helper) {
        BlockPos milkerPos = new BlockPos(2, 2, 2);
        BlockPos hopperPos = new BlockPos(2, 3, 2);
        helper.setBlock(milkerPos, PFBlocks.SLIME_MILKER.get());
        // Default hopper state faces DOWN - the orientation we want, since
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
        source.setVariant(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
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

    private static void hopperBelowSlimeMilkerPullsMilkBucketFromOutputSlot(GameTestHelper helper) {
        BlockPos milkerPos = new BlockPos(2, 3, 2);
        BlockPos hopperPos = new BlockPos(2, 2, 2);
        helper.setBlock(milkerPos, PFBlocks.SLIME_MILKER.get());
        // Hopper below the milker; default-facing DOWN means it'll try to
        // push into air below it - fine, the relevant behavior is the pull
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
        ItemStack ironMilk = milkBucket("iron");
        milker.getInventory().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity.OUTPUT_SLOT, ironMilk);

        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.BlockEntity hopperBe = level.getBlockEntity(absHopper);
            if (!(hopperBe instanceof net.minecraft.world.level.block.entity.HopperBlockEntity hopper)) {
                helper.fail("hopper BE went missing at " + absHopper);
                return;
            }
            ItemStack pulled = hopper.getItem(0);
            if (pulled.isEmpty() || !isMilkBucket(pulled, "iron")) {
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

    // ---------------------------------------------------------------------
    // Shared helpers (the collapsed single-fluid Slime Milk model)
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
}
