package com.flatts.productivefrogs.data;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * The six resource categories Productive Frogs is built around.
 *
 * <p>Implements {@link StringRepresentable} so the enum can be used directly
 * as a blockstate property value (lower-case name serialization, matching the
 * usual blockstate JSON convention).
 *
 * <p>Tier ordering follows {@code docs/categories_and_tiers.md} and matches
 * the natural pickaxe-progression curve.
 */
public enum Category implements StringRepresentable {
    METALLIC,
    MINERAL,
    GEM,
    AQUATIC,
    INFERNAL,
    ARCANE;

    /** Lowercase id used in registry paths, tag paths, lang keys, blockstate values. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Item-tag path for items that prime this category. */
    public String primerTagPath() {
        return "primer/" + id();
    }

    /** Item registry name for the BlockItem placing the primed egg of this category. */
    public String primedEggItemName() {
        return id() + "_frog_egg";
    }

    @Override
    public String getSerializedName() {
        return id();
    }
}
