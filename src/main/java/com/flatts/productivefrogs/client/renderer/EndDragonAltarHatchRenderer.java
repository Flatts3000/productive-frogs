package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.EndDragonAltarHatchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/**
 * In-world animation for the End Dragon Altar (#249), driven by the synced summon
 * progress ({@link EndDragonAltarHatchBlockEntity#summonTicks()}). While a summon
 * runs it draws, mirroring the vanilla respawn:
 *
 * <ul>
 *   <li>Converging crystal beams from each receptacle into the hatch (reusing
 *       vanilla {@link EnderDragonRenderer#renderCrystalBeams}).</li>
 *   <li>A vanilla dragon model in the cell below the hatch that grows from tiny to
 *       its capped size as the summon completes (the {@code DragonModel} from
 *       {@link EnderDragonRenderer}, rendered static - no real entity).</li>
 * </ul>
 *
 * <p>Render-only and GameTest-blind; the transform constants (beam crystal height,
 * dragon scale/position/spin) are a sensible first pass that may want a
 * {@code runClient} tuning nudge.
 */
public class EndDragonAltarHatchRenderer implements BlockEntityRenderer<EndDragonAltarHatchBlockEntity> {

    private static final ResourceLocation DRAGON_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final RenderType DRAGON_RT = RenderType.entityCutoutNoCull(DRAGON_TEXTURE);

    /** Receptacle offsets from the hatch (the beam sources), matching the validator's RECEPTACLES. */
    private static final int[][] RECEPTACLES = {{-3, -6, 0}, {0, -6, -3}, {0, -6, 3}, {3, -6, 0}};
    /** Dragon-model scale at the end of the summon (its max - the size to fill the altar). */
    private static final float DRAGON_END_SCALE = 0.4F;
    /** It starts a quarter of that and grows ~4x to the end size over the summon. */
    private static final float DRAGON_START_SCALE = DRAGON_END_SCALE / 4.0F;

    private final EnderDragonRenderer.DragonModel dragonModel;
    /** A client-side phantom dragon fed to the model (the DragonModel reads its fields); never in the world. */
    private EnderDragon phantom;

    public EndDragonAltarHatchRenderer(BlockEntityRendererProvider.Context ctx) {
        this.dragonModel = new EnderDragonRenderer.DragonModel(ctx.bakeLayer(ModelLayers.ENDER_DRAGON));
    }

    @Override
    public void render(EndDragonAltarHatchBlockEntity be, float partialTick, PoseStack pose,
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
        float progress = Mth.clamp(elapsed / PFConfig.dragonAltarSummonTicks(), 0.0F, 1.0F);

        // Converging beams: from each receptacle (top face, where the crystal sits) to
        // the hatch centre (0.5, 0.5, 0.5).
        for (int[] ro : RECEPTACLES) {
            float cx = ro[0] + 0.5F;
            float cy = ro[1] + 1.0F;
            float cz = ro[2] + 0.5F;
            pose.pushPose();
            pose.translate(cx, cy, cz);
            EnderDragonRenderer.renderCrystalBeams(0.5F - cx, 0.5F - cy, 0.5F - cz,
                partialTick, (int) time, pose, buffers, packedLight);
            pose.popPose();
        }

        // Growing dragon model in the cell below the hatch. The model reads fields off
        // an EnderDragon, so prep it with a cached client-side phantom (never spawned).
        if (be.getLevel() != null) {
            if (phantom == null) {
                phantom = EntityType.ENDER_DRAGON.create(be.getLevel());
            }
            if (phantom != null) {
                pose.pushPose();
                pose.translate(0.5F, -1.0F, 0.5F);
                pose.mulPose(Axis.YP.rotationDegrees((time + partialTick) * 2.0F));
                float scale = Mth.lerp(progress, DRAGON_START_SCALE, DRAGON_END_SCALE);
                pose.scale(-scale, -scale, scale); // entity models render flipped on X/Y
                dragonModel.prepareMobModel(phantom, 0.0F, 0.0F, partialTick);
                VertexConsumer vc = buffers.getBuffer(DRAGON_RT);
                dragonModel.renderToBuffer(pose, vc, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                pose.popPose();
            }
        }
    }
}
