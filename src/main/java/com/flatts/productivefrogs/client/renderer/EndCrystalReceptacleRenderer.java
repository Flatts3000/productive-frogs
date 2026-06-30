package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.EndCrystalReceptacleBlock;
import com.flatts.productivefrogs.content.block.entity.EndCrystalReceptacleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Renders a vanilla-style floating, spinning End Crystal on top of a filled
 * {@link EndCrystalReceptacleBlock} (#249), scaled down to perch on the block
 * rather than fill an entity hitbox. No base ring and no beam here; the
 * converging summon beams are added with the altar animation.
 *
 * <p>26.1 port: the whole {@link EndCrystalModel} is driven through its own
 * {@code setupAnim} (which self-contains the glass/cube bob + triple-nested
 * spin off {@code ageInTicks}) and emitted via
 * {@link SubmitNodeCollector#submitModel} with the entity end-crystal texture.
 * This replaces the old hand-rolled nested-quaternion render of the {@code glass}
 * / {@code cube} parts - whose layer-child names no longer exist on the 26.1
 * crystal model - and is an exact match for the vanilla crystal animation.
 *
 * <p>Render-only: gated on the {@code FILLED} blockstate, so an empty receptacle
 * draws nothing. The perch height / scale are a sensible first pass and may want
 * a {@code runClient} tuning nudge.
 */
public class EndCrystalReceptacleRenderer
        implements BlockEntityRenderer<EndCrystalReceptacleBlockEntity, EndCrystalReceptacleRenderer.EndCrystalReceptacleRenderState> {

    private static final Identifier TEXTURE =
        Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");

    /** How high the crystal's pivot sits above the block origin (1.0 = the top face). Tunable. */
    private static final float HOVER_Y = 1.35F;
    /** Perch size relative to the raw model (the full entity crystal renders at 2x). Tunable. */
    private static final float PERCH_SCALE = 0.5F;

    private final EndCrystalModel model;

    public EndCrystalReceptacleRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new EndCrystalModel(context.bakeLayer(ModelLayers.END_CRYSTAL));
    }

    @Override
    public EndCrystalReceptacleRenderState createRenderState() {
        return new EndCrystalReceptacleRenderState();
    }

    @Override
    public void extractRenderState(EndCrystalReceptacleBlockEntity be, EndCrystalReceptacleRenderState state,
            float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, cameraPosition, breakProgress);
        state.filled = be.getBlockState().hasProperty(EndCrystalReceptacleBlock.FILLED)
            && be.getBlockState().getValue(EndCrystalReceptacleBlock.FILLED);
        long gameTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        state.ageInTicks = (float) gameTime + partialTicks;
    }

    @Override
    public void submit(EndCrystalReceptacleRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.filled) {
            return;
        }
        EndCrystalRenderState crystal = new EndCrystalRenderState();
        crystal.ageInTicks = state.ageInTicks;
        crystal.showsBottom = false; // no base ring on the perch
        this.model.setupAnim(crystal);

        poseStack.pushPose();
        poseStack.translate(0.5F, HOVER_Y, 0.5F);          // perch above the block top
        poseStack.scale(PERCH_SCALE, PERCH_SCALE, PERCH_SCALE);
        poseStack.translate(0.0F, -0.5F, 0.0F);            // seat the model origin (mirrors vanilla)
        collector.submitModel(this.model, crystal, poseStack, TEXTURE,
            state.lightCoords, OverlayTexture.NO_OVERLAY, 0, null);
        poseStack.popPose();
    }

    /** Captured fill state + animation clock for one frame. */
    public static class EndCrystalReceptacleRenderState extends BlockEntityRenderState {
        public boolean filled;
        public float ageInTicks;
    }
}
