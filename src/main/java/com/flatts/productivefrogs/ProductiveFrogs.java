package com.flatts.productivefrogs;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Productive Frogs mod entry point.
 *
 * <p>For now this is a skeleton that proves the build pipeline produces a loadable mod jar.
 * Content registration (items, blocks, entities, fluids, etc.) lands in subsequent commits.
 */
@Mod(ProductiveFrogs.MOD_ID)
public final class ProductiveFrogs {

    public static final String MOD_ID = "productivefrogs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ProductiveFrogs(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Productive Frogs initializing — mod skeleton loaded");
        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Productive Frogs common setup complete");
    }
}
