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
 * slot: an item in one of them makes the Spawnery bottle that species' frogspawn
 * (a Frog Egg stamped with the matching {@link Category}). The defaults are the
 * representative <b>normal-world</b> resource each species unlocks - iron ingot
 * (Cave), amethyst shard (Geode), bone (Bog), prismarine shard (Tide), blaze
 * powder (Infernal), ender pearl (Void); see {@code docs/spawnery.md}. A skyblock
 * or otherwise restricted pack retunes any species by editing one tag JSON.
 *
 * <p>This is a deliberately <b>separate</b> resolution path from
 * {@code SlimeVariant.findByPrimer}: the Spawnery wants exactly one primer per
 * species (not the whole resource pool) and a clean per-species override surface,
 * so a tag-with-one-entry fits where {@code findByPrimer} (which maps every
 * resource to its category) would not. The slime ball is handled separately as the
 * vanilla-frogspawn primer (see {@code SpawneryInventory.isValidPrimer}); a primer
 * is required, so an empty or untagged primer slot produces nothing.
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
