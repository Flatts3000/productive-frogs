package com.flatts.productivefrogs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Maps an EntityType identifier to the {@link Category} it belongs to. One entry
 * per shipped "parent species" — the six PF-native slimes that anchor the six
 * categories:
 *
 * <ul>
 *   <li>{@code productivefrogs:bog_slime}      → {@link Category#BOG}</li>
 *   <li>{@code productivefrogs:cave_slime}     → {@link Category#CAVE}</li>
 *   <li>{@code productivefrogs:geode_slime}    → {@link Category#GEODE}</li>
 *   <li>{@code productivefrogs:tide_slime}     → {@link Category#TIDE}</li>
 *   <li>{@code productivefrogs:infernal_slime} → {@link Category#INFERNAL}</li>
 *   <li>{@code productivefrogs:void_slime}     → {@link Category#VOID}</li>
 * </ul>
 *
 * <p>Per the V1.5 species-as-category redesign, vanilla {@code minecraft:slime}
 * and {@code minecraft:magma_cube} are deliberately NOT parent species — they
 * are absent from the registry, so both split-discovery and infusion ignore
 * them.
 *
 * <p>Datapack-driven so modpacks can wire modded slime mobs into the system
 * (e.g. point Mythic Metals' Pyrite Slime at CAVE) by dropping a single JSON.
 * All six defaults ship at
 * {@code data/productivefrogs/productivefrogs/parent_species/<name>.json};
 * modpacks override or extend the table at
 * {@code data/<their_ns>/productivefrogs/parent_species/<name>.json}.
 *
 * <p>Schema:
 * <pre>{@code
 * {
 *   "entity_type": "productivefrogs:cave_slime",
 *   "category":    "cave",
 *   "inner_block": "minecraft:stone"
 *  }
 * }</pre>
 *
 * <p>Resolved via {@link #categoryFor} on two paths:
 * {@code SlimeSplitDiscoveryHandler.categoryForParent} (discovery) and
 * {@code SlimeInfusionHandler.resolveParentSpecies} (infusion). With six entries
 * the linear scan is trivial; if the registry ever grows past a couple dozen
 * entries, materialize a cache rather than scanning on a hot path.
 */
public record ParentSpeciesEntry(
    ResourceLocation entityType,
    Category category,
    Optional<ResourceLocation> innerBlock
) {

    /**
     * Codec for the {@code parent_species} datapack registry.
     *
     * <p>{@code inner_block} (v1.0.1+): the vanilla block id rendered inside
     * the parent slime. Optional. The PF-native parent renderers read this
     * value at render time via
     * {@code ResourceSlimeInnerBlockLayer.parentSpeciesBlock}, so a modpack can
     * repoint a species' interior block by editing the JSON (parallel to how
     * Resource Slime variants read their {@code inner_block}). Format: a plain
     * block id (namespace + path, no {@code textures/} prefix, no
     * {@code .png}). Example: {@code "minecraft:stone"}.
     */
    public static final Codec<ParentSpeciesEntry> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity_type").forGetter(ParentSpeciesEntry::entityType),
            Category.CODEC.fieldOf("category").forGetter(ParentSpeciesEntry::category),
            ResourceLocation.CODEC.optionalFieldOf("inner_block").forGetter(ParentSpeciesEntry::innerBlock)
        ).apply(instance, ParentSpeciesEntry::new)
    );

    /**
     * Look up the category mapped to an entity-type id, or {@code null} if no
     * entry maps to it. Shared by the split-discovery and infusion handlers so
     * a single {@code parent_species} JSON wires a (modded) slime into both
     * paths consistently. Linear scan; see the class javadoc on scale.
     */
    @Nullable
    public static Category categoryFor(Registry<ParentSpeciesEntry> registry, ResourceLocation entityTypeId) {
        for (ParentSpeciesEntry entry : registry) {
            if (entry.entityType().equals(entityTypeId)) {
                return entry.category();
            }
        }
        return null;
    }
}
