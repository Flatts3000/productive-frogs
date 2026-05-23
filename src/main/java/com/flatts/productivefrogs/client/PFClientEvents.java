package com.flatts.productivefrogs.client;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.client.renderer.CaveSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.GeodeSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceFrogRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.ResourceTadpoleRenderer;
import com.flatts.productivefrogs.client.renderer.TideSlimeRenderer;
import com.flatts.productivefrogs.client.renderer.VoidSlimeRenderer;
import com.flatts.productivefrogs.client.screen.SlimeMilkerScreen;
import com.flatts.productivefrogs.content.item.FrogEggItem;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFFluidTypes;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFMenuTypes;
import com.flatts.productivefrogs.registry.PFRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

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
        event.registerEntityRenderer(PFEntities.RESOURCE_SLIME.get(), ResourceSlimeRenderer::new);
        event.registerEntityRenderer(PFEntities.CAVE_SLIME.get(), CaveSlimeRenderer::new);
        event.registerEntityRenderer(PFEntities.GEODE_SLIME.get(), GeodeSlimeRenderer::new);
        event.registerEntityRenderer(PFEntities.TIDE_SLIME.get(), TideSlimeRenderer::new);
        event.registerEntityRenderer(PFEntities.VOID_SLIME.get(), VoidSlimeRenderer::new);
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
        // Variant-keyed configurable Froglight: BlockColor reads the variant
        // identifier from the BE, looks up the matching SlimeVariant in the
        // datapack registry, returns its primary_color.
        event.register(
            (state, level, pos, tintIndex) -> {
                if (tintIndex != 0 || level == null || pos == null) {
                    return -1;
                }
                var be = level.getBlockEntity(pos);
                if (!(be instanceof com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity froglightBe)) {
                    return -1;
                }
                ResourceLocation variantId = froglightBe.getVariantId();
                if (variantId == null) {
                    return -1;
                }
                var beLevel = froglightBe.getLevel();
                if (beLevel == null) {
                    return -1;
                }
                Registry<SlimeVariant> registry = beLevel.registryAccess()
                    .registry(PFRegistries.SLIME_VARIANT).orElse(null);
                if (registry == null) {
                    return -1;
                }
                SlimeVariant variant = registry.get(variantId);
                return variant == null ? -1 : variant.primaryColor();
            },
            PFBlocks.CONFIGURABLE_FROGLIGHT.get()
        );
    }

    /**
     * Item-color registration — 1.21.1 uses the legacy {@link RegisterColorHandlersEvent.Item}
     * event with per-item lambdas. (1.21.4+ moved this to JSON {@code items/*.json} +
     * {@code ItemTintSource} but that doesn't exist in 1.21.1.)
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        // Frog Egg bottle — vanilla potion model has layer0=potion_overlay (the
        // liquid) and layer1=potion (the bottle glass). We want to tint the
        // liquid, so target tintIndex == 0.
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            Category cat = stack.get(PFDataComponents.CONTAINED_CATEGORY.get());
            return cat == null ? -1 : cat.tintRgb();
        }, PFItems.FROG_EGG.get());

        // Resource Tadpole Bucket — tint from BUCKET_ENTITY_DATA Category
        event.register((stack, tintIndex) -> {
            if (tintIndex != 1) return -1;
            Category cat = ResourceTadpoleBucketItem.readCategory(stack);
            return cat == null ? -1 : cat.tintRgb();
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
                    ResourceLocation variantId = ResourceLocation.tryParse(tag.getString("Variant"));
                    if (variantId != null) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.level != null) {
                            Registry<SlimeVariant> registry = mc.level.registryAccess()
                                .registry(PFRegistries.SLIME_VARIANT).orElse(null);
                            if (registry != null) {
                                SlimeVariant variant = registry.get(variantId);
                                if (variant != null) return variant.primaryColor();
                            }
                        }
                    }
                }
                if (tag.contains("Category")) {
                    try {
                        return Category.valueOf(tag.getString("Category")).tintRgb();
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            return -1;
        }, PFItems.SLIME_BUCKET.get());

        // Configurable Froglight item — tint from SLIME_VARIANT component
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            ResourceLocation variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
            if (variantId == null) return -1;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return -1;
            Registry<SlimeVariant> registry = mc.level.registryAccess()
                .registry(PFRegistries.SLIME_VARIANT).orElse(null);
            if (registry == null) return -1;
            SlimeVariant variant = registry.get(variantId);
            return variant == null ? -1 : variant.primaryColor();
        }, PFItems.CONFIGURABLE_FROGLIGHT.get());

        // Per-category Froglight blockitems — inherit BlockColor automatically,
        // no separate Item color registration needed.

        // Spawn eggs need explicit color handlers under NeoForge 21.1.230 — the
        // vanilla SpawnEggItem auto-registration in ItemColors.createDefault
        // doesn't reliably fire for modded subclasses here. For every spawn egg
        // we fall back to the underlying SpawnEggItem.getColor(layer) which
        // returns the (primary, secondary) colors we passed to the ctor.

        // Variant slime spawn eggs (12) — prefer the datapack registry's
        // primary/secondary for richer per-variant colours; fall back to the
        // ctor colours when the registry isn't loaded yet (creative tab
        // preview before world load).
        for (var entry : PFItems.RESOURCE_SLIME_SPAWN_EGGS.entrySet()) {
            event.register((stack, tintIndex) -> {
                ResourceLocation variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
                if (variantId != null) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null) {
                        Registry<SlimeVariant> registry = mc.level.registryAccess()
                            .registry(PFRegistries.SLIME_VARIANT).orElse(null);
                        if (registry != null) {
                            SlimeVariant variant = registry.get(variantId);
                            if (variant != null) {
                                return tintIndex == 0 ? variant.primaryColor() : variant.secondaryColor();
                            }
                        }
                    }
                }
                // Fallback to SpawnEggItem ctor colors (Category.tintRgb + darker shade).
                return ((net.minecraft.world.item.SpawnEggItem) stack.getItem()).getColor(tintIndex);
            }, entry.getValue().get());
        }

        // Parent slime species spawn eggs (4) + category frog/tadpole spawn eggs (12).
        // All of these inherit from vanilla SpawnEggItem with explicit
        // (primary, secondary) colours set in PFItems.
        java.util.function.Consumer<net.minecraft.world.item.SpawnEggItem> registerCtorColors = egg ->
            event.register((stack, tintIndex) ->
                ((net.minecraft.world.item.SpawnEggItem) stack.getItem()).getColor(tintIndex),
                egg);

        registerCtorColors.accept(PFItems.CAVE_SLIME_SPAWN_EGG.get());
        registerCtorColors.accept(PFItems.GEODE_SLIME_SPAWN_EGG.get());
        registerCtorColors.accept(PFItems.TIDE_SLIME_SPAWN_EGG.get());
        registerCtorColors.accept(PFItems.VOID_SLIME_SPAWN_EGG.get());

        for (var entry : PFItems.RESOURCE_FROG_SPAWN_EGGS.entrySet()) {
            registerCtorColors.accept(entry.getValue().get());
        }
        for (var entry : PFItems.RESOURCE_TADPOLE_SPAWN_EGGS.entrySet()) {
            registerCtorColors.accept(entry.getValue().get());
        }
    }

    /**
     * Bind the Slime Milker's MenuType to its client screen.
     */
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(PFMenuTypes.SLIME_MILKER.get(), SlimeMilkerScreen::new);
    }

    /**
     * Wire each Slime Milk FluidType to its still + flowing block textures.
     */
    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        for (String variant : PFFluidTypes.VARIANTS) {
            ResourceLocation still = ResourceLocation.fromNamespaceAndPath(
                ProductiveFrogs.MOD_ID, "block/" + variant + "_slime_milk_still");
            ResourceLocation flow = ResourceLocation.fromNamespaceAndPath(
                ProductiveFrogs.MOD_ID, "block/" + variant + "_slime_milk_flow");
            event.registerFluidType(
                new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() { return still; }

                    @Override
                    public ResourceLocation getFlowingTexture() { return flow; }
                },
                PFFluidTypes.BY_VARIANT.get(variant).get()
            );
        }
    }
}
