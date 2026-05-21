package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Slime;

/**
 * Resource Slime renderer. Extends vanilla {@link SlimeRenderer} to reuse the
 * vanilla model and shadow size, but swaps two things:
 *
 * <ul>
 *   <li>Inner body texture — points at our desaturated {@code resource_slime.png}
 *       (see field doc below) so multiply-tinting renders as a clear category
 *       color instead of darker-green.</li>
 *   <li>Per-category color multiply via {@link #getModelTint}, driven by the
 *       category stored on {@link ResourceSlimeRenderState}.</li>
 * </ul>
 *
 * <p>Same render-state pattern as {@link com.flatts.productivefrogs.client.renderer.ResourceTadpoleRenderer}:
 * substitute a {@link ResourceSlimeRenderState} for the base state via
 * {@link #createRenderState}, populate the category in
 * {@link #extractRenderState}, read it back in {@code getModelTint}.
 */
public class ResourceSlimeRenderer extends SlimeRenderer {

    /**
     * Desaturated copy of {@code minecraft:textures/entity/slime/slime.png}.
     * Vanilla's texture is bright green, so multiplying our category tints
     * (which include mid-greys like METALLIC's {@code 0x808088}) over it just
     * shifts green darker — the player never sees a true category color. The
     * desaturated palette in this texture multiplies cleanly to whichever
     * category tint we apply.
     *
     * <p>Known limitation: vanilla's {@code SlimeOuterLayer} hard-codes
     * {@code SlimeRenderer.SLIME_LOCATION} and an untinted color, so the
     * faint translucent shell around the slime still shows the vanilla green
     * texture. A follow-up PR can swap that layer out for a category-aware
     * version; for now, the inner body tint is enough to read the category
     * in-world.
     */
    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "textures/entity/slime/resource_slime.png");

    public ResourceSlimeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public Identifier getTextureLocation(SlimeRenderState state) {
        return TEXTURE;
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

    @Override
    protected int getModelTint(SlimeRenderState state) {
        if (state instanceof ResourceSlimeRenderState rState && rState.category != null) {
            return rState.category.tintArgb();
        }
        return super.getModelTint(state);
    }
}
