package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.FrogModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.world.entity.animal.frog.Frog;

/**
 * Renderer for the dragon altar's plinth frog (#249) - "Dragonsbane". Makes it
 * read as a special, dragon-eating apex frog rather than a stock pond frog:
 * rendered much larger than life (display-only; the hitbox is unchanged) and
 * recoloured a deep ender-purple via the shared {@link CategoryTintLayer} (the
 * same overlay the Resource Frogs use for their category tint). Ambient ender
 * particles are emitted by the entity itself (see
 * {@link com.flatts.productivefrogs.content.entity.PlinthFrog#tick()}).
 */
public class PlinthFrogRenderer extends FrogRenderer {

    /** Display scale - imposing, dragon-eating presence. Visual only; hitbox stays frog-sized. */
    private static final float SCALE = 1.8F;
    /** Deep ender-purple void recolour overlaid on the body. */
    private static final int VOID_TINT = 0xFF5A2E8C;

    public PlinthFrogRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new CategoryTintLayer<Frog, FrogModel<Frog>>(
            this, this::getTextureLocation, entity -> VOID_TINT));
    }

    @Override
    protected void scale(Frog frog, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
        super.scale(frog, pose, partialTick);
    }
}
