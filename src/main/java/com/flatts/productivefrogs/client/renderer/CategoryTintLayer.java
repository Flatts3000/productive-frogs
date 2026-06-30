package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.util.PFDebug;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Re-submits the parent model with a per-entity tint colour on top of the vanilla
 * white body render. Used to apply the per-category tint to
 * {@link com.flatts.productivefrogs.content.entity.ResourceFrog} and
 * {@link com.flatts.productivefrogs.content.entity.ResourceTadpole} (and the
 * display frogs) without having to copy the full renderer body just to swap the
 * colour argument.
 *
 * <p>The vanilla white body still renders first (we don't suppress the render
 * type); this layer then runs after and re-submits the same model with the tint
 * via {@code RenderTypes.entityCutout(texture)} (the no-cull cutout type, the
 * 26.1 rename of the old {@code entityCutoutNoCull}). Opaque body pixels of the
 * tinted pass fully replace the white pixels below, which is fine for vanilla
 * Frog / Tadpole textures (both fully opaque on the body). Cutout-transparent
 * pixels pass through untouched.
 *
 * <p>26.1 note: the tint and texture are read off the render state, not the live
 * entity (the renderer extracts them into the state at extract time). The
 * deferred pipeline animates the model from the state at flush, so the layer just
 * submits - it never poses the model itself.
 */
public class CategoryTintLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>>
    extends RenderLayer<S, M> {

    private final Function<S, Identifier> textureGetter;
    private final ToIntFunction<S> tintGetter;

    public CategoryTintLayer(
        RenderLayerParent<S, M> parent,
        Function<S, Identifier> textureGetter,
        ToIntFunction<S> tintGetter
    ) {
        super(parent);
        this.textureGetter = textureGetter;
        this.tintGetter = tintGetter;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int lightCoords,
                       S state, float yRot, float xRot) {
        int tint = tintGetter.applyAsInt(state);
        Identifier texture = textureGetter.apply(state);
        if (PFDebug.on(PFDebug.Area.RENDER)) {
            PFDebug.logOnce(PFDebug.Area.RENDER, "tintlayer/" + texture + "/" + tint,
                () -> String.format("%s categoryTint=#%08X texture=%s",
                    state.getClass().getSimpleName(), tint, texture));
        }
        int overlayCoords = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        // 10-arg form: tint goes to the body colour (tintedColor); the 8-arg form
        // would route it to outlineColor and force the body to -1.
        collector.order(1).submitModel(
            getParentModel(), state, pose, RenderTypes.entityCutout(texture),
            lightCoords, overlayCoords, tint, null, 0, null);
    }
}
