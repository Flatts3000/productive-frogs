package com.flatts.productivefrogs.data;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * The unified frog identity - the single value that says <em>what a frog is</em>.
 *
 * <p>Before 2.0, identity was smeared across a synced {@link Category} ordinal
 * plus a Midas boolean, and every identity-sensitive surface (display name,
 * {@code canMate}, prey sensor, lay-block choice, egg hatch, incubator seed,
 * bucket NBT) branched on the pair. The predation redesign (#281) adds a whole
 * predator tier - and Apex frogs follow in its Phase 4 - so identity is now one
 * first-class sealed type. Branch sites use exhaustive {@code switch} pattern
 * matching over the sealed hierarchy: adding a kind (Apex) fails compilation at
 * every site that must decide, instead of silently falling through an if-chain.
 *
 * <p>Three kinds today:
 * <ul>
 *   <li>{@link Resource} - one of the six species, wrapping its {@link Category}.
 *       Eats same-category Resource Slimes; breeds with its own category, plus the
 *       four designated cross pairs that produce predators (#281).</li>
 *   <li>{@link Midas} - the Equivalence lane's frog (#253), folded in from the old
 *       {@code midas} flag. Eats Mimic Slimes, breeds true with itself only.</li>
 *   <li>{@link Predator} - the four tier-2 predator frogs (#281): Prowler
 *       (overworld), Cinder (nether), Gulper (aquatic), Rift (end). Obtained by a
 *       designated resource-species cross; breeds true with its own kind.</li>
 * </ul>
 *
 * <p><b>Identity string.</b> {@link #id()} is the stable serialized form used in
 * entity synched data, save NBT ({@code "Kind"}), bucket tags, and egg
 * BlockEntities - e.g. {@code "resource/bog"}, {@code "midas"},
 * {@code "predator/prowler"}. {@link #byId(String)} resolves it back; save-safe
 * (no ordinals persisted).
 *
 * <p><b>Breeding.</b> {@link #canMateWith(FrogKind)} / {@link #offspringWith(FrogKind)}
 * hold the <em>pure</em> pairing rules; config gating (predation on/off,
 * {@code sameSpeciesOnly}) is applied by the caller
 * ({@code ResourceFrog#canMate}), keeping this type config-free and unit-testable.
 *
 * <p>{@link #fallbackCategory()} keeps legacy category-reading surfaces (tints,
 * Jade readouts, the egg carrier block) working for kinds that are not a species:
 * Midas falls back to VOID (its historical carrier), a predator to its breeding
 * pair's anchor species.
 */
public sealed interface FrogKind permits FrogKind.Resource, FrogKind.Midas, FrogKind.Predator {

    /** Stable serialized id, e.g. {@code "resource/bog"}, {@code "midas"}, {@code "predator/prowler"}. */
    String id();

    /**
     * The lang-key suffix appended to the entity's description id for the display
     * name - {@code "bog"}.."{@code void}" (the pre-2.0 keys, unchanged),
     * {@code "midas"}, {@code "prowler"}..{@code "rift"}.
     */
    String nameSuffix();

    /**
     * The {@link Category} legacy surfaces read for this kind - the species itself
     * for a {@link Resource}, VOID for {@link Midas} (its historical carrier), the
     * breeding anchor for a {@link Predator}. Never null; identity comparisons must
     * use the kind, not this.
     */
    Category fallbackCategory();

    /** Pure pairing rule - whether these two kinds are a valid breeding pair (no config applied). */
    boolean canMateWith(FrogKind other);

    /**
     * The kind a successful pairing produces, or null when the pairing has no
     * defined offspring (callers fall back to the pregnant parent's kind).
     * Same-kind pairs breed true; the four designated resource crosses produce
     * their predator (#281).
     */
    @Nullable
    FrogKind offspringWith(FrogKind mate);

    /** The Midas kind singleton (#253). */
    Midas MIDAS = new Midas();

    /** The interned Resource kind for {@code category}. */
    static Resource resource(Category category) {
        return Resource.of(category);
    }

    /** Resolve a serialized {@link #id()}, or null when unknown (corrupt/foreign data). */
    @Nullable
    static FrogKind byId(String id) {
        return Registry.BY_ID.get(id);
    }

    /** Resolve a serialized {@link #id()}, falling back to {@code def} when unknown. */
    static FrogKind byIdOrDefault(String id, FrogKind def) {
        FrogKind kind = byId(id);
        return kind != null ? kind : def;
    }

    /**
     * Read a kind from entity save data: the {@code "Kind"} id string, with a
     * legacy fallback for pre-Kind writers ({@code "Category"} + {@code "Midas"} -
     * the baked spawn-egg ENTITY_DATA and old GameTest NBT still write that pair).
     * Empty when the data carries neither form.
     */
    static java.util.Optional<FrogKind> readFrom(net.minecraft.world.level.storage.ValueInput input) {
        java.util.Optional<FrogKind> byKind = input.getString("Kind").map(FrogKind::byId);
        if (byKind.isPresent()) {
            return byKind;
        }
        if (input.getBooleanOr("Midas", false)) {
            return java.util.Optional.of(MIDAS);
        }
        return input.getString("Category").map(name -> {
            try {
                return resource(Category.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        });
    }

    /** {@link #readFrom} for the CompoundTag surfaces (bucket entity data). Same key contract. */
    static java.util.Optional<FrogKind> readFromTag(net.minecraft.nbt.CompoundTag tag) {
        java.util.Optional<FrogKind> byKind = tag.getString("Kind").map(FrogKind::byId);
        if (byKind.isPresent()) {
            return byKind;
        }
        if (tag.getBooleanOr("Midas", false)) {
            return java.util.Optional.of(MIDAS);
        }
        return tag.getString("Category").map(name -> {
            try {
                return resource(Category.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        });
    }

    /**
     * One of the six species - identity wraps the {@link Category}. Interned so
     * {@code ==} works alongside {@code equals} (records also compare by value).
     */
    record Resource(Category category) implements FrogKind {

        private static final EnumMap<Category, Resource> INTERNED = new EnumMap<>(Category.class);
        static {
            for (Category c : Category.values()) {
                INTERNED.put(c, new Resource(c));
            }
        }

        static Resource of(Category category) {
            return INTERNED.get(category);
        }

        @Override
        public String id() {
            return "resource/" + category.id();
        }

        @Override
        public String nameSuffix() {
            return category.id();
        }

        @Override
        public Category fallbackCategory() {
            return category;
        }

        @Override
        public boolean canMateWith(FrogKind other) {
            return other instanceof Resource r
                && (r.category == category || Predator.fromCross(category, r.category) != null);
        }

        @Override
        @Nullable
        public FrogKind offspringWith(FrogKind mate) {
            if (!(mate instanceof Resource r)) {
                return null;
            }
            return r.category == category ? this : Predator.fromCross(category, r.category);
        }
    }

    /**
     * The Midas frog (#253) - folded in from the pre-2.0 {@code midas} boolean.
     * Its own breeding line (Midas x Midas only); VOID is the historical carrier
     * category its tint and egg surfaces fall back to.
     */
    final class Midas implements FrogKind {

        private Midas() {
        }

        @Override
        public String id() {
            return "midas";
        }

        @Override
        public String nameSuffix() {
            return "midas";
        }

        @Override
        public Category fallbackCategory() {
            return Category.VOID;
        }

        @Override
        public boolean canMateWith(FrogKind other) {
            return other instanceof Midas;
        }

        @Override
        @Nullable
        public FrogKind offspringWith(FrogKind mate) {
            return mate instanceof Midas ? this : null;
        }
    }

    /**
     * The four tier-2 Predator Frogs (#281), each mapped to the environment its
     * prey lives in and to the designated resource-species breeding pair that
     * produces it. The pair map is settled design (issue #281, 2026-07-03); the
     * anchor (first species) doubles as the {@link #fallbackCategory()}.
     */
    enum Predator implements FrogKind {
        PROWLER("prowler", Category.BOG, Category.CAVE),
        CINDER("cinder", Category.INFERNAL, Category.GEODE),
        GULPER("gulper", Category.TIDE, Category.BOG),
        RIFT("rift", Category.VOID, Category.GEODE);

        private final String key;
        private final Category anchor;
        private final Category partner;

        Predator(String key, Category anchor, Category partner) {
            this.key = key;
            this.anchor = anchor;
            this.partner = partner;
        }

        /** The lang/registry key fragment ({@code prowler}, {@code cinder}, ...). */
        public String key() {
            return key;
        }

        /** The designated breeding pair, anchor first (also the fallback category). */
        public Category anchor() {
            return anchor;
        }

        public Category partner() {
            return partner;
        }

        @Override
        public String id() {
            return "predator/" + key;
        }

        @Override
        public String nameSuffix() {
            return key;
        }

        @Override
        public Category fallbackCategory() {
            return anchor;
        }

        @Override
        public boolean canMateWith(FrogKind other) {
            // Predators breed true with their own kind only.
            return other == this;
        }

        @Override
        @Nullable
        public FrogKind offspringWith(FrogKind mate) {
            return mate == this ? this : null;
        }

        /**
         * The predator a designated resource-species cross produces, or null when
         * {@code (a, b)} is not one of the four settled pairs. Unordered - the
         * pregnant parent may be either species of the pair.
         */
        @Nullable
        public static Predator fromCross(Category a, Category b) {
            for (Predator p : values()) {
                if ((p.anchor == a && p.partner == b) || (p.anchor == b && p.partner == a)) {
                    return p;
                }
            }
            return null;
        }
    }

    /** Id lookup table; initialization-order-safe holder (interface fields init before use). */
    final class Registry {
        private static final Map<String, FrogKind> BY_ID = new LinkedHashMap<>();
        static {
            for (Category c : Category.values()) {
                FrogKind k = Resource.of(c);
                BY_ID.put(k.id(), k);
            }
            BY_ID.put(MIDAS.id(), MIDAS);
            for (Predator p : Predator.values()) {
                BY_ID.put(p.id(), p);
            }
        }

        private Registry() {
        }
    }
}
