package com.flatts.productivefrogs.client.model;

import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.monster.Slime;

/**
 * Drop-in replacement for vanilla {@link SlimeModel} for the inner cube of a
 * Resource Slime / parent-species slime. The single change from vanilla is the
 * cube geometry + UV mapping: instead of a 6x6x6 box that auto-maps each face
 * to a 6x6 UV region (the vanilla shape, which on a 64x32 atlas forces us to
 * downsample 16x16 vanilla block textures to 6x6 to render them inside the
 * slime), this model uses a 16x16x16 box on a 16x16 texture so each face
 * spans the full bound texture. At render time the model is scaled down by
 * {@link #INNER_SCALE} (= 6/16) so it occupies the same world-space volume as
 * vanilla's inner cube; the only difference is that the bound texture is now
 * a vanilla block PNG (e.g. {@code minecraft:textures/block/iron_block.png})
 * displayed at native 16x16 per face.
 *
 * <p>The texture binding itself is supplied by the renderer's
 * {@code getTextureLocation(entity)} override (e.g.
 * {@link com.flatts.productivefrogs.client.renderer.ResourceSlimeRenderer}
 * returns the variant's {@code inner_texture} field). This model is
 * texture-agnostic; it just provides the geometry + UV layout.
 *
 * <p>Vanilla's setupAnim writes the squish factor into the cube's
 * {@code xScale/yScale/zScale}. We override setupAnim to multiply those
 * post-squish scales by {@link #INNER_SCALE} so the squish animation still
 * plays at the correct visual amplitude after the 0.375 downscaling.
 *
 * <p>Full design context: {@code docs/v1_0_1_scope.md}.
 */
public class ResourceSlimeInnerModel extends SlimeModel<Slime> {

    /** 6 / 16 = 0.375. Renders our 16x16x16 model box at vanilla's 6x6x6 visual size. */
    private static final float INNER_SCALE = 6f / 16f;

    private final ModelPart cube;

    public ResourceSlimeInnerModel(ModelPart root) {
        super(root);
        this.cube = root.getChild("cube");
    }

    @Override
    public void setupAnim(Slime entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        // Vanilla setupAnim wrote squish-driven scale (typically 1.0 +/- squish).
        // Multiply rather than overwrite so the squish animation still plays.
        cube.xScale *= INNER_SCALE;
        cube.yScale *= INNER_SCALE;
        cube.zScale *= INNER_SCALE;
    }

    /**
     * Builds the 16x16x16 inner cube on a 16x16 texture canvas. PartPose at
     * (0, 20, 0) places the cube center where vanilla's 6x6x6 inner cube
     * center sits ({@code (-3, 17, -3)..(3, 23, 3)} -> center at
     * {@code (0, 20, 0)}), so the {@link #INNER_SCALE} downscaling lands the
     * rendered cube on vanilla's exact position.
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("cube",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-8f, -8f, -8f, 16f, 16f, 16f),
            PartPose.offset(0f, 20f, 0f));
        return LayerDefinition.create(mesh, 16, 16);
    }
}
