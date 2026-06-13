package com.flatts.productivefrogs.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * Pure-math unit tests for {@link FrogStats} - the inheritance roll, clamping,
 * and the three stat-to-effect curves (docs/frog_breeding.md). No Minecraft
 * bootstrap is needed: {@code Mth} and {@code RandomSource} are standalone
 * utilities.
 *
 * <p>The inheritance branches are made deterministic by driving the chances to
 * the extremes (1.0 / 0.0) rather than seeding the RNG to a known draw: any
 * draw in {@code [0, 1)} is {@code < 1.0} (always improve) and never {@code < 0.0}
 * (never that branch), so each branch is exercised regardless of the RNG.
 */
class FrogStatsTest {

    private static final int CAP = 10;
    private final RandomSource random = RandomSource.create(1234L);

    // ---- blend (the parent-average base layer) ------------------------

    @Test
    void blendIsTheRoundHalfUpParentAverage() {
        assertEquals(6, FrogStats.blend(7, 5, CAP), "(7+5)/2 = 6 exactly");
        assertEquals(9, FrogStats.blend(9, 8, CAP), "round((9+8)/2) = round(8.5) = 9 (half-up)");
        assertEquals(5, FrogStats.blend(6, 4, CAP), "(6+4)/2 = 5 exactly");
        assertEquals(9, FrogStats.blend(9, 9, CAP), "equal parents blend to themselves");
        assertEquals(6, FrogStats.blend(10, 2, CAP), "round((10+2)/2) = 6 - mismatched pairing blends down");
    }

    @Test
    void blendRoundHalfUpPreservesTheHigherParentOnAOnePointGap() {
        // The 1-gap rounds UP, so a single improver bred back with a parent keeps
        // the improvement (the climb propagates, doesn't stall).
        assertEquals(10, FrogStats.blend(10, 9, CAP), "round((10+9)/2) = round(9.5) = 10");
    }

    // ---- inheritStat (blend then climb, no regression) ----------------

    @Test
    void climbRollsOneAboveTheBlend() {
        // improvementChance 1.0 -> always climb: min(cap, blend + 1).
        assertEquals(7, FrogStats.inheritStat(7, 5, 1.0, CAP, random),
            "blend(7,5)=6, climb -> 7");
        assertEquals(10, FrogStats.inheritStat(9, 9, 1.0, CAP, random),
            "blend(9,9)=9, climb -> 10");
    }

    @Test
    void climbIsCappedAtTheStatCap() {
        assertEquals(CAP, FrogStats.inheritStat(10, 10, 1.0, CAP, random),
            "blend(10,10)=10, climb min(cap,11) stays at the cap");
    }

    @Test
    void holdReturnsTheBlendWhenClimbDoesNotFire() {
        // improvementChance 0.0 -> never climbs: the stat sits at the blended average.
        assertEquals(6, FrogStats.inheritStat(7, 5, 0.0, CAP, random), "holds at blend(7,5)=6");
        assertEquals(9, FrogStats.inheritStat(9, 8, 0.0, CAP, random), "holds at blend(9,8)=9");
    }

    // ---- inheritStats (triple roll + guaranteed progress) -------------

    @Test
    void inheritStatsNeverFallsBelowTheBlendNorExceedsItByMoreThanOne() {
        int[] a = {7, 4, 9};
        int[] b = {5, 8, 2};
        int[] base = {FrogStats.blend(7, 5, CAP), FrogStats.blend(4, 8, CAP), FrogStats.blend(9, 2, CAP)}; // 6/6/6
        for (int i = 0; i < 500; i++) {
            int[] out = FrogStats.inheritStats(a, b, 0.5, true, CAP, random);
            for (int s = 0; s < 3; s++) {
                assertTrue(out[s] >= base[s], "stat " + s + " must not fall below the blend " + base[s] + ", was " + out[s]);
                assertTrue(out[s] <= base[s] + 1, "stat " + s + " must not exceed blend+1, was " + out[s]);
            }
        }
    }

    @Test
    void guaranteedImprovementBumpsExactlyOneStatWhenNoneClimbed() {
        // improvementChance 0.0 -> no stat climbs on its own; the guarantee bumps one.
        int[] parents = {5, 5, 5};
        for (int i = 0; i < 50; i++) {
            int[] out = FrogStats.inheritStats(parents, parents, 0.0, true, CAP, random);
            int bumps = 0;
            for (int s = 0; s < 3; s++) {
                assertTrue(out[s] == 5 || out[s] == 6, "each stat is blend(5) or blend+1, was " + out[s]);
                if (out[s] == 6) {
                    bumps++;
                }
            }
            assertEquals(1, bumps, "exactly one stat should be bumped by the guarantee");
        }
    }

