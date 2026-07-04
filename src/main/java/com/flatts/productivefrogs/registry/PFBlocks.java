package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.AlembicBlock;
import com.flatts.productivefrogs.content.block.CastingMoldBlock;
import com.flatts.productivefrogs.content.block.DistillerBlock;
import com.flatts.productivefrogs.content.block.MimicMilkSourceBlock;
import com.flatts.productivefrogs.content.block.ConfigurableFroglightBlock;
import com.flatts.productivefrogs.content.block.CrucibleBlock;
import com.flatts.productivefrogs.content.block.EndCrystalReceptacleBlock;
import com.flatts.productivefrogs.content.block.BossAltarHatchBlock;
import com.flatts.productivefrogs.content.block.HatchBlock;
import com.flatts.productivefrogs.content.block.IncubatorBlock;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.content.block.BasinBlock;
import com.flatts.productivefrogs.content.block.SlimeChurnBlock;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.block.SlimeMilkerBlock;
import com.flatts.productivefrogs.content.block.SlurryPressBlock;
import com.flatts.productivefrogs.content.block.SpawneryBlock;
import com.flatts.productivefrogs.content.block.SprinklerBlock;
import com.flatts.productivefrogs.content.block.SweetslimedLilyPadBlock;
import com.flatts.productivefrogs.content.block.TerrariumControllerBlock;
import com.flatts.productivefrogs.content.block.SummonReceptacleBlock;
import com.flatts.productivefrogs.data.Category;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registry. Six separate Primed Frog Egg blocks (one per {@link
 * Category}) — matches vanilla's "N visual variants → N block IDs" pattern for
 * coral, saplings, and wool. Each block pairs cleanly with one BlockItem,
 * so vanilla's Block↔Item bijection (used by pick-block, drops, getCloneItemStack)
 * works without overrides.
 *
 * <p>Uses {@code registerBlock(name, factory, properties)} (not the older
 * {@code register(name, Supplier)}) because MC 1.21.x now requires the
 * {@code ResourceKey} to be set on the Properties before the block's
 * constructor runs. The factory form lets DeferredRegister inject the ID
 * into the Properties and then hand them to our constructor.
 */
