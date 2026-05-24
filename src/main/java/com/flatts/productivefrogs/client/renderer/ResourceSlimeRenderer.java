package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;

/**
 * Resource Slime renderer. Three render passes:
 *
 * <ol>
 *   <li><b>Inner cube + eyes + mouth</b> (inherited vanilla {@link SlimeRenderer}
 *       base): drawn from the per-category atlas
 *       ({@code <category>_resource_slime.png}). The eyes/mouth live on the
 *       vanilla inner body layer, so keeping the vanilla model preserves the
 *       slime's face. The inner cube's body texture is covered by the block
 *       pass below; the eyes (z=-3.5, proud of the cube face) stay visible.</li>
 *   <li><b>Inner block</b> ({@link ResourceSlimeInnerBlockLayer}): renders the
 *       variant's {@code inner_block} as an actual vanilla block model in the
 *       inner-cube volume. This is the v1.0.1 "literal block inside the slime"
 *       upgrade over v1.0's downsampled inner-cube texture.</li>
 *   <li><b>Outer shell</b> ({@link ResourceSlimeOuterLayer}): translucent
 *       per-variant-tinted cube. Unchanged from v1.0.</li>
 * </ol>
 *
 * <p>The three items above are listed inner-to-outer for clarity, not in
 * layer-add order. The constructor adds the outer-shell layer before the
 * inner-block layer, but that doesn't affect the result: with the
 * {@code MultiBufferSource.immediate} batching used for entities, draws are
 * grouped and flushed by {@code RenderType}, so the opaque block (solid /
 * cutout) is drawn before the translucent shell regardless of layer order.
 *
 * <p>See {@code docs/v1_0_1_scope.md}. The shipped implementation keeps the
 * vanilla inner model and adds the block layer (rather than the spec's
 * custom-model two-pass), which preserves the eyes/mouth and sidesteps the
 * single-tile-vs-UV-net problem.
 */
public class ResourceSlimeRenderer extends SlimeRenderer {

    private static final Map<Category, ResourceLocation> TEXTURES = buildTextureMap();

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
        // Keep the vanilla inner model (cube + eyes + mouth). Swap only the
        // outer shell for our tinted variant, then add the inner-block pass.
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new ResourceSlimeOuterLayer(this, ctx.getModelSet()));
        this.addLayer(new ResourceSlimeInnerBlockLayer(
            this, ctx.getBlockRenderDispatcher(),
            ResourceSlimeInnerBlockLayer::resourceSlimeBlock));
    }

    @Override
    public ResourceLocation getTextureLocation(Slime entity) {
        if (entity instanceof ResourceSlime resource) {
            Category cat = resource.getCategory();
            if (cat != null) {
                return TEXTURES.get(cat);
            }
        }
        return TEXTURES.get(Category.BOG);
    }
}
