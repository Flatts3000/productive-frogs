package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;

/**
 * Per-frame snapshot data for a Resource Tadpole. Extends the vanilla
 * {@link LivingEntityRenderState} (which is what {@code TadpoleRenderer} uses
 * — it doesn't define its own state subclass) and adds the category field
 * needed for tint rendering.
 */
public class ResourceTadpoleRenderState extends LivingEntityRenderState {
    @Nullable
    public Category category;
}
