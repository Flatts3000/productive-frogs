package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.item.FrogEggItem;
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
     * The Frog Egg item — a glass bottle filled with frogspawn.
     *
     * <p>Obtained by right-clicking vanilla {@code minecraft:frogspawn} with an
     * empty glass bottle: the held bottle transforms in-place into this item,
     * mirroring the vanilla water-bottle / fish-bucket pattern (no separate
     * consumption).
     *
     * <p>Right-clicking this item on a water source places a fresh
     * {@code minecraft:frogspawn} block at the target and transforms the held
     * stack back into an empty {@code minecraft:glass_bottle}. The placed
     * frogspawn can be re-bottled, primed with a category material to become a
     * Primed Frog Egg, or left to hatch into a vanilla tadpole.
     *
     * <p>Stack size is 1 (like vanilla water bottle / fish bucket) since the
     * item carries a contained payload.
     */
    public static final DeferredItem<FrogEggItem> FROG_EGG = ITEMS.register(
        "frog_egg",
        () -> new FrogEggItem(new Item.Properties().stacksTo(1))
    );

    private PFItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
