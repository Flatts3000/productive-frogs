package com.flatts.productivefrogs.client.renderer;

import net.minecraft.client.renderer.entity.state.SlimeRenderState;

/**
 * Render state for {@link MimicSlimeRenderer}. Carries the pre-resolved outer-shell
 * tint (the carried item's sprite-average colour) so the render thread never reads
 * the live {@link com.flatts.productivefrogs.content.entity.MimicSlime}; populated
 * once in {@link MimicSlimeRenderer#extractRenderState}. The runtime counterpart to
 * {@link ResourceSlimeRenderState}'s variant-primary-colour shell, for an item that
 * has no registered variant.
 */
public class MimicSlimeRenderState extends SlimeRenderState {

    /** Pre-resolved ARGB outer-shell tint (carried item sprite-average colour). */
    public int shellTint = -1;
}
