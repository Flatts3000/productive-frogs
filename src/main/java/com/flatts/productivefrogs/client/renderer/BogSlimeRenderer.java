package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Bog Slime renderer. Keeps the vanilla inner model (cube + eyes + mouth)
 * textured from the species atlas, swaps the outer shell for a swamp-leaf-green
 * {@link TintedSlimeOuterLayer}, and adds a {@link ResourceSlimeInnerBlockLayer}
 * that renders the species' inner block inside the slime.
 *
 * <p>The inner block is data-driven, parallel to how Resource Slime variants
 * read theirs: {@link ResourceSlimeInnerBlockLayer#parentSpeciesBlock} reads
 * the {@code inner_block} field from this species' {@code parent_species}
 * registry entry ({@code .../parent_species/bog_slime.json} -> moss_block), so
 * a modpack can repoint it by editing the JSON.
 */
public class BogSlimeRenderer extends SlimeRenderer {

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/bog_slime.png");

    private static final int OUTER_TINT_ARGB = 0xFF6A8540; // swamp-leaf green

    public BogSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), OUTER_TINT_ARGB, TEXTURE));
        this.addLayer(new ResourceSlimeInnerBlockLayer(this, ctx.getBlockRenderDispatcher(),
            ResourceSlimeInnerBlockLayer::parentSpeciesBlock));
    }

    @Override
    public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Slime entity) {
        return TEXTURE;
    }
}
