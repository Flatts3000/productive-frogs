package com.flatts.productivefrogs;

import com.flatts.productivefrogs.data.Category;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Mod configuration spec. Single COMMON config holding everything the
 * mod exposes to operators — Slime Milk source-block tuning (J4/J5
 * production keystone) and the Resource Slime random-discovery chance
 * (promoted out of the public-static testing hack in
 * {@code SlimeSplitDiscoveryHandler}).
 *
 * <p>Registered against {@code ModConfig.Type.COMMON} from the mod
 * constructor; that type runs on both client and dedicated server and
 * syncs nothing (each side keeps its own copy). For values that need
 * to be authoritative on the server only, {@code SERVER} would be more
 * correct — none of these qualify in V1 because the milker block runs
 * its tick loop server-side and the only client read would be a
 * cosmetic depletion shader (deferred polish).
 */
public final class PFConfig {

    public static final ModConfigSpec.BooleanValue DEPLETION_ENABLED;
    public static final ModConfigSpec.IntValue DEPLETION_COUNT;
    public static final ModConfigSpec.IntValue MIN_SPAWN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_SPAWN_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue SPAWN_CAP_ENABLED;
    public static final ModConfigSpec.IntValue MAX_NEARBY_SLIMES;
    public static final ModConfigSpec.IntValue SPAWN_CAP_RADIUS;

    // Slime Milk catalysts: hand-dropped upgrade items that buff a placed source
    // block (docs/slime_milk_catalysts.md). The early-game stopgap toward
    // lower-friction production before the V2 Frog Habitat.
    public static final ModConfigSpec.BooleanValue MILK_CATALYSTS_ENABLED;
    // Per-catalyst on/off, each effective only when MILK_CATALYSTS_ENABLED is on (#201).
    public static final ModConfigSpec.BooleanValue CATALYST_COUNT_ENABLED;
    public static final ModConfigSpec.BooleanValue CATALYST_SPEED_ENABLED;
    public static final ModConfigSpec.BooleanValue CATALYST_QUANTITY_ENABLED;
    public static final ModConfigSpec.BooleanValue CATALYST_INFINITE_ENABLED;
    public static final ModConfigSpec.BooleanValue BREWED_FROGLIGHTS_ENABLED;
    public static final ModConfigSpec.BooleanValue FROG_NET_ENABLED;
    public static final ModConfigSpec.BooleanValue FROG_LEGS_ENABLED;
    public static final ModConfigSpec.BooleanValue HOPPING_ENABLED;
    public static final ModConfigSpec.BooleanValue FROGLIGHT_WEAPON_ENABLED;
    public static final ModConfigSpec.BooleanValue EQUIVALENCE_ENABLED;
    public static final ModConfigSpec.BooleanValue PRINCESS_KISS_ENABLED;
    public static final ModConfigSpec.BooleanValue LILY_PAD_PERCH_ENABLED;
    public static final ModConfigSpec.IntValue LILY_PAD_PERCH_RANGE;
    public static final ModConfigSpec.IntValue CATALYST_COUNT_PER;
    public static final ModConfigSpec.IntValue CATALYST_MAX_SPEED_LEVEL;
    public static final ModConfigSpec.IntValue CATALYST_MAX_QUANTITY_LEVEL;
    public static final ModConfigSpec.DoubleValue CATALYST_SPEED_REDUCTION_PER_LEVEL;
    public static final ModConfigSpec.IntValue CATALYST_MIN_INTERVAL_FLOOR_TICKS;
    public static final ModConfigSpec.DoubleValue DISCOVERY_CHANCE_PER_OFFSPRING;
    public static final ModConfigSpec.BooleanValue SPAWNERY_ENABLED;
    public static final ModConfigSpec.BooleanValue SLIME_MILKER_ENABLED;
    public static final ModConfigSpec.BooleanValue SLIME_CHURN_ENABLED;
    public static final ModConfigSpec.BooleanValue CRUCIBLE_ENABLED;
    public static final ModConfigSpec.BooleanValue CASTING_MOLD_ENABLED;
    public static final ModConfigSpec.IntValue SPAWNERY_PRODUCTION_TICKS;

    // Frog stat breeding (docs/frog_breeding.md).
    public static final ModConfigSpec.BooleanValue BREEDING_SAME_SPECIES_ONLY;
    public static final ModConfigSpec.DoubleValue BREEDING_IMPROVEMENT_CHANCE;
    public static final ModConfigSpec.BooleanValue BREEDING_GUARANTEED_IMPROVEMENT;
    public static final ModConfigSpec.IntValue BREEDING_STAT_CAP;
    public static final ModConfigSpec.IntValue STATS_APPETITE_COOLDOWN_MIN;
    public static final ModConfigSpec.IntValue STATS_APPETITE_COOLDOWN_MAX;
    public static final ModConfigSpec.IntValue STATS_BOUNTY_MAX_DROPS;
    public static final ModConfigSpec.IntValue STATS_REACH_RADIUS_MIN;
    public static final ModConfigSpec.IntValue STATS_REACH_RADIUS_MAX;
    public static final ModConfigSpec.BooleanValue FROGS_PERSISTENT;

    // Master switch for the whole frog stat-breeding layer (#202). When off,
    // Sweetslime is uncraftable + no longer a breeding food, every frog behaves at
    // the baseline (stat 1) regardless of its stored stats (which are frozen, not
    // deleted), new offspring are stamped baseline, and the Jade stat readouts are
    // suppressed. The breeding.* / stats.* knobs below only matter when this is on.
    public static final ModConfigSpec.BooleanValue FROG_STATS_ENABLED;

    // Terrarium multiblock (#185, docs/terrarium.md). slimeCap/frogCap are the
    // box's entity budgets; controllerBufferDepth + sprinklerTopUpThreshold tune
    // the phase-2 milk distribution; validationIntervalTicks is the throttled
    // re-validation cadence (the only key phase 1 reads).
    public static final ModConfigSpec.IntValue TERRARIUM_SLIME_CAP;
    public static final ModConfigSpec.IntValue TERRARIUM_FROG_CAP;
    public static final ModConfigSpec.IntValue TERRARIUM_CONTROLLER_BUFFER_DEPTH;
    public static final ModConfigSpec.IntValue TERRARIUM_VALIDATION_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue TERRARIUM_SPRINKLER_TOPUP_THRESHOLD;
    public static final ModConfigSpec.IntValue TERRARIUM_SWEETSLIME_ACCEL_PERCENT;
    public static final ModConfigSpec.IntValue TERRARIUM_HATCH_VACUUM_INTERVAL_TICKS;

    // Per-variant / per-category content scoping (#203). COMMON config: variants
    // are server/world state. The lists name what to force-OFF (empty = nothing
    // disabled = the non-breaking default); bossVariantsEnabled is the convenience
    // switch over the weight-0 prime-only boss tier (the half #200 consumes). The
    // single read point is variantEnabled(id, category, weight); a disabled variant
    // is unprimable, undiscoverable, and hidden from JEI + the creative tab. The
    // registry entry stays (save-safe soft-hide), so re-enabling restores it.
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_VARIANTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_CATEGORIES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_INTEGRATIONS;
    public static final ModConfigSpec.BooleanValue BOSS_VARIANTS_ENABLED;

    // Boss-tier master (#200). One switch over the whole boss tier: the four
    // weight-0 prime-only variants (ANDed with BOSS_VARIANTS_ENABLED), the four
    // catalyst-altar block recipes, the boss Froglight smelt-backs, and creative
    // visibility of the altar blocks. Toxic boss milk + the 6-face altar gate fall
    // out transitively (no variant -> no source -> no milk). Default true.
    public static final ModConfigSpec.BooleanValue BOSS_ENABLED;

    // End Dragon Altar (#249) tunables, under the boss section. Summon length (the
    // beam-and-grow show), the XP reward per summon, and whether the Dragon Egg is
    // re-deposited on every summon (vanilla only ever yields one).
    public static final ModConfigSpec.IntValue DRAGON_ALTAR_SUMMON_TICKS;
    public static final ModConfigSpec.IntValue DRAGON_ALTAR_XP_REWARD;
    public static final ModConfigSpec.BooleanValue DRAGON_ALTAR_REPEATABLE_EGG;

