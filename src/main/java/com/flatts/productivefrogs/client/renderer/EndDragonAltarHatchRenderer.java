package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.EndDragonAltarHatchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;

/**
 * In-world animation for the End Dragon Altar (#249), driven by the synced summon
 * progress ({@link EndDragonAltarHatchBlockEntity#summonTicks()}). While a summon
 * runs it draws, mirroring the vanilla respawn:
 *
 * <ul>
 *   <li>Converging crystal beams from each receptacle into the hatch (reusing
 *       vanilla {@link EnderDragonRenderer#submitCrystalBeams}).</li>
 *   <li>A vanilla dragon in the cell below the hatch that grows from tiny to its
 *       capped size as the summon completes (a phantom {@link EnderDragon} replayed
 *       through the entity renderer - no real entity).</li>
 * </ul>
 *
 * <p>26.1 port: the dragon is captured into a vanilla {@link EntityRenderState} via
 * {@link EntityRenderDispatcher#extractEntity} during {@code extractRenderState} and
 * replayed in {@code submit} via {@link EntityRenderDispatcher#submit}, scaled and
 * spun on the PoseStack. This replaces the old direct {@code DragonModel} render -
 * the {@code EnderDragonRenderer.DragonModel} inner class no longer exists on 26.1.
 * The beams move from the old {@code MultiBufferSource} call to
 * {@code submitCrystalBeams(..., SubmitNodeCollector, ...)}.
 *
 * <p>Render-only and GameTest-blind; the transform constants (dragon
 * scale/position/spin) are a sensible first pass that may want a {@code runClient}
 * tuning nudge.
 */
public class EndDragonAltarHatchRenderer
        implements BlockEntityRenderer<EndDragonAltarHatchBlockEntity, EndDragonAltarHatchRenderer.EndDragonAltarHatchRenderState> {

    /** Receptacle offsets from the hatch (the beam sources), matching the validator's RECEPTACLES. */
    private static final int[][] RECEPTACLES = {{-3, -6, 0}, {0, -6, -3}, {0, -6, 3}, {3, -6, 0}};
    /** Dragon scale at the end of the summon (its max - the size to fill the altar). */
    private static final float DRAGON_END_SCALE = 0.4F;
    /** It starts a quarter of that and grows ~4x to the end size over the summon. */
    private static final float DRAGON_START_SCALE = DRAGON_END_SCALE / 4.0F;

    private final EntityRenderDispatcher dispatcher;
    /** A client-side phantom dragon fed to the entity renderer; never in the world. */
    private EnderDragon phantom;

    public EndDragonAltarHatchRenderer(BlockEntityRendererProvider.Context ctx) {
        this.dispatcher = ctx.entityRenderer();
    }

    @Override
    public EndDragonAltarHatchRenderState createRenderState() {
        return new EndDragonAltarHatchRenderState();
    }

    @Override
    public void extractRenderState(EndDragonAltarHatchBlockEntity be, EndDragonAltarHatchRenderState state,
            float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPosition, breakProgress);
        state.active = false;
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
        state.progress = Mth.clamp(elapsed / PFConfig.dragonAltarSummonTicks(), 0.0F, 1.0F);
        state.beamTime = (float) time + partialTick;
        state.spin = state.beamTime * 2.0F;

        if (phantom == null) {
            phantom = EntityType.ENDER_DRAGON.create(be.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        }
        if (phantom != null) {
            phantom.tickCount = (int) time;
            state.dragon = dispatcher.extractEntity(phantom, partialTick);
            state.dragon.shadowRadius = 0.0F;
        }
        state.active = true;
    }

    @Override
    public void submit(EndDragonAltarHatchRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.active) {
            return;
        }
        // Converging beams: from each receptacle (top face, where the crystal sits) to
        // the hatch centre (0.5, 0.5, 0.5).
        for (int[] ro : RECEPTACLES) {
            float cx = ro[0] + 0.5F;
            float cy = ro[1] + 1.0F;
            float cz = ro[2] + 0.5F;
            poseStack.pushPose();
            poseStack.translate(cx, cy, cz);
            EnderDragonRenderer.submitCrystalBeams(0.5F - cx, 0.5F - cy, 0.5F - cz,
                state.beamTime, poseStack, collector, state.lightCoords);
            poseStack.popPose();
        }

        // Growing dragon in the cell below the hatch.
        if (state.dragon != null) {
            poseStack.pushPose();
            poseStack.translate(0.5F, -1.0F, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
            float scale = Mth.lerp(state.progress, DRAGON_START_SCALE, DRAGON_END_SCALE);
            poseStack.scale(scale, scale, scale);
            dispatcher.submit(state.dragon, camera, 0.0, 0.0, 0.0, poseStack, collector);
            poseStack.popPose();
        }
    }

    /** Captured beam clock + growing-dragon render state for one frame. */
    public static class EndDragonAltarHatchRenderState extends BlockEntityRenderState {
        public boolean active;
        public float progress;
        public float beamTime;
        public float spin;
        public EntityRenderState dragon;
    }
}
