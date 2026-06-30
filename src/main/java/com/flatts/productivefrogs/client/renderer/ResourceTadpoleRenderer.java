package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import net.minecraft.client.model.animal.frog.TadpoleModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TadpoleRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.animal.frog.Tadpole;

/**
 * Resource Tadpole renderer - extends vanilla {@link TadpoleRenderer}. Same
 * tint-via-RenderLayer pattern as {@link ResourceFrogRenderer}: 26.1 has no
 * per-instance colour hook on the renderer, so we layer a tinted body render on
 * top of the vanilla white pass.
 *
 * <p>Vanilla {@code TadpoleRenderer} uses a bare {@link LivingEntityRenderState}
 * (the tadpole texture is fixed), so the category tint is extracted into a
 * {@link ResourceTadpoleRenderState} once per frame ({@link #extractRenderState});
 * the layer reads the state, not the entity.
 */
public class ResourceTadpoleRenderer extends TadpoleRenderer {

    public ResourceTadpoleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new CategoryTintLayer<LivingEntityRenderState, TadpoleModel>(
            this,
            this::getTextureLocation,
            state -> state instanceof ResourceTadpoleRenderState rs ? rs.tint : 0xFFFFFFFF
        ));
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new ResourceTadpoleRenderState();
    }

    @Override
    public void extractRenderState(Tadpole entity, LivingEntityRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (state instanceof ResourceTadpoleRenderState rs && entity instanceof ResourceTadpole rt) {
            rs.tint = 0xFF000000 | rt.getCategory().tintRgb();
        }
    }
}
