package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.WitherAltarHatchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/**
 * In-world animation for the Wither Altar (#247), driven by the synced summon
 * progress ({@link WitherAltarHatchBlockEntity#summonTicks()}). It copies the vanilla
 * Wither spawn exactly, minus the boss bar: a phantom Wither's invulnerable-ticks
 * count 220 -> 0 over the summon, so the stock {@code WitherBossRenderer} grows it
 * from 1.5x to its full 2.0x size, shows the blue {@code wither_invulnerable}
 * charging texture, and plays the spawn head animation - then Witherbane devours it
 * at full size. The phantom is never in the world and is never a server
 * {@code BossEvent}, so no boss bar appears.
 *
 * <p>26.1 port: the phantom's spawn fields are set during {@code extractRenderState},
 * captured into a vanilla {@link EntityRenderState} via
 * {@link EntityRenderDispatcher#extractEntity}, and replayed in {@code submit} via
 * {@link EntityRenderDispatcher#submit} - replacing the old
 * {@code dispatcher.render(...)} MultiBufferSource call. The shadow is suppressed by
 * zeroing the captured state's shadow radius (the old {@code setRenderShadow(false)}).
 *
 * <p>Render-only and GameTest-blind; the cavity position is a first pass that may want
 * a {@code runClient} tuning nudge.
 */
public class WitherAltarHatchRenderer
        implements BlockEntityRenderer<WitherAltarHatchBlockEntity, WitherAltarHatchRenderer.WitherAltarHatchRenderState> {

    /** Vanilla Wither invulnerable-spawn length; drives the stock spawn scale/texture formula. */
    private static final int VANILLA_INVULNERABLE_TICKS = 220;

    private final EntityRenderDispatcher dispatcher;

    /** A client-side phantom wither fed to the vanilla renderer; never in the world. */
    private WitherBoss phantom;

    public WitherAltarHatchRenderer(BlockEntityRendererProvider.Context ctx) {
        this.dispatcher = ctx.entityRenderer();
    }

    @Override
    public WitherAltarHatchRenderState createRenderState() {
        return new WitherAltarHatchRenderState();
    }

    @Override
    public void extractRenderState(WitherAltarHatchBlockEntity be, WitherAltarHatchRenderState state,
            float partialTick, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPosition, breakProgress);
        state.active = false;
        if (be.summonTicks() <= 0 || be.getLevel() == null) {
            be.clientSummonStartGameTime = -1L;
            return;
        }
        // summonTicks is only synced at start/end (the on/off signal), so drive the
        // spawn from the client's own elapsed time since the summon was first observed.
        long time = be.getLevel().getGameTime();
        if (be.clientSummonStartGameTime < 0L) {
            be.clientSummonStartGameTime = time;
        }
        float elapsed = (time - be.clientSummonStartGameTime) + partialTick;
        float progress = Mth.clamp(elapsed / PFConfig.witherAltarSummonTicks(), 0.0F, 1.0F);

        if (phantom == null) {
            phantom = EntityType.WITHER.create(be.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        }
        if (phantom == null) {
            return;
        }
        // Invulnerable ticks count 220 -> 0: the stock WitherBossRenderer reads this to
        // grow the model (1.5x -> 2.0x) and pick the blue charging texture, so the
        // replica reaches full vanilla size right as the summon completes.
        int inv = Math.round((1.0F - progress) * VANILLA_INVULNERABLE_TICKS);
        phantom.setInvulnerableTicks(inv);
        phantom.tickCount = (int) time;            // advance the idle head bob
        // Face back toward the Hatch / Witherbane: that is the ritual's opposite (canonical
        // ritual SOUTH -> face NORTH, yaw 180 - the old fixed value).
        Direction ritual = be.ritual();
        float yaw = ritual.getOpposite().toYRot();
        phantom.setYRot(yaw);
        phantom.yRotO = yaw;
        phantom.yBodyRot = yaw;
        phantom.yBodyRotO = yaw;
        phantom.yHeadRot = yaw;
        phantom.yHeadRotO = yaw;

        double[] off = horizontalOffset(0.0, 1.2, ritual);
        // Position the phantom at the replica's real world spot before extraction -
        // the extracted lightmap samples at the entity position (unplaced = bad light).
        phantom.setPos(be.getBlockPos().getX() + 0.5 + off[0], be.getBlockPos().getY(),
            be.getBlockPos().getZ() + 0.5 + off[1]);
        EntityRenderState wither = dispatcher.extractEntity(phantom, partialTick);
        wither.shadowRadius = 0.0F;                 // suppress the shadow (was setRenderShadow(false))
        state.wither = wither;
        state.offX = off[0];
        state.offZ = off[1];
        state.active = true;
    }

    @Override
    public void submit(WitherAltarHatchRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.active || state.wither == null) {
            return;
        }
        poseStack.pushPose();
        // Hover in the cavity, in FRONT of the receptacle wall so the frog at the Hatch end
        // sees it. The canonical offset is +Z 1.2 from the Hatch centre, rotated by the
        // resolved ritual. Lifted clear of the floor froglights - the Wither floats in
        // vanilla and its model hangs below its position point.
        poseStack.translate(0.5F + (float) state.offX, 0.0F, 0.5F + (float) state.offZ);
        dispatcher.submit(state.wither, camera, 0.0, 0.0, 0.0, poseStack, collector);
        poseStack.popPose();
    }

    /**
     * Rotate a canonical horizontal offset (frame: ritual = +Z / SOUTH) about the vertical
     * axis so the ritual points {@code ritual}. Mirrors
     * {@code WitherAltarValidator.rotateOffset} for the float render offsets.
     */
    private static double[] horizontalOffset(double x, double z, Direction ritual) {
        return switch (ritual) {
            case SOUTH -> new double[] {x, z};
            case WEST -> new double[] {-z, x};
            case NORTH -> new double[] {-x, -z};
            case EAST -> new double[] {z, -x};
            default -> new double[] {x, z};
        };
    }

    /** Captured phantom-wither render state for one frame. */
    public static class WitherAltarHatchRenderState extends BlockEntityRenderState {
        public boolean active;
        public EntityRenderState wither;
        public double offX;
        public double offZ;
    }
}
