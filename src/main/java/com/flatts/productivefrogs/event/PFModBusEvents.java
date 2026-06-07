package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
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

    @SubscribeEvent
    public static void onCreateAttributes(EntityAttributeCreationEvent event) {
        // ResourceTadpole reuses vanilla Tadpole's attribute table verbatim.
        event.put(PFEntities.RESOURCE_TADPOLE.get(), Tadpole.createAttributes().build());
        event.put(PFEntities.RESOURCE_FROG.get(), ResourceFrog.createAttributes().build());
        // ResourceSlime uses the standard Monster attribute table — same baseline
        // vanilla EntityType.SLIME uses (via Monster.createMonsterAttributes).
        // Per-size HP/movement scaling happens in Slime#setSize at runtime,
        // not via the attribute table itself.
        event.put(PFEntities.RESOURCE_SLIME.get(), Monster.createMonsterAttributes().build());
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
        net.minecraft.world.entity.MobSpawnType reason,
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

        // Per-variant Slime Milk buckets (v1.8) are SlimeMilkBucketItem extends
        // BucketItem. NeoForge only auto-registers FluidHandler.ITEM for the exact
        // BucketItem class, not subclasses, so wire each up explicitly - this is
        // what lets tank/pipe mods pump a variant's milk and round-trip it (the
        // bucket's content is its own variant fluid, so the vanilla
        // FluidBucketWrapper preserves the variant for free).
        for (ResourceLocation variantId : PFVariantMilk.registeredVariants()) {
            net.minecraft.world.item.Item bucket = PFVariantMilk.bucket(variantId);
            if (bucket != null) {
                event.registerItem(
                    Capabilities.FluidHandler.ITEM,
                    (stack, ctx) -> new net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper(stack),
                    bucket
                );
            }
        }

        // Brewed Froglight curio (#169) - register the Curios item capability
        // ONLY when curios is loaded. The call is behind the guard so
        // CuriosCompat (and the Curios API types it references) never classload
        // on a curios-less pack - the Jade/JEI soft-dep posture. Curios is
        // compileOnly + a run/mods drop-in, never bundled.
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
            com.flatts.productivefrogs.integration.curios.CuriosCompat.registerCapabilities(event);
        }
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
        MobSpawnType reason,
        BlockPos pos,
        RandomSource random
    ) {
        return level.getLevel().getDifficulty() != Difficulty.PEACEFUL
            && Monster.isDarkEnoughToSpawn(level, pos, random)
            && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

}
