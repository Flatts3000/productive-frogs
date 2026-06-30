package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.frog.FrogModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.client.renderer.entity.state.FrogRenderState;

/**
 * Renderer for the Wither Altar's display frog (#247) - "Witherbane". The Nether-side
 * counterpart to {@link DragonsbaneFrogRenderer}: rendered much larger than life
 * (display-only; the hitbox is unchanged) and recoloured a dark blue via the shared
 * {@link CategoryTintLayer} - echoing the Wither's blue charging glow. Ambient soul
 * particles are emitted by the entity itself (see
 * {@link com.flatts.productivefrogs.content.entity.WitherbaneFrog#tick()}).
 *
 * <p>The tint is a constant (not per-entity), so no custom render state is needed -
 * the layer's tint getter returns {@link #DARK_BLUE_TINT} regardless of state, and
 * its texture getter reads the vanilla biome-variant texture off
 * {@link FrogRenderState}.
 */
public class WitherbaneFrogRenderer extends FrogRenderer {

    /** Display scale - imposing, Wither-eating presence. Visual only; hitbox stays frog-sized. */
    private static final float SCALE = 1.8F;
    /** Dark blue recolour, echoing the Wither's blue invulnerable charging glow. */
    private static final int DARK_BLUE_TINT = 0xFF1A2B5C;

    public WitherbaneFrogRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new CategoryTintLayer<FrogRenderState, FrogModel>(
            this, state -> state.texture, state -> DARK_BLUE_TINT));
    }

    @Override
    protected void scale(FrogRenderState state, PoseStack poseStack) {
        poseStack.scale(SCALE, SCALE, SCALE);
        super.scale(state, poseStack);
    }
}
