package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.ResourceLocation;

/**
 * Vanilla {@link SlimeRenderer} with a Void-Slime-specific texture and an
 * end-portal-frame purple outer-shell tint. The constructor swaps vanilla's
 * {@link SlimeOuterLayer} for a {@link TintedSlimeOuterLayer} so the species
 * reads as purple in-world instead of the vanilla green that previously
 * dominated the translucent shell. The tint is darker than the broader
 * ARCANE category lavender to evoke the End dimension specifically.
 */
public class VoidSlimeRenderer extends SlimeRenderer {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/void_slime.png");

    private static final int OUTER_TINT_ARGB = 0xFF5E3782;

    public VoidSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), OUTER_TINT_ARGB));
    }

    @Override
    public ResourceLocation getTextureLocation(SlimeRenderState state) {
        return TEXTURE;
    }
}
