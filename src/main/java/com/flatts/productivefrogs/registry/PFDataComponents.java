package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.StoredEffect;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom DataComponentTypes registered by Productive Frogs.
 *
 * <p>Mirrors how vanilla uses data components for {@code potion_contents},
 * {@code bucket_entity_data}, etc.: a typed payload attached to an ItemStack
 * that the item's behavior reads at runtime.
 */
public final class PFDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ProductiveFrogs.MOD_ID);

    /**
     * The category carried by a Frog Egg bottle (or other typed container) —
     * absent means "contains vanilla frogspawn", present means "contains a
     * primed egg of that category". Used by:
     * <ul>
     *   <li>{@code FrogEggItem.useOn} — which block to place</li>
     *   <li>{@code FrogspawnBottlingHandler} — what to write when bottling a
     *       Primed Frog Egg block</li>
     *   <li>{@code FrogEggItem.getName} — dynamic display name</li>
     *   <li>Client ItemColor handler — content layer tint</li>
     * </ul>
     */
    public static final Supplier<DataComponentType<Category>> CONTAINED_CATEGORY = COMPONENTS.register(
        "contained_category",
        () -> DataComponentType.<Category>builder()
            .persistent(Category.CODEC)
            .networkSynchronized(Category.STREAM_CODEC)
            .build()
    );

    /**
     * SlimeVariant identifier carried by items that represent or originated
     * from a specific variant — currently the {@code configurable_froglight}
     * dropped when a frog eats a variant-locked slime. Mirrors Productive
     * Bees' {@code bee_type} component on {@code configurable_honeycomb} (see
     * {@code docs/productive_bees_analysis.md} §2). The actual SlimeVariant
     * data lives in the datapack registry; this component stores the lookup
     * key only.
     */
    public static final Supplier<DataComponentType<Identifier>> SLIME_VARIANT = COMPONENTS.register(
        "slime_variant",
        () -> DataComponentType.<Identifier>builder()
            .persistent(Identifier.CODEC)
            .networkSynchronized(Identifier.STREAM_CODEC)
            .build()
    );

    /**
     * Item id carried by the Equivalence lane's synthesized content (#253) - the Mimic
     * Slime, its bucket/milk, and the Prismatic Froglight. Unlike {@link #SLIME_VARIANT}
     * (a registered variant id), this stores an arbitrary item id with no variant; the
     * lane's display name and runtime tint are derived from it. In practice mutually
     * exclusive with SLIME_VARIANT (synthesized content carries this; variant content
     * carries that).
     */
    public static final Supplier<DataComponentType<Identifier>> SYNTHESIZED_ITEM = COMPONENTS.register(
        "synthesized_item",
        () -> DataComponentType.<Identifier>builder()
            .persistent(Identifier.CODEC)
            .networkSynchronized(Identifier.STREAM_CODEC)
            .build()
    );

    /**
     * Spawns-remaining counter carried by a Slime Milk bucket filled by
     * re-bucketing a placed source. Lets the depletion progress survive the
     * world -> bucket -> world round-trip: {@code SlimeMilkSourceBlock.pickupBlock}
     * stamps the source's {@code SPAWNS_REMAINING} blockstate value here, and
     * {@code SlimeMilkBucketItem.checkExtraContent} restores it onto the
     * re-placed source. Absent on a freshly-milked bucket (from the Slime Milker),
     * so such a bucket places a full source. Prevents the re-bucket "refill to
     * full" exploit (docs/known_issues.md).
     */
    public static final Supplier<DataComponentType<Integer>> SPAWNS_REMAINING = COMPONENTS.register(
        "spawns_remaining",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build()
    );

    /**
     * Speed-upgrade level carried by a Slime Milk bucket, mirroring
     * {@link #SPAWNS_REMAINING}. A source's spawn-cadence buff (from Speed
     * catalysts dropped into the pool) is stored on its
     * {@code SlimeMilkSourceBlockEntity}; {@code SlimeMilkSourceBlock.pickupBlock}
     * stamps it here so it survives the world -> bucket -> world round-trip, and
     * {@code SlimeMilkBucketItem.checkExtraContent} restores it. Absent (treated
     * as 0) on a freshly-milked bucket. See {@code docs/slime_milk_catalysts.md}.
     */
    public static final Supplier<DataComponentType<Integer>> MILK_SPEED = COMPONENTS.register(
        "milk_speed",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build()
    );

    /**
     * Quantity-upgrade level carried by a Slime Milk bucket (slimes spawned per
     * spawn event, above the base 1). Same round-trip role as {@link #MILK_SPEED}.
     */
    public static final Supplier<DataComponentType<Integer>> MILK_QUANTITY = COMPONENTS.register(
        "milk_quantity",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build()
    );

    /**
     * Infinite-spawns flag carried by a Slime Milk bucket. When set, the placed
     * source never depletes (the Infinite Count catalyst, built from Count
     * catalysts). Same round-trip role as {@link #MILK_SPEED}; absent = false.
     */
    public static final Supplier<DataComponentType<Boolean>> MILK_INFINITE = COMPONENTS.register(
        "milk_infinite",
        () -> DataComponentType.<Boolean>builder()
            .persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL)
            .build()
    );

    /**
     * Spawn <i>capacity</i> (high-water mark) carried by a Slime Milk bucket -
     * the source's total budget, which Count catalysts raise above the base.
     * Tracked separately from {@link #SPAWNS_REMAINING} so the "N / cap" readout
     * (Jade + the bucket tooltip) has a stable denominator as the source depletes,
     * instead of the cap tracking the remaining count downward. Same round-trip
     * role as {@link #MILK_SPEED}; absent = the configured default.
     */
    public static final Supplier<DataComponentType<Integer>> MILK_CAPACITY = COMPONENTS.register(
        "milk_capacity",
        () -> DataComponentType.<Integer>builder()
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build()
    );

    /**
     * The potion effect captured onto a Brewed Froglight (#162). Present =
     * brewed; the {@code enabled} field inside is the on/off toggle. Stamped by
     * {@code FrogTongueDropHandler} when a frog eats an effect-bearing slime,
     * read by {@code ConfigurableFroglightItem} (charm) and copied onto
     * {@code ConfigurableFroglightBlockEntity} (aura) on placement. See
     * {@link StoredEffect}.
     */
    public static final Supplier<DataComponentType<StoredEffect>> STORED_EFFECT = COMPONENTS.register(
        "stored_effect",
        () -> DataComponentType.<StoredEffect>builder()
            .persistent(StoredEffect.CODEC)
            .networkSynchronized(StoredEffect.STREAM_CODEC)
            .build()
    );

    private PFDataComponents() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
