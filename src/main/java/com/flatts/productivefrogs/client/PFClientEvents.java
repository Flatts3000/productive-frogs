package com.flatts.productivefrogs.client;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.client.renderer.ParentSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceFrogRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceTadpoleRenderer;
import com.flatts.productivefrogs.client.screen.CastingMoldScreen;
import com.flatts.productivefrogs.client.screen.SlimeChurnScreen;
import com.flatts.productivefrogs.client.screen.SlimeMilkerScreen;
import com.flatts.productivefrogs.client.screen.SpawneryScreen;
import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
import com.flatts.productivefrogs.content.item.FrogEggItem;
import com.flatts.productivefrogs.content.item.FrogNetItem;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFFluidTypes;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFMenuTypes;
import com.flatts.productivefrogs.registry.PFParticles;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup. Registers:
 *
 * <ul>
 *   <li>Entity renderers (slime, tadpole, frog).</li>
 *   <li>{@link RegisterColorHandlersEvent.Block} for the Primed Frog Egg blocks,
 *       category Froglight blocks, and the variant-keyed Configurable Froglight.</li>
 *   <li>{@link RegisterColorHandlersEvent.Item} for the item-form tints
 *       (Slime Bucket, Resource Tadpole Bucket, Frog Egg bottle, variant
 *       spawn eggs, Configurable Froglight item, category Froglights).</li>
 *   <li>{@link RegisterClientExtensionsEvent} for per-variant Slime Milk fluid
 *       textures.</li>
 * </ul>
 *
 * <p>On 1.21.1 we register item colors via {@link RegisterColorHandlersEvent.Item}
 * (the legacy event still present in this version). The 1.21.4+ JSON-driven
 * {@code ItemTintSource} pipeline doesn't exist here — it'll come back in a
 * future MC version bump.
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

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        for (Category cat : Category.values()) {
            final int rgb = opaque(cat.tintRgb());
            event.register(
                (state, level, pos, tintIndex) -> tintIndex == 0 ? rgb : -1,
                PFBlocks.primedEgg(cat)
            );
        }
        // Midas egg (#253) - gold, its own block (not a tinted VOID egg).
        event.register(
            (state, level, pos, tintIndex) -> tintIndex == 0 ? 0xFFFFD700 : -1,
            PFBlocks.MIDAS_FROG_EGG.get()
        );
        // Variant-keyed configurable Froglight: BlockColor reads the variant
        // identifier from the BE, looks up the matching SlimeVariant in the
        // datapack registry, returns its primary_color.
        event.register(
            (state, level, pos, tintIndex) -> {
                if (tintIndex != 0 || level == null || pos == null) {
                    return -1;
                }
                var be = level.getBlockEntity(pos);
                if (!(be instanceof ConfigurableFroglightBlockEntity froglightBe)) {
                    return -1;
                }
                // Equivalence lane (#253): a placed Prismatic Froglight tints from
                // its carried item's sprite-average colour (runtime resolver).
                Identifier synthBlockItem = froglightBe.getSynthesizedItem();
                if (synthBlockItem != null) {
                    Item item = BuiltInRegistries.ITEM.getOptional(synthBlockItem).orElse(null);
                    return item == null ? -1 : SynthesizedTint.colorFor(item);
                }
                Identifier variantId = froglightBe.getVariantId();
                if (variantId == null) {
                    return -1;
                }
                var beLevel = froglightBe.getLevel();
                if (beLevel == null) {
                    return -1;
                }
                var registry = PFRegistries.variants(beLevel.registryAccess());
                if (registry == null) {
                    return -1;
                }
                SlimeVariant variant = PFRegistries.variant(registry, variantId);
                final int argb = variant == null ? -1 : opaque(variant.primaryColor());
                if (PFDebug.on(PFDebug.Area.TINT)) {
                    PFDebug.logOnce(PFDebug.Area.TINT, "froglight_block/" + variantId,
                        () -> String.format("configurable_froglight(block) variant=%s -> #%08X", variantId, argb));
                }
                return argb;
            },
            PFBlocks.CONFIGURABLE_FROGLIGHT.get()
        );
        // Filled Sprinkler: the top milk surface (tintIndex 1) reads the held
        // variant off the BE and tints to its primary_color, so a filled Sprinkler
        // shows what milk is inside it from above. The base faces (tintIndex 0)
        // and an empty Sprinkler (no variant / empty model has no tinted face)
        // are untinted.
        event.register(
            (state, level, pos, tintIndex) -> {
                if (tintIndex != 1 || level == null || pos == null) {
                    return -1;
                }
                if (!(level.getBlockEntity(pos)
                        instanceof com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity sprinkler)
                        || sprinkler.isEmpty()) {
                    return -1;
                }
                Identifier variantId = sprinkler.getVariantId();
                if (variantId == null) {
                    return -1;
                }
                int color = variantTint(variantId);
                return color != -1 ? color : opaque(0xF0F0E0);
            },
            PFBlocks.SPRINKLER.get()
        );
    }

    /**
     * Item-color registration — 1.21.1 uses the legacy {@link RegisterColorHandlersEvent.Item}
     * event with per-item lambdas. (1.21.4+ moved this to JSON {@code items/*.json} +
     * {@code ItemTintSource} but that doesn't exist in 1.21.1.)
     *
     * <p>Every non-{@code -1} return is OR-ed with {@code 0xFF000000} via
     * {@link #opaque(int)}. Without this, a raw 24-bit RGB value like
     * {@code 0x808088} is interpreted as ARGB with {@code alpha == 0}, which
     * renders the tinted layer fully transparent. Vanilla applies opaque-alpha
     * inside its auto-registered SpawnEggItem handler; modded handlers must
     * do it explicitly.
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        // Frog Egg bottle — vanilla potion model has layer0=potion_overlay (the
        // liquid) and layer1=potion (the bottle glass). We want to tint the
        // liquid, so target tintIndex == 0.
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            Category cat = stack.get(PFDataComponents.CONTAINED_CATEGORY.get());
            return cat == null ? -1 : opaque(cat.tintRgb());
        }, PFItems.FROG_EGG.get());

        // Resource Tadpole Bucket — tint from BUCKET_ENTITY_DATA Category.
        // Empty bucket (no captured tadpole) defaults to vanilla tadpole
        // brown so the silhouette stays visible in the creative tab.
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return -1;
            Category cat = ResourceTadpoleBucketItem.readCategory(stack);
            return cat == null ? opaque(0x6B4530) : opaque(cat.tintRgb());
        }, PFItems.RESOURCE_TADPOLE_BUCKET.get());

        // Slime Bucket — variant first (via BUCKET_ENTITY_DATA Variant),
        // fall back to category if no variant
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return -1;
            // Try variant primary_color first
            CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
            if (data != null) {
                CompoundTag tag = data.copyTag();
                if (tag.contains("Variant")) {
                    Identifier variantId = Identifier.tryParse(tag.getString("Variant"));
                    if (variantId != null) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.level != null) {
                            var registry = PFRegistries.variants(mc.level.registryAccess());
                            if (registry != null) {
                                SlimeVariant variant = PFRegistries.variant(registry, variantId);
                                if (variant != null) {
                                    final int argb = opaque(variant.primaryColor());
                                    if (PFDebug.on(PFDebug.Area.TINT)) {
                                        PFDebug.logOnce(PFDebug.Area.TINT, "slime_bucket/" + variantId,
                                            () -> String.format(
                                                "slime_bucket(item) tintIndex=1 variant=%s -> #%08X", variantId, argb));
                                    }
                                    return argb;
                                }
                            }
                        }
                    }
                }
                if (tag.contains("Category")) {
                    try {
                        return opaque(Category.valueOf(tag.getString("Category")).tintRgb());
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            // Empty Slime Bucket (no captured slime) — fall back to vanilla
            // slime green so the silhouette stays visible in the creative
            // tab instead of rendering an invisible (-1 = no tint) layer.
            return opaque(0x5DDE36);
        }, PFItems.SLIME_BUCKET.get());

        // Mimic Slime Bucket (#253) — the silhouette layer (tintIndex 1) wears
        // the carried item's sprite-average colour, read off the top-level
        // SYNTHESIZED_ITEM component. Falls back to a neutral prismatic grey
        // when un-stamped so the silhouette stays visible.
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return -1;
            Identifier itemId = stack.get(PFDataComponents.SYNTHESIZED_ITEM.get());
            if (itemId == null) return opaque(0xC8C8D2);
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            return item == null ? opaque(0xC8C8D2) : SynthesizedTint.colorFor(item);
        }, PFItems.MIMIC_SLIME_BUCKET.get());

        // Mimic Milk Bucket (#253) — milk layer (tintIndex 1) wears the carried
        // item's colour off the top-level SYNTHESIZED_ITEM component.
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return -1;
            Identifier itemId = stack.get(PFDataComponents.SYNTHESIZED_ITEM.get());
            if (itemId == null) return opaque(0xC8C8D2);
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            return item == null ? opaque(0xC8C8D2) : SynthesizedTint.colorFor(item);
        }, PFItems.MIMIC_MILK_BUCKET.get());

        // Slime Milk buckets — one item per variant (v1.8). Each tints its milk
        // layer (tintIndex 1) by its OWN variant's registry colour (the variant is
        // the item identity, no component lookup). Falls back to milky off-white
        // before the registry is available so the item stays visible.
        for (Identifier variantId : PFVariantMilk.registeredVariants()) {
            final Identifier vid = variantId;
            event.register((stack, tintIndex) -> {
                if (tintIndex != 1) {
                    return -1;
                }
                int color = variantTint(vid);
                return color != -1 ? color : opaque(0xF0F0E0);
            }, PFVariantMilk.bucket(vid));
        }

        // Primed Frog Egg block items — BlockColor doesn't auto-propagate to
        // BlockItem in 1.21.1 the way it does in 1.21.4+; register explicit
        // item color handlers so the 6 in-hand bottles tint per category.
        for (Category cat : Category.values()) {
            final int rgb = opaque(cat.tintRgb());
            event.register(
                (stack, tintIndex) -> tintIndex == 0 ? rgb : -1,
                PFItems.PRIMED_FROG_EGG_ITEMS.get(cat).get()
            );
        }
        // Midas egg block item (#253) - gold in inventory, matching the placed block.
        event.register(
            (stack, tintIndex) -> tintIndex == 0 ? 0xFFFFD700 : -1,
            PFItems.MIDAS_FROG_EGG.get()
        );

        // Configurable Froglight item — tint from SLIME_VARIANT component
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            // Equivalence lane (#253): a synthesized Froglight carries an arbitrary
            // item id (not a registered variant). Its tint is sampled from that
            // item's sprite at runtime - no primary_color to look up.
            Identifier synthesizedItem = stack.get(PFDataComponents.SYNTHESIZED_ITEM.get());
            if (synthesizedItem != null) {
                Item item = BuiltInRegistries.ITEM.getOptional(synthesizedItem).orElse(null);
                final int sargb = item == null ? -1 : SynthesizedTint.colorFor(item);
                if (PFDebug.on(PFDebug.Area.TINT)) {
                    PFDebug.logOnce(PFDebug.Area.TINT, "froglight_item_synth/" + synthesizedItem,
                        () -> String.format("configurable_froglight(item) synthesized=%s -> #%08X", synthesizedItem, sargb));
                }
                return sargb;
            }
            Identifier variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
            if (variantId == null) return -1;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return -1;
            var registry = PFRegistries.variants(mc.level.registryAccess());
            SlimeVariant variant = PFRegistries.variant(registry, variantId);
            final int argb = variant == null ? -1 : opaque(variant.primaryColor());
            if (PFDebug.on(PFDebug.Area.TINT)) {
                PFDebug.logOnce(PFDebug.Area.TINT, "froglight_item/" + variantId,
                    () -> String.format("configurable_froglight(item) variant=%s -> #%08X", variantId, argb));
            }
            return argb;
        }, PFItems.CONFIGURABLE_FROGLIGHT.get());

        // Per-category Froglight blockitems — inherit BlockColor automatically,
        // no separate Item color registration needed.

        // Spawn eggs need explicit color handlers under NeoForge 21.1.230 — the
        // vanilla SpawnEggItem auto-registration in ItemColors.createDefault
        // doesn't reliably fire for modded subclasses here. For every spawn egg
        // we fall back to the underlying SpawnEggItem.getColor(layer) which
        // returns the (primary, secondary) colors we passed to the ctor.

        // The single Resource Slime spawn egg — tint per-stack from the
        // SLIME_VARIANT component's registry colours; fall back to the item's
        // ctor colours (BOG) when no variant is set or the registry isn't loaded
        // yet (title-screen creative preview before world load).
        event.register((stack, tintIndex) -> {
            Identifier variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
            if (variantId != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    var registry = PFRegistries.variants(mc.level.registryAccess());
                    if (registry != null) {
                        SlimeVariant variant = PFRegistries.variant(registry, variantId);
                        if (variant != null) {
                            final int argb = opaque(tintIndex == 0 ? variant.primaryColor() : variant.secondaryColor());
                            if (PFDebug.on(PFDebug.Area.TINT)) {
                                PFDebug.logOnce(PFDebug.Area.TINT, "resource_slime_egg/" + variantId + "/" + tintIndex,
                                    () -> String.format("resource_slime_spawn_egg tintIndex=%d variant=%s -> #%08X",
                                        tintIndex, variantId, argb));
                            }
                            return argb;
                        }
                    }
                }
            }
            return opaque(((SpawnEggItem) stack.getItem()).getColor(tintIndex));
        }, PFItems.RESOURCE_SLIME_SPAWN_EGG.get());

        // Parent slime species spawn eggs (4) + category frog/tadpole spawn eggs (12).
        // All of these inherit from vanilla SpawnEggItem with explicit
        // (primary, secondary) colours set in PFItems.
        Consumer<SpawnEggItem> registerCtorColors = egg ->
            event.register((stack, tintIndex) ->
                opaque(((SpawnEggItem) stack.getItem()).getColor(tintIndex)),
                egg);

        registerCtorColors.accept(PFItems.BOG_SLIME_SPAWN_EGG.get());
        registerCtorColors.accept(PFItems.CAVE_SLIME_SPAWN_EGG.get());
        registerCtorColors.accept(PFItems.GEODE_SLIME_SPAWN_EGG.get());
        registerCtorColors.accept(PFItems.TIDE_SLIME_SPAWN_EGG.get());
        registerCtorColors.accept(PFItems.INFERNAL_SLIME_SPAWN_EGG.get());
        registerCtorColors.accept(PFItems.VOID_SLIME_SPAWN_EGG.get());

        for (var entry : PFItems.RESOURCE_FROG_SPAWN_EGGS.entrySet()) {
            registerCtorColors.accept(entry.getValue().get());
        }
        for (var entry : PFItems.RESOURCE_TADPOLE_SPAWN_EGGS.entrySet()) {
            registerCtorColors.accept(entry.getValue().get());
        }
    }

    /**
     * Ensure the alpha byte is set to 0xFF. Item color handlers in 1.21.1
     * return ARGB-shaped {@code int}s; a raw 24-bit RGB value (alpha == 0)
     * makes the tinted layer render fully transparent. Source colors here
     * ({@link Category#tintRgb()}, {@link SlimeVariant#primaryColor()}, the
     * ctor-passed {@link net.minecraft.world.item.SpawnEggItem} colors) are
     * all 24-bit; this normalises them.
     */
    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
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

    /**
     * Client-setup-time registrations. The Frog Net's {@code productivefrogs:filled}
     * item-model property drives its empty/loaded model override (mirrors Productive
     * Bees' bee-cage {@code filled} property). {@code ItemProperties.register} mutates
     * a shared map, so it runs on the main thread via {@code enqueueWork}.
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
            PFItems.FROG_NET.get(),
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "filled"),
            (stack, level, entity, seed) -> FrogNetItem.isFilled(stack) ? 1.0F : 0.0F));
    }

    /** Bind the tintable Sprinkler-drip particle to its sprite set. */
    @SubscribeEvent
    public static void onRegisterParticleProviders(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(PFParticles.SPRINKLER_DRIP.get(),
            com.flatts.productivefrogs.client.particle.SprinklerDripParticle.Provider::new);
    }

    /**
     * Drop the Resource Slime renderer's texture-existence cache on resource
     * reload. Without this, a pack that adds or removes a
     * {@code <variant>_resource_slime.png} between reloads keeps serving the
     * stale presence result (the category fallback would stick, or vice versa).
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
            (ResourceManagerReloadListener) rm -> {
                ResourceSlimeRenderer.clearTextureCaches();
                // Drop the EE-lane sprite-average tint cache so a resource-pack swap /
                // /reload re-samples re-textured items (Mimic Slime + Prismatic Froglight).
                SynthesizedTint.clearCache();
            });
    }

    /**
     * Bind each per-variant Slime Milk FluidType (v1.8) to the shared greyscale
     * still + flowing textures, tinted by that variant's {@code primary_color}.
     * Because the variant IS the fluid (one FluidType per variant), the tint is a
     * simple per-type colour lookup - no position walk-back: every block of a
     * variant's pool, source or flowing, tints the same.
     */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        Identifier still = Identifier.fromNamespaceAndPath(
            ProductiveFrogs.MOD_ID, "block/slime_milk_still");
        Identifier flow = Identifier.fromNamespaceAndPath(
            ProductiveFrogs.MOD_ID, "block/slime_milk_flow");
        for (Identifier variantId : PFVariantMilk.registeredVariants()) {
            final Identifier vid = variantId;
            FluidType type = PFVariantMilk.fluidType(vid);
            if (type == null) {
                continue;
            }
            event.registerFluidType(
                new IClientFluidTypeExtensions() {
                    @Override
                    public Identifier getStillTexture() { return still; }

                    @Override
                    public Identifier getFlowingTexture() { return flow; }

                    @Override
                    public int getTintColor() {
                        int color = variantTint(vid);
                        return color != -1 ? color : 0xFFFFFFFF;
                    }

                    @Override
                    public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                        return getTintColor();
                    }
                },
                type
            );
        }

        // Mimic Milk (#253): ONE fluid type, shared greyscale milk texture. Its
        // per-instance colour comes from the source block's BE at the queried
        // position (source-only, so every Mimic Milk block has a BE), resolved
        // through the runtime item-sprite resolver. No-position fallback is a
        // neutral prismatic grey (e.g. the held-bucket fluid render).
        event.registerFluidType(
            new IClientFluidTypeExtensions() {
                @Override
                public Identifier getStillTexture() { return still; }

                @Override
                public Identifier getFlowingTexture() { return flow; }

                @Override
                public int getTintColor() { return 0xFFC8C8D2; }

                @Override
                public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                    if (getter != null && pos != null
                            && getter.getBlockEntity(pos) instanceof com.flatts.productivefrogs.content.block.entity.MimicMilkSourceBlockEntity be) {
                        Identifier itemId = be.getSynthesizedItem();
                        if (itemId != null) {
                            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                            if (item != null) {
                                return SynthesizedTint.colorFor(item);
                            }
                        }
                    }
                    return getTintColor();
                }
            },
            PFFluidTypes.MIMIC_MILK_TYPE.get()
        );

        // Molten metals (v1.12): same per-variant tint model over a shared
        // greyscale molten texture set (desaturated lava still/flow). The
        // metal id IS the variant id, so the colour lookup is identical.
        Identifier moltenStill = Identifier.fromNamespaceAndPath(
            ProductiveFrogs.MOD_ID, "block/molten_still");
        Identifier moltenFlow = Identifier.fromNamespaceAndPath(
            ProductiveFrogs.MOD_ID, "block/molten_flow");
        for (Identifier metalId : com.flatts.productivefrogs.registry.PFMoltenFluids.registeredMetals()) {
            final Identifier vid = metalId;
            FluidType type = com.flatts.productivefrogs.registry.PFMoltenFluids.fluidType(vid);
            if (type == null) {
                continue;
            }
            event.registerFluidType(
                new IClientFluidTypeExtensions() {
                    @Override
                    public Identifier getStillTexture() { return moltenStill; }

                    @Override
                    public Identifier getFlowingTexture() { return moltenFlow; }

                    @Override
                    public int getTintColor() {
                        int color = variantTint(vid);
                        return color != -1 ? color : 0xFFFFFFFF;
                    }

                    @Override
                    public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                        return getTintColor();
                    }
                },
                type
            );
        }
    }

    /**
     * Opaque {@code primary_color} for a variant from the {@code slime_variant}
     * registry, or {@code -1} when the registry is not yet available (caller picks
     * a fallback). Shared by the per-variant milk bucket item tint and per-variant
     * fluid tint.
     */
    private static int variantTint(Identifier variantId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return -1;
        }
        var registry = PFRegistries.variants(mc.level.registryAccess());
        SlimeVariant variant = PFRegistries.variant(registry, variantId);
        return variant == null ? -1 : opaque(variant.primaryColor());
    }
}
