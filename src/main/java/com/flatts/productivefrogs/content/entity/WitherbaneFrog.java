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
 * The Wither Altar's display frog (#247) - "Witherbane". A display-only frog the
 * altar pins at one end of the arena, facing the ritual, that devours the summoned
 * Wither replica. Functionally identical to the dragon altar's display frog
 * ({@link DragonsbaneFrog}): no AI, no gravity, never moves, invulnerable, silent; the
 * Hatch ticker spawns it when the structure is valid and removes it when broken.
 *
 * <p>Extends vanilla {@link Frog} so it renders with the stock frog model + the
 * tongue/eat animation, recoloured to an infernal hue by its renderer. Ambient soul
 * particles read as a soul-devouring apex frog.
 */
public class WitherbaneFrog extends Frog {

    /** Ticks remaining in the tongue/eat animation (server-driven; resets the pose at 0). */
    private int eatTicks;

    @SuppressWarnings("unchecked")
    public WitherbaneFrog(EntityType<? extends WitherbaneFrog> type, Level level) {
        super((EntityType<? extends Frog>) (EntityType<?>) type, level);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    /**
     * Lash the tongue (the Wither-eat). Setting the pose to {@link Pose#USING_TONGUE}
     * starts vanilla {@link Frog}'s {@code tongueAnimationState} client-side; the pose
     * resets after the animation window (see {@link #tick()}).
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

    /** Ambient soul aura - a few rising soul particles so it reads as soul-devouring. */
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
            level().addParticle(ParticleTypes.SOUL, px, py, pz,
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
    public static EntityType<WitherbaneFrog> type() {
        return PFEntities.WITHERBANE.get();
    }
}
