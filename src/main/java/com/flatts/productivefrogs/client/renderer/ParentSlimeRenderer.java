package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;

/**
 * Shared renderer for the six PF parent species (Bog/Cave/Geode/Tide/Infernal/
 * Void Slime). Keeps the vanilla inner model textured from the species atlas and
 * swaps the outer shell for a per-species tinted {@link TintedSlimeOuterLayer}.
 *
 * <p>One parameterized class replaces six near-identical per-species renderers;
 * each species is registered in {@code PFClientEvents} with its atlas texture
 * and outer-shell tint.
 *
 * <p>26.1 note: the species texture and tint are constants pinned at construction
 * (not per-entity), so no custom render state is needed - {@code getTextureLocation}
 * reads the render state's type only to satisfy the new signature and returns the
 * fixed atlas.
 */
public class ParentSlimeRenderer extends SlimeRenderer {

    private final Identifier texture;

    public ParentSlimeRenderer(EntityRendererProvider.Context ctx, Identifier texture, int outerTintArgb) {
        super(ctx);
        this.texture = texture;
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new TintedSlimeOuterLayer(this, ctx.getModelSet(), outerTintArgb, texture));
        PFDebug.log(PFDebug.Area.RENDER,
            () -> String.format("ParentSlimeRenderer texture=%s outerTint=#%08X", texture, outerTintArgb));
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        return texture;
    }
}
