package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.client.renderer.entity.state.FrogRenderState;

/**
 * Renderer for the Elder Guardian Altar's display frog (#280) - "Elderbane".
 * Same shape as {@link WitherbaneFrogRenderer}: rendered larger than life
 * (display-only) and recoloured via the shared {@link CategoryTintLayer} to the
 * guardian teal of the Elder Apex kind ({@code FrogKind.Apex.ELDER}'s tint).
 * Bubble particles come from the entity itself - it swims the flooded tank.
 */
public class ElderbaneFrogRenderer extends FrogRenderer {

    /** Display scale - slightly smaller than its pit-dwelling sibling; it shares a 3x3 tank. */
    private static final float SCALE = 1.5F;
    /** Guardian teal - matches {@code FrogKind.Apex.ELDER}'s tint. */
    private static final int GUARDIAN_TEAL_TINT = 0xFF4FA79B;

    public ElderbaneFrogRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new CategoryTintLayer<FrogRenderState, FrogModel>(
            this, state -> state.texture, state -> GUARDIAN_TEAL_TINT));
    }

    @Override
    protected void scale(FrogRenderState state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
        super.scale(state, poseStack);
    }
}
