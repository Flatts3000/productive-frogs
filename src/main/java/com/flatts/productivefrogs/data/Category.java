package com.flatts.productivefrogs.data;

import java.util.Locale;

/**
 * The six resource categories Productive Frogs is built around.
 *
 * <p>Each category has a dedicated Primed Frog Egg block + item, a primer tag,
 * and (eventually) Resource Frog / Resource Slime variants. Tier ordering
 * follows {@code docs/categories_and_tiers.md}.
 */
public enum Category {
    METALLIC,
    MINERAL,
    GEM,
    AQUATIC,
    INFERNAL,
    ARCANE;

    /** Lowercase id used in registry paths, tag paths, lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Item-tag path for items that prime this category. */
    public String primerTagPath() {
        return "primer/" + id();
    }

    /** Registry name for this category's Primed Frog Egg block / item. */
    public String primedEggItemName() {
        return id() + "_frog_egg";
    }
}
