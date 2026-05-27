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
    public static final ModConfigSpec.DoubleValue DISCOVERY_CHANCE_PER_OFFSPRING;
    public static final ModConfigSpec.BooleanValue SPAWNERY_ENABLED;
    public static final ModConfigSpec.IntValue SPAWNERY_PRODUCTION_TICKS;

    // Frog stat breeding (docs/frog_breeding.md).
    public static final ModConfigSpec.BooleanValue BREEDING_SAME_SPECIES_ONLY;
    public static final ModConfigSpec.DoubleValue BREEDING_IMPROVEMENT_CHANCE;
    public static final ModConfigSpec.DoubleValue BREEDING_REGRESSION_CHANCE;
    public static final ModConfigSpec.IntValue BREEDING_STAT_CAP;
    public static final ModConfigSpec.IntValue BREEDING_STARTER_STAT_MIN;
    public static final ModConfigSpec.IntValue BREEDING_STARTER_STAT_MAX;
    public static final ModConfigSpec.IntValue STATS_APPETITE_COOLDOWN_MIN;
    public static final ModConfigSpec.IntValue STATS_APPETITE_COOLDOWN_MAX;
    public static final ModConfigSpec.IntValue STATS_BOUNTY_MAX_DROPS;
    public static final ModConfigSpec.IntValue STATS_REACH_RADIUS_MIN;
    public static final ModConfigSpec.IntValue STATS_REACH_RADIUS_MAX;
    public static final ModConfigSpec.BooleanValue FROGS_PERSISTENT;

    // Single source of truth for the breeding/stats defaults: used both by the
    // ModConfigSpec definitions below AND by the accessor methods' pre-config-load
    // fallbacks, so the spec default and the fallback can never drift apart.
    public static final int DEFAULT_STAT_CAP = 10;
    public static final double DEFAULT_IMPROVEMENT_CHANCE = 0.20;
    public static final double DEFAULT_REGRESSION_CHANCE = 0.30;
    public static final int DEFAULT_STARTER_STAT_MIN = 1;
    public static final int DEFAULT_STARTER_STAT_MAX = 3;
    public static final int DEFAULT_APPETITE_COOLDOWN_MIN = 30;
    public static final int DEFAULT_APPETITE_COOLDOWN_MAX = 100;
    public static final int DEFAULT_BOUNTY_MAX_DROPS = 3;
    public static final int DEFAULT_REACH_RADIUS_MIN = 8;
    public static final int DEFAULT_REACH_RADIUS_MAX = 16;
    public static final boolean DEFAULT_FROGS_PERSISTENT = true;

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

        // Capped at 16 because the SPAWNS_REMAINING block-state property has
        // range [0, 16] — values above 16 would not fit. The cap can be
        // raised by widening the IntegerProperty range, at the cost of more
        // blockstate combinations (each extra value × 14 milk variants × 9
        // fluid LEVEL values).
        DEPLETION_COUNT = builder
            .comment(
                "Number of slimes a source block produces before draining (depletionEnabled=true).",
                "Hard ceiling is 16 — that's the blockstate property's range. Default 16 (the full budget)."
            )
            .defineInRange("depletionCount", 16, 1, 16);

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

        BREEDING_STARTER_STAT_MIN = builder
            .comment("Lowest starter (non-bred) stat roll, inclusive. Default 1.")
            .defineInRange("starterStatMin", DEFAULT_STARTER_STAT_MIN, 1, 100);

        BREEDING_STARTER_STAT_MAX = builder
            .comment(
                "Highest starter (non-bred) stat roll, inclusive. Default 3.",
                "A freshly-acquired frog rolls each stat uniformly in [starterStatMin, starterStatMax];",
                "breeding is the only way to climb past this band."
            )
            .defineInRange("starterStatMax", DEFAULT_STARTER_STAT_MAX, 1, 100);

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

    /** Lowest starter stat roll ({@code breeding.starterStatMin}); fallback {@value #DEFAULT_STARTER_STAT_MIN}. */
    public static int starterStatMin() {
        return SPEC.isLoaded() ? BREEDING_STARTER_STAT_MIN.get() : DEFAULT_STARTER_STAT_MIN;
    }

    /** Highest starter stat roll ({@code breeding.starterStatMax}); fallback {@value #DEFAULT_STARTER_STAT_MAX}. */
    public static int starterStatMax() {
        return SPEC.isLoaded() ? BREEDING_STARTER_STAT_MAX.get() : DEFAULT_STARTER_STAT_MAX;
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

    private PFConfig() {
        // utility class
    }
}