    // Wither Altar (#247) tunables, under the boss section. Summon length (the
    // vanilla-spawn charge) and the XP reward per summon.
    public static final ModConfigSpec.IntValue WITHER_ALTAR_SUMMON_TICKS;
    public static final ModConfigSpec.IntValue WITHER_ALTAR_XP_REWARD;

    // Deterministic, config-exposed lifecycle timings (docs/known_issues.md).
    // These are fixed (non-random) delays for the MODDED frog lifecycle; vanilla
    // frogspawn/tadpoles/frogs keep their own stock pacing.
    public static final ModConfigSpec.IntValue LIFECYCLE_HATCH_TICKS;
    public static final ModConfigSpec.IntValue LIFECYCLE_TADPOLE_GROWTH_TICKS;
    public static final ModConfigSpec.IntValue LIFECYCLE_BREEDING_COOLDOWN_TICKS;

    // Single source of truth for the breeding/stats defaults: used both by the
    // ModConfigSpec definitions below AND by the accessor methods' pre-config-load
    // fallbacks, so the spec default and the fallback can never drift apart.
    public static final int DEFAULT_STAT_CAP = 10;
    public static final double DEFAULT_IMPROVEMENT_CHANCE = 0.40;
    public static final boolean DEFAULT_GUARANTEED_IMPROVEMENT = true;
    public static final int DEFAULT_APPETITE_COOLDOWN_MIN = 30;
    public static final int DEFAULT_APPETITE_COOLDOWN_MAX = 100;
    public static final int DEFAULT_BOUNTY_MAX_DROPS = 3;
    public static final int DEFAULT_REACH_RADIUS_MIN = 8;
    public static final int DEFAULT_REACH_RADIUS_MAX = 16;
    public static final boolean DEFAULT_FROGS_PERSISTENT = true;
    // Sweetslimed lily pad perch (#214): how far a frog will travel to a pad.
    public static final int DEFAULT_LILY_PAD_PERCH_RANGE = 16;
    // Lifecycle defaults chosen to preserve current playable feel: hatch fixed at
    // the low end of vanilla's old random window (3 min), tadpole growth at
    // vanilla's 24000-tick maturation, re-breed cooldown at vanilla's 6000.
    public static final int DEFAULT_HATCH_TICKS = 3600;
    public static final int DEFAULT_TADPOLE_GROWTH_TICKS = 24000;
    public static final int DEFAULT_BREEDING_COOLDOWN_TICKS = 6000;
    // Slime Milk catalyst defaults (single source of truth for spec + fallbacks).
    public static final int DEFAULT_CATALYST_COUNT_PER = 16;
    public static final int DEFAULT_CATALYST_MAX_SPEED_LEVEL = 4;
    public static final int DEFAULT_CATALYST_MAX_QUANTITY_LEVEL = 3;
    public static final double DEFAULT_CATALYST_SPEED_REDUCTION_PER_LEVEL = 0.20;
    public static final int DEFAULT_CATALYST_MIN_INTERVAL_FLOOR_TICKS = 20;
    // Slime-spawn density cap defaults (single source of truth for spec + fallbacks).
    public static final boolean DEFAULT_SPAWN_CAP_ENABLED = true;
    public static final int DEFAULT_MAX_NEARBY_SLIMES = 30;
    public static final int DEFAULT_SPAWN_CAP_RADIUS = 8;
    // Terrarium defaults (single source of truth for spec + fallbacks).
    public static final int DEFAULT_TERRARIUM_SLIME_CAP = 15;
    public static final int DEFAULT_TERRARIUM_FROG_CAP = 8;
    public static final int DEFAULT_TERRARIUM_CONTROLLER_BUFFER_DEPTH = 4;
    public static final int DEFAULT_TERRARIUM_VALIDATION_INTERVAL_TICKS = 30;
    public static final int DEFAULT_TERRARIUM_SPRINKLER_TOPUP_THRESHOLD = 4;
    public static final int DEFAULT_TERRARIUM_SWEETSLIME_ACCEL_PERCENT = 10;
    public static final int DEFAULT_TERRARIUM_HATCH_VACUUM_INTERVAL_TICKS = 8;
    // End Dragon Altar defaults (single source of truth for spec + fallbacks).
    public static final int DEFAULT_DRAGON_ALTAR_SUMMON_TICKS = 200;
    public static final int DEFAULT_DRAGON_ALTAR_XP_REWARD = 500;
    public static final boolean DEFAULT_DRAGON_ALTAR_REPEATABLE_EGG = true;
    // Wither Altar defaults: 220 matches the vanilla invulnerable spawn; 50 XP a Wither's value.
    public static final int DEFAULT_WITHER_ALTAR_SUMMON_TICKS = 220;
    public static final int DEFAULT_WITHER_ALTAR_XP_REWARD = 50;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("slime_milk");

        DEPLETION_ENABLED = builder
            .comment(
                "Whether Slime Milk source blocks deplete after a fixed number of slime spawns.",
                "When true (default), each source block produces depletionCount slimes and then disappears.",
                "When false, source blocks spawn indefinitely — better for creative play or packs that want low-friction production."
            )
            .define("depletionEnabled", true);

        // Since v1.7 the spawns-remaining counter lives on the source's
        // BlockEntity (not a blockstate property), so the old "ceiling is 16"
        // blockstate constraint is gone and Count catalysts can push a live
        // source's remaining count arbitrarily high. The configured starting
        // budget below is still bounded for sane defaults.
        DEPLETION_COUNT = builder
            .comment(
                "Number of slimes a freshly-placed source block produces before draining (depletionEnabled=true).",
                "Default 16. Count catalysts (if enabled) raise a placed source's remaining count beyond this."
            )
            .defineInRange("depletionCount", 16, 1, 4096);

        builder.pop();

        builder.push("slime_milk_spawning");

        MIN_SPAWN_INTERVAL_TICKS = builder
            .comment(
                "Minimum delay between slime spawns from a single source block, in ticks.",
                "20 ticks = 1 second. Default 200 (10 seconds) per docs/farming.md."
            )
            .defineInRange("minSpawnIntervalTicks", 200, 1, 24000);

        MAX_SPAWN_INTERVAL_TICKS = builder
            .comment(
                "Maximum delay between slime spawns from a single source block, in ticks.",
                "Each spawn picks a uniform random delay in [minSpawnIntervalTicks, maxSpawnIntervalTicks].",
                "Default 600 (30 seconds)."
            )
            .defineInRange("maxSpawnIntervalTicks", 600, 1, 24000);

        SPAWN_CAP_ENABLED = builder
            .comment(
                "Whether a Slime Milk source pauses spawning when its area is already full of",
                "its own species' slimes. Default true. The safety valve that keeps an",
                "automated farm (especially an Endless/Rapid source with no frog eating fast",
                "enough) from flooding the server with entities. When false, sources spawn on",
                "their cadence regardless of how many slimes are nearby - use with care."
            )
            .define("spawnCapEnabled", DEFAULT_SPAWN_CAP_ENABLED);

        MAX_NEARBY_SLIMES = builder
            .comment(
                "Max Resource Slimes of the source's own species allowed within spawnCapRadius",
                "before the source pauses spawning. Default 30. The source resumes once frogs",
                "(or anything) bring the count back below this. A paused source does NOT spend",
                "its remaining-spawn budget, so a finite source isn't wasted waiting for room.",
                "Counts only Productive Frogs slimes of the matching species, not vanilla slimes.",
                "Note: the cap is per-species-per-area, so a single-species farm (the usual case)",
                "is held at this many; a pen mixing several species can hold this many of each."
            )
            .defineInRange("maxNearbySlimes", DEFAULT_MAX_NEARBY_SLIMES, 1, 4096);

        SPAWN_CAP_RADIUS = builder
            .comment(
                "Block radius of the cube the spawn cap counts slimes in, centered on the source.",
                "Default 8. Larger covers a bigger pen but scans more entities per spawn attempt."
            )
            .defineInRange("spawnCapRadius", DEFAULT_SPAWN_CAP_RADIUS, 1, 64);

        builder.pop();

        builder.push("slime_milk_catalysts");

        MILK_CATALYSTS_ENABLED = builder
            .comment(
                "Whether Slime Milk catalysts are enabled. Default true.",
                "Catalysts are hand-crafted items you drop into a placed Slime Milk source to buff it:",
                "Count (+more spawns), Speed (faster), Quantity (more slimes per spawn), and Infinite Count.",
                "When false the catalysts are uncraftable and hidden from JEI; a placed source still works,",
                "and any upgrades already applied to existing sources are still honoured. Toggling the recipes",
                "off/on requires a world reload to re-evaluate the recipe condition."
            )
            .define("enabled", true);

