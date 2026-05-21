package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;
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
 * <p>The constructor replaces vanilla's {@link SlimeOuterLayer} with our
 * {@link ResourceSlimeOuterLayer}: the vanilla layer hardcodes
 * {@code SlimeRenderer.SLIME_LOCATION} for the translucent shell, so without
 * this swap the outer cube would still render the vanilla green slime
 * texture even though our inner cube reads from the per-category file. The
 * replacement layer routes through {@link #getTextureLocation} so both cubes
 * read from the same per-category file.
 *
 * <p>No {@link #getModelTint} override: the texture itself carries the
 * category color, so a tint multiply would only mute the block textures.
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
        if (state instanceof ResourceSlimeRenderState rState && rState.category != null) {
            return TEXTURES.get(rState.category);
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
            rState.category = resource.getCategory();
        }
    }
}
