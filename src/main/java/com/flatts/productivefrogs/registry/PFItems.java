package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
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
     * The Frog Egg item. Obtained by right-clicking vanilla frogspawn with an
     * empty glass bottle. Placeable on water to produce a Frog Egg block.
     */
    public static final DeferredItem<Item> FROG_EGG = ITEMS.register(
        "frog_egg",
        () -> new Item(new Item.Properties().stacksTo(64))
    );

    private PFItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
