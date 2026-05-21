package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.item.FrogEggItem;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.CustomData;
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
