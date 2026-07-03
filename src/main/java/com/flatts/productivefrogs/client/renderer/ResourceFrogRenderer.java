package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.entity.ResourceFrog;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import net.minecraft.world.entity.animal.frog.Frog;

/**
 * Resource Frog renderer - extends vanilla {@link FrogRenderer} so it inherits
 * the model, animation, and biome-variant texture selection. Adds a single
 * {@link CategoryTintLayer} that re-renders the body in the entity's category
 * tint on top of the vanilla white pass.
 *
 * <p>26.1 has no {@code getModelTint} hook on {@link FrogRenderer}, so we apply
 * the tint via a custom RenderLayer that runs after the main body render. The
 * tinted pass uses {@code RenderTypes.entityCutout} (the no-cull cutout type)
 * with our colour, fully overlaying the vanilla white body - the visible result
 * is the frog rendered in its category colour.
 *
 * <p>The category tint is extracted off the live entity into a
 * {@link ResourceFrogRenderState} once per frame ({@link #extractRenderState});
 * the layer reads the state, not the entity. The biome-variant texture is carried
 * by {@link FrogRenderState#texture}, populated by the vanilla super call.
 */
public class ResourceFrogRenderer extends FrogRenderer {

    public ResourceFrogRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new CategoryTintLayer<FrogRenderState, FrogModel>(
            this,
            state -> state.texture,
            state -> state instanceof ResourceFrogRenderState rs ? rs.tint : 0xFFFFFFFF
        ));
    }

    @Override
    public FrogRenderState createRenderState() {
        return new ResourceFrogRenderState();
    }

    @Override
    public void extractRenderState(Frog entity, FrogRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (state instanceof ResourceFrogRenderState rs && entity instanceof ResourceFrog rf) {
            // One tint per KIND (#281): species = category color, Midas = gold,
            // predators = their own hues.
            rs.tint = rf.getKind().tintArgb();
        }
    }
}
