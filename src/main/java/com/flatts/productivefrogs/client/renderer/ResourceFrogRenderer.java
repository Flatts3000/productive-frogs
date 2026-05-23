package com.flatts.productivefrogs.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FrogRenderer;

/**
 * Resource Frog renderer — extends vanilla {@link FrogRenderer} so it inherits
 * the model, animation, and biome-variant texture selection.
 *
 * <p>TODO (post-port polish): restore per-category color tint. 1.21.1's
 * {@link FrogRenderer} doesn't expose a {@code getModelTint} hook (that's
 * 1.21.4+). To re-apply the category tint in 1.21.1 we need either a custom
 * {@link net.minecraft.client.renderer.entity.layers.RenderLayer} that
 * re-renders the model with a colored {@code renderToBuffer} call, or a full
 * {@code render()} override that copies the vanilla MobRenderer body to swap
 * the colour argument. Tracked in
 * {@code docs/backlog.md} under post-port polish.
 */
public class ResourceFrogRenderer extends FrogRenderer {

    public ResourceFrogRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }
}
