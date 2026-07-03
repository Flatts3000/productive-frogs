package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ai.PFShootTongue;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.data.PredatorPrey;
import com.flatts.productivefrogs.event.PredationTeleportHandler;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/**
 * The predation eat path (#281, Phase 1): the hand-authored prey registry, the
 * kind-aware prey sensor, the fake-player devour (player-kill loot + XP orbs +
 * the Appetite cooldown), the teleport lock, and the amphibious Gulper.
 */
final class PredatorEatPathTests {

    private PredatorEatPathTests() {
    }

    static void register() {
        PFGameTests.test("prey_registry_maps_the_settled_assignments", 20,
            PredatorEatPathTests::preyRegistryMapsTheSettledAssignments);
        PFGameTests.test("prowler_devours_zombie_for_player_kill_loot_and_xp", 100,
            PredatorEatPathTests::prowlerDevoursZombieForPlayerKillLootAndXp);
        PFGameTests.test("predator_sensor_targets_prey_and_ignores_slimes", 120,
            PredatorEatPathTests::predatorSensorTargetsPreyAndIgnoresSlimes);
        PFGameTests.test("species_frog_never_targets_vanilla_mobs", 120,
            PredatorEatPathTests::speciesFrogNeverTargetsVanillaMobs);
        PFGameTests.test("teleport_lock_cancels_ender_teleport", 20,
            PredatorEatPathTests::teleportLockCancelsEnderTeleport);
        PFGameTests.test("gulper_breathes_underwater_other_frogs_do_not", 20,
            PredatorEatPathTests::gulperBreathesUnderwaterOtherFrogsDoNot);
    }

    /** Spot-checks of the shipped predator_prey JSONs against the settled #281 map. */
    private static void preyRegistryMapsTheSettledAssignments(GameTestHelper helper) {
        var registry = PFRegistries.predatorPrey(helper.getLevel().registryAccess());
        record Pin(EntityType<?> type, FrogKind.Predator expected) {}
        List<Pin> pins = List.of(
            new Pin(EntityType.ZOMBIE, FrogKind.Predator.PROWLER),
            new Pin(EntityType.CREEPER, FrogKind.Predator.PROWLER),
            new Pin(EntityType.IRON_GOLEM, FrogKind.Predator.PROWLER),
            new Pin(EntityType.ENDERMAN, FrogKind.Predator.PROWLER),
            new Pin(EntityType.PARCHED, FrogKind.Predator.PROWLER),
            new Pin(EntityType.BLAZE, FrogKind.Predator.CINDER),
            new Pin(EntityType.WITHER_SKELETON, FrogKind.Predator.CINDER),
            new Pin(EntityType.GUARDIAN, FrogKind.Predator.GULPER),
            new Pin(EntityType.NAUTILUS, FrogKind.Predator.GULPER),
            new Pin(EntityType.SHULKER, FrogKind.Predator.RIFT)
        );
        for (Pin pin : pins) {
            FrogKind.Predator mapped = PredatorPrey.predatorFor(registry, pin.type());
            if (mapped != pin.expected()) {
                helper.fail(EntityType.getKey(pin.type()) + " maps to " + mapped + ", expected " + pin.expected());
                return;
            }
        }
        // Bosses + no-kill-drop mobs are deliberately absent.
        if (PredatorPrey.predatorFor(registry, EntityType.WITHER) != null
                || PredatorPrey.predatorFor(registry, EntityType.BAT) != null
                || PredatorPrey.predatorFor(registry, EntityType.VILLAGER) != null) {
            helper.fail("bosses / no-drop mobs / villagers must not be prey");
            return;
        }
        helper.succeed();
    }

    /**
     * The devour seam (the exact code path {@code PFShootTongue.eatEntity} runs):
     * the prey dies a player kill - rotten flesh on the ground (8 zombies make a
     * zero-roll across all of them cosmically unlikely), XP orbs out, the
     * predator's Appetite cooldown armed, and no Froglight anywhere.
     */
    private static void prowlerDevoursZombieForPlayerKillLootAndXp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceFrog prowler = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        prowler.setKind(FrogKind.Predator.PROWLER);
        prowler.setStats(5, 10, 5); // max Bounty -> Looting III on the roll

