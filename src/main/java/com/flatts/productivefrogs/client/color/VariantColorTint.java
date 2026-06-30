package com.flatts.productivefrogs.client.color;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Per-variant Slime Milk bucket tint (v1.8). Each {@code <variant>_slime_milk_bucket}
 * is one item per variant, so the variant is baked into the model JSON's
 * {@code variant} field rather than read from a component. Tints the milk layer
 * by that variant's registry {@code primary_color}, falling back to a milky
 * off-white before the registry is available so the item stays visible.
 *
 * @param variant the {@code slime_variant} registry id this milk bucket carries.
 */
public record VariantColorTint(Identifier variant) implements ItemTintSource {

    public static final MapCodec<VariantColorTint> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
            Identifier.CODEC.fieldOf("variant").forGetter(VariantColorTint::variant)
        ).apply(i, VariantColorTint::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        int color = Tints.variantColor(level, variant);
        return color != -1 ? color : Tints.opaque(0xF0F0E0);
    }

    @Override
    public MapCodec<VariantColorTint> type() {
        return CODEC;
    }
}
