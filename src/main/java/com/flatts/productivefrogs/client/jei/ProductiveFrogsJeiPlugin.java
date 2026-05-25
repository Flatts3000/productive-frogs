package com.flatts.productivefrogs.client.jei;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFRegistries;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * JEI plugin for Productive Frogs. Surfaces two things:
 *
 * <ol>
 *   <li>Per-item <b>subtype interpreters</b> so items that share a single
 *       registry id but vary by data component (Slime Bucket, Resource Tadpole
 *       Bucket, Configurable Froglight, Frog Egg bottle) appear as distinct
 *       rows in the JEI sidebar.</li>
 *   <li>Per-item <b>Information pages</b> (the "Uses" / "Recipes" tab) that
 *       explain each PF item's role in the production loop — what it's
 *       hunted by, what it drops, what it smelts to, where it spawns, etc.
 *       Hover the item in JEI and press <kbd>U</kbd> or <kbd>R</kbd> to see.</li>
 * </ol>
 *
 * <p>Info pages are populated dynamically by walking the {@code SlimeVariant}
 * datapack registry at {@link #registerRecipes} time — adding a new variant
 * JSON (Productive Frogs or downstream modpack) auto-produces its info pages
 * without a code change.
 *
 * <p>{@link #registerRecipes} fires after each resource reload. The
 * {@code Minecraft.getInstance().level} is usually non-null at that point
 * (we've joined a world), but we tolerate null for the title-screen case
 * where JEI may re-run plugin init defensively.
 *
 * <p>JEI is {@code compileOnly} on our classpath; the {@link JeiPlugin}
 * annotation is JEI's auto-discovery hook so this class needs no separate
 * registration. Lives under {@code .client.jei.} because JEI is client-only.
 */
@JeiPlugin
public final class ProductiveFrogsJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // JEI 19.21 (1.21.1) has no registerFromDataComponentTypes shortcut —
        // implement the subtype interpreter manually per item.

        // Buckets — subtype by BUCKET_ENTITY_DATA (carries Category + Variant).
        ISubtypeInterpreter<ItemStack> bucketInterp = new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext ctx) {
                CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
                return data == null ? "" : data.copyTag().toString();
            }
            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext ctx) {
                Object d = getSubtypeData(stack, ctx);
                return d == null ? "" : d.toString();
            }
        };
        registration.registerSubtypeInterpreter(PFItems.SLIME_BUCKET.get(), bucketInterp);
        registration.registerSubtypeInterpreter(PFItems.RESOURCE_TADPOLE_BUCKET.get(), bucketInterp);

        // Configurable Froglight + the single Resource Slime spawn egg — both
        // carry their variant in SLIME_VARIANT, so they share one interpreter
        // and each per-variant stack becomes a distinct JEI row.
        ISubtypeInterpreter<ItemStack> slimeVariantInterp = new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext ctx) {
                ResourceLocation v = stack.get(PFDataComponents.SLIME_VARIANT.get());
                return v == null ? "" : v.toString();
            }
            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext ctx) {
                return (String) getSubtypeData(stack, ctx);
            }
        };
        registration.registerSubtypeInterpreter(PFItems.CONFIGURABLE_FROGLIGHT.get(), slimeVariantInterp);
        registration.registerSubtypeInterpreter(PFItems.RESOURCE_SLIME_SPAWN_EGG.get(), slimeVariantInterp);

        // Frog Egg bottle — subtype by CONTAINED_CATEGORY.
        registration.registerSubtypeInterpreter(PFItems.FROG_EGG.get(), new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext ctx) {
                Category c = stack.get(PFDataComponents.CONTAINED_CATEGORY.get());
                return c == null ? "" : c.name();
            }
            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext ctx) {
                return (String) getSubtypeData(stack, ctx);
            }
        });
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            // Title-screen / pre-join state. Info pages will re-register on
            // world join when level becomes non-null.
            return;
        }
        Registry<SlimeVariant> variants = level.registryAccess()
            .registry(PFRegistries.SLIME_VARIANT).orElse(null);
        if (variants == null) {
            return;
        }

        addVariantInfoPages(reg, variants);
        addSpeciesInfoPages(reg);
        addStaticInfoPages(reg);
    }

    /**
     * Per-variant info pages — one for each shipped SlimeVariant. Each variant
     * gets info on its spawn egg, its variant Slime Bucket, and its
     * variant-stamped Configurable Froglight. The page text references the
     * matching frog (resolved via variant's category).
     */
    private static void addVariantInfoPages(IRecipeRegistration reg, Registry<SlimeVariant> variants) {
        for (java.util.Map.Entry<ResourceKey<SlimeVariant>, SlimeVariant> entry : variants.entrySet()) {
            String variantName = entry.getKey().location().getPath(); // "iron", "copper", etc.
            SlimeVariant variant = entry.getValue();

            Component frogName = Component.translatable(
                "entity.productivefrogs.resource_frog." + variant.category().id());
            Component variantSlimeName = Component.translatable(
                "entity.productivefrogs.resource_slime." + variantName);
            Component info = Component.translatable(
                "productivefrogs.jei.variant_slime.info", variantSlimeName, frogName);

            // 1. Resource Slime spawn egg (per-variant stack of the single item)
            reg.addIngredientInfo(
                PFItems.resourceSlimeSpawnEgg(entry.getKey().location()),
                VanillaTypes.ITEM_STACK, info);

            // 2. Variant-stamped Slime Bucket
            ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            tag.putString("Variant", entry.getKey().location().toString());
            tag.putString("Category", variant.category().name());
            bucket.set(DataComponents.BUCKET_ENTITY_DATA, CustomData.of(tag));
            reg.addIngredientInfo(bucket, VanillaTypes.ITEM_STACK, info);

            // 3. Variant-stamped Configurable Froglight
            ItemStack froglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
            froglight.set(PFDataComponents.SLIME_VARIANT.get(), entry.getKey().location());
            Component froglightInfo = Component.translatable(
                "productivefrogs.jei.variant_froglight.info", frogName, variantSlimeName);
            reg.addIngredientInfo(froglight, VanillaTypes.ITEM_STACK, froglightInfo);

            // 4. Variant Slime Milk bucket — the single slime_milk_bucket item
            // stamped with this variant (collapsed from the per-variant items).
            ItemStack milkBucket = new ItemStack(PFItems.SLIME_MILK_BUCKET.get());
            milkBucket.set(PFDataComponents.SLIME_VARIANT.get(), entry.getKey().location());
            Component milkInfo = Component.translatable(
                "productivefrogs.jei.slime_milk.info", variantSlimeName);
            reg.addIngredientInfo(milkBucket, VanillaTypes.ITEM_STACK, milkInfo);
        }
    }

    /**
     * Per-species info pages — one set per category (Bog / Cave / Geode / Tide
     * / Infernal / Void). Each species gets info on its frog spawn egg,
     * tadpole spawn egg, parent species spawn egg, Primed Frog Egg block, and
     * the species-themed Frog Egg bottle.
     */
    private static void addSpeciesInfoPages(IRecipeRegistration reg) {
        for (Category cat : Category.values()) {
            Component frogName = Component.translatable(
                "entity.productivefrogs.resource_frog." + cat.id());
            Component speciesSlimeName = Component.translatable(
                "entity.productivefrogs." + cat.id() + "_slime");

            // Frog spawn egg → eats <Species> Slimes, drops variant Froglight
            Component frogInfo = Component.translatable(
                "productivefrogs.jei.frog.info", speciesSlimeName, frogName);
            ItemStack frogEgg = new ItemStack(PFItems.RESOURCE_FROG_SPAWN_EGGS.get(cat).get());
            reg.addIngredientInfo(frogEgg, VanillaTypes.ITEM_STACK, frogInfo);

            // Tadpole spawn egg → matures into <Species> Frog
            Component tadpoleInfo = Component.translatable(
                "productivefrogs.jei.tadpole.info", frogName);
            ItemStack tadpoleEgg = new ItemStack(PFItems.RESOURCE_TADPOLE_SPAWN_EGGS.get(cat).get());
            reg.addIngredientInfo(tadpoleEgg, VanillaTypes.ITEM_STACK, tadpoleInfo);

            // Frog Egg bottle (primed with category) → place on water to hatch
            Component bottleInfo = Component.translatable(
                "productivefrogs.jei.frog_egg.info", frogName);
            ItemStack bottle = new ItemStack(PFItems.FROG_EGG.get());
            bottle.set(PFDataComponents.CONTAINED_CATEGORY.get(), cat);
            reg.addIngredientInfo(bottle, VanillaTypes.ITEM_STACK, bottleInfo);

            // Primed Frog Egg block (in-hand BlockItem)
            ItemStack primedEgg = new ItemStack(PFItems.PRIMED_FROG_EGG_ITEMS.get(cat).get());
            reg.addIngredientInfo(primedEgg, VanillaTypes.ITEM_STACK,
                Component.translatable("productivefrogs.jei.primed_egg.info", frogName));
        }

        // Parent species spawn eggs — one fixed-text info per species (lang
        // entries enumerate the biomes).
        addSpawnEggInfo(reg, PFItems.BOG_SLIME_SPAWN_EGG.get(),      "bog");
        addSpawnEggInfo(reg, PFItems.CAVE_SLIME_SPAWN_EGG.get(),     "cave");
        addSpawnEggInfo(reg, PFItems.GEODE_SLIME_SPAWN_EGG.get(),    "geode");
        addSpawnEggInfo(reg, PFItems.TIDE_SLIME_SPAWN_EGG.get(),     "tide");
        addSpawnEggInfo(reg, PFItems.INFERNAL_SLIME_SPAWN_EGG.get(), "infernal");
        addSpawnEggInfo(reg, PFItems.VOID_SLIME_SPAWN_EGG.get(),     "void");
    }

    private static void addSpawnEggInfo(IRecipeRegistration reg, net.minecraft.world.item.Item egg, String speciesId) {
        reg.addIngredientInfo(
            new ItemStack(egg),
            VanillaTypes.ITEM_STACK,
            Component.translatable("productivefrogs.jei.parent_species." + speciesId + ".info"));
    }

    /**
     * Static-text info pages for items that don't enumerate over categories
     * or variants — the unprimed Frog Egg bottle and the Slime Milker block.
     */
    private static void addStaticInfoPages(IRecipeRegistration reg) {
        // Unprimed Frog Egg bottle — explain how it gets primed
        ItemStack unprimedBottle = new ItemStack(PFItems.FROG_EGG.get());
        reg.addIngredientInfo(unprimedBottle, VanillaTypes.ITEM_STACK,
            Component.translatable("productivefrogs.jei.empty_frog_egg.info"));

        // Slime Milker
        reg.addIngredientInfo(
            new ItemStack(PFBlocks.SLIME_MILKER.get().asItem()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("productivefrogs.jei.slime_milker.info"));

        // Empty Slime Bucket
        reg.addIngredientInfo(
            new ItemStack(PFItems.SLIME_BUCKET.get()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("productivefrogs.jei.empty_slime_bucket.info"));

        // Empty Resource Tadpole Bucket
        reg.addIngredientInfo(
            new ItemStack(PFItems.RESOURCE_TADPOLE_BUCKET.get()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("productivefrogs.jei.empty_tadpole_bucket.info"));
    }
}
