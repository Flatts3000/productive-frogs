package com.flatts.productivefrogs.client.renderer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;
import org.jetbrains.annotations.Nullable;

/**
 * Renderer for the Mimic Slime (#253). Per the lane's spec the Mimic Slime has a
 * "generic synthesized render, no baked inner-cube": it is a single translucent
 * jelly tinted at runtime by the carried item's sprite-average colour (redstone
 * reads red, lapis blue, ...), with no inner resource block.
 *
 * <p>Implementation: the vanilla inner slime cube is <b>suppressed</b>
 * ({@link #getRenderType} returns null), and the only geometry drawn is the
 * {@link MimicSlimeOuterLayer}'s item-tinted translucent shell - render layers
 * still draw when the main model is suppressed. This is the deliberate contrast
 * with {@link ResourceSlimeRenderer}, which keeps the inner cube for its baked
 * per-variant resource texture.
 */
public class MimicSlimeRenderer extends SlimeRenderer {

    static final ResourceLocation BODY_TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png");

    public MimicSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new MimicSlimeOuterLayer(this, ctx.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(Slime entity) {
        return BODY_TEXTURE;
    }

    /**
     * Suppress the opaque inner cube. Returning null skips the main-model render
     * (no baked interior); the tinted translucent shell from
     * {@link MimicSlimeOuterLayer} still draws as a render layer.
     */
    @Override
    @Nullable
    protected RenderType getRenderType(Slime entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return null;
    }
}
