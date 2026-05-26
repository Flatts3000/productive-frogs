package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Item {@link TagKey}s owned by Productive Frogs.
 *
 * <p>The six {@code spawnery_primer/<species>} tags drive the Spawnery's primer
 * slot - an item in one of them makes the Spawnery bottle that species' frogspawn
 * (an "egg" stamped with the matching {@link Category}); an empty/untagged primer
 * slot yields plain vanilla frogspawn. This is deliberately a <b>separate</b>
 * resolution path from {@code SlimeVariant.findByPrimer}: that maps farmed
 * resources (iron->Cave, ...) to categories, which is circular for the Spawnery's
 * skyblock bootstrap (you cannot require iron to start the frog that farms iron).
 * The defaults are thematic, skyblock-reachable signature items (cobblestone, mud,
 * kelp, amethyst shard, netherrack, ender pearl - see {@code docs/spawnery.md});
 * packs retune any species by editing one tag JSON.
 */
public final class PFItemTags {

    /** Per-species Spawnery primer tags, keyed by category. */
    public static final Map<Category, TagKey<Item>> SPAWNERY_PRIMER = buildSpawneryPrimers();

    private static Map<Category, TagKey<Item>> buildSpawneryPrimers() {
        EnumMap<Category, TagKey<Item>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "spawnery_primer/" + cat.id())));
        }
        return Collections.unmodifiableMap(map);
    }

    /** The primer tag for one species. */
    public static TagKey<Item> spawneryPrimer(Category category) {
        return SPAWNERY_PRIMER.get(category);
    }

    /**
     * The species a Spawnery primer stack selects, or {@code null} when the stack
     * is empty or in no primer tag. Canonical {@link Category#values()} order;
     * first matching tag wins (deterministic if a pack ever tags one item for two
     * species).
     */
    @Nullable
    public static Category primerCategory(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (Category cat : Category.values()) {
            if (stack.is(spawneryPrimer(cat))) {
                return cat;
            }
        }
        return null;
    }

    private PFItemTags() {
        // utility class
    }
}
