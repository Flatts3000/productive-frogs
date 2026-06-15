package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The dragon altar's plinth frog (#249) - a display-only frog perched on the
 * bedrock plinth. NOT a real creature: it has no AI, no gravity, never moves, is
 * invulnerable and silent, and the altar (the Hatch ticker) spawns it when the
 * structure is valid and removes it when it is broken. It can be turned (yaw),
 * but it is pinned in place.
 *
 * <p>It extends vanilla {@link Frog} so it renders with the stock frog model +
 * texture (the vanilla {@code FrogRenderer} is registered for it) and so the
 * tongue/eat animation is available to the summon sequence later. The vanilla
 * brain never ticks ({@link #customServerAiStep} is a no-op and AI is off), so
 * none of the frog's behaviours run.
 */
public class PlinthFrog extends Frog {

    /** Ticks remaining in the tongue/eat animation (server-driven; resets the pose at 0). */
    private int eatTicks;

    @SuppressWarnings("unchecked")
    public PlinthFrog(EntityType<? extends PlinthFrog> type, Level level) {
        super((EntityType<? extends Frog>) (EntityType<?>) type, level);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    /**
     * Lash the tongue (the dragon-eat). Setting the pose to {@link Pose#USING_TONGUE}
     * makes vanilla {@link Frog} start its {@code tongueAnimationState} client-side;
     * the pose resets after the animation window (see {@link #tick()}).
     */
    public void triggerEat() {
        setPose(Pose.USING_TONGUE);
        this.eatTicks = 12;
    }

    /** No brain tick - the display frog never hunts, jumps, or wanders. */
    @Override
    protected void customServerAiStep() {
        // intentionally empty
    }

    /** Ambient ender aura - a few converging void particles so it reads as otherworldly. */
    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && eatTicks > 0 && --eatTicks == 0) {
            setPose(Pose.STANDING);
        }
        if (level().isClientSide() && this.random.nextInt(3) == 0) {
            double px = getX() + (this.random.nextDouble() - 0.5) * 0.9;
            double py = getY() + 0.4 + this.random.nextDouble() * 0.7;
            double pz = getZ() + (this.random.nextDouble() - 0.5) * 0.9;
            level().addParticle(ParticleTypes.REVERSE_PORTAL, px, py, pz,
                (this.random.nextDouble() - 0.5) * 0.02, 0.02, (this.random.nextDouble() - 0.5) * 0.02);
        }
    }

    @Override
    public void registerGoals() {
        // no goals
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // it never shoves anything, and nothing shoves it (isPushable=false)
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false; // not breedable / temptable
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false; // invulnerable display entity
    }

    /** The altar uses this type for spawn/lifecycle reconciliation. */
    public static EntityType<PlinthFrog> type() {
        return PFEntities.PLINTH_FROG.get();
    }
}
