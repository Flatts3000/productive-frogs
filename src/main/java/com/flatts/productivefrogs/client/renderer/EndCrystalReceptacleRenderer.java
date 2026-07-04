package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.EndCrystalReceptacleBlock;
import com.flatts.productivefrogs.content.block.entity.EndCrystalReceptacleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Renders a vanilla-style floating, spinning End Crystal on top of a filled
 * {@link EndCrystalReceptacleBlock} (#249). Replicates vanilla
 * {@code EndCrystalRenderer}'s glass/cube model + bob + triple-nested spin
 * (baking the same {@link ModelLayers#END_CRYSTAL} layer), scaled down to perch
 * on the block rather than fill an entity hitbox. No base ring and no beam here;
 * the converging summon beams are added with the altar animation (a later chunk).
 *
 * <p>Render-only: gated on the {@code FILLED} blockstate, so an empty receptacle
 * draws nothing. Transform constants are a sensible first pass and may want a
 * {@code runClient} tuning nudge for the exact hover height.
 */
public class EndCrystalReceptacleRenderer implements BlockEntityRenderer<EndCrystalReceptacleBlockEntity> {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutoutNoCull(TEXTURE);
    private static final float SIN_45 = (float) Math.sin(Math.PI / 4);

    /** How high the crystal's pivot sits above the block origin (1.0 = the top face). Tunable. */
    private static final float HOVER_Y = 1.35F;

    private final ModelPart cube;
    private final ModelPart glass;

    public EndCrystalReceptacleRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(ModelLayers.END_CRYSTAL);
        this.glass = root.getChild("glass");
        this.cube = root.getChild("cube");
    }

    @Override
    public void render(EndCrystalReceptacleBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!be.getBlockState().hasProperty(EndCrystalReceptacleBlock.FILLED)
                || !be.getBlockState().getValue(EndCrystalReceptacleBlock.FILLED)) {
            return;
        }
        long gameTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        float t = (float) gameTime + partialTick;
        float bob = bob(t);
        float spin = t * 3.0F;
        VertexConsumer vc = buffers.getBuffer(RENDER_TYPE);
        int overlay = OverlayTexture.NO_OVERLAY;

        pose.pushPose();
        pose.translate(0.5F, HOVER_Y, 0.5F);   // perch above the block top
        pose.scale(0.5F, 0.5F, 0.5F);        // perch-size (vs the 2x entity crystal)
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.translate(0.0F, 1.5F + bob / 2.0F, 0.0F);
        pose.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0.0F, SIN_45));
        this.glass.render(pose, vc, packedLight, overlay);
        pose.scale(0.875F, 0.875F, 0.875F);
        pose.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0.0F, SIN_45));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        this.glass.render(pose, vc, packedLight, overlay);
        pose.scale(0.875F, 0.875F, 0.875F);
        pose.mulPose(new Quaternionf().setAngleAxis((float) (Math.PI / 3), SIN_45, 0.0F, SIN_45));
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        this.cube.render(pose, vc, packedLight, overlay);
        pose.popPose();
    }

    private static float bob(float t) {
        float f = Mth.sin(t * 0.2F) / 2.0F + 0.5F;
        f = (f * f + f) * 0.4F;
        return f - 1.4F;
    }
}
