package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.client.screen.TerrariumControllerScreen;
import com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Draws a red line-box around the block the {@link TerrariumControllerBlockEntity}'s
 * validator flagged as the first structural problem, so the player can walk straight
 * to it instead of hunting for the shell gap. The position is the client-synced
 * {@link TerrariumControllerBlockEntity#clientProblemPos()} (relative to the
 * Controller's own render origin), the companion to the coordinates shown in the
 * status GUI.
 *
 * <p>The outline is scoped to the Controller the player is actively diagnosing - the
 * one their crosshair is on, or whose status screen is open - so nearby unformed
 * Controllers don't clutter the view with persistent boxes.
 *
 * <p>Render-only and GameTest-blind; verify with a {@code runClient} pass.
 */
public class TerrariumControllerRenderer implements BlockEntityRenderer<TerrariumControllerBlockEntity> {

    /** Red outline, split into 0-255 channels for the line vertex builder. */
    private static final int OUT_R = 0xFF;
    private static final int OUT_G = 0x30;
    private static final int OUT_B = 0x30;
    private static final int OUT_A = 0xFF;

    public TerrariumControllerRenderer(BlockEntityRendererProvider.Context context) {
        // No context state needed - the outline is pure geometry.
    }

    @Override
    public void render(TerrariumControllerBlockEntity be, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockPos problem = be.clientProblemPos();
        if (problem == null || !isDiagnosing(be.getBlockPos())) {
            return;
        }
        BlockPos origin = be.getBlockPos();
        int ox = problem.getX() - origin.getX();
        int oy = problem.getY() - origin.getY();
        int oz = problem.getZ() - origin.getZ();

        // The BER pose sits at the Controller's block origin; offset each edge to the
        // flagged block and draw the unit-cube outline there.
        VertexConsumer buffer = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose last = pose.last();
        Shapes.block().forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float nx = (float) (x2 - x1);
            float ny = (float) (y2 - y1);
            float nz = (float) (z2 - z1);
            buffer.addVertex(last, (float) (x1 + ox), (float) (y1 + oy), (float) (z1 + oz))
                .setColor(OUT_R, OUT_G, OUT_B, OUT_A).setNormal(last, nx, ny, nz);
            buffer.addVertex(last, (float) (x2 + ox), (float) (y2 + oy), (float) (z2 + oz))
                .setColor(OUT_R, OUT_G, OUT_B, OUT_A).setNormal(last, nx, ny, nz);
        });
    }

    /** True when the player is looking at this Controller or has its status screen open. */
    private static boolean isDiagnosing(BlockPos controllerPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(controllerPos)) {
            return true;
        }
        return mc.screen instanceof TerrariumControllerScreen screen
            && controllerPos.equals(screen.getMenu().controllerPos());
    }
}
