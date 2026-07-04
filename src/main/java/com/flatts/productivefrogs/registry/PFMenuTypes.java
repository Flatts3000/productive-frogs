package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.menu.AlembicMenu;
import com.flatts.productivefrogs.content.menu.CastingMoldMenu;
import com.flatts.productivefrogs.content.menu.DistillerMenu;
import com.flatts.productivefrogs.content.menu.HatchMenu;
import com.flatts.productivefrogs.content.menu.IncubatorMenu;
import com.flatts.productivefrogs.content.menu.SlimeChurnMenu;
import com.flatts.productivefrogs.content.menu.SlimeMilkerMenu;
import com.flatts.productivefrogs.content.menu.SlurryPressMenu;
import com.flatts.productivefrogs.content.menu.SpawneryMenu;
import com.flatts.productivefrogs.content.menu.TerrariumControllerMenu;
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

    public static final Supplier<MenuType<SlimeChurnMenu>> SLIME_CHURN =
        MENU_TYPES.register(
            "slime_churn",
            () -> IMenuTypeExtension.create(SlimeChurnMenu::new)
        );

    public static final Supplier<MenuType<SlurryPressMenu>> SLURRY_PRESS =
        MENU_TYPES.register(
            "slurry_press",
            () -> IMenuTypeExtension.create(SlurryPressMenu::new)
        );

    public static final Supplier<MenuType<SpawneryMenu>> SPAWNERY =
        MENU_TYPES.register(
            "spawnery",
            () -> IMenuTypeExtension.create(SpawneryMenu::new)
        );

    public static final Supplier<MenuType<CastingMoldMenu>> CASTING_MOLD =
        MENU_TYPES.register(
            "casting_mold",
            () -> IMenuTypeExtension.create(CastingMoldMenu::new)
        );

    public static final Supplier<MenuType<DistillerMenu>> DISTILLER =
        MENU_TYPES.register(
            "distiller",
            () -> IMenuTypeExtension.create(DistillerMenu::new)
        );

    public static final Supplier<MenuType<AlembicMenu>> ALEMBIC =
        MENU_TYPES.register(
            "alembic",
            () -> IMenuTypeExtension.create(AlembicMenu::new)
        );

    public static final Supplier<MenuType<HatchMenu>> HATCH =
        MENU_TYPES.register(
            "hatch",
            () -> IMenuTypeExtension.create(HatchMenu::new)
        );

    public static final Supplier<MenuType<IncubatorMenu>> INCUBATOR =
        MENU_TYPES.register(
            "incubator",
            () -> IMenuTypeExtension.create(IncubatorMenu::new)
        );

    public static final Supplier<MenuType<TerrariumControllerMenu>> TERRARIUM_CONTROLLER =
        MENU_TYPES.register(
            "terrarium_controller",
            () -> IMenuTypeExtension.create(TerrariumControllerMenu::new)
        );

    private PFMenuTypes() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
