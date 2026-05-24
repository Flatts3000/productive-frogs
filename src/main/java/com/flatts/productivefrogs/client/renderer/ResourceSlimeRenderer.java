package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.client.PFModelLayers;
import com.flatts.productivefrogs.client.model.ResourceSlimeInnerModel;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;

/**
 * Resource Slime renderer. Two-pass rendering (v1.0.1+):
 *
 * <ol>
 *   <li><b>Inner cube</b> (this renderer's base path): bound to the variant's
 *       {@code inner_texture} field from the datapack JSON, which is a vanilla
 *       block PNG (e.g. {@code minecraft:textures/block/iron_block.png}). The
 *       inner-cube model ({@link ResourceSlimeInnerModel}) maps each face's
 *       UVs to the full bound texture so the resource block displays at
 *       native 16x16 resolution.</li>
 *   <li><b>Outer shell + eyes + mouth</b> ({@link ResourceSlimeOuterLayer}):
 *       bound to the per-category atlas
 *       ({@code productivefrogs:textures/entity/slime/<category>_resource_slime.png}),
 *       which carries the vanilla outer-cube UV layout plus the eyes/mouth
 *       regions. Per-variant tint applied on top.</li>
 * </ol>
 *
 * <p>Pre-v1.0.1 the inner cube downsampled the vanilla block texture from
 * 16x16 to 6x6 (vanilla SlimeModel's per-face UV resolution) and stamped it
 * into a per-variant atlas, which visibly blurred at large slime sizes. The
 * v1.0.1 refactor binds the vanilla block PNG directly via two-pass
 * rendering; see {@code docs/v1_0_1_scope.md}.
 *
 * <p>When a variant ships without an {@code inner_texture} field (typo,
 * modded block from an absent mod), {@link #getTextureLocation(Slime)} falls
 * back to {@link MissingTextureAtlasSprite#getLocation()} so the failure
 * surfaces as the vanilla purple/black checker rather than crashing or
 * silently rendering nothing.
 */
public class ResourceSlimeRenderer extends SlimeRenderer {

    /**
     * Per-category outer-shell atlas paths. Used by
     * {@link ResourceSlimeOuterLayer} to bind the outer-cube + eyes/mouth
     * texture independently from this renderer's inner-cube binding.
     */
    public static final Map<Category, ResourceLocation> OUTER_TEXTURES = buildOuterTextureMap();

    private static Map<Category, ResourceLocation> buildOuterTextureMap() {
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
        // Swap the inherited vanilla SlimeModel (6x6 UVs) for our 16x16-UV
        // inner cube. The cube's geometry is unchanged in world-space; only
        // the UV mapping per face changes from 6x6 to a full 16x16 of the
        // bound texture.
        this.model = new ResourceSlimeInnerModel(ctx.bakeLayer(PFModelLayers.RESOURCE_SLIME_INNER));
        // Outer shell + eyes/mouth come from ResourceSlimeOuterLayer.
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new ResourceSlimeOuterLayer(this, ctx.getModelSet()));
    }

    /**
     * Returns the variant's {@code inner_texture} (vanilla block PNG). When
     * the field is absent on the variant JSON, returns the vanilla
     * missing-texture sprite location so the failure renders as a loud
     * purple/black checker.
     */
    @Override
    public ResourceLocation getTextureLocation(Slime entity) {
        if (entity instanceof ResourceSlime resource) {
            var variant = resource.getVariant();
            if (variant != null && variant.innerTexture().isPresent()) {
                return variant.innerTexture().get();
            }
        }
        return MissingTextureAtlasSprite.getLocation();
    }
}
