package com.flatts.productivefrogs.client.tint;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * ItemTintSource that reads the {@code productivefrogs:contained_category}
 * data component on an ItemStack and returns the matching category tint.
 *
 * <p>Mirrors vanilla {@code net.minecraft.client.color.item.Potion} (which
 * reads the {@code potion_contents} component and returns the potion's color)
 * — the same vanilla pattern, just keyed on our component.
 *
 * <p>Wired up via item model JSON like:
 * <pre>{@code
 * "tints": [
 *   { "type": "productivefrogs:contained_category", "default": [74, 124, 37] }
 * ]
 * }</pre>
 */
public record ContainedCategoryTint(int defaultColor) implements ItemTintSource {

    public static final MapCodec<ContainedCategoryTint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            ExtraCodecs.RGB_COLOR_CODEC.fieldOf("default").forGetter(ContainedCategoryTint::defaultColor)
        ).apply(instance, ContainedCategoryTint::new)
    );

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity holder) {
        Category category = stack.get(PFDataComponents.CONTAINED_CATEGORY.get());
        if (category != null) {
            return ARGB.opaque(category.tintRgb());
        }
        return ARGB.opaque(this.defaultColor);
    }

    @Override
    public MapCodec<ContainedCategoryTint> type() {
        return MAP_CODEC;
    }
}
