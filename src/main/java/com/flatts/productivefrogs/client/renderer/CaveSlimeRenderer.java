package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;

/**
 * Vanilla {@link SlimeRenderer} with a Cave-Slime-specific texture. No model
 * tint (the texture itself is themed) and no outer-layer override — the
 * {@code SlimeOuterLayer} from the parent already reads our texture via
 * {@code SlimeRenderer.SLIME_LOCATION} as a default, BUT we use our own
 * {@code getTextureLocation} override on this renderer, so the inner cube
 * picks up the cave-themed texture. The outer translucent shell still uses
 * vanilla green from the hard-coded slime.png reference — same known
 * limitation we accept for the parent species since these slimes only exist
 * briefly (they split into ResourceSlimes on death via the discovery hook).
 */
public class CaveSlimeRenderer extends SlimeRenderer {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/cave_slime.png");

    public CaveSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        return TEXTURE;
    }
}
