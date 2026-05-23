package com.flatts.productivefrogs.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.TadpoleRenderer;

/**
 * Resource Tadpole renderer — extends vanilla {@link TadpoleRenderer}.
 *
 * <p>TODO (post-port polish): restore per-category color tint. Same constraint
 * as {@link ResourceFrogRenderer} — 1.21.1 has no {@code getModelTint} hook
 * on the renderer; a custom layer or {@code render()} override is needed.
 */
public class ResourceTadpoleRenderer extends TadpoleRenderer {

    public ResourceTadpoleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }
}
