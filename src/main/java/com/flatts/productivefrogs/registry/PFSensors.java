package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ai.ResourceFrogAttackablesSensor;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Brain sensor registry. Single entry today — the category-filtered prey sensor
 * that replaces vanilla's {@code FROG_ATTACKABLES} in {@link
 * com.flatts.productivefrogs.content.entity.ResourceFrog}'s brain.
 *
 * <p>NeoForge ATs {@code SensorType}'s constructor to public, so we can
 * register custom sensors via the standard {@code DeferredRegister} pattern
 * without going through vanilla's package-private static {@code register} helper.
 */
public final class PFSensors {

    public static final DeferredRegister<SensorType<?>> SENSOR_TYPES =
        DeferredRegister.create(Registries.SENSOR_TYPE, ProductiveFrogs.MOD_ID);

    public static final Supplier<SensorType<ResourceFrogAttackablesSensor>> RESOURCE_FROG_ATTACKABLES =
        SENSOR_TYPES.register(
            "resource_frog_attackables",
            () -> new SensorType<>(ResourceFrogAttackablesSensor::new)
        );

    private PFSensors() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        SENSOR_TYPES.register(modEventBus);
    }
}
