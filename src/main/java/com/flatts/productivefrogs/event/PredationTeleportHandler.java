package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFAttachments;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/**
 * The teleport lock (#281): cancels ender-style teleports (enderman warp,
 * shulker relocate, endermite hop) for any entity carrying
 * {@link PFAttachments#TELEPORT_DISABLED} - the settled no-enclosures answer
 * for farming teleporters. A Mob Slurry Basin stamps the attachment on the
 * teleporters it spawns (predation Phase 3); a locked mob stays inside the
 * player's box for the predator frog to eat. Wild mobs never carry the
 * attachment and teleport normally.
 *
 * <p>Deliberately narrow: only {@link EntityTeleportEvent.EnderEntity} (the mob
 * self-teleport) is cancelled - ender pearls, chorus fruit, and command
 * teleports are untouched.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class PredationTeleportHandler {

    private PredationTeleportHandler() {
        // event handler, not instantiable
    }

    /** Stamp the teleport lock on {@code entity} (the Basin's spawn path + tests). */
    public static void disableTeleport(Entity entity) {
        entity.setData(PFAttachments.TELEPORT_DISABLED.get(), true);
    }

    /**
     * Whether {@code entity} carries the teleport lock. Guarded by hasData:
     * NeoForge's getData CREATES + persists + syncs the default on a miss, and
     * this runs on every wild teleporter's EnderEntity event - an unguarded read
     * would stamp save-data onto every enderman in the world (review finding #2).
     */
    public static boolean isTeleportDisabled(Entity entity) {
        return entity.hasData(PFAttachments.TELEPORT_DISABLED.get())
            && entity.getData(PFAttachments.TELEPORT_DISABLED.get());
    }

    @SubscribeEvent
    public static void onEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (isTeleportDisabled(event.getEntity())) {
            event.setCanceled(true);
            PFDebug.log(PFDebug.Area.SENSOR, () -> "teleport lock: cancelled ender teleport for "
                + event.getEntity().getType());
        }
    }
}
