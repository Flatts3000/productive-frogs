package com.flatts.productivefrogs.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.FrogModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.world.entity.animal.frog.Frog;

/**
 * Renderer for the Wither Altar's display frog (#247) - "Witherbane". The Nether-side
 * counterpart to {@link DragonsbaneFrogRenderer}: rendered much larger than life
 * (display-only; the hitbox is unchanged) and recoloured a dark blue via the shared
 * {@link CategoryTintLayer} - echoing the Wither's blue charging glow. Ambient soul
 * particles are emitted by the entity itself (see
 * {@link com.flatts.productivefrogs.content.entity.WitherbaneFrog#tick()}).
 */
public class WitherbaneFrogRenderer extends FrogRenderer {

    /** Display scale - imposing, Wither-eating presence. Visual only; hitbox stays frog-sized. */
    private static final float SCALE = 1.8F;
    /** Dark blue recolour, echoing the Wither's blue invulnerable charging glow. */
    private static final int DARK_BLUE_TINT = 0xFF1A2B5C;

    public WitherbaneFrogRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.addLayer(new CategoryTintLayer<Frog, FrogModel<Frog>>(
            this, this::getTextureLocation, entity -> DARK_BLUE_TINT));
    }

    @Override
    protected void scale(Frog frog, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
        super.scale(frog, pose, partialTick);
    }
}
