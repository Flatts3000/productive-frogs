package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom {@link MobEffect}s registered by Productive Frogs.
 *
 * <p>{@link #HOPPING} (#215) is a frog-themed mobility effect: while active, a jump
 * adds a forward horizontal impulse (handled by {@code HoppingEffectHandler}), a
 * forward leap distinct from vanilla Jump Boost's vertical-only boost. The effect
 * carries no per-tick logic of its own - the jump event does the work.
 */
public final class PFEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
        DeferredRegister.create(Registries.MOB_EFFECT, ProductiveFrogs.MOD_ID);

    /** Forward-leap-on-jump. Greenish, beneficial; the brewed Potion of Hopping (#215). */
    public static final Holder<MobEffect> HOPPING = MOB_EFFECTS.register(
        "hopping",
        () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x88CC44) { });

    private PFEffects() {
        // registry holder
    }

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
