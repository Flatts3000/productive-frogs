package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.client.renderer.entity.state.FrogRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Per-frame snapshot data for a Resource Frog. Extends the vanilla
 * {@link FrogRenderState} (which already carries swimming + animation states +
 * texture path) and adds the category field needed for tint rendering.
 */
public class ResourceFrogRenderState extends FrogRenderState {
    @Nullable
    public Category category;
}
