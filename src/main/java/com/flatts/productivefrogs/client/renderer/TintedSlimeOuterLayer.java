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
 * vanilla green from {@code SlimeRenderer.SLIME_LOCATION}. Used by the four
 * custom parent species ({@link CaveSlimeRenderer}, {@link GeodeSlimeRenderer},
 * {@link TideSlimeRenderer}, {@link VoidSlimeRenderer}) which each pin a
 * single species colour at construction time.
 *
 * <p>For per-entity tint variation (e.g. {@link ResourceSlimeRenderer}'s
 * per-variant outer-shell colour), use {@link ResourceSlimeOuterLayer} instead.
 *
 * <p>Texture lookup routes through the parent renderer's
 * {@code getTextureLocation(Slime)} so both the inner cube and outer shell
 * pull from the same per-species PNG.
 */
public class TintedSlimeOuterLayer extends RenderLayer<Slime, SlimeModel<Slime>> {

    private final SlimeModel<Slime> model;
    private final SlimeRenderer parentRenderer;
    private final int tintArgb;

    public TintedSlimeOuterLayer(SlimeRenderer renderer, EntityModelSet modelSet, int tintArgb) {
        super(renderer);
        this.parentRenderer = renderer;
        this.model = new SlimeModel<>(modelSet.bakeLayer(ModelLayers.SLIME_OUTER));
        this.tintArgb = tintArgb;
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

        ResourceLocation texture = parentRenderer.getTextureLocation(entity);
        VertexConsumer consumer = glowingOutline
            ? buffer.getBuffer(RenderType.outline(texture))
            : buffer.getBuffer(RenderType.entityTranslucent(texture));

        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.renderToBuffer(pose, consumer, packedLight,
            LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
            glowingOutline ? -1 : tintArgb);
    }
}
