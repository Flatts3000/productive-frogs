package com.flatts.productivefrogs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

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
 *   <li>{@code texture} — optional per-variant entity texture path. When set,
 *       {@code ResourceSlimeRenderer} binds this texture for the slime's
 *       inner + outer cubes instead of falling back to the broad-category
 *       texture. Lets all three METALLIC variants (iron / copper / gold)
 *       render distinctly with their respective resource block inside the
 *       translucent shell rather than sharing one iron-themed look. Absent
 *       in V1's shipped variants — the framework is in place for whenever
 *       per-variant PNGs land in a follow-up.
 *       <br><b>Identifier format:</b> the value must be the full
 *       {@code RenderType.entityTranslucent}-compatible texture location —
 *       namespace, {@code textures/} prefix, and {@code .png} suffix
 *       included. Mirrors how the per-category fallback paths are built in
 *       {@code ResourceSlimeRenderer.buildTextureMap} (and how vanilla's
 *       {@code SlimeRenderer.SLIME_LOCATION} is shaped). Example:
 *       {@code "productivefrogs:textures/entity/slime/iron_resource_slime.png"}.
 *       Anything shorter (e.g. {@code "productivefrogs:entity/slime/iron"})
 *       resolves to a missing-texture binding at render time.</li>
 * </ul>
 */
public record SlimeVariant(
    Identifier primerItem,
    Category category,
    int primaryColor,
    int secondaryColor,
    int weight,
    Optional<Identifier> texture
) {

    /**
     * Codec used by the datapack registry for both JSON loading and client
     * sync (NeoForge's {@code DataPackRegistryEvent.NewRegistry} takes a
     * {@code Codec<T>} for the network side, not a separate StreamCodec —
     * it wraps the codec with its own byte-level serialization).
     *
     * <p>{@code weight} is constrained to {@code [1, Integer.MAX_VALUE]} so
     * datapack input can't produce zero or negative weights that would skew
     * the discovery-pool random pick. The field is optional and defaults to
     * 1 — simple variants don't need to specify it.
     */
    public static final Codec<SlimeVariant> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Identifier.CODEC.fieldOf("primer_item").forGetter(SlimeVariant::primerItem),
            Category.CODEC.fieldOf("category").forGetter(SlimeVariant::category),
            Codec.INT.fieldOf("primary_color").forGetter(SlimeVariant::primaryColor),
            Codec.INT.fieldOf("secondary_color").forGetter(SlimeVariant::secondaryColor),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(SlimeVariant::weight),
            Identifier.CODEC.optionalFieldOf("texture").forGetter(SlimeVariant::texture)
        ).apply(instance, SlimeVariant::new)
    );

    /**
     * Find the variant whose {@code primer_item} matches the given item id, or
     * {@code null} if no variant maps to it. Used by the slime infusion handler
     * to upgrade a primer-tag match from "category only" to "specific variant"
     * when the held item matches a shipped variant's primer.
     *
     * <p>Linear scan over the registry. With ~12 variants V1-shipped (or even
     * the future ~30-50 with cross-mod compat) the cost is trivial compared to
     * the rest of the right-click handler.
     */
    public static Map.Entry<Identifier, SlimeVariant> findByPrimerItem(
            Registry<SlimeVariant> registry, Identifier itemId) {
        for (Map.Entry<net.minecraft.resources.ResourceKey<SlimeVariant>, SlimeVariant> entry : registry.entrySet()) {
            if (entry.getValue().primerItem().equals(itemId)) {
                return Map.entry(entry.getKey().identifier(), entry.getValue());
            }
        }
        return null;
    }

    /**
     * Weighted random pick from all variants whose category matches the given
     * one. Returns {@code null} when the registry has no variants for that
     * category (e.g., during early development before variants for a category
     * have been shipped, or when a datapack ships none for a category).
     *
     * <p>Used by {@code SlimeSplitDiscoveryHandler} to pick the specific
     * variant a passively-discovered Resource Slime becomes — the parent
     * species's default category determines the pool; {@code weight} biases
     * the pick within it.
     */
    public static Map.Entry<Identifier, SlimeVariant> pickWeighted(
            Registry<SlimeVariant> registry, Category category, RandomSource random) {
        List<Map.Entry<Identifier, SlimeVariant>> pool = new ArrayList<>();
        // Accumulate as long so a datapack with many high-weight variants
        // (each capped at Integer.MAX_VALUE individually) can't overflow.
        // RandomSource doesn't expose nextLong-with-bound directly, so cap
        // total + roll at Integer.MAX_VALUE — anything beyond is silly anyway.
        long totalWeight = 0L;
        for (Map.Entry<net.minecraft.resources.ResourceKey<SlimeVariant>, SlimeVariant> entry : registry.entrySet()) {
            if (entry.getValue().category() != category) continue;
            pool.add(Map.entry(entry.getKey().identifier(), entry.getValue()));
            totalWeight += entry.getValue().weight();
        }
        if (pool.isEmpty()) {
            return null;
        }
        int cap = (int) Math.min(totalWeight, Integer.MAX_VALUE);
        int roll = random.nextInt(cap);
        long cumulative = 0L;
        for (Map.Entry<Identifier, SlimeVariant> entry : pool) {
            cumulative += entry.getValue().weight();
            if (roll < cumulative) {
                return entry;
            }
        }
        // Shouldn't reach — defensive fallback to first.
        return pool.get(0);
    }
}
