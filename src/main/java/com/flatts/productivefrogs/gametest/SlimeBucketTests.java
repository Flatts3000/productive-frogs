package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Slime Bucket capture/release regression pins: category and variant survive the
 * bucket NBT round-trip, capture is gated to an empty bucket on a size-1 slime
 * (not a water bucket), and release - by hand or by dispenser - never leaks water
 * and always yields a size-1 slime.
 */
final class SlimeBucketTests {

    private SlimeBucketTests() {
    }

    static void register() {
        PFGameTests.test("slime_bucket_round_trip_preserves_category", 100, SlimeBucketTests::slimeBucketRoundTripPreservesCategory);
        PFGameTests.test("empty_bucket_captures_slime_water_bucket_does_not", 100, SlimeBucketTests::emptyBucketCapturesSlimeWaterBucketDoesNot);
        PFGameTests.test("slime_bucket_round_trip_preserves_variant", 100, SlimeBucketTests::slimeBucketRoundTripPreservesVariant);
        PFGameTests.test("slime_bucket_release_has_no_water_and_is_size_one", 40, SlimeBucketTests::slimeBucketReleaseHasNoWaterAndIsSizeOne);
        PFGameTests.test("slime_bucket_dispenser_releases_slime_no_water", 60, SlimeBucketTests::slimeBucketDispenserReleasesSlimeNoWater);
    }

    /**
     * Exercise the full bucket pickup->release contract: spawn a CAVE slime,
     * write its state into a bucket via {@code saveToBucketTag}, then load
     * that bucket NBT into a fresh ResourceSlime via {@code loadFromBucketTag}.
     * Verifies (1) the bucket carries the category, (2) the released slime
     * decodes it correctly, and (3) the released slime is flagged
     * {@code fromBucket} so it doesn't despawn on chunk reload.
     *
     * <p>Cross-bucket detail: ResourceTadpoleBucketItem.readCategory works for
     * the slime bucket too because both bucket types write the same
     * {@code BUCKET_ENTITY_DATA -> "Category" string} shape - same reader is
     * referenced by both bucket item models via the renamed
     * {@code BucketedCategoryTint} ItemTintSource.
     */
    private static void slimeBucketRoundTripPreservesCategory(GameTestHelper helper) {
        Category cat = Category.CAVE;
        BlockPos pos = new BlockPos(2, 2, 2);

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        source.setSize(1, true);
        source.setCategory(cat);

        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);

        // Step 1: NBT round-trip via the tint-source reader.
        Category readBack = ResourceTadpoleBucketItem.readCategory(bucket);
        if (readBack != cat) {
            helper.fail("slime bucket round-trip lost category: wrote " + cat + ", read " + readBack);
        }

