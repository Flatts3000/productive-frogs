package com.flatts.productivefrogs.client;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.client.color.BucketCategoryTint;
import com.flatts.productivefrogs.client.color.ConfigurableFroglightBlockTint;
import com.flatts.productivefrogs.client.color.ConfigurableFroglightTint;
import com.flatts.productivefrogs.client.color.ConstantBlockTint;
import com.flatts.productivefrogs.client.color.ContainedCategoryTint;
import com.flatts.productivefrogs.client.color.NoTint;
import com.flatts.productivefrogs.client.color.ResourceSlimeEggTint;
import com.flatts.productivefrogs.client.color.SlimeBucketTint;
import com.flatts.productivefrogs.client.color.SprinklerBlockTint;
import com.flatts.productivefrogs.client.color.SynthesizedItemTint;
import com.flatts.productivefrogs.client.color.Tints;
import com.flatts.productivefrogs.client.color.VariantColorTint;
import com.flatts.productivefrogs.client.renderer.ParentSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceFrogRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceTadpoleRenderer;
import com.flatts.productivefrogs.client.screen.CastingMoldScreen;
import com.flatts.productivefrogs.client.screen.SlimeChurnScreen;
import com.flatts.productivefrogs.client.screen.SlimeMilkerScreen;
import com.flatts.productivefrogs.client.screen.SpawneryScreen;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFMenuTypes;
import com.flatts.productivefrogs.registry.PFParticles;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only setup. Registers:
 *
 * <ul>
 *   <li>Entity / block-entity renderers (slime, tadpole, frog, crucible, altars).</li>
 *   <li>{@link RegisterColorHandlersEvent.BlockTintSources} for the Primed Frog Egg
 *       blocks, the variant-keyed Configurable Froglight, and the Sprinkler milk
 *       surface (block tints still bind by {@code Block} object, list index =
 *       tint index).</li>
 *   <li>{@link RegisterColorHandlersEvent.ItemTintSources} for the item-form tints.
 *       In 26.1 item tints are data-driven: this registers the tint-source
 *       <b>codec types</b>; each item's <b>model JSON</b> {@code tints} array
 *       references a type by id to actually apply it (see the NOTE on
 *       {@link #onRegisterItemColors}).</li>
 *   <li>{@link AddClientReloadListenersEvent} to drop the client-side caches on a
 *       resource reload.</li>
 * </ul>
 *
 * <p>26.1 port: the legacy {@code RegisterColorHandlersEvent.Block} / {@code .Item}
 * lambda registration is gone; tints are now {@code BlockTintSource} /
 * {@code ItemTintSource} objects (package {@code client.color}). Per-item fluid
 * texture/tint and the Frog Net model predicate also moved to data-driven model
 * JSON - see the NOTEs below.
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
        // Dragon altar display frog (#249) "Dragonsbane": oversized, ender-tinted apex frog.
        event.registerEntityRenderer(PFEntities.DRAGONSBANE.get(),
            com.flatts.productivefrogs.client.renderer.DragonsbaneFrogRenderer::new);
        // Witherbane (#247): the Wither Altar's oversized, infernal-tinted display frog.
        event.registerEntityRenderer(PFEntities.WITHERBANE.get(),
            com.flatts.productivefrogs.client.renderer.WitherbaneFrogRenderer::new);
        event.registerEntityRenderer(PFEntities.RESOURCE_SLIME.get(), ResourceSlimeRenderer::new);
        // Mimic Slime (#253): vanilla body + an item-tinted translucent shell.
        event.registerEntityRenderer(PFEntities.MIMIC_SLIME.get(),
            com.flatts.productivefrogs.client.renderer.MimicSlimeRenderer::new);
        // Six parent species share one parameterized ParentSlimeRenderer, each
        // constructed with its species atlas + outer-shell tint.
        event.registerEntityRenderer(PFEntities.BOG_SLIME.get(),
            ctx -> new ParentSlimeRenderer(ctx, parentTexture("bog_slime"), 0xFF6A8540));
        event.registerEntityRenderer(PFEntities.CAVE_SLIME.get(),
            ctx -> new ParentSlimeRenderer(ctx, parentTexture("cave_slime"), 0xFF8A8A8A));
        event.registerEntityRenderer(PFEntities.GEODE_SLIME.get(),
            ctx -> new ParentSlimeRenderer(ctx, parentTexture("geode_slime"), 0xFF6CDCD7));
        event.registerEntityRenderer(PFEntities.TIDE_SLIME.get(),
            ctx -> new ParentSlimeRenderer(ctx, parentTexture("tide_slime"), 0xFF3F76E4));
        event.registerEntityRenderer(PFEntities.INFERNAL_SLIME.get(),
            ctx -> new ParentSlimeRenderer(ctx, parentTexture("infernal_slime"), 0xFFC73E1D));
        event.registerEntityRenderer(PFEntities.VOID_SLIME.get(),
            ctx -> new ParentSlimeRenderer(ctx, parentTexture("void_slime"), 0xFF5E3782));
        // Crucible interior surfaces (rising fluid + variant-tinted solids) -
        // Ex Deorum-parity look, see client/renderer/CrucibleRenderer.
        event.registerBlockEntityRenderer(
            com.flatts.productivefrogs.registry.PFBlockEntities.CRUCIBLE.get(),
            com.flatts.productivefrogs.client.renderer.CrucibleRenderer::new);
        // End Crystal Receptacle (#249): the floating vanilla end-crystal model on
        // top when filled. See client/renderer/EndCrystalReceptacleRenderer.
        event.registerBlockEntityRenderer(
            com.flatts.productivefrogs.registry.PFBlockEntities.END_CRYSTAL_RECEPTACLE.get(),
            com.flatts.productivefrogs.client.renderer.EndCrystalReceptacleRenderer::new);
        // End Dragon Altar Hatch (#249): the summon animation - converging beams +
        // the growing dragon model. See client/renderer/EndDragonAltarHatchRenderer.
        event.registerBlockEntityRenderer(
            com.flatts.productivefrogs.registry.PFBlockEntities.END_DRAGON_ALTAR_HATCH.get(),
            com.flatts.productivefrogs.client.renderer.EndDragonAltarHatchRenderer::new);
        // Wither Altar Hatch (#247): the summon animation - the charging Wither replica
        // growing over the ritual. See client/renderer/WitherAltarHatchRenderer.
        event.registerBlockEntityRenderer(
            com.flatts.productivefrogs.registry.PFBlockEntities.WITHER_ALTAR_HATCH.get(),
            com.flatts.productivefrogs.client.renderer.WitherAltarHatchRenderer::new);
        // Wither summon receptacles (#247): the held item (soul sand / wither skull)
        // rendered on the frog-facing face. See client/renderer/WitherSummonReceptacleRenderer.
        event.registerBlockEntityRenderer(
            com.flatts.productivefrogs.registry.PFBlockEntities.WITHER_SUMMON_RECEPTACLE.get(),
            com.flatts.productivefrogs.client.renderer.WitherSummonReceptacleRenderer::new);
    }

    private static Identifier parentTexture(String name) {
        return Identifier.fromNamespaceAndPath(
            ProductiveFrogs.MOD_ID, "textures/entity/slime/" + name + ".png");
    }

    /**
     * Block tint sources (26.1). The legacy {@code RegisterColorHandlersEvent.Block}
     * lambda API is gone; tints are {@code BlockTintSource} objects registered per
     * {@code Block}, where the {@link List} index is the tint index. Unlike item
     * tints, block tints still bind by {@code Block} object here (no model-JSON
     * step), so these apply directly.
     */
    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        for (Category cat : Category.values()) {
            // Primed Frog Egg blocks - per-species constant colour at tint index 0.
            event.register(
                List.of(new ConstantBlockTint(Tints.opaque(cat.tintRgb()))),
                PFBlocks.primedEgg(cat));
        }
        // Midas egg (#253) - gold, its own block (not a tinted VOID egg).
        event.register(
            List.of(new ConstantBlockTint(0xFFFFD700)),
            PFBlocks.MIDAS_FROG_EGG.get());
        // Variant-keyed configurable Froglight: reads the variant id from the BE,
        // looks up the matching SlimeVariant, returns its primary_color (or the
        // EE-lane synthesized item's sprite-average colour). Tint index 0.
        event.register(
            List.of(new ConfigurableFroglightBlockTint()),
            PFBlocks.CONFIGURABLE_FROGLIGHT.get());
        // Filled Sprinkler: the top milk surface (tint index 1) reads the held
        // variant off the BE and tints to its primary_color. Index 0 (base faces)
        // is untinted via NoTint so the milk source sits at index 1.
        event.register(
            List.of(NoTint.INSTANCE, new SprinklerBlockTint()),
            PFBlocks.SPRINKLER.get());
    }

    /**
     * Item tint sources (26.1). The legacy {@code RegisterColorHandlersEvent.Item}
     * per-item lambda API is gone. 26.1 item tints are fully data-driven: this
     * registers the tint-source <b>codec types</b> by id; an item only tints once
     * its <b>model JSON</b> references a type from its {@code tints} array.
     *
     * <p>NOTE (26.1 port, item-model tints TODO - runClient-verified, not a compile
     * blocker): the repo's item models are still in the legacy
     * {@code assets/productivefrogs/models/item/*.json} form. Migrating to the
     * 1.21.4+ {@code assets/productivefrogs/items/*.json} item-definition format and
     * authoring the {@code tints} arrays is a separate (broad) workstream. Until
     * then these tint-source types are registered but unbound, so the item-form
     * tints below will not render. The required {@code tints} bindings are:
     * <ul>
     *   <li>{@code frog_egg} layer 0 -> {@code productivefrogs:contained_category} (spot=false).</li>
     *   <li>{@code resource_tadpole_bucket} silhouette layer -> {@code productivefrogs:tadpole_bucket_category}.</li>
     *   <li>{@code slime_bucket} silhouette layer -> {@code productivefrogs:slime_bucket}.</li>
     *   <li>{@code mimic_slime_bucket} + {@code mimic_milk_bucket} layer -> {@code productivefrogs:synthesized_item}.</li>
     *   <li>each {@code <variant>_slime_milk_bucket} milk layer -> {@code productivefrogs:variant_color} with {@code "variant": "<id>"}
     *       (replaces the old per-variant registration loop over PFVariantMilk.registeredVariants()).</li>
     *   <li>{@code configurable_froglight} layer 0 -> {@code productivefrogs:configurable_froglight}.</li>
     *   <li>{@code resource_slime_spawn_egg} base layer -> {@code productivefrogs:resource_slime_egg} (spot=false),
     *       overlay layer -> same type with {@code "spot": true}.</li>
     *   <li>frog / tadpole spawn eggs base layer -> {@code productivefrogs:contained_category} (spot=false),
     *       overlay layer -> same type with {@code "spot": true}.</li>
     *   <li>the six parent-slime spawn eggs ({@code cave_slime_spawn_egg}, ...) carry no category component,
     *       so bind each to {@code minecraft:constant} with the species' opaque {@code tintRgb()} (base) and
     *       the ~30% darker shade (overlay).</li>
     *   <li>Primed Frog Egg + Midas Frog Egg block-items: bind to {@code minecraft:constant} with the matching
     *       block colour (block tints no longer auto-propagate to the BlockItem icon in this model system).</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(id("contained_category"), ContainedCategoryTint.CODEC);
        event.register(id("tadpole_bucket_category"), BucketCategoryTint.CODEC);
        event.register(id("slime_bucket"), SlimeBucketTint.CODEC);
        event.register(id("synthesized_item"), SynthesizedItemTint.CODEC);
        event.register(id("configurable_froglight"), ConfigurableFroglightTint.CODEC);
        event.register(id("variant_color"), VariantColorTint.CODEC);
        event.register(id("resource_slime_egg"), ResourceSlimeEggTint.CODEC);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, path);
    }

    /**
     * Bind the appliance MenuTypes to their client screens.
     */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(PFMenuTypes.SLIME_MILKER.get(), SlimeMilkerScreen::new);
        event.register(PFMenuTypes.SLIME_CHURN.get(), SlimeChurnScreen::new);
        event.register(PFMenuTypes.SPAWNERY.get(), SpawneryScreen::new);
        event.register(PFMenuTypes.CASTING_MOLD.get(), CastingMoldScreen::new);
        event.register(PFMenuTypes.DISTILLER.get(), com.flatts.productivefrogs.client.screen.DistillerScreen::new);
        event.register(PFMenuTypes.ALEMBIC.get(), com.flatts.productivefrogs.client.screen.AlembicScreen::new);
        event.register(PFMenuTypes.HATCH.get(), com.flatts.productivefrogs.client.screen.HatchScreen::new);
        event.register(PFMenuTypes.INCUBATOR.get(), com.flatts.productivefrogs.client.screen.IncubatorScreen::new);
        event.register(PFMenuTypes.TERRARIUM_CONTROLLER.get(), com.flatts.productivefrogs.client.screen.TerrariumControllerScreen::new);
    }

    // NOTE (26.1 port, onClientSetup removed): net.minecraft.client.renderer.item.ItemProperties
    // is gone - item-model predicates moved to data-driven item model JSON
    // (select / condition / range_dispatch). The Frog Net's
    // productivefrogs:filled empty/loaded override (formerly an ItemProperties
    // predicate driven by FrogNetItem.isFilled) must move into the Frog Net item
    // model: re-home the existing "overrides" block in
    // assets/productivefrogs/models/item/frog_net.json as a 1.21.4+ "condition"
    // model on a boolean item-model property keyed to a "filled" state
    // (frog_net_filled when true, frog_net when false). runClient-verified; not a
    // compile blocker.

    /** Bind the tintable Sprinkler-drip particle to its sprite set. */
    @SubscribeEvent
    public static void onRegisterParticleProviders(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(PFParticles.SPRINKLER_DRIP.get(),
            com.flatts.productivefrogs.client.particle.SprinklerDripParticle.Provider::new);
    }

    /**
     * Drop the client-side caches on a resource reload. Without this, a pack that
     * adds or removes a {@code <variant>_resource_slime.png} between reloads keeps
     * serving the stale texture-presence result, and the EE-lane sprite-average
     * tint cache keeps serving pre-swap colours.
     *
     * <p>26.1 port: {@code RegisterClientReloadListenersEvent#registerReloadListener}
     * became {@link AddClientReloadListenersEvent#addListener(Identifier,
     * net.minecraft.server.packs.resources.PreparableReloadListener)} (now id-keyed).
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "client_caches"),
            (ResourceManagerReloadListener) rm -> {
                ResourceSlimeRenderer.clearTextureCaches();
                // Drop the EE-lane sprite-average tint cache so a resource-pack swap /
                // /reload re-samples re-textured items (Mimic Slime + Prismatic Froglight).
                SynthesizedTint.clearCache();
            });
    }

    // NOTE (26.1 port, R-1, fluid render moved to data-driven FluidModel):
    // onRegisterClientExtensions was removed. IClientFluidTypeExtensions in 26.1
    // lost getStillTexture / getFlowingTexture / getTintColor / getTintColor(state,
    // getter, pos) - it only carries getRenderOverlayTexture / renderOverlay /
    // modifyFogColor / modifyFogRender now. Per-fluid still/flow textures + tint
    // are data-driven via FluidModel (FluidStateModelSet; sprite =
    // ...getFluidStateModelSet().get(state).stillMaterial().sprite(), tint =
    // FluidModel.fluidTintSource()). PF mints its per-variant Slime Milk fluids,
    // the per-metal molten fluids, and the Mimic Milk fluid DYNAMICALLY at mod-init
    // from the variant index, so none of them has a static fluid model JSON. Giving
    // each one a FluidModel (datagen per known variant, or a runtime fluid-model
    // hook) is tied to the deferred R-1 milk single-fluid rework - resolve them
    // together. Until then these fluids render with the vanilla/default fluid model
    // (untextured/untinted milk). The tint logic these extensions used to carry is
    // preserved: per-variant + molten -> the variant's primary_color (see
    // VariantColorTint / Tints.variantColor); Mimic Milk -> the source BE's
    // synthesized item via SynthesizedTint.colorFor, neutral prismatic grey
    // (0xFFC8C8D2) when no position/BE is available. runClient-verified; not a
    // compile blocker.
}
