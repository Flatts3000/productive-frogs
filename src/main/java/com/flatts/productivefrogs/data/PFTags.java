package com.flatts.productivefrogs.data;

import com.flatts.productivefrogs.ProductiveFrogs;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Tag key constants used by Productive Frogs.
 *
 * <p>Tags themselves are defined as JSON files in
 * {@code src/main/resources/data/productivefrogs/tags/items/primer/*.json}.
 * (Singular {@code item/} — MC 1.21.x renamed the tag directory from the
 * legacy plural {@code items/}.) This class holds the typed {@link TagKey}
 * handles that Java code uses to check tag membership at runtime.
 */
public final class PFTags {

    /** Per-category primer item tags. */
    public static final Map<Category, TagKey<Item>> PRIMER_BY_CATEGORY = buildPrimerMap();

    private static Map<Category, TagKey<Item>> buildPrimerMap() {
        EnumMap<Category, TagKey<Item>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, cat.primerTagPath())
            ));
        }
        return map;
    }

    private PFTags() {
        // utility class
    }
}
