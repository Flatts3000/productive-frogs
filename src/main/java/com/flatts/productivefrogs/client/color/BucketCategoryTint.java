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
        com.flatts.productivefrogs.data.FrogKind kind = ResourceTadpoleBucketItem.readKind(stack);
        return kind == null
            ? Tints.opaque(0x6B4530)
            : Tints.opaque(contrastAgainstWater(kind.tintArgb() & 0xFFFFFF));
    }

    /**
     * The tadpole silhouette sits in the vanilla teal water of the bucket art. A
     * species whose colour shares that water's teal/cyan hue (Tide, Geode) tints to
     * roughly the water colour and vanishes into it. Darken only those clashing hues
     * one step so they separate from the water by value; every other species keeps
     * its full brightness. Hue-gated so it touches only the offending colours.
     */
    private static int contrastAgainstWater(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max == min) {
            return rgb; // greyscale - no hue to clash
        }
        float d = max - min;
        float hue;
        if (max == r) {
            hue = ((g - b) / d) % 6.0F;
        } else if (max == g) {
            hue = (b - r) / d + 2.0F;
        } else {
            hue = (r - g) / d + 4.0F;
        }
        hue *= 60.0F;
        if (hue < 0.0F) {
            hue += 360.0F;
        }
        return (hue >= 150.0F && hue <= 205.0F) ? Tints.darker(rgb) : rgb;
    }

    @Override
    public MapCodec<BucketCategoryTint> type() {
        return CODEC;
    }
}
