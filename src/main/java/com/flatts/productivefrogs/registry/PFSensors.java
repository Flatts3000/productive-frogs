package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ai.ResourceFrogAttackablesSensor;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Brain sensor registry for the two vanilla frog sensors {@link
 * com.flatts.productivefrogs.content.entity.ResourceFrog} swaps out: the
 * category-filtered prey sensor (replacing {@code FROG_ATTACKABLES}) and the
 * Sweetslime temptation sensor (replacing {@code FROG_TEMPTATIONS}).
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

    /**
     * Temptation sensor keyed to the Sweetslime treat. Vanilla {@code FROG_TEMPTATIONS}
     * tempts on the {@code ItemTags.FROG_FOOD} tag (slime balls); ResourceFrog swaps
     * this in so a modded frog follows / is lured by the same item it breeds on
     * ({@link com.flatts.productivefrogs.content.entity.ResourceFrog#isFood}), not by
     * loose slime balls. Matches the {@code TemptingSensor(Predicate<ItemStack>)}
     * shape vanilla uses on 1.21.1.
     */
    public static final Supplier<SensorType<TemptingSensor>> RESOURCE_FROG_TEMPTATIONS =
        SENSOR_TYPES.register(
            "resource_frog_temptations",
            () -> new SensorType<>(() -> new TemptingSensor(
                stack -> stack.is(PFItems.SWEETSLIME.get())))
        );

    private PFSensors() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        SENSOR_TYPES.register(modEventBus);
    }
}
