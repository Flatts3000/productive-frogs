package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Re-renders the parent model with a per-entity tint colour on top of the
 * vanilla white body render. Used to apply the per-category tint to
 * {@link com.flatts.productivefrogs.content.entity.ResourceFrog} and
 * {@link com.flatts.productivefrogs.content.entity.ResourceTadpole} without
 * having to copy the full {@code MobRenderer.render} body just to swap the
 * colour argument.
 *
 * <p>The vanilla white body still renders first (we don't suppress
 * {@code getRenderType}); this layer then runs after and draws the same
 * model with the tint via {@code RenderType.entityCutoutNoCull(texture)}.
 * Opaque body pixels of the tinted pass fully replace the white pixels
 * below, which is fine for vanilla Frog / Tadpole textures (both fully
 * opaque on the body). Cutout-transparent pixels of the texture pass
 * through untouched.
 */
public class CategoryTintLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

    private final Function<T, ResourceLocation> textureGetter;
    private final ToIntFunction<T> tintGetter;

    public CategoryTintLayer(
        RenderLayerParent<T, M> parent,
        Function<T, ResourceLocation> textureGetter,
        ToIntFunction<T> tintGetter
    ) {
        super(parent);
        this.textureGetter = textureGetter;
        this.tintGetter = tintGetter;
    }

    @Override
    public void render(
        PoseStack pose, MultiBufferSource buffer, int packedLight, T entity,
        float limbSwing, float limbSwingAmount, float partialTick,
        float ageInTicks, float netHeadYaw, float headPitch
    ) {
        int tint = tintGetter.applyAsInt(entity);
        ResourceLocation texture = textureGetter.apply(entity);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        int packedOverlay = LivingEntityRenderer.getOverlayCoords(entity, 0.0F);
        // Model is already setup by parent renderer (prepareMobModel + setupAnim
        // ran before layer iteration), so we just renderToBuffer with our tint.
        this.getParentModel().renderToBuffer(pose, vc, packedLight, packedOverlay, tint);
    }
}
