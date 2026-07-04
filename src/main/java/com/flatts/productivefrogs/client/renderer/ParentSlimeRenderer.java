package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;

/**
 * Shared renderer for the six PF parent species (Bog/Cave/Geode/Tide/Infernal/
 * Void Slime). Keeps the vanilla inner model textured from the species atlas and
 * swaps the outer shell for a per-species tinted {@link TintedSlimeOuterLayer}.
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
        PFDebug.log(PFDebug.Area.RENDER,
            () -> String.format("ParentSlimeRenderer texture=%s outerTint=#%08X", texture, outerTintArgb));
    }

    @Override
    public ResourceLocation getTextureLocation(Slime entity) {
        return texture;
    }
}
