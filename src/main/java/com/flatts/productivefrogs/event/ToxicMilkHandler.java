package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Boss-tier Slime Milk is <b>toxic to players</b> (#184): standing in a boss
 * variant's milk (Nether Star / Dragon Egg / Wither Skeleton Skull / Dragon
 * Breath) inflicts Wither. This is the narrative reason the boss sources are
 * caged in a catalyst altar - the milk is dangerous, so you contain it.
 *
 * <p><b>Players only</b> (the decided framing): the boss slimes the source
 * spawns wade through their own milk constantly and must not poison themselves,
 * so non-player {@link net.minecraft.world.entity.LivingEntity}s are untouched.
 * Creative/spectator players are exempt (building/observing the altar).
 *
 * <p>Keyed off the same closed boss set as the catalyst blocks
 * ({@link PFBlocks#catalystForVariant()}'s key set) - both source and flowing
 * milk of a variant share one {@link FluidType}, so {@code isInFluidType} catches
 * a player standing in either. Self-registers on the game event bus.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class ToxicMilkHandler {

    /** Re-check cadence: once a second is plenty to keep the effect topped up. */
    private static final int CHECK_INTERVAL = 20;
    /** Wither duration per application (4s), comfortably longer than the interval so it never drops while immersed. */
    private static final int WITHER_DURATION = 80;
    /** Wither I - dangerous (it can kill, unlike Poison) but escapable; the boss-tier "get out" signal. */
    private static final int WITHER_AMPLIFIER = 0;

    private ToxicMilkHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.isCreative() || player.isSpectator()) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        for (Identifier bossVariant : PFBlocks.catalystForVariant().keySet()) {
            FluidType type = PFVariantMilk.fluidType(bossVariant);
            if (type != null && player.isInFluidType(type)) {
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION, WITHER_AMPLIFIER));
                return;
            }
        }
    }
}
