package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.monster.Slime;

/**
 * Resource Slime renderer. Extends vanilla {@link SlimeRenderer} to reuse the
 * vanilla model + shadow, but swaps the texture per-category to give each
 * slime the "block-inside-a-translucent-shell" look.
 *
 * <p>Vanilla slime.png is a single 64×32 PNG, but the SlimeModel cubes sample
 * two distinct UV regions of it (see {@code net.minecraft.client.model.monster.slime.SlimeModel}):
 *
 * <ul>
 *   <li><b>Outer cube</b> ({@code texOffs(0, 0)}, 8×8×8) → samples pixels
 *       in the top-left {@code (0..32, 0..16)} region. Rendered with
 *       {@code entityTranslucent} by the outer layer, so whatever color we
 *       put there shows up as a translucent shell.</li>
 *   <li><b>Inner cube</b> ({@code texOffs(0, 16)}, 6×6×6) → samples pixels
 *       in the lower-left {@code (0..24, 16..28)} region. Rendered solid.</li>
 *   <li>Eyes/mouth in the top-right corner.</li>
 * </ul>
 *
 * <p>Per-category textures fill the inner region with the category's
 * representative vanilla block (iron_block for METALLIC, redstone_block for
 * MINERAL, etc.) and the outer region with solid mid-gray. One file feeds
 * both cubes via the same UV-segmented layout vanilla uses.
 *
 * <p>Texture resolution (see {@link #getTextureLocation}):
 * <ol>
 *   <li>If the slime's variant declares a {@code texture} field, that
 *       per-variant PNG is used. Lets all three METALLIC variants
 *       (iron / copper / gold) render distinctly with their resource block
 *       inside the translucent shell instead of sharing the broader
 *       category PNG. Per-variant PNGs are an asset follow-up — see
 *       {@code docs/known_issues.md}.</li>
 *   <li>Otherwise the broad-category PNG is used. Every shipped variant
 *       currently lands here; the per-variant slot is the schema
 *       extension that lets the asset PR drop in cleanly.</li>
 * </ol>
 *
 * <p>The constructor replaces vanilla's {@link SlimeOuterLayer} with our
 * {@link ResourceSlimeOuterLayer}: the vanilla layer hardcodes
 * {@code SlimeRenderer.SLIME_LOCATION} for the translucent shell, so without
 * this swap the outer cube would still render the vanilla green slime
 * texture even though our inner cube reads from the per-category file. The
 * replacement layer routes through {@link #getTextureLocation} so both cubes
 * read from the same file, and additionally applies a per-variant tint
 * (from {@code SlimeVariant.primaryColor}) to the translucent shell so
 * variants are visually distinguishable even before per-variant PNGs ship.
 *
 * <p>No {@link #getModelTint} override on the inner cube: the texture
 * itself carries the category/variant color, so a tint multiply would only
 * mute the block textures.
 */
public class ResourceSlimeRenderer extends SlimeRenderer {

    private static final Map<Category, Identifier> TEXTURES = buildTextureMap();

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
        // Vanilla SlimeRenderer's constructor adds a SlimeOuterLayer that hardcodes
        // SlimeRenderer.SLIME_LOCATION for the translucent shell — meaning the outer
        // cube would still render with the vanilla green slime texture even after
        // our per-category swap on the inner cube. Replace it with the layer that
        // routes texture lookups through this renderer's getTextureLocation so the
        // shell reads the gray UV region of our per-category file.
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new ResourceSlimeOuterLayer(this, ctx.getModelSet()));
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        if (state instanceof ResourceSlimeRenderState rState) {
            if (rState.variantTexture != null) {
                return rState.variantTexture;
            }
            if (rState.category != null) {
                return TEXTURES.get(rState.category);
            }
        }
        return TEXTURES.get(Category.METALLIC);
    }

    @Override
    public SlimeRenderState createRenderState() {
        return new ResourceSlimeRenderState();
    }

    @Override
    public void extractRenderState(Slime entity, SlimeRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (entity instanceof ResourceSlime resource && state instanceof ResourceSlimeRenderState rState) {
            Category category = resource.getCategory();
            rState.category = category;

            // Tint resolution:
            //   variant present  -> full-saturation variant primary colour
            //                       (iron-silver, copper-orange, gold-yellow,
            //                       etc. — distinguishes the 12 variants).
            //   variant absent   -> per-category subtly-tinted gray
            //                       (cooler for AQUATIC, warmer for INFERNAL,
            //                       etc.) so the six category-only slimes
            //                       still read distinctly from one another
            //                       without going full-saturation. See
            //                       Category.shellTintArgb javadoc.
            //
            // Variant lookup is null-tolerant: unknown-variant-id slimes
            // (datapack removed since save, modded variant whose mod isn't
            // loaded) hit the category branch and get the shell tint.
            //
            // The defensive `category != null` fallback to -1 is
            // belt-and-suspenders: ResourceSlime.getCategory() defaults to
            // METALLIC for out-of-range ordinals, so it never actually
            // returns null. Leaving the check in costs nothing and keeps
            // the renderer robust against future Slime subclasses that
            // might not share that defensiveness.
            SlimeVariant variant = resource.getVariant();
            if (variant != null) {
                rState.variantTexture = variant.texture().orElse(null);
                rState.outerTint = ARGB.opaque(variant.primaryColor());
            } else {
                rState.variantTexture = null;
                rState.outerTint = category != null ? category.shellTintArgb() : -1;
            }
        }
    }
}
