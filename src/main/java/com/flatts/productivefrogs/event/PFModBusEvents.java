package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.world.entity.animal.frog.Tadpole;
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
    }
}
