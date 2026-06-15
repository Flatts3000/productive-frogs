package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.entity.WitherAltarHatchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
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
        float progress = Mth.clamp(elapsed / WitherAltarHatchBlockEntity.SUMMON_TICKS, 0.0F, 1.0F);

        if (phantom == null) {
            phantom = EntityType.WITHER.create(be.getLevel());
        }
        if (phantom == null) {
            return;
        }
        // Invulnerable ticks count 220 -> 0: the stock WitherBossRenderer reads this to
        // grow the model (1.5x -> 2.0x) and pick the blue charging texture, so the
        // replica reaches full vanilla size right as the summon completes.
        int inv = Math.round(Mth.lerp(progress, WitherAltarHatchBlockEntity.SUMMON_TICKS, 0.0F));
        phantom.setInvulnerableTicks(inv);
        phantom.tickCount = (int) time;            // advance the idle head bob
        phantom.setYRot(180.0F);                   // face -Z, toward Witherbane / the player
        phantom.yRotO = 180.0F;
        phantom.yBodyRot = 180.0F;
        phantom.yBodyRotO = 180.0F;
        phantom.yHeadRot = 180.0F;
        phantom.yHeadRotO = 180.0F;

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        pose.pushPose();
        // Feet near the cavity floor, in FRONT of the receptacle wall (offset z=3) so the
        // frog at z=0 sees it; centred on the 3-wide cavity.
        pose.translate(0.5F, -1.0F, 1.7F);
        dispatcher.setRenderShadow(false);
        dispatcher.render(phantom, 0.0, 0.0, 0.0, 180.0F, partialTick, pose, buffers, packedLight);
        dispatcher.setRenderShadow(true);
        pose.popPose();
    }
}