    @Test
    void guaranteedImprovementCannotExceedTheCapWhenAllStatsAreMaxed() {
        int[] maxed = {CAP, CAP, CAP};
        int[] out = FrogStats.inheritStats(maxed, maxed, 0.0, true, CAP, random);
        assertEquals(CAP, out[0]);
        assertEquals(CAP, out[1]);
        assertEquals(CAP, out[2]);
    }

    @Test
    void withoutGuaranteeAStatlessClimbHoldsAtTheBlend() {
        // guaranteeImprovement false + improvementChance 0.0 -> exactly the blend.
        int[] a = {5, 5, 5};
        int[] b = {3, 3, 3};
        int[] out = FrogStats.inheritStats(a, b, 0.0, false, CAP, random); // blend = 4/4/4
        assertEquals(4, out[0]);
        assertEquals(4, out[1]);
        assertEquals(4, out[2]);
    }

    // ---- clamp --------------------------------------------------------

    @Test
    void clampPinsToTheClosedRange() {
        assertEquals(FrogStats.STAT_MIN, FrogStats.clamp(0, CAP), "below floor clamps up to STAT_MIN");
        assertEquals(FrogStats.STAT_MIN, FrogStats.clamp(-5, CAP), "negative clamps to STAT_MIN");
        assertEquals(CAP, FrogStats.clamp(15, CAP), "above cap clamps down");
        assertEquals(5, FrogStats.clamp(5, CAP), "in-range value is unchanged");
    }

    // ---- appetiteCooldownTicks ---------------------------------------

    @Test
    void appetiteCooldownDescendsFromMaxAtStat1ToMinAtCap() {
        int min = 30;
        int max = 100;
        assertEquals(max, FrogStats.appetiteCooldownTicks(1, min, max, CAP),
            "stat 1 is the slowest (max cooldown)");
        assertEquals(min, FrogStats.appetiteCooldownTicks(CAP, min, max, CAP),
            "stat cap is the fastest (min cooldown)");
        // Monotonic non-increasing across the range.
        int prev = Integer.MAX_VALUE;
        for (int stat = 1; stat <= CAP; stat++) {
            int ticks = FrogStats.appetiteCooldownTicks(stat, min, max, CAP);
            assertTrue(ticks <= prev, "cooldown must not increase as Appetite rises (stat " + stat + ")");
            assertTrue(ticks >= 1, "cooldown is always at least 1 tick");
            prev = ticks;
        }
    }

    // ---- bountyDropCount ---------------------------------------------

    @Test
    void bountyDropCountLaddersFromOneToMaxDrops() {
        int maxDrops = 3;
        // Bands of width ceil(10/3)=4: stat 1-4 -> 1, 5-8 -> 2, 9-10 -> 3.
        assertEquals(1, FrogStats.bountyDropCount(1, maxDrops, CAP));
        assertEquals(1, FrogStats.bountyDropCount(4, maxDrops, CAP));
        assertEquals(2, FrogStats.bountyDropCount(5, maxDrops, CAP));
        assertEquals(2, FrogStats.bountyDropCount(8, maxDrops, CAP));
        assertEquals(3, FrogStats.bountyDropCount(9, maxDrops, CAP));
        assertEquals(maxDrops, FrogStats.bountyDropCount(CAP, maxDrops, CAP),
            "the cap stat always reaches maxDrops");
    }

    @Test
    void bountyDropCountIsAlwaysOneWhenMaxDropsIsOne() {
        for (int stat = 1; stat <= CAP; stat++) {
            assertEquals(1, FrogStats.bountyDropCount(stat, 1, CAP),
                "maxDrops=1 means a single drop at every Bounty");
        }
    }

    // ---- reachRadius -------------------------------------------------

    @Test
    void reachRadiusAscendsFromMinAtStat1ToMaxAtCap() {
        int min = 8;
        int max = 16;
        assertEquals(min, FrogStats.reachRadius(1, min, max, CAP), "stat 1 is the min radius");
        assertEquals(max, FrogStats.reachRadius(CAP, min, max, CAP), "stat cap is the max radius");
        int prev = 0;
        for (int stat = 1; stat <= CAP; stat++) {
            int r = FrogStats.reachRadius(stat, min, max, CAP);
            assertTrue(r >= prev, "radius must not decrease as Reach rises (stat " + stat + ")");
            prev = r;
        }
    }
}
