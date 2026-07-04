package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.WitherAltarHatchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * In-world animation for the Wither Altar (#247), driven by the synced summon
 * progress ({@link WitherAltarHatchBlockEntity#summonTicks()}). It copies the vanilla
 * Wither spawn exactly, minus the boss bar: a phantom Wither is rendered through the
 * vanilla {@link EntityRenderDispatcher} with its invulnerable-ticks counting 220 -> 0
 * over the summon, so the stock {@code WitherBossRenderer} grows it from 1.5x to its
 * full 2.0x size, shows the blue {@code wither_invulnerable} charging texture, and
 * plays the spawn head animation - then Witherbane devours it at full size. The
 * phantom is never in the world and is never a server {@code BossEvent}, so no boss
 * bar appears.
 *
 * <p>Render-only and GameTest-blind; the cavity position is a first pass that may want
 * a {@code runClient} tuning nudge.
 */
public class WitherAltarHatchRenderer implements BlockEntityRenderer<WitherAltarHatchBlockEntity> {

    /** Vanilla Wither invulnerable-spawn length; drives the stock spawn scale/texture formula. */
    private static final int VANILLA_INVULNERABLE_TICKS = 220;

    /** A client-side phantom wither fed to the vanilla renderer; never in the world. */
    private WitherBoss phantom;

    public WitherAltarHatchRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(WitherAltarHatchBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
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
            phantom = EntityType.WITHER.create(be.getLevel());
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

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        pose.pushPose();
        // Hover in the cavity, in FRONT of the receptacle wall so the frog at the Hatch end
        // sees it. The canonical offset is +Z 1.2 from the Hatch centre; rotate it by the
        // resolved ritual so the replica forms on the correct side for any build orientation.
        // Lifted clear of the floor froglights - the Wither floats in vanilla and its model
        // hangs below its position point, so feet-on-floor clips downward.
        double[] off = horizontalOffset(0.0, 1.2, ritual);
        pose.translate(0.5F + (float) off[0], 0.0F, 0.5F + (float) off[1]);
        dispatcher.setRenderShadow(false);
        dispatcher.render(phantom, 0.0, 0.0, 0.0, yaw, partialTick, pose, buffers, packedLight);
        dispatcher.setRenderShadow(true);
        pose.popPose();
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
}
