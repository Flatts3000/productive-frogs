package com.flatts.productivefrogs.client.tint;

import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Item tint source that reads {@link PFDataComponents#SLIME_VARIANT} off the
 * stack, looks up the matching {@link SlimeVariant} in the client's view of
 * the datapack registry, and returns the variant's primary or secondary
 * color. One source codec, two layer kinds (selected via the {@code layer}
 * field in JSON) — keeps the layered Froglight icon clean without two
 * separate codecs.
 *
 * <p>Falls back to the JSON-supplied {@code default} when:
 * <ul>
 *   <li>the stack has no SLIME_VARIANT component (raw item, pre-stamp);</li>
 *   <li>the variant id doesn't resolve in the client registry (datapack/mod
 *       removed since save);</li>
 *   <li>the client level is null (e.g., rendering an item outside a world).</li>
 * </ul>
 */
public record SlimeVariantTint(Layer layer, int defaultColor) implements ItemTintSource {

    public enum Layer implements StringRepresentable {
        PRIMARY("primary"), SECONDARY("secondary");

        private final String name;
        Layer(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }

        // Codec via StringRepresentable.fromEnum returns a DataResult error on
        // unknown values instead of throwing — datapack typos surface as a
        // decode log line rather than a crash.
        public static final com.mojang.serialization.Codec<Layer> CODEC =
            StringRepresentable.fromEnum(Layer::values);
    }

    public static final MapCodec<SlimeVariantTint> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            Layer.CODEC.fieldOf("layer").forGetter(SlimeVariantTint::layer),
            ExtraCodecs.RGB_COLOR_CODEC.fieldOf("default").forGetter(SlimeVariantTint::defaultColor)
        ).apply(instance, SlimeVariantTint::new)
    );

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity holder) {
        ResourceLocation variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId != null && level != null) {
            Registry<SlimeVariant> registry = level.registryAccess().lookup(PFRegistries.SLIME_VARIANT).orElse(null);
            if (registry != null) {
                SlimeVariant variant = registry.getValue(variantId);
                if (variant != null) {
                    int rgb = layer == Layer.PRIMARY ? variant.primaryColor() : variant.secondaryColor();
                    return ARGB.opaque(rgb);
                }
            }
        }
        return ARGB.opaque(defaultColor);
    }

    @Override
    public MapCodec<SlimeVariantTint> type() {
        return MAP_CODEC;
    }
}
