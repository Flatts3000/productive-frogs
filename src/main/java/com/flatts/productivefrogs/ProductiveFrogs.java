package com.flatts.productivefrogs;

import com.flatts.productivefrogs.gametest.PFGameTests;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFConditions;
import com.flatts.productivefrogs.registry.PFCreativeTabs;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFFluidTypes;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFMenuTypes;
import com.flatts.productivefrogs.registry.PFParticles;
import com.flatts.productivefrogs.registry.PFRecipeTypes;
import com.flatts.productivefrogs.registry.PFSensors;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import com.flatts.productivefrogs.setup.VariantFluidDiscovery;
import com.flatts.productivefrogs.util.PFDebug;
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

        // Read -Dproductivefrogs.debug=<areas> once, before any handler or
        // renderer runs, so startup-enabled areas catch first-frame resolution.
        // Areas can also be toggled live with /pf debug (see PFCommands).
        PFDebug.bootstrapFromSystemProperty();

        PFDataComponents.register(modEventBus);
        // Per-variant Slime Milk (v1.8): discover the variant ids that get their
        // own fluid (built-in index + config/productivefrogs/variants) and mint a
        // fluid/block/bucket for each. Must run BEFORE the Fluid/Block/Item
        // DeferredRegisters fire - BuiltInRegistries.FLUID freezes right after mod
        // construction, so a fluid can only exist for a variant known now.
        PFVariantMilk.bootstrap(VariantFluidDiscovery.discover());
        // Molten metals (v1.12 Crucible melt lane): mint PF-side fluids only
        // where AllTheOres doesn't already provide them - see PFMoltenFluids
        // for the ATM-interop rules. Same must-run-before-registers constraint
        // as the milk bootstrap above.
        com.flatts.productivefrogs.registry.PFMoltenFluids.bootstrap();
        // FluidTypes before Fluids: BaseFlowingFluid.Properties references the
        // FluidType holder at fluid-build time, so the FluidType register pass
        // must complete first. Blocks come after Fluids in turn: the single
        // SLIME_MILK_SOURCE block's factory resolves PFFluids.SLIME_MILK_SOURCE
        // at block-build time (and PFFluids' source/flowing Properties resolve
        // PFItems.SLIME_MILK_BUCKET + PFBlocks.SLIME_MILK_SOURCE lazily).
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
        PFParticles.register(modEventBus);
        PFSensors.register(modEventBus);
        PFCreativeTabs.register(modEventBus);
        PFGameTests.register(modEventBus);
        // Custom datapack condition codecs (e.g. config_enabled, gating the
        // Spawnery recipe). No ordering dependency on the registers above.
        PFConditions.register(modEventBus);
        // Custom recipe types (crucible_melting, v1.12). No ordering dependency.
        PFRecipeTypes.register(modEventBus);

        // COMMON config — depletion + spawn cadence + discovery chance.
        // Registered here so the config file is generated on first boot and
        // values are available to {@code SlimeMilkSourceBlock} ticks and to
        // {@code SlimeSplitDiscoveryHandler#onMobSplit} from the moment the
        // mod loads.
        modContainer.registerConfig(ModConfig.Type.COMMON, PFConfig.SPEC);

        modEventBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Dispenser releasing a Slime Bucket spawns the slime (no water). The
        // dispenser registry isn't thread-safe, so register on the main thread.
        event.enqueueWork(com.flatts.productivefrogs.content.item.SlimeBucketItem::registerDispenseBehavior);
        // ModConfigSpec has no cross-field validator, so warn here (config is
        // loaded by common-setup time) if the spawn interval is inverted.
        // SlimeMilkSourceBlock.scheduleNextSpawnTick falls back to a fixed
        // min-tick delay in that case, silently ignoring max; surface it.
        int min = PFConfig.MIN_SPAWN_INTERVAL_TICKS.get();
        int max = PFConfig.MAX_SPAWN_INTERVAL_TICKS.get();
        if (min > max) {
            LOGGER.warn(
                "PFConfig: minSpawnIntervalTicks ({}) > maxSpawnIntervalTicks ({}); "
                + "Slime Milk source blocks will use minSpawnIntervalTicks as a fixed delay "
                + "and ignore maxSpawnIntervalTicks until the config is corrected.",
                min, max);
        }
        PFDebug.log(PFDebug.Area.CONFIG, () -> String.format(
            "config: depletionEnabled=%s depletionCount=%d spawnInterval=[%d,%d] discoveryChance=%.3f",
            PFConfig.DEPLETION_ENABLED.get(), PFConfig.DEPLETION_COUNT.get(),
            PFConfig.MIN_SPAWN_INTERVAL_TICKS.get(), PFConfig.MAX_SPAWN_INTERVAL_TICKS.get(),
            PFConfig.DISCOVERY_CHANCE_PER_OFFSPRING.get()));
        PFDebug.log(PFDebug.Area.LIFECYCLE,
            "lifecycle: registries + single slime_milk fluid registered; variants are datapack-driven");

        LOGGER.info("Productive Frogs common setup complete");
    }
}
