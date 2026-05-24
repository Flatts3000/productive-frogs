package com.flatts.productivefrogs.client;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * Mod-owned {@link ModelLayerLocation}s. Registered against
 * {@link net.minecraft.client.model.geom.builders.LayerDefinition}s via
 * {@link net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions}
 * in {@link PFClientEvents}.
 *
 * <p>{@link #RESOURCE_SLIME_INNER} is the v1.0.1 native-resolution inner-cube
 * layer for both {@link com.flatts.productivefrogs.content.entity.ResourceSlime}
 * and the six parent species renderers. The vanilla {@link
 * net.minecraft.client.model.geom.ModelLayers#SLIME} layer's 6x6x6 box maps
 * each face to a 6x6 UV region on a 64x32 atlas. Our replacement is a 16x16x16
 * box at PartPose (0, 20, 0) on a 16x16 texture, with each face spanning the
 * full texture. The model class
 * ({@link com.flatts.productivefrogs.client.model.ResourceSlimeInnerModel}) then
 * scales the box down by 6/16 at setupAnim time so it renders at the same
 * visual size as vanilla's inner cube while still UV-mapping to native
 * 16x16-per-face. See {@code docs/v1_0_1_scope.md} for the full design.
 */
public final class PFModelLayers {

    public static final ModelLayerLocation RESOURCE_SLIME_INNER = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "resource_slime_inner"),
        "main"
    );

    private PFModelLayers() {
        // constants holder, not instantiable
    }
}
