package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.registry.PFDataComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The single Slime Milk bucket's milk-layer tint (26.1 R-1). The variant now rides
 * the {@code SLIME_VARIANT} component on the bucket {@code ItemStack} (not a baked
 * model field), so this reads it off the stack and tints by that variant's registry
 * {@code primary_color}, falling back to a milky off-white when unresolved so the
 * item stays visible. Mirrors {@link SynthesizedItemTint} (the Mimic bucket's
 * component-reading tint).
 */
public record VariantColorTint() implements ItemTintSource {

    public static final VariantColorTint INSTANCE = new VariantColorTint();
    public static final MapCodec<VariantColorTint> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Identifier variant = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variant == null) {
            return Tints.opaque(0xF0F0E0);
        }
        int color = Tints.variantColor(level, variant);
        return color != -1 ? color : Tints.opaque(0xF0F0E0);
    }

    @Override
    public MapCodec<VariantColorTint> type() {
        return CODEC;
    }
}
