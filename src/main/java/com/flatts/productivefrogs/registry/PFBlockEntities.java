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
import com.flatts.productivefrogs.content.block.entity.MobSlurryBasinBlockEntity;
import com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkBasinBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlurryPressBlockEntity;
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
 * the BE directly here through {@code new BlockEntityType<>(...)}
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
            () -> new BlockEntityType<>(SlimeMilkerBlockEntity::new, PFBlocks.SLIME_MILKER.get())
        );

    /**
     * BE type for the {@code slime_churn} block (#187) - owns the 2-in/2-out
     * inventory, the spawn-interval countdown, and the pending-batch state.
     * See {@link SlimeChurnBlockEntity}.
     */
    public static final Supplier<BlockEntityType<SlimeChurnBlockEntity>> SLIME_CHURN =
        BLOCK_ENTITIES.register(
            "slime_churn",
            () -> new BlockEntityType<>(SlimeChurnBlockEntity::new, PFBlocks.SLIME_CHURN.get())
        );

    /**
     * BE type for the {@code slurry_press} block (#281, Phase 3) - the 2-in/2-out
     * inventory and the flat press-cycle progress. See {@link SlurryPressBlockEntity}.
     */
    public static final Supplier<BlockEntityType<SlurryPressBlockEntity>> SLURRY_PRESS =
        BLOCK_ENTITIES.register(
            "slurry_press",
            () -> new BlockEntityType<>(SlurryPressBlockEntity::new, PFBlocks.SLURRY_PRESS.get())
        );

    /**
     * BE type for the {@code mob_slurry_basin} (#281, Phase 3) - the held slurry
     * charge + spawn economy. See {@link MobSlurryBasinBlockEntity}.
     */
    public static final Supplier<BlockEntityType<MobSlurryBasinBlockEntity>> MOB_SLURRY_BASIN =
        BLOCK_ENTITIES.register(
            "mob_slurry_basin",
            () -> new BlockEntityType<>(MobSlurryBasinBlockEntity::new, PFBlocks.MOB_SLURRY_BASIN.get())
        );

    /**
     * BE type for the {@code slime_milk_basin} (#281, Phase 3) - the held milk
     * charge + spawn economy. See {@link SlimeMilkBasinBlockEntity}.
     */
    public static final Supplier<BlockEntityType<SlimeMilkBasinBlockEntity>> SLIME_MILK_BASIN =
        BLOCK_ENTITIES.register(
            "slime_milk_basin",
            () -> new BlockEntityType<>(SlimeMilkBasinBlockEntity::new, PFBlocks.SLIME_MILK_BASIN.get())
        );

    /**
     * BE type for the variant-keyed {@code configurable_froglight} block.
     * Stores one identifier (the variant) — see
     * {@link com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity}.
     */
    public static final Supplier<BlockEntityType<ConfigurableFroglightBlockEntity>> CONFIGURABLE_FROGLIGHT =
        BLOCK_ENTITIES.register(
            "configurable_froglight",
            () -> new BlockEntityType<>(ConfigurableFroglightBlockEntity::new, PFBlocks.CONFIGURABLE_FROGLIGHT.get())
        );

    /** BE type for the {@code end_crystal_receptacle} (#249) - holds one End Crystal. */
    public static final Supplier<BlockEntityType<EndCrystalReceptacleBlockEntity>> END_CRYSTAL_RECEPTACLE =
        BLOCK_ENTITIES.register(
            "end_crystal_receptacle",
            () -> new BlockEntityType<>(EndCrystalReceptacleBlockEntity::new, PFBlocks.END_CRYSTAL_RECEPTACLE.get())
        );

    /** BE type for the {@code end_dragon_altar_hatch} (#249) - the altar's chest-style output. */
    public static final Supplier<BlockEntityType<EndDragonAltarHatchBlockEntity>> END_DRAGON_ALTAR_HATCH =
        BLOCK_ENTITIES.register(
            "end_dragon_altar_hatch",
            () -> new BlockEntityType<>(EndDragonAltarHatchBlockEntity::new, PFBlocks.END_DRAGON_ALTAR_HATCH.get())
        );

    /**
     * One BE type backing both Wither Altar (#247) summon receptacles (soul sand +
     * wither skull) - each holds one item; the accepted item is read from the block.
     */
    public static final Supplier<BlockEntityType<WitherSummonReceptacleBlockEntity>> WITHER_SUMMON_RECEPTACLE =
        BLOCK_ENTITIES.register(
            "wither_summon_receptacle",
            () -> new BlockEntityType<>(WitherSummonReceptacleBlockEntity::new,
                PFBlocks.SOUL_SAND_RECEPTACLE.get(), PFBlocks.WITHER_SKULL_RECEPTACLE.get())
        );

    /** BE type for the {@code wither_altar_hatch} (#247) - the altar's output + summon brain. */
    public static final Supplier<BlockEntityType<WitherAltarHatchBlockEntity>> WITHER_ALTAR_HATCH =
        BLOCK_ENTITIES.register(
            "wither_altar_hatch",
            () -> new BlockEntityType<>(WitherAltarHatchBlockEntity::new, PFBlocks.WITHER_ALTAR_HATCH.get())
        );

    /**
     * BE type backing the single Slime Milk source block (26.1 R-1). Stores the
     * variant it spawns (seeded from the placing bucket's {@code SLIME_VARIANT}
     * component) plus the spawn economy + catalyst upgrades. Replaces the v1.8
     * per-variant block set. See {@link SlimeMilkSourceBlockEntity}.
     */
    public static final Supplier<BlockEntityType<SlimeMilkSourceBlockEntity>> SLIME_MILK_SOURCE =
        BLOCK_ENTITIES.register(
            "slime_milk_source",
            () -> new BlockEntityType<>(SlimeMilkSourceBlockEntity::new, PFBlocks.SLIME_MILK_SOURCE.get())
        );

    /**
     * BE type for the {@code crucible} block (v1.12 wave 1) - owns the 4,000 mB
     * single-fluid tank, the one-at-a-time melt slot, and the heat-scaled melt
     * loop. See {@link CrucibleBlockEntity}.
     */
    public static final Supplier<BlockEntityType<CrucibleBlockEntity>> CRUCIBLE =
        BLOCK_ENTITIES.register(
            "crucible",
            () -> new BlockEntityType<>(CrucibleBlockEntity::new, PFBlocks.CRUCIBLE.get())
        );

    /**
     * BE type for the {@code casting_mold} block (v1.12 wave 2) - owns the
     * 1,000 mB molten buffer, the cast-progress timer, and the one output
     * slot. See {@link CastingMoldBlockEntity}.
     */
    public static final Supplier<BlockEntityType<CastingMoldBlockEntity>> CASTING_MOLD =
        BLOCK_ENTITIES.register(
            "casting_mold",
            () -> new BlockEntityType<>(CastingMoldBlockEntity::new, PFBlocks.CASTING_MOLD.get())
        );

    /**
     * BE type for the {@code distiller} block (#253) - owns the input/output
     * slots, the RF buffer, and the distill timer. PF's first RF machine. See
     * {@link DistillerBlockEntity}.
     */
    public static final Supplier<BlockEntityType<DistillerBlockEntity>> DISTILLER =
        BLOCK_ENTITIES.register(
            "distiller",
            () -> new BlockEntityType<>(DistillerBlockEntity::new, PFBlocks.DISTILLER.get())
        );

    /**
     * BE type for the Mimic Milk source block (#253) - carries the synthesized
     * item + the spawn budget. See {@link MimicMilkSourceBlockEntity}.
     */
    public static final Supplier<BlockEntityType<MimicMilkSourceBlockEntity>> MIMIC_MILK_SOURCE =
        BLOCK_ENTITIES.register(
            "mimic_slime_milk_source",
            () -> new BlockEntityType<>(MimicMilkSourceBlockEntity::new, PFBlocks.MIMIC_MILK.get())
        );

    /**
     * BE type for the {@code alembic} block (#253) - the lane's RF synthesizer
     * (bucket + item slots, output, energy buffer). See {@link AlembicBlockEntity}.
     */
    public static final Supplier<BlockEntityType<AlembicBlockEntity>> ALEMBIC =
        BLOCK_ENTITIES.register(
            "alembic",
            () -> new BlockEntityType<>(AlembicBlockEntity::new, PFBlocks.ALEMBIC.get())
        );

    /** BE type for the {@code spawnery} block - holds the 4-slot inventory + cook/burn timers. */
    public static final Supplier<BlockEntityType<SpawneryBlockEntity>> SPAWNERY =
        BLOCK_ENTITIES.register(
            "spawnery",
            () -> new BlockEntityType<>(SpawneryBlockEntity::new, PFBlocks.SPAWNERY.get())
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
            () -> new BlockEntityType<>(
                PrimedFrogEggBlockEntity::new,
                java.util.stream.Stream.of(
                        PFBlocks.PRIMED_FROG_EGGS.values().stream().map(java.util.function.Supplier::get),
                        // The Midas egg (#253) shares this BE type.
                        java.util.stream.Stream.of(PFBlocks.MIDAS_FROG_EGG.get()),
                        // The per-kind predator/apex eggs (2026-07-04 ruling) too.
                        PFBlocks.KIND_FROG_EGGS.values().stream().map(java.util.function.Supplier::get))
                    .flatMap(st -> st)
                    .toArray(net.minecraft.world.level.block.Block[]::new)
            )
        );

    /**
     * BE type for the Terrarium Controller (#185) - the only ticking Terrarium
     * BE in phase 1 (runs the throttled validation loop). See
     * {@link TerrariumControllerBlockEntity}.
     */
    public static final Supplier<BlockEntityType<TerrariumControllerBlockEntity>> TERRARIUM_CONTROLLER =
        BLOCK_ENTITIES.register(
            "terrarium_controller",
            () -> new BlockEntityType<>(TerrariumControllerBlockEntity::new, PFBlocks.TERRARIUM_CONTROLLER.get())
        );

    /** BE type for the Sprinkler (#185). Inert in phase 1; spawn loop lands in phase 2. */
    public static final Supplier<BlockEntityType<SprinklerBlockEntity>> SPRINKLER =
        BLOCK_ENTITIES.register(
            "sprinkler",
            () -> new BlockEntityType<>(SprinklerBlockEntity::new, PFBlocks.SPRINKLER.get())
        );

    /** BE type for the Incubator (#185). Inert in phase 1; stat relay lands in phase 4. */
    public static final Supplier<BlockEntityType<IncubatorBlockEntity>> INCUBATOR =
        BLOCK_ENTITIES.register(
            "incubator",
            () -> new BlockEntityType<>(IncubatorBlockEntity::new, PFBlocks.INCUBATOR.get())
        );

    /** BE type for the Hatch (#185). Inert in phase 1; output inventory lands in phase 3. */
    public static final Supplier<BlockEntityType<HatchBlockEntity>> HATCH =
        BLOCK_ENTITIES.register(
            "hatch",
            () -> new BlockEntityType<>(HatchBlockEntity::new, PFBlocks.HATCH.get())
        );

    /**
     * BE type for the Sweetslimed Lily Pad (#214) - the perch driver. Stateless;
     * its server ticker claims and holds a nearby frog (see
     * {@link SweetslimedLilyPadBlockEntity}).
     */
    public static final Supplier<BlockEntityType<SweetslimedLilyPadBlockEntity>> SWEETSLIMED_LILY_PAD =
        BLOCK_ENTITIES.register(
            "sweetslimed_lily_pad",
            () -> new BlockEntityType<>(SweetslimedLilyPadBlockEntity::new, PFBlocks.SWEETSLIMED_LILY_PAD.get())
        );

    private PFBlockEntities() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
