package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
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
 * the BE directly here through {@code BlockEntityType.Builder.of(...).build(null)}
 * — the canonical 1.21.1 vanilla entry point. The Builder takes a {@code DSL.Type}
 * data-fixer arg ({@code null} is fine for mod content not subject to vanilla
 * data-fixer-upper migrations).
 */
public final class PFBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProductiveFrogs.MOD_ID);

    public static final Supplier<BlockEntityType<SlimeMilkerBlockEntity>> SLIME_MILKER =
        BLOCK_ENTITIES.register(
            "slime_milker",
            () -> BlockEntityType.Builder.of(SlimeMilkerBlockEntity::new, PFBlocks.SLIME_MILKER.get()).build(null)
        );

    /**
     * BE type for the variant-keyed {@code configurable_froglight} block.
     * Stores one identifier (the variant) — see
     * {@link com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity}.
     */
    public static final Supplier<BlockEntityType<ConfigurableFroglightBlockEntity>> CONFIGURABLE_FROGLIGHT =
        BLOCK_ENTITIES.register(
            "configurable_froglight",
            () -> BlockEntityType.Builder.of(ConfigurableFroglightBlockEntity::new, PFBlocks.CONFIGURABLE_FROGLIGHT.get()).build(null)
        );

    private PFBlockEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
