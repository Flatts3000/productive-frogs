package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.ParentSpeciesEntry;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Registers Productive Frogs' custom datapack registries. The
 * {@code dataPackRegistry} call binds the registry key to its codec pair —
 * vanilla then handles JSON loading, network sync, and exposing the registry
 * through {@code RegistryAccess.lookup(...)}.
 *
 * <p>We pass both the persistent codec (for JSON loading) and the network
 * codec (for client sync) so the loaded variants reach the client
 * automatically. Without a network codec, client-side rendering wouldn't
 * see the variant data and we'd need a separate sync mechanism.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class PFDataPackRegistryEvents {

    private PFDataPackRegistryEvents() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(
            PFRegistries.SLIME_VARIANT,
            SlimeVariant.CODEC,
            SlimeVariant.CODEC
        );
        event.dataPackRegistry(
            PFRegistries.PARENT_SPECIES,
            ParentSpeciesEntry.CODEC,
            ParentSpeciesEntry.CODEC
        );
    }
}
