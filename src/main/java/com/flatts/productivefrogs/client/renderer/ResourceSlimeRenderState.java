package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Render state for {@link ResourceSlimeRenderer}. 26.1's render pipeline extracts
 * per-frame entity data into a state object up front (so the render thread never
 * touches the live entity), then renders from the state. This carries the
 * variant/category identity the per-category texture lookup needs plus the
 * pre-resolved outer-shell tint, all populated once in
 * {@link ResourceSlimeRenderer#extractRenderState}.
 */
public class ResourceSlimeRenderState extends SlimeRenderState {

    @Nullable
    public Category category;

    @Nullable
    public Identifier variantId;

    /** Pre-resolved ARGB outer-shell tint (variant primary colour, category fallback). */
    public int shellTint = -1;
}
