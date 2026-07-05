package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The single Resource Slime spawn egg tint. Per-stack tint from the
 * {@code SLIME_VARIANT} component's registry colours ({@code primary_color} for
 * the base layer, {@code secondary_color} for the spotted overlay when
 * {@code spot} is true). Falls back to the BOG category shade when no variant
 * is set or the registry is not loaded yet (title-screen creative preview
 * before world load) - 26.1 moved spawn-egg colour off the item, so the old
 * {@code SpawnEggItem#getColor} ctor fallback is reproduced here from BOG.
 *
 * @param spot when true, returns the secondary (spotted overlay) colour.
 */
public record ResourceSlimeEggTint(boolean spot) implements ItemTintSource {

    public static final MapCodec<ResourceSlimeEggTint> CODEC = RecordCodecBuilder.mapCodec(
        i -> i.group(
            Codec.BOOL.optionalFieldOf("spot", false).forGetter(ResourceSlimeEggTint::spot)
        ).apply(i, ResourceSlimeEggTint::new));

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Identifier variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId != null) {
            ClientLevel resolved = level != null ? level : Minecraft.getInstance().level;
            if (resolved != null) {
                var registry = PFRegistries.variants(resolved.registryAccess());
                if (registry != null) {
                    SlimeVariant variant = PFRegistries.variant(registry, variantId);
                    if (variant != null) {
                        final int argb = Tints.opaque(spot ? variant.secondaryColor() : variant.primaryColor());
                        if (PFDebug.on(PFDebug.Area.TINT)) {
                            PFDebug.logOnce(PFDebug.Area.TINT, "resource_slime_egg/" + variantId + "/" + spot,
                                () -> String.format("resource_slime_spawn_egg spot=%b variant=%s -> #%08X",
                                    spot, variantId, argb));
                        }
                        return argb;
                    }
                }
            }
        }
        int bog = Category.BOG.tintRgb();
        return Tints.opaque(spot ? Tints.darker(bog) : bog);
    }

    @Override
    public MapCodec<ResourceSlimeEggTint> type() {
        return CODEC;
    }
}
