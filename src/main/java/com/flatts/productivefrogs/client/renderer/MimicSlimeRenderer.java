package com.flatts.productivefrogs.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;

/**
 * Renderer for the Mimic Slime (#253). Keeps the vanilla inner slime model and
 * texture as a neutral body; swaps the outer shell for a {@link MimicSlimeOuterLayer}
 * tinted at runtime by the carried item's sprite-average colour, so a Mimic
 * Slime carrying redstone reads red, lapis reads blue, and so on with no
 * per-item artwork.
 *
 * <p>Placeholder body texture is the vanilla slime sheet until the lane's art
 * pass adds a neutral greyscale mimic body (the shell tint already carries the
 * per-item signal).
 */
public class MimicSlimeRenderer extends SlimeRenderer {

    static final ResourceLocation BODY_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");

    public MimicSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new MimicSlimeOuterLayer(this, ctx.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(Slime entity) {
        return BODY_TEXTURE;
    }
}
