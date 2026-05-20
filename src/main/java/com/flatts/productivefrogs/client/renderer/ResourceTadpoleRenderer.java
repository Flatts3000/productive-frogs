package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TadpoleRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Resource Tadpole renderer — extends vanilla {@link TadpoleRenderer} so it
 * reuses the vanilla model, texture, and shadow size verbatim. The only
 * difference: applies a per-category color multiply via {@link
 * #getModelTint(LivingEntityRenderState)}.
 *
 * <p>Pattern: substitute a {@link ResourceTadpoleRenderState} for the base
 * state via the overridden {@link #createRenderState()}, populate the
 * category field during {@link #extractRenderState(Tadpole, LivingEntityRenderState, float)},
 * and read it back in {@code getModelTint}.
 */
@OnlyIn(Dist.CLIENT)
public class ResourceTadpoleRenderer extends TadpoleRenderer {

    public ResourceTadpoleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new ResourceTadpoleRenderState();
    }

    @Override
    public void extractRenderState(Tadpole entity, LivingEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof ResourceTadpole resource && state instanceof ResourceTadpoleRenderState rState) {
            rState.category = resource.getCategory();
        }
    }

    @Override
    protected int getModelTint(LivingEntityRenderState state) {
        if (state instanceof ResourceTadpoleRenderState rState && rState.category != null) {
            return rState.category.tintArgb();
        }
        return super.getModelTint(state);
    }
}
