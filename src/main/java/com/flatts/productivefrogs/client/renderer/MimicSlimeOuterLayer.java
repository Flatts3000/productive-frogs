package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.client.SynthesizedTint;
import com.flatts.productivefrogs.content.entity.MimicSlime;
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
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Item;

/**
 * Translucent outer shell for the Mimic Slime, tinted by the carried item's
 * sprite-average colour ({@link SynthesizedTint}). The runtime counterpart to
 * {@link ResourceSlimeOuterLayer}'s variant-primary-colour shell, for an item
 * that has no registered variant.
 */
public class MimicSlimeOuterLayer extends RenderLayer<Slime, SlimeModel<Slime>> {

    private final SlimeModel<Slime> model;
    private final MimicSlimeRenderer parentRenderer;

    public MimicSlimeOuterLayer(MimicSlimeRenderer renderer, EntityModelSet modelSet) {
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

        int shellTint = glowingOutline ? -1 : resolveShellTint(entity);

        getParentModel().copyPropertiesTo(model);
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.renderToBuffer(pose, consumer, packedLight,
            LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
            shellTint);
    }

    private static int resolveShellTint(Slime entity) {
        if (!(entity instanceof MimicSlime mimic)) {
            return -1;
        }
        Item item = mimic.getSynthesizedItemAsItem();
        return item != null ? SynthesizedTint.colorFor(item) : -1;
    }
}
