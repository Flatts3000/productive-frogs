package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
import com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity;
import com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SpawneryBlockEntity;
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

    /**
     * One BE type backing every per-variant Slime Milk source block (v1.8). The
     * valid-blocks set is all per-variant blocks minted by {@link PFVariantMilk};
     * resolved lazily here (after bootstrap), so the BE stores the spawn economy +
     * catalyst upgrades while the block carries the variant (see
     * {@link SlimeMilkSourceBlockEntity}).
     */
    public static final Supplier<BlockEntityType<SlimeMilkSourceBlockEntity>> SLIME_MILK_SOURCE =
        BLOCK_ENTITIES.register(
            "slime_milk_source",
            () -> BlockEntityType.Builder.of(SlimeMilkSourceBlockEntity::new, PFVariantMilk.allBlocksArray()).build(null)
        );

    /**
     * BE type for the {@code crucible} block (v1.12 wave 1) - owns the 4,000 mB
     * single-fluid tank, the one-at-a-time melt slot, and the heat-scaled melt
     * loop. See {@link CrucibleBlockEntity}.
     */
    public static final Supplier<BlockEntityType<CrucibleBlockEntity>> CRUCIBLE =
        BLOCK_ENTITIES.register(
            "crucible",
            () -> BlockEntityType.Builder.of(CrucibleBlockEntity::new, PFBlocks.CRUCIBLE.get()).build(null)
        );

    /** BE type for the {@code spawnery} block - holds the 4-slot inventory + cook/burn timers. */
    public static final Supplier<BlockEntityType<SpawneryBlockEntity>> SPAWNERY =
        BLOCK_ENTITIES.register(
            "spawnery",
            () -> BlockEntityType.Builder.of(SpawneryBlockEntity::new, PFBlocks.SPAWNERY.get()).build(null)
        );

    /**
     * BE type bound to all six Primed Frog Egg blocks. Carries the pending
     * offspring stats a breeding pair computed at conception so they survive
     * the frogspawn intermediary and reach the hatched tadpoles (see
     * {@link com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity}).
     * One BE type validates against every per-category egg block via the
     * Builder's block varargs.
     */
    public static final Supplier<BlockEntityType<PrimedFrogEggBlockEntity>> PRIMED_FROG_EGG =
        BLOCK_ENTITIES.register(
            "primed_frog_egg",
            () -> BlockEntityType.Builder.of(
                PrimedFrogEggBlockEntity::new,
                PFBlocks.PRIMED_FROG_EGGS.values().stream()
                    .map(java.util.function.Supplier::get)
                    .toArray(net.minecraft.world.level.block.Block[]::new)
            ).build(null)
        );

    private PFBlockEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
