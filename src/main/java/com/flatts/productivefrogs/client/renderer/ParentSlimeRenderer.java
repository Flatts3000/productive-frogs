package com.flatts.productivefrogs.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;

/**
 * Shared renderer for the six PF parent species (Bog/Cave/Geode/Tide/Infernal/
 * Void Slime). Keeps the vanilla inner model (cube + eyes + mouth) textured
 * from the species atlas, swaps the outer shell for a per-species tinted
 * {@link TintedSlimeOuterLayer}, and adds a {@link ResourceSlimeInnerBlockLayer}
 * that renders the species' data-driven inner block (read from the
 * {@code parent_species} registry's {@code inner_block} field via
 * {@link ResourceSlimeInnerBlockLayer#parentSpeciesBlock}).
 *
 * <p>One parameterized class replaces six near-identical per-species renderers;
 * each species is registered in {@code PFClientEvents} with its atlas texture
 * and outer-shell tint.
 */
public class ParentSlimeRenderer extends SlimeRenderer {

    private final ResourceLocation texture;

    public ParentSlimeRenderer(EntityRendererProvider.Context ctx, ResourceLocation texture, int outerTintArgb) {
        super(ctx);
        this.texture = texture;
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), outerTintArgb, texture));
        this.addLayer(new ResourceSlimeInnerBlockLayer(this, ctx.getBlockRenderDispatcher(),
            ResourceSlimeInnerBlockLayer::parentSpeciesBlock));
    }

    @Override
    public ResourceLocation getTextureLocation(Slime entity) {
        return texture;
    }
}
