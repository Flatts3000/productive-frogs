package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Drop-in replacement for vanilla {@code SlimeOuterLayer} that paints the
 * translucent outer shell with a constant ARGB tint instead of leaving it the
 * vanilla green from {@code SlimeRenderer.SLIME_LOCATION}. Like vanilla's outer
 * layer, this submits {@code ModelLayers.SLIME_OUTER} (the shell cube only); the
 * eyes and mouth live on the base inner model, not here. Used by
 * {@link ParentSlimeRenderer} for the six PF parent species, each pinning a
 * single species tint at construction time.
 *
 * <p>The outer texture is supplied explicitly via the constructor (the species
 * atlas, e.g. {@code cave_slime.png}). The parent renderer's
 * {@code getTextureLocation} returns the same atlas for the base inner cube +
 * eyes/mouth.
 *
 * <p>For per-entity tint variation (e.g. {@link ResourceSlimeRenderer}'s
 * per-variant outer-shell colour), use {@link ResourceSlimeOuterLayer} instead.
 *
 * <p>26.1 note: like vanilla {@code SlimeOuterLayer}, this just submits the model
 * to the collector - the deferred pipeline animates the model from the render
 * state at flush time, so the layer never poses the model itself.
 */
public class TintedSlimeOuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {

    private final SlimeModel model;
    private final int tintArgb;
    private final Identifier outerTexture;

    public TintedSlimeOuterLayer(SlimeRenderer renderer, EntityModelSet modelSet, int tintArgb,
                                 Identifier outerTexture) {
        super(renderer);
        this.model = new SlimeModel(modelSet.bakeLayer(ModelLayers.SLIME_OUTER));
        this.tintArgb = tintArgb;
        this.outerTexture = outerTexture;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int lightCoords,
                       SlimeRenderState state, float yRot, float xRot) {
        boolean glowingOutline = state.appearsGlowing() && state.isInvisible;
        if (state.isInvisible && !glowingOutline) {
            return;
        }

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
            renderType = RenderTypes.outline(outerTexture);
        } else {
            bodyTint = tintArgb;
            outlineColor = 0;
            renderType = RenderTypes.entityTranslucent(outerTexture);
        }

        collector.order(1).submitModel(
            model, state, pose, renderType, lightCoords, overlayCoords, bodyTint, null, outlineColor, null);
    }
}
