package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.client.screen.TerrariumControllerScreen;
import com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
 * <p>26.1 render path: the flagged offset is captured in {@code extractRenderState}
 * and the box is emitted in {@code submit} via
 * {@link SubmitNodeCollector#submitCustomGeometry} on the {@code lines} render type,
 * mirroring vanilla {@code ShapeRenderer}. Render-only and GameTest-blind - verify
 * with a {@code runClient} pass.
 */
public class TerrariumControllerRenderer
        implements BlockEntityRenderer<TerrariumControllerBlockEntity, TerrariumControllerRenderer.ProblemOutlineState> {

    /** Opaque red for the problem outline. */
    private static final int OUTLINE_ARGB = 0xFFFF3030;
    private static final float OUTLINE_WIDTH = 2.0F;

    public TerrariumControllerRenderer(BlockEntityRendererProvider.Context context) {
        // No context state needed - the outline is pure geometry.
    }

    @Override
    public ProblemOutlineState createRenderState() {
        return new ProblemOutlineState();
    }

    @Override
    public void extractRenderState(TerrariumControllerBlockEntity be, ProblemOutlineState state, float partialTicks,
            Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        state.active = false;
        BlockPos problem = be.clientProblemPos();
        if (problem == null || !isDiagnosing(be.getBlockPos())) {
            return;
        }
        BlockPos origin = be.getBlockPos();
        state.dx = problem.getX() - origin.getX();
        state.dy = problem.getY() - origin.getY();
        state.dz = problem.getZ() - origin.getZ();
        state.active = true;
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

    @Override
    public void submit(ProblemOutlineState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.active) {
            return;
        }
        // The BER pose sits at the Controller's block origin; bake the offset to the
        // flagged block into each vertex (the deferred lambda uses its captured pose,
        // so the PoseStack itself is not translated here).
        int ox = state.dx;
        int oy = state.dy;
        int oz = state.dz;
        collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) ->
            Shapes.block().forAllEdges((x1, y1, z1, x2, y2, z2) -> {
                float nx = (float) (x2 - x1);
                float ny = (float) (y2 - y1);
                float nz = (float) (z2 - z1);
                buffer.addVertex(pose, (float) (x1 + ox), (float) (y1 + oy), (float) (z1 + oz))
                    .setColor(OUTLINE_ARGB).setNormal(pose, nx, ny, nz).setLineWidth(OUTLINE_WIDTH);
                buffer.addVertex(pose, (float) (x2 + ox), (float) (y2 + oy), (float) (z2 + oz))
                    .setColor(OUTLINE_ARGB).setNormal(pose, nx, ny, nz).setLineWidth(OUTLINE_WIDTH);
            }));
    }

    /** Captured problem-block offset (from the Controller) for one frame. */
    public static class ProblemOutlineState extends BlockEntityRenderState {
        public boolean active;
        public int dx;
        public int dy;
        public int dz;
    }
}
