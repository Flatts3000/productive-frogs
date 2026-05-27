package com.flatts.productivefrogs.content.entity;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The three breeding stats a {@link ResourceFrog} carries and the pure
 * functions that govern them - inheritance, clamping, and the stat-to-effect
 * curves. Kept separate from the entity so the math is unit-testable without a
 * live Minecraft instance (see {@code FrogStatsTest}).
 *
 * <p>Per {@code docs/frog_breeding.md}: each stat is an integer in
 * {@code [STAT_MIN, statCap]} (default {@code [1, 10]}). {@code 10/10/10} is
 * "maxed". All curves are config-tunable - the constants here are only the
 * compile-time fallback anchors; runtime reads go through {@link com.flatts.productivefrogs.PFConfig}.
 *
 * <ul>
 *   <li><b>Appetite</b> shortens the eat cooldown (faster tongue cadence).</li>
 *   <li><b>Bounty</b> multiplies the Froglight drop count per slime.</li>
 *   <li><b>Reach</b> widens the prey-detection scan radius.</li>
 * </ul>
 */
public final class FrogStats {

    /** Hard floor for every stat. The cap is config-driven ({@code breeding.statCap}). */
    public static final int STAT_MIN = 1;

    private FrogStats() {
        // utility class
    }

    /**
     * Clamp a raw stat value into {@code [STAT_MIN, cap]}. Used by every
     * getter/setter on {@link ResourceFrog} so a tampered or version-skewed
     * save can never inject an out-of-range stat, and by the inheritance roll.
     */
    public static int clamp(int value, int cap) {
        return Mth.clamp(value, STAT_MIN, cap);
    }

    /**
     * Roll one offspring stat from its two parent values per the inheritance
     * rule (D7). With {@code hi = max(a, b)} and {@code lo = min(a, b)}:
     *
     * <ul>
     *   <li>with probability {@code improvementChance}: {@code min(cap, hi + 1)} (the climb)</li>
     *   <li>else with probability {@code regressionChance}: {@code round((hi + lo) / 2)} (regression to mean)</li>
     *   <li>else: {@code hi} (hold at the better parent)</li>
     * </ul>
     *
     * The two chances are sampled from a single uniform draw so they partition
     * the {@code [0, 1)} interval cleanly (improvement first, then regression,
     * then hold). When both parents are equal, regression is a no-op
     * ({@code average == hi}), so an equal pair only ever holds or ticks up -
     * the intended late-game grind. The result is clamped defensively even
     * though the formula already respects the cap.
     */
    public static int inheritStat(int parentA, int parentB, double improvementChance,
                                  double regressionChance, int cap, RandomSource random) {
        int hi = Math.max(parentA, parentB);
        int lo = Math.min(parentA, parentB);
        double roll = random.nextDouble();
        int result;
        if (roll < improvementChance) {
            result = Math.min(cap, hi + 1);
        } else if (roll < improvementChance + regressionChance) {
            // Integer round-half-up of the parent average.
            result = (hi + lo + 1) / 2;
        } else {
            result = hi;
        }
        return clamp(result, cap);
    }

    /**
     * Map an Appetite stat to an eat-cooldown in ticks: stat 1 -> {@code maxTicks}
     * (slow), stat 10 -> {@code minTicks} (fast), linear in between. Higher
     * Appetite means a shorter cooldown, so the curve descends. Always returns
     * at least 1 tick.
     */
    public static int appetiteCooldownTicks(int appetite, int minTicks, int maxTicks, int cap) {
        int a = clamp(appetite, cap);
        if (cap <= STAT_MIN) {
            return Math.max(1, minTicks);
        }
        // Fraction of the way from stat-1 to stat-cap.
        double t = (double) (a - STAT_MIN) / (cap - STAT_MIN);
        double ticks = maxTicks + t * (minTicks - maxTicks);
        return Math.max(1, (int) Math.round(ticks));
    }

    /**
     * Map a Bounty stat to a Froglight drop count via a step curve: split
     * {@code [STAT_MIN, cap]} into {@code maxDrops} equal bands (width rounded up
     * so the top band reaches the cap), and the band index + 1 is the drop count.
     * For the shipped defaults ({@code maxDrops=3}, {@code cap=10}) the band width
     * is 4, giving <b>1 drop at Bounty 1-4, 2 at 5-8, 3 at 9-10</b>. (The spec's
     * illustrative "1-3 / 4-7 / 8-10" was approximate; the equal-band rule is what
     * generalizes when a pack changes {@code maxDrops} or {@code cap}.) Always
     * returns at least 1 and never more than {@code maxDrops}.
     */
    public static int bountyDropCount(int bounty, int maxDrops, int cap) {
        int b = clamp(bounty, cap);
        int drops = Math.max(1, maxDrops);
        if (drops == 1) {
            return 1;
        }
        // Split [STAT_MIN, cap] into `drops` equal bands; band index + 1 is the
        // drop count. Band width rounds up so the top band reaches the cap.
        int span = Math.max(1, cap - STAT_MIN + 1);
        int bandWidth = (span + drops - 1) / drops;
        int band = (b - STAT_MIN) / bandWidth;
        return Mth.clamp(band + 1, STAT_MIN, drops);
    }

    /**
     * Map a Reach stat to a prey-scan radius: stat 1 -> {@code minRadius},
     * stat 10 -> {@code maxRadius}, linear in between. Higher Reach means a
     * larger radius, so the curve ascends.
     */
    public static int reachRadius(int reach, int minRadius, int maxRadius, int cap) {
        int r = clamp(reach, cap);
        if (cap <= STAT_MIN) {
            return maxRadius;
        }
        double t = (double) (r - STAT_MIN) / (cap - STAT_MIN);
        double radius = minRadius + t * (maxRadius - minRadius);
        return Math.max(1, (int) Math.round(radius));
    }
}
