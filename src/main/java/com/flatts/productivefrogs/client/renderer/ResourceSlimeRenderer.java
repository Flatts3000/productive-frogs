package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.entity.ResourceSlime;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.world.entity.monster.Slime;

/**
 * Resource Slime renderer. Extends vanilla {@link SlimeRenderer} so we reuse
 * the vanilla model, texture, and shadow size verbatim — the only difference
 * is a per-category color multiply via {@link #getModelTint}.
 *
 * <p>Same pattern as {@link com.flatts.productivefrogs.client.renderer.ResourceTadpoleRenderer}:
 * substitute a {@link ResourceSlimeRenderState} for the base state via
 * {@link #createRenderState}, populate the category in
 * {@link #extractRenderState}, and read it back in {@code getModelTint}.
 */
public class ResourceSlimeRenderer extends SlimeRenderer {

    public ResourceSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public SlimeRenderState createRenderState() {
        return new ResourceSlimeRenderState();
    }

    @Override
    public void extractRenderState(Slime entity, SlimeRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof ResourceSlime resource && state instanceof ResourceSlimeRenderState rState) {
            rState.category = resource.getCategory();
        }
    }

    @Override
    protected int getModelTint(SlimeRenderState state) {
        if (state instanceof ResourceSlimeRenderState rState && rState.category != null) {
            return rState.category.tintArgb();
        }
        return super.getModelTint(state);
    }
}
