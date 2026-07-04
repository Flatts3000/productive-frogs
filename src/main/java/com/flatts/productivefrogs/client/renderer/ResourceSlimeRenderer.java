package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.monster.Slime;
import org.jetbrains.annotations.Nullable;

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
 *
 * <p>26.1 note: identity (variant/category) and the shell tint are extracted into
 * a {@link ResourceSlimeRenderState} once per frame; the texture/tint lookups
 * read the state, not the live entity.
 */
public class ResourceSlimeRenderer extends SlimeRenderer {

    private static final Map<Category, Identifier> TEXTURES = buildTextureMap();

    // Per-variant texture paths, resolved lazily and cached. Render-thread only,
    // so a plain HashMap is fine.
    private static final Map<String, Identifier> VARIANT_TEXTURES = new HashMap<>();

    private static Map<Category, Identifier> buildTextureMap() {
        EnumMap<Category, Identifier> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, Identifier.fromNamespaceAndPath(
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
    public ResourceSlimeRenderState createRenderState() {
        return new ResourceSlimeRenderState();
    }

    @Override
    public void extractRenderState(Slime entity, SlimeRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (state instanceof ResourceSlimeRenderState rs && entity instanceof ResourceSlime resource) {
            rs.category = resource.getCategory();
            rs.variantId = resource.getVariantId();
            rs.shellTint = resolveShellTint(resource);
        }
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        ResourceSlimeRenderState rs = state instanceof ResourceSlimeRenderState r ? r : null;
        Identifier texture = resolveTexture(rs);
        if (PFDebug.on(PFDebug.Area.RENDER) && rs != null) {
            Identifier variantId = rs.variantId;
            Category cat = rs.category;
            boolean fallback = variantId == null;
            PFDebug.logOnce(PFDebug.Area.RENDER, "slime/" + variantId,
                () -> String.format(
                    "ResourceSlime variant=%s category=%s fallback=%s -> %s",
                    variantId, cat, fallback, texture));
        }
        return texture;
    }

    private Identifier resolveTexture(@Nullable ResourceSlimeRenderState rs) {
        if (rs != null) {
            Identifier variantId = rs.variantId;
            Category cat = rs.category;
            if (variantId != null) {
                Identifier variantTex = VARIANT_TEXTURES.computeIfAbsent(variantId.getPath(), path ->
                    Identifier.fromNamespaceAndPath(
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

    /**
     * Resolve the outer-shell ARGB tint for a Resource Slime: the variant's
     * {@code primary_color} (opaque), or the category shell-tint fallback.
     * Pre-computed at extract time (registry access via the live entity).
     */
    static int resolveShellTint(ResourceSlime resource) {
        SlimeVariant variant = resource.getVariant();
        if (variant != null) {
            return ARGB.color(255,
                (variant.primaryColor() >> 16) & 0xFF,
                (variant.primaryColor() >> 8) & 0xFF,
                variant.primaryColor() & 0xFF);
        }
        Category cat = resource.getCategory();
        return cat != null ? cat.shellTintArgb() : -1;
    }

    // Cached resource-existence check (one lookup per variant texture). Render
    // thread only, so a plain HashMap is fine.
    private static final Map<Identifier, Boolean> TEXTURE_EXISTS = new HashMap<>();

    private static boolean textureExists(Identifier texture) {
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
