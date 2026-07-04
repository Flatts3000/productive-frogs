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

    /**
     * Vanilla's sound wiring, fixed: this behavior is only ever built with the
     * stock frog sounds (and the predator eat re-plays FROG_EAT itself since the
     * base field is private), so the constructor takes none - a configurable
     * param that only half the paths honored was API fiction.
     */
    public PFShootTongue() {
        super(net.minecraft.sounds.SoundEvents.FROG_TONGUE, net.minecraft.sounds.SoundEvents.FROG_EAT);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Frog body) {
        // Species/Midas frogs run vanilla's own gate (no frozen copy to drift on
        // the next port); only the predator arm needs the replicated bookkeeping
        // with prey-registry edibility instead of Frog.canEat.
        if (!(body instanceof ResourceFrog frog) || !isHunterKind(frog.getKind())) {
            return super.checkExtraStartConditions(level, body);
        }
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
        return canPathfindToTarget && body.getPose() != Pose.CROAKING
            && isEligiblePrey(frog, frog.getKind(), target);
    }

    /** Kinds whose tongue bypasses vanilla's frog_food gate: predators + apex (Phase 4). */
    public static boolean isHunterKind(FrogKind kind) {
        return kind instanceof FrogKind.Predator || kind instanceof FrogKind.Apex;
    }

    /**
     * Kind-dispatched edibility: a Predator's prey registry, or an Apex's ONE
     * boss (Phase 4 - matched by entity type id). Species/Midas return false -
     * their edibility is vanilla's own gate, never this path.
     */
    public static boolean isEligiblePrey(ResourceFrog frog, FrogKind kind, LivingEntity target) {
        return switch (kind) {
            case FrogKind.Predator p -> isEligiblePrey(frog, p, target);
            case FrogKind.Apex a -> isEligiblePrey(frog, a, target);
            case FrogKind.Resource r -> false;
            case FrogKind.Midas m -> false;
        };
    }

    /**
     * Apex edibility (Phase 4): its one boss, by entity type id, with the
     * predation system on. Bosses are usually farmed at the altar; this live
     * gate is the both-layer guarantee that an Apex targets nothing else.
     */
    public static boolean isEligiblePrey(ResourceFrog frog, FrogKind.Apex apex, LivingEntity target) {
        if (!PFConfig.predatorsEnabled() || !(target instanceof Mob) || !target.isAlive()) {
            return false;
        }
        return net.minecraft.world.entity.EntityType.getKey(target.getType())
            .toString().equals(apex.bossEntityId());
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
        // Vanilla-frog parity for slime-family prey: size 1 only. Without this,
        // one-shotting a big vanilla slime/magma cube triggers the death split,
        // and every child is fresh eligible prey - a looting-scaled loot cascade
        // per encounter no other prey entry has (review finding #4).
        if (target instanceof net.minecraft.world.entity.monster.Slime slime && slime.getSize() != 1) {
            return false;
        }
        FrogKind.Predator mapped = PredatorPrey.predatorFor(
            PFRegistries.predatorPrey(frog.level().registryAccess()), target.getType());
        return mapped == predator;
    }

    @Override
    protected void eatEntity(ServerLevel level, Frog body) {
        if (!(body instanceof ResourceFrog frog) || !isHunterKind(frog.getKind())) {
            super.eatEntity(level, body);
            return;
        }
        // Predator/Apex eat: vanilla's own sound + target resolution, then the
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
        // Predation milestone (#281 Phase 5): a mob farmed by a predator frog.
        com.flatts.productivefrogs.registry.PFCriterionTriggers.PREDATION_MILESTONE.get().awardNearby(
            level, frog.position(),
            com.flatts.productivefrogs.advancement.PredationMilestoneTrigger.Milestone.MOB_FARMED);
        // Drop the sword reference so the fake player doesn't pin the enchanted
        // stack (the factory caches the player per level).
        killer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static String typeKey(LivingEntity prey) {
        return net.minecraft.world.entity.EntityType.getKey(prey.getType()).toString();
    }
}
