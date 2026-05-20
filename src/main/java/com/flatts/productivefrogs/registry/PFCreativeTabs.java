package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative-mode tab registration. Productive Frogs gets a single dedicated tab
 * that aggregates every item the mod registers.
 */
public final class PFCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProductiveFrogs.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PRODUCTIVE_FROGS_TAB =
        CREATIVE_MODE_TABS.register(
            "productive_frogs",
            () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.productivefrogs"))
                .icon(() -> PFItems.FROG_EGG.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    output.accept(PFItems.FROG_EGG.get());
                    // More items added in subsequent commits.
                })
                .build()
        );

    private PFCreativeTabs() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
