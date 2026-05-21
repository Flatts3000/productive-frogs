package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Mod-event-bus listeners for one-time registration callbacks that need an
 * event instead of (or in addition to) a DeferredRegister. Currently just
 * the entity attribute registration; renderer registration lives in
 * client-only code.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class PFModBusEvents {

    private PFModBusEvents() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onCreateAttributes(EntityAttributeCreationEvent event) {
        // ResourceTadpole reuses vanilla Tadpole's attribute table verbatim.
        event.put(PFEntities.RESOURCE_TADPOLE.get(), Tadpole.createAttributes().build());
        event.put(PFEntities.RESOURCE_FROG.get(), ResourceFrog.createAttributes().build());
        // ResourceSlime uses the standard Monster attribute table — same baseline
        // vanilla EntityType.SLIME uses (via Monster.createMonsterAttributes).
        // Per-size HP/movement scaling happens in Slime#setSize at runtime,
        // not via the attribute table itself.
        event.put(PFEntities.RESOURCE_SLIME.get(), Monster.createMonsterAttributes().build());
        // Cave Slime and all future parent species (Geode/Tide/Void) reuse
        // the same Monster baseline — they're vanilla-shaped Slime subclasses.
        event.put(PFEntities.CAVE_SLIME.get(), Monster.createMonsterAttributes().build());
        event.put(PFEntities.GEODE_SLIME.get(), Monster.createMonsterAttributes().build());
    }
}
