package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.entity.WitherAltarHatchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.WitherBossModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * In-world animation for the Wither Altar (#247), driven by the synced summon
 * progress ({@link WitherAltarHatchBlockEntity#summonTicks()}). While a summon runs
 * it draws a vanilla Wither model over the ritual T at Witherbane's level, growing
 * from tiny to its capped size as the summon completes - rendered with the blue
 * {@code wither_invulnerable} spawn texture so it reads as the charging boss, then
 * devoured by Witherbane on completion (no real entity; the Nether-side counterpart
 * to {@link EndDragonAltarHatchRenderer}'s growing dragon).
 *
 * <p>Render-only and GameTest-blind; the transform constants (scale/position) are a
 * sensible first pass that may want a {@code runClient} tuning nudge.
 */
public class WitherAltarHatchRenderer implements BlockEntityRenderer<WitherAltarHatchBlockEntity> {

    private static final ResourceLocation WITHER_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/wither/wither_invulnerable.png");
    private static final RenderType WITHER_RT = RenderType.entityCutoutNoCull(WITHER_TEXTURE);

    /** Wither-model scale at the end of the summon (its max - sized to fill the cavity). */
    private static final float WITHER_END_SCALE = 0.5F;
    /** It starts a quarter of that and grows ~4x to the end size over the summon. */
    private static final float WITHER_START_SCALE = WITHER_END_SCALE / 4.0F;

    private final WitherBossModel<WitherBoss> witherModel;
    /** A client-side phantom wither fed to the model's setupAnim; never in the world. */
    private WitherBoss phantom;

    public WitherAltarHatchRenderer(BlockEntityRendererProvider.Context ctx) {
        this.witherModel = new WitherBossModel<>(ctx.bakeLayer(ModelLayers.WITHER));
    }

    @Override
    public void render(WitherAltarHatchBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (be.summonTicks() <= 0 || be.getLevel() == null) {
            be.clientSummonStartGameTime = -1L;
            return;
        }
        // summonTicks is only synced at start/end (the on/off signal), so drive growth
        // from the client's own elapsed time since the summon was first observed.
        long time = be.getLevel().getGameTime();
        if (be.clientSummonStartGameTime < 0L) {
            be.clientSummonStartGameTime = time;
        }
        float elapsed = (time - be.clientSummonStartGameTime) + partialTick;
        float progress = Mth.clamp(elapsed / WitherAltarHatchBlockEntity.SUMMON_TICKS, 0.0F, 1.0F);

        if (phantom == null) {
            phantom = EntityType.WITHER.create(be.getLevel());
        }
        if (phantom == null) {
            return;
        }
        // In the cavity, in FRONT of the receptacle wall (which is at offset z=3) so the
        // frog at z=0 sees it - not behind the wall. Centred in the 3-wide cavity at
        // Witherbane's level, facing back toward the frog (-Z).
        pose.pushPose();
        pose.translate(0.5F, 1.0F, 1.7F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        float scale = Mth.lerp(progress, WITHER_START_SCALE, WITHER_END_SCALE);
        pose.scale(-scale, -scale, scale); // entity models render flipped on X/Y
        witherModel.prepareMobModel(phantom, 0.0F, 0.0F, partialTick);
        witherModel.setupAnim(phantom, 0.0F, 0.0F, (float) time, 0.0F, 0.0F);
        VertexConsumer vc = buffers.getBuffer(WITHER_RT);
        witherModel.renderToBuffer(pose, vc, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        pose.popPose();
    }
}
