package com.flatts.productivefrogs;

import com.flatts.productivefrogs.gametest.PFGameTests;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFCreativeTabs;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFFluidTypes;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFMenuTypes;
import com.flatts.productivefrogs.registry.PFSensors;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
        // FluidTypes before Fluids — BaseFlowingFluid.Properties references the
        // FluidType holder at fluid-build time, so the FluidType register pass
        // must complete first.
        PFFluidTypes.register(modEventBus);
        PFFluids.register(modEventBus);
        PFBlocks.register(modEventBus);
        PFItems.register(modEventBus);
        // BlockEntities + MenuTypes after Blocks because SLIME_MILKER's
        // BlockEntityType.Builder.of references PFBlocks.SLIME_MILKER at
        // BE registration time.
        PFBlockEntities.register(modEventBus);
        PFMenuTypes.register(modEventBus);
        PFEntities.register(modEventBus);
        PFSensors.register(modEventBus);
        PFCreativeTabs.register(modEventBus);
        PFGameTests.register(modEventBus);

        // COMMON config — depletion + spawn cadence + discovery chance.
        // Registered here so the config file is generated on first boot and
        // values are available to {@code SlimeMilkSourceBlock} ticks and to
        // {@code SlimeSplitDiscoveryHandler#onMobSplit} from the moment the
        // mod loads.
        modContainer.registerConfig(ModConfig.Type.COMMON, PFConfig.SPEC);

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Productive Frogs common setup complete");
    }
}
