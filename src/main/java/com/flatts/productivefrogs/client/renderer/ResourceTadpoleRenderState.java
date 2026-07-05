package com.flatts.productivefrogs.client.renderer;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for {@link ResourceTadpoleRenderer}. Vanilla {@code TadpoleRenderer}
 * uses a bare {@link LivingEntityRenderState} (the tadpole texture is fixed), so
 * this only adds the pre-resolved category tint the {@link CategoryTintLayer}
 * overlays on the body, populated once in
 * {@link ResourceTadpoleRenderer#extractRenderState}.
 */
public class ResourceTadpoleRenderState extends LivingEntityRenderState {

    /** Pre-resolved ARGB body tint (category tint). */
    public int tint = -1;
}
