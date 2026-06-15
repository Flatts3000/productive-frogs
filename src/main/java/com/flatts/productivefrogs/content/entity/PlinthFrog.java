package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
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

    @SuppressWarnings("unchecked")
    public PlinthFrog(EntityType<? extends PlinthFrog> type, Level level) {
        super((EntityType<? extends Frog>) (EntityType<?>) type, level);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    /** No brain tick - the display frog never hunts, jumps, or wanders. */
    @Override
    protected void customServerAiStep() {
        // intentionally empty
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
