package com.flatts.productivefrogs.client;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.client.renderer.CaveSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.GeodeSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceFrogRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceTadpoleRenderer;
import com.flatts.productivefrogs.client.tint.ContainedCategoryTint;
import com.flatts.productivefrogs.client.tint.SlimeVariantTint;
import com.flatts.productivefrogs.client.tint.TadpoleBucketCategoryTint;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Client-only setup. Registers:
 *
 * <ul>
 *   <li>Custom {@link ResourceTadpoleRenderer} / {@link ResourceFrogRenderer}
 *       so our entities pick up per-category tint at render time.</li>
 *   <li>{@code BlockColor} handlers on each Primed Frog Egg block so
 *       {@code tintindex 0} in the shared block model picks up the category
 *       color in-world.</li>
 *   <li>Custom {@link ContainedCategoryTint} and {@link TadpoleBucketCategoryTint}
 *       ItemTintSources, referenced from item model JSONs to drive Frog Egg
 *       bottle + Tadpole Bucket content-layer tinting.</li>
 * </ul>
 *
 * <p>All tint values flow through {@link Category#tintArgb()} — single source
 * of truth for category color.
 *
 * <p>Note on item color API in 1.21.x: vanilla and NeoForge removed the
 * legacy {@code RegisterColorHandlersEvent.Item} event. Per-item runtime
 * tinting is now declared in the item model JSON via a {@code "tints"} array,
 * with each entry referencing a registered {@code ItemTintSource} type.
 * Block-item inventory icons that reference a block model with
 * {@code tintindex} still pick up tint via {@code BlockColor}.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID, value = Dist.CLIENT)
public final class PFClientEvents {

    private PFClientEvents() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PFEntities.RESOURCE_TADPOLE.get(), ResourceTadpoleRenderer::new);
        event.registerEntityRenderer(PFEntities.RESOURCE_FROG.get(), ResourceFrogRenderer::new);
        event.registerEntityRenderer(PFEntities.RESOURCE_SLIME.get(), ResourceSlimeRenderer::new);
        event.registerEntityRenderer(PFEntities.CAVE_SLIME.get(), CaveSlimeRenderer::new);
        event.registerEntityRenderer(PFEntities.GEODE_SLIME.get(), GeodeSlimeRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        for (Category cat : Category.values()) {
            event.register(
                (state, level, pos, tintIndex) -> tintIndex == 0 ? cat.tintRgb() : -1,
                PFBlocks.primedEgg(cat)
            );
            event.register(
                (state, level, pos, tintIndex) -> tintIndex == 0 ? cat.tintRgb() : -1,
                PFBlocks.resourceFroglight(cat)
            );
        }
    }

    @SubscribeEvent
    public static void onRegisterItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "contained_category"),
            ContainedCategoryTint.MAP_CODEC
        );
        event.register(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_variant"),
            SlimeVariantTint.MAP_CODEC
        );
        event.register(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "tadpole_bucket_category"),
            TadpoleBucketCategoryTint.MAP_CODEC
        );
    }
}
