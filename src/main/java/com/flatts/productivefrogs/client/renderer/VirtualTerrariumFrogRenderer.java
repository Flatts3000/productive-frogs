package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the loaded frog inside the Virtual Terrarium's Display Dome. A client-side
 * vanilla Frog phantom (never in the world) is captured via the 26.1 extract/submit
 * pipeline (mirrors {@code GrowingReplicaRenderer}), positioned one block up inside
 * the dome and scaled down. Renders nothing when the Processor has no frog loaded.
 * The per-species tint is a follow-up; a plain frog reads clearly for now.
 */
public class VirtualTerrariumFrogRenderer
        implements BlockEntityRenderer<VirtualTerrariumBlockEntity, VirtualTerrariumFrogRenderer.DomeRenderState> {

    private static final float SCALE = 0.6F;
    private static final double DOME_Y = 1.05;

    private final EntityRenderDispatcher dispatcher;
    private Entity phantom;

    public VirtualTerrariumFrogRenderer(BlockEntityRendererProvider.Context ctx) {
        this.dispatcher = ctx.entityRenderer();
    }

    @Override
    public DomeRenderState createRenderState() {
        return new DomeRenderState();
    }

    @Override
    public void extractRenderState(VirtualTerrariumBlockEntity be, DomeRenderState state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPosition, breakProgress);
        state.active = false;
        if (be.getLevel() == null || be.getInventory().getFrog().isEmpty()) {
            return;
        }
        if (phantom == null) {
            phantom = EntityType.FROG.create(be.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        }
        if (phantom == null) {
            return;
        }
        long time = be.getLevel().getGameTime();
        phantom.tickCount = (int) time;
        float yaw = 180.0F;
        double wx = be.getBlockPos().getX() + 0.5;
        double wy = be.getBlockPos().getY() + DOME_Y;
        double wz = be.getBlockPos().getZ() + 0.5;
        phantom.setPos(wx, wy, wz);
        phantom.setYRot(yaw);
        phantom.setOldPosAndRot();
        phantom.yRotO = yaw;
        if (phantom instanceof net.minecraft.world.entity.LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
            living.setYHeadRot(yaw);
            living.yHeadRotO = yaw;
        }
        EntityRenderState replica = dispatcher.extractEntity(phantom, partialTick);
        replica.shadowRadius = 0.0F;
        state.replica = replica;
        state.active = true;
    }

    @Override
    public void submit(DomeRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.active || state.replica == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, DOME_Y, 0.5);
        poseStack.scale(SCALE, SCALE, SCALE);
        dispatcher.submit(state.replica, camera, 0.0, 0.0, 0.0, poseStack, collector);
        poseStack.popPose();
    }

    public static class DomeRenderState extends BlockEntityRenderState {
        public boolean active;
        public EntityRenderState replica;
    }
}
