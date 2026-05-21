package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;

/**
 * Vanilla {@link SlimeRenderer} with a Geode-Slime-specific (amethyst/purple)
 * texture. Same shape and caveats as {@link CaveSlimeRenderer} — only the
 * inner cube picks up the themed texture; the outer translucent shell still
 * uses the vanilla slime.png reference since these parent-species slimes are
 * short-lived (they convert to ResourceSlimes on split via the discovery
 * hook).
 */
public class GeodeSlimeRenderer extends SlimeRenderer {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/geode_slime.png");

    public GeodeSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        return TEXTURE;
    }
}