        CATALYST_COUNT_ENABLED = builder
            .comment(
                "Whether the Count catalyst is enabled. Default true.",
                "Effective only when the catalysts master 'enabled' is also true. When off, the Count",
                "catalyst is uncraftable, hidden from JEI + the creative tab, and inert if dropped into",
                "a source/Sprinkler (the item is left for the player). Toggling requires a world reload."
            )
            .define("count", true);

        CATALYST_SPEED_ENABLED = builder
            .comment(
                "Whether the Speed catalyst is enabled. Default true.",
                "Effective only when the catalysts master 'enabled' is also true. When off, the Speed",
                "catalyst is uncraftable, hidden from JEI + the creative tab, and inert if dropped in.",
                "Toggling requires a world reload."
            )
            .define("speed", true);

        CATALYST_QUANTITY_ENABLED = builder
            .comment(
                "Whether the Quantity catalyst is enabled. Default true.",
                "Effective only when the catalysts master 'enabled' is also true. When off, the Quantity",
                "catalyst is uncraftable, hidden from JEI + the creative tab, and inert if dropped in.",
                "Toggling requires a world reload."
            )
            .define("quantity", true);

        CATALYST_INFINITE_ENABLED = builder
            .comment(
                "Whether the Infinite (Endless) catalyst is enabled. Default true.",
                "Effective only when the catalysts master 'enabled' is also true. When off, the Infinite",
                "catalyst is uncraftable, hidden from JEI + the creative tab, and inert if dropped in.",
                "The Infinite catalyst is crafted from Count catalysts, so disabling 'count' also leaves",
                "Infinite uncraftable in survival even if this stays on. Toggling requires a world reload."
            )
            .define("infinite", true);

        CATALYST_COUNT_PER = builder
            .comment(
                "Spawns added to a source's remaining count per Count catalyst consumed.",
                "Default 16 (one full bucket's worth). Count is uncapped - keep feeding to extend a source."
            )
            .defineInRange("countPerCatalyst", DEFAULT_CATALYST_COUNT_PER, 1, 4096);

        CATALYST_MAX_SPEED_LEVEL = builder
            .comment(
                "Maximum Speed catalyst level a source can reach. Default 4.",
                "Each level shortens the spawn interval by speedReductionPerLevel, down to the floor below."
            )
            .defineInRange("maxSpeedLevel", DEFAULT_CATALYST_MAX_SPEED_LEVEL, 1, 64);

        CATALYST_MAX_QUANTITY_LEVEL = builder
            .comment(
                "Maximum Quantity catalyst level a source can reach. Default 3.",
                "Slimes spawned per spawn event = 1 + quantityLevel (so level 3 = 4 slimes per spawn).",
                "Ceiling capped at 16 so a misconfiguration can't make one source spawn a swarm per",
                "event (no per-event entity-density guard exists); raise the source code bound if you",
                "genuinely want more."
            )
            .defineInRange("maxQuantityLevel", DEFAULT_CATALYST_MAX_QUANTITY_LEVEL, 1, 16);

        CATALYST_SPEED_REDUCTION_PER_LEVEL = builder
            .comment(
                "Fraction the spawn interval is reduced per Speed level. Default 0.20 (-20% per level).",
                "Applied to both the min and max interval; the result is clamped to minIntervalFloorTicks."
            )
            .defineInRange("speedReductionPerLevel", DEFAULT_CATALYST_SPEED_REDUCTION_PER_LEVEL, 0.0, 0.95);

        CATALYST_MIN_INTERVAL_FLOOR_TICKS = builder
            .comment(
                "Hard floor (ticks) the Speed-reduced spawn interval cannot drop below. Default 20 (1s).",
                "Stops stacked Speed levels from driving the interval to zero."
            )
            .defineInRange("minIntervalFloorTicks", DEFAULT_CATALYST_MIN_INTERVAL_FLOOR_TICKS, 1, 24000);

        builder.pop();

        builder.push("brewed_froglights");

        BREWED_FROGLIGHTS_ENABLED = builder
            .comment(
                "Whether Brewed Froglights are enabled. Default true.",
                "When a frog eats a slime carrying a potion effect (splash/linger), the Froglight it drops",
                "captures that effect: a toggleable aura when placed, a self-buff when held or worn in the",
                "Curios slot. When false, no effect is captured (Froglights drop plain) and any already-brewed",
                "Froglights go inert - they keep their stored effect but apply nothing and read as plain."
            )
            .define("enabled", true);

        builder.pop();

        builder.push("frog_net");

        FROG_NET_ENABLED = builder
            .comment(
                "Whether the Frog Net is enabled. Default true.",
                "The Frog Net catches a Resource Frog into the item (right-click the frog) and releases",
                "it elsewhere (right-click a block), preserving its species and bred stats. When false the",
                "net is uncraftable, hidden from JEI + the creative tab, and inert if obtained another way",
                "(it neither catches nor releases). Toggling the recipe requires a world reload."
            )
            .define("enabled", true);

        builder.pop();

        builder.push("frog_legs");

        FROG_LEGS_ENABLED = builder
            .comment(
                "Whether killing a frog drops Frog Legs. Default true.",
                "Any frog (vanilla, Resource, or a modded frog in the productivefrogs:frogs tag) drops 1-2",
                "raw Frog Legs when killed, Looting-scaled, and cooked Frog Legs instead if it was on fire",
                "(the cow/chicken behaviour). When false, no legs drop and the items are uncraftable/hidden.",
                "Set false for packs that want losing a frog to sting. Toggling recipes requires a world reload."
            )
            .define("enabled", true);

        builder.pop();

        builder.push("froglight_weapon");

        FROGLIGHT_WEAPON_ENABLED = builder
            .comment(
                "Whether the Froglight Cleaver is enabled. Default true.",
                "A late-game sword that drops a Resource Slime's Froglight when it kills it (Looting-scaled,",
                "and a brewed slime's effect carries onto the Froglight). Gated by boss Froglights in its",
                "recipe, so it's pure endgame. When false the sword is uncraftable, hidden from JEI + the",
                "creative tab, and harvests no Froglight if obtained another way. Recipe toggle needs a world reload."
            )
            .define("enabled", true);

        builder.pop();

        builder.push("equivalence");

        EQUIVALENCE_ENABLED = builder
            .comment(
                "Whether the Equivalence lane is enabled. Default FALSE (opt-in).",
                "The post-capstone, RF-powered transmutation lane for OFF-ROSTER items (#253):",
                "Alembic (item -> Mimic Slime Bucket) -> Mimic Milk -> Mimic Slimes -> Midas",
                "(Kiss-primed frog) -> Prismatic Froglight -> Distiller (-> the item). When false the",
                "WHOLE lane is inert: Alembic + Distiller are uncraftable and don't process, the",
                "Princess's Kiss won't prime a Midas egg, Midas frogs drop nothing, Mimic Milk sources",
                "don't spawn, and the lane's items are hidden from JEI + the creative tab. Recipe toggle",
                "needs a world reload."
            )
            .define("enabled", false);

        builder.pop();

        builder.push("hopping");

        HOPPING_ENABLED = builder
            .comment(
                "Whether the Potion of Hopping is enabled. Default true.",
                "Brewed from an awkward potion + raw Frog Legs. While the effect is active a jump",
                "launches you forward (a frog leap, distinct from vanilla's vertical Jump Boost), and",
                "falls are softened. When false the brewing recipe is not registered and the effect does",
                "nothing if applied another way. Toggling the brew requires a restart."
            )
            .define("enabled", true);

        builder.pop();

        builder.push("princess_kiss");

        PRINCESS_KISS_ENABLED = builder
            .comment(
                "Whether the Princess's Kiss is enabled. Default true.",
                "A rare Ender Dragon drop: right-click a frog to turn it into a villager (the Frog Prince),",
                "a timed conversion like the zombie cure. When false the dragon does not drop it and the",
                "item does nothing if obtained another way."
            )
            .define("enabled", true);

        builder.pop();

        builder.push("lily_pad_perch");

