package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Slime;

/**
 * Renderer for the Mimic Slime (#253). Mirrors {@link ResourceSlimeRenderer}'s
 * two-pass shape: the vanilla inner slime cube (with eyes/mouth) drawn from a
 * neutral <b>greyscale</b> mimic body texture, plus a {@link MimicSlimeOuterLayer}
 * translucent shell tinted at runtime by the carried item's sprite-average colour.
 *
 * <p>The greyscale body is the key: tinting the green vanilla slime texture
 * muddied every colour, so {@code mimic_slime.png} is a desaturated, brightened
 * slime sheet on which the item tint reads true (redstone -> red, lapis -> blue).
 * The inner cube keeps the eyes for character; only the shell carries the colour.
 */
public class MimicSlimeRenderer extends SlimeRenderer {

    static final Identifier BODY_TEXTURE =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/mimic_slime.png");

    public MimicSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.layers.removeIf(l -> l instanceof SlimeOuterLayer);
        this.addLayer(new MimicSlimeOuterLayer(this, ctx.getModelSet()));
    }

    @Override
    public Identifier getTextureLocation(Slime entity) {
        return BODY_TEXTURE;
    }
}
