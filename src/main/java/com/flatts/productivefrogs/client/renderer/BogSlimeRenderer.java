package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Bog Slime renderer — vanilla {@link SlimeRenderer} with a swamp-green
 * texture + outer-shell tint. Mirrors the {@link CaveSlimeRenderer} pattern:
 * swap the vanilla {@link SlimeOuterLayer} for a {@link TintedSlimeOuterLayer}
 * so the outer translucent shell reads green instead of the vanilla default.
 */
public class BogSlimeRenderer extends SlimeRenderer {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/bog_slime.png");

    private static final int OUTER_TINT_ARGB = 0xFF6A8540; // swamp-leaf green

    public BogSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), OUTER_TINT_ARGB));
    }

    @Override
    public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Slime entity) {
        return TEXTURE;
    }
}
