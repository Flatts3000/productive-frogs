package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.SlimeVariant;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Resource keys for Productive Frogs' custom datapack registries. The
 * registry itself is created via {@link
 * com.flatts.productivefrogs.event.PFDataPackRegistryEvents}; this class
 * holds the keys that other code references when looking up entries.
 *
 * <p>JSON layout: entries for {@link #SLIME_VARIANT} live at
 * {@code data/<datapack_ns>/productivefrogs/slime_variant/<name>.json}.
 * The double-namespace ({@code productivefrogs/slime_variant}) comes from
 * NeoForge's {@code DataPackRegistryEvent.NewRegistry} convention:
 * {@code data/<datapack_ns>/<key_namespace>/<key_path>/<entry>.json}.
 */
public final class PFRegistries {

    public static final ResourceKey<Registry<SlimeVariant>> SLIME_VARIANT =
        ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_variant")
        );

    private PFRegistries() {
        // utility class
    }
}
