package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.client.PFModelLayers;
import com.flatts.productivefrogs.client.model.ResourceSlimeInnerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Bog Slime renderer. Two-pass: outer shell + eyes + mouth use the species
 * atlas ({@link #OUTER_TEXTURE}) with a swamp-leaf-green tint via
 * {@link TintedSlimeOuterLayer}; inner cube uses {@link #INNER_TEXTURE}
 * (vanilla moss block) at native 16x16 resolution via
 * {@link ResourceSlimeInnerModel}.
 *
 * <p>{@link #INNER_TEXTURE} stays in sync with the {@code inner_texture}
 * field on {@code data/productivefrogs/productivefrogs/parent_species/bog_slime.json}.
 * Both are part of the source of truth; modpacks overriding the JSON won't
 * change the in-world render (the renderer's hardcoded constant wins). If a
 * future PR routes the renderer through the datapack registry at render
 * time, drop the constant.
 */
public class BogSlimeRenderer extends SlimeRenderer {

    private static final ResourceLocation OUTER_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/bog_slime.png");

    private static final ResourceLocation INNER_TEXTURE =
        ResourceLocation.parse("minecraft:textures/block/moss_block.png");

    private static final int OUTER_TINT_ARGB = 0xFF6A8540; // swamp-leaf green

    public BogSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new ResourceSlimeInnerModel(ctx.bakeLayer(PFModelLayers.RESOURCE_SLIME_INNER));
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), OUTER_TINT_ARGB, OUTER_TEXTURE));
    }

    @Override
    public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Slime entity) {
        return INNER_TEXTURE;
    }
}