        // Step 2: spawn a fresh slime and exercise loadFromBucketTag - same
        // path vanilla MobBucketItem walks on release.
        ResourceSlime released = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos.east());
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("bucket's BUCKET_ENTITY_DATA component is unexpectedly null after saveToBucketTag");
            return;
        }
        released.loadFromBucketTag(data.copyTag());

        if (released.getCategory() != cat) {
            helper.fail("released slime has category " + released.getCategory() + ", expected " + cat);
        }
        if (!released.fromBucket()) {
            helper.fail("released slime must be flagged fromBucket so it survives chunk reload");
        }
        helper.succeed();
    }

    /**
     * Bug fix (known_issues): a size-1 Resource Slime is captured with an
     * <b>empty</b> bucket, not a water bucket. Right-clicking with
     * {@code Items.BUCKET} fills a Slime Bucket and removes the slime; a water
     * bucket must NOT capture it (falls through to vanilla, water bucket
     * unconsumed). Pins {@code ResourceSlime.tryEmptyBucketCapture} against a
     * regression to vanilla {@code Bucketable.bucketMobPickup}, which keys on
     * the water bucket (the fish/axolotl/tadpole convention).
     */
    private static void emptyBucketCapturesSlimeWaterBucketDoesNot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        net.minecraft.world.entity.player.Player player =
            helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

        // 1. Water bucket must NOT capture (the old, wrong behavior).
        ResourceSlime water = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        water.setSize(1, true);
        water.setCategory(Category.CAVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
        net.minecraft.world.InteractionResult waterResult =
            water.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (waterResult.consumesAction()) {
            helper.fail("water bucket must NOT be handled as a capture, got " + waterResult);
            return;
        }
        if (!player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                .is(net.minecraft.world.item.Items.WATER_BUCKET)) {
            helper.fail("water bucket must NOT capture a Resource Slime (it should remain a water bucket)");
            return;
        }
        if (!water.isAlive()) {
            helper.fail("water bucket must not consume/discard the slime");
            return;
        }
        water.discard();

        // 2. Empty bucket must NOT capture a size > 1 slime (size gate): larger
        //    slimes split and the player buckets the offspring.
        ResourceSlime big = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        big.setSize(2, true);
        big.setCategory(Category.CAVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(net.minecraft.world.item.Items.BUCKET));
        net.minecraft.world.InteractionResult bigResult =
            big.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (bigResult.consumesAction()) {
            helper.fail("empty bucket must NOT capture a size-2 slime, got " + bigResult);
            return;
        }
        if (!player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND)
                .is(net.minecraft.world.item.Items.BUCKET)) {
            helper.fail("empty bucket must remain empty when used on a size-2 slime");
            return;
        }
        big.discard();

        // 3. Empty bucket captures a size-1 slime: slime gone, player holds a slime bucket.
        ResourceSlime empty = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        empty.setSize(1, true);
        empty.setCategory(Category.CAVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
            new ItemStack(net.minecraft.world.item.Items.BUCKET));
        net.minecraft.world.InteractionResult result =
            empty.mobInteract(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!result.consumesAction()) {
            helper.fail("empty bucket must capture a size-1 Resource Slime, got " + result);
            return;
        }
        ItemStack held = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!held.is(PFItems.SLIME_BUCKET.get())) {
            helper.fail("after capture the player must hold a slime bucket, got "
                + BuiltInRegistries.ITEM.getKey(held.getItem()));
            return;
        }
        if (empty.isAlive()) {
            helper.fail("captured slime must be discarded");
            return;
        }
        helper.succeed();
    }

    /**
     * Bucket a variant-stamped Resource Slime, then assert the
     * {@code BUCKET_ENTITY_DATA} payload carries the {@code Variant} NBT
     * (read via {@code ResourceTadpoleBucketItem.readVariant}) AND that
     * releasing the bucket restores the variant on the spawned slime.
     *
     * <p>This pins the precondition for the variant-aware Slime Bucket tint:
     * if the bucket doesn't carry the Variant id after capture, the
     * {@code BucketedCategoryTint} resolution-order would skip the
     * variant lookup and fall back to the broader category colour.
     */
    private static void slimeBucketRoundTripPreservesVariant(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        Identifier variantId =
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper");

        ResourceSlime source = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos);
        source.setSize(1, true);
        source.setVariant(variantId);

        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        source.saveToBucketTag(bucket);

        Identifier readBack =
            com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem.readVariant(bucket);
        if (readBack == null || !readBack.equals(variantId)) {
            helper.fail("slime bucket round-trip lost variant: wrote " + variantId
                + ", read " + readBack);
            return;
        }

        ResourceSlime released = helper.spawn(PFEntities.RESOURCE_SLIME.get(), pos.east());
        net.minecraft.world.item.component.CustomData data =
            bucket.get(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            helper.fail("bucket's BUCKET_ENTITY_DATA is unexpectedly null after saveToBucketTag");
            return;
        }
        released.loadFromBucketTag(data.copyTag());

        if (!variantId.equals(released.getVariantId())) {
            helper.fail("released slime variant was " + released.getVariantId()
                + ", expected " + variantId);
            return;
        }
        helper.succeed();
    }

    /**
     * Slime Bucket release: emptying a Slime Bucket must NOT place a water source
     * (a captured slime is a land mob, not a fish), and the released slime is always
     * size 1 (capture is gated to size 1; MobBucketItem#spawn would otherwise let
     * Slime#finalizeSpawn randomize the size to 1/2/4).
     */
    private static void slimeBucketReleaseHasNoWaterAndIsSizeOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockPos abs = helper.absolutePos(pos);
        helper.setBlock(pos, Blocks.AIR);

        // 1) Emptying the bucket places no fluid. Drives the same 5-arg overload
        // that BucketItem#use calls on the player right-click path - the 4-arg
        // method in vanilla just delegates here, so testing only the 4-arg signature
        // is a false positive that misses water leaking via use().
        var bucketItem = (com.flatts.productivefrogs.content.item.SlimeBucketItem) PFItems.SLIME_BUCKET.get();
        ItemStack heldBucket = new ItemStack(bucketItem);
        bucketItem.emptyContents(null, level, abs, null, heldBucket);
        if (!level.getFluidState(abs).isEmpty()) {
            helper.fail("Slime Bucket release placed a fluid at " + pos + " (expected none)");
            return;
        }
        if (!level.getBlockState(abs).isAir()) {
            helper.fail("Slime Bucket release changed the block to "
                + level.getBlockState(abs) + " (expected air)");
            return;
        }

        // 2) loadFromBucketTag forces size 1 even after a larger finalizeSpawn size.
        var slime = PFEntities.RESOURCE_SLIME.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (slime == null) {
            helper.fail("could not create ResourceSlime");
            return;
        }
        slime.setSize(4, true);
        slime.loadFromBucketTag(new net.minecraft.nbt.CompoundTag());
        int size = slime.getSize();
        slime.discard();
        if (size != 1) {
            helper.fail("released slime size expected 1, got " + size);
            return;
        }
        helper.succeed();
    }

    /**
     * A dispenser loaded with a Slime Bucket releases the slime (size 1, no water)
     * into the block it faces, rather than just ejecting the bucket. Powers a
     * dispenser facing up with a redstone block and checks the air block above.
     */
    private static void slimeBucketDispenserReleasesSlimeNoWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos disp = new BlockPos(2, 2, 2);
        BlockPos front = disp.above();
        helper.setBlock(disp, net.minecraft.world.level.block.Blocks.DISPENSER.defaultBlockState()
            .setValue(net.minecraft.world.level.block.DispenserBlock.FACING, net.minecraft.core.Direction.UP));
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(disp))
                instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity dbe)) {
            helper.fail("dispenser BE missing");
            return;
        }
        dbe.setItem(0, PFItems.variantSlimeBucket(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron"),
            com.flatts.productivefrogs.data.Category.CAVE));
        // Rising redstone edge triggers the dispense (fires ~4 ticks later).
        helper.setBlock(disp.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(15, () -> {
            if (!level.getFluidState(helper.absolutePos(front)).isEmpty()) {
                helper.fail("dispenser release placed water in front of the dispenser");
                return;
            }
            var slimes = helper.getEntities(PFEntities.RESOURCE_SLIME.get());
            if (slimes.size() != 1) {
                helper.fail("expected 1 slime dispensed, got " + slimes.size());
                return;
            }
            if (slimes.get(0).getSize() != 1) {
                helper.fail("dispensed slime not size 1, got " + slimes.get(0).getSize());
                return;
            }
            helper.succeed();
        });
    }
}
