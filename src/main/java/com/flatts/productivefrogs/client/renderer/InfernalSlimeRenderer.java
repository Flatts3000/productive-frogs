package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Infernal Slime renderer — vanilla {@link SlimeRenderer} with a nether-red
 * texture + outer-shell tint. Mirrors the {@link CaveSlimeRenderer} pattern.
 */
public class InfernalSlimeRenderer extends SlimeRenderer {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/infernal_slime.png");

    private static final int OUTER_TINT_ARGB = 0xFFC73E1D; // lava red

    public InfernalSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), OUTER_TINT_ARGB));
    }

    @Override
    public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Slime entity) {
        return TEXTURE;
    }
}
