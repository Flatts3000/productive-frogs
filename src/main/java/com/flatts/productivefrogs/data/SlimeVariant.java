package com.flatts.productivefrogs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * Per-resource subdivision within a {@link Category}. One {@code SlimeVariant}
 * = one species of Resource Slime (iron_slime, copper_slime, redstone_slime,
 * ...). Variants are loaded from a datapack registry — JSONs live at
 * {@code data/<datapack_ns>/productivefrogs/slime_variant/<name>.json}.
 *
 * <p>Design lifted from Productive Bees' {@code ConfigurableBee} pattern (see
 * {@code docs/productive_bees_analysis.md} §1) — but with a {@link Codec} from
 * day one rather than hand-rolled JSON→CompoundTag conversion, which is the
 * #1 anti-pattern called out in §5a of the analysis.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code primerItem} — the item that, when right-clicked on a vanilla
 *       slime, transforms it into this variant. Mirrors the per-category
 *       primer tag pattern but at the variant level (iron_ingot → iron_slime
 *       specifically, not just METALLIC).</li>
 *   <li>{@code category} — parent category. Determines which Resource Frog
 *       can eat this slime and which default discovery pool the variant
 *       belongs to.</li>
 *   <li>{@code primaryColor} / {@code secondaryColor} — RGB tints (0xRRGGBB).
 *       Drive inventory icon and entity-render tinting. Distinct from
 *       {@link Category#tintRgb()} so variants within the same category can
 *       look different (iron silver vs copper orange vs gold yellow within
 *       METALLIC).</li>
 *   <li>{@code weight} — relative weight for random discovery pool picks
 *       (per {@code docs/slime_sourcing.md} §V1 Configurability). Higher
 *       weight = more likely to spawn. Default 1.</li>
 * </ul>
 */
public record SlimeVariant(
    Identifier primerItem,
    Category category,
    int primaryColor,
    int secondaryColor,
    int weight
) {

    /**
     * Codec used by the datapack registry to load entries from JSON and by
     * the network codec below for client sync. {@code weight} is optional
     * and defaults to 1 so simple variants don't need to specify it.
     */
    public static final Codec<SlimeVariant> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Identifier.CODEC.fieldOf("primer_item").forGetter(SlimeVariant::primerItem),
            Category.CODEC.fieldOf("category").forGetter(SlimeVariant::category),
            Codec.INT.fieldOf("primary_color").forGetter(SlimeVariant::primaryColor),
            Codec.INT.fieldOf("secondary_color").forGetter(SlimeVariant::secondaryColor),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(SlimeVariant::weight)
        ).apply(instance, SlimeVariant::new)
    );

    /**
     * Network codec — required for the datapack registry to sync entries to
     * connected clients. Without this, client-side rendering and tint code
     * couldn't see the loaded variants.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SlimeVariant> STREAM_CODEC =
        StreamCodec.composite(
            Identifier.STREAM_CODEC, SlimeVariant::primerItem,
            Category.STREAM_CODEC, SlimeVariant::category,
            ByteBufCodecs.INT, SlimeVariant::primaryColor,
            ByteBufCodecs.INT, SlimeVariant::secondaryColor,
            ByteBufCodecs.INT, SlimeVariant::weight,
            SlimeVariant::new
        );
}
