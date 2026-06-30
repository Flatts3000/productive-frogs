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
 * Drop-in replacement for vanilla {@code SlimeOuterLayer} with two changes:
 *
 * <ol>
 *   <li>Texture comes from the parent renderer's {@code getTextureLocation(state)}
 *       (the per-category atlas) instead of being hardcoded to
 *       {@code SlimeRenderer.SLIME_LOCATION}.</li>
 *   <li>The shell is tinted per-entity: variant primary colour for variant-locked
 *       slimes, category shell-tinted-gray for category-only slimes - read off the
 *       pre-resolved {@link ResourceSlimeRenderState#shellTint}.</li>
 * </ol>
 *
 * <p>26.1 note: like vanilla {@code SlimeOuterLayer}, this just submits the model
 * to the collector - the deferred pipeline applies {@code setupAnim(state)} from
 * the render state at flush time, so the layer never poses the model itself.
 */
public class ResourceSlimeOuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {

    private final SlimeModel model;
    private final ResourceSlimeRenderer parentRenderer;

    public ResourceSlimeOuterLayer(ResourceSlimeRenderer renderer, EntityModelSet modelSet) {
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

        // 26.1 deferred submit: the tint must land in the model BODY colour
        // (tintedColor, the 7th submitModel arg), NOT the outline-colour slot.
        // The 8-arg submitModel overload hardcodes tintedColor to -1 and routes
        // its colour into outlineColor - fine for the glowing-outline pass, wrong
        // for the tinted shell - so the body/outline split is passed explicitly
        // via the full 10-arg form.
        int bodyTint;
        int outlineColor;
        RenderType renderType;
        if (glowingOutline) {
            bodyTint = -1;
            outlineColor = state.outlineColor;
            renderType = RenderTypes.outline(texture);
        } else {
            bodyTint = state instanceof ResourceSlimeRenderState rs ? rs.shellTint : -1;
            outlineColor = 0;
            renderType = RenderTypes.entityTranslucent(texture);
        }

        collector.order(1).submitModel(
            model, state, pose, renderType, lightCoords, overlayCoords, bodyTint, null, outlineColor, null);
    }
}
