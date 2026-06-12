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
     * Roll one offspring <b>talent</b> (the ceiling a stat can be trained to)
     * from its two parents' talents (R1/R2, docs/frog_stats_redesign.md). With
     * {@code hi = max(a, b)}:
     *
     * <ul>
     *   <li>with probability {@code improvementChance}: {@code min(cap, hi + 1)} (the climb)</li>
     *   <li>else: {@code hi} (hold at the better parent)</li>
     * </ul>
     *
     * <b>There is no regression branch</b> - a bred talent never drops below the
     * better parent. Earned progress (a high-talent bloodline) is never taken
     * away, which is the core of the redesign's "no loss aversion" rule. So a
     * single champion can uplift a line by breeding against fresh frogs: the
     * offspring's ceiling is at least the champion's. The result is clamped
     * defensively even though {@code min(cap, ...)} already respects the cap.
     */
    public static int inheritTalent(int parentA, int parentB, double improvementChance,
                                    int cap, RandomSource random) {
        int hi = Math.max(parentA, parentB);
        int result = random.nextDouble() < improvementChance ? Math.min(cap, hi + 1) : hi;
        return clamp(result, cap);
    }

    /**
     * The training level (= total stat points earned) a frog has accrued for a
     * given cumulative training-XP total: one point per {@code xpPerLevel} XP.
     * A point raises one live stat by 1 toward its talent ceiling
     * (docs/frog_stats_redesign.md). Defensive against a zero/negative
     * {@code xpPerLevel} (treats it as 1) so a misconfigured pack can't divide
     * by zero. Never negative.
     */
    public static int trainingLevel(long totalXp, int xpPerLevel) {
        long perLevel = Math.max(1, xpPerLevel);
        return (int) Math.max(0, totalXp / perLevel);
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
