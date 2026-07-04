package com.flatts.productivefrogs.content.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the Liquid Experience conversion ratio (#281 Phase 2, risk register:
 * "pin the 20 mB/point ratio in a unit test"). 20 mB per XP point is the
 * {@code c:experience} ecosystem standard documented on NeoForge's
 * {@code Tags.Fluids.EXPERIENCE}; every producer (Phase 4 altars) and consumer
 * (the bucket spend path, third-party drains) depends on it never drifting.
 */
class LiquidExperienceFluidTest {

    @Test
    void ratioIsTheCExperienceStandard() {
        assertEquals(20, LiquidExperienceFluid.MB_PER_POINT,
            "20 mB/point is the c:experience standard - changing it breaks cross-mod XP parity");
        assertEquals(50, LiquidExperienceFluid.POINTS_PER_BUCKET,
            "one 1000 mB bucket must be exactly 50 points");
    }

    @Test
    void conversionsAreExactInBothDirections() {
        assertEquals(1000, LiquidExperienceFluid.pointsToMb(50));
        assertEquals(50, LiquidExperienceFluid.mbToWholePoints(1000));
        assertEquals(0, LiquidExperienceFluid.pointsToMb(0));
        assertEquals(0, LiquidExperienceFluid.mbToWholePoints(0));
    }

    @Test
    void mbToPointsFloorsSoRemainderStaysAsFluid() {
        // 39 mB is 1 whole point with 19 mB left in the tank - never rounded up.
        assertEquals(1, LiquidExperienceFluid.mbToWholePoints(39));
        assertEquals(0, LiquidExperienceFluid.mbToWholePoints(19));
    }

    @Test
    void wholePointRoundTripConserves() {
        for (int points = 0; points <= 1000; points++) {
            assertEquals(points,
                LiquidExperienceFluid.mbToWholePoints(LiquidExperienceFluid.pointsToMb(points)),
                "points -> mB -> points must conserve at " + points);
        }
    }
}
