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
     * The <b>blend</b> layer of inheritance (D7): an offspring stat's base value is
     * the round-half-up average of its two parents' values, clamped to the cap.
     * This is the deterministic floor every offspring starts from before the climb
     * - so breeding two similar frogs holds their level (a 1-point gap rounds up to
     * the higher parent), while mixing a strong frog with a weak one blends toward
     * the middle. See docs/frog_breeding.md.
     */
    public static int blend(int parentA, int parentB, int cap) {
        return clamp((parentA + parentB + 1) / 2, cap);
    }

    /**
     * The <b>climb</b> layer applied on top of a blended base: with probability
     * {@code improvementChance} the stat ticks up one ({@code min(cap, base + 1)}),
     * otherwise it holds at the base. Never regresses below the base.
     */
    private static int climb(int base, double improvementChance, int cap, RandomSource random) {
        int result = random.nextDouble() < improvementChance ? Math.min(cap, base + 1) : base;
        return clamp(result, cap);
    }

    /**
     * Roll one offspring stat: {@link #blend} the parents, then apply the
     * {@link #climb} upgrade. There is no regression branch - the blended average
     * is the floor and the climb only ever goes up, so a breed never produces a
     * stat below the average of its parents.
     */
    public static int inheritStat(int parentA, int parentB, double improvementChance,
                                  int cap, RandomSource random) {
        return climb(blend(parentA, parentB, cap), improvementChance, cap, random);
    }

    /**
     * Roll all three offspring stats {@code {appetite, bounty, reach}} from the two
     * parents' stat triples, applying the breeding model (D7, docs/frog_breeding.md):
     *
     * <ol>
     *   <li><b>Blend</b> - each stat's base is the round-half-up parent average
     *       ({@link #blend}); this is the deterministic floor (no random regression).</li>
     *   <li><b>Climb</b> - each stat independently has an {@code improvementChance}
     *       to tick one above its base.</li>
     *   <li><b>Guaranteed progress</b> - when {@code guaranteeImprovement} is true and
     *       no stat climbed above its base, one stat that still has headroom
     *       ({@code base < cap}, chosen uniformly at random) is bumped to {@code base + 1},
     *       so every breed improves at least one stat over the blend. The only breed
     *       that can't is one whose blended bases are already all at the cap.</li>
     * </ol>
     *
     * Each result lands in {@code [base, base + 1]} for its stat, so an offspring
     * never falls below the parent average nor exceeds it by more than one.
     */
    public static int[] inheritStats(int[] parentA, int[] parentB, double improvementChance,
                                     boolean guaranteeImprovement, int cap, RandomSource random) {
        int[] base = new int[3];
        int[] out = new int[3];
        boolean improved = false;
        for (int i = 0; i < 3; i++) {
            base[i] = blend(parentA[i], parentB[i], cap);
            out[i] = climb(base[i], improvementChance, cap, random);
            if (out[i] > base[i]) {
                improved = true;
            }
        }
        if (guaranteeImprovement && !improved) {
            // No stat happened to climb: bump one random stat that still has headroom
            // (blended base below the cap), so a breed is never a wasted generation.
            int[] headroom = new int[3];
            int n = 0;
            for (int i = 0; i < 3; i++) {
                if (base[i] < cap) {
                    headroom[n++] = i;
                }
            }
            if (n > 0) {
                int pick = headroom[random.nextInt(n)];
                out[pick] = clamp(base[pick] + 1, cap);
            }
        }
        return out;
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

    /**
     * Map a Bounty stat to the looting level a Predator Frog's eat applies to
     * the prey's player-kill loot roll (#281: "looting = Bounty" - base Bounty
     * -> Looting 0, the cap -> Looting III, linear in between). The same stat
     * that scales Froglight drops on the slime side scales rare-drop odds on
     * the predation side, so bred investment carries across both.
     */
    public static int bountyLootingLevel(int bounty, int cap) {
        int b = clamp(bounty, cap);
        if (cap <= STAT_MIN) {
            return 3;
        }
        double t = (double) (b - STAT_MIN) / (cap - STAT_MIN);
        return (int) Math.round(t * 3.0);
    }
}
