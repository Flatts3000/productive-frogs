package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.item.ConfigurableFroglightItem;
import com.flatts.productivefrogs.content.item.FrogEggItem;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/**
 * Centralized item registry for Productive Frogs.
 *
 * <p>Uses {@code registerItem(name, factory, properties)} and
 * {@code registerSimpleBlockItem(name, blockHolder, properties)} (not the
 * older {@code register(name, Supplier)}) because MC 1.21.x requires the
 * {@code ResourceKey} to be set on Properties before the item constructor
 * runs. The factory form lets DeferredRegister inject the ID and hand the
 * properties to our constructor.
 */
public final class PFItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(ProductiveFrogs.MOD_ID);

    /**
     * The Frog Egg item — a glass bottle filled with frogspawn. Stack size 1.
     * Carries an optional category via the
     * {@code productivefrogs:contained_category} data component:
     * absent = vanilla frogspawn, present = primed egg of that category.
     */
    public static final DeferredItem<FrogEggItem> FROG_EGG = ITEMS.registerItem(
        "frog_egg",
        FrogEggItem::new,
        new Item.Properties().stacksTo(1)
    );

    /**
     * Slime Bucket — the V1 transport mechanism for size-1 Resource Slimes
     * (per {@code docs/slime_sourcing.md}). Vanilla {@link MobBucketItem}
     * handles the actual capture/release flow; the slime's category is
     * preserved via {@code BUCKET_ENTITY_DATA} NBT written/read by
     * {@link com.flatts.productivefrogs.content.entity.ResourceSlime}'s
     * {@code saveToBucketTag} / {@code loadFromBucketTag} overrides.
     *
     * <p>Larger slimes split per vanilla mechanics; players bucket the size-1
     * offspring. {@code mobInteract} on ResourceSlime gates the pickup at
     * size==1.
     */
    public static final DeferredItem<MobBucketItem> SLIME_BUCKET = ITEMS.registerItem(
        "slime_bucket",
        props -> new MobBucketItem(
            PFEntities.RESOURCE_SLIME.get(),
            Fluids.WATER,
            SoundEvents.BUCKET_EMPTY_FISH,
            props
        ),
        new Item.Properties()
            .stacksTo(1)
            .component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY)
    );

    /**
     * Resource Tadpole bucket. Mirrors vanilla {@code tadpole_bucket} but
     * preserves the tadpole's category across bucket-and-release. Display
     * name varies by the stored category.
     */
    public static final DeferredItem<ResourceTadpoleBucketItem> RESOURCE_TADPOLE_BUCKET = ITEMS.registerItem(
        "resource_tadpole_bucket",
        props -> new ResourceTadpoleBucketItem(
            PFEntities.RESOURCE_TADPOLE.get(),
            Fluids.WATER,
            SoundEvents.BUCKET_EMPTY_TADPOLE,
            props
        ),
        new Item.Properties()
            .stacksTo(1)
            .component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY)
    );

    /**
     * Per-category BlockItems for the six Primed Frog Egg blocks. Each pairs
     * cleanly with its dedicated block ID — vanilla's Block↔Item bijection
     * applies (pick-block, drops, asItem() all do the right thing).
     */
    public static final Map<Category, DeferredItem<BlockItem>> PRIMED_FROG_EGG_ITEMS = buildPrimedEggItems();

    /** Per-category BlockItems for the six Resource Froglight blocks. */
    public static final Map<Category, DeferredItem<BlockItem>> RESOURCE_FROGLIGHT_ITEMS = buildResourceFroglightItems();

    /**
     * Per-category spawn eggs for Resource Frogs. Each item carries two default
     * components: {@code ENTITY_DATA} (preset NBT seeding the spawned entity's
     * Category) and {@code CONTAINED_CATEGORY} (drives the inventory tint via
     * the existing {@code productivefrogs:contained_category} ItemTintSource).
     *
     * <p>Vanilla {@link SpawnEggItem}'s {@code BY_ID} map only stores one egg per
     * EntityType (it overwrites on each registration with the same type), so its
     * {@code byId} static lookup will resolve to whichever egg registered last.
     * That's a vanilla-only convenience for chunk-spawn / breeding flows we
     * don't use — per-stack {@code getType(ItemStack)} reads from the stack's
     * ENTITY_DATA component and works correctly for every egg.
     */
    public static final Map<Category, DeferredItem<SpawnEggItem>> RESOURCE_FROG_SPAWN_EGGS = buildSpawnEggs(
        "frog", () -> PFEntities.RESOURCE_FROG.get());

    /** Per-category spawn eggs for Resource Tadpoles. Same shape as the frog eggs above. */
    public static final Map<Category, DeferredItem<SpawnEggItem>> RESOURCE_TADPOLE_SPAWN_EGGS = buildSpawnEggs(
        "tadpole", () -> PFEntities.RESOURCE_TADPOLE.get());

    /**
     * Per-variant spawn eggs for Resource Slimes — one item per SlimeVariant
     * shipped in PR H2's datapack registry. Each preset its variant via
     * {@code ENTITY_DATA} NBT (key "Variant", value the variant's full id)
     * and its broad category via {@code CONTAINED_CATEGORY} so the inventory
     * tint uses the existing category palette. JEI surfaces each as a
     * distinct entry → "Iron Slime Spawn Egg", "Copper Slime Spawn Egg", ...
     *
     * <p>Variants are hardcoded here matching the JSON files in
     * {@code data/productivefrogs/productivefrogs/slime_variant/}. If a future
     * PR makes this list datapack-driven (auto-spawn-eggs from variant
     * registry), it'll need a different registration path since item
     * registration happens at mod-init, before the datapack registry loads.
     */
    public static final Map<String, DeferredItem<SpawnEggItem>> RESOURCE_SLIME_SPAWN_EGGS = buildSlimeVariantSpawnEggs();

    /**
     * Variant-keyed Froglight item — the per-resource production currency,
     * stamped with a {@code SLIME_VARIANT} component when dropped by a frog
     * eating a variant-locked slime (see {@code FrogTongueDropHandler}).
     * Display name and inventory tint resolve at runtime from the
     * registry-loaded {@link com.flatts.productivefrogs.data.SlimeVariant}.
     *
     * <p>Distinct from the six broad-category {@code <cat>_froglight} blocks —
     * those stay as placeable decorative blocks; this item is the per-variant
     * input to (future) smelting recipes that produce the underlying resource.
     */
    public static final DeferredItem<ConfigurableFroglightItem> CONFIGURABLE_FROGLIGHT = ITEMS.registerItem(
        "configurable_froglight",
        ConfigurableFroglightItem::new,
        new Item.Properties()
    );

    /**
     * Cave Slime spawn egg — vanilla parent species, not category-themed
     * (which is why it isn't in a per-category Map like the frog/tadpole/slime
     * spawn eggs). Single entry, no preset NBT beyond the standard
     * {@code ENTITY_DATA} for the entity type.
     */
    public static final DeferredItem<SpawnEggItem> CAVE_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "cave_slime_spawn_egg",
        SpawnEggItem::new,
        () -> new Item.Properties().spawnEgg(PFEntities.CAVE_SLIME.get())
    );

    /** Geode Slime spawn egg — GEM parent species. Mirrors Cave Slime. */
    public static final DeferredItem<SpawnEggItem> GEODE_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "geode_slime_spawn_egg",
        SpawnEggItem::new,
        () -> new Item.Properties().spawnEgg(PFEntities.GEODE_SLIME.get())
    );

    /** Tide Slime spawn egg — AQUATIC parent species. Mirrors Cave Slime. */
    public static final DeferredItem<SpawnEggItem> TIDE_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "tide_slime_spawn_egg",
        SpawnEggItem::new,
        () -> new Item.Properties().spawnEgg(PFEntities.TIDE_SLIME.get())
    );

    private static Map<Category, DeferredItem<BlockItem>> buildPrimedEggItems() {
        EnumMap<Category, DeferredItem<BlockItem>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, ITEMS.registerSimpleBlockItem(
                cat.primedEggItemName(),
                PFBlocks.PRIMED_FROG_EGGS.get(cat),
                new Item.Properties()
            ));
        }
        return map;
    }

    private static Map<Category, DeferredItem<BlockItem>> buildResourceFroglightItems() {
        EnumMap<Category, DeferredItem<BlockItem>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, ITEMS.registerSimpleBlockItem(
                cat.id() + "_froglight",
                PFBlocks.RESOURCE_FROGLIGHTS.get(cat),
                new Item.Properties()
            ));
        }
        return map;
    }

    private static Map<Category, DeferredItem<SpawnEggItem>> buildSpawnEggs(
            String entitySuffix,
            java.util.function.Supplier<EntityType<?>> entityTypeSupplier) {
        EnumMap<Category, DeferredItem<SpawnEggItem>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            String name = cat.id() + "_" + entitySuffix + "_spawn_egg";
            // Use the Supplier<Item.Properties> overload so the EntityType
            // lookup is deferred until registry-build time. The Function
            // overload (Item.Properties) would force entityTypeSupplier.get()
            // at PFItems class-init, when PFEntities holders are still unbound.
            map.put(cat, ITEMS.registerItem(
                name,
                SpawnEggItem::new,
                () -> spawnEggProperties(entityTypeSupplier.get(), cat)
            ));
        }
        return map;
    }

    /**
     * Build properties for a category-locked spawn egg: presets the
     * {@code ENTITY_DATA} NBT with {@code Category=&lt;name&gt;} so the spawned
     * entity's {@code readAdditionalSaveData} picks up the right category, and
     * sets {@code CONTAINED_CATEGORY} so the inventory tint reads it via the
     * existing {@code productivefrogs:contained_category} ItemTintSource.
     */
    private static Item.Properties spawnEggProperties(EntityType<?> type, Category category) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("Category", category.name());
        return new Item.Properties()
            .component(DataComponents.ENTITY_DATA, TypedEntityData.of(type, nbt))
            .component(PFDataComponents.CONTAINED_CATEGORY.get(), category);
    }

    /**
     * One spawn egg per SlimeVariant shipped in PR H2. Each item presets
     * both the variant id (via ENTITY_DATA NBT) and the parent category
     * (for the inventory tint). The variant→category mapping here mirrors
     * the JSON files in {@code data/.../slime_variant/}; if those move, this
     * list moves with them.
     */
    private static Map<String, DeferredItem<SpawnEggItem>> buildSlimeVariantSpawnEggs() {
        record VariantSpec(String name, Category category) {}
        VariantSpec[] variants = new VariantSpec[]{
            new VariantSpec("iron",        Category.METALLIC),
            new VariantSpec("copper",      Category.METALLIC),
            new VariantSpec("gold",        Category.METALLIC),
            new VariantSpec("redstone",    Category.MINERAL),
            new VariantSpec("lapis",       Category.MINERAL),
            new VariantSpec("coal",        Category.MINERAL),
            new VariantSpec("diamond",     Category.GEM),
            new VariantSpec("emerald",     Category.GEM),
            new VariantSpec("prismarine",  Category.AQUATIC),
            new VariantSpec("sponge",      Category.AQUATIC),
            new VariantSpec("magma_cream", Category.INFERNAL),
            new VariantSpec("ender_pearl", Category.ARCANE),
        };
        java.util.LinkedHashMap<String, DeferredItem<SpawnEggItem>> map = new java.util.LinkedHashMap<>();
        for (VariantSpec spec : variants) {
            String itemName = spec.name() + "_slime_spawn_egg";
            map.put(spec.name(), ITEMS.registerItem(
                itemName,
                SpawnEggItem::new,
                () -> slimeVariantSpawnEggProperties(spec.name(), spec.category())
            ));
        }
        return map;
    }

    private static Item.Properties slimeVariantSpawnEggProperties(String variantName, Category category) {
        CompoundTag nbt = new CompoundTag();
        // Variant first; readAdditionalSaveData treats Variant as overriding
        // Category via the registry lookup, but the entity also reads Category
        // as a fast-path / fallback so include both for safety.
        nbt.putString("Variant", ProductiveFrogs.MOD_ID + ":" + variantName);
        nbt.putString("Category", category.name());
        return new Item.Properties()
            .component(DataComponents.ENTITY_DATA, TypedEntityData.of(PFEntities.RESOURCE_SLIME.get(), nbt))
            .component(PFDataComponents.CONTAINED_CATEGORY.get(), category);
    }

    /**
     * Convenience helper for client tint code: pull the category out of a
     * Resource Tadpole Bucket's NBT. Returns null when the bucket is empty
     * or has no stored category.
     */
    @Nullable
    public static Category tadpoleBucketCategory(ItemStack stack) {
        return ResourceTadpoleBucketItem.readCategory(stack);
    }

    private PFItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
