package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFPotions;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Mod-event-bus listeners for one-time registration callbacks that need an
 * event instead of (or in addition to) a DeferredRegister. Currently just
 * the entity attribute registration; renderer registration lives in
 * client-only code.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class PFModBusEvents {

    private PFModBusEvents() {
        // event handler, not instantiable
    }

    /**
     * Brewing recipe for the Potion of Hopping (#215): awkward potion + raw Frog
     * Legs -> Hopping, the rabbit's-foot -> Leaping parallel with our reagent and
     * a forward-leap effect. Gated by {@code hopping.enabled}; brewing is
     * registered once at startup, so toggling it needs a restart.
     */
    @SubscribeEvent
    public static void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        if (!PFConfig.hoppingEnabled()) {
            return;
        }
        event.getBuilder().addMix(Potions.AWKWARD, PFItems.RAW_FROG_LEGS.get(), PFPotions.HOPPING);
        // Glowstone upgrades to Hopping II (the vanilla strong-potion pattern).
        event.getBuilder().addMix(PFPotions.HOPPING, net.minecraft.world.item.Items.GLOWSTONE_DUST, PFPotions.HOPPING_STRONG);
    }

    @SubscribeEvent
    public static void onCreateAttributes(EntityAttributeCreationEvent event) {
        // ResourceTadpole reuses vanilla Tadpole's attribute table verbatim.
        event.put(PFEntities.RESOURCE_TADPOLE.get(), Tadpole.createAttributes().build());
        event.put(PFEntities.RESOURCE_FROG.get(), ResourceFrog.createAttributes().build());
        // Dragon altar display frog (#249) "Dragonsbane" reuses the vanilla Frog attribute table.
        event.put(PFEntities.DRAGONSBANE.get(), net.minecraft.world.entity.animal.frog.Frog.createAttributes().build());
        // Witherbane (#247), the Wither Altar's display frog, likewise.
        event.put(PFEntities.WITHERBANE.get(), net.minecraft.world.entity.animal.frog.Frog.createAttributes().build());
        // ResourceSlime uses the standard Monster attribute table — same baseline
        // vanilla EntityType.SLIME uses (via Monster.createMonsterAttributes).
        // Per-size HP/movement scaling happens in Slime#setSize at runtime,
        // not via the attribute table itself.
        event.put(PFEntities.RESOURCE_SLIME.get(), Monster.createMonsterAttributes().build());
        // Mimic Slime (#253) - same vanilla-Slime Monster baseline.
        event.put(PFEntities.MIMIC_SLIME.get(), Monster.createMonsterAttributes().build());
        // Cave Slime and all future parent species (Geode/Tide/Void) reuse
        // the same Monster baseline — they're vanilla-shaped Slime subclasses.
        event.put(PFEntities.CAVE_SLIME.get(), Monster.createMonsterAttributes().build());
        event.put(PFEntities.GEODE_SLIME.get(), Monster.createMonsterAttributes().build());
        event.put(PFEntities.TIDE_SLIME.get(), Monster.createMonsterAttributes().build());
        event.put(PFEntities.VOID_SLIME.get(), Monster.createMonsterAttributes().build());
        event.put(PFEntities.BOG_SLIME.get(), Monster.createMonsterAttributes().build());
        event.put(PFEntities.INFERNAL_SLIME.get(), Monster.createMonsterAttributes().build());
    }

    /**
     * Register {@link net.minecraft.world.entity.SpawnPlacements} for each
     * parent species so they can actually spawn during worldgen / chunk-spawn
     * cycles. Biome filtering is done separately via {@code neoforge:add_spawns}
     * BiomeModifier JSONs at {@code data/productivefrogs/neoforge/biome_modifier/};
     * this event sets the per-block placement type + condition.
     *
     * <p>{@link Heightmap.Types#OCEAN_FLOOR} for Tide Slime so it spawns on the
     * topmost solid block <em>ignoring water</em> — i.e., the actual sea floor.
     * The other three use {@link Heightmap.Types#MOTION_BLOCKING_NO_LEAVES},
     * the standard surface heightmap vanilla mobs use.
     *
     * <p>Spawn condition is {@link #checkParentSlimeSpawnRules}: standard
     * monster rules (peaceful = no, darkness = yes, vanilla mob position
     * checks) but typed against {@link Mob} instead of {@link Monster}, since
     * {@code Slime} (and our parent species) don't extend {@code Monster}.
     */
    @SubscribeEvent
    public static void onRegisterSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
            PFEntities.CAVE_SLIME.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            PFModBusEvents::checkParentSlimeSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
        event.register(
            PFEntities.GEODE_SLIME.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            PFModBusEvents::checkParentSlimeSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
        event.register(
            PFEntities.TIDE_SLIME.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.OCEAN_FLOOR,
            PFModBusEvents::checkParentSlimeSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
        event.register(
            PFEntities.VOID_SLIME.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            PFModBusEvents::checkParentSlimeSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
        event.register(
            PFEntities.BOG_SLIME.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            PFModBusEvents::checkParentSlimeSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
        event.register(
            PFEntities.INFERNAL_SLIME.get(),
            SpawnPlacementTypes.ON_GROUND,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            // Infernal Slime spawns in the nether — no darkness gate, mirroring
            // vanilla magma cube. Use a custom predicate that drops the
            // darkness check for this species.
            PFModBusEvents::checkInfernalSlimeSpawnRules,
            RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }

    /**
     * Nether-mob spawn predicate — same as the standard parent-slime rule
     * but without the darkness check (vanilla magma cubes spawn at any
     * light level in the nether).
     */
    private static <T extends net.minecraft.world.entity.Mob> boolean checkInfernalSlimeSpawnRules(
        net.minecraft.world.entity.EntityType<T> type,
        net.minecraft.world.level.ServerLevelAccessor level,
        net.minecraft.world.entity.EntitySpawnReason reason,
        net.minecraft.core.BlockPos pos,
        net.minecraft.util.RandomSource random
    ) {
        return level.getLevel().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
            && net.minecraft.world.entity.Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    /**
     * Register block-side capabilities — currently just the Slime Milker's
     * {@code Capabilities.Item.BLOCK} so hoppers can push Slime Buckets into
     * the input slot and pull finished Slime Milk buckets from the output.
     *
     * <p>Side-aware view, mirroring vanilla furnace conventions:
     * <ul>
     *   <li>{@link Direction#DOWN} → extract-only OUTPUT view (a hopper
     *       below the milker queries side=DOWN to pull items).</li>
     *   <li>top + horizontal faces (and {@code null} for non-sided access)
     *       → insert-only INPUT view restricted to SLIME_BUCKET.</li>
     * </ul>
     *
     * <p>The views themselves live on {@link com.flatts.productivefrogs.content.block.entity.SlimeMilkerInventory};
     * this listener just routes by side.
     */
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // 1.21.1 NeoForge: Capabilities.ItemHandler.BLOCK (returns IItemHandler).
        // The Capabilities.Item.BLOCK rename + ResourceHandler<ItemResource>
        // transfer-API surface only landed in 1.21.4+.
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.SLIME_MILKER.get(),
            (be, side) -> {
                if (side == Direction.DOWN) {
                    return be.getInventory().outputView();
                }
                return be.getInventory().inputView();
            }
        );

        // Slime Churn (#187): bottom face = extract-only view over BOTH output
        // slots (slime buckets + spent containers); every other face = the
        // insert view over both input slots, routed by per-slot validity
        // (milk buckets to the milk slot, empties to the bucket slot).
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.SLIME_CHURN.get(),
            (be, side) -> side == Direction.DOWN
                ? be.getInventory().outputView()
                : be.getInventory().inputView()
        );

        // Spawnery: bottom face = extract-only output; every other face = the
        // insert view over the three input slots (bottle / fuel / primer), which
        // routes each pushed item to the slot that accepts it.
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.SPAWNERY.get(),
            (be, side) -> side == Direction.DOWN
                ? be.getInventory().outputView()
                : be.getInventory().inputView()
        );

        // Crucible (v1.12): extract-only fluid tank for pipes and the bucket
        // right-click (FluidUtil walks this same capability) - fill() is a
        // no-op by design - plus an insert-only, recipe-validated item view so
        // hoppers can feed Froglights into the solids queue (Ex Deorum
        // parity). See CrucibleBlockEntity.
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            PFBlockEntities.CRUCIBLE.get(),
            (be, side) -> be.fluidHandler()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.CRUCIBLE.get(),
            (be, side) -> be.itemHandler()
        );

        // Casting Mold (v1.12 wave 2): fill-ONLY fluid tank (recipe-
        // validated, single-fluid - pipes and buckets pour molten in; drain
        // is a no-op, committed molten only leaves as a cast item) plus an
        // extract-only item view so hoppers can pull cast ingots.
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            PFBlockEntities.CASTING_MOLD.get(),
            (be, side) -> be.fluidHandler()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.CASTING_MOLD.get(),
            (be, side) -> be.outputView()
        );

        // Distiller (#253): PF's first RF machine. A receive-only energy buffer
        // for power cables (extraction blocked - the distill loop spends it
        // internally), plus side-aware item views - the down face pulls the
        // rendered item, every other face feeds Prismatic Froglights in.
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            PFBlockEntities.DISTILLER.get(),
            (be, side) -> be.energyStorage()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.DISTILLER.get(),
            (be, side) -> side == Direction.DOWN ? be.outputView() : be.inputView()
        );

        // Alembic (#253): the lane's other RF machine. Receive-only energy buffer;
        // down face pulls the Mimic Slime Bucket, other faces feed bucket + item.
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            PFBlockEntities.ALEMBIC.get(),
            (be, side) -> be.energyStorage()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.ALEMBIC.get(),
            (be, side) -> side == Direction.DOWN ? be.outputView() : be.inputView()
        );

        // Terrarium Controller (#185): fill-only fluid intake for piped milk. The
        // catalyst components ride the FluidStack (via MilkFluidBucketWrapper), so
        // the Controller reads them back into a MilkCharge. Drain is a no-op (the
        // funnel converts fluid to charges, it is not a reservoir).
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            PFBlockEntities.TERRARIUM_CONTROLLER.get(),
            (be, side) -> be.fluidHandler()
        );

        // Terrarium Hatch (#185): the froglight output inventory, for piping out.
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.HATCH.get(),
            (be, side) -> be.inventory()
        );

        // End Crystal Receptacle (#249): insert-only - hoppers/pipes feed End
        // Crystals in; the dragon-altar summon spends them, so extraction is
        // blocked (a hopper below can't steal a primed crystal).
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.END_CRYSTAL_RECEPTACLE.get(),
            (be, side) -> be.insertOnlyHandler()
        );

        // End Dragon Altar Hatch (#249): the altar's output - pipes pull the
        // dragon's drops from any face (chest-style inventory).
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.END_DRAGON_ALTAR_HATCH.get(),
            (be, side) -> be.itemHandler()
        );

        // Wither Altar summon receptacles (#247): insert-only, like the dragon altar's
        // crystal sockets - pipes feed soul sand / skulls in; the summon spends them.
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.WITHER_SUMMON_RECEPTACLE.get(),
            (be, side) -> be.insertOnlyHandler()
        );

        // Wither Altar Hatch (#247): the altar's output - pipes pull the reward
        // from any face (chest-style inventory).
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PFBlockEntities.WITHER_ALTAR_HATCH.get(),
            (be, side) -> be.itemHandler()
        );

        // Per-variant Slime Milk buckets (v1.8) are SlimeMilkBucketItem extends
        // BucketItem. NeoForge only auto-registers FluidHandler.ITEM for the exact
        // BucketItem class, not subclasses, so wire each up explicitly - this is
        // what lets tank/pipe mods pump a variant's milk and round-trip it. The
        // component-preserving MilkFluidBucketWrapper additionally copies the
        // catalyst components onto the drained FluidStack (#185), so piped milk
        // keeps Count/Speed/Quantity/Infinite end to end; the variant rides the
        // fluid identity regardless.
        for (Identifier variantId : PFVariantMilk.registeredVariants()) {
            net.minecraft.world.item.Item bucket = PFVariantMilk.bucket(variantId);
            if (bucket != null) {
                event.registerItem(
                    Capabilities.FluidHandler.ITEM,
                    (stack, ctx) -> new com.flatts.productivefrogs.content.fluid.MilkFluidBucketWrapper(stack),
                    bucket
                );
            }
        }

        // Mimic Milk bucket (#253) is also a BucketItem subclass, so it needs the
        // same explicit FluidHandler.ITEM registration. Its wrapper additionally
        // preserves the synthesized item id onto the drained FluidStack so a
        // bucket <-> tank round-trip keeps it (MimicMilkFluidBucketWrapper).
        event.registerItem(
            Capabilities.FluidHandler.ITEM,
            (stack, ctx) -> new com.flatts.productivefrogs.content.fluid.MimicMilkFluidBucketWrapper(stack),
            PFItems.MIMIC_MILK_BUCKET.get()
        );

        // PORT-DROP(2.0): the brewed-Froglight Curios item capability returns with
        // Curios support as a 2.x minor (CuriosCompat removed in the 26.1 port).
        // Brewed Froglights keep their placed-aura + held-buff forms; only the
        // worn-charm slot is gone until Curios is re-added.
    }

    /**
     * Register NeoForge data maps - currently just the Crucible's
     * {@code crucible_heat} block -> heat-value map (see
     * {@link com.flatts.productivefrogs.registry.PFDataMaps}).
     */
    @SubscribeEvent
    public static void onRegisterDataMaps(net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent event) {
        event.register(com.flatts.productivefrogs.registry.PFDataMaps.CRUCIBLE_HEAT);
        event.register(com.flatts.productivefrogs.registry.PFDataMaps.FROGLIGHT_HEAT);
    }


    /**
     * Mirror of {@link Monster#checkMonsterSpawnRules} but typed against
     * {@link Mob} so it accepts our Slime-derived parent species (Slime does
     * not extend Monster in vanilla).
     */
    private static <T extends Mob> boolean checkParentSlimeSpawnRules(
        EntityType<T> type,
        ServerLevelAccessor level,
        EntitySpawnReason reason,
        BlockPos pos,
        RandomSource random
    ) {
        return level.getLevel().getDifficulty() != Difficulty.PEACEFUL
            && Monster.isDarkEnoughToSpawn(level, pos, random)
            && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

}
