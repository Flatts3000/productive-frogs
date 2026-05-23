package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Per-frame render state for {@link com.flatts.productivefrogs.content.entity.ResourceSlime}.
 * Threads category + variant data through the render pipeline so
 * {@link ResourceSlimeRenderer} and {@link ResourceSlimeOuterLayer} can pick
 * the right texture and outer-shell tint without re-querying the entity
 * mid-render.
 *
 * <p>Resolution order (consumed in {@link ResourceSlimeRenderer#getTextureLocation}):
 * <ol>
 *   <li>{@link #variantTexture} — set when the slime carries a
 *       {@link com.flatts.productivefrogs.data.SlimeVariant} that declares
 *       a {@code texture} field. Lets each variant render with its own
 *       resource-themed inner texture (iron block inside iron, copper
 *       inside copper, etc.) rather than sharing the broader category
 *       texture.</li>
 *   <li>{@link #category} — broad-category fallback texture
 *       ({@code <category>_resource_slime.png}). Used by every variant
 *       that doesn't ship a custom texture, plus category-only slimes
 *       (no variant).</li>
 * </ol>
 *
 * <p>{@link #outerTint} is the ARGB tint applied to the outer translucent
 * shell. When the slime carries a variant the tint is the variant's
 * primary colour (so the shell reads as variant-specific even before
 * per-variant inner textures ship); for category-only slimes it stays at
 * {@code -1} (no tint).
 *
 * <p>Pattern mirrors {@link com.flatts.productivefrogs.client.renderer.ResourceTadpoleRenderState}
 * and is the standard 1.21.x way to thread custom entity data through the
 * vanilla render-state pipeline: subclass the vanilla state, add fields,
 * populate them in {@code extractRenderState}.
 */
public class ResourceSlimeRenderState extends SlimeRenderState {
    @Nullable
    public Category category;

    @Nullable
    public ResourceLocation variantTexture;

    /** ARGB tint for the outer translucent shell. {@code -1} = no tint (white). */
    public int outerTint = -1;
}