        LILY_PAD_PERCH_ENABLED = builder
            .comment(
                "Whether the Sweetslimed lily pad perch is enabled. Default true.",
                "Right-click a placed lily pad with a Sweetslime to make a perch; the nearest Resource Frog",
                "pins to it (one frog per pad) and stays put while still eating slimes and dropping Froglights -",
                "a way to keep a working frog over a hopper without leashing it. When false the create",
                "interaction is inert and the block/item is hidden from the creative tab. Resource Frogs only."
            )
            .define("enabled", true);

        LILY_PAD_PERCH_RANGE = builder
            .comment(
                "How far (blocks) a Resource Frog will travel to reach a sweetslimed lily pad. Default 16,",
                "matching the max prey-scan reach. Larger pulls frogs from farther at slightly more scan cost."
            )
            .defineInRange("range", DEFAULT_LILY_PAD_PERCH_RANGE, 1, 64);

        builder.pop();

        builder.push("discovery");

        DISCOVERY_CHANCE_PER_OFFSPRING = builder
            .comment(
                "Per-offspring chance for a parent-species slime split to convert each child",
                "into a category-matched Resource Slime. Default 0.05 (5%) — a slime farm yields ~1 Resource Slime per dozen splits."
            )
            .defineInRange("discoveryChancePerOffspring", 0.05, 0.0, 1.0);

        builder.pop();

        builder.push("spawnery");

        SPAWNERY_ENABLED = builder
            .comment(
                "Whether the Spawnery is enabled. Default false.",
                "The Spawnery is a skyblock bootstrap appliance: it turns glass bottles into bottled frogspawn",
                "(Frog Eggs), fueled by slime balls, optionally primed to a species. In a normal world swamps",
                "already provide frogspawn, so it ships off. When false the Spawnery is uncraftable and hidden",
                "from JEI + the creative tab; a placed block (e.g. via /give) still functions. Toggling requires",
                "a world reload to re-evaluate the recipe condition."
            )
            .define("enabled", false);

        SPAWNERY_PRODUCTION_TICKS = builder
            .comment(
                "Ticks to produce one bottled frogspawn (one slime ball of burn, one glass bottle).",
                "20 ticks = 1 second. Default 200 (10 seconds)."
            )
            .defineInRange("productionTicks", 200, 1, 24000);

        builder.pop();

        builder.push("appliances");

        SLIME_MILKER_ENABLED = builder
            .comment(
                "Whether the Slime Milker is craftable. Default true.",
                "When false the Slime Milker is uncraftable and hidden from JEI + the creative tab; a placed",
                "Milker still works (the flag gates crafting/JEI/creative visibility only). Toggling requires a",
                "world reload to re-evaluate the recipe condition."
            )
            .define("slimeMilker", true);

        SLIME_CHURN_ENABLED = builder
            .comment(
                "Whether the Slime Churn is craftable. Default true.",
                "When false the Slime Churn is uncraftable and hidden from JEI + the creative tab; a placed",
                "Churn still works. Toggling requires a world reload to re-evaluate the recipe condition."
            )
            .define("slimeChurn", true);

        CRUCIBLE_ENABLED = builder
            .comment(
                "Whether the Froglight Crucible is craftable. Default true.",
                "When false the Crucible is uncraftable and its melt lane (Froglights -> fluids) is hidden",
                "from JEI + the creative tab; a placed Crucible still works. Toggling requires a world reload."
            )
            .define("crucible", true);

        CASTING_MOLD_ENABLED = builder
            .comment(
                "Whether the Casting Mold is craftable. Default true.",
                "When false the Mold is uncraftable and its cast lane (molten fluids -> ingots/blocks) is",
                "hidden from JEI + the creative tab; a placed Mold still works. Toggling requires a world reload."
            )
            .define("castingMold", true);

        builder.pop();

        builder.push("frog_stats");

        FROG_STATS_ENABLED = builder
            .comment(
                "Master switch for the frog stat-breeding layer - Appetite / Bounty / Reach bred via",
                "Sweetslime (#202). Default true. When false, a pack gets the plain six-species froglight",
                "loop with no breeding minigame: Sweetslime is uncraftable and hidden from JEI + the creative",
                "tab and no longer breeds frogs, every frog behaves at the baseline (as if stat 1) so there is",
                "no per-frog variance, newly bred frogs carry baseline stats, and the Jade stat lines are",
                "hidden. Stored stats on existing frogs are frozen, not deleted - re-enabling restores them.",
                "The breeding.* and stats.* tuning below only takes effect while this is on. Recipe gating",
                "needs a world reload."
            )
            .define("enabled", true);

        builder.pop();

        builder.push("breeding");

        BREEDING_SAME_SPECIES_ONLY = builder
            .comment(
                "Whether a Resource Frog only breeds with another frog of the same species/category.",
                "Default true - a Cave frog breeds only with another Cave frog, keeping each species' stat line clean.",
                "When false, any two Resource Frogs can breed (the offspring inherits the initiating frog's species)."
            )
            .define("sameSpeciesOnly", true);

        BREEDING_IMPROVEMENT_CHANCE = builder
            .comment(
                "Per-stat chance the offspring climbs one above the blended parent average (min(cap, base + 1)),",
                "otherwise it holds at the average. Default 0.40. Inheritance is a two-layer roll: each stat is",
                "first the round-half-up average of the two parents (the blend), then this is the chance it ticks",
                "up by one on top. There is no regression below the average. Raising this makes the climb brisker."
            )
            .defineInRange("improvementChance", DEFAULT_IMPROVEMENT_CHANCE, 0.0, 1.0);

        BREEDING_GUARANTEED_IMPROVEMENT = builder
            .comment(
                "Whether every breed improves at least one stat over the blended average. Default true.",
                "When true, if no stat happened to climb on its own, one stat that still has headroom (its blended",
                "average is below the cap) is bumped by one - so a breed is never a wasted generation. When false,",
                "a breed can come out exactly at the parent average (slower, purer-RNG climb)."
            )
            .define("guaranteedImprovement", DEFAULT_GUARANTEED_IMPROVEMENT);

        BREEDING_STAT_CAP = builder
            .comment("Maximum value any single stat can reach. Default 10 (the 'maxed' cap).")
            .defineInRange("statCap", DEFAULT_STAT_CAP, 1, 100);

        builder.pop();

        builder.push("stats");

        STATS_APPETITE_COOLDOWN_MIN = builder
            .comment("Eat cooldown in ticks at Appetite = statCap (fastest). Default 30 (1.5s).")
            .defineInRange("appetiteCooldownMin", DEFAULT_APPETITE_COOLDOWN_MIN, 1, 24000);

        STATS_APPETITE_COOLDOWN_MAX = builder
            .comment(
                "Eat cooldown in ticks at Appetite = 1 (slowest). Default 100 (5s).",
                "Should be >= appetiteCooldownMin; the curve interpolates linearly between the two."
            )
            .defineInRange("appetiteCooldownMax", DEFAULT_APPETITE_COOLDOWN_MAX, 1, 24000);

        STATS_BOUNTY_MAX_DROPS = builder
            .comment("Froglights dropped per slime at Bounty = statCap. Default 3 (step curve: 1 / 2 / 3).")
            .defineInRange("bountyMaxDrops", DEFAULT_BOUNTY_MAX_DROPS, 1, 64);

        STATS_REACH_RADIUS_MIN = builder
            .comment("Prey-scan radius in blocks at Reach = 1. Default 8.")
            .defineInRange("reachRadiusMin", DEFAULT_REACH_RADIUS_MIN, 1, 64);

        STATS_REACH_RADIUS_MAX = builder
            .comment(
                "Prey-scan radius in blocks at Reach = statCap. Default 16.",
                "Should be >= reachRadiusMin; the curve interpolates linearly between the two."
            )
            .defineInRange("reachRadiusMax", DEFAULT_REACH_RADIUS_MAX, 1, 64);

        builder.pop();

        builder.push("frogs");

        FROGS_PERSISTENT = builder
            .comment(
                "Whether Resource Frogs are persistent (never despawn). Default true.",
                "A bred-up frog is valuable; persistence prevents losing a stat line to despawn.",
                "They can still die to damage. Set false to let frogs despawn like vanilla animals."
            )
            .define("persistent", DEFAULT_FROGS_PERSISTENT);

        builder.pop();

        builder.push("lifecycle");

        LIFECYCLE_HATCH_TICKS = builder
            .comment(
                "Ticks a Primed Frog Egg block waits before hatching into tadpoles.",
                "Deterministic (fixed), unlike vanilla frogspawn's random 3600-12000-tick window.",
                "20 ticks = 1 second. Default 3600 (3 minutes). Vanilla frogspawn is unaffected."
            )
            .defineInRange("primedFrogspawnHatchTicks", DEFAULT_HATCH_TICKS, 1, 24000);

