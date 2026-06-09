package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Creative-mode tab registration. Productive Frogs gets a single dedicated tab
 * that aggregates every item the mod registers.
 */
public final class PFCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProductiveFrogs.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PRODUCTIVE_FROGS_TAB =
        CREATIVE_MODE_TABS.register(
            "productive_frogs",
            () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.productivefrogs"))
                .icon(() -> PFItems.FROG_EGG.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    // Variant catalog is the SLIME_VARIANT datapack registry, read
                    // from the display params' holder lookup. Present when the tab
                    // is (re)built in-world; absent/empty at the title screen before
                    // a world's datapacks load, in which case the variant-driven
                    // groups below simply show nothing until a world is joined
                    // (standard for datapack-registry-driven creative entries).
                    var variantLookup = parameters.holders().lookup(PFRegistries.SLIME_VARIANT);

                    // Default (unprimed) Frog Egg bottle, then one primed bottle
                    // per category. The JEI plugin
                    // (client/jei/ProductiveFrogsJeiPlugin) subtypes Frog Egg
                    // by CONTAINED_CATEGORY, so each stamped stack here becomes
                    // a distinct JEI entry.
                    output.accept(PFItems.FROG_EGG.get());
                    for (Category cat : Category.values()) {
                        ItemStack primed = new ItemStack(PFItems.FROG_EGG.get());
                        primed.set(PFDataComponents.CONTAINED_CATEGORY.get(), cat);
                        output.accept(primed);
                    }
                    // Default empty Resource Tadpole Bucket, then one stamped
                    // with each category. The bucket's category lives inside
                    // BUCKET_ENTITY_DATA NBT (the JEI plugin keys the subtype
                    // off that component).
                    output.accept(PFItems.RESOURCE_TADPOLE_BUCKET.get());
                    for (Category cat : Category.values()) {
                        output.accept(makeCategoryTadpoleBucket(cat));
                    }
                    // Default empty Slime Bucket, then one stamped per variant.
                    // Variant stamping mirrors what ResourceSlime.saveToBucketTag
                    // writes when a player buckets a variant-locked slime
                    // (Category + Variant strings in BUCKET_ENTITY_DATA).
                    output.accept(PFItems.SLIME_BUCKET.get());
                    variantLookup.ifPresent(reg -> reg.listElements().forEach(h ->
                        output.accept(PFItems.variantSlimeBucket(h.key().location(), h.value().category()))));
                    // Milker / Churn appear only when enabled (config-gated, #196).
                    if (!PFConfig.SPEC.isLoaded() || PFConfig.SLIME_MILKER_ENABLED.get()) {
                        output.accept(PFItems.SLIME_MILKER.get());
                    }
                    // The Slime Churn (#187) sits right after its inverse.
                    if (!PFConfig.SPEC.isLoaded() || PFConfig.SLIME_CHURN_ENABLED.get()) {
                        output.accept(PFItems.SLIME_CHURN.get());
                    }
                    // The Froglight Crucible + Casting Mold (v1.12) sit with
                    // the other appliances; each appears only when enabled (#196).
                    if (!PFConfig.SPEC.isLoaded() || PFConfig.CRUCIBLE_ENABLED.get()) {
                        output.accept(PFItems.CRUCIBLE.get());
                    }
                    if (!PFConfig.SPEC.isLoaded() || PFConfig.CASTING_MOLD_ENABLED.get()) {
                        output.accept(PFItems.CASTING_MOLD.get());
                    }
                    // Boss-tier catalyst altar blocks (#184).
                    output.accept(PFItems.NETHER_STAR_CATALYST.get());
                    output.accept(PFItems.DRAGON_EGG_CATALYST.get());
                    output.accept(PFItems.WITHER_SKELETON_SKULL_CATALYST.get());
                    output.accept(PFItems.DRAGON_BREATH_CATALYST.get());
                    // Terrarium multiblock machines (#185).
                    output.accept(PFItems.TERRARIUM_CONTROLLER.get());
                    output.accept(PFItems.SPRINKLER.get());
                    output.accept(PFItems.INCUBATOR.get());
                    output.accept(PFItems.HATCH.get());
                    // Spawnery only appears in the tab when enabled (skyblock
                    // bootstrap; off by default). isLoaded guards the title-screen
                    // build before COMMON config is available.
                    if (PFConfig.SPEC.isLoaded() && PFConfig.SPAWNERY_ENABLED.get()) {
                        output.accept(PFItems.SPAWNERY.get());
                    }
                    // The Sweetslime breeding treat (slime ball + sugar), placed
                    // after the appliances so the two machines stay adjacent in the
                    // tab. It's the hand-fed item that drives same-species breeding.
                    output.accept(PFItems.SWEETSLIME.get());
                    // Slime Milk catalysts (drop into a source to buff it). Hidden
                    // when the feature is config-disabled, mirroring the Spawnery;
                    // shown by default. isLoaded guards the title-screen build.
                    // Master gates the group; each catalyst then shows only when its
                    // own flag is on (#201). isLoaded guards the title-screen build.
                    if (PFConfig.SPEC.isLoaded() && PFConfig.MILK_CATALYSTS_ENABLED.get()) {
                        if (PFConfig.CATALYST_COUNT_ENABLED.get()) {
                            output.accept(PFItems.COUNT_CATALYST.get());
                        }
                        if (PFConfig.CATALYST_SPEED_ENABLED.get()) {
                            output.accept(PFItems.SPEED_CATALYST.get());
                        }
                        if (PFConfig.CATALYST_QUANTITY_ENABLED.get()) {
                            output.accept(PFItems.QUANTITY_CATALYST.get());
                        }
                        if (PFConfig.CATALYST_INFINITE_ENABLED.get()) {
                            output.accept(PFItems.INFINITE_CATALYST.get());
                        }
                    }
                    // One Slime Milk bucket per registry variant that has a
                    // per-variant fluid (v1.8). A content-only variant (in the
                    // slime_variant registry but not minted at mod-init - e.g. a
                    // pack/world-datapack variant) has no bucket: slimeMilkBucket
                    // returns EMPTY, so skip it rather than add an invisible entry.
                    // (The vanilla/magma sentinels likewise have no bucket now.)
                    // Empty at the title screen until a world's datapacks load.
                    variantLookup.ifPresent(reg -> reg.listElements().forEach(h -> {
                        ItemStack milk = PFItems.slimeMilkBucket(h.key().location());
                        if (!milk.isEmpty()) {
                            output.accept(milk);
                        }
                    }));
                    for (var entry : PFItems.PRIMED_FROG_EGG_ITEMS.values()) {
                        output.accept(entry.get());
                    }
                    // V1.5: the 6 broad-strokes category Froglight BlockItems
                    // (bog_froglight, cave_froglight, …) were deleted entirely.
                    // ResourceSlimes always carry a variant, so the no-variant
                    // fallback drop path no longer exists. The only Froglight
                    // is the variant-stamped configurable_froglight below.
                    //
                    // One configurable_froglight per shipped variant — each stack
                    // carries its variant id in the SLIME_VARIANT data component so
                    // creative testers can see what the production loop produces.
                    variantLookup.ifPresent(reg -> reg.listElements().forEach(h -> {
                        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
                        stack.set(PFDataComponents.SLIME_VARIANT.get(), h.key().location());
                        output.accept(stack);
                    }));
                    // Spawn eggs grouped at the end so they read as a single block
                    // in the creative tab — frogs first, tadpoles after.
                    for (var entry : PFItems.RESOURCE_FROG_SPAWN_EGGS.values()) {
                        output.accept(entry.get());
                    }
                    for (var entry : PFItems.RESOURCE_TADPOLE_SPAWN_EGGS.values()) {
                        output.accept(entry.get());
                    }
                    // One stamped stack per variant (no unstamped base egg — a
                    // variant-less Resource Slime egg isn't a meaningful creative
                    // entry; the item still counts as "in a tab" via these stacks).
                    variantLookup.ifPresent(reg -> reg.listElements().forEach(h ->
                        output.accept(PFItems.resourceSlimeSpawnEgg(h.key().location()))));
                    // Parent species spawn eggs (Cave / Geode / Tide / Void) —
                    // not category-themed, kept after the variant eggs so the
                    // tab reads as: variants first, then upstream sources.
                    output.accept(PFItems.BOG_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.CAVE_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.GEODE_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.TIDE_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.INFERNAL_SLIME_SPAWN_EGG.get());
                    output.accept(PFItems.VOID_SLIME_SPAWN_EGG.get());
                })
                .build()
        );

    /**
     * Build a Resource Tadpole Bucket stamped with a category — mirrors
     * {@code ResourceTadpole.saveToBucketTag}. Tadpole buckets only carry
     * Category (no variant); the dynamic display name in
     * {@code ResourceTadpoleBucketItem.getName} reads this tag too.
     */
    private static ItemStack makeCategoryTadpoleBucket(Category category) {
        ItemStack stack = new ItemStack(PFItems.RESOURCE_TADPOLE_BUCKET.get());
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> {
            tag.putString("Category", category.name());
        });
        return stack;
    }

    private PFCreativeTabs() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
