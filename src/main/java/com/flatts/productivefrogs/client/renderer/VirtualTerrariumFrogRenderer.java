package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.VirtualTerrariumProcessorBlock;
import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.EntityType;

/**
 * Draws the loaded frog inside the Virtual Terrarium's Display Dome. A client-side
 * vanilla Frog phantom (never in the world) is fed to the vanilla
 * {@link EntityRenderDispatcher}, positioned one block up inside the dome and scaled
 * down (the WitherAltarHatch display pattern). Renders nothing when the Processor has
 * no frog loaded or no Dome above.
 *
 * <p>A plain vanilla frog is used - the per-species tint is a follow-up; the frog
 * reads clearly either way, and the feedstock colour already shows the species in the
 * GUI.
 */
public class VirtualTerrariumFrogRenderer implements BlockEntityRenderer<VirtualTerrariumBlockEntity> {

    private static final float SCALE = 0.6F;
    private static final double DOME_Y = 1.05;

    /** A client-side phantom frog fed to the vanilla renderer; never in the world. */
    private Frog phantom;

    public VirtualTerrariumFrogRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
        // no model parts; delegates to the entity dispatcher
    }

    @Override
    public void render(VirtualTerrariumBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // No frog, or no Display Dome above -> render nothing (the dome IS the glass case).
        if (be.getLevel() == null || be.getInventory().getFrog().isEmpty() || !be.hasDome()) {
            return;
        }
        if (phantom == null) {
            phantom = EntityType.FROG.create(be.getLevel());
        }
        if (phantom == null) {
            return;
        }
        long time = be.getLevel().getGameTime();
        phantom.tickCount = (int) time;   // advance the idle bob
        // Face the frog toward the Processor's front (its horizontal FACING).
        float yaw = be.getBlockState().getValue(VirtualTerrariumProcessorBlock.FACING).toYRot();
        phantom.setYRot(yaw);
        phantom.yRotO = yaw;
        phantom.yBodyRot = yaw;
        phantom.yBodyRotO = yaw;
        phantom.yHeadRot = yaw;
        phantom.yHeadRotO = yaw;

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        pose.pushPose();
        pose.translate(0.5, DOME_Y, 0.5);
        pose.scale(SCALE, SCALE, SCALE);
        dispatcher.setRenderShadow(false);
        dispatcher.render(phantom, 0.0, 0.0, 0.0, yaw, partialTick, pose, buffers, packedLight);
        dispatcher.setRenderShadow(true);
        pose.popPose();
    }

    /** Keep the dome contents visible even when the Processor block is off-screen behind the dome. */
    @Override
    public boolean shouldRenderOffScreen(VirtualTerrariumBlockEntity be) {
        return true;
    }
}
