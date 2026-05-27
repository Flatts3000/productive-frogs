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
            .defineInRange("improvementChance", 0.20, 0.0, 1.0);

        BREEDING_REGRESSION_CHANCE = builder
            .comment(
                "Per-stat chance the offspring regresses to the parent average (round((hi + lo) / 2)).",
                "Default 0.30. Sampled after improvementChance from the same draw, so",
                "improvementChance + regressionChance must stay <= 1.0; the remainder is the 'hold at better parent' chance."
            )
            .defineInRange("regressionChance", 0.30, 0.0, 1.0);

        BREEDING_STAT_CAP = builder
            .comment("Maximum value any single stat can reach. Default 10 (the 'maxed' cap).")
            .defineInRange("statCap", 10, 1, 100);

        BREEDING_STARTER_STAT_MIN = builder
            .comment("Lowest starter (non-bred) stat roll, inclusive. Default 1.")
            .defineInRange("starterStatMin", 1, 1, 100);

        BREEDING_STARTER_STAT_MAX = builder
            .comment(
                "Highest starter (non-bred) stat roll, inclusive. Default 3.",
                "A freshly-acquired frog rolls each stat uniformly in [starterStatMin, starterStatMax];",
                "breeding is the only way to climb past this band."
            )
            .defineInRange("starterStatMax", 3, 1, 100);

        builder.pop();

        builder.push("stats");

        STATS_APPETITE_COOLDOWN_MIN = builder
            .comment("Eat cooldown in ticks at Appetite = statCap (fastest). Default 30 (1.5s).")
            .defineInRange("appetiteCooldownMin", 30, 1, 24000);

        STATS_APPETITE_COOLDOWN_MAX = builder
            .comment(
                "Eat cooldown in ticks at Appetite = 1 (slowest). Default 100 (5s).",
                "Should be >= appetiteCooldownMin; the curve interpolates linearly between the two."
            )
            .defineInRange("appetiteCooldownMax", 100, 1, 24000);

        STATS_BOUNTY_MAX_DROPS = builder
            .comment("Froglights dropped per slime at Bounty = statCap. Default 3 (step curve: 1 / 2 / 3).")
            .defineInRange("bountyMaxDrops", 3, 1, 64);

        STATS_REACH_RADIUS_MIN = builder
            .comment("Prey-scan radius in blocks at Reach = 1. Default 8.")
            .defineInRange("reachRadiusMin", 8, 1, 64);

        STATS_REACH_RADIUS_MAX = builder
            .comment(
                "Prey-scan radius in blocks at Reach = statCap. Default 16.",
                "Should be >= reachRadiusMin; the curve interpolates linearly between the two."
            )
            .defineInRange("reachRadiusMax", 16, 1, 64);

        builder.pop();

        builder.push("frogs");

        FROGS_PERSISTENT = builder
            .comment(
                "Whether Resource Frogs are persistent (never despawn). Default true.",
                "A bred-up frog is valuable; persistence prevents losing a stat line to despawn.",
                "They can still die to damage. Set false to let frogs despawn like vanilla animals."
            )
            .define("persistent", true);

        builder.pop();

        SPEC = builder.build();
    }

    private PFConfig() {
        // utility class
    }
}
