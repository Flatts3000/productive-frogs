package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFAttachments;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Drives the Princess's Kiss (#216): the timed frog -> villager conversion, and
 * the Ender Dragon drop that grants the Kiss.
 *
 * <p>The conversion countdown lives in the {@link PFAttachments#PRINCESS_CONVERTING}
 * data attachment (so it works on any frog and survives a reload). Each server
 * tick of a converting frog this decrements it, sprinkles particles, and at zero
 * replaces the frog with a plain villager.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class PrincessKissHandler {

    /** Conversion time once kissed (~10s), like a quick zombie cure. */
    public static final int CONVERSION_TICKS = 200;

    private PrincessKissHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        if (!entity.hasData(PFAttachments.PRINCESS_CONVERTING)) {
            return;
        }
        // Feature turned off mid-conversion: cancel cleanly, frog stays a frog.
        if (!PFConfig.princessKissEnabled()) {
            entity.removeData(PFAttachments.PRINCESS_CONVERTING);
            return;
        }
        int ticks = entity.getData(PFAttachments.PRINCESS_CONVERTING) - 1;
        level.sendParticles(ParticleTypes.HEART,
            entity.getX(), entity.getY() + 0.5, entity.getZ(), 1, 0.3, 0.3, 0.3, 0.0);
        if (ticks <= 0) {
            entity.removeData(PFAttachments.PRINCESS_CONVERTING);
            convertToVillager(entity, level);
        } else {
            entity.setData(PFAttachments.PRINCESS_CONVERTING, ticks);
        }
    }

    /** Replace the frog with a plain (unemployed, never nitwit) villager at its spot. */
    private static void convertToVillager(Entity frog, ServerLevel level) {
        Villager villager = EntityType.VILLAGER.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (villager == null) {
            return;
        }
        villager.snapTo(frog.getX(), frog.getY(), frog.getZ(), frog.getYRot(), frog.getXRot());
        // finalizeSpawn leaves the villager unemployed (profession NONE) - not a
        // nitwit, which must be set explicitly - so it can take a job.
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(frog.blockPosition()),
            EntitySpawnReason.CONVERSION, null);
        // CONVERSION spawns don't pick a biome villager type, so it would default
        // to PLAINS everywhere; set it from the conversion biome explicitly.
        villager.setVillagerData(villager.getVillagerData()
            .setType(VillagerType.byBiome(level.getBiome(frog.blockPosition()))));
        if (frog.hasCustomName()) {
            villager.setCustomName(frog.getCustomName());
        }
        level.addFreshEntity(villager);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
            frog.getX(), frog.getY() + 0.5, frog.getZ(), 20, 0.4, 0.5, 0.4, 0.0);
        // The cured-zombie-villager fanfare, to match the vanilla conversion.
        level.playSound(null, frog.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED,
            SoundSource.NEUTRAL, 1.0F, 1.0F);
        frog.discard();
    }

    /**
     * The Ender Dragon - the princess - drops one Princess's Kiss when slain. The
     * dragon's death does NOT fire {@code LivingDeathEvent}: its custom death
     * animation calls {@code remove(RemovalReason.KILLED)} directly, bypassing
     * {@code LivingEntity#die}. So hook removal instead and filter to a killed
     * dragon, spawning the Kiss at its position. Drops on every kill (re-fights via
     * end crystals cost the player, so a guaranteed drop is the intended gate).
     */
    @SubscribeEvent
    public static void onDragonRemoved(EntityLeaveLevelEvent event) {
        if (!PFConfig.princessKissEnabled()) {
            return;
        }
        Entity dragon = event.getEntity();
        if (dragon.getType() != EntityType.ENDER_DRAGON
                || dragon.getRemovalReason() != Entity.RemovalReason.KILLED
                || !(dragon.level() instanceof ServerLevel level)) {
            return;
        }
        ItemEntity drop = new ItemEntity(level, dragon.getX(), dragon.getY(), dragon.getZ(),
            new ItemStack(PFItems.PRINCESS_KISS.get()));
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }
}
