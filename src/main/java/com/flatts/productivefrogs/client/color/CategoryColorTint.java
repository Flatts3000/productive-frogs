package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.data.Category;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Constant per-{@link Category} tint for the fixed species spawn eggs (Cave Frog,
 * Void Slime, etc.). 26.1 dropped vanilla's tintable {@code template_spawn_egg}, so
 * PF's eggs render the greyscale creature-egg base ({@code item/slime_spawn_egg} /
 * {@code frog_spawn_egg} / {@code tadpole_spawn_egg}) and recolour it here.
 *
 * <p>Reads {@link Category#tintArgb()} at render time so the egg colour tracks the
 * single source of truth in {@link Category} - no ARGB duplicated into JSON. The
 * species is a fixed field in the item model's {@code tints} entry
 * ({@code {"type":"productivefrogs:category_color","category":"cave"}}), since a
 * fixed spawn egg carries no variant component to read (unlike the variant-driven
 * {@link ResourceSlimeEggTint}).
 */
public record CategoryColorTint(Category category) implements ItemTintSource {

    public static final MapCodec<CategoryColorTint> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
            Category.CODEC.fieldOf("category").forGetter(CategoryColorTint::category)
        ).apply(i, CategoryColorTint::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        return category.tintArgb();
    }

    @Override
    public MapCodec<CategoryColorTint> type() {
        return CODEC;
    }
}
