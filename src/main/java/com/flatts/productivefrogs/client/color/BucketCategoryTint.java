package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Resource Tadpole Bucket tint. Reads the captured tadpole's {@link Category}
 * from {@code BUCKET_ENTITY_DATA}; an empty bucket (no captured tadpole)
 * defaults to vanilla tadpole brown so the silhouette stays visible in the
 * creative tab. Bound to the tadpole-silhouette layer in the item model.
 */
public record BucketCategoryTint() implements ItemTintSource {

    public static final BucketCategoryTint INSTANCE = new BucketCategoryTint();
    public static final MapCodec<BucketCategoryTint> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Category cat = ResourceTadpoleBucketItem.readCategory(stack);
        return cat == null ? Tints.opaque(0x6B4530) : Tints.opaque(cat.tintRgb());
    }

    @Override
    public MapCodec<BucketCategoryTint> type() {
        return CODEC;
    }
}
