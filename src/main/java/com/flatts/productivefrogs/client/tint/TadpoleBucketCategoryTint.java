package com.flatts.productivefrogs.client.tint;

import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * ItemTintSource for the bucket items (Resource Tadpole Bucket + Slime
 * Bucket). Resolution order, applied per stack:
 *
 * <ol>
 *   <li><b>SlimeVariant</b> — read the {@code Variant} id from
 *       {@code BUCKET_ENTITY_DATA}, look it up in the client-side
 *       {@link PFRegistries#SLIME_VARIANT} registry, and use the variant's
 *       primary colour. Only Slime Buckets that captured a variant-stamped
 *       Resource Slime carry this; Tadpole Buckets never do.</li>
 *   <li><b>Category</b> — fall back to the broader category tint read from
 *       the same {@code BUCKET_ENTITY_DATA} payload. This is the Tadpole
 *       Bucket's only signal and the Slime Bucket's fallback for
 *       category-only slimes (no variant).</li>
 *   <li><b>Default</b> — the JSON-supplied colour, used when the stack has
 *       no bucket entity data at all (a freshly-crafted bucket, raw item
 *       icon outside a world, etc.).</li>
 * </ol>
 *
 * <p>The class name still says "TadpoleBucket" for historical reasons —
 * see {@code docs/backlog.md} §Code hygiene for the rename TODO. The
 * implementation is bucket-agnostic; both bucket item models reference it.
 */
public record TadpoleBucketCategoryTint(int defaultColor) implements ItemTintSource {

    public static final MapCodec<TadpoleBucketCategoryTint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            ExtraCodecs.RGB_COLOR_CODEC.fieldOf("default").forGetter(TadpoleBucketCategoryTint::defaultColor)
        ).apply(instance, TadpoleBucketCategoryTint::new)
    );

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity holder) {
        // 1. Variant wins when present — most specific colour. The client
        //    level can be null (rendering an item out of world), in which
        //    case we can't reach the registry; skip to category.
        Identifier variantId = ResourceTadpoleBucketItem.readVariant(stack);
        if (variantId != null && level != null) {
            Registry<SlimeVariant> registry = level.registryAccess().lookup(PFRegistries.SLIME_VARIANT).orElse(null);
            if (registry != null) {
                SlimeVariant variant = registry.getValue(variantId);
                if (variant != null) {
                    return ARGB.opaque(variant.primaryColor());
                }
            }
        }
        // 2. Category fallback — Tadpole Buckets only carry this; Slime
        //    Buckets that captured a non-variant ResourceSlime land here.
        Category category = ResourceTadpoleBucketItem.readCategory(stack);
        if (category != null) {
            return ARGB.opaque(category.tintRgb());
        }
        // 3. Static default from the JSON.
        return ARGB.opaque(this.defaultColor);
    }

    @Override
    public MapCodec<TadpoleBucketCategoryTint> type() {
        return MAP_CODEC;
    }
}
