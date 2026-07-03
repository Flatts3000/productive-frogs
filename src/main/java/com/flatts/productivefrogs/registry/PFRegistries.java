package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.ParentSpeciesEntry;
import com.flatts.productivefrogs.data.SlimeVariant;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

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

    /**
     * The predation prey map (#281): one entry per mob a Predator Frog eats -
     * {@code data/<ns>/productivefrogs/predator_prey/<name>.json}. Hand-authored
     * from the settled #281 map; datapack-extensible like the two above.
     */
    public static final ResourceKey<Registry<com.flatts.productivefrogs.data.PredatorPrey>> PREDATOR_PREY =
        ResourceKey.createRegistryKey(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "predator_prey")
        );

    // ---- 26.1 registry access ------------------------------------------------
    // On 26.1 RegistryAccess no longer exposes registry()/registryOrThrow()
    // (returning a Registry<T>); the access path is HolderLookup.Provider's
    // lookupOrThrow(key) -> HolderLookup.RegistryLookup<T>, whose get(ResourceKey)
    // yields Optional<Holder<T>>. These helpers centralize that mechanics so call
    // sites stay one-liners and the API coupling lives in one place.

    /** The Slime Variant registry lookup, for iteration/streaming via listElements(). */
    public static HolderLookup.RegistryLookup<SlimeVariant> variants(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(SLIME_VARIANT);
    }

    /** The predation prey-map lookup (#281). */
    public static HolderLookup.RegistryLookup<com.flatts.productivefrogs.data.PredatorPrey> predatorPrey(
            HolderLookup.Provider registries) {
        return registries.lookupOrThrow(PREDATOR_PREY);
    }

    /** Resolve a Slime Variant by id, or {@code null} if absent. */
    @Nullable
    public static SlimeVariant variant(HolderLookup.Provider registries, Identifier id) {
        return variant(registries.lookupOrThrow(SLIME_VARIANT), id);
    }

    /** Resolve a Slime Variant by id from an already-obtained lookup, or {@code null}. */
    @Nullable
    public static SlimeVariant variant(HolderLookup.RegistryLookup<SlimeVariant> lookup, Identifier id) {
        return lookup.get(ResourceKey.create(SLIME_VARIANT, id)).map(Holder::value).orElse(null);
    }

    /** The Parent Species registry lookup, for iteration/streaming. */
    public static HolderLookup.RegistryLookup<ParentSpeciesEntry> parentSpeciesLookup(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(PARENT_SPECIES);
    }

    /** Resolve a Parent Species entry by id, or {@code null} if absent. */
    @Nullable
    public static ParentSpeciesEntry parentSpecies(HolderLookup.Provider registries, Identifier id) {
        return registries.lookupOrThrow(PARENT_SPECIES)
            .get(ResourceKey.create(PARENT_SPECIES, id))
            .map(Holder::value)
            .orElse(null);
    }

    private PFRegistries() {
        // utility class
    }
}
