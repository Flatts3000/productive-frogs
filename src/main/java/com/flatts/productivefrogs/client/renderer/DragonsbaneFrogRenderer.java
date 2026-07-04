package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.client.renderer.entity.state.FrogRenderState;

/**
 * Renderer for the dragon altar's display frog (#249) - "Dragonsbane". Makes it read
 * as a special, dragon-eating apex frog rather than a stock pond frog: rendered much
 * larger than life (display-only; the hitbox is unchanged) and recoloured a deep
 * ender-purple via the shared {@link CategoryTintLayer} (the same overlay the Resource
 * Frogs use for their category tint). Ambient ender particles are emitted by the entity
 * itself (see {@link com.flatts.productivefrogs.content.entity.DragonsbaneFrog#tick()}).
 *
 * <p>The tint is a constant (not per-entity), so no custom render state is needed -
 * the layer's tint getter returns {@link #VOID_TINT} regardless of state, and its
 * texture getter reads the vanilla biome-variant texture off {@link FrogRenderState}.
 */
public class DragonsbaneFrogRenderer extends FrogRenderer {

    /** Display scale - imposing, dragon-eating presence. Visual only; hitbox stays frog-sized. */
    private static final float SCALE = 1.8F;
    /** Deep ender-purple void recolour overlaid on the body. */
    private static final int VOID_TINT = 0xFF5A2E8C;

    public DragonsbaneFrogRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new CategoryTintLayer<FrogRenderState, FrogModel>(
            this, state -> state.texture, state -> VOID_TINT));
    }

    @Override
    protected void scale(FrogRenderState state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
        super.scale(state, poseStack);
    }
}
