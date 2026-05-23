package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;

/**
 * Vanilla {@link SlimeRenderer} with a Cave-Slime-specific texture and a
 * stone-grey outer-shell tint. The constructor swaps vanilla's
 * {@link SlimeOuterLayer} (which hardcodes {@code SlimeRenderer.SLIME_LOCATION}
 * and produces the vanilla green translucent shell) for a
 * {@link TintedSlimeOuterLayer} that routes texture lookups through this
 * renderer and applies a stone-grey ARGB tint, so the species reads as grey
 * in-world instead of the vanilla green that previously dominated.
 */
public class CaveSlimeRenderer extends SlimeRenderer {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/cave_slime.png");

    private static final int OUTER_TINT_ARGB = 0xFF8A8A8A;

    public CaveSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), OUTER_TINT_ARGB));
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        return TEXTURE;
    }
}
