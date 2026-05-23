package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.entity.ResourceFrog;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.world.entity.animal.frog.Frog;

/**
 * Resource Frog renderer — extends vanilla {@link FrogRenderer} so it inherits
 * the model, animation, and biome-variant texture selection. Adds a single
 * {@link CategoryTintLayer} that re-renders the body in the entity's category
 * tint on top of the vanilla white pass.
 *
 * <p>1.21.1 has no {@code getModelTint} hook on {@link FrogRenderer} (that
 * landed in 1.21.4+ with the entity render-state rewrite), so we apply the
 * tint via a custom RenderLayer that runs after the main body render. The
 * tinted pass uses {@link net.minecraft.client.renderer.RenderType#entityCutoutNoCull}
 * with our colour, fully overlaying the vanilla white body — the visible
 * result is the frog rendered in its category colour.
 */
public class ResourceFrogRenderer extends FrogRenderer {

    public ResourceFrogRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new CategoryTintLayer<Frog, net.minecraft.client.model.FrogModel<Frog>>(
            this,
            this::getTextureLocation,
            entity -> {
                if (entity instanceof ResourceFrog rf) {
                    return 0xFF000000 | rf.getCategory().tintRgb();
                }
                return 0xFFFFFFFF;
            }
        ));
    }
}
