package com.flatts.productivefrogs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

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
 * dropping a single JSON into their pack. The default 6 ship at
 * {@code data/<ns>/productivefrogs/parent_species/<name>.json}, with the
 * "minecraft" namespace used for the two vanilla entries so they live
 * alongside other minecraft datapack files.
 *
 * <p>Schema:
 * <pre>{@code
 * {
 *   "entity_type": "productivefrogs:cave_slime",
 *   "category":    "mineral"
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
public record ParentSpeciesEntry(Identifier entityType, Category category) {

    public static final Codec<ParentSpeciesEntry> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Identifier.CODEC.fieldOf("entity_type").forGetter(ParentSpeciesEntry::entityType),
            Category.CODEC.fieldOf("category").forGetter(ParentSpeciesEntry::category)
        ).apply(instance, ParentSpeciesEntry::new)
    );
}
