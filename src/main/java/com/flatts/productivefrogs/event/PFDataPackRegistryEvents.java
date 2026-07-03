package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.ParentSpeciesEntry;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
            PFRegistries.PREDATOR_PREY,
            com.flatts.productivefrogs.data.PredatorPrey.CODEC,
            com.flatts.productivefrogs.data.PredatorPrey.CODEC
        );
        event.dataPackRegistry(
            PFRegistries.PARENT_SPECIES,
            ParentSpeciesEntry.CODEC,
            ParentSpeciesEntry.CODEC
        );
    }

    /**
     * When the {@code registry} debug area is on, dump the loaded datapack
     * registry contents once the server is up (codecs have decoded all JSON by
     * now). Surfaces decode coverage (counts) and each entry's resolved mapping,
     * which is otherwise invisible. Gated, so zero cost in normal play.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!PFDebug.on(PFDebug.Area.REGISTRY)) {
            return;
        }
        var access = event.getServer().registryAccess();
        var variants = PFRegistries.variants(access);
        PFDebug.log(PFDebug.Area.REGISTRY, "slime_variant: {} entries loaded", variants.listElements().count());
        variants.listElements().forEach(ref -> {
            var v = ref.value();
            String primer = v.primerItem().map(Identifier::toString)
                .or(() -> v.primerTag().map(t -> "#" + t.location()))
                .orElse("(none)");
            PFDebug.log(PFDebug.Area.REGISTRY,
                "  slime_variant {} -> category={} primer={}",
                ref.key().identifier(), v.category(), primer);
        });
        var parents = PFRegistries.parentSpeciesLookup(access);
        PFDebug.log(PFDebug.Area.REGISTRY, "parent_species: {} entries loaded", parents.listElements().count());
        parents.listElements().forEach(ref -> PFDebug.log(PFDebug.Area.REGISTRY,
            "  parent_species {} -> entity_type={} category={}",
            ref.key().identifier(), ref.value().entityType(), ref.value().category()));
    }
}
