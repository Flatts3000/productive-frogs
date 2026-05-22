package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block-entity registry. First entry is the {@link SlimeMilkerBlockEntity}
 * — the furnace-style cook surface that holds the Slime Milker's inventory
 * and progress counter.
 *
 * <p>Productive Bees' centrifuge is the closest reference shape, but they
 * lean heavily on their {@code productivelib} utility module. We register
 * the BE directly here with vanilla {@link BlockEntityType.Builder} for a
 * lighter footprint.
 */
public final class PFBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProductiveFrogs.MOD_ID);

    public static final Supplier<BlockEntityType<SlimeMilkerBlockEntity>> SLIME_MILKER =
        BLOCK_ENTITIES.register(
            "slime_milker",
            () -> new BlockEntityType<>(SlimeMilkerBlockEntity::new, PFBlocks.SLIME_MILKER.get())
        );

    private PFBlockEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
