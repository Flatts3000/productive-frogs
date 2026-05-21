package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.slime.SlimeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Drop-in replacement for vanilla {@code SlimeOuterLayer}. Identical in
 * behavior except the texture comes from the parent renderer's
 * {@code getTextureLocation(state)} instead of being hardcoded to
 * {@code SlimeRenderer.SLIME_LOCATION} — that hardcode is the reason the
 * outer translucent shell would otherwise still show the vanilla green slime
 * texture even when we swap the inner cube to a per-category texture.
 *
 * <p>The model itself still uses {@code ModelLayers.SLIME_OUTER} so the
 * cube geometry is the vanilla outer cube (8×8×8, slightly larger than the
 * inner 6×6×6 inner cube). Only the texture lookup changes.
 */
public class ResourceSlimeOuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {

    private final SlimeModel model;
    private final ResourceSlimeRenderer parentRenderer;

    public ResourceSlimeOuterLayer(ResourceSlimeRenderer renderer, EntityModelSet modelSet) {
        super(renderer);
        this.parentRenderer = renderer;
        this.model = new SlimeModel(modelSet.bakeLayer(ModelLayers.SLIME_OUTER));
    }

    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, SlimeRenderState state,
                       float yRot, float xRot) {
        boolean glowingOutline = state.appearsGlowing() && state.isInvisible;
        if (state.isInvisible && !glowingOutline) {
            return;
        }

        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        Identifier texture = parentRenderer.getTextureLocation(state);

        if (glowingOutline) {
            collector.order(1).submitModel(
                this.model, state, pose,
                RenderTypes.outline(texture),
                light, overlay, -1, null, state.outlineColor, null
            );
        } else {
            collector.order(1).submitModel(
                this.model, state, pose,
                RenderTypes.entityTranslucent(texture),
                light, overlay, -1, null, state.outlineColor, null
            );
        }
    }
}
