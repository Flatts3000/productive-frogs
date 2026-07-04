package com.flatts.productivefrogs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * One entry of the {@code productivefrogs:predator_prey} datapack registry
 * (#281): maps a vanilla (or modded) mob to the {@link FrogKind.Predator} that
 * eats it and the movement-class handling the design assigned it. The shipped
 * set is <b>hand-authored per mob</b> from the settled map on issue #281 (no
 * runtime locomotion derivation); packs add or reclassify mobs by dropping a
 * JSON - the same posture as {@code slime_variant} / {@code parent_species}.
 *
 * <p>Schema ({@code data/<ns>/productivefrogs/predator_prey/<name>.json}):
 * <pre>{@code
 * {
 *   "entity_type": "minecraft:zombie",
 *   "predator":    "prowler",
 *   "handling":    "walk"
 * }
 * }</pre>
 *
 * <p>{@code handling} records the settled movement class (walk / fly / swim /
 * teleport). Eligibility is driven by the {@code predator} match; the handling
 * class is carried for player-facing surfaces (JEI/Jade/guide) and future
 * gating - the open eat path targets any eligible class in range (flyers are
 * player-boxed, aquatic prey is eaten in water by the amphibious Gulper,
 * Basin-spawned teleporters have teleportation disabled).
 *
 * <p>Bosses are deliberately absent from this registry - they are farmed at
 * their altars by Apex Frogs, never by the open eat path.
 */
public record PredatorPrey(
    Identifier entityType,
    FrogKind.Predator predator,
    Handling handling
) {

    /** The settled movement-class buckets (#281). */
    public enum Handling implements StringRepresentable {
        WALK,
        FLY,
        SWIM,
        TELEPORT;

        public static final Codec<Handling> CODEC = StringRepresentable.fromEnum(Handling::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Codec<FrogKind.Predator> PREDATOR_CODEC = Codec.STRING.comapFlatMap(
        key -> {
            for (FrogKind.Predator p : FrogKind.Predator.values()) {
                if (p.key().equals(key)) {
                    return com.mojang.serialization.DataResult.success(p);
                }
            }
            return com.mojang.serialization.DataResult.error(() -> "Unknown predator kind: " + key);
        },
        FrogKind.Predator::key
    );

    public static final Codec<PredatorPrey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.fieldOf("entity_type").forGetter(PredatorPrey::entityType),
        PREDATOR_CODEC.fieldOf("predator").forGetter(PredatorPrey::predator),
        Handling.CODEC.fieldOf("handling").forGetter(PredatorPrey::handling)
    ).apply(instance, PredatorPrey::new));

    // Per-reload index (review finding #10): predatorFor runs per candidate per
    // predator frog per sensor scan, so the registry scan (and especially the
    // per-call listElements().toList() allocation) is hot-path waste. Rebuilt by
    // rebuildIndex on TagsUpdatedEvent (server start + /reload); until the first
    // rebuild the raw registry walk (no toList) serves as the fallback.
    @Nullable
    private static volatile java.util.Map<Identifier, FrogKind.Predator> index;

    /** Rebuild the lookup index from the loaded registry (called on datapack (re)load). */
    public static void rebuildIndex(HolderLookup.Provider registries) {
        java.util.Map<Identifier, FrogKind.Predator> map = new java.util.HashMap<>();
        registries.lookupOrThrow(com.flatts.productivefrogs.registry.PFRegistries.PREDATOR_PREY)
            .listElements()
            .forEach(holder -> map.put(holder.value().entityType(), holder.value().predator()));
        index = java.util.Map.copyOf(map);
    }

    /**
     * The predator that eats {@code type}, or null when the mob is not in the
     * prey registry (bosses, no-drop mobs, unmapped modded mobs). One map get
     * once the reload index is built; a raw registry walk before that.
     */
    @Nullable
    public static FrogKind.Predator predatorFor(HolderLookup.RegistryLookup<PredatorPrey> registry, EntityType<?> type) {
        Identifier id = EntityType.getKey(type);
        java.util.Map<Identifier, FrogKind.Predator> idx = index;
        if (idx != null) {
            return idx.get(id);
        }
        return registry.listElements()
            .filter(holder -> holder.value().entityType().equals(id))
            .map(holder -> holder.value().predator())
            .findFirst()
            .orElse(null);
    }

}
