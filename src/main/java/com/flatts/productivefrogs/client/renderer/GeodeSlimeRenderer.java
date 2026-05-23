package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;

/**
 * Vanilla {@link SlimeRenderer} with a Geode-Slime-specific texture and a
 * diamond-cyan outer-shell tint. The constructor swaps vanilla's
 * {@link SlimeOuterLayer} for a {@link TintedSlimeOuterLayer} so the species
 * reads as cyan in-world instead of the vanilla green that previously
 * dominated the translucent shell. The tint matches the {@code diamond}
 * variant's {@code primary_color} so the parent species reads consistently
 * with the resource variants it spawns.
 */
public class GeodeSlimeRenderer extends SlimeRenderer {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/geode_slime.png");

    private static final int OUTER_TINT_ARGB = 0xFF6CDCD7;

    public GeodeSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), OUTER_TINT_ARGB));
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        return TEXTURE;
    }
}