        LIFECYCLE_TADPOLE_GROWTH_TICKS = builder
            .comment(
                "Ticks a Resource Tadpole takes to mature into a Resource Frog.",
                "Default 24000 (20 minutes), matching vanilla. Lower values speed maturation;",
                "values at or above vanilla's 24000-tick ceiling leave the stock 20-minute pace",
                "(raising the ceiling would require changing the shared vanilla tadpole timer).",
                "Vanilla tadpoles are unaffected; slime-ball feeding still accelerates growth."
            )
            .defineInRange("tadpoleGrowthTicks", DEFAULT_TADPOLE_GROWTH_TICKS, 1, 24000);

        LIFECYCLE_BREEDING_COOLDOWN_TICKS = builder
            .comment(
                "Ticks two Resource Frogs must wait after breeding before they can breed again.",
                "Deterministic. Default 6000 (5 minutes), matching vanilla animals."
            )
            .defineInRange("breedingCooldownTicks", DEFAULT_BREEDING_COOLDOWN_TICKS, 1, 24000);

        builder.pop();

        builder.push("terrarium");

        TERRARIUM_SLIME_CAP = builder
            .comment(
                "Max slimes allowed inside a formed Terrarium's 5x5x5 cavity before its Sprinklers",
                "pause spawning. Counts ALL slimes in the cavity however they arrived. Default 15."
            )
            .defineInRange("slimeCap", DEFAULT_TERRARIUM_SLIME_CAP, 1, 4096);

        TERRARIUM_FROG_CAP = builder
            .comment(
                "Max frogs a formed Terrarium releases into its cavity. At the cap, Incubators hold",
                "matured frogs and release them as space frees. Default 8."
            )
            .defineInRange("frogCap", DEFAULT_TERRARIUM_FROG_CAP, 1, 4096);

        TERRARIUM_CONTROLLER_BUFFER_DEPTH = builder
            .comment(
                "How many bucket-equivalent milk charges the Controller's funnel buffers before it",
                "stops accepting more. The Controller holds one variant at a time. Default 4."
            )
            .defineInRange("controllerBufferDepth", DEFAULT_TERRARIUM_CONTROLLER_BUFFER_DEPTH, 1, 64);

        TERRARIUM_VALIDATION_INTERVAL_TICKS = builder
            .comment(
                "Ticks between a Controller's automatic structure re-validations. 20 ticks = 1 second.",
                "Default 30. Lower revalidates sooner after a shell change at slightly more scan cost."
            )
            .defineInRange("validationIntervalTicks", DEFAULT_TERRARIUM_VALIDATION_INTERVAL_TICKS, 1, 1200);

        TERRARIUM_SPRINKLER_TOPUP_THRESHOLD = builder
            .comment(
                "When a matching-variant Sprinkler's remaining spawns fall to or below this, the",
                "Controller tops it up from a buffered charge (phase 2). Default 4."
            )
            .defineInRange("sprinklerTopUpThreshold", DEFAULT_TERRARIUM_SPRINKLER_TOPUP_THRESHOLD, 0, 4096);

        TERRARIUM_SWEETSLIME_ACCEL_PERCENT = builder
            .comment(
                "Percent of the full incubation lifecycle a single Sweetslime shaves off when",
                "right-clicked into an incubating Incubator. Default 10 (10% per slime). 0 disables the speed-up."
            )
            .defineInRange("sweetslimeAcceleratePercent", DEFAULT_TERRARIUM_SWEETSLIME_ACCEL_PERCENT, 0, 100);

        TERRARIUM_HATCH_VACUUM_INTERVAL_TICKS = builder
            .comment(
                "Ticks between a Hatch's auto-collect sweeps of loose items in the cavity. 20 ticks = 1 second.",
                "Default 8. Lower picks items up sooner at slightly more scan cost."
            )
            .defineInRange("hatchVacuumIntervalTicks", DEFAULT_TERRARIUM_HATCH_VACUUM_INTERVAL_TICKS, 1, 1200);

        builder.pop();

        builder.push("variants");

        DISABLED_VARIANTS = builder
            .comment(
                "Slime variants to force-OFF, by full id (e.g. \"productivefrogs:iron\", \"productivefrogs:tin\").",
                "A disabled variant cannot be primed, never appears in split-discovery, and is hidden from",
                "JEI and the creative tab. The registry entry stays, so re-enabling restores it (no save",
                "surgery). Default empty (nothing disabled). Already-placed slimes/buckets/froglights of a",
                "disabled variant keep working - this gates resolution, it does not delete world content.",
                "Note: a disabled variant may still have its per-variant Slime Milk fluid registered (that is",
                "minted at mod-init before this config loads), but it stays unobtainable since every way to",
                "reach the variant is gated. Applies on world reload."
            )
            .defineListAllowEmpty("disabledVariants", List.of(), () -> "productivefrogs:example",
                PFConfig::isValidVariantId);

        DISABLED_CATEGORIES = builder
            .comment(
                "Whole species/categories to force-OFF, by lowercase name: cave, geode, bog, tide, infernal, void.",
                "Disabling a category disables every variant in it (same effect as listing them all in",
                "disabledVariants). Default empty. Use this to ship, say, only the Cave and Bog lines."
            )
            .defineListAllowEmpty("disabledCategories", List.of(), () -> "void",
                PFConfig::isValidCategoryName);

        DISABLED_INTEGRATIONS = builder
            .comment(
                "Host mods whose cross-mod variants you want force-OFF, by modid (e.g.",
                "[\"mekanism\", \"powah\"]). Every variant gated solely behind a listed mod becomes",
                "disabled exactly like disabledVariants: unprimable, undiscovered, hidden from JEI +",
                "the creative tab. Use this to keep a host mod for its machines while dropping its",
                "Resource Slime variants. Default empty.",
                "The provider mod is read from each variant's mod_loaded gate, so the key is that gate's",
                "modid (e.g. AllTheOres-sourced metals like tin/lead are under \"alltheores\", not a per-",
                "metal mod). A variant shared by several providers (only \"silicon\": ae2 OR refinedstorage)",
                "is disabled only when EVERY one of its providers is listed - disabling one leaves it",
                "reachable via the other; to drop such a variant outright, name it in disabledVariants.",
                "Only built-in variants are covered; a variant added through a live world datapack is not."
            )
            .defineListAllowEmpty("disabledIntegrations", List.of(), () -> "examplemod",
                PFConfig::isValidIntegrationKey);

        BOSS_VARIANTS_ENABLED = builder
            .comment(
                "Whether the boss tier's prime-only variants (weight 0: wither skull, nether star, dragon egg,",
                "dragon breath) are enabled. Default true. When false they cannot be primed and are hidden from",
                "JEI + the creative tab - the one-switch way to drop boss farming without listing each id.",
                "(The catalyst altars and boss recipes have their own master under the boss section, #200.)"
            )
            .define("bossVariantsEnabled", true);

        builder.pop();

        builder.push("boss");

        BOSS_ENABLED = builder
            .comment(
                "Master switch for the whole boss tier (#200). Default true. When false:",
                "- the four prime-only boss variants (wither skull, nether star, dragon egg, dragon breath)",
                "  are suppressed exactly like variants.bossVariantsEnabled=false (unprimable, undiscovered,",
                "  hidden from JEI + the creative tab), which also removes their toxic milk and altar gating;",
                "- the four catalyst-altar blocks become uncraftable and hidden from JEI + the creative tab;",
                "- the boss Froglight smelt-back recipes are dropped.",
                "Lets a pack run the standard froglight loop with no boss farming in one toggle. The narrower",
                "variants.bossVariantsEnabled stays available to drop just the variants while keeping the altar",
                "blocks craftable. Recipe gating needs a world reload."
            )
            .define("enabled", true);

        builder.push("dragon_altar");

        DRAGON_ALTAR_SUMMON_TICKS = builder
            .comment(
                "End Dragon Altar (#249): length in ticks of the summon sequence - the converging crystal",
                "beams and the dragon model growing from tiny to full before the plinth frog eats it.",
                "Default 200 (10s). Purely the show length; the rewards land at the end regardless."
            )
            .defineInRange("summonTicks", DEFAULT_DRAGON_ALTAR_SUMMON_TICKS, 1, 24000);

