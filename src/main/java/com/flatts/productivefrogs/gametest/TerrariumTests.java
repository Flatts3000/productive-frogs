package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * In-world GameTests for the Terrarium multiblock (#185). Migrated from the
 * monolithic {@code PFGameTests} into a focused holder for the MC 26.1 line;
 * registration is now via {@link PFGameTests#test} rather than {@code @GameTest}
 * annotations.
 *
 * <p>Every test in this class is built on the 9x9x9 plot ({@code empty_9x9x9})
 * since a Terrarium needs a 5x4x5 cavity inside a stone shell.
 */
final class TerrariumTests {

    private TerrariumTests() {
    }

    static void register() {
        PFGameTests.test("terrarium_forms_with_5x4x5_cavity", "empty_9x9x9", 100, TerrariumTests::terrariumFormsWith5x4x5Cavity);
        PFGameTests.test("terrarium_rejects_machine_on_edge", "empty_9x9x9", 100, TerrariumTests::terrariumRejectsMachineOnEdge);
        PFGameTests.test("terrarium_rejects_sprinkler_off_ceiling", "empty_9x9x9", 100, TerrariumTests::terrariumRejectsSprinklerOffCeiling);
        PFGameTests.test("terrarium_shell_break_deregisters", "empty_9x9x9", 100, TerrariumTests::terrariumShellBreakDeregisters);
        PFGameTests.test("terrarium_controller_rejects_second_variant_until_empty", "empty_9x9x9", 100, TerrariumTests::terrariumControllerRejectsSecondVariantUntilEmpty);
        PFGameTests.test("terrarium_charge_preserves_catalysts", "empty_9x9x9", 100, TerrariumTests::terrariumChargePreservesCatalysts);
        PFGameTests.test("terrarium_sprinkler_spawns_into_cavity", "empty_9x9x9", 100, TerrariumTests::terrariumSprinklerSpawnsIntoCavity);
        PFGameTests.test("terrarium_cavity_cap_pauses_sprinkler", "empty_9x9x9", 100, TerrariumTests::terrariumCavityCapPausesSprinkler);
        PFGameTests.test("terrarium_redstone_pauses_sprinkler", "empty_9x9x9", 100, TerrariumTests::terrariumRedstonePausesSprinkler);
        PFGameTests.test("terrarium_redstone_does_not_bleed_to_adjacent_sprinkler", "empty_9x9x9", 100, TerrariumTests::terrariumRedstoneDoesNotBleedToAdjacentSprinkler);
        PFGameTests.test("terrarium_hatch_receives_froglight_directly", "empty_9x9x9", 100, TerrariumTests::terrariumHatchReceivesFroglightDirectly);
        PFGameTests.test("terrarium_hatch_vacuums_raw_frog_legs", "empty_9x9x9", 100, TerrariumTests::terrariumHatchVacuumsRawFrogLegs);
        PFGameTests.test("terrarium_full_hatch_stops_drop", "empty_9x9x9", 100, TerrariumTests::terrariumFullHatchStopsDrop);
        PFGameTests.test("terrarium_accepts_mud_floor", "empty_9x9x9", 100, TerrariumTests::terrariumAcceptsMudFloor);
        PFGameTests.test("terrarium_incubator_preserves_stats", "empty_9x9x9", 100, TerrariumTests::terrariumIncubatorPreservesStats);
        PFGameTests.test("terrarium_frog_cap_holds_in_incubator", "empty_9x9x9", 100, TerrariumTests::terrariumFrogCapHoldsInIncubator);
        PFGameTests.test("terrarium_hatch_vacuums_cavity_items", "empty_9x9x9", 100, TerrariumTests::terrariumHatchVacuumsCavityItems);
        PFGameTests.test("terrarium_forms_without_incubator", "empty_9x9x9", 100, TerrariumTests::terrariumFormsWithoutIncubator);
    }

    /**
     * A complete, valid Terrarium forms: the Controller validates, registers in
     * {@link com.flatts.productivefrogs.content.multiblock.TerrariumManager}, and the
     * registry knows the cavity + Hatch.
     */
    private static void terrariumFormsWith5x4x5Cavity(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 2);
        ServerLevel level = helper.getLevel();
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(controller))
                instanceof com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity be)) {
            helper.fail("no Terrarium Controller block entity at " + controller);
            return;
        }
        com.flatts.productivefrogs.content.multiblock.TerrariumValidationResult result =
            be.forceValidate(level, helper.absolutePos(controller));
        if (!result.formed()) {
            helper.fail("expected formed, got problem: "
                + (result.firstProblem() == null ? "(none)" : result.firstProblem().messageKey()));
            return;
        }
        net.minecraft.world.phys.Vec3 center =
            net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 4, 4)));
        com.flatts.productivefrogs.content.multiblock.TerrariumManager.FormedTerrarium formed =
            com.flatts.productivefrogs.content.multiblock.TerrariumManager.containing(level, center);
        if (formed == null) {
            helper.fail("formed Terrarium not registered in TerrariumManager");
            return;
        }
        if (!formed.hatchPos().equals(helper.absolutePos(new BlockPos(7, 4, 4)))) {
            helper.fail("registered hatchPos mismatch: " + formed.hatchPos());
            return;
        }
        helper.succeed();
    }

    /** A machine on a shell edge cell fails with {@code machine_on_edge}. */
    private static void terrariumRejectsMachineOnEdge(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 0);
        // (1,1,4): x and y both extreme -> a shell EDGE; a machine here is illegal.
        helper.setBlock(new BlockPos(1, 1, 4),
            PFBlocks.INCUBATOR.get().defaultBlockState().setValue(
                com.flatts.productivefrogs.content.block.IncubatorBlock.FACING, net.minecraft.core.Direction.WEST));
        assertTerrariumProblem(helper, controller, "machine_on_edge");
    }

    /** A Sprinkler in a wall (non-ceiling) face cell fails with {@code sprinkler_off_ceiling}. */
    private static void terrariumRejectsSprinklerOffCeiling(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 0);
        // (1,2,4): west-wall face cell, not the ceiling (y != 6).
        helper.setBlock(new BlockPos(1, 2, 4),
            PFBlocks.SPRINKLER.get().defaultBlockState().setValue(
                com.flatts.productivefrogs.content.block.SprinklerBlock.FACING, net.minecraft.core.Direction.DOWN));
        assertTerrariumProblem(helper, controller, "sprinkler_off_ceiling");
    }

    /** Breaking a shell cell deregisters a formed Terrarium; repair would re-form it. */
    private static void terrariumShellBreakDeregisters(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 0);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(controller);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(controller))
                instanceof com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity be)) {
            helper.fail("no Terrarium Controller block entity at " + controller);
            return;
        }
        if (!be.forceValidate(level, abs).formed()) {
            helper.fail("expected the built Terrarium to form initially");
            return;
        }
        net.minecraft.world.phys.Vec3 center =
            net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(new BlockPos(4, 4, 4)));
        if (com.flatts.productivefrogs.content.multiblock.TerrariumManager.containing(level, center) == null) {
            helper.fail("formed Terrarium not registered before the shell break");
            return;
        }
        // Punch a hole in the floor (a plain solid shell cell).
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.AIR);
        if (be.forceValidate(level, abs).formed()) {
            helper.fail("expected not formed after the shell break");
            return;
        }
        if (com.flatts.productivefrogs.content.multiblock.TerrariumManager.containing(level, center) != null) {
            helper.fail("Terrarium still registered after the shell break");
            return;
        }
        helper.succeed();
    }

    /** The Controller buffer holds one variant at a time: a second is refused until it drains. */
    private static void terrariumControllerRejectsSecondVariantUntilEmpty(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 1);
        ServerLevel level = helper.getLevel();
        BlockState state = level.getBlockState(helper.absolutePos(controller));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(controller))
                instanceof com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity be)) {
            helper.fail("no Controller BE");
            return;
        }
        be.forceValidate(level, helper.absolutePos(controller));
        ItemStack iron = PFItems.slimeMilkBucket(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        ItemStack copper = PFItems.slimeMilkBucket(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper"));
        if (iron.isEmpty() || copper.isEmpty()) {
            helper.fail("iron/copper milk buckets should exist");
            return;
        }
        if (!be.pushChargeFromBucket(iron)) {
            helper.fail("first (iron) charge should be accepted");
            return;
        }
        if (be.pushChargeFromBucket(copper)) {
            helper.fail("copper must be refused while the buffer holds iron");
            return;
        }
        // Drain the buffer into the Sprinkler, then copper is accepted.
        com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity.serverTick(
            level, helper.absolutePos(controller), state, be);
        if (be.bufferedCharges() != 0 || be.tankVariant() != null) {
            helper.fail("buffer should be empty after distributing the iron charge");
            return;
        }
        if (!be.pushChargeFromBucket(copper)) {
            helper.fail("copper should be accepted once the buffer drained");
            return;
        }
        helper.succeed();
    }

    /** A charge built from a catalyzed bucket stamps the Sprinkler with the same stats. */
    private static void terrariumChargePreservesCatalysts(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 1);
        ServerLevel level = helper.getLevel();
        BlockState state = level.getBlockState(helper.absolutePos(controller));
        com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity be =
            (com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(controller));
        be.forceValidate(level, helper.absolutePos(controller));
        ItemStack iron = PFItems.slimeMilkBucket(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        iron.set(com.flatts.productivefrogs.registry.PFDataComponents.MILK_SPEED.get(), 2);
        iron.set(com.flatts.productivefrogs.registry.PFDataComponents.MILK_INFINITE.get(), true);
        be.pushChargeFromBucket(iron);
        com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity.serverTick(
            level, helper.absolutePos(controller), state, be);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(2, 6, 2)))
                instanceof com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity sprinkler)) {
            helper.fail("no Sprinkler BE at the ceiling cell");
            return;
        }
        if (!sprinkler.isInfinite() || sprinkler.getSpeedLevel() != 2) {
            helper.fail("Sprinkler did not inherit the charge's catalysts: infinite="
                + sprinkler.isInfinite() + " speed=" + sprinkler.getSpeedLevel());
            return;
        }
        helper.succeed();
    }

    /** A filled Sprinkler spawns its variant's slime into the cavity below and spends one budget. */
    private static void terrariumSprinklerSpawnsIntoCavity(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 1);
        ServerLevel level = helper.getLevel();
        ((com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity)
            helper.getLevel().getBlockEntity(helper.absolutePos(controller))).forceValidate(level, helper.absolutePos(controller));
        BlockPos sprinklerRel = new BlockPos(2, 6, 2);
        com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity sprinkler =
            (com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(sprinklerRel));
        Identifier ironId = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = Boolean.TRUE;
        com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.cavitySlimeCapOverride = 64;
        try {
            sprinkler.loadCharge(ironId, new com.flatts.productivefrogs.content.multiblock.MilkCharge(8, 8, 0, 0, false));
            sprinkler.primeForImmediateSpawn();
            BlockState sState = level.getBlockState(helper.absolutePos(sprinklerRel));
            com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.serverTick(
                level, helper.absolutePos(sprinklerRel), sState, sprinkler);
            java.util.List<ResourceSlime> slimes = level.getEntitiesOfClass(ResourceSlime.class,
                net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
                    helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(6, 6, 6))));
            if (slimes.isEmpty()) {
                helper.fail("Sprinkler did not spawn a slime into the cavity");
                return;
            }
            if (sprinkler.getSpawnsRemaining() != 7) {
                helper.fail("expected remaining 7 after one spawn, got " + sprinkler.getSpawnsRemaining());
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = null;
            com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.cavitySlimeCapOverride = null;
        }
    }

    /** At the cavity slime cap, a Sprinkler pauses without spawning or spending budget. */
    private static void terrariumCavityCapPausesSprinkler(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 1);
        ServerLevel level = helper.getLevel();
        ((com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity)
            helper.getLevel().getBlockEntity(helper.absolutePos(controller))).forceValidate(level, helper.absolutePos(controller));
        BlockPos sprinklerRel = new BlockPos(2, 6, 2);
        com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity sprinkler =
            (com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(sprinklerRel));
        Identifier ironId = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = Boolean.TRUE;
        com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.cavitySlimeCapOverride = 1;
        try {
            // Seed the cavity at the cap (1), then a spawn attempt must pause.
            ResourceSlime existing = helper.spawn(PFEntities.RESOURCE_SLIME.get(), new BlockPos(4, 3, 4));
            existing.setVariant(ironId);
            sprinkler.loadCharge(ironId, new com.flatts.productivefrogs.content.multiblock.MilkCharge(8, 8, 0, 0, false));
            sprinkler.primeForImmediateSpawn();
            BlockState sState = level.getBlockState(helper.absolutePos(sprinklerRel));
            com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.serverTick(
                level, helper.absolutePos(sprinklerRel), sState, sprinkler);
            if (sprinkler.getSpawnsRemaining() != 8) {
                helper.fail("capped Sprinkler must not spend budget; remaining=" + sprinkler.getSpawnsRemaining());
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = null;
            com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.cavitySlimeCapOverride = null;
        }
    }

    /** A redstone-powered Sprinkler pauses spawning (hopper convention) without spending budget. */
    private static void terrariumRedstonePausesSprinkler(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 1);
        ServerLevel level = helper.getLevel();
        ((com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity)
            helper.getLevel().getBlockEntity(helper.absolutePos(controller))).forceValidate(level, helper.absolutePos(controller));
        BlockPos sprinklerRel = new BlockPos(2, 6, 2);
        com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity sprinkler =
            (com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(sprinklerRel));
        Identifier ironId = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = Boolean.TRUE;
        com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.cavitySlimeCapOverride = 64;
        try {
            // Power the Sprinkler from above the roof (outside the shell, so the
            // structure stays formed and the cavity spawn target stays clear).
            helper.setBlock(new BlockPos(2, 7, 2), Blocks.REDSTONE_BLOCK);
            sprinkler.loadCharge(ironId, new com.flatts.productivefrogs.content.multiblock.MilkCharge(8, 8, 0, 0, false));
            sprinkler.primeForImmediateSpawn();
            BlockState sState = level.getBlockState(helper.absolutePos(sprinklerRel));
            com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.serverTick(
                level, helper.absolutePos(sprinklerRel), sState, sprinkler);
            if (sprinkler.getSpawnsRemaining() != 8) {
                helper.fail("powered Sprinkler must not spend budget; remaining=" + sprinkler.getSpawnsRemaining());
                return;
            }
            java.util.List<ResourceSlime> slimes = level.getEntitiesOfClass(ResourceSlime.class,
                net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
                    helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(6, 6, 6))));
            if (!slimes.isEmpty()) {
                helper.fail("powered Sprinkler spawned " + slimes.size() + " slime(s); it should be paused");
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = null;
            com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.cavitySlimeCapOverride = null;
        }
    }

    /**
     * Redstone on one Sprinkler must NOT pause the Sprinkler next to it (#264 follow-up):
     * a lever attached to a Sprinkler strong-powers it, and a full-cube conductor would
     * re-emit that to its face neighbours, pausing a whole adjacent cluster off one lever.
     * The Sprinkler is registered as a non-redstone-conductor so the signal stays local.
     * Two adjacent Sprinklers: a powered lever sits on top of A only; A pauses, B keeps spawning.
     */
    private static void terrariumRedstoneDoesNotBleedToAdjacentSprinkler(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 2);
        ServerLevel level = helper.getLevel();
        ((com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity)
            helper.getLevel().getBlockEntity(helper.absolutePos(controller))).forceValidate(level, helper.absolutePos(controller));
        // buildValidTerrarium lays Sprinklers in a contiguous row: A=(2,6,2), B=(2,6,3) share a face.
        BlockPos aRel = new BlockPos(2, 6, 2);
        BlockPos bRel = new BlockPos(2, 6, 3);
        com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity a =
            (com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(aRel));
        com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity b =
            (com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(bRel));
        Identifier ironId = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = Boolean.TRUE;
        com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.cavitySlimeCapOverride = 64;
        try {
            // A FLOOR lever standing on top of A strong-powers A (direction UP). It is
            // diagonal to B, so the only path to B is conductor re-emission through A -
            // exactly the bleed the non-conductor fix removes.
            helper.setBlock(new BlockPos(2, 7, 2), Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,
                    net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, Boolean.TRUE));
            for (com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity s : java.util.List.of(a, b)) {
                s.loadCharge(ironId, new com.flatts.productivefrogs.content.multiblock.MilkCharge(8, 8, 0, 0, false));
                s.primeForImmediateSpawn();
            }
            com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.serverTick(
                level, helper.absolutePos(aRel), level.getBlockState(helper.absolutePos(aRel)), a);
            com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.serverTick(
                level, helper.absolutePos(bRel), level.getBlockState(helper.absolutePos(bRel)), b);
            if (a.getSpawnsRemaining() != 8) {
                helper.fail("Sprinkler A (powered by the lever) must be paused; remaining=" + a.getSpawnsRemaining());
                return;
            }
            if (b.getSpawnsRemaining() == 8) {
                helper.fail("Sprinkler B (adjacent, unpowered) was paused by A's redstone - power bled across the cluster");
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.depletionEnabledOverride = null;
            com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity.cavitySlimeCapOverride = null;
        }
    }

    /** Inside a formed Terrarium the frog-eat drop lands in the Hatch with no item entity. */
    private static void terrariumHatchReceivesFroglightDirectly(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 0);
        ServerLevel level = helper.getLevel();
        ((com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity)
            helper.getLevel().getBlockEntity(helper.absolutePos(controller))).forceValidate(level, helper.absolutePos(controller));
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(4, 4, 4));
        frog.setCategory(Category.CAVE);
        com.flatts.productivefrogs.event.FrogTongueDropHandler.dropFroglightAtFrog(
            frog, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, 4, 4)))
                instanceof com.flatts.productivefrogs.content.block.entity.HatchBlockEntity hatch) || hatch.isEmpty()) {
            helper.fail("Hatch did not receive the froglight");
            return;
        }
        java.util.List<net.minecraft.world.entity.item.ItemEntity> items = level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
                helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(6, 6, 6))));
        if (!items.isEmpty()) {
            helper.fail("a froglight item entity spawned inside the Terrarium (should be entity-free)");
            return;
        }
        helper.succeed();
    }

    /** The Hatch auto-collects loose Raw Frog Legs from the cavity (#194 drop + hatch_collectible tag). */
    private static void terrariumHatchVacuumsRawFrogLegs(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 0);
        ServerLevel level = helper.getLevel();
        ((com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity)
            helper.getLevel().getBlockEntity(helper.absolutePos(controller))).forceValidate(level, helper.absolutePos(controller));
        // Drop raw frog legs loose in the cavity; the Hatch's vacuum should pull them in.
        BlockPos dropAbs = helper.absolutePos(new BlockPos(4, 3, 4));
        net.minecraft.world.entity.item.ItemEntity legs = new net.minecraft.world.entity.item.ItemEntity(
            level, dropAbs.getX() + 0.5, dropAbs.getY(), dropAbs.getZ() + 0.5,
            new net.minecraft.world.item.ItemStack(PFItems.RAW_FROG_LEGS.get()));
        level.addFreshEntity(legs);
        helper.succeedWhen(() -> {
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, 4, 4)))
                    instanceof com.flatts.productivefrogs.content.block.entity.HatchBlockEntity hatch)
                    || hatch.isEmpty()) {
                helper.fail("Hatch should vacuum the raw frog legs from the cavity");
            }
        });
    }

    /** A full Hatch is backpressure: the drop deposits nothing and spills no item entity. */
    private static void terrariumFullHatchStopsDrop(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 0);
        ServerLevel level = helper.getLevel();
        ((com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity)
            helper.getLevel().getBlockEntity(helper.absolutePos(controller))).forceValidate(level, helper.absolutePos(controller));
        com.flatts.productivefrogs.content.block.entity.HatchBlockEntity hatch =
            (com.flatts.productivefrogs.content.block.entity.HatchBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, 4, 4)));
        hatch.fillForTest();
        if (!hatch.isFull()) {
            helper.fail("Hatch should be full after fillForTest");
            return;
        }
        ResourceFrog frog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(4, 4, 4));
        frog.setCategory(Category.CAVE);
        com.flatts.productivefrogs.event.FrogTongueDropHandler.dropFroglightAtFrog(
            frog, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        java.util.List<net.minecraft.world.entity.item.ItemEntity> items = level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class,
            net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
                helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(6, 6, 6))));
        if (!items.isEmpty()) {
            helper.fail("a full Hatch must not spill froglights into the world");
            return;
        }
        helper.succeed();
    }

    /** A mud floor (not a full-cube solid) still seals the shell via the terrarium_shell tag. */
    private static void terrariumAcceptsMudFloor(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 0);
        // Swap the whole shell floor (y=1 layer) to mud - mud is not a full-cube
        // solid, but the terrarium_shell tag whitelists it.
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.MUD);
            }
        }
        ServerLevel level = helper.getLevel();
        com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity be =
            (com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(controller));
        if (!be.forceValidate(level, helper.absolutePos(controller)).formed()) {
            helper.fail("a mud floor should still form the Terrarium (terrarium_shell tag)");
            return;
        }
        helper.succeed();
    }

    /** An Incubator releases a frog into the cavity with bred stats preserved post-finalizeSpawn. */
    private static void terrariumIncubatorPreservesStats(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 0);
        ServerLevel level = helper.getLevel();
        ((com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity)
            helper.getLevel().getBlockEntity(helper.absolutePos(controller))).forceValidate(level, helper.absolutePos(controller));
        BlockPos incRel = new BlockPos(4, 4, 1);
        com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity inc =
            (com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(incRel));
        com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity.frogCapOverride = 64;
        try {
            inc.seedFromBreeding(com.flatts.productivefrogs.data.FrogKind.resource(Category.CAVE), 5, 7, 3);
            inc.primeForImmediateRelease();
            com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity.serverTick(
                level, helper.absolutePos(incRel), level.getBlockState(helper.absolutePos(incRel)), inc);
            java.util.List<ResourceFrog> frogs = level.getEntitiesOfClass(ResourceFrog.class,
                net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
                    helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(6, 6, 6))));
            if (frogs.isEmpty()) {
                helper.fail("Incubator released no frog");
                return;
            }
            ResourceFrog f = frogs.get(0);
            if (f.getAppetite() != 5 || f.getBounty() != 7 || f.getReach() != 3) {
                helper.fail("stats not preserved: appetite=" + f.getAppetite()
                    + " bounty=" + f.getBounty() + " reach=" + f.getReach());
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity.frogCapOverride = null;
        }
    }

    /** At the frog cap an Incubator holds the matured frog; it releases once space frees. */
    private static void terrariumFrogCapHoldsInIncubator(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 0);
        ServerLevel level = helper.getLevel();
        ((com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity)
            helper.getLevel().getBlockEntity(helper.absolutePos(controller))).forceValidate(level, helper.absolutePos(controller));
        BlockPos incRel = new BlockPos(4, 4, 1);
        com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity inc =
            (com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(incRel));
        BlockState iState = level.getBlockState(helper.absolutePos(incRel));
        net.minecraft.world.phys.AABB cavity = net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
            helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(6, 6, 6)));
        try {
            inc.seedFromBreeding(com.flatts.productivefrogs.data.FrogKind.resource(Category.CAVE), 1, 1, 1);
            inc.primeForImmediateRelease();
            // Cap 0 -> hold (no release, still incubating).
            com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity.frogCapOverride = 0;
            com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity.serverTick(level, helper.absolutePos(incRel), iState, inc);
            if (!level.getEntitiesOfClass(ResourceFrog.class, cavity).isEmpty()) {
                helper.fail("frog released at the cap (should hold)");
                return;
            }
            if (inc.hasRoom()) {
                helper.fail("held Incubator should still be incubating");
                return;
            }
            // Raise the cap -> releases.
            com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity.frogCapOverride = 8;
            com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity.serverTick(level, helper.absolutePos(incRel), iState, inc);
            if (level.getEntitiesOfClass(ResourceFrog.class, cavity).isEmpty()) {
                helper.fail("Incubator did not release once the cap rose");
                return;
            }
            helper.succeed();
        } finally {
            com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity.frogCapOverride = null;
        }
    }

    /** The Hatch auto-collects loose collectible items (a slimeball) from the cavity. */
    private static void terrariumHatchVacuumsCavityItems(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 0);
        ServerLevel level = helper.getLevel();
        ((com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity)
            helper.getLevel().getBlockEntity(helper.absolutePos(controller))).forceValidate(level, helper.absolutePos(controller));
        BlockPos hatchRel = new BlockPos(7, 4, 4);
        com.flatts.productivefrogs.content.block.entity.HatchBlockEntity hatch =
            (com.flatts.productivefrogs.content.block.entity.HatchBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(hatchRel));
        // Drop a slimeball item entity inside the cavity.
        BlockPos dropAbs = helper.absolutePos(new BlockPos(4, 3, 4));
        net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
            level, dropAbs.getX() + 0.5, dropAbs.getY() + 0.5, dropAbs.getZ() + 0.5,
            new ItemStack(net.minecraft.world.item.Items.SLIME_BALL));
        level.addFreshEntity(item);
        BlockPos hatchAbs = helper.absolutePos(hatchRel);
        BlockState hatchState = level.getBlockState(hatchAbs);
        // Tick past the vacuum cadence so it fires.
        for (int i = 0; i < 8; i++) {
            com.flatts.productivefrogs.content.block.entity.HatchBlockEntity.serverTick(
                level, hatchAbs, hatchState, hatch);
        }
        if (hatch.isEmpty()) {
            helper.fail("Hatch did not vacuum the slimeball from the cavity");
            return;
        }
        if (item.isAlive() && !item.getItem().isEmpty()) {
            helper.fail("the slimeball item entity should be consumed by the Hatch");
            return;
        }
        helper.succeed();
    }

    /**
     * Incubators are optional (maintainer ruling): a sealed box with a Controller,
     * a Hatch, and no Incubator still forms. Build the valid shell, replace the
     * incubator face cell with plain shell, and assert it forms.
     */
    private static void terrariumFormsWithoutIncubator(GameTestHelper helper) {
        BlockPos controller = buildValidTerrarium(helper, 1);
        // Swap the incubator (placed at 4,4,1 by the helper) for a plain solid
        // shell cell, so the box has zero incubators.
        helper.setBlock(new BlockPos(4, 4, 1), Blocks.STONE);
        ServerLevel level = helper.getLevel();
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(controller))
                instanceof com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity be)) {
            helper.fail("no Terrarium Controller block entity at " + controller);
            return;
        }
        if (!be.forceValidate(level, helper.absolutePos(controller)).formed()) {
            helper.fail("a Terrarium with zero incubators should still form");
            return;
        }
        helper.succeed();
    }

    /**
     * Build a valid Terrarium inside the 9x9x9 plot: a stone shell over rel
     * {@code x/z (1..7)} and {@code y (1..6)} (5x4x5 air cavity at rel
     * {@code x/z (2..6)}, {@code y (2..5)}), a Controller / Hatch / Incubator on
     * opposing wall-face centers (each facing inward, at y=4), and
     * {@code sprinklerCount} Sprinklers in the ceiling (y=6). Returns the
     * Controller's relative position.
     */
    private static BlockPos buildValidTerrarium(GameTestHelper helper, int sprinklerCount) {
        for (int x = 1; x <= 7; x++) {
            for (int y = 1; y <= 6; y++) {
                for (int z = 1; z <= 7; z++) {
                    boolean shell = x == 1 || x == 7 || y == 1 || y == 6 || z == 1 || z == 7;
                    helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        // FACING points outward (front-to-player); the opposite face abuts the cavity.
        helper.setBlock(new BlockPos(1, 4, 4),
            PFBlocks.TERRARIUM_CONTROLLER.get().defaultBlockState().setValue(
                com.flatts.productivefrogs.content.block.TerrariumControllerBlock.FACING, net.minecraft.core.Direction.WEST));
        helper.setBlock(new BlockPos(7, 4, 4),
            PFBlocks.HATCH.get().defaultBlockState().setValue(
                com.flatts.productivefrogs.content.block.HatchBlock.FACING, net.minecraft.core.Direction.EAST));
        helper.setBlock(new BlockPos(4, 4, 1),
            PFBlocks.INCUBATOR.get().defaultBlockState().setValue(
                com.flatts.productivefrogs.content.block.IncubatorBlock.FACING, net.minecraft.core.Direction.NORTH));
        int placed = 0;
        for (int x = 2; x <= 6 && placed < sprinklerCount; x++) {
            for (int z = 2; z <= 6 && placed < sprinklerCount; z++) {
                helper.setBlock(new BlockPos(x, 6, z),
                    PFBlocks.SPRINKLER.get().defaultBlockState().setValue(
                        com.flatts.productivefrogs.content.block.SprinklerBlock.FACING, net.minecraft.core.Direction.DOWN));
                placed++;
            }
        }
        return new BlockPos(1, 4, 4);
    }

    /** Force a Controller validate and assert it failed with the expected message key. */
    private static void assertTerrariumProblem(GameTestHelper helper, BlockPos controllerRel, String expectedKey) {
        ServerLevel level = helper.getLevel();
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(controllerRel))
                instanceof com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity be)) {
            helper.fail("no Terrarium Controller block entity at " + controllerRel);
            return;
        }
        com.flatts.productivefrogs.content.multiblock.TerrariumValidationResult result =
            be.forceValidate(level, helper.absolutePos(controllerRel));
        if (result.formed()) {
            helper.fail("expected failure '" + expectedKey + "' but the Terrarium formed");
            return;
        }
        String key = result.firstProblem() == null ? "(none)" : result.firstProblem().messageKey();
        if (!expectedKey.equals(key)) {
            helper.fail("expected problem '" + expectedKey + "', got '" + key + "'");
            return;
        }
        helper.succeed();
    }
}
