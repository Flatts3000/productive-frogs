package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.SummonReceptacleBlock;
import com.flatts.productivefrogs.content.block.entity.SummonReceptacleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the held item (Soul Sand or a Wither Skeleton Skull) on a filled
 * {@link SummonReceptacleBlock} (#247), flat against the block's -Z face -
 * the face toward Witherbane and the player at the Hatch end - so the loaded ritual
 * is visible at a glance. Empty receptacles draw nothing.
 *
 * <p>26.1 port: the held stack is baked into an {@link ItemStackRenderState} during
 * {@code extractRenderState} via the context {@link ItemModelResolver}, then emitted
 * in {@code submit} through {@link ItemStackRenderState#submit} (the same path the
 * vanilla campfire renderer uses) - replacing the old
 * {@code ItemRenderer.renderStatic} MultiBufferSource call.
 *
 * <p>Render-only, gated on the {@code FILLED} blockstate (synced from the BE's
 * {@code Held} update tag, so it appears the moment an item is inserted). Transform
 * constants are a first pass that may want a {@code runClient} tuning nudge.
 */
public class SummonReceptacleRenderer
        implements BlockEntityRenderer<SummonReceptacleBlockEntity, SummonReceptacleRenderer.WitherSummonReceptacleRenderState> {

    private final ItemModelResolver itemModelResolver;

    public SummonReceptacleRenderer(BlockEntityRendererProvider.Context ctx) {
        this.itemModelResolver = ctx.itemModelResolver();
    }

    @Override
    public WitherSummonReceptacleRenderState createRenderState() {
        return new WitherSummonReceptacleRenderState();
    }

    @Override
    public void extractRenderState(SummonReceptacleBlockEntity be, WitherSummonReceptacleRenderState state,
            float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        state.item = new ItemStackRenderState();
        boolean filled = be.getBlockState().hasProperty(SummonReceptacleBlock.FILLED)
            && be.getBlockState().getValue(SummonReceptacleBlock.FILLED);
        ItemStack stack = be.contents();
        if (!filled || stack.isEmpty()) {
            state.filled = false;
            return;
        }
        state.filled = true;
        state.ritualYaw = be.ritual().toYRot();
        state.onTop = be.getBlockState().getBlock() instanceof SummonReceptacleBlock rb
            && rb.displayMode() == SummonReceptacleBlock.DisplayMode.TOP;
        // The receptacle is a solid full cube, so the default lightCoords (sampled
        // AT the BE position) is pitch black. Sample where the item actually
        // renders: the block above (TOP) or the neighbor off the displayed face.
        if (be.getLevel() != null) {
            net.minecraft.core.BlockPos samplePos = state.onTop
                ? be.getBlockPos().above()
                : be.getBlockPos().relative(be.ritual().getOpposite());
            int blockLight = be.getLevel().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, samplePos);
            int skyLight = be.getLevel().getBrightness(net.minecraft.world.level.LightLayer.SKY, samplePos);
            // packed lightmap coords: (sky << 20) | (block << 4) - the stable wire format
            state.itemLight = (skyLight << 20) | (blockLight << 4);
        } else {
            state.itemLight = state.lightCoords;
        }
        this.itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED,
            be.getLevel(), null, 0);
    }

    @Override
    public void submit(WitherSummonReceptacleRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.filled || state.item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        if (state.onTop) {
            // TOP mode (block-shaped fuel, e.g. the sculk shrieker): the held block
            // stands on the receptacle's top surface, End-Crystal-receptacle style.
            // FIXED context centers a block model on the translate point, so lift by
            // half the scaled block above the top face.
            poseStack.translate(0.5F, 1.0F + 0.35F, 0.5F);
            poseStack.scale(0.7F, 0.7F, 0.7F);
            state.item.submit(poseStack, collector, state.itemLight, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
            return;
        }
        // The held item sits on the receptacle face that points back toward the Hatch/arena,
        // so the loaded ritual reads at a glance. In the canonical (SOUTH ritual) frame that
        // is the -Z face and the wither skull's FIXED orientation already faces -Z. For any
        // other build orientation, apply the same structure rotation the validator used
        // (SOUTH -> ritual is a +Y rotation of -ritual.toYRot()), so the item swings onto the
        // correct face and keeps facing inward. Soul sand is a symmetric cube; the skull is a
        // block-entity item whose face turns with the rotation.
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.ritualYaw));
        poseStack.translate(0.0F, 0.0F, -0.44F);
        poseStack.scale(0.7F, 0.7F, 0.7F);
        state.item.submit(poseStack, collector, state.itemLight, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /** Captured held-item render state for one frame. */
    public static class WitherSummonReceptacleRenderState extends BlockEntityRenderState {
        public boolean filled;
        public boolean onTop;
        public float ritualYaw;
        /** Light sampled at the item's render position (the block itself is solid = dark). */
        public int itemLight;
        public ItemStackRenderState item = new ItemStackRenderState();
    }
}