        for (int i = 0; i < 8; i++) {
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 3));
            PFShootTongue.devour(level, prowler, zombie);
            if (zombie.isAlive()) {
                helper.fail("devoured zombie survived the fake-player kill");
                return;
            }
        }

        helper.succeedWhen(() -> {
            List<ItemEntity> items = helper.getEntities(EntityType.ITEM);
            boolean flesh = items.stream().anyMatch(e -> e.getItem().is(Items.ROTTEN_FLESH));
            List<ExperienceOrb> orbs = helper.getEntities(EntityType.EXPERIENCE_ORB);
            if (!flesh) {
                helper.fail("no rotten flesh dropped across 8 player-kill devours");
            }
            if (orbs.isEmpty()) {
                helper.fail("no XP orbs dropped from the player-kill devours");
            }
            if (!prowler.getBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN)) {
                helper.fail("devour did not arm the Appetite eat cooldown");
            }
        });
    }

    /**
     * Sensor layer, live: a Prowler acquires a zombie as NEAREST_ATTACKABLE but
     * never a Resource Slime standing beside it (the kind-diet switch).
     */
    private static void predatorSensorTargetsPreyAndIgnoresSlimes(GameTestHelper helper) {
        ResourceFrog prowler = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        prowler.setKind(FrogKind.Predator.PROWLER);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
        zombie.setNoAi(true);
        sunProof(zombie);
        var slime = helper.spawn(PFEntities.BOG_SLIME.get(), new BlockPos(4, 2, 2));
        slime.setNoAi(true);

        helper.succeedWhen(() -> {
            var target = prowler.getBrain().getMemory(MemoryModuleType.NEAREST_ATTACKABLE);
            if (target.isEmpty()) {
                helper.fail("prowler has not acquired its prey yet");
                return;
            }
            if (target.get() != zombie) {
                helper.fail("prowler targeted " + target.get().getType() + " instead of the zombie");
            }
        });
    }

    /** A species frog's sensor never surfaces a vanilla mob (mutual exclusion, the other direction). */
    private static void speciesFrogNeverTargetsVanillaMobs(GameTestHelper helper) {
        ResourceFrog bog = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 2));
        bog.setKind(FrogKind.resource(Category.BOG));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
        zombie.setNoAi(true);
        sunProof(zombie);

        // Give the sensor several scan cycles, then assert it never bit.
        helper.runAfterDelay(80, () -> {
            if (bog.getBrain().getMemory(MemoryModuleType.NEAREST_ATTACKABLE).isPresent()) {
                helper.fail("a species frog surfaced a vanilla mob as prey");
                return;
            }
            helper.succeed();
        });
    }

    /** The teleport lock cancels the ender self-teleport event; an unlocked mob teleports normally. */
    /** GameTest worlds run at day - helmet a zombie so daylight doesn't kill it mid-test. */
    private static void sunProof(Zombie zombie) {
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
            new net.minecraft.world.item.ItemStack(Items.IRON_HELMET));
    }

    private static void teleportLockCancelsEnderTeleport(GameTestHelper helper) {
        var locked = helper.spawn(EntityType.ENDERMAN, new BlockPos(1, 2, 2));
        locked.setNoAi(true);
        PredationTeleportHandler.disableTeleport(locked);
        var free = helper.spawn(EntityType.ENDERMAN, new BlockPos(3, 2, 2));
        free.setNoAi(true);

        EntityTeleportEvent.EnderEntity lockedEvent =
            new EntityTeleportEvent.EnderEntity(locked, locked.getX() + 8, locked.getY(), locked.getZ());
        NeoForge.EVENT_BUS.post(lockedEvent);
        if (!lockedEvent.isCanceled()) {
            helper.fail("teleport-locked enderman's ender teleport was not cancelled");
            return;
        }
        EntityTeleportEvent.EnderEntity freeEvent =
            new EntityTeleportEvent.EnderEntity(free, free.getX() + 8, free.getY(), free.getZ());
        NeoForge.EVENT_BUS.post(freeEvent);
        if (freeEvent.isCanceled()) {
            helper.fail("an unlocked enderman's teleport must not be cancelled");
            return;
        }
        helper.succeed();
    }

    /** The Gulper's amphibious ability: it breathes underwater; other kinds keep vanilla drowning. */
    private static void gulperBreathesUnderwaterOtherFrogsDoNot(GameTestHelper helper) {
        ResourceFrog gulper = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(1, 2, 2));
        gulper.setKind(FrogKind.Predator.GULPER);
        ResourceFrog prowler = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(3, 2, 2));
        prowler.setKind(FrogKind.Predator.PROWLER);
        ResourceFrog tide = helper.spawn(PFEntities.RESOURCE_FROG.get(), new BlockPos(2, 2, 3));
        tide.setKind(FrogKind.resource(Category.TIDE));

        if (!gulper.canBreatheUnderwater()) {
            helper.fail("the Gulper must breathe underwater (its settled ability)");
            return;
        }
        if (prowler.canBreatheUnderwater() || tide.canBreatheUnderwater()) {
            helper.fail("only the Gulper gets the underwater-breathing ability");
            return;
        }
        helper.succeed();
    }
}
