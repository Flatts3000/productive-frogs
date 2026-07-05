package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Translucent outer shell for the Mimic Slime, tinted by the carried item's
 * sprite-average colour (resolved in {@link MimicSlimeRenderer#extractRenderState}
 * into {@link MimicSlimeRenderState#shellTint}). The runtime counterpart to
 * {@link ResourceSlimeOuterLayer}'s variant-primary-colour shell, for an item that
 * has no registered variant.
 *
 * <p>26.1 note: like vanilla {@code SlimeOuterLayer}, this just submits the model
 * to the collector - the deferred pipeline animates the model from the render
 * state at flush time, so the layer never poses the model itself.
 */
public class MimicSlimeOuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {

    private final SlimeModel model;
    private final MimicSlimeRenderer parentRenderer;

    public MimicSlimeOuterLayer(MimicSlimeRenderer renderer, EntityModelSet modelSet) {
        super(renderer);
        this.parentRenderer = renderer;
        this.model = new SlimeModel(modelSet.bakeLayer(ModelLayers.SLIME_OUTER));
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int lightCoords,
                       SlimeRenderState state, float yRot, float xRot) {
        boolean glowingOutline = state.appearsGlowing() && state.isInvisible;
        if (state.isInvisible && !glowingOutline) {
            return;
        }

        Identifier texture = parentRenderer.getTextureLocation(state);
        int overlayCoords = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

        // Tint must land in the model BODY colour (tintedColor) via the 10-arg
        // submitModel; the 8-arg form routes its colour to outlineColor and forces
        // tintedColor to -1 (see ResourceSlimeOuterLayer).
        int bodyTint;
        int outlineColor;
        RenderType renderType;
        if (glowingOutline) {
            bodyTint = -1;
            outlineColor = state.outlineColor;
            renderType = RenderTypes.outline(texture);
        } else {
            bodyTint = state instanceof MimicSlimeRenderState rs ? rs.shellTint : -1;
            outlineColor = 0;
            renderType = RenderTypes.entityTranslucent(texture);
        }

        collector.order(1).submitModel(
            model, state, pose, renderType, lightCoords, overlayCoords, bodyTint, null, outlineColor, null);
    }
}
