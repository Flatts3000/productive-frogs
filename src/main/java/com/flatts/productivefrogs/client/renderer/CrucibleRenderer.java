package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.entity.CrucibleBlockEntity;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
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
 */
public class CrucibleRenderer implements BlockEntityRenderer<CrucibleBlockEntity> {

    private static final float MIN_XZ = 2.0F / 16.0F;
    private static final float MAX_XZ = 14.0F / 16.0F;
    private static final float FLOOR_Y = 4.0F / 16.0F;
    private static final float RIM_Y = 15.0F / 16.0F;

    /** The shared froglight texture every Configurable Froglight tints. */
    private static final Identifier FROGLIGHT_TOP =
        Identifier.withDefaultNamespace("block/ochre_froglight_top");

    public CrucibleRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
        // no model parts; pure quad rendering
    }

    @Override
    public void render(CrucibleBlockEntity crucible, float partialTicks, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
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
        BlockPos pos = crucible.getBlockPos();

        // Draw order matters: the OPAQUE solids surface must be submitted
        // BEFORE the translucent fluid. The fluid quad writes depth, so a
        // solids quad drawn after it (at a lower height, seen through the
        // water) would fail the depth test and vanish instead of showing
        // through the translucent surface.
        if (solidsFrac > 0.0F) {
            TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(FROGLIGHT_TOP);
            int tint = variantTint(crucible, level);
            float y = Mth.lerp(solidsFrac, FLOOR_Y, RIM_Y);
            drawSurface(pose, buffers.getBuffer(RenderType.cutout()), sprite, y,
                (tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, 0xFF, packedLight);
        }

        if (fluidFrac > 0.0F) {
            Fluid fluid = fluidStack.getFluid();
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
            TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ext.getStillTexture(fluidStack));
            int tint = ext.getTintColor(fluid.defaultFluidState(), level, pos);
            float y = Mth.lerp(fluidFrac, FLOOR_Y, RIM_Y);
            drawSurface(pose, buffers.getBuffer(RenderType.translucent()), sprite, y,
                (tint >> 16) & 0xFF, (tint >> 8) & 0xFF, tint & 0xFF, 0xFF, packedLight);
        }
    }

    /** Primary color of the most recent variant, or white when unknown. */
    private static int variantTint(CrucibleBlockEntity crucible, Level level) {
        Identifier variantId = crucible.lastVariant();
        if (variantId == null) {
            return 0xFFFFFF;
        }
        return level.registryAccess().registry(PFRegistries.SLIME_VARIANT)
            .map(reg -> reg.get(variantId))
            .map(SlimeVariant::primaryColor)
            .orElse(0xFFFFFF);
    }

    /** One upward-facing quad spanning the basin interior at height {@code y}. */
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
