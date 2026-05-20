package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.entity.ResourceFrog;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import net.minecraft.world.entity.animal.frog.Frog;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Resource Frog renderer — extends vanilla {@link FrogRenderer} so it inherits
 * the model, animation states, and biome-variant-driven texture selection.
 * The only difference: applies a per-category color multiply via {@link
 * #getModelTint(FrogRenderState)}.
 *
 * <p>The category is carried on a {@link ResourceFrogRenderState} subclass
 * substituted via {@link #createRenderState()} and populated in
 * {@link #extractRenderState(Frog, FrogRenderState, float)}.
 */
@OnlyIn(Dist.CLIENT)
public class ResourceFrogRenderer extends FrogRenderer {

    public ResourceFrogRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public FrogRenderState createRenderState() {
        return new ResourceFrogRenderState();
    }

    @Override
    public void extractRenderState(Frog entity, FrogRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof ResourceFrog resource && state instanceof ResourceFrogRenderState rState) {
            rState.category = resource.getCategory();
        }
    }

    @Override
    protected int getModelTint(FrogRenderState state) {
        if (state instanceof ResourceFrogRenderState rState && rState.category != null) {
            return rState.category.tintArgb();
        }
        return super.getModelTint(state);
    }
}
