package com.flatts.productivefrogs.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure-math unit tests for {@link ResourceTadpole#correctedRemainingTicks} - the
 * corrected Jade "Growing time" computation (#238). {@code aiStep} accelerates the
 * vanilla {@code age} counter so it reaches {@code VANILLA} (24000) in {@code target}
 * ticks; this maps a live {@code age} to the real ticks left, so Jade no longer reads
 * the vanilla-rate (too long) number when a pack lowers {@code tadpoleGrowthTicks}.
 * No Minecraft bootstrap - just integer math (matches {@code FrogStatsTest}).
 */
class ResourceTadpoleGrowthTest {

    private static final int VANILLA = 24000;

    @Test
    void acceleratedRateScalesRemainingDownByTheAccelerationFactor() {
        // target 3600 (Sky Frogs' 3 min): a fresh tadpole matures in 3600 ticks (180s),
        // not the vanilla 24000 (1200s) Jade would otherwise show.
        assertEquals(3600, ResourceTadpole.correctedRemainingTicks(0, 3600, VANILLA),
            "fresh tadpole at target 3600 -> 3600 real ticks (180s)");
        // Half-aged (12000 of 24000) -> half of 3600 = 1800 ticks (90s).
        assertEquals(1800, ResourceTadpole.correctedRemainingTicks(12000, 3600, VANILLA),
            "half-aged at target 3600 -> 1800 real ticks (90s)");
    }

    @Test
    void defaultOrSlowerRateMatchesVanillaRemainder() {
        // At target == vanilla (the default) there is no acceleration: remaining is the
        // plain vanilla remainder, so the value matches Jade's stock line (no change).
        assertEquals(VANILLA, ResourceTadpole.correctedRemainingTicks(0, VANILLA, VANILLA));
        assertEquals(6000, ResourceTadpole.correctedRemainingTicks(18000, VANILLA, VANILLA));
        // A configured value above the ceiling is clamped to vanilla (aiStep does not
        // accelerate there), so it also matches the vanilla remainder.
        assertEquals(VANILLA, ResourceTadpole.correctedRemainingTicks(0, 48000, VANILLA),
            "target above the ceiling clamps to the vanilla rate");
    }

    @Test
    void neverNegativeAndZeroAtOrPastMaturity() {
        assertEquals(0, ResourceTadpole.correctedRemainingTicks(VANILLA, 3600, VANILLA),
            "matured tadpole has zero remaining");
        assertEquals(0, ResourceTadpole.correctedRemainingTicks(VANILLA + 5000, 3600, VANILLA),
            "age past the ceiling clamps to zero, never negative");
    }

    @Test
    void monotonicNonIncreasingAsAgeRises() {
        int prev = Integer.MAX_VALUE;
        for (int age = 0; age <= VANILLA; age += 2000) {
            int remaining = ResourceTadpole.correctedRemainingTicks(age, 3600, VANILLA);
            assertTrue(remaining <= prev, "remaining must not increase as age rises (age " + age + ")");
            assertTrue(remaining >= 0, "remaining must never be negative (age " + age + ")");
            prev = remaining;
        }
    }
}
