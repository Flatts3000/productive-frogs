package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.WitherSummonReceptacleBlock;
import com.flatts.productivefrogs.content.block.entity.WitherSummonReceptacleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the held item (Soul Sand or a Wither Skeleton Skull) on a filled
 * {@link WitherSummonReceptacleBlock} (#247), flat against the block's -Z face -
 * the face toward Witherbane and the player at the Hatch end - so the loaded ritual
 * is visible at a glance. Empty receptacles draw nothing.
 *
 * <p>Render-only, gated on the {@code FILLED} blockstate (synced from the BE's
 * {@code Held} update tag, so it appears the moment an item is inserted). Transform
 * constants are a first pass that may want a {@code runClient} tuning nudge.
 */
public class WitherSummonReceptacleRenderer implements BlockEntityRenderer<WitherSummonReceptacleBlockEntity> {

    private final ItemRenderer itemRenderer;

    public WitherSummonReceptacleRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(WitherSummonReceptacleBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!be.getBlockState().hasProperty(WitherSummonReceptacleBlock.FILLED)
                || !be.getBlockState().getValue(WitherSummonReceptacleBlock.FILLED)) {
            return;
        }
        ItemStack stack = be.contents();
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        // The held item sits on the receptacle face that points back toward the Hatch/arena,
        // so the loaded ritual reads at a glance. In the canonical (SOUTH ritual) frame that
        // is the -Z face and the wither skull's FIXED orientation already faces -Z. For any
        // other build orientation, apply the same structure rotation the validator used
        // (SOUTH -> ritual is a +Y rotation of -ritual.toYRot()), so the item swings onto the
        // correct face and keeps facing inward. Soul sand is a symmetric cube; the skull is a
        // block-entity item whose face turns with the rotation.
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-be.ritual().toYRot()));
        pose.translate(0.0F, 0.0F, -0.44F);
        pose.scale(0.7F, 0.7F, 0.7F);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
            pose, buffers, be.getLevel(), 0);
        pose.popPose();
    }
}
