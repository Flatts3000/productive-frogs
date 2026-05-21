package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.registry.PFFluidTypes;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Slime Milker — V1 production keystone per {@code docs/farming.md}.
 *
 * <p>Single appliance block, no power, no GUI, no internal storage. The player
 * right-clicks the block with a {@link com.flatts.productivefrogs.registry.PFItems#SLIME_BUCKET}
 * containing a captured Resource Slime; the block consumes the slime and
 * swaps the held bucket for a typed Slime Milk bucket matching the slime's
 * variant.
 *
 * <p>Variant resolution: reads the {@code Variant} string out of the bucket's
 * {@code BUCKET_ENTITY_DATA} NBT (written by {@link com.flatts.productivefrogs.content.entity.ResourceSlime#saveToBucketTag}),
 * extracts the path portion (e.g., {@code "productivefrogs:iron"} → {@code "iron"}),
 * and looks up the matching variant in {@link PFFluidTypes#VARIANTS}. If no
 * variant is present or the variant isn't a known milk variant, the
 * interaction is a no-op (the player keeps the slime bucket) — better UX
 * than producing an arbitrary "default" milk.
 *
 * <p>Future polish tracked in {@code docs/backlog.md}: press animation,
 * facing-based block model (top input port + side output spout), tighter
 * SoundEvents.SLIME_SQUISH-style audio.
 */
public class SlimeMilkerBlock extends Block {

    public SlimeMilkerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        if (!stack.is(PFItems.SLIME_BUCKET.get())) {
            // Not a slime bucket — fall through so the player can try the
            // no-item interaction (e.g. pick-block) or other tools.
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        String variant = readBucketVariant(stack);
        if (variant == null || !PFFluidTypes.VARIANTS.contains(variant)) {
            // Bucket has no variant (e.g. a category-only slime, which can't
            // map to a single milk fluid) or the variant isn't one we ship —
            // fail closed. The player keeps the bucket so they can try a
            // different slime; no item destruction.
            return InteractionResult.CONSUME;
        }

        if (!level.isClientSide()) {
            BucketItem outputBucket = PFItems.MILK_BUCKETS.get(variant).get();
            ItemStack milkStack = new ItemStack(outputBucket);
            // Slime Bucket is stacksTo(1), so the held stack count is always
            // 1 — replacing it directly is the simplest equivalent of the
            // vanilla "consume one, produce one" handheld swap.
            player.setItemInHand(hand, milkStack);
            level.playSound(
                null,
                pos,
                SoundEvents.SLIME_BLOCK_PLACE,
                SoundSource.BLOCKS,
                0.8F,
                1.2F + level.getRandom().nextFloat() * 0.2F
            );
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Pull the variant path (e.g. {@code "iron"}) out of a Slime Bucket's
     * {@code BUCKET_ENTITY_DATA} NBT. Returns null when the bucket is empty
     * (no entity data), the entity didn't store a Variant tag, or the stored
     * Variant id is malformed.
     */
    @Nullable
    static String readBucketVariant(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            return null;
        }
        String raw = data.copyTag().getString("Variant").orElse(null);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(raw);
        return id == null ? null : id.getPath();
    }
}
