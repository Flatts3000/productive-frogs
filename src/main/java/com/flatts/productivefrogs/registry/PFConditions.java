package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.condition.ConfigEnabledCondition;
import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registry for the mod's custom datapack {@link ICondition} codecs. NeoForge
 * keeps condition serializers in {@code NeoForgeRegistries.CONDITION_CODECS};
 * registering against {@link NeoForgeRegistries.Keys#CONDITION_CODECS} makes the
 * {@code "type"} string usable inside any {@code neoforge:conditions} block.
 *
 * <p>Currently just {@link ConfigEnabledCondition} (id
 * {@code productivefrogs:config_enabled}), which gates the Spawnery recipe on
 * {@code spawnery.enabled}.
 */
public final class PFConditions {

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
        DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, ProductiveFrogs.MOD_ID);

    public static final Supplier<MapCodec<? extends ICondition>> CONFIG_ENABLED =
        CONDITION_CODECS.register("config_enabled", () -> ConfigEnabledCondition.CODEC);

    private PFConditions() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        CONDITION_CODECS.register(modEventBus);
    }
}
