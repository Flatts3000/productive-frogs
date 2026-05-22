package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