        DRAGON_ALTAR_XP_REWARD = builder
            .comment(
                "Experience granted per completed altar summon. Default 500 (one vanilla first-kill's worth;",
                "vanilla respawns grant none). Set 0 to grant no XP."
            )
            .defineInRange("xpReward", DEFAULT_DRAGON_ALTAR_XP_REWARD, 0, 100000);

        DRAGON_ALTAR_REPEATABLE_EGG = builder
            .comment(
                "Whether each altar summon deposits a Dragon Egg Froglight into the Hatch (it smelts back to a",
                "Dragon Egg). Default true (the altar is a renewable-egg farm). When false the egg is granted only",
                "the way vanilla does it - never by the altar - so the altar still pays out the Dragon Breath",
                "Froglight and the dragon's drops, but no renewable egg."
            )
            .define("repeatableEgg", DEFAULT_DRAGON_ALTAR_REPEATABLE_EGG);

        builder.pop();

        builder.push("wither_altar");

        WITHER_ALTAR_SUMMON_TICKS = builder
            .comment(
                "Wither Altar (#247): length in ticks of the summon - the replica Wither's charging spawn",
                "before Witherbane devours it. Default 220 (matches the vanilla invulnerable spawn). The",
                "rewards land at the end regardless."
            )
            .defineInRange("summonTicks", DEFAULT_WITHER_ALTAR_SUMMON_TICKS, 1, 24000);

        WITHER_ALTAR_XP_REWARD = builder
            .comment(
                "Experience granted per completed Wither Altar summon. Default 50 (a vanilla Wither's value).",
                "Set 0 to grant no XP."
            )
            .defineInRange("xpReward", DEFAULT_WITHER_ALTAR_XP_REWARD, 0, 100000);

        builder.pop();

        builder.pop();

