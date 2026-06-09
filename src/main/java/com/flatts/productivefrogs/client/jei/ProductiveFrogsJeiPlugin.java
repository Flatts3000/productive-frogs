package com.flatts.productivefrogs.client.jei;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItemTags;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFRegistries;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * JEI plugin for Productive Frogs. Surfaces three things:
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
 *   <li><b>Recipe categories</b> for the two custom-block transforms that have
 *       no vanilla-recipe analogue: the {@link SpawneryRecipeCategory} (glass
 *       bottle + primer -> bottled frogspawn) and the
 *       {@link SlimeMilkerRecipeCategory} (variant Slime Bucket -> matching
 *       Slime Milk bucket). Both enumerate the same datapack data the info
 *       pages do, so pack tag overrides and new variants display automatically.</li>
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

    /**
     * Whether the Spawnery is enabled. Fails closed (disabled) until the config
     * spec loads, so every Spawnery JEI surface - ingredient visibility, the
     * recipe category + catalyst, and the info page - shares one gate.
     */
    private static boolean spawneryEnabled() {
        return PFConfig.SPEC.isLoaded() && PFConfig.SPAWNERY_ENABLED.get();
    }

    /** Whether the Slime Milker is enabled (#196). Default-on: fail OPEN until config loads. */
    private static boolean slimeMilkerEnabled() {
        return !PFConfig.SPEC.isLoaded() || PFConfig.SLIME_MILKER_ENABLED.get();
    }

    /** Whether the Slime Churn is enabled (#196). Default-on: fail OPEN until config loads. */
    private static boolean slimeChurnEnabled() {
        return !PFConfig.SPEC.isLoaded() || PFConfig.SLIME_CHURN_ENABLED.get();
    }

    /** Whether the Froglight Crucible is enabled (#196). Default-on: fail OPEN until config loads. */
    private static boolean crucibleEnabled() {
        return !PFConfig.SPEC.isLoaded() || PFConfig.CRUCIBLE_ENABLED.get();
    }

    /** Whether the Casting Mold is enabled (#196). Default-on: fail OPEN until config loads. */
    private static boolean castingMoldEnabled() {
        return !PFConfig.SPEC.isLoaded() || PFConfig.CASTING_MOLD_ENABLED.get();
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // Hide any config-disabled appliance from the JEI ingredient list so JEI
        // doesn't surface a block players can't obtain. The recipe condition +
        // creative-tab guard cover crafting/creative; this covers the JEI sidebar.
        // No-op (and harmless) if an item is already absent.
        java.util.List<ItemStack> hidden = new java.util.ArrayList<>();
        if (!spawneryEnabled()) {
            hidden.add(new ItemStack(PFItems.SPAWNERY.get()));
        }
        if (!slimeMilkerEnabled()) {
            hidden.add(new ItemStack(PFItems.SLIME_MILKER.get()));
        }
        if (!slimeChurnEnabled()) {
            hidden.add(new ItemStack(PFItems.SLIME_CHURN.get()));
        }
        if (!crucibleEnabled()) {
            hidden.add(new ItemStack(PFItems.CRUCIBLE.get()));
        }
        if (!castingMoldEnabled()) {
            hidden.add(new ItemStack(PFItems.CASTING_MOLD.get()));
        }
        // Per-catalyst hide (#201): each accessor ANDs the catalysts master, so a
        // disabled master hides all four and an individual flag hides just its own.
        if (!PFConfig.catalystCountEnabled()) {
            hidden.add(new ItemStack(PFItems.COUNT_CATALYST.get()));
        }
        if (!PFConfig.catalystSpeedEnabled()) {
            hidden.add(new ItemStack(PFItems.SPEED_CATALYST.get()));
        }
        if (!PFConfig.catalystQuantityEnabled()) {
            hidden.add(new ItemStack(PFItems.QUANTITY_CATALYST.get()));
        }
        if (!PFConfig.catalystInfiniteEnabled()) {
            hidden.add(new ItemStack(PFItems.INFINITE_CATALYST.get()));
        }
        if (!hidden.isEmpty()) {
            jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        }
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
            new SlimeMilkerRecipeCategory(guiHelper),
            new SlimeChurnRecipeCategory(guiHelper),
            new SpawneryRecipeCategory(guiHelper),
            new CrucibleHeatCategory(guiHelper),
            new CrucibleMeltCategory(guiHelper),
            new MoldCastingCategory(guiHelper));
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // JEI 19.27 (1.21.1) has no registerFromDataComponentTypes shortcut —
        // implement the subtype interpreter manually per item.

        // Buckets — subtype by ONLY the Category + Variant inside BUCKET_ENTITY_DATA,
        // not the whole tag. A real captured bucket also carries the vanilla
        // mob-bucket keys (Health, NoAI, ... via saveDefaultDataToBucketTag), so
        // keying on the full tag string would give it a different JEI subtype than
        // the minimal {Category,Variant} stacks the creative tab, info pages, and
        // recipe categories build — pressing U/R on a captured bucket would then
        // fail to find its recipes (e.g. the Slime Milker). Keying on just these two
        // collapses every same-variant bucket to one stable entry.
        ISubtypeInterpreter<ItemStack> bucketInterp = new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext ctx) {
                return bucketSubtypeKey(stack);
            }
            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext ctx) {
                return bucketSubtypeKey(stack);
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
        // Slime Milk buckets are per-variant distinct items (v1.8) - no subtype
        // interpreter needed; each <variant>_slime_milk_bucket is already its own
        // JEI entry.

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

    /**
     * Stable JEI subtype key for a Slime / Resource Tadpole bucket: the Category
     * and Variant strings from {@code BUCKET_ENTITY_DATA}, ignoring the vanilla
     * mob-bucket NBT (Health, NoAI, ...) a real captured bucket also carries.
     * Absent keys read as empty (a category-only slime or a tadpole bucket has no
     * Variant), so every stack still gets a deterministic key.
     */
    private static String bucketSubtypeKey(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            return "";
        }
        CompoundTag tag = data.copyTag();
        return tag.getString("Category") + "|" + tag.getString("Variant");
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

        if (slimeMilkerEnabled()) {
            addMilkerRecipes(reg, variants);
        }
        if (slimeChurnEnabled()) {
            addChurnRecipes(reg, variants);
        }
        addSpawneryRecipes(reg);
        // Melt + cast recipes ride their real recipe types, so datapack additions
        // (molten metals, nugget/block casts) surface with no code change. Gated by
        // the Crucible/Mold toggles (#196) so a disabled lane leaves no JEI category.
        if (crucibleEnabled()) {
            addCrucibleHeatEntries(reg, variants);
            reg.addRecipes(CrucibleMeltCategory.TYPE, level.getRecipeManager()
                .getAllRecipesFor(com.flatts.productivefrogs.registry.PFRecipeTypes.CRUCIBLE_MELTING.get()));
        }
        if (castingMoldEnabled()) {
            reg.addRecipes(MoldCastingCategory.TYPE, level.getRecipeManager()
                .getAllRecipesFor(com.flatts.productivefrogs.registry.PFRecipeTypes.MOLD_CASTING.get()));
        }
    }

    /**
     * One JEI entry per Crucible heat source, enumerated from the live
     * (synced) {@code crucible_heat} data map so pack overrides display
     * automatically. Wall variants dedupe through their shared floor item
     * (wall torch -> torch); item-less blocks (lava, fire, soul fire) display
     * via a representative item. The placed Lava Froglight - the one
     * code-side heat rule in {@code CrucibleBlockEntity#heatBelow} - is
     * appended with lava's own value.
     */
    private static void addCrucibleHeatEntries(IRecipeRegistration reg, Registry<SlimeVariant> variants) {
        var blockRegistry = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        java.util.List<CrucibleHeatCategory.Entry> entries = new ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (var e : blockRegistry.getDataMap(
                com.flatts.productivefrogs.registry.PFDataMaps.CRUCIBLE_HEAT).entrySet()) {
            net.minecraft.world.level.block.Block block = blockRegistry.get(e.getKey());
            if (block == null) {
                continue;
            }
            ItemStack display = heatSourceDisplay(block);
            if (display.isEmpty() || !seen.add(display.getItem())) {
                continue;
            }
            entries.add(new CrucibleHeatCategory.Entry(display, e.getValue(), e.getKey().location()));
        }
        // Placed Froglight heat sources ride the variant-keyed froglight_heat
        // data map (lava/blaze/blazing by default) - enumerate it the same way
        // so pack overrides display automatically. Froglight stacks dedupe by
        // variant, not item, so they bypass the `seen` set.
        for (var e : variants.getDataMap(
                com.flatts.productivefrogs.registry.PFDataMaps.FROGLIGHT_HEAT).entrySet()) {
            ItemStack froglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
            froglight.set(PFDataComponents.SLIME_VARIANT.get(), e.getKey().location());
            entries.add(new CrucibleHeatCategory.Entry(froglight, e.getValue(),
                e.getKey().location().withSuffix("_froglight_heat")));
        }
        entries.sort(java.util.Comparator
            .comparingInt(CrucibleHeatCategory.Entry::heat)
            .thenComparing(e -> e.id().toString()));
        reg.addRecipes(CrucibleHeatCategory.TYPE, entries);
    }

    /** Display stack for a heat-source block; item-less blocks get a stand-in. */
    private static ItemStack heatSourceDisplay(net.minecraft.world.level.block.Block block) {
        if (block == net.minecraft.world.level.block.Blocks.LAVA) {
            return new ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET);
        }
        if (block == net.minecraft.world.level.block.Blocks.FIRE
                || block == net.minecraft.world.level.block.Blocks.SOUL_FIRE) {
            return new ItemStack(net.minecraft.world.item.Items.FLINT_AND_STEEL);
        }
        return new ItemStack(block.asItem());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (slimeMilkerEnabled()) {
            registration.addRecipeCatalyst(PFBlocks.SLIME_MILKER.get(), SlimeMilkerRecipeCategory.TYPE);
        }
        if (slimeChurnEnabled()) {
            registration.addRecipeCatalyst(PFBlocks.SLIME_CHURN.get(), SlimeChurnRecipeCategory.TYPE);
        }
        if (crucibleEnabled()) {
            registration.addRecipeCatalyst(PFBlocks.CRUCIBLE.get(), CrucibleHeatCategory.TYPE);
            registration.addRecipeCatalyst(PFBlocks.CRUCIBLE.get(), CrucibleMeltCategory.TYPE);
        }
        if (castingMoldEnabled()) {
            registration.addRecipeCatalyst(PFBlocks.CASTING_MOLD.get(), MoldCastingCategory.TYPE);
        }
        // Spawnery catalyst only when enabled - the block is removed from JEI in
        // onRuntimeAvailable when off, and addSpawneryRecipes adds no recipes then,
        // so registering the catalyst would surface an empty category for a hidden
        // block. Same guard the static info page uses.
        if (spawneryEnabled()) {
            registration.addRecipeCatalyst(PFBlocks.SPAWNERY.get(), SpawneryRecipeCategory.TYPE);
        }
    }

    /**
     * One Slime Milker recipe per shipped SlimeVariant: a variant Slime Bucket
     * converts to the matching variant-stamped Slime Milk bucket. Builds both
     * stacks through the shared {@link PFItems} builders (same as the creative
     * tab + info pages) so a datapack variant produces its recipe with no code
     * change and the bucket NBT shape stays single-sourced.
     */
    private static void addMilkerRecipes(IRecipeRegistration reg, Registry<SlimeVariant> variants) {
        List<SlimeMilkerRecipeCategory.Recipe> recipes = new ArrayList<>();
        for (java.util.Map.Entry<ResourceKey<SlimeVariant>, SlimeVariant> entry : variants.entrySet()) {
            ResourceLocation variantId = entry.getKey().location();
            ItemStack output = PFItems.slimeMilkBucket(variantId);
            // A content-only variant (no per-variant milk fluid minted at mod-init)
            // has no milk bucket - skip it rather than show a broken empty-output recipe.
            if (output.isEmpty()) {
                continue;
            }
            ItemStack input = PFItems.variantSlimeBucket(variantId, entry.getValue().category());
            recipes.add(new SlimeMilkerRecipeCategory.Recipe(input, output));
        }
        reg.addRecipes(SlimeMilkerRecipeCategory.TYPE, recipes);
    }

    /**
     * One Slime Churn recipe per shipped SlimeVariant - the Milker mapping
     * inverted: a variant Slime Milk bucket (plus an empty bucket) converts
     * to the matching captured variant Slime Bucket, returning the spent
     * container. Same registry walk and {@link PFItems} stack builders as
     * {@link #addMilkerRecipes}, so the two categories stay in lockstep.
     */
    private static void addChurnRecipes(IRecipeRegistration reg, Registry<SlimeVariant> variants) {
        List<SlimeChurnRecipeCategory.Recipe> recipes = new ArrayList<>();
        for (java.util.Map.Entry<ResourceKey<SlimeVariant>, SlimeVariant> entry : variants.entrySet()) {
            ResourceLocation variantId = entry.getKey().location();
            ItemStack milkBucket = PFItems.slimeMilkBucket(variantId);
            // No per-variant milk fluid (content-only variant) = nothing the
            // churn could consume - skip, mirroring addMilkerRecipes.
            if (milkBucket.isEmpty()) {
                continue;
            }
            ItemStack slimeBucket = PFItems.variantSlimeBucket(variantId, entry.getValue().category());
            recipes.add(new SlimeChurnRecipeCategory.Recipe(milkBucket, slimeBucket));
        }
        reg.addRecipes(SlimeChurnRecipeCategory.TYPE, recipes);
    }

    /**
     * Spawnery recipes - one per species plus the vanilla case. Each species
     * recipe's primer slot cycles every item in that species'
     * {@code spawnery_primer/<species>} tag, so pack tag overrides display.
     * Gated on {@code spawnery.enabled} to stay consistent with the block being
     * removed from JEI when the appliance is off.
     */
    private static void addSpawneryRecipes(IRecipeRegistration reg) {
        if (!spawneryEnabled()) {
            return;
        }
        List<SpawneryRecipeCategory.Recipe> recipes = new ArrayList<>();
        for (Category cat : Category.values()) {
            List<ItemStack> primers = primerStacks(cat);
            if (primers.isEmpty()) {
                continue; // No primer for this species in the current tags - skip.
            }
            ItemStack egg = new ItemStack(PFItems.FROG_EGG.get());
            egg.set(PFDataComponents.CONTAINED_CATEGORY.get(), cat);
            recipes.add(new SpawneryRecipeCategory.Recipe(primers, egg));
        }
        // Vanilla case: a slime ball primes plain frogspawn (no contained category).
        recipes.add(new SpawneryRecipeCategory.Recipe(
            List.of(new ItemStack(Items.SLIME_BALL)),
            new ItemStack(PFItems.FROG_EGG.get())));
        reg.addRecipes(SpawneryRecipeCategory.TYPE, recipes);
    }

    /** Every item in a species' {@code spawnery_primer/<species>} tag, as stacks. */
    private static List<ItemStack> primerStacks(Category cat) {
        TagKey<Item> tag = PFItemTags.spawneryPrimer(cat);
        return BuiltInRegistries.ITEM.getTag(tag)
            .map(set -> set.stream().map(h -> new ItemStack(h.value())).toList())
            .orElse(List.of());
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
            // Built-in variants have explicit lang keys; a cross-mod / datapack
            // variant (no lang) falls back to a title-cased "<Name> Slime" so the
            // info pages below don't surface the raw translation key.
            Component variantSlimeName = Component.translatableWithFallback(
                "entity.productivefrogs.resource_slime." + variantName,
                com.flatts.productivefrogs.util.VariantNames.titleCase(entry.getKey().location()) + " Slime");
            Component info = Component.translatable(
                "productivefrogs.jei.variant_slime.info", variantSlimeName, frogName);

            // 1. Resource Slime spawn egg (per-variant stack of the single item)
            reg.addIngredientInfo(
                PFItems.resourceSlimeSpawnEgg(entry.getKey().location()),
                VanillaTypes.ITEM_STACK, info);

            // 2. Variant-stamped Slime Bucket
            ItemStack bucket = PFItems.variantSlimeBucket(entry.getKey().location(), variant.category());
            reg.addIngredientInfo(bucket, VanillaTypes.ITEM_STACK, info);

            // 3. Variant-stamped Configurable Froglight
            ItemStack froglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
            froglight.set(PFDataComponents.SLIME_VARIANT.get(), entry.getKey().location());
            Component froglightInfo = Component.translatable(
                "productivefrogs.jei.variant_froglight.info", frogName, variantSlimeName);
            reg.addIngredientInfo(froglight, VanillaTypes.ITEM_STACK, froglightInfo);

            // 4. The variant's own Slime Milk bucket (per-variant, v1.8). A
            // content-only variant with no milk fluid yields EMPTY - JEI rejects an
            // empty ingredient, so only add the info page when a bucket exists.
            ItemStack milkBucket = PFItems.slimeMilkBucket(entry.getKey().location());
            if (!milkBucket.isEmpty()) {
                Component milkInfo = Component.translatable(
                    "productivefrogs.jei.slime_milk.info", variantSlimeName);
                reg.addIngredientInfo(milkBucket, VanillaTypes.ITEM_STACK, milkInfo);
            }
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

        // Slime Milker - only when enabled (removed from JEI in onRuntimeAvailable
        // when off, so an info page for a hidden item would orphan + warn).
        if (slimeMilkerEnabled()) {
            reg.addIngredientInfo(
                new ItemStack(PFBlocks.SLIME_MILKER.get().asItem()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("productivefrogs.jei.slime_milker.info"));
        }

        // Slime Churn (#187) - the Milker's inverse; same enable gate.
        if (slimeChurnEnabled()) {
            reg.addIngredientInfo(
                new ItemStack(PFBlocks.SLIME_CHURN.get().asItem()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("productivefrogs.jei.slime_churn.info"));
        }

        // Terrarium multiblock (#185) - one info page per machine block.
        reg.addIngredientInfo(new ItemStack(PFBlocks.TERRARIUM_CONTROLLER.get().asItem()),
            VanillaTypes.ITEM_STACK, Component.translatable("productivefrogs.jei.terrarium_controller.info"));
        reg.addIngredientInfo(new ItemStack(PFBlocks.SPRINKLER.get().asItem()),
            VanillaTypes.ITEM_STACK, Component.translatable("productivefrogs.jei.sprinkler.info"));
        reg.addIngredientInfo(new ItemStack(PFBlocks.INCUBATOR.get().asItem()),
            VanillaTypes.ITEM_STACK, Component.translatable("productivefrogs.jei.incubator.info"));
        reg.addIngredientInfo(new ItemStack(PFBlocks.HATCH.get().asItem()),
            VanillaTypes.ITEM_STACK, Component.translatable("productivefrogs.jei.hatch.info"));

        // Spawnery — only when enabled; when off it's removed from JEI entirely
        // in onRuntimeAvailable, so adding an info page for a hidden item would be
        // pointless (and JEI warns about info for an absent ingredient).
        if (spawneryEnabled()) {
            reg.addIngredientInfo(
                new ItemStack(PFBlocks.SPAWNERY.get().asItem()),
                VanillaTypes.ITEM_STACK,
                Component.translatable("productivefrogs.jei.spawnery.info"));
        }

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

        // Slime Milk catalysts - each only when its own flag is enabled (#201).
        // The accessors AND the catalysts master, so an off master drops all four
        // and an individual flag drops just its own info page (a page for a hidden
        // item would orphan + warn, mirroring the Spawnery guard above).
        if (PFConfig.catalystCountEnabled()) {
            reg.addIngredientInfo(new ItemStack(PFItems.COUNT_CATALYST.get()),
                VanillaTypes.ITEM_STACK, Component.translatable("productivefrogs.jei.count_catalyst.info"));
        }
        if (PFConfig.catalystSpeedEnabled()) {
            reg.addIngredientInfo(new ItemStack(PFItems.SPEED_CATALYST.get()),
                VanillaTypes.ITEM_STACK, Component.translatable("productivefrogs.jei.speed_catalyst.info"));
        }
        if (PFConfig.catalystQuantityEnabled()) {
            reg.addIngredientInfo(new ItemStack(PFItems.QUANTITY_CATALYST.get()),
                VanillaTypes.ITEM_STACK, Component.translatable("productivefrogs.jei.quantity_catalyst.info"));
        }
        if (PFConfig.catalystInfiniteEnabled()) {
            reg.addIngredientInfo(new ItemStack(PFItems.INFINITE_CATALYST.get()),
                VanillaTypes.ITEM_STACK, Component.translatable("productivefrogs.jei.infinite_catalyst.info"));
        }
    }
}
