package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.content.fluid.LiquidExperienceFluid;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Liquid Experience pins (#281 Phase 2): the {@code c:experience} tag
 * membership, the 20 mB/point conservation through the bucket spend path
 * (one bucket = exactly 50 points, nothing created or lost), the
 * {@code Capabilities.Fluid.ITEM} exact-volume round-trip any third-party
 * tank/pipe/drain uses, and the no-world-pools guarantee (no block form).
 */
final class LiquidExperienceTests {

    private LiquidExperienceTests() {
    }

    static void register() {
        PFGameTests.test("liquid_experience_is_in_the_c_experience_tag", 20,
            LiquidExperienceTests::liquidExperienceIsInTheCExperienceTag);
        PFGameTests.test("liquid_experience_bucket_grants_exactly_fifty_points", 20,
            LiquidExperienceTests::liquidExperienceBucketGrantsExactlyFiftyPoints);
        PFGameTests.test("liquid_experience_bucket_round_trips_exact_volume", 20,
            LiquidExperienceTests::liquidExperienceBucketRoundTripsExactVolume);
        PFGameTests.test("liquid_experience_has_no_world_form", 20,
            LiquidExperienceTests::liquidExperienceHasNoWorldForm);
    }

    /**
     * Tag interop is THE cross-mod contract (risk register: "test against the
     * tag, not a specific mod"): both forms sit in {@code c:experience}, so any
     * XP tank/drain keying on {@code Tags.Fluids.EXPERIENCE} accepts PF's fluid.
     */
    private static void liquidExperienceIsInTheCExperienceTag(GameTestHelper helper) {
        if (!PFFluids.LIQUID_EXPERIENCE.get().defaultFluidState().is(Tags.Fluids.EXPERIENCE)) {
            helper.fail("liquid_experience (source) is not in c:experience");
            return;
        }
        if (!PFFluids.LIQUID_EXPERIENCE_FLOWING.get().defaultFluidState().is(Tags.Fluids.EXPERIENCE)) {
            helper.fail("liquid_experience_flowing is not in c:experience");
            return;
        }
        helper.succeed();
    }

    /**
     * The bucket spend path at the 20 mB/point standard: drinking one bucket
     * (1000 mB) grants exactly {@link LiquidExperienceFluid#POINTS_PER_BUCKET 50}
     * points - no rounding, no orb scatter - and leaves the empty bucket in hand.
     */
    private static void liquidExperienceBucketGrantsExactlyFiftyPoints(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bucket = new ItemStack(PFItems.LIQUID_EXPERIENCE_BUCKET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);

        int before = player.totalExperience;
        bucket.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        int gained = player.totalExperience - before;
        if (gained != LiquidExperienceFluid.POINTS_PER_BUCKET) {
            helper.fail("one bucket must grant exactly " + LiquidExperienceFluid.POINTS_PER_BUCKET
                + " XP points (1000 mB at 20 mB/point), granted " + gained);
            return;
        }
        ItemStack after = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!after.is(Items.BUCKET)) {
            helper.fail("drinking must leave the empty bucket in hand, left " + after);
            return;
        }
        helper.succeed();
    }

    /**
     * The {@code Capabilities.Fluid.ITEM} round-trip every tank/pipe mod drives:
     * a full bucket drains exactly 1000 mB of the tagged fluid, the drained item
     * is the empty bucket, refilling the same access with 1000 mB restores the
     * Liquid Experience bucket, and a partial (non-bucket-volume) fill is
     * refused - the exact-volume rule that guarantees no drift at the 20 mB
     * boundary.
     */
    private static void liquidExperienceBucketRoundTripsExactVolume(GameTestHelper helper) {
        // The access must allow the ITEM to change (full bucket <-> empty bucket),
        // which ItemAccess.forStack explicitly never does - use a player slot, the
        // same mutable access a tank's bucket slot or a player interaction drives.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(PFItems.LIQUID_EXPERIENCE_BUCKET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        ItemAccess access = ItemAccess.forPlayerInteraction(player, InteractionHand.MAIN_HAND);
        ResourceHandler<FluidResource> handler = stack.getCapability(Capabilities.Fluid.ITEM, access);
        if (handler == null) {
            helper.fail("liquid_experience_bucket exposes no Fluid.ITEM capability");
            return;
        }
        FluidResource contents = handler.getResource(0);
        if (contents.getFluid() != PFFluids.LIQUID_EXPERIENCE.get()) {
            helper.fail("bucket handler reports " + contents + ", expected liquid_experience");
            return;
        }
        if (handler.getAmountAsInt(0) != FluidType.BUCKET_VOLUME) {
            helper.fail("full bucket must report " + FluidType.BUCKET_VOLUME + " mB, reports "
                + handler.getAmountAsInt(0));
            return;
        }

        // Drain the full bucket.
        int drained;
        try (Transaction tx = Transaction.openRoot()) {
            drained = handler.extract(contents, FluidType.BUCKET_VOLUME, tx);
            tx.commit();
        }
        if (drained != FluidType.BUCKET_VOLUME) {
            helper.fail("drain must yield exactly 1000 mB, yielded " + drained);
            return;
        }
        if (!access.getResource().is(Items.BUCKET)) {
            helper.fail("drained access must hold the empty bucket, holds " + access.getResource());
            return;
        }

        // A partial fill is refused (buckets are exact-volume only - no drift).
        int partial;
        try (Transaction tx = Transaction.openRoot()) {
            partial = handler.insert(FluidResource.of(PFFluids.LIQUID_EXPERIENCE.get()), 500, tx);
            tx.commit();
        }
        if (partial != 0) {
            helper.fail("a 500 mB partial fill must be refused, accepted " + partial);
            return;
        }

        // Refill the full bucket - the exact round-trip any tank drain performs.
        int filled;
        try (Transaction tx = Transaction.openRoot()) {
            filled = handler.insert(FluidResource.of(PFFluids.LIQUID_EXPERIENCE.get()),
                FluidType.BUCKET_VOLUME, tx);
            tx.commit();
        }
        if (filled != FluidType.BUCKET_VOLUME) {
            helper.fail("refill must accept exactly 1000 mB, accepted " + filled);
            return;
        }
        if (access.getResource().getItem() != PFItems.LIQUID_EXPERIENCE_BUCKET.get()) {
            helper.fail("refilled access must hold the Liquid Experience bucket again, holds "
                + access.getResource());
            return;
        }
        helper.succeed();
    }

    /**
     * "No world pools": Liquid Experience declares no block form, so nothing -
     * not even a mod force-placing the fluid state - can create a world pool
     * of it (the state resolves to air), and the fluid's minted bucket is ours.
     */
    private static void liquidExperienceHasNoWorldForm(GameTestHelper helper) {
        if (!PFFluids.LIQUID_EXPERIENCE.get().defaultFluidState().createLegacyBlock().isAir()) {
            helper.fail("liquid_experience must have no block form (world placement is a design non-goal)");
            return;
        }
        if (PFFluids.LIQUID_EXPERIENCE.get().getBucket() != PFItems.LIQUID_EXPERIENCE_BUCKET.get()) {
            helper.fail("the fluid's bucket must be the Liquid Experience bucket (tank -> bucket drains mint it)");
            return;
        }
        helper.succeed();
    }
}
