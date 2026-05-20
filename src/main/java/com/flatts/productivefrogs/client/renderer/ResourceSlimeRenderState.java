package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Per-frame render state for {@link com.flatts.productivefrogs.content.entity.ResourceSlime}.
 * Adds the category field that drives the per-frame tint in
 * {@link ResourceSlimeRenderer#getModelTint}.
 *
 * <p>Pattern mirrors {@link com.flatts.productivefrogs.client.renderer.ResourceTadpoleRenderState}
 * and is the standard 1.21.x way to thread custom entity data through the
 * vanilla render-state pipeline: subclass the vanilla state, add fields,
 * populate them in {@code extractRenderState}.
 */
public class ResourceSlimeRenderState extends SlimeRenderState {
    @Nullable
    public Category category;
}
