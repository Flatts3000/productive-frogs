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
                    output.accept(PFItems.RESOURCE_TADPOLE_BUCKET.get());
                    output.accept(PFItems.SLIME_BUCKET.get());
                    output.accept(PFItems.IRON_SLIME_MILK_BUCKET.get());
                    for (var entry : PFItems.PRIMED_FROG_EGG_ITEMS.values()) {
                        output.accept(entry.get());
                    }
                    for (var entry : PFItems.RESOURCE_FROGLIGHT_ITEMS.values()) {
                        output.accept(entry.get());
                    }
                    // One configurable_froglight per shipped variant — each stack
                    // carries its variant id in the SLIME_VARIANT data component so
                    // creative testers can see what the production loop produces.
                    for (String variantName : PFItems.RESOURCE_SLIME_SPAWN_EGGS.keySet()) {
                        net.minecraft.world.item.ItemStack stack =
                            new net.minecraft.world.item.ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
                        stack.set(PFDataComponents.SLIME_VARIANT.get(),
                            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                com.flatts.productivefrogs.ProductiveFrogs.MOD_ID, variantName));
                        output.accept(stack);
                    }
                    // Spawn eggs grouped at the end so they read as a single block
                    // in the creative tab — frogs first, tadpoles after.
                    for (var entry : PFItems.RESOURCE_FROG_SPAWN_EGGS.values()) {
                        output.accept(entry.get());
                    }
                    for (var entry : PFItems.RESOURCE_TADPOLE_SPAWN_EGGS.values()) {
                        output.accept(entry.get());
                    }
                    for (var entry : PFItems.RESOURCE_SLIME_SPAWN_EGGS.values()) {
                        output.accept(entry.get());
                    }
                    // Parent species spawn eggs (Cave / Geode / Tide / Void) —
                    // not category-themed, kept after the variant eggs so the
                    // tab reads as: variants first, then upstream sources.
                    output.accept(PFItems.CAVE_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.GEODE_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.TIDE_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.VOID_SLIME_SPAWN_EGG.get());
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
