package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * NeoForge data maps. The first (and so far only) map is {@code crucible_heat}:
 * block id -> heat value, read from the block UNDER a Froglight Crucible to
 * drive its melt speed ({@code progress += heat} per tick toward a fixed total).
 *
 * <p>The default values ship at
 * {@code data/productivefrogs/data_maps/block/crucible_heat.json} and are
 * <b>copied verbatim from Ex Deorum</b> (torch 1, soul/campfire 2, lava 3,
 * fire 5) so the same block heats both mods' crucibles at the same relative
 * strength in Sky Frogs - see {@code docs/froglight_crucible.md}. A pack
 * overrides or extends the map with plain datapack JSON, the same posture as
 * the Spawnery primer tags.
 *
 * <p>State-sensitive sources (campfires' {@code lit}) are handled in code by
 * {@link com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity};
 * the data map only keys on the block.
 */
public final class PFDataMaps {

    public static final DataMapType<Block, Integer> CRUCIBLE_HEAT = DataMapType.builder(
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "crucible_heat"),
        Registries.BLOCK,
        Codec.intRange(0, 1024)
    )
        // Synced so client-side UIs can enumerate it - the JEI "Crucible Heat"
        // category lists the sources straight off the live map, so pack
        // overrides display automatically. (Server logic alone wouldn't need
        // the sync.)
        .synced(Codec.intRange(0, 1024), false)
        .build();

    /**
     * Froglight heat: slime_variant id -> heat value for a PLACED Configurable
     * Froglight of that variant under a Crucible. A second map because the
     * variant lives on the Froglight's BlockEntity, invisible to the
     * block-keyed {@link #CRUCIBLE_HEAT} map. Defaults (decided 2026-06-06):
     * lava 3 (parity with the lava block), blaze 6 (above fire - it's a block
     * of fire elemental), blazing crystal 10 (the Powah-gated top of the
     * ladder). Same pack-override posture; per-entry
     * {@code neoforge:conditions} gate cross-mod variants. Synced for the JEI
     * heat category.
     */
    public static final DataMapType<com.flatts.productivefrogs.data.SlimeVariant, Integer> FROGLIGHT_HEAT =
        DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "froglight_heat"),
            PFRegistries.SLIME_VARIANT,
            Codec.intRange(0, 1024)
        )
            .synced(Codec.intRange(0, 1024), false)
            .build();

    private PFDataMaps() {
        // utility class
    }

    /**
     * Heat value of a block, or 0 when the block carries no map entry.
     * Campfire lit-ness is NOT checked here - callers that care about
     * blockstate gate it themselves.
     */
    public static int heatOf(Block block) {
        Integer heat = BuiltInRegistries.BLOCK.wrapAsHolder(block).getData(CRUCIBLE_HEAT);
        return heat == null ? 0 : heat;
    }
}
