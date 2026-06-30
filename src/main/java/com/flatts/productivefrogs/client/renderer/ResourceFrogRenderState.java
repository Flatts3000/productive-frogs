package com.flatts.productivefrogs.client.renderer;

import net.minecraft.client.renderer.entity.state.FrogRenderState;

/**
 * Render state for {@link ResourceFrogRenderer}. 26.1's deferred render pipeline
 * extracts per-frame entity data into a state object up front (so the render
 * thread never touches the live entity), then renders from the state. This
 * carries the pre-resolved category tint the {@link CategoryTintLayer} overlays
 * on the body, populated once in {@link ResourceFrogRenderer#extractRenderState}.
 * The biome-variant texture is already carried by {@link FrogRenderState#texture}.
 */
public class ResourceFrogRenderState extends FrogRenderState {

    /** Pre-resolved ARGB body tint (Midas gold, else category tint). */
    public int tint = -1;
}
