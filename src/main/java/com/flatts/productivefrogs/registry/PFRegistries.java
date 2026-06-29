package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.ParentSpeciesEntry;
import com.flatts.productivefrogs.data.SlimeVariant;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Resource keys for Productive Frogs' custom datapack registries. The
 * registries themselves are created via {@link
 * com.flatts.productivefrogs.event.PFDataPackRegistryEvents}; this class
 * holds the keys that other code references when looking up entries.
 *
 * <p>JSON layout (NeoForge {@code DataPackRegistryEvent.NewRegistry}
 * convention: {@code data/<datapack_ns>/<key_namespace>/<key_path>/<entry>.json}):
 * <ul>
 *   <li>{@link #SLIME_VARIANT} entries live at
 *       {@code data/<datapack_ns>/productivefrogs/slime_variant/<name>.json}.</li>
 *   <li>{@link #PARENT_SPECIES} entries live at
 *       {@code data/<datapack_ns>/productivefrogs/parent_species/<name>.json}.</li>
 * </ul>
 */
public final class PFRegistries {

    public static final ResourceKey<Registry<SlimeVariant>> SLIME_VARIANT =
        ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_variant")
        );

    /**
     * Per-parent-species default category mapping. Each entry pins an
     * EntityType id to the category its offspring inherit during slime
     * split-discovery (see {@link com.flatts.productivefrogs.data.ParentSpeciesEntry}).
     * Six default entries ship with the mod; datapacks can override or extend
     * the list to wire modded slime mobs into the discovery loop.
     */
    public static final ResourceKey<Registry<ParentSpeciesEntry>> PARENT_SPECIES =
        ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "parent_species")
        );

    private PFRegistries() {
        // utility class
    }
}
