package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The one upward-facing fluid-surface quad emitter shared by the Crucible and
 * the Basins (the review found two byte-identical private copies differing only
 * in their bounds constants - a vertex-format or overlay fix applied to one
 * would silently miss the other). Bounds are the interior square in block
 * fractions; the quad writes color, UV, {@code NO_OVERLAY} (required on the
 * block sheets - the UV1 crash), light, and an up normal per vertex.
 */
final class FluidSurfaces {

    private FluidSurfaces() {
    }

    static void drawSurface(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite,
            float minXz, float maxXz, float y, int argb, int packedLight) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = (argb >>> 24) & 0xFF;
        float u0 = sprite.getU(minXz);
        float u1 = sprite.getU(maxXz);
        float v0 = sprite.getV(minXz);
        float v1 = sprite.getV(maxXz);
        buffer.addVertex(pose, minXz, y, minXz).setColor(r, g, b, a).setUv(u0, v0)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(pose, minXz, y, maxXz).setColor(r, g, b, a).setUv(u0, v1)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(pose, maxXz, y, maxXz).setColor(r, g, b, a).setUv(u1, v1)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
        buffer.addVertex(pose, maxXz, y, minXz).setColor(r, g, b, a).setUv(u1, v0)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
