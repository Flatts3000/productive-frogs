package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;

/**
 * Drop-in replacement for vanilla {@code SlimeOuterLayer} that paints the
 * translucent outer shell with a constant ARGB tint instead of leaving it the
 * vanilla green from {@code SlimeRenderer.SLIME_LOCATION}. Like vanilla's
 * outer layer, this renders {@code ModelLayers.SLIME_OUTER} (the shell cube
 * only); the eyes and mouth live on the base inner model, not here. Used
 * by the six PF parent species renderers ({@link BogSlimeRenderer},
 * {@link CaveSlimeRenderer}, {@link GeodeSlimeRenderer},
 * {@link TideSlimeRenderer}, {@link InfernalSlimeRenderer},
 * {@link VoidSlimeRenderer}), each pinning a single species tint at
 * construction time.
 *
 * <p>The outer texture is supplied explicitly via the constructor (the
 * species atlas, e.g. {@code cave_slime.png}). The parent renderer's
 * {@code getTextureLocation} returns the same atlas for the base inner cube +
 * eyes/mouth; the v1.0.1 inner resource block is drawn separately by
 * {@link ResourceSlimeInnerBlockLayer}.
 *
 * <p>For per-entity tint variation (e.g. {@link ResourceSlimeRenderer}'s
 * per-variant outer-shell colour), use {@link ResourceSlimeOuterLayer} instead.
 */
public class TintedSlimeOuterLayer extends RenderLayer<Slime, SlimeModel<Slime>> {

    private final SlimeModel<Slime> model;
    private final int tintArgb;
    private final ResourceLocation outerTexture;

    public TintedSlimeOuterLayer(SlimeRenderer renderer, EntityModelSet modelSet, int tintArgb,
                                 ResourceLocation outerTexture) {
        super(renderer);
        this.model = new SlimeModel<>(modelSet.bakeLayer(ModelLayers.SLIME_OUTER));
        this.tintArgb = tintArgb;
        this.outerTexture = outerTexture;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, Slime entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean glowingOutline = minecraft.shouldEntityAppearGlowing(entity) && entity.isInvisible();
        if (entity.isInvisible() && !glowingOutline) {
            return;
        }

        VertexConsumer consumer = glowingOutline
            ? buffer.getBuffer(RenderType.outline(outerTexture))
            : buffer.getBuffer(RenderType.entityTranslucent(outerTexture));

        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.renderToBuffer(pose, consumer, packedLight,
            LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
            glowingOutline ? -1 : tintArgb);
    }
}
