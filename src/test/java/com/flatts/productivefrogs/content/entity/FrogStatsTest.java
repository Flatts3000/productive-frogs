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

    // ---- inheritStat --------------------------------------------------

    @Test
    void improvementRollsOneAboveTheBetterParent() {
        // improvementChance 1.0 -> always the improve branch: min(cap, hi + 1).
        assertEquals(8, FrogStats.inheritStat(7, 5, 1.0, 0.0, CAP, random),
            "improve should be max(7,5)+1 = 8");
        assertEquals(5, FrogStats.inheritStat(3, 4, 1.0, 0.0, CAP, random),
            "improve should be max(3,4)+1 = 5");
    }

    @Test
    void improvementIsCappedAtTheStatCap() {
        assertEquals(CAP, FrogStats.inheritStat(10, 10, 1.0, 0.0, CAP, random),
            "improving a capped pair stays at the cap");
        assertEquals(CAP, FrogStats.inheritStat(10, 8, 1.0, 0.0, CAP, random),
            "min(cap, 10+1) clamps to the cap");
    }

    @Test
    void regressionRollsTheRoundedParentAverage() {
        // improvementChance 0.0, regressionChance 1.0 -> always regress.
        assertEquals(7, FrogStats.inheritStat(8, 5, 0.0, 1.0, CAP, random),
            "round((8+5)/2) = round(6.5) = 7 (half-up)");
        assertEquals(5, FrogStats.inheritStat(6, 4, 0.0, 1.0, CAP, random),
            "(6+4)/2 = 5 exactly");
    }

    @Test
    void regressionIsANoOpForEqualParents() {
        // average == hi, so an equal pair never drops - the late-game grind.
        assertEquals(9, FrogStats.inheritStat(9, 9, 0.0, 1.0, CAP, random),
            "equal parents regress to themselves (no-op)");
    }

    @Test
    void holdReturnsTheBetterParentWhenNeitherBranchFires() {
        // Both chances 0.0 -> the else (hold) branch.
        assertEquals(7, FrogStats.inheritStat(7, 3, 0.0, 0.0, CAP, random),
            "hold should be max(7,3) = 7");
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
