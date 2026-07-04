package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.client.SynthesizedTint;
import com.flatts.productivefrogs.content.entity.MimicSlime;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Item;

/**
 * Renderer for the Mimic Slime (#253). Mirrors {@link ResourceSlimeRenderer}'s
 * two-pass shape: the vanilla inner slime cube (with eyes/mouth) drawn from a
 * neutral <b>greyscale</b> mimic body texture, plus a {@link MimicSlimeOuterLayer}
 * translucent shell tinted by the carried item's sprite-average colour.
 *
 * <p>The greyscale body is the key: tinting the green vanilla slime texture
 * muddied every colour, so {@code mimic_slime.png} is a desaturated, brightened
 * slime sheet on which the item tint reads true (redstone -> red, lapis -> blue).
 * The inner cube keeps the eyes for character; only the shell carries the colour.
 *
 * <p>26.1 note: the shell tint is resolved off the live entity once in
 * {@link #extractRenderState} into a {@link MimicSlimeRenderState}; the layer reads
 * the state, not the entity.
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
    public MimicSlimeRenderState createRenderState() {
        return new MimicSlimeRenderState();
    }

    @Override
    public void extractRenderState(Slime entity, SlimeRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (state instanceof MimicSlimeRenderState rs && entity instanceof MimicSlime mimic) {
            rs.shellTint = resolveShellTint(mimic);
        }
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        return BODY_TEXTURE;
    }

    /**
     * Resolve the outer-shell tint: the carried item's sprite-average colour, or
     * {@code -1} (no tint) when the slime carries no synthesized item. Pre-computed
     * at extract time (entity access via the live slime).
     */
    private static int resolveShellTint(MimicSlime mimic) {
        Item item = mimic.getSynthesizedItemAsItem();
        return item != null ? SynthesizedTint.colorFor(item) : -1;
    }
}
