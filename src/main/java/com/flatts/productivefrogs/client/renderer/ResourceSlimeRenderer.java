package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;

/**
 * Resource Slime renderer. Two render passes:
 *
 * <ol>
 *   <li><b>Inner cube</b> (inherited vanilla {@link SlimeRenderer} base): drawn
 *       from a per-variant texture ({@code <variant>_resource_slime.png}) whose
 *       inner-cube faces carry a downscaled copy of the variant's vanilla
 *       resource block (e.g. the iron-block face). Variant-less slimes fall back
 *       to the per-category texture ({@code <category>_resource_slime.png}).</li>
 *   <li><b>Outer shell</b> ({@link ResourceSlimeOuterLayer}): translucent cube
 *       tinted by the variant's {@code primary_color} (category fallback).</li>
 * </ol>
 *
 * <p>The interior resource is baked into the texture rather than drawn as a live
 * block model: an opaque block rendered in a separate pass is depth-culled by
 * the slime's translucent shell and never appears (the failure that made every
 * variant show its category's colour). The inner-cube texture renders as part of
 * the translucent entity, so it is reliably visible through the shell. The
 * per-variant textures are produced by {@code scripts/generate_resource_slime_textures.py}.
 */
public class ResourceSlimeRenderer extends SlimeRenderer {

    private static final Map<Category, ResourceLocation> TEXTURES = buildTextureMap();

    // Per-variant texture paths, resolved lazily and cached. Render-thread only,
    // so a plain HashMap is fine.
    private static final Map<String, ResourceLocation> VARIANT_TEXTURES = new HashMap<>();

    private static Map<Category, ResourceLocation> buildTextureMap() {
        EnumMap<Category, ResourceLocation> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, ResourceLocation.fromNamespaceAndPath(
                ProductiveFrogs.MOD_ID,
                "textures/entity/slime/" + cat.id() + "_resource_slime.png"
            ));
        }
        return map;
    }

    public ResourceSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        // Keep the vanilla inner model (the inner cube carries the downscaled
        // resource block via the per-variant texture). Swap only the outer shell
        // for our per-variant-tinted translucent cube.
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new ResourceSlimeOuterLayer(this, ctx.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(Slime entity) {
        ResourceLocation texture = resolveTexture(entity);
        if (PFDebug.on(PFDebug.Area.RENDER) && entity instanceof ResourceSlime resource) {
            ResourceLocation variantId = resource.getVariantId();
            Category cat = resource.getCategory();
            boolean fallback = variantId == null;
            PFDebug.logOnce(PFDebug.Area.RENDER, "slime#" + entity.getId() + "/" + variantId,
                () -> String.format(
                    "ResourceSlime id=%d variant=%s category=%s fallback=%s -> %s",
                    entity.getId(), variantId, cat, fallback, texture));
        }
        return texture;
    }

    private ResourceLocation resolveTexture(Slime entity) {
        if (entity instanceof ResourceSlime resource) {
            ResourceLocation variantId = resource.getVariantId();
            Category cat = resource.getCategory();
            if (variantId != null) {
                ResourceLocation variantTex = VARIANT_TEXTURES.computeIfAbsent(variantId.getPath(), path ->
                    ResourceLocation.fromNamespaceAndPath(
                        ProductiveFrogs.MOD_ID,
                        "textures/entity/slime/" + path + "_resource_slime.png"));
                // A built-in variant ships this texture; a datapack-added variant
                // typically does not. Fall back to the category cube when the
                // per-variant texture is absent (the shell still tints by
                // primary_color), so a config-only variant never shows the
                // missing-texture checkerboard.
                if (textureExists(variantTex)) {
                    return variantTex;
                }
            }
            if (cat != null) {
                return TEXTURES.get(cat);
            }
        }
        return TEXTURES.get(Category.BOG);
    }

    // Cached resource-existence check (one lookup per variant texture). Render
    // thread only, so a plain HashMap is fine.
    private static final Map<ResourceLocation, Boolean> TEXTURE_EXISTS = new HashMap<>();

    private static boolean textureExists(ResourceLocation texture) {
        return TEXTURE_EXISTS.computeIfAbsent(texture,
            t -> Minecraft.getInstance().getResourceManager().getResource(t).isPresent());
    }

    /**
     * Drop the cached texture-resolution maps. Called on resource reload (see
     * {@code PFClientEvents}) so a pack that adds or removes a
     * {@code <variant>_resource_slime.png} between reloads is picked up instead
     * of serving a stale existence result from {@link #TEXTURE_EXISTS}.
     */
    public static void clearTextureCaches() {
        VARIANT_TEXTURES.clear();
        TEXTURE_EXISTS.clear();
    }
}
