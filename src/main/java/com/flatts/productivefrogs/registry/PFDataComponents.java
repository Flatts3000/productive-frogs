package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom DataComponentTypes registered by Productive Frogs.
 *
 * <p>Mirrors how vanilla uses data components for {@code potion_contents},
 * {@code bucket_entity_data}, etc.: a typed payload attached to an ItemStack
 * that the item's behavior reads at runtime.
 */
public final class PFDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ProductiveFrogs.MOD_ID);

    /**
     * The category carried by a Frog Egg bottle (or other typed container) —
     * absent means "contains vanilla frogspawn", present means "contains a
     * primed egg of that category". Used by:
     * <ul>
     *   <li>{@code FrogEggItem.useOn} — which block to place</li>
     *   <li>{@code FrogspawnBottlingHandler} — what to write when bottling a
     *       Primed Frog Egg block</li>
     *   <li>{@code FrogEggItem.getName} — dynamic display name</li>
     *   <li>Client ItemColor handler — content layer tint</li>
     * </ul>
     */
    public static final Supplier<DataComponentType<Category>> CONTAINED_CATEGORY = COMPONENTS.register(
        "contained_category",
        () -> DataComponentType.<Category>builder()
            .persistent(Category.CODEC)
            .networkSynchronized(Category.STREAM_CODEC)
            .build()
    );

    private PFDataComponents() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
