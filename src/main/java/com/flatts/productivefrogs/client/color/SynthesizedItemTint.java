package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.client.SynthesizedTint;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Equivalence-lane (#253) tint for the Mimic Slime Bucket and Mimic Milk
 * Bucket. The silhouette / milk layer wears the carried item's sprite-average
 * colour, read off the top-level {@code SYNTHESIZED_ITEM} component and
 * resolved through {@link SynthesizedTint}. Falls back to a neutral prismatic
 * grey when un-stamped so the silhouette stays visible.
 */
public record SynthesizedItemTint() implements ItemTintSource {

    public static final SynthesizedItemTint INSTANCE = new SynthesizedItemTint();
    public static final MapCodec<SynthesizedItemTint> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Identifier itemId = stack.get(PFDataComponents.SYNTHESIZED_ITEM.get());
        if (itemId == null) {
            return Tints.opaque(0xC8C8D2);
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        return item == null ? Tints.opaque(0xC8C8D2) : SynthesizedTint.colorFor(item);
    }

    @Override
    public MapCodec<SynthesizedItemTint> type() {
        return CODEC;
    }
}
