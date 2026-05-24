package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.monster.Slime;

/**
 * Renders the outer translucent shell + eyes + mouth of a Resource Slime
 * against the per-category atlas, with a per-variant tint applied to the
 * shell.
 *
 * <p>Pre-v1.0.1 this layer routed its texture lookup through the parent
 * renderer's {@code getTextureLocation}, which returned the same per-category
 * atlas. v1.0.1 splits inner-vs-outer rendering: the parent renderer's
 * {@code getTextureLocation} now returns the variant's vanilla block PNG
 * (for the inner cube), so this layer resolves the outer atlas
 * independently from the entity's category via
 * {@link ResourceSlimeRenderer#OUTER_TEXTURES}.
 *
 * <p>The shell tint comes from the variant's {@code primary_color} (or the
 * category's {@code shellTintArgb} when no variant is bound), preserving the
 * v1.0 tint behavior.
 */
public class ResourceSlimeOuterLayer extends RenderLayer<Slime, SlimeModel<Slime>> {

    private final SlimeModel<Slime> model;

    public ResourceSlimeOuterLayer(ResourceSlimeRenderer renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new SlimeModel<>(modelSet.bakeLayer(ModelLayers.SLIME_OUTER));
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

        ResourceLocation texture = resolveOuterTexture(entity);
        VertexConsumer consumer = glowingOutline
            ? buffer.getBuffer(RenderType.outline(texture))
            : buffer.getBuffer(RenderType.entityTranslucent(texture));

        int shellTint = resolveShellTint(entity);
        if (glowingOutline) {
            shellTint = -1;
        }

        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.renderToBuffer(pose, consumer, packedLight,
            LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
            shellTint);
    }

    private static ResourceLocation resolveOuterTexture(Slime entity) {
        Category cat = Category.BOG;
        if (entity instanceof ResourceSlime resource) {
            Category resolved = resource.getCategory();
            if (resolved != null) {
                cat = resolved;
            }
        }
        return ResourceSlimeRenderer.OUTER_TEXTURES.get(cat);
    }

    private static int resolveShellTint(Slime entity) {
        if (!(entity instanceof ResourceSlime resource)) {
            return -1;
        }
        SlimeVariant variant = resource.getVariant();
        if (variant != null) {
            return FastColor.ARGB32.color(255,
                (variant.primaryColor() >> 16) & 0xFF,
                (variant.primaryColor() >> 8) & 0xFF,
                variant.primaryColor() & 0xFF);
        }
        Category cat = resource.getCategory();
        return cat != null ? cat.shellTintArgb() : -1;
    }
}
