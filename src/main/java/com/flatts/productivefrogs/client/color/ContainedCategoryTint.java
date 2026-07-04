package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Tints a layer from the {@code productivefrogs:contained_category} data
 * component. Drives the Frog Egg bottle liquid (base colour) and the
 * category frog / tadpole spawn eggs (base colour, plus the spotted overlay
 * when {@code spot} is true). Returns {@code -1} (no tint) when the stack
 * carries no category, matching the legacy Frog Egg handler.
 *
 * @param spot when true, returns the darkened (spotted) secondary shade for
 *             the spawn-egg overlay layer; when false, the base category colour.
 */
public record ContainedCategoryTint(boolean spot) implements ItemTintSource {

    public static final MapCodec<ContainedCategoryTint> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
            Codec.BOOL.optionalFieldOf("spot", false).forGetter(ContainedCategoryTint::spot)
        ).apply(i, ContainedCategoryTint::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Category cat = stack.get(PFDataComponents.CONTAINED_CATEGORY.get());
        if (cat == null) {
            return -1;
        }
        return Tints.opaque(spot ? Tints.darker(cat.tintRgb()) : cat.tintRgb());
    }

    @Override
    public MapCodec<ContainedCategoryTint> type() {
        return CODEC;
    }
}
