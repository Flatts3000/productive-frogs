package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity;
import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
import com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity;
import com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity;
import com.flatts.productivefrogs.content.block.entity.DistillerBlockEntity;
import com.flatts.productivefrogs.content.block.entity.EndCrystalReceptacleBlockEntity;
import com.flatts.productivefrogs.content.block.entity.EndDragonAltarHatchBlockEntity;
import com.flatts.productivefrogs.content.block.entity.HatchBlockEntity;
import com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity;
import com.flatts.productivefrogs.content.block.entity.MimicMilkSourceBlockEntity;
import com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkBasinBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SpawneryBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SweetslimedLilyPadBlockEntity;
import com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity;
import com.flatts.productivefrogs.content.block.entity.WitherAltarHatchBlockEntity;
import com.flatts.productivefrogs.content.block.entity.WitherSummonReceptacleBlockEntity;
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
     * BE type for the {@code slime_churn} block (#187) - owns the 2-in/2-out
     * inventory, the spawn-interval countdown, and the pending-batch state.
     * See {@link SlimeChurnBlockEntity}.
     */
    public static final Supplier<BlockEntityType<SlimeChurnBlockEntity>> SLIME_CHURN =
        BLOCK_ENTITIES.register(
            "slime_churn",
            () -> BlockEntityType.Builder.of(SlimeChurnBlockEntity::new, PFBlocks.SLIME_CHURN.get()).build(null)
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

    /** BE type for the {@code end_crystal_receptacle} (#249) - holds one End Crystal. */
    public static final Supplier<BlockEntityType<EndCrystalReceptacleBlockEntity>> END_CRYSTAL_RECEPTACLE =
        BLOCK_ENTITIES.register(
            "end_crystal_receptacle",
            () -> BlockEntityType.Builder.of(EndCrystalReceptacleBlockEntity::new, PFBlocks.END_CRYSTAL_RECEPTACLE.get()).build(null)
        );

    /** BE type for the {@code end_dragon_altar_hatch} (#249) - the altar's chest-style output. */
    public static final Supplier<BlockEntityType<EndDragonAltarHatchBlockEntity>> END_DRAGON_ALTAR_HATCH =
        BLOCK_ENTITIES.register(
            "end_dragon_altar_hatch",
            () -> BlockEntityType.Builder.of(EndDragonAltarHatchBlockEntity::new, PFBlocks.END_DRAGON_ALTAR_HATCH.get()).build(null)
        );

    /**
     * One BE type backing both Wither Altar (#247) summon receptacles (soul sand +
     * wither skull) - each holds one item; the accepted item is read from the block.
     */
    public static final Supplier<BlockEntityType<WitherSummonReceptacleBlockEntity>> WITHER_SUMMON_RECEPTACLE =
        BLOCK_ENTITIES.register(
            "wither_summon_receptacle",
            () -> BlockEntityType.Builder.of(WitherSummonReceptacleBlockEntity::new,
                PFBlocks.SOUL_SAND_RECEPTACLE.get(), PFBlocks.WITHER_SKULL_RECEPTACLE.get()).build(null)
        );

    /** BE type for the {@code wither_altar_hatch} (#247) - the altar's output + summon brain. */
    public static final Supplier<BlockEntityType<WitherAltarHatchBlockEntity>> WITHER_ALTAR_HATCH =
        BLOCK_ENTITIES.register(
            "wither_altar_hatch",
            () -> BlockEntityType.Builder.of(WitherAltarHatchBlockEntity::new, PFBlocks.WITHER_ALTAR_HATCH.get()).build(null)
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

    /**
     * BE type for the {@code slime_milk_basin} block - owns the held charge
     * (variant, spawn budget, catalysts) and the spawn countdown. See
     * {@link com.flatts.productivefrogs.content.block.entity.SlimeMilkBasinBlockEntity}.
     */
    public static final Supplier<BlockEntityType<SlimeMilkBasinBlockEntity>> SLIME_MILK_BASIN =
        BLOCK_ENTITIES.register(
            "slime_milk_basin",
            () -> BlockEntityType.Builder.of(SlimeMilkBasinBlockEntity::new, PFBlocks.SLIME_MILK_BASIN.get()).build(null)
        );

    /**
     * BE type for the {@code casting_mold} block (v1.12 wave 2) - owns the
     * 1,000 mB molten buffer, the cast-progress timer, and the one output
     * slot. See {@link CastingMoldBlockEntity}.
     */
    public static final Supplier<BlockEntityType<CastingMoldBlockEntity>> CASTING_MOLD =
        BLOCK_ENTITIES.register(
            "casting_mold",
            () -> BlockEntityType.Builder.of(CastingMoldBlockEntity::new, PFBlocks.CASTING_MOLD.get()).build(null)
        );

    /**
     * BE type for the {@code distiller} block (#253) - owns the input/output
     * slots, the RF buffer, and the distill timer. PF's first RF machine. See
     * {@link DistillerBlockEntity}.
     */
    public static final Supplier<BlockEntityType<DistillerBlockEntity>> DISTILLER =
        BLOCK_ENTITIES.register(
            "distiller",
            () -> BlockEntityType.Builder.of(DistillerBlockEntity::new, PFBlocks.DISTILLER.get()).build(null)
        );

    /**
     * BE type for the Mimic Milk source block (#253) - carries the synthesized
     * item + the spawn budget. See {@link MimicMilkSourceBlockEntity}.
     */
    public static final Supplier<BlockEntityType<MimicMilkSourceBlockEntity>> MIMIC_MILK_SOURCE =
        BLOCK_ENTITIES.register(
            "mimic_slime_milk_source",
            () -> BlockEntityType.Builder.of(MimicMilkSourceBlockEntity::new, PFBlocks.MIMIC_MILK.get()).build(null)
        );

    /**
     * BE type for the {@code alembic} block (#253) - the lane's RF synthesizer
     * (bucket + item slots, output, energy buffer). See {@link AlembicBlockEntity}.
     */
    public static final Supplier<BlockEntityType<AlembicBlockEntity>> ALEMBIC =
        BLOCK_ENTITIES.register(
            "alembic",
            () -> BlockEntityType.Builder.of(AlembicBlockEntity::new, PFBlocks.ALEMBIC.get()).build(null)
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
                java.util.stream.Stream.concat(
                        PFBlocks.PRIMED_FROG_EGGS.values().stream().map(java.util.function.Supplier::get),
                        // The Midas egg (#253) shares this BE type.
                        java.util.stream.Stream.of(PFBlocks.MIDAS_FROG_EGG.get()))
                    .toArray(net.minecraft.world.level.block.Block[]::new)
            ).build(null)
        );

    /**
     * BE type for the Terrarium Controller (#185) - the only ticking Terrarium
     * BE in phase 1 (runs the throttled validation loop). See
     * {@link TerrariumControllerBlockEntity}.
     */
    public static final Supplier<BlockEntityType<TerrariumControllerBlockEntity>> TERRARIUM_CONTROLLER =
        BLOCK_ENTITIES.register(
            "terrarium_controller",
            () -> BlockEntityType.Builder.of(TerrariumControllerBlockEntity::new, PFBlocks.TERRARIUM_CONTROLLER.get()).build(null)
        );

    /** BE type for the Sprinkler (#185). Inert in phase 1; spawn loop lands in phase 2. */
    public static final Supplier<BlockEntityType<SprinklerBlockEntity>> SPRINKLER =
        BLOCK_ENTITIES.register(
            "sprinkler",
            () -> BlockEntityType.Builder.of(SprinklerBlockEntity::new, PFBlocks.SPRINKLER.get()).build(null)
        );

    /** BE type for the Incubator (#185). Inert in phase 1; stat relay lands in phase 4. */
    public static final Supplier<BlockEntityType<IncubatorBlockEntity>> INCUBATOR =
        BLOCK_ENTITIES.register(
            "incubator",
            () -> BlockEntityType.Builder.of(IncubatorBlockEntity::new, PFBlocks.INCUBATOR.get()).build(null)
        );

    /** BE type for the Hatch (#185). Inert in phase 1; output inventory lands in phase 3. */
    public static final Supplier<BlockEntityType<HatchBlockEntity>> HATCH =
        BLOCK_ENTITIES.register(
            "hatch",
            () -> BlockEntityType.Builder.of(HatchBlockEntity::new, PFBlocks.HATCH.get()).build(null)
        );

    /**
     * BE type for the Sweetslimed Lily Pad (#214) - the perch driver. Stateless;
     * its server ticker claims and holds a nearby frog (see
     * {@link SweetslimedLilyPadBlockEntity}).
     */
    public static final Supplier<BlockEntityType<SweetslimedLilyPadBlockEntity>> SWEETSLIMED_LILY_PAD =
        BLOCK_ENTITIES.register(
            "sweetslimed_lily_pad",
            () -> BlockEntityType.Builder.of(SweetslimedLilyPadBlockEntity::new, PFBlocks.SWEETSLIMED_LILY_PAD.get()).build(null)
        );

    private PFBlockEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
