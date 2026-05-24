package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TadpoleRenderer;
import net.minecraft.world.entity.animal.frog.Tadpole;

/**
 * Resource Tadpole renderer — extends vanilla {@link TadpoleRenderer}. Same
 * tint-via-RenderLayer pattern as {@link ResourceFrogRenderer}: 1.21.1 has
 * no per-instance colour hook on the renderer, so we layer a tinted body
 * render on top of the vanilla white pass.
 */
public class ResourceTadpoleRenderer extends TadpoleRenderer {

    public ResourceTadpoleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new CategoryTintLayer<Tadpole, net.minecraft.client.model.TadpoleModel<Tadpole>>(
            this,
            this::getTextureLocation,
            entity -> {
                if (entity instanceof ResourceTadpole rt) {
                    return 0xFF000000 | rt.getCategory().tintRgb();
                }
                return 0xFFFFFFFF;
            }
        ));
    }
}
