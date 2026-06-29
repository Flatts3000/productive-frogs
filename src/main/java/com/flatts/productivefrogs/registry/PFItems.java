package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.item.ConfigurableFroglightItem;
import com.flatts.productivefrogs.content.item.FrogEggItem;
import com.flatts.productivefrogs.content.item.FrogNetItem;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.content.item.MilkCatalystItem;
import com.flatts.productivefrogs.content.item.MimicMilkBucketItem;
import com.flatts.productivefrogs.content.item.MimicSlimeBucketItem;
import com.flatts.productivefrogs.content.item.ResourceSlimeSpawnEggItem;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.content.item.SlimeBucketItem;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
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
     * Froglight Cleaver (#212) - a late-game sword that drops a slime's Froglight
     * when it kills it (handled by {@code FroglightWeaponHandler}), the active-play
     * counterpart to the passive frog loop. Netherite-tier {@link SwordItem} but
     * clearly stronger: +7 attack-damage bonus (12 displayed, vs netherite's 8) and
     * fire-resistant (it's forged from boss froglights + dragon's breath). The
     * harvest behaviour is event-driven. Gated by boss Froglights in its recipe
     * (`froglight_weapon`), so it's pure endgame and the extra power is earned.
     */
    public static final DeferredItem<SwordItem> FROGLIGHT_CLEAVER = ITEMS.registerItem(
        "froglight_cleaver",
        props -> new SwordItem(Tiers.NETHERITE,
            props.fireResistant().attributes(SwordItem.createAttributes(Tiers.NETHERITE, 7, -2.4F)))
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
        props -> new PlaceOnWaterBlockItem(PFBlocks.SWEETSLIMED_LILY_PAD.get(), props)
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
        new FoodProperties.Builder().nutrition(10).saturationModifier(0.6F)
            .usingConvertsTo(Items.BOWL).build();

    public static final DeferredItem<Item> FROG_LEGS_SOUP = ITEMS.registerItem(
        "frog_legs_soup",
        props -> new Item(props.stacksTo(1).food(FROG_LEGS_SOUP_FOOD))
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
            nbt.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            nbt.putString("Category", Category.VOID.name());
            nbt.putBoolean("Midas", true);
            return new SpawnEggItem((EntityType<? extends Mob>) type, 0xFFD700, 0xB8860B,
                new Item.Properties().component(DataComponents.ENTITY_DATA, CustomData.of(nbt)));
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
            nbt.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            nbt.putString("Category", Category.VOID.name());
            nbt.putBoolean("Midas", true);
            return new SpawnEggItem((EntityType<? extends Mob>) type, 0xFFD700, 0xB8860B,
                new Item.Properties().component(DataComponents.ENTITY_DATA, CustomData.of(nbt)));
        });

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
     * Slime Milk buckets are <b>per-variant</b> ({@code <variant>_slime_milk_bucket}),
     * minted dynamically at mod-init by {@link PFVariantMilk} - there is no single
     * milk bucket item. Each is a vanilla {@code BucketItem} whose content is its
     * own variant fluid, so tank mods round-trip it with vanilla handling. The
     * {@link com.flatts.productivefrogs.content.block.SlimeMilkerBlock} outputs the
     * input variant's bucket via {@code PFVariantMilk.bucket(variantId)}. See
     * {@code docs/automated_milk_variants.md}.
     */

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

    /**
     * Slime Churn BlockItem (#187) - places {@link PFBlocks#SLIME_CHURN}, the
     * Milker's inverse (milk bucket + empty buckets -> captured Slime Buckets
     * on the placed-source spawn economy).
     */
    public static final DeferredItem<BlockItem> SLIME_CHURN = ITEMS.registerSimpleBlockItem(
        "slime_churn",
        PFBlocks.SLIME_CHURN,
        new Item.Properties()
    );

    /**
     * Spawnery BlockItem - places {@link PFBlocks#SPAWNERY}. Config-gated: only
     * craftable + shown in JEI/creative when {@code spawnery.enabled} is true (the
     * item is always registered so a placed block functions if obtained via /give).
     */
    public static final DeferredItem<BlockItem> SPAWNERY = ITEMS.registerSimpleBlockItem(
        "spawnery",
        PFBlocks.SPAWNERY,
        new Item.Properties()
    );

    /**
     * Froglight Crucible BlockItem (v1.12 wave 1) - places {@link PFBlocks#CRUCIBLE},
     * the GUI-less heated basin that melts Froglights into fluids.
     */
    public static final DeferredItem<BlockItem> CRUCIBLE = ITEMS.registerSimpleBlockItem(
        "crucible",
        PFBlocks.CRUCIBLE,
        new Item.Properties()
    );

    /**
     * Casting Mold BlockItem (v1.12 wave 2) - places {@link PFBlocks#CASTING_MOLD},
     * the molten-to-ingot solidifier that completes the Crucible tower.
     */
    public static final DeferredItem<BlockItem> CASTING_MOLD = ITEMS.registerSimpleBlockItem(
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
        props -> new PlaceOnWaterBlockItem(PFBlocks.MIDAS_FROG_EGG.get(), props)
    );

    /**
     * Distiller BlockItem (#253) - places {@link PFBlocks#DISTILLER}, the
     * Equivalence lane's RF-powered extractor (Prismatic Froglight -> item).
     */
    public static final DeferredItem<BlockItem> DISTILLER = ITEMS.registerSimpleBlockItem(
        "distiller",
        PFBlocks.DISTILLER,
        new Item.Properties()
    );

    /**
     * Alembic BlockItem (#253) - places {@link PFBlocks#ALEMBIC}, the Equivalence
     * lane's RF-powered synthesizer (item -> Mimic Slime Bucket).
     */
    public static final DeferredItem<BlockItem> ALEMBIC = ITEMS.registerSimpleBlockItem(
        "alembic",
        PFBlocks.ALEMBIC,
        new Item.Properties()
    );

    /**
     * Terrarium BlockItems (#185). The four machine blocks of the multiblock
     * habitat - Controller / Sprinkler / Incubator / Hatch.
     */
    public static final DeferredItem<BlockItem> TERRARIUM_CONTROLLER =
        ITEMS.registerSimpleBlockItem("terrarium_controller", PFBlocks.TERRARIUM_CONTROLLER, new Item.Properties());
    public static final DeferredItem<BlockItem> SPRINKLER =
        ITEMS.registerSimpleBlockItem("sprinkler", PFBlocks.SPRINKLER, new Item.Properties());
    public static final DeferredItem<BlockItem> INCUBATOR =
        ITEMS.registerSimpleBlockItem("incubator", PFBlocks.INCUBATOR, new Item.Properties());
    public static final DeferredItem<BlockItem> HATCH =
        ITEMS.registerSimpleBlockItem("hatch", PFBlocks.HATCH, new Item.Properties());

    /**
     * Boss-tier catalyst BlockItems (#184). Placing the matching catalyst on all
     * six faces of a boss-variant Slime Milk source arms it. See
     * {@code docs/boss_catalyst_altar.md}.
     */
    public static final DeferredItem<BlockItem> NETHER_STAR_CATALYST =
        ITEMS.registerSimpleBlockItem("nether_star_catalyst", PFBlocks.NETHER_STAR_CATALYST, new Item.Properties());
    public static final DeferredItem<BlockItem> DRAGON_EGG_CATALYST =
        ITEMS.registerSimpleBlockItem("dragon_egg_catalyst", PFBlocks.DRAGON_EGG_CATALYST, new Item.Properties());
    public static final DeferredItem<BlockItem> WITHER_SKELETON_SKULL_CATALYST =
        ITEMS.registerSimpleBlockItem("wither_skeleton_skull_catalyst", PFBlocks.WITHER_SKELETON_SKULL_CATALYST, new Item.Properties());
    public static final DeferredItem<BlockItem> DRAGON_BREATH_CATALYST =
        ITEMS.registerSimpleBlockItem("dragon_breath_catalyst", PFBlocks.DRAGON_BREATH_CATALYST, new Item.Properties());

    // Reinforced Froglights (#249) - the dragon altar's structural blocks.
    public static final DeferredItem<BlockItem> REINFORCED_WITHER_SKELETON_SKULL_FROGLIGHT =
        ITEMS.registerSimpleBlockItem("reinforced_wither_skeleton_skull_froglight", PFBlocks.REINFORCED_WITHER_SKELETON_SKULL_FROGLIGHT, new Item.Properties());
    public static final DeferredItem<BlockItem> REINFORCED_NETHER_STAR_FROGLIGHT =
        ITEMS.registerSimpleBlockItem("reinforced_nether_star_froglight", PFBlocks.REINFORCED_NETHER_STAR_FROGLIGHT, new Item.Properties());

    // End Crystal Receptacle (#249) - the dragon altar's crystal sockets.
    public static final DeferredItem<BlockItem> END_CRYSTAL_RECEPTACLE =
        ITEMS.registerSimpleBlockItem("end_crystal_receptacle", PFBlocks.END_CRYSTAL_RECEPTACLE, new Item.Properties());
    // End Dragon Altar Hatch (#249) - the dragon altar's output.
    public static final DeferredItem<BlockItem> END_DRAGON_ALTAR_HATCH =
        ITEMS.registerSimpleBlockItem("end_dragon_altar_hatch", PFBlocks.END_DRAGON_ALTAR_HATCH, new Item.Properties());

    // Wither Altar (#247) - the Nether-themed reinforced froglights, receptacles, hatch, and capstone.
    public static final DeferredItem<BlockItem> REINFORCED_SOUL_SAND_FROGLIGHT =
        ITEMS.registerSimpleBlockItem("reinforced_soul_sand_froglight", PFBlocks.REINFORCED_SOUL_SAND_FROGLIGHT, new Item.Properties());
    public static final DeferredItem<BlockItem> REINFORCED_BLAZE_ROD_FROGLIGHT =
        ITEMS.registerSimpleBlockItem("reinforced_blaze_rod_froglight", PFBlocks.REINFORCED_BLAZE_ROD_FROGLIGHT, new Item.Properties());
    public static final DeferredItem<BlockItem> SOUL_SAND_RECEPTACLE =
        ITEMS.registerSimpleBlockItem("soul_sand_receptacle", PFBlocks.SOUL_SAND_RECEPTACLE, new Item.Properties());
    public static final DeferredItem<BlockItem> WITHER_SKULL_RECEPTACLE =
        ITEMS.registerSimpleBlockItem("wither_skull_receptacle", PFBlocks.WITHER_SKULL_RECEPTACLE, new Item.Properties());
    public static final DeferredItem<BlockItem> WITHER_ALTAR_HATCH =
        ITEMS.registerSimpleBlockItem("wither_altar_hatch", PFBlocks.WITHER_ALTAR_HATCH, new Item.Properties());
    public static final DeferredItem<BlockItem> WITHERED_STAR =
        ITEMS.registerSimpleBlockItem("withered_star", PFBlocks.WITHERED_STAR, new Item.Properties());

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
    public static ItemStack resourceSlimeSpawnEgg(Identifier variantId) {
        ItemStack stack = new ItemStack(RESOURCE_SLIME_SPAWN_EGG.get());
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(PFEntities.RESOURCE_SLIME.get()).toString());
        nbt.putString("Variant", variantId.toString());
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(nbt));
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
     * The variant's own Slime Milk bucket stack (per-variant fluids, v1.8). The
     * item identity carries the variant, so no component stamp is needed. Returns
     * {@link ItemStack#EMPTY} for a variant with no per-variant fluid (one not
     * declared at mod-init) - such variants get no milk.
     */
    public static ItemStack slimeMilkBucket(Identifier variantId) {
        net.minecraft.world.item.Item bucket = PFVariantMilk.bucket(variantId);
        return bucket == null ? ItemStack.EMPTY : new ItemStack(bucket);
    }

    private PFItems() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
