package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.item.ConfigurableFroglightItem;
import com.flatts.productivefrogs.content.item.FrogEggItem;
import com.flatts.productivefrogs.content.item.ResourceSlimeSpawnEggItem;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.content.item.SlimeBucketItem;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import com.flatts.productivefrogs.data.Category;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

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
        props -> new FrogEggItem(props.stacksTo(1))
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
    public static final DeferredItem<SlimeBucketItem> SLIME_BUCKET = ITEMS.registerItem(
        "slime_bucket",
        props -> new SlimeBucketItem(
            PFEntities.RESOURCE_SLIME.get(),
            Fluids.WATER,
            SoundEvents.BUCKET_EMPTY_FISH,
            props.stacksTo(1).component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY)
        )
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
            props.stacksTo(1).component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY)
        )
    );

    /**
     * Per-category BlockItems for the six Primed Frog Egg blocks. Each pairs
     * cleanly with its dedicated block ID — vanilla's Block↔Item bijection
     * applies (pick-block, drops, asItem() all do the right thing).
     */
    public static final Map<Category, DeferredItem<BlockItem>> PRIMED_FROG_EGG_ITEMS = buildPrimedEggItems();

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
     * The single Resource Slime spawn egg. One registered item whose variant
     * identity lives in the {@code SLIME_VARIANT} data component (see
     * {@link ResourceSlimeSpawnEggItem}). Per-variant stacks are built by
     * {@link #resourceSlimeSpawnEgg(ResourceLocation)}; the creative tab + JEI
     * enumerate variants from the {@code SLIME_VARIANT} datapack registry, so
     * adding a variant needs no spawn-egg Java edit (CR-9).
     *
     * <p>Replaced the pre-v1.1 per-variant spawn-egg item IDs
     * ({@code iron_slime_spawn_egg}, ...) and their hardcoded variant table.
     * Default ctor colours fall back to BOG; the real per-variant tint comes
     * from the registered item-colour handler reading {@code SLIME_VARIANT}.
     */
    public static final DeferredItem<ResourceSlimeSpawnEggItem> RESOURCE_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "resource_slime_spawn_egg",
        props -> new ResourceSlimeSpawnEggItem(
            PFEntities.RESOURCE_SLIME.get(),
            Category.BOG.tintRgb(), darker(Category.BOG.tintRgb()), props)
    );

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
        // 1.21.1: no Item.Properties.useBlockDescriptionPrefix(); ConfigurableFroglightItem
        // overrides getDescriptionId itself to fall through to the block translation key.
        props -> new ConfigurableFroglightItem(PFBlocks.CONFIGURABLE_FROGLIGHT.get(), props)
    );

    /**
     * Cave Slime spawn egg — vanilla parent species, not category-themed
     * (which is why it isn't in a per-category Map like the frog/tadpole/slime
     * spawn eggs). Single entry, no preset NBT beyond the standard
     * {@code ENTITY_DATA} for the entity type.
     */
    public static final DeferredItem<SpawnEggItem> CAVE_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "cave_slime_spawn_egg",
        props -> new SpawnEggItem(PFEntities.CAVE_SLIME.get(),
            Category.CAVE.tintRgb(), darker(Category.CAVE.tintRgb()), props)
    );

    /** Geode Slime spawn egg — GEODE parent species. Mirrors Cave Slime. */
    public static final DeferredItem<SpawnEggItem> GEODE_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "geode_slime_spawn_egg",
        props -> new SpawnEggItem(PFEntities.GEODE_SLIME.get(),
            Category.GEODE.tintRgb(), darker(Category.GEODE.tintRgb()), props)
    );

    /** Tide Slime spawn egg — TIDE parent species. Mirrors Cave Slime. */
    public static final DeferredItem<SpawnEggItem> TIDE_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "tide_slime_spawn_egg",
        props -> new SpawnEggItem(PFEntities.TIDE_SLIME.get(),
            Category.TIDE.tintRgb(), darker(Category.TIDE.tintRgb()), props)
    );

    /** Void Slime spawn egg — VOID parent species. Mirrors Cave Slime. */
    public static final DeferredItem<SpawnEggItem> VOID_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "void_slime_spawn_egg",
        props -> new SpawnEggItem(PFEntities.VOID_SLIME.get(),
            Category.VOID.tintRgb(), darker(Category.VOID.tintRgb()), props)
    );

    /** Bog Slime spawn egg — BOG parent species. V1.5 addition. */
    public static final DeferredItem<SpawnEggItem> BOG_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "bog_slime_spawn_egg",
        props -> new SpawnEggItem(PFEntities.BOG_SLIME.get(),
            Category.BOG.tintRgb(), darker(Category.BOG.tintRgb()), props)
    );

    /** Infernal Slime spawn egg — INFERNAL parent species. V1.5 addition. */
    public static final DeferredItem<SpawnEggItem> INFERNAL_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "infernal_slime_spawn_egg",
        props -> new SpawnEggItem(PFEntities.INFERNAL_SLIME.get(),
            Category.INFERNAL.tintRgb(), darker(Category.INFERNAL.tintRgb()), props)
    );

    /**
     * Darken an RGB triple by ~30% for use as the spawn egg's secondary (highlight)
     * colour. 1.21.1 SpawnEggItem renders a two-tone overlay; the secondary is
     * the spotted-blob colour, so a darker shade of the primary reads as
     * a darker variant of the parent's tint.
     */
    private static int darker(int rgb) {
        int r = ((rgb >> 16) & 0xFF) * 70 / 100;
        int g = ((rgb >>  8) & 0xFF) * 70 / 100;
        int b =  (rgb        & 0xFF) * 70 / 100;
        return (r << 16) | (g << 8) | b;
    }

    /**
     * The single Slime Milk bucket. Variant rides in the {@code SLIME_VARIANT}
     * data component (see {@link SlimeMilkBucketItem}); collapsed from the former
     * per-variant {@code <variant>_slime_milk_bucket} items so a datapack-added
     * variant gets milk with no Java edit. Wraps the one {@code slime_milk}
     * source fluid; stack size 1; leaves an empty bucket as a recipe remainder.
     * The {@link com.flatts.productivefrogs.content.block.SlimeMilkerBlock}
     * consumes a Slime Bucket and outputs this bucket stamped with the input's
     * variant.
     */
    public static final DeferredItem<SlimeMilkBucketItem> SLIME_MILK_BUCKET = ITEMS.registerItem(
        "slime_milk_bucket",
        props -> new SlimeMilkBucketItem(
            PFFluids.SLIME_MILK_SOURCE.get(),
            props.stacksTo(1).craftRemainder(Items.BUCKET))
    );

    /**
     * Slime Milker BlockItem — places {@link PFBlocks#SLIME_MILKER}. The block
     * is the V1 production keystone (right-click with slime bucket → milk
     * bucket out). See {@link com.flatts.productivefrogs.content.block.SlimeMilkerBlock}.
     */
    public static final DeferredItem<BlockItem> SLIME_MILKER = ITEMS.registerSimpleBlockItem(
        "slime_milker",
        PFBlocks.SLIME_MILKER,
        new Item.Properties()
    );

    private static Map<Category, DeferredItem<BlockItem>> buildPrimedEggItems() {
        EnumMap<Category, DeferredItem<BlockItem>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            // PlaceOnWaterBlockItem mirrors vanilla Items.FROGSPAWN — the
            // BlockItem raytraces SOURCE_ONLY fluid so right-clicking the
            // surface of a water source places the egg ABOVE the water
            // (rather than the default BlockItem flow which tries to REPLACE
            // the water block and then fails canSurvive). Without this, the
            // primed egg block items can't be placed at all.
            Category catCopy = cat;
            map.put(cat, ITEMS.registerItem(
                cat.primedEggItemName(),
                props -> new PlaceOnWaterBlockItem(
                    PFBlocks.PRIMED_FROG_EGGS.get(catCopy).get(), props)
            ));
        }
        return map;
    }


    @SuppressWarnings("unchecked")
    private static Map<Category, DeferredItem<SpawnEggItem>> buildSpawnEggs(
            String entitySuffix,
            Supplier<EntityType<?>> entityTypeSupplier) {
        EnumMap<Category, DeferredItem<SpawnEggItem>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            String name = cat.id() + "_" + entitySuffix + "_spawn_egg";
            int primary = cat.tintRgb();
            int secondary = darker(primary);
            // 1.21.1 NeoForge has no Supplier<Properties> registerItem overload —
            // do the EntityType.get() lookup inside the Function lambda, which runs
            // at registry-build time (post-PFEntities binding).
            map.put(cat, ITEMS.registerItem(
                name,
                props -> {
                    EntityType<?> type = entityTypeSupplier.get();
                    return new SpawnEggItem(
                        (EntityType<? extends Mob>) type,
                        primary, secondary,
                        applySpawnEggProps(props, type, cat));
                }
            ));
        }
        return map;
    }

    private static Item.Properties applySpawnEggProps(Item.Properties props, EntityType<?> type, Category category) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        nbt.putString("Category", category.name());
        return props
            .component(DataComponents.ENTITY_DATA, CustomData.of(nbt))
            .component(PFDataComponents.CONTAINED_CATEGORY.get(), category);
    }

    /**
     * Build a per-variant stack of the single {@link #RESOURCE_SLIME_SPAWN_EGG}.
     * Stamps the variant id into the {@code SLIME_VARIANT} component (display
     * name + inventory tint + JEI subtype) and into the vanilla
     * {@code ENTITY_DATA} {@code "Variant"} field (so vanilla's spawn path
     * stamps the variant onto the {@code ResourceSlime} it creates; the slime
     * resolves its category from the variant registry on spawn). The entity
     * type id is embedded so vanilla {@code SpawnEggItem} semantics resolve.
     */
    public static ItemStack resourceSlimeSpawnEgg(ResourceLocation variantId) {
        ItemStack stack = new ItemStack(RESOURCE_SLIME_SPAWN_EGG.get());
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(PFEntities.RESOURCE_SLIME.get()).toString());
        nbt.putString("Variant", variantId.toString());
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(nbt));
        stack.set(PFDataComponents.SLIME_VARIANT.get(), variantId);
        return stack;
    }

    private PFItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
