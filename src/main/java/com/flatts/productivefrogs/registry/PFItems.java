package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.item.ConfigurableFroglightItem;
import com.flatts.productivefrogs.content.item.EnderNetItem;
import com.flatts.productivefrogs.content.item.FrogEggItem;
import com.flatts.productivefrogs.content.item.FrogNetItem;
import com.flatts.productivefrogs.content.item.LiquidExperienceBucketItem;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.content.item.MilkCatalystItem;
import com.flatts.productivefrogs.content.item.MimicMilkBucketItem;
import com.flatts.productivefrogs.content.item.MimicSlimeBucketItem;
import com.flatts.productivefrogs.content.item.MobSlurryBucketItem;
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
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Block;
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
     * The Frog Net (#205) - catches a Resource Frog into the item and releases it
     * elsewhere, stats intact. Reusable; mirrors Productive Bees' Sturdy Bee Cage
     * (see {@link com.flatts.productivefrogs.content.item.FrogNetItem}). Stacks to 1:
     * a loaded net is unique, and one net per slot keeps the catch/release UX simple.
     * Config-gated ({@code frog_net.enabled}) at the recipe layer.
     */
    public static final DeferredItem<FrogNetItem> FROG_NET = ITEMS.registerItem(
        "frog_net",
        props -> new FrogNetItem(props.stacksTo(1))
    );

    /**
     * Ender Net (#281, predation Phase 3) - the any-mob counterpart to the Frog
     * Net: catches any living mob whole-entity and releases it elsewhere, or
     * feeds it to the Slurry Press. Boss mobs are catchable (relocation) but the
     * Press refuses them.
     */
    public static final DeferredItem<EnderNetItem> ENDER_NET = ITEMS.registerItem(
        "ender_net",
        props -> new EnderNetItem(props.stacksTo(1))
    );

    /**
     * Froglight Cleaver (#212) - a late-game sword that drops a slime's Froglight
     * when it kills it (handled by {@code FroglightWeaponHandler}), the active-play
     * counterpart to the passive frog loop. A netherite-tier weapon {@link Item} but
     * clearly stronger: +7 attack-damage bonus (12 displayed, vs netherite's 8) and
     * fire-resistant (it's forged from boss froglights + dragon's breath). The
     * harvest behaviour is event-driven. Gated by boss Froglights in its recipe
     * (`froglight_weapon`), so it's pure endgame and the extra power is earned.
     */
    public static final DeferredItem<Item> FROGLIGHT_CLEAVER = ITEMS.registerItem(
        "froglight_cleaver",
        props -> new Item(props.fireResistant().sword(ToolMaterial.NETHERITE, 7, -2.4F))
    );

    /**
     * Sweetslime — the dedicated Resource Frog breeding treat (slime ball +
     * sugar; see {@code docs/frog_breeding.md} D5/D6). A plain item: the
     * breeding behaviour lives entirely in
     * {@link com.flatts.productivefrogs.content.entity.ResourceFrog#isFood}
     * keying off this id, so it needs no custom class. Deliberately NOT the slime ball
     * (which is ubiquitous in a slime farm and would trigger accidental
     * breeding) — feeding two same-species frogs a Sweetslime each is the
     * intentional act that starts a cross.
     */
    public static final DeferredItem<Item> SWEETSLIME = ITEMS.registerItem(
        "sweetslime",
        Item::new
    );

    /**
     * Sweetslimed Lily Pad (#214) - the perch block's item. A {@link PlaceOnWaterBlockItem}
     * like the vanilla lily pad, so it can be placed onto water from the bank. Normally
     * made by right-clicking a placed lily pad with a Sweetslime
     * ({@link com.flatts.productivefrogs.event.LilyPadPerchHandler}); the item exists so
     * the pad drops itself (reusable) and shows in the creative tab. See docs/lily_pad_perch.md.
     */
    public static final DeferredItem<PlaceOnWaterBlockItem> SWEETSLIMED_LILY_PAD = ITEMS.registerItem(
        "sweetslimed_lily_pad",
        props -> new PlaceOnWaterBlockItem(PFBlocks.SWEETSLIMED_LILY_PAD.get(), props.useBlockDescriptionPrefix())
    );

    /**
     * Princess's Kiss (#216) - a rare Ender Dragon drop. Right-click a frog to turn
     * it into a villager (the Frog Prince), a timed conversion like the zombie cure.
     * Behaviour lives in {@link com.flatts.productivefrogs.content.item.PrincessKissItem}
     * + {@code PrincessKissHandler}. Stacks to 16 (it's a consumable trophy).
     */
    public static final DeferredItem<Item> PRINCESS_KISS = ITEMS.registerItem(
        "princess_kiss",
        props -> new com.flatts.productivefrogs.content.item.PrincessKissItem(props.stacksTo(16))
    );

    /**
     * Frog legs (#194) - the death payoff for killing a frog, a renewable meat on a
     * skyblock where animals are scarce. Raw is chicken-tier; cooked is a step up
     * (cooked-chicken values). Plain {@link Item}s with {@link FoodProperties}; the
     * drop is handled by {@code FrogLegDropHandler} and cooking by the smelting /
     * smoking / campfire recipes, all gated by {@code frog_legs.enabled}.
     */
    public static final FoodProperties RAW_FROG_LEGS_FOOD =
        new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build();
    public static final FoodProperties COOKED_FROG_LEGS_FOOD =
        new FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build();

    public static final DeferredItem<Item> RAW_FROG_LEGS = ITEMS.registerItem(
        "raw_frog_legs",
        props -> new Item(props.food(RAW_FROG_LEGS_FOOD))
    );
    public static final DeferredItem<Item> COOKED_FROG_LEGS = ITEMS.registerItem(
        "cooked_frog_legs",
        props -> new Item(props.food(COOKED_FROG_LEGS_FOOD))
    );

    /**
     * Frog Legs Soup (#217) - the bowl-meal step above Cooked Frog Legs, the
     * mod's rabbit-stew analogue. Stacks to 1 and returns an empty bowl on eat
     * via {@code usingConvertsTo} (the 1.21.1 way; the old {@code BowlFoodItem}
     * class is gone). Stew-tier food. Crafted from Cooked Frog Legs; shares the
     * {@code frog_legs} config gate.
     */
    public static final FoodProperties FROG_LEGS_SOUP_FOOD =
        new FoodProperties.Builder().nutrition(10).saturationModifier(0.6F).build();

    // 26.1: the bowl "converts to" remainder moved off FoodProperties.Builder onto
    // Item.Properties.usingConvertsTo(Item).
    public static final DeferredItem<Item> FROG_LEGS_SOUP = ITEMS.registerItem(
        "frog_legs_soup",
        props -> new Item(props.stacksTo(1).food(FROG_LEGS_SOUP_FOOD).usingConvertsTo(Items.BOWL))
    );

    /**
     * The four Slime Milk catalysts - hand-crafted items dropped into a placed
     * Slime Milk source to buff it (the early-game stopgap toward lower-friction
     * production; see {@code docs/slime_milk_catalysts.md}). All plain
     * {@link Item}s: the apply logic lives in {@code SlimeMilkSourceBlock} +
     * {@code SlimeMilkSourceBlockEntity}, keyed off
     * {@link com.flatts.productivefrogs.content.item.MilkCatalyst}, so the items
     * carry no behaviour themselves. Config-gated ({@code slime_milk_catalysts.enabled})
     * at the recipe layer, like the Spawnery.
     */
    public static final DeferredItem<MilkCatalystItem> COUNT_CATALYST = ITEMS.registerItem(
        "count_catalyst", props -> new MilkCatalystItem(MilkCatalyst.COUNT, props));
    public static final DeferredItem<MilkCatalystItem> SPEED_CATALYST = ITEMS.registerItem(
        "speed_catalyst", props -> new MilkCatalystItem(MilkCatalyst.SPEED, props));
    public static final DeferredItem<MilkCatalystItem> QUANTITY_CATALYST = ITEMS.registerItem(
        "quantity_catalyst", props -> new MilkCatalystItem(MilkCatalyst.QUANTITY, props));
    public static final DeferredItem<MilkCatalystItem> INFINITE_CATALYST = ITEMS.registerItem(
        "infinite_catalyst", props -> new MilkCatalystItem(MilkCatalyst.INFINITE, props));

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
     * Mimic Slime Bucket (#253) - the Equivalence lane's captured synthesized
     * slime. Same MobBucketItem round-trip as {@link #SLIME_BUCKET}; carries the
     * synthesized item id as a top-level component (runtime tint + name) and in
     * BUCKET_ENTITY_DATA (respawn). Produced by the Alembic; releasable by hand.
     */
    public static final DeferredItem<MimicSlimeBucketItem> MIMIC_SLIME_BUCKET = ITEMS.registerItem(
        "mimic_slime_bucket",
        props -> new MimicSlimeBucketItem(
            PFEntities.MIMIC_SLIME.get(),
            Fluids.WATER,
            SoundEvents.BUCKET_EMPTY_FISH,
            props.stacksTo(1).component(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY)
        )
    );

    /**
     * Mimic Milk Bucket (#253) - the EE lane's milk. A fluid bucket placing the
     * single Mimic Milk source; carries the synthesized item id as a top-level
     * component (tint + name) and writes it to the placed source's BE.
     */
    public static final DeferredItem<MimicMilkBucketItem> MIMIC_MILK_BUCKET = ITEMS.registerItem(
        "mimic_slime_milk_bucket",
        props -> new MimicMilkBucketItem(
            PFFluids.MIMIC_MILK.get(),
            props.stacksTo(1).craftRemainder(Items.BUCKET)
        )
    );

    /**
     * The single Slime Milk bucket (26.1 R-1) - places the single Slime Milk source
     * block; carries the variant as a top-level {@code SLIME_VARIANT} component (tint
     * + name) and writes it to the placed source's BE. Replaces the v1.8 per-variant
     * milk buckets ({@code PFVariantMilk}, deleted). Mint variant-stamped stacks via
     * {@link SlimeMilkBucketItem#forVariant}. See {@code docs/port_mc_26_1_reimplementation.md}.
     */
    public static final DeferredItem<SlimeMilkBucketItem> SLIME_MILK_BUCKET = ITEMS.registerItem(
        "slime_milk_bucket",
        props -> new SlimeMilkBucketItem(
            PFFluids.SLIME_MILK.get(),
            props.stacksTo(1).craftRemainder(Items.BUCKET)
        )
    );

    /**
     * Liquid Experience bucket (#281 Phase 2). Right-click drinks it - the player
     * absorbs exactly 50 XP points (one bucket at the {@code c:experience}
     * 20 mB/point standard) and keeps the empty bucket. Never places a fluid
     * block (Liquid Experience has none); tanks/pipes move it via
     * {@code Capabilities.Fluid.ITEM} (wired in {@code PFModBusEvents}).
     */
    public static final DeferredItem<LiquidExperienceBucketItem> LIQUID_EXPERIENCE_BUCKET = ITEMS.registerItem(
        "liquid_experience_bucket",
        props -> new LiquidExperienceBucketItem(
            PFFluids.LIQUID_EXPERIENCE.get(),
            props.stacksTo(1).craftRemainder(Items.BUCKET)
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
     * Midas spawn egg (Equivalence lane, #253). Midas is a {@code midas}-flagged
     * ResourceFrog (not a Category), so it's outside {@link #buildSpawnEggs}; this
     * one-off egg stamps {@code Midas:true} (and the VOID carrier category) into
     * ENTITY_DATA, which {@code ResourceFrog.readAdditionalSaveData} reads on spawn.
     * Gold colours. The normal acquisition is still Kiss-priming; this is the
     * creative/testing counterpart, hidden when the lane is disabled.
     */
    public static final DeferredItem<SpawnEggItem> MIDAS_FROG_SPAWN_EGG = ITEMS.registerItem(
        "midas_frog_spawn_egg",
        props -> {
            EntityType<?> type = PFEntities.RESOURCE_FROG.get();
            CompoundTag nbt = new CompoundTag();
            nbt.putString("Kind", com.flatts.productivefrogs.data.FrogKind.MIDAS.id());
            return new SpawnEggItem(props.component(DataComponents.ENTITY_DATA, TypedEntityData.of(type, nbt)));
        });

    /**
     * Midas <i>tadpole</i> spawn egg (#253) - the tadpole counterpart of
     * {@link #MIDAS_FROG_SPAWN_EGG}, for spawn-egg parity with the six species
     * (each has a frog + tadpole egg). Stamps {@code Midas:true} into a Resource
     * Tadpole's ENTITY_DATA; it matures into a Midas frog.
     */
    public static final DeferredItem<SpawnEggItem> MIDAS_TADPOLE_SPAWN_EGG = ITEMS.registerItem(
        "midas_tadpole_spawn_egg",
        props -> {
            EntityType<?> type = PFEntities.RESOURCE_TADPOLE.get();
            CompoundTag nbt = new CompoundTag();
            nbt.putString("Kind", com.flatts.productivefrogs.data.FrogKind.MIDAS.id());
            return new SpawnEggItem(props.component(DataComponents.ENTITY_DATA, TypedEntityData.of(type, nbt)));
        });

    /**
     * Predator spawn eggs (#281) - one frog + one tadpole egg per predator kind
     * (Prowler / Cinder / Gulper / Rift), the creative/testing counterpart to
     * the breeding crosses (the survival acquisition). Each stamps the kind id
     * into ENTITY_DATA ({@code Kind}), which the entities' readAdditionalSaveData
     * resolves via {@code FrogKind.readFrom}.
     */
    public static final Map<com.flatts.productivefrogs.data.FrogKind, DeferredItem<SpawnEggItem>>
        PREDATOR_FROG_SPAWN_EGGS = buildKindSpawnEggs("frog", () -> PFEntities.RESOURCE_FROG.get(),
            java.util.List.of(com.flatts.productivefrogs.data.FrogKind.Predator.values()));

    /** Tadpole counterparts of {@link #PREDATOR_FROG_SPAWN_EGGS}. */
    public static final Map<com.flatts.productivefrogs.data.FrogKind, DeferredItem<SpawnEggItem>>
        PREDATOR_TADPOLE_SPAWN_EGGS = buildKindSpawnEggs("tadpole", () -> PFEntities.RESOURCE_TADPOLE.get(),
            java.util.List.of(com.flatts.productivefrogs.data.FrogKind.Predator.values()));

    /** Apex frog spawn eggs (#281 Phase 4) - one per boss tier kind, Kind NBT baked. */
    public static final Map<com.flatts.productivefrogs.data.FrogKind, DeferredItem<SpawnEggItem>>
        APEX_FROG_SPAWN_EGGS = buildKindSpawnEggs("frog", () -> PFEntities.RESOURCE_FROG.get(),
            java.util.List.of(com.flatts.productivefrogs.data.FrogKind.Apex.values()));

    /** Tadpole counterparts of {@link #APEX_FROG_SPAWN_EGGS}. */
    public static final Map<com.flatts.productivefrogs.data.FrogKind, DeferredItem<SpawnEggItem>>
        APEX_TADPOLE_SPAWN_EGGS = buildKindSpawnEggs("tadpole", () -> PFEntities.RESOURCE_TADPOLE.get(),
            java.util.List.of(com.flatts.productivefrogs.data.FrogKind.Apex.values()));

    /**
     * Kind-keyed spawn eggs (predators, apex - any kind whose eggs are one item
     * per kind). Item id = {@code <nameSuffix>_<noun>_spawn_egg}; the Kind rides
     * baked ENTITY_DATA NBT, the same dialect the entity save reads.
     */
    private static Map<com.flatts.productivefrogs.data.FrogKind, DeferredItem<SpawnEggItem>>
            buildKindSpawnEggs(String noun, java.util.function.Supplier<EntityType<?>> typeSupplier,
                java.util.List<? extends com.flatts.productivefrogs.data.FrogKind> kinds) {
        Map<com.flatts.productivefrogs.data.FrogKind, DeferredItem<SpawnEggItem>> eggs =
            new LinkedHashMap<>();
        for (com.flatts.productivefrogs.data.FrogKind kind : kinds) {
            eggs.put(kind, ITEMS.registerItem(
                kind.nameSuffix() + "_" + noun + "_spawn_egg",
                props -> {
                    CompoundTag nbt = new CompoundTag();
                    nbt.putString("Kind", kind.id());
                    return new SpawnEggItem(props.component(
                        DataComponents.ENTITY_DATA, TypedEntityData.of(typeSupplier.get(), nbt)));
                }));
        }
        return java.util.Collections.unmodifiableMap(eggs);
    }

    /**
     * The single Resource Slime spawn egg. One registered item whose variant
     * identity lives in the {@code SLIME_VARIANT} data component (see
     * {@link ResourceSlimeSpawnEggItem}). Per-variant stacks are built by
     * {@link #resourceSlimeSpawnEgg(Identifier)}; the creative tab + JEI
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
            PFEntities.RESOURCE_SLIME.get(), props)
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
        // useBlockDescriptionPrefix: every froglight lang key (base AND the
        // per-variant "block...configurable_froglight.<variant>" suffixes the
        // item's getName builds) lives under the block.* prefix.
        props -> new ConfigurableFroglightItem(PFBlocks.CONFIGURABLE_FROGLIGHT.get(), props.useBlockDescriptionPrefix())
    );

    /**
     * Cave Slime spawn egg — vanilla parent species, not category-themed
     * (which is why it isn't in a per-category Map like the frog/tadpole/slime
     * spawn eggs). Single entry, no preset NBT beyond the standard
     * {@code ENTITY_DATA} for the entity type.
     */
    public static final DeferredItem<SpawnEggItem> CAVE_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "cave_slime_spawn_egg",
        props -> new SpawnEggItem(props.spawnEgg(PFEntities.CAVE_SLIME.get()))
    );

    /** Geode Slime spawn egg — GEODE parent species. Mirrors Cave Slime. */
    public static final DeferredItem<SpawnEggItem> GEODE_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "geode_slime_spawn_egg",
        props -> new SpawnEggItem(props.spawnEgg(PFEntities.GEODE_SLIME.get()))
    );

    /** Tide Slime spawn egg — TIDE parent species. Mirrors Cave Slime. */
    public static final DeferredItem<SpawnEggItem> TIDE_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "tide_slime_spawn_egg",
        props -> new SpawnEggItem(props.spawnEgg(PFEntities.TIDE_SLIME.get()))
    );

    /** Void Slime spawn egg — VOID parent species. Mirrors Cave Slime. */
    public static final DeferredItem<SpawnEggItem> VOID_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "void_slime_spawn_egg",
        props -> new SpawnEggItem(props.spawnEgg(PFEntities.VOID_SLIME.get()))
    );

    /** Bog Slime spawn egg — BOG parent species. V1.5 addition. */
    public static final DeferredItem<SpawnEggItem> BOG_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "bog_slime_spawn_egg",
        props -> new SpawnEggItem(props.spawnEgg(PFEntities.BOG_SLIME.get()))
    );

    /** Infernal Slime spawn egg — INFERNAL parent species. V1.5 addition. */
    public static final DeferredItem<SpawnEggItem> INFERNAL_SLIME_SPAWN_EGG = ITEMS.registerItem(
        "infernal_slime_spawn_egg",
        props -> new SpawnEggItem(props.spawnEgg(PFEntities.INFERNAL_SLIME.get()))
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
     * Slime Milker BlockItem — places {@link PFBlocks#SLIME_MILKER}. The block
     * is the V1 production keystone (right-click with slime bucket → milk
     * bucket out). See {@link com.flatts.productivefrogs.content.block.SlimeMilkerBlock}.
     */
    public static final DeferredItem<BlockItem> SLIME_MILKER = registerSimpleBlockItem(
        "slime_milker",
        PFBlocks.SLIME_MILKER,
        new Item.Properties()
    );

    /** Virtual Terrarium Processor BlockItem (docs/virtual_terrarium.md). */
    public static final DeferredItem<BlockItem> VIRTUAL_TERRARIUM = registerSimpleBlockItem(
        "virtual_terrarium",
        PFBlocks.VIRTUAL_TERRARIUM,
        new Item.Properties()
    );

    /**
     * Slime Churn BlockItem (#187) - places {@link PFBlocks#SLIME_CHURN}, the
     * Milker's inverse (milk bucket + empty buckets -> captured Slime Buckets
     * on the placed-source spawn economy).
     */
    public static final DeferredItem<BlockItem> SLIME_CHURN = registerSimpleBlockItem(
        "slime_churn",
        PFBlocks.SLIME_CHURN,
        new Item.Properties()
    );

    /** Slurry Press BlockItem (#281, Phase 3) - places {@link PFBlocks#SLURRY_PRESS}. */
    public static final DeferredItem<BlockItem> SLURRY_PRESS = registerSimpleBlockItem(
        "slurry_press",
        PFBlocks.SLURRY_PRESS,
        new Item.Properties()
    );

    /** Mob Slurry Basin BlockItem (#281, Phase 3) - places {@link PFBlocks#MOB_SLURRY_BASIN}. */
    public static final DeferredItem<BlockItem> MOB_SLURRY_BASIN = registerSimpleBlockItem(
        "mob_slurry_basin",
        PFBlocks.MOB_SLURRY_BASIN,
        new Item.Properties()
    );

    /** Slime Milk Basin BlockItem (#281, Phase 3) - places {@link PFBlocks#SLIME_MILK_BASIN}. */
    public static final DeferredItem<BlockItem> SLIME_MILK_BASIN = registerSimpleBlockItem(
        "slime_milk_basin",
        PFBlocks.SLIME_MILK_BASIN,
        new Item.Properties()
    );

    /**
     * The Mob Slurry bucket (#281, Phase 3) - one item; the condensed mob rides
     * the {@code SLURRIED_ENTITY} component. Produced by the Slurry Press, spent
     * in the Mob Slurry Basin. Never places a fluid (Mob Slurry has no world form).
     */
    public static final DeferredItem<MobSlurryBucketItem> MOB_SLURRY_BUCKET = ITEMS.registerItem(
        "mob_slurry_bucket",
        props -> new MobSlurryBucketItem(
            PFFluids.MOB_SLURRY.get(),
            props.stacksTo(1).craftRemainder(Items.BUCKET)
        )
    );

    /**
     * Spawnery BlockItem - places {@link PFBlocks#SPAWNERY}. Config-gated: only
     * craftable + shown in JEI/creative when {@code spawnery.enabled} is true (the
     * item is always registered so a placed block functions if obtained via /give).
     */
    public static final DeferredItem<BlockItem> SPAWNERY = registerSimpleBlockItem(
        "spawnery",
        PFBlocks.SPAWNERY,
        new Item.Properties()
    );

    /**
     * Froglight Crucible BlockItem (v1.12 wave 1) - places {@link PFBlocks#CRUCIBLE},
     * the GUI-less heated basin that melts Froglights into fluids.
     */
    public static final DeferredItem<BlockItem> CRUCIBLE = registerSimpleBlockItem(
        "crucible",
        PFBlocks.CRUCIBLE,
        new Item.Properties()
    );

    /**
     * Casting Mold BlockItem (v1.12 wave 2) - places {@link PFBlocks#CASTING_MOLD},
     * the molten-to-ingot solidifier that completes the Crucible tower.
     */
    public static final DeferredItem<BlockItem> CASTING_MOLD = registerSimpleBlockItem(
        "casting_mold",
        PFBlocks.CASTING_MOLD,
        new Item.Properties()
    );

    /**
     * Midas frog egg BlockItem (#253) - places {@link PFBlocks#MIDAS_FROG_EGG}.
     * Uses {@link PlaceOnWaterBlockItem} exactly like the per-species primed-egg
     * block items (see {@link #buildPrimedEggItems}): a plain BlockItem can't
     * place frogspawn on a water source, so a simple block item is unplaceable.
     */
    public static final DeferredItem<BlockItem> MIDAS_FROG_EGG = ITEMS.registerItem(
        "midas_frog_egg",
        props -> new PlaceOnWaterBlockItem(PFBlocks.MIDAS_FROG_EGG.get(), props.useBlockDescriptionPrefix())
    );

    /** BlockItems for the per-kind egg blocks (predators + apex, 2026-07-04 ruling). */
    public static final Map<com.flatts.productivefrogs.data.FrogKind, DeferredItem<BlockItem>>
        KIND_FROG_EGG_ITEMS = buildKindEggItems();

    private static Map<com.flatts.productivefrogs.data.FrogKind, DeferredItem<BlockItem>> buildKindEggItems() {
        Map<com.flatts.productivefrogs.data.FrogKind, DeferredItem<BlockItem>> map = new LinkedHashMap<>();
        for (var entry : PFBlocks.KIND_FROG_EGGS.entrySet()) {
            map.put(entry.getKey(), ITEMS.registerItem(
                entry.getKey().nameSuffix() + "_frog_egg",
                props -> new PlaceOnWaterBlockItem(entry.getValue().get(), props.useBlockDescriptionPrefix())
            ));
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * Distiller BlockItem (#253) - places {@link PFBlocks#DISTILLER}, the
     * Equivalence lane's RF-powered extractor (Prismatic Froglight -> item).
     */
    public static final DeferredItem<BlockItem> DISTILLER = registerSimpleBlockItem(
        "distiller",
        PFBlocks.DISTILLER,
        new Item.Properties()
    );

    /**
     * Alembic BlockItem (#253) - places {@link PFBlocks#ALEMBIC}, the Equivalence
     * lane's RF-powered synthesizer (item -> Mimic Slime Bucket).
     */
    public static final DeferredItem<BlockItem> ALEMBIC = registerSimpleBlockItem(
        "alembic",
        PFBlocks.ALEMBIC,
        new Item.Properties()
    );

    /**
     * Terrarium BlockItems (#185). The four machine blocks of the multiblock
     * habitat - Controller / Sprinkler / Incubator / Hatch.
     */
    public static final DeferredItem<BlockItem> TERRARIUM_CONTROLLER =
        registerSimpleBlockItem("terrarium_controller", PFBlocks.TERRARIUM_CONTROLLER, new Item.Properties());
    public static final DeferredItem<BlockItem> SPRINKLER =
        registerSimpleBlockItem("sprinkler", PFBlocks.SPRINKLER, new Item.Properties());
    public static final DeferredItem<BlockItem> INCUBATOR =
        registerSimpleBlockItem("incubator", PFBlocks.INCUBATOR, new Item.Properties());
    public static final DeferredItem<BlockItem> HATCH =
        registerSimpleBlockItem("hatch", PFBlocks.HATCH, new Item.Properties());

    /**
     * Boss-tier catalyst BlockItems (#184). Placing the matching catalyst on all
     * six faces of a boss-variant Slime Milk source arms it. See
     * {@code docs/boss_catalyst_altar.md}.
     */

    // Reinforced Froglights (#249) - the dragon altar's structural blocks.
    public static final DeferredItem<BlockItem> REINFORCED_OBSIDIAN_FROGLIGHT =
        registerSimpleBlockItem("reinforced_obsidian_froglight", PFBlocks.REINFORCED_OBSIDIAN_FROGLIGHT, new Item.Properties());
    public static final DeferredItem<BlockItem> REINFORCED_END_STONE_FROGLIGHT =
        registerSimpleBlockItem("reinforced_end_stone_froglight", PFBlocks.REINFORCED_END_STONE_FROGLIGHT, new Item.Properties());

    // End Crystal Receptacle (#249) - the dragon altar's crystal sockets.
    public static final DeferredItem<BlockItem> END_CRYSTAL_RECEPTACLE =
        registerSimpleBlockItem("end_crystal_receptacle", PFBlocks.END_CRYSTAL_RECEPTACLE, new Item.Properties());
    // End Dragon Altar Hatch (#249) - the dragon altar's output.
    public static final DeferredItem<BlockItem> END_DRAGON_ALTAR_HATCH =
        registerSimpleBlockItem("end_dragon_altar_hatch", PFBlocks.END_DRAGON_ALTAR_HATCH, new Item.Properties());

    // Wither Altar (#247) - the Nether-themed reinforced froglights, receptacles, hatch, and capstone.
    public static final DeferredItem<BlockItem> REINFORCED_SOUL_SAND_FROGLIGHT =
        registerSimpleBlockItem("reinforced_soul_sand_froglight", PFBlocks.REINFORCED_SOUL_SAND_FROGLIGHT, new Item.Properties());
    public static final DeferredItem<BlockItem> REINFORCED_GLOWSTONE_FROGLIGHT =
        registerSimpleBlockItem("reinforced_glowstone_froglight", PFBlocks.REINFORCED_GLOWSTONE_FROGLIGHT, new Item.Properties());

    // Phase 4b altars (#279/#280) - the Shrieker Pit + Monument Well structural blocks.
    public static final DeferredItem<BlockItem> REINFORCED_SCULK_FROGLIGHT =
        registerSimpleBlockItem("reinforced_sculk_froglight", PFBlocks.REINFORCED_SCULK_FROGLIGHT, new Item.Properties());
    public static final DeferredItem<BlockItem> REINFORCED_ECHO_SHARD_FROGLIGHT =
        registerSimpleBlockItem("reinforced_echo_shard_froglight", PFBlocks.REINFORCED_ECHO_SHARD_FROGLIGHT, new Item.Properties());
    public static final DeferredItem<BlockItem> REINFORCED_PRISMARINE_FROGLIGHT =
        registerSimpleBlockItem("reinforced_prismarine_froglight", PFBlocks.REINFORCED_PRISMARINE_FROGLIGHT, new Item.Properties());
    public static final DeferredItem<BlockItem> REINFORCED_SPONGE_FROGLIGHT =
        registerSimpleBlockItem("reinforced_sponge_froglight", PFBlocks.REINFORCED_SPONGE_FROGLIGHT, new Item.Properties());
    public static final DeferredItem<BlockItem> SOUL_SAND_RECEPTACLE =
        registerSimpleBlockItem("soul_sand_receptacle", PFBlocks.SOUL_SAND_RECEPTACLE, new Item.Properties());
    public static final DeferredItem<BlockItem> WITHER_SKULL_RECEPTACLE =
        registerSimpleBlockItem("wither_skull_receptacle", PFBlocks.WITHER_SKULL_RECEPTACLE, new Item.Properties());
    public static final DeferredItem<BlockItem> WITHER_ALTAR_HATCH =
        registerSimpleBlockItem("wither_altar_hatch", PFBlocks.WITHER_ALTAR_HATCH, new Item.Properties());
    public static final DeferredItem<BlockItem> WITHERED_STAR =
        registerSimpleBlockItem("withered_star", PFBlocks.WITHERED_STAR, new Item.Properties());

    // Warden Altar (#279) + Elder Guardian Altar (#280) - hatches, receptacles, capstones.
    public static final DeferredItem<BlockItem> WARDEN_ALTAR_HATCH =
        registerSimpleBlockItem("warden_altar_hatch", PFBlocks.WARDEN_ALTAR_HATCH, new Item.Properties());
    public static final DeferredItem<BlockItem> SHRIEKER_RECEPTACLE =
        registerSimpleBlockItem("shrieker_receptacle", PFBlocks.SHRIEKER_RECEPTACLE, new Item.Properties());
    public static final DeferredItem<BlockItem> ECHOING_CATALYST =
        registerSimpleBlockItem("echoing_catalyst", PFBlocks.ECHOING_CATALYST, new Item.Properties());
    public static final DeferredItem<BlockItem> ELDER_ALTAR_HATCH =
        registerSimpleBlockItem("elder_altar_hatch", PFBlocks.ELDER_ALTAR_HATCH, new Item.Properties());
    public static final DeferredItem<BlockItem> TIDE_OFFERING_RECEPTACLE =
        registerSimpleBlockItem("tide_offering_receptacle", PFBlocks.TIDE_OFFERING_RECEPTACLE, new Item.Properties());
    public static final DeferredItem<BlockItem> MONUMENT_CORE =
        registerSimpleBlockItem("monument_core", PFBlocks.MONUMENT_CORE, new Item.Properties());
    public static final DeferredItem<BlockItem> REINFORCED_LIGHT_BLUE_STAINED_GLASS =
        registerSimpleBlockItem("reinforced_light_blue_stained_glass", PFBlocks.REINFORCED_LIGHT_BLUE_STAINED_GLASS, new Item.Properties());

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
            // useBlockDescriptionPrefix: the name comes from the BLOCK lang key
            // (block.productivefrogs.<cat>_frog_egg) - a bare registerItem would
            // mint an item.* description id no lang entry covers.
            map.put(cat, ITEMS.registerItem(
                cat.primedEggItemName(),
                props -> new PlaceOnWaterBlockItem(
                    PFBlocks.PRIMED_FROG_EGGS.get(catCopy).get(), props.useBlockDescriptionPrefix())
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
            // 1.21.1 NeoForge has no Supplier<Properties> registerItem overload —
            // do the EntityType.get() lookup inside the Function lambda, which runs
            // at registry-build time (post-PFEntities binding).
            map.put(cat, ITEMS.registerItem(
                name,
                props -> {
                    EntityType<?> type = entityTypeSupplier.get();
                    return new SpawnEggItem(applySpawnEggProps(props, type, cat));
                }
            ));
        }
        return map;
    }

    // NOTE (26.1 port): the per-species spawn-egg primary/secondary tint (cat.tintRgb()
    // + darker()) no longer rides the SpawnEggItem constructor - 26.1 moved spawn-egg
    // colour off the item. Re-wire the category tint through the item-colour handler in
    // PFClientEvents (RegisterColorHandlersEvent) when that file lands.
    private static Item.Properties applySpawnEggProps(Item.Properties props, EntityType<?> type, Category category) {
        CompoundTag nbt = new CompoundTag();
        // Modern "Kind" id (26.1 TypedEntityData.loadInto merges this tag over a
        // full entity save, so the egg must speak the same dialect the entity
        // persists - see FrogKind.readFrom's legacy-precedence note).
        nbt.putString("Kind", com.flatts.productivefrogs.data.FrogKind.resource(category).id());
        return props
            .component(DataComponents.ENTITY_DATA, TypedEntityData.of(type, nbt))
            .requiredFeatures(type.requiredFeatures())
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
    public static ItemStack resourceSlimeSpawnEgg(Identifier variantId) {
        ItemStack stack = new ItemStack(RESOURCE_SLIME_SPAWN_EGG.get());
        CompoundTag nbt = new CompoundTag();
        nbt.putString("Variant", variantId.toString());
        stack.set(DataComponents.ENTITY_DATA, TypedEntityData.of(PFEntities.RESOURCE_SLIME.get(), nbt));
        stack.set(PFDataComponents.SLIME_VARIANT.get(), variantId);
        return stack;
    }

    /**
     * Build a Slime Bucket carrying a variant's {@code BUCKET_ENTITY_DATA} NBT -
     * the canonical minimal {@code Category}+{@code Variant} shape mirrored by
     * what {@code ResourceSlime.saveToBucketTag} writes (minus the vanilla
     * mob-bucket keys a real capture also adds). Single source for these NBT key
     * names; shared by the creative tab and the JEI plugin so all display stacks
     * stay consistent with real captured buckets.
     */
    public static ItemStack variantSlimeBucket(Identifier variantId, Category category) {
        ItemStack stack = new ItemStack(SLIME_BUCKET.get());
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> {
            tag.putString("Category", category.name());
            tag.putString("Variant", variantId.toString());
        });
        return stack;
    }

    /**
     * A Slime Milk bucket stack for {@code variantId} (26.1 R-1, single bucket). The
     * variant rides the {@code SLIME_VARIANT} component; every variant can be milked,
     * so this always returns a non-empty stack (there is no per-variant fluid gate
     * any more).
     */
    public static ItemStack slimeMilkBucket(Identifier variantId) {
        return SlimeMilkBucketItem.forVariant(variantId);
    }

    /**
     * Adapter for the 26.1 {@code registerSimpleBlockItem} signature, which now
     * takes a {@code Supplier<Item.Properties>} rather than a Properties instance.
     * Call sites keep passing a Properties instance; this wraps it in a supplier.
     */
    private static DeferredItem<BlockItem> registerSimpleBlockItem(
            String name, Supplier<? extends Block> block, Item.Properties properties) {
        return ITEMS.registerSimpleBlockItem(name, block, () -> properties);
    }

    private PFItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
