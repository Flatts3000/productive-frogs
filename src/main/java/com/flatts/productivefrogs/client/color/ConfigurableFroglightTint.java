package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.client.SynthesizedTint;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Configurable Froglight item tint. A synthesized Froglight (#253) carries an
 * arbitrary {@code SYNTHESIZED_ITEM} id and samples that item's sprite at
 * runtime; otherwise the tint is the {@code SLIME_VARIANT}'s registry
 * {@code primary_color}. Returns {@code -1} (no tint) when neither is present.
 * Bound to the Froglight base layer in the item model.
 */
public record ConfigurableFroglightTint() implements ItemTintSource {

    public static final ConfigurableFroglightTint INSTANCE = new ConfigurableFroglightTint();
    public static final MapCodec<ConfigurableFroglightTint> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        // Equivalence lane (#253): a synthesized Froglight carries an arbitrary
        // item id (not a registered variant). Its tint is sampled from that
        // item's sprite at runtime - no primary_color to look up.
        Identifier synthesizedItem = stack.get(PFDataComponents.SYNTHESIZED_ITEM.get());
        if (synthesizedItem != null) {
            Item item = BuiltInRegistries.ITEM.getOptional(synthesizedItem).orElse(null);
            final int sargb = item == null ? -1 : SynthesizedTint.colorFor(item);
            if (PFDebug.on(PFDebug.Area.TINT)) {
                PFDebug.logOnce(PFDebug.Area.TINT, "froglight_item_synth/" + synthesizedItem,
                    () -> String.format("configurable_froglight(item) synthesized=%s -> #%08X", synthesizedItem, sargb));
            }
            return sargb;
        }
        Identifier variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId == null) {
            return -1;
        }
        ClientLevel resolved = level != null ? level : Minecraft.getInstance().level;
        if (resolved == null) {
            return -1;
        }
        var registry = PFRegistries.variants(resolved.registryAccess());
        SlimeVariant variant = PFRegistries.variant(registry, variantId);
        final int argb = variant == null ? -1 : Tints.opaque(variant.primaryColor());
        if (PFDebug.on(PFDebug.Area.TINT)) {
            PFDebug.logOnce(PFDebug.Area.TINT, "froglight_item/" + variantId,
                () -> String.format("configurable_froglight(item) variant=%s -> #%08X", variantId, argb));
        }
        return argb;
    }

    @Override
    public MapCodec<ConfigurableFroglightTint> type() {
        return CODEC;
    }
}
