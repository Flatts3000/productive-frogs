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
 * Resource Slime renderer. Extends vanilla {@link SlimeRenderer} to reuse the
 * vanilla model + shadow, but swaps the texture per-category (or per-variant)
 * to give each slime the "block-inside-a-translucent-shell" look.
 *
 * <p>The constructor replaces vanilla's {@link SlimeOuterLayer} with our
 * {@link ResourceSlimeOuterLayer} so the outer translucent cube reads from
 * our per-category PNG and gets a per-variant tint applied. See the layer
 * class for the tint resolution details.
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
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new ResourceSlimeOuterLayer(this, ctx.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(Slime entity) {
        if (entity instanceof ResourceSlime resource) {
            var variant = resource.getVariant();
            if (variant != null && variant.texture().isPresent()) {
                return variant.texture().get();
            }
            Category cat = resource.getCategory();
            if (cat != null) {
                return TEXTURES.get(cat);
            }
        }
        return TEXTURES.get(Category.BOG);
    }
}
