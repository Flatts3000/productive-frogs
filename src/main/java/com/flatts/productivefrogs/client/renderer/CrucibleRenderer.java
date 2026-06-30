package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Renders the Froglight Crucible's interior: the rising fluid surface and,
 * above it, the not-yet-melted "solids" surface drawn with the froglight
 * texture tinted by the most recent variant's primary color. Behavioral
 * mirror of Ex Deorum's crucible renderer (original implementation).
 *
 * <p>Both surfaces are flat quads inside the basin (x/z 2..14 px), lerped
 * between the interior floor (y=4 px) and the rim (y=15 px) by their
 * fill fraction. Fluid renders translucent with the fluid type's own still
 * sprite + tint; solids render cutout with the shared froglight top sprite.
 *
 * <p>26.1 port: the live fill state is captured into {@link CrucibleRenderState}
 * during {@code extractRenderState} (sprites resolved off the block atlas there)
 * and each surface is emitted in {@code submit} via
 * {@link SubmitNodeCollector#submitCustomGeometry} on the cutout / translucent
 * block sheets. The submit pipeline orders the cutout pass before the translucent
 * one, so the depth ordering the old draw-order comment guarded is now implicit.
 */
public class CrucibleRenderer implements BlockEntityRenderer<CrucibleBlockEntity, CrucibleRenderer.CrucibleRenderState> {

    private static final float MIN_XZ = 2.0F / 16.0F;
    private static final float MAX_XZ = 14.0F / 16.0F;
    private static final float FLOOR_Y = 4.0F / 16.0F;
    private static final float RIM_Y = 15.0F / 16.0F;

    /** The shared froglight texture every Configurable Froglight tints. */
    private static final Identifier FROGLIGHT_TOP =
        Identifier.withDefaultNamespace("block/ochre_froglight_top");

    private final SpriteGetter sprites;

    public CrucibleRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
    }

    @Override
    public CrucibleRenderState createRenderState() {
        return new CrucibleRenderState();
    }

    @Override
    public void extractRenderState(CrucibleBlockEntity crucible, CrucibleRenderState state, float partialTicks,
            Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(crucible, state, partialTicks, cameraPosition, breakProgress);
        state.hasSolids = false;
        state.hasFluid = false;
        Level level = crucible.getLevel();
        if (level == null) {
            return;
        }
        FluidStack fluidStack = crucible.fluid();
        float fluidFrac = fluidStack.getAmount() / (float) CrucibleBlockEntity.TANK_CAPACITY;
        float solidsFrac = crucible.solids() / (float) CrucibleBlockEntity.MAX_SOLIDS;
        if (fluidFrac <= 0.0F && solidsFrac <= 0.0F) {
            return;
        }

        if (solidsFrac > 0.0F) {
            state.hasSolids = true;
            state.solidsSprite = sprites.get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, FROGLIGHT_TOP));
            state.solidsColor = 0xFF000000 | (variantTint(crucible, level) & 0xFFFFFF);
            state.solidsY = Mth.lerp(solidsFrac, FLOOR_Y, RIM_Y);
        }

        if (fluidFrac > 0.0F) {
            Fluid fluid = fluidStack.getFluid();
            // 26.1: the fluid still sprite + tint moved off IClientFluidTypeExtensions
            // onto the baked FluidModel - the sprite is the still material's sprite and
            // the tint resolves through the model's FluidTintSource (null for fluids
            // with no registered tint, in which case we render untinted/white).
            FluidModel fluidModel = Minecraft.getInstance().getModelManager()
                .getFluidStateModelSet().get(fluid.defaultFluidState());
            FluidTintSource tint = fluidModel.fluidTintSource();
            int rawTint = tint == null ? 0xFFFFFF : tint.colorAsStack(fluidStack);
            state.hasFluid = true;
            state.fluidSprite = fluidModel.stillMaterial().sprite();
            state.fluidColor = 0xFF000000 | (rawTint & 0xFFFFFF);
            state.fluidY = Mth.lerp(fluidFrac, FLOOR_Y, RIM_Y);
        }
    }

    @Override
    public void submit(CrucibleRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        // The OPAQUE solids surface is submitted on the cutout block sheet and the
        // fluid on the translucent sheet; the submit pipeline renders cutout before
        // translucent, preserving the depth ordering (solids visible through the
        // translucent fluid) the old single-buffer path arranged by draw order.
        if (state.hasSolids) {
            collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockSheet(),
                (pose, buffer) -> drawSurface(pose, buffer, state.solidsSprite, state.solidsY,
                    state.solidsColor, state.lightCoords));
        }
        if (state.hasFluid) {
            collector.submitCustomGeometry(poseStack, Sheets.translucentBlockSheet(),
                (pose, buffer) -> drawSurface(pose, buffer, state.fluidSprite, state.fluidY,
                    state.fluidColor, state.lightCoords));
        }
    }

    /** Primary color of the most recent variant, or white when unknown. */
    private static int variantTint(CrucibleBlockEntity crucible, Level level) {
        Identifier variantId = crucible.lastVariant();
        if (variantId == null) {
            return 0xFFFFFF;
        }
        SlimeVariant v = PFRegistries.variant(level.registryAccess(), variantId);
        return v == null ? 0xFFFFFF : v.primaryColor();
    }

    /** One upward-facing quad spanning the basin interior at height {@code y}. */
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
            .setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(pose, MIN_XZ, y, MAX_XZ).setColor(r, g, b, a).setUv(u0, v1)
            .setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(pose, MAX_XZ, y, MAX_XZ).setColor(r, g, b, a).setUv(u1, v1)
            .setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(pose, MAX_XZ, y, MIN_XZ).setColor(r, g, b, a).setUv(u1, v0)
            .setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** Captured basin fill state for one frame. */
    public static class CrucibleRenderState extends BlockEntityRenderState {
        public boolean hasSolids;
        public TextureAtlasSprite solidsSprite;
        public int solidsColor;
        public float solidsY;

        public boolean hasFluid;
        public TextureAtlasSprite fluidSprite;
        public int fluidColor;
        public float fluidY;
    }
}
