package com.flatts.productivefrogs;

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
    public static final ModConfigSpec.IntValue CATALYST_COUNT_PER;
    public static final ModConfigSpec.IntValue CATALYST_MAX_SPEED_LEVEL;
    public static final ModConfigSpec.IntValue CATALYST_MAX_QUANTITY_LEVEL;
    public static final ModConfigSpec.DoubleValue CATALYST_SPEED_REDUCTION_PER_LEVEL;
    public static final ModConfigSpec.IntValue CATALYST_MIN_INTERVAL_FLOOR_TICKS;
    public static final ModConfigSpec.DoubleValue DISCOVERY_CHANCE_PER_OFFSPRING;
    public static final ModConfigSpec.BooleanValue SPAWNERY_ENABLED;
    public static final ModConfigSpec.IntValue SPAWNERY_PRODUCTION_TICKS;

    // Frog stat breeding (docs/frog_breeding.md).
    public static final ModConfigSpec.BooleanValue BREEDING_SAME_SPECIES_ONLY;
    public static final ModConfigSpec.DoubleValue BREEDING_IMPROVEMENT_CHANCE;
    public static final ModConfigSpec.DoubleValue BREEDING_REGRESSION_CHANCE;
    public static final ModConfigSpec.IntValue BREEDING_STAT_CAP;
    public static final ModConfigSpec.IntValue STATS_APPETITE_COOLDOWN_MIN;
    public static final ModConfigSpec.IntValue STATS_APPETITE_COOLDOWN_MAX;
    public static final ModConfigSpec.IntValue STATS_BOUNTY_MAX_DROPS;
    public static final ModConfigSpec.IntValue STATS_REACH_RADIUS_MIN;
    public static final ModConfigSpec.IntValue STATS_REACH_RADIUS_MAX;
    public static final ModConfigSpec.BooleanValue FROGS_PERSISTENT;

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
    public static final double DEFAULT_IMPROVEMENT_CHANCE = 0.20;
    public static final double DEFAULT_REGRESSION_CHANCE = 0.30;
    public static final int DEFAULT_APPETITE_COOLDOWN_MIN = 30;
    public static final int DEFAULT_APPETITE_COOLDOWN_MAX = 100;
    public static final int DEFAULT_BOUNTY_MAX_DROPS = 3;
    public static final int DEFAULT_REACH_RADIUS_MIN = 8;
    public static final int DEFAULT_REACH_RADIUS_MAX = 16;
    public static final boolean DEFAULT_FROGS_PERSISTENT = true;
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
                "Per-stat chance the offspring rolls one above the better parent (min(cap, hi + 1)).",
                "Default 0.20. Raising this makes the climb to a maxed frog brisker."
            )
            .defineInRange("improvementChance", DEFAULT_IMPROVEMENT_CHANCE, 0.0, 1.0);

        BREEDING_REGRESSION_CHANCE = builder
            .comment(
                "Per-stat chance the offspring regresses to the parent average (round((hi + lo) / 2)).",
                "Default 0.30. Sampled after improvementChance from the same draw, so",
                "improvementChance + regressionChance must stay <= 1.0; the remainder is the 'hold at better parent' chance."
            )
            .defineInRange("regressionChance", DEFAULT_REGRESSION_CHANCE, 0.0, 1.0);

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

        SPEC = builder.build();
    }

    // ------------------------------------------------------------------
    // Breeding/stats accessors: read the live config when loaded, else the
    // compile-time default. These collapse the `SPEC.isLoaded() ? X.get() : lit`
    // pattern that was hand-copied across ResourceFrog, the drop handler, the
    // sensor, and the Jade plugin into one place per value.
    // ------------------------------------------------------------------

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

    /** Per-stat regression chance ({@code breeding.regressionChance}); fallback {@value #DEFAULT_REGRESSION_CHANCE}. */
    public static double regressionChance() {
        return SPEC.isLoaded() ? BREEDING_REGRESSION_CHANCE.get() : DEFAULT_REGRESSION_CHANCE;
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
    // Slime Milk catalyst accessors (docs/slime_milk_catalysts.md). Read the
    // live config when loaded, else the compile-time default - the source block
    // and its BlockEntity touch these on the server tick path.
    // ------------------------------------------------------------------

    /** Whether catalysts are enabled ({@code slime_milk_catalysts.enabled}); fallback true. */
    public static boolean milkCatalystsEnabled() {
        return !SPEC.isLoaded() || MILK_CATALYSTS_ENABLED.get();
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