public final class PFBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(ProductiveFrogs.MOD_ID);

    /**
     * Local adapter for the 26.1 {@code registerBlock} signature change: the 3rd
     * argument is now a {@code Supplier<BlockBehaviour.Properties>} (or UnaryOperator),
     * not a Properties instance. Call sites still hand a freshly-built Properties
     * instance per block; we wrap it in a supplier the DeferredRegister invokes once.
     */
    private static <B extends Block> DeferredBlock<B> registerBlock(
            String name,
            java.util.function.Function<BlockBehaviour.Properties, ? extends B> factory,
            BlockBehaviour.Properties properties) {
        return BLOCKS.registerBlock(name, factory, () -> properties);
    }

    public static final Map<Category, DeferredBlock<PrimedFrogEggBlock>> PRIMED_FROG_EGGS = buildPrimedEggs();

    /**
     * The Midas frog egg (Equivalence lane, #253) - Midas's own frogspawn, made by
     * Kiss-priming. A standalone PrimedFrogEggBlock (NOT a 7th Category): natively
     * named "Midas Egg", carries the VOID sentinel category for its tadpoles, and
     * the midas marker so it hatches Midas.
     */
    public static final DeferredBlock<PrimedFrogEggBlock> MIDAS_FROG_EGG = registerBlock(
        "midas_frog_egg",
        props -> new PrimedFrogEggBlock(com.flatts.productivefrogs.data.FrogKind.MIDAS, props),
        primedEggProperties(Category.VOID)
    );

    /**
     * One egg block per predator + apex kind (2026-07-04 ruling: no carrier
     * eggs - a cross lays ITS OWN egg block, named and tinted for what it
     * hatches). Same frogspawn behaviour as the species eggs.
     */
    public static final Map<com.flatts.productivefrogs.data.FrogKind, DeferredBlock<PrimedFrogEggBlock>>
        KIND_FROG_EGGS = buildKindEggs();

    private static Map<com.flatts.productivefrogs.data.FrogKind, DeferredBlock<PrimedFrogEggBlock>> buildKindEggs() {
        Map<com.flatts.productivefrogs.data.FrogKind, DeferredBlock<PrimedFrogEggBlock>> map =
            new LinkedHashMap<>();
        java.util.List<com.flatts.productivefrogs.data.FrogKind> kinds = new java.util.ArrayList<>();
        kinds.addAll(java.util.List.of(com.flatts.productivefrogs.data.FrogKind.Predator.values()));
        kinds.addAll(java.util.List.of(com.flatts.productivefrogs.data.FrogKind.Apex.values()));
        for (com.flatts.productivefrogs.data.FrogKind kind : kinds) {
            map.put(kind, registerBlock(
                kind.nameSuffix() + "_frog_egg",
                props -> new PrimedFrogEggBlock(kind, props),
                primedEggProperties(kind.fallbackCategory())
            ));
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * The variant-keyed configurable Froglight block. One block, datapack-driven
     * variant via a {@link com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity}
     * — placement copies the item's {@code SLIME_VARIANT} component into the BE,
     * drops copy the BE back into the item via the loot table at
     * {@code data/productivefrogs/loot_table/blocks/configurable_froglight.json}.
     * Inherits vanilla Froglight properties (light 15, FROGLIGHT sound).
     */
    public static final DeferredBlock<ConfigurableFroglightBlock> CONFIGURABLE_FROGLIGHT =
        registerBlock(
            "configurable_froglight",
            ConfigurableFroglightBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.SAND)
                .strength(0.3F)
                .lightLevel(state -> 15)
                .sound(SoundType.FROGLIGHT)
        );

    /**
     * The single Slime Milk source block (26.1 R-1) - {@code slime_milk_source}.
     * The variant it spawns rides on its {@link SlimeMilkSourceBlockEntity}, seeded
     * from the placing bucket's {@code SLIME_VARIANT} component. Replaces the v1.8
     * per-variant source blocks ({@code PFVariantMilk}, deleted). Mirrors the Mimic
     * Milk source block below. See {@code docs/port_mc_26_1_reimplementation.md} (R-1).
     */
    public static final DeferredBlock<SlimeMilkSourceBlock> SLIME_MILK_SOURCE = registerBlock(
        "slime_milk_source",
        p -> new SlimeMilkSourceBlock(PFFluids.SLIME_MILK.get(), p),
        milkBlockProperties()
    );

    /**
     * The Slime Milker — V1 production keystone block. Furnace-shaped GUI
     * block with one input slot (Slime Bucket), one output slot (variant
     * Slime Milk bucket), and a 100-tick cook. Hopper-compatible via a
     * side-aware Capabilities.ItemHandler.BLOCK provider in PFModBusEvents.
     * See {@link SlimeMilkerBlock} for interaction details.
     *
     * <p>Strength chosen to match the brewing stand / composter feel —
     * cheap to break with hand, sturdy enough to feel "built". Sound type
     * METAL since the design is a mechanical press.
     */
    public static final DeferredBlock<SlimeMilkerBlock> SLIME_MILKER = registerBlock(
        "slime_milker",
        SlimeMilkerBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(0.5F)
            .sound(SoundType.METAL)
    );

    /**
     * The Slime Churn (#187) - the Milker's inverse: consumes a per-variant
     * Slime Milk bucket + empty buckets, produces captured variant Slime
     * Buckets on the placed-source spawn economy (cadence, budget, catalysts).
     * Two inputs / two outputs; hopper-compatible like the Milker. Identity:
     * an oak barrel churn over a packed-mud base with moss accents - the
     * texture follows the recipe (oak planks frame / moss lid / slime core /
     * packed-mud base row), so the block feels like wood.
     */
    public static final DeferredBlock<SlimeChurnBlock> SLIME_CHURN = registerBlock(
        "slime_churn",
        SlimeChurnBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD)
            .strength(0.5F)
            .sound(SoundType.WOOD)
    );

    /**
     * The Slurry Press (#281, predation Phase 3) - condenses a netted mob into
     * a Mob Slurry bucket: filled Ender Net + empty bucket in, Slurry bucket +
     * the emptied net out. Boss mobs rejected. Same appliance shape as the
     * Churn; ender-themed identity (obsidian body, purpur accents).
     */
    public static final DeferredBlock<SlurryPressBlock> SLURRY_PRESS = registerBlock(
        "slurry_press",
        SlurryPressBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(1.5F)
            .sound(SoundType.STONE)
    );

    /**
     * The Mob Slurry Basin (#281, predation Phase 3) - a waterloggable
     * container holding one bucket of Mob Slurry INSIDE the block, respawning
     * that mob on the milk spawn economy (teleport-locked). Works wet or dry.
     */
    public static final DeferredBlock<BasinBlock> MOB_SLURRY_BASIN = registerBlock(
        "mob_slurry_basin",
        props -> new BasinBlock(props,
            com.flatts.productivefrogs.content.block.entity.MobSlurryBasinBlockEntity::new,
            () -> PFBlockEntities.MOB_SLURRY_BASIN.get()),
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE)
            .strength(1.5F)
            .sound(SoundType.STONE)
    );

    /**
     * The Slime Milk Basin (#281, predation Phase 3) - the slime-side sibling:
     * holds any variant's Slime Milk, spawns its Resource Slimes. Additive to
     * the placeable milk source (both coexist); refuses boss (altar-gated) milk.
     */
    public static final DeferredBlock<BasinBlock> SLIME_MILK_BASIN = registerBlock(
        "slime_milk_basin",
        props -> new BasinBlock(props,
            com.flatts.productivefrogs.content.block.entity.SlimeMilkBasinBlockEntity::new,
            () -> PFBlockEntities.SLIME_MILK_BASIN.get()),
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_LIGHT_GREEN)
            .strength(1.5F)
            .sound(SoundType.STONE)
    );

    /**
     * The Spawnery - a skyblock bootstrap appliance, config-gated and off by
     * default. Furnace-style GUI; turns glass bottles into bottled frogspawn
     * fueled by slime balls, optionally primed to a species. {@link SpawneryBlock#LIT}
     * drives a furnace-style burn glow. See {@code docs/spawnery.md}.
     */
    public static final DeferredBlock<SpawneryBlock> SPAWNERY = registerBlock(
        "spawnery",
        SpawneryBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .strength(1.5F)
            .sound(SoundType.STONE)
            .lightLevel(state -> state.getValue(SpawneryBlock.LIT) ? 8 : 0)
    );

    /**
     * The Froglight Crucible (v1.12 wave 1) - GUI-less heated basin that melts
     * a Froglight into a fluid. Heat comes from the block below (data-map
     * driven); {@link CrucibleBlock#LIT} glows while actively melting. Stone
     * feel like a cauldron; light level 11 while lit (molten glow).
     */
    public static final DeferredBlock<CrucibleBlock> CRUCIBLE = registerBlock(
        "crucible",
        CrucibleBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0F)
            .sound(SoundType.STONE)
            .lightLevel(state -> state.getValue(CrucibleBlock.LIT) ? 11 : 0)
            // Cauldron geometry has gaps (legs, open top). Without noOcclusion
            // the renderer treats the block as a full opaque cube and culls the
            // neighbor faces behind it - which read as see-through-to-the-sky
            // holes in-world.
            .noOcclusion()
    );

    /**
     * The Casting Mold (v1.12 wave 2) - solidifies molten metal into ingots.
     * Sits on top of a Crucible to complete the heat / Crucible / Mold tower,
     * or runs free-standing fed by pipes and buckets. Iron-and-bricks identity
     * like the Crucible; METAL sound for the iron frame.
     */
    public static final DeferredBlock<CastingMoldBlock> CASTING_MOLD = registerBlock(
        "casting_mold",
        CastingMoldBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F)
            .sound(SoundType.METAL)
    );

    /**
     * The Distiller (#253) - the Equivalence lane's extractor. RF-powered (PF's
     * first energy machine); renders a Prismatic Froglight back to its carried
     * item. Iron/glass alchemy identity; METAL sound.
     */
    public static final DeferredBlock<DistillerBlock> DISTILLER = registerBlock(
        "distiller",
        DistillerBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F)
            .sound(SoundType.METAL)
    );

    /**
     * The Alembic (#253) - the Equivalence lane's RF-powered synthesizer (empty
     * bucket + off-roster item -> Mimic Slime Bucket). Same alchemy identity as
     * the Distiller; METAL sound.
     */
    public static final DeferredBlock<AlembicBlock> ALEMBIC = registerBlock(
        "alembic",
        AlembicBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(2.0F)
            .sound(SoundType.METAL)
    );

    /**
     * Mimic Milk source block (#253) - the EE lane's placeable, source-only milk.
     * One block (the synthesized item rides on its BE); reuses the shared milk
     * fluid render + block properties. Spawns Mimic Slimes via the milk economy.
     */
    public static final DeferredBlock<MimicMilkSourceBlock> MIMIC_MILK = registerBlock(
        "mimic_slime_milk",
        p -> new MimicMilkSourceBlock(PFFluids.MIMIC_MILK.get(), p),
        milkBlockProperties()
    );

    /** Shared block properties for the Slime Milk + Mimic Milk source blocks (was PFVariantMilk's). */
    private static BlockBehaviour.Properties milkBlockProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .replaceable()
            .noCollision()
            .strength(100.0F)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY);
    }

    /**
     * The Terrarium Controller (#185) - the multiblock anchor. Validates the
     * 7x7x7 shell around a 5x5x5 cavity on a throttled tick and lights
     * {@link TerrariumControllerBlock#FORMED} when formed. Infernal-tier
     * identity (the blocks craft from Infernal-species resources); the milk
     * intake + Sprinkler distribution land in phase 2.
     */
    public static final DeferredBlock<TerrariumControllerBlock> TERRARIUM_CONTROLLER = registerBlock(
        "terrarium_controller",
        TerrariumControllerBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.FIRE)
            .strength(2.5F)
            .sound(SoundType.METAL)
            .lightLevel(state -> state.getValue(TerrariumControllerBlock.FORMED) ? 10 : 0)
    );

    /**
     * The Sprinkler (#185) - a ceiling-cell spawn source. Up to 25 sit in the
     * cavity ceiling; phase 2 gives each the placed-Slime-Milk spawn loop.
     * Cheapest of the five (you craft many).
     */
    public static final DeferredBlock<SprinklerBlock> SPRINKLER = registerBlock(
        "sprinkler",
        SprinklerBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.FIRE)
            .strength(1.0F)
            .sound(SoundType.METAL)
            // Sprinklers cluster in adjacent ceiling cells, and the redstone gate
            // (#263) pauses any Sprinkler reading hasNeighborSignal. A full-cube
            // conductor RE-EMITS strong power (a lever attached to it, dust on top)
            // to its face neighbours, so one lever would pause a whole adjacent
            // cluster. Marking the block a non-conductor stops that bleed - each
            // Sprinkler still pauses on redstone applied directly to it, but a
            // signal on one no longer leaks into the Sprinkler next to it. (Full
            // solid cube otherwise: collision / occlusion / validator unchanged.)
            .isRedstoneConductor((state, level, pos) -> false)
    );

    /** The Incubator (#185) - grows frogspawn/tadpoles into stat-preserving frogs (phase 4). */
    public static final DeferredBlock<IncubatorBlock> INCUBATOR = registerBlock(
        "incubator",
        IncubatorBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.FIRE)
            .strength(2.0F)
            .sound(SoundType.METAL)
    );

    /** The Hatch (#185) - the Terrarium's froglight output inventory (phase 3). */
    public static final DeferredBlock<HatchBlock> HATCH = registerBlock(
        "hatch",
        HatchBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.FIRE)
            .strength(2.0F)
            .sound(SoundType.METAL)
    );

    /**
     * The Sweetslimed Lily Pad (#214, docs/lily_pad_perch.md) - a frog perch. A
     * {@link WaterlilyBlock} (sits on water, instant break) with a BlockEntity ticker
     * that pins the nearest Resource Frog to it. Lily-pad-like properties; instant
     * break and DESTROY push-reaction match the vanilla pad.
     */
    public static final DeferredBlock<SweetslimedLilyPadBlock> SWEETSLIMED_LILY_PAD = registerBlock(
        "sweetslimed_lily_pad",
        SweetslimedLilyPadBlock::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT)
            .instabreak()
            .sound(SoundType.GRASS)
            .noOcclusion()
            .pushReaction(PushReaction.DESTROY)
    );

    /**
     * Boss-tier catalyst blocks (#184, {@code docs/boss_catalyst_altar.md}). A
     * Slime Milk source whose variant declares {@code spawn_catalyst: true}
     * spawns nothing until the matching catalyst block surrounds it on all six
     * faces. Four distinct blocks (one per boss resource, bespoke art - NOT one
     * tinted block), keyed to the variant they arm by {@link #CATALYST_FOR_VARIANT}.
     * Plain full cubes; the spawn gate lives in {@code SlimeMilkSourceBlock#tick},
     * so the catalyst itself needs no BlockEntity.
     */
    public static final DeferredBlock<Block> NETHER_STAR_CATALYST = registerCatalyst("nether_star_catalyst");
    public static final DeferredBlock<Block> DRAGON_EGG_CATALYST = registerCatalyst("dragon_egg_catalyst");
    public static final DeferredBlock<Block> WITHER_SKELETON_SKULL_CATALYST = registerCatalyst("wither_skeleton_skull_catalyst");
    public static final DeferredBlock<Block> DRAGON_BREATH_CATALYST = registerCatalyst("dragon_breath_catalyst");

    /**
     * Reinforced Froglights (#249) - the dragon altar's structural blocks, and the
     * main reason they exist. Bespoke, decorative apart from that role; crafted from
     * 4 obsidian + the matching resource Froglight (Obsidian / End Stone). Full-cube,
     * <b>non-directional</b> blocks (no axis state) with vanilla Froglight
     * light (15) and obsidian-tier blast resistance so the altar reads as dragon-proof.
     * No BlockEntity and no variant component - each is its own fixed block, unlike the
     * data-driven {@link ConfigurableFroglightBlock}.
     *
     * <p><b>Resource variants only</b> (#279/#280 re-key, maintainer ruling): every
     * reinforced froglight is crafted from a froglight of a variant that survives the
     * Phase 5 mob-variant retirement (block/ore resources), never a mob-drop variant
     * (the original blaze rod / nether star / skull versions are retired).
     */
    public static final DeferredBlock<Block> REINFORCED_OBSIDIAN_FROGLIGHT =
        registerBlock("reinforced_obsidian_froglight", Block::new, reinforcedFroglightProperties());
    public static final DeferredBlock<Block> REINFORCED_END_STONE_FROGLIGHT =
        registerBlock("reinforced_end_stone_froglight", Block::new, reinforcedFroglightProperties());

    /**
     * End Crystal Receptacle (#249) - the dragon altar's four crystal sockets, at
     * the exit-portal crystal positions. Holds one End Crystal; the {@code FILLED}
     * blockstate flips the texture and drives the on-top crystal render. Obsidian-
     * tier blast resistance, so it reads as part of the dragon-proof altar.
     */
    public static final DeferredBlock<EndCrystalReceptacleBlock> END_CRYSTAL_RECEPTACLE =
        registerBlock("end_crystal_receptacle", EndCrystalReceptacleBlock::new, receptacleProperties());

    /**
     * End Dragon Altar Hatch (#249) - the altar's output. Same function as the
     * Terrarium Hatch (open like a chest, pipe items out) but a distinct,
     * non-directional block; the summon deposits the dragon's drops here.
     */
    public static final DeferredBlock<BossAltarHatchBlock> END_DRAGON_ALTAR_HATCH =
        registerBlock("end_dragon_altar_hatch",
            p -> new BossAltarHatchBlock(p,
                () -> PFBlockEntities.END_DRAGON_ALTAR_HATCH.get(),
                com.flatts.productivefrogs.content.block.entity.EndDragonAltarHatchBlockEntity::new),
            receptacleProperties());

    /**
     * Reinforced Froglights for the Wither Altar (#247) - the Nether-themed structural
     * blocks, mirroring the dragon altar's pair. Crafted from 4 obsidian + the matching
     * resource Froglight (Soul Sand / Glowstone). Same shape as the dragon altar's
     * reinforced froglights: full-cube, non-directional, light 15, obsidian-tier blast
     * resistance.
     */
    public static final DeferredBlock<Block> REINFORCED_SOUL_SAND_FROGLIGHT =
        registerBlock("reinforced_soul_sand_froglight", Block::new, reinforcedFroglightProperties());
    public static final DeferredBlock<Block> REINFORCED_GLOWSTONE_FROGLIGHT =
        registerBlock("reinforced_glowstone_froglight", Block::new, reinforcedFroglightProperties());

    /**
     * Reinforced Froglights for the Phase 4b altars (#279 Warden / #280 Elder
     * Guardian): sculk + echo shard line the Shrieker Pit; prismarine + sponge build
     * the Monument Well. Same shape as the rest of the reinforced family.
     */
    public static final DeferredBlock<Block> REINFORCED_SCULK_FROGLIGHT =
        registerBlock("reinforced_sculk_froglight", Block::new, reinforcedFroglightProperties());
    public static final DeferredBlock<Block> REINFORCED_ECHO_SHARD_FROGLIGHT =
        registerBlock("reinforced_echo_shard_froglight", Block::new, reinforcedFroglightProperties());
    public static final DeferredBlock<Block> REINFORCED_PRISMARINE_FROGLIGHT =
        registerBlock("reinforced_prismarine_froglight", Block::new, reinforcedFroglightProperties());
    public static final DeferredBlock<Block> REINFORCED_SPONGE_FROGLIGHT =
        registerBlock("reinforced_sponge_froglight", Block::new, reinforcedFroglightProperties());

    /**
     * The two Wither Altar summon receptacles (#247) - the vanilla summon T rendered as
     * sockets. One parameterized {@link SummonReceptacleBlock} backs both, each
     * accepting its own item; a full T (4 soul sand + 3 skulls) fires the summon.
     */
    public static final DeferredBlock<SummonReceptacleBlock> SOUL_SAND_RECEPTACLE =
        registerBlock("soul_sand_receptacle",
            p -> new SummonReceptacleBlock(p, Items.SOUL_SAND), receptacleProperties());
    public static final DeferredBlock<SummonReceptacleBlock> WITHER_SKULL_RECEPTACLE =
        registerBlock("wither_skull_receptacle",
            p -> new SummonReceptacleBlock(p, Items.WITHER_SKELETON_SKULL), receptacleProperties());

    /** Wither Altar Hatch (#247) - the altar's output + summon brain. */
    public static final DeferredBlock<BossAltarHatchBlock> WITHER_ALTAR_HATCH =
        registerBlock("wither_altar_hatch",
            p -> new BossAltarHatchBlock(p,
                () -> PFBlockEntities.WITHER_ALTAR_HATCH.get(),
                com.flatts.productivefrogs.content.block.entity.WitherAltarHatchBlockEntity::new),
            receptacleProperties());

    /**
     * Withered Star (#247) - the Wither Altar's capstone, set into the arena floor.
     * Crafted from a Nether Star, so building the altar proves a first Wither kill.
     * A glowing, obsidian-tier placeable block.
     */
    public static final DeferredBlock<Block> WITHERED_STAR =
        registerBlock("withered_star", Block::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW)
            .strength(3.0F, 1200.0F)
            .lightLevel(state -> 10)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops());

    /**
     * Warden Altar - the Shrieker Pit (#279). The Hatch anchors the pit floor; the
     * four Shrieker Receptacles on the rim take one sculk shrieker each (warning
     * level 4 = a summon); the Echoing Catalyst capstone (crafted from a Sculk
     * Catalyst - proof of a first Warden kill) sits beneath the Hatch.
     */
    public static final DeferredBlock<BossAltarHatchBlock> WARDEN_ALTAR_HATCH =
        registerBlock("warden_altar_hatch",
            p -> new BossAltarHatchBlock(p,
                () -> PFBlockEntities.WARDEN_ALTAR_HATCH.get(),
                com.flatts.productivefrogs.content.block.entity.WardenAltarHatchBlockEntity::new),
            receptacleProperties());
    public static final DeferredBlock<SummonReceptacleBlock> SHRIEKER_RECEPTACLE =
        registerBlock("shrieker_receptacle",
            p -> new SummonReceptacleBlock(p, Items.SCULK_SHRIEKER,
                SummonReceptacleBlock.DisplayMode.TOP), receptacleProperties());
    public static final DeferredBlock<Block> ECHOING_CATALYST =
        registerBlock("echoing_catalyst", Block::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(3.0F, 1200.0F)
            .lightLevel(state -> 10)
            .sound(SoundType.SCULK_CATALYST)
            .requiresCorrectToolForDrops());

    /**
     * Elder Guardian Altar - the Monument Well (#280). The Hatch anchors the tank
     * floor; the four Tide Offering Receptacles at the roof corners take one
     * prismarine crystal each; the Monument Core capstone (crafted from a Wet
     * Sponge - the Elder's signature drop) crowns the roof center.
     */
    public static final DeferredBlock<BossAltarHatchBlock> ELDER_ALTAR_HATCH =
        registerBlock("elder_altar_hatch",
            p -> new BossAltarHatchBlock(p,
                () -> PFBlockEntities.ELDER_ALTAR_HATCH.get(),
                com.flatts.productivefrogs.content.block.entity.ElderAltarHatchBlockEntity::new),
            receptacleProperties());
    public static final DeferredBlock<SummonReceptacleBlock> TIDE_OFFERING_RECEPTACLE =
        registerBlock("tide_offering_receptacle",
            p -> new SummonReceptacleBlock(p, Items.PRISMARINE_CRYSTALS), receptacleProperties());
    /**
     * Reinforced Light Blue Stained Glass - the Monument Well's walls (#280,
     * maintainer ruling 2026-07-04: the tank walls are glass so the swimming
     * Elderbane and the summon replica read from outside). Vanilla-glass render
     * behavior (translucent, no occlusion) at obsidian-tier blast resistance;
     * crafted like the reinforced family (obsidian cross + the vanilla pane).
     */
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> REINFORCED_LIGHT_BLUE_STAINED_GLASS =
        registerBlock("reinforced_light_blue_stained_glass",
            net.minecraft.world.level.block.TransparentBlock::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .strength(3.0F, 1200.0F)
                .sound(SoundType.GLASS)
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false));

    public static final DeferredBlock<Block> MONUMENT_CORE =
        registerBlock("monument_core", Block::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_CYAN)
            .strength(3.0F, 1200.0F)
            .lightLevel(state -> 10)
            .sound(SoundType.WET_SPONGE)
            .requiresCorrectToolForDrops());

    /** Memoized {@link #catalystForVariant()} - the blocks are stable post-registration. */
    private static Map<Identifier, Block> catalystMap;

    /**
     * Single source of truth wiring each boss variant id to the catalyst block
     * that arms its source - read by the 6-face gate in {@code SlimeMilkSourceBlock}
     * and the recipe generator. Built once on first call (the DeferredBlocks
     * resolve only after registration), then memoized: the gate runs per
     * milk-source tick, so rebuilding a 4-entry map each call was needless churn.
     */
    public static Map<Identifier, Block> catalystForVariant() {
        Map<Identifier, Block> map = catalystMap;
        if (map == null) {
            map = Map.of(
                Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "nether_star"), NETHER_STAR_CATALYST.get(),
                Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "dragon_egg"), DRAGON_EGG_CATALYST.get(),
                Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "wither_skeleton_skull"), WITHER_SKELETON_SKULL_CATALYST.get(),
                Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "dragon_breath"), DRAGON_BREATH_CATALYST.get()
            );
            catalystMap = map;
        }
        return map;
    }

    private static DeferredBlock<Block> registerCatalyst(String name) {
        return registerBlock(
            name,
            Block::new,
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(3.0F, 6.0F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops()
        );
    }

    /** Shared properties for the two Reinforced Froglights (#249). */
    private static BlockBehaviour.Properties reinforcedFroglightProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.SAND)
            .strength(3.0F, 1200.0F)   // obsidian-tier blast resistance (dragon-proof); moderate hardness
            .lightLevel(state -> 15)
            .sound(SoundType.FROGLIGHT);
    }

    /** Shared properties for the End Crystal Receptacle (#249). */
    private static BlockBehaviour.Properties receptacleProperties() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK)
            .strength(3.0F, 1200.0F)   // obsidian-tier blast resistance (dragon-proof altar)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops();
    }

    private static Map<Category, DeferredBlock<PrimedFrogEggBlock>> buildPrimedEggs() {
        EnumMap<Category, DeferredBlock<PrimedFrogEggBlock>> map = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            map.put(cat, registerBlock(
                cat.primedEggItemName(),
                props -> new PrimedFrogEggBlock(com.flatts.productivefrogs.data.FrogKind.resource(cat), props),
                primedEggProperties(cat)
            ));
        }
        return map;
    }

    private static BlockBehaviour.Properties primedEggProperties(Category cat) {
        return BlockBehaviour.Properties.of()
            .mapColor(mapColorFor(cat))
            .replaceable()
            .noCollision()
            .instabreak()
            .sound(SoundType.FROGSPAWN)
            .noOcclusion()
            .pushReaction(PushReaction.DESTROY);
    }

    /**
     * Rough map-color picks per category — used in inventory icons and on
     * cartography-table maps. Visual textures use full RGB tints; this map
     * color is just the closest vanilla-bucket approximation.
     */
    private static MapColor mapColorFor(Category cat) {
        return switch (cat) {
            case BOG      -> MapColor.PLANT;
            case CAVE     -> MapColor.METAL;
            case GEODE    -> MapColor.DIAMOND;
            case TIDE     -> MapColor.WATER;
            case INFERNAL -> MapColor.FIRE;
            case VOID     -> MapColor.COLOR_PURPLE;
        };
    }

    private PFBlocks() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    /** Convenience: get the primed egg block for a given category. */
    public static PrimedFrogEggBlock primedEgg(Category category) {
        return PRIMED_FROG_EGGS.get(category).get();
    }

    /** The egg block for ANY kind - every kind has its own block (2026-07-04 ruling). */
    public static PrimedFrogEggBlock primedEgg(com.flatts.productivefrogs.data.FrogKind kind) {
        return switch (kind) {
            case com.flatts.productivefrogs.data.FrogKind.Resource r -> primedEgg(r.category());
            case com.flatts.productivefrogs.data.FrogKind.Midas m -> MIDAS_FROG_EGG.get();
            case com.flatts.productivefrogs.data.FrogKind.Predator p -> KIND_FROG_EGGS.get(p).get();
            case com.flatts.productivefrogs.data.FrogKind.Apex a -> KIND_FROG_EGGS.get(a).get();
        };
    }
}
