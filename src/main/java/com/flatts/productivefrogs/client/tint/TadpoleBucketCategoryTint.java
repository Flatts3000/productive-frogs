package com.flatts.productivefrogs.client.tint;

import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/**
 * ItemTintSource for the Resource Tadpole Bucket — reads the
 * {@code bucket_entity_data} component's {@code Category} string and returns
 * the matching category tint.
 *
 * <p>Separate from {@link ContainedCategoryTint} because the data lives on a
 * different component for buckets (vanilla puts entity bucket data on
 * {@code BUCKET_ENTITY_DATA}, not our custom component).
 */
@OnlyIn(Dist.CLIENT)
public record TadpoleBucketCategoryTint(int defaultColor) implements ItemTintSource {

    public static final MapCodec<TadpoleBucketCategoryTint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            ExtraCodecs.RGB_COLOR_CODEC.fieldOf("default").forGetter(TadpoleBucketCategoryTint::defaultColor)
        ).apply(instance, TadpoleBucketCategoryTint::new)
    );

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity holder) {
        Category category = ResourceTadpoleBucketItem.readCategory(stack);
        if (category != null) {
            return ARGB.opaque(category.tintRgb());
        }
        return ARGB.opaque(this.defaultColor);
    }

    @Override
    public MapCodec<TadpoleBucketCategoryTint> type() {
        return MAP_CODEC;
    }
}
