package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Cave Slime renderer. Keeps the vanilla inner model (cube + eyes + mouth)
 * textured from the species atlas, swaps the outer shell for a stone-grey
 * {@link TintedSlimeOuterLayer}, and adds a {@link ResourceSlimeInnerBlockLayer}
 * that renders {@link #INNER_BLOCK} (vanilla stone) inside the slime.
 *
 * <p>{@link #INNER_BLOCK} mirrors the {@code inner_block} field on
 * {@code data/productivefrogs/productivefrogs/parent_species/cave_slime.json}.
 */
public class CaveSlimeRenderer extends SlimeRenderer {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/cave_slime.png");

    private static final ResourceLocation INNER_BLOCK = ResourceLocation.parse("minecraft:stone");

    private static final int OUTER_TINT_ARGB = 0xFF8A8A8A;

    public CaveSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), OUTER_TINT_ARGB, TEXTURE));
        this.addLayer(new ResourceSlimeInnerBlockLayer(this, ctx.getBlockRenderDispatcher(),
            ResourceSlimeInnerBlockLayer.constant(INNER_BLOCK)));
    }

    @Override
    public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Slime entity) {
        return TEXTURE;
    }
}
