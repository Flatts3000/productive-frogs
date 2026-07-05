package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Froglight Crucible + Casting Mold in-world GameTests (v1.12), ported to the
 * 26.1 registration shape (see {@link PFGameTests#test}). Covers the per-variant
 * configurable-froglight smelt recipes, the place/break variant round-trip, the
 * heat-driven Crucible melt loop, and the molten -> ingot Casting Mold lane.
 *
 * <p><b>26.1 ports applied here.</b> The server-side recipe lookup moved from
 * {@code level.getServer().getRecipeManager()} to {@code level.recipeAccess()}
 * (which is the {@code RecipeManager} on the server, mirroring
 * {@code CrucibleBlockEntity.recipeFor}), and value lookups by id moved from
 * {@code Registry.get(Identifier)} to {@code Registry.getValue(Identifier)}.
 *
 * <p><b>Capability ports applied.</b> The four capability-mutation assertions
 * (Crucible/Mold fill/drain/insert) now use the 26.1
 * {@code Capabilities.Fluid.BLOCK} / {@code Capabilities.Item.BLOCK} surfaces over
 * {@code ResourceHandler<FluidResource>} / {@code ResourceHandler<ItemResource>},
 * driven through a {@code Transaction} (commit makes a mutation real; a no-commit
 * transaction simulates). See the four {@code *Capability*} / {@code *Hopper*}
 * methods.
 */
final class CrucibleMoldTests {

    private CrucibleMoldTests() {
    }

    static void register() {
        PFGameTests.test("variant_configurable_froglight_smelt_recipes_resolve_per_variant", 100,
            CrucibleMoldTests::variantConfigurableFroglightSmeltRecipesResolvePerVariant);
        PFGameTests.test("configurable_froglight_without_variant_does_not_smelt", 100,
            CrucibleMoldTests::configurableFroglightWithoutVariantDoesNotSmelt);
        PFGameTests.test("variant_froglight_round_trip_preserves_variant_through_place_and_break", 100,
            CrucibleMoldTests::variantFroglightRoundTripPreservesVariantThroughPlaceAndBreak);
        PFGameTests.test("crucible_melts_lava_froglight_over_torch_heat", 1600,
            CrucibleMoldTests::crucibleMeltsLavaFroglightOverTorchHeat);
        PFGameTests.test("crucible_melts_cobblestone_to_lava_over_heat", 1600,
            CrucibleMoldTests::crucibleMeltsCobblestoneToLavaOverHeat);
        PFGameTests.test("crucible_does_not_melt_without_heat", 200,
            CrucibleMoldTests::crucibleDoesNotMeltWithoutHeat);
        PFGameTests.test("crucible_reads_froglight_heat_from_variant_data_map", 100,
            CrucibleMoldTests::crucibleReadsFroglightHeatFromVariantDataMap);
        PFGameTests.test("crucible_melts_metal_froglight_to_molten_fluid", 1400,
            CrucibleMoldTests::crucibleMeltsMetalFroglightToMoltenFluid);
        PFGameTests.test("crucible_insert_gating_and_hopper_queue_cap", 100,
            CrucibleMoldTests::crucibleInsertGatingAndHopperQueueCap);
        PFGameTests.test("casting_mold_tower_casts_iron_froglight_to_two_ingots", 1600,
            CrucibleMoldTests::castingMoldTowerCastsIronFroglightToTwoIngots);
        PFGameTests.test("casting_mold_capability_fill_casts_one_ingot", 200,
            CrucibleMoldTests::castingMoldCapabilityFillCastsOneIngot);
        PFGameTests.test("casting_mold_rejects_non_castable_fluids", 100,
            CrucibleMoldTests::castingMoldRejectsNonCastableFluids);
    }

    /**
     * Every variant-stamped configurable_froglight should smelt to the variant's
     * canonical resource. Pins the 12 component-ingredient smelting recipes
     * against the {@code neoforge:components} ingredient codec - if NeoForge ever
     * renames the discriminator, the recipes silently stop matching and this
     * catches it.
     */
    private static void variantConfigurableFroglightSmeltRecipesResolvePerVariant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 26.1: the server-side RecipeManager is reached via recipeAccess()
        // (was level.getServer().getRecipeManager()); recipeAccess() returns a
        // RecipeAccess that is the RecipeManager on the server.
        if (!(level.recipeAccess() instanceof net.minecraft.world.item.crafting.RecipeManager rm)) {
            helper.fail("recipeAccess() did not yield a server-side RecipeManager");
            return;
        }

        // Full coverage - all 12 variants. The component-ingredient pipeline
        // is the load-bearing piece here (one bad codec name and every
        // variant silently stops matching), so it's cheap insurance to
        // exercise the whole roster rather than rely on a sample.
        assertVariantSmelts(helper, rm, level, "iron", net.minecraft.world.item.Items.IRON_INGOT);
        assertVariantSmelts(helper, rm, level, "copper", net.minecraft.world.item.Items.COPPER_INGOT);
        assertVariantSmelts(helper, rm, level, "gold", net.minecraft.world.item.Items.GOLD_INGOT);
        assertVariantSmelts(helper, rm, level, "redstone", net.minecraft.world.item.Items.REDSTONE);
        assertVariantSmelts(helper, rm, level, "lapis", net.minecraft.world.item.Items.LAPIS_LAZULI);
        assertVariantSmelts(helper, rm, level, "coal", net.minecraft.world.item.Items.COAL);
        assertVariantSmelts(helper, rm, level, "diamond", net.minecraft.world.item.Items.DIAMOND);
        assertVariantSmelts(helper, rm, level, "emerald", net.minecraft.world.item.Items.EMERALD);
        assertVariantSmelts(helper, rm, level, "prismarine", net.minecraft.world.item.Items.PRISMARINE);
        assertVariantSmelts(helper, rm, level, "sponge", net.minecraft.world.item.Items.SPONGE);

        helper.succeed();
    }

    /**
     * Negative: a configurable_froglight stack with no slime_variant component
     * should not match any of the 12 variant smelting recipes - each recipe's
     * ingredient is strict on the component. Pins the "fail closed" semantic:
     * no variant -> no recipe -> no spurious smelt output.
     */
    private static void configurableFroglightWithoutVariantDoesNotSmelt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // 26.1: server-side RecipeManager via recipeAccess() (see above).
        if (!(level.recipeAccess() instanceof net.minecraft.world.item.crafting.RecipeManager rm)) {
            helper.fail("recipeAccess() did not yield a server-side RecipeManager");
            return;
        }

        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        net.minecraft.world.item.crafting.SingleRecipeInput input =
            new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe>> match =
            rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, level);
        if (match.isPresent()) {
            helper.fail("configurable_froglight without slime_variant component must NOT match any smelt recipe, but matched "
                + match.get().id() + " -> " + match.get().value().assemble(input));
            return;
        }
        helper.succeed();
    }

    /**
     * Round-trip: a variant-stamped configurable_froglight ItemStack placed as a
     * block, then broken, must produce a dropped ItemStack carrying the same
     * {@code SLIME_VARIANT} component. Pins the BE setter, the implicit-component
     * exposure, and the {@code copy_components} loot table together - if any
     * silently breaks, an Iron Froglight loses its variant on break.
     */
    private static void variantFroglightRoundTripPreservesVariantThroughPlaceAndBreak(GameTestHelper helper) {
        BlockPos blockPos = new BlockPos(2, 2, 2);
        Identifier ironVariant = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

        ServerLevel level = helper.getLevel();
        BlockPos absPos = helper.absolutePos(blockPos);

        // Mirror the placement code path: set the block, then write the
        // variant onto the BE (this is what ConfigurableFroglightItem's
        // updateCustomBlockEntityTag override does after vanilla seats the BE).
        level.setBlock(absPos,
            PFBlocks.CONFIGURABLE_FROGLIGHT.get().defaultBlockState(), 3);
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(absPos);
        if (!(be instanceof com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity froglightBe)) {
            helper.fail("expected ConfigurableFroglightBlockEntity at " + blockPos
                + ", got " + (be == null ? "null" : be.getClass().getSimpleName()));
            return;
        }
        froglightBe.setVariantId(ironVariant);

        // Run the loot table. The 4-arg dropResources overload passes the
        // BE through LootContextParams.BLOCK_ENTITY, which copy_components
        // (source=block_entity) reads via BlockEntity.collectComponents().
        BlockState state = level.getBlockState(absPos);
        net.minecraft.world.level.block.Block.dropResources(state, level, absPos, froglightBe);

        helper.succeedWhen(() -> {
            net.minecraft.world.item.Item expected = PFItems.CONFIGURABLE_FROGLIGHT.get();
            net.minecraft.world.entity.item.ItemEntity match =
                helper.getEntities(net.minecraft.world.entity.EntityType.ITEM).stream()
                    .filter(e -> e.getItem().is(expected))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                helper.fail("expected configurable_froglight to drop after break");
                return;
            }
            Identifier droppedVariant = match.getItem().get(
                com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get());
            if (!ironVariant.equals(droppedVariant)) {
                helper.fail("dropped variant=" + droppedVariant + ", expected " + ironVariant);
            }
        });
    }

    // ===================================================================
    // Froglight Crucible (v1.12 wave 1) - heat-driven Froglight -> fluid
    // ===================================================================

    /**
     * End-to-end melt loop: a torch first pins the heat-1 data-map read, then
     * the heat source swaps to soul fire (heat 5) so the 1,000 mB lava Froglight
     * melts in CI-friendly time. Once full, three invariants are checked in the
     * same world state: (1) a water Froglight is rejected, (2) the fluid
     * capability refuses fill(), and (3) the capability drains the full 1,000 mB.
     */
    private static void crucibleMeltsLavaFroglightOverTorchHeat(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(base.above(), net.minecraft.world.level.block.Blocks.TORCH);
        helper.setBlock(base.above(2), PFBlocks.CRUCIBLE.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(base.above(2)))
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        if (crucible.heatBelow() != 1) {
            helper.fail("torch below should read heat 1 from the crucible_heat data map, got " + crucible.heatBelow());
            return;
        }
        // Swap the torch for soul fire so the bulk melt finishes in CI time.
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.SOUL_SAND);
        helper.setBlock(base.above(), net.minecraft.world.level.block.Blocks.SOUL_FIRE);
        if (crucible.heatBelow() != 5) {
            helper.fail("soul fire below should read heat 5 from the crucible_heat data map, got " + crucible.heatBelow());
            return;
        }
        if (!crucible.acceptMelt(stampedFroglight("lava"))) {
            helper.fail("lava Froglight should queue into an idle, empty crucible");
            return;
        }
        if (crucible.solids() != 1000) {
            helper.fail("queued solids should be 1000 mB after one lava Froglight, got " + crucible.solids());
            return;
        }
        helper.succeedWhen(() -> {
            net.neoforged.neoforge.fluids.FluidStack fluid = crucible.fluid();
            helper.assertTrue(fluid.getFluid() == net.minecraft.world.level.material.Fluids.LAVA
                    && fluid.getAmount() == 1000,
                "tank should hold 1000 mB lava after the melt, holds "
                    + fluid.getAmount() + " of " + fluid.getFluid());
            helper.assertTrue(crucible.insertCheck(stampedFroglight("water"))
                    == com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity.InsertCheck.REJECT,
                "water Froglight must be rejected while the tank holds lava (single-fluid rule)");
            // 26.1: Capabilities.Fluid.BLOCK -> ResourceHandler<FluidResource>,
            // mutated through a Transaction (commit makes it real; no commit simulates).
            net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> cap =
                helper.getLevel().getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                    helper.absolutePos(base.above(2)), null);
            helper.assertTrue(cap != null, "crucible exposes no Fluid.BLOCK capability");
            helper.assertTrue(fillCommit(cap, net.minecraft.world.level.material.Fluids.WATER, 1000) == 0,
                "capability must be extract-only (fill must return 0)");
            net.neoforged.neoforge.transfer.fluid.FluidResource have = cap.getResource(0);
            int drained;
            try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                    net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                drained = cap.extract(have, 1000, tx);
                tx.commit();
            }
            helper.assertTrue(have.getFluid() == net.minecraft.world.level.material.Fluids.LAVA
                    && drained == 1000,
                "capability drain should yield the full 1000 mB lava, got "
                    + drained + " of " + have.getFluid());
        });
    }

    /**
     * Ex-Deorum heated-crucible parity: a raw block melts to lava over heat, the
     * same loop the Froglight lane uses. Four cobblestone (250 mB each) melt to
     * one full bucket of lava over soul fire; a non-meltable block (dirt) is
     * REJECTed by the recipe gate.
     */
    private static void crucibleMeltsCobblestoneToLavaOverHeat(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.SOUL_SAND);
        helper.setBlock(base.above(), net.minecraft.world.level.block.Blocks.SOUL_FIRE);
        helper.setBlock(base.above(2), PFBlocks.CRUCIBLE.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(base.above(2)))
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        // The recipe gate accepts cobblestone (has a melt recipe) and rejects dirt.
        if (crucible.insertCheck(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE))
                != com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity.InsertCheck.OK) {
            helper.fail("cobblestone should classify OK (c:cobblestones -> lava)");
            return;
        }
        if (crucible.insertCheck(new ItemStack(net.minecraft.world.item.Items.DIRT))
                != com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity.InsertCheck.REJECT) {
            helper.fail("dirt has no melt recipe and must be REJECTed");
            return;
        }
        // Four cobblestone -> 4 x 250 = 1000 mB lava (one bucket).
        for (int i = 0; i < 4; i++) {
            if (!crucible.acceptMelt(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE))) {
                helper.fail("cobblestone " + (i + 1) + " should queue into the crucible");
                return;
            }
        }
        if (crucible.solids() != 1000) {
            helper.fail("four cobblestone should queue 1000 mB solids, got " + crucible.solids());
            return;
        }
        helper.succeedWhen(() -> {
            net.neoforged.neoforge.fluids.FluidStack fluid = crucible.fluid();
            helper.assertTrue(fluid.getFluid() == net.minecraft.world.level.material.Fluids.LAVA
                    && fluid.getAmount() == 1000,
                "tank should hold 1000 mB lava after melting four cobblestone, holds "
                    + fluid.getAmount() + " of " + fluid.getFluid());
        });
    }

    /**
     * No heat below = no melt progress. Loading is deliberately allowed (stage
     * the Froglight, light the fire after), but with stone below the loop must
     * not advance: after a generous delay the tank stays empty, the Froglight
     * stays loaded, and progress stays 0.
     */
    private static void crucibleDoesNotMeltWithoutHeat(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(base.above(), PFBlocks.CRUCIBLE.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(base.above()))
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        if (!crucible.acceptMelt(stampedFroglight("lava"))) {
            helper.fail("queueing a Froglight must not require heat (stage first, light after)");
            return;
        }
        helper.runAfterDelay(80L, () -> {
            if (!crucible.fluid().isEmpty()) {
                helper.fail("tank filled despite no heat source below");
                return;
            }
            if (crucible.solids() != 1000) {
                helper.fail("solids should stay fully queued without heat, got " + crucible.solids());
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Placed Froglights as heat sources via the variant-keyed
     * {@code froglight_heat} data map: a Blaze Froglight under a Crucible reads
     * heat 6, while an unmapped variant (iron) contributes nothing. Pins the
     * BE-variant lookup path that the block-keyed {@code crucible_heat} map can't
     * express.
     */
    private static void crucibleReadsFroglightHeatFromVariantDataMap(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, PFBlocks.CONFIGURABLE_FROGLIGHT.get());
        helper.setBlock(base.above(), PFBlocks.CRUCIBLE.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(base))
                instanceof com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity froglight)) {
            helper.fail("configurable froglight did not create its BlockEntity");
            return;
        }
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(base.above()))
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        froglight.setVariantId(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "lava"));
        if (crucible.heatBelow() != 3) {
            helper.fail("Lava Froglight below should read heat 3 from froglight_heat, got " + crucible.heatBelow());
            return;
        }
        froglight.setVariantId(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"));
        if (crucible.heatBelow() != 0) {
            helper.fail("an unmapped Froglight variant must contribute no heat, got " + crucible.heatBelow());
            return;
        }
        helper.succeed();
    }

    /**
     * The molten lane: an iron Froglight melts to 180 mB (2 ingots' worth) of
     * {@code productivefrogs:molten_iron} - PF's own fluid regardless of
     * environment (ATO 4.x dropped its fluid system on 26.1, so the old
     * ATO-loaded conditional output is gone; the melt recipe is unconditional).
     */
    private static void crucibleMeltsMetalFroglightToMoltenFluid(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(base.above(), net.minecraft.world.level.block.Blocks.TORCH);
        helper.setBlock(base.above(2), PFBlocks.CRUCIBLE.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(base.above(2)))
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        if (!crucible.acceptMelt(stampedFroglight("iron"))) {
            helper.fail("iron Froglight should queue (every metal has a molten mapping in wave 2)");
            return;
        }
        Identifier expected = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "molten_iron");
        helper.succeedWhen(() -> {
            net.neoforged.neoforge.fluids.FluidStack fluid = crucible.fluid();
            Identifier actual = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            helper.assertTrue(expected.equals(actual) && fluid.getAmount() == 180,
                "iron Froglight should melt to 180 mB of " + expected + ", got "
                    + fluid.getAmount() + " of " + actual);
        });
    }

    /**
     * Insert gating on the solids model: (1) a Froglight with no
     * {@code crucible_melting} recipe (bone) classifies REJECT and
     * {@code acceptMelt} refuses it; (2) the hopper-facing item capability accepts
     * lava Froglights one at a time until the solids queue is full (4 x 1,000 mB),
     * then bounces the fifth - pinning both the automation path and the MAX_SOLIDS
     * cap.
     */
    private static void crucibleInsertGatingAndHopperQueueCap(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(base.above(), PFBlocks.CRUCIBLE.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(base.above()))
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        // Bone has no melt recipe (and never will - the melt roster is the
        // metal lane). Iron stopped being the reject example when wave 2 gave
        // every metal a molten mapping.
        ItemStack bone = stampedFroglight("bone");
        if (crucible.insertCheck(bone)
                != com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity.InsertCheck.REJECT) {
            helper.fail("bone Froglight has no melt recipe and must classify REJECT");
            return;
        }
        if (crucible.acceptMelt(bone)) {
            helper.fail("acceptMelt must refuse a REJECT-classified stack");
            return;
        }
        // 26.1: Capabilities.Item.BLOCK -> ResourceHandler<ItemResource>; inserts
        // run through a Transaction (insert returns the count accepted, not a leftover).
        net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> items =
            helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                helper.absolutePos(base.above()), null);
        if (items == null) {
            helper.fail("crucible exposes no Item.BLOCK capability");
            return;
        }
        // Hopper path: one Froglight consumed per insert call (one recipe
        // evaluation each), four fit, the fifth bounces off the full queue.
        net.neoforged.neoforge.transfer.item.ItemResource lavaRes =
            net.neoforged.neoforge.transfer.item.ItemResource.of(stampedFroglight("lava"));
        int remaining = 4;
        for (int i = 0; i < 4 && remaining > 0; i++) {
            int moved;
            try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                    net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                moved = items.insert(lavaRes, remaining, tx);
                tx.commit();
            }
            remaining -= moved;
        }
        if (remaining != 0) {
            helper.fail("four lava Froglights should hopper-insert while the queue has room, "
                + remaining + " left over");
            return;
        }
        if (crucible.solids() != 4000) {
            helper.fail("solids queue should hold 4000 mB after four Froglights, got " + crucible.solids());
            return;
        }
        int fifthInserted;
        try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            fifthInserted = items.insert(
                net.neoforged.neoforge.transfer.item.ItemResource.of(stampedFroglight("lava")), 1, tx);
            tx.commit();
        }
        if (fifthInserted != 0) {
            helper.fail("fifth Froglight must bounce off the full solids queue");
            return;
        }
        helper.succeed();
    }

    // ===================================================================
    // Casting Mold (v1.12 wave 2 part B) - molten -> ingot
    // ===================================================================

    /**
     * The full three-block tower, end to end: torch (heat 1) under a Crucible
     * under a Casting Mold. An iron Froglight melts to 180 mB molten iron, the
     * Mold tower-pulls it (no pipes), and two 90 mB casts land 2 iron ingots in
     * the output slot - the Tinkers-convention ore-doubling yield surfacing as
     * items. The cast output is vanilla iron either way (the recipe matches by
     * {@code c:molten_iron} TAG).
     */
    private static void castingMoldTowerCastsIronFroglightToTwoIngots(GameTestHelper helper) {
        BlockPos base = new BlockPos(2, 1, 2);
        helper.setBlock(base, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(base.above(), net.minecraft.world.level.block.Blocks.TORCH);
        helper.setBlock(base.above(2), PFBlocks.CRUCIBLE.get());
        helper.setBlock(base.above(3), PFBlocks.CASTING_MOLD.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(base.above(2)))
                instanceof com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity crucible)) {
            helper.fail("crucible block did not create a CrucibleBlockEntity");
            return;
        }
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(base.above(3)))
                instanceof com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity mold)) {
            helper.fail("casting mold block did not create a CastingMoldBlockEntity");
            return;
        }
        if (!crucible.acceptMelt(stampedFroglight("iron"))) {
            helper.fail("iron Froglight should queue into the crucible");
            return;
        }
        helper.succeedWhen(() -> {
            ItemStack out = mold.output().getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(out.is(net.minecraft.world.item.Items.IRON_INGOT) && out.getCount() == 2,
                "tower should cast 2 iron ingots from one iron Froglight (180 mB / 90 mB), got "
                    + out.getCount() + " x " + BuiltInRegistries.ITEM.getKey(out.getItem()));
            helper.assertTrue(mold.fluid().isEmpty() && crucible.fluid().isEmpty(),
                "both tanks should be empty after the full melt + double cast");
        });
    }

    /**
     * Free-standing Mold fed through the fill-enabled fluid capability (the pipe
     * path): 90 mB of this environment's molten iron fills, and one cast lands 1
     * iron ingot with the buffer drained.
     */
    private static void castingMoldCapabilityFillCastsOneIngot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PFBlocks.CASTING_MOLD.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity mold)) {
            helper.fail("casting mold block did not create a CastingMoldBlockEntity");
            return;
        }
        // 26.1: Capabilities.Fluid.BLOCK -> ResourceHandler<FluidResource>;
        // fill via a committed Transaction, drain-refusal via a no-commit (simulate) one.
        net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> cap =
            helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                helper.absolutePos(pos), null);
        if (cap == null) {
            helper.fail("casting mold exposes no Fluid.BLOCK capability");
            return;
        }
        int filled = fillCommit(cap, envMoltenIron(), 90);
        if (filled != 90) {
            helper.fail("capability should accept the full 90 mB of molten iron, took " + filled);
            return;
        }
        // Fill-only handler: committed molten must not be pipeable back out -
        // it leaves as a cast item or not at all. Simulate the drain (no commit).
        int drainSim;
        try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            drainSim = cap.extract(cap.getResource(0), 90, tx);
        }
        if (drainSim != 0) {
            helper.fail("mold capability must refuse drain (fill-only; molten leaves as an item)");
            return;
        }
        helper.succeedWhen(() -> {
            ItemStack out = mold.output().getStackInSlot(
                com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(out.is(net.minecraft.world.item.Items.IRON_INGOT) && out.getCount() == 1,
                "90 mB molten iron should cast 1 iron ingot, got "
                    + out.getCount() + " x " + BuiltInRegistries.ITEM.getKey(out.getItem()));
            helper.assertTrue(mold.fluid().isEmpty(), "buffer should be drained after the cast");
        });
    }

    /**
     * Fill gating: the Mold accepts only fluids some {@code mold_casting} recipe
     * consumes. Water and lava (no cast recipes) bounce off the capability with 0
     * accepted, and once molten iron is buffered a different castable molten is
     * refused too (single-fluid buffer).
     */
    private static void castingMoldRejectsNonCastableFluids(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PFBlocks.CASTING_MOLD.get());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity mold)) {
            helper.fail("casting mold block did not create a CastingMoldBlockEntity");
            return;
        }
        // 26.1: Capabilities.Fluid.BLOCK -> ResourceHandler<FluidResource>; each
        // fill is a committed Transaction (fillCommit returns the mB accepted).
        net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> cap =
            helper.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                helper.absolutePos(pos), null);
        if (cap == null) {
            helper.fail("casting mold exposes no Fluid.BLOCK capability");
            return;
        }
        if (fillCommit(cap, net.minecraft.world.level.material.Fluids.WATER, 1000) != 0) {
            helper.fail("water has no mold_casting recipe and must be refused");
            return;
        }
        if (fillCommit(cap, net.minecraft.world.level.material.Fluids.LAVA, 1000) != 0) {
            helper.fail("lava has no mold_casting recipe and must be refused");
            return;
        }
        // Buffer 45 mB molten iron (a partial tower pull's worth)...
        if (fillCommit(cap, envMoltenIron(), 45) != 45) {
            helper.fail("a partial 45 mB of molten iron should still be accepted (amount is the solidify loop's concern)");
            return;
        }
        // ...then a different castable molten must bounce (single-fluid buffer).
        net.minecraft.world.level.material.Fluid copper = BuiltInRegistries.FLUID.getValue(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "molten_copper"));
        if (fillCommit(cap, copper, 45) != 0) {
            helper.fail("molten copper must be refused while the buffer holds molten iron (single-fluid rule)");
            return;
        }
        helper.succeed();
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    /**
     * Commit a fill against a 26.1 fluid {@code ResourceHandler}, returning the mB
     * accepted. Replaces the 1.21.1 {@code IFluidHandler.fill(stack, EXECUTE)} call.
     */
    private static int fillCommit(
            net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> cap,
            net.minecraft.world.level.material.Fluid fluid,
            int amount) {
        try (net.neoforged.neoforge.transfer.transaction.Transaction tx =
                net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int moved = cap.insert(net.neoforged.neoforge.transfer.fluid.FluidResource.of(fluid), amount, tx);
            tx.commit();
            return moved;
        }
    }

    /**
     * PF's own molten iron - the only molten iron on the 2.0 line (ATO 4.x
     * ships no fluids on 26.1; PF mints unconditionally for iron).
     */
    private static net.minecraft.world.level.material.Fluid envMoltenIron() {
        Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "molten_iron");
        // 26.1: Registry value-by-id lookup is getValue(Identifier) (was get(Identifier)).
        return BuiltInRegistries.FLUID.getValue(id);
    }

    private static ItemStack stampedFroglight(String variant) {
        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        stack.set(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get(),
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant));
        return stack;
    }

    private static void assertVariantSmelts(
            GameTestHelper helper,
            net.minecraft.world.item.crafting.RecipeManager rm,
            ServerLevel level,
            String variant,
            net.minecraft.world.item.Item expectedOutput) {
        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        stack.set(com.flatts.productivefrogs.registry.PFDataComponents.SLIME_VARIANT.get(),
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant));
        net.minecraft.world.item.crafting.SingleRecipeInput recipeInput =
            new net.minecraft.world.item.crafting.SingleRecipeInput(stack);
        java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.SmeltingRecipe>> match =
            rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, recipeInput, level);
        if (match.isEmpty()) {
            helper.fail("no smelting recipe matches configurable_froglight[variant=" + variant + "]");
            return;
        }
        ItemStack output = match.get().value().assemble(recipeInput);
        if (!output.is(expectedOutput)) {
            helper.fail("smelt(configurable_froglight[variant=" + variant + "]) = "
                + BuiltInRegistries.ITEM.getKey(output.getItem())
                + ", expected " + BuiltInRegistries.ITEM.getKey(expectedOutput));
        }
    }
}
