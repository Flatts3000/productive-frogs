package com.flatts.productivefrogs;

import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFCreativeTabs;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Productive Frogs mod entry point.
 *
 * <p>Wires up the DeferredRegisters for items and creative tabs against the
 * mod event bus. Game-side event handlers (e.g. the frogspawn bottling hook)
 * register themselves via {@code @EventBusSubscriber}.
 */
@Mod(ProductiveFrogs.MOD_ID)
public final class ProductiveFrogs {

    public static final String MOD_ID = "productivefrogs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ProductiveFrogs(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Productive Frogs initializing");

        PFDataComponents.register(modEventBus);
        PFBlocks.register(modEventBus);
        PFItems.register(modEventBus);
        PFEntities.register(modEventBus);
        PFCreativeTabs.register(modEventBus);

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Productive Frogs common setup complete");
    }
}
