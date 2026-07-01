package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFFluidTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
 * <p>26.1 R-1: Slime Milk is one component-carrying fluid, so a boss variant is
 * no longer a distinct {@code FluidType}. Detection is now two steps: the player's
 * feet must be in the single {@code slime_milk} FluidType, AND the source block at
 * that position must carry a boss variant on its BE (milk is source-only and never
 * spreads, so a standing player is always in a source cell with a BE). Keyed off
 * the same closed boss set as the catalyst blocks
 * ({@link PFBlocks#catalystForVariant()}'s key set). Self-registers on the game bus.
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
        // The player must be standing in the single slime_milk fluid...
        BlockPos feet = player.blockPosition();
        if (player.level().getFluidState(feet).getFluidType() != PFFluidTypes.SLIME_MILK_TYPE.get()) {
            return;
        }
        // ...and the source at that position must be a boss variant (read off its BE;
        // milk is source-only, so a standing player is in a source cell with a BE).
        if (!(player.level().getBlockEntity(feet) instanceof SlimeMilkSourceBlockEntity be)) {
            return;
        }
        Identifier variant = be.getVariantId();
        if (variant != null && PFBlocks.catalystForVariant().containsKey(variant)) {
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_DURATION, WITHER_AMPLIFIER));
        }
    }
}
