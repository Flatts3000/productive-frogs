package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders an actual vanilla block inside a Resource Slime / parent-species
 * slime, in the volume vanilla's 6x6x6 inner cube occupies. This is the
 * v1.0.1 "interior mapped to the internal block" pass: instead of texturing
 * the inner cube with a downsampled block image (the v1.0 approach), we draw
 * the real block model via {@link BlockRenderDispatcher}, so the block reads
 * at native resolution with vanilla's own UVs, mipmaps, and (where relevant)
 * animation.
 *
 * <p>This layer is purely additive: the base {@link SlimeRenderer} still draws
 * the vanilla inner cube + eyes + mouth (the eyes/mouth live on the vanilla
 * inner body layer), and {@link ResourceSlimeOuterLayer} /
 * {@link TintedSlimeOuterLayer} still draw the translucent tinted shell. The
 * opaque block here covers the inner cube's body texture; the eyes sit at
 * z=-3.5 (proud of the inner cube's front face at z=-3) so they render in
 * front of the block and stay visible.
 *
 * <p>Block selection is supplied per-renderer via a {@link Function}: the
 * Resource Slime renderer reads each entity's variant ({@link
 * #resourceSlimeBlock}); each parent-species renderer passes a constant block
 * ({@link #constant}).
 *
 * <p><b>Transform constants are first-guesses pending an in-client tuning
 * pass</b> (the render can't be verified headless). See {@link #BLOCK_EDGE} /
 * {@link #CENTER_Y}. Modelled on vanilla {@code CarriedBlockLayer}: at
 * layer-render time the PoseStack is block-scaled and axis-flipped (entity
 * models render with X/Y inverted), so the block is un-flipped via a negative
 * scale and centered with a final half-unit translate.
 */
public class ResourceSlimeInnerBlockLayer extends RenderLayer<Slime, SlimeModel<Slime>> {

    // Inner cube (vanilla createInnerBodyLayer): pixels (-3,17,-3)..(3,23,3),
    // center (0,20,0). At layer time the pose is block-scaled (ModelParts
    // divide their pixel coords by 16 internally), so the cube center sits at
    // pose y = 20/16 = 1.25.
    //
    // BLOCK_EDGE is set slightly above the inner cube's 6/16 so the opaque
    // block fully covers the inner cube body (no z-fight / texture peek).
    // TUNE IN-CLIENT.
    private static final float BLOCK_EDGE = 7.0f / 16.0f; // ~0.4375 block
    private static final float CENTER_Y = 20.0f / 16.0f;  // 1.25 block, inner cube center

    private final BlockRenderDispatcher blockRenderer;
    private final Function<Slime, BlockState> blockResolver;

    public ResourceSlimeInnerBlockLayer(RenderLayerParent<Slime, SlimeModel<Slime>> parent,
                                        BlockRenderDispatcher blockRenderer,
                                        Function<Slime, BlockState> blockResolver) {
        super(parent);
        this.blockRenderer = blockRenderer;
        this.blockResolver = blockResolver;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, Slime entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        BlockState state = blockResolver.apply(entity);
        if (state == null) {
            return;
        }

        // The pose already carries the slime's size + squish scale (applied in
        // SlimeRenderer.scale before layers render), so the block squishes with
        // the body for free.
        pose.pushPose();
        pose.translate(0.0f, CENTER_Y, 0.0f);
        // Un-flip entity X/Y axes and size to the inner cube edge (mirrors
        // vanilla CarriedBlockLayer's negative-scale convention).
        pose.scale(-BLOCK_EDGE, -BLOCK_EDGE, BLOCK_EDGE);
        // Center the [0,1] block model on the pivot.
        pose.translate(-0.5f, -0.5f, -0.5f);
        blockRenderer.renderSingleBlock(state, pose, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /**
     * Resolve a {@link ResourceSlime}'s inner block from its variant's
     * {@code inner_block} field. Returns {@code null} when the entity isn't a
     * ResourceSlime, has no variant, the variant declares no inner_block, or
     * the id doesn't resolve to a registered block (a modded block from an
     * absent mod). The layer skips rendering on null.
     */
    public static BlockState resourceSlimeBlock(Slime entity) {
        if (!(entity instanceof ResourceSlime resource)) {
            return null;
        }
        SlimeVariant variant = resource.getVariant();
        if (variant == null || variant.innerBlock().isEmpty()) {
            return null;
        }
        return blockStateOrNull(variant.innerBlock().get());
    }

    /**
     * Build a constant-block resolver for a parent-species renderer. Resolves
     * the id once at construction; the returned function ignores its entity
     * argument.
     */
    public static Function<Slime, BlockState> constant(ResourceLocation blockId) {
        BlockState state = blockStateOrNull(blockId);
        return entity -> state;
    }

    private static BlockState blockStateOrNull(ResourceLocation blockId) {
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        return block == null ? null : block.defaultBlockState();
    }
}
