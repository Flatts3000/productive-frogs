package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.BasinBlock;
import com.flatts.productivefrogs.content.block.entity.MobSlurryBasinBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkBasinBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlurryPressBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlurryPressInventory;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.item.EntityNetItem;
import com.flatts.productivefrogs.event.PredationTeleportHandler;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Predation Phase 3 pins (#281): the Ender Net whole-entity round-trip (the
 * #210 lesson), the Slurry Press conversion + boss rejection, both Basins'
 * spawn economy (dry AND waterlogged), the Basin teleport lock, the drain
 * round-trip, and the predation master switch.
 */
final class PredationSupplyChainTests {

    private static final Identifier SLIME_ID =
        Identifier.fromNamespaceAndPath("minecraft", "slime");

    private PredationSupplyChainTests() {
    }

    static void register() {
        PFGameTests.test("ender_net_capture_round_trip_conserves_entity", 40,
            PredationSupplyChainTests::enderNetCaptureRoundTripConservesEntity);
        PFGameTests.test("ender_net_and_frog_net_gate_their_own_targets", 40,
            PredationSupplyChainTests::enderNetAndFrogNetGateTheirOwnTargets);
        PFGameTests.test("slurry_press_presses_netted_mob_into_slurry_bucket", 160,
            PredationSupplyChainTests::slurryPressPressesNettedMobIntoSlurryBucket);
        PFGameTests.test("slurry_press_rejects_boss_nets", 60,
            PredationSupplyChainTests::slurryPressRejectsBossNets);
        PFGameTests.test("mob_slurry_basin_spawns_locked_mob_and_depletes", 80,
            PredationSupplyChainTests::mobSlurryBasinSpawnsLockedMobAndDepletes);
        PFGameTests.test("mob_slurry_basin_works_waterlogged", 80,
            PredationSupplyChainTests::mobSlurryBasinWorksWaterlogged);
        PFGameTests.test("slime_milk_basin_spawns_variant_slime", 80,
            PredationSupplyChainTests::slimeMilkBasinSpawnsVariantSlime);
        PFGameTests.test("basin_drain_returns_bucket_with_budget_intact", 40,
            PredationSupplyChainTests::basinDrainReturnsBucketWithBudgetIntact);
        PFGameTests.test("basin_consumes_dropped_catalyst", 80,
            PredationSupplyChainTests::basinConsumesDroppedCatalyst);
        PFGameTests.test("predators_disabled_idles_press_and_slurry_basin", 60,
            PredationSupplyChainTests::predatorsDisabledIdlesPressAndSlurryBasin);
    }

    /** A filled Ender Net around {@code typeId}, hand-built (no live entity needed). */
    private static ItemStack netWith(String typeId) {
        ItemStack net = new ItemStack(PFItems.ENDER_NET.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("entity", typeId);
        tag.putString("name", typeId);
        net.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return net;
    }

    /**
     * The whole-entity round-trip (#210): a named, hurt mob goes into the net
     * and comes back out with its type, name, and health intact.
     */
    private static void enderNetCaptureRoundTripConservesEntity(GameTestHelper helper) {
        Slime spider = helper.spawn(EntityType.SLIME, new BlockPos(2, 2, 2));
        spider.setSize(3, true); // size drives max health - set BEFORE health
        spider.setCustomName(Component.literal("Boris"));
        spider.setHealth(5.0F);

        ItemStack net = new ItemStack(PFItems.ENDER_NET.get());
        EntityNetItem.captureEntity(spider, net);
        spider.discard();
        if (!EntityNetItem.isFilled(net)) {
            helper.fail("capture must fill the net");
            return;
        }
        if (EntityNetItem.capturedType(net) != EntityType.SLIME) {
            helper.fail("capturedType must read slime, got " + EntityNetItem.capturedType(net));
            return;
        }
        Entity released = PFItems.ENDER_NET.get().entityFromStack(net, helper.getLevel());
        if (!(released instanceof Slime restored)) {
            helper.fail("release must rebuild a slime, got " + released);
            return;
        }
        if (!"Boris".equals(restored.getName().getString())) {
            helper.fail("custom name lost in the round trip: " + restored.getName().getString());
            return;
        }
        if (restored.getSize() != 3) {
            helper.fail("size lost in the round trip: " + restored.getSize());
            return;
        }
        if (restored.getHealth() != 5.0F) {
            helper.fail("health lost in the round trip: " + restored.getHealth());
            return;
        }
        restored.discard();
        helper.succeed();
    }

    /**
     * Both-layer net gating: the Ender Net catches any mob (and its release
     * defense-in-depth accepts one), while the Frog Net's release re-check
     * refuses a non-frog stuffed into its NBT.
     */
    private static void enderNetAndFrogNetGateTheirOwnTargets(GameTestHelper helper) {
        // A zombie stuffed into a FROG net must not release (tampered NBT).
        ItemStack frogNet = new ItemStack(PFItems.FROG_NET.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("entity", "minecraft:zombie");
        frogNet.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        Entity fromFrogNet = PFItems.FROG_NET.get().entityFromStack(frogNet, helper.getLevel());
        if (fromFrogNet != null) {
            helper.fail("the Frog Net must refuse to release a zombie");
            fromFrogNet.discard();
            return;
        }
        // The same zombie in an ENDER net releases fine.
        Entity fromEnderNet = PFItems.ENDER_NET.get().entityFromStack(netWith("minecraft:zombie"), helper.getLevel());
        if (fromEnderNet == null || fromEnderNet.getType() != EntityType.ZOMBIE) {
            helper.fail("the Ender Net must release a zombie, got " + fromEnderNet);
            return;
        }
        fromEnderNet.discard();
        helper.succeed();
    }

    /**
     * The Press conversion: filled net + empty bucket -> a SLURRIED_ENTITY-
     * stamped Mob Slurry bucket in one output and the EMPTIED Ender Net (the
     * net is a tool, never consumed) in the other, after the flat press cycle.
     */
    private static void slurryPressPressesNettedMobIntoSlurryBucket(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SLURRY_PRESS.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof SlurryPressBlockEntity press)) {
            helper.fail("no SlurryPressBlockEntity");
            return;
        }
        press.getInventory().setStackInSlot(SlurryPressInventory.NET_SLOT, netWith("minecraft:slime"));
        press.getInventory().setStackInSlot(SlurryPressInventory.BUCKET_SLOT, new ItemStack(Items.BUCKET));

        helper.succeedWhen(() -> {
            ItemStack slurry = press.getInventory().getStackInSlot(SlurryPressInventory.SLURRY_OUTPUT_SLOT);
            helper.assertTrue(!slurry.isEmpty(), "press has not produced yet");
            Identifier slurried = slurry.get(PFDataComponents.SLURRIED_ENTITY.get());
            helper.assertTrue(SLIME_ID.equals(slurried),
                "slurry bucket must carry minecraft:slime, got " + slurried);
            ItemStack returnedNet = press.getInventory().getStackInSlot(SlurryPressInventory.NET_OUTPUT_SLOT);
            helper.assertTrue(returnedNet.getItem() == PFItems.ENDER_NET.get()
                    && !EntityNetItem.isFilled(returnedNet),
                "the emptied Ender Net must come back in the second output");
            helper.assertTrue(press.getInventory().getStackInSlot(SlurryPressInventory.BUCKET_SLOT).isEmpty(),
                "the empty bucket must be consumed");
        });
    }

    /**
     * Boss rejection at BOTH layers: the slot filter refuses a boss net on
     * insert, and a force-set boss net (tampered) never advances the cycle.
     */
    private static void slurryPressRejectsBossNets(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SLURRY_PRESS.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof SlurryPressBlockEntity press)) {
            helper.fail("no SlurryPressBlockEntity");
            return;
        }
        ItemStack witherNet = netWith("minecraft:wither");
        // Layer 1: the slot filter refuses the insert outright.
        ItemStack refused = press.getInventory().insertItem(SlurryPressInventory.NET_SLOT, witherNet, false);
        if (refused.isEmpty()) {
            helper.fail("insertItem must refuse a boss (c:bosses) net");
            return;
        }
        // PF's own mobs are denylisted too (a Resource Slime pressed into
        // slurry would bypass the milk economy - maintainer ruling).
        ItemStack pfNet = netWith("productivefrogs:resource_slime");
        if (press.getInventory().insertItem(SlurryPressInventory.NET_SLOT, pfNet, false).isEmpty()) {
            helper.fail("insertItem must refuse a PF-mob (slurry_denylist) net");
            return;
        }
        // Layer 2: a force-set boss net (setStackInSlot bypasses the filter,
        // like tampered NBT would) stalls inert - no progress, nothing consumed.
        press.getInventory().setStackInSlot(SlurryPressInventory.NET_SLOT, witherNet);
        press.getInventory().setStackInSlot(SlurryPressInventory.BUCKET_SLOT, new ItemStack(Items.BUCKET));
        helper.runAfterDelay(30, () -> {
            if (press.getProgress() != 0) {
                helper.fail("a boss net must never advance the press cycle");
                return;
            }
            if (!press.getInventory().getStackInSlot(SlurryPressInventory.SLURRY_OUTPUT_SLOT).isEmpty()) {
                helper.fail("a boss net must never produce slurry");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The dry Basin loop: a charged Mob Slurry Basin spawns its mob into an
     * adjacent cell, the spawn carries the teleport lock (maintainer ruling:
     * Basin-spawned teleporters are locked by default; a freshly-spawned wild
     * mob is not), the budget decrements once per event, and the last spawn
     * empties the Basin (the block persists - it never drains to air).
     */
    private static void mobSlurryBasinSpawnsLockedMobAndDepletes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.MOB_SLURRY_BASIN.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof MobSlurryBasinBlockEntity basin)) {
            helper.fail("no MobSlurryBasinBlockEntity");
            return;
        }
        basin.charge(SLIME_ID, 1, 1, 0, 0, false);
        basin.forceReadyToFire();

        helper.succeedWhen(() -> {
            ServerLevel level = helper.getLevel();
            AABB box = new AABB(helper.absolutePos(pos)).inflate(2);
            var spawned = level.getEntitiesOfClass(Slime.class, box);
            if (spawned.isEmpty()) {
                basin.forceReadyToFire(); // re-arm: a transient pause must not strand the test
            }
            helper.assertTrue(!spawned.isEmpty(), "basin has not spawned yet");
            helper.assertTrue(PredationTeleportHandler.isTeleportDisabled(spawned.get(0)),
                "a Basin-spawned mob must carry the teleport lock");
            // Contrast: a wild mob does NOT carry the lock.
            Slime wild = EntityType.SLIME.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            helper.assertTrue(wild != null && !PredationTeleportHandler.isTeleportDisabled(wild),
                "a wild mob must not carry the teleport lock");
            if (wild != null) {
                wild.discard();
            }
            // remaining was 1: the spawn spent it and the Basin emptied, but the
            // BLOCK is still standing (persists for the next bucket).
            helper.assertTrue(!basin.isCharged(), "the depleted Basin must empty");
            helper.assertBlockPresent(PFBlocks.MOB_SLURRY_BASIN.get(), pos);
        });
    }

    /**
     * Waterlogged parity (maintainer ruling: works wet or dry): a waterlogged
     * Basin inside a pool spawns into the surrounding water, and the pool is
     * untouched (the held fluid never becomes a world fluid, so there is
     * nothing to mix or wash away).
     */
    private static void mobSlurryBasinWorksWaterlogged(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        // A small pool around the basin cell.
        for (BlockPos p : new BlockPos[] {pos.north(), pos.south(), pos.east(), pos.west()}) {
            helper.setBlock(p, Blocks.WATER);
        }
        helper.setBlock(pos, PFBlocks.MOB_SLURRY_BASIN.get().defaultBlockState()
            .setValue(BasinBlock.WATERLOGGED, true));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof MobSlurryBasinBlockEntity basin)) {
            helper.fail("no MobSlurryBasinBlockEntity");
            return;
        }
        basin.charge(SLIME_ID, 4, 4, 0, 0, false);
        basin.forceReadyToFire();

        helper.succeedWhen(() -> {
            AABB box = new AABB(helper.absolutePos(pos)).inflate(2);
            var spawned = helper.getLevel().getEntitiesOfClass(Slime.class, box);
            if (spawned.isEmpty()) {
                basin.forceReadyToFire(); // re-arm: a transient pause must not strand the test
            }
            helper.assertTrue(!spawned.isEmpty(), "waterlogged basin has not spawned yet");
            // The pool survives: neighbours still water, the basin still waterlogged.
            helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pos.north())).is(
                net.minecraft.world.level.material.Fluids.WATER), "the pool must be untouched");
            helper.assertTrue(helper.getBlockState(pos).getValue(BasinBlock.WATERLOGGED),
                "the basin must stay waterlogged");
        });
    }

    /**
     * The slime-side Basin: charged with a variant's milk it spawns that
     * variant's Resource Slime (through the shared createSlimeForVariant seam),
     */
    private static void slimeMilkBasinSpawnsVariantSlime(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.SLIME_MILK_BASIN.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof SlimeMilkBasinBlockEntity basin)) {
            helper.fail("no SlimeMilkBasinBlockEntity");
            return;
        }
        Identifier iron = Identifier.fromNamespaceAndPath("productivefrogs", "iron");
        basin.charge(iron, 4, 4, 0, 0, false);
        basin.forceReadyToFire();

        helper.succeedWhen(() -> {
            AABB box = new AABB(helper.absolutePos(pos)).inflate(2);
            var slimes = helper.getLevel().getEntitiesOfClass(ResourceSlime.class, box);
            if (slimes.isEmpty()) {
                basin.forceReadyToFire(); // re-arm: a transient pause must not strand the test
            }
            helper.assertTrue(!slimes.isEmpty(), "milk basin has not spawned yet");
            helper.assertTrue(iron.equals(slimes.get(0).getVariantId()),
                "spawned slime must carry the iron variant, got " + slimes.get(0).getVariantId());
        });
    }

    /** Drain round-trip: the empty-bucket drain hands back the key + the live budget. */
    private static void basinDrainReturnsBucketWithBudgetIntact(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.MOB_SLURRY_BASIN.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof MobSlurryBasinBlockEntity basin)) {
            helper.fail("no MobSlurryBasinBlockEntity");
            return;
        }
        basin.charge(SLIME_ID, 5, 16, 2, 1, false);
        ItemStack drained = basin.drainToBucket();
        if (drained.isEmpty() || !SLIME_ID.equals(drained.get(PFDataComponents.SLURRIED_ENTITY.get()))) {
            helper.fail("drain must mint a slime Slurry bucket");
            return;
        }
        if (!Integer.valueOf(5).equals(drained.get(PFDataComponents.SPAWNS_REMAINING.get()))
                || !Integer.valueOf(16).equals(drained.get(PFDataComponents.MILK_CAPACITY.get()))
                || !Integer.valueOf(2).equals(drained.get(PFDataComponents.MILK_SPEED.get()))
                || !Integer.valueOf(1).equals(drained.get(PFDataComponents.MILK_QUANTITY.get()))) {
            helper.fail("drain must carry the live budget + catalyst levels onto the bucket");
            return;
        }
        if (basin.isCharged()) {
            helper.fail("drain must empty the Basin");
            return;
        }
        helper.succeed();
    }

    /**
     * Catalyst parity (maintainer ruling: Basins allow catalysts): a catalyst
     * item DROPPED into the bowl is consumed and applied, exactly like dropping
     * one into a milk source pool - which is also what makes a dropper able to
     * feed catalysts in. (The right-click path shares the same consumption
     * core, so this pins both.)
     */
    private static void basinConsumesDroppedCatalyst(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.MOB_SLURRY_BASIN.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof MobSlurryBasinBlockEntity basin)) {
            helper.fail("no MobSlurryBasinBlockEntity");
            return;
        }
        basin.charge(SLIME_ID, 8, 8, 0, 0, false);

        // Drop a Speed catalyst into the bowl (spawned just above, falls in).
        BlockPos abs = helper.absolutePos(pos);
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
            helper.getLevel(), abs.getX() + 0.5, abs.getY() + 0.9, abs.getZ() + 0.5,
            new ItemStack(com.flatts.productivefrogs.registry.PFItems.SPEED_CATALYST.get()));
        drop.setDeltaMovement(0, 0, 0);
        helper.getLevel().addFreshEntity(drop);

        helper.succeedWhen(() -> {
            helper.assertTrue(basin.getSpeedLevel() == 1,
                "dropped Speed catalyst must apply (speed=" + basin.getSpeedLevel() + ")");
            helper.assertTrue(drop.isRemoved() || drop.getItem().isEmpty(),
                "the consumed catalyst item must be gone");
        });
    }

    /**
     * {@code predators.enabled=false}: the Press never advances and the Mob
     * Slurry Basin never spawns (charge held, nothing lost - the flip-back
     * resumes both). The Slime Milk Basin is deliberately NOT gated (slime-side
     * machinery; only its recipe is predation content).
     */
    private static void predatorsDisabledIdlesPressAndSlurryBasin(GameTestHelper helper) {
        // Drive the ticks MANUALLY inside one synchronous body: the override is
        // global, and GameTests batch concurrently - holding it across real
        // ticks would idle the other predation tests' blocks too (that exact
        // interference failed the first run of this suite).
        BlockPos pressPos = new BlockPos(1, 2, 2);
        BlockPos basinPos = new BlockPos(3, 2, 2);
        helper.setBlock(pressPos, PFBlocks.SLURRY_PRESS.get());
        helper.setBlock(basinPos, PFBlocks.MOB_SLURRY_BASIN.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pressPos))
                instanceof SlurryPressBlockEntity press)
                || !(helper.getLevel().getBlockEntity(helper.absolutePos(basinPos))
                instanceof MobSlurryBasinBlockEntity basin)) {
            helper.fail("missing press/basin BEs");
            return;
        }
        press.getInventory().setStackInSlot(SlurryPressInventory.NET_SLOT, netWith("minecraft:slime"));
        press.getInventory().setStackInSlot(SlurryPressInventory.BUCKET_SLOT, new ItemStack(Items.BUCKET));
        basin.charge(SLIME_ID, 4, 4, 0, 0, false);
        basin.forceReadyToFire();

        PFConfig.predatorsEnabledOverride = Boolean.FALSE;
        try {
            ServerLevel level = helper.getLevel();
            for (int i = 0; i < 10; i++) {
                SlurryPressBlockEntity.serverTick(level, helper.absolutePos(pressPos),
                    level.getBlockState(helper.absolutePos(pressPos)), press);
                MobSlurryBasinBlockEntity.serverTick(level, helper.absolutePos(basinPos),
                    level.getBlockState(helper.absolutePos(basinPos)), basin);
            }
            if (press.getProgress() != 0) {
                helper.fail("disabled predation must idle the press");
                return;
            }
            AABB box = new AABB(helper.absolutePos(basinPos)).inflate(2);
            if (!helper.getLevel().getEntitiesOfClass(Slime.class, box).isEmpty()) {
                helper.fail("disabled predation must idle the slurry basin");
                return;
            }
            if (!basin.isCharged()) {
                helper.fail("the idled basin must keep its charge (nothing lost)");
                return;
            }
        } finally {
            PFConfig.predatorsEnabledOverride = null;
        }
        // Empty the basin before the world ticker resumes with the override
        // cleared, so this plot doesn't spill slimes into neighbouring tests.
        basin.drainToBucket();
        press.getInventory().setStackInSlot(SlurryPressInventory.NET_SLOT, ItemStack.EMPTY);
        helper.succeed();
    }
}
