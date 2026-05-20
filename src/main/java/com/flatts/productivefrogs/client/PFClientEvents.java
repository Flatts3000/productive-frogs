package com.flatts.productivefrogs.client;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.client.renderer.entity.TadpoleRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only setup. Registers entity renderers for our Resource Tadpole and
 * Resource Frog using vanilla's renderers — same models, same textures, same
 * animations. Per-category visual variation (tinting) lands with the texture
 * batch; for now both entities look exactly like their vanilla counterparts.
 *
 * <p>Type-erasure note: vanilla renderers are parameterized on {@code Tadpole}
 * and {@code Frog} respectively. Since our entities subclass those, the
 * renderers work over our entities without modification.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID, value = Dist.CLIENT)
public final class PFClientEvents {

    private PFClientEvents() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PFEntities.RESOURCE_TADPOLE.get(), TadpoleRenderer::new);
        event.registerEntityRenderer(PFEntities.RESOURCE_FROG.get(), FrogRenderer::new);
    }
}
