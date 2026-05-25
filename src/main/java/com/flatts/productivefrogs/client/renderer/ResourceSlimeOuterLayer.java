package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.util.PFDebug;
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
 * Drop-in replacement for vanilla {@code SlimeOuterLayer} with two changes:
 *
 * <ol>
 *   <li>Texture comes from the parent renderer's {@code getTextureLocation(Slime)}
 *       (the per-category atlas) instead of being hardcoded to
 *       {@code SlimeRenderer.SLIME_LOCATION}.</li>
 *   <li>The shell is tinted per-entity: variant primary colour for variant-locked
 *       slimes, category shell-tinted-gray for category-only slimes.</li>
 * </ol>
 */
public class ResourceSlimeOuterLayer extends RenderLayer<Slime, SlimeModel<Slime>> {

    private final SlimeModel<Slime> model;
    private final ResourceSlimeRenderer parentRenderer;

    public ResourceSlimeOuterLayer(ResourceSlimeRenderer renderer, EntityModelSet modelSet) {
        super(renderer);
        this.parentRenderer = renderer;
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

        ResourceLocation texture = parentRenderer.getTextureLocation(entity);
        VertexConsumer consumer = glowingOutline
            ? buffer.getBuffer(RenderType.outline(texture))
            : buffer.getBuffer(RenderType.entityTranslucent(texture));

        int shellTint = resolveShellTint(entity);
        if (glowingOutline) {
            shellTint = -1;
        }

        if (PFDebug.on(PFDebug.Area.RENDER) && entity instanceof ResourceSlime resource) {
            final int tint = shellTint;
            final String source = resource.getVariant() != null ? "variant" : "category";
            PFDebug.logOnce(PFDebug.Area.RENDER, "shell#" + entity.getId() + "/" + tint,
                () -> String.format("ResourceSlime id=%d shellTint=#%08X source=%s glowing=%s",
                    entity.getId(), tint, source, glowingOutline));
        }

        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.renderToBuffer(pose, consumer, packedLight,
            LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
            shellTint);
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
