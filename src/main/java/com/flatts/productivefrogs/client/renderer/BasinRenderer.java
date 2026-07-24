package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.entity.SlimeMilkBasinBlockEntity;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Renders a Slime Milk Basin's held charge: one translucent surface inside the
 * half-block bowl, at a height proportional to the remaining spawn budget,
 * drawn with the held variant's own milk sprite and tint.
 *
 * <p>The {@link CrucibleRenderer}'s shape, simplified to a single surface. On
 * this line the variant <b>is</b> its own fluid (v1.8 per-variant milk), so the
 * sprite and tint come straight off that fluid's client extensions - no
 * component walk and nothing to cache, because the lookup is a map hit on a
 * fluid the Basin already knows.
 */
public class BasinRenderer implements BlockEntityRenderer<SlimeMilkBasinBlockEntity> {

    /** Inset to the bowl's interior walls (the model's inner faces). */
    private static final float MIN_XZ = 3.0F / 16.0F;
    private static final float MAX_XZ = 13.0F / 16.0F;
    /** The bowl floor and the rim lip, between which the surface travels. */
    private static final float FLOOR_Y = 2.5F / 16.0F;
    private static final float RIM_Y = 7.5F / 16.0F;

    /**
     * Floor on the fill fraction so a nearly-spent charge still visibly holds
     * something instead of thinning to an invisible sliver on the bowl floor.
     */
    private static final float MIN_VISIBLE_FRAC = 0.15F;

    public BasinRenderer(BlockEntityRendererProvider.Context context) {
        // no model parts; pure quad rendering
    }

    @Override
    public void render(SlimeMilkBasinBlockEntity basin, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = basin.getLevel();
        ResourceLocation variant = basin.getContainedVariant();
        if (level == null || variant == null) {
            return;
        }
        Fluid fluid = PFVariantMilk.sourceFluid(variant);
        if (fluid == null) {
            // A pack-added variant with no registered milk fluid: nothing to draw.
            return;
        }
        // Full for an Endless charge, else remaining/capacity.
        float frac = basin.isInfinite()
            ? 1.0F
            : basin.getSpawnsRemaining() / (float) Math.max(1, basin.getSpawnsCapacity());
        FluidStack stack = new FluidStack(fluid, SlimeMilkBasinBlockEntity.CAPACITY_MB);
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
            .apply(ext.getStillTexture(stack));
        int tint = ext.getTintColor(fluid.defaultFluidState(), level, basin.getBlockPos());
        float y = Mth.lerp(Mth.clamp(frac, MIN_VISIBLE_FRAC, 1.0F), FLOOR_Y, RIM_Y);

        drawSurface(pose, buffers.getBuffer(RenderType.translucent()), sprite, y,
            (tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, (tint >>> 24) == 0 ? 0xFF : (tint >>> 24),
            packedLight);
    }

    /** One upward-facing quad spanning the bowl interior at height {@code y}. */
    private static void drawSurface(PoseStack pose, VertexConsumer buffer, TextureAtlasSprite sprite,
            float y, int r, int g, int b, int a, int packedLight) {
        float u0 = sprite.getU(MIN_XZ);
        float u1 = sprite.getU(MAX_XZ);
        float v0 = sprite.getV(MIN_XZ);
        float v1 = sprite.getV(MAX_XZ);
        PoseStack.Pose last = pose.last();
        buffer.addVertex(last, MIN_XZ, y, MIN_XZ).setColor(r, g, b, a).setUv(u0, v0)
            .setLight(packedLight).setNormal(last, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(last, MIN_XZ, y, MAX_XZ).setColor(r, g, b, a).setUv(u0, v1)
            .setLight(packedLight).setNormal(last, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(last, MAX_XZ, y, MAX_XZ).setColor(r, g, b, a).setUv(u1, v1)
            .setLight(packedLight).setNormal(last, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(last, MAX_XZ, y, MIN_XZ).setColor(r, g, b, a).setUv(u1, v0)
            .setLight(packedLight).setNormal(last, 0.0F, 1.0F, 0.0F);
    }
}
