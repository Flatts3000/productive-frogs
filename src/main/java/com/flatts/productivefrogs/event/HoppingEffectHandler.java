package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/**
 * Drives the Hopping effect (#215): while {@link PFEffects#HOPPING} is active, a
 * jump gets a forward horizontal impulse (a frog leap), and falls are softened so
 * leaping off a ledge does not punish - frogs do not splat.
 *
 * <p>Applied in {@link LivingEvent.LivingJumpEvent}, the same hook vanilla Jump
 * Boost uses, so it runs on whichever side simulates the entity (client for the
 * local player, server for mobs) and stays in sync the same way Jump Boost does.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class HoppingEffectHandler {

    /** Forward velocity added per amplifier level. */
    private static final double POWER_PER_LEVEL = 0.45;
    /** Blocks of fall distance the effect shaves off (so a leap landing is survivable). */
    private static final float FALL_REDUCTION = 5.0F;

    private HoppingEffectHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!PFConfig.hoppingEnabled()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        MobEffectInstance hopping = entity.getEffect(PFEffects.HOPPING);
        if (hopping == null) {
            return;
        }
        Vec3 look = entity.getLookAngle();
        double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
        if (horizontal < 1.0e-4) {
            return; // looking straight up/down: nothing to push forward
        }
        double power = POWER_PER_LEVEL * (hopping.getAmplifier() + 1);
        Vec3 d = entity.getDeltaMovement();
        entity.setDeltaMovement(d.x + look.x / horizontal * power, d.y, d.z + look.z / horizontal * power);
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!PFConfig.hoppingEnabled()) {
            return;
        }
        if (event.getEntity().hasEffect(PFEffects.HOPPING)) {
            event.setDistance(Math.max(0.0F, event.getDistance() - FALL_REDUCTION));
        }
    }
}
