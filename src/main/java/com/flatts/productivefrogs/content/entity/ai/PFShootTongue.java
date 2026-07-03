package com.flatts.productivefrogs.content.entity.ai;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.FrogStats;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.data.PredatorPrey;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.ShootTongue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * The kind-aware tongue behavior (#281) - replaces vanilla {@link ShootTongue}
 * in the {@link ResourceFrog} brain's TONGUE activity. Two overrides:
 *
 * <ul>
 *   <li><b>Edibility</b> ({@link #checkExtraStartConditions}): vanilla gates on
 *       {@link Frog#canEat} (the {@code frog_food} entity tag + size-1 slimes),
 *       which is right for the species and Midas paths but can never admit a
 *       Predator's prey - tagging zombies/cows as {@code frog_food} would make
 *       VANILLA frogs hunt them. A predator instead gates on the
 *       {@code predator_prey} datapack registry (its kind must match the
 *       entry). The pathfind/unreachable bookkeeping is vanilla's own
 *       (AT-opened helpers), so behavior stays identical otherwise.</li>
 *   <li><b>The eat</b> ({@link #eatEntity}): a species/Midas eat defers to
 *       vanilla (tongue damage; the drop handlers roll Froglights). A predator
 *       eat is a <b>fake-player kill</b>: a server {@link FakePlayer} wielding
 *       a sword enchanted with looting = the frog's Bounty mapping
 *       ({@link FrogStats#bountyLootingLevel}) deals lethal damage, so the prey
 *       dies a genuine player death - its player-kill-gated drops, looting
 *       scaling, and XP orbs all come out of the vanilla death pipeline with no
 *       loot-table emulation (the exact "drops = as if the mob was
 *       player-killed" rule). Loot + orbs land at the prey, beside the frog.</li>
 * </ul>
 */
public class PFShootTongue extends ShootTongue {

    /** Stable identity for the predation kill credit; one fake player per level, cached by NeoForge. */
    private static final GameProfile PREDATOR_PROFILE = new GameProfile(
        UUID.fromString("d1a2b7f0-81c4-4c69-9a73-6a0f8f2c281e"), "[PF] Predator Frog");

    public PFShootTongue(SoundEvent tongueSound, SoundEvent eatSound) {
        super(tongueSound, eatSound);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Frog body) {
        Optional<LivingEntity> memory = body.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        if (memory.isEmpty()) {
            return false;
        }
        LivingEntity target = memory.get();
        // Vanilla's pathfind + unreachable bookkeeping, via the AT-opened helpers.
        boolean canPathfindToTarget = this.canPathfindToTarget(body, target);
        if (!canPathfindToTarget) {
            body.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            this.addUnreachableTargetToMemory(body, target);
        }
        return canPathfindToTarget && body.getPose() != Pose.CROAKING && canEatByKind(body, target);
    }

    /** Kind-aware edibility - the tongue-side twin of the sensor's diet switch. */
    private static boolean canEatByKind(Frog body, LivingEntity target) {
        if (body instanceof ResourceFrog frog && frog.getKind() instanceof FrogKind.Predator predator) {
            return isEligiblePrey(frog, predator, target);
        }
        return Frog.canEat(target);
    }

    /**
     * Whether {@code target} is this predator's prey: a live {@link Mob} whose
     * EntityType maps to this predator kind in the {@code predator_prey}
     * registry, with the predation system on. Shared by the prey sensor.
     */
    public static boolean isEligiblePrey(ResourceFrog frog, FrogKind.Predator predator, LivingEntity target) {
        if (!PFConfig.predatorsEnabled() || !(target instanceof Mob) || !target.isAlive()) {
            return false;
        }
        FrogKind.Predator mapped = PredatorPrey.predatorFor(
            PFRegistries.predatorPrey(frog.level().registryAccess()), target.getType());
        return mapped == predator;
    }

    @Override
    protected void eatEntity(ServerLevel level, Frog body) {
        if (!(body instanceof ResourceFrog frog) || !(frog.getKind() instanceof FrogKind.Predator)) {
            super.eatEntity(level, body);
            return;
        }
        // Predator eat: vanilla's own sound + target resolution, then the
        // fake-player kill instead of tongue damage.
        level.playSound(null, frog, net.minecraft.sounds.SoundEvents.FROG_EAT,
            net.minecraft.sounds.SoundSource.NEUTRAL, 2.0F, 1.0F);
        Optional<Entity> tongueTarget = frog.getTongueTarget();
        if (tongueTarget.isEmpty() || !(tongueTarget.get() instanceof LivingEntity prey) || !prey.isAlive()) {
            return;
        }
        devour(level, frog, prey);
    }

    /**
     * The predation kill (#281): lethal damage from a {@link FakePlayer} whose
     * mainhand sword carries looting = the frog's Bounty mapping. The prey dies
     * a genuine player kill - player-gated drops, looting, and XP orbs all flow
     * from the vanilla death pipeline at the prey's position. Public seam so the
     * GameTests drive the exact production code path.
     */
    public static void devour(ServerLevel level, ResourceFrog frog, LivingEntity prey) {
        FakePlayer killer = FakePlayerFactory.get(level, PREDATOR_PROFILE);
        killer.setPos(frog.getX(), frog.getY(), frog.getZ());
        int looting = FrogStats.bountyLootingLevel(frog.effectiveBounty(), PFConfig.statCap());
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        if (looting > 0) {
            Holder<Enchantment> lootingEnchant = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.LOOTING);
            sword.enchant(lootingEnchant, looting);
        }
        killer.setItemInHand(InteractionHand.MAIN_HAND, sword);
        PFDebug.log(PFDebug.Area.TONGUE, () -> String.format(
            "predator eat: %s (bounty=%d -> looting %d) devours %s",
            frog.getKind().id(), frog.effectiveBounty(), looting,
            typeKey(prey)));
        prey.hurtServer(level, level.damageSources().playerAttack(killer), Float.MAX_VALUE);
        // Pace exactly like a slime eat: the Appetite cooldown gates the next hunt.
        frog.startEatCooldown();
        // Drop the sword reference so the fake player doesn't pin the enchanted
        // stack (the factory caches the player per level).
        killer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static String typeKey(LivingEntity prey) {
        return net.minecraft.world.entity.EntityType.getKey(prey.getType()).toString();
    }
}
