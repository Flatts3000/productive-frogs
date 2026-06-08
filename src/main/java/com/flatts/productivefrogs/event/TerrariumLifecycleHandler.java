package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.multiblock.TerrariumManager;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Clears the transient {@link TerrariumManager} registry on level unload and
 * server stop, so a stale {@code FormedTerrarium} never survives the level that
 * owns its positions. The registry is rebuilt lazily as Controllers re-validate
 * on their next tick after a (re)load.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class TerrariumLifecycleHandler {

    private TerrariumLifecycleHandler() {
        // static event subscriber
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            TerrariumManager.onLevelUnload(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TerrariumManager.clearAll();
    }
}
