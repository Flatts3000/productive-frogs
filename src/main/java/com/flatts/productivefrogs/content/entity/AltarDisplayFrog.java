package com.flatts.productivefrogs.content.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Base class for the boss altars' display frogs (#249/#247/#279/#280) -
 * Dragonsbane, Witherbane, Wardenbane, Elderbane. NOT real creatures: no AI, no
 * gravity, never move, invulnerable and silent; each altar's Hatch ticker spawns
 * its frog when the structure is valid + armed and removes it when broken.
 *
 * <p>Extends vanilla {@link Frog} so the stock frog model/renderer and the
 * tongue/eat animation work unchanged; the vanilla brain never ticks. Subclasses
 * supply only their {@linkplain #ambientParticle() ambient aura particle} - the
 * per-altar tint lives in each renderer, not here.
 */
public abstract class AltarDisplayFrog extends Frog {

    /** Ticks remaining in the tongue/eat animation (server-driven; resets the pose at 0). */
    private int eatTicks;

    @SuppressWarnings("unchecked")
    protected AltarDisplayFrog(EntityType<? extends AltarDisplayFrog> type, Level level) {
        super((EntityType<? extends Frog>) (EntityType<?>) type, level);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    /** The ambient aura particle this display frog emits (soul, portal, sculk...). */
    protected abstract ParticleOptions ambientParticle();

    /**
     * Lash the tongue (the boss-eat). Setting the pose to {@link Pose#USING_TONGUE}
     * starts vanilla {@link Frog}'s {@code tongueAnimationState} client-side; the
     * pose resets after the animation window (see {@link #tick()}).
     */
    public void triggerEat() {
        setPose(Pose.USING_TONGUE);
        this.eatTicks = 12;
    }

    /** No brain tick - the display frog never hunts, jumps, or wanders. */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        // intentionally empty
    }

    /** Reset the eat pose server-side; emit the subclass's ambient aura client-side. */
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
            level().addParticle(ambientParticle(), px, py, pz,
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
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false; // invulnerable display entity
    }
}
