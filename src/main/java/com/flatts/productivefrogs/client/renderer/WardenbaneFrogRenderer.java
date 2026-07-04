package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.client.renderer.entity.state.FrogRenderState;

/**
 * Renderer for the Warden Altar's display frog (#279) - "Wardenbane". Same shape
 * as {@link WitherbaneFrogRenderer}: rendered larger than life (display-only; the
 * hitbox is unchanged) and recoloured via the shared {@link CategoryTintLayer} to
 * the deep-teal of the Warden Apex kind ({@code FrogKind.Apex.WARDEN}'s tint), so
 * the perched display reads as the installed frog. Sculk-soul particles come from
 * the entity itself.
 */
public class WardenbaneFrogRenderer extends FrogRenderer {

    /** Display scale - imposing, Warden-eating presence. Visual only; hitbox stays frog-sized. */
    private static final float SCALE = 1.8F;
    /** Deep sculk teal - matches {@code FrogKind.Apex.WARDEN}'s tint. */
    private static final int SCULK_TEAL_TINT = 0xFF0F4A52;

    public WardenbaneFrogRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new CategoryTintLayer<FrogRenderState, FrogModel>(
            this, state -> state.texture, state -> SCULK_TEAL_TINT));
    }

    @Override
    protected void scale(FrogRenderState state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
        super.scale(state, poseStack);
    }
}
