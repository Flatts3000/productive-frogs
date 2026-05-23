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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Generic drop-in replacement for vanilla {@code SlimeOuterLayer} that paints
 * the translucent outer shell with a constant ARGB tint instead of leaving it
 * the vanilla green from {@code SlimeRenderer.SLIME_LOCATION}. Designed for
 * the four custom parent species ({@link CaveSlimeRenderer},
 * {@link GeodeSlimeRenderer}, {@link TideSlimeRenderer},
 * {@link VoidSlimeRenderer}) which each pin a single species colour.
 *
 * <p>For per-state tint variation (e.g. {@link ResourceSlimeRenderer}'s
 * per-variant outer-shell colour), use {@link ResourceSlimeOuterLayer} instead
 * — it reads the tint from the render state at submit time. This layer holds a
 * constant set at construction, which is the right shape for the parent
 * species (every Cave Slime is grey, every Geode Slime is cyan, etc.).
 *
 * <p>Texture lookup routes through the parent renderer's
 * {@code getTextureLocation(state)} so both the inner cube and outer shell
 * pull from the same per-species PNG (same pattern as
 * {@link ResourceSlimeOuterLayer}).
 */
public class TintedSlimeOuterLayer extends RenderLayer<SlimeRenderState, SlimeModel> {

    private final SlimeModel model;
    private final SlimeRenderer parentRenderer;
    private final int tintArgb;

    public TintedSlimeOuterLayer(SlimeRenderer renderer, EntityModelSet modelSet, int tintArgb) {
        super(renderer);
        this.parentRenderer = renderer;
        this.model = new SlimeModel(modelSet.bakeLayer(ModelLayers.SLIME_OUTER));
        this.tintArgb = tintArgb;
    }

    @Override
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
                light, overlay, tintArgb, null, state.outlineColor, null
            );
        }
    }
}
