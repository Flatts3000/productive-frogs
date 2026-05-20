package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.item.FrogEggItem;
import com.flatts.productivefrogs.data.Category;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Centralized item registry for Productive Frogs.
 *
 * <p>Items are registered via {@link DeferredRegister.Items}. Each public field
 * is a {@link DeferredItem} holder — call {@code .get()} to retrieve the live
 * {@link Item} instance after registration.
 */
public final class PFItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(ProductiveFrogs.MOD_ID);

    /**
     * The Frog Egg item — a glass bottle filled with frogspawn. Obtained by
     * right-clicking vanilla frogspawn with an empty glass bottle; placing it
     * back puts down vanilla frogspawn and returns an empty bottle. Mirrors
     * vanilla water-bottle / fish-bucket semantics.
     */
    public static final DeferredItem<FrogEggItem> FROG_EGG = ITEMS.register(
        "frog_egg",
        () -> new FrogEggItem(new Item.Properties().stacksTo(1))
    );

    /**
     * Per-category BlockItems for the six Primed Frog Egg blocks. Each pairs
     * cleanly with its dedicated block ID — vanilla's Block↔Item bijection
     * applies (pick-block, drops, asItem() all do the right thing).
     */
    public static final Map<Category, DeferredItem<BlockItem>> PRIMED_FROG_EGG_ITEMS = buildPrimedEggItems();

    private static Map<Category, DeferredItem<BlockItem>> buildPrimedEggItems() {
        EnumMap<Category, DeferredItem<BlockItem>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, ITEMS.register(
                cat.primedEggItemName(),
                () -> new BlockItem(PFBlocks.primedEgg(cat), new Item.Properties())
            ));
        }
        return map;
    }

    private PFItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
