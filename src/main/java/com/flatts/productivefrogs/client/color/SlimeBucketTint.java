package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * Slime Bucket tint. Variant first (via {@code BUCKET_ENTITY_DATA} "Variant" ->
 * the variant's {@code primary_color}); falls back to the {@code Category}
 * colour, then to vanilla slime green so an empty bucket keeps a visible
 * silhouette. Bound to the slime-silhouette layer in the item model.
 */
public record SlimeBucketTint() implements ItemTintSource {

    public static final SlimeBucketTint INSTANCE = new SlimeBucketTint();
    public static final MapCodec<SlimeBucketTint> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        // Try variant primary_color first
        CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            // 26.1: CompoundTag accessors return Optional; getStringOr keeps the
            // old value-or-default semantics (empty string == absent key).
            String variantStr = tag.getStringOr("Variant", "");
            if (!variantStr.isEmpty()) {
                Identifier variantId = Identifier.tryParse(variantStr);
                if (variantId != null) {
                    ClientLevel resolved = level != null ? level : Minecraft.getInstance().level;
                    if (resolved != null) {
                        var registry = PFRegistries.variants(resolved.registryAccess());
                        if (registry != null) {
                            SlimeVariant variant = PFRegistries.variant(registry, variantId);
                            if (variant != null) {
                                final int argb = Tints.opaque(variant.primaryColor());
                                if (PFDebug.on(PFDebug.Area.TINT)) {
                                    PFDebug.logOnce(PFDebug.Area.TINT, "slime_bucket/" + variantId,
                                        () -> String.format(
                                            "slime_bucket(item) variant=%s -> #%08X", variantId, argb));
                                }
                                return argb;
                            }
                        }
                    }
                }
            }
            String catStr = tag.getStringOr("Category", "");
            if (!catStr.isEmpty()) {
                try {
                    return Tints.opaque(Category.valueOf(catStr).tintRgb());
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        // Empty Slime Bucket (no captured slime) - fall back to vanilla slime
        // green so the silhouette stays visible in the creative tab instead of
        // rendering an invisible (-1 = no tint) layer.
        return Tints.opaque(0x5DDE36);
    }

    @Override
    public MapCodec<SlimeBucketTint> type() {
        return CODEC;
    }
}
