package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.entity.BossAltarHatchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.IntSupplier;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Generic in-world summon animation for the Phase 4b altars (#279/#280): a
 * phantom boss replica scaled 0 -> 1 over the synced summon window, rising out of
 * {@code riseDepth} blocks below its rest position - the Warden climbs out of the
 * pit floor, the Elder Guardian swells mid-tank. Simpler than the bespoke
 * wither/dragon renderers (no vanilla growth mechanic to borrow), one class for
 * both altars, parameterized at registration.
 *
 * <p>26.1 shape mirrors {@link WitherAltarHatchRenderer}: the phantom is captured
 * via {@link EntityRenderDispatcher#extractEntity} during {@code extractRenderState}
 * and replayed in {@code submit}; it is never in the world, so no boss bar and no
 * sonic booms. Render-only and GameTest-blind.
 */
public class GrowingReplicaRenderer<T extends BossAltarHatchBlockEntity>
        implements BlockEntityRenderer<T, GrowingReplicaRenderer.ReplicaRenderState> {

    private final EntityRenderDispatcher dispatcher;
    private final EntityType<?> replicaType;
    /** Rest offset of the replica's feet from the hatch block origin. */
    private final Vec3 restOffset;
    /** How far below the rest position the replica starts (rises as the summon runs). */
    private final double riseDepth;
    private final IntSupplier summonDuration;

    /** A client-side phantom boss fed to the vanilla renderer; never in the world. */
    private Entity phantom;

    public GrowingReplicaRenderer(BlockEntityRendererProvider.Context ctx, EntityType<?> replicaType,
            Vec3 restOffset, double riseDepth, IntSupplier summonDuration) {
        this.dispatcher = ctx.entityRenderer();
        this.replicaType = replicaType;
        this.restOffset = restOffset;
        this.riseDepth = riseDepth;
        this.summonDuration = summonDuration;
    }

    @Override
    public ReplicaRenderState createRenderState() {
        return new ReplicaRenderState();
    }

    @Override
    public void extractRenderState(T be, ReplicaRenderState state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPosition, breakProgress);
        state.active = false;
        if (be.summonTicks() <= 0 || be.getLevel() == null) {
            be.clientSummonStartGameTime = -1L;
            return;
        }
        // summonTicks is only synced at start/end (the on/off signal), so drive the
        // growth from the client's own elapsed time since the summon was first observed.
        long time = be.getLevel().getGameTime();
        if (be.clientSummonStartGameTime < 0L) {
            be.clientSummonStartGameTime = time;
        }
        float elapsed = (time - be.clientSummonStartGameTime) + partialTick;
        float progress = Mth.clamp(elapsed / summonDuration.getAsInt(), 0.0F, 1.0F);

        if (phantom == null) {
            phantom = replicaType.create(be.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        }
        if (phantom == null) {
            return;
        }
        phantom.tickCount = (int) time; // advance idle animation
        phantom.setYRot(180.0F);
        EntityRenderState replica = dispatcher.extractEntity(phantom, partialTick);
        replica.shadowRadius = 0.0F; // suppress the shadow (it is a mirage)
        state.replica = replica;
        state.progress = progress;
        state.active = true;
    }

    @Override
    public void submit(ReplicaRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.active || state.replica == null) {
            return;
        }
        float progress = state.progress;
        float scale = 0.05F + 0.95F * progress;
        poseStack.pushPose();
        poseStack.translate(
            0.5 + restOffset.x,
            restOffset.y - riseDepth * (1.0 - progress),
            0.5 + restOffset.z);
        poseStack.scale(scale, scale, scale);
        dispatcher.submit(state.replica, camera, 0.0, 0.0, 0.0, poseStack, collector);
        poseStack.popPose();
    }

    /** Captured phantom-replica render state for one frame. */
    public static class ReplicaRenderState extends BlockEntityRenderState {
        public boolean active;
        public EntityRenderState replica;
        public float progress;
    }
}
