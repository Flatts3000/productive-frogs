package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * In-world GameTests for the Equivalence (EE) lane (#253): the Alembic / Distiller
 * RF machines and the Midas egg -> tadpole -> frog identity chain. Ported from the
 * MC 1.21.1 {@code PFGameTests} annotation scaffold to the MC 26.1 registry scaffold
 * ({@link PFGameTests#test}).
 *
 * <p>The whole lane defaults OFF; each machine test sets the
 * {@code PFConfig.equivalenceEnabledOverride} test hook (unchanged across the port).
 *
 * <p>CAPABILITY FLAG (26.1): the four machine tests reach the BE energy/item buffers
 * through the {@code energyStorage()} / {@code items()} BE accessors (not through
 * {@code Capabilities.Energy.BLOCK} / {@code Capabilities.Item.BLOCK} lookups). The
 * {@code fillEnergy} helper is typed against the legacy
 * {@code net.neoforged.neoforge.energy.EnergyStorage}. These surfaces are kept
 * verbatim from 1.21.1 pending confirmation of the 26.1 BE accessor return types
 * (EnergyHandler / ResourceHandler) - see the migration notes.
 */
final class EquivalenceLaneTests {

    private EquivalenceLaneTests() {
    }

    static void register() {
        PFGameTests.test("alembic_synthesizes_off_roster_item_into_mimic_slime_bucket", 200,
            EquivalenceLaneTests::alembicSynthesizesOffRosterItemIntoMimicSlimeBucket);
        PFGameTests.test("alembic_refuses_component_bearing_item", 200,
            EquivalenceLaneTests::alembicRefusesComponentBearingItem);
        PFGameTests.test("alembic_refuses_on_roster_item", 200,
            EquivalenceLaneTests::alembicRefusesOnRosterItem);
        PFGameTests.test("distiller_extracts_item_from_prismatic_froglight", 200,
            EquivalenceLaneTests::distillerExtractsItemFromPrismaticFroglight);
        PFGameTests.test("midas_egg_hatches_midas_tadpoles_that_mature_to_midas_frog", 100,
            EquivalenceLaneTests::midasEggHatchesMidasTadpolesThatMatureToMidasFrog);
    }

    /**
     * Fill an Alembic/Distiller RF buffer to capacity (receiveEnergy is capped per call).
     *
     * <p>CAPABILITY FLAG (26.1): typed against the legacy
     * {@code net.neoforged.neoforge.energy.EnergyStorage}; kept verbatim pending the
     * 26.1 {@code EnergyHandler} mapping.
     */
    private static void fillEnergy(net.neoforged.neoforge.energy.EnergyStorage energy) {
        for (int i = 0; i < 200; i++) {
            energy.receiveEnergy(Integer.MAX_VALUE, false);
        }
    }

    private static void alembicSynthesizesOffRosterItemIntoMimicSlimeBucket(GameTestHelper helper) {
        // EE lane defaults OFF; force it on so the machine ticks (no test asserts it off).
        com.flatts.productivefrogs.PFConfig.equivalenceEnabledOverride = Boolean.TRUE;
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.ALEMBIC.get());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        if (!(level.getBlockEntity(abs)
                instanceof com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity alembic)) {
            helper.fail("expected AlembicBlockEntity");
            return;
        }
        fillEnergy(alembic.energyStorage());
        alembic.items().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.BUCKET_SLOT,
            new ItemStack(Items.BUCKET));
        // Flint: off-roster (no slime variant), plain (no component patch).
        alembic.items().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.ITEM_SLOT,
            new ItemStack(Items.FLINT));
        for (int i = 0; i < com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.SYNTH_TIME; i++) {
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.serverTick(
                level, abs, level.getBlockState(abs), alembic);
        }
        ItemStack out = alembic.items().getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.OUTPUT_SLOT);
        if (!(out.getItem() instanceof com.flatts.productivefrogs.content.item.MimicSlimeBucketItem)
                || !Identifier.withDefaultNamespace("flint").equals(
                    out.get(com.flatts.productivefrogs.registry.PFDataComponents.SYNTHESIZED_ITEM.get()))) {
            helper.fail("expected a flint Mimic Slime Bucket, got "
                + (out.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(out.getItem())));
            return;
        }
        helper.succeed();
    }

    private static void alembicRefusesComponentBearingItem(GameTestHelper helper) {
        com.flatts.productivefrogs.PFConfig.equivalenceEnabledOverride = Boolean.TRUE;
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.ALEMBIC.get());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        if (!(level.getBlockEntity(abs)
                instanceof com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity alembic)) {
            helper.fail("expected AlembicBlockEntity");
            return;
        }
        fillEnergy(alembic.energyStorage());
        alembic.items().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.BUCKET_SLOT,
            new ItemStack(Items.BUCKET));
        // A renamed flint carries a CUSTOM_NAME component patch -> the type-only
        // lane must refuse it (the guidebook-laundering guard).
        ItemStack named = new ItemStack(Items.FLINT);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            net.minecraft.network.chat.Component.literal("Fancy Flint"));
        alembic.items().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.ITEM_SLOT, named);
        for (int i = 0; i < com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.SYNTH_TIME; i++) {
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.serverTick(
                level, abs, level.getBlockState(abs), alembic);
        }
        ItemStack out = alembic.items().getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.OUTPUT_SLOT);
        if (!out.isEmpty()) {
            helper.fail("component-bearing item should not synthesize, but got "
                + BuiltInRegistries.ITEM.getKey(out.getItem()));
            return;
        }
        helper.succeed();
    }

    private static void alembicRefusesOnRosterItem(GameTestHelper helper) {
        com.flatts.productivefrogs.PFConfig.equivalenceEnabledOverride = Boolean.TRUE;
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.ALEMBIC.get());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        if (!(level.getBlockEntity(abs)
                instanceof com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity alembic)) {
            helper.fail("expected AlembicBlockEntity");
            return;
        }
        fillEnergy(alembic.energyStorage());
        alembic.items().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.BUCKET_SLOT,
            new ItemStack(Items.BUCKET));
        // Iron ingot primes the iron variant (findByPrimer != null) -> refused;
        // it has an authored slime lane already.
        alembic.items().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.ITEM_SLOT,
            new ItemStack(Items.IRON_INGOT));
        for (int i = 0; i < com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.SYNTH_TIME; i++) {
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.serverTick(
                level, abs, level.getBlockState(abs), alembic);
        }
        ItemStack out = alembic.items().getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity.OUTPUT_SLOT);
        if (!out.isEmpty()) {
            helper.fail("on-roster iron ingot should not synthesize, but got "
                + BuiltInRegistries.ITEM.getKey(out.getItem()));
            return;
        }
        helper.succeed();
    }

    private static void distillerExtractsItemFromPrismaticFroglight(GameTestHelper helper) {
        com.flatts.productivefrogs.PFConfig.equivalenceEnabledOverride = Boolean.TRUE;
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, PFBlocks.DISTILLER.get());
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);
        if (!(level.getBlockEntity(abs)
                instanceof com.flatts.productivefrogs.content.block.entity.DistillerBlockEntity distiller)) {
            helper.fail("expected DistillerBlockEntity");
            return;
        }
        fillEnergy(distiller.energyStorage());
        ItemStack froglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        froglight.set(com.flatts.productivefrogs.registry.PFDataComponents.SYNTHESIZED_ITEM.get(),
            Identifier.withDefaultNamespace("flint"));
        distiller.items().setStackInSlot(
            com.flatts.productivefrogs.content.block.entity.DistillerBlockEntity.INPUT_SLOT, froglight);
        for (int i = 0; i < com.flatts.productivefrogs.content.block.entity.DistillerBlockEntity.DISTILL_TIME; i++) {
            com.flatts.productivefrogs.content.block.entity.DistillerBlockEntity.serverTick(
                level, abs, level.getBlockState(abs), distiller);
        }
        ItemStack out = distiller.items().getStackInSlot(
            com.flatts.productivefrogs.content.block.entity.DistillerBlockEntity.OUTPUT_SLOT);
        if (!out.is(Items.FLINT)) {
            helper.fail("expected the Distiller to render a flint, got "
                + (out.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(out.getItem())));
            return;
        }
        helper.succeed();
    }

    /**
     * The Midas identity must survive the whole egg -> tadpole -> frog chain (#253).
     * A Midas Frog Egg block stamps the midas marker in onPlace; hatching it must
     * yield Midas tadpoles, and maturing one must yield a Midas frog. This is the
     * regression that the "Void leak" bugs kept reopening - lock it.
     */
    private static void midasEggHatchesMidasTadpolesThatMatureToMidasFrog(GameTestHelper helper) {
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);

        PrimedFrogEggBlock eggBlock = (PrimedFrogEggBlock) PFBlocks.MIDAS_FROG_EGG.get();
        helper.setBlock(eggPos, eggBlock);

        ServerLevel level = helper.getLevel();
        BlockPos absEggPos = helper.absolutePos(eggPos);

        // onPlace stamps the midas marker on the BE.
        if (!(level.getBlockEntity(absEggPos)
                instanceof com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity eggBe)
                || !eggBe.isMidas()) {
            helper.fail("placed Midas Frog Egg must stamp midas on its BlockEntity");
            return;
        }

        // Hatch directly (bypass the scheduled delay), then every tadpole must be Midas.
        eggBlock.tick(level.getBlockState(absEggPos), level, absEggPos, level.getRandom());
        List<ResourceTadpole> tadpoles = helper.getEntities(PFEntities.RESOURCE_TADPOLE.get());
        if (tadpoles.isEmpty()) {
            helper.fail("expected 1-3 Midas tadpoles after hatch, got 0");
            return;
        }
        for (ResourceTadpole tadpole : tadpoles) {
            if (!tadpole.isMidas()) {
                helper.fail("hatched tadpole is not Midas");
                return;
            }
        }

        // Maturing a Midas tadpole yields a Midas frog.
        tadpoles.get(0).ageUp();
        helper.succeedWhen(() -> {
            List<ResourceFrog> midas = helper.getEntities(PFEntities.RESOURCE_FROG.get()).stream()
                .filter(ResourceFrog::isMidas).toList();
            if (midas.isEmpty()) {
                helper.fail("matured Midas tadpole did not produce a Midas frog");
            }
        });
    }
}
