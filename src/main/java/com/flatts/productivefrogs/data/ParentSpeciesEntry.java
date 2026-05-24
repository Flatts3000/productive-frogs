package com.flatts.productivefrogs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Maps an EntityType identifier to the {@link Category} its offspring inherit
 * during split-discovery. One entry per shipped "parent species" — the six
 * slimes that can split into Resource Slimes when the discovery roll succeeds:
 *
 * <ul>
 *   <li>{@code minecraft:slime}        → {@link Category#METALLIC}</li>
 *   <li>{@code minecraft:magma_cube}   → {@link Category#INFERNAL}</li>
 *   <li>{@code productivefrogs:cave_slime}   → {@link Category#MINERAL}</li>
 *   <li>{@code productivefrogs:geode_slime}  → {@link Category#GEM}</li>
 *   <li>{@code productivefrogs:tide_slime}   → {@link Category#AQUATIC}</li>
 *   <li>{@code productivefrogs:void_slime}   → {@link Category#ARCANE}</li>
 * </ul>
 *
 * <p>Datapack-driven so modpacks can remap modded slime mobs into the
 * discovery loop (e.g. point Mythic Metals' Pyrite Slime at METALLIC) by
 * dropping a single JSON into their pack. All six defaults ship under this
 * mod's own namespace at
 * {@code data/productivefrogs/productivefrogs/parent_species/<name>.json};
 * the two vanilla parents are encoded by setting {@code entity_type} to
 * {@code minecraft:slime} / {@code minecraft:magma_cube} inside those files,
 * not by relocating the JSONs into a {@code data/minecraft/...} tree.
 * Modpacks override or extend the table by dropping JSONs at
 * {@code data/<their_ns>/productivefrogs/parent_species/<name>.json}.
 *
 * <p>Schema:
 * <pre>{@code
 * {
 *   "entity_type": "productivefrogs:cave_slime",
 *   "category":    "cave"
 *  }
 * }</pre>
 *
 * <p>Lookup happens once per split event via
 * {@code SlimeSplitDiscoveryHandler.categoryForParent}. With six entries the
 * linear scan is trivial; if the registry ever grows past a couple dozen
 * entries we'd want to materialize a cache in
 * {@code PFRegistries.PARENT_SPECIES} lookups, but at V1 scale a fold is
 * cheaper than the indirection.
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
     * the parent slime. Optional; absent for vanilla parents
     * ({@code minecraft:slime}, {@code minecraft:magma_cube}) which use their
     * own vanilla renderers and don't read this field. The PF-native parent
     * renderers read this value at render time via
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
}
