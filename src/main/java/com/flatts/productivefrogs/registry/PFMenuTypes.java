package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.menu.SlimeMilkerMenu;
import com.flatts.productivefrogs.content.menu.SpawneryMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu (container) registry. The Slime Milker is the only menu in V1;
 * future Centrifuge / Auto-feeder / etc. blocks would land here too.
 *
 * <p>Uses {@link IMenuTypeExtension#create} (NeoForge's network-aware
 * factory) instead of vanilla's bare {@link MenuType#MenuSupplier} so the
 * client-side constructor can read the milker's position from the network
 * buffer and look up its BlockEntity directly.
 */
public final class PFMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, ProductiveFrogs.MOD_ID);

    public static final Supplier<MenuType<SlimeMilkerMenu>> SLIME_MILKER =
        MENU_TYPES.register(
            "slime_milker",
            () -> IMenuTypeExtension.create(SlimeMilkerMenu::new)
        );

    public static final Supplier<MenuType<SpawneryMenu>> SPAWNERY =
        MENU_TYPES.register(
            "spawnery",
            () -> IMenuTypeExtension.create(SpawneryMenu::new)
        );

    private PFMenuTypes() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
