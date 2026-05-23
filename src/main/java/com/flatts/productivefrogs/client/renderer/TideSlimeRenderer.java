package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.ResourceLocation;

/**
 * Vanilla {@link SlimeRenderer} with a Tide-Slime-specific texture and a
 * water-blue outer-shell tint. The constructor swaps vanilla's
 * {@link SlimeOuterLayer} for a {@link TintedSlimeOuterLayer} so the species
 * reads as blue in-world instead of the vanilla green that previously
 * dominated the translucent shell. The tint matches the vanilla water
 * particle colour so the parent species reads as an ocean-themed slime.
 */
public class TideSlimeRenderer extends SlimeRenderer {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/tide_slime.png");

    private static final int OUTER_TINT_ARGB = 0xFF3F76E4;

    public TideSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), OUTER_TINT_ARGB));
    }

    @Override
    public ResourceLocation getTextureLocation(SlimeRenderState state) {
        return TEXTURE;
    }
}
