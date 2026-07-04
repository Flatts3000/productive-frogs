package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.entity.AbstractBasinBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Renders a Basin's held charge (#281 Phase 3, maintainer ruling: the Basin
 * shows the fluid that's in it): one translucent surface inside the half-block
 * bowl, at a height proportional to the remaining spawn budget, tinted by the
 * contents - the slurry purple, or the milk variant's {@code primary_color}.
 *
 * <p>The Crucible renderer's shape, simplified: the sprite comes from the
 * basin fluid's baked {@link FluidModel}, and the tint resolves through the
 * model's {@link FluidTintSource#colorAsStack} against a stack stamped with
 * the Basin's contained key - one uniform path for both Basin flavours
 * (Mob Slurry's tint is a constant; Slime Milk's colorAsStack reads the
 * {@code SLIME_VARIANT} component).
 */
public class BasinRenderer implements BlockEntityRenderer<AbstractBasinBlockEntity, BasinRenderer.BasinRenderState> {

    private static final float MIN_XZ = 3.0F / 16.0F;
    private static final float MAX_XZ = 13.0F / 16.0F;
    /** The bowl floor (the model's inner floor face) and the rim lip. */
    private static final float FLOOR_Y = 2.5F / 16.0F;
    private static final float RIM_Y = 7.5F / 16.0F;

    public BasinRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public BasinRenderState createRenderState() {
        return new BasinRenderState();
    }

    @Override
    public void extractRenderState(AbstractBasinBlockEntity basin, BasinRenderState state, float partialTicks,
            Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(basin, state, partialTicks, cameraPosition, breakProgress);
        state.hasFluid = false;
        Identifier key = basin.getContainedKey();
        if (key == null) {
            return;
        }
        FluidModel fluidModel = Minecraft.getInstance().getModelManager()
            .getFluidStateModelSet().get(basin.pipeFluid().defaultFluidState());
        FluidStack stack = new FluidStack(basin.pipeFluid(), 1000);
        stack.set(basin.pipeKeyComponent(), key);
        FluidTintSource tint = fluidModel.fluidTintSource();
        int rawTint = tint == null ? 0xFFFFFF : tint.colorAsStack(stack);

        // Fill height tracks the remaining budget (full for an Endless charge);
        // floored so a nearly-spent charge still visibly holds fluid.
        float frac = basin.isInfinite() ? 1.0F
            : basin.getSpawnsRemaining() / (float) Math.max(1, basin.getSpawnsCapacity());
        state.hasFluid = true;
        state.fluidSprite = fluidModel.stillMaterial().sprite();
        state.fluidColor = 0xFF000000 | (rawTint & 0xFFFFFF);
        state.fluidY = Mth.lerp(Mth.clamp(frac, 0.15F, 1.0F), FLOOR_Y, RIM_Y);
    }

    @Override
    public void submit(BasinRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (state.hasFluid) {
            collector.submitCustomGeometry(poseStack, Sheets.translucentBlockSheet(),
                (pose, buffer) -> drawSurface(pose, buffer, state.fluidSprite, state.fluidY,
                    state.fluidColor, state.lightCoords));
        }
    }

    /** One upward-facing quad spanning the bowl interior at height {@code y}. */
    private static void drawSurface(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
            float y, int argb, int packedLight) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = (argb >>> 24) & 0xFF;
        float u0 = sprite.getU(MIN_XZ);
        float u1 = sprite.getU(MAX_XZ);
        float v0 = sprite.getV(MIN_XZ);
        float v1 = sprite.getV(MAX_XZ);
        buffer.addVertex(pose, MIN_XZ, y, MIN_XZ).setColor(r, g, b, a).setUv(u0, v0)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(pose, MIN_XZ, y, MAX_XZ).setColor(r, g, b, a).setUv(u0, v1)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(pose, MAX_XZ, y, MAX_XZ).setColor(r, g, b, a).setUv(u1, v1)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(pose, MAX_XZ, y, MIN_XZ).setColor(r, g, b, a).setUv(u1, v0)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** Captured fill state for one frame. */
    public static class BasinRenderState extends BlockEntityRenderState {
        public boolean hasFluid;
        public TextureAtlasSprite fluidSprite;
        public int fluidColor;
        public float fluidY;
    }
}
