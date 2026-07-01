package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Boss-tier in-world GameTests: the v1.14 boss catalyst altar gate
 * (docs/boss_catalyst_altar.md), the End Dragon Altar (#249,
 * docs/dragon_altar.md), and the Wither Altar (#247). Each pins a
 * validator / gate decision to the shipped fixture so a layout or offset
 * drift fails CI.
 *
 * <p>Ported from the 1.21.1 {@code @GameTest} annotation form to the 26.1
 * {@link PFGameTests#test(String, int, java.util.function.Consumer)} registrar.
 * The dragon/wither tests load their own structures (not {@code empty_5x5x5});
 * those template mappings live in the central registrar.
 */
final class BossAltarTests {

    private BossAltarTests() {
    }

    static void register() {
        PFGameTests.test("boss_catalyst_altar_gate", 100, BossAltarTests::bossCatalystAltarGate);
        PFGameTests.test("boss_altar_source_does_not_spawn_inside_the_source_block", 100,
            BossAltarTests::bossAltarSourceDoesNotSpawnInsideTheSourceBlock);
        PFGameTests.test("dragon_altar_validates_when_built", "dragon_altar", 100, BossAltarTests::dragonAltarValidatesWhenBuilt);
        PFGameTests.test("dragon_altar_rejects_missing_froglight", "dragon_altar", 100, BossAltarTests::dragonAltarRejectsMissingFroglight);
        PFGameTests.test("dragon_altar_summon_deposits_drops", "dragon_altar", 320, BossAltarTests::dragonAltarSummonDepositsDrops);
        PFGameTests.test("wither_altar_validates_when_built", "wither_altar", 100, BossAltarTests::witherAltarValidatesWhenBuilt);
        PFGameTests.test("wither_altar_validates_when_rotated", "wither_altar", Rotation.CLOCKWISE_90, 100, BossAltarTests::witherAltarValidatesWhenRotated);
        PFGameTests.test("wither_altar_rejects_missing_froglight", "wither_altar", 100, BossAltarTests::witherAltarRejectsMissingFroglight);
        PFGameTests.test("wither_altar_summon_deposits_drops", "wither_altar", 320, BossAltarTests::witherAltarSummonDepositsDrops);
    }

    /**
     * Boss catalyst altar gate (#184, docs/boss_catalyst_altar.md). Tests the
     * gate's decision logic directly via the public helpers (the spawn tick
     * itself is scheduled + private; the predicate is the testable seam):
     * (1) only spawn_catalyst variants are gated; (2) a 6-face count requires
     * the MATCHING catalyst on all six faces; (3) a mismatched catalyst does
     * not count.
     */
    private static void bossCatalystAltarGate(GameTestHelper helper) {
        Identifier netherStar = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "nether_star");
        Identifier iron = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

        // (1) gating predicate: boss variant requires a catalyst, iron does not.
        if (!com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock
                .variantRequiresCatalyst(helper.getLevel(), netherStar)) {
            helper.fail("nether_star (spawn_catalyst) should require a catalyst altar");
            return;
        }
        if (com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock
                .variantRequiresCatalyst(helper.getLevel(), iron)) {
            helper.fail("iron is not a boss variant and must not be altar-gated");
            return;
        }

        // (2) surround a center with nether_star_catalyst on 5 of 6 faces.
        BlockPos center = new BlockPos(2, 2, 2);
        net.minecraft.world.level.block.Block starCat = PFBlocks.NETHER_STAR_CATALYST.get();
        net.minecraft.core.Direction[] dirs = net.minecraft.core.Direction.values();
        for (int i = 0; i < dirs.length - 1; i++) {
            helper.setBlock(center.relative(dirs[i]), starCat);
        }
        int five = com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock
            .catalystFaceCount(helper.getLevel(), helper.absolutePos(center), netherStar);
        // catalystFaceCount takes a WORLD pos; helper.setBlock takes relative.
        // Re-count against the relative center mapped to world via absolutePos.
        if (five != 5) {
            helper.fail("5 catalyst faces should read 5, got " + five);
            return;
        }
        // (3) the 6th face: a MISMATCHED catalyst must not count.
        helper.setBlock(center.relative(dirs[5]), PFBlocks.DRAGON_EGG_CATALYST.get());
        int stillFive = com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock
            .catalystFaceCount(helper.getLevel(), helper.absolutePos(center), netherStar);
        if (stillFive != 5) {
            helper.fail("a dragon_egg_catalyst must not count toward a nether_star altar, got " + stillFive);
            return;
        }
        // The matching 6th completes it.
        helper.setBlock(center.relative(dirs[5]), starCat);
        int six = com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock
            .catalystFaceCount(helper.getLevel(), helper.absolutePos(center), netherStar);
        if (six != 6) {
            helper.fail("6 matching catalyst faces should read 6, got " + six);
            return;
        }
        helper.succeed();
    }

    /**
     * Boss altar spawn safety (docs/boss_catalyst_altar.md): a fully-sealed,
     * altar-gated source must NOT spawn a slime inside its own (enclosed) milk
     * source block. The altar is complete (6 matching catalyst faces, so the gate
     * passes and the source would otherwise spawn) and the source is boxed in solid
     * on every side - including the layer above the top shell - so chooseSpawnPos
     * finds no free neighbour landing. The fix skips the spawn for an altar-gated
     * source rather than falling back to the source cell (which would trap the slime
     * inside the milk block). A non-altar source keeps that fallback.
     */
    private static void bossAltarSourceDoesNotSpawnInsideTheSourceBlock(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 2, 2);
        Identifier netherStar = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "nether_star");
        // Seal the source in solid stone on every side, and cap the layer above the
        // top shell (y up to 4), so no neighbour cell has an open block above it.
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        // The six faces must be the MATCHING catalyst so the altar gate (>=6) passes.
        net.minecraft.world.level.block.Block starCat = PFBlocks.NETHER_STAR_CATALYST.get();
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            helper.setBlock(center.relative(dir), starCat);
        }
        com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock block =
            placeMilkSource(helper, center, "nether_star");

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(center);
        helper.assertTrue(com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock
                .catalystFaceCount(level, abs, netherStar) == 6,
            "test setup: a sealed altar should read 6 catalyst faces");
        // Drive a spawn tick directly (the gate passes, so without the fix this would
        // fall back to spawning a slime inside the source block).
        block.tick(level.getBlockState(abs), level, abs, level.getRandom());

        helper.runAfterDelay(3, () -> {
            int slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get()).size();
            helper.assertTrue(slimes == 0,
                "a sealed boss altar must not spawn a slime inside the milk source block, found " + slimes);
            helper.succeed();
        });
    }

    /**
     * The shipped {@code dragon_altar} structure must validate as built. This pins
     * {@link com.flatts.productivefrogs.content.multiblock.DragonAltarValidator} to the
     * canonical structure: if either drifts (a layout edit without re-syncing the
     * validator, or vice versa), this test fails.
     */
    private static void dragonAltarValidatesWhenBuilt(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            BlockPos hatch = findAltarHatch(helper);
            helper.assertTrue(hatch != null, "no End Dragon Altar Hatch in the loaded structure");
            com.flatts.productivefrogs.content.multiblock.DragonAltarValidator.Result r =
                com.flatts.productivefrogs.content.multiblock.DragonAltarValidator
                    .validate(helper.getLevel(), helper.absolutePos(hatch));
            helper.assertTrue(r.valid(), "dragon_altar must validate at " + hatch + "; validator says: " + r.detail());
        });
    }

    /** Strictness: knocking out one froglight must make the altar fail validation. */
    private static void dragonAltarRejectsMissingFroglight(GameTestHelper helper) {
        BlockPos hatch = findAltarHatch(helper);
        helper.assertTrue(hatch != null, "no End Dragon Altar Hatch in the loaded structure");
        // A WSS froglight sits at offset {-3,-6,-1} from the hatch.
        helper.setBlock(hatch.offset(-3, -6, -1), Blocks.AIR);
        helper.assertTrue(
            !com.flatts.productivefrogs.content.multiblock.DragonAltarValidator
                .validate(helper.getLevel(), helper.absolutePos(hatch)).valid(),
            "a dragon altar missing a froglight must not validate (strictness)");
        helper.succeed();
    }

    /**
     * End-to-end: a built altar with all four receptacles primed must run a summon and
     * deposit the reward into the Hatch - the boss Froglights (a Dragon Breath Froglight
     * and a Dragon Egg Froglight, each variant-stamped) plus the dragon's own drop (the
     * Princess's Kiss from the {@code productivefrogs:dragon_altar} loot table). Guards
     * both the loot-table path (a wrong id or param set would crash/empty it) and the
     * froglight payout (wrong variant id would mis-stamp).
     */
    private static void dragonAltarSummonDepositsDrops(GameTestHelper helper) {
        BlockPos hatch = findAltarHatch(helper);
        helper.assertTrue(hatch != null, "no End Dragon Altar Hatch in the loaded structure");
        BlockPos absHatch = helper.absolutePos(hatch);
        // Ensure all four receptacles are primed with an End Crystal - this triggers the
        // summon. (The fixture may already ship armed; only fill the empty ones.)
        for (BlockPos rp : com.flatts.productivefrogs.content.multiblock.DragonAltarValidator.receptacles(absHatch)) {
            if (helper.getLevel().getBlockEntity(rp)
                    instanceof com.flatts.productivefrogs.content.block.entity.EndCrystalReceptacleBlockEntity r) {
                if (!r.isFilled()) {
                    r.tryInsert(new ItemStack(Items.END_CRYSTAL));
                }
                helper.assertTrue(r.isFilled(), "receptacle at " + rp + " could not be primed");
            } else {
                helper.fail("no receptacle block entity at " + rp);
            }
        }
        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.BlockEntity be = helper.getLevel().getBlockEntity(absHatch);
            helper.assertTrue(be instanceof com.flatts.productivefrogs.content.block.entity.EndDragonAltarHatchBlockEntity,
                "hatch block entity missing");
            net.minecraft.world.Container c = (net.minecraft.world.Container) be;
            helper.assertTrue(containerHas(c, PFItems.PRINCESS_KISS.get()), "hatch missing the Princess's Kiss after summon");
            helper.assertTrue(containsFroglightVariant(c, "dragon_breath"), "hatch missing the Dragon Breath Froglight after summon");
            helper.assertTrue(containsFroglightVariant(c, "dragon_egg"), "hatch missing the Dragon Egg Froglight after summon");
        });
    }

    /** The shipped {@code wither_altar} structure must validate as built (pins validator + structure). */
    private static void witherAltarValidatesWhenBuilt(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            BlockPos hatch = findWitherAltarHatch(helper);
            helper.assertTrue(hatch != null, "no Wither Altar Hatch in the loaded structure");
            com.flatts.productivefrogs.content.multiblock.WitherAltarValidator.Result r =
                com.flatts.productivefrogs.content.multiblock.WitherAltarValidator
                    .validate(helper.getLevel(), helper.absolutePos(hatch));
            helper.assertTrue(r.valid(), "wither_altar must validate at " + hatch + "; validator says: " + r.detail());
        });
    }

    /**
     * Facing-aware: the shipped structure placed at a 90-degree rotation must still
     * validate (the ritual wall now points a different world direction). Guards the
     * build-orientation fix - previously the altar only validated in one world rotation.
     */
    private static void witherAltarValidatesWhenRotated(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            BlockPos hatch = findWitherAltarHatch(helper);
            helper.assertTrue(hatch != null, "no Wither Altar Hatch in the rotated structure");
            com.flatts.productivefrogs.content.multiblock.WitherAltarValidator.Result r =
                com.flatts.productivefrogs.content.multiblock.WitherAltarValidator
                    .validate(helper.getLevel(), helper.absolutePos(hatch));
            helper.assertTrue(r.valid(),
                "a rotated wither_altar must still validate; validator says: " + r.detail());
        });
    }

    /** Strictness: knocking out one shell froglight must make the altar fail validation. */
    private static void witherAltarRejectsMissingFroglight(GameTestHelper helper) {
        BlockPos hatch = findWitherAltarHatch(helper);
        helper.assertTrue(hatch != null, "no Wither Altar Hatch in the loaded structure");
        // A blaze rod shell froglight sits at offset {0,3,3} from the hatch.
        helper.setBlock(hatch.offset(0, 3, 3), Blocks.AIR);
        helper.assertTrue(
            !com.flatts.productivefrogs.content.multiblock.WitherAltarValidator
                .validate(helper.getLevel(), helper.absolutePos(hatch)).valid(),
            "a wither altar missing a shell froglight must not validate (strictness)");
        helper.succeed();
    }

    /**
     * End-to-end: a built altar with all seven receptacles primed (4 soul sand + 3 skulls)
     * must run a summon and deposit the Nether Star Froglight into the Hatch. Guards the
     * summon state machine + the boss-Froglight payout.
     */
    private static void witherAltarSummonDepositsDrops(GameTestHelper helper) {
        BlockPos hatch = findWitherAltarHatch(helper);
        helper.assertTrue(hatch != null, "no Wither Altar Hatch in the loaded structure");
        BlockPos absHatch = helper.absolutePos(hatch);
        // Prime each receptacle with its accepted item (soul sand or a wither skeleton skull).
        for (BlockPos rp : com.flatts.productivefrogs.content.multiblock.WitherAltarValidator.receptacles(absHatch)) {
            if (helper.getLevel().getBlockState(rp).getBlock()
                        instanceof com.flatts.productivefrogs.content.block.WitherSummonReceptacleBlock wb
                    && helper.getLevel().getBlockEntity(rp)
                        instanceof com.flatts.productivefrogs.content.block.entity.WitherSummonReceptacleBlockEntity r) {
                if (!r.isFilled()) {
                    r.tryInsert(new ItemStack(wb.accepted()));
                }
                helper.assertTrue(r.isFilled(), "receptacle at " + rp + " could not be primed");
            } else {
                helper.fail("no Wither summon receptacle at " + rp);
            }
        }
        helper.succeedWhen(() -> {
            net.minecraft.world.level.block.entity.BlockEntity be = helper.getLevel().getBlockEntity(absHatch);
            helper.assertTrue(be instanceof com.flatts.productivefrogs.content.block.entity.WitherAltarHatchBlockEntity,
                "hatch block entity missing");
            net.minecraft.world.Container c = (net.minecraft.world.Container) be;
            helper.assertTrue(containsFroglightVariant(c, "nether_star"),
                "hatch missing the Nether Star Froglight after summon");
            // The star is paid out only as the Froglight - a raw Nether Star must be stripped.
            helper.assertTrue(!containerHas(c, Items.NETHER_STAR),
                "raw Nether Star leaked into the hatch (should be stripped from the wither loot)");
        });
    }

    // ---- Shared helpers (carried from the 1.21.1 PFGameTests monolith) ----

    /** Place the single Slime Milk source block and stamp its BE variant (26.1 R-1). */
    private static com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock placeMilkSource(
            GameTestHelper helper, BlockPos pos, String variantPath) {
        var block = PFBlocks.SLIME_MILK_SOURCE.get();
        // The single source block has no baked variant; it is inert until the BE is
        // stamped (in-world the placing bucket's checkExtraContent does this from its
        // SLIME_VARIANT component). stampMilkVariant is that seed here.
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

    /** Find the altar Hatch within the loaded structure (relative pos), or null. */
    private static BlockPos findAltarHatch(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(6, 9, 6))) {
            if (helper.getBlockState(p).is(PFBlocks.END_DRAGON_ALTAR_HATCH.get())) {
                return p.immutable();
            }
        }
        return null;
    }

    /** Find the Wither Altar Hatch within the loaded 5x5x5 structure (relative pos), or null. */
    private static BlockPos findWitherAltarHatch(GameTestHelper helper) {
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4))) {
            if (helper.getBlockState(p).is(PFBlocks.WITHER_ALTAR_HATCH.get())) {
                return p.immutable();
            }
        }
        return null;
    }

    private static boolean containerHas(net.minecraft.world.Container c, net.minecraft.world.item.Item item) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (c.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** True if the container holds a configurable_froglight stamped with productivefrogs:&lt;variantPath&gt;. */
    private static boolean containsFroglightVariant(net.minecraft.world.Container c, String variantPath) {
        net.minecraft.resources.Identifier want =
            net.minecraft.resources.Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantPath);
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.is(PFItems.CONFIGURABLE_FROGLIGHT.get())
                    && want.equals(s.get(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get()))) {
                return true;
            }
        }
        return false;
    }
}