        SPEC = builder.build();
    }

    /**
     * True if {@code o} is a parseable resource-location string (variant id list
     * element). Only checks syntax, not registry membership: the slime_variant
     * datapack registry loads after this config spec, so a registry lookup here is
     * impossible. A typo'd id is therefore accepted and simply matches nothing
     * (the variant stays enabled) - intentional, since the list may legitimately
     * name a pack/datapack variant not present this launch.
     */
    private static boolean isValidVariantId(Object o) {
        return o instanceof String s && Identifier.tryParse(s) != null;
    }

    /** True if {@code o} is one of the six lowercase {@link Category} names. */
    private static boolean isValidCategoryName(Object o) {
        if (!(o instanceof String s)) {
            return false;
        }
        for (Category c : Category.values()) {
            if (c.id().equals(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if {@code o} is a syntactically valid modid string (the integration-key
     * charset: lowercase letters, digits, {@code _ - .}). Membership isn't checked
     * - the mod may be absent this launch, in which case its variants already don't
     * load and listing it is a harmless no-op.
     */
    private static boolean isValidIntegrationKey(Object o) {
        return o instanceof String s && !s.isEmpty() && s.matches("[a-z0-9_.-]+");
    }

    // ------------------------------------------------------------------
    // Breeding/stats accessors: read the live config when loaded, else the
    // compile-time default. These collapse the `SPEC.isLoaded() ? X.get() : lit`
    // pattern that was hand-copied across ResourceFrog, the drop handler, the
    // sensor, and the Jade plugin into one place per value.
    // ------------------------------------------------------------------

    /** Whether the frog stat-breeding layer is enabled ({@code frog_stats.enabled}, #202); fallback true. */
    public static boolean frogStatsEnabled() {
        return !SPEC.isLoaded() || FROG_STATS_ENABLED.get();
    }

    /** Per-stat cap ({@code breeding.statCap}); fallback {@value #DEFAULT_STAT_CAP}. */
    public static int statCap() {
        return SPEC.isLoaded() ? BREEDING_STAT_CAP.get() : DEFAULT_STAT_CAP;
    }

    /** Whether the same-species breeding gate is on ({@code breeding.sameSpeciesOnly}); fallback true. */
    public static boolean sameSpeciesOnly() {
        return !SPEC.isLoaded() || BREEDING_SAME_SPECIES_ONLY.get();
    }

    /** Per-stat improvement chance ({@code breeding.improvementChance}); fallback {@value #DEFAULT_IMPROVEMENT_CHANCE}. */
    public static double improvementChance() {
        return SPEC.isLoaded() ? BREEDING_IMPROVEMENT_CHANCE.get() : DEFAULT_IMPROVEMENT_CHANCE;
    }

    /** Whether every breed improves at least one stat over the blend ({@code breeding.guaranteedImprovement}); fallback true. */
    public static boolean guaranteedImprovement() {
        return !SPEC.isLoaded() || BREEDING_GUARANTEED_IMPROVEMENT.get();
    }

    /** Eat cooldown (ticks) at max Appetite ({@code stats.appetiteCooldownMin}); fallback {@value #DEFAULT_APPETITE_COOLDOWN_MIN}. */
    public static int appetiteCooldownMin() {
        return SPEC.isLoaded() ? STATS_APPETITE_COOLDOWN_MIN.get() : DEFAULT_APPETITE_COOLDOWN_MIN;
    }

    /** Eat cooldown (ticks) at Appetite 1 ({@code stats.appetiteCooldownMax}); fallback {@value #DEFAULT_APPETITE_COOLDOWN_MAX}. */
    public static int appetiteCooldownMax() {
        return SPEC.isLoaded() ? STATS_APPETITE_COOLDOWN_MAX.get() : DEFAULT_APPETITE_COOLDOWN_MAX;
    }

    /** Froglight drops at max Bounty ({@code stats.bountyMaxDrops}); fallback {@value #DEFAULT_BOUNTY_MAX_DROPS}. */
    public static int bountyMaxDrops() {
        return SPEC.isLoaded() ? STATS_BOUNTY_MAX_DROPS.get() : DEFAULT_BOUNTY_MAX_DROPS;
    }

    /** Prey-scan radius at Reach 1 ({@code stats.reachRadiusMin}); fallback {@value #DEFAULT_REACH_RADIUS_MIN}. */
    public static int reachRadiusMin() {
        return SPEC.isLoaded() ? STATS_REACH_RADIUS_MIN.get() : DEFAULT_REACH_RADIUS_MIN;
    }

    /** Prey-scan radius at max Reach ({@code stats.reachRadiusMax}); fallback {@value #DEFAULT_REACH_RADIUS_MAX}. */
    public static int reachRadiusMax() {
        return SPEC.isLoaded() ? STATS_REACH_RADIUS_MAX.get() : DEFAULT_REACH_RADIUS_MAX;
    }

    /** Whether Resource Frogs are persistent ({@code frogs.persistent}); fallback true. */
    public static boolean frogsPersistent() {
        return !SPEC.isLoaded() || FROGS_PERSISTENT.get();
    }

    /** Whether the slime-density spawn cap is active ({@code spawnCapEnabled}); fallback true. */
    public static boolean spawnCapEnabled() {
        return !SPEC.isLoaded() || SPAWN_CAP_ENABLED.get();
    }

    /** Max same-species slimes near a source before it pauses ({@code maxNearbySlimes}); fallback {@value #DEFAULT_MAX_NEARBY_SLIMES}. */
    public static int maxNearbySlimes() {
        return SPEC.isLoaded() ? MAX_NEARBY_SLIMES.get() : DEFAULT_MAX_NEARBY_SLIMES;
    }

    /** Block radius the spawn cap counts slimes in ({@code spawnCapRadius}); fallback {@value #DEFAULT_SPAWN_CAP_RADIUS}. */
    public static int spawnCapRadius() {
        return SPEC.isLoaded() ? SPAWN_CAP_RADIUS.get() : DEFAULT_SPAWN_CAP_RADIUS;
    }

    /** Fixed hatch delay in ticks for primed frogspawn ({@code lifecycle.primedFrogspawnHatchTicks}); fallback {@value #DEFAULT_HATCH_TICKS}. */
    public static int hatchTicks() {
        return SPEC.isLoaded() ? LIFECYCLE_HATCH_TICKS.get() : DEFAULT_HATCH_TICKS;
    }

    /** Tadpole maturation time in ticks ({@code lifecycle.tadpoleGrowthTicks}); fallback {@value #DEFAULT_TADPOLE_GROWTH_TICKS}. */
    public static int tadpoleGrowthTicks() {
        return SPEC.isLoaded() ? LIFECYCLE_TADPOLE_GROWTH_TICKS.get() : DEFAULT_TADPOLE_GROWTH_TICKS;
    }

    /** Post-breed re-breed cooldown in ticks ({@code lifecycle.breedingCooldownTicks}); fallback {@value #DEFAULT_BREEDING_COOLDOWN_TICKS}. */
    public static int breedingCooldownTicks() {
        return SPEC.isLoaded() ? LIFECYCLE_BREEDING_COOLDOWN_TICKS.get() : DEFAULT_BREEDING_COOLDOWN_TICKS;
    }

    // ------------------------------------------------------------------
    // Terrarium accessors (#185, docs/terrarium.md).
    // ------------------------------------------------------------------

    /** Cavity slime cap ({@code terrarium.slimeCap}); fallback {@value #DEFAULT_TERRARIUM_SLIME_CAP}. */
    public static int terrariumSlimeCap() {
        return SPEC.isLoaded() ? TERRARIUM_SLIME_CAP.get() : DEFAULT_TERRARIUM_SLIME_CAP;
    }

    /** Frog cap ({@code terrarium.frogCap}); fallback {@value #DEFAULT_TERRARIUM_FROG_CAP}. */
    public static int terrariumFrogCap() {
        return SPEC.isLoaded() ? TERRARIUM_FROG_CAP.get() : DEFAULT_TERRARIUM_FROG_CAP;
    }

    /** Controller milk-charge buffer depth ({@code terrarium.controllerBufferDepth}); fallback {@value #DEFAULT_TERRARIUM_CONTROLLER_BUFFER_DEPTH}. */
    public static int terrariumControllerBufferDepth() {
        return SPEC.isLoaded() ? TERRARIUM_CONTROLLER_BUFFER_DEPTH.get() : DEFAULT_TERRARIUM_CONTROLLER_BUFFER_DEPTH;
    }

    /** Controller re-validation cadence in ticks ({@code terrarium.validationIntervalTicks}); fallback {@value #DEFAULT_TERRARIUM_VALIDATION_INTERVAL_TICKS}. */
    public static int terrariumValidationIntervalTicks() {
        return SPEC.isLoaded() ? TERRARIUM_VALIDATION_INTERVAL_TICKS.get() : DEFAULT_TERRARIUM_VALIDATION_INTERVAL_TICKS;
    }

    /** Sprinkler top-up threshold ({@code terrarium.sprinklerTopUpThreshold}); fallback {@value #DEFAULT_TERRARIUM_SPRINKLER_TOPUP_THRESHOLD}. */
    public static int terrariumSprinklerTopUpThreshold() {
        return SPEC.isLoaded() ? TERRARIUM_SPRINKLER_TOPUP_THRESHOLD.get() : DEFAULT_TERRARIUM_SPRINKLER_TOPUP_THRESHOLD;
    }

    /** Sweetslime acceleration percent ({@code terrarium.sweetslimeAcceleratePercent}); fallback {@value #DEFAULT_TERRARIUM_SWEETSLIME_ACCEL_PERCENT}. */
    public static int terrariumSweetslimeAcceleratePercent() {
        return SPEC.isLoaded() ? TERRARIUM_SWEETSLIME_ACCEL_PERCENT.get() : DEFAULT_TERRARIUM_SWEETSLIME_ACCEL_PERCENT;
    }

    /** Hatch auto-collect cadence in ticks ({@code terrarium.hatchVacuumIntervalTicks}); fallback {@value #DEFAULT_TERRARIUM_HATCH_VACUUM_INTERVAL_TICKS}. */
    public static int terrariumHatchVacuumIntervalTicks() {
        return SPEC.isLoaded() ? TERRARIUM_HATCH_VACUUM_INTERVAL_TICKS.get() : DEFAULT_TERRARIUM_HATCH_VACUUM_INTERVAL_TICKS;
    }

    // ------------------------------------------------------------------
    // Variant / category scoping (#203). One predicate, read at every variant
    // resolution site so a disabled variant is unprimable, undiscoverable, and
    // invisible. Fails open (everything enabled) until the spec loads.
    // ------------------------------------------------------------------

    /**
     * Whether a variant is enabled given its id, category, and weight. The single
     * gate for {@code SlimeVariant} resolution, JEI, and the creative tab. Fails
     * open before the config loads (mod-init, title screen) so the default - and
     * any pre-config-load resolution - treats everything as enabled (non-breaking).
     *
     * <p>Order: a {@code weight 0} boss variant is gated by {@link #bossVariantsEnabled()};
     * then a disabled category; then an explicitly disabled id.
     *
     * <p><b>Extending this (e.g. #204 per-integration force-off):</b> add the new
     * dimension <i>inside</i> {@link com.flatts.productivefrogs.data.SlimeVariant#isEnabled}
     * (which can read the
     * variant's own fields - {@code primer_tag} etc. - to resolve its integration)
     * rather than threading a new parameter through this signature. Every
     * resolution site (priming, discovery, JEI, the creative tab) routes through
     * {@code variant.isEnabled(id)}, so a clause added there is picked up by all of
     * them with no call-site changes.
     */
    public static boolean variantEnabled(Identifier id, Category category, int weight) {
        if (!SPEC.isLoaded()) {
            return true;
        }
        // A weight-0 boss variant is gated by BOTH the boss master (#200) and the
        // narrow per-variant switch (#203): either being off suppresses it.
        if (weight == 0 && !(BOSS_ENABLED.get() && BOSS_VARIANTS_ENABLED.get())) {
            return false;
        }
        if (DISABLED_CATEGORIES.get().contains(category.id())) {
            return false;
        }
        return !DISABLED_VARIANTS.get().contains(id.toString());
    }

    /**
     * Whether the boss-tier prime-only variants are effectively enabled - the boss
     * master ({@code boss.enabled}, #200) AND the narrow switch
     * ({@code variants.bossVariantsEnabled}, #203). Fallback true.
     */
    public static boolean bossVariantsEnabled() {
        return !SPEC.isLoaded() || (BOSS_ENABLED.get() && BOSS_VARIANTS_ENABLED.get());
    }

    /** Whether the boss tier master is on ({@code boss.enabled}, #200); fallback true. */
    public static boolean bossEnabled() {
        return !SPEC.isLoaded() || BOSS_ENABLED.get();
    }

    /** End Dragon Altar summon length in ticks ({@code boss.dragon_altar.summonTicks}, #249); fallback {@value #DEFAULT_DRAGON_ALTAR_SUMMON_TICKS}. */
    public static int dragonAltarSummonTicks() {
        return SPEC.isLoaded() ? DRAGON_ALTAR_SUMMON_TICKS.get() : DEFAULT_DRAGON_ALTAR_SUMMON_TICKS;
    }

    /** XP granted per completed altar summon ({@code boss.dragon_altar.xpReward}, #249); fallback {@value #DEFAULT_DRAGON_ALTAR_XP_REWARD}. */
    public static int dragonAltarXpReward() {
        return SPEC.isLoaded() ? DRAGON_ALTAR_XP_REWARD.get() : DEFAULT_DRAGON_ALTAR_XP_REWARD;
    }

    /** Whether each altar summon deposits a Dragon Egg Froglight ({@code boss.dragon_altar.repeatableEgg}, #249); fallback {@value #DEFAULT_DRAGON_ALTAR_REPEATABLE_EGG}. */
    public static boolean dragonAltarRepeatableEgg() {
        return SPEC.isLoaded() ? DRAGON_ALTAR_REPEATABLE_EGG.get() : DEFAULT_DRAGON_ALTAR_REPEATABLE_EGG;
    }

    /** Wither Altar summon length in ticks ({@code boss.wither_altar.summonTicks}, #247); fallback {@value #DEFAULT_WITHER_ALTAR_SUMMON_TICKS}. */
    public static int witherAltarSummonTicks() {
        return SPEC.isLoaded() ? WITHER_ALTAR_SUMMON_TICKS.get() : DEFAULT_WITHER_ALTAR_SUMMON_TICKS;
    }

    /** XP granted per completed Wither Altar summon ({@code boss.wither_altar.xpReward}, #247); fallback {@value #DEFAULT_WITHER_ALTAR_XP_REWARD}. */
    public static int witherAltarXpReward() {
        return SPEC.isLoaded() ? WITHER_ALTAR_XP_REWARD.get() : DEFAULT_WITHER_ALTAR_XP_REWARD;
    }

    /**
     * Whether {@code id}'s provider integration is force-disabled
     * ({@code variants.disabledIntegrations}, #204). True only for a known cross-mod
     * variant whose every provider mod is listed; fails closed (not disabled) before
     * the config loads. Consulted by {@link com.flatts.productivefrogs.data.SlimeVariant#isEnabled}.
     */
    public static boolean integrationDisabled(Identifier id) {
        return SPEC.isLoaded()
            && com.flatts.productivefrogs.setup.VariantIntegrations.allProvidersDisabled(id, DISABLED_INTEGRATIONS.get());
    }

    // ------------------------------------------------------------------
    // Slime Milk catalyst accessors (docs/slime_milk_catalysts.md). Read the
    // live config when loaded, else the compile-time default - the source block
    // and its BlockEntity touch these on the server tick path.
    // ------------------------------------------------------------------

    /** Whether Brewed Froglights are enabled ({@code brewed_froglights.enabled}); fallback true. */
    public static boolean brewedFroglightsEnabled() {
        return !SPEC.isLoaded() || BREWED_FROGLIGHTS_ENABLED.get();
    }

    /** Whether the Frog Net is enabled ({@code frog_net.enabled}); fallback true. */
    public static boolean frogNetEnabled() {
        return !SPEC.isLoaded() || FROG_NET_ENABLED.get();
    }

    /** Whether killing a frog drops Frog Legs ({@code frog_legs.enabled}); fallback true. */
    public static boolean frogLegsEnabled() {
        return !SPEC.isLoaded() || FROG_LEGS_ENABLED.get();
    }

    /** Whether the Potion of Hopping is enabled ({@code hopping.enabled}); fallback true. */
    public static boolean hoppingEnabled() {
        return !SPEC.isLoaded() || HOPPING_ENABLED.get();
    }

    /**
     * Test-only override for {@link #equivalenceEnabled()}. The EE lane defaults OFF,
     * so the GameTests that drive the Alembic/Distiller directly set this true (mirrors
     * {@code SlimeMilkSourceBlock.depletionEnabledOverride}). Null in production.
     */
    @org.jetbrains.annotations.Nullable
    public static Boolean equivalenceEnabledOverride;

    /**
     * Dev-only force-on for the EE lane: our interactive runs (build.gradle client/server)
     * pass {@code -Dproductivefrogs.equivalence=true} so the lane is usable in the dev
     * environment even though it ships default OFF. Read once at class load; no production
     * JVM sets it, so shipped behaviour is unaffected.
     */
    private static final boolean DEV_FORCE_EQUIVALENCE = Boolean.getBoolean("productivefrogs.equivalence");

    /** Whether the Equivalence lane is enabled ({@code equivalence.enabled}, #253); fallback OFF. */
    public static boolean equivalenceEnabled() {
        if (equivalenceEnabledOverride != null) {
            return equivalenceEnabledOverride;
        }
        if (DEV_FORCE_EQUIVALENCE) {
            return true;
        }
        return SPEC.isLoaded() && EQUIVALENCE_ENABLED.get();
    }

    /** Whether the Froglight Cleaver is enabled ({@code froglight_weapon.enabled}); fallback true. */
    public static boolean froglightWeaponEnabled() {
        return !SPEC.isLoaded() || FROGLIGHT_WEAPON_ENABLED.get();
    }

    /** Whether the Princess's Kiss is enabled ({@code princess_kiss.enabled}); fallback true. */
    public static boolean princessKissEnabled() {
        return !SPEC.isLoaded() || PRINCESS_KISS_ENABLED.get();
    }

    /** Whether the Sweetslimed lily pad perch is enabled ({@code lily_pad_perch.enabled}); fallback true. */
    public static boolean lilyPadPerchEnabled() {
        return !SPEC.isLoaded() || LILY_PAD_PERCH_ENABLED.get();
    }

    /** How far a frog travels to a sweetslimed lily pad ({@code lily_pad_perch.range}); fallback {@value #DEFAULT_LILY_PAD_PERCH_RANGE}. */
    public static int lilyPadPerchRange() {
        return SPEC.isLoaded() ? LILY_PAD_PERCH_RANGE.get() : DEFAULT_LILY_PAD_PERCH_RANGE;
    }

    /** Whether Slime Milk catalysts are enabled ({@code slime_milk_catalysts.enabled}); fallback true. */
    public static boolean milkCatalystsEnabled() {
        return !SPEC.isLoaded() || MILK_CATALYSTS_ENABLED.get();
    }

    // The four per-catalyst accessors are the runtime (fail-open) copy of the
    // master-AND-child relationship; ConfigEnabledCondition.Key.{COUNT,SPEED,
    // QUANTITY,INFINITE}_CATALYST.read() is the recipe-load (fail-closed) copy.
    // Keep the two in sync if the relationship changes (#201).

    /** Whether the Count catalyst is enabled (master AND {@code slime_milk_catalysts.count}); fallback true. */
    public static boolean catalystCountEnabled() {
        return !SPEC.isLoaded() || (MILK_CATALYSTS_ENABLED.get() && CATALYST_COUNT_ENABLED.get());
    }

    /** Whether the Speed catalyst is enabled (master AND {@code slime_milk_catalysts.speed}); fallback true. */
    public static boolean catalystSpeedEnabled() {
        return !SPEC.isLoaded() || (MILK_CATALYSTS_ENABLED.get() && CATALYST_SPEED_ENABLED.get());
    }

    /** Whether the Quantity catalyst is enabled (master AND {@code slime_milk_catalysts.quantity}); fallback true. */
    public static boolean catalystQuantityEnabled() {
        return !SPEC.isLoaded() || (MILK_CATALYSTS_ENABLED.get() && CATALYST_QUANTITY_ENABLED.get());
    }

    /** Whether the Infinite catalyst is enabled (master AND {@code slime_milk_catalysts.infinite}); fallback true. */
    public static boolean catalystInfiniteEnabled() {
        return !SPEC.isLoaded() || (MILK_CATALYSTS_ENABLED.get() && CATALYST_INFINITE_ENABLED.get());
    }

    /** Spawns added per Count catalyst ({@code slime_milk_catalysts.countPerCatalyst}); fallback {@value #DEFAULT_CATALYST_COUNT_PER}. */
    public static int catalystCountPer() {
        return SPEC.isLoaded() ? CATALYST_COUNT_PER.get() : DEFAULT_CATALYST_COUNT_PER;
    }

    /** Max Speed catalyst level ({@code slime_milk_catalysts.maxSpeedLevel}); fallback {@value #DEFAULT_CATALYST_MAX_SPEED_LEVEL}. */
    public static int catalystMaxSpeedLevel() {
        return SPEC.isLoaded() ? CATALYST_MAX_SPEED_LEVEL.get() : DEFAULT_CATALYST_MAX_SPEED_LEVEL;
    }

    /** Max Quantity catalyst level ({@code slime_milk_catalysts.maxQuantityLevel}); fallback {@value #DEFAULT_CATALYST_MAX_QUANTITY_LEVEL}. */
    public static int catalystMaxQuantityLevel() {
        return SPEC.isLoaded() ? CATALYST_MAX_QUANTITY_LEVEL.get() : DEFAULT_CATALYST_MAX_QUANTITY_LEVEL;
    }

    /** Spawn-interval reduction per Speed level ({@code slime_milk_catalysts.speedReductionPerLevel}); fallback {@value #DEFAULT_CATALYST_SPEED_REDUCTION_PER_LEVEL}. */
    public static double catalystSpeedReductionPerLevel() {
        return SPEC.isLoaded() ? CATALYST_SPEED_REDUCTION_PER_LEVEL.get() : DEFAULT_CATALYST_SPEED_REDUCTION_PER_LEVEL;
    }

    /** Floor (ticks) the Speed-reduced interval cannot drop below ({@code slime_milk_catalysts.minIntervalFloorTicks}); fallback {@value #DEFAULT_CATALYST_MIN_INTERVAL_FLOOR_TICKS}. */
    public static int catalystMinIntervalFloorTicks() {
        return SPEC.isLoaded() ? CATALYST_MIN_INTERVAL_FLOOR_TICKS.get() : DEFAULT_CATALYST_MIN_INTERVAL_FLOOR_TICKS;
    }

    private PFConfig() {
        // utility class
    }
}
